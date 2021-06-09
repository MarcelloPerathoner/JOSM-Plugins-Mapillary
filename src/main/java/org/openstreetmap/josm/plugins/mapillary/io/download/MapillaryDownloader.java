// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.io.download;

import org.openstreetmap.josm.data.vector.VectorDataSet;
import org.openstreetmap.josm.data.vector.VectorNode;
import org.openstreetmap.josm.data.vector.VectorWay;
import org.openstreetmap.josm.plugins.mapillary.gui.layer.MapillaryLayer;
import org.openstreetmap.josm.plugins.mapillary.oauth.OAuthUtils;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryImageUtils;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryKeys;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryURL;
import org.openstreetmap.josm.plugins.mapillary.utils.api.JsonDecoder;
import org.openstreetmap.josm.plugins.mapillary.utils.api.JsonImageDetailsDecoder;
import org.openstreetmap.josm.plugins.mapillary.utils.api.JsonSequencesDecoder;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Logging;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class that concentrates all the ways of downloading of the plugin. All the
 * download petitions will be managed one by one.
 *
 * @author nokutu
 */
public final class MapillaryDownloader {
  private MapillaryDownloader() {
    // Private constructor to avoid instantiation
  }

  /**
   * Download a specific set of images
   *
   * @param images The images to download
   * @return The downloaded images
   */
  public static Map<String, Collection<VectorNode>> downloadImages(String... images) {
    if (images.length == 0) {
      return Collections.emptyMap();
    }
    return Stream.of(images).map(MapillaryURL.APIv4::getImageInformation).parallel()
      .map(MapillaryDownloader::getUrlResponse)
      .map(data -> JsonDecoder.decodeData(data, JsonImageDetailsDecoder::decodeImageInfos)).flatMap(Collection::stream)
      .sorted(Comparator.comparingLong(image -> MapillaryImageUtils.getDate(image).toEpochMilli()))
      .collect(Collector.of(
        HashMap<String, Collection<VectorNode>>::new, (map, node) -> map
          .computeIfAbsent(MapillaryImageUtils.getSequenceKey(node), key -> new ArrayList<>()).add(node),
        (rMap, oMap) -> {
          rMap.putAll(oMap);
          return rMap;
        }));
  }

  /**
   * Download a specific set of sequences
   *
   * @param sequences The sequences to download
   * @return The downloaded sequences
   */
  public static Collection<VectorWay> downloadSequences(String... sequences) {
    return downloadSequences(true, sequences);
  }

  /**
   * Download a specific set of sequences
   *
   * @param sequences The sequences to download
   * @param force Force the download if the sequence has already been downloaded once.
   * @return The downloaded sequences
   */
  public static Collection<VectorWay> downloadSequences(boolean force, String... sequences) {
    // prevent infinite loops. See #20470.
    if (Arrays.stream(Thread.currentThread().getStackTrace())
      .skip(/* getStackTrace, downloadSequences(sequences), downloadSequences(force, sequences) */ 3)
      .filter(element -> MapillaryDownloader.class.getName().equals(element.getClassName()))
      .filter(element -> "downloadSequences".equals(element.getMethodName())).count() > 2) {
      return Collections.emptyList();
    }
    String[] toGet = sequences != null
      ? Stream.of(sequences).filter(Objects::nonNull).filter(s -> !s.trim().isEmpty()).toArray(String[]::new)
      : new String[0];
    if (MapillaryLayer.hasInstance() && !force) {
      VectorDataSet data = MapillaryLayer.getInstance().getData();
      Set<String> previousSequences = data.getWays().stream().map(way -> way.get(MapillaryKeys.KEY))
        .collect(Collectors.toSet());
      toGet = Stream.of(toGet).filter(seq -> !previousSequences.contains(seq)).toArray(String[]::new);
    }
    if (toGet.length > 0) {
      return Stream.of(toGet).map(MapillaryURL.APIv4::getImagesBySequences).map(MapillaryDownloader::getUrlResponse)
        .flatMap(jsonObject -> JsonDecoder.decodeData(jsonObject, JsonSequencesDecoder::decodeSequence).stream())
        .collect(Collectors.toSet());
    }
    return Collections.emptyList();
  }

  private static JsonObject getUrlResponse(URL url) {
    HttpClient client = OAuthUtils.addAuthenticationHeader(HttpClient.create(url));
    try (JsonReader reader = Json.createReader(client.connect().getContentReader())) {
      return reader.readObject();
    } catch (IOException e) {
      Logging.trace(e);
      return null;
    } finally {
      client.disconnect();
    }
  }

  private static JsonObject getUrlResponse(String url) {
    try {
      return getUrlResponse(new URL(url));
    } catch (MalformedURLException e) {
      Logging.error(e);
    }
    return null;
  }
}

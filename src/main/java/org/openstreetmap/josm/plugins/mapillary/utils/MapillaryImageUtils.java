package org.openstreetmap.josm.plugins.mapillary.utils;

import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.plugins.mapillary.cache.CacheUtils;
import org.openstreetmap.josm.plugins.mapillary.cache.MapillaryCache;
import org.openstreetmap.josm.tools.Logging;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * Keys and utility methods for Mapillary Images
 */
public final class MapillaryImageUtils {
  private MapillaryImageUtils() {
    /* No op */}

  // Documented
  public static final String KEY = "key";
  public static final String CREATED_AT = "created_at";
  public static final String UPDATED_AT = "updated_at";
  public static final String CREATED_BY = "created_by";
  public static final String OWNED_BY = "owned_by";
  public static final String URL = "url";
  // Undocumented (from v3)
  public static final String PANORAMIC = "pano";
  // Image specific
  /** Check if the node has one of the Mapillary keys */
  public static final Predicate<INode> IS_IMAGE = node -> node.hasKey(MapillaryKeys.KEY)
    || node.hasKey(MapillaryImageUtils.IMPORTED_KEY);
  /** Check if the node is for a panoramic image */
  public static final Predicate<INode> IS_PANORAMIC = node -> node != null
    && MapillaryKeys.PANORAMIC_TRUE.equals(node.get(PANORAMIC));
  public static final String CAMERA_ANGLE = "ca";
  public static final String QUALITY_SCORE = "quality_score";
  public static final String SEQUENCE_KEY = "skey";
  public static final String IMPORTED_KEY = "import_file";

  public static IWay<?> getSequence(INode image) {
    if (image == null) {
      return null;
    }
    return image.getReferrers().stream().filter(IWay.class::isInstance).map(IWay.class::cast).findFirst().orElse(null);
  }

  /**
   * Get the date an image was created at
   *
   * @param img The image
   * @return The instant the image was created
   */
  public static Instant getDate(INode img) {
    if (img.hasKey(CREATED_AT) && Instant.EPOCH.equals(img.getInstant())) {
      try {
        Instant instant = Instant.ofEpochSecond(Long.parseLong(img.get(CREATED_AT)));
        img.setInstant(instant);
        return instant;
      } catch (NumberFormatException e) {
        Logging.error(e);
      }
    }
    return Instant.EPOCH;
  }

  /**
   * Get the quality score for an image
   *
   * @param img The image to get the quality score for
   * @return The quality score (1, 2, 3, 4, 5, or {@link Integer#MIN_VALUE})
   */
  public static int getQuality(INode img) {
    if (img.hasKey(MapillaryImageUtils.QUALITY_SCORE)) {
      try {
        return Integer.parseInt(img.get(MapillaryImageUtils.QUALITY_SCORE));
      } catch (final NumberFormatException e) {
        Logging.error(e);
      }
    }
    return Integer.MIN_VALUE;
  }

  /**
   * Get the angle for an image
   *
   * @param img The image to get the angle for
   * @return The angle, or {@link Double#NaN}.
   */
  public static double getAngle(INode img) {
    return img.hasKey(CAMERA_ANGLE) ? Math.toRadians(Double.parseDouble(img.get(CAMERA_ANGLE))) : Double.NaN;
  }

  /**
   * Get the file for the image
   *
   * @param img The image to get the file for
   * @return The image file. May be {@code null}.
   */
  public static File getFile(INode img) {
    return img.hasKey(IMPORTED_KEY) ? new File(img.get(IMPORTED_KEY)) : null;
  }

  /**
   * Get a future for an image
   *
   * @param image The node with image information
   * @return The future with a potential image (image may be {@code null})
   */
  public static Future<BufferedImage> getImage(INode image) {
    // TODO use URL field in v4
    if (image.hasKey(KEY)) {
      CompletableFuture<BufferedImage> completableFuture = new CompletableFuture<>();
      CacheUtils.submit(image.get(KEY), MapillaryCache.Type.FULL_IMAGE, (entry, attributes, result) -> {
        try {
          BufferedImage realImage = ImageIO.read(new ByteArrayInputStream(entry.getContent()));
          completableFuture.complete(realImage);
        } catch (IOException e) {
          Logging.error(e);
          completableFuture.complete(null);
        }
      });
      return completableFuture;
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Get the captured at time
   *
   * @param image The image to get the captured at time
   * @return The time the image was captured at, or {@link Instant#EPOCH} if not known.
   */
  public static Instant getCapturedAt(INode image) {
    if (image.hasKey(CREATED_AT)) {
      return Instant.parse(image.get(CREATED_AT));
    }
    return Instant.EPOCH;
  }

  /**
   * Get The key for a node
   *
   * @param image The image
   * @return The key, or {@code ""} if no key exists
   */
  public static String getKey(INode image) {
    if (image != null && image.hasKey(KEY)) {
      return image.get(KEY);
    }
    return "";
  }

  /**
   * Get the sequence key
   *
   * @param image The image with a sequence key
   * @return The sequence key
   */
  public static String getSequenceKey(INode image) {
    if (image != null && image.hasKey(SEQUENCE_KEY)) {
      return image.get(SEQUENCE_KEY);
    }
    return null;
  }
}

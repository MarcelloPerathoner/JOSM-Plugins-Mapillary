// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.actions;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.apache.commons.math3.stat.inference.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.vector.VectorNode;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetDialog;
import org.openstreetmap.josm.plugins.mapillary.data.mapillary.ObjectDetections;
import org.openstreetmap.josm.plugins.mapillary.gui.layer.MapillaryLayer;
import org.openstreetmap.josm.plugins.mapillary.gui.layer.PointObjectLayer;
import org.openstreetmap.josm.plugins.mapillary.testutils.annotations.MapillaryLayerAnnotation;
import org.openstreetmap.josm.plugins.mapillary.testutils.annotations.MapillaryURLWireMock;
import org.openstreetmap.josm.plugins.mapillary.testutils.annotations.MapillaryURLWireMockErrors;
import org.openstreetmap.josm.plugins.mapillary.testutils.annotations.ObjectDetectionsAnnotation;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryImageUtils;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryKeys;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryMapFeatureUtils;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.testutils.annotations.TaggingPresets;

import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;

/**
 * Test class for {@link SmartEditAddAction}
 *
 * @author Taylor Smock
 */
@MapillaryLayerAnnotation
@MapillaryURLWireMock
@BasicPreferences
@Main
@ObjectDetectionsAnnotation
@Projection
@TaggingPresets
class SmartEditAddActionTest {
    private PointObjectLayer pointObjectLayer;
    private VectorNode node;
    private TaggingPresetDialogMock taggingPresetDialogMock = new TaggingPresetDialogMock();

    /**
     * Get a stream of arguments
     *
     * @return Arguments.of(ObjectDetection, boolean willBeAdded)
     */
    static Stream<Arguments> detectionsAreAdded() {
        return Stream.of(Arguments.of(ObjectDetections.valueOfMapillaryValue("object--fire-hydrant"), true),
            Arguments.of(ObjectDetections.valueOfMapillaryValue("regulatory--stop--g1"), true),
            Arguments.of(ObjectDetections.valueOfMapillaryValue("human--rider--bicyclist"), false));
    }

    @BeforeEach
    void setUp() {
        pointObjectLayer = new PointObjectLayer(MapillaryKeys.MAPILLARY_TRAFFIC_SIGNS);
        node = new VectorNode("");
        node.setCoor(LatLon.ZERO);
        // This id is actually for complementary--both-directions--g2
        node.setOsmId(496980935069177L, 1);
        pointObjectLayer.getData().addPrimitive(node);
    }

    @MapillaryURLWireMockErrors
    @ParameterizedTest
    @MethodSource("detectionsAreAdded")
    void testActionPerformedNoOsmLayer(ObjectDetections detection) {
        node.put("value", detection.getKey());
        final SmartEditAddAction smartEditAddAction = new SmartEditAddAction(pointObjectLayer, node);

        taggingPresetDialogMock.answer = TaggingPresetDialog.DIALOG_ANSWER_APPLY;
        smartEditAddAction.actionPerformed(null);

        assertAll(() -> assertFalse(node.isDeleted()), () -> assertFalse(node.isDisabled()),
            () -> assertTrue(node.isVisible()));

    }

    @MapillaryURLWireMockErrors
    @ParameterizedTest
    @MethodSource("detectionsAreAdded")
    void testActionPerformedOsmLayerLocked(ObjectDetections detection) {
        node.put("value", detection.getKey());
        final SmartEditAddAction smartEditAddAction = new SmartEditAddAction(pointObjectLayer, node);
        final OsmDataLayer osmDataLayer = new OsmDataLayer(new DataSet(), "SmartEditAddActionTest", null);
        osmDataLayer.lock();
        MainApplication.getLayerManager().addLayer(osmDataLayer);

        taggingPresetDialogMock.answer = TaggingPresetDialog.DIALOG_ANSWER_APPLY;
        smartEditAddAction.actionPerformed(null);

        assertAll(() -> assertFalse(node.isDeleted()), () -> assertFalse(node.isDisabled()),
            () -> assertTrue(node.isVisible()));
    }

    @MapillaryURLWireMockErrors
    @ParameterizedTest
    @MethodSource("detectionsAreAdded")
    void testActionPerformedOsmLayerUnlockedNoApply(ObjectDetections detection) {
        TestUtils.assumeWorkingJMockit();
        node.put("value", detection.getKey());
        final SmartEditAddAction smartEditAddAction = new SmartEditAddAction(pointObjectLayer, node);
        final OsmDataLayer osmDataLayer = new OsmDataLayer(new DataSet(), "SmartEditAddActionTest", null);
        MainApplication.getLayerManager().addLayer(osmDataLayer);

        taggingPresetDialogMock.answer = TaggingPresetDialog.DIALOG_ANSWER_CANCEL;
        smartEditAddAction.actionPerformed(null);

        assertAll(() -> assertFalse(node.isDeleted()), () -> assertFalse(node.isDisabled()),
            () -> assertTrue(node.isVisible()));
    }

    @MapillaryURLWireMockErrors
    @ParameterizedTest
    @MethodSource("detectionsAreAdded")
    void testActionPerformedOsmLayerUnlockedApply(ObjectDetections detection, boolean added) {
        final OsmDataLayer osmDataLayer = this.commonApply(detection);
        final SmartEditAddAction smartEditAddAction = new SmartEditAddAction(pointObjectLayer, node);

        taggingPresetDialogMock.answer = TaggingPresetDialog.DIALOG_ANSWER_APPLY;
        smartEditAddAction.actionPerformed(null);

        if (added) {
            assertAll(() -> assertTrue(node.isDeleted(), "The node should be deleted"), () -> assertEquals(1,
                osmDataLayer.getDataSet().allPrimitives().size(), "The data layer should have a new node"));
        } else {
            assertAll(() -> assertFalse(node.isDeleted()), () -> assertFalse(node.isDisabled()),
                () -> assertTrue(node.isVisible()),
                () -> assertEquals(0, osmDataLayer.getDataSet().allPrimitives().size()));
        }
    }

    @MapillaryURLWireMockErrors
    @ParameterizedTest
    @MethodSource("detectionsAreAdded")
    void testAddMapillaryTagsMapObjectNoMapillaryLayer(ObjectDetections detection, boolean added) {
        // The detection has the id of the detection object
        node.setOsmId(1234, 1);
        node.put(MapillaryMapFeatureUtils.MapFeatureProperties.IMAGES.toString(), "12345678");
        final OsmDataLayer osmDataLayer = this.commonApply(detection);

        taggingPresetDialogMock.answer = TaggingPresetDialog.DIALOG_ANSWER_APPLY;
        new SmartEditAddAction(pointObjectLayer, node).actionPerformed(null);

        if (added) {
            final Node osmNode = osmDataLayer.getDataSet().getNodes().stream().findAny().orElse(null);
            assertNotNull(osmNode);
            assertAll(() -> assertEquals(Long.toString(node.getId()), osmNode.get("mapillary:map_feature")),
                () -> assertFalse(osmNode.hasKey("mapillary")), () -> assertFalse(osmNode.hasKey("mapillary:image")));
        }
    }

    @MapillaryURLWireMockErrors
    @ParameterizedTest
    @MethodSource("detectionsAreAdded")
    void testAddMapillaryTagsMapObjectWithMapillaryLayerNoMapillarySelected(ObjectDetections detection, boolean added) {
        VectorNode image = new VectorNode("");
        image.setOsmId(12345678, 1);
        MapillaryLayer.getInstance().getData().addPrimitive(image);
        this.testAddMapillaryTagsMapObjectNoMapillaryLayer(detection, added);
    }

    @MapillaryURLWireMockErrors
    @ParameterizedTest
    @MethodSource("detectionsAreAdded")
    void testAddMapillaryTagsMapObjectWithMapillaryLayerMapillarySelected(ObjectDetections detection, boolean added) {
        VectorNode image = new VectorNode("");
        image.setOsmId(12345678, 1);
        image.put(MapillaryImageUtils.ImageProperties.ID.toString(), String.valueOf(image.getId()));
        MapillaryLayer.getInstance().getData().addPrimitive(image);
        MapillaryLayer.getInstance().getData().setSelected(image);
        node.setOsmId(1234, 1);
        node.put(MapillaryMapFeatureUtils.MapFeatureProperties.IMAGES.toString(), "12345678");
        final OsmDataLayer osmDataLayer = this.commonApply(detection);

        taggingPresetDialogMock.answer = TaggingPresetDialog.DIALOG_ANSWER_APPLY;
        new SmartEditAddAction(pointObjectLayer, node).actionPerformed(null);

        if (added) {
            final Node osmNode = osmDataLayer.getDataSet().getNodes().stream().findAny().orElse(null);
            assertNotNull(osmNode);
            assertAll(() -> assertEquals(Long.toString(node.getId()), osmNode.get("mapillary:map_feature")),
                () -> assertFalse(osmNode.hasKey("mapillary")),
                () -> assertEquals(Long.toString(image.getId()), osmNode.get("mapillary:image")));
        }
    }

    /**
     * Do the common actions for actual apply code
     *
     * @param detection The detection that will be applied
     * @return The osm data layer that will be applied to
     */
    private OsmDataLayer commonApply(ObjectDetections detection) {
        TestUtils.assumeWorkingJMockit();
        final OsmDataLayer osmDataLayer = new OsmDataLayer(new DataSet(), "SmartEditAddActionTest", null);
        node.put("value", detection.getKey());
        MainApplication.getLayerManager().addLayer(osmDataLayer);

        taggingPresetDialogMock.answer = TaggingPresetDialog.DIALOG_ANSWER_APPLY;

        return osmDataLayer;
    }

    /**
     * Fake the TaggingPresetDialog.
     * <p>
     * This class fakes a {@link TaggingPresetDialog} by returning immediately from
     * {@link TaggingPresetDialog#setVisible} with the pre-set answer.
     */
    private static class TaggingPresetDialogMock extends MockUp<TaggingPresetDialog> {
        int answer = TaggingPresetDialog.DIALOG_ANSWER_APPLY;

        @Mock
        public void setVisible(Invocation inv, boolean visible) {
            TaggingPresetDialog dialog = inv.getInvokedInstance();
            dialog.answer = answer;
        }
    }
}

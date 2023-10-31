// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.mapillary.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.vector.VectorPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetDialog;
import org.openstreetmap.josm.plugins.mapillary.command.SmartEditAddCommand;
import org.openstreetmap.josm.plugins.mapillary.data.mapillary.AdditionalInstructions;
import org.openstreetmap.josm.plugins.mapillary.data.mapillary.ObjectDetections;
import org.openstreetmap.josm.plugins.mapillary.gui.layer.MapillaryLayer;
import org.openstreetmap.josm.plugins.mapillary.gui.layer.PointObjectLayer;
import org.openstreetmap.josm.plugins.mapillary.utils.MapillaryMapFeatureUtils;
import org.openstreetmap.josm.tools.ImageProvider;

import jakarta.annotation.Nonnull;

/**
 * Add an item to the map from a {@link org.openstreetmap.josm.plugins.mapillary.gui.layer.PointObjectLayer}
 *
 * @author Taylor Smock
 */
public class SmartEditAddAction extends JosmAction {
    private final transient VectorPrimitive mapillaryObject;
    private final transient PointObjectLayer pointObjectLayer;
    private final ObjectDetections detection;

    /**
     * Create a new {@link SmartEditRemoveAction}
     *
     * @param pointObjectLayer The point object layer we are adding data from
     * @param primitive The primitive to be added (it should be in/on the layer)
     */
    public SmartEditAddAction(final PointObjectLayer pointObjectLayer, final VectorPrimitive primitive) {
        super(tr("Add"), new ImageProvider("dialogs", "add"), tr("Add Map Feature to OSM"), null, false, null, false);
        Objects.requireNonNull(pointObjectLayer);
        Objects.requireNonNull(primitive);
        this.mapillaryObject = primitive;
        this.pointObjectLayer = pointObjectLayer;
        this.detection = ObjectDetections.valueOfMapillaryValue(mapillaryObject.get("value"));
        this.updateEnabledState();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        this.pointObjectLayer.hideWindow(mapillaryObject);
        addMapillaryPrimitiveToOsm(this.mapillaryObject, this.detection);
        this.updateEnabledState();
    }

    @Override
    public final void updateEnabledState() {
        this.setEnabled(!this.detection.getTaggingPresets().isEmpty());
    }

    /**
     * Add a Mapillary Object Detection Primitive to OSM
     * <p>
     * This function first shows a preset dialog to the user pre-filled with the
     * detected values.  On apply it adds an osm primitive to the OSM data layer and
     * removes the vector primitive from the mapillary layer.  This operation can be
     * undone.
     *
     * @param mapillaryObject The vector primitive to use as prototype
     * @param detection The detections for the vector primitive
     */
    void addMapillaryPrimitiveToOsm(final VectorPrimitive mapillaryObject, final ObjectDetections detection) {
        final DataSet dataSet = MainApplication.getLayerManager().getActiveDataSet();
        final List<TaggingPreset> presets = detection.getTaggingPresets();
        final List<OsmPrimitive> toAdd = generateToAdd();
        if (dataSet != null && !dataSet.isLocked() && presets.size() == 1 && !toAdd.isEmpty()) {
            final TaggingPreset preset = presets.get(0);
            final OsmPrimitive firstPrimitive = toAdd.get(0);

            // the tags we want added to the first primitive
            Map<String, String> firstTags = getMapillaryTags();
            firstTags.putAll(detection.getOsmKeys());
            if (preset.getAllKeys().contains("direction")) {
                try {
                    String direction = firstPrimitive.get(MapillaryMapFeatureUtils.MapFeatureProperties.ALIGNED_DIRECTION.toString());
                    firstTags.put("direction", Long.toString(Math.round(Double.valueOf(direction))));
                } catch (NumberFormatException | NullPointerException e) {
                    // make errorprone happy
                }
            }

            // Show the dialog.  Usually we would pass a data handler to the dialog but
            // since our case is special (there's no primitive yet in the dataset for
            // the preset to work upon) it's maybe easier this way.
            TaggingPresetDialog dialog = preset.prepareDialog(null, null, false);
            if (dialog != null) {
                dialog.getPresetInstance().fillIn(firstTags);
                dialog.setVisible(true);
                if (dialog.getAnswer() == TaggingPresetDialog.DIALOG_ANSWER_APPLY) {
                    // since we passed no handler to the dialog we must handle it ourselves
                    firstTags.putAll(dialog.getPresetInstance().getChangedTags());
                    List<PrimitiveData> add = toAdd.stream().map(OsmPrimitive::save).collect(Collectors.toList());
                    add.get(0).setKeys(firstTags);

                    List<Command> commands = new ArrayList<>();
                    commands.add(new SmartEditAddCommand(mapillaryObject, add, dataSet));
                    commands.addAll(additionalCommands(firstPrimitive));
                    String title = commands.get(0).getDescriptionText();
                    UndoRedoHandler.getInstance().add(SequenceCommand.wrapIfNeeded(title, commands));
                }
            }
        }
    }

    /**
     * Generate additional commands
     *
     * @param addedPrimitive The primitive added to the OSM dataset
     */
    private List<Command> additionalCommands(@Nonnull final OsmPrimitive addedPrimitive) {
        List<Command> commands = new ArrayList<>();

        final AdditionalInstructions additionalInstructions = detection.getAdditionalInstructions();
        if (additionalInstructions != null) {
            final Command additionalCommand = additionalInstructions.apply(addedPrimitive);
            if (additionalCommand != null) {
                commands.add(additionalCommand);
            }
        }
        return commands;
    }

    /**
     * Returns the mapillary:* tags
     *
     * @return the mapillary tags
     */
    private Map<String, String> getMapillaryTags() {
        Map<String, String> map = new HashMap<>();
        long[] imageIds = MapillaryMapFeatureUtils.getImageIds(mapillaryObject);
        if (imageIds.length > 0) {
            final OptionalLong imageId = MapillaryLayer.getInstance().getData().getSelectedNodes().stream()
                .mapToLong(IPrimitive::getUniqueId).distinct()
                .filter(i -> LongStream.of(imageIds).anyMatch(id -> id == i)).findFirst();
            if (imageId.isPresent()) {
                map.put("mapillary:image", Long.toString(imageId.getAsLong()));
            }
        }
        if (mapillaryObject.getId() != 0) {
            map.put("mapillary:map_feature", Long.toString(mapillaryObject.getId()));
        }
        return map;
    }

    /**
     * Generate the objects to add
     *
     * @return The objects to add (first object in list is what should have the tags)
     */
    private List<OsmPrimitive> generateToAdd() {
        if (mapillaryObject instanceof INode) {
            final Node tNode = new Node();
            tNode.setCoor(((INode) mapillaryObject).getCoor());
            mapillaryObject.getKeys().forEach(tNode::put);
            return Collections.singletonList(tNode);
        } else if (mapillaryObject instanceof IWay) {
            Way way = new Way();
            way.removeAll();
            way.setNodes(Collections.emptyList());
            ((IWay<?>) mapillaryObject).getNodes().stream().map(INode::getCoor).map(Node::new).forEach(way::addNode);
            List<OsmPrimitive> toAdd = new ArrayList<>(way.getNodesCount() + 1);
            toAdd.add(way);
            toAdd.addAll(way.getNodes());
            return Collections.unmodifiableList(toAdd);
        } else {
            return Collections.emptyList();
        }
    }
}

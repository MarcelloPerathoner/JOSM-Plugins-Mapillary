package org.openstreetmap.josm.plugins.mapillary.command;

import static org.openstreetmap.josm.tools.I18n.trn;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.AddPrimitivesCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.vector.VectorDataSet;
import org.openstreetmap.josm.data.vector.VectorPrimitive;

/**
 * Deletes the VectorPrimitive in the Mapillary vector layer and adds the OsmPrimitives
 * to the current edit layer.
 * <p>
 * This new command becomes necessary because the OSM command subsystem only works on
 * OsmPrimitives and the unfortunate decision to derive VectorPrimitive from
 * AbstractPrimitive instead of from OsmPrimitive.
 */
public class SmartEditAddCommand extends AddPrimitivesCommand {
    VectorDataSet vectorDataSet;
    VectorPrimitive vectorPrimitive;
    Collection<OsmPrimitive> osmPrimitives;
    String title;

    /**
     * Adds osm primitives while deleting a vector primitive.
     * <p>
     * After execution the first primitive in data is selected.
     *
     * @param vectorPrimitive the vector primitive to remove
     * @param osmPrimitives the primitivedata of the osm primitives to add
     * @param osmDataSet the dataSet of the osm primitives
     */
    public SmartEditAddCommand(VectorPrimitive vectorPrimitive,
            List<PrimitiveData> osmPrimitives, DataSet osmDataSet) {
        super(osmPrimitives, osmPrimitives.subList(0, 1), osmDataSet);
        this.vectorPrimitive = vectorPrimitive;
        this.vectorDataSet = vectorPrimitive.getDataSet();
        int size = osmPrimitives.size();
        title = trn(
            "Mapillary Smart Edit: Added {0} object",
            "Mapillary Smart Edit: Added {0} objects", size, size
        );
    }

    @Override
    public boolean executeCommand() {
        super.executeCommand();
        vectorDataSet.setSelected(vectorDataSet.getSelected().stream()
            .filter(n -> !n.equals(vectorPrimitive)).collect(Collectors.toList()));
        vectorPrimitive.setDeleted(true);
        return true;
    }

    @Override
    public void undoCommand() {
        vectorPrimitive.setDeleted(false);
        super.undoCommand();
    }

    @Override
    public String getDescriptionText() {
        return title;
    }
}

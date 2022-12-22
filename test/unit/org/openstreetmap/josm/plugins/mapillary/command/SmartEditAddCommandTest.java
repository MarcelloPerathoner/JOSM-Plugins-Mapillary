package org.openstreetmap.josm.plugins.mapillary.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.PrimitiveData;
import org.openstreetmap.josm.data.vector.VectorDataSet;
import org.openstreetmap.josm.data.vector.VectorNode;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

@BasicPreferences
class SmartEditAddCommandTest {
    @Test
    void testCommand() {
        final DataSet dataSet = new DataSet();
        final PrimitiveData primitiveData = new Node(LatLon.ZERO).save();

        final VectorDataSet vectorDataSet = new VectorDataSet();
        final VectorNode vectorPrimitive = new VectorNode("foo");
        vectorDataSet.addPrimitive(vectorPrimitive);

        SmartEditAddCommand cmd = new SmartEditAddCommand(vectorPrimitive,
            Collections.singletonList(primitiveData), dataSet);

        assertFalse(vectorPrimitive.isDeleted());

        cmd.executeCommand();

        assertTrue(vectorPrimitive.isDeleted());

        assertEquals(1, dataSet.getNodes().size());

        cmd.undoCommand();

        assertFalse(vectorPrimitive.isDeleted());

        assertEquals(0, dataSet.getNodes().size());
    }
}

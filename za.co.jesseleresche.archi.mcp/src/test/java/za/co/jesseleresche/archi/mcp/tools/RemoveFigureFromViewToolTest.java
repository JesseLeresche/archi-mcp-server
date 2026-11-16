package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class RemoveFigureFromViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final RemoveFigureFromViewTool tool = new RemoveFigureFromViewTool();

    @Test
    void removesFigureAndItsConnectionsButKeepsModel() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 300, 0, 120, 55);
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        models.connection(fa, fb, rel);

        run(tool, "{'view_id':'" + v.getId() + "','figure_id':'" + fa.getId() + "'}");

        assertNull(ModelAccessor.findDiagramObjectById(v, fa.getId()));
        assertTrue(fb.getTargetConnections().isEmpty());
        assertNull(ModelAccessor.findConnectionByRelationshipId(v, rel.getId()));
        assertNotNull(ModelAccessor.findElementById(models.model, a.getId()));
        assertNotNull(ModelAccessor.findRelationshipById(models.model, rel.getId()));
        assertEquals(1, models.saves);
    }

    @Test
    void removesNestedFigureByElementId() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IArchimateElement a = models.element("BusinessActor", "A");
        models.figure(g, a, 10, 10, 120, 55);
        run(tool, "{'view_id':'" + v.getId() + "','element_id':'" + a.getId() + "'}");
        assertTrue(g.getChildren().isEmpty());
        assertSame(v, g.eContainer());
    }

    @Test
    void rejectsBadRequests() {
        IArchimateDiagramModel v = models.view("V");
        String vid = v.getId();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("Either figure_id or element_id"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope','figure_id':'x'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','figure_id':'x'}"));
        assertTrue(e.getMessage().contains("Figure not found on view: x"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','element_id':'y'}"));
        assertTrue(e.getMessage().contains("No figure found for element on view: y"));
    }
}

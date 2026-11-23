package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class DeleteConnectionToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final DeleteConnectionTool tool = new DeleteConnectionTool();

    IArchimateDiagramModel v;
    IArchimateRelationship rel;
    IDiagramModelArchimateObject fa;
    IDiagramModelArchimateConnection c;

    void setUp() {
        v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 300, 0, 120, 55);
        rel = models.relationship("ServingRelationship", a, b);
        c = models.connection(fa, fb, rel);
    }

    @Test
    void deletesByConnectionIdKeepingRelationship() throws Exception {
        setUp();
        run(tool, "{'view_id':'" + v.getId() + "','connection_id':'" + c.getId() + "'}");
        assertNull(ModelAccessor.findConnectionById(v, c.getId()));
        assertTrue(fa.getSourceConnections().isEmpty());
        assertNotNull(ModelAccessor.findRelationshipById(models.model, rel.getId()));
        assertEquals(1, models.saves);
    }

    @Test
    void deletesByRelationshipId() throws Exception {
        setUp();
        run(tool, "{'view_id':'" + v.getId() + "','relationship_id':'" + rel.getId() + "'}");
        assertNull(ModelAccessor.findConnectionByRelationshipId(v, rel.getId()));
    }

    @Test
    void rejectsBadRequests() {
        setUp();
        String vid = v.getId();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("Either connection_id or relationship_id"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope','connection_id':'x'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','connection_id':'x'}"));
        assertTrue(e.getMessage().contains("Connection not found on view: x"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','relationship_id':'y'}"));
        assertTrue(e.getMessage().contains("No connection found for relationship on view: y"));
        assertEquals(0, models.saves);
    }

    /** S1.10 / FR-8: a target-side ghost (detached source) can be found and removed by connection_id. */
    @Test
    void removesTargetSideGhostByConnectionId() throws Exception {
        IArchimateDiagramModel view = models.view("V");
        IArchimateElement s = models.element("ApplicationComponent", "S");
        IArchimateElement t = models.element("ApplicationComponent", "T");
        IDiagramModelGroup g = models.group(view, "G", 0, 0, 300, 200);
        IDiagramModelArchimateObject fs = models.figure(g, s, 10, 30, 120, 55);
        IDiagramModelArchimateObject ft = models.figure(view, t, 400, 0, 120, 55);
        IDiagramModelArchimateConnection ghost = models.connection(fs, ft,
                models.relationship("ServingRelationship", s, t));
        view.getChildren().remove(g); // detach the source without disconnecting

        run(tool, "{'view_id':'" + view.getId() + "','connection_id':'" + ghost.getId() + "'}");

        assertTrue(ft.getTargetConnections().isEmpty());
        assertEquals(1, models.saves);
    }

    /** S15 / FR-7: connection_id removes only that drawing. */
    @Test
    void connectionIdTargetsOneOfSeveralDrawings() throws Exception {
        setUp();
        IDiagramModelArchimateConnection c2 = models.connection(fa, (IDiagramModelArchimateObject) c.getTarget(), rel);
        run(tool, "{'view_id':'" + v.getId() + "','connection_id':'" + c2.getId() + "'}");
        assertNull(ModelAccessor.findConnectionById(v, c2.getId()));
        assertSame(c, ModelAccessor.findConnectionById(v, c.getId()));
    }

    /** S14 / FR-6. */
    @Test
    void removeByRelationshipIdRejectsAmbiguous() {
        setUp();
        IDiagramModelArchimateConnection c2 = models.connection(fa, (IDiagramModelArchimateObject) c.getTarget(), rel);
        Exception e = assertThrows(Exception.class,
                () -> run(tool, "{'view_id':'" + v.getId() + "','relationship_id':'" + rel.getId() + "'}"));
        TestModels.assertMentions(e.getMessage(), rel.getId(), "2 connections", c.getId(), c2.getId(),
                "pass connection_id");
        assertEquals(2, ModelAccessor.findConnectionsByRelationshipId(v, rel.getId()).size());
        assertEquals(0, models.saves);
    }
}

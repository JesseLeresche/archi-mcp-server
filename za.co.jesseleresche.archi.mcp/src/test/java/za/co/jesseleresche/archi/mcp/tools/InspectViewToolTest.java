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
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class InspectViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final InspectViewTool tool = new InspectViewTool();

    @Test
    void routesLayoutConnectionsAndGetConnection() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(g, a, 10, 10, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 500, 0, 120, 55);
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        IDiagramModelArchimateConnection c = models.connection(fa, fb, rel);
        String vid = "'view_id':'" + v.getId() + "'";

        JsonNode layout = run(tool, "{'operation':'layout'," + vid + "}").get("figures");
        assertEquals(3, layout.size());
        JsonNode one = run(tool, "{'operation':'layout'," + vid + ",'element_id':'" + a.getId() + "'}").get("figures");
        assertEquals(1, one.size());
        assertEquals(g.getId(), one.get(0).get("parent_figure_id").asText());

        JsonNode conns = run(tool, "{'operation':'connections'," + vid + "}").get("connections");
        assertEquals(1, conns.size());
        assertEquals(c.getId(), conns.get(0).get("connection_id").asText());

        JsonNode conn = run(tool, "{'operation':'get_connection'," + vid + ",'relationship_id':'" + rel.getId() + "'}");
        assertEquals(c.getId(), conn.get("connection_id").asText());
        assertEquals(fa.getId(), conn.get("source_figure_id").asText());
    }

    @Test
    void rejectsBadRequests() {
        String vid = models.view("V").getId();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'operation':'frob','view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("Unknown operation 'frob'"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("'operation' is required"));
        e = assertThrows(Exception.class, () -> run(tool, "{'operation':'layout','view_id':'nope'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
    }
}

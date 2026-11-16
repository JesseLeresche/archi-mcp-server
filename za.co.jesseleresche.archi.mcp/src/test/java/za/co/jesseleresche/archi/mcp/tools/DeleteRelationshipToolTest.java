package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class DeleteRelationshipToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final DeleteRelationshipTool tool = new DeleteRelationshipTool();

    IArchimateElement a, b;
    IArchimateRelationship rel;
    IDiagramModelArchimateObject fa;

    @BeforeEach
    void setUp() {
        a = models.element("BusinessActor", "A");
        b = models.element("BusinessRole", "B");
        rel = models.relationship("AssignmentRelationship", a, b);
        IArchimateDiagramModel v = models.view("V");
        fa = models.figure(v, a, 0, 0, 120, 55);
        models.connection(fa, models.figure(v, b, 200, 0, 120, 55), rel);
    }

    @Test
    void dryRunPreviewsWithoutChanges() throws Exception {
        JsonNode r = run(tool, "{'relationship_id':'" + rel.getId() + "','dry_run':true}");
        assertTrue(r.get("would_delete").asBoolean());
        assertEquals(1, r.get("connections").asInt());
        assertEquals("AssignmentRelationship", r.get("type").asText());
        assertEquals(a.getId(), r.get("source_id").asText());
        assertNotNull(rel.eContainer());
        assertEquals(1, fa.getSourceConnections().size());
        assertEquals(0, models.saves);
    }

    @Test
    void deletesRelationshipAndItsConnections() throws Exception {
        JsonNode r = run(tool, "{'relationship_id':'" + rel.getId() + "'}");
        assertTrue(r.get("deleted").asBoolean());
        assertEquals(1, r.get("connections_removed").asInt());
        assertEquals(b.getId(), r.get("target_id").asText());
        assertNull(rel.eContainer());
        assertTrue(fa.getSourceConnections().isEmpty());
        assertEquals(1, models.saves);
    }

    @Test
    void errorsOnMissingOrUnknownId() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{}"));
        assertEquals("Missing required field: relationship_id", ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'relationship_id':'" + a.getId() + "'}"));
        assertEquals("Relationship not found: " + a.getId(), ex.getMessage());
    }
}

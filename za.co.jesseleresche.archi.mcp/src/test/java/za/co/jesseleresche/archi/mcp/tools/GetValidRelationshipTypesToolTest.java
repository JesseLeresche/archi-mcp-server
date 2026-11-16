package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class GetValidRelationshipTypesToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final GetValidRelationshipTypesTool tool = new GetValidRelationshipTypesTool();

    private void assertFails(String expected, String args) {
        Exception e = assertThrows(Exception.class, () -> run(tool, args));
        assertTrue(e.getMessage() != null && e.getMessage().contains(expected), String.valueOf(e.getMessage()));
    }

    @Test
    void listsValidTypesBetweenElements() throws Exception {
        IArchimateElement actor = models.element("BusinessActor", "A");
        IArchimateElement role = models.element("BusinessRole", "R");
        JsonNode r = run(tool, "{'source_id':'" + actor.getId() + "','target_id':'" + role.getId() + "'}");
        assertEquals("BusinessActor", r.get("source_type").asText());
        assertEquals("BusinessRole", r.get("target_type").asText());
        assertEquals(role.getId(), r.get("target_id").asText());
        List<String> types = new ArrayList<>();
        r.get("valid_relationship_types").forEach(n -> types.add(n.asText()));
        assertTrue(types.contains("AssignmentRelationship"), types.toString());
        assertFalse(types.contains("CompositionRelationship"), types.toString());
    }

    @Test
    void errors() {
        IArchimateElement actor = models.element("BusinessActor", "A");
        assertFails("missing required field 'source_id'", "{'target_id':'x'}");
        assertFails("Source element not found: nope", "{'source_id':'nope','target_id':'x'}");
        assertFails("Target element not found: nope", "{'source_id':'" + actor.getId() + "','target_id':'nope'}");
        models.closeAll();
        assertFails("No model is currently open", "{'source_id':'a','target_id':'b'}");
    }
}

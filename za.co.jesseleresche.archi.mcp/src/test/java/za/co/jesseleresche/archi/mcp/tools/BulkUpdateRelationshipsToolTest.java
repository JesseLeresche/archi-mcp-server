package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class BulkUpdateRelationshipsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkUpdateRelationshipsTool tool = new BulkUpdateRelationshipsTool();

    @Test
    void appliesEachUpdateWithPerItemOkAndSavesOnce() throws Exception {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessRole", "B");
        IArchimateRelationship r1 = models.relationship("AssignmentRelationship", a, b);
        IArchimateRelationship r2 = models.relationship("AssociationRelationship", a, b);
        JsonNode r = run(tool, "{'updates':[{'relationship_id':'" + r1.getId() + "','name':'one'},"
                + "{'relationship_id':'ghost','name':'x'},"
                + "{'relationship_id':'" + r2.getId() + "','documentation':'two'}]}").get("results");
        assertTrue(r.get(0).get("ok").asBoolean());
        assertEquals(r1.getId(), r.get(0).get("relationship_id").asText());
        assertFalse(r.get(1).get("ok").asBoolean());
        assertEquals("ghost", r.get(1).get("relationship_id").asText());
        assertEquals("Relationship not found: ghost", r.get(1).get("error").asText());
        assertTrue(r.get(2).get("ok").asBoolean());
        assertEquals("one", r1.getName());
        assertEquals("two", r2.getDocumentation());
        assertEquals(1, models.saves);
    }

    @Test
    void rejectsMissingOrEmptyUpdates() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{}"));
        assertEquals("'updates' must be a non-empty array", ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'updates':[]}"));
        assertEquals("'updates' must be a non-empty array", ex.getMessage());
    }
}

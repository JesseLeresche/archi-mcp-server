package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class BulkDeleteRelationshipsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkDeleteRelationshipsTool tool = new BulkDeleteRelationshipsTool();

    IArchimateRelationship r1, r2;

    @BeforeEach
    void setUp() {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessRole", "B");
        r1 = models.relationship("AssignmentRelationship", a, b);
        r2 = models.relationship("AssociationRelationship", a, b);
    }

    @Test
    void deletesEachWithPerItemErrors() throws Exception {
        JsonNode r = run(tool, "{'relationship_ids':['" + r1.getId() + "','ghost','" + r2.getId() + "']}")
                .get("results");
        assertTrue(r.get(0).get("ok").asBoolean());
        assertTrue(r.get(0).get("deleted").asBoolean());
        assertFalse(r.get(1).get("ok").asBoolean());
        assertEquals("Relationship not found: ghost", r.get(1).get("error").asText());
        assertTrue(r.get(2).get("ok").asBoolean());
        assertNull(r1.eContainer());
        assertNull(r2.eContainer());
        assertEquals(1, models.saves);
    }

    @Test
    void dryRunLeavesModelUntouched() throws Exception {
        JsonNode r = run(tool, "{'relationship_ids':['" + r1.getId() + "'],'dry_run':true}").get("results");
        assertTrue(r.get(0).get("would_delete").asBoolean());
        assertNotNull(r1.eContainer());
        assertEquals(0, models.saves);
    }

    @Test
    void rejectsMissingOrEmptyIds() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'relationship_ids':[]}"));
        assertEquals("'relationship_ids' must be a non-empty array", ex.getMessage());
    }
}

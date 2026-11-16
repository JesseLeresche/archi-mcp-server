package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class ManageRelationshipsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ManageRelationshipsTool tool = new ManageRelationshipsTool();

    IArchimateElement a, b;

    @BeforeEach
    void setUp() {
        a = models.element("BusinessActor", "A");
        b = models.element("BusinessRole", "B");
    }

    @Test
    void createsSingleObjectOrStringifiedItems() throws Exception {
        JsonNode r = run(tool, "{'operation':'create','items':{'source_id':'" + a.getId() + "','target_id':'"
                + b.getId() + "','type':'AssignmentRelationship'}}").get("results");
        IArchimateRelationship rel = (IArchimateRelationship) ArchimateModelUtils.getObjectByID(
                models.model, r.get(0).get("id").asText());
        assertSame(a, rel.getSource());

        r = run(tool, "{'operation':'create','items':'[{\"source_id\":\"" + a.getId() + "\",\"target_id\":\""
                + b.getId() + "\",\"type\":\"AssociationRelationship\"}]'}").get("results");
        assertNotNull(ArchimateModelUtils.getObjectByID(models.model, r.get(0).get("id").asText()));
    }

    @Test
    void updatesAndDeletesByStringOrObject() throws Exception {
        IArchimateRelationship r1 = models.relationship("AssignmentRelationship", a, b);
        IArchimateRelationship r2 = models.relationship("AssociationRelationship", a, b);
        run(tool, "{'operation':'update','items':[{'relationship_id':'" + r1.getId() + "','name':'renamed'}]}");
        assertEquals("renamed", r1.getName());

        JsonNode dry = run(tool, "{'operation':'delete','items':[{'relationship_id':'" + r1.getId() + "','dry_run':true}]}");
        assertTrue(dry.get("results").get(0).get("would_delete").asBoolean());
        assertNotNull(r1.eContainer());

        run(tool, "{'operation':'delete','items':['" + r1.getId() + "',{'relationship_id':'" + r2.getId() + "'}]}");
        assertNull(r1.eContainer());
        assertNull(r2.eContainer());
    }

    @Test
    void createRoutesToBulkCreateIncludingStringifiedItems() throws Exception {
        JsonNode r = run(tool, "{'operation':'create','items':'{\"source_id\":\"ghost\",\"target_id\":\""
                + b.getId() + "\",\"type\":\"AssignmentRelationship\"}'}").get("results");
        assertEquals("Source element not found: ghost", r.get(0).get("error").asText());
        assertEquals(1, models.saves);
    }

    @Test
    void validTypesIsPerItem() throws Exception {
        JsonNode vt = run(tool, "{'operation':'valid_types','items':{'source_id':'" + a.getId() + "','target_id':'"
                + b.getId() + "'}}").get("results").get(0);
        assertTrue(vt.get("valid_relationship_types").toString().contains("AssignmentRelationship"), vt.toString());
    }

    @Test
    void betweenIsPerItem() throws Exception {
        IArchimateRelationship rel = models.relationship("AssignmentRelationship", a, b);
        JsonNode bt = run(tool, "{'operation':'between','items':[{'element_a_id':'" + a.getId() + "','element_b_id':'"
                + b.getId() + "'},{'element_a_id':'ghost','element_b_id':'" + b.getId() + "'}]}").get("results");
        assertEquals(rel.getId(), bt.get(0).get("results").get(0).get("relationship_id").asText());
        assertEquals("Element not found: ghost", bt.get(1).get("error").asText());
    }

    @Test
    void rejectsUnknownOperationAndMissingItems() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'operation':'frob','items':{}}"));
        assertTrue(ex.getMessage().startsWith("Unknown operation 'frob'"), ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'operation':'create'}"));
        assertEquals("'items' is required (a single object or an array)", ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'operation':'create','items':[]}"));
        assertEquals("'items' must not be an empty array", ex.getMessage());
    }
}

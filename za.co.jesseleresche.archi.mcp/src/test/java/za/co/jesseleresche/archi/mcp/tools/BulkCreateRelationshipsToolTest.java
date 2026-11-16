package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class BulkCreateRelationshipsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkCreateRelationshipsTool tool = new BulkCreateRelationshipsTool();

    IArchimateElement actor, role, obj;

    @BeforeEach
    void setUp() {
        actor = models.element("BusinessActor", "Actor");
        role = models.element("BusinessRole", "Role");
        obj = models.element("BusinessObject", "Obj");
    }

    IArchimateRelationship rel(JsonNode entry) {
        return (IArchimateRelationship) ArchimateModelUtils.getObjectByID(models.model, entry.get("id").asText());
    }

    @Test
    void createsRelationshipsWithNameAndAccessType() throws Exception {
        JsonNode r = run(tool, "{'relationships':["
                + "{'source_id':'" + actor.getId() + "','target_id':'" + role.getId() + "','type':'assignmentrelationship','name':'plays'},"
                + "{'source_id':'" + role.getId() + "','target_id':'" + obj.getId() + "','type':'AccessRelationship','access_type':1}]}")
                .get("results");
        IArchimateRelationship a = rel(r.get(0));
        assertEquals("AssignmentRelationship", a.eClass().getName());
        assertEquals("plays", a.getName());
        assertSame(actor, a.getSource());
        assertSame(role, a.getTarget());
        assertEquals("Relations", r.get(0).get("folder_path").asText());
        assertEquals(models.model.getFolder(FolderType.RELATIONS).getId(), r.get(0).get("folder_id").asText());
        assertEquals(1, ((IAccessRelationship) rel(r.get(1))).getAccessType());
        assertEquals(1, models.saves);
    }

    @Test
    void reportsPerItemErrors() throws Exception {
        JsonNode r = run(tool, "{'relationships':["
                + "{'source_id':'ghost','target_id':'" + role.getId() + "','type':'AssignmentRelationship'},"
                + "{'source_id':'" + actor.getId() + "','target_id':'ghost','type':'AssignmentRelationship'},"
                + "{'source_id':'" + actor.getId() + "','target_id':'" + role.getId() + "','type':'Bogus'},"
                + "{'target_id':'" + role.getId() + "','type':'AssignmentRelationship'}]}")
                .get("results");
        assertEquals("Source element not found: ghost", r.get(0).get("error").asText());
        assertEquals("Target element not found: ghost", r.get(1).get("error").asText());
        assertEquals("Unknown relationship type: Bogus", r.get(2).get("error").asText());
        assertTrue(r.get(3).get("error").asText().contains("source_id"), r.get(3).toString());
        assertTrue(models.model.getFolder(FolderType.RELATIONS).getElements().isEmpty());
    }

    @Test
    void placesByFolderPathAndRejectsWrongSection() throws Exception {
        String viewsId = models.model.getFolder(FolderType.DIAGRAMS).getId();
        String base = "'source_id':'" + actor.getId() + "','target_id':'" + role.getId() + "','type':'AssignmentRelationship'";
        JsonNode r = run(tool, "{'relationships':["
                + "{" + base + ",'folder_path':'Relations/Org'},"
                + "{" + base + ",'folder_id':'" + viewsId + "'},"
                + "{" + base + ",'folder_path':'Business/Sub'}]}").get("results");
        assertEquals("Relations/Org", r.get(0).get("folder_path").asText());
        assertNotNull(rel(r.get(0)));
        assertTrue(r.get(1).get("error").asText().contains("relationships must be placed under the 'Relations' section"),
                r.get(1).toString());
        assertTrue(r.get(2).get("error").asText().contains("relationships must be placed under the 'Relations' section"),
                r.get(2).toString());
        assertTrue(models.model.getFolder(FolderType.DIAGRAMS).getElements().isEmpty());
        assertTrue(models.model.getFolder(FolderType.BUSINESS).getFolders().isEmpty());
    }

    @Test
    void rejectsMatrixInvalidRelationship() throws Exception {
        JsonNode r = run(tool, "{'relationships':[{'source_id':'" + obj.getId() + "','target_id':'" + actor.getId()
                + "','type':'AssignmentRelationship'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().startsWith("Invalid relationship: AssignmentRelationship not allowed"),
                r.get(0).toString());
        assertTrue(models.model.getFolder(FolderType.RELATIONS).getElements().isEmpty());
    }

    @Test
    void rejectsEmptyArray() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'relationships':[]}"));
        assertEquals("'relationships' must be a non-empty array", ex.getMessage());
    }
}

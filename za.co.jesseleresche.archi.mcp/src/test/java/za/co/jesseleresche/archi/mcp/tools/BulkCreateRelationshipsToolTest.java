package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IInfluenceRelationship;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

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
                + "{" + base + ",'folder_id':'" + viewsId + "','on_existing':'add'},"
                + "{" + base + ",'folder_path':'Business/Sub','on_existing':'add'}]}").get("results");
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

    @Test
    void honoursCallerSuppliedIdsWithConflictModes() throws Exception {
        String base = "'source_id':'" + actor.getId() + "','target_id':'" + role.getId() + "','type':'AssignmentRelationship'";
        JsonNode r = run(tool, "{'relationships':["
                + "{" + base + ",'id':'rel-1'},"
                + "{" + base + ",'id':'rel-1'},"
                + "{" + base + ",'id':'" + actor.getId() + "'},"
                + "{" + base + ",'id':'rel-1','on_conflict':'skip'},"
                + "{" + base + ",'id':''}]}").get("results");
        assertEquals("rel-1", r.get(0).get("id").asText());
        assertNotNull(rel(r.get(0)));
        assertEquals("ID already exists in the model: rel-1", r.get(1).get("error").asText());
        assertEquals("ID already exists in the model: " + actor.getId(), r.get(2).get("error").asText());
        assertTrue(r.get(3).get("skipped").asBoolean());
        assertEquals("'id' must not be empty", r.get(4).get("error").asText());
        assertEquals(1, models.model.getFolder(FolderType.RELATIONS).getElements().size());
    }

    @Test
    void setsPropertiesAndSpecializationOnCreate() throws Exception {
        JsonNode r = run(tool, "{'relationships':[{'source_id':'" + actor.getId() + "','target_id':'" + role.getId()
                + "','type':'AssignmentRelationship','properties':[{'key':'k','value':'v'}],"
                + "'specialization':'Plays'}]}").get("results");
        IArchimateRelationship x = rel(r.get(0));
        assertEquals("k", x.getProperties().get(0).getKey());
        assertEquals("v", x.getProperties().get(0).getValue());
        assertEquals("Plays", x.getPrimaryProfile().getName());
        assertEquals("AssignmentRelationship", x.getPrimaryProfile().getConceptType());
    }

    // --- access_type names, influence_strength, directed (QR-23..28) ---

    private List<IArchimateRelationship> all() {
        return ModelAccessor.collectAllFromFolders(models.model, IArchimateRelationship.class);
    }

    private String accessItem(IArchimateElement target, String accessType) {
        return "{'source_id':'" + role.getId() + "','target_id':'" + target.getId()
                + "','type':'AccessRelationship','access_type':" + accessType + "}";
    }

    @Test
    void accessTypeByNameCaseInsensitive() throws Exception {
        IArchimateElement o2 = models.element("BusinessObject", "O2");
        IArchimateElement o3 = models.element("BusinessObject", "O3");
        IArchimateElement o4 = models.element("BusinessObject", "O4");
        JsonNode r = run(tool, "{'relationships':[" + accessItem(obj, "'write'") + "," + accessItem(o2, "'Read'") + ","
                + accessItem(o3, "'UNSPECIFIED'") + "," + accessItem(o4, "'ReadWrite'") + "]}").get("results");
        assertEquals(IAccessRelationship.WRITE_ACCESS, ((IAccessRelationship) rel(r.get(0))).getAccessType());
        assertEquals(IAccessRelationship.READ_ACCESS, ((IAccessRelationship) rel(r.get(1))).getAccessType());
        assertEquals(IAccessRelationship.UNSPECIFIED_ACCESS, ((IAccessRelationship) rel(r.get(2))).getAccessType());
        assertEquals(IAccessRelationship.READ_WRITE_ACCESS, ((IAccessRelationship) rel(r.get(3))).getAccessType());
    }

    @Test
    void accessTypeByIntegerStillWorks() throws Exception {
        IArchimateElement o2 = models.element("BusinessObject", "O2");
        JsonNode r = run(tool, "{'relationships':[" + accessItem(obj, String.valueOf(IAccessRelationship.READ_ACCESS))
                + "," + accessItem(o2, String.valueOf(IAccessRelationship.UNSPECIFIED_ACCESS)) + "]}").get("results");
        assertEquals(IAccessRelationship.READ_ACCESS, ((IAccessRelationship) rel(r.get(0))).getAccessType());
        assertEquals(IAccessRelationship.UNSPECIFIED_ACCESS, ((IAccessRelationship) rel(r.get(1))).getAccessType());
    }

    @Test
    void badAccessTypeIsItemError() throws Exception {
        IArchimateElement o2 = models.element("BusinessObject", "O2");
        JsonNode r = run(tool, "{'relationships':[" + accessItem(obj, "'Sometimes'") + "," + accessItem(o2, "7") + "]}")
                .get("results");
        for (JsonNode e : r) {
            assertTrue(e.path("error").asText().startsWith(
                    "access_type must be one of Write, Read, Unspecified, ReadWrite or 0-3"), e.toString());
        }
        assertTrue(all().isEmpty());
    }

    @Test
    void omittedAccessTypeIsWrite() throws Exception {
        JsonNode r = run(tool, "{'relationships':[{'source_id':'" + role.getId() + "','target_id':'" + obj.getId()
                + "','type':'AccessRelationship'}]}").get("results");
        assertEquals(IAccessRelationship.WRITE_ACCESS, ((IAccessRelationship) rel(r.get(0))).getAccessType());
        JsonNode q = run(new QueryRelationshipsTool(), "{'type_filter':'AccessRelationship'}");
        assertEquals("Write", q.get("relationships").get(0).get("access_type").asText());
    }

    @Test
    void createsInfluenceStrengthAndDirected() throws Exception {
        IArchimateElement g1 = models.element("Goal", "G1");
        IArchimateElement g2 = models.element("Goal", "G2");
        JsonNode r = run(tool, "{'relationships':["
                + "{'source_id':'" + g1.getId() + "','target_id':'" + g2.getId() + "','type':'InfluenceRelationship','influence_strength':'++'},"
                + "{'source_id':'" + actor.getId() + "','target_id':'" + role.getId() + "','type':'AssociationRelationship','directed':true}]}")
                .get("results");
        assertEquals("++", ((IInfluenceRelationship) rel(r.get(0))).getStrength());
        assertTrue(((IAssociationRelationship) rel(r.get(1))).isDirected());
    }

    @Test
    void inapplicableFieldsAreItemErrors() throws Exception {
        IArchimateElement c1 = models.element("ApplicationComponent", "C1");
        IArchimateElement c2 = models.element("ApplicationComponent", "C2");
        String base = "'source_id':'" + c1.getId() + "','target_id':'" + c2.getId() + "','type':'ServingRelationship'";
        JsonNode r = run(tool, "{'relationships':["
                + "{" + base + ",'access_type':'Read'},"
                + "{" + base + ",'influence_strength':'+'},"
                + "{" + base + ",'directed':true},"
                + "{" + base + "}]}").get("results");
        assertEquals("'access_type' does not apply to ServingRelationship", r.get(0).path("error").asText());
        assertEquals("'influence_strength' does not apply to ServingRelationship", r.get(1).path("error").asText());
        assertEquals("'directed' does not apply to ServingRelationship", r.get(2).path("error").asText());
        assertNotNull(rel(r.get(3)));
        assertEquals(1, all().size());
    }

    // --- on_existing (QR-30..36) ---

    private IArchimateElement c1, c2;

    private IArchimateRelationship existingServing() {
        c1 = models.element("ApplicationComponent", "C1");
        c2 = models.element("ApplicationComponent", "C2");
        return models.relationship("ServingRelationship", c1, c2);
    }

    private String serving(IArchimateElement s, IArchimateElement t, String extra) {
        return "{'source_id':'" + s.getId() + "','target_id':'" + t.getId() + "','type':'ServingRelationship'" + extra + "}";
    }

    private static String alreadyExists(String id) {
        return "relationship " + id + " already exists between these elements; pass on_existing: \"reuse\" or \"add\"";
    }

    @Test
    void duplicateFailsWithoutOnExisting() throws Exception {
        IArchimateRelationship existing = existingServing();
        JsonNode r = run(tool, "{'relationships':[" + serving(c1, c2, "") + "]}").get("results");
        assertEquals(alreadyExists(existing.getId()), r.get(0).path("error").asText());
        assertEquals(1, all().size());
    }

    @Test
    void onExistingReuseReturnsExisting() throws Exception {
        IArchimateRelationship existing = existingServing();
        existing.setName("orig");
        ModelAccessor.findOrCreateFolderByPath(models.model, "Relations/Sub").getElements().add(existing);
        JsonNode r = run(tool, "{'relationships':[" + serving(c1, c2, ",'on_existing':'reuse','name':'other'") + "]}")
                .get("results");
        assertEquals(TestModels.json("{'id':'" + existing.getId() + "','reused':true,'folder_path':'Relations/Sub'}"),
                r.get(0));
        assertEquals(1, all().size());
        assertEquals("orig", existing.getName());
    }

    @Test
    void onExistingAddCreatesAnother() throws Exception {
        IArchimateRelationship existing = existingServing();
        JsonNode r = run(tool, "{'relationships':[" + serving(c1, c2, ",'on_existing':'add'") + "]}").get("results");
        IArchimateRelationship second = rel(r.get(0));
        assertNotSame(existing, second);
        assertEquals("ServingRelationship", second.eClass().getName());
        assertSame(c1, second.getSource());
        assertSame(c2, second.getTarget());
        assertEquals(2, all().size());
    }

    @Test
    void onlySameTypeSameDirectionIsExisting() throws Exception {
        existingServing();
        JsonNode r = run(tool, "{'relationships':[" + serving(c2, c1, "") + ","
                + "{'source_id':'" + c1.getId() + "','target_id':'" + c2.getId() + "','type':'FlowRelationship'}]}")
                .get("results");
        assertFalse(r.get(0).has("error"), r.toString());
        assertFalse(r.get(1).has("error"), r.toString());
        assertEquals(3, all().size());
    }

    @Test
    void earlierBatchItemsCount() throws Exception {
        c1 = models.element("ApplicationComponent", "C1");
        c2 = models.element("ApplicationComponent", "C2");
        JsonNode r = run(tool, "{'relationships':[" + serving(c1, c2, "") + "," + serving(c1, c2, "") + "]}")
                .get("results");
        String first = r.get(0).get("id").asText();
        assertNotNull(rel(r.get(0)));
        assertEquals(alreadyExists(first), r.get(1).path("error").asText());
        assertEquals(1, all().size());
    }

    @Test
    void existingIdIsOnConflictsJob() throws Exception {
        IArchimateRelationship existing = existingServing();
        existing.setId("r1");
        JsonNode r = run(tool, "{'relationships':["
                + serving(c1, c2, ",'id':'r1','on_existing':'add'") + ","
                + serving(c2, c1, ",'id':'r1','on_existing':'reuse'") + ","
                + serving(c1, c2, ",'id':'r1','on_conflict':'skip','on_existing':'reuse'") + ","
                + serving(c1, c2, ",'id':'r1','on_conflict':'skip','on_existing':'merge'") + "]}").get("results");
        assertEquals("ID already exists in the model: r1", r.get(0).path("error").asText());
        assertEquals("ID already exists in the model: r1", r.get(1).path("error").asText());
        for (int i = 2; i < 4; i++) {
            assertEquals(TestModels.json("{'id':'r1','skipped':true}"), r.get(i));
        }
        assertEquals(1, all().size());
    }

    @Test
    void newIdWithDuplicateEndsUsesOnExisting() throws Exception {
        IArchimateRelationship existing = existingServing();
        existing.setId("r1");
        JsonNode r = run(tool, "{'relationships':[" + serving(c1, c2, ",'id':'r2'") + "]}").get("results");
        assertEquals(alreadyExists("r1"), r.get(0).path("error").asText());

        r = run(tool, "{'relationships':[" + serving(c1, c2, ",'id':'r2','on_existing':'reuse'") + "]}").get("results");
        assertEquals("r1", r.get(0).path("id").asText(), r.toString());
        assertTrue(r.get(0).path("reused").asBoolean(), r.toString());
        assertNull(ArchimateModelUtils.getObjectByID(models.model, "r2"));

        r = run(tool, "{'relationships':[" + serving(c1, c2, ",'id':'r2','on_existing':'add'") + "]}").get("results");
        assertEquals("r2", r.get(0).path("id").asText(), r.toString());
        assertNotNull(ArchimateModelUtils.getObjectByID(models.model, "r2"));
        assertEquals(2, all().size());
    }

    @Test
    void badOnExistingIsItemError() throws Exception {
        c1 = models.element("ApplicationComponent", "C1");
        c2 = models.element("ApplicationComponent", "C2");
        JsonNode r = run(tool, "{'relationships':[" + serving(c1, c2, ",'on_existing':'merge'") + "]}").get("results");
        String err = r.get(0).path("error").asText();
        assertTrue(err.contains("reuse") && err.contains("add"), r.toString());
        assertTrue(all().isEmpty());
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IInfluenceRelationship;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class UpdateRelationshipToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final UpdateRelationshipTool tool = new UpdateRelationshipTool();

    IArchimateElement actor, role;
    IArchimateRelationship rel;

    @BeforeEach
    void setUp() {
        actor = models.element("BusinessActor", "Actor");
        role = models.element("BusinessRole", "Role");
        rel = models.relationship("AssociationRelationship", actor, role);
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey("old");
        p.setValue("x");
        rel.getProperties().add(p);
    }

    @Test
    void updatesNameDocumentationAndMergesProperties() throws Exception {
        JsonNode r = run(tool, "{'relationship_id':'" + rel.getId() + "','name':'n','documentation':'d',"
                + "'properties':[{'key':'a','value':'1'},{'key':'b','value':'2'},{'key':'old','value':'y'}]}");
        assertEquals(rel.getId(), r.get("id").asText());
        assertEquals("n", rel.getName());
        assertEquals("d", rel.getDocumentation());
        assertEquals(3, rel.getProperties().size());
        assertEquals("old", rel.getProperties().get(0).getKey());
        assertEquals("y", rel.getProperties().get(0).getValue());
        assertEquals("2", rel.getProperties().get(2).getValue());
        r = run(tool, "{'relationship_id':'" + rel.getId() + "','properties':[{'key':'a','value':null}],"
                + "'remove_properties':['b','nope']}");
        assertEquals(2, r.get("removed").asInt());
        assertEquals(1, rel.getProperties().size());
        assertEquals(2, models.saves);
    }

    @Test
    void typeChangeKeepsIdAndReattachesConnections() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateConnection c = models.connection(
                models.figure(v, actor, 0, 0, 120, 55), models.figure(v, role, 200, 0, 120, 55), rel);
        JsonNode r = run(tool, "{'relationship_id':'" + rel.getId() + "','new_type':'AssignmentRelationship'}");
        assertEquals("AssignmentRelationship", r.get("new_type").asText());
        assertEquals(1, r.get("connections_preserved").asInt());
        assertFalse(r.has("warning"));
        IArchimateRelationship nr = (IArchimateRelationship) ArchimateModelUtils.getObjectByID(models.model, rel.getId());
        assertNotSame(rel, nr);
        assertEquals("AssignmentRelationship", nr.eClass().getName());
        assertSame(actor, nr.getSource());
        assertEquals("x", nr.getProperties().get(0).getValue());
        assertSame(nr, c.getArchimateRelationship());
    }

    @Test
    void typeChangeToInvalidTypeWarns() throws Exception {
        JsonNode r = run(tool, "{'relationship_id':'" + rel.getId() + "','new_type':'AccessRelationship'}");
        assertTrue(r.get("warning").asText().contains("not allowed by the ArchiMate matrix"), r.toString());
    }

    @Test
    void movesFolderAndRejectsWrongSection() throws Exception {
        IFolder sub = IArchimateFactory.eINSTANCE.createFolder();
        sub.setName("Sub");
        models.model.getFolder(FolderType.RELATIONS).getFolders().add(sub);
        run(tool, "{'relationship_id':'" + rel.getId() + "','new_folder_id':'" + sub.getId() + "'}");
        assertSame(sub, rel.eContainer());

        String biz = models.model.getFolder(FolderType.BUSINESS).getId();
        Exception ex = assertThrows(Exception.class,
                () -> run(tool, "{'relationship_id':'" + rel.getId() + "','new_folder_id':'" + biz + "'}"));
        assertTrue(ex.getMessage().contains("relationships must be placed under the 'Relations' section"), ex.getMessage());
        assertSame(sub, rel.eContainer());
    }

    @Test
    void errorsOnMissingOrUnknownRelationshipOrType() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'name':'x'}"));
        assertEquals("Missing required field: relationship_id", ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'relationship_id':'ghost'}"));
        assertEquals("Relationship not found: ghost", ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'relationship_id':'" + actor.getId() + "'}"));
        assertEquals("Relationship not found: " + actor.getId(), ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'relationship_id':'" + rel.getId() + "','new_type':'Bogus'}"));
        assertEquals("Unknown relationship type: Bogus", ex.getMessage());
    }

    @Test
    void setsAndClearsSpecializationAcrossTypeChange() throws Exception {
        run(tool, "{'relationship_id':'" + rel.getId() + "','specialization':'Knows'}");
        assertEquals("Knows", za.co.jesseleresche.archi.mcp.util.ModelAccessor.getSpecialization(rel));
        run(tool, "{'relationship_id':'" + rel.getId() + "','new_type':'AssignmentRelationship','specialization':'Plays'}");
        IArchimateRelationship nr = (IArchimateRelationship) ArchimateModelUtils.getObjectByID(models.model, rel.getId());
        assertEquals("Plays", nr.getPrimaryProfile().getName());
        assertEquals("AssignmentRelationship", nr.getPrimaryProfile().getConceptType());
        run(tool, "{'relationship_id':'" + rel.getId() + "','specialization':''}");
        assertNull(nr.getPrimaryProfile());
    }

    // --- access_type names, influence_strength, directed, applicability (QR-23, 25, 27..29) ---

    private IAccessRelationship access(int accessType) {
        IAccessRelationship a = (IAccessRelationship) models.relationship("AccessRelationship", role,
                models.element("BusinessObject", "Obj"));
        a.setAccessType(accessType);
        return a;
    }

    private IArchimateRelationship byId(String id) {
        return (IArchimateRelationship) ArchimateModelUtils.getObjectByID(models.model, id);
    }

    @Test
    void accessTypeByName() throws Exception {
        IAccessRelationship a = access(IAccessRelationship.WRITE_ACCESS);
        run(tool, "{'relationship_id':'" + a.getId() + "','access_type':'read'}");
        assertEquals(IAccessRelationship.READ_ACCESS, a.getAccessType());
    }

    @Test
    void badAccessTypeIsItemError() {
        IAccessRelationship a = access(IAccessRelationship.READ_ACCESS);
        for (String bad : new String[] {"'Sometimes'", "7"}) {
            Exception ex = assertThrows(Exception.class, () -> run(tool,
                    "{'relationship_id':'" + a.getId() + "','name':'changed','access_type':" + bad + "}"));
            assertTrue(String.valueOf(ex.getMessage()).startsWith(
                    "access_type must be one of Write, Read, Unspecified, ReadWrite or 0-3"), ex.toString());
            assertEquals(IAccessRelationship.READ_ACCESS, a.getAccessType());
            assertNotEquals("changed", a.getName(), "name unchanged");
        }
    }

    @Test
    void updatesInfluenceStrengthAndDirected() throws Exception {
        IInfluenceRelationship inf = (IInfluenceRelationship) models.relationship("InfluenceRelationship",
                models.element("Goal", "G1"), models.element("Goal", "G2"));
        inf.setStrength("++");
        IAssociationRelationship assoc = (IAssociationRelationship) rel;
        assoc.setDirected(true);
        run(tool, "{'relationship_id':'" + inf.getId() + "','influence_strength':'-'}");
        assertEquals("-", inf.getStrength());
        run(tool, "{'relationship_id':'" + assoc.getId() + "','directed':false}");
        assertFalse(assoc.isDirected());
        run(tool, "{'relationship_id':'" + assoc.getId() + "','directed':true}");
        assertTrue(assoc.isDirected());
        run(tool, "{'relationship_id':'" + inf.getId() + "','influence_strength':''}");
        assertTrue(inf.getStrength() == null || inf.getStrength().isEmpty(), "'' clears: " + inf.getStrength());
    }

    @Test
    void inapplicableFieldsAreItemErrors() {
        IArchimateRelationship serving = models.relationship("ServingRelationship", role, actor);
        for (String field : new String[] {"'access_type':'Read'", "'influence_strength':'+'", "'directed':true"}) {
            Exception ex = assertThrows(Exception.class, () -> run(tool,
                    "{'relationship_id':'" + serving.getId() + "','name':'changed'," + field + "}"));
            String name = field.substring(1, field.indexOf('\'', 1));
            assertEquals("'" + name + "' does not apply to ServingRelationship", ex.getMessage());
            assertNotEquals("changed", serving.getName(), "name unchanged");
        }
        assertEquals(0, models.saves);
    }

    @Test
    void applicabilityFollowsNewType() throws Exception {
        run(tool, "{'relationship_id':'" + rel.getId() + "','new_type':'AccessRelationship','access_type':'Read'}");
        IArchimateRelationship nr = byId(rel.getId());
        assertTrue(nr instanceof IAccessRelationship, nr.eClass().getName());
        assertEquals(IAccessRelationship.READ_ACCESS, ((IAccessRelationship) nr).getAccessType());
    }

    @Test
    void inapplicableAfterNewTypeChangesNothing() {
        IAccessRelationship a = access(IAccessRelationship.READ_ACCESS);
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'relationship_id':'" + a.getId()
                + "','new_type':'ServingRelationship','access_type':'Read'}"));
        assertEquals("'access_type' does not apply to ServingRelationship", ex.getMessage());
        assertSame(a, byId(a.getId()));
        assertEquals("AccessRelationship", a.eClass().getName());
        assertEquals(IAccessRelationship.READ_ACCESS, a.getAccessType());
    }

    // --- type change carry-over / dropped (QR-39, QR-40, FR-QR-24) ---

    @Test
    void typeChangeReportsDroppedAttributes() throws Exception {
        IAccessRelationship a = access(IAccessRelationship.READ_ACCESS);
        a.setName("reads");
        a.setDocumentation("doc");
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey("k");
        p.setValue("v");
        a.getProperties().add(p);
        ModelAccessor.applySpecialization(models.model, a, TestModels.json("{'specialization':'Feed'}"));
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateConnection c = models.connection(models.figure(v, role, 0, 0, 120, 55),
                models.figure(v, (IArchimateElement) a.getTarget(), 200, 0, 120, 55), a);

        JsonNode r = run(tool, "{'relationship_id':'" + a.getId() + "','new_type':'FlowRelationship'}");
        assertEquals(TestModels.json("{'access_type':'Read','specialization':'Feed'}"), r.get("dropped"), r.toString());
        IArchimateRelationship flow = byId(a.getId());
        assertEquals("FlowRelationship", flow.eClass().getName());
        assertEquals("reads", flow.getName());
        assertEquals("doc", flow.getDocumentation());
        assertEquals("v", flow.getProperties().get(0).getValue());
        assertSame(flow, c.getArchimateRelationship());
        assertNull(ModelAccessor.getSpecialization(flow));

        IInfluenceRelationship inf = (IInfluenceRelationship) models.relationship("InfluenceRelationship",
                models.element("Goal", "G1"), models.element("Goal", "G2"));
        inf.setStrength("++");
        r = run(tool, "{'relationship_id':'" + inf.getId() + "','new_type':'AssociationRelationship'}");
        assertEquals(TestModels.json("{'influence_strength':'++'}"), r.get("dropped"), r.toString());

        ((IAssociationRelationship) rel).setDirected(true);
        r = run(tool, "{'relationship_id':'" + rel.getId() + "','new_type':'AccessRelationship','access_type':'Read'}");
        IArchimateRelationship acc = byId(rel.getId());
        assertEquals(IAccessRelationship.READ_ACCESS, ((IAccessRelationship) acc).getAccessType());
        assertEquals(TestModels.json("{'directed':true}"), r.get("dropped"), r.toString());
    }

    @Test
    void typeChangeKeepsSpecializationSetInSameItem() throws Exception {
        IAccessRelationship a = access(IAccessRelationship.READ_ACCESS);
        ModelAccessor.applySpecialization(models.model, a, TestModels.json("{'specialization':'Feed'}"));
        JsonNode r = run(tool, "{'relationship_id':'" + a.getId()
                + "','new_type':'FlowRelationship','specialization':'Feed'}");
        IArchimateRelationship flow = byId(a.getId());
        assertEquals("Feed", ModelAccessor.getSpecialization(flow));
        assertEquals("FlowRelationship", flow.getPrimaryProfile().getConceptType());
        assertEquals(TestModels.json("{'access_type':'Read'}"), r.get("dropped"), r.toString());
    }

    @Test
    void noDroppedKeyWhenNothingDropped() throws Exception {
        IArchimateRelationship serving = models.relationship("ServingRelationship", role, actor);
        JsonNode r = run(tool, "{'relationship_id':'" + serving.getId() + "','new_type':'FlowRelationship'}");
        assertEquals("FlowRelationship", r.get("new_type").asText());
        assertFalse(r.has("dropped"), r.toString());

        IAccessRelationship a = access(IAccessRelationship.READ_ACCESS);
        ModelAccessor.applySpecialization(models.model, a, TestModels.json("{'specialization':'Feed'}"));
        r = run(tool, "{'relationship_id':'" + a.getId() + "','name':'plain'}");
        assertFalse(r.has("dropped"), r.toString());
        r = run(tool, "{'relationship_id':'" + a.getId() + "','new_type':'AccessRelationship'}");
        assertFalse(r.has("dropped"), r.toString());
        assertEquals("Feed", ModelAccessor.getSpecialization(a));
    }
}

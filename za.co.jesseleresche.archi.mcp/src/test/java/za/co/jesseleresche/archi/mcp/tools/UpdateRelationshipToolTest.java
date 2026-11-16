package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

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
}

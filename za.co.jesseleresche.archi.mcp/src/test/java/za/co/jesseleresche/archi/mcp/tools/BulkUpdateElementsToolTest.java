package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class BulkUpdateElementsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkUpdateElementsTool tool = new BulkUpdateElementsTool();

    static void prop(IArchimateElement e, String k, String v) {
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey(k);
        p.setValue(v);
        e.getProperties().add(p);
    }

    @Test
    void updatesNameAndDocumentationAndReportsMissing() throws Exception {
        IArchimateElement e = models.element("BusinessActor", "Old");
        JsonNode r = run(tool, "{'updates':[{'element_id':'" + e.getId() + "','name':'New','documentation':'d'},"
                + "{'element_id':'ghost','name':'x'}]}").get("results");
        assertEquals("New", e.getName());
        assertEquals("d", e.getDocumentation());
        assertEquals(e.getId(), r.get(0).get("element_id").asText());
        assertFalse(r.get(0).has("removed"));
        assertEquals("Element not found: ghost", r.get(1).get("error").asText());
        assertEquals(1, models.saves);
    }

    @Test
    void addsUpdatesAndRemovesProperties() throws Exception {
        IArchimateElement e = models.element("BusinessActor", "A");
        prop(e, "a", "1");
        prop(e, "b", "2");
        prop(e, "c", "3");
        JsonNode r = run(tool, "{'updates':[{'element_id':'" + e.getId() + "','properties':["
                + "{'key':'a','value':'10'},{'key':'d','value':'4'},{'key':'b','value':null}],"
                + "'remove_properties':['c','absent']}]}").get("results").get(0);
        assertEquals(2, r.get("removed").asInt());
        assertEquals(2, e.getProperties().size());
        assertEquals("a", e.getProperties().get(0).getKey());
        assertEquals("10", e.getProperties().get(0).getValue());
        assertEquals("d", e.getProperties().get(1).getKey());
        assertEquals("4", e.getProperties().get(1).getValue());
    }

    @Test
    void typeChangeRewiresRelationshipsAndFigures() throws Exception {
        IArchimateElement cap = models.element("Capability", "Pay");
        prop(cap, "k", "v");
        IArchimateElement actor = models.element("BusinessActor", "Bank");
        IArchimateRelationship rel = models.relationship("AssociationRelationship", actor, cap);
        IArchimateDiagramModel view = models.view("V");
        IDiagramModelArchimateObject fig = models.figure(view, cap, 0, 0, 120, 55);
        JsonNode r = run(tool, "{'updates':[{'element_id':'" + cap.getId() + "','new_type':'BusinessService'}]}")
                .get("results").get(0);
        assertEquals(cap.getId(), r.get("old_id").asText());
        IArchimateElement ne = ModelAccessor.findElementById(models.model, r.get("new_id").asText());
        assertNotEquals(cap.getId(), ne.getId());
        assertEquals("BusinessService", ne.eClass().getName());
        assertEquals("Pay", ne.getName());
        assertEquals("v", ne.getProperties().get(0).getValue());
        assertSame(ne, rel.getTarget());
        assertSame(ne, fig.getArchimateElement());
        assertNull(ModelAccessor.findElementById(models.model, cap.getId()));
        // new_folder_id omitted: stays in the old element's folder
        assertEquals(models.model.getFolder(FolderType.STRATEGY).getId(), r.get("folder_id").asText());
    }

    @Test
    void typeChangeRejectsUnknownTypeAndWrongSectionFolder() throws Exception {
        IArchimateElement e = models.element("Capability", "C");
        String appFolder = models.model.getFolder(FolderType.APPLICATION).getId();
        JsonNode r = run(tool, "{'updates':[{'element_id':'" + e.getId() + "','new_type':'Bogus'},"
                + "{'element_id':'" + e.getId() + "','new_type':'BusinessService','new_folder_id':'" + appFolder + "'}]}")
                .get("results");
        assertEquals("Unknown ArchiMate element type: Bogus", r.get(0).get("error").asText());
        assertTrue(r.get(1).get("error").asText().contains("must be placed under the 'Business' section"),
                r.get(1).toString());
        assertSame(e, ModelAccessor.findElementById(models.model, e.getId()));
        assertTrue(models.model.getFolder(FolderType.APPLICATION).getElements().isEmpty());
    }

    @Test
    void rejectsEmptyUpdates() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'updates':[]}"));
        assertEquals("'updates' must be a non-empty array", ex.getMessage());
    }
}

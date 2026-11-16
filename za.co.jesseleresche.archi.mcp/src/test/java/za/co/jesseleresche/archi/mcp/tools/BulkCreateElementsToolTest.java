package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.INameable;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class BulkCreateElementsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkCreateElementsTool tool = new BulkCreateElementsTool();

    @Test
    void createsElementsWithDocumentationAndPropertiesInDefaultFolder() throws Exception {
        JsonNode r = run(tool, "{'elements':[{'name':'CRM','type':'applicationcomponent','documentation':'doc',"
                + "'properties':[{'key':'owner','value':'Ops'},{'key':'tier','value':'1'}]}]}").get("results");
        IArchimateElement e = ModelAccessor.findElementById(models.model, r.get(0).get("id").asText());
        assertEquals("CRM", e.getName());
        assertEquals("ApplicationComponent", e.eClass().getName());
        assertEquals("doc", e.getDocumentation());
        assertEquals(2, e.getProperties().size());
        assertEquals("owner", e.getProperties().get(0).getKey());
        assertEquals("Ops", e.getProperties().get(0).getValue());
        assertEquals(models.model.getFolder(FolderType.APPLICATION).getId(), r.get(0).get("folder_id").asText());
        assertEquals("Application", r.get(0).get("folder_path").asText());
        assertEquals(1, models.saves);
    }

    @Test
    void reportsPerItemErrors() throws Exception {
        JsonNode r = run(tool, "{'elements':[{'name':'X','type':'NoSuchType'},{'type':'BusinessActor'},"
                + "{'name':'Y','type':'BusinessActor','folder_id':'nope'},{'name':'Ok','type':'BusinessActor'}]}")
                .get("results");
        assertTrue(r.get(0).get("error").asText().contains("Unknown ArchiMate element type: NoSuchType"));
        assertTrue(r.get(1).get("error").asText().contains("name"));
        assertEquals("Folder not found: nope", r.get(2).get("error").asText());
        assertNotNull(ModelAccessor.findElementById(models.model, r.get(3).get("id").asText()));
    }

    @Test
    void placesByFolderPathCreatingIntermediates() throws Exception {
        JsonNode r = run(tool, "{'elements':[{'name':'S','type':'ApplicationService','folder_path':'application/Domains/Core'}]}")
                .get("results").get(0);
        assertEquals("Application/Domains/Core", r.get("folder_path").asText());
        IFolder f = ModelAccessor.findFolderById(models.model, r.get("folder_id").asText());
        assertEquals("Core", f.getName());
        assertEquals("S", ((INameable) f.getElements().get(0)).getName());
    }

    @Test
    void rejectsWrongSectionViaFolderIdAndFolderPath() throws Exception {
        String viewsId = models.model.getFolder(FolderType.DIAGRAMS).getId();
        JsonNode r = run(tool, "{'elements':[{'name':'A','type':'BusinessActor','folder_id':'" + viewsId + "'},"
                + "{'name':'B','type':'BusinessActor','folder_path':'Application/Sub'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("must be placed under the 'Business' section"),
                r.get(0).toString());
        assertTrue(r.get(1).get("error").asText().contains("must be placed under the 'Business' section"),
                r.get(1).toString());
        assertTrue(models.model.getFolder(FolderType.BUSINESS).getElements().isEmpty());
        assertTrue(models.model.getFolder(FolderType.DIAGRAMS).getElements().isEmpty());
    }

    @Test
    void rejectsEmptyOrMissingModel() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'elements':[]}"));
        assertEquals("'elements' must be a non-empty array", ex.getMessage());
        models.closeAll();
        ex = assertThrows(Exception.class, () -> run(tool, "{'elements':[{'name':'A','type':'BusinessActor'}]}"));
        assertEquals("No model is currently open in Archi", ex.getMessage());
    }
}

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

    @Test
    void honoursCallerSuppliedIdsWithConflictModes() throws Exception {
        String viewId = models.view("V").getId();
        JsonNode r = run(tool, "{'elements':["
                + "{'id':'el-1','name':'A','type':'BusinessActor'},"
                + "{'id':'el-1','name':'Dup in batch','type':'BusinessActor'},"
                + "{'id':'" + viewId + "','name':'Clashes with a view','type':'BusinessActor'},"
                + "{'id':'  ','name':'Blank','type':'BusinessActor'},"
                + "{'id':'el-1','name':'Skip me','type':'BusinessActor','on_conflict':'skip'},"
                + "{'id':'el-2','name':'B','type':'BusinessActor','on_conflict':'reuse'}]}").get("results");
        assertEquals("el-1", r.get(0).get("id").asText());
        assertEquals("A", ModelAccessor.findElementById(models.model, "el-1").getName());
        assertEquals("ID already exists in the model: el-1", r.get(1).get("error").asText());
        assertEquals("ID already exists in the model: " + viewId, r.get(2).get("error").asText());
        assertEquals("'id' must not be empty", r.get(3).get("error").asText());
        assertTrue(r.get(4).get("skipped").asBoolean());
        assertEquals("el-1", r.get(4).get("id").asText());
        assertEquals("on_conflict must be 'error' or 'skip'", r.get(5).get("error").asText());
        assertEquals(1, ModelAccessor.collectAllFromFolders(models.model, IArchimateElement.class).size());
        assertEquals("A", ModelAccessor.findElementById(models.model, "el-1").getName(), "skip leaves it unchanged");
    }

    @Test
    void setsSpecializationReusingProfilePerNameAndType() throws Exception {
        JsonNode r = run(tool, "{'elements':["
                + "{'name':'A','type':'ApplicationComponent','specialization':'Microservice'},"
                + "{'name':'B','type':'ApplicationComponent','specialization':'Microservice'},"
                + "{'name':'C','type':'Node','specialization':'Microservice'}]}").get("results");
        IArchimateElement a = ModelAccessor.findElementById(models.model, r.get(0).get("id").asText());
        IArchimateElement b = ModelAccessor.findElementById(models.model, r.get(1).get("id").asText());
        IArchimateElement c = ModelAccessor.findElementById(models.model, r.get(2).get("id").asText());
        assertEquals("Microservice", a.getPrimaryProfile().getName());
        assertSame(a.getPrimaryProfile(), b.getPrimaryProfile());
        assertNotSame(a.getPrimaryProfile(), c.getPrimaryProfile());
        assertEquals("Node", c.getPrimaryProfile().getConceptType());
        assertEquals(2, models.model.getProfiles().size());
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class ManageElementsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ManageElementsTool tool = new ManageElementsTool();

    @Test
    void createsBatchWithPerItemErrors() throws Exception {
        JsonNode r = run(tool, "{'operation':'create','items':["
                + "{'name':'CRM','type':'ApplicationComponent'},"
                + "{'name':'Bad','type':'NoSuchType'}]}").get("results");
        String id = r.get(0).get("id").asText();
        IArchimateElement e = ModelAccessor.findElementById(models.model, id);
        assertEquals("CRM", e.getName());
        assertEquals("Application", r.get(0).get("folder_path").asText());
        assertTrue(r.get(1).get("error").asText().contains("NoSuchType"));
        assertEquals(1, models.saves);
    }

    @Test
    void acceptsStringifiedItems() throws Exception {
        JsonNode r = run(tool, "{'operation':'create','items':'{\"name\":\"A\",\"type\":\"BusinessActor\"}'}");
        assertNotNull(ModelAccessor.findElementById(models.model, r.get("results").get(0).get("id").asText()));
    }

    @Test
    void updatesAndDeletes() throws Exception {
        IArchimateElement e = models.element("BusinessActor", "Old");
        run(tool, "{'operation':'update','items':{'element_id':'" + e.getId() + "','name':'New'}}");
        assertEquals("New", e.getName());
        run(tool, "{'operation':'delete','items':['" + e.getId() + "']}");
        assertNull(ModelAccessor.findElementById(models.model, e.getId()));
    }

    @Test
    void rejectsUnknownOperationAndMissingItems() {
        assertTrue(assertThrows(Exception.class, () -> run(tool, "{'operation':'frob','items':{}}"))
                .getMessage().contains("Unknown operation 'frob'"));
        assertTrue(assertThrows(Exception.class, () -> run(tool, "{'operation':'create'}"))
                .getMessage().contains("'items' is required"));
        assertTrue(assertThrows(Exception.class, () -> run(tool, "{'operation':'create','items':[]}"))
                .getMessage().contains("must not be an empty array"));
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IProperty;
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

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path exchange;

    @Test
    void thousandItemBatchFromFileWithSummary() throws Exception {
        System.setProperty(za.co.jesseleresche.archi.mcp.util.ExchangeDir.PROPERTY, exchange.toString());
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < 1000; i++) {
                sb.append(i == 0 ? "" : ",").append("{\"name\":\"E").append(i)
                        .append("\",\"type\":\"").append(i == 500 ? "Nope" : "BusinessActor").append("\"}");
            }
            java.nio.file.Files.writeString(exchange.resolve("big.json"), sb.append("]").toString());
            JsonNode r = run(tool, "{'operation':'create','items_file':'big.json','results':'summary'}");
            assertEquals(1000, r.get("count").asInt());
            assertEquals(999, r.get("succeeded").asInt());
            assertEquals(1, r.get("failed").asInt());
            assertEquals(500, r.get("errors").get(0).get("index").asInt());
            assertEquals(999, ModelAccessor.collectAllFromFolders(models.model, IArchimateElement.class).size());
        } finally {
            System.clearProperty(za.co.jesseleresche.archi.mcp.util.ExchangeDir.PROPERTY);
        }
    }

    /** FR-10, FR-25: merge is in the enum and description, routed, and listed for unknown operations. */
    @Test
    void mergeIsRoutedAndListedInSchema() throws Exception {
        assertTrue(tool.getInputSchema().get("properties").get("operation").get("enum").toString()
                .contains("\"merge\""));
        assertTrue(tool.getDescription().contains("merge"));
        IArchimateElement s = models.element("ApplicationComponent", "CRM");
        IArchimateElement d = models.element("ApplicationComponent", "CRM");
        JsonNode r = run(tool, "{'operation':'merge','items':{'survivor_id':'" + s.getId()
                + "','duplicate_ids':['" + d.getId() + "']}}").get("results");
        assertEquals(1, r.size());
        assertEquals(s.getId(), r.get(0).get("survivor_id").asText(), r.toString());
        assertNull(ModelAccessor.findElementById(models.model, d.getId()));
        assertTrue(assertThrows(Exception.class, () -> run(tool, "{'operation':'frob','items':{}}"))
                .getMessage().contains("merge"));
    }

    /** FR-23: in the summary shape a merge blocked by conflicts counts as failed. */
    @Test
    void blockedMergeCountsAsFailedInSummary() throws Exception {
        IArchimateElement s = models.element("ApplicationComponent", "CRM");
        IArchimateElement d = models.element("ApplicationComponent", "CRM");
        for (Object[] kv : new Object[][] {{s, "sales"}, {d, "finance"}}) {
            IProperty p = IArchimateFactory.eINSTANCE.createProperty();
            p.setKey("owner");
            p.setValue((String) kv[1]);
            ((IArchimateElement) kv[0]).getProperties().add(p);
        }
        JsonNode r = run(tool, "{'operation':'merge','results':'summary','items':[{'survivor_id':'" + s.getId()
                + "','duplicate_ids':['" + d.getId() + "']}]}");
        assertEquals(1, r.get("failed").asInt(), r.toString());
        assertEquals("unresolved conflicts", r.get("errors").get(0).get("error").asText());
        assertNotNull(ModelAccessor.findElementById(models.model, d.getId()));
        assertEquals(0, models.saves);
    }
}

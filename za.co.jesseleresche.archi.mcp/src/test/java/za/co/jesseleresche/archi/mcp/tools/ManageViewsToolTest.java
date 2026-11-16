package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class ManageViewsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ManageViewsTool tool = new ManageViewsTool();

    static void fails(String msg, Executable e) {
        Exception ex = assertThrows(Exception.class, e);
        assertTrue(String.valueOf(ex.getMessage()).contains(msg), ex.getMessage());
    }

    @Test
    void createsBatchAndRejectsWrongSection() throws Exception {
        String business = models.model.getFolder(FolderType.BUSINESS).getId();
        JsonNode r = run(tool, "{'operation':'create','items':[{'name':'A'},"
                + "{'name':'B','folder_id':'" + business + "'}]}").get("results");
        assertEquals("A", ModelAccessor.findViewById(models.model, r.get(0).get("id").asText()).getName());
        assertTrue(r.get(1).get("error").asText().contains("'Views' section"));
        assertTrue(models.model.getFolder(FolderType.BUSINESS).getElements().isEmpty());
    }

    @Test
    void acceptsSingleStringifiedItem() throws Exception {
        JsonNode r = run(tool, "{'operation':'create','items':'{\"name\":\"S\"}'}").get("results");
        assertEquals(1, r.size());
        assertEquals("S", ModelAccessor.findViewById(models.model, r.get(0).get("id").asText()).getName());
    }

    @Test
    void updatesDuplicatesAndDeletesPerItem() throws Exception {
        IArchimateDiagramModel v = models.view("Old");
        JsonNode r = run(tool, "{'operation':'update','items':[{'view_id':'" + v.getId() + "','name':'New'},"
                + "{'view_id':'nope'}]}").get("results");
        assertEquals("New", v.getName());
        assertEquals("View not found: nope", r.get(1).get("error").asText());

        r = run(tool, "{'operation':'duplicate','items':{'source_view_id':'" + v.getId() + "','new_name':'Dup'}}")
                .get("results");
        assertEquals("Dup", ModelAccessor.findViewById(models.model, r.get(0).get("new_view_id").asText()).getName());

        run(tool, "{'operation':'delete','items':[{'view_id':'" + v.getId() + "'}]}");
        assertNull(ModelAccessor.findViewById(models.model, v.getId()));
    }

    @Test
    void rejectsUnknownOperationAndMissingItems() {
        fails("Unknown operation 'frob'", () -> run(tool, "{'operation':'frob','items':{}}"));
        fails("'items' is required", () -> run(tool, "{'operation':'create'}"));
        fails("'operation' is required", () -> run(tool, "{'items':{}}"));
    }
}

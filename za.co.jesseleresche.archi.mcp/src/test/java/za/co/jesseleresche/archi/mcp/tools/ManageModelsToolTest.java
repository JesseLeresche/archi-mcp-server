package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateModel;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class ManageModelsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ManageModelsTool tool = new ManageModelsTool();

    @Test
    void listAndSelectRouteToDelegates() throws Exception {
        IArchimateModel second = models.newModel("Second");
        JsonNode list = run(tool, "{'operation':'list'}");
        assertEquals(2, list.size());
        assertTrue(list.get(0).get("selected").asBoolean());

        JsonNode sel = run(tool, "{'operation':'select','name_or_id':'Second'}");
        assertEquals(second.getId(), sel.get("id").asText());
        assertSame(second, ModelAccessor.getOpenModel());
    }

    @Test
    void rejectsMissingOrUnknownOperation() {
        Exception missing = assertThrows(Exception.class, () -> run(tool, "{}"));
        assertTrue(missing.getMessage().contains("'operation' is required"), missing.getMessage());
        Exception unknown = assertThrows(Exception.class, () -> run(tool, "{'operation':'close'}"));
        assertTrue(unknown.getMessage().contains("Unknown operation 'close'"), unknown.getMessage());
    }
}

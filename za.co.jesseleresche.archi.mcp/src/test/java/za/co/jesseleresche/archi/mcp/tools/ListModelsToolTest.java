package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateModel;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class ListModelsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ListModelsTool tool = new ListModelsTool();

    @Test
    void listsOpenModelsMarkingSelected() throws Exception {
        IArchimateModel second = models.newModel("Second");
        ModelAccessor.selectModel("Second");
        JsonNode r = run(tool, "{}");
        assertEquals(2, r.size());
        assertEquals("Test Model", r.get(0).get("name").asText());
        assertEquals(models.model.getId(), r.get(0).get("id").asText());
        assertFalse(r.get(0).get("selected").asBoolean());
        assertEquals(second.getId(), r.get(1).get("id").asText());
        assertTrue(r.get(1).get("selected").asBoolean());
    }

    @Test
    void failsWhenNoModelsOpen() {
        models.closeAll();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{}"));
        assertTrue(e.getMessage().contains("No models are currently open"), e.getMessage());
    }
}

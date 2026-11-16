package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateModel;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class SelectModelToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final SelectModelTool tool = new SelectModelTool();

    @Test
    void selectsByNameOrId() throws Exception {
        IArchimateModel second = models.newModel("Second");
        JsonNode r = run(tool, "{'name_or_id':'second'}");
        assertEquals(second.getId(), r.get("id").asText());
        assertEquals("Second", r.get("name").asText());
        assertSame(second, ModelAccessor.getOpenModel());

        run(tool, "{'name_or_id':'" + models.model.getId() + "'}");
        assertSame(models.model, ModelAccessor.getOpenModel());
    }

    @Test
    void unknownModelFails() {
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'name_or_id':'Ghost'}"));
        assertTrue(e.getMessage().contains("No open model found matching: Ghost"), e.getMessage());
        assertSame(models.model, ModelAccessor.getOpenModel());
    }
}

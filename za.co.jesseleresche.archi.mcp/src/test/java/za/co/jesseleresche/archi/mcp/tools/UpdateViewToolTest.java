package za.co.jesseleresche.archi.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class UpdateViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final UpdateViewTool tool = new UpdateViewTool();

    @Test
    void updatesNameAndDocumentation() throws Exception {
        IArchimateDiagramModel v = models.view("Old");
        assertEquals(v.getId(), run(tool, "{'view_id':'" + v.getId() + "','name':'New','documentation':'d'}")
                .get("view_id").asText());
        assertEquals("New", v.getName());
        assertEquals("d", v.getDocumentation());
        assertEquals(1, models.saves);
    }

    @Test
    void leavesOmittedFieldsUntouched() throws Exception {
        IArchimateDiagramModel v = models.view("Keep");
        v.setDocumentation("old doc");
        run(tool, "{'view_id':'" + v.getId() + "','documentation':'new doc'}");
        assertEquals("Keep", v.getName());
        assertEquals("new doc", v.getDocumentation());
    }

    @Test
    void rejectsUnknownView() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope','name':'X'}"));
        assertEquals("View not found: nope", ex.getMessage());
        assertEquals(0, models.saves);
    }

    @Test
    void mergesAndRemovesProperties() throws Exception {
        var v = models.view("V");
        run(tool, "{'view_id':'" + v.getId() + "','properties':[{'key':'a','value':'1'},{'key':'b','value':'2'}]}");
        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','properties':[{'key':'a','value':'9'}],'remove_properties':['b']}");
        assertEquals(1, r.get("removed").asInt());
        assertEquals(1, v.getProperties().size());
        assertEquals("9", v.getProperties().get(0).getValue());
    }
}

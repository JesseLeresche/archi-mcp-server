package za.co.jesseleresche.archi.mcp.tools;

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
}

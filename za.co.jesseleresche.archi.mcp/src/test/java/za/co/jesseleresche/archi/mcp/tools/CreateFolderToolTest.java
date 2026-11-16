package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IFolder;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class CreateFolderToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final CreateFolderTool tool = new CreateFolderTool();

    static void fails(String msg, Executable e) {
        Exception ex = assertThrows(Exception.class, e);
        assertTrue(String.valueOf(ex.getMessage()).contains(msg), ex.getMessage());
    }

    @Test
    void createsUnderSectionCaseInsensitively() throws Exception {
        String id = run(tool, "{'name':'Flows','section':' relations '}").get("id").asText();
        IFolder f = ModelAccessor.findFolderById(models.model, id);
        assertEquals("Relations/Flows", ModelAccessor.getFolderPath(f));
        assertEquals(1, models.saves);
    }

    @Test
    void nestsUnderParentAndChecksSectionConsistency() throws Exception {
        IFolder app = models.model.getFolder(FolderType.APPLICATION);
        String id = run(tool, "{'name':'Sub','parent_folder_id':'" + app.getId() + "','section':'Application'}")
                .get("id").asText();
        IFolder sub = ModelAccessor.findFolderById(models.model, id);
        assertSame(app, sub.eContainer());
        fails("is under section 'Application', not 'Views'", () -> run(tool,
                "{'name':'X','parent_folder_id':'" + sub.getId() + "','section':'Views'}"));
        assertTrue(sub.getFolders().isEmpty());
    }

    @Test
    void rejectsAmbiguousUnknownSectionAndMissingParent() {
        fails("Specify parent_folder_id or section", () -> run(tool, "{'name':'X'}"));
        fails("Unknown section 'Nowhere'. Valid sections: ", () -> run(tool, "{'name':'X','section':'Nowhere'}"));
        fails("Parent folder not found: nope", () -> run(tool, "{'name':'X','parent_folder_id':'nope'}"));
        assertEquals(0, models.saves);
    }
}

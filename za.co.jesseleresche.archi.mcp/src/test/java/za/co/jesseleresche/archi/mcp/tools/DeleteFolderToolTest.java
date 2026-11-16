package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class DeleteFolderToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final DeleteFolderTool tool = new DeleteFolderTool();

    static void fails(String msg, Executable e) {
        Exception ex = assertThrows(Exception.class, e);
        assertTrue(String.valueOf(ex.getMessage()).contains(msg), ex.getMessage());
    }

    IFolder sub(IFolder parent, String name) {
        IFolder f = IArchimateFactory.eINSTANCE.createFolder();
        f.setName(name);
        parent.getFolders().add(f);
        return f;
    }

    @Test
    void deletesEmptyFolder() throws Exception {
        IFolder f = sub(models.model.getFolder(FolderType.BUSINESS), "Empty");
        JsonNode r = run(tool, "{'folder_id':'" + f.getId() + "'}");
        assertEquals("Empty", r.get("folder_name").asText());
        assertEquals(f.getId(), r.get("folder_id").asText());
        assertNull(ModelAccessor.findFolderById(models.model, f.getId()));
        assertEquals(1, models.saves);
    }

    @Test
    void refusesRootAndNonEmptyFolders() {
        IFolder business = models.model.getFolder(FolderType.BUSINESS);
        fails("Cannot delete a top-level root folder: " + business.getName(),
                () -> run(tool, "{'folder_id':'" + business.getId() + "'}"));
        IFolder withSub = sub(business, "Parent");
        sub(withSub, "Child");
        fails("contains 1 subfolder(s)", () -> run(tool, "{'folder_id':'" + withSub.getId() + "'}"));
        IFolder withItem = sub(business, "Items");
        withItem.getElements().add(IArchimateFactory.eINSTANCE.createBusinessActor());
        fails("contains 1 item(s)", () -> run(tool, "{'folder_id':'" + withItem.getId() + "'}"));
        assertNotNull(ModelAccessor.findFolderById(models.model, withSub.getId()));
        assertNotNull(ModelAccessor.findFolderById(models.model, withItem.getId()));
        assertEquals(0, models.saves);
    }

    @Test
    void rejectsMissingOrUnknownId() {
        fails("missing required field 'folder_id'", () -> run(tool, "{}"));
        fails("Folder not found: nope", () -> run(tool, "{'folder_id':'nope'}"));
    }
}

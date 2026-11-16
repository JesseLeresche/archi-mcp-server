package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class ManageFoldersToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ManageFoldersTool tool = new ManageFoldersTool();

    static void fails(String msg, Executable e) {
        Exception ex = assertThrows(Exception.class, e);
        assertTrue(String.valueOf(ex.getMessage()).contains(msg), ex.getMessage());
    }

    String create(String item) throws Exception {
        return run(tool, "{'operation':'create','items':" + item + "}").get("results").get(0).get("id").asText();
    }

    @Test
    void createsSectionAwareAndDeletes() throws Exception {
        IFolder f = ModelAccessor.findFolderById(models.model, create("{'name':'Flows','section':'Relations'}"));
        assertEquals("Relations/Flows", ModelAccessor.getFolderPath(f));
        JsonNode r = run(tool, "{'operation':'create','items':[{'name':'Bare'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("Specify parent_folder_id or section"));

        run(tool, "{'operation':'delete','items':{'folder_id':'" + f.getId() + "'}}");
        assertNull(ModelAccessor.findFolderById(models.model, f.getId()));
    }

    @Test
    void movesElementsAndViewsWithSectionGuard() throws Exception {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateDiagramModel v = models.view("V");
        String biz = create("{'name':'B','section':'Business'}");
        String views = create("{'name':'V','section':'Views'}");
        run(tool, "{'operation':'move_element','items':{'folder_id':'" + biz + "','element_id':'" + a.getId() + "'}}");
        assertEquals(biz, ((IFolder) a.eContainer()).getId());

        JsonNode r = run(tool, "{'operation':'move_view','items':[{'view_id':'" + v.getId() + "','folder_id':'" + biz + "'},"
                + "{'view_id':'" + v.getId() + "','folder_id':'" + views + "'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("'Views' section"));
        assertEquals(views, ((IFolder) v.eContainer()).getId());
    }

    @Test
    void readOperationsTakeTopLevelArgs() throws Exception {
        models.element("BusinessActor", "A");
        String business = models.model.getFolder(FolderType.BUSINESS).getId();
        assertEquals(1, run(tool, "{'operation':'list_contents','folder_id':'" + business + "'}").get("elements").size());
        JsonNode tree = run(tool, "{'operation':'tree','section':'business'}").get("folders");
        assertEquals(1, tree.size());
        assertEquals(business, tree.get(0).get("id").asText());
    }

    @Test
    void rejectsUnknownOperationAndMissingItems() {
        fails("Unknown operation 'frob'", () -> run(tool, "{'operation':'frob'}"));
        fails("'items' is required", () -> run(tool, "{'operation':'create'}"));
        fails("'items' must not be an empty array", () -> run(tool, "{'operation':'move_view','items':[]}"));
    }
}

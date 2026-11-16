package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.function.Executable;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class BulkCreateViewsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkCreateViewsTool tool = new BulkCreateViewsTool();

    static void fails(String msg, Executable e) {
        Exception ex = assertThrows(Exception.class, e);
        assertTrue(String.valueOf(ex.getMessage()).contains(msg), ex.getMessage());
    }

    @Test
    void createsInDefaultViewsFolderWithDocumentation() throws Exception {
        JsonNode r = run(tool, "{'views':[{'name':'A','documentation':'doc'},{'name':'B'}]}").get("results");
        IFolder views = models.model.getFolder(FolderType.DIAGRAMS);
        IArchimateDiagramModel a = ModelAccessor.findViewById(models.model, r.get(0).get("id").asText());
        assertEquals("A", a.getName());
        assertEquals("doc", a.getDocumentation());
        assertSame(views, a.eContainer());
        assertEquals(views.getId(), r.get(0).get("folder_id").asText());
        assertEquals(2, views.getElements().size());
        assertEquals(1, models.saves);
    }

    @Test
    void folderPathCreatesSubfoldersUnderViews() throws Exception {
        JsonNode r = run(tool, "{'views':[{'name':'A','folder_path':'views/Overviews/Deep'},"
                + "{'name':'B','folder_path':'Data Flows'}]}").get("results");
        IFolder a = (IFolder) ModelAccessor.findViewById(models.model, r.get(0).get("id").asText()).eContainer();
        assertEquals("Views/Overviews/Deep", ModelAccessor.getFolderPath(a));
        IFolder b = (IFolder) ModelAccessor.findViewById(models.model, r.get(1).get("id").asText()).eContainer();
        assertEquals("Views/Data Flows", ModelAccessor.getFolderPath(b));
    }

    @Test
    void rejectsNonViewsSectionPerItem() throws Exception {
        IFolder business = models.model.getFolder(FolderType.BUSINESS);
        JsonNode r = run(tool, "{'views':[{'name':'A','folder_id':'" + business.getId() + "'},"
                + "{'name':'B','folder_path':'Business/Sub'},"
                + "{'name':'C','folder_id':'nope'},"
                + "{'documentation':'no name'},"
                + "{'name':'OK'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("must be placed under the 'Views' section"));
        assertTrue(r.get(1).get("error").asText().contains("is under section 'Business'"));
        assertTrue(r.get(2).get("error").asText().contains("Folder not found: nope"));
        assertTrue(r.get(3).get("error").asText().contains("missing required field 'name'"));
        assertFalse(r.get(4).has("error"));
        assertTrue(business.getElements().isEmpty());
        assertTrue(business.getFolders().isEmpty());
        assertEquals(1, models.model.getFolder(FolderType.DIAGRAMS).getElements().size());
    }

    @Test
    void rejectsEmptyArrayAndNoModel() {
        fails("'views' must be a non-empty array", () -> run(tool, "{'views':[]}"));
        models.closeAll();
        fails("No model is currently open", () -> run(tool, "{'views':[{'name':'A'}]}"));
    }
}

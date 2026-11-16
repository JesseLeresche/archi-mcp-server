package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class BulkMoveViewsToFolderToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkMoveViewsToFolderTool tool = new BulkMoveViewsToFolderTool();

    IFolder sub(FolderType section) {
        IFolder f = IArchimateFactory.eINSTANCE.createFolder();
        models.model.getFolder(section).getFolders().add(f);
        return f;
    }

    @Test
    void movesViewIntoViewsSubfolder() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IFolder target = sub(FolderType.DIAGRAMS);
        JsonNode r = run(tool, "{'moves':[{'view_id':'" + v.getId() + "','folder_id':'" + target.getId() + "'}]}")
                .get("results").get(0);
        assertSame(target, v.eContainer());
        assertEquals(v.getId(), r.get("view_id").asText());
        assertEquals(models.model.getFolder(FolderType.DIAGRAMS).getId(), r.get("previous_folder_id").asText());
        assertEquals(1, models.saves);
    }

    @Test
    void rejectsNonViewsSectionAndUnknownIds() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IFolder business = sub(FolderType.BUSINESS);
        JsonNode r = run(tool, "{'moves':[{'view_id':'" + v.getId() + "','folder_id':'" + business.getId() + "'},"
                + "{'view_id':'ghost','folder_id':'" + business.getId() + "'},"
                + "{'view_id':'" + v.getId() + "','folder_id':'nope'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("views must be placed under the 'Views' section"));
        assertEquals("View not found: ghost", r.get(1).get("error").asText());
        assertEquals("Folder not found: nope", r.get(2).get("error").asText());
        assertSame(models.model.getFolder(FolderType.DIAGRAMS), v.eContainer());
        assertTrue(business.getElements().isEmpty());
    }

    @Test
    void rejectsEmptyMoves() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'moves':[]}"));
        assertEquals("'moves' must be a non-empty array", ex.getMessage());
    }
}

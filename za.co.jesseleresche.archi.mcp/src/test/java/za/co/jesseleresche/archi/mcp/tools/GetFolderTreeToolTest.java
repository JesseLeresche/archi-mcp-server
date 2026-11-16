package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class GetFolderTreeToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final GetFolderTreeTool tool = new GetFolderTreeTool();

    IFolder sub;

    void nest() {
        sub = IArchimateFactory.eINSTANCE.createFolder();
        sub.setName("Sub");
        models.model.getFolder(FolderType.APPLICATION).getFolders().add(sub);
        models.element("ApplicationComponent", "C");
    }

    @Test
    void returnsFullTree() throws Exception {
        nest();
        JsonNode folders = run(tool, "{}").get("folders");
        assertEquals(models.model.getFolders().size(), folders.size());
        JsonNode app = null;
        for (JsonNode f : folders) {
            if (f.get("name").asText().equals("Application")) app = f;
        }
        assertEquals(1, app.get("element_count").asInt());
        assertEquals(sub.getId(), app.get("folders").get(0).get("id").asText());
    }

    @Test
    void filtersBySectionOrStartsAtFolder() throws Exception {
        nest();
        JsonNode folders = run(tool, "{'section':'APPLIC'}").get("folders");
        assertEquals(1, folders.size());
        assertEquals("Application", folders.get(0).get("name").asText());

        JsonNode r = run(tool, "{'folder_id':'" + sub.getId() + "'}");
        assertEquals("Sub", r.get("name").asText());
        assertEquals(0, r.get("folders").size());
    }

    @Test
    void rejectsUnknownFolder() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'folder_id':'nope'}"));
        assertEquals("Folder not found: nope", ex.getMessage());
    }
}

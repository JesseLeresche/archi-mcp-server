package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class GetViewsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final GetViewsTool tool = new GetViewsTool();

    @Test
    void countsFiguresGroupsAndNotesIncludingNested() throws Exception {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessRole", "B");
        IArchimateDiagramModel v = models.view("Main");
        v.setDocumentation("the main view");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        models.figure(g, a, 10, 30, 120, 55);
        models.figure(v, b, 500, 10, 120, 55);
        models.note(v, "hi", 500, 200, 100, 50);

        JsonNode r = run(tool, "{}");
        assertEquals(1, r.size());
        JsonNode e = r.get(0);
        assertEquals(v.getId(), e.get("id").asText());
        assertEquals("Main", e.get("name").asText());
        assertEquals("the main view", e.get("documentation").asText());
        assertEquals(2, e.get("element_count").asInt());
        assertEquals(1, e.get("group_count").asInt());
        assertEquals(1, e.get("note_count").asInt());
        assertFalse(e.has("element_ids"));

        JsonNode withIds = run(tool, "{'include_elements':true}").get(0).get("element_ids");
        assertEquals(2, withIds.size());
        assertTrue(withIds.toString().contains(a.getId()) && withIds.toString().contains(b.getId()));
    }

    @Test
    void findsViewsInSubfoldersAndOmitsZeroCounts() throws Exception {
        models.view("Top");
        IFolder sub = ModelAccessor.findOrCreateFolderByPath(models.model, "Views/Sub");
        IArchimateDiagramModel nested = models.view("Nested");
        sub.getElements().add(nested);

        JsonNode r = run(tool, "{}");
        assertEquals(2, r.size());
        assertEquals("Top", r.get(0).get("name").asText());
        assertEquals("Nested", r.get(1).get("name").asText());
        assertEquals(0, r.get(1).get("element_count").asInt(-1));
        assertFalse(r.get(1).has("group_count"));
        assertFalse(r.get(1).has("note_count"));
    }

    @Test
    void failsWithNoOpenModel() {
        models.closeAll();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{}"));
        assertTrue(e.getMessage().contains("No model is currently open"), e.getMessage());
    }
}

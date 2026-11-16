package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;

class GetViewLayoutToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final GetViewLayoutTool tool = new GetViewLayoutTool();

    @Test
    void listsAllFiguresIncludingNestedGroupsAndNotes() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IArchimateElement a = models.element("BusinessActor", "A");
        IDiagramModelArchimateObject fa = models.figure(g, a, 10, 20, 130, 60);
        fa.getFeatures().putString(FigureStyle.LABEL_EXPRESSION, "${name}", "");
        IDiagramModelNote n = models.note(v, "hello", 500, 0, 100, 50);

        Map<String, JsonNode> byId = new HashMap<>();
        run(tool, "{'view_id':'" + v.getId() + "'}").get("figures")
                .forEach(f -> byId.put(f.get("figure_id").asText(), f));
        assertEquals(3, byId.size());

        JsonNode jf = byId.get(fa.getId());
        assertEquals(a.getId(), jf.get("element_id").asText());
        assertEquals("A", jf.get("element_name").asText());
        assertEquals("BusinessActor", jf.get("element_type").asText());
        assertEquals(g.getId(), jf.get("parent_figure_id").asText());
        assertEquals("${name}", jf.get("label_expression").asText());
        assertEquals(10, jf.get("x").asInt());
        assertEquals(20, jf.get("y").asInt());
        assertEquals(130, jf.get("width").asInt());
        assertEquals(60, jf.get("height").asInt());

        assertEquals("Group", byId.get(g.getId()).get("type").asText());
        assertEquals("G", byId.get(g.getId()).get("name").asText());
        assertFalse(byId.get(g.getId()).has("parent_figure_id"));
        assertEquals("Note", byId.get(n.getId()).get("type").asText());
        assertEquals("hello", byId.get(n.getId()).get("content").asText());
    }

    @Test
    void filtersByFigureOrElementId() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("BusinessActor", "A");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, models.element("BusinessActor", "B"), 0, 0, 120, 55);

        JsonNode f = run(tool, "{'view_id':'" + v.getId() + "','element_id':'" + a.getId() + "'}").get("figures");
        assertEquals(1, f.size());
        assertEquals(fa.getId(), f.get(0).get("figure_id").asText());
        f = run(tool, "{'view_id':'" + v.getId() + "','figure_id':'" + fb.getId() + "'}").get("figures");
        assertEquals(1, f.size());
        assertEquals("B", f.get(0).get("element_name").asText());
    }

    @Test
    void rejectsBadRequests() {
        String vid = models.view("V").getId();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','figure_id':'x'}"));
        assertTrue(e.getMessage().contains("Figure not found on view: x"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','element_id':'y'}"));
        assertTrue(e.getMessage().contains("No figure found for element on view: y"));
    }
}

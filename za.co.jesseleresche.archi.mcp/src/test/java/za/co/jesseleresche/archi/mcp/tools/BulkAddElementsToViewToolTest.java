package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

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
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class BulkAddElementsToViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkAddElementsToViewTool tool = new BulkAddElementsToViewTool();

    @Test
    void placesFiguresWithDefaultsAndLabelExpression() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','figures':["
                + "{'element_id':'" + a.getId() + "'},"
                + "{'element_id':'" + b.getId() + "','x':200,'y':10,'width':150,'height':80,"
                + "'label_expression':'${name}'}]}").get("results");

        IDiagramModelArchimateObject fa = ModelAccessor.findFigureById(v, r.get(0).get("figure_id").asText());
        assertSame(a, fa.getArchimateElement());
        assertSame(v, fa.eContainer());
        assertEquals(50, fa.getBounds().getX());
        assertEquals(50, fa.getBounds().getY());
        assertEquals(120, fa.getBounds().getWidth());
        assertEquals(55, fa.getBounds().getHeight());

        IDiagramModelArchimateObject fb = ModelAccessor.findFigureById(v, r.get(1).get("figure_id").asText());
        assertEquals(200, fb.getBounds().getX());
        assertEquals(150, fb.getBounds().getWidth());
        assertEquals("${name}", FigureStyle.getLabelExpression(fb));
        assertEquals(1, models.saves);
    }

    @Test
    void nestsIntoGroupAndElementFigureViaParentFigureId() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IDiagramModelArchimateObject outer =
                models.figure(v, models.element("ApplicationComponent", "Outer"), 500, 0, 300, 200);
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessActor", "B");
        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','figures':["
                + "{'element_id':'" + a.getId() + "','parent_figure_id':'" + g.getId() + "','x':10,'y':20},"
                + "{'element_id':'" + b.getId() + "','parent_figure_id':'" + outer.getId() + "'}]}")
                .get("results");

        IDiagramModelArchimateObject fa = ModelAccessor.findFigureById(v, r.get(0).get("figure_id").asText());
        assertSame(g, fa.eContainer());
        assertEquals(10, fa.getBounds().getX());
        assertEquals(20, fa.getBounds().getY());
        assertTrue(g.getChildren().contains(fa));
        assertFalse(v.getChildren().contains(fa));

        IDiagramModelArchimateObject fb = ModelAccessor.findFigureById(v, r.get(1).get("figure_id").asText());
        assertSame(outer, fb.eContainer());
    }

    @Test
    void reportsPerItemErrorsWithoutAddingFigures() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelNote n = models.note(v, "note", 0, 0, 100, 50);
        IArchimateElement a = models.element("BusinessActor", "A");
        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','figures':["
                + "{'element_id':'nope'},"
                + "{'element_id':'" + a.getId() + "','parent_figure_id':'missing'},"
                + "{'element_id':'" + a.getId() + "','parent_figure_id':'" + n.getId() + "'},"
                + "{'element_id':'" + a.getId() + "'}]}").get("results");

        assertTrue(r.get(0).get("error").asText().contains("Element not found: nope"));
        assertTrue(r.get(1).get("error").asText().contains("Parent figure not found on view: missing"));
        assertTrue(r.get(2).get("error").asText().contains("not a container"));
        assertFalse(r.get(2).has("figure_id"));
        assertTrue(r.get(3).has("figure_id"));
        assertEquals(2, v.getChildren().size()); // note + one new figure
    }

    @Test
    void rejectsBadRequests() {
        IArchimateDiagramModel v = models.view("V");
        Exception e = assertThrows(Exception.class,
                () -> run(tool, "{'view_id':'" + v.getId() + "','figures':[]}"));
        assertTrue(e.getMessage().contains("non-empty array"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope','figures':[{'element_id':'x'}]}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
        models.closeAll();
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'x','figures':[{'element_id':'x'}]}"));
        assertTrue(e.getMessage().contains("No model"));
    }
}

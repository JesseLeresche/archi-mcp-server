package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;

class ManageAppearanceToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ManageAppearanceTool tool = new ManageAppearanceTool();

    @Test
    void setStyleAppliesTopLevelViewIdUnlessItemOverrides() throws Exception {
        IArchimateDiagramModel v1 = models.view("V1");
        IArchimateDiagramModel v2 = models.view("V2");
        IDiagramModelArchimateObject f1 = models.figure(v1, models.element("BusinessActor", "A"), 0, 0, 120, 55);
        IDiagramModelArchimateObject f2 = models.figure(v2, models.element("BusinessActor", "B"), 0, 0, 120, 55);
        f2.setLineColor("#333333");

        JsonNode r = run(tool, "{'operation':'set_style','view_id':'" + v1.getId() + "','items':["
                + "{'figure_id':'" + f1.getId() + "','fill_color':'#abcdef','label_expression':'${name}'},"
                + "{'view_id':'" + v2.getId() + "','figure_id':'" + f2.getId() + "','line_color':''}]}")
                .get("results");
        assertEquals(f1.getId(), r.get(0).get("figure_id").asText());
        assertEquals(f2.getId(), r.get(1).get("figure_id").asText());
        assertEquals("#abcdef", f1.getFillColor());
        assertEquals("${name}", FigureStyle.getLabelExpression(f1));
        assertNull(f2.getLineColor());

        run(tool, "{'operation':'set_style','items':{'view_id':'" + v1.getId() + "','figure_id':'" + f1.getId()
                + "','fill_color':null,'label_expression':null}}");
        assertNull(f1.getFillColor());
        assertNull(FigureStyle.getLabelExpression(f1));
    }

    @Test
    void setStyleTargetsConnectionsByConnectionOrRelationshipId() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        var a = models.element("ApplicationComponent", "A");
        var b = models.element("ApplicationComponent", "B");
        var fa = models.figure(v, a, 0, 0, 120, 55);
        var fb = models.figure(v, b, 200, 0, 120, 55);
        var rel = models.relationship("ServingRelationship", a, b);
        var c = models.connection(fa, fb, rel);

        JsonNode r = run(tool, "{'operation':'set_style','view_id':'" + v.getId() + "','items':["
                + "{'relationship_id':'" + rel.getId() + "','line_color':'#ff0000','font':'1|Arial|9.0|0|COCOA|1|Arial'},"
                + "{'connection_id':'" + c.getId() + "','line_width':3},"
                + "{'connection_id':'" + c.getId() + "','fill_color':'#ffffff'},"
                + "{'connection_id':'" + c.getId() + "','x':5}]}").get("results");
        assertEquals(c.getId(), r.get(0).get("connection_id").asText());
        assertEquals("#ff0000", c.getLineColor());
        assertEquals("1|Arial|9.0|0|COCOA|1|Arial", c.getFont());
        assertEquals(3, c.getLineWidth());
        assertTrue(r.get(2).get("error").asText().contains("'fill_color' does not apply"));
        assertTrue(r.get(3).get("error").asText().contains("bounds"));
    }

    @Test
    void setStyleSupportsFullVocabularyOnFigures() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        var g = models.group(v, "G", 0, 0, 300, 200);
        JsonNode r = run(tool, "{'operation':'set_style','view_id':'" + v.getId() + "','items':"
                + "{'figure_id':'" + g.getId() + "','border_type':1,'gradient':2,'text_position':2,'font_color':'#010203'}}")
                .get("results");
        assertEquals(g.getId(), r.get(0).get("figure_id").asText());
        assertEquals(1, g.getBorderType());
        assertEquals(2, g.getGradient());
        assertEquals(2, g.getTextPosition());
        assertEquals("#010203", g.getFontColor());
    }

    @Test
    void oldOperationNameNamesItsReplacement() {
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'operation':'set_figure','items':{}}"));
        assertTrue(e.getMessage().contains("'set_figure' was replaced by 'set_style'"), e.getMessage());
    }

    @Test
    void layoutViewRoutesToLayout() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateObject f = models.figure(v, models.element("BusinessActor", "A"), 900, 900, 120, 55);
        JsonNode r = run(tool, "{'operation':'layout_view','view_id':'" + v.getId() + "'}");
        assertEquals(1, r.get("figures_repositioned").asInt());
        assertTrue(f.getBounds().getX() < 900, "figure moved back towards the origin");
    }

    @Test
    void rejectsBadRequests() {
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'operation':'frob'}"));
        assertTrue(e.getMessage().contains("Unknown operation 'frob'"));
        e = assertThrows(Exception.class, () -> run(tool, "{'items':{}}"));
        assertTrue(e.getMessage().contains("'operation' is required"));
        e = assertThrows(Exception.class, () -> run(tool, "{'operation':'set_style','items':[]}"));
        assertTrue(e.getMessage().contains("'items' must not be an empty array"));
    }
}

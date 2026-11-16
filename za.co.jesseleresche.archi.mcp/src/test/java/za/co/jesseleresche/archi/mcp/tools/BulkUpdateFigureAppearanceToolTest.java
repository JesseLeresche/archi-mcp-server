package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;

class BulkUpdateFigureAppearanceToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkUpdateFigureAppearanceTool tool = new BulkUpdateFigureAppearanceTool();

    @Test
    void stylesAndMovesFiguresByFigureOrElementId() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("BusinessActor", "A");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        JsonNode r = run(tool, "{'updates':["
                + "{'view_id':'" + v.getId() + "','element_id':'" + a.getId() + "','fill_color':'#aabbcc',"
                + "'font_color':'#010203','line_color':'#040506','opacity':128,'line_width':2,'text_alignment':1,"
                + "'label_expression':'${name}','x':30,'height':90},"
                + "{'view_id':'" + v.getId() + "','figure_id':'" + g.getId() + "','fill_color':'#ffffff'}]}")
                .get("results");

        assertEquals(fa.getId(), r.get(0).get("figure_id").asText());
        assertEquals("#aabbcc", fa.getFillColor());
        assertEquals("#010203", fa.getFontColor());
        assertEquals("#040506", fa.getLineColor());
        assertEquals(128, fa.getAlpha());
        assertEquals(2, fa.getLineWidth());
        assertEquals(1, fa.getTextAlignment());
        assertEquals("${name}", FigureStyle.getLabelExpression(fa));
        assertEquals(30, fa.getBounds().getX());
        assertEquals(0, fa.getBounds().getY());
        assertEquals(120, fa.getBounds().getWidth());
        assertEquals(90, fa.getBounds().getHeight());
        assertEquals("#ffffff", g.getFillColor());
        assertEquals(1, models.saves);
    }

    @Test
    void nullOrEmptyResetsColoursAndClearsLabel_omittedLeavesUnchanged() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateObject f = models.figure(v, models.element("BusinessActor", "A"), 0, 0, 120, 55);
        f.setFillColor("#111111");
        f.setFontColor("#222222");
        f.setLineColor("#333333");
        FigureStyle.applyLabelExpression(TestModels.json("{'label_expression':'X'}"), f);
        String target = "'view_id':'" + v.getId() + "','figure_id':'" + f.getId() + "'";

        run(tool, "{'updates':[{" + target + ",'opacity':200}]}");
        assertEquals("#111111", f.getFillColor());
        assertEquals("X", FigureStyle.getLabelExpression(f));

        run(tool, "{'updates':[{" + target + ",'fill_color':null,'font_color':'','line_color':null,"
                + "'label_expression':''}]}");
        assertNull(f.getFillColor());
        assertNull(f.getFontColor());
        assertNull(f.getLineColor());
        assertNull(FigureStyle.getLabelExpression(f));

        FigureStyle.applyLabelExpression(TestModels.json("{'label_expression':'Y'}"), f);
        run(tool, "{'updates':[{" + target + ",'label_expression':null}]}");
        assertNull(FigureStyle.getLabelExpression(f));
    }

    @Test
    void reportsPerItemErrors() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        String vid = v.getId();
        JsonNode r = run(tool, "{'updates':["
                + "{'figure_id':'x'},"
                + "{'view_id':'" + vid + "'},"
                + "{'view_id':'nope','figure_id':'x'},"
                + "{'view_id':'" + vid + "','figure_id':'x'},"
                + "{'view_id':'" + vid + "','element_id':'y'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("missing required field 'view_id'"));
        assertTrue(r.get(1).get("error").asText().contains("Either element_id or figure_id"));
        assertTrue(r.get(2).get("error").asText().contains("View not found: nope"));
        assertTrue(r.get(3).get("error").asText().contains("Figure not found on view: x"));
        assertTrue(r.get(4).get("error").asText().contains("No figure found for element on view: y"));
    }

    @Test
    void rejectsEmptyUpdates() {
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'updates':[]}"));
        assertTrue(e.getMessage().contains("'updates' must be a non-empty array"));
    }
}

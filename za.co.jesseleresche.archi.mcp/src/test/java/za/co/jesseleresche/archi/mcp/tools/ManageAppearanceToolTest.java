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
    void setFigureAppliesTopLevelViewIdUnlessItemOverrides() throws Exception {
        IArchimateDiagramModel v1 = models.view("V1");
        IArchimateDiagramModel v2 = models.view("V2");
        IDiagramModelArchimateObject f1 = models.figure(v1, models.element("BusinessActor", "A"), 0, 0, 120, 55);
        IDiagramModelArchimateObject f2 = models.figure(v2, models.element("BusinessActor", "B"), 0, 0, 120, 55);
        f2.setLineColor("#333333");

        JsonNode r = run(tool, "{'operation':'set_figure','view_id':'" + v1.getId() + "','items':["
                + "{'figure_id':'" + f1.getId() + "','fill_color':'#abcdef','label_expression':'${name}'},"
                + "{'view_id':'" + v2.getId() + "','figure_id':'" + f2.getId() + "','line_color':''}]}")
                .get("results");
        assertEquals(f1.getId(), r.get(0).get("figure_id").asText());
        assertEquals(f2.getId(), r.get(1).get("figure_id").asText());
        assertEquals("#abcdef", f1.getFillColor());
        assertEquals("${name}", FigureStyle.getLabelExpression(f1));
        assertNull(f2.getLineColor());

        run(tool, "{'operation':'set_figure','items':{'view_id':'" + v1.getId() + "','figure_id':'" + f1.getId()
                + "','fill_color':null,'label_expression':null}}");
        assertNull(f1.getFillColor());
        assertNull(FigureStyle.getLabelExpression(f1));
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
        e = assertThrows(Exception.class, () -> run(tool, "{'operation':'set_figure','items':[]}"));
        assertTrue(e.getMessage().contains("'items' must not be an empty array"));
    }
}

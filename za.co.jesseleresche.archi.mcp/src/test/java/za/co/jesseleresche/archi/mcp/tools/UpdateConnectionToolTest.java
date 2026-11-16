package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;

class UpdateConnectionToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final UpdateConnectionTool tool = new UpdateConnectionTool();

    IArchimateDiagramModel v;
    IArchimateRelationship rel;
    IDiagramModelArchimateConnection c;

    void setUp() {
        v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 300, 0, 120, 55);
        rel = models.relationship("ServingRelationship", a, b);
        c = models.connection(fa, fb, rel);
    }

    String target() {
        return "'view_id':'" + v.getId() + "','connection_id':'" + c.getId() + "'";
    }

    @Test
    void updatesVisualPropertiesAndReplacesBendpoints() throws Exception {
        setUp();
        run(tool, "{" + target() + ",'bendpoints':[{'startX':1},{'endY':9}]}");
        assertEquals(2, c.getBendpoints().size());
        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','relationship_id':'" + rel.getId() + "',"
                + "'bendpoints':[{'startX':5,'startY':6,'endX':7,'endY':8}],'line_width':3,'text_position':2,"
                + "'line_color':'#112233','font_color':'#445566','label_expression':'${name}\\nTLS'}");
        assertEquals(c.getId(), r.get("connection_id").asText());
        assertEquals(1, c.getBendpoints().size());
        assertEquals(8, c.getBendpoints().get(0).getEndY());
        assertEquals(3, c.getLineWidth());
        assertEquals(2, c.getTextPosition());
        assertEquals("#112233", c.getLineColor());
        assertEquals("#445566", c.getFontColor());
        assertEquals("${name}\nTLS", FigureStyle.getLabelExpression(c));

        run(tool, "{" + target() + ",'bendpoints':[]}");
        assertTrue(c.getBendpoints().isEmpty());
        assertEquals(3, models.saves);
    }

    @Test
    void nullOrEmptyResetsColoursAndClearsLabel_omittedLeavesUnchanged() throws Exception {
        setUp();
        c.setLineColor("#112233");
        c.setFontColor("#445566");
        c.getFeatures().putString(FigureStyle.LABEL_EXPRESSION, "X", "");

        run(tool, "{" + target() + ",'line_width':2}");
        assertEquals("#112233", c.getLineColor());
        assertEquals("X", FigureStyle.getLabelExpression(c));

        run(tool, "{" + target() + ",'line_color':null,'font_color':'','label_expression':null}");
        assertNull(c.getLineColor());
        assertNull(c.getFontColor());
        assertNull(FigureStyle.getLabelExpression(c));

        c.getFeatures().putString(FigureStyle.LABEL_EXPRESSION, "Y", "");
        run(tool, "{" + target() + ",'label_expression':''}");
        assertNull(FigureStyle.getLabelExpression(c));
    }

    @Test
    void rejectsBadRequests() {
        setUp();
        String vid = v.getId();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("Either connection_id or relationship_id"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope','connection_id':'x'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','connection_id':'x'}"));
        assertTrue(e.getMessage().contains("Connection not found on view: x"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','relationship_id':'y'}"));
        assertTrue(e.getMessage().contains("No connection found for relationship on view: y"));
    }
}

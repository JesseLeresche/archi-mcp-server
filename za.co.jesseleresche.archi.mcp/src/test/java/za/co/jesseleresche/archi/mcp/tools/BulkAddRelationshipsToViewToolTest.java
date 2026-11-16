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
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class BulkAddRelationshipsToViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final BulkAddRelationshipsToViewTool tool = new BulkAddRelationshipsToViewTool();

    @Test
    void drawsConnectionWithStyleAndReportsExisting() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 300, 0, 120, 55);
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        String args = "{'view_id':'" + v.getId() + "','connections':[{'relationship_id':'" + rel.getId() + "',"
                + "'bendpoints':[{'startX':1,'startY':2,'endX':3,'endY':4}],'text_position':0,"
                + "'line_color':'#ff0000','font_color':'#00ff00','label_expression':'${name}'}]}";

        JsonNode r = run(tool, args).get("results").get(0);
        IDiagramModelArchimateConnection c = ModelAccessor.findConnectionById(v, r.get("connection_id").asText());
        assertSame(rel, c.getArchimateRelationship());
        assertSame(fa, c.getSource());
        assertSame(fb, c.getTarget());
        assertEquals(1, c.getBendpoints().size());
        assertEquals(3, c.getBendpoints().get(0).getEndX());
        assertEquals(0, c.getTextPosition());
        assertEquals("#ff0000", c.getLineColor());
        assertEquals("#00ff00", c.getFontColor());
        assertEquals("${name}", FigureStyle.getLabelExpression(c));
        assertEquals(1, models.saves);

        JsonNode again = run(tool, args).get("results").get(0);
        assertTrue(again.get("already_exists").asBoolean());
        assertEquals(c.getId(), again.get("connection_id").asText());
        assertEquals(1, fa.getSourceConnections().size());
    }

    @Test
    void usesExplicitFigureIds() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fa2 = models.figure(v, a, 0, 200, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 300, 0, 120, 55);
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','connections':[{'relationship_id':'" + rel.getId()
                + "','source_figure_id':'" + fa2.getId() + "','target_figure_id':'" + fb.getId() + "'}]}");
        IDiagramModelArchimateConnection c =
                ModelAccessor.findConnectionById(v, r.get("results").get(0).get("connection_id").asText());
        assertSame(fa2, c.getSource());
    }

    @Test
    void reportsPerItemErrors() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "Alpha");
        IArchimateElement b = models.element("ApplicationComponent", "Beta");
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','connections':["
                + "{'relationship_id':'nope'},{'relationship_id':'" + rel.getId() + "'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("Relationship not found: nope"));
        assertTrue(r.get(1).get("error").asText().contains("Source element 'Alpha' not found on view"));

        models.figure(v, a, 0, 0, 120, 55);
        r = run(tool, "{'view_id':'" + v.getId() + "','connections':[{'relationship_id':'" + rel.getId() + "'}]}")
                .get("results");
        assertTrue(r.get(0).get("error").asText().contains("Target element 'Beta' not found on view"));
    }

    @Test
    void rejectsBadRequests() {
        IArchimateDiagramModel v = models.view("V");
        Exception e = assertThrows(Exception.class,
                () -> run(tool, "{'view_id':'" + v.getId() + "','connections':[]}"));
        assertTrue(e.getMessage().contains("non-empty array"));
        e = assertThrows(Exception.class,
                () -> run(tool, "{'view_id':'nope','connections':[{'relationship_id':'x'}]}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
    }
}

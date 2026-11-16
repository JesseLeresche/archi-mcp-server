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
import com.archimatetool.model.IDiagramModelGroup;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class ManageViewContentToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ManageViewContentTool tool = new ManageViewContentTool();

    @Test
    void addElementNestsIntoGroupViaParentFigureId_singleObjectItems() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IArchimateElement a = models.element("BusinessActor", "A");
        JsonNode r = run(tool, "{'operation':'add_element','view_id':'" + v.getId() + "','items':"
                + "{'element_id':'" + a.getId() + "','parent_figure_id':'" + g.getId() + "','x':15,'y':25}}")
                .get("results");
        assertEquals(1, r.size());
        IDiagramModelArchimateObject f = ModelAccessor.findFigureById(v, r.get(0).get("figure_id").asText());
        assertSame(g, f.eContainer());
        assertEquals(15, f.getBounds().getX());
        assertEquals(25, f.getBounds().getY());
    }

    @Test
    void addRelationshipUpdateAndDeleteConnection() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        models.figure(v, a, 0, 0, 120, 55);
        models.figure(v, b, 300, 0, 120, 55);
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        String vid = "'view_id':'" + v.getId() + "'";

        // stringified items payload is accepted too
        JsonNode r = run(tool, "{'operation':'add_relationship'," + vid + ",'items':'[{\"relationship_id\":\""
                + rel.getId() + "\",\"line_color\":\"#ff0000\"}]'}").get("results");
        IDiagramModelArchimateConnection c = ModelAccessor.findConnectionById(v, r.get(0).get("connection_id").asText());
        assertEquals("#ff0000", c.getLineColor());

        r = run(tool, "{'operation':'update_connection'," + vid + ",'items':[{'connection_id':'" + c.getId()
                + "','line_color':null,'label_expression':'${name}'},{'connection_id':'nope'}]}").get("results");
        assertEquals(c.getId(), r.get(0).get("connection_id").asText());
        assertTrue(r.get(1).get("error").asText().contains("Connection not found on view: nope"));
        assertNull(c.getLineColor());
        assertEquals("${name}", FigureStyle.getLabelExpression(c));

        run(tool, "{'operation':'update_connection'," + vid + ",'items':{'relationship_id':'" + rel.getId()
                + "','label_expression':''}}");
        assertNull(FigureStyle.getLabelExpression(c));

        run(tool, "{'operation':'delete_connection'," + vid + ",'items':{'relationship_id':'" + rel.getId() + "'}}");
        assertNull(ModelAccessor.findConnectionByRelationshipId(v, rel.getId()));
        assertNotNull(ModelAccessor.findRelationshipById(models.model, rel.getId()));
    }

    @Test
    void removeFigureCollectsPerItemResults() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("BusinessActor", "A");
        IDiagramModelArchimateObject f = models.figure(v, a, 0, 0, 120, 55);
        JsonNode r = run(tool, "{'operation':'remove_figure','view_id':'" + v.getId() + "','items':["
                + "{'figure_id':'" + f.getId() + "'},{'element_id':'" + a.getId() + "'}]}").get("results");
        assertEquals(2, r.size());
        assertFalse(r.get(0).has("error"));
        assertTrue(r.get(1).get("error").asText().contains("No figure found for element on view"));
        assertTrue(v.getChildren().isEmpty());
    }

    @Test
    void rejectsBadRequests() {
        String vid = models.view("V").getId();
        Exception e = assertThrows(Exception.class,
                () -> run(tool, "{'operation':'frob','view_id':'" + vid + "','items':{}}"));
        assertTrue(e.getMessage().contains("Unknown operation 'frob'"));
        e = assertThrows(Exception.class, () -> run(tool, "{'operation':'add_element','items':{}}"));
        assertTrue(e.getMessage().contains("'view_id' is required"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','items':{}}"));
        assertTrue(e.getMessage().contains("'operation' is required"));
        e = assertThrows(Exception.class, () -> run(tool, "{'operation':'add_element','view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("'items' is required"));
    }
}

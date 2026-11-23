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
        JsonNode r = run(tool, "{'operation':'add_figure','view_id':'" + v.getId() + "','items':"
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
        JsonNode r = run(tool, "{'operation':'add_connection'," + vid + ",'items':'[{\"relationship_id\":\""
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

        run(tool, "{'operation':'remove_connection'," + vid + ",'items':{'relationship_id':'" + rel.getId() + "'}}");
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
        e = assertThrows(Exception.class, () -> run(tool, "{'operation':'add_figure','items':{}}"));
        assertTrue(e.getMessage().contains("'view_id' is required"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','items':{}}"));
        assertTrue(e.getMessage().contains("'operation' is required"));
        e = assertThrows(Exception.class, () -> run(tool, "{'operation':'add_figure','view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("'items' is required"));
    }

    @Test
    void addFigureCreatesGroupsNotesAndNestedElementsWithStyle() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement e = models.element("ApplicationComponent", "App");
        String font = "1|Arial|11.0|1|COCOA|1|Arial-Bold";
        JsonNode r = run(tool, "{'operation':'add_figure','view_id':'" + v.getId() + "','items':["
                + "{'kind':'group','figure_id':'g-1','name':'Zone','documentation':'d','x':10,'y':10,"
                + "'width':500,'height':300,'fill_color':'#eeeeee','font':'" + font + "','border_type':1},"
                + "{'kind':'note','figure_id':'n-1','parent_figure_id':'g-1','content':'Hello','x':5,'y':5,"
                + "'font_color':'#ff0000','text_alignment':1},"
                + "{'element_id':'" + e.getId() + "','parent_figure_id':'g-1','x':200,'y':40,"
                + "'line_color':'#00ff00','font':'" + font + "'}]}").get("results");

        assertEquals("g-1", r.get(0).get("figure_id").asText());
        assertEquals("group", r.get(0).get("kind").asText());
        IDiagramModelGroup g = (IDiagramModelGroup) ModelAccessor.findDiagramObjectById(v, "g-1");
        assertEquals("Zone", g.getName());
        assertEquals("d", g.getDocumentation());
        assertEquals(500, g.getBounds().getWidth());
        assertEquals("#eeeeee", g.getFillColor());
        assertEquals(font, g.getFont());
        assertEquals(1, g.getBorderType());
        assertSame(v, g.eContainer());

        var n = (com.archimatetool.model.IDiagramModelNote) ModelAccessor.findDiagramObjectById(v, "n-1");
        assertSame(g, n.eContainer());
        assertEquals("Hello", n.getContent());
        assertEquals("#ff0000", n.getFontColor());
        assertEquals(1, n.getTextAlignment());

        IDiagramModelArchimateObject f = ModelAccessor.findFigureByElementId(v, e.getId());
        assertSame(g, f.eContainer());
        assertEquals(r.get(2).get("figure_id").asText(), f.getId());
        assertEquals("#00ff00", f.getLineColor());
        assertEquals(font, f.getFont());
    }

    @Test
    void addFigurePerItemErrorsAndIdConflicts() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement e = models.element("ApplicationComponent", "App");
        String vid = v.getId();
        run(tool, "{'operation':'add_figure','view_id':'" + vid + "','items':{'kind':'note','figure_id':'n-1'}}");

        JsonNode r = run(tool, "{'operation':'add_figure','view_id':'" + vid + "','items':["
                + "{'kind':'blob'},"
                + "{'kind':'note','figure_id':'n-1'},"
                + "{'kind':'note','figure_id':'n-1','on_conflict':'skip'},"
                + "{'kind':'note','figure_id':'   '},"
                + "{'kind':'group','border_type':7,'figure_type':1},"
                + "{'element_id':'" + e.getId() + "','border_type':1},"
                + "{'kind':'note','figure_id':'" + e.getId() + "'}]}").get("results");
        assertTrue(r.get(0).get("error").asText().contains("kind must be one of element, group, note"));
        assertTrue(r.get(1).get("error").asText().contains("ID already exists in the model: n-1"));
        assertTrue(r.get(2).get("skipped").asBoolean());
        assertEquals("n-1", r.get(2).get("figure_id").asText());
        assertTrue(r.get(3).get("error").asText().contains("'figure_id' must not be empty"));
        assertTrue(r.get(4).get("error").asText().contains("'figure_type' does not apply"));
        assertTrue(r.get(5).get("error").asText().contains("'border_type' does not apply"));
        assertTrue(r.get(6).get("error").asText().contains("ID already exists in the model: " + e.getId()));
        assertEquals(1, v.getChildren().size(), "only the first note exists");
    }

    @Test
    void addConnectionResolvesEndpointsByFigureIdAndRemoveConnectionKeepsRelationship() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        var g = models.group(v, "G", 0, 0, 400, 300);
        var fa = models.figure(g, a, 10, 10, 120, 55);
        var fb = models.figure(v, b, 500, 0, 120, 55);
        var note = models.note(v, "n", 0, 400, 50, 50);
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        String vid = v.getId();

        JsonNode r = run(tool, "{'operation':'add_connection','view_id':'" + vid + "','items':["
                + "{'relationship_id':'" + rel.getId() + "','source_figure_id':'" + note.getId() + "'},"
                + "{'relationship_id':'" + rel.getId() + "','source_figure_id':'" + fb.getId() + "'},"
                + "{'relationship_id':'" + rel.getId() + "','source_figure_id':'" + fa.getId() + "',"
                + "'target_figure_id':'" + fb.getId() + "','font':'1|Arial|9.0|0|COCOA|1|Arial','name_visible':false}]}")
                .get("results");
        assertTrue(r.get(0).get("error").asText().contains("is not an element figure"), r.toString());
        assertTrue(r.get(1).get("error").asText().contains("does not show"), r.toString());
        IDiagramModelArchimateConnection c = ModelAccessor.findConnectionByRelationshipId(v, rel.getId());
        assertEquals(r.get(2).get("connection_id").asText(), c.getId());
        assertSame(fa, c.getSource());
        assertFalse(c.isNameVisible());

        run(tool, "{'operation':'remove_connection','view_id':'" + vid + "','items':{'relationship_id':'" + rel.getId() + "'}}");
        assertNull(ModelAccessor.findConnectionByRelationshipId(v, rel.getId()));
        assertNotNull(ModelAccessor.findRelationshipById(models.model, rel.getId()));
    }

    @Test
    void oldOperationNamesNameTheirReplacements() {
        String vid = models.view("V").getId();
        for (String[] p : new String[][] {{"add_element", "add_figure"}, {"add_relationship", "add_connection"},
                {"delete_connection", "remove_connection"}}) {
            Exception e = assertThrows(Exception.class,
                    () -> run(tool, "{'operation':'" + p[0] + "','view_id':'" + vid + "','items':{}}"));
            assertTrue(e.getMessage().contains("'" + p[0] + "' was replaced by '" + p[1] + "'"), e.getMessage());
        }
    }

    // --- multiple drawings per view (multiple-connections spec) ---

    IArchimateDiagramModel v;
    IDiagramModelArchimateObject e1, e2, f;
    IArchimateRelationship rel;

    /** View V: element E shown as e1 and e2, element F as f, relationship R from E to F (undrawn). */
    void shared() {
        v = models.view("V");
        IArchimateElement e = models.element("ApplicationComponent", "E");
        IArchimateElement fe = models.element("ApplicationComponent", "F");
        e1 = models.figure(v, e, 0, 0, 120, 55);
        e2 = models.figure(v, e, 0, 200, 120, 55);
        f = models.figure(v, fe, 300, 0, 120, 55);
        rel = models.relationship("ServingRelationship", e, fe);
    }

    String item(IDiagramModelArchimateObject src, String extra) {
        return "{'relationship_id':'" + rel.getId() + "','source_figure_id':'" + src.getId()
                + "','target_figure_id':'" + f.getId() + "'" + extra + "}";
    }

    /** S13 / FR-5. */
    @Test
    void summaryCountsReusedAndSkippedConnections() throws Exception {
        shared();
        IDiagramModelArchimateConnection c1 = models.connection(e1, f, rel);
        JsonNode sum = run(tool, "{'operation':'add_connection','view_id':'" + v.getId() + "','results':'summary',"
                + "'items':[" + item(e2, "") + "," + item(e1, ",'on_existing':'reuse'") + ","
                + item(e1, ",'on_existing':'add','connection_id':'" + c1.getId() + "','on_conflict':'skip'") + ","
                + "{'relationship_id':'nope'}]}");
        assertEquals(4, sum.get("count").asInt(), sum.toString());
        assertEquals(1, sum.get("succeeded").asInt(), sum.toString());
        assertEquals(1, sum.path("reused").asInt(), sum.toString());
        assertEquals(1, sum.path("skipped").asInt(), sum.toString());
        assertEquals(1, sum.get("failed").asInt(), sum.toString());
        assertEquals(3, sum.get("errors").get(0).get("index").asInt(), sum.toString());
        assertEquals(2, ModelAccessor.findConnectionsByRelationshipId(v, rel.getId()).size());
    }

    /** S14 / FR-6. */
    @Test
    void updateByRelationshipIdRejectsAmbiguous() throws Exception {
        shared();
        IDiagramModelArchimateConnection c1 = models.connection(e1, f, rel);
        IDiagramModelArchimateConnection c2 = models.connection(e2, f, rel);
        JsonNode r = run(tool, "{'operation':'update_connection','view_id':'" + v.getId() + "','items':"
                + "{'relationship_id':'" + rel.getId() + "','line_color':'#ff0000'}}").get("results").get(0);
        TestModels.assertMentions(r.path("error").asText(null), rel.getId(), "2 connections", c1.getId(), c2.getId(),
                "pass connection_id");
        assertNull(c1.getLineColor());
        assertNull(c2.getLineColor());
    }

    /** S14 / FR-6. */
    @Test
    void removeByRelationshipIdRejectsAmbiguous() throws Exception {
        shared();
        IDiagramModelArchimateConnection c1 = models.connection(e1, f, rel);
        IDiagramModelArchimateConnection c2 = models.connection(e2, f, rel);
        JsonNode r = run(tool, "{'operation':'remove_connection','view_id':'" + v.getId() + "','items':"
                + "{'relationship_id':'" + rel.getId() + "'}}").get("results").get(0);
        TestModels.assertMentions(r.path("error").asText(null), rel.getId(), "2 connections", c1.getId(), c2.getId(),
                "pass connection_id");
        assertEquals(2, ModelAccessor.findConnectionsByRelationshipId(v, rel.getId()).size());
    }

    /** S18 / FR-8. */
    @Test
    void removeFigureByElementIdRejectsAmbiguous() throws Exception {
        shared();
        String eid = e1.getArchimateElement().getId();
        JsonNode r = run(tool, "{'operation':'remove_figure','view_id':'" + v.getId() + "','items':"
                + "{'element_id':'" + eid + "'}}").get("results").get(0);
        TestModels.assertMentions(r.path("error").asText(null), eid, "2 figures", e1.getId(), e2.getId(),
                "pass figure_id");
        assertNotNull(ModelAccessor.findFigureById(v, e1.getId()));
        assertNotNull(ModelAccessor.findFigureById(v, e2.getId()));
    }
}

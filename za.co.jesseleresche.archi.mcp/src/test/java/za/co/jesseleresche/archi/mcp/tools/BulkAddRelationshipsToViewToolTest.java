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

        // FR-3: a plain re-run now fails instead of reporting already_exists
        JsonNode again = run(tool, args).get("results").get(0);
        TestModels.assertMentions(again.path("error").asText(null), c.getId(), "on_existing");
        assertEquals(1, fa.getSourceConnections().size());

        JsonNode reused = run(tool, args.replace("'label_expression'", "'on_existing':'reuse','label_expression'"))
                .get("results").get(0);
        assertTrue(reused.path("reused").asBoolean(), reused.toString());
        assertFalse(reused.has("already_exists"));
        assertEquals(c.getId(), reused.get("connection_id").asText());
        assertEquals(1, fa.getSourceConnections().size());
    }

    // --- multiple drawings of one relationship (multiple-connections spec S1-S9, S12) ---

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

    JsonNode add(String itemFields) throws Exception {
        return run(tool, "{'view_id':'" + v.getId() + "','connections':[{'relationship_id':'" + rel.getId() + "'"
                + itemFields + "}]}").get("results").get(0);
    }

    String figs(IDiagramModelArchimateObject src, IDiagramModelArchimateObject tgt) {
        return ",'source_figure_id':'" + src.getId() + "','target_figure_id':'" + tgt.getId() + "'";
    }

    int drawings() {
        return ModelAccessor.findConnectionsByRelationshipId(v, rel.getId()).size();
    }

    /** S1 / FR-1. */
    @Test
    void drawsSameRelationshipFromSecondFigure() throws Exception {
        shared();
        IDiagramModelArchimateConnection c1 = models.connection(e1, f, rel);
        JsonNode r = add(figs(e2, f));
        assertFalse(r.has("error"), r.toString());
        assertFalse(r.has("reused"), r.toString());
        assertNotEquals(c1.getId(), r.get("connection_id").asText());
        assertSame(e2, ModelAccessor.findConnectionById(v, r.get("connection_id").asText()).getSource());
        assertEquals(2, drawings());
    }

    /** S2 / FR-3. */
    @Test
    void samePairWithoutOnExistingFails() throws Exception {
        shared();
        IDiagramModelArchimateConnection c1 = models.connection(e1, f, rel);
        JsonNode r = add(figs(e1, f));
        assertEquals("Relationship " + rel.getId() + " is already drawn as connection " + c1.getId()
                + " between these figures; pass on_existing: \"reuse\" or \"add\"", r.path("error").asText(null));
        assertEquals(1, drawings());
    }

    /** S3 / FR-3. */
    @Test
    void onExistingReuseReturnsExisting() throws Exception {
        shared();
        IDiagramModelArchimateConnection c1 = models.connection(e1, f, rel);
        JsonNode r = add(figs(e1, f) + ",'on_existing':'reuse','connection_id':'c-new','line_color':'#ff0000'");
        assertFalse(r.has("error"), r.toString());
        assertEquals(c1.getId(), r.get("connection_id").asText());
        assertTrue(r.path("reused").asBoolean(), r.toString());
        assertFalse(r.has("already_exists"));
        assertEquals(1, drawings());
        assertNull(c1.getLineColor(), "reuse ignores the item's style");
    }

    /** S4 / FR-3. */
    @Test
    void onExistingAddDrawsAnotherLine() throws Exception {
        shared();
        IDiagramModelArchimateConnection c1 = models.connection(e1, f, rel);
        JsonNode r = add(figs(e1, f) + ",'on_existing':'add'");
        assertFalse(r.has("error"), r.toString());
        assertNotEquals(c1.getId(), r.get("connection_id").asText());
        assertEquals(2, drawings());
        for (IDiagramModelArchimateConnection c : ModelAccessor.findConnectionsByRelationshipId(v, rel.getId())) {
            assertSame(e1, c.getSource());
            assertSame(f, c.getTarget());
        }
    }

    /** S5 / FR-1. */
    @Test
    void omittedFiguresMatchAnyExistingDrawing() throws Exception {
        shared();
        IDiagramModelArchimateConnection c2 = models.connection(e2, f, rel);
        JsonNode r = add("");
        TestModels.assertMentions(r.path("error").asText(null), "already drawn as connection " + c2.getId());
        assertEquals(1, drawings());

        r = add(",'on_existing':'reuse'");
        assertEquals(c2.getId(), r.path("connection_id").asText(null), r.toString());
        assertTrue(r.path("reused").asBoolean(), r.toString());
        assertEquals(1, drawings());
    }

    /** S6 / FR-1, FR-4. */
    @Test
    void oneEndGivenMatchesOnThatEndOnly() throws Exception {
        shared();
        models.connection(e1, f, rel);
        JsonNode r = add(",'source_figure_id':'" + e2.getId() + "'");
        assertFalse(r.has("error"), r.toString());
        IDiagramModelArchimateConnection c = ModelAccessor.findConnectionById(v, r.get("connection_id").asText());
        assertSame(e2, c.getSource());
        assertSame(f, c.getTarget());
        assertEquals(2, drawings());
    }

    /** S7 / FR-4, FR-8. */
    @Test
    void omittedEndWithTwoFiguresFails() throws Exception {
        shared();
        JsonNode r = add(",'target_figure_id':'" + f.getId() + "'");
        TestModels.assertMentions(r.path("error").asText(null), rel.getSource().getId(), "2 figures",
                e1.getId(), e2.getId(), "source_figure_id");
        assertEquals(0, drawings());
    }

    /** S8 / FR-4. */
    @Test
    void createsWithUnusedConnectionIdForNewFigurePair() throws Exception {
        shared();
        models.connection(e1, f, rel);
        JsonNode r = add(figs(e2, f) + ",'connection_id':'c-2'");
        assertEquals("c-2", r.path("connection_id").asText(null), r.toString());
        assertFalse(r.has("skipped"), r.toString());
        assertSame(e2, ModelAccessor.findConnectionById(v, "c-2").getSource());
    }

    /** S9 / FR-2. */
    @Test
    void rejectsUnknownOnExisting() throws Exception {
        shared();
        JsonNode r = add(figs(e1, f) + ",'on_existing':'replace'");
        assertEquals("on_existing must be 'reuse' or 'add'", r.path("error").asText(null));
        assertEquals(0, drawings());
    }

    /** S12 / FR-3, FR-11. */
    @Test
    void rerunWithReuseIsIdempotent() throws Exception {
        shared();
        String batch = "{'view_id':'" + v.getId() + "','connections':["
                + "{'relationship_id':'" + rel.getId() + "'" + figs(e1, f) + ",'on_existing':'reuse'},"
                + "{'relationship_id':'" + rel.getId() + "'" + figs(e2, f) + ",'on_existing':'reuse'}]}";
        JsonNode first = run(tool, batch).get("results");
        assertEquals(2, drawings());
        JsonNode second = run(tool, batch).get("results");
        assertEquals(2, drawings());
        for (int i = 0; i < 2; i++) {
            assertTrue(second.get(i).path("reused").asBoolean(), second.get(i).toString());
            assertEquals(first.get(i).get("connection_id").asText(), second.get(i).get("connection_id").asText());
        }
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

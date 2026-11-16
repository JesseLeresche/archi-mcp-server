package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.FolderType;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

/**
 * Appearance copied today: figure bounds, fill/line/font colour, alpha, text alignment; group
 * name/documentation; note content; connection bendpoints, line colour/width, font colour,
 * text position. Not copied: font, gradient, line alpha, icon colour, figure text position,
 * label expressions, properties.
 */
class DuplicateViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final DuplicateViewTool tool = new DuplicateViewTool();

    IArchimateElement a, b;
    IArchimateRelationship rel;

    IArchimateDiagramModel source() {
        a = models.element("ApplicationComponent", "A");
        b = models.element("ApplicationComponent", "B");
        rel = models.relationship("ServingRelationship", a, b);
        IArchimateDiagramModel v = models.view("Src");
        v.setDocumentation("src doc");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 300, 300);
        g.setDocumentation("group doc");
        IDiagramModelArchimateObject fa = models.figure(g, a, 10, 20, 120, 55);
        fa.setFillColor("#ff0000");
        fa.setLineColor("#00ff00");
        fa.setFontColor("#0000ff");
        fa.setAlpha(100);
        fa.setTextAlignment(1);
        IDiagramModelArchimateObject fb = models.figure(v, b, 400, 10, 130, 60);
        models.note(v, "hello", 0, 400, 200, 80);
        IDiagramModelArchimateConnection c = models.connection(fa, fb, rel);
        c.setLineColor("#123456");
        c.setLineWidth(3);
        c.setTextPosition(0);
        IDiagramModelBendpoint bp = IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bp.setStartX(1);
        bp.setStartY(2);
        bp.setEndX(3);
        bp.setEndY(4);
        c.getBendpoints().add(bp);
        return v;
    }

    @Test
    void clonesFiguresNestingConnectionsAndAppearance() throws Exception {
        IArchimateDiagramModel src = source();
        JsonNode r = run(tool, "{'source_view_id':'" + src.getId() + "','new_name':'Copy'}");
        assertEquals(4, r.get("figure_count").asInt());
        assertEquals(1, r.get("connection_count").asInt());
        IArchimateDiagramModel copy = ModelAccessor.findViewById(models.model, r.get("new_view_id").asText());
        assertNotSame(src, copy);
        assertEquals("Copy", copy.getName());
        assertEquals("src doc", copy.getDocumentation());
        assertSame(src.eContainer(), copy.eContainer());
        assertEquals(((IFolder) copy.eContainer()).getId(), r.get("folder_id").asText());

        assertEquals(3, copy.getChildren().size());
        IDiagramModelGroup g = (IDiagramModelGroup) copy.getChildren().get(0);
        assertEquals("G", g.getName());
        assertEquals("group doc", g.getDocumentation());
        IDiagramModelArchimateObject fa = (IDiagramModelArchimateObject) g.getChildren().get(0);
        assertSame(a, fa.getArchimateElement());
        assertEquals(10, fa.getBounds().getX());
        assertEquals(20, fa.getBounds().getY());
        assertEquals(120, fa.getBounds().getWidth());
        assertEquals("#ff0000", fa.getFillColor());
        assertEquals("#00ff00", fa.getLineColor());
        assertEquals("#0000ff", fa.getFontColor());
        assertEquals(100, fa.getAlpha());
        assertEquals(1, fa.getTextAlignment());
        assertEquals("hello", ((IDiagramModelNote) copy.getChildren().get(2)).getContent());

        IDiagramModelArchimateConnection c = (IDiagramModelArchimateConnection) fa.getSourceConnections().get(0);
        assertSame(rel, c.getArchimateRelationship());
        assertSame(copy.getChildren().get(1), c.getTarget());
        assertEquals("#123456", c.getLineColor());
        assertEquals(3, c.getLineWidth());
        assertEquals(0, c.getTextPosition());
        assertEquals(1, c.getBendpoints().size());
        assertEquals(4, c.getBendpoints().get(0).getEndY());
        // source untouched
        assertEquals(1, ((IDiagramModelArchimateObject) src.getChildren().get(1)).getTargetConnections().size());
        assertEquals(1, models.saves);
    }

    @Test
    void copiesFullStyleIncludingFontLineWidthAndLabels() throws Exception {
        IArchimateDiagramModel src = models.view("Src");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(src, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(src, b, 200, 0, 120, 55);
        IDiagramModelArchimateConnection c = models.connection(fa, fb, models.relationship("ServingRelationship", a, b));
        FigureStyle.apply(TestModels.json("{'font':'1|Arial|12.0|1|COCOA|1|Arial-Bold','line_width':3,"
                + "'gradient':1,'label_expression':'${name}!'}"), fa);
        FigureStyle.apply(TestModels.json("{'font':'1|Arial|8.0|0|COCOA|1|Arial','line_width':2,"
                + "'name_visible':false,'label_expression':'${type}'}"), c);

        JsonNode r = run(tool, "{'source_view_id':'" + src.getId() + "','new_name':'Copy'}");
        IArchimateDiagramModel copy = ModelAccessor.findViewById(models.model, r.get("new_view_id").asText());
        IDiagramModelArchimateObject ca = ModelAccessor.findFigureByElementId(copy, a.getId());
        assertEquals(FigureStyle.read(fa), FigureStyle.read(ca));
        assertEquals(FigureStyle.read(c),
                FigureStyle.read(ca.getSourceConnections().get(0)));
    }

    @Test
    void placesIntoTargetViewsSubfolder() throws Exception {
        IArchimateDiagramModel src = source();
        IFolder sub = IArchimateFactory.eINSTANCE.createFolder();
        models.model.getFolder(FolderType.DIAGRAMS).getFolders().add(sub);
        JsonNode r = run(tool, "{'source_view_id':'" + src.getId() + "','new_name':'C','target_folder_id':'"
                + sub.getId() + "'}");
        assertEquals(sub.getId(), r.get("folder_id").asText());
        assertEquals(1, sub.getElements().size());
    }

    @Test
    void rejectsNonViewsTargetFolder() {
        IArchimateDiagramModel src = source();
        IFolder business = models.model.getFolder(FolderType.BUSINESS);
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'source_view_id':'" + src.getId()
                + "','new_name':'C','target_folder_id':'" + business.getId() + "'}"));
        assertTrue(ex.getMessage().contains("must be placed under the 'Views' section"), ex.getMessage());
        assertTrue(business.getElements().isEmpty());
    }

    @Test
    void rejectsUnknownSourceAndFolder() {
        Exception ex = assertThrows(Exception.class,
                () -> run(tool, "{'source_view_id':'nope','new_name':'C'}"));
        assertEquals("Source view not found: nope", ex.getMessage());
        IArchimateDiagramModel src = models.view("S");
        ex = assertThrows(Exception.class, () -> run(tool, "{'source_view_id':'" + src.getId()
                + "','new_name':'C','target_folder_id':'nope'}"));
        assertEquals("Target folder not found: nope", ex.getMessage());
    }
}

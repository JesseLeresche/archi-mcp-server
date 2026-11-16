package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelGroup;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class LayoutViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final LayoutViewTool tool = new LayoutViewTool();

    @Test
    void laysOutHierarchicallyAndClearsBendpoints() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(v, a, 500, 500, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 500, 500, 120, 55);
        IDiagramModelArchimateConnection c =
                models.connection(fa, fb, models.relationship("ServingRelationship", a, b));
        c.getBendpoints().add(IArchimateFactory.eINSTANCE.createDiagramModelBendpoint());

        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "'}");

        assertEquals(2, r.get("figures_repositioned").asInt());
        assertEquals("hierarchical", r.get("algorithm").asText());
        assertTrue(c.getBendpoints().isEmpty());
        // Source is ranked above target; both start at the view-level offset.
        assertTrue(fa.getBounds().getY() < fb.getBounds().getY());
        assertTrue(fa.getBounds().getX() >= 60 && fa.getBounds().getY() >= 60);
        assertEquals(1, models.saves);
    }

    @Test
    void laysOutNestedChildrenAndResizesContainer() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 50, 50);
        IDiagramModelArchimateObject f1 = models.figure(g, models.element("BusinessActor", "1"), 0, 0, 120, 55);
        IDiagramModelArchimateObject f2 = models.figure(g, models.element("BusinessActor", "2"), 0, 0, 120, 55);

        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','spacing':20}");

        assertEquals(3, r.get("figures_repositioned").asInt());
        assertSame(g, f1.eContainer());
        int right = Math.max(f1.getBounds().getX() + 120, f2.getBounds().getX() + 120);
        int bottom = Math.max(f1.getBounds().getY() + 55, f2.getBounds().getY() + 55);
        assertTrue(g.getBounds().getWidth() > right, "group widened to fit children");
        assertTrue(g.getBounds().getHeight() > bottom, "group heightened to fit children");
        assertFalse(f1.getBounds().getX() == f2.getBounds().getX()
                && f1.getBounds().getY() == f2.getBounds().getY(), "children no longer overlap");
    }

    @Test
    void rejectsUnknownView() {
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
    }
}

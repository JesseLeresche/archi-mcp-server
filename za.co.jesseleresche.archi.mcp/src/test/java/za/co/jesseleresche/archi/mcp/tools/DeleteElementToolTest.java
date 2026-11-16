package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class DeleteElementToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final DeleteElementTool tool = new DeleteElementTool();

    IArchimateElement a, b;
    IArchimateRelationship rel;
    IArchimateDiagramModel view;
    IDiagramModelArchimateObject fa, fb;

    @BeforeEach
    void setUp() {
        a = models.element("BusinessActor", "A");
        b = models.element("BusinessRole", "B");
        rel = models.relationship("AssignmentRelationship", a, b);
        view = models.view("V");
        fa = models.figure(view, a, 0, 0, 120, 55);
        fb = models.figure(view, b, 200, 0, 120, 55);
        models.connection(fa, fb, rel);
    }

    @Test
    void dryRunReportsImpactWithoutChanges() throws Exception {
        JsonNode r = run(tool, "{'element_id':'" + a.getId() + "','dry_run':true}");
        assertEquals(1, r.get("relationships_to_delete").asInt());
        assertEquals(1, r.get("figures_to_remove").asInt());
        assertEquals(1, r.get("connections_to_remove").asInt());
        assertEquals(view.getId(), r.get("affected_view_ids").get(0).asText());
        assertNotNull(ModelAccessor.findElementById(models.model, a.getId()));
        assertNotNull(rel.eContainer());
        assertEquals(2, view.getChildren().size());
        assertEquals(0, models.saves);
    }

    @Test
    void deletesElementRelationshipsFiguresAndConnections() throws Exception {
        JsonNode r = run(tool, "{'element_id':'" + a.getId() + "'}");
        assertEquals(1, r.get("relationships_deleted").asInt());
        assertEquals(1, r.get("figures_removed").asInt());
        assertNull(ModelAccessor.findElementById(models.model, a.getId()));
        assertNull(rel.eContainer());
        assertEquals(1, view.getChildren().size());
        assertSame(fb, view.getChildren().get(0));
        assertTrue(fb.getTargetConnections().isEmpty());
        assertNotNull(ModelAccessor.findElementById(models.model, b.getId()));
        assertEquals(1, models.saves);
    }

    @Test
    void unknownElementFails() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'element_id':'ghost'}"));
        assertEquals("Element not found: ghost", ex.getMessage());
    }
}

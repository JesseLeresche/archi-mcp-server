package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelGroup;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class DeleteViewToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final DeleteViewTool tool = new DeleteViewTool();

    @Test
    void deletesViewButKeepsModelElements() throws Exception {
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        IArchimateDiagramModel v = models.view("Doomed");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 300, 300);
        models.connection(models.figure(g, a, 10, 10, 120, 55), models.figure(v, b, 400, 10, 120, 55), rel);

        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "'}");
        assertEquals(v.getId(), r.get("view_id").asText());
        assertEquals("Doomed", r.get("view_name").asText());
        assertEquals(3, r.get("figures_removed").asInt()); // group + nested figure + root figure
        assertEquals(1, r.get("connections_removed").asInt());
        assertNull(ModelAccessor.findViewById(models.model, v.getId()));
        assertNotNull(ModelAccessor.findElementById(models.model, a.getId()));
        assertNotNull(ModelAccessor.findRelationshipById(models.model, rel.getId()));
        assertEquals(1, models.saves);
    }

    @Test
    void rejectsUnknownView() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope'}"));
        assertEquals("View not found: nope", ex.getMessage());
    }
}

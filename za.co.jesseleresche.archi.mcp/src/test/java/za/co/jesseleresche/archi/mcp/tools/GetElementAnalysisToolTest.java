package za.co.jesseleresche.archi.mcp.tools;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class GetElementAnalysisToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final GetElementAnalysisTool tool = new GetElementAnalysisTool();

    IArchimateElement actor, role, other;
    IArchimateRelationship out, in;
    IArchimateDiagramModel view;
    IDiagramModelArchimateObject fig;

    @BeforeEach
    void setUp() {
        actor = models.element("BusinessActor", "Actor");
        role = models.element("BusinessRole", "Role");
        other = models.element("BusinessActor", "Other");
        out = models.relationship("AssignmentRelationship", actor, role);
        in = models.relationship("AssociationRelationship", other, actor);
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey("owner");
        p.setValue("Ops");
        actor.getProperties().add(p);
        view = models.view("Main");
        fig = models.figure(view, actor, 10, 20, 120, 55);
        fig.setFillColor("#ff0000");
    }

    @Test
    void reportsRelationshipsViewsAndProperties() throws Exception {
        JsonNode r = run(tool, "{'element_id':'" + actor.getId() + "'}");
        assertEquals("Actor", r.get("name").asText());
        assertEquals("BusinessActor", r.get("type").asText());
        JsonNode o = r.get("outgoing_relationships");
        assertEquals(1, o.size());
        assertEquals(out.getId(), o.get(0).get("relationship_id").asText());
        assertEquals(role.getId(), o.get(0).get("target_id").asText());
        assertEquals("BusinessRole", o.get(0).get("target_type").asText());
        JsonNode i = r.get("incoming_relationships");
        assertEquals(1, i.size());
        assertEquals("Other", i.get(0).get("source_name").asText());
        JsonNode v = r.get("used_in_views").get(0);
        assertEquals(view.getId(), v.get("view_id").asText());
        assertEquals(fig.getId(), v.get("figure_id").asText());
        assertEquals(10, v.get("x").asInt());
        assertEquals(55, v.get("height").asInt());
        assertEquals("#ff0000", v.get("fill_color").asText());
        assertFalse(v.has("line_color"));
        assertEquals("Ops", r.get("properties").get(0).get("value").asText());
    }

    @Test
    void canOmitPropertiesAndReportsEmptyCollections() throws Exception {
        assertFalse(run(tool, "{'element_id':'" + actor.getId() + "','include_properties':false}").has("properties"));
        JsonNode r = run(tool, "{'element_id':'" + role.getId() + "'}");
        assertEquals(0, r.get("outgoing_relationships").size());
        assertEquals(1, r.get("incoming_relationships").size());
        assertEquals(0, r.get("used_in_views").size());
        assertFalse(r.has("properties"));
    }

    @Test
    void unknownElementFails() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'element_id':'ghost'}"));
        assertEquals("Element not found: ghost", ex.getMessage());
    }

    @Test
    void reportsSpecialization() throws Exception {
        ModelAccessor.applySpecialization(models.model, actor, TestModels.json("{'specialization':'Customer'}"));
        assertEquals("Customer", run(tool, "{'element_id':'" + actor.getId() + "'}").get("specialization").asText());
    }

    /** S21 / FR-13. */
    @Test
    void usedInViewsListsEveryFigure() throws Exception {
        IDiagramModelArchimateObject fig2 = models.figure(view, actor, 10, 200, 120, 55);
        JsonNode used = run(tool, "{'element_id':'" + actor.getId() + "'}").get("used_in_views");
        assertEquals(2, used.size(), used.toString());
        java.util.Set<String> figs = new java.util.HashSet<>();
        for (JsonNode u : used) {
            assertEquals(view.getId(), u.get("view_id").asText());
            figs.add(u.get("figure_id").asText());
        }
        assertEquals(java.util.Set.of(fig.getId(), fig2.getId()), figs);
    }
}

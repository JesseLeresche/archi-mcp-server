package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IInfluenceRelationship;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;

class GetViewConnectionsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final GetViewConnectionsTool tool = new GetViewConnectionsTool();

    @Test
    void listsConnectionsIncludingNestedFiguresAndFilters() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 400, 300);
        IArchimateElement app = models.element("ApplicationComponent", "App");
        IArchimateElement data = models.element("DataObject", "Data");
        IArchimateElement other = models.element("ApplicationComponent", "Other");
        IDiagramModelArchimateObject fApp = models.figure(g, app, 10, 10, 120, 55);
        IDiagramModelArchimateObject fData = models.figure(v, data, 500, 0, 120, 55);
        IDiagramModelArchimateObject fOther = models.figure(v, other, 500, 200, 120, 55);
        IArchimateRelationship access = models.relationship("AccessRelationship", app, data);
        ((IAccessRelationship) access).setAccessType(IAccessRelationship.READ_ACCESS);
        IDiagramModelArchimateConnection c1 = models.connection(fApp, fData, access);
        IDiagramModelBendpoint bp = IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bp.setStartX(7);
        c1.getBendpoints().add(bp);
        c1.setTextPosition(2);
        c1.getFeatures().putString(FigureStyle.LABEL_EXPRESSION, "${name}", "");
        IArchimateRelationship serving = models.relationship("ServingRelationship", other, app);
        models.connection(fOther, fApp, serving);

        JsonNode all = run(tool, "{'view_id':'" + v.getId() + "'}").get("connections");
        assertEquals(2, all.size());

        JsonNode only = run(tool, "{'view_id':'" + v.getId() + "','relationship_id':'" + access.getId() + "'}")
                .get("connections");
        assertEquals(1, only.size());
        JsonNode c = only.get(0);
        assertEquals(c1.getId(), c.get("connection_id").asText());
        assertEquals("AccessRelationship", c.get("relationship_type").asText());
        assertEquals("Read", c.get("access_type").asText());
        assertEquals(app.getId(), c.get("source_element_id").asText());
        assertEquals("App", c.get("source_element_name").asText());
        assertEquals(fApp.getId(), c.get("source_figure_id").asText());
        assertEquals(fData.getId(), c.get("target_figure_id").asText());
        assertEquals("Data", c.get("target_element_name").asText());
        assertEquals("${name}", c.get("label_expression").asText());
        assertEquals(7, c.get("bendpoints").get(0).get("startX").asInt());
        assertEquals(2, c.get("text_position").asInt());
    }

    @Test
    void rejectsUnknownView() {
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
    }

    private static JsonNode byRelationship(JsonNode conns, String relId) {
        for (JsonNode c : conns) {
            if (relId.equals(c.get("relationship_id").asText())) {
                return c;
            }
        }
        throw new AssertionError(relId + " not in " + conns);
    }

    @Test
    void reportsStrengthAndDirected() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement app = models.element("ApplicationComponent", "App");
        IArchimateElement data = models.element("DataObject", "Data");
        IArchimateElement g1 = models.element("Goal", "G1");
        IArchimateElement g2 = models.element("Goal", "G2");
        IDiagramModelArchimateObject fApp = models.figure(v, app, 0, 0, 120, 55);
        IDiagramModelArchimateObject fData = models.figure(v, data, 300, 0, 120, 55);
        IAccessRelationship rw = (IAccessRelationship) models.relationship("AccessRelationship", app, data);
        rw.setAccessType(IAccessRelationship.READ_WRITE_ACCESS);
        models.connection(fApp, fData, rw);
        IInfluenceRelationship inf = (IInfluenceRelationship) models.relationship("InfluenceRelationship", g1, g2);
        inf.setStrength("+");
        models.connection(models.figure(v, g1, 0, 200, 120, 55), models.figure(v, g2, 300, 200, 120, 55), inf);
        IAssociationRelationship assoc = (IAssociationRelationship) models.relationship("AssociationRelationship", data, app);
        assoc.setDirected(true);
        models.connection(fData, fApp, assoc);
        IArchimateElement other = models.element("ApplicationComponent", "Other");
        IArchimateRelationship serving = models.relationship("ServingRelationship", other, app);
        models.connection(models.figure(v, other, 600, 0, 120, 55), fApp, serving);

        JsonNode all = run(tool, "{'view_id':'" + v.getId() + "'}").get("connections");
        assertEquals("ReadWrite", byRelationship(all, rw.getId()).get("access_type").asText());
        assertEquals("+", byRelationship(all, inf.getId()).get("influence_strength").asText());
        assertTrue(byRelationship(all, assoc.getId()).get("directed").asBoolean());
        JsonNode plain = byRelationship(all, serving.getId());
        assertFalse(plain.has("access_type") || plain.has("influence_strength") || plain.has("directed"), plain.toString());
    }
}

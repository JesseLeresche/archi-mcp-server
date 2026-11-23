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
import com.archimatetool.model.IInfluenceRelationship;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;

class GetConnectionToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final GetConnectionTool tool = new GetConnectionTool();

    @Test
    void returnsVisualPropertiesByConnectionOrRelationshipId() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateObject fa = models.figure(v, a, 0, 0, 120, 55);
        IDiagramModelArchimateObject fb = models.figure(v, b, 300, 0, 120, 55);
        IArchimateRelationship rel = models.relationship("ServingRelationship", a, b);
        rel.setName("serves");
        IDiagramModelArchimateConnection c = models.connection(fa, fb, rel);
        c.setLineColor("#ff0000");
        c.setFontColor("#00ff00");
        c.setLineWidth(3);
        c.setTextPosition(0);
        IDiagramModelBendpoint bp = IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bp.setEndY(42);
        c.getBendpoints().add(bp);
        c.getFeatures().putString(FigureStyle.LABEL_EXPRESSION, "${name}", "");

        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','connection_id':'" + c.getId() + "'}");
        assertEquals(rel.getId(), r.get("relationship_id").asText());
        assertEquals("ServingRelationship", r.get("relationship_type").asText());
        assertEquals("serves", r.get("relationship_name").asText());
        assertEquals(fa.getId(), r.get("source_figure_id").asText());
        assertEquals(fb.getId(), r.get("target_figure_id").asText());
        assertEquals("#ff0000", r.get("line_color").asText());
        assertEquals("#00ff00", r.get("font_color").asText());
        assertEquals(3, r.get("line_width").asInt());
        assertEquals(0, r.get("text_position").asInt());
        assertEquals("${name}", r.get("label_expression").asText());
        assertEquals(42, r.get("bendpoints").get(0).get("endY").asInt());

        r = run(tool, "{'view_id':'" + v.getId() + "','relationship_id':'" + rel.getId() + "'}");
        assertEquals(c.getId(), r.get("connection_id").asText());
    }

    @Test
    void omitsLabelExpressionWhenUnset() throws Exception {
        IArchimateDiagramModel v = models.view("V");
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IDiagramModelArchimateConnection c = models.connection(models.figure(v, a, 0, 0, 120, 55),
                models.figure(v, b, 300, 0, 120, 55), models.relationship("ServingRelationship", a, b));
        JsonNode r = run(tool, "{'view_id':'" + v.getId() + "','connection_id':'" + c.getId() + "'}");
        assertEquals(c.getId(), r.get("connection_id").asText());
        assertFalse(r.has("label_expression"));
    }

    @Test
    void rejectsBadRequests() {
        String vid = models.view("V").getId();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "'}"));
        assertTrue(e.getMessage().contains("Either connection_id or relationship_id"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'nope','connection_id':'x'}"));
        assertTrue(e.getMessage().contains("View not found: nope"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','connection_id':'x'}"));
        assertTrue(e.getMessage().contains("Connection not found on view: x"));
        e = assertThrows(Exception.class, () -> run(tool, "{'view_id':'" + vid + "','relationship_id':'y'}"));
        assertTrue(e.getMessage().contains("No connection found for relationship on view: y"));
    }

    @Test
    void reportsRelationshipAttributes() throws Exception {
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

        String byRel = "{'view_id':'" + v.getId() + "','relationship_id':'";
        assertEquals("ReadWrite", run(tool, byRel + rw.getId() + "'}").get("access_type").asText());
        assertEquals("+", run(tool, byRel + inf.getId() + "'}").get("influence_strength").asText());
        assertTrue(run(tool, byRel + assoc.getId() + "'}").get("directed").asBoolean());
    }
}

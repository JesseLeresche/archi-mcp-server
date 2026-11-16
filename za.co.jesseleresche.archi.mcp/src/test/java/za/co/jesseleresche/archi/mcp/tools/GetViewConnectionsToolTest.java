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
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelGroup;
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
        assertEquals(IAccessRelationship.READ_ACCESS, c.get("access_type").asInt());
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
}

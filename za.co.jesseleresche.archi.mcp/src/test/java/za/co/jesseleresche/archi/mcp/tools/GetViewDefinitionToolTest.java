package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ExchangeDir;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class GetViewDefinitionToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    @TempDir
    Path exchange;

    final InspectViewTool inspect = new InspectViewTool();

    IArchimateDiagramModel view;
    IArchimateElement app, svc;
    IArchimateRelationship serves;

    @BeforeEach
    void buildView() throws Exception {
        System.setProperty(ExchangeDir.PROPERTY, exchange.toString());
        IArchimateModel m = models.model;
        app = models.element("ApplicationComponent", "CRM");
        app.setDocumentation("the CRM");
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey("owner");
        p.setValue("sales");
        app.getProperties().add(p);
        ModelAccessor.applySpecialization(m, app, json("{'specialization':'Microservice'}"));
        svc = models.element("ApplicationService", "Lookup");
        ModelAccessor.findOrCreateFolderByPath(m, "Application/Services").getElements().add(svc);
        serves = models.relationship("ServingRelationship", svc, app);
        serves.setName("serves");

        view = models.view("Context");
        view.setDocumentation("context view");
        var g = models.group(view, "Zone", 10, 10, 500, 300);
        FigureStyle.apply(json("{'fill_color':'#eeeeee','border_type':1}"), g);
        var n = models.note(g, "Remember", 5, 200, 150, 60);
        FigureStyle.apply(json("{'text_alignment':1}"), n);
        var fa = models.figure(g, app, 20, 30, 120, 55);
        FigureStyle.apply(json("{'font':'1|Arial|11.0|1|COCOA|1|Arial-Bold','label_expression':'${name}'}"), fa);
        var fs = models.figure(view, svc, 600, 40, 120, 55);
        IDiagramModelArchimateConnection c = models.connection(fs, fa, serves);
        IDiagramModelBendpoint bp = IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bp.setStartX(10);
        bp.setEndY(-20);
        c.getBendpoints().add(bp);
        FigureStyle.apply(json("{'line_color':'#ff0000','line_width':2}"), c);
    }

    @AfterEach
    void clearExchange() {
        System.clearProperty(ExchangeDir.PROPERTY);
    }

    private JsonNode definition(String extra) throws Exception {
        return run(inspect, "{'operation':'definition','view_id':'" + view.getId() + "'" + extra + "}");
    }

    @Test
    void describesViewInCreateInputVocabulary() throws Exception {
        JsonNode d = definition("");
        assertEquals(view.getId(), d.get("view").get("id").asText());
        assertEquals("Context", d.get("view").get("name").asText());
        assertEquals("context view", d.get("view").get("documentation").asText());

        JsonNode crm = find(d.get("elements"), "id", app.getId());
        assertEquals("ApplicationComponent", crm.get("type").asText());
        assertEquals("the CRM", crm.get("documentation").asText());
        assertEquals("sales", crm.get("properties").get(0).get("value").asText());
        assertEquals("Microservice", crm.get("specialization").asText());
        assertEquals("Application/Services", find(d.get("elements"), "id", svc.getId()).get("folder_path").asText());

        JsonNode rel = d.get("relationships").get(0);
        assertEquals(serves.getId(), rel.get("id").asText());
        assertEquals("ServingRelationship", rel.get("type").asText());
        assertEquals(svc.getId(), rel.get("source_id").asText());
        assertEquals("serves", rel.get("name").asText());

        JsonNode figs = d.get("figures");
        assertEquals(4, figs.size());
        assertEquals("group", figs.get(0).get("kind").asText());
        assertEquals("#eeeeee", figs.get(0).get("fill_color").asText());
        assertEquals(1, figs.get(0).get("border_type").asInt());
        String gid = figs.get(0).get("figure_id").asText();
        for (int i = 1; i < 3; i++) {
            assertEquals(gid, figs.get(i).get("parent_figure_id").asText(), "children follow their parent");
        }
        JsonNode note = find(figs, "kind", "note");
        assertEquals("Remember", note.get("content").asText());
        JsonNode fa = find(figs, "element_id", app.getId());
        assertEquals("${name}", fa.get("label_expression").asText());
        assertEquals(20, fa.get("x").asInt());
        assertFalse(find(figs, "element_id", svc.getId()).has("parent_figure_id"));

        JsonNode conn = d.get("connections").get(0);
        assertEquals(serves.getId(), conn.get("relationship_id").asText());
        assertEquals(fa.get("figure_id").asText(), conn.get("target_figure_id").asText());
        assertEquals("#ff0000", conn.get("line_color").asText());
        assertEquals(-20, conn.get("bendpoints").get(0).get("endY").asInt());
    }

    @Test
    void readsAnotherOpenModelWithoutChangingTheSelection() throws Exception {
        IArchimateModel other = models.newModel("Other");
        IArchimateDiagramModel ov = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        ov.setName("Elsewhere");
        other.getDefaultFolderForObject(ov).getElements().add(ov);
        JsonNode d = run(inspect, "{'operation':'definition','model':'Other','view_id':'" + ov.getId() + "'}");
        assertEquals("Elsewhere", d.get("view").get("name").asText());
        assertSame(models.model, ModelAccessor.getOpenModel());
        Exception e = assertThrows(Exception.class,
                () -> run(inspect, "{'operation':'definition','model':'Nope','view_id':'x'}"));
        assertTrue(e.getMessage().contains("Model not found: Nope"), e.getMessage());
    }

    @Test
    void writesOutputFileToTheExchangeFolderOnly() throws Exception {
        JsonNode r = definition(",'output_file':'context.json'");
        assertEquals(4, r.get("figures").asInt());
        Path written = exchange.toRealPath().resolve("context.json");
        assertEquals(written.toString(), r.get("output_file").asText());
        assertEquals(view.getId(), json(Files.readString(written)).get("view").get("id").asText());
        Exception e = assertThrows(Exception.class, () -> definition(",'output_file':'context.json'"));
        assertTrue(e.getMessage().contains("already exists"), e.getMessage());
        e = assertThrows(Exception.class, () -> definition(",'output_file':'../escape.json'"));
        assertTrue(e.getMessage().contains("bare .json file name"), e.getMessage());
    }

    @Test
    void replayIntoAnotherModelReproducesTheDefinition() throws Exception {
        definition(",'output_file':'ctx.json'");
        JsonNode d = ExchangeDir.read("ctx.json");

        IArchimateModel target = models.newModel("Target");
        ModelAccessor.selectModel("Target");
        ObjectNode viewItem = ((ObjectNode) d.get("view")).deepCopy();
        run(new ManageViewsTool(), "{'operation':'create','items':" + viewItem + "}");
        replay(new ManageElementsTool(), "create", null, d.get("elements"));
        replay(new ManageRelationshipsTool(), "create", null, d.get("relationships"));
        replay(new ManageViewContentTool(), "add_figure", view.getId(), d.get("figures"));
        replay(new ManageViewContentTool(), "add_connection", view.getId(), d.get("connections"));

        JsonNode again = run(inspect, "{'operation':'definition','model':'Target','view_id':'" + view.getId() + "'}");
        assertEquals(d, again);
        assertNotSame(target, models.model);
    }

    private static void replay(ConsolidatedTool tool, String op, String viewId, JsonNode items) throws Exception {
        ObjectNode args = json("{}").deepCopy();
        args.put("operation", op);
        if (viewId != null) {
            args.put("view_id", viewId);
        }
        args.set("items", items);
        JsonNode r = json(tool.execute(args)).get("results");
        for (JsonNode e : r) {
            assertFalse(e.has("error"), op + ": " + e);
        }
    }

    private static JsonNode find(JsonNode arr, String key, String value) {
        for (JsonNode n : arr) {
            if (value.equals(n.path(key).asText(null))) {
                return n;
            }
        }
        throw new AssertionError(key + "=" + value + " not in " + arr);
    }
}

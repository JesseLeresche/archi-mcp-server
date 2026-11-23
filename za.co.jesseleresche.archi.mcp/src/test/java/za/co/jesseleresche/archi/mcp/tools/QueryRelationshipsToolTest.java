package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IInfluenceRelationship;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ExchangeDir;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class QueryRelationshipsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    @TempDir
    Path exchange;

    final QueryRelationshipsTool tool = new QueryRelationshipsTool();

    IArchimateElement app, data;

    @BeforeEach
    void setUp() {
        System.setProperty(ExchangeDir.PROPERTY, exchange.toString());
        app = models.element("ApplicationComponent", "A");
        data = models.element("DataObject", "D");
    }

    @AfterEach
    void clearExchange() {
        System.clearProperty(ExchangeDir.PROPERTY);
    }

    private JsonNode query(String filter) throws Exception {
        return run(tool, filter);
    }

    private static List<String> ids(JsonNode result) {
        List<String> ids = new ArrayList<>();
        for (JsonNode e : result.get("relationships")) {
            ids.add(e.get("id").asText());
        }
        return ids;
    }

    private static JsonNode entry(JsonNode result, String id) {
        for (JsonNode e : result.get("relationships")) {
            if (id.equals(e.get("id").asText())) {
                return e;
            }
        }
        throw new AssertionError(id + " not in " + result);
    }

    private IArchimateRelationship access(IArchimateElement s, IArchimateElement t, int accessType) {
        IAccessRelationship r = (IAccessRelationship) models.relationship("AccessRelationship", s, t);
        r.setAccessType(accessType);
        return r;
    }

    private void draw(IArchimateDiagramModel v, IArchimateRelationship r) {
        IDiagramModelArchimateObject fs = models.figure(v, (IArchimateElement) r.getSource(), 0, 0, 120, 55);
        IDiagramModelArchimateObject ft = models.figure(v, (IArchimateElement) r.getTarget(), 300, 0, 120, 55);
        models.connection(fs, ft, r);
    }

    @Test
    void undrawnAccessRelationshipHasFullDetail() throws Exception {
        IArchimateRelationship r = access(app, data, IAccessRelationship.READ_ACCESS);
        r.setName("reads");
        r.setDocumentation("reads the data");
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey("owner");
        p.setValue("team-x");
        r.getProperties().add(p);
        ModelAccessor.applySpecialization(models.model, r, json("{'specialization':'Feed'}"));
        ModelAccessor.findOrCreateFolderByPath(models.model, "Relations/Data").getElements().add(r);

        JsonNode result = query("{}");
        assertEquals(1, result.get("count").asInt());
        JsonNode e = result.get("relationships").get(0);
        assertEquals(r.getId(), e.get("id").asText());
        assertEquals("AccessRelationship", e.get("type").asText());
        assertEquals(app.getId(), e.get("source_id").asText());
        assertEquals(data.getId(), e.get("target_id").asText());
        assertEquals("reads", e.get("name").asText());
        assertEquals("reads the data", e.get("documentation").asText());
        assertEquals("Relations/Data", e.get("folder_path").asText());
        assertEquals(json("[{'key':'owner','value':'team-x'}]"), e.get("properties"));
        assertEquals("Feed", e.get("specialization").asText());
        assertEquals("Read", e.get("access_type").asText());
        assertEquals(json("[]"), e.get("views"));
    }

    @Test
    void everyAccessTypeIsNamed() throws Exception {
        IArchimateRelationship w = access(app, data, IAccessRelationship.WRITE_ACCESS);
        IArchimateRelationship r = access(app, data, IAccessRelationship.READ_ACCESS);
        IArchimateRelationship u = access(app, data, IAccessRelationship.UNSPECIFIED_ACCESS);
        IArchimateRelationship rw = access(app, data, IAccessRelationship.READ_WRITE_ACCESS);
        JsonNode result = query("{}");
        assertEquals("Write", entry(result, w.getId()).get("access_type").asText());
        assertEquals("Read", entry(result, r.getId()).get("access_type").asText());
        assertEquals("Unspecified", entry(result, u.getId()).get("access_type").asText());
        assertEquals("ReadWrite", entry(result, rw.getId()).get("access_type").asText());
    }

    @Test
    void viewsListedOncePerView() throws Exception {
        IArchimateElement other = models.element("ApplicationComponent", "B");
        IArchimateRelationship r = models.relationship("ServingRelationship", app, other);
        IArchimateDiagramModel v1 = models.view("V1");
        IArchimateDiagramModel v2 = models.view("V2");
        draw(v1, r);
        draw(v1, r);
        draw(v2, r);
        JsonNode e = entry(query("{}"), r.getId());
        assertEquals(json("['" + v1.getId() + "','" + v2.getId() + "']"), e.get("views"));
    }

    @Test
    void viewsInViewFolderOrder() throws Exception {
        IArchimateRelationship r = models.relationship("ServingRelationship", app, models.element("ApplicationComponent", "B"));
        for (int i = 0; i < 8; i++) {
            models.view("V" + i);
        }
        // Move one view into a subfolder so folder order differs from creation order.
        IArchimateDiagramModel first = ModelAccessor.collectAllFromFolders(models.model, IArchimateDiagramModel.class).get(0);
        ModelAccessor.findOrCreateFolderByPath(models.model, "Views/Sub").getElements().add(first);
        List<String> expected = new ArrayList<>();
        for (IArchimateDiagramModel v : ModelAccessor.collectAllFromFolders(models.model, IArchimateDiagramModel.class)) {
            expected.add(v.getId());
        }
        for (int i = expected.size() - 1; i >= 0; i--) {
            draw(ModelAccessor.findViewById(models.model, expected.get(i)), r);
        }
        List<String> views = new ArrayList<>();
        entry(query("{}"), r.getId()).get("views").forEach(n -> views.add(n.asText()));
        assertEquals(expected, views);
    }

    @Test
    void onViewMustBeBoolean() {
        Exception e = assertThrows(Exception.class, () -> query("{'on_view':'yes'}"));
        assertEquals("on_view must be true or false, got \"yes\"", e.getMessage());
    }

    @Test
    void onViewFalseReturnsExactlyUndrawn() throws Exception {
        IArchimateRelationship r1 = access(app, data, IAccessRelationship.READ_ACCESS);
        IArchimateRelationship r2 = access(app, data, IAccessRelationship.READ_ACCESS);
        IArchimateRelationship r3 = access(app, data, IAccessRelationship.READ_ACCESS);
        draw(models.view("V"), r1);
        assertEquals(List.of(r2.getId(), r3.getId()), ids(query("{'on_view':false}")));
    }

    @Test
    void onViewTrueReturnsExactlyDrawn() throws Exception {
        IArchimateRelationship r1 = access(app, data, IAccessRelationship.READ_ACCESS);
        access(app, data, IAccessRelationship.READ_ACCESS);
        access(app, data, IAccessRelationship.READ_ACCESS);
        draw(models.view("V"), r1);
        assertEquals(List.of(r1.getId()), ids(query("{'on_view':true}")));
    }

    @Test
    void elementIdMatchesEitherEnd() throws Exception {
        IArchimateElement a = models.element("ApplicationComponent", "a");
        IArchimateElement b = models.element("ApplicationComponent", "b");
        IArchimateElement c = models.element("ApplicationComponent", "c");
        IArchimateRelationship ab = models.relationship("ServingRelationship", a, b);
        IArchimateRelationship ca = models.relationship("ServingRelationship", c, a);
        models.relationship("ServingRelationship", b, c);
        assertEquals(List.of(ab.getId(), ca.getId()), ids(query("{'element_id':'" + a.getId() + "'}")));
    }

    @Test
    void typeFilterIsCaseInsensitive() throws Exception {
        IArchimateRelationship acc = access(app, data, IAccessRelationship.READ_ACCESS);
        models.relationship("ServingRelationship", app, models.element("ApplicationComponent", "B"));
        assertEquals(List.of(acc.getId()), ids(query("{'type_filter':'accessrelationship'}")));
    }

    @Test
    void unknownTypeIsError() {
        Exception e = assertThrows(Exception.class, () -> query("{'type_filter':'NotARelationship'}"));
        assertTrue(String.valueOf(e.getMessage()).contains("NotARelationship"), e.toString());
    }

    @Test
    void unknownElementIdIsError() {
        access(app, data, IAccessRelationship.READ_ACCESS);
        Exception e = assertThrows(Exception.class, () -> query("{'element_id':'ghost'}"));
        assertTrue(String.valueOf(e.getMessage()).contains("ghost"), e.toString());
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        IArchimateRelationship drawn = access(app, data, IAccessRelationship.READ_ACCESS);
        IArchimateRelationship undrawn = access(app, data, IAccessRelationship.READ_ACCESS);
        models.relationship("ServingRelationship", app, models.element("ApplicationComponent", "B"));
        draw(models.view("V"), drawn);
        JsonNode r = query("{'element_id':'" + app.getId() + "','type_filter':'AccessRelationship','on_view':false}");
        assertEquals(List.of(undrawn.getId()), ids(r));
    }

    @Test
    void emptyFilterReturnsAllInFolderOrder() throws Exception {
        IArchimateRelationship r1 = access(app, data, IAccessRelationship.READ_ACCESS);
        IArchimateRelationship r2 = access(app, data, IAccessRelationship.WRITE_ACCESS);
        IArchimateRelationship r3 = models.relationship("AssociationRelationship", app, data);
        IArchimateRelationship r4 = models.relationship("AssociationRelationship", data, app);
        ModelAccessor.findOrCreateFolderByPath(models.model, "Relations/Y").getElements().add(r1);
        ModelAccessor.findOrCreateFolderByPath(models.model, "Relations/X").getElements().add(r3);
        ModelAccessor.findOrCreateFolderByPath(models.model, "Relations/X/Deep").getElements().add(r4);
        List<String> folderOrder = new ArrayList<>();
        for (IArchimateRelationship r : ModelAccessor.collectAllFromFolders(models.model, IArchimateRelationship.class)) {
            folderOrder.add(r.getId());
        }
        assertEquals(4, folderOrder.size());
        assertEquals(r2.getId(), folderOrder.get(0), "fixture: root Relations folder first");

        JsonNode result = query("{}");
        assertEquals(4, result.get("count").asInt());
        assertEquals(folderOrder, ids(result));
        assertEquals(result, query("{}"), "same query on an unchanged model is identical");
    }

    @Test
    void influenceStrengthAndDirectedReported() throws Exception {
        IArchimateElement g1 = models.element("Goal", "G1");
        IArchimateElement g2 = models.element("Goal", "G2");
        IInfluenceRelationship inf = (IInfluenceRelationship) models.relationship("InfluenceRelationship", g1, g2);
        inf.setStrength("++");
        IAssociationRelationship assoc = (IAssociationRelationship) models.relationship("AssociationRelationship", app, data);
        assoc.setDirected(true);
        JsonNode result = query("{}");
        assertEquals("++", entry(result, inf.getId()).get("influence_strength").asText());
        assertTrue(entry(result, assoc.getId()).get("directed").asBoolean());
    }

    @Test
    void defaultsOmitted() throws Exception {
        IArchimateElement g1 = models.element("Goal", "G1");
        IArchimateElement g2 = models.element("Goal", "G2");
        models.relationship("InfluenceRelationship", g1, g2);
        models.relationship("AssociationRelationship", app, data);
        models.relationship("ServingRelationship", app, models.element("ApplicationComponent", "B"));
        access(app, data, IAccessRelationship.READ_ACCESS);
        JsonNode result = query("{}");
        assertEquals(4, result.get("count").asInt());
        for (JsonNode e : result.get("relationships")) {
            assertFalse(e.has("influence_strength"), e.toString());
            assertFalse(e.has("directed"), e.toString());
        }
    }

    @Test
    void modelParamReadsWithoutChangingSelection() throws Exception {
        access(app, data, IAccessRelationship.READ_ACCESS);
        IArchimateModel other = models.newModel("Other");
        IArchimateElement oa = (IArchimateElement) IArchimateFactory.eINSTANCE.createApplicationComponent();
        IArchimateElement ob = (IArchimateElement) IArchimateFactory.eINSTANCE.createApplicationComponent();
        other.getDefaultFolderForObject(oa).getElements().add(oa);
        other.getDefaultFolderForObject(ob).getElements().add(ob);
        IArchimateRelationship rt = IArchimateFactory.eINSTANCE.createServingRelationship();
        rt.connect(oa, ob);
        other.getDefaultFolderForObject(rt).getElements().add(rt);
        String selectedBefore = ModelAccessor.getSelectedModelId();
        IArchimateModel openBefore = ModelAccessor.getOpenModel();

        assertEquals(List.of(rt.getId()), ids(query("{'model':'Other'}")));
        assertEquals(List.of(rt.getId()), ids(query("{'model':'" + other.getId() + "'}")));
        assertEquals(selectedBefore, ModelAccessor.getSelectedModelId());
        assertSame(openBefore, ModelAccessor.getOpenModel());
    }

    @Test
    void unknownModelIsError() {
        Exception e = assertThrows(Exception.class, () -> query("{'model':'nope'}"));
        assertEquals("Model not found: nope", e.getMessage());
    }

    @Test
    void outputFileWritesBareArray() throws Exception {
        access(app, data, IAccessRelationship.READ_ACCESS);
        models.relationship("AssociationRelationship", app, data);
        JsonNode inline = query("{}");

        JsonNode r = query("{'output_file':'rels.json'}");
        Path written = exchange.toRealPath().resolve("rels.json");
        assertEquals(written.toString(), r.get("output_file").asText());
        assertEquals(2, r.get("count").asInt());
        assertFalse(r.has("relationships"), r.toString());
        JsonNode file = json(Files.readString(written));
        assertTrue(file.isArray(), "bare array, not wrapped: " + file);
        assertEquals(inline.get("relationships"), file);
    }

    @Test
    void outputFileNeverOverwritesOrAcceptsPath() throws Exception {
        access(app, data, IAccessRelationship.READ_ACCESS);
        Path existing = exchange.resolve("rels.json");
        Files.writeString(existing, "keep");
        Exception e = assertThrows(Exception.class, () -> query("{'output_file':'rels.json'}"));
        assertTrue(String.valueOf(e.getMessage()).contains("already exists"), e.toString());
        e = assertThrows(Exception.class, () -> query("{'output_file':'../rels.json'}"));
        assertTrue(String.valueOf(e.getMessage()).contains("bare .json file name"), e.toString());
        assertEquals("keep", Files.readString(existing));
    }

    @Test
    void queryIsReadOnly() throws Exception {
        IArchimateRelationship r = access(app, data, IAccessRelationship.READ_ACCESS);
        draw(models.view("V"), r);
        int folders = models.model.getFolders().get(0).getFolders().size();
        query("{}");
        query("{'on_view':true,'type_filter':'AccessRelationship','element_id':'" + app.getId() + "'}");
        query("{'output_file':'ro.json'}");
        assertEquals(0, models.saves);
        assertEquals(List.of(r), ModelAccessor.collectAllFromFolders(models.model, IArchimateRelationship.class));
        assertEquals(IAccessRelationship.READ_ACCESS, ((IAccessRelationship) r).getAccessType());
        assertEquals(folders, models.model.getFolders().get(0).getFolders().size());
    }

    @Test
    void noOpenModelIsError() {
        models.closeAll();
        Exception e = assertThrows(Exception.class, () -> query("{}"));
        assertEquals("No model is currently open in Archi", e.getMessage());
    }
}

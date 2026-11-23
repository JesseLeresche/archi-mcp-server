package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IInfluenceRelationship;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ExchangeDir;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class ManageRelationshipsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ManageRelationshipsTool tool = new ManageRelationshipsTool();

    @TempDir
    Path exchange;

    IArchimateElement a, b;

    @BeforeEach
    void setUp() {
        a = models.element("BusinessActor", "A");
        b = models.element("BusinessRole", "B");
    }

    @AfterEach
    void clearExchange() {
        System.clearProperty(ExchangeDir.PROPERTY);
    }

    @Test
    void createsSingleObjectOrStringifiedItems() throws Exception {
        JsonNode r = run(tool, "{'operation':'create','items':{'source_id':'" + a.getId() + "','target_id':'"
                + b.getId() + "','type':'AssignmentRelationship'}}").get("results");
        IArchimateRelationship rel = (IArchimateRelationship) ArchimateModelUtils.getObjectByID(
                models.model, r.get(0).get("id").asText());
        assertSame(a, rel.getSource());

        r = run(tool, "{'operation':'create','items':'[{\"source_id\":\"" + a.getId() + "\",\"target_id\":\""
                + b.getId() + "\",\"type\":\"AssociationRelationship\"}]'}").get("results");
        assertNotNull(ArchimateModelUtils.getObjectByID(models.model, r.get(0).get("id").asText()));
    }

    @Test
    void updatesAndDeletesByStringOrObject() throws Exception {
        IArchimateRelationship r1 = models.relationship("AssignmentRelationship", a, b);
        IArchimateRelationship r2 = models.relationship("AssociationRelationship", a, b);
        run(tool, "{'operation':'update','items':[{'relationship_id':'" + r1.getId() + "','name':'renamed'}]}");
        assertEquals("renamed", r1.getName());

        JsonNode dry = run(tool, "{'operation':'delete','items':[{'relationship_id':'" + r1.getId() + "','dry_run':true}]}");
        assertTrue(dry.get("results").get(0).get("would_delete").asBoolean());
        assertNotNull(r1.eContainer());

        run(tool, "{'operation':'delete','items':['" + r1.getId() + "',{'relationship_id':'" + r2.getId() + "'}]}");
        assertNull(r1.eContainer());
        assertNull(r2.eContainer());
    }

    @Test
    void createRoutesToBulkCreateIncludingStringifiedItems() throws Exception {
        JsonNode r = run(tool, "{'operation':'create','items':'{\"source_id\":\"ghost\",\"target_id\":\""
                + b.getId() + "\",\"type\":\"AssignmentRelationship\"}'}").get("results");
        assertEquals("Source element not found: ghost", r.get(0).get("error").asText());
        assertEquals(1, models.saves);
    }

    @Test
    void validTypesIsPerItem() throws Exception {
        JsonNode vt = run(tool, "{'operation':'valid_types','items':{'source_id':'" + a.getId() + "','target_id':'"
                + b.getId() + "'}}").get("results").get(0);
        assertTrue(vt.get("valid_relationship_types").toString().contains("AssignmentRelationship"), vt.toString());
    }

    @Test
    void betweenIsPerItem() throws Exception {
        IArchimateRelationship rel = models.relationship("AssignmentRelationship", a, b);
        JsonNode bt = run(tool, "{'operation':'between','items':[{'element_a_id':'" + a.getId() + "','element_b_id':'"
                + b.getId() + "'},{'element_a_id':'ghost','element_b_id':'" + b.getId() + "'}]}").get("results");
        assertEquals(rel.getId(), bt.get(0).get("results").get(0).get("relationship_id").asText());
        assertEquals("Element not found: ghost", bt.get(1).get("error").asText());
    }

    @Test
    void rejectsUnknownOperationAndMissingItems() {
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{'operation':'frob','items':{}}"));
        assertTrue(ex.getMessage().startsWith("Unknown operation 'frob'"), ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'operation':'create'}"));
        assertEquals("'items' is required (a single object or an array)", ex.getMessage());
        ex = assertThrows(Exception.class, () -> run(tool, "{'operation':'create','items':[]}"));
        assertEquals("'items' must not be an empty array", ex.getMessage());
    }

    // --- query (FR-QR-1, 16, 21) and replay (QR-18, 19, 37) ---

    @Test
    void queryIsPerItem() throws Exception {
        IArchimateRelationship rel = models.relationship("AssignmentRelationship", a, b);
        JsonNode r = run(tool, "{'operation':'query','items':[{'element_id':'ghost'},{'element_id':'"
                + a.getId() + "'}]}").get("results");
        assertTrue(r.get(0).path("error").asText().contains("ghost"), r.toString());
        assertEquals(1, r.get(1).get("count").asInt());
        assertEquals(rel.getId(), r.get(1).get("relationships").get(0).get("id").asText());
    }

    @Test
    void queryListedInSchemaEnum() {
        assertTrue(tool.getInputSchema().get("properties").get("operation").get("enum").toString()
                .contains("\"query\""));
        String d = tool.getDescription();
        for (String s : List.of("element_id", "type_filter", "on_view", "output_file", "on_existing", "reuse")) {
            assertTrue(d.contains(s), s + " missing from description: " + d);
        }
    }

    @Test
    void accessTypeLegendMatchesArchiConstants() {
        String[] legend = {
                IAccessRelationship.WRITE_ACCESS + "=Write",
                IAccessRelationship.READ_ACCESS + "=Read",
                IAccessRelationship.UNSPECIFIED_ACCESS + "=Unspecified",
                IAccessRelationship.READ_WRITE_ACCESS + "=ReadWrite" };
        String create = new BulkCreateRelationshipsTool().getInputSchema().toString();
        String update = new UpdateRelationshipTool().getInputSchema().toString();
        for (String text : List.of(tool.getDescription(), create, update)) {
            for (String l : legend) {
                assertTrue(text.contains(l), l + " missing from: " + text);
            }
        }
        assertFalse(create.contains("0=Unspecified"), create);
    }

    @Test
    void queryOutputReplaysIntoAnotherModel() throws Exception {
        replaySource();
        JsonNode s = queryAll();
        assertEquals(4, s.get("count").asInt());

        IArchimateModel t = target("Target");
        assertNoErrors(create(s.get("relationships")));
        assertSameRelationships(models.model, t);
        assertEquals(s, queryAll(), "querying T returns the same entries as querying S");
    }

    @Test
    void queryOutputFileReplaysViaItemsFile() throws Exception {
        System.setProperty(ExchangeDir.PROPERTY, exchange.toString());
        replaySource();
        JsonNode s = queryAll();
        JsonNode w = run(tool, "{'operation':'query','items':{'output_file':'rels.json'}}").get("results").get(0);
        assertEquals(4, w.get("count").asInt(), w.toString());

        IArchimateModel t = target("Target");
        JsonNode r = run(tool, "{'operation':'create','items_file':'rels.json'}").get("results");
        assertEquals(4, r.size());
        assertNoErrors(r);
        assertSameRelationships(models.model, t);
        assertEquals(s, queryAll());
    }

    @Test
    void replayIntoPopulatedModelNeedsSkipOrReuse() throws Exception {
        replaySource();
        JsonNode entries = queryAll().get("relationships");

        IArchimateModel same = target("SameIds");
        assertNoErrors(create(entries));
        JsonNode r = create(with(entries, "on_conflict", "skip"));
        for (int i = 0; i < entries.size(); i++) {
            assertTrue(r.get(i).path("skipped").asBoolean(), r.get(i).toString());
            assertEquals(entries.get(i).get("id").asText(), r.get(i).get("id").asText());
        }
        assertEquals(4, relationships(same).size());

        IArchimateModel other = target("OtherIds");
        JsonNode made = create(without(entries, "id"));
        assertNoErrors(made);
        r = create(with(entries, "on_existing", "reuse"));
        for (int i = 0; i < entries.size(); i++) {
            assertTrue(r.get(i).path("reused").asBoolean(), r.get(i).toString());
            assertEquals(made.get(i).get("id").asText(), r.get(i).get("id").asText());
        }
        assertEquals(4, relationships(other).size());
        for (JsonNode e : entries) {
            assertNull(ArchimateModelUtils.getObjectByID(other, e.get("id").asText()), "S's ID not created in T");
        }
    }

    /** QR-37 summary counts (deferred to multiple-connections FR-5): skipped and reused are not succeeded. */
    @Test
    void replayRerunSummaryCountsSkippedAndReused() throws Exception {
        replaySource();
        JsonNode entries = queryAll().get("relationships");
        int n = entries.size();

        target("SameIds");
        assertNoErrors(create(entries));
        JsonNode sum = createSummary(with(entries, "on_conflict", "skip"));
        assertEquals(n, sum.get("count").asInt(), sum.toString());
        assertEquals(n, sum.path("skipped").asInt(), sum.toString());
        assertEquals(0, sum.path("reused").asInt(), sum.toString());
        assertEquals(0, sum.get("succeeded").asInt(), sum.toString());
        assertEquals(0, sum.get("failed").asInt(), sum.toString());

        target("OtherIds");
        assertNoErrors(create(without(entries, "id")));
        sum = createSummary(with(entries, "on_existing", "reuse"));
        assertEquals(n, sum.get("count").asInt(), sum.toString());
        assertEquals(n, sum.path("reused").asInt(), sum.toString());
        assertEquals(0, sum.path("skipped").asInt(), sum.toString());
        assertEquals(0, sum.get("succeeded").asInt(), sum.toString());
        assertEquals(0, sum.get("failed").asInt(), sum.toString());
    }

    private JsonNode createSummary(JsonNode items) throws Exception {
        ObjectNode args = json("{'operation':'create','results':'summary'}").deepCopy();
        args.set("items", items);
        return json(tool.execute(args));
    }

    /** Access (Read, full detail), Influence (+), directed Association, Serving; undrawn, in subfolders. */
    private void replaySource() {
        IArchimateModel m = models.model;
        IArchimateElement app = models.element("ApplicationComponent", "App");
        IArchimateElement app2 = models.element("ApplicationComponent", "App2");
        IArchimateElement data = models.element("DataObject", "Data");
        IArchimateElement g1 = models.element("Goal", "G1");
        IArchimateElement g2 = models.element("Goal", "G2");

        IAccessRelationship acc = (IAccessRelationship) models.relationship("AccessRelationship", app, data);
        acc.setAccessType(IAccessRelationship.READ_ACCESS);
        acc.setName("reads");
        acc.setDocumentation("reads the data");
        property(acc, "owner", "team-x");
        ModelAccessor.applySpecialization(m, acc, json("{'specialization':'Feed'}"));
        ModelAccessor.findOrCreateFolderByPath(m, "Relations/Data").getElements().add(acc);

        IInfluenceRelationship inf = (IInfluenceRelationship) models.relationship("InfluenceRelationship", g1, g2);
        inf.setStrength("+");
        ModelAccessor.findOrCreateFolderByPath(m, "Relations/Motivation").getElements().add(inf);

        IAssociationRelationship assoc = (IAssociationRelationship) models.relationship("AssociationRelationship", app, app2);
        assoc.setDirected(true);

        IArchimateRelationship srv = models.relationship("ServingRelationship", app2, app);
        srv.setName("serves");
        srv.setDocumentation("serving doc");
        property(srv, "k", "v");
        ModelAccessor.applySpecialization(m, srv, json("{'specialization':'Uses'}"));
        ModelAccessor.findOrCreateFolderByPath(m, "Relations/Data/Inner").getElements().add(srv);
    }

    private static void property(IArchimateRelationship r, String key, String value) {
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey(key);
        p.setValue(value);
        r.getProperties().add(p);
    }

    /** A new open model holding copies of S's elements (same IDs, no relationships), selected. */
    private IArchimateModel target(String name) {
        IArchimateModel t = models.newModel(name);
        for (IArchimateElement e : ModelAccessor.collectAllFromFolders(models.model, IArchimateElement.class)) {
            IArchimateElement c = (IArchimateElement) IArchimateFactory.eINSTANCE.create(e.eClass());
            c.setId(e.getId());
            c.setName(e.getName());
            t.getDefaultFolderForObject(c).getElements().add(c);
        }
        assertSame(t, ModelAccessor.selectModel(name));
        return t;
    }

    private JsonNode queryAll() throws Exception {
        return run(tool, "{'operation':'query','items':{}}").get("results").get(0);
    }

    private JsonNode create(JsonNode items) throws Exception {
        ObjectNode args = json("{'operation':'create'}").deepCopy();
        args.set("items", items);
        return json(tool.execute(args)).get("results");
    }

    private static ArrayNode with(JsonNode items, String key, String value) {
        ArrayNode out = ((ArrayNode) items).deepCopy();
        out.forEach(n -> ((ObjectNode) n).put(key, value));
        return out;
    }

    private static ArrayNode without(JsonNode items, String key) {
        ArrayNode out = ((ArrayNode) items).deepCopy();
        out.forEach(n -> ((ObjectNode) n).remove(key));
        return out;
    }

    private static void assertNoErrors(JsonNode results) {
        for (JsonNode r : results) {
            assertFalse(r.has("error"), r.toString());
        }
    }

    private static List<IArchimateRelationship> relationships(IArchimateModel m) {
        return ModelAccessor.collectAllFromFolders(m, IArchimateRelationship.class);
    }

    private static void assertSameRelationships(IArchimateModel s, IArchimateModel t) {
        assertEquals(relationships(s).size(), relationships(t).size());
        List<String> sIds = new ArrayList<>();
        for (IArchimateRelationship r : relationships(s)) {
            sIds.add(r.getId());
            IArchimateRelationship c = ModelAccessor.findRelationshipById(t, r.getId());
            assertNotNull(c, "missing in target: " + r.getId());
            assertEquals(r.eClass(), c.eClass());
            assertEquals(r.getSource().getId(), c.getSource().getId());
            assertEquals(r.getTarget().getId(), c.getTarget().getId());
            assertEquals(r.getName(), c.getName());
            assertEquals(r.getDocumentation(), c.getDocumentation());
            assertEquals(ModelAccessor.getFolderPath((IFolder) r.eContainer()),
                    ModelAccessor.getFolderPath((IFolder) c.eContainer()));
            assertEquals(ModelAccessor.readProperties(r), ModelAccessor.readProperties(c));
            assertEquals(ModelAccessor.getSpecialization(r), ModelAccessor.getSpecialization(c));
            if (r instanceof IAccessRelationship ra) {
                assertEquals(ra.getAccessType(), ((IAccessRelationship) c).getAccessType(), r.getId());
            }
            if (r instanceof IInfluenceRelationship ri) {
                assertEquals(ri.getStrength(), ((IInfluenceRelationship) c).getStrength(), r.getId());
            }
            if (r instanceof IAssociationRelationship ra) {
                assertEquals(ra.isDirected(), ((IAssociationRelationship) c).isDirected(), r.getId());
            }
        }
        List<String> tIds = new ArrayList<>();
        relationships(t).forEach(r -> tIds.add(r.getId()));
        assertEquals(sIds, tIds, "same folder order");
    }
}

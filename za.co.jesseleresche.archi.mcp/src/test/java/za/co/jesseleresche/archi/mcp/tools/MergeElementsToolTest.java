package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.gef.commands.CommandStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IAssociationRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IInfluenceRelationship;
import com.archimatetool.model.IProperties;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

/** Spec figure-removal-dangling-connections, US-2 (manage_elements merge). */
class MergeElementsToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final MergeElementsTool tool = new MergeElementsTool();

    // --- helpers

    static void prop(IProperties target, String key, String value) {
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey(key);
        p.setValue(value);
        target.getProperties().add(p);
    }

    /** The concept's properties as "key=value" in order. */
    static List<String> props(IProperties target) {
        return target.getProperties().stream().map(p -> p.getKey() + "=" + p.getValue())
                .collect(Collectors.toList());
    }

    IArchimateElement comp(String name) {
        return models.element("ApplicationComponent", name);
    }

    static String ids(IArchimateConcept... cs) {
        return Stream.of(cs).map(c -> "'" + c.getId() + "'").collect(Collectors.joining(",", "[", "]"));
    }

    /** Merge item JSON: survivor plus duplicates, with any extra fields appended verbatim. */
    static String item(IArchimateConcept survivor, List<? extends IArchimateConcept> dups, String extra) {
        return "{'survivor_id':'" + survivor.getId() + "','duplicate_ids':"
                + ids(dups.toArray(new IArchimateConcept[0])) + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    JsonNode merge(IArchimateConcept survivor, IArchimateConcept dup, String extra) throws Exception {
        return run(tool, item(survivor, List.of(dup), extra));
    }

    static boolean containsNode(JsonNode array, JsonNode expected) {
        for (JsonNode n : array) {
            if (n.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    boolean exists(IArchimateConcept c) {
        return c instanceof IArchimateElement
                ? ModelAccessor.findElementById(models.model, c.getId()) != null
                : ModelAccessor.findRelationshipById(models.model, c.getId()) != null;
    }

    // --- S2.1 / FR-12, FR-22

    @Test
    void repointsRelationshipsKeepingIds() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y"), z = comp("Z");
        IArchimateRelationship r1 = models.relationship("ServingRelationship", d, y);
        IArchimateRelationship r2 = models.relationship("FlowRelationship", z, d);
        r1.setName("serves");
        prop(r1, "k", "v");
        IFolder r1Folder = (IFolder) r1.eContainer();

        JsonNode r = merge(s, d, "");

        assertFalse(r.has("error"), r.toString());
        assertSame(r1, ModelAccessor.findRelationshipById(models.model, r1.getId()));
        assertSame(s, r1.getSource());
        assertSame(y, r1.getTarget());
        assertSame(z, r2.getSource());
        assertSame(s, r2.getTarget());
        assertEquals("ServingRelationship", r1.eClass().getName());
        assertEquals("serves", r1.getName());
        assertEquals(List.of("k=v"), props(r1));
        assertSame(r1Folder, r1.eContainer());
        assertFalse(exists(d));
        assertEquals(2, r.get("relationships_repointed").asInt());
        assertEquals(s.getId(), r.get("survivor_id").asText());
        assertEquals(List.of(d.getId()), texts(r.get("merged")));
        assertEquals(1, models.saves);
    }

    // --- S2.2 / FR-14

    @Test
    void retargetsFiguresInPlace() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y"), n = comp("N");
        IArchimateRelationship rdy = models.relationship("ServingRelationship", d, y);
        IArchimateDiagramModel v = models.view("V");
        IArchimateDiagramModel w = models.view("W");
        IDiagramModelGroup g = models.group(v, "G", 0, 0, 600, 400);
        IDiagramModelArchimateObject f = models.figure(g, d, 10, 30, 300, 200);
        f.setFillColor("#ff0000");
        IDiagramModelArchimateObject fn = models.figure(f, n, 10, 30, 120, 55);
        IDiagramModelArchimateObject fy = models.figure(v, y, 700, 0, 120, 55);
        IDiagramModelArchimateConnection k = models.connection(f, fy, rdy);
        IDiagramModelArchimateObject fw = models.figure(w, d, 0, 0, 120, 55);
        String fid = f.getId();

        JsonNode r = merge(s, d, "");

        assertFalse(r.has("error"), r.toString());
        assertSame(f, ModelAccessor.findDiagramObjectById(v, fid));
        assertSame(s, f.getArchimateElement());
        assertSame(s, fw.getArchimateElement());
        assertSame(g, f.eContainer());
        assertEquals(10, f.getBounds().getX());
        assertEquals(300, f.getBounds().getWidth());
        assertEquals("#ff0000", f.getFillColor());
        assertEquals(List.of(fn), new ArrayList<>(f.getChildren()));
        assertEquals(List.of(k), new ArrayList<>(f.getSourceConnections()));
        assertEquals(2, r.get("figures_retargeted").asInt());
        List<String> views = texts(r.get("affected_view_ids"));
        assertTrue(views.contains(v.getId()) && views.contains(w.getId()), views.toString());
        assertEquals(2, views.size());
    }

    // --- S2.3 / FR-14

    @Test
    void viewDefinitionUnchangedExceptElementId() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y");
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateObject fs = models.figure(v, s, 0, 0, 120, 55);
        IDiagramModelArchimateObject fd = models.figure(v, d, 0, 200, 300, 200);
        models.figure(fd, comp("Child"), 10, 30, 120, 55);
        IDiagramModelArchimateObject fy = models.figure(v, y, 400, 0, 120, 55);
        IDiagramModelArchimateConnection k = models.connection(fd, fy, models.relationship("ServingRelationship", d, y));
        IDiagramModelBendpoint bp = IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bp.setStartX(5);
        bp.setEndX(-5);
        k.getBendpoints().add(bp);
        models.connection(fs, fy, models.relationship("FlowRelationship", s, y));
        InspectViewTool inspect = new InspectViewTool();
        String def = "{'operation':'definition','view_id':'" + v.getId() + "'}";
        JsonNode before = run(inspect, def);

        JsonNode r = merge(s, d, "");
        assertFalse(r.has("error"), r.toString());
        JsonNode after = run(inspect, def);

        for (JsonNode fig : before.get("figures")) {
            if (d.getId().equals(fig.path("element_id").asText())) {
                ((ObjectNode) fig).put("element_id", s.getId());
            }
        }
        assertEquals(before.get("figures"), after.get("figures"));
        assertEquals(before.get("connections"), after.get("connections"));
    }

    // --- S2.4 / FR-13, FR-24

    @Test
    void deletesRelationshipsThatBecomeSelfLoops() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), d1 = comp("D1"), d2 = comp("D2");
        IArchimateRelationship r3 = models.relationship("ServingRelationship", s, d);
        IArchimateRelationship r4 = models.relationship("FlowRelationship", d1, d2);
        IArchimateRelationship r0 = models.relationship("FlowRelationship", s, s); // already a self-loop
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateObject fs = models.figure(v, s, 0, 0, 120, 55);
        IDiagramModelArchimateObject fd = models.figure(v, d, 200, 0, 120, 55);
        IDiagramModelArchimateObject fd1 = models.figure(v, d1, 0, 200, 120, 55);
        IDiagramModelArchimateObject fd2 = models.figure(v, d2, 200, 200, 120, 55);
        IDiagramModelArchimateConnection k3 = models.connection(fs, fd, r3);
        IDiagramModelArchimateConnection k4 = models.connection(fd1, fd2, r4);

        JsonNode r = run(tool, item(s, List.of(d, d1, d2), ""));

        assertFalse(r.has("error"), r.toString());
        assertFalse(exists(r3));
        assertFalse(exists(r4));
        assertTrue(exists(r0), "an existing survivor self-loop is untouched");
        assertNull(ModelAccessor.findConnectionById(v, k3.getId()));
        assertNull(ModelAccessor.findConnectionById(v, k4.getId()));
        assertTrue(fs.getSourceConnections().isEmpty());
        assertTrue(fd.getTargetConnections().isEmpty());
        assertTrue(fd2.getTargetConnections().isEmpty());
        List<String> deleted = texts(r.get("relationships_deleted"));
        assertTrue(deleted.containsAll(List.of(r3.getId(), r4.getId())), deleted.toString());
        assertEquals(2, deleted.size());
        models.assertNoDanglingConnections();
    }

    // --- S2.5 / FR-15, FR-16

    @Test
    void nonConflictingValuesFillIn() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D");
        prop(s, "owner", "A");
        prop(d, "tier", "1");
        d.setDocumentation("Doc D");

        JsonNode r = merge(s, d, "");

        assertFalse(r.has("error"), r.toString());
        assertEquals(List.of("owner=A", "tier=1"), props(s));
        assertEquals("Doc D", s.getDocumentation());
    }

    // --- S2.6 / FR-17

    @Test
    void unresolvedConflictsBlockAndReturnDiff() throws Exception {
        IArchimateElement s = comp("S"), d1 = comp("D1");
        prop(s, "owner", "sales");
        s.setDocumentation("Doc S");
        prop(d1, "owner", "finance");
        d1.setDocumentation("Doc D1");

        JsonNode r = merge(s, d1, "");

        assertEquals("unresolved conflicts", r.path("error").asText(), r.toString());
        JsonNode expected = json("[{'property':'owner','survivor':['sales'],'duplicates':{'" + d1.getId()
                + "':['finance']}},{'documentation':{'survivor':'Doc S','" + d1.getId() + "':'Doc D1'}}]");
        assertEquals(expected, r.get("conflicts"));
        assertTrue(r.has("parallel_relationships"));
        assertTrue(exists(d1));
        assertEquals(List.of("owner=sales"), props(s));
        assertEquals("Doc S", s.getDocumentation());
        assertEquals(0, models.saves);
    }

    // --- S2.7 / FR-17

    @Test
    void resolveAppliesChosenValues() throws Exception {
        IArchimateElement s = comp("S"), d1 = comp("D1");
        prop(s, "owner", "sales");
        s.setDocumentation("Doc S");
        prop(d1, "owner", "finance");
        d1.setDocumentation("Doc D1");

        JsonNode r = merge(s, d1, "'resolve':{'properties':{'owner':'finance'},'documentation':'Doc merged'}");

        assertFalse(r.has("error"), r.toString());
        assertEquals(List.of("owner=finance"), props(s));
        assertEquals("Doc merged", s.getDocumentation());
        assertFalse(exists(d1));
        assertEquals(1, models.saves);
    }

    // --- S2.8 / FR-15

    @Test
    void duplicatesDisagreeWithoutSurvivorValue() throws Exception {
        IArchimateElement s = comp("S"), d1 = comp("D1"), d2 = comp("D2");
        prop(d1, "tier", "1");
        prop(d2, "tier", "2");

        JsonNode r = run(tool, item(s, List.of(d1, d2), ""));

        assertEquals("unresolved conflicts", r.path("error").asText(), r.toString());
        JsonNode expected = json("{'property':'tier','survivor':[],'duplicates':{'" + d1.getId() + "':['1'],'"
                + d2.getId() + "':['2']}}");
        assertTrue(containsNode(r.get("conflicts"), expected), r.get("conflicts").toString());
        assertTrue(s.getProperties().isEmpty());
        assertTrue(exists(d1) && exists(d2));
        assertEquals(0, models.saves);
    }

    // --- S2.8a / FR-15

    @Test
    void repeatedKeysComparedAsOrderedLists() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D");
        prop(s, "tag", "a");
        prop(s, "tag", "b");
        prop(d, "tag", "a");

        JsonNode r = merge(s, d, "");

        JsonNode expected = json("{'property':'tag','survivor':['a','b'],'duplicates':{'" + d.getId() + "':['a']}}");
        assertTrue(containsNode(r.path("conflicts"), expected), r.toString());
        assertTrue(exists(d));
    }

    // --- S2.8a / FR-17

    @Test
    void resolveWithListReplacesAllValuesOfKey() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D");
        prop(s, "x", "1");
        prop(s, "tag", "a");
        prop(s, "y", "2");
        prop(s, "tag", "b");
        prop(d, "tag", "a");

        JsonNode r = merge(s, d, "'resolve':{'properties':{'tag':['a','b','c']}}");

        assertFalse(r.has("error"), r.toString());
        assertEquals(List.of("x=1", "tag=a", "tag=b", "tag=c", "y=2"), props(s));

        IArchimateElement s2 = comp("S2"), d2 = comp("D2");
        prop(s2, "x", "1");
        prop(s2, "tag", "a");
        prop(s2, "tag", "b");
        prop(d2, "tag", "a");

        r = merge(s2, d2, "'resolve':{'properties':{'tag':'a'}}");

        assertFalse(r.has("error"), r.toString());
        assertEquals(List.of("x=1", "tag=a"), props(s2));
    }

    // --- S2.9 / FR-17

    @Test
    void resolveEntryWithoutConflictReturnsCurrentDiff() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D");
        prop(s, "owner", "sales");
        prop(d, "owner", "finance");

        JsonNode r = merge(s, d, "'resolve':{'properties':{'owner':'finance','colour':'red'}}");

        assertTrue(r.path("error").asText().contains("colour"), r.toString());
        JsonNode expected = json("{'property':'owner','survivor':['sales'],'duplicates':{'" + d.getId()
                + "':['finance']}}");
        assertTrue(containsNode(r.path("conflicts"), expected), r.toString());
        assertTrue(r.has("parallel_relationships"));
        assertTrue(exists(d));
        assertEquals(List.of("owner=sales"), props(s));
        assertEquals(0, models.saves);
    }

    // --- S2.9a / FR-18

    @Test
    void differingDuplicateNamesReportedNotBlocked() throws Exception {
        IArchimateElement s = comp("Mantle"), d = comp("Mantle (old)");

        JsonNode r = merge(s, d, "");

        assertFalse(r.has("error"), r.toString());
        assertEquals("Mantle", s.getName());
        assertEquals(json("{'" + d.getId() + "':'Mantle (old)'}"), r.get("duplicate_names"));
        assertFalse(exists(d));
    }

    // --- FR-18

    @Test
    void survivorNameFolderAndSpecializationUnchanged() throws Exception {
        IArchimateElement s = comp("CRM"), d = comp("CRM copy");
        IFolder core = ModelAccessor.findOrCreateFolderByPath(models.model, "Application/Core");
        core.getElements().add(s);
        ModelAccessor.findOrCreateFolderByPath(models.model, "Application/Legacy").getElements().add(d);
        ModelAccessor.applySpecialization(models.model, s, json("{'specialization':'Microservice'}"));
        ModelAccessor.applySpecialization(models.model, d, json("{'specialization':'Legacy'}"));

        JsonNode r = merge(s, d, "");

        assertFalse(r.has("error"), r.toString());
        assertEquals("CRM", s.getName());
        assertSame(core, s.eContainer());
        assertEquals("Microservice", ModelAccessor.getSpecialization(s));
        assertEquals(1, s.getProfiles().size());
    }

    // --- S2.10 / FR-19

    @Test
    void parallelRelationshipsKeptAndReported() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y");
        IArchimateRelationship r5 = models.relationship("ServingRelationship", s, y);
        IArchimateRelationship r6 = models.relationship("ServingRelationship", d, y);

        JsonNode r = merge(s, d, "");

        assertFalse(r.has("error"), r.toString());
        assertTrue(exists(r5) && exists(r6));
        assertSame(s, r6.getSource());
        assertEquals(json("[{'kept':'" + r5.getId() + "','parallel':['" + r6.getId() + "']}]"),
                r.get("parallel_relationships"));
    }

    // --- FR-19: type-specific attributes take part in "parallel"

    @Test
    void differingTypeAttributesNotParallel() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y");
        IArchimateElement data = models.element("DataObject", "Data");
        IAccessRelationship write = (IAccessRelationship) models.relationship("AccessRelationship", s, data);
        write.setAccessType(IAccessRelationship.WRITE_ACCESS);
        IAccessRelationship read = (IAccessRelationship) models.relationship("AccessRelationship", d, data);
        read.setAccessType(IAccessRelationship.READ_ACCESS);
        ((IInfluenceRelationship) models.relationship("InfluenceRelationship", s, y)).setStrength("+");
        ((IInfluenceRelationship) models.relationship("InfluenceRelationship", d, y)).setStrength("++");
        ((IAssociationRelationship) models.relationship("AssociationRelationship", s, y)).setDirected(true);
        ((IAssociationRelationship) models.relationship("AssociationRelationship", d, y)).setDirected(false);

        JsonNode r = merge(s, d, "");

        assertFalse(r.has("error"), r.toString());
        assertEquals(0, r.get("parallel_relationships").size(), r.toString());
    }

    // --- S2.11 / FR-20, FR-21

    @Test
    void collapseMovesConnections() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y");
        IArchimateRelationship r5 = models.relationship("ServingRelationship", s, y);
        IArchimateRelationship r6 = models.relationship("ServingRelationship", d, y);
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateObject fd = models.figure(v, d, 0, 0, 120, 55);
        IDiagramModelArchimateObject fy = models.figure(v, y, 400, 0, 120, 55);
        IDiagramModelArchimateConnection k6 = models.connection(fd, fy, r6);
        IDiagramModelBendpoint bp = IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
        bp.setStartX(10);
        k6.getBendpoints().add(bp);
        String k6Id = k6.getId();

        JsonNode r = merge(s, d, "'collapse':true");

        assertFalse(r.has("error"), r.toString());
        assertFalse(exists(r6));
        assertSame(k6, ModelAccessor.findConnectionById(v, k6Id));
        assertSame(r5, k6.getArchimateRelationship());
        assertEquals(List.of(bp), new ArrayList<>(k6.getBendpoints()));
        assertSame(fd, k6.getSource());
        assertSame(s, fd.getArchimateElement());
        assertSame(fy, k6.getTarget());
        assertEquals(List.of(r6.getId()), texts(r.get("relationships_collapsed")));
        assertEquals(1, r.get("connections_moved").asInt());
        models.assertNoDanglingConnections();
    }

    // --- S2.12 / FR-21

    @Test
    void collapseDropsDoubledConnection() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y");
        IArchimateRelationship r5 = models.relationship("ServingRelationship", s, y);
        IArchimateRelationship r6 = models.relationship("ServingRelationship", d, y);
        IArchimateDiagramModel w = models.view("W");
        IDiagramModelArchimateObject fs = models.figure(w, s, 0, 0, 120, 55);
        IDiagramModelArchimateObject fy = models.figure(w, y, 400, 0, 120, 55);
        IDiagramModelArchimateConnection k5 = models.connection(fs, fy, r5);
        IDiagramModelArchimateConnection k6 = models.connection(fs, fy, r6); // inconsistent on purpose

        JsonNode r = merge(s, d, "'collapse':true");

        assertFalse(r.has("error"), r.toString());
        assertEquals(List.of(k5), new ArrayList<>(fs.getSourceConnections()));
        assertEquals(List.of(k5), new ArrayList<>(fy.getTargetConnections()));
        assertNull(ModelAccessor.findConnectionById(w, k6.getId()));
        assertEquals(1, r.get("connections_removed").asInt());
        models.assertNoDanglingConnections();
    }

    // --- FR-21, second sentence

    @Test
    void collapseRepointsRelationshipOnRelationship() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y"), z = comp("Z");
        IArchimateRelationship r5 = models.relationship("ServingRelationship", s, y);
        IArchimateRelationship r6 = models.relationship("ServingRelationship", d, y);
        IArchimateRelationship ra = IArchimateFactory.eINSTANCE.createAssociationRelationship();
        ra.connect(z, r6);
        models.model.getDefaultFolderForObject(ra).getElements().add(ra);

        JsonNode r = merge(s, d, "'collapse':true");

        assertFalse(r.has("error"), r.toString());
        assertFalse(exists(r6));
        assertSame(r5, ra.getTarget());
        assertSame(z, ra.getSource());
    }

    // --- S2.13 / FR-20

    @Test
    void collapseConflictsGoThroughResolve() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y");
        IArchimateRelationship r5 = models.relationship("ServingRelationship", s, y);
        IArchimateRelationship r6 = models.relationship("ServingRelationship", d, y);
        r5.setDocumentation("a");
        r5.setName("serves");
        r6.setDocumentation("b");
        r6.setName("provides");

        JsonNode r = merge(s, d, "'collapse':true");

        assertEquals("unresolved conflicts", r.path("error").asText(), r.toString());
        JsonNode conflicts = r.get("conflicts");
        assertTrue(containsNode(conflicts, json("{'relationship':'" + r5.getId()
                + "','documentation':{'survivor':'a','" + r6.getId() + "':'b'}}")), conflicts.toString());
        assertTrue(containsNode(conflicts, json("{'relationship':'" + r5.getId()
                + "','name':{'survivor':'serves','" + r6.getId() + "':'provides'}}")), conflicts.toString());
        assertTrue(exists(d) && exists(r6));
        assertSame(d, r6.getSource());

        r = merge(s, d, "'collapse':true,'resolve':{'relationships':{'" + r5.getId()
                + "':{'documentation':'a','name':'serves'}}}");

        assertFalse(r.has("error"), r.toString());
        assertEquals("a", r5.getDocumentation());
        assertEquals("serves", r5.getName());
        assertFalse(exists(r6));
    }

    // --- S2.14 / FR-23

    @Test
    void dryRunReturnsDiffAndImpactWithoutChanges() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y");
        prop(s, "owner", "sales");
        prop(d, "owner", "finance");
        IArchimateRelationship rdy = models.relationship("ServingRelationship", d, y);
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateObject fd = models.figure(v, d, 0, 0, 120, 55);

        JsonNode r = merge(s, d, "'dry_run':true");

        assertFalse(r.has("error"), r.toString());
        assertTrue(r.path("dry_run").asBoolean(), r.toString());
        assertEquals(1, r.get("conflicts").size());
        assertTrue(r.has("parallel_relationships"));
        assertEquals(1, r.get("relationships_repointed").asInt());
        assertEquals(1, r.get("figures_retargeted").asInt());
        assertEquals(List.of(v.getId()), texts(r.get("affected_view_ids")));
        assertTrue(exists(d));
        assertSame(d, rdy.getSource());
        assertSame(d, fd.getArchimateElement());
        assertEquals(List.of("owner=sales"), props(s));
        assertEquals(0, models.saves);
    }

    // --- S2.15

    @Test
    void undoRestoresEverything() throws Exception {
        CommandStack stack = models.commandStack();
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y"), z = comp("Z");
        prop(s, "owner", "sales");
        s.setDocumentation("");
        prop(d, "owner", "finance");
        prop(d, "tier", "1");
        d.setDocumentation("Doc D");
        IArchimateRelationship r1 = models.relationship("FlowRelationship", d, z);   // re-pointed
        IArchimateRelationship r3 = models.relationship("FlowRelationship", s, d);   // self-loop -> deleted
        IArchimateRelationship r5 = models.relationship("ServingRelationship", s, y); // kept
        IArchimateRelationship r6 = models.relationship("ServingRelationship", d, y); // collapsed
        r6.setDocumentation("b");
        prop(r6, "via", "api");
        IArchimateDiagramModel v = models.view("V");
        IDiagramModelArchimateObject fs = models.figure(v, s, 0, 0, 120, 55);
        IDiagramModelArchimateObject fd = models.figure(v, d, 0, 200, 120, 55);
        IDiagramModelArchimateObject fy = models.figure(v, y, 400, 200, 120, 55);
        IDiagramModelArchimateConnection k3 = models.connection(fs, fd, r3);
        IDiagramModelArchimateConnection k6 = models.connection(fd, fy, r6);
        IFolder dFolder = (IFolder) d.eContainer();
        int dIndex = dFolder.getElements().indexOf(d);
        IFolder relFolder = (IFolder) r3.eContainer();
        int r3Index = relFolder.getElements().indexOf(r3);
        int r6Index = relFolder.getElements().indexOf(r6);

        JsonNode r = merge(s, d, "'collapse':true,'resolve':{'properties':{'owner':'finance'}}");
        assertFalse(r.has("error"), r.toString());
        assertFalse(exists(d), "merge must have run before undo is meaningful");

        stack.undo();

        assertSame(d, dFolder.getElements().get(dIndex));
        assertSame(r3, relFolder.getElements().get(r3Index));
        assertSame(r6, relFolder.getElements().get(r6Index));
        assertSame(d, r1.getSource());
        assertSame(z, r1.getTarget());
        assertSame(s, r3.getSource());
        assertSame(d, r3.getTarget());
        assertSame(d, r6.getSource());
        assertSame(r6, k6.getArchimateRelationship());
        assertSame(d, fd.getArchimateElement());
        assertSame(fs, k3.getSource());
        assertSame(fd, k3.getTarget());
        assertTrue(fs.getSourceConnections().contains(k3));
        assertTrue(fd.getTargetConnections().contains(k3));
        assertTrue(fd.getSourceConnections().contains(k6));
        assertEquals(List.of("owner=sales"), props(s));
        assertEquals("", s.getDocumentation());
        assertTrue(r5.getProperties().isEmpty());
        assertEquals("", r5.getDocumentation());
        models.assertNoDanglingConnections();
    }

    // --- S2.16 / FR-11

    @Test
    void rejectsInvalidItemsWithoutChanges() throws Exception {
        IArchimateElement s = comp("S"), d = comp("D"), y = comp("Y");
        IArchimateElement actor = models.element("BusinessActor", "Actor");
        IArchimateRelationship rel = models.relationship("ServingRelationship", s, y);
        String sid = s.getId(), did = d.getId();

        assertError("{'survivor_id':'nope','duplicate_ids':['" + did + "']}", "nope");
        assertError("{'survivor_id':'" + sid + "','duplicate_ids':['ghost']}", "ghost");
        assertError("{'survivor_id':'" + rel.getId() + "','duplicate_ids':['" + did + "']}", rel.getId());
        assertError("{'survivor_id':'" + sid + "','duplicate_ids':['" + sid + "']}", sid);
        assertError("{'survivor_id':'" + sid + "','duplicate_ids':[]}", "duplicate_ids");
        assertError("{'survivor_id':'" + sid + "','duplicate_ids':['" + did + "','" + did + "']}", did);
        assertError("{'survivor_id':'" + sid + "','duplicate_ids':['" + actor.getId() + "']}", actor.getId());
        assertTrue(exists(s) && exists(d) && exists(y) && exists(actor) && exists(rel));
        assertEquals(0, models.saves);

        // a bad item does not stop the rest of the batch
        JsonNode results = run(new ManageElementsTool(), "{'operation':'merge','items':["
                + "{'survivor_id':'" + sid + "','duplicate_ids':['ghost']},"
                + "{'survivor_id':'" + sid + "','duplicate_ids':['" + did + "']}]}").get("results");
        assertTrue(results.get(0).path("error").asText().contains("ghost"), results.toString());
        assertEquals(sid, results.get(1).path("survivor_id").asText(), results.toString());
        assertFalse(exists(d));
    }

    void assertError(String item, String mentioned) {
        Exception e = assertThrows(Exception.class, () -> run(tool, item), item);
        assertTrue(e.getMessage() != null && e.getMessage().contains(mentioned),
                "error for " + item + " should name " + mentioned + ", was: " + e.getMessage());
    }
}

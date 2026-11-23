package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelGroup;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class ValidateModelToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final ValidateModelTool tool = new ValidateModelTool();

    static void prop(IArchimateElement e, String k, String v) {
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey(k);
        p.setValue(v);
        e.getProperties().add(p);
    }

    @Test
    void reportsInvalidRelationshipsWithAlternatives() throws Exception {
        IArchimateElement a = models.element("BusinessActor", "A");
        IArchimateElement b = models.element("BusinessActor", "B");
        models.relationship("AssociationRelationship", a, b);
        IArchimateRelationship bad = models.relationship("AccessRelationship", a, b);
        assertFalse(ArchimateModelUtils.isValidRelationship(a, b, bad.eClass()), "precondition");

        JsonNode r = run(tool, "{}");
        assertEquals(2, r.get("total_relationships").asInt());
        assertEquals(1, r.get("violations").size());
        JsonNode v = r.get("violations").get(0);
        assertEquals(bad.getId(), v.get("relationship_id").asText());
        assertEquals("AccessRelationship", v.get("relationship_type").asText());
        assertEquals("A", v.get("source_name").asText());
        assertTrue(v.get("valid_relationship_types").toString().contains("AssociationRelationship"), v.toString());
        assertFalse(r.has("orphan_elements"));
        assertFalse(r.has("missing_required_properties"));
    }

    @Test
    void optionalChecksForPropertiesOrphansAndVocabularies() throws Exception {
        IArchimateElement placed = models.element("BusinessActor", "Placed");
        IArchimateElement orphan = models.element("BusinessActor", "Orphan");
        prop(placed, "owner", "Ops");
        prop(placed, "status", "live");
        prop(orphan, "status", "bogus");
        models.figure(models.view("V"), placed, 0, 0, 120, 55);

        JsonNode r = run(tool, "{'require_properties':['owner'],'check_orphans':true,"
                + "'property_vocabularies':{'status':['live','retired']}}");
        JsonNode missing = r.get("missing_required_properties");
        assertEquals(1, missing.size());
        assertEquals(orphan.getId(), missing.get(0).get("element_id").asText());
        assertEquals("owner", missing.get(0).get("missing_keys").get(0).asText());
        JsonNode orphans = r.get("orphan_elements");
        assertEquals(1, orphans.size());
        assertEquals("Orphan", orphans.get(0).get("element_name").asText());
        JsonNode vocab = r.get("property_vocabulary_violations");
        assertEquals(1, vocab.size());
        assertEquals("bogus", vocab.get(0).get("value").asText());
        assertEquals(2, vocab.get(0).get("allowed_values").size());
        assertEquals(0, r.get("violations").size());
    }

    @Test
    void failsWithoutOpenModel() {
        models.closeAll();
        Exception ex = assertThrows(Exception.class, () -> run(tool, "{}"));
        assertEquals("No model is currently open in Archi", ex.getMessage());
    }

    // --- dangling_connections (spec figure-removal-dangling-connections, US-3)

    IArchimateDiagramModel v;

    /** Build a target-side ghost on {@link #v}: source figure detached, target still on the view. */
    IDiagramModelArchimateConnection targetSideGhost() {
        IArchimateElement s = models.element("ApplicationComponent", "S1");
        IArchimateElement t = models.element("ApplicationComponent", "T1");
        IDiagramModelGroup g = models.group(v, "G1", 0, 0, 300, 200);
        IDiagramModelArchimateObject fs = models.figure(g, s, 10, 30, 120, 55);
        IDiagramModelArchimateObject ft = models.figure(v, t, 400, 0, 120, 55);
        IDiagramModelArchimateConnection k = models.connection(fs, ft, models.relationship("ServingRelationship", s, t));
        v.getChildren().remove(g);
        return k;
    }

    static Set<String> problems(JsonNode entry) {
        Set<String> out = new TreeSet<>();
        entry.get("problems").forEach(n -> out.add(n.asText()));
        return out;
    }

    /** S3.1 / FR-26: each kind of ghost is reported once, with its problems. */
    @Test
    void reportsEachKindOfDanglingConnection() throws Exception {
        v = models.view("V");
        IArchimateDiagramModel w = models.view("W");
        IDiagramModelArchimateConnection k1 = targetSideGhost();

        IArchimateElement s2 = models.element("ApplicationComponent", "S2");
        IArchimateElement t2 = models.element("ApplicationComponent", "T2");
        IDiagramModelArchimateObject fs2 = models.figure(v, s2, 0, 300, 120, 55);
        IDiagramModelGroup g2 = models.group(v, "G2", 400, 300, 300, 200);
        IDiagramModelArchimateObject ft2 = models.figure(g2, t2, 10, 30, 120, 55);
        IDiagramModelArchimateConnection k2 = models.connection(fs2, ft2, models.relationship("ServingRelationship", s2, t2));
        v.getChildren().remove(g2);

        IDiagramModelArchimateObject fs3 = models.figure(v, models.element("ApplicationComponent", "S3"), 0, 600, 120, 55);
        IDiagramModelArchimateObject ft3 = models.figure(v, models.element("ApplicationComponent", "T3"), 400, 600, 120, 55);
        IDiagramModelConnection k3 = IArchimateFactory.eINSTANCE.createDiagramModelConnection(); // non-ArchiMate line
        k3.connect(fs3, ft3);
        ft3.getTargetConnections().remove(k3);
        k3.setTarget(null);

        IArchimateElement s4 = models.element("ApplicationComponent", "S4");
        IArchimateElement t4 = models.element("ApplicationComponent", "T4");
        IDiagramModelArchimateObject fs4 = models.figure(v, s4, 0, 900, 120, 55);
        IDiagramModelArchimateObject ft4 = models.figure(w, t4, 0, 0, 120, 55);
        IDiagramModelArchimateConnection k4 = models.connection(fs4, ft4, models.relationship("ServingRelationship", s4, t4));

        // a healthy connection that must not be reported
        IArchimateElement ok1 = models.element("ApplicationComponent", "Ok1");
        IArchimateElement ok2 = models.element("ApplicationComponent", "Ok2");
        models.connection(models.figure(v, ok1, 0, 1200, 120, 55), models.figure(v, ok2, 400, 1200, 120, 55),
                models.relationship("ServingRelationship", ok1, ok2));

        JsonNode list = run(tool, "{}").get("dangling_connections");
        assertNotNull(list, "dangling_connections missing");
        assertEquals(4, list.size(), list.toString());
        Map<String, JsonNode> byId = new HashMap<>();
        list.forEach(e -> byId.put(e.get("connection_id").asText(), e));

        JsonNode e1 = byId.get(k1.getId());
        assertEquals(v.getId(), e1.get("view_id").asText());
        assertEquals("V", e1.get("view_name").asText());
        assertEquals(k1.getArchimateRelationship().getId(), e1.get("relationship_id").asText());
        assertEquals(Set.of("source_detached", "connection_detached"), problems(e1));

        assertEquals(Set.of("target_detached"), problems(byId.get(k2.getId())));

        JsonNode e3 = byId.get(k3.getId());
        assertTrue(e3.get("relationship_id").isNull(), e3.toString());
        assertEquals(Set.of("target_missing"), problems(e3));

        JsonNode e4 = byId.get(k4.getId());
        assertEquals(v.getId(), e4.get("view_id").asText(), "listed once, under the view it was first found from");
        assertEquals(Set.of("target_other_view"), problems(e4));
    }

    /** S3.2 / FR-26: a clean model has an empty list. */
    @Test
    void cleanModelHasEmptyDanglingList() throws Exception {
        IArchimateElement a = models.element("ApplicationComponent", "A");
        IArchimateElement b = models.element("ApplicationComponent", "B");
        IArchimateDiagramModel view = models.view("V");
        models.connection(models.figure(view, a, 0, 0, 120, 55), models.figure(view, b, 400, 0, 120, 55),
                models.relationship("ServingRelationship", a, b));

        JsonNode list = run(tool, "{}").get("dangling_connections");
        assertNotNull(list, "dangling_connections missing");
        assertTrue(list.isArray());
        assertEquals(0, list.size());
    }

    /** S3.3 / FR-27: a reported ghost can be removed by its connection_id. */
    @Test
    void reportedGhostCanBeRemovedByConnectionId() throws Exception {
        v = models.view("V");
        targetSideGhost();
        JsonNode list = run(tool, "{}").get("dangling_connections");
        assertNotNull(list, "dangling_connections missing");
        assertEquals(1, list.size());
        JsonNode e = list.get(0);

        run(new DeleteConnectionTool(), "{'view_id':'" + e.get("view_id").asText()
                + "','connection_id':'" + e.get("connection_id").asText() + "'}");

        assertEquals(0, run(tool, "{}").get("dangling_connections").size());
    }
}

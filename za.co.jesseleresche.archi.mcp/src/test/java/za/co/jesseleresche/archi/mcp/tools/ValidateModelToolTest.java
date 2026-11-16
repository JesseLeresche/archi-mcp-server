package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateRelationship;
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
}

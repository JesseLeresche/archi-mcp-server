package za.co.jesseleresche.archi.mcp.tools;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class GetRelationshipsBetweenToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final GetRelationshipsBetweenTool tool = new GetRelationshipsBetweenTool();

    IArchimateElement a, b, c;
    IArchimateRelationship ab, ba;

    @BeforeEach
    void setUp() {
        a = models.element("BusinessActor", "A");
        b = models.element("BusinessActor", "B");
        c = models.element("BusinessActor", "C");
        ab = models.relationship("AssociationRelationship", a, b);
        ab.setName("ab");
        ba = models.relationship("FlowRelationship", b, a);
        models.relationship("AssociationRelationship", a, c);
    }

    @Test
    void findsBothDirectionsByDefault() throws Exception {
        JsonNode r = run(tool, "{'element_a_id':'" + a.getId() + "','element_b_id':'" + b.getId() + "'}").get("results");
        assertEquals(2, r.size());
        assertEquals(ab.getId(), r.get(0).get("relationship_id").asText());
        assertEquals("AssociationRelationship", r.get(0).get("relationship_type").asText());
        assertEquals("ab", r.get(0).get("relationship_name").asText());
        assertEquals(ba.getId(), r.get(1).get("relationship_id").asText());
        assertEquals("", r.get(1).get("relationship_name").asText());
        assertEquals(b.getId(), r.get(1).get("source_id").asText());
    }

    @Test
    void sourceToTargetOnlyFiltersReverse() throws Exception {
        JsonNode r = run(tool, "{'element_a_id':'" + a.getId() + "','element_b_id':'" + b.getId()
                + "','direction':'source_to_target'}").get("results");
        assertEquals(1, r.size());
        assertEquals(ab.getId(), r.get(0).get("relationship_id").asText());
    }

    @Test
    void errorsOnUnknownElement() {
        Exception ex = assertThrows(Exception.class,
                () -> run(tool, "{'element_a_id':'" + a.getId() + "','element_b_id':'ghost'}"));
        assertEquals("Element not found: ghost", ex.getMessage());
    }

    @Test
    void includesPropertiesAndSpecializationWhenAsked() throws Exception {
        var r = models.relationship("ServingRelationship", a, b);
        var p = com.archimatetool.model.IArchimateFactory.eINSTANCE.createProperty();
        p.setKey("k");
        p.setValue("v");
        r.getProperties().add(p);
        ModelAccessor.applySpecialization(models.model, r, TestModels.json("{'specialization':'Feeds'}"));
        JsonNode out = run(tool, "{'element_a_id':'" + a.getId() + "','element_b_id':'" + b.getId()
                + "','include_properties':true}").get("results");
        JsonNode e = null;
        for (JsonNode x : out) {
            if (x.get("relationship_id").asText().equals(r.getId())) e = x;
        }
        assertEquals("v", e.get("properties").get(0).get("value").asText());
        assertEquals("Feeds", e.get("specialization").asText());
    }
}

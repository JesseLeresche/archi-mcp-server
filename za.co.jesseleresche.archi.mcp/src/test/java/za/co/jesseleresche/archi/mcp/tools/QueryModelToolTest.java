package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.run;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

class QueryModelToolTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final QueryModelTool tool = new QueryModelTool();

    private static List<String> names(JsonNode r) {
        List<String> out = new ArrayList<>();
        r.forEach(n -> out.add(n.get("name").asText()));
        return out;
    }

    private static void prop(IArchimateElement e, String k, String v) {
        IProperty p = IArchimateFactory.eINSTANCE.createProperty();
        p.setKey(k);
        p.setValue(v);
        e.getProperties().add(p);
    }

    @Test
    void filtersByTypeLayerAndName() throws Exception {
        IArchimateElement crm = models.element("ApplicationComponent", "CRM System");
        crm.setDocumentation("customer stuff");
        models.element("BusinessActor", "Customer");
        models.element("Node", "Server");
        models.element("Goal", "Grow");

        JsonNode all = run(tool, "{}");
        assertEquals(4, all.size());

        JsonNode app = run(tool, "{'type_filter':'applicationcomponent'}");
        assertEquals(List.of("CRM System"), names(app));
        assertEquals("Application", app.get(0).get("layer").asText());
        assertEquals("customer stuff", app.get(0).get("documentation").asText());
        assertEquals(crm.getId(), app.get(0).get("id").asText());

        assertEquals(List.of("Server"), names(run(tool, "{'layer_filter':'Technology'}")));
        assertEquals(List.of("Grow"), names(run(tool, "{'layer_filter':'Motivation'}")));
        // name_search is a case-insensitive substring match (documentation is not searched)
        assertEquals(List.of("Customer"), names(run(tool, "{'name_search':'CUST'}")));
    }

    @Test
    void filtersByPropertyAndIncludesProperties() throws Exception {
        IArchimateElement a = models.element("BusinessActor", "A");
        prop(a, "bian.id", "SD-001");
        IArchimateElement b = models.element("BusinessActor", "B");
        prop(b, "bian.id", "XX-002");
        models.element("BusinessActor", "C");

        assertEquals(List.of("A", "B"), names(run(tool, "{'property_key':'bian.id'}")));
        assertEquals(List.of("B"), names(run(tool, "{'property_key':'bian.id','property_value':'XX-002'}")));
        assertEquals(List.of("A"), names(run(tool, "{'property_key':'bian.id','property_value_prefix':'SD-'}")));

        JsonNode withProps = run(tool, "{'name_search':'A','include_properties':true}");
        assertEquals("SD-001", withProps.get(0).get("properties").get(0).get("value").asText());
        assertFalse(run(tool, "{'name_search':'A'}").get(0).has("properties"));
    }

    @Test
    void scopesToFolderWithOrWithoutSubfolders() throws Exception {
        IFolder top = ModelAccessor.findOrCreateFolderByPath(models.model, "Application/Top");
        IFolder sub = ModelAccessor.findOrCreateFolderByPath(models.model, "Application/Top/Sub");
        IArchimateElement x = models.element("ApplicationComponent", "X");
        IArchimateElement y = models.element("ApplicationComponent", "Y");
        models.element("ApplicationComponent", "Z");
        top.getElements().add(x);
        sub.getElements().add(y);

        assertEquals(List.of("X", "Y"), names(run(tool, "{'folder_id':'" + top.getId() + "'}")));
        assertEquals(List.of("X"),
                names(run(tool, "{'folder_id':'" + top.getId() + "','include_subfolders':false}")));

        Exception e = assertThrows(Exception.class, () -> run(tool, "{'folder_id':'nope'}"));
        assertTrue(e.getMessage().contains("Folder not found: nope"), e.getMessage());
    }

    @Test
    void failsWithNoOpenModel() {
        models.closeAll();
        Exception e = assertThrows(Exception.class, () -> run(tool, "{}"));
        assertTrue(e.getMessage().contains("No model is currently open"), e.getMessage());
    }
}

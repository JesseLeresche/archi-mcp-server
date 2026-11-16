package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

class ToolRegistryTest {

    static final List<String> EXPECTED = List.of("manage_models", "query_model", "get_views",
            "manage_elements", "manage_relationships", "manage_views", "manage_view_content",
            "manage_folders", "manage_appearance", "inspect_view", "get_element_analysis",
            "validate_model", "export_view_as_image", "create_sd_overview_view");

    final ToolRegistry registry = new ToolRegistry();

    @Test
    void descriptorsListTheFourteenToolsInOrder() {
        List<ObjectNode> d = registry.getDescriptors();
        assertEquals(EXPECTED, d.stream().map(n -> n.get("name").asText()).toList());
        for (ObjectNode n : d) {
            assertFalse(n.get("description").asText().isBlank(), n.toString());
            assertTrue(n.get("inputSchema").isObject(), n.toString());
        }
    }

    @Test
    void lookupByName() {
        assertInstanceOf(ManageElementsTool.class, registry.get("manage_elements"));
        assertEquals("query_model", registry.get("query_model").getName());
        assertNull(registry.get("bulk_create_elements"));
        assertNull(registry.get("nope"));
    }
}

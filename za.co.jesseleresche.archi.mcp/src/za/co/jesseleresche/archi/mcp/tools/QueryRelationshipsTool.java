package za.co.jesseleresche.archi.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Model-wide relationship query: one filter object in ({@code element_id?, type_filter?,
 * on_view?, model?, output_file?}), {@code {count, relationships}} or
 * {@code {output_file, count}} out. Internal helper behind
 * {@code manage_relationships query}; not registered.
 */
public class QueryRelationshipsTool implements ITool {

    @Override
    public String getName() {
        return "query_relationships";
    }

    @Override
    public String getDescription() {
        return "Query relationships across the whole model.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }
}

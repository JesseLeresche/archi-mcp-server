package za.co.jesseleresche.archi.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated read-only view inspection tool. Replaces get_view_layout,
 * get_view_connections and get_connection.
 */
public class InspectViewTool extends ConsolidatedTool {

    private final GetViewLayoutTool layout = new GetViewLayoutTool();
    private final GetViewConnectionsTool connections = new GetViewConnectionsTool();
    private final GetConnectionTool connection = new GetConnectionTool();

    @Override
    public String getName() {
        return "inspect_view";
    }

    @Override
    public String getDescription() {
        return "Inspect a view (read-only). Set 'operation' and 'view_id'. Fields by operation — "
                + "layout: figure positions/sizes, optional {element_id?|figure_id?} filter; "
                + "connections: visual connections, optional {relationship_id?} filter; "
                + "get_connection: one connection's visual properties via {connection_id?|relationship_id?}.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, new String[] {"layout", "connections", "get_connection"});
        addStringProp(props, "view_id", "ID of the view to inspect.");
        addStringProp(props, "element_id", "For layout: filter to this element's figure.");
        addStringProp(props, "figure_id", "For layout: filter to this figure ID.");
        addStringProp(props, "relationship_id",
                "For connections (filter) or get_connection (identify the connection).");
        addStringProp(props, "connection_id", "For get_connection: identify the visual connection.");
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        required.add("view_id");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        switch (operation) {
            case "layout":
                return layout.execute(args);
            case "connections":
                return connections.execute(args);
            case "get_connection":
                return connection.execute(args);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: layout, connections, get_connection");
        }
    }
}

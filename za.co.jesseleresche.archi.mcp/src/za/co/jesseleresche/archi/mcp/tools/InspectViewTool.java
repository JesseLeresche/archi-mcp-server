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
    private final GetViewDefinitionTool definition = new GetViewDefinitionTool();

    @Override
    public String getName() {
        return "inspect_view";
    }

    @Override
    public String getDescription() {
        return "Inspect a view (read-only). Set 'operation' and 'view_id'. Fields by operation — "
                + "layout: figure positions/sizes, optional {element_id?|figure_id?} filter; "
                + "connections: visual connections, optional {relationship_id?} filter; "
                + "get_connection: one connection's visual properties via {connection_id?|relationship_id?}; "
                + "(layout's element_id and get_connection's relationship_id fail if drawn more than once; pass "
                + "figure_id/connection_id); "
                + "definition: the full replayable view {view, elements, relationships, figures, connections} "
                + "using the same field names as manage_views/manage_elements/manage_relationships create and "
                + "manage_view_content add_figure/add_connection (figures parent-before-child), optional "
                + "{model?: name or ID of any open model (read-only, selection unchanged), output_file?: bare "
                + ".json name written to the exchange folder ~/.archi-mcp/exchange, never overwritten}.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, new String[] {"layout", "connections", "get_connection", "definition"});
        addStringProp(props, "view_id", "ID of the view to inspect.");
        addStringProp(props, "element_id", "For layout: filter to this element's figure (fails if it has several; pass figure_id).");
        addStringProp(props, "figure_id", "For layout: filter to this figure ID.");
        addStringProp(props, "relationship_id",
                "For connections (filter) or get_connection (identify the connection).");
        addStringProp(props, "connection_id", "For get_connection: identify the visual connection.");
        addStringProp(props, "model", "For definition: name or ID of the open model to read (default: selected).");
        addStringProp(props, "output_file", "For definition: bare .json file name to write in the exchange folder.");
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        required.add("view_id");
        return schema;
    }

    @Override
    protected String executeOperation(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        switch (operation) {
            case "layout":
                return layout.execute(args);
            case "connections":
                return connections.execute(args);
            case "get_connection":
                return connection.execute(args);
            case "definition":
                return definition.execute(args);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: layout, connections, get_connection, definition");
        }
    }
}

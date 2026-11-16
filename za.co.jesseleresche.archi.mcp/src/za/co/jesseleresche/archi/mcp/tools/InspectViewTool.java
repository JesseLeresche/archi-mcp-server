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
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public String getDescription() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public ObjectNode getInputSchema() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        throw new UnsupportedOperationException("stub");
    }
}

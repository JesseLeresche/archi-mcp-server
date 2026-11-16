package za.co.jesseleresche.archi.mcp.protocol;

import java.util.List;
import za.co.jesseleresche.archi.mcp.resources.ResourceRegistry;
import za.co.jesseleresche.archi.mcp.tools.ITool;
import za.co.jesseleresche.archi.mcp.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Routes JSON-RPC 2.0 method calls to the appropriate MCP handler.
 *
 * <p>Handles the MCP lifecycle (initialize, notifications/initialized),
 * tool discovery (tools/list), tool execution (tools/call),
 * and resource access (resources/list, resources/read).
 */
public class McpDispatcher {

    private static final ObjectMapper MAPPER = ObjectMapperHolder.MAPPER;

    private final ToolRegistry registry;

    private final ResourceRegistry resources = ResourceRegistry.getInstance();

    public McpDispatcher(ToolRegistry registry) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Dispatch an MCP request to the appropriate handler.
     *
     * @return the response, or null for notifications (no response expected)
     */
    public McpResponse dispatch(McpRequest request) {
        throw new UnsupportedOperationException("stub");
    }

    private McpResponse handleInitialize(McpRequest request) {
        throw new UnsupportedOperationException("stub");
    }

    private McpResponse handleToolsList(McpRequest request) {
        throw new UnsupportedOperationException("stub");
    }

    private McpResponse handleToolsCall(McpRequest request) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * resources/list — returns all registered resource descriptors.
     */
    private McpResponse handleResourcesList(McpRequest request) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * resources/read — returns the content of a single resource by URI.
     */
    private McpResponse handleResourcesRead(McpRequest request) {
        throw new UnsupportedOperationException("stub");
    }
}

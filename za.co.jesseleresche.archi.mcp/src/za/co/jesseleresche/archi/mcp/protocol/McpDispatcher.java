package za.co.jesseleresche.archi.mcp.protocol;

import za.co.jesseleresche.archi.mcp.tools.ITool;
import za.co.jesseleresche.archi.mcp.tools.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Routes JSON-RPC 2.0 method calls to the appropriate MCP handler.
 * <p>
 * Handles the MCP lifecycle (initialize, notifications/initialized),
 * tool discovery (tools/list), and tool execution (tools/call).
 */
public class McpDispatcher {

    private static final ObjectMapper MAPPER = ObjectMapperHolder.MAPPER;

    private final ToolRegistry registry;

    public McpDispatcher(ToolRegistry registry) {
        this.registry = registry;
    }

    /**
     * Dispatch an MCP request to the appropriate handler.
     *
     * @return the response, or null for notifications (no response expected)
     */
    public McpResponse dispatch(McpRequest request) {
        return switch (request.getMethod()) {
            case "initialize"                -> handleInitialize(request);
            case "notifications/initialized" -> null; // notification, no response
            case "tools/list"                -> handleToolsList(request);
            case "tools/call"                -> handleToolsCall(request);
            default                          -> McpResponse.methodNotFound(request.getId());
        };
    }

    private McpResponse handleInitialize(McpRequest request) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = MAPPER.createObjectNode();
        capabilities.set("tools", MAPPER.createObjectNode());
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", "archi-mcp");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        return McpResponse.success(request.getId(), result);
    }

    private McpResponse handleToolsList(McpRequest request) {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode toolsArray = MAPPER.createArrayNode();
        for (ObjectNode descriptor : registry.getDescriptors()) {
            toolsArray.add(descriptor);
        }
        result.set("tools", toolsArray);
        return McpResponse.success(request.getId(), result);
    }

    private McpResponse handleToolsCall(McpRequest request) {
        JsonNode params = request.getParams();
        if (params == null) {
            return McpResponse.invalidParams(request.getId(), "Missing params");
        }

        String toolName = params.has("name") ? params.get("name").asText() : null;
        if (toolName == null) {
            return McpResponse.invalidParams(request.getId(), "Missing tool name");
        }

        ITool tool = registry.get(toolName);
        if (tool == null) {
            return McpResponse.methodNotFound(request.getId());
        }

        JsonNode arguments = params.has("arguments") ? params.get("arguments") : MAPPER.createObjectNode();

        try {
            String resultString = tool.execute(arguments);

            ObjectNode result = MAPPER.createObjectNode();
            ArrayNode content = MAPPER.createArrayNode();
            ObjectNode textContent = MAPPER.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", resultString);
            content.add(textContent);
            result.set("content", content);

            return McpResponse.success(request.getId(), result);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return McpResponse.appError(request.getId(), message);
        }
    }
}

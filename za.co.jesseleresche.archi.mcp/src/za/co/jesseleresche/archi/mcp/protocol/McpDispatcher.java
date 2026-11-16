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
            case "resources/list"            -> handleResourcesList(request);
            case "resources/read"            -> handleResourcesRead(request);
            default                          -> McpResponse.methodNotFound(request.getId());
        };
    }

    private McpResponse handleInitialize(McpRequest request) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");

        ObjectNode capabilities = MAPPER.createObjectNode();
        capabilities.set("tools", MAPPER.createObjectNode());
        // Advertise static resource support (no subscriptions needed)
        ObjectNode resourcesCap = MAPPER.createObjectNode();
        resourcesCap.put("subscribe", false);
        resourcesCap.put("listChanged", false);
        capabilities.set("resources", resourcesCap);
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
            List<ObjectNode> contentBlocks = tool.executeWithContent(arguments);

            ObjectNode result = MAPPER.createObjectNode();
            ArrayNode content = MAPPER.createArrayNode();
            for (ObjectNode block : contentBlocks) {
                content.add(block);
            }
            result.set("content", content);

            return McpResponse.success(request.getId(), result);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return McpResponse.appError(request.getId(), message);
        }
    }

    /**
     * resources/list — returns all registered resource descriptors.
     */
    private McpResponse handleResourcesList(McpRequest request) {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode list = MAPPER.createArrayNode();

        for (ResourceRegistry.ResourceDescriptor r : resources.list()) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("uri",         r.uri());
            node.put("name",        r.name());
            node.put("description", r.description());
            node.put("mimeType",    r.mimeType());
            list.add(node);
        }

        result.set("resources", list);
        return McpResponse.success(request.getId(), result);
    }

    /**
     * resources/read — returns the content of a single resource by URI.
     */
    private McpResponse handleResourcesRead(McpRequest request) {
        JsonNode params = request.getParams();
        if (params == null || !params.has("uri")) {
            return McpResponse.invalidParams(request.getId(), "Missing 'uri' param");
        }

        String uri = params.get("uri").asText();

        try {
            String content = resources.read(uri);

            // MCP resources/read response: { contents: [ { uri, mimeType, text } ] }
            ObjectNode contentItem = MAPPER.createObjectNode();
            contentItem.put("uri",      uri);
            contentItem.put("mimeType", "text/markdown");
            contentItem.put("text",     content);

            ArrayNode contents = MAPPER.createArrayNode();
            contents.add(contentItem);

            ObjectNode result = MAPPER.createObjectNode();
            result.set("contents", contents);

            return McpResponse.success(request.getId(), result);

        } catch (IllegalArgumentException e) {
            return McpResponse.invalidParams(request.getId(), e.getMessage());
        } catch (Exception e) {
            return McpResponse.appError(request.getId(),
                    "Failed to read resource: " + e.getMessage());
        }
    }
}

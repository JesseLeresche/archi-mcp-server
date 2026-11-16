package za.co.jesseleresche.archi.mcp.tools;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Interface for MCP tool implementations.
 * Each tool provides a name, description, input schema, and execution logic.
 */
public interface ITool {
    String getName();
    String getDescription();
    ObjectNode getInputSchema();
    String execute(JsonNode args) throws Exception;

    /**
     * Execute the tool and return MCP content blocks.
     * Override this for tools that return non-text content (e.g. images).
     * Default implementation wraps the text result from {@link #execute}.
     */
    default List<ObjectNode> executeWithContent(JsonNode args) throws Exception {
        String text = execute(args);
        ObjectNode block = ToolRegistry.MAPPER.createObjectNode();
        block.put("type", "text");
        block.put("text", text);
        return List.of(block);
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import za.co.jesseleresche.archi.mcp.protocol.ObjectMapperHolder;

/**
 * Registry of all MCP tools. Provides tool lookup and descriptor generation
 * for the tools/list MCP method.
 */
public class ToolRegistry {

    static final ObjectMapper MAPPER = ObjectMapperHolder.get();

    private final Map<String, ITool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        throw new UnsupportedOperationException("stub");
    }

    private void register(ITool tool) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Returns MCP tool descriptors with name, description, and inputSchema for each tool.
     */
    public List<ObjectNode> getDescriptors() {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Look up a tool by name.
     * @return the tool, or null if not found
     */
    public ITool get(String name) {
        throw new UnsupportedOperationException("stub");
    }
}

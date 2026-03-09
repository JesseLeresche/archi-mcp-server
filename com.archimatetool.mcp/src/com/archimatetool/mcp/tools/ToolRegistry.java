package com.archimatetool.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.archimatetool.mcp.protocol.ObjectMapperHolder;

/**
 * Registry of all MCP tools. Provides tool lookup and descriptor generation
 * for the tools/list MCP method.
 */
public class ToolRegistry {

    static final ObjectMapper MAPPER = ObjectMapperHolder.get();

    private final Map<String, ITool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new QueryModelTool());
        register(new GetViewsTool());
        register(new CreateElementTool());
        register(new CreateRelationshipTool());
        register(new CreateViewTool());
        register(new AddElementToViewTool());
        register(new AddRelationshipToViewTool());
    }

    private void register(ITool tool) {
        tools.put(tool.getName(), tool);
    }

    /**
     * Returns MCP tool descriptors with name, description, and inputSchema for each tool.
     */
    public List<ObjectNode> getDescriptors() {
        List<ObjectNode> descriptors = new ArrayList<>();
        for (ITool tool : tools.values()) {
            ObjectNode descriptor = MAPPER.createObjectNode();
            descriptor.put("name", tool.getName());
            descriptor.put("description", tool.getDescription());
            descriptor.set("inputSchema", tool.getInputSchema());
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    /**
     * Look up a tool by name.
     * @return the tool, or null if not found
     */
    public ITool get(String name) {
        return tools.get(name);
    }
}

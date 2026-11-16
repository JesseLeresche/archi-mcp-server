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
        // Model session & querying
        register(new ManageModelsTool());
        register(new QueryModelTool());
        register(new GetViewsTool());

        // Consolidated CRUD / content tools (operation + items convention)
        register(new ManageElementsTool());
        register(new ManageRelationshipsTool());
        register(new ManageViewsTool());
        register(new ManageViewContentTool());
        register(new ManageFoldersTool());
        register(new ManageAppearanceTool());

        // Read-only inspection & analysis
        register(new InspectViewTool());
        register(new GetElementAnalysisTool());
        register(new ValidateModelTool());

        // Specialised / standalone
        register(new ExportViewAsImageTool());
        register(new CreateSdOverviewViewTool());
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

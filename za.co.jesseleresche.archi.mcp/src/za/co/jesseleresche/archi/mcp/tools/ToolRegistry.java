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
        register(new ListModelsTool());
        register(new SelectModelTool());
        register(new QueryModelTool());
        register(new GetViewsTool());
        register(new CreateElementTool());
        register(new CreateRelationshipTool());
        register(new CreateViewTool());
        register(new AddElementToViewTool());
        register(new AddRelationshipToViewTool());
        register(new UpdateFigureAppearanceTool());
        register(new UpdateElementTool());
        register(new GetElementAnalysisTool());
        register(new DeleteViewTool());
        register(new DeleteElementTool());
        register(new CreateFolderTool());
        register(new BulkCreateElementsTool());
        register(new BulkUpdateElementsTool());
        register(new BulkCreateRelationshipsTool());
        register(new BulkAddElementsToViewTool());
        register(new BulkAddRelationshipsToViewTool());
        register(new MoveElementToFolderTool());
        register(new BulkMoveElementsToFolderTool());
        register(new MoveViewToFolderTool());
        register(new BulkMoveViewsToFolderTool());
        register(new DeleteConnectionTool());
        register(new UpdateConnectionTool());
        register(new GetViewLayoutTool());
        register(new RemoveFigureFromViewTool());
        register(new ListFolderContentsTool());
        register(new GetFolderTreeTool());
        register(new GetConnectionTool());
        register(new DuplicateViewTool());
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

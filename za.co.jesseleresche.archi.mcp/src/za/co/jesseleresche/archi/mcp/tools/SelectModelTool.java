package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.archimatetool.model.IArchimateModel;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

/**
 * MCP tool that selects which open model to use for subsequent tool calls.
 */
public class SelectModelTool implements ITool {

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

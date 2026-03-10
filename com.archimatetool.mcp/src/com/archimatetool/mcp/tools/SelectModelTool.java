package com.archimatetool.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.mcp.util.ModelAccessor;

/**
 * MCP tool that selects which open model to use for subsequent tool calls.
 */
public class SelectModelTool implements ITool {

    @Override
    public String getName() {
        return "select_model";
    }

    @Override
    public String getDescription() {
        return "Select which open model to use by name or ID. All subsequent tool calls will operate on the selected model.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode nameOrId = properties.putObject("name_or_id");
        nameOrId.put("type", "string");
        nameOrId.put("description", "The name or ID of the model to select (name matching is case-insensitive)");

        ArrayNode required = schema.putArray("required");
        required.add("name_or_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String nameOrId = args.get("name_or_id").asText();

        IArchimateModel model = ModelAccessor.selectModel(nameOrId);
        if (model == null) {
            throw new Exception("No open model found matching: " + nameOrId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", model.getId());
        result.put("name", model.getName());
        result.put("selected", true);
        result.put("success", true);

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

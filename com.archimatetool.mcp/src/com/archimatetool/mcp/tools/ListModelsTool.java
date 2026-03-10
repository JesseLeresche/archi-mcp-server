package com.archimatetool.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.mcp.util.ModelAccessor;

/**
 * MCP tool that lists all open models in Archi, indicating which one is currently selected.
 */
public class ListModelsTool implements ITool {

    @Override
    public String getName() {
        return "list_models";
    }

    @Override
    public String getDescription() {
        return "List all open models in Archi. Shows which model is currently selected for use by other tools.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        List<IArchimateModel> models = ModelAccessor.getAllModels();
        if (models.isEmpty()) {
            throw new Exception("No models are currently open in Archi");
        }

        IArchimateModel selected = ModelAccessor.getOpenModel();
        List<Map<String, Object>> results = new ArrayList<>();

        for (IArchimateModel model : models) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", model.getId());
            entry.put("name", model.getName());
            entry.put("selected", model == selected);
            results.add(entry);
        }

        return ToolRegistry.MAPPER.writeValueAsString(results);
    }
}

package com.archimatetool.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.util.ModelAccessor;
import com.archimatetool.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Updates multiple ArchiMate elements (name, documentation, properties) in a single call.
 */
public class BulkUpdateElementsTool implements ITool {

    @Override
    public String getName() {
        return "bulk_update_elements";
    }

    @Override
    public String getDescription() {
        return "Update name, documentation, and/or custom properties on multiple elements "
                + "in a single call. Returns a result entry per element.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode updates = properties.putObject("updates");
        updates.put("type", "array");
        updates.put("description", "List of element updates");
        ObjectNode items = updates.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("element_id").put("type", "string");
        itemProps.putObject("name").put("type", "string");
        itemProps.putObject("documentation").put("type", "string");
        ObjectNode propsSchema = itemProps.putObject("properties");
        propsSchema.put("type", "array");
        ObjectNode propsItems = propsSchema.putObject("items");
        propsItems.put("type", "object");
        ObjectNode propsItemProps = propsItems.putObject("properties");
        propsItemProps.putObject("key").put("type", "string");
        propsItemProps.putObject("value").put("type", "string");
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("element_id");

        ArrayNode required = schema.putArray("required");
        required.add("updates");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode updatesNode = args.get("updates");
        if (!updatesNode.isArray() || updatesNode.isEmpty()) {
            throw new Exception("'updates' must be a non-empty array");
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : updatesNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String elementId = item.get("element_id").asText();
                entry.put("element_id", elementId);
                try {
                    IArchimateElement element = ModelAccessor.findElementById(model, elementId);
                    if (element == null) {
                        entry.put("success", false);
                        entry.put("error", "Element not found: " + elementId);
                        entries.add(entry);
                        continue;
                    }

                    if (item.has("name")) {
                        element.setName(item.get("name").asText());
                        entry.put("name", element.getName());
                    }
                    if (item.has("documentation")) {
                        element.setDocumentation(item.get("documentation").asText());
                    }
                    if (item.has("properties") && item.get("properties").isArray()) {
                        for (JsonNode prop : item.get("properties")) {
                            setProperty(element, prop.get("key").asText(), prop.get("value").asText());
                        }
                    }
                    entry.put("success", true);
                } catch (Exception e) {
                    entry.put("success", false);
                    entry.put("error", e.getMessage());
                }
                entries.add(entry);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        response.put("total", results.size());
        response.put("succeeded", results.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count());
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }

    private void setProperty(IArchimateElement element, String key, String value) {
        for (IProperty prop : element.getProperties()) {
            if (key.equals(prop.getKey())) {
                prop.setValue(value);
                return;
            }
        }
        IProperty newProp = IArchimateFactory.eINSTANCE.createProperty();
        newProp.setKey(key);
        newProp.setValue(value);
        element.getProperties().add(newProp);
    }
}

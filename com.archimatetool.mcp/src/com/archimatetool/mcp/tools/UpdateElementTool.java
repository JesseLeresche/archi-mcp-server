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
 * Updates the properties of an existing ArchiMate element: name, documentation,
 * and custom key/value properties.
 */
public class UpdateElementTool implements ITool {

    @Override
    public String getName() {
        return "update_element";
    }

    @Override
    public String getDescription() {
        return "Update an existing ArchiMate element's name, documentation, "
                + "or custom properties (key/value pairs).";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description", "ID of the element to update");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "New element name");

        ObjectNode documentation = properties.putObject("documentation");
        documentation.put("type", "string");
        documentation.put("description", "New documentation text");

        ObjectNode props = properties.putObject("properties");
        props.put("type", "array");
        props.put("description",
                "Custom properties to set as key/value pairs. "
                        + "Existing properties with matching keys are updated; "
                        + "new keys are added.");
        ObjectNode items = props.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        ObjectNode key = itemProps.putObject("key");
        key.put("type", "string");
        ObjectNode value = itemProps.putObject("value");
        value.put("type", "string");
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("key");
        itemRequired.add("value");

        ArrayNode required = schema.putArray("required");
        required.add("element_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String elementId = args.get("element_id").asText();

        IArchimateElement element =
                ModelAccessor.findElementById(model, elementId);
        if (element == null) {
            throw new Exception("Element not found: " + elementId);
        }

        String newName = args.has("name")
                ? args.get("name").asText() : null;
        String newDoc = args.has("documentation")
                ? args.get("documentation").asText() : null;
        JsonNode propsNode = args.has("properties")
                ? args.get("properties") : null;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("element_id", element.getId());

            if (newName != null) {
                element.setName(newName);
                entry.put("name", newName);
            }
            if (newDoc != null) {
                element.setDocumentation(newDoc);
                entry.put("documentation", newDoc);
            }
            if (propsNode != null && propsNode.isArray()) {
                List<Map<String, String>> updatedProps = new ArrayList<>();
                for (JsonNode propEntry : propsNode) {
                    String key = propEntry.get("key").asText();
                    String value = propEntry.get("value").asText();
                    setProperty(element, key, value);
                    Map<String, String> kv = new LinkedHashMap<>();
                    kv.put("key", key);
                    kv.put("value", value);
                    updatedProps.add(kv);
                }
                entry.put("properties", updatedProps);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    /**
     * Sets a property on an element. If a property with the given key already
     * exists, its value is updated. Otherwise a new property is created.
     */
    private void setProperty(IArchimateElement element,
            String key, String value) {
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

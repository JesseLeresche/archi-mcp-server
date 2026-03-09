package com.archimatetool.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.util.ModelAccessor;
import com.archimatetool.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates a new ArchiMate element and adds it to the model's default folder for that type.
 */
public class CreateElementTool implements ITool {

    @Override
    public String getName() {
        return "create_element";
    }

    @Override
    public String getDescription() {
        return "Create a new ArchiMate element in the open model.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "Element name");

        ObjectNode type = properties.putObject("type");
        type.put("type", "string");
        type.put("description", "ArchiMate element type, e.g. ApplicationComponent");

        ObjectNode documentation = properties.putObject("documentation");
        documentation.put("type", "string");
        documentation.put("description", "Optional documentation");

        ArrayNode required = schema.putArray("required");
        required.add("name");
        required.add("type");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String name = args.get("name").asText();
        String typeName = args.get("type").asText();
        String documentation = args.has("documentation") ? args.get("documentation").asText() : null;

        EClass eClass = ModelAccessor.resolveElementClass(typeName);
        if (eClass == null) {
            throw new Exception("Unknown ArchiMate element type: " + typeName);
        }

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
            IArchimateElement element = (IArchimateElement) eObject;
            element.setName(name);
            if (documentation != null) {
                element.setDocumentation(documentation);
            }

            model.getDefaultFolderForObject(element).getElements().add(element);
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", element.getId());
            entry.put("name", element.getName());
            entry.put("type", element.eClass().getName());
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

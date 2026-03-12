package com.archimatetool.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.util.ModelAccessor;
import com.archimatetool.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Moves an existing ArchiMate element into a different folder or subfolder.
 */
public class MoveElementToFolderTool implements ITool {

    @Override
    public String getName() {
        return "move_element_to_folder";
    }

    @Override
    public String getDescription() {
        return "Move an existing ArchiMate element into a different folder or subfolder. "
                + "The target folder must already exist in the model.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description", "ID of the element to move");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description", "ID of the target folder to move the element into");

        ArrayNode required = schema.putArray("required");
        required.add("element_id");
        required.add("folder_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String elementId = args.get("element_id").asText();
        String folderId = args.get("folder_id").asText();

        IArchimateElement element = ModelAccessor.findElementById(model, elementId);
        if (element == null) {
            throw new Exception("Element not found: " + elementId);
        }

        IFolder targetFolder = ModelAccessor.findFolderById(model, folderId);
        if (targetFolder == null) {
            throw new Exception("Folder not found: " + folderId);
        }

        IFolder resolvedTarget = targetFolder;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            IFolder currentFolder = (IFolder) element.eContainer();
            String previousFolderId = currentFolder != null ? currentFolder.getId() : null;

            // Remove from current container and add to target folder
            if (currentFolder != null) {
                currentFolder.getElements().remove(element);
            }
            resolvedTarget.getElements().add(element);

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("element_id", element.getId());
            entry.put("element_name", element.getName());
            entry.put("folder_id", resolvedTarget.getId());
            entry.put("folder_name", resolvedTarget.getName());
            entry.put("previous_folder_id", previousFolderId);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

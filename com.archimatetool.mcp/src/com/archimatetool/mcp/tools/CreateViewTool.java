package com.archimatetool.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.util.ModelAccessor;
import com.archimatetool.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates a new empty ArchiMate diagram view, optionally placed inside a folder.
 */
public class CreateViewTool implements ITool {

    @Override
    public String getName() {
        return "create_view";
    }

    @Override
    public String getDescription() {
        return "Create a new empty ArchiMate diagram view, optionally inside a folder.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "View name");

        ObjectNode documentation = properties.putObject("documentation");
        documentation.put("type", "string");
        documentation.put("description", "Optional documentation");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description",
                "Optional: ID of a folder to place the view in. "
                        + "If omitted, the view is placed in the default Views root folder.");

        ArrayNode required = schema.putArray("required");
        required.add("name");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String name = args.get("name").asText();
        String documentation = args.has("documentation") ? args.get("documentation").asText() : null;
        String folderId = args.has("folder_id") ? args.get("folder_id").asText() : null;

        IFolder targetFolder;
        if (folderId != null) {
            targetFolder = ModelAccessor.findFolderById(model, folderId);
            if (targetFolder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
        } else {
            targetFolder = null; // resolved inside syncExec using default
        }

        IFolder resolvedFolder = targetFolder;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            IArchimateDiagramModel view = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
            view.setName(name);
            if (documentation != null) {
                view.setDocumentation(documentation);
            }

            IFolder folder = resolvedFolder != null
                    ? resolvedFolder
                    : model.getDefaultFolderForObject(view);
            folder.getElements().add(view);
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", view.getId());
            entry.put("name", view.getName());
            entry.put("folder_id", folder.getId());
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

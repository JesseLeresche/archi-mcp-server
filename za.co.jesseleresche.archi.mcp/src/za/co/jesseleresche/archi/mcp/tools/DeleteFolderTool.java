package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;

/**
 * Deletes an empty folder from the model. Refuses to delete a folder that
 * still contains elements, relationships, views, or subfolders, and refuses
 * to delete one of the model's built-in top-level root folders — callers
 * must empty a folder (or its subfolders) before it can be removed.
 */
public class DeleteFolderTool implements ITool {

    @Override
    public String getName() {
        return "delete_folder";
    }

    @Override
    public String getDescription() {
        return "Delete an empty folder by ID. Fails with a clear error if the folder still "
                + "contains elements, relationships, views, or subfolders, or if it's one of "
                + "the model's built-in top-level root folders.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("folder_id").put("type", "string")
                .put("description", "ID of the folder to delete");
        ArrayNode required = schema.putArray("required");
        required.add("folder_id");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String folderId = ConsolidatedTool.requireText(args, "folder_id");
        IFolder folder = ModelAccessor.findFolderById(model, folderId);
        if (folder == null) {
            throw new Exception("Folder not found: " + folderId);
        }

        if (!(folder.eContainer() instanceof IFolder parent)) {
            throw new Exception("Cannot delete a top-level root folder: " + folder.getName());
        }

        if (!folder.getFolders().isEmpty()) {
            throw new Exception("Folder is not empty: contains " + folder.getFolders().size()
                    + " subfolder(s). Delete or move those first.");
        }
        if (!folder.getElements().isEmpty()) {
            throw new Exception("Folder is not empty: contains " + folder.getElements().size()
                    + " item(s) (elements, relationships, or views). Move or delete those first.");
        }

        String folderName = folder.getName();

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            parent.getFolders().remove(folder);
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("folder_id", folderId);
            entry.put("folder_name", folderName);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

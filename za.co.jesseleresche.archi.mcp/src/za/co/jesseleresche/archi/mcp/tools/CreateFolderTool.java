package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates a folder in the Views tree, optionally nested under a parent folder.
 * Use this to organise diagrams hierarchically, e.g. Business Area > Business Domain > SD Overview.
 */
public class CreateFolderTool implements ITool {

    @Override
    public String getName() {
        return "create_folder";
    }

    @Override
    public String getDescription() {
        return "Create a folder in the Views tree to organise diagrams hierarchically. "
                + "Optionally nest under a parent folder by supplying its ID.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "Folder name");

        ObjectNode parentFolderId = properties.putObject("parent_folder_id");
        parentFolderId.put("type", "string");
        parentFolderId.put("description",
                "Optional: ID of the parent folder. If omitted, the folder is created "
                        + "at the root of the Views tree.");

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
        String parentFolderId = args.has("parent_folder_id") ? args.get("parent_folder_id").asText() : null;

        // Resolve parent folder
        IFolder parentFolder;
        if (parentFolderId != null) {
            parentFolder = ModelAccessor.findFolderById(model, parentFolderId);
            if (parentFolder == null) {
                throw new Exception("Parent folder not found: " + parentFolderId);
            }
        } else {
            parentFolder = ModelAccessor.getViewsFolder(model);
            if (parentFolder == null) {
                throw new Exception("Could not locate the Views root folder in the model");
            }
        }

        IFolder resolvedParent = parentFolder;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            IFolder folder = IArchimateFactory.eINSTANCE.createFolder();
            folder.setName(name);
            resolvedParent.getFolders().add(folder);
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", folder.getId());
            entry.put("name", folder.getName());
            entry.put("parent_folder_id", resolvedParent.getId());
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Moves an existing diagram view into a different folder or subfolder.
 */
public class MoveViewToFolderTool implements ITool {

    @Override
    public String getName() {
        return "move_view_to_folder";
    }

    @Override
    public String getDescription() {
        return "Move an existing diagram view into a different folder or subfolder. "
                + "The target folder must already exist in the model.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view to move");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description", "ID of the target folder to move the view into");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");
        required.add("folder_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        String folderId = args.get("folder_id").asText();

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        IFolder targetFolder = ModelAccessor.findFolderById(model, folderId);
        if (targetFolder == null) {
            throw new Exception("Folder not found: " + folderId);
        }

        IFolder resolvedTarget = targetFolder;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            IFolder currentFolder = (IFolder) view.eContainer();
            String previousFolderId = currentFolder != null ? currentFolder.getId() : null;

            if (currentFolder != null) {
                currentFolder.getElements().remove(view);
            }
            resolvedTarget.getElements().add(view);

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("view_id", view.getId());
            entry.put("view_name", view.getName());
            entry.put("folder_id", resolvedTarget.getId());
            entry.put("folder_name", resolvedTarget.getName());
            entry.put("previous_folder_id", previousFolderId);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

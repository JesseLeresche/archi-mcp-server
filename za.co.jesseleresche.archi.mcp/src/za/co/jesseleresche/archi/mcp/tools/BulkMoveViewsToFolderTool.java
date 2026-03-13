package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Moves multiple diagram views into target folders in a single call.
 */
public class BulkMoveViewsToFolderTool implements ITool {

    @Override
    public String getName() {
        return "bulk_move_views_to_folder";
    }

    @Override
    public String getDescription() {
        return "Move multiple diagram views into target folders in a single call. "
                + "Each move entry specifies a view_id and a folder_id. "
                + "Returns a result entry per view with success/error status.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode moves = properties.putObject("moves");
        moves.put("type", "array");
        moves.put("description",
                "List of move operations, each with a view_id and folder_id");
        ObjectNode items = moves.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("view_id").put("type", "string");
        itemProps.putObject("folder_id").put("type", "string");
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("view_id");
        itemRequired.add("folder_id");

        ArrayNode required = schema.putArray("required");
        required.add("moves");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode movesNode = args.get("moves");
        if (!movesNode.isArray() || movesNode.isEmpty()) {
            throw new Exception("'moves' must be a non-empty array");
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : movesNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String viewId = item.get("view_id").asText();
                entry.put("view_id", viewId);
                try {
                    String folderId = item.get("folder_id").asText();

                    IArchimateDiagramModel view =
                            ModelAccessor.findViewById(model, viewId);
                    if (view == null) {
                        entry.put("success", false);
                        entry.put("error", "View not found: " + viewId);
                        entries.add(entry);
                        continue;
                    }

                    IFolder targetFolder =
                            ModelAccessor.findFolderById(model, folderId);
                    if (targetFolder == null) {
                        entry.put("success", false);
                        entry.put("error", "Folder not found: " + folderId);
                        entries.add(entry);
                        continue;
                    }

                    IFolder currentFolder = (IFolder) view.eContainer();
                    entry.put("previous_folder_id",
                            currentFolder != null ? currentFolder.getId() : null);

                    if (currentFolder != null) {
                        currentFolder.getElements().remove(view);
                    }
                    targetFolder.getElements().add(view);

                    entry.put("view_name", view.getName());
                    entry.put("folder_id", targetFolder.getId());
                    entry.put("folder_name", targetFolder.getName());
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
        response.put("succeeded", results.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("success")))
                .count());
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

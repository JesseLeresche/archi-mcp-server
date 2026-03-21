package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates multiple empty ArchiMate diagram views in a single call.
 */
public class BulkCreateViewsTool implements ITool {

    @Override
    public String getName() {
        return "bulk_create_views";
    }

    @Override
    public String getDescription() {
        return "Create multiple empty ArchiMate diagram views in a single call. "
                + "Each item may optionally specify a folder_id and documentation.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode views = properties.putObject("views");
        views.put("type", "array");
        views.put("description", "List of views to create");
        ObjectNode items = views.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("name").put("type", "string");
        itemProps.putObject("folder_id").put("type", "string")
                .put("description", "Optional folder ID for this view");
        itemProps.putObject("documentation").put("type", "string");
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("name");

        ArrayNode required = schema.putArray("required");
        required.add("views");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode viewsNode = args.get("views");
        if (!viewsNode.isArray() || viewsNode.isEmpty()) {
            throw new Exception("'views' must be a non-empty array");
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : viewsNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                try {
                    String name = item.get("name").asText();
                    String folderId = item.has("folder_id")
                            ? item.get("folder_id").asText() : null;
                    String documentation = item.has("documentation")
                            ? item.get("documentation").asText() : null;

                    IArchimateDiagramModel view =
                            IArchimateFactory.eINSTANCE
                                    .createArchimateDiagramModel();
                    view.setName(name);
                    if (documentation != null) {
                        view.setDocumentation(documentation);
                    }

                    IFolder folder;
                    if (folderId != null) {
                        folder = ModelAccessor.findFolderById(model, folderId);
                        if (folder == null) {
                            entry.put("error",
                                    "Folder not found: " + folderId);
                            entries.add(entry);
                            continue;
                        }
                    } else {
                        folder = model.getDefaultFolderForObject(view);
                    }
                    folder.getElements().add(view);

                    entry.put("id", view.getId());
                    entry.put("folder_id", folder.getId());
                } catch (Exception e) {
                    entry.put("error", e.getMessage());
                }
                entries.add(entry);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

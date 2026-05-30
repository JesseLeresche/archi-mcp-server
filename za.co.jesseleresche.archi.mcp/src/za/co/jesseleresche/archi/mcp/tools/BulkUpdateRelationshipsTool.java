package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;

/**
 * Updates multiple logical relationships in a single call. Each item follows
 * the same shape as {@code update_relationship}; results are returned per item
 * with success or error, and a failed item does not roll back the others.
 */
public class BulkUpdateRelationshipsTool implements ITool {

    @Override
    public String getName() {
        return "bulk_update_relationships";
    }

    @Override
    public String getDescription() {
        return "Update multiple logical relationships in a single call "
                + "(including type changes). Returns a result entry per item, "
                + "with per-item success or error. Type changes preserve each "
                + "relationship's ID and view connections.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode updates = properties.putObject("updates");
        updates.put("type", "array");
        updates.put("description", "List of relationship updates");
        ObjectNode items = updates.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        UpdateRelationshipTool.addItemProperties(itemProps);
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("relationship_id");

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
        if (updatesNode == null || !updatesNode.isArray() || updatesNode.isEmpty()) {
            throw new Exception("'updates' must be a non-empty array");
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (JsonNode item : updatesNode) {
                Map<String, Object> entry;
                try {
                    entry = UpdateRelationshipTool.applyUpdate(model, item);
                    entry.put("ok", true);
                } catch (Exception e) {
                    entry = new LinkedHashMap<>();
                    entry.put("relationship_id", item.has("relationship_id")
                            ? item.get("relationship_id").asText() : null);
                    entry.put("ok", false);
                    entry.put("error", e.getMessage() != null
                            ? e.getMessage() : e.getClass().getSimpleName());
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

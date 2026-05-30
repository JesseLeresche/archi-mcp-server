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
 * Deletes multiple logical relationships in a single call. Each item is a
 * relationship ID; results are returned per item with success or error, and a
 * failed item does not roll back the others.
 */
public class BulkDeleteRelationshipsTool implements ITool {

    @Override
    public String getName() {
        return "bulk_delete_relationships";
    }

    @Override
    public String getDescription() {
        return "Delete multiple logical relationships in a single call. Also "
                + "removes any visual connections that reference them; endpoints "
                + "are not deleted. Returns a result entry per item.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode ids = properties.putObject("relationship_ids");
        ids.put("type", "array");
        ids.put("description", "List of relationship IDs to delete");
        ids.putObject("items").put("type", "string");
        properties.putObject("dry_run").put("type", "boolean")
                .put("description", "If true, report what would be deleted without deleting");

        ArrayNode required = schema.putArray("required");
        required.add("relationship_ids");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode idsNode = args.get("relationship_ids");
        if (idsNode == null || !idsNode.isArray() || idsNode.isEmpty()) {
            throw new Exception("'relationship_ids' must be a non-empty array");
        }
        boolean dryRun = args.has("dry_run") && args.get("dry_run").asBoolean();

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                String relId = idNode.asText();
                Map<String, Object> entry;
                try {
                    entry = DeleteRelationshipTool.applyDelete(model, relId, dryRun);
                    entry.put("ok", true);
                } catch (Exception e) {
                    entry = new LinkedHashMap<>();
                    entry.put("relationship_id", relId);
                    entry.put("ok", false);
                    entry.put("error", e.getMessage() != null
                            ? e.getMessage() : e.getClass().getSimpleName());
                }
                entries.add(entry);
            }
            if (!dryRun) {
                IEditorModelManager.INSTANCE.saveModel(model);
            }
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

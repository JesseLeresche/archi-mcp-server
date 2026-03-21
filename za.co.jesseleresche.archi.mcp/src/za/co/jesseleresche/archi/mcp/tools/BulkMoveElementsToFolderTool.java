package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Moves multiple ArchiMate elements or relationships into target folders in a single call.
 * Each entry may specify either an element_id or a relationship_id together with a folder_id.
 */
public class BulkMoveElementsToFolderTool implements ITool {

    @Override
    public String getName() {
        return "bulk_move_elements_to_folder";
    }

    @Override
    public String getDescription() {
        return "Move multiple ArchiMate elements or relationships into target folders in a single call. "
                + "Each move entry specifies a folder_id and either an element_id or a relationship_id. "
                + "Returns a result entry per item with success/error status.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode moves = properties.putObject("moves");
        moves.put("type", "array");
        moves.put("description",
                "List of move operations. Each item needs a folder_id and either element_id or relationship_id.");
        ObjectNode items = moves.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("element_id").put("type", "string")
                .put("description", "ID of the element to move (mutually exclusive with relationship_id)");
        itemProps.putObject("relationship_id").put("type", "string")
                .put("description", "ID of the relationship to move (mutually exclusive with element_id)");
        itemProps.putObject("folder_id").put("type", "string")
                .put("description", "ID of the target folder");
        ArrayNode itemRequired = items.putArray("required");
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
                try {
                    String folderId = item.get("folder_id").asText();
                    String elementId = item.has("element_id") ? item.get("element_id").asText() : null;
                    String relationshipId = item.has("relationship_id") ? item.get("relationship_id").asText() : null;

                    if (elementId == null && relationshipId == null) {
                        entry.put("error", "Each item must specify either element_id or relationship_id");
                        entries.add(entry);
                        continue;
                    }

                    IFolder targetFolder = ModelAccessor.findFolderById(model, folderId);
                    if (targetFolder == null) {
                        entry.put("error", "Folder not found: " + folderId);
                        entries.add(entry);
                        continue;
                    }

                    if (elementId != null) {
                        entry.put("element_id", elementId);
                        IArchimateElement element = ModelAccessor.findElementById(model, elementId);
                        if (element == null) {
                            entry.put("error", "Element not found: " + elementId);
                            entries.add(entry);
                            continue;
                        }
                        IFolder currentFolder = (IFolder) element.eContainer();
                        entry.put("previous_folder_id",
                                currentFolder != null ? currentFolder.getId() : null);
                        if (currentFolder != null) currentFolder.getElements().remove(element);
                        targetFolder.getElements().add(element);
                    } else {
                        entry.put("relationship_id", relationshipId);
                        IArchimateRelationship rel = ModelAccessor.findRelationshipById(model, relationshipId);
                        if (rel == null) {
                            entry.put("error", "Relationship not found: " + relationshipId);
                            entries.add(entry);
                            continue;
                        }
                        IFolder currentFolder = (IFolder) rel.eContainer();
                        entry.put("previous_folder_id",
                                currentFolder != null ? currentFolder.getId() : null);
                        if (currentFolder != null) currentFolder.getElements().remove(rel);
                        targetFolder.getElements().add(rel);
                    }

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

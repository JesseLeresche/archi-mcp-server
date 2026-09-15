package za.co.jesseleresche.archi.mcp.tools;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated relationship CRUD tool. Replaces create_relationship,
 * update_relationship, delete_relationship and their bulk variants.
 */
public class ManageRelationshipsTool extends ConsolidatedTool {

    private final BulkCreateRelationshipsTool create = new BulkCreateRelationshipsTool();
    private final BulkUpdateRelationshipsTool update = new BulkUpdateRelationshipsTool();
    private final BulkDeleteRelationshipsTool delete = new BulkDeleteRelationshipsTool();
    private final GetValidRelationshipTypesTool validTypes = new GetValidRelationshipTypesTool();
    private final GetRelationshipsBetweenTool between = new GetRelationshipsBetweenTool();

    @Override
    public String getName() {
        return "manage_relationships";
    }

    @Override
    public String getDescription() {
        return "Create, update, or delete ArchiMate relationships, or query relationship "
                + "options. Set 'operation' and pass 'items' as a single object or an array "
                + "(batch). Returns a result entry per item. Fields by operation — "
                + "create: {source_id, target_id, type, name?, folder_id?|folder_path?, access_type?} "
                + "(results include folder_path); "
                + "update: {relationship_id, new_type?, name?, documentation?, access_type?, properties?, new_folder_id?}; "
                + "delete: a relationship_id string, or {relationship_id, dry_run?}; "
                + "valid_types: {source_id, target_id} — returns which relationship types the "
                + "ArchiMate spec allows between them, without creating anything; "
                + "between: {element_a_id, element_b_id, direction?} — returns existing "
                + "relationships already connecting that pair (direction: \"either\" default or "
                + "\"source_to_target\").";
    }

    @Override
    public ObjectNode getInputSchema() {
        return operationItemsSchema(
                new String[] {"create", "update", "delete", "valid_types", "between"},
                "Relationship payload(s): a single object or an array. For delete, items may be "
                        + "relationship_id strings. Fields depend on 'operation' (see the tool description).");
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        List<JsonNode> items = normalizeItems(args);
        switch (operation) {
            case "create":
                return delegateBulk(create, "relationships", items);
            case "update":
                return delegateBulk(update, "updates", items);
            case "delete": {
                ObjectNode wrapper = MAPPER.createObjectNode();
                ArrayNode ids = wrapper.putArray("relationship_ids");
                boolean dryRun = false;
                for (JsonNode item : items) {
                    if (item.isTextual()) {
                        ids.add(item.asText());
                    } else if (item.hasNonNull("relationship_id")) {
                        ids.add(item.get("relationship_id").asText());
                    }
                    if (item.isObject() && item.path("dry_run").asBoolean(false)) {
                        dryRun = true;
                    }
                }
                if (dryRun) {
                    wrapper.put("dry_run", true);
                }
                return delete.execute(wrapper);
            }
            case "valid_types":
                return delegatePerItem(validTypes, items);
            case "between":
                return delegatePerItem(between, items);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: create, update, delete, valid_types, between");
        }
    }
}

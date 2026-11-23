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
    private final QueryRelationshipsTool query = new QueryRelationshipsTool();

    @Override
    public String getName() {
        return "manage_relationships";
    }

    @Override
    public String getDescription() {
        return "Create, update, delete or query ArchiMate relationships, or query relationship "
                + "options. Set 'operation' and pass 'items' as a single object or an array "
                + "(batch). Returns a result entry per item. Fields by operation — "
                + "create: {source_id, target_id, type, id? (caller-supplied; on_conflict?: error|skip), name?, documentation?, folder_id?|folder_path?, access_type?, influence_strength?, directed?, properties?, specialization?, on_existing?} "
                + "(results include folder_path). If a relationship of the same type already joins source to "
                + "target (including earlier items in the batch), the item fails unless on_existing is "
                + "\"reuse\" (returns the existing one as {id, reused: true, folder_path}) or \"add\" "
                + "(creates another); an existing caller id is on_conflict's job alone; "
                + "update: {relationship_id, new_type?, name?, documentation?, access_type?, influence_strength?, directed?, properties?, remove_properties?, specialization?, new_folder_id?} "
                + "(a type change reports values the new type can't hold in 'dropped'); "
                + "delete: a relationship_id string, or {relationship_id, dry_run?}; "
                + "valid_types: {source_id, target_id} — returns which relationship types the "
                + "ArchiMate spec allows between them, without creating anything; "
                + "between: {element_a_id, element_b_id, direction?, include_properties?} — returns existing "
                + "relationships already connecting that pair (direction: \"either\" default or "
                + "\"source_to_target\"); "
                + "query: {element_id?, type_filter?, on_view?, model?, output_file?} — every relationship "
                + "in the model (drawn or not) matching all given filters (element_id: either end; "
                + "on_view: true = drawn on a view, false = drawn on none), in folder order, as "
                + "{count, relationships} or, with output_file (bare .json name in the exchange folder, "
                + "never overwritten), {output_file, count}. Entries use create's field names plus a "
                + "read-only views list, so they can be passed to create unchanged (inline, or via "
                + "items_file); replaying into a model that already holds them needs on_conflict: \"skip\" "
                + "(same IDs) or on_existing: \"reuse\" (different IDs). "
                + "access_type (AccessRelationship only): Write, Read, Unspecified or ReadWrite, or Archi's "
                + "integer " + RelationshipAttributes.ACCESS_LEGEND + "; default Write. influence_strength "
                + "applies to InfluenceRelationship only, directed to AssociationRelationship only; given for "
                + "another type they are an item error.";
    }

    @Override
    public ObjectNode getInputSchema() {
        return operationItemsSchema(
                new String[] {"create", "update", "delete", "valid_types", "between", "query"},
                "Relationship payload(s): a single object or an array. For delete, items may be "
                        + "relationship_id strings. Fields depend on 'operation' (see the tool description).");
    }

    @Override
    protected String executeOperation(JsonNode args) throws Exception {
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
            case "query":
                return delegatePerItem(query, items);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: create, update, delete, valid_types, between, query");
        }
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated element CRUD tool. Replaces create_element, update_element,
 * delete_element and their bulk variants.
 */
public class ManageElementsTool extends ConsolidatedTool {

    private final BulkCreateElementsTool create = new BulkCreateElementsTool();
    private final BulkUpdateElementsTool update = new BulkUpdateElementsTool();
    private final DeleteElementTool delete = new DeleteElementTool();
    private final MergeElementsTool merge = new MergeElementsTool();

    @Override
    public String getName() {
        return "manage_elements";
    }

    @Override
    public String getDescription() {
        return "Create, update, delete or merge ArchiMate elements. Set 'operation' and pass 'items' "
                + "as a single object or an array (batch). Returns a result entry per item. "
                + "Fields by operation — "
                + "create: {name, type, id? (caller-supplied; on_conflict?: error|skip), documentation?, folder_id?|folder_path?, properties?, specialization?}; "
                + "update: {element_id, name?, documentation?, new_type?, new_folder_id?, properties?, remove_properties?, specialization?}; "
                + "delete: {element_id, dry_run?} (removes every figure of the element and every attached connection); "
                + "merge: {survivor_id, duplicate_ids: [id,...], collapse?: false, resolve?, dry_run?} — "
                + "folds same-type duplicates into the survivor: relationships are re-pointed and figures "
                + "retargeted in place (IDs kept), relationships that would become survivor self-loops are "
                + "deleted, the duplicates are removed; one undo step. Like a git diff, conflicting property "
                + "values, documentation (and, with collapse, relationship names) block the merge: the item "
                + "returns {error: \"unresolved conflicts\", conflicts, parallel_relationships}; re-send with "
                + "resolve: {properties: {key: value|[values]}, documentation, relationships: {<kept id>: "
                + "{properties, documentation, name}}}. Values only one side has fill in automatically. "
                + "Relationships left with the same type, ends and attributes are reported in "
                + "parallel_relationships; collapse: true merges each group into its kept relationship and "
                + "moves its connections. dry_run returns the diff and impact without changing anything. "
                + "properties is [{key, value}] (insertion order preserved); on update, a null value "
                + "removes that property, and remove_properties: [key,...] removes by key "
                + "(result entries then include removed: n, the actual removal count). "
                + "folder_path (e.g. \"Application/Service domains\") resolves/creates folders by "
                + "name instead of requiring a prior manage_folders lookup for folder_id. "
                + "For large delete batches, some MCP clients gate the whole call behind a single "
                + "destructive-action confirmation — prefer smaller batches if that becomes a problem.";
    }

    @Override
    public ObjectNode getInputSchema() {
        return operationItemsSchema(
                new String[] {"create", "update", "delete", "merge"},
                "Element payload(s): a single object or an array. Fields depend on 'operation' "
                        + "(see the tool description).");
    }

    @Override
    protected String executeOperation(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        List<JsonNode> items = normalizeItems(args);
        switch (operation) {
            case "create":
                return delegateBulk(create, "elements", items);
            case "update":
                return delegateBulk(update, "updates", items);
            case "delete": {
                List<JsonNode> coerced = new ArrayList<>();
                for (JsonNode item : items) {
                    coerced.add(asObject(item, "element_id"));
                }
                return delegatePerItem(delete, coerced);
            }
            case "merge":
                return delegatePerItem(merge, items);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: create, update, delete, merge");
        }
    }
}

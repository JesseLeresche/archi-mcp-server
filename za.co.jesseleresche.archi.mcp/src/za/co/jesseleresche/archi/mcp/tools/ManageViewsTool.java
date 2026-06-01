package za.co.jesseleresche.archi.mcp.tools;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated view management tool. Replaces create_view, update_view,
 * delete_view, duplicate_view and bulk_create_views.
 */
public class ManageViewsTool extends ConsolidatedTool {

    private final BulkCreateViewsTool create = new BulkCreateViewsTool();
    private final UpdateViewTool update = new UpdateViewTool();
    private final DeleteViewTool delete = new DeleteViewTool();
    private final DuplicateViewTool duplicate = new DuplicateViewTool();

    @Override
    public String getName() {
        return "manage_views";
    }

    @Override
    public String getDescription() {
        return "Create, update, delete, or duplicate ArchiMate diagram views. Set 'operation' and "
                + "pass 'items' as a single object or an array (batch). Returns a result entry per item. "
                + "Fields by operation — "
                + "create: {name, folder_id?, documentation?}; "
                + "update: {view_id, name?, documentation?}; "
                + "delete: {view_id}; "
                + "duplicate: {source_view_id, new_name, target_folder_id?}.";
    }

    @Override
    public ObjectNode getInputSchema() {
        return operationItemsSchema(
                new String[] {"create", "update", "delete", "duplicate"},
                "View payload(s): a single object or an array. Fields depend on 'operation' "
                        + "(see the tool description).");
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        List<JsonNode> items = normalizeItems(args);
        switch (operation) {
            case "create":
                return delegateBulk(create, "views", items);
            case "update":
                return delegatePerItem(update, items);
            case "delete":
                return delegatePerItem(delete, items);
            case "duplicate":
                return delegatePerItem(duplicate, items);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: create, update, delete, duplicate");
        }
    }
}

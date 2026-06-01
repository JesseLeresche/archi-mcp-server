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

    @Override
    public String getName() {
        return "manage_elements";
    }

    @Override
    public String getDescription() {
        return "Create, update, or delete ArchiMate elements. Set 'operation' and pass 'items' "
                + "as a single object or an array (batch). Returns a result entry per item. "
                + "Fields by operation — "
                + "create: {name, type, documentation?, folder_id?}; "
                + "update: {element_id, name?, documentation?, new_type?, new_folder_id?, properties?}; "
                + "delete: {element_id, dry_run?}.";
    }

    @Override
    public ObjectNode getInputSchema() {
        return operationItemsSchema(
                new String[] {"create", "update", "delete"},
                "Element payload(s): a single object or an array. Fields depend on 'operation' "
                        + "(see the tool description).");
    }

    @Override
    public String execute(JsonNode args) throws Exception {
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
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: create, update, delete");
        }
    }
}

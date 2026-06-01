package za.co.jesseleresche.archi.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated folder tool. Replaces create_folder, move_element_to_folder,
 * move_view_to_folder (and their bulk variants), list_folder_contents and
 * get_folder_tree.
 *
 * <p>Write operations (create, move_element, move_view) use {@code items}; read
 * operations (list_contents, tree) use the flat {@code folder_id} / {@code section}
 * parameters.
 */
public class ManageFoldersTool extends ConsolidatedTool {

    private final CreateFolderTool create = new CreateFolderTool();
    private final BulkMoveElementsToFolderTool moveElements = new BulkMoveElementsToFolderTool();
    private final BulkMoveViewsToFolderTool moveViews = new BulkMoveViewsToFolderTool();
    private final ListFolderContentsTool listContents = new ListFolderContentsTool();
    private final GetFolderTreeTool tree = new GetFolderTreeTool();

    @Override
    public String getName() {
        return "manage_folders";
    }

    @Override
    public String getDescription() {
        return "Create folders, move elements/relationships/views between folders, and inspect the "
                + "folder hierarchy. Set 'operation'. Write operations take 'items' (single object or "
                + "array); read operations take folder_id/section. Fields by operation — "
                + "create: items {name, parent_folder_id?}; "
                + "move_element: items {folder_id, element_id|relationship_id}; "
                + "move_view: items {view_id, folder_id}; "
                + "list_contents: {folder_id, include_subfolders?}; "
                + "tree: {folder_id?, section?}.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, new String[] {
                "create", "move_element", "move_view", "list_contents", "tree"});
        addItems(props, "Payload for write operations (create, move_element, move_view): a single "
                + "object or an array. Fields depend on 'operation' (see the tool description).");
        addStringProp(props, "folder_id", "For list_contents (required) and tree (optional start folder).");
        props.putObject("include_subfolders").put("type", "boolean")
                .put("description", "For list_contents: recursively include subfolders.");
        addStringProp(props, "section",
                "For tree: restrict to a top-level section (Business, Application, Relations, Views).");
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        switch (operation) {
            case "create":
                return delegatePerItem(create, normalizeItems(args));
            case "move_element":
                return delegateBulk(moveElements, "moves", normalizeItems(args));
            case "move_view":
                return delegateBulk(moveViews, "moves", normalizeItems(args));
            case "list_contents":
                return listContents.execute(args);
            case "tree":
                return tree.execute(args);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: create, move_element, move_view, list_contents, tree");
        }
    }
}

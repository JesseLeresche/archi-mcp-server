package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated tool for placing and editing content on a view: element figures,
 * relationship connections, figure removal, and connection update/delete. Replaces
 * add_element_to_view, add_relationship_to_view (and their bulk variants),
 * remove_figure_from_view, update_connection and delete_connection.
 *
 * <p>{@code view_id} is supplied once at the top level and applied to every item.
 */
public class ManageViewContentTool extends ConsolidatedTool {

    private final BulkAddElementsToViewTool addElements = new BulkAddElementsToViewTool();
    private final BulkAddRelationshipsToViewTool addRelationships = new BulkAddRelationshipsToViewTool();
    private final RemoveFigureFromViewTool removeFigure = new RemoveFigureFromViewTool();
    private final UpdateConnectionTool updateConnection = new UpdateConnectionTool();
    private final DeleteConnectionTool deleteConnection = new DeleteConnectionTool();

    @Override
    public String getName() {
        return "manage_view_content";
    }

    @Override
    public String getDescription() {
        return "Add or remove figures and connections on a view. Set 'operation', 'view_id', and "
                + "pass 'items' as a single object or an array (batch). Returns a result entry per item. "
                + "Fields by operation — "
                + "add_element: {element_id, x?, y?, width?, height?, parent_figure_id?}; "
                + "add_relationship: {relationship_id, source_figure_id?, target_figure_id?, bendpoints?}; "
                + "remove_figure: {figure_id?|element_id?}; "
                + "update_connection: {connection_id?|relationship_id?, bendpoints?, line_color?, line_width?, font_color?, text_position?}; "
                + "delete_connection: {connection_id?|relationship_id?}.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, new String[] {
                "add_element", "add_relationship", "remove_figure",
                "update_connection", "delete_connection"});
        addStringProp(props, "view_id", "ID of the view to operate on.");
        addItems(props, "Item payload(s): a single object or an array. Fields depend on "
                + "'operation' (see the tool description). Do not repeat view_id inside items.");
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        required.add("view_id");
        required.add("items");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        if (!args.hasNonNull("view_id")) {
            throw new Exception("'view_id' is required");
        }
        String viewId = args.get("view_id").asText();
        List<JsonNode> items = normalizeItems(args);

        switch (operation) {
            case "add_element":
                return delegateViewBulk(addElements, viewId, "figures", items);
            case "add_relationship":
                return delegateViewBulk(addRelationships, viewId, "connections", items);
            case "remove_figure":
                return delegateViewPerItem(removeFigure, viewId, items);
            case "update_connection":
                return delegateViewPerItem(updateConnection, viewId, items);
            case "delete_connection":
                return delegateViewPerItem(deleteConnection, viewId, items);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: add_element, add_relationship, remove_figure, "
                        + "update_connection, delete_connection");
        }
    }

    /** Delegate to a bulk view tool that takes a top-level view_id plus an array. */
    private String delegateViewBulk(ITool tool, String viewId, String key, List<JsonNode> items) throws Exception {
        ObjectNode wrapper = MAPPER.createObjectNode();
        wrapper.put("view_id", viewId);
        ArrayNode arr = wrapper.putArray(key);
        items.forEach(arr::add);
        return tool.execute(wrapper);
    }

    /** Delegate to a single-item view tool, injecting view_id into each item. */
    private String delegateViewPerItem(ITool tool, String viewId, List<JsonNode> items) throws Exception {
        List<JsonNode> merged = new ArrayList<>();
        for (JsonNode item : items) {
            merged.add(withField(item, "view_id", viewId));
        }
        return delegatePerItem(tool, merged);
    }
}

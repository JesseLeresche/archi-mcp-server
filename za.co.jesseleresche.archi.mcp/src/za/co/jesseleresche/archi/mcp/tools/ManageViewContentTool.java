package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.FigureStyle;

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

    private final BulkAddElementsToViewTool addFigures = new BulkAddElementsToViewTool();
    private final BulkAddRelationshipsToViewTool addConnections = new BulkAddRelationshipsToViewTool();
    private final RemoveFigureFromViewTool removeFigure = new RemoveFigureFromViewTool();
    private final UpdateConnectionTool updateConnection = new UpdateConnectionTool();
    private final DeleteConnectionTool removeConnection = new DeleteConnectionTool();

    @Override
    public String getName() {
        return "manage_view_content";
    }

    @Override
    public String getDescription() {
        return "Add or remove figures and connections on a view. Set 'operation', 'view_id', and "
                + "pass 'items' as a single object or an array (batch). Returns a result entry per item. "
                + "Figures: add_figure / remove_figure. Connections: add_connection / update_connection / "
                + "remove_connection. Fields by operation — "
                + "add_figure: {kind?: element|group|note (default element), element_id (element), "
                + "name?, documentation? (group), content? (note), x?, y?, width?, height?, "
                + "parent_figure_id? (may name a figure added earlier in the same batch), figure_id? "
                + "(caller-supplied ID; on_conflict?: error|skip), <style fields>}; "
                + "remove_figure: {figure_id?|element_id?}; "
                + "add_connection: {relationship_id, source_figure_id?, target_figure_id?, connection_id?, "
                + "on_conflict?: error|skip, on_existing?: reuse|add, bendpoints?, <style fields>} — a relationship "
                + "may be drawn more than once; an existing drawing between the same figures (an omitted figure ID "
                + "matches any figure) fails unless on_existing is given (reuse returns it with reused: true, add "
                + "draws another line); an omitted end whose element is shown more than once fails, pass "
                + "source_figure_id/target_figure_id; "
                + "update_connection: {connection_id?|relationship_id?, bendpoints?, <style fields>}; "
                + "remove_connection: {connection_id?|relationship_id?} removes only the drawing — to delete "
                + "the relationship from the model use manage_relationships delete. "
                + "relationship_id/element_id fails if it matches more than one drawing; pass connection_id/figure_id. "
                + FigureStyle.VOCABULARY;
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, OPERATIONS);
        addStringProp(props, "view_id", "ID of the view to operate on.");
        addItems(props, "Item payload(s): a single object or an array. Fields depend on "
                + "'operation' (see the tool description). Do not repeat view_id inside items.");
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        required.add("view_id");
        return schema;
    }

    private static final String[] OPERATIONS = {
            "add_figure", "remove_figure", "add_connection", "update_connection", "remove_connection"};

    private static final Map<String, String> RENAMED = Map.of(
            "add_element", "add_figure",
            "add_relationship", "add_connection",
            "delete_connection", "remove_connection");

    @Override
    protected String executeOperation(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        if (RENAMED.containsKey(operation)) {
            throw unknownOperation(operation, RENAMED, OPERATIONS);
        }
        if (!args.hasNonNull("view_id")) {
            throw new Exception("'view_id' is required");
        }
        String viewId = args.get("view_id").asText();
        List<JsonNode> items = normalizeItems(args);

        switch (operation) {
            case "add_figure":
                return delegateViewBulk(addFigures, viewId, "figures", items);
            case "add_connection":
                return delegateViewBulk(addConnections, viewId, "connections", items);
            case "remove_figure":
                return delegateViewPerItem(removeFigure, viewId, items);
            case "update_connection":
                return delegateViewPerItem(updateConnection, viewId, items);
            case "remove_connection":
                return delegateViewPerItem(removeConnection, viewId, items);
            default:
                throw unknownOperation(operation, RENAMED, OPERATIONS);
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

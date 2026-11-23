package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.FigureStyle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated visual appearance tool. Replaces update_figure_appearance,
 * bulk_update_figure_appearance and layout_view.
 */
public class ManageAppearanceTool extends ConsolidatedTool {

    private final BulkUpdateFigureAppearanceTool setFigure = new BulkUpdateFigureAppearanceTool();
    private final LayoutViewTool layout = new LayoutViewTool();

    @Override
    public String getName() {
        return "manage_appearance";
    }

    @Override
    public String getDescription() {
        return "Style figures and connections, or auto-layout a view. Set 'operation'. "
                + "set_style: items (single object or array) {view_id?, figure_id?|element_id?|connection_id?|"
                + "relationship_id?, <style fields>, x?, y?, width?, height?} (bounds only for figures). "
                + "A top-level view_id applies to every item that omits its own. "
                + "element_id/relationship_id fails if the element or relationship is drawn more than once on the "
                + "view; pass figure_id/connection_id. "
                + "layout_view: {view_id, spacing?}. " + FigureStyle.VOCABULARY;
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, new String[] {"set_style", "layout_view"});
        addItems(props, "For set_style: style update(s), a single object or an array. Each item "
                + "targets one figure or connection on a view (see the tool description).");
        addStringProp(props, "view_id",
                "For layout_view: the view to auto-layout. For set_style: default view_id "
                        + "applied to items that don't specify their own.");
        props.putObject("spacing").put("type", "integer")
                .put("description", "For layout_view: spacing between elements in pixels (default 60).");
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        return schema;
    }

    @Override
    protected String executeOperation(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        switch (operation) {
            case "set_style": {
                List<JsonNode> items = normalizeItems(args);
                if (args.hasNonNull("view_id")) {
                    String viewId = args.get("view_id").asText();
                    List<JsonNode> merged = new ArrayList<>();
                    for (JsonNode item : items) {
                        merged.add(withDefaultField(item, "view_id", viewId));
                    }
                    items = merged;
                }
                return delegateBulk(setFigure, "updates", items);
            }
            case "layout_view":
                return layout.execute(args);
            default:
                throw unknownOperation(operation, Map.of("set_figure", "set_style"), "set_style", "layout_view");
        }
    }
}

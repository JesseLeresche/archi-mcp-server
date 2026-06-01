package za.co.jesseleresche.archi.mcp.tools;

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
        return "Style figures or auto-layout a view. Set 'operation'. Fields by operation — "
                + "set_figure: items (single object or array) {view_id, figure_id?|element_id?, "
                + "fill_color?, line_color?, font_color?, opacity?, line_width?, text_alignment?}; "
                + "layout_view: {view_id, spacing?}.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, new String[] {"set_figure", "layout_view"});
        addItems(props, "For set_figure: appearance update(s), a single object or an array. "
                + "Each item targets a figure on a view (see the tool description).");
        addStringProp(props, "view_id", "For layout_view: the view to auto-layout.");
        props.putObject("spacing").put("type", "integer")
                .put("description", "For layout_view: spacing between elements in pixels (default 60).");
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        switch (operation) {
            case "set_figure":
                return delegateBulk(setFigure, "updates", normalizeItems(args));
            case "layout_view":
                return layout.execute(args);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: set_figure, layout_view");
        }
    }
}

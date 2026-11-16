package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.List;
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
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public String getDescription() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public ObjectNode getInputSchema() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        throw new UnsupportedOperationException("stub");
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Returns the position and size (bounds) of figures on a view.
 * Optionally filtered to a single element by element_id or figure_id.
 */
public class GetViewLayoutTool implements ITool {

    @Override
    public String getName() {
        return "get_view_layout";
    }

    @Override
    public String getDescription() {
        return "Return the position and size (x, y, width, height) of all figures on a view. "
                + "Optionally filter to a single figure by element_id or figure_id.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view to inspect");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description",
                "Optional: return bounds for this element only");

        ObjectNode figureId = properties.putObject("figure_id");
        figureId.put("type", "string");
        figureId.put("description",
                "Optional: return bounds for this figure ID only");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        String elementId = args.has("element_id")
                ? args.get("element_id").asText() : null;
        String figureId = args.has("figure_id")
                ? args.get("figure_id").asText() : null;

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        // Single-figure lookup
        if (figureId != null || elementId != null) {
            IDiagramModelArchimateObject figure = figureId != null
                    ? ModelAccessor.findFigureById(view, figureId)
                    : ModelAccessor.findFigureByElementId(view, elementId);

            if (figure == null) {
                String label = figureId != null
                        ? "Figure not found on view: " + figureId
                        : "No figure found for element on view: " + elementId;
                throw new Exception(label);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("view_id", viewId);
            result.put("view_name", view.getName());
            result.put("figures", List.of(toFigureMap(figure)));
            return ToolRegistry.MAPPER.writeValueAsString(result);
        }

        // All figures on the view (including nested)
        List<Map<String, Object>> figures = new ArrayList<>();
        for (var figure : ModelAccessor.collectAllFigures(view)) {
            figures.add(toFigureMap(figure));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("view_id", viewId);
        result.put("view_name", view.getName());
        result.put("figure_count", figures.size());
        result.put("figures", figures);
        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    private Map<String, Object> toFigureMap(IDiagramModelArchimateObject figure) {
        IBounds bounds = figure.getBounds();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("figure_id", figure.getId());
        entry.put("element_id", figure.getArchimateElement().getId());
        entry.put("element_name", figure.getArchimateElement().getName());
        entry.put("element_type", figure.getArchimateElement().eClass().getName());
        if (figure.eContainer() instanceof IDiagramModelArchimateObject parent) {
            entry.put("parent_figure_id", parent.getId());
        }
        entry.put("x", bounds.getX());
        entry.put("y", bounds.getY());
        entry.put("width", bounds.getWidth());
        entry.put("height", bounds.getHeight());
        return entry;
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Updates the visual appearance of multiple figures across one or more views
 * in a single call.
 */
public class BulkUpdateFigureAppearanceTool implements ITool {

    @Override
    public String getName() {
        return "bulk_update_figure_appearance";
    }

    @Override
    public String getDescription() {
        return "Update visual appearance of multiple figures in a single call. "
                + "Each entry targets a specific figure on a specific view. "
                + "Supports fill color, font color, line color, opacity, "
                + "line width, and text alignment.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode updates = properties.putObject("updates");
        updates.put("type", "array");
        updates.put("description", "List of figure appearance updates");
        ObjectNode items = updates.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("view_id").put("type", "string")
                .put("description", "ID of the view containing the figure");
        itemProps.putObject("element_id").put("type", "string")
                .put("description",
                        "Element whose figure to style (alternative to figure_id)");
        itemProps.putObject("figure_id").put("type", "string")
                .put("description",
                        "Figure ID to style (alternative to element_id)");
        itemProps.putObject("fill_color").put("type", "string")
                .put("description", "Hex color, e.g. '#F5A623'");
        itemProps.putObject("line_color").put("type", "string")
                .put("description", "Hex color, e.g. '#D4850F'");
        itemProps.putObject("font_color").put("type", "string")
                .put("description", "Hex color for text");
        ObjectNode opacity = itemProps.putObject("opacity");
        opacity.put("type", "integer");
        opacity.put("minimum", 0);
        opacity.put("maximum", 255);
        ObjectNode lineWidth = itemProps.putObject("line_width");
        lineWidth.put("type", "integer");
        lineWidth.put("minimum", 1);
        ObjectNode textAlignment = itemProps.putObject("text_alignment");
        textAlignment.put("type", "integer");
        textAlignment.put("description", "1=left, 2=center, 4=right");
        ArrayNode alignEnum = textAlignment.putArray("enum");
        alignEnum.add(1);
        alignEnum.add(2);
        alignEnum.add(4);

        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("view_id");

        ArrayNode required = schema.putArray("required");
        required.add("updates");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode updatesNode = args.get("updates");
        if (!updatesNode.isArray() || updatesNode.isEmpty()) {
            throw new Exception("'updates' must be a non-empty array");
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : updatesNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                try {
                    String viewId = item.get("view_id").asText();
                    String elementId = item.has("element_id")
                            ? item.get("element_id").asText() : null;
                    String figureId = item.has("figure_id")
                            ? item.get("figure_id").asText() : null;

                    if (elementId == null && figureId == null) {
                        entry.put("error",
                                "Either element_id or figure_id must be provided");
                        entries.add(entry);
                        continue;
                    }

                    IArchimateDiagramModel view =
                            ModelAccessor.findViewById(model, viewId);
                    if (view == null) {
                        entry.put("error", "View not found: " + viewId);
                        entries.add(entry);
                        continue;
                    }

                    IDiagramModelObject fig;
                    if (figureId != null) {
                        fig = ModelAccessor.findDiagramObjectById(view,
                                figureId);
                        if (fig == null) {
                            entry.put("error",
                                    "Figure not found on view: " + figureId);
                            entries.add(entry);
                            continue;
                        }
                    } else {
                        fig = ModelAccessor.findFigureByElementId(view,
                                elementId);
                        if (fig == null) {
                            entry.put("error",
                                    "No figure found for element on view: "
                                            + elementId);
                            entries.add(entry);
                            continue;
                        }
                    }

                    if (item.has("fill_color")) {
                        fig.setFillColor(item.get("fill_color").asText());
                    }
                    if (item.has("font_color")) {
                        fig.setFontColor(item.get("font_color").asText());
                    }
                    if (item.has("line_color")) {
                        fig.setLineColor(item.get("line_color").asText());
                    }
                    if (item.has("opacity")) {
                        fig.setAlpha(item.get("opacity").asInt());
                    }
                    if (item.has("line_width")) {
                        fig.setLineWidth(item.get("line_width").asInt());
                    }
                    if (item.has("text_alignment")) {
                        fig.setTextAlignment(
                                item.get("text_alignment").asInt());
                    }

                    entry.put("figure_id", fig.getId());
                } catch (Exception e) {
                    entry.put("error", e.getMessage());
                }
                entries.add(entry);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

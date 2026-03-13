package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Updates the visual appearance of a figure on a view (fill color, font color,
 * line color, opacity, line width, text alignment).
 */
public class UpdateFigureAppearanceTool implements ITool {

    @Override
    public String getName() {
        return "update_figure_appearance";
    }

    @Override
    public String getDescription() {
        return "Update the visual appearance of a figure on a view. "
                + "Supports fill color, font color, line color, opacity, "
                + "line width, and text alignment.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view containing the figure");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description",
                "ID of the model element whose figure to update "
                        + "(alternative to figure_id)");

        ObjectNode figureId = properties.putObject("figure_id");
        figureId.put("type", "string");
        figureId.put("description",
                "ID of the figure on the view (alternative to element_id)");

        ObjectNode fillColor = properties.putObject("fill_color");
        fillColor.put("type", "string");
        fillColor.put("description",
                "Fill/background color as hex string, e.g. \"#FF0000\" "
                        + "for red");

        ObjectNode fontColor = properties.putObject("font_color");
        fontColor.put("type", "string");
        fontColor.put("description",
                "Font/text color as hex string, e.g. \"#FFFFFF\" for white");

        ObjectNode lineColor = properties.putObject("line_color");
        lineColor.put("type", "string");
        lineColor.put("description",
                "Border/line color as hex string, e.g. \"#000000\" "
                        + "for black");

        ObjectNode opacity = properties.putObject("opacity");
        opacity.put("type", "integer");
        opacity.put("minimum", 0);
        opacity.put("maximum", 255);
        opacity.put("description",
                "Opacity from 0 (fully transparent) to 255 (fully opaque)");

        ObjectNode lineWidth = properties.putObject("line_width");
        lineWidth.put("type", "integer");
        lineWidth.put("minimum", 1);
        lineWidth.put("description", "Border/line width in pixels");

        ObjectNode textAlignment = properties.putObject("text_alignment");
        textAlignment.put("type", "integer");
        textAlignment.put("description",
                "Text alignment: 1=left, 2=center, 4=right");
        ArrayNode alignEnum = textAlignment.putArray("enum");
        alignEnum.add(1);
        alignEnum.add(2);
        alignEnum.add(4);

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

        if (elementId == null && figureId == null) {
            throw new Exception(
                    "Either element_id or figure_id must be provided");
        }

        IArchimateDiagramModel view =
                ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        IDiagramModelArchimateObject figure;
        if (figureId != null) {
            figure = ModelAccessor.findFigureById(view, figureId);
            if (figure == null) {
                throw new Exception(
                        "Figure not found on view: " + figureId);
            }
        } else {
            figure = ModelAccessor.findFigureByElementId(view, elementId);
            if (figure == null) {
                throw new Exception(
                        "No figure found for element on view: " + elementId);
            }
        }

        String fillColor = args.has("fill_color")
                ? args.get("fill_color").asText() : null;
        String fontColor = args.has("font_color")
                ? args.get("font_color").asText() : null;
        String lineColor = args.has("line_color")
                ? args.get("line_color").asText() : null;
        Integer opacity = args.has("opacity")
                ? args.get("opacity").asInt() : null;
        Integer lineWidth = args.has("line_width")
                ? args.get("line_width").asInt() : null;
        Integer textAlignment = args.has("text_alignment")
                ? args.get("text_alignment").asInt() : null;

        IDiagramModelArchimateObject fig = figure;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            Map<String, Object> applied = new LinkedHashMap<>();
            applied.put("view_id", viewId);
            applied.put("figure_id", fig.getId());
            applied.put("element_id",
                    fig.getArchimateElement().getId());

            if (fillColor != null) {
                fig.setFillColor(fillColor);
                applied.put("fill_color", fillColor);
            }
            if (fontColor != null) {
                fig.setFontColor(fontColor);
                applied.put("font_color", fontColor);
            }
            if (lineColor != null) {
                fig.setLineColor(lineColor);
                applied.put("line_color", lineColor);
            }
            if (opacity != null) {
                fig.setAlpha(opacity);
                applied.put("opacity", opacity);
            }
            if (lineWidth != null) {
                fig.setLineWidth(lineWidth);
                applied.put("line_width", lineWidth);
            }
            if (textAlignment != null) {
                fig.setTextAlignment(textAlignment);
                applied.put("text_alignment", textAlignment);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            applied.put("success", true);
            return applied;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

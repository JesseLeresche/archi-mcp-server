package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.model.IBounds;
import com.archimatetool.model.IIdentifier;
import org.eclipse.emf.ecore.EObject;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;
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
                + "line width, text alignment, x/y position, and width/height.";
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
        ObjectNode x = itemProps.putObject("x");
        x.put("type", "integer");
        ObjectNode y = itemProps.putObject("y");
        y.put("type", "integer");
        ObjectNode width = itemProps.putObject("width");
        width.put("type", "integer");
        ObjectNode height = itemProps.putObject("height");
        height.put("type", "integer");

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
                    String viewId = ConsolidatedTool.requireText(item, "view_id");
                    String elementId = item.path("element_id").asText(null);
                    String figureId = item.path("figure_id").asText(null);
                    String connectionId = item.path("connection_id").asText(null);
                    String relationshipId = item.path("relationship_id").asText(null);

                    IArchimateDiagramModel view =
                            ModelAccessor.findViewById(model, viewId);
                    if (view == null) {
                        entry.put("error", "View not found: " + viewId);
                        entries.add(entry);
                        continue;
                    }

                    EObject target;
                    String idKey;
                    if (figureId != null) {
                        target = ModelAccessor.findDiagramObjectById(view, figureId);
                        idKey = "figure_id";
                        if (target == null) throw new Exception("Figure not found on view: " + figureId);
                    } else if (elementId != null) {
                        target = ModelAccessor.findFigureByElementId(view, elementId);
                        idKey = "figure_id";
                        if (target == null) throw new Exception("No figure found for element on view: " + elementId);
                    } else if (connectionId != null) {
                        target = ModelAccessor.findConnectionById(view, connectionId);
                        idKey = "connection_id";
                        if (target == null) throw new Exception("Connection not found on view: " + connectionId);
                    } else if (relationshipId != null) {
                        target = ModelAccessor.findConnectionByRelationshipId(view, relationshipId);
                        idKey = "connection_id";
                        if (target == null) {
                            throw new Exception("No connection found for relationship on view: " + relationshipId);
                        }
                    } else {
                        throw new Exception("One of figure_id, element_id, connection_id or relationship_id "
                                + "must be provided");
                    }

                    boolean hasBounds = item.has("x") || item.has("y") || item.has("width") || item.has("height");
                    if (hasBounds && !(target instanceof IDiagramModelObject)) {
                        throw new Exception("bounds (x, y, width, height) apply only to figures");
                    }

                    FigureStyle.apply(item, target);
                    if (hasBounds && target instanceof IDiagramModelObject fig) {
                        IBounds bounds = fig.getBounds();
                        if (item.has("x")) {
                            bounds.setX(item.get("x").asInt());
                        }
                        if (item.has("y")) {
                            bounds.setY(item.get("y").asInt());
                        }
                        if (item.has("width")) {
                            bounds.setWidth(item.get("width").asInt());
                        }
                        if (item.has("height")) {
                            bounds.setHeight(item.get("height").asInt());
                        }
                        fig.setBounds(bounds);
                    }

                    entry.put(idKey, ((IIdentifier) target).getId());
                } catch (Exception e) {
                    entry.put("error", e.getMessage());
                }
                entries.add(entry);
            }

            ModelAccessor.saveModel(model);
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

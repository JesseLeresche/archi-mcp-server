package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Updates the visual properties of an existing connection on a view:
 * bendpoints, line color, line width, font color, and text position.
 */
public class UpdateConnectionTool implements ITool {

    @Override
    public String getName() {
        return "update_connection";
    }

    @Override
    public String getDescription() {
        return "Update the visual properties of an existing connection on a view. "
                + "Supports bendpoints, line color, line width, font color, and text position. "
                + "Identify the connection by connection_id or by relationship_id + view_id.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view containing the connection");

        ObjectNode connectionId = properties.putObject("connection_id");
        connectionId.put("type", "string");
        connectionId.put("description",
                "ID of the visual connection to update (alternative to relationship_id)");

        ObjectNode relationshipId = properties.putObject("relationship_id");
        relationshipId.put("type", "string");
        relationshipId.put("description",
                "ID of the logical relationship whose connection to update "
                        + "(alternative to connection_id)");

        ObjectNode bendpoints = properties.putObject("bendpoints");
        bendpoints.put("type", "array");
        bendpoints.put("description",
                "Replaces all bendpoints on the connection. Pass an empty array to remove all bendpoints.");
        ObjectNode bpItems = bendpoints.putObject("items");
        bpItems.put("type", "object");
        ObjectNode bpProps = bpItems.putObject("properties");
        bpProps.putObject("startX").put("type", "integer");
        bpProps.putObject("startY").put("type", "integer");
        bpProps.putObject("endX").put("type", "integer");
        bpProps.putObject("endY").put("type", "integer");

        ObjectNode lineColor = properties.putObject("line_color");
        lineColor.put("type", "string");
        lineColor.put("description", "Line color as hex string, e.g. \"#FF0000\"");

        ObjectNode lineWidth = properties.putObject("line_width");
        lineWidth.put("type", "integer");
        lineWidth.put("minimum", 1);
        lineWidth.put("description", "Line width in pixels");

        ObjectNode fontColor = properties.putObject("font_color");
        fontColor.put("type", "string");
        fontColor.put("description", "Label font color as hex string, e.g. \"#000000\"");

        ObjectNode textPosition = properties.putObject("text_position");
        textPosition.put("type", "integer");
        textPosition.put("description",
                "Position of the relationship label: 0=source end, 1=middle, 2=target end");
        ArrayNode posEnum = textPosition.putArray("enum");
        posEnum.add(0);
        posEnum.add(1);
        posEnum.add(2);

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
        String connectionId = args.has("connection_id")
                ? args.get("connection_id").asText() : null;
        String relationshipId = args.has("relationship_id")
                ? args.get("relationship_id").asText() : null;

        if (connectionId == null && relationshipId == null) {
            throw new Exception("Either connection_id or relationship_id must be provided");
        }

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        IDiagramModelArchimateConnection connection;
        if (connectionId != null) {
            connection = ModelAccessor.findConnectionById(view, connectionId);
            if (connection == null) {
                throw new Exception("Connection not found on view: " + connectionId);
            }
        } else {
            connection = ModelAccessor.findConnectionByRelationshipId(view, relationshipId);
            if (connection == null) {
                throw new Exception("No connection found for relationship on view: " + relationshipId);
            }
        }

        JsonNode bendpointsNode = args.has("bendpoints") ? args.get("bendpoints") : null;
        String lineColor = args.has("line_color") ? args.get("line_color").asText() : null;
        Integer lineWidth = args.has("line_width") ? args.get("line_width").asInt() : null;
        String fontColor = args.has("font_color") ? args.get("font_color").asText() : null;
        Integer textPosition = args.has("text_position") ? args.get("text_position").asInt() : null;

        IDiagramModelArchimateConnection conn = connection;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("connection_id", conn.getId());
            entry.put("relationship_id", conn.getArchimateRelationship().getId());
            entry.put("view_id", viewId);

            if (bendpointsNode != null && bendpointsNode.isArray()) {
                conn.getBendpoints().clear();
                List<Map<String, Integer>> savedBendpoints = new ArrayList<>();
                for (JsonNode bp : bendpointsNode) {
                    IDiagramModelBendpoint bendpoint =
                            IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
                    int sx = bp.path("startX").asInt(0);
                    int sy = bp.path("startY").asInt(0);
                    int ex = bp.path("endX").asInt(0);
                    int ey = bp.path("endY").asInt(0);
                    bendpoint.setStartX(sx);
                    bendpoint.setStartY(sy);
                    bendpoint.setEndX(ex);
                    bendpoint.setEndY(ey);
                    conn.getBendpoints().add(bendpoint);
                    Map<String, Integer> bpEntry = new LinkedHashMap<>();
                    bpEntry.put("startX", sx);
                    bpEntry.put("startY", sy);
                    bpEntry.put("endX", ex);
                    bpEntry.put("endY", ey);
                    savedBendpoints.add(bpEntry);
                }
                entry.put("bendpoints", savedBendpoints);
            }
            if (lineColor != null) {
                conn.setLineColor(lineColor);
                entry.put("line_color", lineColor);
            }
            if (lineWidth != null) {
                conn.setLineWidth(lineWidth);
                entry.put("line_width", lineWidth);
            }
            if (fontColor != null) {
                conn.setFontColor(fontColor);
                entry.put("font_color", fontColor);
            }
            if (textPosition != null) {
                conn.setTextPosition(textPosition);
                entry.put("text_position", textPosition);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

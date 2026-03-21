package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Returns the current visual properties of a connection on a view, including bendpoints.
 */
public class GetConnectionTool implements ITool {

    @Override
    public String getName() {
        return "get_connection";
    }

    @Override
    public String getDescription() {
        return "Return the visual properties of a connection on a view, "
                + "including bendpoints, colors, line width, and text position. "
                + "Identify the connection by connection_id or relationship_id + view_id.";
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
        connectionId.put("description", "ID of the visual connection (alternative to relationship_id)");

        ObjectNode relationshipId = properties.putObject("relationship_id");
        relationshipId.put("type", "string");
        relationshipId.put("description",
                "ID of the logical relationship to look up (alternative to connection_id)");

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
        String connectionId = args.has("connection_id") ? args.get("connection_id").asText() : null;
        String relationshipId = args.has("relationship_id") ? args.get("relationship_id").asText() : null;

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

        List<Map<String, Integer>> bendpoints = new ArrayList<>();
        for (IDiagramModelBendpoint bp : connection.getBendpoints()) {
            Map<String, Integer> bpMap = new LinkedHashMap<>();
            bpMap.put("startX", bp.getStartX());
            bpMap.put("startY", bp.getStartY());
            bpMap.put("endX", bp.getEndX());
            bpMap.put("endY", bp.getEndY());
            bendpoints.add(bpMap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connection_id", connection.getId());
        result.put("relationship_id", connection.getArchimateRelationship().getId());
        result.put("relationship_type", connection.getArchimateRelationship().eClass().getName());
        result.put("relationship_name", connection.getArchimateRelationship().getName());
        result.put("source_figure_id", connection.getSource() != null ? connection.getSource().getId() : null);
        result.put("target_figure_id", connection.getTarget() != null ? connection.getTarget().getId() : null);
        result.put("line_color", connection.getLineColor());
        result.put("line_width", connection.getLineWidth());
        result.put("font_color", connection.getFontColor());
        result.put("text_position", connection.getTextPosition());
        result.put("bendpoints", bendpoints);

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

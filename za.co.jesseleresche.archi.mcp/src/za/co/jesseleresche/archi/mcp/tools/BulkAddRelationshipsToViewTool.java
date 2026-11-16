package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import za.co.jesseleresche.archi.mcp.util.FigureStyle;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Draws multiple visual connections on a view in a single call.
 */
public class BulkAddRelationshipsToViewTool implements ITool {

    @Override
    public String getName() {
        return "bulk_add_relationships_to_view";
    }

    @Override
    public String getDescription() {
        return "Draw multiple visual connections on a view for existing logical relationships "
                + "in a single call. Returns a result entry per connection, with per-item success or error. "
                + "Optionally set text_position, line_color, and font_color at creation time "
                + "(equivalent to a follow-up update_connection call).";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the target view");

        ObjectNode connections = properties.putObject("connections");
        connections.put("type", "array");
        connections.put("description", "List of relationships to draw on the view");
        ObjectNode items = connections.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("relationship_id").put("type", "string");
        itemProps.putObject("label_expression").put("type", "string")
                .put("description", "Archi Label Expression, e.g. \"${name}\\n${property:protocol}\"; null/\"\" clears");
        itemProps.putObject("source_figure_id").put("type", "string");
        itemProps.putObject("target_figure_id").put("type", "string");
        ObjectNode bendpoints = itemProps.putObject("bendpoints");
        bendpoints.put("type", "array");
        ObjectNode bpItems = bendpoints.putObject("items");
        bpItems.put("type", "object");
        ObjectNode bpProps = bpItems.putObject("properties");
        bpProps.putObject("startX").put("type", "integer");
        bpProps.putObject("startY").put("type", "integer");
        bpProps.putObject("endX").put("type", "integer");
        bpProps.putObject("endY").put("type", "integer");

        ObjectNode textPosition = itemProps.putObject("text_position");
        textPosition.put("type", "integer");
        textPosition.put("description",
                "Position of the relationship label: 0=source end, 1=middle, 2=target end. "
                        + "Set this on connections between the same pair of elements to avoid "
                        + "overlapping midpoint labels.");
        ArrayNode posEnum = textPosition.putArray("enum");
        posEnum.add(0);
        posEnum.add(1);
        posEnum.add(2);

        itemProps.putObject("line_color").put("type", "string")
                .put("description", "Line color as hex string, e.g. \"#FF0000\"");
        itemProps.putObject("font_color").put("type", "string")
                .put("description", "Label font color as hex string, e.g. \"#000000\"");

        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("relationship_id");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");
        required.add("connections");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        JsonNode connectionsNode = args.get("connections");
        if (!connectionsNode.isArray() || connectionsNode.isEmpty()) {
            throw new Exception("'connections' must be a non-empty array");
        }

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : connectionsNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String relationshipId = item.get("relationship_id").asText();
                entry.put("relationship_id", relationshipId);
                try {
                    IArchimateRelationship relationship = ModelAccessor.findRelationshipById(model, relationshipId);
                    if (relationship == null) {
                        entry.put("error", "Relationship not found: " + relationshipId);
                        entries.add(entry);
                        continue;
                    }

                    IDiagramModelArchimateObject sourceFigure =
                            endpoint(view, item, "source_figure_id", relationship.getSource());
                    if (sourceFigure == null) {
                        entry.put("error", "Source element '" + relationship.getSource().getName()
                                + "' not found on view. Add it with add_figure first.");
                        entries.add(entry);
                        continue;
                    }

                    IDiagramModelArchimateObject targetFigure =
                            endpoint(view, item, "target_figure_id", relationship.getTarget());
                    if (targetFigure == null) {
                        entry.put("error", "Target element '" + relationship.getTarget().getName()
                                + "' not found on view. Add it with add_figure first.");
                        entries.add(entry);
                        continue;
                    }

                    // Check for existing connection
                    IDiagramModelArchimateConnection existing =
                            ModelAccessor.findConnectionByRelationshipId(view, relationshipId);
                    if (existing != null) {
                        entry.put("connection_id", existing.getId());
                        entry.put("already_exists", true);
                        entries.add(entry);
                        continue;
                    }

                    String connectionId = ModelAccessor.requestedId(item, "connection_id");
                    if (connectionId != null
                            && ModelAccessor.claimId(model, connectionId, item, Set.of()) != null) {
                        entry.put("connection_id", connectionId);
                        entry.put("skipped", true);
                        entries.add(entry);
                        continue;
                    }
                    IDiagramModelArchimateConnection connection =
                            IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
                    if (connectionId != null) {
                        connection.setId(connectionId);
                    }
                    connection.setArchimateRelationship(relationship);
                    FigureStyle.apply(item, connection, model); // before connect: a bad field adds nothing
                    connection.connect(sourceFigure, targetFigure);

                    if (item.has("bendpoints") && item.get("bendpoints").isArray()) {
                        for (JsonNode bp : item.get("bendpoints")) {
                            IDiagramModelBendpoint bendpoint =
                                    IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
                            bendpoint.setStartX(bp.path("startX").asInt(0));
                            bendpoint.setStartY(bp.path("startY").asInt(0));
                            bendpoint.setEndX(bp.path("endX").asInt(0));
                            bendpoint.setEndY(bp.path("endY").asInt(0));
                            connection.getBendpoints().add(bendpoint);
                        }
                    }

                    entry.put("connection_id", connection.getId());
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

    /**
     * The figure for one end of a relationship: the given figure ID (any figure on the view,
     * which must then be an element figure showing that end) or the first figure of that element.
     */
    private static IDiagramModelArchimateObject endpoint(IArchimateDiagramModel view, JsonNode item, String key,
            com.archimatetool.model.IArchimateConcept end) throws Exception {
        if (!item.hasNonNull(key)) {
            return ModelAccessor.findFigureByElementId(view, end.getId());
        }
        String id = item.get(key).asText();
        var fig = ModelAccessor.findDiagramObjectById(view, id);
        if (fig == null) {
            throw new Exception("Figure not found on view: " + id);
        }
        if (!(fig instanceof IDiagramModelArchimateObject f)) {
            throw new Exception(key + " " + id + " is not an element figure (it is a "
                    + fig.eClass().getName() + ")");
        }
        if (f.getArchimateElement() != end) {
            throw new Exception(key + " " + id + " does not show '" + end.getName()
                    + "', the relationship's " + (key.startsWith("source") ? "source" : "target"));
        }
        return f;
    }
}

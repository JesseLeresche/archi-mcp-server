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
                + "in a single call. Returns a result entry per connection, with per-item success or error.";
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
                        entry.put("success", false);
                        entry.put("error", "Relationship not found: " + relationshipId);
                        entries.add(entry);
                        continue;
                    }

                    // Resolve source figure
                    IDiagramModelArchimateObject sourceFigure;
                    if (item.has("source_figure_id")) {
                        sourceFigure = ModelAccessor.findFigureById(view, item.get("source_figure_id").asText());
                    } else {
                        sourceFigure = ModelAccessor.findFigureByElementId(view, relationship.getSource().getId());
                    }
                    if (sourceFigure == null) {
                        entry.put("success", false);
                        entry.put("error", "Source element '" + relationship.getSource().getName()
                                + "' not found on view. Call add_element_to_view first.");
                        entries.add(entry);
                        continue;
                    }

                    // Resolve target figure
                    IDiagramModelArchimateObject targetFigure;
                    if (item.has("target_figure_id")) {
                        targetFigure = ModelAccessor.findFigureById(view, item.get("target_figure_id").asText());
                    } else {
                        targetFigure = ModelAccessor.findFigureByElementId(view, relationship.getTarget().getId());
                    }
                    if (targetFigure == null) {
                        entry.put("success", false);
                        entry.put("error", "Target element '" + relationship.getTarget().getName()
                                + "' not found on view. Call add_element_to_view first.");
                        entries.add(entry);
                        continue;
                    }

                    // Check for existing connection
                    IDiagramModelArchimateConnection existing =
                            ModelAccessor.findConnectionByRelationshipId(view, relationshipId);
                    if (existing != null) {
                        entry.put("connection_id", existing.getId());
                        entry.put("already_exists", true);
                        entry.put("success", true);
                        entries.add(entry);
                        continue;
                    }

                    IDiagramModelArchimateConnection connection =
                            IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
                    connection.setArchimateRelationship(relationship);
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
                    entry.put("source_figure_id", sourceFigure.getId());
                    entry.put("target_figure_id", targetFigure.getId());
                    entry.put("bendpoint_count", connection.getBendpoints().size());
                    entry.put("already_exists", false);
                    entry.put("success", true);
                } catch (Exception e) {
                    entry.put("success", false);
                    entry.put("error", e.getMessage());
                }
                entries.add(entry);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("view_id", viewId);
        response.put("results", results);
        response.put("total", results.size());
        response.put("succeeded", results.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count());
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

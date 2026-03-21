package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.archimatetool.model.IAccessRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Returns all visual connections drawn on a view, with routing/bendpoint data.
 * Optionally filtered to a single relationship.
 */
public class GetViewConnectionsTool implements ITool {

    @Override
    public String getName() {
        return "get_view_connections";
    }

    @Override
    public String getDescription() {
        return "List all visual connections drawn on a view. Returns relationship details, "
                + "source/target figures, and bendpoint data. "
                + "Optionally filter to a single relationship by relationship_id.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view to inspect");

        ObjectNode relationshipId = properties.putObject("relationship_id");
        relationshipId.put("type", "string");
        relationshipId.put("description",
                "Optional: return only the connection for this relationship");

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
        String relationshipId = args.has("relationship_id")
                ? args.get("relationship_id").asText() : null;

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        List<Map<String, Object>> connections = new ArrayList<>();

        for (IDiagramModelObject dmo : ModelAccessor.collectAllDiagramObjects(view)) {
            for (var conn : dmo.getSourceConnections()) {
                if (!(conn instanceof IDiagramModelArchimateConnection c)) continue;

                String relId = c.getArchimateRelationship().getId();
                if (relationshipId != null && !relationshipId.equals(relId)) continue;

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("connection_id", c.getId());
                entry.put("relationship_id", relId);
                entry.put("relationship_type",
                        c.getArchimateRelationship().eClass().getName());
                if (c.getArchimateRelationship() instanceof IAccessRelationship accessRel) {
                    entry.put("access_type", accessRel.getAccessType());
                }
                entry.put("source_element_id",
                        c.getArchimateRelationship().getSource().getId());
                entry.put("source_element_name",
                        c.getArchimateRelationship().getSource().getName());
                entry.put("source_figure_id",
                        c.getSource() != null ? c.getSource().getId() : null);
                entry.put("target_element_id",
                        c.getArchimateRelationship().getTarget().getId());
                entry.put("target_element_name",
                        c.getArchimateRelationship().getTarget().getName());
                entry.put("target_figure_id",
                        c.getTarget() != null ? c.getTarget().getId() : null);

                List<Map<String, Integer>> bendpoints = new ArrayList<>();
                for (IDiagramModelBendpoint bp : c.getBendpoints()) {
                    Map<String, Integer> bpMap = new LinkedHashMap<>();
                    bpMap.put("startX", bp.getStartX());
                    bpMap.put("startY", bp.getStartY());
                    bpMap.put("endX", bp.getEndX());
                    bpMap.put("endY", bp.getEndY());
                    bendpoints.add(bpMap);
                }
                entry.put("bendpoints", bendpoints);

                connections.add(entry);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connections", connections);
        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

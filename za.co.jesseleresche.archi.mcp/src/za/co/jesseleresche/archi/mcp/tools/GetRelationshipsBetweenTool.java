package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

/**
 * Finds existing relationships between two specific elements, in either
 * direction by default. Avoids reading and filtering the entire relationship
 * set client-side just to answer "what already joins A and B?".
 */
public class GetRelationshipsBetweenTool implements ITool {

    @Override
    public String getName() {
        return "get_relationships_between";
    }

    @Override
    public String getDescription() {
        return "Find existing relationships between two specific elements. By default matches "
                + "either direction (A->B or B->A); set direction to \"source_to_target\" to "
                + "match only relationships where element_a is the source.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("element_a_id").put("type", "string");
        properties.putObject("element_b_id").put("type", "string");
        ObjectNode direction = properties.putObject("direction");
        direction.put("type", "string");
        direction.put("default", "either");
        var directionEnum = direction.putArray("enum");
        directionEnum.add("either");
        directionEnum.add("source_to_target");
        var required = schema.putArray("required");
        required.add("element_a_id");
        required.add("element_b_id");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String aId = ConsolidatedTool.requireText(args, "element_a_id");
        String bId = ConsolidatedTool.requireText(args, "element_b_id");
        boolean sourceToTargetOnly = "source_to_target".equals(
                args.has("direction") ? args.get("direction").asText() : "either");

        IArchimateElement a = ModelAccessor.findElementById(model, aId);
        if (a == null) {
            throw new Exception("Element not found: " + aId);
        }
        IArchimateElement b = ModelAccessor.findElementById(model, bId);
        if (b == null) {
            throw new Exception("Element not found: " + bId);
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        for (IArchimateRelationship rel : ModelAccessor.collectAllFromFolders(model, IArchimateRelationship.class)) {
            String srcId = rel.getSource().getId();
            String tgtId = rel.getTarget().getId();
            boolean forward = aId.equals(srcId) && bId.equals(tgtId);
            boolean backward = !sourceToTargetOnly && bId.equals(srcId) && aId.equals(tgtId);
            if (!forward && !backward) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("relationship_id", rel.getId());
            entry.put("relationship_type", rel.eClass().getName());
            entry.put("relationship_name", rel.getName() != null ? rel.getName() : "");
            entry.put("source_id", srcId);
            entry.put("target_id", tgtId);
            matches.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("results", matches);
        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

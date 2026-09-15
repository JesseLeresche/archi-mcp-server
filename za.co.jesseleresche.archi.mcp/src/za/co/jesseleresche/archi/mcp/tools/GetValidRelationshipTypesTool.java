package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

/**
 * Looks up which ArchiMate relationship types are valid between a source and
 * target element, without attempting to create one. Used to answer "what
 * relationship type should I use here?" up front instead of guessing from
 * rejected create attempts.
 */
public class GetValidRelationshipTypesTool implements ITool {

    @Override
    public String getName() {
        return "get_valid_relationship_types";
    }

    @Override
    public String getDescription() {
        return "Return the ArchiMate relationship types valid between a source and target "
                + "element, per the ArchiMate specification. Does not create anything.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("source_id").put("type", "string");
        properties.putObject("target_id").put("type", "string");
        var required = schema.putArray("required");
        required.add("source_id");
        required.add("target_id");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String sourceId = ConsolidatedTool.requireText(args, "source_id");
        String targetId = ConsolidatedTool.requireText(args, "target_id");

        IArchimateElement source = ModelAccessor.findElementById(model, sourceId);
        if (source == null) {
            throw new Exception("Source element not found: " + sourceId);
        }
        IArchimateElement target = ModelAccessor.findElementById(model, targetId);
        if (target == null) {
            throw new Exception("Target element not found: " + targetId);
        }

        List<EClass> validTypes = ArchimateModelUtils.getValidRelationships(source, target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source_id", sourceId);
        result.put("source_type", source.eClass().getName());
        result.put("target_id", targetId);
        result.put("target_type", target.eClass().getName());
        result.put("valid_relationship_types", validTypes.stream()
                .map(EClass::getName).collect(Collectors.toList()));

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.util.ArchimateModelUtils;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Validates the model against the ArchiMate specification.
 * Reports invalid relationships (type not allowed between source/target element types).
 */
public class ValidateModelTool implements ITool {

    @Override
    public String getName() {
        return "validate_model";
    }

    @Override
    public String getDescription() {
        return "Validate the open model against the ArchiMate specification. "
                + "Reports invalid relationships where the relationship type is not "
                + "allowed between the source and target element types.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        List<IArchimateRelationship> allRels =
                ModelAccessor.collectAllFromFolders(model, IArchimateRelationship.class);

        List<Map<String, Object>> violations = new ArrayList<>();

        for (IArchimateRelationship rel : allRels) {
            if (rel.getSource() == null || rel.getTarget() == null) {
                continue;
            }

            boolean valid = ArchimateModelUtils.isValidRelationship(
                    rel.getSource(), rel.getTarget(), rel.eClass());

            if (!valid) {
                Map<String, Object> violation = new LinkedHashMap<>();
                violation.put("relationship_id", rel.getId());
                violation.put("relationship_type", rel.eClass().getName());
                violation.put("source_id", rel.getSource().getId());
                violation.put("source_name", rel.getSource().getName());
                violation.put("source_type", rel.getSource().eClass().getName());
                violation.put("target_id", rel.getTarget().getId());
                violation.put("target_name", rel.getTarget().getName());
                violation.put("target_type", rel.getTarget().eClass().getName());

                // Include valid alternatives
                EClass[] validTypes = ArchimateModelUtils.getValidRelationships(
                        rel.getSource(), rel.getTarget());
                List<String> validNames = new ArrayList<>();
                for (EClass ec : validTypes) {
                    validNames.add(ec.getName());
                }
                violation.put("valid_relationship_types", validNames);

                violations.add(violation);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_relationships", allRels.size());
        result.put("violations", violations);
        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

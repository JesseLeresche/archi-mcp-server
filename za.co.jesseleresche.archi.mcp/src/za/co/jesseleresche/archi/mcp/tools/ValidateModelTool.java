package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.util.ArchimateModelUtils;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Validates the model against the ArchiMate specification, and optionally
 * against project-specific conventions (required properties, orphan
 * elements, property value vocabularies).
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
                + "allowed between the source and target element types. "
                + "Optionally also checks project-specific conventions (opt-in, since these "
                + "aren't part of the ArchiMate spec): require_properties (element property "
                + "keys that must be present), check_orphans (elements not used in any view), "
                + "and property_vocabularies (allowed values per property key).";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        ObjectNode requireProperties = properties.putObject("require_properties");
        requireProperties.put("type", "array");
        requireProperties.put("description",
                "Flag elements missing any of these custom property keys.");
        requireProperties.putObject("items").put("type", "string");

        ObjectNode checkOrphans = properties.putObject("check_orphans");
        checkOrphans.put("type", "boolean");
        checkOrphans.put("default", false);
        checkOrphans.put("description", "Flag elements that don't appear in any view.");

        ObjectNode propertyVocabularies = properties.putObject("property_vocabularies");
        propertyVocabularies.put("type", "object");
        propertyVocabularies.put("description",
                "Map of property key to the list of values it's allowed to hold, e.g. "
                        + "{\"status\": [\"draft\", \"approved\", \"retired\"]}. Flags elements "
                        + "whose property value for that key is set but not in the list.");

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
                List<EClass> validTypes = ArchimateModelUtils.getValidRelationships(
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

        List<String> requiredKeys = new ArrayList<>();
        if (args != null && args.has("require_properties") && args.get("require_properties").isArray()) {
            for (JsonNode k : args.get("require_properties")) {
                requiredKeys.add(k.asText());
            }
        }
        boolean checkOrphans = args != null && args.has("check_orphans")
                && args.get("check_orphans").asBoolean(false);
        JsonNode vocabNode = args != null && args.has("property_vocabularies")
                && args.get("property_vocabularies").isObject()
                ? args.get("property_vocabularies") : null;

        if (!requiredKeys.isEmpty() || checkOrphans || vocabNode != null) {
            List<IArchimateElement> allElements =
                    ModelAccessor.collectAllFromFolders(model, IArchimateElement.class);

            Set<String> elementIdsInViews = new HashSet<>();
            if (checkOrphans) {
                for (IArchimateDiagramModel view : ModelAccessor.getAllViews(model)) {
                    for (var fig : ModelAccessor.collectAllFigures(view)) {
                        elementIdsInViews.add(fig.getArchimateElement().getId());
                    }
                }
            }

            List<Map<String, Object>> missingRequiredProperties = new ArrayList<>();
            List<Map<String, Object>> orphanElements = new ArrayList<>();
            List<Map<String, Object>> vocabularyViolations = new ArrayList<>();

            for (IArchimateElement element : allElements) {
                if (!requiredKeys.isEmpty()) {
                    List<String> missing = new ArrayList<>();
                    for (String key : requiredKeys) {
                        boolean present = false;
                        for (IProperty p : element.getProperties()) {
                            if (key.equals(p.getKey())) {
                                present = true;
                                break;
                            }
                        }
                        if (!present) missing.add(key);
                    }
                    if (!missing.isEmpty()) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("element_id", element.getId());
                        entry.put("element_name", element.getName());
                        entry.put("element_type", element.eClass().getName());
                        entry.put("missing_keys", missing);
                        missingRequiredProperties.add(entry);
                    }
                }

                if (checkOrphans && !elementIdsInViews.contains(element.getId())) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("element_id", element.getId());
                    entry.put("element_name", element.getName());
                    entry.put("element_type", element.eClass().getName());
                    orphanElements.add(entry);
                }

                if (vocabNode != null) {
                    for (IProperty p : element.getProperties()) {
                        if (!vocabNode.has(p.getKey())) continue;
                        JsonNode allowed = vocabNode.get(p.getKey());
                        if (!allowed.isArray()) continue;
                        boolean ok = false;
                        for (JsonNode v : allowed) {
                            if (v.asText().equals(p.getValue())) { ok = true; break; }
                        }
                        if (!ok) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("element_id", element.getId());
                            entry.put("element_name", element.getName());
                            entry.put("property_key", p.getKey());
                            entry.put("value", p.getValue());
                            List<String> allowedValues = new ArrayList<>();
                            allowed.forEach(v -> allowedValues.add(v.asText()));
                            entry.put("allowed_values", allowedValues);
                            vocabularyViolations.add(entry);
                        }
                    }
                }
            }

            if (!requiredKeys.isEmpty()) {
                result.put("missing_required_properties", missingRequiredProperties);
            }
            if (checkOrphans) {
                result.put("orphan_elements", orphanElements);
            }
            if (vocabNode != null) {
                result.put("property_vocabulary_violations", vocabularyViolations);
            }
        }

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

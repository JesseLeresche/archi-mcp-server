package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import java.util.stream.Collectors;

import com.archimatetool.model.util.ArchimateModelUtils;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates multiple ArchiMate relationships in a single call.
 */
public class BulkCreateRelationshipsTool implements ITool {

    @Override
    public String getName() {
        return "bulk_create_relationships";
    }

    @Override
    public String getDescription() {
        return "Create multiple ArchiMate relationships in a single call. "
                + "Returns a result entry per relationship, with per-item success or error. "
                + "Each item may optionally specify folder_id, access_type, influence_strength, "
                + "directed and on_existing.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode relationships = properties.putObject("relationships");
        relationships.put("type", "array");
        relationships.put("description", "List of relationships to create");
        ObjectNode items = relationships.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("source_id").put("type", "string");
        itemProps.putObject("target_id").put("type", "string");
        itemProps.putObject("type").put("type", "string");
        itemProps.putObject("name").put("type", "string");
        itemProps.putObject("folder_id").put("type", "string");
        itemProps.putObject("folder_path").put("type", "string")
                .put("description",
                        "Alternative to folder_id: a \"/\"-separated path starting with a "
                                + "top-level folder name (e.g. \"Relations/Integrations\"), "
                                + "matched case-insensitively. Missing intermediate folders are "
                                + "created. Ignored if folder_id is also set.");
        RelationshipAttributes.addSchema(itemProps);
        ObjectNode onExisting = itemProps.putObject("on_existing");
        onExisting.put("type", "string");
        onExisting.putArray("enum").add("reuse").add("add");
        onExisting.put("description", "When a relationship of this type already joins source to target: "
                + "absent = item error, \"reuse\" = return the existing one ({id, reused: true}), "
                + "\"add\" = create another.");
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("source_id");
        itemRequired.add("target_id");
        itemRequired.add("type");

        ArrayNode required = schema.putArray("required");
        required.add("relationships");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode relsNode = args.get("relationships");
        if (!relsNode.isArray() || relsNode.isEmpty()) {
            throw new Exception("'relationships' must be a non-empty array");
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : relsNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                try {
                    String sourceId = ConsolidatedTool.requireText(item, "source_id");
                    String targetId = ConsolidatedTool.requireText(item, "target_id");
                    String typeName = ConsolidatedTool.requireText(item, "type");
                    String requestedId = ModelAccessor.requestedId(item, "id");
                    if (requestedId != null && ModelAccessor.claimId(model, requestedId, item, Set.of()) != null) {
                        entry.put("id", requestedId);
                        entry.put("skipped", true);
                        entries.add(entry);
                        continue;
                    }
                    String name = item.has("name") ? item.get("name").asText() : null;
                    String folderId = item.has("folder_id") ? item.get("folder_id").asText() : null;
                    String folderPath = item.has("folder_path") ? item.get("folder_path").asText() : null;

                    IArchimateElement source = ModelAccessor.findElementById(model, sourceId);
                    if (source == null) {
                        entry.put("error", "Source element not found: " + sourceId);
                        entries.add(entry);
                        continue;
                    }

                    IArchimateElement target = ModelAccessor.findElementById(model, targetId);
                    if (target == null) {
                        entry.put("error", "Target element not found: " + targetId);
                        entries.add(entry);
                        continue;
                    }

                    EClass eClass = ModelAccessor.resolveRelationshipClass(typeName);
                    if (eClass == null) {
                        entry.put("error", "Unknown relationship type: " + typeName);
                        entries.add(entry);
                        continue;
                    }

                    if (!ArchimateModelUtils.isValidRelationship(source, target, eClass)) {
                        List<EClass> validTypes = ArchimateModelUtils.getValidRelationships(source, target);
                        String msg = "Invalid relationship: " + eClass.getName()
                                + " not allowed between " + source.eClass().getName()
                                + " and " + target.eClass().getName();
                        if (!validTypes.isEmpty()) {
                            msg += ". Valid types: " + validTypes.stream()
                                    .map(EClass::getName).collect(Collectors.joining(", "));
                        }
                        entry.put("error", msg);
                        entries.add(entry);
                        continue;
                    }

                    RelationshipAttributes.check(eClass, item);
                    String onExisting = item.hasNonNull("on_existing") ? item.get("on_existing").asText() : null;
                    if (onExisting != null && !onExisting.equals("reuse") && !onExisting.equals("add")) {
                        throw new Exception("on_existing must be \"reuse\" or \"add\", got " + onExisting);
                    }
                    if (!"add".equals(onExisting)) {
                        IArchimateRelationship existing = findExisting(model, eClass, source, target);
                        if (existing != null && onExisting == null) {
                            throw new Exception("relationship " + existing.getId() + " already exists between "
                                    + "these elements; pass on_existing: \"reuse\" or \"add\"");
                        }
                        if (existing != null) {
                            entry.put("id", existing.getId());
                            entry.put("reused", true);
                            entry.put("folder_path", ModelAccessor.getFolderPath((IFolder) existing.eContainer()));
                            entries.add(entry);
                            continue;
                        }
                    }

                    EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
                    IArchimateRelationship relationship = (IArchimateRelationship) eObject;
                    if (requestedId != null) {
                        relationship.setId(requestedId);
                    }
                    relationship.setSource(source);
                    relationship.setTarget(target);
                    if (name != null) {
                        relationship.setName(name);
                    }
                    if (item.hasNonNull("documentation")) {
                        relationship.setDocumentation(item.get("documentation").asText());
                    }
                    RelationshipAttributes.apply(relationship, item);
                    ModelAccessor.applyProperties(relationship, item);
                    ModelAccessor.applySpecialization(model, relationship, item);

                    IFolder folder;
                    if (folderId != null) {
                        folder = ModelAccessor.findFolderById(model, folderId);
                        if (folder == null) {
                            entry.put("error", "Folder not found: " + folderId);
                            entries.add(entry);
                            continue;
                        }
                        ModelAccessor.requireSection(model, folder, relationship);
                    } else if (folderPath != null) {
                        folder = ModelAccessor.findOrCreateFolderByPath(model, folderPath, relationship);
                        if (folder == null) {
                            entry.put("error", "folder_path's top-level segment doesn't match "
                                    + "any root folder: " + folderPath);
                            entries.add(entry);
                            continue;
                        }
                    } else {
                        folder = model.getDefaultFolderForObject(relationship);
                    }
                    folder.getElements().add(relationship);

                    entry.put("id", relationship.getId());
                    entry.put("folder_id", folder.getId());
                    entry.put("folder_path", ModelAccessor.getFolderPath(folder));
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

    /** First relationship in folder order of this exact type from source to target, or null. */
    private static IArchimateRelationship findExisting(IArchimateModel model, EClass eClass,
            IArchimateElement source, IArchimateElement target) {
        // ponytail: linear scan per item, O(items x rels); index by (source,type) if batches get large
        for (IArchimateRelationship r : ModelAccessor.collectAllFromFolders(model, IArchimateRelationship.class)) {
            if (r.eClass() == eClass && r.getSource() == source && r.getTarget() == target) {
                return r;
            }
        }
        return null;
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IAccessRelationship;
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
                + "Each item may optionally specify folder_id and access_type.";
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
        ObjectNode accessTypeNode = itemProps.putObject("access_type");
        accessTypeNode.put("type", "integer");
        accessTypeNode.put("description", "For AccessRelationship: 0=Unspecified, 1=Read, 2=Write, 3=ReadWrite");
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
                    String sourceId = item.get("source_id").asText();
                    String targetId = item.get("target_id").asText();
                    String typeName = item.get("type").asText();
                    String name = item.has("name") ? item.get("name").asText() : null;
                    String folderId = item.has("folder_id") ? item.get("folder_id").asText() : null;
                    Integer accessTypeVal = item.has("access_type") ? item.get("access_type").asInt() : null;

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

                    EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
                    IArchimateRelationship relationship = (IArchimateRelationship) eObject;
                    relationship.setSource(source);
                    relationship.setTarget(target);
                    if (name != null) {
                        relationship.setName(name);
                    }
                    if (accessTypeVal != null && relationship instanceof IAccessRelationship accessRel) {
                        accessRel.setAccessType(accessTypeVal);
                    }

                    IFolder folder;
                    if (folderId != null) {
                        folder = ModelAccessor.findFolderById(model, folderId);
                        if (folder == null) {
                            entry.put("error", "Folder not found: " + folderId);
                            entries.add(entry);
                            continue;
                        }
                    } else {
                        folder = model.getDefaultFolderForObject(relationship);
                    }
                    folder.getElements().add(relationship);

                    entry.put("id", relationship.getId());
                    entry.put("folder_id", folder.getId());
                } catch (Exception e) {
                    entry.put("error", e.getMessage());
                }
                entries.add(entry);
            }

            IEditorModelManager.INSTANCE.saveModel(model);
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

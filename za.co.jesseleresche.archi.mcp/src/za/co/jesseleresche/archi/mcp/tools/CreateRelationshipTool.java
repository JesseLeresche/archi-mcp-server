package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
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
 * Creates an ArchiMate relationship between two existing elements.
 */
public class CreateRelationshipTool implements ITool {

    @Override
    public String getName() {
        return "create_relationship";
    }

    @Override
    public String getDescription() {
        return "Create an ArchiMate relationship between two existing elements. "
                + "Optionally specify folder_id to place it in a specific folder, "
                + "and access_type for AccessRelationships.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode sourceId = properties.putObject("source_id");
        sourceId.put("type", "string");
        sourceId.put("description", "ID of the source element");

        ObjectNode targetId = properties.putObject("target_id");
        targetId.put("type", "string");
        targetId.put("description", "ID of the target element");

        ObjectNode type = properties.putObject("type");
        type.put("type", "string");
        type.put("description", "Relationship type, e.g. AssignmentRelationship");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "Optional relationship name");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description",
                "Optional: ID of the folder to place the relationship in. "
                        + "If omitted, the relationship is placed in the default Relations folder.");

        ObjectNode accessType = properties.putObject("access_type");
        accessType.put("type", "integer");
        accessType.put("description",
                "For AccessRelationship only: 0=Unspecified, 1=Read, 2=Write, 3=ReadWrite");
        ArrayNode accessEnum = accessType.putArray("enum");
        accessEnum.add(0);
        accessEnum.add(1);
        accessEnum.add(2);
        accessEnum.add(3);

        ArrayNode required = schema.putArray("required");
        required.add("source_id");
        required.add("target_id");
        required.add("type");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String sourceId = args.get("source_id").asText();
        String targetId = args.get("target_id").asText();
        String typeName = args.get("type").asText();
        String name = args.has("name") ? args.get("name").asText() : null;
        String folderId = args.has("folder_id") ? args.get("folder_id").asText() : null;
        Integer accessTypeVal = args.has("access_type") ? args.get("access_type").asInt() : null;

        IArchimateElement source = ModelAccessor.findElementById(model, sourceId);
        if (source == null) {
            throw new Exception("Source element not found: " + sourceId);
        }

        IArchimateElement target = ModelAccessor.findElementById(model, targetId);
        if (target == null) {
            throw new Exception("Target element not found: " + targetId);
        }

        EClass eClass = ModelAccessor.resolveRelationshipClass(typeName);
        if (eClass == null) {
            throw new Exception("Unknown ArchiMate relationship type: " + typeName);
        }

        IFolder targetFolder;
        if (folderId != null) {
            targetFolder = ModelAccessor.findFolderById(model, folderId);
            if (targetFolder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
        } else {
            targetFolder = null;
        }

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
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

            IFolder folder = targetFolder != null
                    ? targetFolder
                    : model.getDefaultFolderForObject(relationship);
            folder.getElements().add(relationship);
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", relationship.getId());
            entry.put("source_id", sourceId);
            entry.put("target_id", targetId);
            entry.put("type", relationship.eClass().getName());
            entry.put("folder_id", folder.getId());
            if (relationship instanceof IAccessRelationship accessRel) {
                entry.put("access_type", accessRel.getAccessType());
            }
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

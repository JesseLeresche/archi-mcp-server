package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
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
        return "Create an ArchiMate relationship between two existing elements.";
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

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
            IArchimateRelationship relationship = (IArchimateRelationship) eObject;
            relationship.setSource(source);
            relationship.setTarget(target);
            if (name != null) {
                relationship.setName(name);
            }

            model.getDefaultFolderForObject(relationship).getElements().add(relationship);
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", relationship.getId());
            entry.put("source_id", sourceId);
            entry.put("target_id", targetId);
            entry.put("type", relationship.eClass().getName());
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

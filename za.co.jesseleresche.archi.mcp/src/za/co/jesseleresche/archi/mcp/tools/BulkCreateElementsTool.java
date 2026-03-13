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
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates multiple ArchiMate elements in a single call.
 */
public class BulkCreateElementsTool implements ITool {

    @Override
    public String getName() {
        return "bulk_create_elements";
    }

    @Override
    public String getDescription() {
        return "Create multiple ArchiMate elements in a single call. "
                + "Returns a result entry per element, with per-item success or error.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode elements = properties.putObject("elements");
        elements.put("type", "array");
        elements.put("description", "List of elements to create");
        ObjectNode items = elements.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("name").put("type", "string");
        itemProps.putObject("type").put("type", "string");
        itemProps.putObject("documentation").put("type", "string");
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("name");
        itemRequired.add("type");

        ArrayNode required = schema.putArray("required");
        required.add("elements");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode elementsNode = args.get("elements");
        if (!elementsNode.isArray() || elementsNode.isEmpty()) {
            throw new Exception("'elements' must be a non-empty array");
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : elementsNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                try {
                    String name = item.get("name").asText();
                    String typeName = item.get("type").asText();
                    String documentation = item.has("documentation") ? item.get("documentation").asText() : null;

                    EClass eClass = ModelAccessor.resolveElementClass(typeName);
                    if (eClass == null) {
                        entry.put("name", name);
                        entry.put("type", typeName);
                        entry.put("success", false);
                        entry.put("error", "Unknown ArchiMate element type: " + typeName);
                        entries.add(entry);
                        continue;
                    }

                    EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
                    IArchimateElement element = (IArchimateElement) eObject;
                    element.setName(name);
                    if (documentation != null) {
                        element.setDocumentation(documentation);
                    }
                    model.getDefaultFolderForObject(element).getElements().add(element);

                    entry.put("id", element.getId());
                    entry.put("name", element.getName());
                    entry.put("type", element.eClass().getName());
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
        response.put("results", results);
        response.put("total", results.size());
        response.put("succeeded", results.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count());
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

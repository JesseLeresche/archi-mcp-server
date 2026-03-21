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
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Updates multiple ArchiMate elements (name, documentation, properties, type)
 * in a single call. Supports type changes via new_type, which creates a new
 * element with a new ID and updates all references.
 */
public class BulkUpdateElementsTool implements ITool {

    @Override
    public String getName() {
        return "bulk_update_elements";
    }

    @Override
    public String getDescription() {
        return "Update name, documentation, custom properties, and/or ArchiMate type "
                + "on multiple elements in a single call. "
                + "Type changes create a new element with a new ID; all view figures "
                + "and relationships are updated to reference the new element.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode updates = properties.putObject("updates");
        updates.put("type", "array");
        updates.put("description", "List of element updates");
        ObjectNode items = updates.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("element_id").put("type", "string");
        itemProps.putObject("name").put("type", "string");
        itemProps.putObject("documentation").put("type", "string");
        ObjectNode newType = itemProps.putObject("new_type");
        newType.put("type", "string");
        newType.put("description",
                "Change the ArchiMate type (e.g. 'Capability' to 'BusinessService'). "
                        + "Creates a new element with a new ID; all view figures and "
                        + "relationships are updated. Returns old_id and new_id.");
        ObjectNode newFolderId = itemProps.putObject("new_folder_id");
        newFolderId.put("type", "string");
        newFolderId.put("description",
                "When new_type is set: folder for the new element. "
                        + "If omitted, same folder as the old element.");
        ObjectNode propsSchema = itemProps.putObject("properties");
        propsSchema.put("type", "array");
        ObjectNode propsItems = propsSchema.putObject("items");
        propsItems.put("type", "object");
        ObjectNode propsItemProps = propsItems.putObject("properties");
        propsItemProps.putObject("key").put("type", "string");
        propsItemProps.putObject("value").put("type", "string");
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("element_id");

        ArrayNode required = schema.putArray("required");
        required.add("updates");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        JsonNode updatesNode = args.get("updates");
        if (!updatesNode.isArray() || updatesNode.isEmpty()) {
            throw new Exception("'updates' must be a non-empty array");
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : updatesNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String elementId = item.get("element_id").asText();
                try {
                    IArchimateElement element =
                            ModelAccessor.findElementById(model, elementId);
                    if (element == null) {
                        entry.put("element_id", elementId);
                        entry.put("error",
                                "Element not found: " + elementId);
                        entries.add(entry);
                        continue;
                    }

                    String newTypeName = item.has("new_type")
                            ? item.get("new_type").asText() : null;

                    if (newTypeName != null) {
                        // Type-change path
                        entry = performTypeChange(model, element, item,
                                newTypeName);
                    } else {
                        // Standard update path
                        if (item.has("name")) {
                            element.setName(item.get("name").asText());
                        }
                        if (item.has("documentation")) {
                            element.setDocumentation(
                                    item.get("documentation").asText());
                        }
                        if (item.has("properties")
                                && item.get("properties").isArray()) {
                            for (JsonNode prop : item.get("properties")) {
                                setProperty(element,
                                        prop.get("key").asText(),
                                        prop.get("value").asText());
                            }
                        }
                        entry.put("element_id", element.getId());
                    }
                } catch (Exception e) {
                    entry.put("element_id", elementId);
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

    private Map<String, Object> performTypeChange(IArchimateModel model,
            IArchimateElement element, JsonNode item, String newTypeName) {
        Map<String, Object> entry = new LinkedHashMap<>();

        EClass newEClass = ModelAccessor.resolveElementClass(newTypeName);
        if (newEClass == null) {
            entry.put("element_id", element.getId());
            entry.put("error",
                    "Unknown ArchiMate element type: " + newTypeName);
            return entry;
        }

        String newFolderIdStr = item.has("new_folder_id")
                ? item.get("new_folder_id").asText() : null;

        IFolder newFolder;
        if (newFolderIdStr != null) {
            newFolder = ModelAccessor.findFolderById(model, newFolderIdStr);
            if (newFolder == null) {
                entry.put("element_id", element.getId());
                entry.put("error",
                        "Folder not found: " + newFolderIdStr);
                return entry;
            }
        } else {
            newFolder = element.eContainer() instanceof IFolder f
                    ? f : null;
        }

        // Create new element of new type
        EObject newEObject = IArchimateFactory.eINSTANCE.create(newEClass);
        IArchimateElement newElement = (IArchimateElement) newEObject;

        // Copy/override metadata
        String nameToUse = item.has("name")
                ? item.get("name").asText() : element.getName();
        String docToUse = item.has("documentation")
                ? item.get("documentation").asText()
                : element.getDocumentation();
        newElement.setName(nameToUse);
        if (docToUse != null) newElement.setDocumentation(docToUse);

        // Copy existing properties, then apply overrides
        for (IProperty oldProp : element.getProperties()) {
            IProperty p = IArchimateFactory.eINSTANCE.createProperty();
            p.setKey(oldProp.getKey());
            p.setValue(oldProp.getValue());
            newElement.getProperties().add(p);
        }
        if (item.has("properties") && item.get("properties").isArray()) {
            for (JsonNode prop : item.get("properties")) {
                setProperty(newElement,
                        prop.get("key").asText(),
                        prop.get("value").asText());
            }
        }

        // Place in folder
        IFolder targetFolder = newFolder != null
                ? newFolder
                : model.getDefaultFolderForObject(newElement);
        targetFolder.getElements().add(newElement);

        // Update all relationships
        for (IArchimateRelationship rel :
                ModelAccessor.collectAllFromFolders(
                        model, IArchimateRelationship.class)) {
            if (element.equals(rel.getSource())) rel.setSource(newElement);
            if (element.equals(rel.getTarget())) rel.setTarget(newElement);
        }

        // Update all view figures
        for (IArchimateDiagramModel view : ModelAccessor.getAllViews(model)) {
            for (IDiagramModelArchimateObject fig :
                    ModelAccessor.findAllFiguresByElementId(
                            view, element.getId())) {
                fig.setArchimateElement(newElement);
            }
        }

        // Remove old element
        if (element.eContainer() instanceof IFolder oldFolder) {
            oldFolder.getElements().remove(element);
        }

        entry.put("old_id", element.getId());
        entry.put("new_id", newElement.getId());
        entry.put("folder_id", targetFolder.getId());
        return entry;
    }

    private void setProperty(IArchimateElement element, String key,
            String value) {
        for (IProperty prop : element.getProperties()) {
            if (key.equals(prop.getKey())) {
                prop.setValue(value);
                return;
            }
        }
        IProperty newProp = IArchimateFactory.eINSTANCE.createProperty();
        newProp.setKey(key);
        newProp.setValue(value);
        element.getProperties().add(newProp);
    }
}

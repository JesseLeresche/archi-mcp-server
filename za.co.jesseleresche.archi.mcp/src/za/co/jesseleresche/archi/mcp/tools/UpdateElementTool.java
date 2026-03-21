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
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Updates the properties of an existing ArchiMate element: name, documentation,
 * custom key/value properties, and optionally the ArchiMate type.
 */
public class UpdateElementTool implements ITool {

    @Override
    public String getName() {
        return "update_element";
    }

    @Override
    public String getDescription() {
        return "Update an existing ArchiMate element's name, documentation, "
                + "custom properties (key/value pairs), or ArchiMate type. "
                + "Changing the type creates a new element with a new ID and updates all references.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description", "ID of the element to update");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "New element name");

        ObjectNode documentation = properties.putObject("documentation");
        documentation.put("type", "string");
        documentation.put("description", "New documentation text");

        ObjectNode newType = properties.putObject("new_type");
        newType.put("type", "string");
        newType.put("description",
                "Change the ArchiMate type of this element (e.g. Capability → BusinessService). "
                        + "A new element is created with a new ID; all view figures and relationships "
                        + "are updated to reference the new element. Returns both old_id and new_id.");

        ObjectNode newFolderId = properties.putObject("new_folder_id");
        newFolderId.put("type", "string");
        newFolderId.put("description",
                "When new_type is specified: folder to place the new element in. "
                        + "If omitted, the new element goes in the same folder as the old element.");

        ObjectNode props = properties.putObject("properties");
        props.put("type", "array");
        props.put("description",
                "Custom properties to set as key/value pairs. "
                        + "Existing properties with matching keys are updated; "
                        + "new keys are added.");
        ObjectNode items = props.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        ObjectNode key = itemProps.putObject("key");
        key.put("type", "string");
        ObjectNode value = itemProps.putObject("value");
        value.put("type", "string");
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("key");
        itemRequired.add("value");

        ArrayNode required = schema.putArray("required");
        required.add("element_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String elementId = args.get("element_id").asText();

        IArchimateElement element = ModelAccessor.findElementById(model, elementId);
        if (element == null) {
            throw new Exception("Element not found: " + elementId);
        }

        String newName = args.has("name") ? args.get("name").asText() : null;
        String newDoc = args.has("documentation") ? args.get("documentation").asText() : null;
        JsonNode propsNode = args.has("properties") ? args.get("properties") : null;
        String newTypeName = args.has("new_type") ? args.get("new_type").asText() : null;
        String newFolderId = args.has("new_folder_id") ? args.get("new_folder_id").asText() : null;

        // If new_type is specified, perform a type-change operation
        if (newTypeName != null) {
            EClass newEClass = ModelAccessor.resolveElementClass(newTypeName);
            if (newEClass == null) {
                throw new Exception("Unknown ArchiMate element type: " + newTypeName);
            }

            IFolder newFolder;
            if (newFolderId != null) {
                newFolder = ModelAccessor.findFolderById(model, newFolderId);
                if (newFolder == null) {
                    throw new Exception("Folder not found: " + newFolderId);
                }
            } else {
                // Keep in same folder as old element
                newFolder = element.eContainer() instanceof IFolder f ? f : null;
            }

            IFolder resolvedNewFolder = newFolder;

            Map<String, Object> result = UiThreadUtil.syncExec(() -> {
                // Create new element of new type
                EObject newEObject = IArchimateFactory.eINSTANCE.create(newEClass);
                IArchimateElement newElement = (IArchimateElement) newEObject;

                // Copy metadata
                String nameToUse = newName != null ? newName : element.getName();
                String docToUse = newDoc != null ? newDoc : element.getDocumentation();
                newElement.setName(nameToUse);
                if (docToUse != null) newElement.setDocumentation(docToUse);

                // Apply any property updates or copy existing ones
                for (IProperty oldProp : element.getProperties()) {
                    IProperty p = IArchimateFactory.eINSTANCE.createProperty();
                    p.setKey(oldProp.getKey());
                    p.setValue(oldProp.getValue());
                    newElement.getProperties().add(p);
                }
                if (propsNode != null && propsNode.isArray()) {
                    for (JsonNode propEntry : propsNode) {
                        setProperty(newElement,
                                propEntry.get("key").asText(),
                                propEntry.get("value").asText());
                    }
                }

                // Place in folder
                IFolder targetFolder = resolvedNewFolder != null
                        ? resolvedNewFolder
                        : model.getDefaultFolderForObject(newElement);
                targetFolder.getElements().add(newElement);

                // Update all relationships that reference the old element
                for (IArchimateRelationship rel :
                        ModelAccessor.collectAllFromFolders(model, IArchimateRelationship.class)) {
                    if (element.equals(rel.getSource())) rel.setSource(newElement);
                    if (element.equals(rel.getTarget())) rel.setTarget(newElement);
                }

                // Update all view figures that reference the old element
                for (IArchimateDiagramModel view : ModelAccessor.getAllViews(model)) {
                    for (IDiagramModelArchimateObject fig :
                            ModelAccessor.findAllFiguresByElementId(view, element.getId())) {
                        fig.setArchimateElement(newElement);
                    }
                }

                // Remove old element from its folder
                if (element.eContainer() instanceof IFolder oldFolder) {
                    oldFolder.getElements().remove(element);
                }

                IEditorModelManager.INSTANCE.saveModel(model);

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("old_id", element.getId());
                entry.put("new_id", newElement.getId());
                entry.put("folder_id", targetFolder.getId());
                return entry;
            });

            return ToolRegistry.MAPPER.writeValueAsString(result);
        }

        // Standard update (no type change)
        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            if (newName != null) {
                element.setName(newName);
            }
            if (newDoc != null) {
                element.setDocumentation(newDoc);
            }
            if (propsNode != null && propsNode.isArray()) {
                for (JsonNode propEntry : propsNode) {
                    setProperty(element,
                            propEntry.get("key").asText(),
                            propEntry.get("value").asText());
                }
            }

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("element_id", element.getId());
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    private void setProperty(IArchimateElement element, String key, String value) {
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

package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.util.ArchimateModelUtils;
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

        if (!ArchimateModelUtils.isValidRelationship(source, target, eClass)) {
            EClass[] validTypes = ArchimateModelUtils.getValidRelationships(source, target);
            StringBuilder msg = new StringBuilder();
            msg.append("Invalid relationship: ").append(eClass.getName())
                    .append(" is not allowed between ")
                    .append(source.eClass().getName())
                    .append(" and ").append(target.eClass().getName());
            if (validTypes.length > 0) {
                msg.append(". Valid types: ");
                for (int i = 0; i < validTypes.length; i++) {
                    if (i > 0) msg.append(", ");
                    msg.append(validTypes[i].getName());
                }
            }
            throw new Exception(msg.toString());
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
            final IArchimateRelationship[] created = {null};
            final IFolder[] usedFolder = {null};

            Command cmd = new Command("Create Relationship") {
                @Override
                public void execute() {
                    EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
                    created[0] = (IArchimateRelationship) eObject;
                    created[0].setSource(source);
                    created[0].setTarget(target);
                    if (name != null) {
                        created[0].setName(name);
                    }
                    if (accessTypeVal != null && created[0] instanceof IAccessRelationship accessRel) {
                        accessRel.setAccessType(accessTypeVal);
                    }

                    usedFolder[0] = targetFolder != null
                            ? targetFolder
                            : model.getDefaultFolderForObject(created[0]);
                    usedFolder[0].getElements().add(created[0]);
                }

                @Override
                public void undo() {
                    usedFolder[0].getElements().remove(created[0]);
                }
            };

            CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);
            if (stack != null) { stack.execute(cmd); } else { cmd.execute(); }
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", created[0].getId());
            entry.put("folder_id", usedFolder[0].getId());
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates a new ArchiMate element and adds it to the model's default folder for that type,
 * or to a specified folder if folder_id is provided.
 */
public class CreateElementTool implements ITool {

    @Override
    public String getName() {
        return "create_element";
    }

    @Override
    public String getDescription() {
        return "Create a new ArchiMate element in the open model. "
                + "Optionally specify a folder_id to place it in a specific folder instead of the default.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "Element name");

        ObjectNode type = properties.putObject("type");
        type.put("type", "string");
        type.put("description", "ArchiMate element type, e.g. ApplicationComponent");

        ObjectNode documentation = properties.putObject("documentation");
        documentation.put("type", "string");
        documentation.put("description", "Optional documentation");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description",
                "Optional: ID of the folder to place the element in. "
                        + "If omitted, the element is placed in the default layer folder for its type.");

        ArrayNode required = schema.putArray("required");
        required.add("name");
        required.add("type");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String name = args.get("name").asText();
        String typeName = args.get("type").asText();
        String documentation = args.has("documentation") ? args.get("documentation").asText() : null;
        String folderId = args.has("folder_id") ? args.get("folder_id").asText() : null;

        EClass eClass = ModelAccessor.resolveElementClass(typeName);
        if (eClass == null) {
            throw new Exception("Unknown ArchiMate element type: " + typeName);
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
            final IArchimateElement[] created = {null};
            final IFolder[] usedFolder = {null};

            Command cmd = new Command("Create Element") {
                @Override
                public void execute() {
                    EObject eObject = IArchimateFactory.eINSTANCE.create(eClass);
                    created[0] = (IArchimateElement) eObject;
                    created[0].setName(name);
                    if (documentation != null) {
                        created[0].setDocumentation(documentation);
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

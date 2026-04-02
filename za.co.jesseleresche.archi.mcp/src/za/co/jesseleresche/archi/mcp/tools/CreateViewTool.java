package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates a new empty ArchiMate diagram view, optionally placed inside a folder.
 */
public class CreateViewTool implements ITool {

    @Override
    public String getName() {
        return "create_view";
    }

    @Override
    public String getDescription() {
        return "Create a new empty ArchiMate diagram view, optionally inside a folder.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "View name");

        ObjectNode documentation = properties.putObject("documentation");
        documentation.put("type", "string");
        documentation.put("description", "Optional documentation");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description",
                "Optional: ID of a folder to place the view in. "
                        + "If omitted, the view is placed in the default Views root folder.");

        ArrayNode required = schema.putArray("required");
        required.add("name");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String name = args.get("name").asText();
        String documentation = args.has("documentation") ? args.get("documentation").asText() : null;
        String folderId = args.has("folder_id") ? args.get("folder_id").asText() : null;

        IFolder targetFolder;
        if (folderId != null) {
            targetFolder = ModelAccessor.findFolderById(model, folderId);
            if (targetFolder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
        } else {
            targetFolder = null; // resolved inside syncExec using default
        }

        IFolder resolvedFolder = targetFolder;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            final IArchimateDiagramModel[] created = {null};
            final IFolder[] usedFolder = {null};

            Command cmd = new Command("Create View") {
                @Override
                public void execute() {
                    created[0] = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
                    created[0].setName(name);
                    if (documentation != null) {
                        created[0].setDocumentation(documentation);
                    }

                    usedFolder[0] = resolvedFolder != null
                            ? resolvedFolder
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

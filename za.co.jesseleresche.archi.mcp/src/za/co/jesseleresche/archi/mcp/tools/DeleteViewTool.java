package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Deletes a diagram view from the model. The underlying model elements and
 * relationships are not affected — only the visual diagram is removed.
 */
public class DeleteViewTool implements ITool {

    @Override
    public String getName() {
        return "delete_view";
    }

    @Override
    public String getDescription() {
        return "Delete a diagram view by ID. Only the visual diagram is removed; "
                + "underlying model elements and relationships are not affected.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view to delete");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            final IFolder[] folderHolder = {(IFolder) view.eContainer()};
            final int[] indexHolder =
                    {folderHolder[0].getElements().indexOf(view)};

            CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);

            Command cmd = new Command("Delete View") {
                @Override
                public void execute() {
                    folderHolder[0].getElements().remove(view);
                }

                @Override
                public void undo() {
                    folderHolder[0].getElements().add(indexHolder[0], view);
                }
            };

            if (stack != null) {
                stack.execute(cmd);
            } else {
                cmd.execute();
            }

            IEditorModelManager.INSTANCE.saveModel(model);

            return new LinkedHashMap<String, Object>();
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Removes a visual figure from a view without deleting the underlying element or
 * its relationships from the model.
 */
public class RemoveFigureFromViewTool implements ITool {

    @Override
    public String getName() {
        return "remove_figure_from_view";
    }

    @Override
    public String getDescription() {
        return "Remove a visual figure from a view without deleting the underlying element "
                + "or its relationships from the model. Identify the figure by figure_id or element_id.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view containing the figure");

        ObjectNode figureId = properties.putObject("figure_id");
        figureId.put("type", "string");
        figureId.put("description",
                "ID of the figure to remove. Use either figure_id or element_id.");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description",
                "ID of the element whose figure to remove. "
                        + "If the element appears multiple times on the view, removes the first match.");

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
        String figureId = args.has("figure_id") ? args.get("figure_id").asText() : null;
        String elementId = args.has("element_id") ? args.get("element_id").asText() : null;

        if (figureId == null && elementId == null) {
            throw new Exception("Either figure_id or element_id must be provided");
        }

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        IDiagramModelObject diagramObject;
        if (figureId != null) {
            diagramObject = ModelAccessor.findDiagramObjectById(view, figureId);
            if (diagramObject == null) {
                throw new Exception("Figure not found on view: " + figureId);
            }
        } else {
            diagramObject = ModelAccessor.findFigureByElementId(view, elementId);
            if (diagramObject == null) {
                throw new Exception("No figure found for element on view: " + elementId);
            }
        }

        IDiagramModelObject resolved = diagramObject;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            final IDiagramModelContainer[] parentHolder =
                    {(IDiagramModelContainer) resolved.eContainer()};
            final int[] indexHolder =
                    {parentHolder[0].getChildren().indexOf(resolved)};

            CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);

            Command cmd = new Command("Remove Figure from View") {
                @Override
                public void execute() {
                    // Remove all source/target connections first to avoid orphaned connections
                    List<IDiagramModelConnection> conns =
                            new ArrayList<>(resolved.getSourceConnections());
                    conns.addAll(resolved.getTargetConnections());
                    for (IDiagramModelConnection conn : conns) {
                        EcoreUtil.delete(conn, true);
                    }

                    // Now remove the figure itself
                    EcoreUtil.delete(resolved, true);
                }

                @Override
                public void undo() {
                    parentHolder[0].getChildren().add(indexHolder[0], resolved);
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

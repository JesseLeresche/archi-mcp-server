package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
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

        IDiagramModelArchimateObject figure;
        if (figureId != null) {
            figure = ModelAccessor.findFigureById(view, figureId);
            if (figure == null) {
                throw new Exception("Figure not found on view: " + figureId);
            }
        } else {
            figure = ModelAccessor.findFigureByElementId(view, elementId);
            if (figure == null) {
                throw new Exception("No figure found for element on view: " + elementId);
            }
        }

        IDiagramModelArchimateObject resolvedFigure = figure;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            String removedFigureId = resolvedFigure.getId();
            String removedElementId = resolvedFigure.getArchimateElement().getId();
            String removedElementName = resolvedFigure.getArchimateElement().getName();

            // EcoreUtil.delete removes the figure and all attached connections from the view
            EcoreUtil.delete(resolvedFigure, true);

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("view_id", viewId);
            entry.put("figure_id", removedFigureId);
            entry.put("element_id", removedElementId);
            entry.put("element_name", removedElementName);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

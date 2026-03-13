package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Places an existing model element onto a view as a visual figure with layout position and size.
 */
public class AddElementToViewTool implements ITool {

    @Override
    public String getName() {
        return "add_element_to_view";
    }

    @Override
    public String getDescription() {
        return "Place an existing model element onto a view as a visual figure with layout position and size.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the target view");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description", "ID of the element to add");

        ObjectNode x = properties.putObject("x");
        x.put("type", "integer");
        x.put("default", 50);
        x.put("description", "X position");

        ObjectNode y = properties.putObject("y");
        y.put("type", "integer");
        y.put("default", 50);
        y.put("description", "Y position");

        ObjectNode width = properties.putObject("width");
        width.put("type", "integer");
        width.put("default", 120);
        width.put("description", "Figure width");

        ObjectNode height = properties.putObject("height");
        height.put("type", "integer");
        height.put("default", 55);
        height.put("description", "Figure height");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");
        required.add("element_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        String elementId = args.get("element_id").asText();
        int x = args.has("x") ? args.get("x").asInt(50) : 50;
        int y = args.has("y") ? args.get("y").asInt(50) : 50;
        int width = args.has("width") ? args.get("width").asInt(120) : 120;
        int height = args.has("height") ? args.get("height").asInt(55) : 55;

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        IArchimateElement element = ModelAccessor.findElementById(model, elementId);
        if (element == null) {
            throw new Exception("Element not found: " + elementId);
        }

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            IDiagramModelArchimateObject figure =
                    IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
            figure.setArchimateElement(element);

            IBounds bounds = IArchimateFactory.eINSTANCE.createBounds();
            bounds.setX(x);
            bounds.setY(y);
            bounds.setWidth(width);
            bounds.setHeight(height);
            figure.setBounds(bounds);

            view.getChildren().add(figure);
            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("view_id", viewId);
            entry.put("element_id", elementId);
            entry.put("figure_id", figure.getId());
            entry.put("x", x);
            entry.put("y", y);
            entry.put("width", width);
            entry.put("height", height);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

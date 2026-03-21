package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Places multiple existing model elements onto a view as visual figures in a single call.
 */
public class BulkAddElementsToViewTool implements ITool {

    @Override
    public String getName() {
        return "bulk_add_elements_to_view";
    }

    @Override
    public String getDescription() {
        return "Place multiple existing model elements onto a view as visual figures in a single call. "
                + "Returns a result entry per element, with per-item success or error.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the target view");

        ObjectNode figures = properties.putObject("figures");
        figures.put("type", "array");
        figures.put("description", "List of elements to place on the view");
        ObjectNode items = figures.putObject("items");
        items.put("type", "object");
        ObjectNode itemProps = items.putObject("properties");
        itemProps.putObject("element_id").put("type", "string");
        ObjectNode x = itemProps.putObject("x");
        x.put("type", "integer");
        x.put("default", 50);
        ObjectNode y = itemProps.putObject("y");
        y.put("type", "integer");
        y.put("default", 50);
        ObjectNode width = itemProps.putObject("width");
        width.put("type", "integer");
        width.put("default", 120);
        ObjectNode height = itemProps.putObject("height");
        height.put("type", "integer");
        height.put("default", 55);
        ArrayNode itemRequired = items.putArray("required");
        itemRequired.add("element_id");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");
        required.add("figures");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        JsonNode figuresNode = args.get("figures");
        if (!figuresNode.isArray() || figuresNode.isEmpty()) {
            throw new Exception("'figures' must be a non-empty array");
        }

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        List<Map<String, Object>> results = UiThreadUtil.syncExec(() -> {
            List<Map<String, Object>> entries = new ArrayList<>();

            for (JsonNode item : figuresNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                String elementId = item.get("element_id").asText();
                entry.put("element_id", elementId);
                try {
                    IArchimateElement element = ModelAccessor.findElementById(model, elementId);
                    if (element == null) {
                        entry.put("error", "Element not found: " + elementId);
                        entries.add(entry);
                        continue;
                    }

                    int x = item.path("x").asInt(50);
                    int y = item.path("y").asInt(50);
                    int w = item.path("width").asInt(120);
                    int h = item.path("height").asInt(55);

                    IDiagramModelArchimateObject figure =
                            IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
                    figure.setArchimateElement(element);
                    IBounds bounds = IArchimateFactory.eINSTANCE.createBounds();
                    bounds.setX(x);
                    bounds.setY(y);
                    bounds.setWidth(w);
                    bounds.setHeight(h);
                    figure.setBounds(bounds);
                    view.getChildren().add(figure);

                    entry.put("figure_id", figure.getId());
                } catch (Exception e) {
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
}

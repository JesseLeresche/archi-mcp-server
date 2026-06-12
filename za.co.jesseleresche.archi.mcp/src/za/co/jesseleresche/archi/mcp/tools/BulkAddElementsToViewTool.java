package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.commands.CompoundCommand;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
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
        ObjectNode parentFigureId = itemProps.putObject("parent_figure_id");
        parentFigureId.put("type", "string");
        parentFigureId.put("description",
                "Optional: ID of a parent figure (group or element) on this view to nest this "
                        + "element inside. When set, x/y are relative to the parent's client area.");
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
            // Pair each validated figure with its target container, then commit them all
            // through a single CompoundCommand so a batch add undoes in one step.
            List<IDiagramModelArchimateObject> figures = new ArrayList<>();
            List<IDiagramModelContainer> containers = new ArrayList<>();
            List<Map<String, Object>> figureEntries = new ArrayList<>();

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

                    // Resolve the target container. When parent_figure_id is supplied it must
                    // exist on this view and be a container; otherwise fail explicitly rather
                    // than silently falling back to top-level placement.
                    IDiagramModelContainer parentContainer = view;
                    if (item.hasNonNull("parent_figure_id")) {
                        String parentFigureId = item.get("parent_figure_id").asText();
                        IDiagramModelObject parentObj =
                                ModelAccessor.findDiagramObjectById(view, parentFigureId);
                        if (parentObj == null) {
                            entry.put("error", "Parent figure not found on view: " + parentFigureId);
                            entries.add(entry);
                            continue;
                        }
                        if (!(parentObj instanceof IDiagramModelContainer)) {
                            entry.put("error",
                                    "Parent figure is not a container (cannot hold children): "
                                            + parentFigureId);
                            entries.add(entry);
                            continue;
                        }
                        parentContainer = (IDiagramModelContainer) parentObj;
                    }

                    int x = item.path("x").asInt(50);
                    int y = item.path("y").asInt(50);
                    int w = item.path("width").asInt(120);
                    int h = item.path("height").asInt(55);

                    IDiagramModelArchimateObject figure =
                            IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
                    figure.setArchimateElement(element);
                    IBounds bounds = IArchimateFactory.eINSTANCE.createBounds();
                    // x/y are parent-relative when nesting; absolute canvas coords at top level.
                    bounds.setX(x);
                    bounds.setY(y);
                    bounds.setWidth(w);
                    bounds.setHeight(h);
                    figure.setBounds(bounds);

                    figures.add(figure);
                    containers.add(parentContainer);
                    // figure_id is assigned once the figure is added to the model (below).
                    figureEntries.add(entry);
                } catch (Exception e) {
                    entry.put("error", e.getMessage());
                }
                entries.add(entry);
            }

            if (!figures.isEmpty()) {
                CompoundCommand compound = new CompoundCommand("Add Elements to View");
                for (int i = 0; i < figures.size(); i++) {
                    final IDiagramModelArchimateObject figure = figures.get(i);
                    final IDiagramModelContainer container = containers.get(i);
                    compound.add(new Command("Add Element to View") {
                        @Override
                        public void execute() {
                            container.getChildren().add(figure);
                        }

                        @Override
                        public void undo() {
                            container.getChildren().remove(figure);
                        }
                    });
                }

                CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);
                if (stack != null) { stack.execute(compound); } else { compound.execute(); }

                // IDs are assigned when the figures join the model, so read them now.
                for (int i = 0; i < figures.size(); i++) {
                    figureEntries.get(i).put("figure_id", figures.get(i).getId());
                }
                IEditorModelManager.INSTANCE.saveModel(model);
            }

            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }
}

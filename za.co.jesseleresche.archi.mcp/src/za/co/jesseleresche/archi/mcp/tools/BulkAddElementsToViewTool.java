package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.commands.CompoundCommand;

import za.co.jesseleresche.archi.mcp.util.FigureStyle;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.util.UUIDFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Adds figures to a view in one undoable step: element figures, groups and notes
 * ({@code kind}), optionally nested ({@code parent_figure_id}, which may name a figure created
 * earlier in the same batch), with an optional caller-supplied {@code figure_id} and any
 * {@link FigureStyle} fields applied at creation.
 */
public class BulkAddElementsToViewTool implements ITool {

    @Override
    public String getName() {
        return "bulk_add_elements_to_view";
    }

    @Override
    public String getDescription() {
        return "Add element figures, groups and notes to a view in a single call.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("view_id").put("type", "string");
        props.putObject("figures").put("type", "array").putObject("items").put("type", "object");
        schema.putArray("required").add("view_id").add("figures");
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
            // Validated figures and their containers, committed through one CompoundCommand so a
            // batch add undoes in one step. Figures get their IDs up front so later items in the
            // batch can nest inside earlier ones.
            List<IDiagramModelObject> figures = new ArrayList<>();
            List<IDiagramModelContainer> containers = new ArrayList<>();
            Map<String, IDiagramModelObject> pending = new HashMap<>();
            Set<String> claimed = new HashSet<>();

            for (JsonNode item : figuresNode) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entries.add(entry);
                try {
                    String kind = item.path("kind").asText("element");
                    entry.put("kind", kind);
                    String figureId = ModelAccessor.requestedId(item, "figure_id");
                    if (figureId != null) {
                        EObject existing = ModelAccessor.claimId(model, figureId, item, claimed);
                        if (existing != null) {
                            entry.put("figure_id", figureId);
                            entry.put("skipped", true);
                            continue;
                        }
                    }

                    IDiagramModelObject figure = create(model, kind, item, entry);
                    IDiagramModelContainer parent = resolveParent(view, item, pending);
                    figure.setBounds(item.path("x").asInt(50), item.path("y").asInt(50),
                            item.path("width").asInt(defaultWidth(kind)),
                            item.path("height").asInt(defaultHeight(kind)));
                    FigureStyle.apply(item, figure, model);
                    figure.setId(figureId != null ? figureId : UUIDFactory.createID(figure));

                    figures.add(figure);
                    containers.add(parent);
                    pending.put(figure.getId(), figure);
                    claimed.add(figure.getId());
                    entry.put("figure_id", figure.getId());
                } catch (Exception e) {
                    entry.put("error", e.getMessage());
                }
            }

            if (!figures.isEmpty()) {
                CompoundCommand compound = new CompoundCommand("Add Figures to View");
                for (int i = 0; i < figures.size(); i++) {
                    final IDiagramModelObject figure = figures.get(i);
                    final IDiagramModelContainer container = containers.get(i);
                    compound.add(new Command("Add Figure to View") {
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
                ModelAccessor.saveModel(model);
            }
            return entries;
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        return ToolRegistry.MAPPER.writeValueAsString(response);
    }

    private static IDiagramModelObject create(IArchimateModel model, String kind, JsonNode item,
            Map<String, Object> entry) throws Exception {
        switch (kind) {
            case "element": {
                String elementId = ConsolidatedTool.requireText(item, "element_id");
                entry.put("element_id", elementId);
                IArchimateElement element = ModelAccessor.findElementById(model, elementId);
                if (element == null) {
                    throw new Exception("Element not found: " + elementId);
                }
                IDiagramModelArchimateObject f = IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
                f.setArchimateElement(element);
                return f;
            }
            case "group": {
                IDiagramModelGroup g = IArchimateFactory.eINSTANCE.createDiagramModelGroup();
                g.setName(item.path("name").asText("Group"));
                if (item.hasNonNull("documentation")) {
                    g.setDocumentation(item.get("documentation").asText());
                }
                return g;
            }
            case "note": {
                IDiagramModelNote n = IArchimateFactory.eINSTANCE.createDiagramModelNote();
                n.setContent(item.path("content").asText(""));
                return n;
            }
            default:
                throw new Exception("kind must be one of element, group, note (got '" + kind + "')");
        }
    }

    /** The view, or the parent figure (on the view or created earlier in this batch). */
    private static IDiagramModelContainer resolveParent(IArchimateDiagramModel view, JsonNode item,
            Map<String, IDiagramModelObject> pending) throws Exception {
        if (!item.hasNonNull("parent_figure_id") || item.get("parent_figure_id").asText().isEmpty()) {
            return view;
        }
        String parentId = item.get("parent_figure_id").asText();
        IDiagramModelObject parent = pending.get(parentId);
        if (parent == null) {
            parent = ModelAccessor.findDiagramObjectById(view, parentId);
        }
        if (parent == null) {
            throw new Exception("Parent figure not found on view: " + parentId);
        }
        if (!(parent instanceof IDiagramModelContainer container)) {
            throw new Exception("Parent figure is not a container (cannot hold children): " + parentId);
        }
        return container;
    }

    private static int defaultWidth(String kind) {
        return switch (kind) {
            case "group" -> 400;
            case "note" -> 185;
            default -> 120;
        };
    }

    private static int defaultHeight(String kind) {
        return switch (kind) {
            case "group" -> 140;
            case "note" -> 80;
            default -> 55;
        };
    }
}

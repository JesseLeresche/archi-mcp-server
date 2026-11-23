package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import za.co.jesseleresche.archi.mcp.util.ConnectableRemoval;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Deletes an element from the model, including all connected relationships,
 * view figures, and visual connections.
 */
public class DeleteElementTool implements ITool {

    @Override
    public String getName() {
        return "delete_element";
    }

    @Override
    public String getDescription() {
        return "Delete an element from the model by ID. Also removes all relationships "
                + "connected to it, all figures on views, and all visual connections.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description", "ID of the element to delete");

        ObjectNode dryRun = properties.putObject("dry_run");
        dryRun.put("type", "boolean");
        dryRun.put("default", false);
        dryRun.put("description",
                "If true, returns what would be deleted without making any changes");

        ArrayNode required = schema.putArray("required");
        required.add("element_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String elementId = args.get("element_id").asText();
        boolean dryRun = args.has("dry_run") && args.get("dry_run").asBoolean(false);

        IArchimateElement element = ModelAccessor.findElementById(model, elementId);
        if (element == null) {
            throw new Exception("Element not found: " + elementId);
        }

        String elementName = element.getName();
        String elementType = element.eClass().getName();

        // Collect all relationships connected to this element
        List<IArchimateRelationship> relationships = new ArrayList<>();
        for (IArchimateRelationship rel : ModelAccessor.collectAllFromFolders(model, IArchimateRelationship.class)) {
            if (elementId.equals(rel.getSource().getId()) || elementId.equals(rel.getTarget().getId())) {
                relationships.add(rel);
            }
        }

        // Every figure of the element on every view, plus every drawing of its relationships;
        // the removal takes each subtree and every attached connection with it.
        List<IConnectable> removed = new ArrayList<>();
        Set<String> affectedViewIds = new LinkedHashSet<>();
        int figureCount = 0;
        for (IArchimateDiagramModel view : ModelAccessor.getAllViews(model)) {
            List<IConnectable> onView = new ArrayList<>(ModelAccessor.findAllFiguresByElementId(view, elementId));
            figureCount += onView.size();
            for (IArchimateRelationship rel : relationships) {
                onView.addAll(ModelAccessor.findConnectionsByRelationshipId(view, rel.getId()));
            }
            if (!onView.isEmpty()) {
                affectedViewIds.add(view.getId());
                removed.addAll(onView);
            }
        }
        ConnectableRemoval removal = new ConnectableRemoval(removed);

        if (dryRun) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("relationships_to_delete", relationships.size());
            result.put("figures_to_remove", figureCount);
            result.put("connections_to_remove", removal.connectionCount());
            result.put("affected_view_ids", affectedViewIds);
            return ToolRegistry.MAPPER.writeValueAsString(result);
        }

        int[] counts = {relationships.size(), figureCount};
        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            // Capture state for undo: element folder and index
            final IFolder elementFolder = (IFolder) element.eContainer();
            final int elementIndex = elementFolder.getElements().indexOf(element);

            // Capture relationship folders/indexes for undo
            final List<Object[]> relState = new ArrayList<>();
            for (IArchimateRelationship rel : relationships) {
                IFolder f = (IFolder) rel.eContainer();
                relState.add(new Object[]{rel, f, f.getElements().indexOf(rel)});
            }

            CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);

            Command cmd = new Command("Delete Element") {
                @Override
                public void execute() {
                    removal.execute();
                    for (IArchimateRelationship rel : relationships) {
                        if (rel.eContainer() instanceof IFolder f) {
                            f.getElements().remove(rel);
                        }
                    }
                    elementFolder.getElements().remove(element);
                }

                @Override
                public void undo() {
                    elementFolder.getElements().add(
                            Math.min(elementIndex, elementFolder.getElements().size()), element);
                    for (Object[] state : relState) {
                        IArchimateRelationship rel = (IArchimateRelationship) state[0];
                        IFolder f = (IFolder) state[1];
                        int idx = (int) state[2];
                        f.getElements().add(Math.min(idx, f.getElements().size()), rel);
                    }
                    removal.undo();
                }
            };

            if (stack != null) {
                stack.execute(cmd);
            } else {
                cmd.execute();
            }

            ModelAccessor.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("relationships_deleted", counts[0]);
            entry.put("figures_removed", counts[1]);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

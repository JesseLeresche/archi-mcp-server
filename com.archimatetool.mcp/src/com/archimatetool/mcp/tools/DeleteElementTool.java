package com.archimatetool.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.util.EcoreUtil;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.mcp.util.ModelAccessor;
import com.archimatetool.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
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

        // Collect all views and find affected figures/connections
        List<IArchimateDiagramModel> views = ModelAccessor.getAllViews(model);
        List<String> affectedViewIds = new ArrayList<>();
        int figureCount = 0;
        int connectionCount = 0;
        for (IArchimateDiagramModel view : views) {
            IDiagramModelArchimateObject fig = ModelAccessor.findFigureByElementId(view, elementId);
            if (fig != null) {
                affectedViewIds.add(view.getId());
                figureCount++;
                connectionCount += fig.getSourceConnections().size() + fig.getTargetConnections().size();
            }
        }

        if (dryRun) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("element_id", elementId);
            result.put("element_name", elementName);
            result.put("element_type", elementType);
            result.put("relationships_to_delete", relationships.size());
            result.put("figures_to_remove", figureCount);
            result.put("connections_to_remove", connectionCount);
            result.put("affected_view_ids", affectedViewIds);
            result.put("dry_run", true);
            result.put("success", true);
            return ToolRegistry.MAPPER.writeValueAsString(result);
        }

        int[] counts = {relationships.size(), figureCount};
        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            // Remove figures from views (connections removed automatically via EcoreUtil.delete)
            for (IArchimateDiagramModel view : views) {
                IDiagramModelArchimateObject fig = ModelAccessor.findFigureByElementId(view, elementId);
                if (fig != null) {
                    // Remove all source/target connections first
                    List<IDiagramModelConnection> conns = new ArrayList<>(fig.getSourceConnections());
                    conns.addAll(fig.getTargetConnections());
                    for (IDiagramModelConnection conn : conns) {
                        EcoreUtil.delete(conn, true);
                    }
                    EcoreUtil.delete(fig, true);
                }
            }

            // Remove relationships
            for (IArchimateRelationship rel : relationships) {
                EcoreUtil.delete(rel, true);
            }

            // Remove the element itself
            EcoreUtil.delete(element, true);

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("element_id", elementId);
            entry.put("element_name", elementName);
            entry.put("element_type", elementType);
            entry.put("relationships_deleted", counts[0]);
            entry.put("figures_removed", counts[1]);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

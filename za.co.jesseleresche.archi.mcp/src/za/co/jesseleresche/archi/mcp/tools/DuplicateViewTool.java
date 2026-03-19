package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Duplicates an existing view, copying all figures and connections to a new view.
 * Useful as a starting point for views with similar structure.
 */
public class DuplicateViewTool implements ITool {

    @Override
    public String getName() {
        return "duplicate_view";
    }

    @Override
    public String getDescription() {
        return "Clone an existing view (all figures and connections) as a new view. "
                + "Useful when creating views that share the same structure or elements.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode sourceViewId = properties.putObject("source_view_id");
        sourceViewId.put("type", "string");
        sourceViewId.put("description", "ID of the view to duplicate");

        ObjectNode newName = properties.putObject("new_name");
        newName.put("type", "string");
        newName.put("description", "Name for the new view");

        ObjectNode targetFolderId = properties.putObject("target_folder_id");
        targetFolderId.put("type", "string");
        targetFolderId.put("description",
                "Optional: folder to place the new view in. "
                        + "If omitted, placed in the same folder as the source view.");

        ArrayNode required = schema.putArray("required");
        required.add("source_view_id");
        required.add("new_name");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String sourceViewId = args.get("source_view_id").asText();
        String newName = args.get("new_name").asText();
        String targetFolderId = args.has("target_folder_id") ? args.get("target_folder_id").asText() : null;

        IArchimateDiagramModel sourceView = ModelAccessor.findViewById(model, sourceViewId);
        if (sourceView == null) {
            throw new Exception("Source view not found: " + sourceViewId);
        }

        IFolder targetFolder;
        if (targetFolderId != null) {
            targetFolder = ModelAccessor.findFolderById(model, targetFolderId);
            if (targetFolder == null) {
                throw new Exception("Target folder not found: " + targetFolderId);
            }
        } else {
            targetFolder = sourceView.eContainer() instanceof IFolder f ? f : ModelAccessor.getViewsFolder(model);
        }

        IFolder resolvedFolder = targetFolder;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            IArchimateDiagramModel newView = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
            newView.setName(newName);
            if (sourceView.getDocumentation() != null) {
                newView.setDocumentation(sourceView.getDocumentation());
            }

            resolvedFolder.getElements().add(newView);

            // Pass 1: recursively copy figures, preserving nesting hierarchy
            Map<String, IDiagramModelArchimateObject> figureMap = new HashMap<>();
            copyFigures(sourceView, newView, figureMap);

            // Pass 2: copy connections using the figure map (across all depth levels)
            int connectionCount = 0;
            for (var oldFig : ModelAccessor.collectAllFigures(sourceView)) {
                for (var conn : oldFig.getSourceConnections()) {
                    if (conn instanceof IDiagramModelArchimateConnection oldConn) {
                        IDiagramModelArchimateObject newSourceFig = figureMap.get(oldFig.getId());
                        String targetId = oldConn.getTarget() != null ? oldConn.getTarget().getId() : null;
                        IDiagramModelArchimateObject newTargetFig =
                                targetId != null ? figureMap.get(targetId) : null;

                        if (newSourceFig == null || newTargetFig == null) continue;

                        IDiagramModelArchimateConnection newConn =
                                IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
                        newConn.setArchimateRelationship(oldConn.getArchimateRelationship());
                        newConn.connect(newSourceFig, newTargetFig);

                        for (IDiagramModelBendpoint oldBp : oldConn.getBendpoints()) {
                            IDiagramModelBendpoint newBp =
                                    IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
                            newBp.setStartX(oldBp.getStartX());
                            newBp.setStartY(oldBp.getStartY());
                            newBp.setEndX(oldBp.getEndX());
                            newBp.setEndY(oldBp.getEndY());
                            newConn.getBendpoints().add(newBp);
                        }

                        if (oldConn.getLineColor() != null) newConn.setLineColor(oldConn.getLineColor());
                        newConn.setLineWidth(oldConn.getLineWidth());
                        if (oldConn.getFontColor() != null) newConn.setFontColor(oldConn.getFontColor());
                        newConn.setTextPosition(oldConn.getTextPosition());
                        connectionCount++;
                    }
                }
            }

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("new_view_id", newView.getId());
            entry.put("new_view_name", newView.getName());
            entry.put("source_view_id", sourceViewId);
            entry.put("folder_id", resolvedFolder.getId());
            entry.put("figure_count", figureMap.size());
            entry.put("connection_count", connectionCount);
            entry.put("success", true);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    private void copyFigures(IDiagramModelContainer source, IDiagramModelContainer target,
            Map<String, IDiagramModelArchimateObject> figureMap) {
        for (var child : source.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject oldFig) {
                IDiagramModelArchimateObject newFig =
                        IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
                newFig.setArchimateElement(oldFig.getArchimateElement());

                IBounds oldBounds = oldFig.getBounds();
                IBounds newBounds = IArchimateFactory.eINSTANCE.createBounds();
                newBounds.setX(oldBounds.getX());
                newBounds.setY(oldBounds.getY());
                newBounds.setWidth(oldBounds.getWidth());
                newBounds.setHeight(oldBounds.getHeight());
                newFig.setBounds(newBounds);

                if (oldFig.getFillColor() != null) newFig.setFillColor(oldFig.getFillColor());
                if (oldFig.getLineColor() != null) newFig.setLineColor(oldFig.getLineColor());
                if (oldFig.getFontColor() != null) newFig.setFontColor(oldFig.getFontColor());
                newFig.setAlpha(oldFig.getAlpha());
                newFig.setTextAlignment(oldFig.getTextAlignment());

                target.getChildren().add(newFig);
                figureMap.put(oldFig.getId(), newFig);

                // Recurse into nested children
                copyFigures(oldFig, newFig, figureMap);
            }
        }
    }
}

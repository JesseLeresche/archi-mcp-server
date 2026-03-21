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
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.ITextContent;
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

            // Pass 1: recursively copy all diagram objects, preserving nesting hierarchy
            Map<String, IDiagramModelObject> objectMap = new HashMap<>();
            copyDiagramObjects(sourceView, newView, objectMap);

            // Pass 2: copy connections using the object map (across all depth levels)
            int connectionCount = 0;
            for (var oldObj : ModelAccessor.collectAllDiagramObjects(sourceView)) {
                for (var conn : oldObj.getSourceConnections()) {
                    IDiagramModelObject newSource = objectMap.get(oldObj.getId());
                    String targetId = conn.getTarget() != null ? conn.getTarget().getId() : null;
                    IDiagramModelObject newTarget =
                            targetId != null ? objectMap.get(targetId) : null;

                    if (newSource == null || newTarget == null) continue;

                    if (conn instanceof IDiagramModelArchimateConnection oldConn) {
                        IDiagramModelArchimateConnection newConn =
                                IArchimateFactory.eINSTANCE.createDiagramModelArchimateConnection();
                        newConn.setArchimateRelationship(oldConn.getArchimateRelationship());
                        newConn.connect(newSource, newTarget);

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
                    } else {
                        IDiagramModelConnection newConn =
                                IArchimateFactory.eINSTANCE.createDiagramModelConnection();
                        newConn.connect(newSource, newTarget);

                        for (IDiagramModelBendpoint oldBp : conn.getBendpoints()) {
                            IDiagramModelBendpoint newBp =
                                    IArchimateFactory.eINSTANCE.createDiagramModelBendpoint();
                            newBp.setStartX(oldBp.getStartX());
                            newBp.setStartY(oldBp.getStartY());
                            newBp.setEndX(oldBp.getEndX());
                            newBp.setEndY(oldBp.getEndY());
                            newConn.getBendpoints().add(newBp);
                        }

                        if (conn.getLineColor() != null) newConn.setLineColor(conn.getLineColor());
                        newConn.setLineWidth(conn.getLineWidth());
                        if (conn.getFontColor() != null) newConn.setFontColor(conn.getFontColor());
                        newConn.setTextPosition(conn.getTextPosition());
                    }
                    connectionCount++;
                }
            }

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("new_view_id", newView.getId());
            entry.put("folder_id", resolvedFolder.getId());
            entry.put("figure_count", objectMap.size());
            entry.put("connection_count", connectionCount);
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    private void copyDiagramObjects(IDiagramModelContainer source, IDiagramModelContainer target,
            Map<String, IDiagramModelObject> objectMap) {
        for (var child : source.getChildren()) {
            IDiagramModelObject newObj = null;

            if (child instanceof IDiagramModelArchimateObject oldFig) {
                IDiagramModelArchimateObject newFig =
                        IArchimateFactory.eINSTANCE.createDiagramModelArchimateObject();
                newFig.setArchimateElement(oldFig.getArchimateElement());
                copyAppearance(oldFig, newFig);
                newObj = newFig;
            } else if (child instanceof IDiagramModelGroup oldGroup) {
                IDiagramModelGroup newGroup =
                        IArchimateFactory.eINSTANCE.createDiagramModelGroup();
                newGroup.setName(oldGroup.getName());
                if (oldGroup.getDocumentation() != null) {
                    newGroup.setDocumentation(oldGroup.getDocumentation());
                }
                copyAppearance(oldGroup, newGroup);
                newObj = newGroup;
            } else if (child instanceof IDiagramModelNote oldNote) {
                IDiagramModelNote newNote =
                        IArchimateFactory.eINSTANCE.createDiagramModelNote();
                if (oldNote.getContent() != null) {
                    newNote.setContent(oldNote.getContent());
                }
                copyAppearance(oldNote, newNote);
                newObj = newNote;
            }

            if (newObj != null) {
                target.getChildren().add(newObj);
                objectMap.put(child.getId(), newObj);

                // Recurse into nested children if both are containers
                if (child instanceof IDiagramModelContainer oldContainer
                        && newObj instanceof IDiagramModelContainer newContainer) {
                    copyDiagramObjects(oldContainer, newContainer, objectMap);
                }
            }
        }
    }

    private void copyAppearance(IDiagramModelObject source, IDiagramModelObject target) {
        IBounds oldBounds = source.getBounds();
        IBounds newBounds = IArchimateFactory.eINSTANCE.createBounds();
        newBounds.setX(oldBounds.getX());
        newBounds.setY(oldBounds.getY());
        newBounds.setWidth(oldBounds.getWidth());
        newBounds.setHeight(oldBounds.getHeight());
        target.setBounds(newBounds);

        if (source.getFillColor() != null) target.setFillColor(source.getFillColor());
        if (source.getLineColor() != null) target.setLineColor(source.getLineColor());
        if (source.getFontColor() != null) target.setFontColor(source.getFontColor());
        target.setAlpha(source.getAlpha());
        target.setTextAlignment(source.getTextAlignment());
    }
}

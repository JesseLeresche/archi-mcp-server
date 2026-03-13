package com.archimatetool.mcp.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;

/**
 * Utility methods for safe model access.
 * <p>
 * All read-only operations may be called from any thread.
 * All mutation operations must be called from within {@link UiThreadUtil#syncExec}.
 */
public class ModelAccessor {

    private static volatile String selectedModelId;

    /**
     * Returns all open models from the Archi editor.
     */
    public static List<IArchimateModel> getAllModels() {
        return IEditorModelManager.INSTANCE.getModels();
    }

    /**
     * Returns the currently selected model, or the first open model if none is selected.
     * Returns null if no models are open.
     */
    public static IArchimateModel getOpenModel() {
        List<IArchimateModel> models = IEditorModelManager.INSTANCE.getModels();
        if (models.isEmpty()) return null;

        if (selectedModelId != null) {
            for (IArchimateModel model : models) {
                if (selectedModelId.equals(model.getId())) {
                    return model;
                }
            }
            // Selected model no longer open, clear selection
            selectedModelId = null;
        }
        return models.get(0);
    }

    /**
     * Select a model by name or ID. Returns the matched model, or null if not found.
     */
    public static IArchimateModel selectModel(String nameOrId) {
        List<IArchimateModel> models = IEditorModelManager.INSTANCE.getModels();
        for (IArchimateModel model : models) {
            if (nameOrId.equals(model.getId()) || nameOrId.equalsIgnoreCase(model.getName())) {
                selectedModelId = model.getId();
                return model;
            }
        }
        return null;
    }

    /**
     * Returns the ID of the currently selected model, or null if using default.
     */
    public static String getSelectedModelId() {
        return selectedModelId;
    }

    /**
     * Find an element by its ID within the given model.
     */
    public static IArchimateElement findElementById(IArchimateModel model, String id) {
        return collectAllFromFolders(model, IArchimateElement.class).stream()
                .filter(e -> id.equals(e.getId()))
                .findFirst().orElse(null);
    }

    /**
     * Find a relationship by its ID within the given model.
     */
    public static IArchimateRelationship findRelationshipById(IArchimateModel model, String id) {
        return collectAllFromFolders(model, IArchimateRelationship.class).stream()
                .filter(r -> id.equals(r.getId()))
                .findFirst().orElse(null);
    }

    /**
     * Recursively collect all objects of a given type from all folders in the model.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> collectAllFromFolders(IArchimateModel model, Class<T> type) {
        List<T> results = new ArrayList<>();
        for (IFolder folder : model.getFolders()) {
            collectFromFolder(folder, type, results);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private static <T> void collectFromFolder(IFolder folder, Class<T> type, List<T> results) {
        for (EObject element : folder.getElements()) {
            if (type.isInstance(element)) {
                results.add((T) element);
            }
        }
        for (IFolder sub : folder.getFolders()) {
            collectFromFolder(sub, type, results);
        }
    }

    /**
     * Find a diagram view by its ID, searching recursively through all folders.
     */
    public static IArchimateDiagramModel findViewById(IArchimateModel model, String id) {
        for (IFolder folder : model.getFolders()) {
            IArchimateDiagramModel result = searchFolderForView(folder, id);
            if (result != null) return result;
        }
        return null;
    }

    private static IArchimateDiagramModel searchFolderForView(IFolder folder, String id) {
        for (var element : folder.getElements()) {
            if (element instanceof IArchimateDiagramModel v && id.equals(v.getId())) return v;
        }
        for (IFolder sub : folder.getFolders()) {
            IArchimateDiagramModel result = searchFolderForView(sub, id);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Save the model via the Archi editor model manager.
     */
    public static void saveModel(IArchimateModel model) throws IOException {
        IEditorModelManager.INSTANCE.saveModel(model);
    }

    /**
     * Find a diagram figure (IDiagramModelArchimateObject) on a view by its figure ID.
     * Searches top-level children only -- does not recurse into nested containers.
     */
    public static IDiagramModelArchimateObject findFigureById(
            IArchimateDiagramModel view, String figureId) {
        return view.getChildren().stream()
                .filter(c -> c instanceof IDiagramModelArchimateObject
                        && figureId.equals(c.getId()))
                .map(c -> (IDiagramModelArchimateObject) c)
                .findFirst().orElse(null);
    }

    /**
     * Find the figure on a view whose archimateElement has the given element ID.
     * Searches top-level children only.
     */
    public static IDiagramModelArchimateObject findFigureByElementId(
            IArchimateDiagramModel view, String elementId) {
        return view.getChildren().stream()
                .filter(c -> c instanceof IDiagramModelArchimateObject dmo
                        && elementId.equals(dmo.getArchimateElement().getId()))
                .map(c -> (IDiagramModelArchimateObject) c)
                .findFirst().orElse(null);
    }

    /**
     * Find an existing visual connection on a view for a given relationship ID.
     * Checks connections attached to each figure's source connections.
     * Returns null if no connection exists for this relationship.
     */
    public static IDiagramModelArchimateConnection findConnectionByRelationshipId(
            IArchimateDiagramModel view, String relationshipId) {
        for (var child : view.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject figure) {
                for (var conn : figure.getSourceConnections()) {
                    if (conn instanceof IDiagramModelArchimateConnection c
                            && relationshipId.equals(c.getArchimateRelationship().getId())) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find a visual connection on a view by its own connection ID.
     * Checks source connections on all figures.
     * Returns null if not found.
     */
    public static IDiagramModelArchimateConnection findConnectionById(
            IArchimateDiagramModel view, String connectionId) {
        for (var child : view.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject figure) {
                for (var conn : figure.getSourceConnections()) {
                    if (conn instanceof IDiagramModelArchimateConnection c
                            && connectionId.equals(c.getId())) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns all diagram views in the model, searching recursively through all folders.
     */
    public static List<IArchimateDiagramModel> getAllViews(IArchimateModel model) {
        return collectAllFromFolders(model, IArchimateDiagramModel.class);
    }

    /**
     * Find a folder by its ID, searching recursively through all root folders.
     * Returns null if not found.
     */
    public static IFolder findFolderById(IArchimateModel model, String id) {
        for (IFolder folder : model.getFolders()) {
            IFolder result = searchFolderById(folder, id);
            if (result != null) return result;
        }
        return null;
    }

    private static IFolder searchFolderById(IFolder folder, String id) {
        if (id.equals(folder.getId())) return folder;
        for (IFolder sub : folder.getFolders()) {
            IFolder result = searchFolderById(sub, id);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Returns the root Views folder (the default folder for diagram models).
     */
    public static IFolder getViewsFolder(IArchimateModel model) {
        com.archimatetool.model.IArchimateDiagramModel probe =
                com.archimatetool.model.IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
        return model.getDefaultFolderForObject(probe);
    }

    /**
     * Resolve an ArchiMate element EClass by type name (case-insensitive).
     * Returns null if no matching element type is found.
     */
    public static EClass resolveElementClass(String typeName) {
        return IArchimatePackage.eINSTANCE.getEClassifiers().stream()
                .filter(c -> c instanceof EClass ec
                        && ec.getName().equalsIgnoreCase(typeName)
                        && IArchimatePackage.eINSTANCE.getArchimateElement()
                                .isSuperTypeOf(ec))
                .map(c -> (EClass) c)
                .findFirst().orElse(null);
    }

    /**
     * Resolve an ArchiMate relationship EClass by type name (case-insensitive).
     * Returns null if no matching relationship type is found.
     */
    public static EClass resolveRelationshipClass(String typeName) {
        return IArchimatePackage.eINSTANCE.getEClassifiers().stream()
                .filter(c -> c instanceof EClass ec
                        && ec.getName().equalsIgnoreCase(typeName)
                        && IArchimatePackage.eINSTANCE.getArchimateRelationship()
                                .isSuperTypeOf(ec))
                .map(c -> (EClass) c)
                .findFirst().orElse(null);
    }
}

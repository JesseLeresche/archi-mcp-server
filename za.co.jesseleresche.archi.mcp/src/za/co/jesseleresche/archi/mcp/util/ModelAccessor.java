package za.co.jesseleresche.archi.mcp.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;

/**
 * Utility methods for safe model access.
 * <p>
 * All read-only operations may be called from any thread.
 * All mutation operations must be called from within {@link UiThreadUtil#syncExec}.
 */
public class ModelAccessor {

    private static volatile String selectedModelId;

    /** Where open models come from and how they are saved. Tests supply an in-memory one. */
    public interface ModelSource {
        List<IArchimateModel> getModels();

        void save(IArchimateModel model) throws IOException;
    }

    /** Default source; a holder class so IEditorModelManager is only loaded when used. */
    private static final class EditorSource implements ModelSource {
        static final EditorSource INSTANCE = new EditorSource();

        @Override
        public List<IArchimateModel> getModels() {
            return IEditorModelManager.INSTANCE.getModels();
        }

        @Override
        public void save(IArchimateModel model) throws IOException {
            IEditorModelManager.INSTANCE.saveModel(model);
        }
    }

    private static volatile ModelSource source;

    /** Replace the model source; {@code null} restores the Archi editor. Clears the selection. */
    public static void setModelSource(ModelSource s) {
        source = s;
        selectedModelId = null;
    }

    private static ModelSource source() {
        ModelSource s = source;
        return s != null ? s : EditorSource.INSTANCE;
    }

    /**
     * Returns all open models from the Archi editor.
     */
    public static List<IArchimateModel> getAllModels() {
        return source().getModels();
    }

    /**
     * Returns the currently selected model, or the first open model if none is selected.
     * Returns null if no models are open.
     */
    public static IArchimateModel getOpenModel() {
        List<IArchimateModel> models = source().getModels();
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

    /** The {@code model} arg's open model (name or ID, selection unchanged), else the selected one. */
    public static IArchimateModel resolveModel(com.fasterxml.jackson.databind.JsonNode args) throws Exception {
        if (args.hasNonNull("model")) {
            String m = args.get("model").asText();
            IArchimateModel model = findModel(m);
            if (model == null) {
                throw new Exception("Model not found: " + m);
            }
            return model;
        }
        IArchimateModel model = getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }
        return model;
    }

    /** An open model by ID or (case-insensitive) name, without changing the selection; null if none. */
    public static IArchimateModel findModel(String nameOrId) {
        for (IArchimateModel model : source().getModels()) {
            if (nameOrId.equals(model.getId()) || nameOrId.equalsIgnoreCase(model.getName())) {
                return model;
            }
        }
        return null;
    }

    /**
     * Select a model by name or ID. Returns the matched model, or null if not found.
     */
    public static IArchimateModel selectModel(String nameOrId) {
        List<IArchimateModel> models = source().getModels();
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
     * Collect all objects of a given type from a specific folder (and subfolders).
     */
    public static <T> List<T> collectFromFolder(IFolder folder, Class<T> type) {
        List<T> results = new ArrayList<>();
        collectFromFolder(folder, type, results);
        return results;
    }

    /**
     * Collect all IDiagramModelArchimateObject figures on a view,
     * recursing into nested containers.
     */
    public static List<IDiagramModelArchimateObject> collectAllFigures(
            IArchimateDiagramModel view) {
        List<IDiagramModelArchimateObject> results = new ArrayList<>();
        collectFiguresRecursive(view.getChildren(), results);
        return results;
    }

    private static void collectFiguresRecursive(
            java.util.List<? extends org.eclipse.emf.ecore.EObject> children,
            List<IDiagramModelArchimateObject> results) {
        for (var child : children) {
            if (child instanceof IDiagramModelArchimateObject dmo) {
                results.add(dmo);
            }
            if (child instanceof com.archimatetool.model.IDiagramModelContainer container) {
                collectFiguresRecursive(container.getChildren(), results);
            }
        }
    }

    /**
     * Collect all IDiagramModelObject instances on a view (ArchiMate objects,
     * groups, and notes), recursing into nested containers.
     */
    public static List<IDiagramModelObject> collectAllDiagramObjects(
            IArchimateDiagramModel view) {
        List<IDiagramModelObject> results = new ArrayList<>();
        collectDiagramObjectsRecursive(view.getChildren(), results);
        return results;
    }

    private static void collectDiagramObjectsRecursive(
            java.util.List<? extends EObject> children,
            List<IDiagramModelObject> results) {
        for (var child : children) {
            if (child instanceof IDiagramModelObject dmo) {
                results.add(dmo);
            }
            if (child instanceof IDiagramModelContainer container) {
                collectDiagramObjectsRecursive(container.getChildren(), results);
            }
        }
    }

    /**
     * Find any diagram object on a view by its ID (ArchiMate object, group, or note).
     * Searches recursively through nested containers.
     */
    public static IDiagramModelObject findDiagramObjectById(
            IArchimateDiagramModel view, String id) {
        return collectAllDiagramObjects(view).stream()
                .filter(dmo -> id.equals(dmo.getId()))
                .findFirst().orElse(null);
    }

    /**
     * Find all figures on a view (recursively through nested containers)
     * whose archimateElement has the given element ID.
     */
    public static List<IDiagramModelArchimateObject> findAllFiguresByElementId(
            IArchimateDiagramModel view, String elementId) {
        return collectAllFigures(view).stream()
                .filter(dmo -> elementId.equals(dmo.getArchimateElement().getId()))
                .collect(java.util.stream.Collectors.toList());
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
        source().save(model);
    }

    /**
     * Find a diagram figure (IDiagramModelArchimateObject) on a view by its figure ID.
     * Searches recursively through nested containers.
     */
    public static IDiagramModelArchimateObject findFigureById(
            IArchimateDiagramModel view, String figureId) {
        return collectAllFigures(view).stream()
                .filter(dmo -> figureId.equals(dmo.getId()))
                .findFirst().orElse(null);
    }

    /**
     * Find the first figure on a view whose archimateElement has the given element ID.
     * Searches recursively through nested containers.
     */
    public static IDiagramModelArchimateObject findFigureByElementId(
            IArchimateDiagramModel view, String elementId) {
        return collectAllFigures(view).stream()
                .filter(dmo -> elementId.equals(dmo.getArchimateElement().getId()))
                .findFirst().orElse(null);
    }

    /**
     * Every connection attached at either end to a diagram object on the view, then the
     * connections attached to those connections, transitively. Distinct, in tree order.
     * Either-end, so a connection whose source was detached (a target-side ghost) is included.
     */
    public static Collection<IDiagramModelConnection> connectionsOnView(IArchimateDiagramModel view) {
        return attachedConnections(collectAllDiagramObjects(view));
    }

    /**
     * The seeds that are connections, plus every connection attached at either end to a seed,
     * then to those connections, transitively. Distinct, breadth-first from the seeds' order.
     */
    public static Set<IDiagramModelConnection> attachedConnections(Collection<? extends IConnectable> seeds) {
        Set<IDiagramModelConnection> out = new LinkedHashSet<>();
        List<IConnectable> todo = new ArrayList<>(seeds);
        for (IConnectable seed : seeds) {
            if (seed instanceof IDiagramModelConnection k) {
                out.add(k);
            }
        }
        for (int i = 0; i < todo.size(); i++) {
            IConnectable c = todo.get(i);
            List<IDiagramModelConnection> attached = new ArrayList<>(c.getSourceConnections());
            attached.addAll(c.getTargetConnections());
            for (IDiagramModelConnection k : attached) {
                if (out.add(k)) {
                    todo.add(k);
                }
            }
        }
        return out;
    }

    /** Every connection on the view (see {@link #connectionsOnView}) drawing the relationship. */
    public static List<IDiagramModelArchimateConnection> findConnectionsByRelationshipId(
            IArchimateDiagramModel view, String relationshipId) {
        List<IDiagramModelArchimateConnection> out = new ArrayList<>();
        for (IDiagramModelConnection k : connectionsOnView(view)) {
            if (k instanceof IDiagramModelArchimateConnection c
                    && relationshipId.equals(c.getArchimateRelationship().getId())) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Find an existing visual connection on a view for a given relationship ID: the first
     * match in {@link #connectionsOnView} order. Returns null if none.
     */
    public static IDiagramModelArchimateConnection findConnectionByRelationshipId(
            IArchimateDiagramModel view, String relationshipId) {
        List<IDiagramModelArchimateConnection> all = findConnectionsByRelationshipId(view, relationshipId);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Find a visual connection on a view by its own connection ID, attached at either end to
     * the view (see {@link #connectionsOnView}). Returns null if not found.
     */
    public static IDiagramModelArchimateConnection findConnectionById(
            IArchimateDiagramModel view, String connectionId) {
        return findAnyConnectionById(view, connectionId) instanceof IDiagramModelArchimateConnection c ? c : null;
    }

    /** Like {@link #findConnectionById}, but also finds non-ArchiMate connections (plain lines). */
    public static IDiagramModelConnection findAnyConnectionById(IArchimateDiagramModel view, String connectionId) {
        for (IDiagramModelConnection k : connectionsOnView(view)) {
            if (connectionId.equals(k.getId())) {
                return k;
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
     * Returns the folder's full path from its root folder, e.g. "Application/Service domains".
     */
    public static String getFolderPath(IFolder folder) {
        List<String> names = new ArrayList<>();
        IFolder current = folder;
        while (current != null) {
            names.add(0, current.getName());
            current = current.eContainer() instanceof IFolder parent ? parent : null;
        }
        return String.join("/", names);
    }

    /**
     * Resolve a "/"-separated folder path (e.g. "Application/Service domains") under the given
     * root folder, creating any missing intermediate folders. Segment matching is exact
     * (case-sensitive) against existing subfolder names.
     */
    public static IFolder findOrCreateFolderByPath(IFolder root, String path) {
        IFolder current = root;
        for (String segment : path.split("/")) {
            if (segment.isBlank()) continue;
            IFolder next = null;
            for (IFolder sub : current.getFolders()) {
                if (segment.equals(sub.getName())) {
                    next = sub;
                    break;
                }
            }
            if (next == null) {
                next = com.archimatetool.model.IArchimateFactory.eINSTANCE.createFolder();
                next.setName(segment);
                current.getFolders().add(next);
            }
            current = next;
        }
        return current;
    }

    /**
     * Resolve a "/"-separated folder path whose first segment names one of the model's
     * top-level folders (e.g. "Application/Service domains"), matched case-insensitively;
     * remaining segments are resolved/created under it. Returns null if the first segment
     * doesn't match any top-level folder.
     */
    public static IFolder findOrCreateFolderByPath(IArchimateModel model, String path) {
        String[] segments = path.split("/", 2);
        String rootName = segments[0];
        IFolder root = null;
        for (IFolder f : model.getFolders()) {
            if (rootName.equalsIgnoreCase(f.getName())) {
                root = f;
                break;
            }
        }
        if (root == null) {
            return null;
        }
        return segments.length > 1 ? findOrCreateFolderByPath(root, segments[1]) : root;
    }

    /**
     * Like {@link #findOrCreateFolderByPath(IArchimateModel, String)} but anchored to the section
     * implied by {@code obj}: a path whose first segment names the object's own section is used
     * as-is, a first segment naming a different top-level section is rejected, and any other path
     * (e.g. "Data Flows") is created under the object's section.
     */
    public static IFolder findOrCreateFolderByPath(IArchimateModel model, String path, EObject obj)
            throws Exception {
        IFolder section = getSectionFor(model, obj);
        if (section == null) {
            return findOrCreateFolderByPath(model, path);
        }
        String[] segments = path.split("/", 2);
        for (IFolder f : model.getFolders()) {
            if (segments[0].equalsIgnoreCase(f.getName())) {
                if (f != section) {
                    throw new Exception("folder_path '" + path + "' is under section '" + f.getName()
                            + "'; " + kindOf(obj) + " must be placed under the '" + section.getName()
                            + "' section. Use folder_path \"" + section.getName() + "/...\".");
                }
                return segments.length > 1 ? findOrCreateFolderByPath(f, segments[1]) : f;
            }
        }
        return findOrCreateFolderByPath(section, path);
    }

    /** Top-level (section) folder containing {@code folder}, or the folder itself if it is one. */
    public static IFolder getSectionFolder(IFolder folder) {
        IFolder current = folder;
        while (current.eContainer() instanceof IFolder parent) {
            current = parent;
        }
        return current;
    }

    /** Section folder an object of this kind belongs in (e.g. Relations for relationships). */
    public static IFolder getSectionFor(IArchimateModel model, EObject obj) {
        IFolder def = model.getDefaultFolderForObject(obj);
        return def == null ? null : getSectionFolder(def);
    }

    /** Section folder for an element/relationship EClass, e.g. "Application". */
    public static IFolder getSectionFor(IArchimateModel model, EClass eClass) {
        return getSectionFor(model, com.archimatetool.model.IArchimateFactory.eINSTANCE.create(eClass));
    }

    private static String kindOf(EObject obj) {
        if (obj instanceof IArchimateRelationship) return "relationships";
        if (obj instanceof com.archimatetool.model.IDiagramModel) return "views";
        return "elements of this type";
    }

    /**
     * Rejects a target folder that lives under the wrong top-level section for {@code obj}
     * (e.g. a relationship into a Views-section folder).
     */
    public static void requireSection(IArchimateModel model, IFolder target, EObject obj) throws Exception {
        IFolder expected = getSectionFor(model, obj);
        IFolder actual = getSectionFolder(target);
        if (expected != null && expected != actual) {
            throw new Exception("folder_id " + target.getId() + " ('" + getFolderPath(target)
                    + "') is under section '" + actual.getName() + "'; " + kindOf(obj)
                    + " must be placed under the '" + expected.getName() + "' section. Use folder_path \""
                    + expected.getName() + "/...\" or a " + expected.getName() + "-section folder.");
        }
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

    /**
     * A caller-supplied ID from {@code item[key]}, or null when absent/null.
     *
     * @throws Exception if present but empty or whitespace
     */
    public static String requestedId(com.fasterxml.jackson.databind.JsonNode item, String key) throws Exception {
        if (!item.hasNonNull(key)) {
            return null;
        }
        String id = item.get(key).asText();
        if (id.isBlank()) {
            throw new Exception("'" + key + "' must not be empty");
        }
        return id;
    }

    /**
     * Checks a caller-supplied ID against everything in the model (elements, relationships,
     * views, folders, figures, connections) plus IDs already used earlier in the same batch
     * ({@code batch}; the caller adds an ID once its object is created). Returns the existing object
     * when it is taken and {@code on_conflict} is "skip"; returns null when the ID is free.
     *
     * @throws Exception when the ID is taken and {@code on_conflict} is "error" (the default)
     */
    public static EObject claimId(IArchimateModel model, String id, com.fasterxml.jackson.databind.JsonNode item,
            java.util.Set<String> batch) throws Exception {
        String mode = item.path("on_conflict").asText("error");
        if (!mode.equals("error") && !mode.equals("skip")) {
            throw new Exception("on_conflict must be 'error' or 'skip'");
        }
        EObject existing = com.archimatetool.model.util.ArchimateModelUtils.getObjectByID(model, id);
        if (existing == null && !batch.contains(id)) {
            return null;
        }
        if (mode.equals("skip") && existing != null) {
            return existing;
        }
        throw new Exception("ID already exists in the model: " + id);
    }

    /**
     * Applies {@code properties: [{key, value}]} (existing keys updated in place, new keys
     * appended, a null value removes the key) and {@code remove_properties: [key, ...]}.
     * Returns how many properties were actually removed.
     */
    public static int applyProperties(com.archimatetool.model.IProperties target,
            com.fasterxml.jackson.databind.JsonNode item) {
        int removed = 0;
        if (item.has("properties") && item.get("properties").isArray()) {
            for (var prop : item.get("properties")) {
                String key = prop.path("key").asText();
                var value = prop.get("value");
                if (value == null || value.isNull()) {
                    if (target.getProperties().removeIf(p -> key.equals(p.getKey()))) {
                        removed++;
                    }
                } else {
                    setProperty(target, key, value.asText());
                }
            }
        }
        if (item.has("remove_properties") && item.get("remove_properties").isArray()) {
            for (var keyNode : item.get("remove_properties")) {
                String key = keyNode.asText();
                if (target.getProperties().removeIf(p -> key.equals(p.getKey()))) {
                    removed++;
                }
            }
        }
        return removed;
    }

    private static void setProperty(com.archimatetool.model.IProperties target, String key, String value) {
        for (var prop : target.getProperties()) {
            if (key.equals(prop.getKey())) {
                prop.setValue(value);
                return;
            }
        }
        var p = com.archimatetool.model.IArchimateFactory.eINSTANCE.createProperty();
        p.setKey(key);
        p.setValue(value);
        target.getProperties().add(p);
    }

    /** Properties as [{key, value}] in model order. */
    public static List<java.util.Map<String, String>> readProperties(com.archimatetool.model.IProperties target) {
        List<java.util.Map<String, String>> out = new ArrayList<>();
        for (var p : target.getProperties()) {
            java.util.Map<String, String> e = new java.util.LinkedHashMap<>();
            e.put("key", p.getKey());
            e.put("value", p.getValue());
            out.add(e);
        }
        return out;
    }

    /**
     * Applies {@code specialization} if present: a name sets the concept's (single, primary)
     * specialization, finding or creating the model profile with that name for the concept's
     * type; null or "" clears it. Profile images are not created.
     */
    public static void applySpecialization(IArchimateModel model, com.archimatetool.model.IArchimateConcept concept,
            com.fasterxml.jackson.databind.JsonNode item) {
        if (!item.has("specialization")) {
            return;
        }
        var n = item.get("specialization");
        concept.getProfiles().clear();
        if (n.isNull() || n.asText().isEmpty()) {
            return;
        }
        concept.getProfiles().add(findOrCreateProfile(model, n.asText(), concept.eClass().getName()));
    }

    private static com.archimatetool.model.IProfile findOrCreateProfile(IArchimateModel model, String name,
            String conceptType) {
        for (var p : model.getProfiles()) {
            if (name.equals(p.getName()) && conceptType.equals(p.getConceptType())) {
                return p;
            }
        }
        var p = com.archimatetool.model.IArchimateFactory.eINSTANCE.createProfile();
        p.setName(name);
        p.setConceptType(conceptType);
        p.setSpecialization(true);
        model.getProfiles().add(p);
        return p;
    }

    /** The concept's specialization name, or null. */
    public static String getSpecialization(com.archimatetool.model.IArchimateConcept concept) {
        var p = concept.getPrimaryProfile();
        return p == null ? null : p.getName();
    }
}

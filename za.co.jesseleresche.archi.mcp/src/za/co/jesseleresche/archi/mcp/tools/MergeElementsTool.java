package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IConnectable;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ConnectableRemoval;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;

/**
 * Merges duplicate elements into a surviving element (manage_elements merge).
 * Per-operation class: owns the diff, the merge command, syncExec and the save.
 *
 * <p>Relationships are re-pointed and figures retargeted in place (IDs kept); relationships that
 * would become survivor self-loops are deleted with their connections. Conflicting property,
 * documentation (and, on collapse, relationship name) values block the merge and are returned as
 * a diff until the caller re-sends with {@code resolve}. A blocked item is returned, not thrown,
 * so it can carry the diff; only invalid requests throw.
 */
public class MergeElementsTool implements ITool {

    private static final String UNRESOLVED = "unresolved conflicts";

    @Override
    public String getName() {
        return "merge_elements";
    }

    @Override
    public String getDescription() {
        return "Merge duplicate elements into a surviving element.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        return schema;
    }

    @Override
    public String execute(JsonNode item) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        // --- validate (FR-11): every failure here throws and changes nothing
        String sid = ConsolidatedTool.requireText(item, "survivor_id");
        IArchimateElement survivor = ModelAccessor.findElementById(model, sid);
        if (survivor == null) {
            throw new Exception("Survivor not found or not an element: " + sid);
        }
        JsonNode dupIds = item.get("duplicate_ids");
        if (dupIds == null || !dupIds.isArray() || dupIds.isEmpty()) {
            throw new Exception("duplicate_ids must be a non-empty array of element IDs");
        }
        List<IArchimateElement> dups = new ArrayList<>();
        for (JsonNode n : dupIds) {
            String id = n.asText();
            if (id.equals(sid)) {
                throw new Exception("Survivor " + sid + " is also listed in duplicate_ids");
            }
            IArchimateElement d = ModelAccessor.findElementById(model, id);
            if (d == null) {
                throw new Exception("Duplicate not found or not an element: " + id);
            }
            if (dups.contains(d)) {
                throw new Exception("duplicate_ids repeats " + id);
            }
            if (d.eClass() != survivor.eClass()) {
                throw new Exception("Duplicate " + id + " is a " + d.eClass().getName() + " but the survivor is a "
                        + survivor.eClass().getName() + "; cross-type merge is not supported");
            }
            dups.add(d);
        }
        boolean collapse = item.path("collapse").asBoolean(false);
        boolean dryRun = item.path("dry_run").asBoolean(false);
        JsonNode resolve = item.path("resolve");
        if (!resolve.isMissingNode() && !resolve.isNull() && !resolve.isObject()) {
            throw new Exception("resolve must be an object");
        }

        // --- analysis (reads only)
        Set<IArchimateConcept> dupSet = new HashSet<>(dups);
        List<IArchimateRelationship> rels = ModelAccessor.collectAllFromFolders(model, IArchimateRelationship.class);
        Map<IArchimateRelationship, IArchimateConcept[]> ends = new LinkedHashMap<>(); // originals, for undo
        List<IArchimateRelationship> repoint = new ArrayList<>();
        Set<IArchimateRelationship> selfLoops = new LinkedHashSet<>();
        for (IArchimateRelationship rel : rels) {
            if (dupSet.contains(rel.getSource()) || dupSet.contains(rel.getTarget())) {
                repoint.add(rel);
                if (after(rel.getSource(), dupSet, survivor) == survivor
                        && after(rel.getTarget(), dupSet, survivor) == survivor) {
                    selfLoops.add(rel);
                }
            }
        }

        // Parallel groups (FR-19). With collapse, relationships on collapsed relationships are
        // re-pointed too, so regroup once with the final ends (collapsed -> kept).
        // ponytail: one regroup covers one level of relationship-on-relationship nesting.
        Map<IArchimateRelationship, IArchimateRelationship> keptOf = new LinkedHashMap<>(); // collapsed -> kept
        java.util.function.UnaryOperator<IArchimateConcept> fin = e -> {
            IArchimateConcept a = after(e, dupSet, survivor);
            IArchimateRelationship k = keptOf.get(a);
            return k != null ? k : a;
        };
        Map<IArchimateRelationship, List<IArchimateRelationship>> groups =
                groups(rels, selfLoops, new HashSet<>(repoint), fin, dups);
        if (collapse) {
            groups.forEach((kept, parallel) -> parallel.forEach(r -> keptOf.put(r, kept)));
            Set<IArchimateRelationship> touched = new HashSet<>(repoint);
            for (IArchimateRelationship rel : rels) {
                if (keptOf.containsKey(rel.getSource()) || keptOf.containsKey(rel.getTarget())) {
                    touched.add(rel);
                }
            }
            groups = groups(rels, selfLoops, touched, fin, dups);
            keptOf.clear();
            groups.forEach((kept, parallel) -> parallel.forEach(r -> keptOf.put(r, kept)));
        }
        ArrayNode parallelJson = ToolRegistry.MAPPER.createArrayNode();
        groups.forEach((kept, parallel) -> {
            ObjectNode g = parallelJson.addObject().put("kept", kept.getId());
            ArrayNode p = g.putArray("parallel");
            parallel.forEach(r -> p.add(r.getId()));
        });

        // Diffs (FR-15..20)
        ArrayNode conflicts = ToolRegistry.MAPPER.createArrayNode();
        Diff elementDiff = new Diff(survivor, dups, false, null, conflicts);
        Map<String, Diff> relDiffs = new LinkedHashMap<>();
        if (collapse) {
            groups.forEach((kept, parallel) -> relDiffs.put(kept.getId(),
                    new Diff(kept, parallel, true, kept.getId(), conflicts)));
        }

        // Resolve (FR-17): strict, and a stale entry returns the current diff.
        String unmatched = null;
        if (resolve.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = resolve.fields(); it.hasNext() && unmatched == null;) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!e.getKey().equals("relationships")) {
                    unmatched = elementDiff.resolve(e.getKey(), e.getValue(), "");
                    continue;
                }
                for (Iterator<Map.Entry<String, JsonNode>> rit = objectFields(e.getValue(), "resolve.relationships");
                        rit.hasNext() && unmatched == null;) {
                    Map.Entry<String, JsonNode> re = rit.next();
                    String prefix = "relationships." + re.getKey() + ".";
                    Diff rd = relDiffs.get(re.getKey());
                    if (rd == null || rd.conflictPaths.isEmpty()) {
                        unmatched = "relationships." + re.getKey();
                        continue;
                    }
                    for (Iterator<Map.Entry<String, JsonNode>> fit = objectFields(re.getValue(), "resolve." + prefix);
                            fit.hasNext() && unmatched == null;) {
                        Map.Entry<String, JsonNode> fe = fit.next();
                        unmatched = rd.resolve(fe.getKey(), fe.getValue(), prefix);
                    }
                }
            }
        }
        if (unmatched != null) {
            return blocked("resolve entry '" + unmatched + "' matches no conflict", conflicts, parallelJson);
        }
        boolean pending = !elementDiff.pending.isEmpty() || relDiffs.values().stream().anyMatch(d -> !d.pending.isEmpty());
        if (pending && !dryRun) {
            return blocked(UNRESOLVED, conflicts, parallelJson);
        }

        // Figures and connections, per view.
        List<IConnectable> removedRoots = new ArrayList<>();
        Map<IDiagramModelArchimateConnection, IArchimateRelationship> moveFrom = new LinkedHashMap<>();
        Map<IDiagramModelArchimateObject, IArchimateElement> figures = new LinkedHashMap<>();
        Set<String> affectedViews = new LinkedHashSet<>();
        for (IArchimateDiagramModel view : ModelAccessor.getAllViews(model)) {
            int before = removedRoots.size() + moveFrom.size() + figures.size();
            for (IArchimateElement d : dups) {
                ModelAccessor.findAllFiguresByElementId(view, d.getId()).forEach(f -> figures.put(f, d));
            }
            for (IArchimateRelationship rel : selfLoops) {
                removedRoots.addAll(ModelAccessor.findConnectionsByRelationshipId(view, rel.getId()));
            }
            Map<IArchimateRelationship, List<IDiagramModelArchimateConnection>> drawn = new HashMap<>();
            keptOf.forEach((c, kept) -> {
                List<IDiagramModelArchimateConnection> keptDrawn = drawn.computeIfAbsent(kept,
                        k -> new ArrayList<>(ModelAccessor.findConnectionsByRelationshipId(view, k.getId())));
                for (IDiagramModelArchimateConnection k : ModelAccessor.findConnectionsByRelationshipId(view, c.getId())) {
                    if (keptDrawn.stream().anyMatch(o -> o.getSource() == k.getSource() && o.getTarget() == k.getTarget())) {
                        removedRoots.add(k); // FR-21: would double an existing drawing
                    } else {
                        moveFrom.put(k, c);
                        keptDrawn.add(k);
                    }
                }
            });
            if (removedRoots.size() + moveFrom.size() + figures.size() > before) {
                affectedViews.add(view.getId());
            }
        }
        ConnectableRemoval removal = new ConnectableRemoval(removedRoots);

        // Relationship-on-relationship re-pointing onto kept relationships (collapse only).
        List<IArchimateRelationship> relRepoint = new ArrayList<>();
        for (IArchimateRelationship rel : rels) {
            if (!keptOf.containsKey(rel)
                    && (keptOf.containsKey(rel.getSource()) || keptOf.containsKey(rel.getTarget()))) {
                relRepoint.add(rel);
            }
        }
        List<IArchimateRelationship> repointLive = new ArrayList<>(repoint);
        repointLive.removeIf(r -> selfLoops.contains(r) || keptOf.containsKey(r));
        for (IArchimateRelationship rel : repointLive) {
            ends.put(rel, new IArchimateConcept[] {rel.getSource(), rel.getTarget()});
        }
        for (IArchimateRelationship rel : relRepoint) {
            ends.putIfAbsent(rel, new IArchimateConcept[] {rel.getSource(), rel.getTarget()});
        }
        List<EObject> deletedRels = new ArrayList<>(selfLoops);
        deletedRels.addAll(keptOf.keySet());

        ObjectNode out = ToolRegistry.MAPPER.createObjectNode();
        out.put("survivor_id", sid);
        ArrayNode merged = out.putArray("merged");
        dups.forEach(d -> merged.add(d.getId()));
        out.put("relationships_repointed", repointLive.size());
        ArrayNode deleted = out.putArray("relationships_deleted");
        selfLoops.forEach(r -> deleted.add(r.getId()));
        ArrayNode collapsed = out.putArray("relationships_collapsed");
        keptOf.keySet().forEach(r -> collapsed.add(r.getId()));
        out.put("connections_moved", moveFrom.size());
        out.put("connections_removed", removal.connectionCount());
        out.put("figures_retargeted", figures.size());
        ArrayNode views = out.putArray("affected_view_ids");
        affectedViews.forEach(views::add);
        out.set("parallel_relationships", parallelJson);
        ObjectNode names = out.putObject("duplicate_names");
        for (IArchimateElement d : dups) {
            if (!nz(d.getName()).equals(nz(survivor.getName()))) {
                names.put(d.getId(), d.getName());
            }
        }
        if (dryRun) {
            out.put("dry_run", true);
            out.set("conflicts", conflicts);
            return ToolRegistry.MAPPER.writeValueAsString(out);
        }

        List<Diff> diffs = new ArrayList<>();
        diffs.add(elementDiff);
        diffs.addAll(relDiffs.values());
        List<EObject> folderRemovals = new ArrayList<>(deletedRels);
        folderRemovals.addAll(dups);

        return UiThreadUtil.syncExec(() -> {
            Command cmd = new Command("Merge Elements") {
                final List<Object[]> removedFromFolders = new ArrayList<>(); // {folder, index, object}

                @Override
                public void execute() {
                    removal.execute();
                    moveFrom.keySet().forEach(k -> k.setArchimateRelationship(keptOf.get(moveFrom.get(k))));
                    removedFromFolders.clear();
                    for (EObject o : deletedRels) {
                        removeFromFolder(o);
                    }
                    // Each final end once: duplicate -> survivor, collapsed -> kept.
                    ends.forEach((rel, e) -> {
                        rel.setSource(fin.apply(e[0]));
                        rel.setTarget(fin.apply(e[1]));
                    });
                    figures.keySet().forEach(f -> f.setArchimateElement(survivor));
                    diffs.forEach(Diff::apply);
                    for (IArchimateElement d : dups) {
                        removeFromFolder(d);
                    }
                }

                private void removeFromFolder(EObject o) {
                    IFolder f = (IFolder) o.eContainer();
                    removedFromFolders.add(new Object[] {f, f.getElements().indexOf(o), o});
                    f.getElements().remove(o);
                }

                @Override
                public void undo() {
                    for (int i = removedFromFolders.size() - 1; i >= 0; i--) {
                        Object[] r = removedFromFolders.get(i);
                        ((IFolder) r[0]).getElements().add((int) r[1], (EObject) r[2]);
                    }
                    diffs.forEach(Diff::undo);
                    figures.forEach(IDiagramModelArchimateObject::setArchimateElement);
                    moveFrom.forEach(IDiagramModelArchimateConnection::setArchimateRelationship);
                    removal.undo();
                    // Ends last, so nothing re-attached above can leave them re-synced.
                    ends.forEach((rel, e) -> {
                        rel.setSource(e[0]);
                        rel.setTarget(e[1]);
                    });
                }
            };

            CommandStack stack = (CommandStack) model.getAdapter(CommandStack.class);
            if (stack != null) {
                stack.execute(cmd);
            } else {
                cmd.execute();
            }
            ModelAccessor.saveModel(model);
            return ToolRegistry.MAPPER.writeValueAsString(out);
        });
    }

    /** The end after the merge: a duplicate becomes the survivor. */
    private static IArchimateConcept after(IArchimateConcept end, Set<IArchimateConcept> dupSet, IArchimateElement survivor) {
        return dupSet.contains(end) ? survivor : end;
    }

    /**
     * Parallel groups (FR-19): relationships with the same type, final ends and type-specific
     * attributes, where at least one was touched (re-pointed) by the merge. Only touched ones are
     * listed as parallel; untouched relationships are never collapsed. Kept: an untouched one, else
     * the first duplicate's, else model order.
     */
    private static Map<IArchimateRelationship, List<IArchimateRelationship>> groups(List<IArchimateRelationship> rels,
            Set<IArchimateRelationship> selfLoops, Set<IArchimateRelationship> touched,
            java.util.function.UnaryOperator<IArchimateConcept> fin, List<IArchimateElement> dups) {
        Map<List<Object>, List<IArchimateRelationship>> buckets = new LinkedHashMap<>();
        for (IArchimateRelationship rel : rels) {
            if (selfLoops.contains(rel)) {
                continue;
            }
            Map<String, Object> attrs = new HashMap<>();
            RelationshipAttributes.read(rel, attrs);
            buckets.computeIfAbsent(Arrays.asList(rel.eClass(), fin.apply(rel.getSource()),
                    fin.apply(rel.getTarget()), attrs), k -> new ArrayList<>()).add(rel);
        }
        Map<IArchimateRelationship, List<IArchimateRelationship>> groups = new LinkedHashMap<>();
        for (List<IArchimateRelationship> bucket : buckets.values()) {
            if (bucket.size() < 2 || bucket.stream().noneMatch(touched::contains)) {
                continue;
            }
            List<IArchimateRelationship> byKeep = new ArrayList<>(bucket); // stable: ties stay in model order
            byKeep.sort(java.util.Comparator.comparingInt(r -> {
                if (!touched.contains(r)) {
                    return -1;
                }
                int rank = dupRank(r, dups);
                return rank < 0 ? dups.size() : rank;
            }));
            IArchimateRelationship kept = byKeep.get(0);
            List<IArchimateRelationship> parallel = new ArrayList<>(bucket);
            parallel.removeIf(r -> r == kept || !touched.contains(r));
            if (!parallel.isEmpty()) {
                groups.put(kept, parallel);
            }
        }
        return groups;
    }

    /** Index in duplicate_ids of the first duplicate at either end of a re-pointed relationship. */
    private static int dupRank(IArchimateRelationship r, List<IArchimateElement> dups) {
        int s = dups.indexOf(r.getSource());
        int t = dups.indexOf(r.getTarget());
        return s < 0 ? t : t < 0 ? s : Math.min(s, t);
    }

    private static String blocked(String error, ArrayNode conflicts, ArrayNode parallel) throws Exception {
        ObjectNode out = ToolRegistry.MAPPER.createObjectNode();
        out.put("error", error);
        out.set("conflicts", conflicts);
        out.set("parallel_relationships", parallel);
        return ToolRegistry.MAPPER.writeValueAsString(out);
    }

    private static Iterator<Map.Entry<String, JsonNode>> objectFields(JsonNode n, String path) throws Exception {
        if (!n.isObject()) {
            throw new Exception(path + " must be an object");
        }
        return n.fields();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * One concept's diff against the concepts merged into it, plus the changes that resolve it:
     * conflicts are appended to the shared list; fills and resolutions are applied by the command.
     */
    private static final class Diff {
        final IArchimateConcept target;
        /** Conflict paths: "properties.<key>", "documentation", "name". */
        final Set<String> conflictPaths = new LinkedHashSet<>();
        final Set<String> pending = new LinkedHashSet<>();
        /** Final values per key: appended (fill) unless the key is in {@link #replace}. */
        final Map<String, List<String>> props = new LinkedHashMap<>();
        final Set<String> replace = new HashSet<>();
        /** Final "documentation" / "name". */
        final Map<String, String> texts = new HashMap<>();
        private List<IProperty> snapshot;
        private String oldDoc;
        private String oldName;

        Diff(IArchimateConcept target, List<? extends IArchimateConcept> others, boolean withName, String relId,
                ArrayNode conflicts) {
            this.target = target;
            Map<String, List<String>> mine = values(target);
            Map<IArchimateConcept, Map<String, List<String>>> theirs = new LinkedHashMap<>();
            Set<String> keys = new LinkedHashSet<>(mine.keySet());
            for (IArchimateConcept o : others) {
                theirs.put(o, values(o));
                keys.addAll(theirs.get(o).keySet());
            }
            for (String key : keys) {
                List<String> own = mine.getOrDefault(key, List.of());
                Set<List<String>> distinct = new LinkedHashSet<>();
                if (!own.isEmpty()) {
                    distinct.add(own);
                }
                ObjectNode dupsNode = ToolRegistry.MAPPER.createObjectNode();
                theirs.forEach((o, v) -> {
                    if (v.containsKey(key)) {
                        distinct.add(v.get(key));
                        ArrayNode a = dupsNode.putArray(o.getId());
                        v.get(key).forEach(a::add);
                    }
                });
                if (distinct.size() > 1) {
                    ObjectNode c = conflict(conflicts, relId).put("property", key);
                    ArrayNode survivorValues = c.putArray("survivor");
                    own.forEach(survivorValues::add);
                    c.set("duplicates", dupsNode);
                    conflictPaths.add("properties." + key);
                } else if (own.isEmpty()) {
                    props.put(key, distinct.iterator().next());
                }
            }
            text("documentation", target.getDocumentation(), others, true, relId, conflicts);
            if (withName) {
                text("name", target.getName(), others, false, relId, conflicts);
            }
            pending.addAll(conflictPaths);
        }

        private void text(String field, String own, List<? extends IArchimateConcept> others, boolean doc,
                String relId, ArrayNode conflicts) {
            ObjectNode sides = ToolRegistry.MAPPER.createObjectNode();
            sides.put("survivor", nz(own).isEmpty() ? null : own);
            Set<String> distinct = new LinkedHashSet<>();
            if (!nz(own).isEmpty()) {
                distinct.add(own);
            }
            for (IArchimateConcept o : others) {
                String t = nz(doc ? o.getDocumentation() : o.getName());
                if (!t.isEmpty()) {
                    sides.put(o.getId(), t);
                    distinct.add(t);
                }
            }
            if (distinct.size() > 1) {
                conflict(conflicts, relId).set(field, sides);
                conflictPaths.add(field);
            } else if (nz(own).isEmpty() && !distinct.isEmpty()) {
                texts.put(field, distinct.iterator().next());
            }
        }

        private static ObjectNode conflict(ArrayNode conflicts, String relId) {
            ObjectNode c = conflicts.addObject();
            if (relId != null) {
                c.put("relationship", relId);
            }
            return c;
        }

        private static Map<String, List<String>> values(IArchimateConcept c) {
            Map<String, List<String>> out = new LinkedHashMap<>();
            for (IProperty p : c.getProperties()) {
                out.computeIfAbsent(p.getKey(), k -> new ArrayList<>()).add(nz(p.getValue()));
            }
            return out;
        }

        /** Record one resolve field; returns the unmatched path, or null. */
        String resolve(String field, JsonNode value, String prefix) throws Exception {
            if (field.equals("properties")) {
                for (Iterator<Map.Entry<String, JsonNode>> it = objectFields(value, "resolve." + prefix + "properties");
                        it.hasNext();) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String path = "properties." + e.getKey();
                    if (!conflictPaths.contains(path)) {
                        return prefix + path;
                    }
                    List<String> vals = new ArrayList<>();
                    if (e.getValue().isTextual()) {
                        vals.add(e.getValue().asText());
                    } else if (e.getValue().isArray()) {
                        for (JsonNode v : e.getValue()) {
                            if (!v.isTextual()) {
                                throw new Exception("resolve." + prefix + path + " must be a string or an array of strings");
                            }
                            vals.add(v.asText());
                        }
                    } else {
                        throw new Exception("resolve." + prefix + path + " must be a string or an array of strings");
                    }
                    props.put(e.getKey(), vals);
                    replace.add(e.getKey());
                    pending.remove(path);
                }
                return null;
            }
            if (!conflictPaths.contains(field)) {
                return prefix + field;
            }
            if (!value.isTextual()) {
                throw new Exception("resolve." + prefix + field + " must be a string");
            }
            texts.put(field, value.asText());
            pending.remove(field);
            return null;
        }

        void apply() {
            EList<IProperty> list = target.getProperties();
            snapshot = new ArrayList<>(list);
            oldDoc = target.getDocumentation();
            oldName = target.getName();
            props.forEach((key, vals) -> {
                int at = -1;
                if (replace.contains(key)) {
                    for (int i = list.size() - 1; i >= 0; i--) {
                        if (key.equals(list.get(i).getKey())) {
                            list.remove(i);
                            at = i;
                        }
                    }
                }
                if (at < 0) {
                    at = list.size();
                }
                for (String v : vals) {
                    IProperty p = IArchimateFactory.eINSTANCE.createProperty();
                    p.setKey(key);
                    p.setValue(v);
                    list.add(at++, p);
                }
            });
            if (texts.containsKey("documentation")) {
                target.setDocumentation(texts.get("documentation"));
            }
            if (texts.containsKey("name")) {
                target.setName(texts.get("name"));
            }
        }

        void undo() {
            target.getProperties().clear();
            target.getProperties().addAll(snapshot);
            target.setDocumentation(oldDoc);
            target.setName(oldName);
        }
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelBendpoint;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ExchangeDir;
import za.co.jesseleresche.archi.mcp.util.FigureStyle;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

/**
 * Full definition of a view — {@code {view, elements, relationships, figures, connections}} —
 * using exactly the field names the create / add_figure / add_connection inputs take, so the
 * output can be replayed (possibly into another model) to reproduce the view. Figures are
 * listed parent-before-child. Read-only; {@code model} reads any open model without changing
 * the selection, {@code output_file} writes the definition to the exchange folder instead.
 */
public class GetViewDefinitionTool implements ITool {

    @Override
    public String getName() {
        return "get_view_definition";
    }

    @Override
    public String getDescription() {
        return "Full replayable definition of a view.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("view_id").put("type", "string");
        props.putObject("model").put("type", "string");
        props.putObject("output_file").put("type", "string");
        schema.putArray("required").add("view_id");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = resolveModel(args);
        String viewId = ConsolidatedTool.requireText(args, "view_id");
        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        Map<String, Object> def = define(view);
        String json = ToolRegistry.MAPPER.writeValueAsString(def);
        if (!args.hasNonNull("output_file")) {
            return json;
        }
        var path = ExchangeDir.write(args.get("output_file").asText(), json);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("output_file", path.toString());
        for (String k : List.of("elements", "relationships", "figures", "connections")) {
            out.put(k, ((List<?>) def.get(k)).size());
        }
        return ToolRegistry.MAPPER.writeValueAsString(out);
    }

    static Map<String, Object> define(IArchimateDiagramModel view) {
        Set<IArchimateElement> elements = new LinkedHashSet<>();
        Set<IArchimateRelationship> relationships = new LinkedHashSet<>();
        List<Map<String, Object>> figures = new ArrayList<>();
        List<Map<String, Object>> connections = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Pre-order traversal, so a figure always follows its parent.
        for (IDiagramModelObject obj : ModelAccessor.collectAllDiagramObjects(view)) {
            Map<String, Object> f = new LinkedHashMap<>();
            if (obj instanceof IDiagramModelArchimateObject dmo) {
                f.put("kind", "element");
                f.put("element_id", dmo.getArchimateElement().getId());
                elements.add(dmo.getArchimateElement());
            } else if (obj instanceof IDiagramModelGroup g) {
                f.put("kind", "group");
                f.put("name", g.getName());
                putIfSet(f, "documentation", g.getDocumentation());
            } else if (obj instanceof IDiagramModelNote n) {
                f.put("kind", "note");
                f.put("content", n.getContent() == null ? "" : n.getContent());
            } else {
                warnings.add("figure " + obj.getId() + " (" + obj.eClass().getName() + ") is not supported and was left out");
                continue;
            }
            f.put("figure_id", obj.getId());
            if (obj.eContainer() instanceof IDiagramModelObject parent) {
                f.put("parent_figure_id", parent.getId());
            }
            f.put("x", obj.getBounds().getX());
            f.put("y", obj.getBounds().getY());
            f.put("width", obj.getBounds().getWidth());
            f.put("height", obj.getBounds().getHeight());
            f.putAll(FigureStyle.read(obj));
            figures.add(f);

            for (IDiagramModelConnection conn : obj.getSourceConnections()) {
                if (!(conn instanceof IDiagramModelArchimateConnection c)) {
                    warnings.add("connection " + conn.getId() + " is not a relationship connection and was left out");
                    continue;
                }
                relationships.add(c.getArchimateRelationship());
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("relationship_id", c.getArchimateRelationship().getId());
                e.put("connection_id", c.getId());
                e.put("source_figure_id", c.getSource().getId());
                e.put("target_figure_id", c.getTarget().getId());
                if (!c.getBendpoints().isEmpty()) {
                    List<Map<String, Integer>> bps = new ArrayList<>();
                    for (IDiagramModelBendpoint bp : c.getBendpoints()) {
                        bps.add(Map.of("startX", bp.getStartX(), "startY", bp.getStartY(),
                                "endX", bp.getEndX(), "endY", bp.getEndY()));
                    }
                    e.put("bendpoints", bps);
                }
                e.putAll(FigureStyle.read(c));
                connections.add(e);
            }
        }

        Map<String, Object> viewEntry = new LinkedHashMap<>();
        viewEntry.put("id", view.getId());
        viewEntry.put("name", view.getName());
        putIfSet(viewEntry, "documentation", view.getDocumentation());
        viewEntry.put("folder_path", ModelAccessor.getFolderPath((IFolder) view.eContainer()));
        putProperties(viewEntry, view);

        List<Map<String, Object>> elementEntries = new ArrayList<>();
        for (IArchimateElement el : elements) {
            Map<String, Object> e = concept(el);
            elementEntries.add(e);
        }
        List<Map<String, Object>> relationshipEntries = new ArrayList<>();
        for (IArchimateRelationship r : relationships) {
            relationshipEntries.add(relationshipEntry(r));
        }

        Map<String, Object> def = new LinkedHashMap<>();
        def.put("view", viewEntry);
        def.put("elements", elementEntries);
        def.put("relationships", relationshipEntries);
        def.put("figures", figures);
        def.put("connections", connections);
        if (!warnings.isEmpty()) {
            def.put("warnings", warnings);
        }
        return def;
    }

    /** The {@code model} arg's open model (name or ID), else the selected one. */
    static IArchimateModel resolveModel(JsonNode args) throws Exception {
        if (args.hasNonNull("model")) {
            String m = args.get("model").asText();
            IArchimateModel model = ModelAccessor.findModel(m);
            if (model == null) {
                throw new Exception("Model not found: " + m);
            }
            return model;
        }
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }
        return model;
    }

    /** A relationship in create's input form; shared with {@code manage_relationships query}. */
    static Map<String, Object> relationshipEntry(IArchimateRelationship r) {
        Map<String, Object> e = concept(r);
        e.put("source_id", r.getSource().getId());
        e.put("target_id", r.getTarget().getId());
        RelationshipAttributes.read(r, e);
        return e;
    }

    private static Map<String, Object> concept(IArchimateConcept c) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", c.getId());
        e.put("type", c.eClass().getName());
        e.put("name", c.getName() == null ? "" : c.getName());
        putIfSet(e, "documentation", c.getDocumentation());
        e.put("folder_path", ModelAccessor.getFolderPath((IFolder) c.eContainer()));
        putProperties(e, c);
        String spec = ModelAccessor.getSpecialization(c);
        if (spec != null) {
            e.put("specialization", spec);
        }
        return e;
    }

    private static void putIfSet(Map<String, Object> m, String key, String value) {
        if (value != null && !value.isEmpty()) {
            m.put(key, value);
        }
    }

    private static void putProperties(Map<String, Object> m, IProperties p) {
        if (!p.getProperties().isEmpty()) {
            m.put("properties", ModelAccessor.readProperties(p));
        }
    }
}

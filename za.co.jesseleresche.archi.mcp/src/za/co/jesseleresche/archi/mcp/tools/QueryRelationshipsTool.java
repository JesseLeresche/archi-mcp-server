package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EClass;

import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelObject;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import za.co.jesseleresche.archi.mcp.util.ExchangeDir;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

/**
 * Model-wide relationship query: one filter object in ({@code element_id?, type_filter?,
 * on_view?, model?, output_file?}), {@code {count, relationships}} or
 * {@code {output_file, count}} out. Entries are in create's input form plus a read-only
 * {@code views}. Read-only. Internal helper behind {@code manage_relationships query}; not
 * registered.
 */
public class QueryRelationshipsTool implements ITool {

    @Override
    public String getName() {
        return "query_relationships";
    }

    @Override
    public String getDescription() {
        return "Query relationships across the whole model.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("element_id").put("type", "string");
        props.putObject("type_filter").put("type", "string");
        props.putObject("on_view").put("type", "boolean");
        props.putObject("model").put("type", "string");
        props.putObject("output_file").put("type", "string");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.resolveModel(args);
        EClass type = null;
        if (args.hasNonNull("type_filter")) {
            String t = args.get("type_filter").asText();
            type = ModelAccessor.resolveRelationshipClass(t);
            if (type == null) {
                throw new Exception("Unknown relationship type: " + t);
            }
        }
        String elementId = args.hasNonNull("element_id") ? args.get("element_id").asText() : null;
        if (elementId != null && !(ArchimateModelUtils.getObjectByID(model, elementId) instanceof IArchimateConcept)) {
            throw new Exception("Element or relationship not found: " + elementId);
        }
        Boolean onView = null;
        if (args.hasNonNull("on_view")) {
            if (!args.get("on_view").isBoolean()) {
                throw new Exception("on_view must be true or false, got " + args.get("on_view"));
            }
            onView = args.get("on_view").asBoolean();
        }

        // Views per relationship, in view folder order (Archi's referencing-components set is unordered).
        Map<IArchimateRelationship, Set<String>> drawnOn = new HashMap<>();
        for (IArchimateDiagramModel v : ModelAccessor.collectAllFromFolders(model, IArchimateDiagramModel.class)) {
            for (IDiagramModelObject o : ModelAccessor.collectAllDiagramObjects(v)) {
                for (IDiagramModelConnection conn : o.getSourceConnections()) {
                    if (conn instanceof IDiagramModelArchimateConnection c) {
                        drawnOn.computeIfAbsent(c.getArchimateRelationship(), k -> new LinkedHashSet<>()).add(v.getId());
                    }
                }
            }
        }

        List<Map<String, Object>> entries = new ArrayList<>();
        for (IArchimateRelationship r : ModelAccessor.collectAllFromFolders(model, IArchimateRelationship.class)) {
            if (type != null && r.eClass() != type) {
                continue;
            }
            if (elementId != null && !elementId.equals(r.getSource().getId())
                    && !elementId.equals(r.getTarget().getId())) {
                continue;
            }
            Set<String> views = drawnOn.getOrDefault(r, Set.of());
            if (onView != null && onView == views.isEmpty()) {
                continue;
            }
            Map<String, Object> e = GetViewDefinitionTool.relationshipEntry(r);
            e.put("views", new ArrayList<>(views));
            entries.add(e);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        if (args.hasNonNull("output_file")) {
            var path = ExchangeDir.write(args.get("output_file").asText(),
                    ToolRegistry.MAPPER.writeValueAsString(entries));
            out.put("output_file", path.toString());
            out.put("count", entries.size());
        } else {
            out.put("count", entries.size());
            out.put("relationships", entries);
        }
        return ToolRegistry.MAPPER.writeValueAsString(out);
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Provides analysis information for an element, mirroring the Analysis tab
 * in Archi's properties panel. Returns the element's relationships (incoming
 * and outgoing), views where it appears, and custom properties.
 * <p>
 * This enables targeted queries about element relationships and usage
 * without needing to scan the entire model.
 */
public class GetElementAnalysisTool implements ITool {

    @Override
    public String getName() {
        return "get_element_analysis";
    }

    @Override
    public String getDescription() {
        return "Analyze an element's relationships, view usage, and "
                + "properties. Returns incoming/outgoing relationships, "
                + "which views the element appears in (with each figure's "
                + "geometry and fill/line/font color, when set), and custom "
                + "properties (insertion order preserved). Use this instead "
                + "of scanning the model to find how elements are connected.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode elementId = properties.putObject("element_id");
        elementId.put("type", "string");
        elementId.put("description", "ID of the element to analyze");

        ObjectNode includeProperties = properties.putObject(
                "include_properties");
        includeProperties.put("type", "boolean");
        includeProperties.put("description",
                "Include custom key/value properties in the result "
                        + "(default: true)");

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
        boolean includeProperties = !args.has("include_properties")
                || args.get("include_properties").asBoolean(true);

        IArchimateElement element =
                ModelAccessor.findElementById(model, elementId);
        if (element == null) {
            throw new Exception("Element not found: " + elementId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", element.getName());
        result.put("type", element.eClass().getName());

        // Collect relationships
        List<IArchimateRelationship> allRelationships =
                ModelAccessor.collectAllFromFolders(
                        model, IArchimateRelationship.class);

        List<Map<String, String>> outgoing = new ArrayList<>();
        List<Map<String, String>> incoming = new ArrayList<>();

        for (IArchimateRelationship rel : allRelationships) {
            if (elementId.equals(rel.getSource().getId())) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("relationship_id", rel.getId());
                entry.put("relationship_type", rel.eClass().getName());
                entry.put("relationship_name",
                        rel.getName() != null ? rel.getName() : "");
                entry.put("target_id", rel.getTarget().getId());
                entry.put("target_name", rel.getTarget().getName());
                entry.put("target_type",
                        rel.getTarget().eClass().getName());
                outgoing.add(entry);
            }
            if (elementId.equals(rel.getTarget().getId())) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("relationship_id", rel.getId());
                entry.put("relationship_type", rel.eClass().getName());
                entry.put("relationship_name",
                        rel.getName() != null ? rel.getName() : "");
                entry.put("source_id", rel.getSource().getId());
                entry.put("source_name", rel.getSource().getName());
                entry.put("source_type",
                        rel.getSource().eClass().getName());
                incoming.add(entry);
            }
        }

        result.put("outgoing_relationships", outgoing);
        result.put("incoming_relationships", incoming);

        // Collect views where this element appears
        List<IArchimateDiagramModel> allViews =
                ModelAccessor.collectAllFromFolders(
                        model, IArchimateDiagramModel.class);

        List<Map<String, Object>> usedInViews = new ArrayList<>();
        for (IArchimateDiagramModel view : allViews) {
            IDiagramModelArchimateObject figure =
                    ModelAccessor.findFigureByElementId(view, elementId);
            if (figure != null) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("view_id", view.getId());
                entry.put("view_name", view.getName());
                entry.put("figure_id", figure.getId());
                IBounds bounds = figure.getBounds();
                if (bounds != null) {
                    entry.put("x", bounds.getX());
                    entry.put("y", bounds.getY());
                    entry.put("width", bounds.getWidth());
                    entry.put("height", bounds.getHeight());
                }
                if (figure.getFillColor() != null) {
                    entry.put("fill_color", figure.getFillColor());
                }
                if (figure.getLineColor() != null) {
                    entry.put("line_color", figure.getLineColor());
                }
                if (figure.getFontColor() != null) {
                    entry.put("font_color", figure.getFontColor());
                }
                usedInViews.add(entry);
            }
        }

        result.put("used_in_views", usedInViews);

        // Include custom properties
        if (includeProperties && !element.getProperties().isEmpty()) {
            List<Map<String, String>> props = new ArrayList<>();
            for (IProperty prop : element.getProperties()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("key", prop.getKey());
                entry.put("value", prop.getValue());
                props.add(entry);
            }
            result.put("properties", props);
        }

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

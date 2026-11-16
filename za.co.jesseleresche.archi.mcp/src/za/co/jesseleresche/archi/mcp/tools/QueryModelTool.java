package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.archimatetool.model.IApplicationElement;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IBusinessElement;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IImplementationMigrationElement;
import com.archimatetool.model.IMotivationElement;
import com.archimatetool.model.IProperty;
import com.archimatetool.model.ITechnologyElement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Lists all elements in the open model with optional filtering by type, layer, name, or folder.
 */
public class QueryModelTool implements ITool {

    @Override
    public String getName() {
        return "query_model";
    }

    @Override
    public String getDescription() {
        return "List all elements in the open model. Optionally filter by ArchiMate type, "
                + "layer, name substring, folder ID, or custom property (property_key, with "
                + "property_value for an exact match or property_value_prefix for a prefix match). "
                + "Set include_properties=true to include each element's custom properties "
                + "(as [{key, value}], insertion order preserved) in the results.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode typeFilter = properties.putObject("type_filter");
        typeFilter.put("type", "string");
        typeFilter.put("description", "ArchiMate type, e.g. ApplicationComponent");

        ObjectNode layerFilter = properties.putObject("layer_filter");
        layerFilter.put("type", "string");
        layerFilter.put("description", "Filter by ArchiMate layer");
        ArrayNode enumValues = layerFilter.putArray("enum");
        enumValues.add("Application");
        enumValues.add("Business");
        enumValues.add("Technology");
        enumValues.add("Motivation");
        enumValues.add("Implementation");

        ObjectNode nameSearch = properties.putObject("name_search");
        nameSearch.put("type", "string");
        nameSearch.put("description", "Case-insensitive partial name match");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description",
                "Optional: only return elements within this folder (and subfolders by default). "
                        + "Use include_subfolders=false to restrict to direct children only.");

        ObjectNode includeSubfolders = properties.putObject("include_subfolders");
        includeSubfolders.put("type", "boolean");
        includeSubfolders.put("default", true);
        includeSubfolders.put("description",
                "When folder_id is set: if true (default), include elements in all subfolders; "
                        + "if false, include only elements directly in that folder.");

        ObjectNode propertyKey = properties.putObject("property_key");
        propertyKey.put("type", "string");
        propertyKey.put("description", "Only return elements that have a custom property with this key.");

        ObjectNode propertyValue = properties.putObject("property_value");
        propertyValue.put("type", "string");
        propertyValue.put("description",
                "With property_key: only elements whose property value equals this exactly.");

        ObjectNode propertyValuePrefix = properties.putObject("property_value_prefix");
        propertyValuePrefix.put("type", "string");
        propertyValuePrefix.put("description",
                "With property_key: only elements whose property value starts with this "
                        + "(ignored if property_value is also set).");

        ObjectNode includeProperties = properties.putObject("include_properties");
        includeProperties.put("type", "boolean");
        includeProperties.put("default", false);
        includeProperties.put("description",
                "If true, include each element's custom properties as [{key, value}] "
                        + "(insertion order preserved) in the results.");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String typeFilter = args != null && args.has("type_filter")
                ? args.get("type_filter").asText() : null;
        String layerFilter = args != null && args.has("layer_filter")
                ? args.get("layer_filter").asText() : null;
        String nameSearch = args != null && args.has("name_search")
                ? args.get("name_search").asText().toLowerCase() : null;
        String folderId = args != null && args.has("folder_id")
                ? args.get("folder_id").asText() : null;
        boolean includeSubfolders = args == null || !args.has("include_subfolders")
                || args.get("include_subfolders").asBoolean(true);
        String propertyKey = args != null && args.has("property_key")
                ? args.get("property_key").asText() : null;
        String propertyValue = args != null && args.has("property_value")
                ? args.get("property_value").asText() : null;
        String propertyValuePrefix = args != null && args.has("property_value_prefix")
                ? args.get("property_value_prefix").asText() : null;
        boolean includeProperties = args != null && args.has("include_properties")
                && args.get("include_properties").asBoolean(false);

        List<IArchimateElement> candidates;

        if (folderId != null) {
            IFolder folder = ModelAccessor.findFolderById(model, folderId);
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
            if (includeSubfolders) {
                candidates = ModelAccessor.collectFromFolder(folder, IArchimateElement.class);
            } else {
                candidates = new ArrayList<>();
                for (var obj : folder.getElements()) {
                    if (obj instanceof IArchimateElement e) candidates.add(e);
                }
            }
        } else {
            candidates = ModelAccessor.collectAllFromFolders(model, IArchimateElement.class);
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (IArchimateElement element : candidates) {
            String layer = detectLayer(element);
            String type = element.eClass().getName();

            if (typeFilter != null && !type.equalsIgnoreCase(typeFilter)) {
                continue;
            }
            if (layerFilter != null && !layerFilter.equals(layer)) {
                continue;
            }
            if (nameSearch != null && (element.getName() == null
                    || !element.getName().toLowerCase().contains(nameSearch))) {
                continue;
            }
            if (propertyKey != null) {
                boolean found = false;
                for (IProperty p : element.getProperties()) {
                    if (!propertyKey.equals(p.getKey())) {
                        continue;
                    }
                    String v = p.getValue();
                    if (propertyValue != null) {
                        found = propertyValue.equals(v);
                    } else if (propertyValuePrefix != null) {
                        found = v != null && v.startsWith(propertyValuePrefix);
                    } else {
                        found = true;
                    }
                    if (found) break;
                }
                if (!found) {
                    continue;
                }
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", element.getId());
            entry.put("name", element.getName());
            entry.put("type", type);
            entry.put("layer", layer);
            String doc = element.getDocumentation();
            if (doc != null && !doc.isEmpty()) {
                entry.put("documentation", doc);
            }
            if (includeProperties) {
                List<Map<String, String>> props = new ArrayList<>();
                for (IProperty p : element.getProperties()) {
                    Map<String, String> pEntry = new LinkedHashMap<>();
                    pEntry.put("key", p.getKey());
                    pEntry.put("value", p.getValue());
                    props.add(pEntry);
                }
                entry.put("properties", props);
            }
            results.add(entry);
        }

        return ToolRegistry.MAPPER.writeValueAsString(results);
    }

    private static String detectLayer(IArchimateElement element) {
        if (element instanceof IApplicationElement) return "Application";
        if (element instanceof IBusinessElement) return "Business";
        if (element instanceof ITechnologyElement) return "Technology";
        if (element instanceof IMotivationElement) return "Motivation";
        if (element instanceof IImplementationMigrationElement) return "Implementation";
        return "Other";
    }
}

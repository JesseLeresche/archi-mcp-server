package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Returns elements, relationships, views, and subfolders directly contained in a given folder.
 */
public class ListFolderContentsTool implements ITool {

    @Override
    public String getName() {
        return "list_folder_contents";
    }

    @Override
    public String getDescription() {
        return "Return elements, relationships, views, and direct subfolders contained in a folder. "
                + "Use get_folder_tree to discover folder IDs.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description", "ID of the folder to inspect");

        ObjectNode includeSubfolders = properties.putObject("include_subfolders");
        includeSubfolders.put("type", "boolean");
        includeSubfolders.put("default", false);
        includeSubfolders.put("description",
                "If true, recursively include contents of all subfolders. "
                        + "Default false (direct children only).");

        ArrayNode required = schema.putArray("required");
        required.add("folder_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String folderId = args.get("folder_id").asText();
        boolean includeSubfolders = args.has("include_subfolders")
                && args.get("include_subfolders").asBoolean(false);

        IFolder folder = ModelAccessor.findFolderById(model, folderId);
        if (folder == null) {
            throw new Exception("Folder not found: " + folderId);
        }

        List<Map<String, String>> elements = new ArrayList<>();
        List<Map<String, String>> relationships = new ArrayList<>();
        List<Map<String, String>> views = new ArrayList<>();

        if (includeSubfolders) {
            for (IArchimateElement e : ModelAccessor.collectFromFolder(folder, IArchimateElement.class)) {
                elements.add(elementEntry(e));
            }
            for (IArchimateRelationship r : ModelAccessor.collectFromFolder(folder, IArchimateRelationship.class)) {
                relationships.add(relationshipEntry(r));
            }
            for (IArchimateDiagramModel v : ModelAccessor.collectFromFolder(folder, IArchimateDiagramModel.class)) {
                views.add(viewEntry(v));
            }
        } else {
            for (var obj : folder.getElements()) {
                if (obj instanceof IArchimateElement e) elements.add(elementEntry(e));
                else if (obj instanceof IArchimateRelationship r) relationships.add(relationshipEntry(r));
                else if (obj instanceof IArchimateDiagramModel v) views.add(viewEntry(v));
            }
        }

        List<Map<String, String>> subfolders = new ArrayList<>();
        for (IFolder sub : folder.getFolders()) {
            Map<String, String> sf = new LinkedHashMap<>();
            sf.put("id", sub.getId());
            sf.put("name", sub.getName());
            subfolders.add(sf);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("elements", elements);
        result.put("relationships", relationships);
        result.put("views", views);
        result.put("subfolders", subfolders);

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    private static Map<String, String> elementEntry(IArchimateElement e) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("type", e.eClass().getName());
        return m;
    }

    private static Map<String, String> relationshipEntry(IArchimateRelationship r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("type", r.eClass().getName());
        m.put("source_id", r.getSource() != null ? r.getSource().getId() : null);
        m.put("target_id", r.getTarget() != null ? r.getTarget().getId() : null);
        return m;
    }

    private static Map<String, String> viewEntry(IArchimateDiagramModel v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("name", v.getName());
        return m;
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Returns the complete folder hierarchy for the open model, or for a specific subtree.
 */
public class GetFolderTreeTool implements ITool {

    @Override
    public String getName() {
        return "get_folder_tree";
    }

    @Override
    public String getDescription() {
        return "Return the complete folder hierarchy for the open model. "
                + "Shows folder IDs, names, and nesting. "
                + "Optionally start from a specific folder_id to get only that subtree.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode folderId = properties.putObject("folder_id");
        folderId.put("type", "string");
        folderId.put("description",
                "Optional: start from this folder ID. If omitted, returns the full tree.");

        ObjectNode section = properties.putObject("section");
        section.put("type", "string");
        section.put("description",
                "Optional: restrict to a top-level section by name "
                        + "(e.g. 'Business', 'Application', 'Relations', 'Views'). "
                        + "Case-insensitive partial match on the root folder name.");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String folderId = args != null && args.has("folder_id") ? args.get("folder_id").asText() : null;
        String section = args != null && args.has("section") ? args.get("section").asText().toLowerCase() : null;

        if (folderId != null) {
            IFolder folder = ModelAccessor.findFolderById(model, folderId);
            if (folder == null) {
                throw new Exception("Folder not found: " + folderId);
            }
            return ToolRegistry.MAPPER.writeValueAsString(folderToMap(folder));
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (IFolder root : model.getFolders()) {
            if (section != null && !root.getName().toLowerCase().contains(section)) {
                continue;
            }
            roots.add(folderToMap(root));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("folders", roots);
        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    private static Map<String, Object> folderToMap(IFolder folder) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", folder.getId());
        map.put("name", folder.getName());
        map.put("element_count", folder.getElements().size());

        List<Map<String, Object>> subfolders = new ArrayList<>();
        for (IFolder sub : folder.getFolders()) {
            subfolders.add(folderToMap(sub));
        }
        map.put("folders", subfolders);
        return map;
    }
}

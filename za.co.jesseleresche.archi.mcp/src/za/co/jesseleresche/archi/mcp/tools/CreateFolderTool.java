package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Creates a folder in the Views tree, optionally nested under a parent folder.
 * Use this to organise diagrams hierarchically, e.g. Business Area > Business Domain > SD Overview.
 */
public class CreateFolderTool implements ITool {

    @Override
    public String getName() {
        return "create_folder";
    }

    @Override
    public String getDescription() {
        return "Create a folder in any top-level section (Views, Relations, Business, Application, ...). "
                + "Supply parent_folder_id to nest under an existing folder, or 'section' to create "
                + "directly under a top-level section. One of the two is required.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "Folder name");

        ObjectNode parentFolderId = properties.putObject("parent_folder_id");
        parentFolderId.put("type", "string");
        parentFolderId.put("description",
                "ID of the parent folder. Required unless 'section' is given.");

        ObjectNode section = properties.putObject("section");
        section.put("type", "string");
        section.put("description",
                "Top-level section to create the folder under when parent_folder_id is omitted, "
                        + "e.g. \"Relations\", \"Application\", \"Views\" (case-insensitive). "
                        + "If both are given, the parent must be inside this section.");

        ArrayNode required = schema.putArray("required");
        required.add("name");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String name = args.get("name").asText();
        String parentFolderId = args.hasNonNull("parent_folder_id") ? args.get("parent_folder_id").asText() : null;
        String sectionName = args.hasNonNull("section") ? args.get("section").asText() : null;

        // Resolve parent folder
        IFolder parentFolder;
        if (parentFolderId != null) {
            parentFolder = ModelAccessor.findFolderById(model, parentFolderId);
            if (parentFolder == null) {
                throw new Exception("Parent folder not found: " + parentFolderId);
            }
        } else if (sectionName != null) {
            parentFolder = null;
            for (IFolder f : model.getFolders()) {
                if (f.getName().equalsIgnoreCase(sectionName.trim())) {
                    parentFolder = f;
                    break;
                }
            }
            if (parentFolder == null) {
                throw new Exception("Unknown section '" + sectionName + "'. Valid sections: "
                        + sectionNames(model));
            }
        } else {
            throw new Exception("Specify parent_folder_id or section — a bare create is ambiguous. "
                    + "Valid sections: " + sectionNames(model));
        }
        if (sectionName != null && parentFolderId != null) {
            IFolder section = ModelAccessor.getSectionFolder(parentFolder);
            if (!section.getName().equalsIgnoreCase(sectionName.trim())) {
                throw new Exception("parent_folder_id " + parentFolderId + " is under section '"
                        + section.getName() + "', not '" + sectionName + "'");
            }
        }

        IFolder resolvedParent = parentFolder;

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            IFolder folder = IArchimateFactory.eINSTANCE.createFolder();
            folder.setName(name);
            resolvedParent.getFolders().add(folder);
            ModelAccessor.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", folder.getId());
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }

    private static String sectionNames(IArchimateModel model) {
        return model.getFolders().stream().map(IFolder::getName)
                .collect(java.util.stream.Collectors.joining(", "));
    }
}

package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;

import com.archimatetool.editor.model.IEditorModelManager;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import za.co.jesseleresche.archi.mcp.util.UiThreadUtil;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Updates the name and/or documentation of an existing diagram view.
 */
public class UpdateViewTool implements ITool {

    @Override
    public String getName() {
        return "update_view";
    }

    @Override
    public String getDescription() {
        return "Update an existing view's name and/or documentation.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode viewId = properties.putObject("view_id");
        viewId.put("type", "string");
        viewId.put("description", "ID of the view to update");

        ObjectNode name = properties.putObject("name");
        name.put("type", "string");
        name.put("description", "New view name");

        ObjectNode documentation = properties.putObject("documentation");
        documentation.put("type", "string");
        documentation.put("description", "New documentation text");

        ArrayNode required = schema.putArray("required");
        required.add("view_id");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        String viewId = args.get("view_id").asText();
        String newName = args.has("name") ? args.get("name").asText() : null;
        String newDoc = args.has("documentation")
                ? args.get("documentation").asText() : null;

        IArchimateDiagramModel view = ModelAccessor.findViewById(model, viewId);
        if (view == null) {
            throw new Exception("View not found: " + viewId);
        }

        Map<String, Object> result = UiThreadUtil.syncExec(() -> {
            if (newName != null) {
                view.setName(newName);
            }
            if (newDoc != null) {
                view.setDocumentation(newDoc);
            }

            IEditorModelManager.INSTANCE.saveModel(model);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("view_id", view.getId());
            return entry;
        });

        return ToolRegistry.MAPPER.writeValueAsString(result);
    }
}

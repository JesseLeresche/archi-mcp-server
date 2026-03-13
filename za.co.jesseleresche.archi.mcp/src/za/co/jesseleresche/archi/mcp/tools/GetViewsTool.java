package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import za.co.jesseleresche.archi.mcp.util.ModelAccessor;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Lists all ArchiMate diagram views in the open model.
 */
public class GetViewsTool implements ITool {

    @Override
    public String getName() {
        return "get_views";
    }

    @Override
    public String getDescription() {
        return "List all ArchiMate diagram views in the open model.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        ObjectNode includeElements = properties.putObject("include_elements");
        includeElements.put("type", "boolean");
        includeElements.put("description", "Include element IDs on each view");

        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        IArchimateModel model = ModelAccessor.getOpenModel();
        if (model == null) {
            throw new Exception("No model is currently open in Archi");
        }

        boolean includeElements = args != null && args.has("include_elements")
                && args.get("include_elements").asBoolean(false);

        List<Map<String, Object>> results = new ArrayList<>();

        for (IFolder folder : model.getFolders()) {
            collectViews(folder, includeElements, results);
        }

        return ToolRegistry.MAPPER.writeValueAsString(results);
    }

    private void collectViews(IFolder folder, boolean includeElements,
            List<Map<String, Object>> results) {
        for (var element : folder.getElements()) {
            if (element instanceof IArchimateDiagramModel view) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", view.getId());
                entry.put("name", view.getName());
                entry.put("documentation", view.getDocumentation());

                int elementCount = 0;
                List<String> elementIds = includeElements ? new ArrayList<>() : null;

                for (var child : view.getChildren()) {
                    if (child instanceof IDiagramModelArchimateObject dmo) {
                        elementCount++;
                        if (includeElements) {
                            elementIds.add(dmo.getArchimateElement().getId());
                        }
                    }
                }

                entry.put("element_count", elementCount);
                if (includeElements) {
                    entry.put("element_ids", elementIds);
                }

                results.add(entry);
            }
        }

        for (IFolder sub : folder.getFolders()) {
            collectViews(sub, includeElements, results);
        }
    }
}

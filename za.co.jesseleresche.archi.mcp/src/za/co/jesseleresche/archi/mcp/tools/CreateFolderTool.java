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
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public String getDescription() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public ObjectNode getInputSchema() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    private static String sectionNames(IArchimateModel model) {
        throw new UnsupportedOperationException("stub");
    }
}

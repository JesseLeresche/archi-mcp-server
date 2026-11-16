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

    private static Map<String, String> elementEntry(IArchimateElement e) {
        throw new UnsupportedOperationException("stub");
    }

    private static Map<String, String> relationshipEntry(IArchimateRelationship r) {
        throw new UnsupportedOperationException("stub");
    }

    private static Map<String, String> viewEntry(IArchimateDiagramModel v) {
        throw new UnsupportedOperationException("stub");
    }
}

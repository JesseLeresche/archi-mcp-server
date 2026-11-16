package za.co.jesseleresche.archi.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EClass;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.util.ArchimateModelUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import za.co.jesseleresche.archi.mcp.util.ModelAccessor;

/**
 * Looks up which ArchiMate relationship types are valid between a source and
 * target element, without attempting to create one. Used to answer "what
 * relationship type should I use here?" up front instead of guessing from
 * rejected create attempts.
 */
public class GetValidRelationshipTypesTool implements ITool {

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
}

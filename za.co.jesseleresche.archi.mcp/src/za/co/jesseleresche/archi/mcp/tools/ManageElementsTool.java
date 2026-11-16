package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated element CRUD tool. Replaces create_element, update_element,
 * delete_element and their bulk variants.
 */
public class ManageElementsTool extends ConsolidatedTool {

    private final BulkCreateElementsTool create = new BulkCreateElementsTool();

    private final BulkUpdateElementsTool update = new BulkUpdateElementsTool();

    private final DeleteElementTool delete = new DeleteElementTool();

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

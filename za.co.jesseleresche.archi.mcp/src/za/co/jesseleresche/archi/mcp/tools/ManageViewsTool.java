package za.co.jesseleresche.archi.mcp.tools;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated view management tool. Replaces create_view, update_view,
 * delete_view, duplicate_view and bulk_create_views.
 */
public class ManageViewsTool extends ConsolidatedTool {

    private final BulkCreateViewsTool create = new BulkCreateViewsTool();

    private final UpdateViewTool update = new UpdateViewTool();

    private final DeleteViewTool delete = new DeleteViewTool();

    private final DuplicateViewTool duplicate = new DuplicateViewTool();

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

package za.co.jesseleresche.archi.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Merges duplicate elements into a surviving element (manage_elements merge).
 * Per-operation class: owns the diff, the merge command, syncExec and the save.
 */
public class MergeElementsTool implements ITool {

    @Override
    public String getName() {
        return "merge_elements";
    }

    @Override
    public String getDescription() {
        return "Merge duplicate elements into a surviving element.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = ToolRegistry.MAPPER.createObjectNode();
        schema.put("type", "object");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }
}

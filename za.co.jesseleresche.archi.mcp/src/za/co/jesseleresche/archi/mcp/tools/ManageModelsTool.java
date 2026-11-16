package za.co.jesseleresche.archi.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Consolidated model-session tool. Replaces list_models and select_model.
 */
public class ManageModelsTool extends ConsolidatedTool {

    private final ListModelsTool list = new ListModelsTool();
    private final SelectModelTool select = new SelectModelTool();

    @Override
    public String getName() {
        return "manage_models";
    }

    @Override
    public String getDescription() {
        return "List the open models in Archi or select which one subsequent tools operate on. "
                + "Set 'operation'. Fields by operation — list: (none); select: {name_or_id}.";
    }

    @Override
    public ObjectNode getInputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, new String[] {"list", "select"});
        addStringProp(props, "name_or_id", "For select: the name or ID of the model to select.");
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        return schema;
    }

    @Override
    public String execute(JsonNode args) throws Exception {
        String operation = requireOperation(args);
        switch (operation) {
            case "list":
                return list.execute(args);
            case "select":
                return select.execute(args);
            default:
                throw new Exception("Unknown operation '" + operation
                        + "'. Valid operations: list, select");
        }
    }
}

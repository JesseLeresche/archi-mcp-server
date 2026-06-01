package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Base class for the consolidated MCP tools (manage_elements, manage_views, ...).
 *
 * <p>Each consolidated tool exposes an {@code operation} discriminator plus an
 * {@code items} payload (a single object or an array for batch). The actual model
 * logic is NOT reimplemented here — every operation delegates to one of the existing
 * per-operation tool classes, which already run their mutations inside
 * {@code UiThreadUtil.syncExec(...)} and save the model. Consolidated tools therefore
 * stay pure routing + argument-reshaping and must never wrap delegate calls in their
 * own {@code syncExec} (that would nest).
 */
abstract class ConsolidatedTool implements ITool {

    protected static final ObjectMapper MAPPER = ToolRegistry.MAPPER;

    /** Read the required {@code operation} discriminator. */
    protected String requireOperation(JsonNode args) throws Exception {
        if (args == null || !args.hasNonNull("operation")) {
            throw new Exception("'operation' is required");
        }
        return args.get("operation").asText();
    }

    /**
     * Normalise the {@code items} payload to a list. Accepts either a single object
     * (treated as a one-element batch) or a non-empty array.
     */
    protected List<JsonNode> normalizeItems(JsonNode args) throws Exception {
        JsonNode items = args == null ? null : args.get("items");
        if (items == null || items.isNull()) {
            throw new Exception("'items' is required (a single object or an array)");
        }
        List<JsonNode> list = new ArrayList<>();
        if (items.isArray()) {
            if (items.isEmpty()) {
                throw new Exception("'items' must not be an empty array");
            }
            items.forEach(list::add);
        } else {
            list.add(items);
        }
        return list;
    }

    /**
     * Delegate to a bulk tool by wrapping the items under its expected array key
     * (e.g. {@code {"elements": [...]}}). The bulk tool returns {@code {results:[...]}}.
     */
    protected String delegateBulk(ITool bulkTool, String wrapperKey, List<JsonNode> items) throws Exception {
        ObjectNode wrapper = MAPPER.createObjectNode();
        ArrayNode arr = wrapper.putArray(wrapperKey);
        items.forEach(arr::add);
        return bulkTool.execute(wrapper);
    }

    /**
     * Delegate to a single-item tool once per item, collecting each result (or a
     * per-item error) into a uniform {@code {results:[...]}} response.
     */
    protected String delegatePerItem(ITool singleTool, List<JsonNode> items) throws Exception {
        ArrayNode results = MAPPER.createArrayNode();
        for (JsonNode item : items) {
            try {
                results.add(MAPPER.readTree(singleTool.execute(item)));
            } catch (Exception e) {
                results.add(MAPPER.createObjectNode().put("error", e.getMessage()));
            }
        }
        ObjectNode response = MAPPER.createObjectNode();
        response.set("results", results);
        return MAPPER.writeValueAsString(response);
    }

    /** Coerce a bare string item into {@code {key: value}}; pass objects through unchanged. */
    protected JsonNode asObject(JsonNode item, String stringKey) {
        if (item.isTextual()) {
            return MAPPER.createObjectNode().put(stringKey, item.asText());
        }
        return item;
    }

    /** Return a copy of the item object with an extra string field set. */
    protected JsonNode withField(JsonNode item, String key, String value) {
        ObjectNode out = item.isObject() ? ((ObjectNode) item).deepCopy() : MAPPER.createObjectNode();
        out.put(key, value);
        return out;
    }

    /** Build the standard {@code operation} + {@code items} schema for write tools. */
    protected ObjectNode operationItemsSchema(String[] operations, String itemsDescription) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        addOperation(props, operations);
        addItems(props, itemsDescription);
        ArrayNode required = schema.putArray("required");
        required.add("operation");
        required.add("items");
        return schema;
    }

    /** Add an {@code operation} string property constrained to the given enum values. */
    protected void addOperation(ObjectNode props, String[] operations) {
        ObjectNode op = props.putObject("operation");
        op.put("type", "string");
        op.put("description", "The action to perform.");
        ArrayNode en = op.putArray("enum");
        for (String o : operations) {
            en.add(o);
        }
    }

    /** Add an {@code items} property that accepts a single object or an array of objects. */
    protected void addItems(ObjectNode props, String description) {
        ObjectNode items = props.putObject("items");
        items.put("description", description);
        ArrayNode oneOf = items.putArray("oneOf");
        oneOf.addObject().put("type", "object");
        ObjectNode arr = oneOf.addObject();
        arr.put("type", "array");
        arr.putObject("items").put("type", "object");
    }

    protected void addStringProp(ObjectNode props, String name, String description) {
        props.putObject(name).put("type", "string").put("description", description);
    }
}

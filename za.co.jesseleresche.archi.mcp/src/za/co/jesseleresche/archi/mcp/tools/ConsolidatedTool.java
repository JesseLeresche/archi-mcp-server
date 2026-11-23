package za.co.jesseleresche.archi.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.util.ExchangeDir;
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

    /** The tool's own routing; {@link #execute} wraps it with the shared {@code results} option. */
    protected abstract String executeOperation(JsonNode args) throws Exception;

    /**
     * Runs the operation, then applies {@code results}: "full" (default) returns every per-item
     * entry; "summary" returns {@code {count, succeeded, reused, skipped, failed, errors: [{index, error}]}}
     * (an entry with {@code reused} or {@code skipped} true is not counted as succeeded)
     * so large batches don't flood the client.
     */
    @Override
    public final String execute(JsonNode args) throws Exception {
        String mode = args == null ? "full" : args.path("results").asText("full");
        if (!mode.equals("full") && !mode.equals("summary")) {
            throw new Exception("'results' must be 'full' or 'summary'");
        }
        String out = executeOperation(args);
        if (mode.equals("full")) {
            return out;
        }
        JsonNode results = MAPPER.readTree(out).get("results");
        if (results == null || !results.isArray()) {
            return out; // not a batch response (e.g. a read-only operation)
        }
        ObjectNode summary = MAPPER.createObjectNode();
        ArrayNode errors = MAPPER.createArrayNode();
        int reused = 0;
        int skipped = 0;
        for (int i = 0; i < results.size(); i++) {
            JsonNode r = results.get(i);
            if (r.has("error")) {
                errors.addObject().put("index", i).put("error", r.get("error").asText());
            } else if (r.path("reused").asBoolean()) {
                reused++;
            } else if (r.path("skipped").asBoolean()) {
                skipped++;
            }
        }
        summary.put("count", results.size());
        summary.put("succeeded", results.size() - errors.size() - reused - skipped);
        summary.put("reused", reused);
        summary.put("skipped", skipped);
        summary.put("failed", errors.size());
        summary.set("errors", errors);
        return MAPPER.writeValueAsString(summary);
    }

    /**
     * Error for an unsupported operation. Operations renamed in v3.0.0 name their replacement
     * so a client still using the old name can correct itself.
     */
    protected Exception unknownOperation(String operation, Map<String, String> renamed, String... valid) {
        String replacement = renamed.get(operation);
        if (replacement != null) {
            return new Exception("Operation '" + operation + "' was replaced by '" + replacement
                    + "' in v3.0.0. Valid operations: " + String.join(", ", valid));
        }
        return new Exception("Unknown operation '" + operation + "'. Valid operations: "
                + String.join(", ", valid));
    }

    /** Read the required {@code operation} discriminator. */
    protected String requireOperation(JsonNode args) throws Exception {
        if (args == null || !args.hasNonNull("operation")) {
            throw new Exception("'operation' is required");
        }
        return args.get("operation").asText();
    }

    /**
     * Normalise the {@code items} payload to a list. Accepts either a single object
     * (treated as a one-element batch) or a non-empty array, given inline as {@code items}
     * or as {@code items_file}, a .json file in the exchange folder (see {@link ExchangeDir}).
     */
    protected List<JsonNode> normalizeItems(JsonNode args) throws Exception {
        JsonNode items = args == null ? null : args.get("items");
        if (args != null && args.hasNonNull("items_file")) {
            if (items != null && !items.isNull()) {
                throw new Exception("Pass either 'items' or 'items_file', not both");
            }
            items = ExchangeDir.read(args.get("items_file").asText());
        }
        if (items == null || items.isNull()) {
            throw new Exception("'items' is required (a single object or an array)");
        }
        // Some MCP clients encode the structured 'items' payload as a JSON string
        // (a TextNode) rather than a JSON object/array. Unwrap it so per-item field
        // lookups work for both encodings.
        if (items.isTextual()) {
            String raw = items.asText().trim();
            if (raw.isEmpty()) {
                throw new Exception("'items' is required (a single object or an array)");
            }
            try {
                items = MAPPER.readTree(raw);
            } catch (Exception e) {
                throw new Exception("'items' is a string but not valid JSON: " + e.getMessage());
            }
            if (items == null || items.isNull()) {
                throw new Exception("'items' is required (a single object or an array)");
            }
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

    /**
     * Read a required text field from an item, throwing a clear message (rather than a
     * blind {@code NullPointerException} from {@code get(field).asText()}) when it is
     * absent or null. Shared by the bulk delegate tools.
     */
    static String requireText(JsonNode item, String field) throws Exception {
        JsonNode n = item == null ? null : item.get(field);
        if (n == null || n.isNull()) {
            throw new Exception("missing required field '" + field + "'");
        }
        return n.asText();
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

    /**
     * Return a copy of the item object with a string field set only if it isn't already
     * present — lets a top-level default (e.g. view_id) be supplied once while still allowing
     * individual items to override it.
     */
    protected JsonNode withDefaultField(JsonNode item, String key, String value) {
        if (item.isObject() && item.hasNonNull(key)) {
            return item;
        }
        return withField(item, key, value);
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

    /**
     * Add {@code items} (a single object or an array of objects), plus the shared batch options
     * {@code items_file} and {@code results}.
     */
    protected void addItems(ObjectNode props, String description) {
        props.putObject("items_file").put("type", "string").put("description",
                "Instead of items: a bare .json file name in the exchange folder "
                        + "(~/.archi-mcp/exchange) holding the items array. Paths are rejected.");
        props.putObject("results").put("type", "string").put("description",
                "\"full\" (default) returns an entry per item; \"summary\" returns "
                        + "{count, succeeded, reused, skipped, failed, errors: [{index, error}]}; reused and "
                        + "skipped items are not counted as succeeded.")
                .putArray("enum").add("full").add("summary");
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

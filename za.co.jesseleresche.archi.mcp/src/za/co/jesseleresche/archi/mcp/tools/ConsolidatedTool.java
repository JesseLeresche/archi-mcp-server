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

    /**
     * Read the required {@code operation} discriminator.
     */
    protected String requireOperation(JsonNode args) throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Normalise the {@code items} payload to a list. Accepts either a single object
     * (treated as a one-element batch) or a non-empty array.
     */
    protected List<JsonNode> normalizeItems(JsonNode args) throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Delegate to a bulk tool by wrapping the items under its expected array key
     * (e.g. {@code {"elements": [...]}}). The bulk tool returns {@code {results:[...]}}.
     */
    protected String delegateBulk(ITool bulkTool, String wrapperKey, List<JsonNode> items) throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Delegate to a single-item tool once per item, collecting each result (or a
     * per-item error) into a uniform {@code {results:[...]}} response.
     */
    protected String delegatePerItem(ITool singleTool, List<JsonNode> items) throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Read a required text field from an item, throwing a clear message (rather than a
     * blind {@code NullPointerException} from {@code get(field).asText()}) when it is
     * absent or null. Shared by the bulk delegate tools.
     */
    static String requireText(JsonNode item, String field) throws Exception {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Coerce a bare string item into {@code {key: value}}; pass objects through unchanged.
     */
    protected JsonNode asObject(JsonNode item, String stringKey) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Return a copy of the item object with an extra string field set.
     */
    protected JsonNode withField(JsonNode item, String key, String value) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Return a copy of the item object with a string field set only if it isn't already
     * present — lets a top-level default (e.g. view_id) be supplied once while still allowing
     * individual items to override it.
     */
    protected JsonNode withDefaultField(JsonNode item, String key, String value) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Build the standard {@code operation} + {@code items} schema for write tools.
     */
    protected ObjectNode operationItemsSchema(String[] operations, String itemsDescription) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Add an {@code operation} string property constrained to the given enum values.
     */
    protected void addOperation(ObjectNode props, String[] operations) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Add an {@code items} property that accepts a single object or an array of objects.
     */
    protected void addItems(ObjectNode props, String description) {
        throw new UnsupportedOperationException("stub");
    }

    protected void addStringProp(ObjectNode props, String name, String description) {
        throw new UnsupportedOperationException("stub");
    }
}

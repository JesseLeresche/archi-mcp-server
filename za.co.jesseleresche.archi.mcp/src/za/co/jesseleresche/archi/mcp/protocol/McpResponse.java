package za.co.jesseleresche.archi.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Represents an outgoing JSON-RPC 2.0 response.
 * <p>
 * A response contains either a {@code result} or an {@code error}, never both.
 * Null fields are excluded from serialization per the JSON-RPC spec.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {

    private final String jsonrpc = "2.0";
    private final Object id;
    private final JsonNode result;
    private final McpError error;

    private McpResponse(Object id, JsonNode result, McpError error) {
        this.id = id;
        this.result = result;
        this.error = error;
    }

    /**
     * Create a success response with the given result.
     */
    public static McpResponse success(Object id, JsonNode result) {
        return new McpResponse(id, result, null);
    }

    /**
     * Create an error response with the given code and message.
     */
    public static McpResponse error(Object id, int code, String message) {
        return new McpResponse(id, null, new McpError(code, message));
    }

    /**
     * JSON-RPC parse error (-32700).
     */
    public static McpResponse parseError() {
        return error(null, -32700, "Parse error");
    }

    /**
     * JSON-RPC method not found (-32601).
     */
    public static McpResponse methodNotFound(Object id) {
        return error(id, -32601, "Method not found");
    }

    /**
     * JSON-RPC invalid params (-32602).
     */
    public static McpResponse invalidParams(Object id, String message) {
        return error(id, -32602, message);
    }

    /**
     * Application-level error (-32000).
     */
    public static McpResponse appError(Object id, String message) {
        return error(id, -32000, message);
    }

    @JsonProperty("jsonrpc")
    public String getJsonrpc() {
        return jsonrpc;
    }

    @JsonProperty("id")
    public Object getId() {
        return id;
    }

    @JsonProperty("result")
    public JsonNode getResult() {
        return result;
    }

    @JsonProperty("error")
    public McpError getError() {
        return error;
    }

    /**
     * Serialize this response to a JSON string.
     */
    public String toJson() {
        try {
            return ObjectMapperHolder.get().writeValueAsString(this);
        } catch (Exception e) {
            // Fallback — should never happen with a well-formed response
            return "{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }
}

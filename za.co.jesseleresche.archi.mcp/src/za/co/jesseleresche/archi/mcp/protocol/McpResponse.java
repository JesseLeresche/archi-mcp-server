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
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Create a success response with the given result.
     */
    public static McpResponse success(Object id, JsonNode result) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Create an error response with the given code and message.
     */
    public static McpResponse error(Object id, int code, String message) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * JSON-RPC parse error (-32700).
     */
    public static McpResponse parseError() {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * JSON-RPC method not found (-32601).
     */
    public static McpResponse methodNotFound(Object id) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * JSON-RPC invalid params (-32602).
     */
    public static McpResponse invalidParams(Object id, String message) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Application-level error (-32000).
     */
    public static McpResponse appError(Object id, String message) {
        throw new UnsupportedOperationException("stub");
    }

    @JsonProperty("jsonrpc")
    public String getJsonrpc() {
        throw new UnsupportedOperationException("stub");
    }

    @JsonProperty("id")
    public Object getId() {
        throw new UnsupportedOperationException("stub");
    }

    @JsonProperty("result")
    public JsonNode getResult() {
        throw new UnsupportedOperationException("stub");
    }

    @JsonProperty("error")
    public McpError getError() {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Serialize this response to a JSON string.
     */
    public String toJson() {
        throw new UnsupportedOperationException("stub");
    }
}

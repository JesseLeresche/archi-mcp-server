package za.co.jesseleresche.archi.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents an incoming JSON-RPC 2.0 request or notification.
 * <p>
 * Requests have an {@code id} field; notifications do not.
 */
public class McpRequest {

    private static final ObjectMapper MAPPER = ObjectMapperHolder.MAPPER;

    private final String jsonrpc;

    private final Object id;

    private final String method;

    private final JsonNode params;

    @JsonCreator
    public McpRequest(@JsonProperty("jsonrpc") String jsonrpc, @JsonProperty("id") Object id, @JsonProperty("method") String method, @JsonProperty("params") JsonNode params) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Parse a JSON string into an McpRequest.
     * Returns null if parsing fails.
     */
    public static McpRequest parse(String json) {
        throw new UnsupportedOperationException("stub");
    }

    public String getJsonrpc() {
        throw new UnsupportedOperationException("stub");
    }

    public Object getId() {
        throw new UnsupportedOperationException("stub");
    }

    public String getMethod() {
        throw new UnsupportedOperationException("stub");
    }

    public JsonNode getParams() {
        throw new UnsupportedOperationException("stub");
    }
}

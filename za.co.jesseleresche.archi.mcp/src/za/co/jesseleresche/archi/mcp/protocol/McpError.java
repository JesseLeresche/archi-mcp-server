package za.co.jesseleresche.archi.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON-RPC 2.0 error object for MCP responses.
 */
public class McpError {

    private final int code;

    private final String message;

    @JsonCreator
    public McpError(@JsonProperty("code") int code, @JsonProperty("message") String message) {
        throw new UnsupportedOperationException("stub");
    }

    public int getCode() {
        throw new UnsupportedOperationException("stub");
    }

    public String getMessage() {
        throw new UnsupportedOperationException("stub");
    }
}

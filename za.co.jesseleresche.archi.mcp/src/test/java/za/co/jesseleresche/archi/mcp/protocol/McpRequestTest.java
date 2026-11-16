package za.co.jesseleresche.archi.mcp.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class McpRequestTest {

    @Test
    void parsesRequest() {
        McpRequest r = McpRequest.parse(
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"x\"}}");
        assertEquals("2.0", r.getJsonrpc());
        assertEquals(7, r.getId());
        assertEquals("tools/call", r.getMethod());
        assertEquals("x", r.getParams().get("name").asText());
    }

    @Test
    void notificationHasNoIdOrParams() {
        McpRequest r = McpRequest.parse("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        assertEquals("notifications/initialized", r.getMethod());
        assertNull(r.getId());
        assertNull(r.getParams());
    }

    @Test
    void stringIdIsKept() {
        assertEquals("abc", McpRequest.parse("{\"jsonrpc\":\"2.0\",\"id\":\"abc\",\"method\":\"m\"}").getId());
    }

    @Test
    void invalidJsonParsesToNull() {
        assertNull(McpRequest.parse("{not json"));
        // the constructor must be real for the happy path; prove parse is not a stub returning null
        assertNotNull(McpRequest.parse("{\"method\":\"m\"}"));
    }
}

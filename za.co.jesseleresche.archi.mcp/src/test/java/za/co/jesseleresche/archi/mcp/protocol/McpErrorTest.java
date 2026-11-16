package za.co.jesseleresche.archi.mcp.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import za.co.jesseleresche.archi.mcp.testing.TestModels;

class McpErrorTest {

    @Test
    void holdsCodeAndMessage() {
        McpError e = new McpError(-32601, "Method not found");
        assertEquals(-32601, e.getCode());
        assertEquals("Method not found", e.getMessage());
    }

    @Test
    void deserialisesFromJson() throws Exception {
        McpError e = TestModels.JSON.readValue("{\"code\":-32000,\"message\":\"boom\"}", McpError.class);
        assertEquals(-32000, e.getCode());
        assertEquals("boom", e.getMessage());
    }
}

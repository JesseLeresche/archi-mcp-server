package za.co.jesseleresche.archi.mcp.protocol;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class McpResponseTest {

    @Test
    void successSerialisesWithoutError() {
        McpResponse r = McpResponse.success(1, json("{'ok':true}"));
        JsonNode j = json(r.toJson());
        assertEquals("2.0", j.get("jsonrpc").asText());
        assertEquals(1, j.get("id").asInt());
        assertTrue(j.get("result").get("ok").asBoolean());
        assertFalse(j.has("error"));
        assertNull(r.getError());
    }

    @Test
    void errorFactoriesUseJsonRpcCodes() {
        assertEquals(-32700, McpResponse.parseError().getError().getCode());
        assertEquals("Parse error", McpResponse.parseError().getError().getMessage());
        assertEquals(-32601, McpResponse.methodNotFound(2).getError().getCode());
        McpResponse ip = McpResponse.invalidParams(3, "bad");
        assertEquals(-32602, ip.getError().getCode());
        assertEquals("bad", ip.getError().getMessage());
        assertEquals(-32000, McpResponse.appError(4, "app").getError().getCode());
        assertEquals(4, McpResponse.appError(4, "app").getId());
    }

    @Test
    void errorSerialisesWithoutResultAndNullIdOmitted() {
        JsonNode j = json(McpResponse.methodNotFound("x").toJson());
        assertEquals("x", j.get("id").asText());
        assertEquals(-32601, j.get("error").get("code").asInt());
        assertEquals("Method not found", j.get("error").get("message").asText());
        assertFalse(j.has("result"));

        JsonNode pe = json(McpResponse.parseError().toJson());
        assertEquals(-32700, pe.get("error").get("code").asInt());
        assertFalse(pe.has("id"));
    }
}

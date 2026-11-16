package za.co.jesseleresche.archi.mcp.protocol;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import za.co.jesseleresche.archi.mcp.testing.TestModels;
import za.co.jesseleresche.archi.mcp.tools.ToolRegistry;

class McpDispatcherTest {

    @RegisterExtension
    final TestModels models = new TestModels();

    final McpDispatcher dispatcher = new McpDispatcher(new ToolRegistry());

    private JsonNode call(String request) {
        McpResponse r = dispatcher.dispatch(McpRequest.parse(request.replace('\'', '"')));
        return r == null ? null : json(r.toJson());
    }

    @Test
    void initializeAdvertisesProtocolAndCapabilities() {
        JsonNode r = call("{'jsonrpc':'2.0','id':1,'method':'initialize','params':{}}");
        assertEquals(1, r.get("id").asInt());
        JsonNode res = r.get("result");
        assertEquals("2024-11-05", res.get("protocolVersion").asText());
        assertTrue(res.get("capabilities").has("tools"));
        assertFalse(res.get("capabilities").get("resources").get("subscribe").asBoolean(true));
        assertEquals("archi-mcp", res.get("serverInfo").get("name").asText());
    }

    @Test
    void initializedNotificationHasNoResponse() {
        assertNull(dispatcher.dispatch(McpRequest.parse(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}")));
        // and the dispatcher is live (not a no-op): a request does get a response
        assertNotNull(call("{'jsonrpc':'2.0','id':1,'method':'tools/list'}"));
    }

    @Test
    void toolsListReturnsFourteenDescribedTools() {
        JsonNode tools = call("{'jsonrpc':'2.0','id':2,'method':'tools/list'}").get("result").get("tools");
        assertEquals(14, tools.size());
        List<String> names = new ArrayList<>();
        for (JsonNode t : tools) {
            names.add(t.get("name").asText());
            assertFalse(t.get("description").asText().isEmpty());
            assertEquals("object", t.get("inputSchema").get("type").asText());
        }
        assertTrue(names.containsAll(List.of("manage_models", "query_model", "manage_elements",
                "inspect_view", "export_view_as_image", "create_sd_overview_view")), names.toString());
    }

    @Test
    void toolsCallWrapsResultAsTextContent() {
        models.element("BusinessActor", "Alice");
        JsonNode r = call("{'jsonrpc':'2.0','id':3,'method':'tools/call',"
                + "'params':{'name':'query_model','arguments':{'name_search':'ali'}}}");
        JsonNode block = r.get("result").get("content").get(0);
        assertEquals("text", block.get("type").asText());
        JsonNode hits = json(block.get("text").asText());
        assertEquals("Alice", hits.get(0).get("name").asText());
    }

    @Test
    void toolsCallFailureBecomesAppError() {
        models.closeAll();
        JsonNode e = call("{'jsonrpc':'2.0','id':4,'method':'tools/call',"
                + "'params':{'name':'query_model','arguments':{}}}").get("error");
        assertEquals(-32000, e.get("code").asInt());
        assertTrue(e.get("message").asText().contains("No model is currently open"), e.toString());
    }

    @Test
    void toolsCallParamErrors() {
        assertEquals(-32602, call("{'jsonrpc':'2.0','id':5,'method':'tools/call'}")
                .get("error").get("code").asInt());
        JsonNode noName = call("{'jsonrpc':'2.0','id':5,'method':'tools/call','params':{}}").get("error");
        assertEquals(-32602, noName.get("code").asInt());
        assertEquals("Missing tool name", noName.get("message").asText());
        assertEquals(-32601, call("{'jsonrpc':'2.0','id':5,'method':'tools/call','params':{'name':'nope'}}")
                .get("error").get("code").asInt());
    }

    @Test
    void unknownMethodIsMethodNotFound() {
        JsonNode r = call("{'jsonrpc':'2.0','id':6,'method':'bogus'}");
        assertEquals(6, r.get("id").asInt());
        assertEquals(-32601, r.get("error").get("code").asInt());
    }

    @Test
    void resourcesListAndRead() {
        JsonNode list = call("{'jsonrpc':'2.0','id':7,'method':'resources/list'}").get("result").get("resources");
        assertTrue(list.size() > 0);
        String uri = list.get(0).get("uri").asText();
        assertTrue(uri.startsWith("archi://"), uri);

        JsonNode content = call("{'jsonrpc':'2.0','id':8,'method':'resources/read','params':{'uri':'" + uri + "'}}")
                .get("result").get("contents").get(0);
        assertEquals(uri, content.get("uri").asText());
        assertEquals("text/markdown", content.get("mimeType").asText());
        assertFalse(content.get("text").asText().isBlank());

        JsonNode bad = call("{'jsonrpc':'2.0','id':9,'method':'resources/read','params':{'uri':'archi://nope'}}");
        assertEquals(-32602, bad.get("error").get("code").asInt());
        assertTrue(bad.get("error").get("message").asText().contains("archi://nope"));
        assertEquals(-32602, call("{'jsonrpc':'2.0','id':9,'method':'resources/read','params':{}}")
                .get("error").get("code").asInt());
    }
}

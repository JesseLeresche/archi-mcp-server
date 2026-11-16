package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class ConsolidatedToolTest {

    /** Minimal concrete subclass exposing the protected helpers. */
    static class Probe extends ConsolidatedTool {
        public String getName() { return "probe"; }
        public String getDescription() { return "probe"; }
        public ObjectNode getInputSchema() { return operationItemsSchema(new String[] {"a", "b"}, "things"); }
        protected String executeOperation(JsonNode args) { return "{}"; }
    }

    final Probe tool = new Probe();

    private static void assertFails(String expected, org.junit.jupiter.api.function.Executable ex) {
        Exception e = assertThrows(Exception.class, ex);
        assertTrue(e.getMessage() != null && e.getMessage().contains(expected), String.valueOf(e.getMessage()));
    }

    @Test
    void normalizeItemsAcceptsObjectArrayAndStringifiedJson() throws Exception {
        assertEquals(1, tool.normalizeItems(json("{'items':{'a':1}}")).size());
        List<JsonNode> arr = tool.normalizeItems(json("{'items':[{'a':1},{'a':2}]}"));
        assertEquals(2, arr.get(1).get("a").asInt());

        List<JsonNode> s = tool.normalizeItems(json("{'items':'[{\"a\":1},{\"a\":2},{\"a\":3}]'}"));
        assertEquals(3, s.size());
        assertEquals(3, s.get(2).get("a").asInt());
        assertEquals("x", tool.normalizeItems(json("{'items':' {\"n\":\"x\"} '}")).get(0).get("n").asText());
    }

    @Test
    void normalizeItemsRejectsMissingEmptyAndBadJson() {
        assertFails("'items' is required", () -> tool.normalizeItems(json("{}")));
        assertFails("'items' is required", () -> tool.normalizeItems(json("{'items':null}")));
        assertFails("'items' is required", () -> tool.normalizeItems(json("{'items':'  '}")));
        assertFails("'items' is required", () -> tool.normalizeItems(json("{'items':'null'}")));
        assertFails("must not be an empty array", () -> tool.normalizeItems(json("{'items':[]}")));
        assertFails("not valid JSON", () -> tool.normalizeItems(json("{'items':'{oops'}")));
    }

    @Test
    void requireOperationAndRequireText() throws Exception {
        assertEquals("a", tool.requireOperation(json("{'operation':'a'}")));
        assertFails("'operation' is required", () -> tool.requireOperation(json("{}")));
        assertFails("'operation' is required", () -> tool.requireOperation(null));

        assertEquals("v", ConsolidatedTool.requireText(json("{'f':'v'}"), "f"));
        assertFails("missing required field 'f'", () -> ConsolidatedTool.requireText(json("{'f':null}"), "f"));
        assertFails("missing required field 'f'", () -> ConsolidatedTool.requireText(null, "f"));
    }

    @Test
    void fieldHelpers() {
        JsonNode item = json("{'view_id':'mine','x':1}");
        assertSame(item, tool.withDefaultField(item, "view_id", "top"));
        JsonNode filled = tool.withDefaultField(json("{'x':1}"), "view_id", "top");
        assertEquals("top", filled.get("view_id").asText());
        assertEquals(1, filled.get("x").asInt());
        assertEquals("top", tool.withDefaultField(json("{'view_id':null}"), "view_id", "top").get("view_id").asText());

        JsonNode orig = json("{'a':'1'}");
        JsonNode copy = tool.withField(orig, "a", "2");
        assertEquals("2", copy.get("a").asText());
        assertEquals("1", orig.get("a").asText());

        assertEquals("id1", tool.asObject(json("'id1'"), "element_id").get("element_id").asText());
        JsonNode obj = json("{'element_id':'z'}");
        assertSame(obj, tool.asObject(obj, "element_id"));
    }

    @Test
    void delegatePerItemCollectsPerItemErrors() throws Exception {
        ITool echo = new Probe() {
            @Override
            protected String executeOperation(JsonNode args) {
                if (args.has("fail")) throw new IllegalStateException("bad item");
                return "{\"ok\":" + args.get("n").asInt() + "}";
            }
        };
        JsonNode r = json(tool.delegatePerItem(echo, List.of(json("{'n':1}"), json("{'fail':true}"))))
                .get("results");
        assertEquals(1, r.get(0).get("ok").asInt());
        assertEquals("bad item", r.get(1).get("error").asText());

        ITool bulk = new Probe() {
            @Override
            protected String executeOperation(JsonNode args) {
                return "{\"seen\":" + args.get("elements").size() + "}";
            }
        };
        assertEquals(2, json(tool.delegateBulk(bulk, "elements", List.of(json("{}"), json("{}"))))
                .get("seen").asInt());
    }

    @Test
    void operationItemsSchemaShape() {
        ObjectNode s = tool.getInputSchema();
        assertEquals("object", s.get("type").asText());
        assertEquals(2, s.get("properties").get("operation").get("enum").size());
        assertEquals(2, s.get("properties").get("items").get("oneOf").size());
        assertEquals("[\"operation\"]", s.get("required").toString());
        assertTrue(s.get("properties").has("items_file"));
        assertTrue(s.get("properties").has("results"));
    }

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path exchange;

    @Test
    void itemsFileIsReadFromTheExchangeFolderOnly() throws Exception {
        System.setProperty(za.co.jesseleresche.archi.mcp.util.ExchangeDir.PROPERTY, exchange.toString());
        try {
            java.nio.file.Files.writeString(exchange.resolve("batch.json"), "[{\"a\":1},{\"a\":2}]");
            assertEquals(2, tool.normalizeItems(json("{'items_file':'batch.json'}")).size());
            assertFails("either 'items' or 'items_file', not both",
                    () -> tool.normalizeItems(json("{'items':{},'items_file':'batch.json'}")));
            assertFails("bare .json file name", () -> tool.normalizeItems(json("{'items_file':'../batch.json'}")));
            assertFails("bare .json file name", () -> tool.normalizeItems(json("{'items_file':'/tmp/batch.json'}")));
        } finally {
            System.clearProperty(za.co.jesseleresche.archi.mcp.util.ExchangeDir.PROPERTY);
        }
    }

    @Test
    void summaryResultsCollapseToCounts() throws Exception {
        ITool tool3 = new Probe() {
            @Override
            protected String executeOperation(JsonNode args) {
                return "{\"results\":[{\"id\":\"a\"},{\"error\":\"boom\"},{\"id\":\"b\",\"skipped\":true}]}";
            }
        };
        JsonNode full = json(tool3.execute(json("{}")));
        assertEquals(3, full.get("results").size());
        JsonNode sum = json(tool3.execute(json("{'results':'summary'}")));
        assertEquals(3, sum.get("count").asInt());
        assertEquals(2, sum.get("succeeded").asInt());
        assertEquals(1, sum.get("failed").asInt());
        assertEquals(1, sum.get("errors").get(0).get("index").asInt());
        assertEquals("boom", sum.get("errors").get(0).get("error").asText());
        assertFalse(sum.has("results"));
        assertFails("'results' must be 'full' or 'summary'", () -> tool3.execute(json("{'results':'some'}")));
    }
}

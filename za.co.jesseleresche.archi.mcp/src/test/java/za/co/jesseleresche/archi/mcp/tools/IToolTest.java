package za.co.jesseleresche.archi.mcp.tools;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.jesseleresche.archi.mcp.testing.TestModels.json;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class IToolTest {

    final ITool tool = new ITool() {
        public String getName() { return "t"; }
        public String getDescription() { return "t"; }
        public ObjectNode getInputSchema() { return null; }
        public String execute(JsonNode args) throws Exception {
            if (args.has("fail")) throw new Exception("tool failed");
            return "{\"echo\":" + args.get("v") + "}";
        }
    };

    @Test
    void executeWithContentWrapsTextInOneBlock() throws Exception {
        List<ObjectNode> blocks = tool.executeWithContent(json("{'v':5}"));
        assertEquals(1, blocks.size());
        assertEquals("text", blocks.get(0).get("type").asText());
        assertEquals("{\"echo\":5}", blocks.get(0).get("text").asText());
    }

    @Test
    void executeWithContentPropagatesExecuteFailure() {
        Exception e = assertThrows(Exception.class, () -> tool.executeWithContent(json("{'fail':true}")));
        assertEquals("tool failed", e.getMessage());
    }
}

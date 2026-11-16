package za.co.jesseleresche.archi.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared ObjectMapper instance for the MCP protocol layer.
 * <p>
 * Jackson ObjectMapper is thread-safe for read operations once configured,
 * so a single shared instance is both safe and efficient.
 */
public final class ObjectMapperHolder {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ObjectMapperHolder() {
        // utility class
    }

    /**
     * Returns the shared ObjectMapper instance.
     */
    public static ObjectMapper get() {
        return MAPPER;
    }
}

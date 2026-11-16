package za.co.jesseleresche.archi.mcp.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ObjectMapperHolderTest {

    @Test
    void sharedMapperOmitsNullProperties() throws Exception {
        assertSame(ObjectMapperHolder.get(), ObjectMapperHolder.get());
        assertEquals("{\"code\":1}", ObjectMapperHolder.get().writeValueAsString(new Bean()));
    }

    static class Bean {
        public int code = 1;
        public String message = null;
    }
}

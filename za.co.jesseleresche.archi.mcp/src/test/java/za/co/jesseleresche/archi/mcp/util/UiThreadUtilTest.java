package za.co.jesseleresche.archi.mcp.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UiThreadUtilTest {

    final AtomicInteger dispatched = new AtomicInteger();

    @AfterEach
    void restore() {
        UiThreadUtil.setExecutor(null);
    }

    @Test
    void syncExecRunsThroughExecutorAndReturnsResult() {
        UiThreadUtil.setExecutor(r -> {
            dispatched.incrementAndGet();
            r.run();
        });
        assertEquals("ok", UiThreadUtil.syncExec(() -> "ok"));
        assertEquals(1, dispatched.get());
    }

    @Test
    void syncExecWrapsCheckedExceptionKeepingMessageAndCause() {
        UiThreadUtil.setExecutor(Runnable::run);
        IOException boom = new IOException("disk full");
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> UiThreadUtil.syncExec(() -> { throw boom; }));
        assertEquals("disk full", e.getMessage());
        assertSame(boom, e.getCause());
    }

    @Test
    void syncExecVoidRunsAndPropagates() {
        UiThreadUtil.setExecutor(Runnable::run);
        UiThreadUtil.syncExecVoid(dispatched::incrementAndGet);
        assertEquals(1, dispatched.get());

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> UiThreadUtil.syncExecVoid(() -> { throw new Exception("nope"); }));
        assertEquals("nope", e.getMessage());
    }
}

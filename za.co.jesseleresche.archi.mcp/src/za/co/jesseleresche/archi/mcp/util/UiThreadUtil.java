package za.co.jesseleresche.archi.mcp.util;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.swt.widgets.Display;

/**
 * Utility for executing operations on the Eclipse UI thread.
 * <p>
 * All Archi model mutation operations must be performed on the SWT display thread.
 * This class provides safe wrappers that propagate exceptions back to the calling thread.
 */
public class UiThreadUtil {

    /**
     * Runs a task synchronously on the UI thread. Tests swap in {@code Runnable::run}.
     */
    private static final Executor DISPLAY = r -> Display.getDefault().syncExec(r);

    private static volatile Executor executor = DISPLAY;

    /**
     * Replace the UI-thread executor; {@code null} restores the Display path.
     */
    public static void setExecutor(Executor e) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Run a callable on the Eclipse UI thread and return its result.
     * Throws RuntimeException if the callable throws.
     */
    public static <T> T syncExec(Callable<T> callable) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Variant for void operations that may throw checked exceptions.
     */
    public static void syncExecVoid(ThrowingRunnable runnable) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Functional interface for void operations that may throw checked exceptions.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {

        void run() throws Exception;
    }
}

package za.co.jesseleresche.archi.mcp.util;

import java.util.concurrent.Callable;
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
     * Run a callable on the Eclipse UI thread and return its result.
     * Throws RuntimeException if the callable throws.
     */
    public static <T> T syncExec(Callable<T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        Display.getDefault().syncExec(() -> {
            try {
                result.set(callable.call());
            } catch (Exception e) {
                error.set(e);
            }
        });

        if (error.get() != null) {
            throw new RuntimeException(error.get().getMessage(), error.get());
        }
        return result.get();
    }

    /**
     * Variant for void operations that may throw checked exceptions.
     */
    public static void syncExecVoid(ThrowingRunnable runnable) {
        syncExec(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Functional interface for void operations that may throw checked exceptions.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}

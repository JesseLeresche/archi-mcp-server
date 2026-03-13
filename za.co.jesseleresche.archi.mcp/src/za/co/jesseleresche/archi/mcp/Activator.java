package za.co.jesseleresche.archi.mcp;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import za.co.jesseleresche.archi.mcp.server.McpServerManager;

/**
 * OSGi bundle activator for the Archi MCP plugin.
 * <p>
 * Implements {@link IStartup} so that the plugin is activated early via the
 * {@code org.eclipse.ui.startup} extension point. The MCP server is started
 * during bundle activation and stopped on shutdown.
 */
public class Activator extends AbstractUIPlugin implements IStartup {

    public static final String PLUGIN_ID = "com.archimatetool.mcp";

    private static Activator plugin;

    private McpServerManager serverManager;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;

        serverManager = new McpServerManager();
        serverManager.start();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (serverManager != null) {
            serverManager.stop();
        }
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared plugin instance.
     */
    public static Activator getDefault() {
        return plugin;
    }

    @Override
    public void earlyStartup() {
        // Required by IStartup interface.
        // Actual startup logic is in start(BundleContext).
    }
}

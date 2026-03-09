package com.archimatetool.mcp.server;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;

import com.archimatetool.mcp.Activator;

/**
 * Manages the lifecycle of the MCP server.
 * <p>
 * Reads the port from the system property {@code archi.mcp.port} (default 7432),
 * creates the {@link McpServer}, and handles start/stop with logging.
 */
public class McpServerManager {

    static final int DEFAULT_PORT = 7432;

    private McpServer server;

    /**
     * Start the MCP server on the configured port.
     */
    public void start() {
        int port = Integer.getInteger("archi.mcp.port", DEFAULT_PORT);
        ILog log = Platform.getLog(Activator.getDefault().getBundle());

        try {
            server = new McpServer(port);
            server.start();
            log.info("Archi MCP server started on port " + port);
        } catch (Exception e) {
            log.error("Failed to start Archi MCP server on port " + port, e);
        }
    }

    /**
     * Stop the MCP server if it is running.
     */
    public void stop() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                try {
                    ILog log = Platform.getLog(Activator.getDefault().getBundle());
                    log.error("Error stopping Archi MCP server", e);
                } catch (Exception ignored) {
                    // Activator may already be null during shutdown
                }
            }
        }
    }
}

package com.archimatetool.mcp.server;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import jakarta.servlet.AsyncContext;

import com.archimatetool.mcp.protocol.McpDispatcher;
import com.archimatetool.mcp.tools.ToolRegistry;
import com.archimatetool.mcp.transport.HealthHandler;
import com.archimatetool.mcp.transport.OpenApiHandler;
import com.archimatetool.mcp.transport.SseMessageHandler;
import com.archimatetool.mcp.transport.SseTransportHandler;
import com.archimatetool.mcp.transport.StreamableTransportHandler;

/**
 * Central wiring class for the Archi MCP embedded HTTP server.
 * <p>
 * Creates all components (tool registry, dispatcher, session map, Jetty server)
 * and registers the servlet handlers for SSE, Streamable, health, and OpenAPI endpoints.
 * The server binds to localhost only (127.0.0.1) for security.
 */
public class McpServer {

    private final Server server;

    public McpServer(int port) {
        ToolRegistry toolRegistry = new ToolRegistry();
        McpDispatcher dispatcher = new McpDispatcher(toolRegistry);
        ConcurrentHashMap<String, AsyncContext> sseSessions = new ConcurrentHashMap<>();

        server = new Server(new InetSocketAddress("127.0.0.1", port));

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        context.addServlet(new ServletHolder(new SseTransportHandler(sseSessions)), "/sse");
        context.addServlet(new ServletHolder(new SseMessageHandler(sseSessions, dispatcher)), "/message");
        context.addServlet(new ServletHolder(new StreamableTransportHandler(dispatcher)), "/mcp");
        context.addServlet(new ServletHolder(new HealthHandler(port)), "/health");
        context.addServlet(new ServletHolder(new OpenApiHandler()), "/openapi.yaml");
    }

    /**
     * Start the embedded Jetty server.
     */
    public void start() throws Exception {
        server.start();
    }

    /**
     * Stop the embedded Jetty server.
     */
    public void stop() throws Exception {
        server.stop();
    }
}

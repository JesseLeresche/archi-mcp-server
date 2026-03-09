package com.archimatetool.mcp.transport;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.archimatetool.mcp.protocol.McpDispatcher;
import com.archimatetool.mcp.protocol.McpRequest;
import com.archimatetool.mcp.protocol.McpResponse;
import com.archimatetool.mcp.protocol.ObjectMapperHolder;

/**
 * SSE message endpoint ({@code POST /message?sessionId=...}).
 * <p>
 * Receives JSON-RPC messages from the client, returns 202 immediately,
 * and sends the response back via the SSE stream associated with the session.
 * <p>
 * Dispatch is performed in {@link CompletableFuture#runAsync} to avoid
 * deadlocks: tool calls may invoke {@code Display.syncExec()} which would
 * block if executed on a Jetty thread.
 */
public class SseMessageHandler extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ConcurrentHashMap<String, AsyncContext> sseSessions;
    private final McpDispatcher dispatcher;

    public SseMessageHandler(ConcurrentHashMap<String, AsyncContext> sseSessions,
                             McpDispatcher dispatcher) {
        this.sseSessions = sseSessions;
        this.dispatcher = dispatcher;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        String sessionId = request.getParameter("sessionId");
        AsyncContext async = sseSessions.get(sessionId);

        if (async == null) {
            response.sendError(404, "Session not found");
            return;
        }

        // Read body before acknowledging
        String body = new String(request.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        // Acknowledge receipt immediately
        response.setStatus(202);
        response.getWriter().write("accepted");
        response.getWriter().flush();

        // Dispatch in a separate thread so we don't block the HTTP thread
        // (tools/call may block on Display.syncExec)
        CompletableFuture.runAsync(() -> {
            McpRequest mcpRequest = McpRequest.parse(body);
            if (mcpRequest == null) {
                sendSseMessage(async, McpResponse.parseError());
                return;
            }

            // Notifications have no id and require no response
            if (mcpRequest.getId() == null) {
                dispatcher.dispatch(mcpRequest); // side-effect only
                return;
            }

            McpResponse mcpResponse = dispatcher.dispatch(mcpRequest);
            if (mcpResponse != null) {
                sendSseMessage(async, mcpResponse);
            }
        });
    }

    /**
     * Send an SSE message through the async context's response writer.
     * Synchronized on the writer to prevent interleaving of concurrent messages.
     */
    private void sendSseMessage(AsyncContext async, McpResponse mcpResponse) {
        try {
            PrintWriter writer = async.getResponse().getWriter();
            String json = ObjectMapperHolder.get().writeValueAsString(mcpResponse);
            synchronized (writer) {
                writer.write("event: message\ndata: " + json + "\n\n");
                writer.flush();
            }
        } catch (IOException e) {
            // Client disconnected
        }
    }
}

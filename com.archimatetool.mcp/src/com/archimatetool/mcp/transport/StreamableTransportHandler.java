package com.archimatetool.mcp.transport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.archimatetool.mcp.protocol.McpDispatcher;
import com.archimatetool.mcp.protocol.McpRequest;
import com.archimatetool.mcp.protocol.McpResponse;
import com.archimatetool.mcp.protocol.ObjectMapperHolder;

/**
 * Streamable HTTP transport endpoint ({@code POST /mcp}).
 * <p>
 * Handles MCP requests synchronously over a single HTTP POST endpoint.
 * This is the newer MCP transport required by Copilot Studio.
 */
public class StreamableTransportHandler extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final McpDispatcher dispatcher;

    public StreamableTransportHandler(McpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setHeader("Access-Control-Allow-Origin", "*");

        String body = new String(request.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        McpRequest mcpRequest = McpRequest.parse(body);

        if (mcpRequest == null) {
            writeJsonResponse(response, 200, McpResponse.parseError());
            return;
        }

        // Notifications: no response body needed
        if (mcpRequest.getId() == null) {
            dispatcher.dispatch(mcpRequest);
            response.setStatus(202);
            return;
        }

        McpResponse mcpResponse = dispatcher.dispatch(mcpRequest);

        // For initialize, include session ID header
        if ("initialize".equals(mcpRequest.getMethod())) {
            response.setHeader("Mcp-Session-Id", UUID.randomUUID().toString());
        }

        writeJsonResponse(response, 200, mcpResponse);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Mcp-Session-Id");
        response.setStatus(204);
    }

    private void writeJsonResponse(HttpServletResponse response, int status, McpResponse body)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        ObjectMapperHolder.get().writeValue(response.getWriter(), body);
    }
}

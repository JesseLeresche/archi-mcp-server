package com.archimatetool.mcp.transport;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.archimatetool.model.IArchimateModel;
import com.archimatetool.mcp.protocol.ObjectMapperHolder;
import com.archimatetool.mcp.util.ModelAccessor;

/**
 * Health check endpoint ({@code GET /health}).
 * <p>
 * Returns a JSON status object indicating whether the plugin is running
 * and whether a model is currently open.
 */
public class HealthHandler extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final int port;

    public HealthHandler(int port) {
        this.port = port;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        IArchimateModel model = ModelAccessor.getOpenModel();

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ok");
        status.put("model_open", model != null);
        status.put("model_name", model != null ? model.getName() : null);
        status.put("transport_sse", "http://localhost:" + port + "/sse");
        status.put("transport_streamable", "http://localhost:" + port + "/mcp");
        status.put("version", "1.0.0");

        ObjectMapperHolder.get().writeValue(response.getWriter(), status);
    }
}

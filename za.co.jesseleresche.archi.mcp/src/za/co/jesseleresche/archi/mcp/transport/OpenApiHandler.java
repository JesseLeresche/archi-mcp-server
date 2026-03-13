package za.co.jesseleresche.archi.mcp.transport;

import java.io.IOException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * OpenAPI descriptor endpoint ({@code GET /openapi.yaml}).
 * <p>
 * Returns a Swagger 2.0 descriptor for the streamable MCP endpoint,
 * used by Copilot Studio to discover and connect to the server.
 */
public class OpenApiHandler extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String OPENAPI_YAML = """
            swagger: '2.0'
            info:
              title: Archi MCP Server
              description: ArchiMate model interaction via Model Context Protocol
              version: 1.0.0
            host: localhost:7432
            basePath: /
            schemes:
              - http
            paths:
              /mcp:
                post:
                  summary: Archi MCP Server
                  x-ms-agentic-protocol: mcp-streamable-1.0
                  operationId: InvokeMCP
                  responses:
                    '200':
                      description: Success
            """;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/yaml");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        response.getWriter().write(OPENAPI_YAML);
    }
}

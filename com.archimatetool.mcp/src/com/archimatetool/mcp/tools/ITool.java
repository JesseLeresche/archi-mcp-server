package com.archimatetool.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Interface for MCP tool implementations.
 * Each tool provides a name, description, input schema, and execution logic.
 */
public interface ITool {
    String getName();
    String getDescription();
    ObjectNode getInputSchema();
    String execute(JsonNode args) throws Exception;
}

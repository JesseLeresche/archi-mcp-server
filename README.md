# Archi MCP Plugin

An Eclipse OSGi plugin for [Archi](https://www.archimatetool.com/) that implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) over HTTP. When installed, it starts an embedded Jetty HTTP server on `localhost:7432` that allows any MCP-compliant client to read and modify ArchiMate models.

## Supported MCP Clients

- **Claude Code** (SSE transport)
- **GitHub Copilot** in VS Code (SSE transport)
- **Copilot Studio** (Streamable HTTP transport)

## Prerequisites

- **Archi 5.3.0+** installed locally
- **JDK 21+** (for building)
- **Maven 3.9+** (for building)

## Building

The build uses Maven Tycho and resolves Archi/Eclipse dependencies from your local Archi installation.

### 1. Configure the Archi path

Edit `target-platform/archi-mcp.target` and update the `<location path="...">` to match your Archi installation:

| OS | Path |
|----|------|
| macOS | `/Applications/Archi.app/Contents/Eclipse` |
| Linux | `/opt/Archi` |
| Windows | `C:\Program Files\Archi` |

### 2. Build the JAR

```bash
# Ensure JAVA_HOME points to JDK 21
JAVA_HOME="$(/usr/libexec/java_home -v 21)" mvn clean verify
```

The plugin JAR is produced at:
```
com.archimatetool.mcp/target/com.archimatetool.mcp-1.0.0-SNAPSHOT.jar
```

The `lib/` directory (Jetty 11 + Jackson JARs) is downloaded automatically by Maven during the build.

## Installation

1. Copy the built JAR into Archi's `dropins` folder:

   ```bash
   # macOS
   cp com.archimatetool.mcp/target/com.archimatetool.mcp-1.0.0-SNAPSHOT.jar \
      /Applications/Archi.app/Contents/Eclipse/dropins/

   # Linux
   cp com.archimatetool.mcp/target/com.archimatetool.mcp-1.0.0-SNAPSHOT.jar \
      /opt/Archi/dropins/

   # Windows (PowerShell)
   Copy-Item com.archimatetool.mcp\target\com.archimatetool.mcp-1.0.0-SNAPSHOT.jar `
      "C:\Program Files\Archi\dropins\"
   ```

2. Restart Archi. The plugin starts automatically and launches the MCP server.

3. Verify by opening a model in Archi, then:
   ```bash
   curl http://localhost:7432/health
   ```

## Connecting MCP Clients

### Claude Code

Add to your MCP config (`~/.claude/claude_code_config.json` or project `.mcp.json`):

```json
{
  "mcpServers": {
    "archi": {
      "type": "sse",
      "url": "http://localhost:7432/sse"
    }
  }
}
```

### GitHub Copilot (VS Code)

Configure your Copilot MCP settings to point to `http://localhost:7432/sse` (SSE transport).

### Copilot Studio / Streamable HTTP

Use `POST http://localhost:7432/mcp` (single-endpoint Streamable HTTP transport).

## Available MCP Tools

| Tool | Description |
|------|-------------|
| `query_model` | List/filter elements by type, layer, or name |
| `get_views` | List diagram views with optional element details |
| `create_element` | Create an ArchiMate element in the model |
| `create_relationship` | Create a relationship between two elements |
| `create_view` | Create an empty diagram view |
| `add_element_to_view` | Place an element as a visual figure on a view |
| `add_relationship_to_view` | Draw a visual connection for an existing relationship |

A model must be open in Archi for the tools to work.

## HTTP Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/sse` | GET | SSE transport — establishes event stream |
| `/message` | POST | SSE transport — sends JSON-RPC messages |
| `/mcp` | POST | Streamable HTTP transport |
| `/health` | GET | Health check and transport URLs |
| `/openapi.yaml` | GET | OpenAPI specification |

## Configuration

- **Default port**: `7432` (override with `-Darchi.mcp.port=<port>` JVM argument in Archi)
- **Bind address**: `127.0.0.1` (localhost only, not configurable for security)
- **MCP protocol version**: `2024-11-05`

## License

See [LICENSE](LICENSE) for details.

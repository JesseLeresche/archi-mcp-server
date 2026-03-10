# Archi MCP Plugin

An Eclipse OSGi plugin for [Archi](https://www.archimatetool.com/) that implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) over HTTP. When installed, it starts an embedded Jetty HTTP server on `localhost:7432` that allows any MCP-compliant client to read and modify ArchiMate models in real time.

## Features

- **Multi-model support** — list all open models and switch between them
- **Query & filter** — search elements by type, layer, or name substring
- **Element analysis** — inspect an element's relationships, view usage, and properties without scanning raw XML
- **Full model authoring** — create elements, relationships, and diagram views
- **Property management** — update element names, documentation, and custom key/value properties
- **Visual layout** — place elements on views and draw relationship connections
- **Appearance control** — change fill color, font color, line color, opacity, and line width on view figures
- **Dual MCP transport** — SSE (for Claude Code / GitHub Copilot) and Streamable HTTP (for Copilot Studio)
- **Zero external dependencies** — single JAR bundles Jetty 11 + Jackson; no sidecar process needed

## Supported MCP Clients

| Client | Transport |
|--------|-----------|
| [Claude Code](https://claude.ai/code) | SSE (`GET /sse` + `POST /message`) |
| GitHub Copilot (VS Code) | SSE |
| Copilot Studio | Streamable HTTP (`POST /mcp`) |

## Prerequisites

- **Archi 5.3.0+** installed locally
- **JDK 21+** (for building only)
- **Maven 3.9+** (for building only)

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

1. Build the plugin JAR (see above).

2. Copy the JAR into Archi's `plugins` folder:

   ```bash
   # macOS
   cp com.archimatetool.mcp/target/com.archimatetool.mcp-1.0.0-SNAPSHOT.jar \
      /Applications/Archi.app/Contents/Eclipse/plugins/

   # Linux
   cp com.archimatetool.mcp/target/com.archimatetool.mcp-1.0.0-SNAPSHOT.jar \
      /opt/Archi/plugins/

   # Windows (PowerShell)
   Copy-Item com.archimatetool.mcp\target\com.archimatetool.mcp-1.0.0-SNAPSHOT.jar `
      "C:\Program Files\Archi\plugins\"
   ```

3. Restart Archi. The plugin starts automatically and launches the MCP server.

4. Verify by opening a model in Archi, then:
   ```bash
   curl http://localhost:7432/health
   ```

## Usage

### Claude Code

Add to your project `.mcp.json` (or `~/.claude/claude_code_config.json` for global):

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

Then use Claude Code normally — it will discover the Archi tools and can query, create, and modify elements in your open model.

### GitHub Copilot (VS Code)

Configure your Copilot MCP settings to point to `http://localhost:7432/sse` (SSE transport).

### Copilot Studio / Streamable HTTP

Use `POST http://localhost:7432/mcp` (single-endpoint Streamable HTTP transport).

## Available MCP Tools

### Model Management

| Tool | Description |
|------|-------------|
| `list_models` | List all open models in Archi with selection status |
| `select_model` | Select which open model to use by name or ID |

### Querying

| Tool | Description |
|------|-------------|
| `query_model` | List/filter elements by ArchiMate type, layer, or name |
| `get_views` | List diagram views with optional element details |

### Authoring

| Tool | Description |
|------|-------------|
| `create_element` | Create an ArchiMate element in the model |
| `create_relationship` | Create a relationship between two elements |
| `create_view` | Create an empty diagram view |

### Visual Layout & Appearance

| Tool | Description |
|------|-------------|
| `add_element_to_view` | Place an element as a visual figure on a view |
| `add_relationship_to_view` | Draw a visual connection for an existing relationship |
| `update_figure_appearance` | Update fill color, font color, line color, opacity, line width, or text alignment of a figure on a view |

### Properties & Analysis

| Tool | Description |
|------|-------------|
| `update_element` | Update an element's name, documentation, or custom key/value properties |
| `get_element_analysis` | Analyze an element's incoming/outgoing relationships, view usage, and custom properties |

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

| Property | Default | Description |
|----------|---------|-------------|
| `archi.mcp.port` | `7432` | HTTP server port (set via `-Darchi.mcp.port=<port>` JVM arg) |
| Bind address | `127.0.0.1` | Localhost only (not configurable, for security) |
| MCP version | `2024-11-05` | Protocol version advertised to clients |

## License

See [LICENSE](LICENSE) for details.

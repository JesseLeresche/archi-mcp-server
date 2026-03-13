# Archi MCP Plugin

> **Status: Early Testing** — This plugin has been tested with Claude Code and GitHub Copilot in VS Code on macOS and Windows. It is functional but may have rough edges. Feedback, bug reports, and feature requests are very welcome — please open a [GitHub Issue](https://github.com/JesseLeresche/archi-mcp-server/issues).

An Eclipse OSGi plugin for [Archi](https://www.archimatetool.com/) that implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) over HTTP. Once installed, it starts an embedded MCP server on `localhost:7432` that lets any MCP-compliant AI assistant read and modify your open ArchiMate models in real time.

**No additional software required.** Unlike many MCP servers that require Node.js, Python, or a separate process to be running, this plugin is a single JAR that installs directly into Archi. Drop it in the `plugins/` folder, restart Archi, and it's ready — nothing else to install or configure.

## Features

- **No extra dependencies** — single JAR bundles everything (Jetty 11 + Jackson); no Node.js, Python, or sidecar process needed
- **Multi-model support** — list all open models and switch between them
- **Query & filter** — search elements by type, layer, or name
- **Full model authoring** — create elements, relationships, views, and folders
- **Visual layout** — place elements on views, draw connections, set positions and sizes
- **Appearance control** — change fill color, font color, line color, opacity, and line width
- **Property management** — update names, documentation, and custom key/value properties
- **Folder management** — create folders and move elements and views between folders
- **View layout** — query figure positions and sizes on any view
- **Connection management** — update or delete visual connections on views
- **Bulk operations** — create, update, or move multiple elements or views in a single call
- **Element analysis** — inspect an element's relationships and view usage
- **Dual MCP transport** — SSE (Claude Code, VS Code Copilot) and Streamable HTTP (Copilot Studio)

## Tested With

| Client | Platform | Status |
|--------|----------|--------|
| [Claude Code](https://claude.ai/code) | macOS, Windows | Tested |
| GitHub Copilot (VS Code) | macOS, Windows | Tested |
| Copilot Studio | — | Untested — feedback welcome |

## Supported Clients

| Client | Transport |
|--------|-----------|
| [Claude Code](https://claude.ai/code) | SSE |
| GitHub Copilot (VS Code) | SSE |
| Copilot Studio | Streamable HTTP |

---

## Installation

### Option A — Use the pre-built release (recommended)

1. Download the latest JAR from the [Releases page](https://github.com/JesseLeresche/archi-mcp-server/releases).

2. Copy it into Archi's `plugins/` directory:

   **macOS**
   ```bash
   cp za.co.jesseleresche.archi.mcp-*.jar /Applications/Archi.app/Contents/Eclipse/plugins/
   ```

   **Linux**
   ```bash
   cp za.co.jesseleresche.archi.mcp-*.jar /opt/Archi/plugins/
   ```

   **Windows** (PowerShell)
   ```powershell
   Copy-Item za.co.jesseleresche.archi.mcp-*.jar "C:\Program Files\Archi\plugins\"
   ```

3. Restart Archi. The MCP server starts automatically.

4. Verify it's running:
   ```bash
   curl http://localhost:7432/health
   ```

### Option B — Build from source

See [Building Locally](#building-locally) below.

---

## Configuration for AI Clients

### Claude Code

Add to your project's `.mcp.json`, or to `~/.claude/claude_code_config.json` for global access:

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

Restart Claude Code (or run `/mcp` to reload servers). Archi tools will appear automatically once a model is open in Archi.

### GitHub Copilot in VS Code

1. Open **Settings** (`Cmd+,` / `Ctrl+,`) and search for `mcp`.
2. Click **Edit in settings.json** under `github.copilot.chat.mcp.servers`.
3. Add the following entry:

```json
{
  "github.copilot.chat.mcp.servers": {
    "archi": {
      "type": "sse",
      "url": "http://localhost:7432/sse"
    }
  }
}
```

4. Reload VS Code. The Archi tools will be available in Copilot Chat when Agent mode is active (`@workspace` or `#tools`).

### Copilot Studio / Streamable HTTP

Use `POST http://localhost:7432/mcp` as the single-endpoint Streamable HTTP connector.

---

## Available Tools

### Model Management

| Tool | Description |
|------|-------------|
| `list_models` | List all open models with selection status |
| `select_model` | Switch active model by name or ID |

### Querying

| Tool | Description |
|------|-------------|
| `query_model` | Filter elements by ArchiMate type, layer, or name |
| `get_views` | List diagram views with optional element details |

### Authoring

| Tool | Description |
|------|-------------|
| `create_element` | Create an ArchiMate element |
| `create_relationship` | Create a relationship between two elements |
| `create_view` | Create an empty diagram view |
| `create_folder` | Create a folder or subfolder |

### Visual Layout & Appearance

| Tool | Description |
|------|-------------|
| `add_element_to_view` | Place an element as a figure on a view |
| `add_relationship_to_view` | Draw a visual connection for a relationship |
| `update_figure_appearance` | Set fill color, font color, line color, opacity, or line width |
| `get_view_layout` | Return position and size (x, y, width, height) of all figures on a view |

### Connections

| Tool | Description |
|------|-------------|
| `update_connection` | Update bendpoints, line color, line width, font color, or text position on a connection |
| `delete_connection` | Remove a visual connection from a view (logical relationship is preserved) |

### Properties & Analysis

| Tool | Description |
|------|-------------|
| `update_element` | Update name, documentation, or custom properties |
| `get_element_analysis` | Inspect relationships, view usage, and properties |

### Folders

| Tool | Description |
|------|-------------|
| `move_element_to_folder` | Move an element to a different folder |
| `move_view_to_folder` | Move a diagram view to a different folder |

### Deletion

| Tool | Description |
|------|-------------|
| `delete_element` | Delete an element and its relationships (cascade) |
| `delete_view` | Delete a diagram view |

### Bulk Operations

| Tool | Description |
|------|-------------|
| `bulk_create_elements` | Create multiple elements in one call |
| `bulk_update_elements` | Update multiple elements in one call |
| `bulk_create_relationships` | Create multiple relationships in one call |
| `bulk_add_elements_to_view` | Place multiple elements on a view |
| `bulk_add_relationships_to_view` | Draw multiple connections on a view |
| `bulk_move_elements_to_folder` | Move multiple elements to folders |
| `bulk_move_views_to_folder` | Move multiple views to folders |

---

## HTTP Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/sse` | GET | SSE transport — establishes event stream |
| `/message` | POST | SSE transport — sends JSON-RPC messages |
| `/mcp` | POST | Streamable HTTP transport |
| `/health` | GET | Health check and server info |
| `/openapi.yaml` | GET | OpenAPI specification |

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `archi.mcp.port` | `7432` | HTTP port. Override with `-Darchi.mcp.port=<port>` JVM arg |
| Bind address | `127.0.0.1` | Loopback only — not configurable |
| MCP version | `2024-11-05` | Protocol version advertised to clients |

To set a custom port, add the JVM argument in Archi's `Archi.ini`:
```
-Darchi.mcp.port=8080
```

---

## Building Locally

### Prerequisites

- [Archi 5.3.0+](https://www.archimatetool.com/download/) installed locally
- JDK 21+ (`JAVA_HOME` must point to it)
- Maven 3.9+

### 1. Configure the Archi path

Edit `target-platform/archi-mcp.target` and update the `<location path="...">` to your Archi installation:

| OS | Default path |
|----|-------------|
| macOS | `/Applications/Archi.app/Contents/Eclipse` |
| Linux | `/opt/Archi` |
| Windows | `C:\Program Files\Archi` |

Or pass it on the command line with `-Darchi.dir=<path>` (requires the profile-based setup described in the target file comments).

### 2. Build

```bash
# macOS / Linux
JAVA_HOME="$(/usr/libexec/java_home -v 21)" mvn clean verify

# Windows
set JAVA_HOME=C:\path\to\jdk-21
mvn clean verify
```

The plugin JAR is produced at:
```
za.co.jesseleresche.archi.mcp/target/za.co.jesseleresche.archi.mcp-1.0.0-SNAPSHOT.jar
```

Jetty and Jackson JARs are downloaded automatically into `lib/` during the build.

### 3. Install

Copy the built JAR to Archi's `plugins/` directory (see [Installation](#installation) above) and restart Archi.

---

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for the full workflow.

In short:
1. Fork the repo and create a feature branch from `main`
2. Make your change and verify the build passes
3. Open a pull request with a clear description of what changed and why

---

## License

[MIT License](LICENSE) — Copyright (c) 2025 Jesse Leresche

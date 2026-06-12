# Archi MCP Plugin

> **Status: Early Testing** — This plugin has been tested with Claude Code and GitHub Copilot in VS Code on macOS and Windows. It is functional but may have rough edges. Feedback, bug reports, and feature requests are very welcome — please open a [GitHub Issue](https://github.com/JesseLeresche/archi-mcp-server/issues).

An Eclipse OSGi plugin for [Archi](https://www.archimatetool.com/) that implements the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) over HTTP. Once installed, it starts an embedded MCP server on `localhost:7432` that lets any MCP-compliant AI assistant read and modify your open ArchiMate models in real time.

**No additional software required.** Unlike many MCP servers that require Node.js, Python, or a separate process to be running, this plugin is a single JAR that installs directly into Archi. Drop it in the `dropins/` folder, restart Archi, and it's ready — nothing else to install or configure.

## Architecture

> *This diagram was created, exported, and added to this README automatically by Claude Code using the Archi MCP Plugin.*

![Archi MCP Plugin — Application Composition](docs/archi-mcp-application-composition.png)

## Features

- **No extra dependencies** — single JAR bundles everything (Jetty 11 + Jackson); no Node.js, Python, or sidecar process needed
- **Multi-model support** — list all open models and switch between them
- **Query & filter** — search elements by type, layer, or name
- **Full model authoring** — create elements, relationships, views, and folders with optional folder placement
- **Type change** — change an element's ArchiMate type while preserving all relationships and view references
- **Visual layout** — place elements on views (including inside groups), draw connections, set positions and sizes
- **Appearance control** — change fill color, font color, line color, opacity, and line width
- **Property management** — update names, documentation, and custom key/value properties
- **Folder browsing** — get folder tree hierarchy, list folder contents (elements, relationships, views)
- **Folder management** — create folders and move elements, relationships, and views between folders
- **View management** — duplicate views with all figures, groups, notes, and connections; remove figures from views
- **View layout** — query figure positions and sizes on any view, including nested and grouped elements
- **Groups & notes** — full support for visual grouping elements and notes on views
- **Connection inspection** — get connection details including bendpoints, colors, and line width
- **Connection management** — update or delete visual connections on views
- **Access relationship support** — set access type (Read, Write, ReadWrite) on access relationships
- **Bulk operations** — create, update, or move multiple elements, relationships, or views in a single call
- **Relationship editing** — change a logical relationship's type (preserving its ID and view connections) or delete it without touching endpoints, with bulk variants for spec-compliance sweeps
- **Bulk type change** — change the ArchiMate type of multiple elements in one call, preserving all references
- **Bulk view creation** — create multiple views in a single call
- **Bulk figure styling** — update appearance of multiple figures across views in one call
- **View editing** — update a view's name and documentation after creation
- **Connection inspection** — list all connections on a view with routing, bendpoints, and access types
- **BIAN SD Overview composite** — create a complete BIAN Service Domain Overview view (elements, relationships, figures, connections, styling) in one atomic call
- **Bulk BIAN SD Overview** — create many SD Overview views in a single call with shared folder defaults and per-item success/error results
- **Element analysis** — inspect an element's relationships and view usage
- **Token-efficient responses** — minimal JSON responses (no echoed inputs, no success flags, no derivable counts) to reduce LLM token usage by ~35-40%
- **View export** — export any view as a PNG image, returned inline via MCP and optionally saved to disk
- **Auto-layout** — hierarchical directed graph layout for views, with recursive nested container support and automatic container resizing
- **Model validation** — validate all relationships against the ArchiMate specification, with inline validation on relationship creation
- **Undo support** — all mutation tools integrate with Eclipse's CommandStack, enabling Ctrl+Z undo in Archi for MCP-driven changes
- **MCP image content** — tools can return image content blocks natively, enabling visual feedback to AI agents
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

2. Copy it into Archi's `dropins/` directory:

   **macOS**
   ```bash
   cp za.co.jesseleresche.archi.mcp-2.0.1.jar /Applications/Archi.app/Contents/Eclipse/dropins/
   ```

   **Linux**
   ```bash
   cp za.co.jesseleresche.archi.mcp-2.0.1.jar /opt/Archi/dropins/
   ```

   **Windows** (PowerShell)
   ```powershell
   Copy-Item za.co.jesseleresche.archi.mcp-2.0.1.jar "C:\Program Files\Archi\dropins\"
   ```

   > **Upgrading from an earlier version?** Delete any existing
   > `za.co.jesseleresche.archi.mcp-*.jar` from `dropins/` **before** copying the new one.
   > The plugin is a singleton OSGi bundle — if multiple versions are present, Archi may load
   > an old one and your upgrade won't take effect. Only `za.co.jesseleresche.archi.mcp-2.0.1.jar`
   > should remain. For example, on macOS:
   > ```bash
   > rm /Applications/Archi.app/Contents/Eclipse/dropins/za.co.jesseleresche.archi.mcp-*.jar
   > cp za.co.jesseleresche.archi.mcp-2.0.1.jar /Applications/Archi.app/Contents/Eclipse/dropins/
   > ```

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

As of **v2.0.0** the tools are consolidated by domain entity. Each `manage_*` tool takes an
`operation` discriminator plus an `items` payload that accepts **either a single object or an
array** (batch) — a single object is treated as a one-item batch. Write tools return a uniform
`{ "results": [ … ] }` with a per-item success or error entry. This replaces the former
~45 single/`bulk_*` tools and dramatically shrinks the `tools/list` payload.

> **Migration from v1.x:** the old per-operation tool names (`create_element`, `bulk_create_elements`,
> `add_element_to_view`, `delete_view`, …) were removed. Use the consolidated tools below — e.g.
> `create_element` → `manage_elements` with `operation: "create"`; `bulk_add_elements_to_view`
> → `manage_view_content` with `operation: "add_element"`.

### Model session & querying

| Tool | Description |
|------|-------------|
| `manage_models` | `operation: list \| select` — list open models, or switch the active model by name/ID |
| `query_model` | Filter elements by ArchiMate type, layer, name, or folder |
| `get_views` | List diagram views with element, group, and note counts |

### Authoring & editing (write)

| Tool | Operations | Description |
|------|-----------|-------------|
| `manage_elements` | `create / update / delete` | Element CRUD. Update supports type changes (`new_type`); delete supports `dry_run` |
| `manage_relationships` | `create / update / delete` | Relationship CRUD. Type changes preserve ID and view connections; delete supports `dry_run` |
| `manage_views` | `create / update / delete / duplicate` | View management, including cloning a view with all figures/connections |
| `manage_view_content` | `add_element / add_relationship / remove_figure / update_connection / delete_connection` | Place/remove figures and draw/edit connections on a view (`view_id` given once at top level) |
| `manage_folders` | `create / move_element / move_view / list_contents / tree` | Create folders, move elements/relationships/views, and inspect the folder hierarchy |
| `manage_appearance` | `set_figure / layout_view` | Style figures (fill/font/line color, opacity, width, alignment) or auto-layout a view |

### Inspection & analysis (read)

| Tool | Description |
|------|-------------|
| `inspect_view` | `operation: layout \| connections \| get_connection` — figure positions/sizes, connections on a view, or one connection's visual properties |
| `get_element_analysis` | Inspect an element's relationships, view usage, and properties |
| `validate_model` | Validate all relationships against the ArchiMate specification, reporting violations with valid alternatives |

### Export & composite

| Tool | Description |
|------|-------------|
| `export_view_as_image` | Export a view as a PNG image (returned inline and optionally saved to disk) |
| `create_sd_overview_view` | Create a complete BIAN SD Overview view with all elements, relationships, figures, connections, and styling in one atomic call |

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
za.co.jesseleresche.archi.mcp/target/za.co.jesseleresche.archi.mcp-2.0.1.jar
```

Jetty and Jackson JARs are downloaded automatically into `lib/` during the build.

### 3. Install

Copy the built JAR to Archi's `dropins/` directory (see [Installation](#installation) above) and restart Archi.

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

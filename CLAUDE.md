# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Archi MCP Plugin — a single Eclipse OSGi plugin JAR for [Archi](https://www.archimatetool.com/) that implements the **Model Context Protocol (MCP)** over HTTP. When installed in Archi, it starts an embedded Jetty 11 HTTP server on `localhost:7432` that speaks MCP (JSON-RPC 2.0) to any compliant client (Claude Code, GitHub Copilot, Copilot Studio).

The full specification lives in `REQUIREMENTS.md`.

## Architecture

- **OSGi Plugin**: Bundle ID `com.archimatetool.mcp`, activator-started, targets JavaSE-21
- **Embedded Jetty 11 + Jackson**: Bundled in `lib/` (downloaded by Maven). All listed in `Bundle-ClassPath` in MANIFEST.MF.
- **Two MCP transports**:
  - **SSE** (`GET /sse` + `POST /message?sessionId=`) — for Claude Code and GitHub Copilot VS Code
  - **Streamable HTTP** (`POST /mcp`) — for Copilot Studio
- **Endpoints**: `/sse`, `/message`, `/mcp`, `/health`, `/openapi.yaml`

### Key Layers

```
Activator → McpServerManager → McpServer (Jetty)
                                  ├── Transport handlers (SSE, Streamable)
                                  ├── McpDispatcher (JSON-RPC routing)
                                  ├── ToolRegistry → ITool implementations
                                  └── ModelAccessor / UiThreadUtil (EMF model access)
```

### Threading Rules

- **Read-only** model operations can run on any thread
- **All mutations** must run inside `UiThreadUtil.syncExec()` (Eclipse UI thread)
- Jetty must bind to `127.0.0.1` only, never `0.0.0.0`

### MCP Tools

| Tool | Purpose |
|------|---------|
| `query_model` | List/filter elements by type, layer, or name |
| `get_views` | List diagram views, optionally with element IDs |
| `create_element` | Create an ArchiMate element in the model |
| `create_relationship` | Create a relationship between two elements |
| `create_view` | Create an empty diagram view |
| `add_element_to_view` | Place an element as a visual figure on a view |
| `add_relationship_to_view` | Draw a visual connection for an existing relationship |

### Important Patterns

- All `ITool.execute()` methods return a JSON string (serialized via Jackson) and throw `Exception` on failure
- Element/relationship type resolution is case-insensitive against `IArchimatePackage.eINSTANCE`
- Visual connections use `connection.connect(source, target)` — never manually add to `view.getChildren()`
- `ModelAccessor.getOpenModel()` returns the first open model or `null`
- Save after every mutation via `IEditorModelManager.INSTANCE.saveModel(model)`

## Build

Maven Tycho 5.0.2 builds the OSGi plugin JAR. Requires **Java 21** (`JAVA_HOME` must point to a JDK 21+).

The target platform resolves Archi and Eclipse dependencies from:
- Eclipse 2024-06 p2 repository (downloaded automatically)
- Local Archi installation directory (hardcoded in `target-platform/archi-mcp.target` — update the path for your OS)

```bash
# Build the plugin JAR (ensure JAVA_HOME points to JDK 21)
JAVA_HOME="$(/usr/libexec/java_home -v 21)" mvn clean verify

# Output: za.co.jesseleresche.archi.mcp/target/za.co.jesseleresche.archi.mcp-1.8.0.jar
```

The `lib/` directory (Jetty + Jackson JARs) is downloaded automatically by `maven-dependency-plugin` during build and is gitignored.

### Cross-platform Archi path

Edit `target-platform/archi-mcp.target` and update the `<location path="...">` to match your Archi installation:
- **macOS**: `/Applications/Archi.app/Contents/Eclipse`
- **Linux**: `/opt/Archi`
- **Windows**: `C:\Program Files\Archi`

### MANIFEST.MF formatting rules

- Every header must have its value start on the **same line** (e.g., `Require-Bundle: org.eclipse.ui,`)
- Continuation lines must start with exactly **one space**
- No line may exceed **72 bytes** (Tycho packaging will fail otherwise)

## Configuration

- Default port: `7432` (override with `-Darchi.mcp.port=<port>` system property)
- MCP protocol version: `2024-11-05`

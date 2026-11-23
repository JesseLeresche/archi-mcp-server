# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Archi MCP Plugin — a single Eclipse OSGi plugin JAR for [Archi](https://www.archimatetool.com/) that implements the **Model Context Protocol (MCP)** over HTTP. When installed in Archi, it starts an embedded Jetty 12 HTTP server on `localhost:7432` that speaks MCP (JSON-RPC 2.0) to any compliant client (Claude Code, GitHub Copilot, Copilot Studio).

The full specification lives in `REQUIREMENTS.md`.

## Architecture

- **OSGi Plugin**: Bundle ID `com.archimatetool.mcp`, activator-started, targets JavaSE-21
- **Embedded Jetty 12 (EE11 / Servlet 6.1) + Jackson**: Bundled in `lib/` (downloaded by Maven). All listed in `Bundle-ClassPath` in MANIFEST.MF.
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

### MCP Tools (consolidated in v2.0.0, ops renamed in v3.0.0)

Since v2.0.0 the tools are grouped by domain entity. Each `manage_*` tool takes an `operation`
discriminator plus an `items` payload (a single object **or** an array for batch) and returns a
uniform `{ "results": [...] }`. The 14 registered tools are:

| Tool | Operations / Purpose |
|------|----------------------|
| `manage_models` | `list` / `select` |
| `query_model` | List/filter elements by type, layer, name, or folder |
| `get_views` | List diagram views with element/group/note counts |
| `manage_elements` | `create` / `update` / `delete` / `merge` |
| `manage_relationships` | `create` / `update` / `delete` / `valid_types` / `between` / `query` |
| `manage_views` | `create` / `update` / `delete` / `duplicate` |
| `manage_view_content` | `add_figure` / `remove_figure` / `add_connection` / `update_connection` / `remove_connection` |
| `manage_folders` | `create` / `delete` / `move_element` / `move_view` / `list_contents` / `tree` |
| `manage_appearance` | `set_style` / `layout_view` |
| `inspect_view` | `layout` / `connections` / `get_connection` / `definition` (read-only) |
| `get_element_analysis` | Inspect an element's relationships, view usage, properties |
| `validate_model` | Validate relationships against the ArchiMate spec; always lists `dangling_connections` |
| `export_view_as_image` | Export a view as a PNG (inline + optional disk save) |
| `create_sd_overview_view` | Build a complete BIAN SD Overview view atomically |

**Consolidation architecture:** the `manage_*` tools (`ManageElementsTool`, etc.) extend
`ConsolidatedTool` and are **pure routing** — they reshape arguments and delegate to the existing
per-operation tool classes (e.g. `BulkCreateElementsTool`, `UpdateConnectionTool`), which remain in
the codebase as internal helpers. Those classes still own the EMF mutation logic and `syncExec`
wrapping, so consolidated tools must **never** wrap delegate calls in their own `syncExec`.

### Important Patterns

- Styling goes through `FigureStyle.apply/read/copy` (one vocabulary for figures and connections); never hand-roll setters in a tool
- Relationship `access_type` / `influence_strength` / `directed` go through `RelationshipAttributes` (check before mutating, apply, read as names); never `asInt()` an access type in a tool
- `ConsolidatedTool.execute` is final: subclasses implement `executeOperation`; it handles `results: "summary"` (`{count, succeeded, reused, skipped, failed, errors}`; `reused`/`skipped` entries are not `succeeded`), and `normalizeItems` handles `items_file`
- Files clients name (`items_file`, `output_file`) go through `ExchangeDir` only — never accept a path
- All `ITool.execute()` methods return a JSON string (serialized via Jackson) and throw `Exception` on failure
- Consolidated `manage_*` tools delegate to per-operation tool classes; do not reimplement model logic in them
- `ModelAccessor.findFigureByElementId` / `findConnectionByRelationshipId` throw when the element/relationship is drawn more than once ("pass figure_id" / "pass connection_id"); use `findAllFiguresByElementId` / `findConnectionsByRelationshipId` to enumerate
- Element/relationship type resolution is case-insensitive against `IArchimatePackage.eINSTANCE`
- Visual connections use `connection.connect(source, target)` — never manually add to `view.getChildren()`. Note `connect`/`reconnect` re-sync the relationship's source/target to the figures' elements
- Removing figures or connections goes through `util/ConnectableRemoval` (subtree + every attached connection, undoable); never `EcoreUtil.delete`
- `ModelAccessor.getOpenModel()` returns the first open model or `null`
- Save after every mutation via `ModelAccessor.saveModel(model)` (never `IEditorModelManager` directly — tests swap the model source)

## Tests

- JUnit 5 tests live in `za.co.jesseleresche.archi.mcp/src/test/java` and run on a plain JVM via
  `maven-surefire-plugin` during `mvn clean verify` (excluded from the bundle by the
  tycho-compiler `test/**` exclude; Archi's inner bundle jars are added by an antrun step).
- Tools are tested through `ITool.execute()` with the `TestModels` JUnit extension
  (`@RegisterExtension final TestModels models = new TestModels();`): an in-memory model
  registered via `ModelAccessor.setModelSource`, UI-thread work run inline via
  `UiThreadUtil.setExecutor`, saves counted. Assert on the returned JSON and the EMF model.
- `src/test/java/org/eclipse/core/runtime/Platform.java` is a test-only shadow so Archi's
  `RelationshipsMatrix` can load its XML outside OSGi.
- New behaviour is written test-first (red, then green). Not covered by unit tests (verify
  live): `ExportViewAsImageTool` (SWT), `Activator`, `server/`, transport servlets (the
  Host/Origin rule itself is unit-tested in `LocalOriginGuardTest`).
- `scripts/StubBodies.java` stubs every method body for red/green characterisation runs.

## Build

Maven Tycho 5.0.2 builds the OSGi plugin JAR. Requires **Java 21** (`JAVA_HOME` must point to a JDK 21+).

The target platform resolves Archi and Eclipse dependencies from:
- Eclipse 2024-06 p2 repository (downloaded automatically)
- Local Archi installation directory (hardcoded in `target-platform/archi-mcp.target` — update the path for your OS)

```bash
# Build the plugin JAR (ensure JAVA_HOME points to JDK 21)
JAVA_HOME="$(/usr/libexec/java_home -v 21)" mvn clean verify

# Output: za.co.jesseleresche.archi.mcp/target/za.co.jesseleresche.archi.mcp-3.1.0.jar
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
- Extra allowed Host/Origin names: `-Darchi.mcp.allowed.hosts=a,b` (default: localhost only)
- Exchange folder for `items_file`/`output_file`: `~/.archi-mcp/exchange` (`-Darchi.mcp.exchange.dir`)
- MCP protocol version: `2024-11-05`

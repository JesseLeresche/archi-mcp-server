# Claude Code Spec: Archi MCP Plugin (Single JAR)

## Overview

Build a single Eclipse OSGi plugin for Archi that implements the Model Context Protocol
directly. When installed, it starts an embedded Jetty HTTP server that speaks MCP to any
compliant client — Claude Code, GitHub Copilot (VS Code), or Copilot Studio — with no
external dependencies beyond the JAR itself.

```
MCP Client (Claude Code / GitHub Copilot / Copilot Studio)
    │
    │  MCP over HTTP
    ▼
┌──────────────────────────────────────────────┐
│           com.archimatetool.mcp              │
│                                              │
│  Jetty HTTP Server (localhost:7432)          │
│  ├── GET  /sse          SSE transport        │
│  ├── POST /message      SSE messages         │
│  ├── POST /mcp          Streamable transport │
│  └── GET  /health       Health check         │
│                                              │
│  MCP Protocol Layer                          │
│  ├── initialize / initialized                │
│  ├── tools/list                              │
│  └── tools/call → Tool Handlers             │
│       ├── manage_models / query_model        │
│       ├── get_views / inspect_view           │
│       ├── manage_elements                    │
│       ├── manage_relationships               │
│       ├── manage_views                       │
│       ├── manage_view_content                │
│       ├── manage_folders / manage_appearance │
│       └── validate_model / export / sd_view  │
│                                              │
│  EMF Model Access (Archi Java API)           │
└──────────────────────────────────────────────┘
    │
    │  com.archimatetool.model EMF API
    ▼
  Open .archimate model
```

---

## Project Structure

```
com.archimatetool.mcp/
├── src/
│   └── com/archimatetool/mcp/
│       ├── Activator.java
│       ├── server/
│       │   ├── McpServer.java
│       │   └── McpServerManager.java
│       ├── transport/
│       │   ├── SseTransportHandler.java
│       │   ├── SseMessageHandler.java
│       │   └── StreamableTransportHandler.java
│       ├── protocol/
│       │   ├── McpDispatcher.java
│       │   ├── McpRequest.java
│       │   ├── McpResponse.java
│       │   └── McpError.java
│       ├── tools/
│       │   ├── ToolRegistry.java
│       │   ├── ITool.java
│       │   ├── QueryModelTool.java
│       │   ├── GetViewsTool.java
│       │   ├── ConsolidatedTool.java      (base of the manage_* tools)
│       │   ├── Manage*Tool.java / InspectViewTool.java (registered, pure routing)
│       │   └── Bulk*/Get*/Update*/Delete*Tool.java     (internal per-operation helpers)
│       └── util/
│           ├── ModelAccessor.java
│           ├── FigureStyle.java          (the one style vocabulary)
│           ├── ExchangeDir.java          (sandboxed items_file / output_file)
│           └── UiThreadUtil.java
├── lib/
│   ├── jetty-server-11.0.20.jar
│   ├── jetty-servlet-11.0.20.jar
│   ├── jetty-util-11.0.20.jar
│   ├── jetty-http-11.0.20.jar
│   ├── jetty-io-11.0.20.jar
│   └── jakarta.servlet-api-5.0.0.jar
├── META-INF/
│   └── MANIFEST.MF
├── plugin.xml
└── build.properties
```

---

## META-INF/MANIFEST.MF

```
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: Archi MCP Plugin
Bundle-SymbolicName: com.archimatetool.mcp;singleton:=true
Bundle-Version: 1.0.0.qualifier
Bundle-Activator: com.archimatetool.mcp.Activator
Bundle-RequiredExecutionEnvironment: JavaSE-17
Require-Bundle:
 org.eclipse.ui,
 org.eclipse.core.runtime,
 com.archimatetool.model,
 com.archimatetool.editor,
 com.fasterxml.jackson.core.jackson-databind,
 com.fasterxml.jackson.core.jackson-core,
 com.fasterxml.jackson.core.jackson-annotations
Bundle-ClassPath: .,
 lib/jetty-server-11.0.20.jar,
 lib/jetty-servlet-11.0.20.jar,
 lib/jetty-util-11.0.20.jar,
 lib/jetty-http-11.0.20.jar,
 lib/jetty-io-11.0.20.jar,
 lib/jakarta.servlet-api-5.0.0.jar
Export-Package: com.archimatetool.mcp
Bundle-ActivationPolicy: lazy
```

Download Jetty 11 JARs from Maven Central and place them in the `lib/` directory:
- `org.eclipse.jetty:jetty-server:11.0.20`
- `org.eclipse.jetty:jetty-servlet:11.0.20`
- `jakarta.servlet:jakarta.servlet-api:5.0.0`
- Plus transitive deps: jetty-util, jetty-http, jetty-io

Jackson is bundled with Archi 5.x — declare it in `Require-Bundle`, do not embed it.

---

## build.properties

```
source.. = src/
output.. = bin/
bin.includes = META-INF/,\
               .,\
               lib/,\
               plugin.xml
```

---

## plugin.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
  <extension-point id="com.archimatetool.mcp" name="Archi MCP Plugin"/>
</plugin>
```

---

## MCP Protocol Overview

The plugin implements JSON-RPC 2.0 as required by the MCP spec. All messages are JSON
objects with `jsonrpc: "2.0"` and either an `id` (requests/responses) or no `id`
(notifications).

### Lifecycle messages to handle

```
Client → Server: {"jsonrpc":"2.0","id":1,"method":"initialize","params":{...}}
Server → Client: {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05",
                   "capabilities":{"tools":{}},"serverInfo":{"name":"archi-mcp","version":"1.0.0"}}}

Client → Server: {"jsonrpc":"2.0","method":"notifications/initialized"}  (no id — notification)

Client → Server: {"jsonrpc":"2.0","id":2,"method":"tools/list"}
Server → Client: {"jsonrpc":"2.0","id":2,"result":{"tools":[...]}}

Client → Server: {"jsonrpc":"2.0","id":3,"method":"tools/call",
                   "params":{"name":"query_model","arguments":{...}}}
Server → Client: {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"..."}]}}
```

### Error response format

```json
{"jsonrpc":"2.0","id":3,"error":{"code":-32600,"message":"No model open in Archi"}}
```

Standard JSON-RPC error codes to use:
- `-32700` Parse error
- `-32600` Invalid request
- `-32601` Method not found
- `-32602` Invalid params
- `-32000` Application error (no model open, element not found, etc.)

---

## Transport 1: SSE (Claude Code, GitHub Copilot VS Code)

SSE transport uses two separate HTTP endpoints.

### `GET /sse` — `SseTransportHandler.java`

Opens a persistent Server-Sent Events stream. The client keeps this connection open for
the lifetime of the session.

Behaviour:
1. Set response headers:
   ```
   Content-Type: text/event-stream
   Cache-Control: no-cache
   Connection: keep-alive
   Access-Control-Allow-Origin: *
   ```
2. Immediately send the endpoint event telling the client where to POST messages:
   ```
   event: endpoint
   data: /message?sessionId=<uuid>
   ```
3. Register the response writer in a `ConcurrentHashMap<String, PrintWriter> sessions`
   keyed by the session UUID.
4. Block the servlet thread (e.g. with `AsyncContext` or a `CountDownLatch`) until the
   client disconnects or the server shuts down.
5. On disconnect, remove the session from the map.

Use Jetty async servlet support (`request.startAsync()`) so the thread is not held.

### `POST /message?sessionId=<uuid>` — `SseMessageHandler.java`

Receives a JSON-RPC message from the client.

Behaviour:
1. Read the raw JSON body.
2. Look up the session's `PrintWriter` from the sessions map by `sessionId`.
3. Return `202 Accepted` to the client immediately (the actual response goes via SSE).
4. Parse the JSON-RPC request and dispatch to `McpDispatcher`.
5. Send the JSON-RPC response back to the client via the SSE stream:
   ```
   event: message
   data: {"jsonrpc":"2.0","id":3,"result":{...}}
   ```
   Each SSE message must end with a blank line (`\n\n`).

---

## Transport 2: Streamable HTTP (Copilot Studio)

Streamable transport uses a single endpoint. This is the MCP spec's newer transport,
required by Copilot Studio after August 2025.

### `POST /mcp` — `StreamableTransportHandler.java`

Behaviour:
1. Read the JSON-RPC request body.
2. Parse and dispatch to `McpDispatcher` synchronously.
3. Return `200 OK` with `Content-Type: application/json` and the JSON-RPC response body.

For `initialize` requests, also return the header:
```
Mcp-Session-Id: <generated-uuid>
```

Copilot Studio uses streamable transport. The Swagger/OpenAPI descriptor it needs:

```yaml
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
```

Expose this descriptor at `GET /openapi.yaml`.

---

## GET /health

Returns a simple JSON status object. Used by clients to check the plugin is running before
attempting MCP operations.

```json
{
  "status": "ok",
  "model_open": true,
  "model_name": "RMB Architecture",
  "transport_sse": "http://localhost:7432/sse",
  "transport_streamable": "http://localhost:7432/mcp",
  "version": "1.0.0"
}
```

If no model is open: `model_open: false`, `model_name: null`.

---

## McpDispatcher.java

Routes JSON-RPC method calls to the appropriate handler. All methods handled:

| Method | Handler |
|---|---|
| `initialize` | Return server capabilities inline |
| `notifications/initialized` | No-op (notification, no response) |
| `tools/list` | Return tool descriptors from `ToolRegistry` |
| `tools/call` | Delegate to `ToolRegistry.call(name, arguments)` |
| Any other method | Return `-32601` Method not found error |

```java
public McpResponse dispatch(McpRequest request) {
    return switch (request.getMethod()) {
        case "initialize"               -> handleInitialize(request);
        case "notifications/initialized"-> null; // notification, no response
        case "tools/list"               -> handleToolsList(request);
        case "tools/call"               -> handleToolsCall(request);
        default                         -> McpResponse.methodNotFound(request.getId());
    };
}
```

### `handleInitialize`

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {}
    },
    "serverInfo": {
      "name": "archi-mcp",
      "version": "1.0.0"
    }
  }
}
```

### `handleToolsList`

Return the full tool descriptor list from `ToolRegistry.getDescriptors()`. Each tool
descriptor follows the MCP schema:

```json
{
  "name": "create_element",
  "description": "Create a new ArchiMate element in the open model.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "name":          {"type": "string", "description": "Element name"},
      "type":          {"type": "string", "description": "ArchiMate type, e.g. ApplicationComponent"},
      "documentation": {"type": "string", "description": "Optional documentation"}
    },
    "required": ["name", "type"]
  }
}
```

### `handleToolsCall`

1. Extract `name` and `arguments` from `params`.
2. Look up the tool in `ToolRegistry`.
3. If not found: return `-32601` error.
4. Call `tool.execute(arguments)` — returns a `String` result.
5. Wrap in MCP content response:

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      {"type": "text", "text": "<result JSON string>"}
    ]
  }
}
```

6. If `tool.execute()` throws, return a `-32000` error with the exception message.

---

## ITool.java

```java
package com.archimatetool.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface ITool {
    String getName();
    String getDescription();
    ObjectNode getInputSchema();   // JSON Schema object for MCP tools/list
    String execute(JsonNode args) throws Exception;
}
```

All `execute()` implementations must return a JSON string (serialised via Jackson).
Throw a plain `Exception` with a human-readable message on any failure.

---

## ToolRegistry.java

```java
public class ToolRegistry {
    private final Map<String, ITool> tools = new LinkedHashMap<>();

    public ToolRegistry() {
        register(new ManageModelsTool());
        register(new QueryModelTool());
        register(new GetViewsTool());
        register(new ManageElementsTool());
        register(new ManageRelationshipsTool());
        register(new ManageViewsTool());
        register(new ManageViewContentTool());
        register(new ManageFoldersTool());
        register(new ManageAppearanceTool());
        register(new InspectViewTool());
        register(new GetElementAnalysisTool());
        register(new ValidateModelTool());
        register(new ExportViewAsImageTool());
        register(new CreateSdOverviewViewTool());
    }

    private void register(ITool tool) {
        tools.put(tool.getName(), tool);
    }

    public List<ObjectNode> getDescriptors() { ... }   // returns list of tool descriptor nodes
    public ITool get(String name) { return tools.get(name); }
}
```

---

## Tool Implementations

All tools access the model via `ModelAccessor.getOpenModel()`. All mutation operations
must be wrapped in `UiThreadUtil.syncExec(() -> { ... })`.

> **v2.0.0 consolidation note.** The per-operation classes documented below remain in the
> codebase and still own the model logic, but they are no longer registered individually.
> The public MCP surface is now ~14 consolidated tools (`manage_elements`, `manage_views`,
> `manage_view_content`, …) that take an `operation` discriminator plus an `items` payload
> (single object or array) and **delegate** to these classes. See `CLAUDE.md` / `README.md`
> for the current tool list and `ConsolidatedTool` for the delegation pattern. The sections
> below therefore describe the *operations* available within the consolidated tools.

### QueryModelTool — `query_model`

**Description**: List all elements in the open model. Optionally filter by ArchiMate
type, layer, or name substring.

**Input schema**:
```json
{
  "type": "object",
  "properties": {
    "type_filter":  {"type": "string", "description": "ArchiMate type, e.g. ApplicationComponent"},
    "layer_filter": {"type": "string", "enum": ["Application","Business","Technology","Motivation","Implementation"]},
    "name_search":  {"type": "string", "description": "Case-insensitive partial name match"}
  }
}
```

**Implementation**: Iterate `model.getElements()`. For each `IArchimateElement`, apply
filters. Build a `List<Map>` with fields `id`, `name`, `type`, `layer`, `documentation`.
Return as JSON array string.

Layer detection via instanceof checks:
- `IApplicationLayerElement` → "Application"
- `IBusinessLayerElement` → "Business"
- `ITechnologyLayerElement` → "Technology"
- `IMotivationElement` → "Motivation"
- `IImplementationMigrationElement` → "Implementation"

---

### GetViewsTool — `get_views`

**Description**: List all ArchiMate diagram views in the open model.

**Input schema**:
```json
{
  "type": "object",
  "properties": {
    "include_elements": {"type": "boolean", "description": "Include element IDs on each view"}
  }
}
```

**Implementation**: Walk all `IFolder` trees recursively. For each
`IArchimateDiagramModel`, collect `id`, `name`, `documentation`, `element_count`. If
`include_elements` is true, also collect the `IArchimateElement` IDs from children that
are `IDiagramModelArchimateObject`.

---

### v3.0.0 contract for the write tools

Every `manage_*` tool takes `operation` plus `items` (one object or an array), or instead
`items_file`: a bare `.json` file name in the exchange folder `~/.archi-mcp/exchange`
(override `-Darchi.mcp.exchange.dir`). Paths, symlinks and non-`.json` names are rejected;
files over 20 MB are refused. `results: "summary"` replaces the per-item list with
`{count, succeeded, reused, skipped, failed, errors: [{index, error}]}`; items marked `reused: true` or `skipped: true` count there, not
under `succeeded`.

**Caller-supplied IDs.** `manage_elements` / `manage_relationships` / `manage_views` `create`
accept `id`; `add_figure` accepts `figure_id`; `add_connection` accepts `connection_id`.
Empty or whitespace IDs are an item error. An ID already used anywhere in the model (or
earlier in the same batch) is an item error unless `on_conflict: "skip"`, which reports
`{id, skipped: true}` and leaves the existing object unchanged.

**Properties and specialisations.** `properties: [{key, value}]` on element, relationship
and view create/update (existing keys updated in place, a `null` value removes the key,
`remove_properties: [key]` removes by key; updates report `removed: n`).
`specialization: "<name>"` on element and relationship create/update finds or creates the
model profile with that name for the concept's type and makes it the concept's
specialisation; `null`/`""` clears it. `query_model` and `get_element_analysis` return it;
`query_model` filters on it.

**View content** (`manage_view_content`, `view_id` given once):

| Operation | Item |
|---|---|
| `add_figure` | `{kind?: element\|group\|note, element_id (element), name?, documentation? (group), content? (note), x?, y?, width?, height?, parent_figure_id?, figure_id?, on_conflict?, <style>}` |
| `remove_figure` | `{figure_id?\|element_id?}` (`element_id` fails if the element has more than one figure; pass `figure_id`) |
| `add_connection` | `{relationship_id, source_figure_id?, target_figure_id?, connection_id?, on_conflict?, on_existing?: reuse\|add, bendpoints?, <style>}` |
| `update_connection` | `{connection_id?\|relationship_id?, bendpoints?, <style>}` |
| `remove_connection` | `{connection_id?\|relationship_id?}`: removes the drawing only; delete the relationship with `manage_relationships delete` |

`parent_figure_id` may name a figure added earlier in the same batch. A connection endpoint
named by figure ID must be an element figure showing that end of the relationship.
All figures in one `add_figure` call are added in a single undoable command.

**A relationship drawn more than once.** `add_connection` may draw a relationship again, for
example from a second figure of its source. An existing drawing between the same figures (an
omitted figure ID matches any figure of that element) fails the item with `Relationship <id> is
already drawn as connection <cid> between these figures; pass on_existing: "reuse" or "add"`.
`"reuse"` returns `{connection_id, reused: true}` of the first match and changes nothing (style,
bendpoints and `connection_id` are ignored); `"add"` draws another line. Any other value is an
item error. An omitted end whose element is shown more than once fails with `Element <id> has <n>
figures on view <viewId> (<f1>, <f2>); pass source_figure_id` (or `target_figure_id`).
Wherever a `relationship_id` or `element_id` targets one drawing (`update_connection`,
`remove_connection`, `remove_figure`, `set_style`, `get_connection`, `inspect_view layout`), it
fails when the relationship or element is drawn more than once, listing the IDs and asking for
`connection_id` / `figure_id`. `inspect_view connections` and `get_element_analysis` list every
drawing.

**Styling** (`manage_appearance set_style`): `{view_id?, figure_id?|element_id?|connection_id?|relationship_id?, <style>, x?, y?, width?, height?}`; bounds only for figures.

**Style vocabulary** (`FigureStyle`; absent = unchanged, `null`/`""` = reset to the default
Archi gives a new object of that type; a field that doesn't apply to the target is an item
error and nothing is changed): `fill_color`, `line_color`, `font_color`, `icon_color`,
`font` (Archi font string), `opacity`, `line_opacity`, `line_width`, `line_style`,
`text_alignment`, `text_position`, `gradient`, `derive_line_color`, `icon_visible`,
`figure_type`, `border_type`, `image_source`, `image_path` (must already be used in the
model; importing images is not supported), `image_position`, `hide_junction_arrows`,
`name_visible` (connections), `label_expression`. Values are Archi's native ones.

**Renamed in v3.0.0** (clean break; the old names return an error naming the replacement):
`set_figure` → `set_style`, `add_element` → `add_figure`, `add_relationship` →
`add_connection`, `delete_connection` → `remove_connection`.

**`inspect_view definition`** returns `{view, elements, relationships, figures, connections}`
using exactly the field names of the create / `add_figure` / `add_connection` inputs,
figures parent-before-child, styles as non-default vocabulary fields. Replaying it in that
order (into any open model) reproduces the view; a second `definition` is identical.
Optional `model` (name or ID; read-only, selection unchanged) and `output_file` (written
to the exchange folder, never overwriting). Note connectors and diagram references are
reported under `warnings`, not exported.

**Relationship attributes** (`manage_relationships create` / `update`): `access_type`
(AccessRelationship only) is `"Write"`, `"Read"`, `"Unspecified"` or `"ReadWrite"`
(case-insensitive) or Archi's integer (0=Write, 1=Read, 2=Unspecified, 3=ReadWrite); a new
Access relationship defaults to Write. `influence_strength` (string, `""` clears;
InfluenceRelationship only) and `directed` (boolean; AssociationRelationship only). Given
for another type, each is the item error `'<field>' does not apply to <Type>` (on update,
judged against the type after `new_type`), and nothing in that item changes. An update
that changes the type reports the old values the new type can't hold (and the
specialization, unless the item sets one) as `dropped: {field: old value}`. Every output
(`query`, `inspect_view definition` / `connections` / `get_connection`) reports
`access_type` as a name, `influence_strength` if set and `directed` only if true.

**`on_existing`** (`manage_relationships create`): if a relationship of the same type
already joins the same source to the same target (direction matters; earlier items in
the batch count), the item fails with `relationship <id> already exists between these
elements; pass on_existing: "reuse" or "add"`. `"reuse"` returns `{id, reused: true,
folder_path}` of the existing one (first in folder order) and changes nothing; `"add"`
creates another. A caller `id` that already exists is decided by `on_conflict` alone.

**`manage_relationships query`**: each item is a filter `{element_id?, type_filter?,
on_view?, model?, output_file?}` (AND; `element_id` matches either end; `on_view`
true/false = drawn on some/no view). Returns `{count, relationships}` over every
relationship in the model in folder order, each entry in create's field names
(`id, type, name, documentation?, folder_path, properties?, specialization?, source_id,
target_id, access_type?, influence_strength?, directed?`) plus read-only `views` (view IDs,
each once). With `output_file`, the bare entry array is written to the exchange folder
(never overwriting) and the result is `{output_file, count}`. Read-only; `model` as for
`definition`. Unknown type, element ID or model is an item error. The output replays into
`create` unchanged (inline or `items_file`); into a model that already holds the
relationships, pass `on_conflict: "skip"` (same IDs) or `on_existing: "reuse"`
(different IDs).

**Figure removal leaves no dangling connections** (spec
`docs/specs/figure-removal-dangling-connections/`). `manage_elements delete` removes
**every** figure of the element on every view (nested, and several on one view);
`remove_figure` removes its figure. Either way the whole subtree leaves the view together
with every connection attached at either end to any figure in it, and, transitively, every
connection attached to a removed connection. Each is a single undo step that restores
figures (same parent and index) and connections (both ends). Delete's `dry_run` reports
true counts: `figures_to_remove` (figures, not views), `connections_to_remove` (distinct
connections), `affected_view_ids` (each once); `figures_removed` is a true count. Lookups by
`connection_id` / `relationship_id` find a connection attached at either end to the view, so
a ghost whose source was detached can be removed with `remove_connection`.

**`manage_elements merge`**: item `{survivor_id, duplicate_ids: [id,...], collapse?: false,
resolve?, dry_run?}`, one undo step per item. Missing/non-element IDs, an empty or repeated
`duplicate_ids`, the survivor listed as a duplicate, or a duplicate of another type is an
item error. Relationships touching a duplicate are re-pointed to the survivor (IDs, type,
properties, folder kept); those that would become survivor self-loops are deleted with all
their connections. Every duplicate figure is retargeted in place (same ID, bounds, parent,
children, style, connections). The survivor keeps its name, folder and specialisation;
differing duplicate names are listed in `duplicate_names`. The diff, like git:
`conflicts` holds `{property, survivor: [values], duplicates: {id: [values]}}` (repeated
keys compared as ordered lists) and `{documentation: {survivor, id: text}}`; values only
one side has fill in. Unresolved conflicts block the item, which returns
`{error: "unresolved conflicts", conflicts, parallel_relationships}` and changes nothing;
re-send with `resolve: {properties: {key: value|[values]}, documentation, relationships:
{<kept id>: {properties, documentation, name}}}`. A `resolve` entry matching no conflict
is an error that carries the current diff. `parallel_relationships: [{kept, parallel}]`
lists relationships left with the same type, ends and type attributes (kept: the one not
re-pointed, else the first duplicate's, else model order); `collapse: true` merges each
group into the kept one (relationship names join the diff), moves its connections (same
objects) or drops a connection that would double an existing one, and re-points
relationships on collapsed relationships. Result: `{survivor_id, merged,
relationships_repointed, relationships_deleted, relationships_collapsed, connections_moved,
connections_removed, figures_retargeted, affected_view_ids, parallel_relationships,
duplicate_names}`; `dry_run` adds `dry_run: true` and `conflicts`, never blocks and never
saves.

**`validate_model dangling_connections`** (always returned): every connection reachable
from a view with a missing end, an end detached or on another view, or that is itself
detached, once, as `{view_id, view_name, connection_id, relationship_id|null, problems}`
with problems from `source_missing`, `target_missing`, `source_detached`,
`target_detached`, `source_other_view`, `target_other_view`, `connection_detached`. Each
can be removed with `remove_connection` by `connection_id`.

---

## ModelAccessor.java

Utility methods for safe model access. All read-only operations may be called from any
thread. All mutation operations must be called from within `UiThreadUtil.syncExec`.

```java
public class ModelAccessor {

    public static IArchimateModel getOpenModel() {
        List<IArchimateModel> models = IEditorModelManager.INSTANCE.getModels();
        return models.isEmpty() ? null : models.get(0);
    }

    public static IArchimateElement findElementById(IArchimateModel model, String id) {
        return model.getElements().stream()
            .filter(e -> id.equals(e.getId()))
            .findFirst().orElse(null);
    }

    public static IArchimateRelationship findRelationshipById(IArchimateModel model, String id) {
        return model.getRelationships().stream()
            .filter(r -> id.equals(r.getId()))
            .findFirst().orElse(null);
    }

    public static IArchimateDiagramModel findViewById(IArchimateModel model, String id) {
        for (IFolder folder : model.getFolders()) {
            IArchimateDiagramModel result = searchFolderForView(folder, id);
            if (result != null) return result;
        }
        return null;
    }

    private static IArchimateDiagramModel searchFolderForView(IFolder folder, String id) {
        for (var element : folder.getElements()) {
            if (element instanceof IArchimateDiagramModel v && id.equals(v.getId())) return v;
        }
        for (IFolder sub : folder.getFolders()) {
            IArchimateDiagramModel result = searchFolderForView(sub, id);
            if (result != null) return result;
        }
        return null;
    }

    public static void saveModel(IArchimateModel model) throws IOException {
        IEditorModelManager.INSTANCE.saveModel(model);
    }

    /**
     * Find a diagram figure (IDiagramModelArchimateObject) on a view by its figure ID.
     * Searches top-level children only — does not recurse into nested containers.
     */
    public static IDiagramModelArchimateObject findFigureById(
            IArchimateDiagramModel view, String figureId) {
        return view.getChildren().stream()
            .filter(c -> c instanceof IDiagramModelArchimateObject
                      && figureId.equals(c.getId()))
            .map(c -> (IDiagramModelArchimateObject) c)
            .findFirst().orElse(null);
    }

    /**
     * Find the figure on a view whose archimateElement has the given element ID.
     * Searches top-level children only.
     */
    public static IDiagramModelArchimateObject findFigureByElementId(
            IArchimateDiagramModel view, String elementId) {
        return view.getChildren().stream()
            .filter(c -> c instanceof IDiagramModelArchimateObject dmo
                      && elementId.equals(dmo.getArchimateElement().getId()))
            .map(c -> (IDiagramModelArchimateObject) c)
            .findFirst().orElse(null);
    }

    /**
     * Find an existing visual connection on a view for a given relationship ID.
     * Checks both top-level children and connections attached to each figure.
     * Returns null if no connection exists for this relationship.
     */
    public static IDiagramModelArchimateConnection findConnectionByRelationshipId(
            IArchimateDiagramModel view, String relationshipId) {
        for (var child : view.getChildren()) {
            if (child instanceof IDiagramModelArchimateObject figure) {
                for (var conn : figure.getSourceConnections()) {
                    if (conn instanceof IDiagramModelArchimateConnection c
                            && relationshipId.equals(c.getArchimateRelationship().getId())) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    public static EClass resolveElementClass(String typeName) {
        return IArchimatePackage.eINSTANCE.getEClassifiers().stream()
            .filter(c -> c instanceof EClass ec
                && ec.getName().equalsIgnoreCase(typeName)
                && IArchimatePackage.eINSTANCE.getArchimateElement()
                       .isSuperTypeOf(ec))
            .map(c -> (EClass) c)
            .findFirst().orElse(null);
    }

    public static EClass resolveRelationshipClass(String typeName) {
        return IArchimatePackage.eINSTANCE.getEClassifiers().stream()
            .filter(c -> c instanceof EClass ec
                && ec.getName().equalsIgnoreCase(typeName)
                && IArchimatePackage.eINSTANCE.getArchimateRelationship()
                       .isSuperTypeOf(ec))
            .map(c -> (EClass) c)
            .findFirst().orElse(null);
    }
}
```

---

## UiThreadUtil.java

```java
public class UiThreadUtil {

    /**
     * Run a callable on the Eclipse UI thread and return its result.
     * Throws RuntimeException if the callable throws.
     */
    public static <T> T syncExec(Callable<T> callable) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        Display.getDefault().syncExec(() -> {
            try {
                result.set(callable.call());
            } catch (Exception e) {
                error.set(e);
            }
        });

        if (error.get() != null) {
            throw new RuntimeException(error.get().getMessage(), error.get());
        }
        return result.get();
    }

    /** Variant for void operations. */
    public static void syncExecVoid(ThrowingRunnable runnable) {
        syncExec(() -> { runnable.run(); return null; });
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
```

---

## McpServer.java

```java
public class McpServer {

    private final Server server;
    private final ToolRegistry toolRegistry;
    private final McpDispatcher dispatcher;

    // Shared SSE session map: sessionId → PrintWriter
    private final ConcurrentHashMap<String, AsyncContext> sseSessions = new ConcurrentHashMap<>();

    public McpServer(int port) {
        toolRegistry = new ToolRegistry();
        dispatcher   = new McpDispatcher(toolRegistry);

        server = new Server(new InetSocketAddress("127.0.0.1", port));

        ServletContextHandler context = new ServletContextHandler(
            ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // SSE transport
        context.addServlet(
            new ServletHolder(new SseTransportHandler(sseSessions)), "/sse");
        context.addServlet(
            new ServletHolder(new SseMessageHandler(sseSessions, dispatcher)), "/message");

        // Streamable transport
        context.addServlet(
            new ServletHolder(new StreamableTransportHandler(dispatcher)), "/mcp");

        // Health + OpenAPI
        context.addServlet(
            new ServletHolder(new HealthHandler()), "/health");
        context.addServlet(
            new ServletHolder(new OpenApiHandler()), "/openapi.yaml");

        server.setHandler(context);
    }

    public void start() throws Exception { server.start(); }
    public void stop()  throws Exception { server.stop(); }
}
```

**Critical**: The Jetty server must bind to `127.0.0.1` only, never `0.0.0.0`. Use
`new InetSocketAddress("127.0.0.1", port)` as shown above.

---

## McpServerManager.java

```java
public class McpServerManager {

    private static final int DEFAULT_PORT = 7432;
    private McpServer server;

    public void start() {
        int port = Integer.getInteger("archi.mcp.port", DEFAULT_PORT);
        try {
            server = new McpServer(port);
            server.start();
            Platform.getLog(Activator.getDefault().getBundle())
                .info("Archi MCP server started on port " + port);
        } catch (Exception e) {
            Platform.getLog(Activator.getDefault().getBundle())
                .error("Failed to start Archi MCP server", e);
        }
    }

    public void stop() {
        if (server != null) {
            try { server.stop(); }
            catch (Exception e) { /* log */ }
        }
    }
}
```

---

## Activator.java

```java
public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.archimatetool.mcp";
    private static Activator plugin;
    private McpServerManager serverManager;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        serverManager = new McpServerManager();
        serverManager.start();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (serverManager != null) serverManager.stop();
        plugin = null;
        super.stop(context);
    }

    public static Activator getDefault() { return plugin; }
}
```

---

## McpRequest.java and McpResponse.java

Plain POJOs for JSON-RPC 2.0 messages, deserialised/serialised with Jackson.

### McpRequest

Fields: `String jsonrpc`, `Object id`, `String method`, `JsonNode params`

Include a static factory: `McpRequest.parse(String json)` using Jackson ObjectMapper.
Return null if the body is not valid JSON (caller should return a parse error response).

### McpResponse

Fields: `String jsonrpc = "2.0"`, `Object id`, `JsonNode result`, `McpError error`

Static factories:
```java
McpResponse.success(Object id, JsonNode result)
McpResponse.error(Object id, int code, String message)
McpResponse.parseError()
McpResponse.methodNotFound(Object id)
McpResponse.invalidParams(Object id, String detail)
McpResponse.appError(Object id, String message)
```

### McpError

Fields: `int code`, `String message`

---

## SseTransportHandler.java (key detail)

Use Jetty async servlets to avoid holding a thread per SSE connection:

```java
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {

    response.setContentType("text/event-stream");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-cache");
    response.setHeader("Connection", "keep-alive");
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.flushBuffer();

    String sessionId = UUID.randomUUID().toString();
    AsyncContext async = request.startAsync();
    async.setTimeout(0); // no timeout

    sseSessions.put(sessionId, async);

    PrintWriter writer = response.getWriter();

    // Send endpoint event
    writer.write("event: endpoint\n");
    writer.write("data: /message?sessionId=" + sessionId + "\n\n");
    writer.flush();

    // Register cleanup on disconnect
    async.addListener(new AsyncListener() {
        @Override public void onComplete(AsyncEvent e) { sseSessions.remove(sessionId); }
        @Override public void onError(AsyncEvent e)    { sseSessions.remove(sessionId); }
        @Override public void onTimeout(AsyncEvent e)  { sseSessions.remove(sessionId); }
        @Override public void onStartAsync(AsyncEvent e) {}
    });
}
```

---

## SseMessageHandler.java (key detail)

```java
@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {

    String sessionId = request.getParameter("sessionId");
    AsyncContext async = sseSessions.get(sessionId);

    if (async == null) {
        response.sendError(404, "Session not found");
        return;
    }

    // Acknowledge receipt immediately
    response.setStatus(202);
    response.getWriter().write("accepted");
    response.getWriter().flush();

    // Read body
    String body = new String(request.getInputStream().readAllBytes(),
                             StandardCharsets.UTF_8);

    // Dispatch in a separate thread so we don't block the HTTP thread
    // (tools/call may block on Display.syncExec)
    CompletableFuture.runAsync(() -> {
        McpRequest mcpRequest = McpRequest.parse(body);
        if (mcpRequest == null) {
            sendSseMessage(async, McpResponse.parseError());
            return;
        }

        // Notifications have no id and require no response
        if (mcpRequest.getId() == null) {
            dispatcher.dispatch(mcpRequest); // side-effect only
            return;
        }

        McpResponse mcpResponse = dispatcher.dispatch(mcpRequest);
        if (mcpResponse != null) {
            sendSseMessage(async, mcpResponse);
        }
    });
}

private void sendSseMessage(AsyncContext async, McpResponse mcpResponse) {
    try {
        PrintWriter writer = async.getResponse().getWriter();
        String json = ObjectMapperHolder.MAPPER.writeValueAsString(mcpResponse);
        writer.write("event: message\ndata: " + json + "\n\n");
        writer.flush();
    } catch (IOException e) {
        // Client disconnected
    }
}
```

---

## StreamableTransportHandler.java (key detail)

```java
@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws IOException {

    String body = new String(request.getInputStream().readAllBytes(),
                             StandardCharsets.UTF_8);

    McpRequest mcpRequest = McpRequest.parse(body);

    if (mcpRequest == null) {
        writeJsonResponse(response, 400, McpResponse.parseError());
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

private void writeJsonResponse(HttpServletResponse response, int status, McpResponse body)
        throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Access-Control-Allow-Origin", "*");
    ObjectMapperHolder.MAPPER.writeValue(response.getWriter(), body);
}

// Also handle OPTIONS for CORS preflight (Copilot Studio sends these)
@Override
protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    response.setHeader("Access-Control-Allow-Headers", "Content-Type, Mcp-Session-Id");
    response.setStatus(204);
}
```

---

## ArchiMate Type Reference

Include this as a constant in `ModelAccessor` or a separate `ArchiMateTypes` class for
documentation and validation purposes.

### Element types (by layer)

```java
// Application
"ApplicationComponent", "ApplicationFunction", "ApplicationInterface",
"ApplicationService", "DataObject", "ApplicationProcess", "ApplicationEvent",
"ApplicationCollaboration", "ApplicationInteraction",

// Business
"BusinessActor", "BusinessRole", "BusinessCollaboration", "BusinessInterface",
"BusinessProcess", "BusinessFunction", "BusinessInteraction", "BusinessEvent",
"BusinessService", "BusinessObject", "Contract", "Representation", "Product",

// Technology
"Node", "Device", "SystemSoftware", "TechnologyCollaboration",
"TechnologyInterface", "Path", "CommunicationNetwork", "TechnologyFunction",
"TechnologyProcess", "TechnologyInteraction", "TechnologyEvent",
"TechnologyService", "Artifact",

// Motivation
"Stakeholder", "Driver", "Assessment", "Goal", "Outcome", "Principle",
"Requirement", "Constraint", "Meaning", "Value",

// Implementation & Migration
"WorkPackage", "Deliverable", "ImplementationEvent", "Gap", "Plateau"
```

### Relationship types

```java
"AssignmentRelationship", "AssociationRelationship", "CompositionRelationship",
"AggregationRelationship", "RealizationRelationship", "ServingRelationship",
"AccessRelationship", "InfluenceRelationship", "TriggeringRelationship",
"FlowRelationship", "SpecializationRelationship"
```

---

## Building and Installing

### Build in Eclipse

1. Set up the Archi target platform:
   - Clone `https://github.com/archimatetool/archi`
   - In Eclipse: `File → Open → com.archimatetool.editor.target/archi.target`
   - Click **Set as Active Target Platform** and wait for resolution
2. Import the `com.archimatetool.mcp` project into Eclipse
3. Download Jetty JARs into `lib/` (see MANIFEST.MF above for exact versions)
4. Export: **File → Export → Plug-in Development → Deployable plug-ins and fragments**
   - Export to directory: `/tmp/archi-mcp-export`

### Install

Copy the JAR from `/tmp/archi-mcp-export/plugins/` to Archi's `dropins` folder:

| OS | Path |
|---|---|
| macOS | `~/Library/Application Support/Archi4/dropins/` |
| Linux | `~/.archi4/dropins/` |
| Windows | `%APPDATA%\Archi4\dropins\` |

Restart Archi. Verify:
```bash
curl http://localhost:7432/health
```

### Port override

Add to `archi.ini` (in the Archi install directory):
```
-Darchi.mcp.port=7433
```

---

## Client Configuration

### Claude Code (`~/.claude.json` or project `.mcp.json`)

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

### GitHub Copilot VS Code (`.vscode/mcp.json` — commit to repo)

```json
{
  "servers": {
    "archi": {
      "type": "http",
      "url": "http://localhost:7432/sse"
    }
  }
}
```

### Copilot Studio

In Copilot Studio: **Agent → Tools → Add a Tool → New Tool → Model Context Protocol**

- **Server URL**: `http://<host>:7432/mcp`
- **Authentication**: None (internal network)

Or build a Power Platform custom connector using the OpenAPI descriptor at
`GET http://localhost:7432/openapi.yaml`.

If Copilot Studio reaches Archi through a tunnel or reverse proxy that forwards its own
host name, add that host name with `-Darchi.mcp.allowed.hosts=<host>`; otherwise the
Host/Origin guard returns 403.

---

## Critical Implementation Rules

1. **UI thread for all mutations**: Every call to `IArchimateFactory`, folder `add()`,
   `setSource()`, `setTarget()`, `setBounds()`, and `saveModel()` must execute inside
   `UiThreadUtil.syncExec()`. Violations cause random crashes that are hard to diagnose.

2. **Localhost only**: Jetty must bind to `127.0.0.1`, not `0.0.0.0`. This is a security
   requirement for corporate environments.

3. **SSE thread**: SSE connections must use Jetty's async servlet API
   (`request.startAsync()`). Do not hold a thread per connection.

4. **Dispatch off HTTP thread**: `tools/call` dispatches may block on `syncExec` (waiting
   for the UI thread). Always dispatch in a `CompletableFuture.runAsync()` in the SSE
   message handler to avoid blocking Jetty's request thread pool.

5. **Notification handling**: JSON-RPC notifications (no `id` field) must never receive
   a response. `notifications/initialized` is the main one to handle — process it and
   return null from dispatch.

6. **Jackson serialisation**: Use a single shared `ObjectMapper` instance (thread-safe).
   Annotate `McpResponse` with `@JsonInclude(NON_NULL)` to omit null `error`/`result`
   fields.

7. **Local Host/Origin only**: `/sse`, `/message` and `/mcp` reject (403) a `Host` header
   that isn't `localhost`, `127.0.0.1` or `[::1]` (any port), and an `Origin` header, when
   present, that isn't one of those (`LocalOriginGuard`). This blocks DNS-rebinding and
   cross-site requests from browser pages. Extra host names (e.g. a tunnel) can be allowed
   with `-Darchi.mcp.allowed.hosts=host1,host2`. No permissive CORS headers.

8. **Model null check**: Every tool must check `ModelAccessor.getOpenModel()` returns
   non-null before proceeding, and return a `-32000` error with the message
   `"No model is currently open in Archi"` if it is null.

9. **Visual connections use `connect()`**: When creating an
   `IDiagramModelArchimateConnection`, always call `connection.connect(sourceFigure,
   targetFigure)` to wire it to the view. Never call `view.getChildren().add(connection)`
   manually — `connect()` handles all internal containment and source/target list wiring.
   Bypassing it produces a diagram that renders incorrectly and may corrupt the file.

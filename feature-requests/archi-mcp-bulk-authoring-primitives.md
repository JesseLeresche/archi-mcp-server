# Feature request: bulk-authoring primitives (file payloads, preserved IDs, groups/notes, fonts, view round-trip)

**Plugin:** archi-mcp (Archi MCP server)
**Type:** enhancements to existing tools + one new read operation
**Priority:** high — blocks faithful, scripted migration of views between models

## Summary

Composable additions that let an agent reproduce an existing view with full fidelity,
at scale, without pasting hundreds of KB of JSON into tool calls. **Status: implemented
in v3.0.0** (breaking: four operations renamed). The design below is what shipped; it
differs from the original request where noted.

Together these give the capability of a dedicated "import views from another model"
tool, but as general-purpose primitives that also serve bulk generation, scripted
rebuilds, syncs from external sources, backups, diffs and templating.

## Motivating case

Reconciling an exported model (`Mantle 1.archimate`) into the live `ConceptualViews`
model: 14 views to copy, needing ~830 elements, ~650 relationships, 1,161 figures
(856 nested) and 1,016 connections.

A fidelity scan of those views found these gaps in the current MCP:

| Need | Current MCP | Consequence |
|---|---|---|
| Keep source IDs | `create` always generates a new ID | Caller must build an old→new ID map from results; breaks when results are truncated |
| Diagram Groups (20) | Cannot create | Visual containers lost; nested children orphaned |
| Fonts (359 figures) | Cannot set | Text size/weight wrong |
| `deriveElementLineColor` (10 figures) | Cannot set | Minor line-colour drift |
| Bulk payloads | Inline `items` only | ~400 KB of arguments over ~60 calls; context exhaustion; large results truncated |

Already supported and NOT part of this request: fill/line/font colour, text
alignment, bounds, nesting via `parent_figure_id`, bendpoints.

## Design as built (v3.0.0)

Tool count stays at 14.

| Tool | Before | After |
|---|---|---|
| `manage_appearance` | `set_figure`, `layout_view` | `set_style`, `layout_view` |
| `manage_view_content` | `add_element`, `add_relationship`, `remove_figure`, `update_connection`, `delete_connection` | `add_figure`, `remove_figure`, `add_connection`, `update_connection`, `remove_connection` |
| `inspect_view` | `layout`, `connections`, `get_connection` | + `definition` |
| `create` (elements, relationships, views) | generated ID | optional `id`, `on_conflict: error \| skip` |
| all `manage_*` batch ops | inline `items` | `items` or `items_file`; `results: "full" \| "summary"` |

The old operation names return an error naming the replacement.

### 1. One style vocabulary (instead of adding `font` to `set_figure`)

Styling was spread over four operations with different field subsets. Now every styling
path (`set_style`, `add_figure`, `add_connection`, `update_connection`, duplicate,
`definition`) uses one vocabulary: `fill_color`, `line_color`, `font_color`, `icon_color`,
`font` (Archi's native font string), `opacity`, `line_opacity`, `line_width`, `line_style`,
`text_alignment`, `text_position`, `gradient`, `derive_line_color`, `icon_visible`,
`figure_type`, `border_type`, `image_source`, `image_path` (must already be in the model),
`image_position`, `hide_junction_arrows`, `name_visible` (connections), `label_expression`.
Absent leaves a field unchanged; `null`/`""` resets it; a field that doesn't apply to the
target is an item error. `set_style` targets figures (`figure_id`/`element_id`) or
connections (`connection_id`/`relationship_id`).

### 2. `add_figure` (instead of `add_group` + `add_note`)

`{kind?: element|group|note, element_id | name, documentation? | content, x?, y?, width?,
height?, parent_figure_id?, figure_id?, on_conflict?, <style>}`. `parent_figure_id` may name
a figure added earlier in the same batch, so a definition replays in one call.

### 3. Caller-supplied IDs

`id` on element / relationship / view `create`, `figure_id` on `add_figure`,
`connection_id` on `add_connection`. The ID is checked against the whole model and the
batch. `on_conflict: error` (default) or `skip`. `reuse` was not built.

### 4. Files, sandboxed (instead of absolute paths)

`items_file` and `output_file` take a bare `.json` file name in `~/.archi-mcp/exchange`
(`-Darchi.mcp.exchange.dir`). Paths, symlinks, and anything resolving outside the folder are
rejected; writes never overwrite; reads are capped at 20 MB. The server is unauthenticated on
localhost, so absolute paths would have meant arbitrary file read/write. `results_file` was
dropped: `results: "summary"` returns `{count, succeeded, failed, errors: [{index, error}]}`
inline.

### 5. `inspect_view definition` (instead of a new `get_view_definition` tool)

Returns `{view, elements, relationships, figures, connections}` using the exact field names
of the create / `add_figure` / `add_connection` inputs, figures parent-before-child.
Replaying it in that order into another open model reproduces the view, and a second
`definition` is identical (unit-tested). It takes optional `model` (any open model;
read-only) and `output_file`.

### Also in v3.0.0

- `properties` on relationship and view create, and on view update. Relationship update
  now merges properties instead of replacing them.
- `specialization` on element and relationship create/update. It is returned and
  filterable in `query_model`, and returned by `get_element_analysis`.
- The MCP endpoints reject a non-local `Host`/`Origin`. Use
  `-Darchi.mcp.allowed.hosts` for tunnels.

## Not built

`reuse` conflict mode, structured font objects, auto-spill, `atomic` / `dry_run` on
batches, importing image files, note legend options, plain note connectors, cross-model
`duplicate`.

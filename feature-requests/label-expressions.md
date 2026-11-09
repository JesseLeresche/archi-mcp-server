# Feature request: read/write Label Expressions on figures and connections

**Plugin:** archi-mcp (Archi MCP server)
**Tools:** `manage_view_content` (`add_element`, `add_relationship`, `update_connection`),
`manage_appearance` (`set_figure`), `inspect_view` (`get_connection`, `layout`)
**Type:** enhancement
**Priority:** medium

## Summary

Support getting and setting ArchiMate **Label Expressions** on diagram objects (figures) and
connections, so a diagram label can differ from the underlying element/relationship name
(e.g. multi-line labels with protocol annotations) without polluting model names.

## Motivation

Archi stores a per-figure/per-connection Label Expression as
`<feature name="labelExpression" value="..."/>` on the diagram object/connection, e.g.
`${name}\nTLS1.3; HTTPS; API Call`. The relationship name stays clean
(`Query LLM with scenario & RAG Context`) while the diagram renders a richer, multi-line label.

Today the only way to get a multi-line connection label is to embed literal `\n` and the extra
text in the relationship **name**, which:

- pollutes the name shown in the model tree and every other view,
- makes names noisy / non-unique and harder to search or reconcile,
- diverges from hand-authored reference views (which use Label Expressions with a clean `${name}`).

`inspect_view { get_connection }` also doesn't surface the label expression, so a round-trip
loses it.

## Proposed change

- Add a `label_expression` field to:
  - `manage_view_content` → `add_element`, `add_relationship`, `update_connection`
  - `manage_appearance` → `set_figure`
- Return `label_expression` (when set) from `inspect_view` → `get_connection` and `layout`.
- `null` / empty string clears the expression (reverts to `${name}` / default).
- Persist via Archi's native feature mechanism (`labelExpression` feature) so it is
  interoperable with the Archi UI.
- Support literal text incl. newlines and the tokens Archi recognises (`${name}`,
  `${documentation}`, `${property:KEY}`), so `${name}\n${property:protocol}` works and the
  protocol detail can live in a custom property.

### Example

```json
// manage_view_content update_connection
{ "view_id": "…", "items": [
  { "relationship_id": "…",
    "label_expression": "${name}\nTLS1.3; HTTPS; API Call" }
]}
```

## Acceptance criteria

- Can set, read back, and clear a label expression on both a connection and a figure via MCP.
- Supports `${name}` and literal text including newlines; value survives save/reload and is
  editable in the Archi UI.
- `get_connection` / `layout` include the expression when present.

## Implementation notes

- Same "absent = don't touch, null/empty = clear" semantics as the reset-colour request
  (see `reset-figure-colors-to-default.md`) — design both together.
- Consolidated `manage_*` tools are pure routing; the mutation lives in the per-operation
  tool classes (`UpdateConnectionTool`, `UpdateFigureAppearanceTool`, etc.).

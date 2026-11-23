# Feature request: allow a relationship to be drawn more than once on a view

**Plugin:** archi-mcp (Archi MCP server) 3.0.0
**Type:** enhancement to `manage_view_content add_connection` (+ lookups that assume one connection)
**Priority:** medium — blocks faithful view replay; silently drops lines

## Summary

Archi allows the same relationship to appear as several connections on one view — e.g. when
an element is shown twice on a view, the relationship can be drawn from each figure. The
MCP's `add_connection` refuses this: if any connection for the relationship already exists
on the view it returns the existing one with `already_exists: true` and draws nothing, even
when a different `connection_id`, `source_figure_id` and `target_figure_id` are supplied.

## Observed

After merging parallel relationships during duplicate consolidation, 6 connections could not
be redrawn (Sample Knowledge graph ×3, Detailed layered ×2, migrated - Process Setup ×1).
Each call reported success-shaped results with `already_exists: true`, so a batch with
`results: "summary"` shows `failed: 0` while the lines are missing.

Source: `BulkAddRelationshipsToViewTool` calls
`ModelAccessor.findConnectionByRelationshipId(view, relationshipId)` and short-circuits on
any hit, regardless of the requested endpoints.

## Proposed change

- Treat a connection as "already existing" only when the **same relationship already
  connects the same source and target figures**. A different figure pair is a new drawing.
- If `connection_id` is supplied and is unused, always create (subject to the figure-pair
  rule above).
- Optional explicit control: `on_existing: "reuse" | "add" | "error"` (default `reuse`,
  meaning "reuse only an identical figure pair").
- `results: "summary"` must count `already_exists` items separately
  (`{succeeded, failed, reused}`) so silent no-ops are visible.

### Related lookups that assume one connection per relationship

| Call | Change |
|---|---|
| `update_connection` / `remove_connection` by `relationship_id` | Error if more than one connection matches ("ambiguous — pass connection_id"), instead of acting on the first. |
| `inspect_view get_connection` by `relationship_id` | Same ambiguity rule. |
| `inspect_view connections` (filtered by relationship) | Already returns a list — confirm it returns all. |
| `manage_appearance set_style` by `relationship_id` | Same ambiguity rule. |

## Acceptance criteria

- With element E shown twice on a view, a relationship E→F can be drawn from both E
  figures; `inspect_view definition` returns two connections with distinct IDs.
- Replaying an `inspect_view definition` that contains duplicate drawings recreates all of
  them.
- Adding a connection for the same relationship and the same figure pair is still reused,
  not duplicated.
- Relationship-ID-addressed edits fail with a clear message when ambiguous.

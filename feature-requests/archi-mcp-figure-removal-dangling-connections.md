# Bug + feature: figure removal leaves dangling connections; add an element `merge` operation

**Plugin:** archi-mcp (Archi MCP server) 3.0.0
**Type:** bug fix (element delete, remove_figure) + new operation (`manage_elements merge`)
**Priority:** high — corrupts views silently; read-back tools report the view as correct

## Summary

Deleting an element (or removing a figure) whose figure **contains nested child figures**
removes those children from the view without detaching *their* connections. Any connection
between a nested child and a figure outside the removed subtree stays attached to the
surviving figure and points at a detached figure. Archi then renders it as a line to a
phantom point (near the diagram origin), and the canvas grows to include that point.

Merging duplicate elements is the workflow that hits this hardest — so the same request
asks for a first-class `merge` operation that does the re-pointing server-side.

## Observed (ConceptualViews model, exact-duplicate merge of 70 clusters)

- Deleted a duplicate `Mantle` ApplicationComponent whose figure on "High Level Process"
  nested 17 interface figures (Define, Ideate, Build, …). Re-created the survivor's figure
  and children with the original figure IDs, then re-added all connections.
- 9 actor→interface connections rendered as lines converging on a point above the diagram;
  canvas height went from 584 to 689 px. Same on "Mantle Integration into Keymaker" (4).
- `inspect_view definition` and `connections` reported the view as **identical** to the
  pre-merge backup — the stale connections are not reachable by those tools.
- When the surviving end was the **source**, `remove_connection` by ID cleared it. When the
  surviving end was the **target**, the stale connection was invisible to every tool; the
  only fix was `remove_figure` on the target figure, then re-adding it and its connections.

## Root cause (source references)

| Code path | Problem |
|---|---|
| `DeleteElementTool.execute` | Disconnects only `fig.getSourceConnections()` / `getTargetConnections()` of the element's own figure, then `dmc.getChildren().remove(fig)`. Connections of **descendant** figures are never disconnected. |
| `DeleteElementTool` | Uses `ModelAccessor.findFigureByElementId(view, id)`, which is `findFirst()`. If an element is shown **twice on one view**, only the first figure is removed; the second remains, referencing a deleted element. |
| `RemoveFigureFromViewTool.execute` | Same descendant gap: deletes only `resolved`'s own connections before `EcoreUtil.delete(resolved, true)`. |
| `RemoveFigureFromViewTool.undo` | Re-adds the figure but not the connections it deleted — undo is lossy. |
| `ModelAccessor.findConnectionByRelationshipId` | Walks `getSourceConnections()` of figures in the diagram tree only, so a connection whose source is detached cannot be found (why target-side ghosts are invisible). |

## Proposed fix

1. On element delete and `remove_figure`, collect the figure **and all descendants**
   (`eAllContents`), gather every source/target connection of every figure in that set,
   disconnect/delete them, then remove the figure. Capture all of it for undo.
2. Element delete must remove **every** figure for the element on every view, not the first.
3. Optional safety net: a `validate_model` check `dangling_connections` that reports
   connections whose source or target figure is not contained in its view.

## New operation: `manage_elements merge`

```json
{ "operation": "merge",
  "items": [ { "survivor_id": "...", "duplicate_ids": ["...", "..."],
               "merge_properties": "union|survivor|error",
               "merge_documentation": "survivor|concat|error",
               "on_parallel_relationship": "collapse|keep",
               "dry_run": false } ] }
```

Behaviour, per item, as one undoable command:

- Re-point every relationship with a duplicate as source/target to the survivor, **keeping
  relationship IDs**. Relationships that become self-loops are deleted. Relationships that
  become parallel to an existing one (same type, source, target, access type) are
  collapsed when `on_parallel_relationship: collapse` — connections are moved to the
  surviving relationship first.
- On every view, set `archimateElement` of each duplicate's figure to the survivor **in
  place** (`IDiagramModelArchimateObject.setArchimateElement`). Figure IDs, bounds, nesting,
  styles and connections are untouched — no remove/re-add, so no dangling connections.
- Merge properties/documentation per the chosen policy; `error` reports conflicts instead.
- Delete the duplicates.
- `dry_run` returns the impact without mutating: relationships re-pointed, collapsed,
  dropped; figures retargeted per view; property/documentation conflicts.

`BulkUpdateElementsTool` already does the in-place re-pointing for type changes
(`rel.setSource/setTarget`, `fig.setArchimateElement`), so most of the mechanics exist.

## Acceptance criteria

- Deleting an element whose figure nests children with external connections leaves no
  connection in the model whose source or target figure is outside its view.
- Exporting a view after such a delete + re-add produces the same canvas size as before.
- `merge` of N duplicates into a survivor leaves every affected view's
  `inspect_view definition` identical to before, except `element_id` on retargeted figures.
- `merge` and the delete fix are each a single undo step that fully restores the model.
- An element shown twice on a view is fully removed from that view by element delete.

# Spec: figure removal leaves no dangling connections; `manage_elements merge`

Source request: `feature-requests/archi-mcp-figure-removal-dangling-connections.md`
Target version: 3.0.0 (unreleased, branch `feat/v3-bulk-authoring`)
Sibling spec: `docs/specs/multiple-connections-per-relationship/`. It owns several shared
rules: lookups by `relationship_id` / `element_id` when more than one drawing matches, and
the `results: "summary"` shape `{count, succeeded, reused, skipped, failed, errors}`.

## Terms

- **Figure subtree**: a figure plus every figure, group and note nested inside it, at any depth.
- **Attached connection**: a connection whose source or target is a figure in the subtree.
  This includes connections attached to an attached connection (connection-to-connection).
- **Dangling connection** (also called a ghost): a connection that has any of these:
  - a null source or target;
  - a source or target that is not contained in the connection's view (it was detached, or it
    belongs to another view);
  - the connection itself is not contained in the view, but it is still attached to a figure
    that is on the view (a target-side ghost).

## Defect analysis (claims in the request, checked against the source)

| # | Claim | Verdict |
|---|---|---|
| D1 | `DeleteElementTool` disconnects only the element figure's own connections, then removes the figure, so connections of nested descendants stay attached to surviving figures. | **Confirmed.** `dmc.getChildren().remove(fig)` takes the subtree out of the view, but it never disconnects descendant connections. A connection is *contained* by its source figure, so: (a) when the descendant is the source, the connection leaves with the subtree but stays in the surviving target's `targetConnections` (a target-side ghost); (b) when the descendant is the target, the connection stays on the surviving source and points at a detached figure. |
| D2 | Element delete uses `findFigureByElementId` (first match), so an element shown twice on one view keeps its second figure. | **Confirmed.** The figure left behind references a deleted element and deleted relationships. Related: `figures_to_remove` / `figures_removed` count *views*, not figures. `ModelAccessor.findAllFiguresByElementId` already exists and is not used. |
| D3 | `RemoveFigureFromViewTool.execute` has the same descendant gap. | **Partly wrong about the mechanism; the outcome is still broken.** It calls `EcoreUtil.delete(resolved, true)`, which also clears references *to* every object in the subtree. As we read `EcoreUtil.delete`: (a) connections whose source is a descendant are dropped from the surviving targets' `targetConnections`, so this path leaves no target-side ghost; (b) connections whose target is a descendant stay on the surviving source with their **target set to null**. So the connection still dangles, with a null end instead of a detached one. The red tests have to confirm this. |
| D4 | `RemoveFigureFromViewTool.undo` re-adds the figure but not the connections it deleted. | **Confirmed, and it's worse than reported.** Undo doesn't restore the figure's own connections. Connections whose target was a descendant stay broken after undo, because `EcoreUtil.delete` nulled that target. |
| D5 | `findConnectionByRelationshipId` walks only `getSourceConnections()` of figures in the view, so a connection whose source is detached can't be found. | **Confirmed.** `findConnectionById` has the same blind spot. This is why `remove_connection` cleared source-side ghosts but couldn't see target-side ones. |

Found during verification (not in the request):

- D6: when a connection is deleted, connections attached *to it* (connection-to-connection)
  are never removed, on either path. They dangle the same way.
- D7 (out of scope, recorded): element delete removes relationships whose ends are the
  element. It does not remove relationships whose end is one of *those* relationships
  (relationship-on-relationship). In the model, those are left pointing at a deleted relationship.

---

## US-1 Removing figures never leaves dangling connections

*As a modeller using an MCP client, I want deleting an element or removing a figure to take
every connection of the removed figures with it, so that my views never render lines to
phantom points and the read-back tools tell the truth.*

### Scenarios

**S1.1 Element delete: nested child with an external connection (child is source)**
- Given view V with a figure of element P that nests a figure of element C, a figure of
  element X outside P, and a connection C→X for relationship R1
- When `manage_elements delete` is called for P
- Then the figures of P and C are no longer on V
- And X has no source or target connections
- And no connection anywhere in the model is dangling

**S1.2 Element delete: nested child with an external connection (child is target)**
- Given the same view, but the connection is X→C for relationship R2
- When `manage_elements delete` is called for P
- Then X has no source or target connections
- And no connection anywhere in the model is dangling

**S1.3 Element delete: deeply nested child**
- Given P nests group G, which nests a figure of C, and C has a connection to X outside P
- When P is deleted
- Then no connection anywhere in the model is dangling

**S1.4 Element delete: element shown twice on one view**
- Given view V shows element P twice (figures F1 and F2), each with a connection to X
- When P is deleted
- Then neither F1 nor F2 is on V, and X has no connections
- And `figures_removed` is 2

**S1.5 Element delete: shown on several views**
- Given P has one figure on V1 and two on V2
- When P is deleted with `dry_run: true`
- Then `figures_to_remove` is 3, and `affected_view_ids` lists V1 and V2 once each
- And `connections_to_remove` counts each distinct connection that would be removed once,
  including descendants' connections
- And the model is unchanged and not saved

**S1.6 Element delete is one complete undo step**
- Given the model from S1.1 + S1.2 (connections C→X and X→C, with P nesting C)
- When P is deleted and the command is undone once
- Then P, its relationships, and its figure are back, with the figure at the same parent and
  index and the C figure still nested in it
- And every removed connection is back in its source's `sourceConnections` and its target's
  `targetConnections`

**S1.7 `remove_figure`: nested child with external connections**
- Given view V with figure P nesting figure C, X outside P, and connections C→X and X→C
- When `manage_view_content remove_figure` is called for P's figure
- Then P and C are no longer on V, X has no connections, and no connection is dangling
- And no connection has a null source or target
- And the elements and relationships are still in the model

**S1.8 `remove_figure` is one complete undo step**
- Given the setup of S1.7
- When the figure is removed and the command is undone once
- Then P is back at its original parent and index, with C still nested
- And connections C→X and X→C are attached at both ends again

**S1.9 Connection attached to a removed connection**
- Given figures A and B with a connection K (A→B), and a figure X with a connection L from X to K
- When A's figure is removed (or A is deleted)
- Then K and L are both gone and no connection is dangling

**S1.10 A ghost connection can be found and removed**
- Given a view where a connection's source is detached but its target figure T is on the
  view (a target-side ghost left by an earlier plugin version)
- When `manage_view_content remove_connection` is called with that connection's `connection_id`
- Then the connection is found and removed, and T has no target connections

### Functional requirements

- **FR-1** Element delete removes **every** figure of the element on **every** view, including
  figures nested at any depth and several figures on the same view.
- **FR-2** When a figure is removed by element delete or `remove_figure`, its whole subtree
  leaves the view. This is unchanged, and it matches Archi.
- **FR-3** Removing a figure subtree also removes every connection attached at either end to
  any figure in it. It also removes, transitively, every connection attached to a removed
  connection. Each connection is removed once, even when both of its ends are removed.
- **FR-4** After element delete or `remove_figure`, no connection in the model is dangling.
- **FR-5** Element delete is a single undoable command. Undo restores all of these:
  - the element;
  - its relationships, in the same folder and at the same index;
  - every removed figure, at the same parent and index;
  - every removed connection, attached at both ends.
- **FR-6** `remove_figure` is a single undoable command. Undo fully restores its figure and
  connections, as in FR-5.
- **FR-7** Element-delete `dry_run` and result counts are true counts:
  - `figures_to_remove` / `figures_removed` is the number of the element's figures, not the
    number of views;
  - `connections_to_remove` is the number of distinct connections FR-3 would remove;
  - `affected_view_ids` lists each view once.
- **FR-8** Looking up a connection by `connection_id` or `relationship_id` on a view finds
  connections attached at **either** end to a figure on that view. That way a connection whose
  source is detached can still be found and removed. What happens when several connections
  match a `relationship_id` is decided by the sibling spec.
- **FR-9** `remove_figure` by `element_id` follows the sibling spec's figure-ambiguity rule. If
  the element is shown more than once on the view, the call is an error that asks for
  `figure_id`. The sibling spec owns and tests this rule for every `element_id` figure lookup.
  This spec does not redefine it. Element delete is not affected, because FR-1 removes every
  figure.

---

## US-2 Merge duplicate elements

*As a modeller cleaning up a model, I want to merge duplicate elements into one surviving
element on the server, so that every relationship and every figure on every view keeps
pointing at the right element, with nothing removed and re-added. I want conflicting
values shown to me like a git diff, so that I choose what survives instead of losing it
silently.*

### Request

```json
{ "operation": "merge",
  "items": [ {
    "survivor_id": "S", "duplicate_ids": ["D1", "D2"],
    "collapse": false,
    "resolve": {
      "properties": { "owner": "finance", "tag": ["a", "b"] },
      "documentation": "...",
      "relationships": { "<kept relationship id>": { "properties": {...}, "documentation": "...", "name": "..." } }
    },
    "dry_run": false } ] }
```

### The diff

Merge always computes a **diff** before changing anything:

- `conflicts`: each entry is one of these:
  - `{property: "<key>", survivor: [<values>], duplicates: {"D1": [<values>], ...}}`.
    Archi allows a key to repeat, so each side is the **ordered list** of that key's values on
    that concept. `survivor` is `[]` when the survivor lacks the key. `duplicates` lists only
    the duplicates that have the key. The key is in conflict when the non-empty lists are not
    all equal.
  - `{documentation: {survivor: <text|null>, "D1": <text>, ...}}`. This appears when the
    survivor and duplicates have more than one distinct non-empty documentation text.
  - For collapsed relationship groups (only when `collapse: true`), the same two shapes plus
    `{name: {survivor: <name>, "<collapsed id>": <name>, ...}}`, all with
    `relationship: "<kept id>"` added. Here `survivor` means the kept relationship, and the
    collapsed relationships' IDs are the other keys. A name is in conflict when there is more
    than one distinct non-empty name.
- `parallel_relationships`: `[{kept: "<id>", parallel: ["<id>", ...]}]`, one entry per group of
  relationships that end up with the same type, source, target and type-specific attributes
  after re-pointing (FR-19).

A value only one side has is not a conflict. It is filled in automatically.

### Scenarios

**S2.1 Relationships re-pointed, IDs kept**
- Given survivor S, duplicate D, and relationships D→Y (R1) and Z→D (R2)
- When `manage_elements merge` is called with `survivor_id: S, duplicate_ids: [D]`
- Then R1 is S→Y and R2 is Z→S, with the same IDs, types and properties
- And D no longer exists in the model
- And the result reports `relationships_repointed: 2`

**S2.2 Figures retargeted in place**
- Given D has a figure F on view V, and another figure on view W. F has a figure ID, bounds,
  a parent, a style, nested child figures and connections.
- When D is merged into S
- Then F is the same object, with the same ID, bounds, parent, style, children and
  connections, and it now shows S
- And the result reports `figures_retargeted: 2` and lists V and W in `affected_view_ids`

**S2.3 View definitions are unchanged apart from `element_id`**
- Given views affected by a merge without `collapse`
- When the merge completes
- Then for each affected view, the `figures` and `connections` of `inspect_view definition`
  are identical to those before the merge, except that `element_id` on retargeted figures
  is now S's ID

**S2.4 Relationships that would become self-loops are deleted**
- Given relationships S→D (R3) and D1→D2 (R4, between two duplicates), each drawn on view V
- When D, D1 and D2 are merged into S
- Then R3 and R4 and all their connections are deleted
- And the result lists them in `relationships_deleted`
- And no connection is dangling

**S2.5 Non-conflicting values fill in**
- Given S has `owner=A` and empty documentation, and D has `tier=1` and documentation "Doc D"
- When D is merged into S
- Then S has `owner=A`, then `tier=1` (appended), and its documentation is "Doc D"

**S2.6 Unresolved conflicts block the merge and return the diff**
- Given S has `owner=sales` and documentation "Doc S", and D1 has `owner=finance` and
  documentation "Doc D1"
- When D1 is merged into S without `resolve`
- Then the item has `error: "unresolved conflicts"` and `conflicts`:
  `[{property: "owner", survivor: ["sales"], duplicates: {D1: ["finance"]}},
  {documentation: {survivor: "Doc S", D1: "Doc D1"}}]`
- And the model is unchanged and not saved

**S2.7 Conflicts resolved on re-send**
- Given the setup of S2.6
- When the merge is re-sent with
  `resolve: {properties: {owner: "finance"}, documentation: "Doc merged"}`
- Then the merge happens, and S has `owner=finance` and documentation "Doc merged"

**S2.8 Two duplicates disagree while the survivor lacks the key**
- Given S has no `tier`, D1 has `tier=1`, and D2 has `tier=2`
- When D1 and D2 are merged into S without `resolve`
- Then `conflicts` contains `{property: "tier", survivor: [], duplicates: {D1: ["1"], D2: ["2"]}}`
  and nothing changes

**S2.8a Repeated keys are compared as ordered lists**
- Given S has `tag=a`, `tag=b` and D has `tag=a`
- When D is merged into S without `resolve`
- Then `conflicts` contains `{property: "tag", survivor: ["a","b"], duplicates: {D: ["a"]}}`
- When it is re-sent with `resolve: {properties: {tag: ["a","b","c"]}}`
- Then S has exactly three `tag` properties, `a`, `b` and `c` in that order, at the position of
  the first original `tag`
- And a single string resolution, for example `tag: "a"`, leaves exactly one `tag=a`

**S2.9 Resolve must match a conflict, and a stale resolve returns the current diff**
- Given a `resolve` that names a property key, documentation, relationship or field that is
  not in the current diff (for example because the model changed after a dry run)
- When merge is called
- Then the item has an `error` naming the unmatched entry, plus the full current `conflicts`
  and `parallel_relationships`, so the caller can correct `resolve` and re-send in one step
- And nothing changes

**S2.9a Differing duplicate names are reported, not blocked**
- Given S is named "Mantle" and D is named "Mantle (old)"
- When D is merged into S
- Then S keeps the name "Mantle", the merge is not blocked, and the result contains
  `duplicate_names: {D: "Mantle (old)"}`

**S2.10 Parallel relationships are kept and reported by default**
- Given S→Y (R5) and D→Y (R6), both ServingRelationship
- When D is merged into S
- Then R5 and R6 both exist (both are now S→Y)
- And `parallel_relationships` contains `{kept: R5, parallel: [R6]}`
- And the merge is not blocked

**S2.11 Collapse moves connections onto the kept relationship**
- Given the setup of S2.10. On view V, R6 is drawn as connection K6 between figures FD and FY,
  and R5 is not drawn on V.
- When D is merged into S with `collapse: true`
- Then R6 is deleted
- And K6 is the same object, with the same ID and bendpoints, and it now shows R5 between FD
  (now showing S) and FY
- And no connection is dangling

**S2.12 Collapse drops a moved connection that would double an existing one**
- Given on view W, connection K5 shows R5 and connection K6 shows R6, both between the same
  figures FS and FY. Well-formed views can't reach this state, because before the merge a
  figure shows exactly one element. The rule guards inconsistent views, and the test builds
  this state directly.
- When D is merged into S with `collapse: true`
- Then K6 is removed, K5 remains, and no connection is dangling
- And the result counts K6 in `connections_removed`

**S2.13 Collapse conflicts go through resolve**
- Given R5 has documentation "a" and name "serves", R6 has documentation "b" and name
  "provides", and `collapse: true`
- When the merge is called without a matching resolve
- Then `conflicts` contains `{relationship: R5, documentation: {survivor: "a", R6: "b"}}` and
  `{relationship: R5, name: {survivor: "serves", R6: "provides"}}`, and nothing changes
- When it is re-sent with `resolve: {relationships: {R5: {documentation: "a", name: "serves"}}}`
- Then R5 keeps "a" and "serves", and R6 is collapsed into it

**S2.14 Dry run returns the diff and the impact**
- Given any of the setups above
- When `dry_run: true`
- Then the result has `dry_run: true`, the diff (`conflicts`, `parallel_relationships`),
  and the impact counts a real merge would report
- And the result is not an error, even when there are unresolved conflicts
- And the model is unchanged and not saved

**S2.15 One undo step**
- Given a merge that re-points, deletes self-loops, retargets figures, fills and resolves
  properties, and collapses a parallel pair
- When the merge is executed and the command is undone once
- Then the model is exactly as before: the duplicates and the collapsed and deleted
  relationships are back in their folders, every relationship has its original ends, every
  connection has its original relationship and attachments, every figure shows its original
  element, and S's and the kept relationships' properties and documentation are restored

**S2.16 Invalid requests change nothing**
- Given any of these:
  - a survivor or duplicate ID that does not exist;
  - a survivor listed in `duplicate_ids`;
  - an empty or repeated `duplicate_ids`;
  - a duplicate whose type differs from the survivor's
- When merge is called
- Then that item returns an error naming the problem, and the model is unchanged
- And other items in the same batch are still processed

### Functional requirements

- **FR-10** `manage_elements` accepts `operation: "merge"` with items `{survivor_id,
  duplicate_ids: [id, ...], collapse?: false, resolve?, dry_run?}`. Each item is applied
  atomically as one undoable command.
- **FR-11** These are item errors, and nothing is changed:
  - the survivor or any duplicate is missing or is not an element;
  - `duplicate_ids` is empty, contains the survivor, or repeats an ID;
  - any duplicate's element type differs from the survivor's type (cross-type merge is not
    supported).
- **FR-12** Every relationship whose source or target is a duplicate is re-pointed to the
  survivor. It keeps its ID, type, name, properties and folder.
- **FR-13** A relationship whose source and target would both be the survivor after
  re-pointing is deleted, together with all of its connections on every view (with FR-3
  applied). This covers survivor↔duplicate and duplicate↔duplicate relationships. A
  relationship that was already a survivor self-loop is untouched.
- **FR-14** Every figure of a duplicate, on every view, is retargeted to the survivor in
  place. Its figure ID, bounds, parent, children, style and connections are unchanged.
- **FR-15** Property conflicts: for each key, take the ordered list of that key's values on each
  concept (the survivor and each duplicate). The key is in conflict when the non-empty lists
  are not all equal. For non-conflicting keys:
  - a key the survivor lacks is appended to the survivor with its value list, in duplicate
    order;
  - a key the survivor has keeps its values.
- **FR-16** Documentation conflicts: documentation is in conflict when the survivor and
  duplicates have more than one distinct non-empty text between them. Otherwise, if the
  survivor's documentation is empty, it takes the single non-empty text.
- **FR-17** Resolving conflicts:
  - `resolve.properties` maps each conflicting key to its final value. A string gives the key
    exactly one property with that value. An array of strings gives the key exactly those
    properties, in that order. Either way, the properties replace every existing property with
    that key on the survivor, at the position of the first existing one (or appended if there
    was none).
  - `resolve.documentation` gives the final documentation.
  - If any conflict is unresolved, the item has `error: "unresolved conflicts"`, includes the
    full diff, and changes nothing.
  - If a `resolve` entry matches no conflict in the current diff, the item has an `error`
    naming that entry. It also includes the full current `conflicts` and
    `parallel_relationships`, and nothing changes.
- **FR-18** The survivor's name, specialization and folder are unchanged. Duplicates'
  specializations are not carried over. A duplicate name that differs from the survivor's
  never blocks the merge. It is listed in `duplicate_names: {<id>: <name>}`, for information
  only.
- **FR-19** Parallel relationships are relationships with the same type, the same source, the
  same target, and the same type-specific attributes after re-pointing. The type-specific
  attributes are `access_type` (Access), `influence_strength` (Influence) and `directed`
  (Association), compared through the attribute helper from the query-relationships spec.
  Relationships that are parallel are grouped. Every group contains at least one relationship
  that was re-pointed. Merge never touches a relationship it did not re-point: a group's
  `parallel` list holds only re-pointed relationships, so relationships that were already
  parallel before the merge are neither listed nor collapsed.
  Each group has one **kept** relationship, chosen in this order:
  1. the relationship that was not re-pointed, if there is one;
  2. otherwise, the relationship of the duplicate that comes first in `duplicate_ids`;
  3. otherwise, the relationship that comes first in model order.

  Groups are listed in `parallel_relationships`. Without `collapse` they are all kept, and
  they don't block the merge.
- **FR-20** With `collapse: true`, the other relationships in each group are merged into the
  kept one, using the FR-15/16/17 rules. Their properties, documentation and names are
  compared with the kept relationship's. A name conflict is more than one distinct non-empty
  name; otherwise an empty kept name takes the single non-empty name. Conflicts are reported
  with `relationship: <kept id>` and resolved through `resolve.relationships[<kept id>]`, which
  takes `{properties?, documentation?, name?}`. Then the collapsed relationships are deleted.
- **FR-21** On collapse, each connection of a collapsed relationship is moved to the kept
  relationship. It is the same connection object, with the same ID, bendpoints and style. The
  exception: if, after retargeting, the kept relationship is already drawn on that view
  between the exact same source figure and target figure, the moved connection is removed
  instead, with FR-3 applied. A relationship whose source or target is a collapsed
  relationship is re-pointed to the kept relationship. Moving a connection can leave one
  relationship drawn several times on a view, which the sibling spec allows.
- **FR-22** The duplicates are deleted from the model.
- **FR-23** The result of a successful item is `{survivor_id, merged: [ids],
  relationships_repointed: n, relationships_deleted: [ids], relationships_collapsed: [ids],
  connections_moved: n, connections_removed: n, figures_retargeted: n,
  affected_view_ids: [ids], parallel_relationships: [...], duplicate_names: {...}}`.
  - `dry_run: true` returns the same fields, plus `dry_run: true` and `conflicts`. It is never
    an item error because of conflicts, and it neither changes nor saves the model.
  - In the `results: "summary"` shape owned by the sibling spec, an item blocked by unresolved
    conflicts counts as `failed`. A dry run counts as `succeeded`.
- **FR-24** After a merge, no connection in the model is dangling (FR-4 holds).
- **FR-25** The `manage_elements` description and its `operation` enum include `merge`, its
  item fields, and the diff/resolve round trip.

---

## US-3 Find ghost connections already in a model

*As a modeller with a model edited by an earlier plugin version, I want `validate_model` to
list dangling connections, so that I can find and remove ghosts that no other read tool
shows.*

### Scenarios

**S3.1 Each kind of ghost is reported**
- Given a model with each of these on view V:
  - a connection whose source figure was detached and whose target is on V (target-side ghost);
  - a connection on V whose target figure was detached;
  - a connection on V with a null target;
  - a connection on V whose target is a figure on view W
- When `validate_model` is called
- Then `dangling_connections` has one entry for each, with `view_id`, `view_name`,
  `connection_id`, `relationship_id` (null for non-ArchiMate connections), and `problems`.
  `problems` is drawn from `source_missing`, `target_missing`, `source_detached`,
  `target_detached`, `source_other_view`, `target_other_view` and `connection_detached`.

**S3.2 Clean model**
- Given a model with no ghosts
- When `validate_model` is called
- Then `dangling_connections` is an empty array

**S3.3 Reported ghosts can be removed**
- Given a ghost reported in S3.1
- When `manage_view_content remove_connection` is called with its `view_id` and `connection_id`
- Then it is removed, and a second `validate_model` no longer reports it

### Functional requirements

- **FR-26** `validate_model` always returns `dangling_connections`, a list of every dangling
  connection (see Terms) reachable from any view through the source or target connections of a
  diagram object, or of a connection. Each connection is listed once, under the view it was
  found from.
- **FR-27** Every entry `{view_id, view_name, connection_id, relationship_id, problems: [...]}`
  can be removed with `remove_connection` by `connection_id` (via FR-8).
- **FR-28** The `validate_model` description mentions the check.

---

## Out of scope

- Showing ghosts in `inspect_view definition` / `connections`. `validate_model` (US-3) is
  where they are found.
- D7: relationships whose end is a deleted relationship. This applies to element delete and to
  merge's self-loop deletion. Collapse is the exception: it does re-point them (FR-21).
- Exported-image canvas size (acceptance criterion 2). It follows from FR-4 and is verified
  live, because `ExportViewAsImageTool` needs SWT.
- Merging relationships, as opposed to elements, outside a merge's collapse.

## Decisions recorded (from the user, 2026-11-23)

- `remove_figure` by `element_id` is ambiguous when the element is shown twice, so it is an
  error. The sibling spec owns this rule (FR-9).
- Merge conflicts work like a git diff: there are no policy modes. Unresolved conflicts block
  the merge, and the caller re-sends with `resolve`.
- Parallel relationships are kept and reported by default. `collapse: true` collapses them.
- Cross-type merge is rejected.
- `validate_model` gets the `dangling_connections` check.

- Parallel means the same type-specific attributes as well (`access_type`,
  `influence_strength`, `directed`) (FR-19).
- `resolve` is strict, and the error for an unmatched entry carries the current diff (FR-17).
- Relationship names go through the diff on collapse. Differing element names are reported
  only, in `duplicate_names` (FR-18, FR-20).
- Repeated property keys are compared as ordered value lists. A resolution is a single value
  or a list of values (FR-15, FR-17).
- `DeleteElementTool` and merge use the list figure lookup. This spec owns that code change.

## Open questions

None.

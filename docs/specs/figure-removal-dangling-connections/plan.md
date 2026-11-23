# Plan: figure removal leaves no dangling connections; `manage_elements merge`; ghost check

Spec: `docs/specs/figure-removal-dangling-connections/spec.md`
Source root: `za.co.jesseleresche.archi.mcp/src/za/co/jesseleresche/archi/mcp`
Test root: `za.co.jesseleresche.archi.mcp/src/test/java/za/co/jesseleresche/archi/mcp`
Sibling plan: `docs/specs/multiple-connections-per-relationship/plan.md` (see "Shared touch points").

## Files

| File | Change | FRs |
|---|---|---|
| `util/ConnectableRemoval.java` (new) | One shared, undoable "remove these figures/connections and everything attached" helper. | FR-2..6, FR-13, FR-21 |
| `util/ModelAccessor.java` | Add a public `connectionsOnView(view)` walk that covers either end; the connection lookups filter it. | FR-8, FR-26 |
| `tools/DeleteElementTool.java` | Replace all three `findFigureByElementId` calls with the existing `findAllFiguresByElementId`; use `ConnectableRemoval`; report true counts; delete the hand-rolled capture. | FR-1..5, FR-7 |
| `tools/RemoveFigureFromViewTool.java` | Replace the `EcoreUtil.delete` calls and the lossy undo with `ConnectableRemoval`. The figure lookup by `element_id` follows the sibling spec's ambiguity rule. | FR-2..4, FR-6, FR-9 |
| `tools/MergeElementsTool.java` (new, per-operation class) | Owns the diff, the merge `Command`, `syncExec` and the save. | FR-10..24 |
| `tools/ManageElementsTool.java` | Route `merge` → `delegatePerItem(merge, items)`. Add it to the enum, the description and the unknown-op message. | FR-10, FR-25 |
| `tools/ValidateModelTool.java` | Always add `dangling_connections`. | FR-26..28 |
| `test/.../testing/TestModels.java` | Add `commandStack()` and `assertNoDanglingConnections()`. | — |

Not touched: `ConsolidatedTool` (its summary is owned by the sibling spec) and
`DeleteConnectionTool` (it gets FR-8 through `ModelAccessor`).

`MergeElementsTool` is a new file because CLAUDE.md says consolidated tools only route; the
per-operation classes own mutation and `syncExec`.

## Algorithm notes

### ConnectableRemoval (shared helper)

The helper is built from a list of roots. A root is either a diagram object (figure, group or
note) or a connection. It runs inside a caller's `Command` and never saves.

1. **Collect.** Walk `eAllContents()` of each object root and gather every `IConnectable`,
   including connections contained in it. Then take the closure: add each gathered
   connectable's `getSourceConnections()` and `getTargetConnections()` to a
   `LinkedHashSet<IDiagramModelConnection>`, and keep adding connections attached to
   connections until nothing new appears (D6). Using a set means each connection is counted
   once (FR-3, FR-7).
2. **Top-level roots only.** Drop any root object that has another root as an ancestor
   (`EcoreUtil.isAncestor`). Record `(parent, index)` for each remaining root at the moment it
   is removed, in removal order.
3. **execute()**
   - `disconnect()` every collected connection.
   - Remove each top-level root from its parent's `getChildren()`.
   - Don't use `EcoreUtil.delete`: it nulls references that undo needs (D3, D4).
4. **undo()**
   - Re-add the roots in **reverse** removal order at their recorded indexes.
   - Call `reconnect()` on every collected connection. Archi's `disconnect()` keeps the
     connection's `source`/`target`, and the current `DeleteElementTool` undo already relies
     on that.
5. Expose `connectionCount()` and `rootCount()`. Collection happens in the constructor, so a
   dry run can build a removal and read its counts without executing it.

`ponytail:` the closure is a fixed-point loop over at most dozens of connections per removal.
That's fine at view scale.

Disconnecting explicitly is what takes descendant-sourced connections out of surviving
targets' `targetConnections` (D1a).

Alternative considered: Archi's `DiagramCommandFactory.createDeleteDiagramObjectCommand`
(editor bundle). It may not load on the plain-JVM test classpath, so it isn't the default.

### DeleteElementTool

- **What gets removed:** call the existing `ModelAccessor.findAllFiguresByElementId(view, id)`
  (line 239; no rename, no new method) on every view (FR-1). This spec owns switching
  `DeleteElementTool` (all three `findFigureByElementId` calls, at lines 98, 131 and 156) and
  `MergeElementsTool` onto that list lookup. Doing so implements the sibling's FR-12.
  - **Ordering constraint:** the sibling makes the single-figure `findFigureByElementId`
    throw when more than one figure matches. This `DeleteElementTool` rewrite must land
    before that change or in the same merge. Otherwise element delete would throw for an
    element shown twice on a view. Build one `ConnectableRemoval` over those figures plus **all** connections of the
  deleted relationships on every view, using the list form `findConnectionsByRelationshipId`.
- **Dry run:** build the removal but don't execute it. Report:
  - `figures_to_remove` = the number of the element's figures;
  - `connections_to_remove` = `connectionCount()`;
  - `affected_view_ids` = de-duplicated.
- **execute:** `removal.execute()`, then remove the relationships, then remove the element.
- **undo:** re-add the element, re-add the relationships (keep the existing folder/index
  capture), then `removal.undo()`.
- **Result keys:** `relationships_deleted` and `figures_removed` stay. `figures_removed` is now
  a true count.

### RemoveFigureFromViewTool

- **Figure lookup:** by `figure_id` as today. For `element_id`, use whatever the sibling spec
  introduces for figure ambiguity: it errors when the element appears more than once and asks
  for `figure_id` (FR-9). This plan doesn't add its own check. If this spec lands first, keep
  the current first-match lookup, and the sibling's change replaces it.
- **Command:** a `ConnectableRemoval` over `List.of(resolved)`. Execute and undo delegate to
  it. Remove the `EcoreUtil` import.

### ModelAccessor — shared touch point with the multi-connection spec

- `public static Collection<IDiagramModelConnection> connectionsOnView(view)` is a
  `LinkedHashSet` built in this order:
  1. for each object in `collectAllDiagramObjects(view)` order, its source connections, then
     its target connections;
  2. then the connections attached to those connections, until nothing new is added.

  It is public because `ValidateModelTool` uses it.
- `findConnectionById` and `findConnectionByRelationshipId` filter `connectionsOnView`. The
  sibling's `findConnectionsByRelationshipId` filters it too.
- **Guarantees the sibling relies on:** no duplicates, and the order is deterministic (it
  defines "first match").
- This spec doesn't change any lookup signature or behaviour. It only widens the walk.
- **Owned by the sibling spec:**
  - `findConnectionByRelationshipId` throwing when the match is ambiguous;
  - the list form `findConnectionsByRelationshipId`;
  - the `element_id` figure-ambiguity rule;
  - the `results: "summary"` shape `{count, succeeded, reused, skipped, failed, errors}`.
- Code in this spec never calls the single-result `findConnectionByRelationshipId`.
- **Merge order:** whichever spec lands second rebases onto the other's `ModelAccessor`
  change. spec-multiconn has agreed.

### MergeElementsTool

Item: `survivor_id`, `duplicate_ids`, `collapse?`, `resolve?`, `dry_run?`.

**Blocked results are returned, not thrown.** `delegatePerItem` turns an exception into a bare
`{error}`. A blocked merge has to carry the diff as well, so the tool returns
`{error: "unresolved conflicts", conflicts, parallel_relationships}`. The summary still counts
it as `failed`, because it has `error`. An unmatched `resolve` entry is returned the same way,
with its own error text. Only validation failures (FR-11) throw.

**Analysis phase** (reads only; shared by dry run, the blocked result and execute):

1. **Validate** (FR-11) before anything else.
2. **Post-merge ends:** `end' = survivor` if the end is a duplicate, otherwise `end`.
   - `repoint` = the relationships that have a duplicate at either end.
   - `selfLoops` = the relationships in `repoint` whose `src' == tgt' == survivor`.
3. **Parallel groups** (FR-19): bucket every relationship that isn't a self-loop in a `HashMap`
   keyed by `(eClass, src', tgt', attrs)`. `attrs` is the map filled by
   `RelationshipAttributes.read(rel, map)` from the query-relationships plan, and maps compare
   by value. That spec owns the helper. If this spec lands first, add the helper's `read` as
   specified in `docs/specs/query-relationships/plan.md` rather than writing a local
   comparison.
   - Keep buckets of size 2 or more that contain at least one relationship from `repoint`.
   - Choose the kept relationship in this order: the relationship not in `repoint`; otherwise
     the one whose duplicate comes first in `duplicate_ids`; otherwise model order.
   - This makes one pass over the relationships, O(R).
4. **Element diff** (FR-15/16/18):
   - Properties: for each concept, build a `LinkedHashMap<key, List<value>>`. A key is a
     conflict when the non-empty lists are not all `List.equals`.
   - Documentation: a conflict when there is more than one distinct non-empty text.
   - Non-conflicting fills: keys the survivor lacks, and documentation when the survivor's is
     empty.
   - `duplicate_names`: each duplicate whose name differs from the survivor's.
5. **Relationship diffs:** only when `collapse` is true. Do the same diff for each kept
   relationship against its collapsed ones, plus the name (FR-20).
6. **Resolve:**
   - Any `resolve` key, field or relationship ID that matches no conflict → **return** (don't
     throw) `{error: "resolve entry '<path>' matches no conflict", conflicts,
     parallel_relationships}`, so the caller gets the current diff in the same response.
   - Any conflict left unresolved → return the blocked result, unless this is a dry run.
   - Resolved property values: a string becomes `[value]`; an array becomes the list. Apply
     the list by removing every property with that key and inserting the new ones at the index
     of the first removed property, or appending them if the key had none. This is a few lines
     on the `getProperties()` EList; no helper is needed.
7. **Connections:**
   - `removal` collects every connection of each self-loop relationship on every view (the
     list form).
   - With `collapse`, check each connection of each collapsed relationship:
     - If the kept relationship already has a connection on that view between the same source
       and target figure (including one moved earlier in this item), add it to `removal`
       (FR-21 drop).
     - Otherwise add it to `moves`.
8. **Relationship-on-relationship** (collapse only): relationships whose source or target is a
   collapsed relationship go into `relRepoint`, pointed at the kept relationship.

   `ponytail:` they are not re-checked for new parallels. Add that if anyone hits it.

**Command** (one per item, so one undo step). Record the original before every change.

- **execute:**
  1. `removal.execute()`.
  2. For each connection in `moves`, call `setArchimateRelationship(kept)`.
  3. Re-point `relRepoint`.
  4. Remove `selfLoops` and the collapsed relationships from their folders.
  5. Re-point `repoint` with `setSource`/`setTarget`, the same mechanics as
     `BulkUpdateElementsTool` lines 245-259.
  6. For each figure, call `setArchimateElement(survivor)`.
  7. Apply properties and documentation to the survivor and the kept relationships, and
     names to the kept relationships. Record each old name for undo.
  8. Remove the duplicates from their folders.
- **undo** reverses each step in reverse order, and calls `removal.undo()` last.
- **Recording properties for undo:** snapshot each concept's `(IProperty, value)` list before
  any change. Undo clears the list, re-adds the snapshot objects and restores their values.
  This also covers values edited in place by `resolve`.
- **Recording ends:** record each relationship's original source and target once, before any
  re-pointing. Two duplicates can share a relationship (D1→D2).

After the command, save once with `ModelAccessor.saveModel` and return the FR-23 entry. A dry
run returns the analysis with `dry_run: true`; it runs no command and doesn't save.

Order matters: retarget figures in place before removing the duplicates, and never remove and
re-add a figure (FR-14, S2.3).

### ValidateModelTool

- **Walk:** for each view, call `ModelAccessor.connectionsOnView(view)`, skipping connections
  already reported (a global `Set`).
- **Problems for each end:**
  - `*_missing` if the end is null;
  - `*_detached` if `getDiagramModel()` of the end is null;
  - `*_other_view` if `getDiagramModel()` of the end is a different view.
- **The connection itself:** `connection_detached` if its own `getDiagramModel()` is not the
  view.
- **Reporting:** a connection is reported only if it has at least one problem. Each entry is
  `{view_id, view_name, connection_id, relationship_id|null, problems}`.
- The output always includes `dangling_connections`. v3.0.0 hasn't been released, so this is
  an additive key.

## FR → test mapping

| FR | Tests |
|---|---|
| FR-1 | `DeleteElementToolTest.removesEveryFigureIncludingSecondOnSameView`, `.removesNestedFigureOfElement` |
| FR-2, FR-3 | `DeleteElementToolTest.nestedChildSourceConnectionIsRemoved`, `.nestedChildTargetConnectionIsRemoved`, `.deeplyNestedChildConnectionIsRemoved`, `.connectionAttachedToRemovedConnectionIsRemoved`; `RemoveFigureFromViewToolTest.removesSubtreeAndAllAttachedConnections`, `.connectionAttachedToRemovedConnectionIsRemoved` |
| FR-4 | every FR-3 test ends with `models.assertNoDanglingConnections()`; `RemoveFigureFromViewToolTest.noConnectionLeftWithNullEnd` |
| FR-5 | `DeleteElementToolTest.undoRestoresFiguresConnectionsRelationshipsAndElement` |
| FR-6 | `RemoveFigureFromViewToolTest.undoRestoresFigureAndAllConnections` |
| FR-7 | `DeleteElementToolTest.dryRunCountsFiguresNotViews`, existing `.dryRunReportsImpactWithoutChanges` |
| FR-8 | `ModelAccessorTest.findsConnectionWhoseSourceIsDetached`, `.connectionsOnViewIsDistinctAndOrdered`, `DeleteConnectionToolTest.removesTargetSideGhostByConnectionId` |
| FR-9 | owned and tested by the sibling spec (`element_id` ambiguity); existing `RemoveFigureFromViewToolTest.removesNestedFigureByElementId` (single figure) still passes |
| FR-10, FR-25 | `ManageElementsToolTest.mergeIsRoutedAndListedInSchema` |
| FR-11 | `MergeElementsToolTest.rejectsInvalidItemsWithoutChanges` |
| FR-12, FR-22 | `MergeElementsToolTest.repointsRelationshipsKeepingIds` |
| FR-13 | `MergeElementsToolTest.deletesRelationshipsThatBecomeSelfLoops` |
| FR-14 | `MergeElementsToolTest.retargetsFiguresInPlace`, `.viewDefinitionUnchangedExceptElementId` |
| FR-15, FR-16 | `MergeElementsToolTest.nonConflictingValuesFillIn`, `.duplicatesDisagreeWithoutSurvivorValue`, `.repeatedKeysComparedAsOrderedLists` |
| FR-17 | `MergeElementsToolTest.unresolvedConflictsBlockAndReturnDiff`, `.resolveAppliesChosenValues`, `.resolveWithListReplacesAllValuesOfKey`, `.resolveEntryWithoutConflictReturnsCurrentDiff` |
| FR-18 | `MergeElementsToolTest.survivorNameFolderAndSpecializationUnchanged`, `.differingDuplicateNamesReportedNotBlocked` |
| FR-19 | `MergeElementsToolTest.parallelRelationshipsKeptAndReported`, `.differingTypeAttributesNotParallel` |
| FR-20 | `MergeElementsToolTest.collapseConflictsGoThroughResolve` (documentation and name) |
| FR-21 | `MergeElementsToolTest.collapseMovesConnections`, `.collapseDropsDoubledConnection`, `.collapseRepointsRelationshipOnRelationship` |
| FR-23 | `MergeElementsToolTest.dryRunReturnsDiffAndImpactWithoutChanges`, `ManageElementsToolTest.blockedMergeCountsAsFailedInSummary` |
| FR-24 | `assertNoDanglingConnections()` in every merge test that deletes or moves connections |
| S2.15 | `MergeElementsToolTest.undoRestoresEverything` |
| FR-26, FR-28 | `ValidateModelToolTest.reportsEachKindOfDanglingConnection`, `.cleanModelHasEmptyDanglingList` |
| FR-27 | `ValidateModelToolTest.reportedGhostCanBeRemovedByConnectionId` |

## Tests (JUnit 5, `@RegisterExtension final TestModels models = new TestModels();`)

Helpers to add to `testing/TestModels.java`:
- **`CommandStack commandStack()`** calls `model.setAdapter(CommandStack.class, stack)` and
  returns the stack. Tools already execute through `model.getAdapter(CommandStack.class)`, so
  tests can then call `stack.undo()`. First confirm that `IArchimateModel.setAdapter` is
  usable on the test classpath.
- **`assertNoDanglingConnections()`** checks every view, every diagram object, and every
  connection in both its source and target lists (including connections on connections). It
  asserts that both ends are non-null, that `EcoreUtil.isAncestor(view, end)` holds for both
  ends, and that it holds for the connection itself. The walk is independent of
  `connectionsOnView`, so the helper doesn't trust the code under test.

`DeleteElementToolTest`
- `removesEveryFigureIncludingSecondOnSameView`: S1.4.
- `removesNestedFigureOfElement`: the element's figure is inside a group.
- `nestedChildSourceConnectionIsRemoved`: S1.1.
- `nestedChildTargetConnectionIsRemoved`: S1.2.
- `deeplyNestedChildConnectionIsRemoved`: S1.3.
- `connectionAttachedToRemovedConnectionIsRemoved`: S1.9.
- `dryRunCountsFiguresNotViews`: S1.5.
- `undoRestoresFiguresConnectionsRelationshipsAndElement`: S1.6.

`RemoveFigureFromViewToolTest`
- `removesSubtreeAndAllAttachedConnections`: S1.7.
- `noConnectionLeftWithNullEnd`: S1.7 with X→C. This pins D3 red.
- `connectionAttachedToRemovedConnectionIsRemoved`: S1.9.
- `undoRestoresFigureAndAllConnections`: S1.8.

`ModelAccessorTest`
- `findsConnectionWhoseSourceIsDetached`: build a ghost by hand. Connect C→T, then remove C's
  parent without disconnecting. Then check that both lookups find it.
- `connectionsOnViewIsDistinctAndOrdered`: a connection whose two ends are both on the view
  appears once, in tree order.

`DeleteConnectionToolTest`
- `removesTargetSideGhostByConnectionId`: S1.10.

`ManageElementsToolTest`
- `mergeIsRoutedAndListedInSchema`: the enum contains `merge`; a single-object merge returns
  `{results:[{survivor_id...}]}`; an unknown-operation error lists `merge`.
- `blockedMergeCountsAsFailedInSummary`: with `results: "summary"`, a blocked merge counts as
  `failed`, and its error is "unresolved conflicts".

`MergeElementsToolTest` (new)
- `repointsRelationshipsKeepingIds`: S2.1. The duplicate is gone.
- `retargetsFiguresInPlace`: S2.2.
- `viewDefinitionUnchangedExceptElementId`: S2.3. Use `GetViewDefinitionTool` before and after.
- `deletesRelationshipsThatBecomeSelfLoops`: S2.4.
- `nonConflictingValuesFillIn`: S2.5.
- `unresolvedConflictsBlockAndReturnDiff`: S2.6. The exact diff shape; the model is unchanged
  and `saves == 0`.
- `resolveAppliesChosenValues`: S2.7.
- `duplicatesDisagreeWithoutSurvivorValue`: S2.8.
- `repeatedKeysComparedAsOrderedLists`: S2.8a. Checks the conflict shape.
- `resolveWithListReplacesAllValuesOfKey`: S2.8a. A list gives three ordered values at the first key's position; a string gives exactly one.
- `resolveEntryWithoutConflictReturnsCurrentDiff`: S2.9. The error names the entry and the result includes the current `conflicts`; the model is unchanged.
- `differingDuplicateNamesReportedNotBlocked`: S2.9a.
- `parallelRelationshipsKeptAndReported`: S2.10.
- `differingTypeAttributesNotParallel`: Read and Write access to the same target are not
  grouped. Influence relationships with different `influence_strength` values are not
  grouped, and Association relationships with different `directed` values are not grouped.
- `collapseMovesConnections`: S2.11. Same connection object, with the same ID and bendpoints.
- `collapseDropsDoubledConnection`: S2.12. Build the state by hand.
- `collapseRepointsRelationshipOnRelationship`: FR-21, second sentence.
- `collapseConflictsGoThroughResolve`: S2.13. Covers documentation and name.
- `dryRunReturnsDiffAndImpactWithoutChanges`: S2.14. It is not an error even when there are
  conflicts.
- `undoRestoresEverything`: S2.15, using `models.commandStack()`.
- `survivorNameFolderAndSpecializationUnchanged`: FR-18.
- `rejectsInvalidItemsWithoutChanges`: S2.16. Includes a type mismatch and a mixed
  good/bad batch.

`ValidateModelToolTest`
- `reportsEachKindOfDanglingConnection`: S3.1. Build each ghost by hand and check the
  `problems` values.
- `cleanModelHasEmptyDanglingList`: S3.2.
- `reportedGhostCanBeRemovedByConnectionId`: S3.3.

## Verify live (not unit-testable)

- Delete an element whose figure nests children with external connections, re-add it, and
  export. The canvas size should match the size before the delete.
- Use Undo/Redo from Archi's Edit menu after delete, remove_figure, merge, and merge with
  `collapse`.
- Run `validate_model` on a copy of the ConceptualViews backup from before the fix, then remove
  the reported ghosts.

## Versioning

3.0.0 is unreleased, so no bump is needed. `REQUIREMENTS.md` and the CLAUDE.md tool table
(`manage_elements`: add `merge`; `validate_model`: add the dangling check) get updated in the
later docs pass, not as part of this spec.

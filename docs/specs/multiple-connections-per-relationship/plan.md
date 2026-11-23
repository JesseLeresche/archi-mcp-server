# Plan: a relationship or element drawn more than once on a view

Spec: `docs/specs/multiple-connections-per-relationship/spec.md` (FR-1 to FR-14).
Paths below are relative to `za.co.jesseleresche.archi.mcp/src/za/co/jesseleresche/archi/mcp/`
(source) and `za.co.jesseleresche.archi.mcp/src/test/java/za/co/jesseleresche/archi/mcp/` (tests).
v3.0.0 has not been released, so there is no version bump.

## Every lookup that assumes one drawing per view

### Connections by relationship (`findConnectionByRelationshipId`)

| Call site | Today | After |
|---|---|---|
| `tools/BulkAddRelationshipsToViewTool.java:151` (`add_connection`) | Any drawing means `already_exists` | Figure-pair match plus `on_existing` (FR-1 to FR-4) |
| `tools/UpdateConnectionTool.java:132` (`update_connection`) | Acts on the first | Error when ambiguous (FR-6), no edit needed |
| `tools/DeleteConnectionTool.java:94` (`remove_connection`) | Acts on the first | Error when ambiguous (FR-6), no edit needed |
| `tools/BulkUpdateFigureAppearanceTool.java:146` (`set_style`) | Acts on the first | Item error (FR-6), no edit needed |
| `tools/GetConnectionTool.java:88` (`get_connection`) | Returns the first | Error when ambiguous (FR-6), no edit needed |
| `tools/GetViewConnectionsTool.java:78` (`connections`) | Already returns all | Description only (FR-10) |
| `GetViewDefinitionTool:134`, `DeleteRelationshipTool:96`, `UpdateRelationshipTool:156`, `DuplicateViewTool:120` | Already cover all | No change |

### Figures by element (`findFigureByElementId`)

| Call site | Kind | After |
|---|---|---|
| `tools/BulkUpdateFigureAppearanceTool.java:138` (`set_style`) | Targeting | Item error (FR-8), no edit needed |
| `tools/RemoveFigureFromViewTool.java:95` (`remove_figure`) | Targeting | Error (FR-8), no edit needed |
| `tools/GetViewLayoutTool.java:92` (`inspect_view layout`) | Targeting | Error (FR-8), no edit needed |
| `tools/BulkAddRelationshipsToViewTool.java:212` (`endpoint` default) | Targeting | Error naming `source_figure_id`/`target_figure_id` (FR-4, FR-8) |
| `tools/DeleteElementTool.java:98,131,156` (`delete` + `dry_run`) | Enumeration (bug: leaves second figure) | spec-dangling's rewrite removes every figure (FR-12 here, their FR-1) |
| `tools/GetElementAnalysisTool.java:140` (`used_in_views`) | Enumeration | Use `findAllFiguresByElementId`, one entry per figure (FR-13) |

## Files to change

1. **`util/ModelAccessor.java`**
   - Add `findConnectionsByRelationshipId(view, relationshipId)`, which returns a
     `List<IDiagramModelArchimateConnection>` in view order. It filters the shared connection
     walk (see "Shared touch points").
   - Change `findConnectionByRelationshipId` to build on the list: it returns null when the
     list is empty, the single connection when there is one, and throws when there are more.
     Message: `"Relationship <relId> has <n> connections on view <viewId> (<c1>, <c2>); pass connection_id"`.
     Its signature gains `throws Exception`.
   - Reuse the existing `findAllFiguresByElementId(view, elementId)` (`ModelAccessor.java:239`,
     which returns a List in `collectAllFigures` tree order and is already used by
     `BulkUpdateElementsTool`). Do **not** add a second plural method.
   - Change `findFigureByElementId` to build on it in the same way: null for none, the figure for one, and a
     throw for more. Message: `"Element <elId> has <n> figures on view <viewId> (<f1>, <f2>); pass figure_id"`.
     Its signature gains `throws Exception`.
   - Putting the ambiguity check in these two shared methods fixes all six targeting callers
     (FR-6, FR-8) without editing them. They already run in methods or `try` blocks that
     turn an exception into an error.
2. **`tools/BulkAddRelationshipsToViewTool.java`** (per item, inside the existing `try`):
   1. Validate `on_existing` (absent, `reuse` or `add`; FR-2).
   2. Resolve **given** figure IDs with the existing `endpoint` validation. Leave omitted ends
      unresolved.
   3. Matches: filter `findConnectionsByRelationshipId` by `getSource() == givenSource`
      (if given) and `getTarget() == givenTarget` (if given). This is FR-1.
   4. If there is a match: with `on_existing` absent, fail with the FR-3 message; with
      `reuse`, return the first match with `reused: true` (this replaces today's
      `already_exists` flag); with `add`, fall through.
      This happens **before** `claimId`, as it does today.
   5. Resolve omitted ends with `findAllFiguresByElementId`. None gives today's "not found on
      view" message. More than one gives
      `"Element <id> has <n> figures on view <viewId> (<f1>, <f2>); pass source_figure_id"`
      (or `target_figure_id`). One gives that figure (FR-4).
   6. Then the existing `claimId`, style, connect and bendpoint code, unchanged.
   - Schema: add `connection_id`, `on_conflict` and `on_existing` (enum `reuse`, `add`) to
     `itemProps`. `connection_id` is currently read but not declared.
   - Keep the existing missing-element errors working. Today the source and target elements
     are checked before anything else, so check that each omitted end's element has at least
     one figure in step 2, which keeps the error order the same.
3. **`tools/DeleteElementTool.java`**: no change in this plan. spec-dangling's rewrite
   (their FR-1) replaces all three `findFigureByElementId` calls with
   `findAllFiguresByElementId` and removes every figure, which implements FR-12 here.
4. **`tools/GetElementAnalysisTool.java:138-164`** (FR-13): loop over
   `findAllFiguresByElementId` and add one entry per figure. The entry fields stay the same.
5. **`tools/ConsolidatedTool.java`** (FR-5): in the summary loop, count `reused` (no error
   and `reused` is true) and `skipped` (no error and `skipped` is true), and set
   `succeeded = count - failed - reused - skipped`. Output order is
   `count, succeeded, reused, skipped, failed, errors`. Update the `results` description in
   `addItems`.
6. **`tools/ManageViewContentTool.java`** (FR-14): the `add_connection` field list gains
   `connection_id?, on_conflict?, on_existing?: reuse|add`. Add one sentence on matching
   ("an existing drawing between the same figures, where an omitted figure ID matches any
   figure, fails unless on_existing is given"). Add to `update_connection`,
   `remove_connection` and `remove_figure`: "relationship_id/element_id fails if it matches
   more than one drawing; pass connection_id/figure_id".
7. **`tools/ManageAppearanceTool.java`**, **`tools/InspectViewTool.java`**: one clause each
   in the description about the `element_id`/`relationship_id` ambiguity error (FR-14).
8. **`tools/GetViewConnectionsTool.java:52`**: change "the connection" to "the connections"
   (FR-10).
9. **Docs (implementation phase):** update `REQUIREMENTS.md:520-521` (summary shape) and
   `:543-551` (add_connection, the connection operations, styling), and
   `README.md:188-189` (FR-14).

## Algorithm notes

- Matching compares figures by object identity. `endpoint()` has already checked that a
  given figure shows the right element.
- "First match in view order" (FR-3 reuse) is the order of the shared connection walk, which
  is deterministic.
- `on_existing: "add"` together with a taken `connection_id` and `on_conflict: "skip"`
  reports `skipped: true`. That is what makes S11 (an idempotent replay re-run) work without
  any new code.
- Reuse never applies style, bendpoints or `connection_id` from the item (unchanged).

## Shared touch points

**spec-dangling (`docs/specs/figure-removal-dangling-connections/`)**
- spec-dangling replaces the source-only traversal with a private walk that finds
  connections at either end, `connectionsOnView(view)`. It is public, because their
  `validate_model` check uses it. It is a `LinkedHashSet`, so there
  are no duplicates. Its order is tree order from `collectAllDiagramObjects`, with each
  object's source connections first, then its target connections, then connections attached
  to connections. `findConnectionsByRelationshipId` **filters that walk**; it has no
  traversal of its own. If this spec lands first, it filters today's walk, and the dangling
  change swaps the walk underneath it.
- spec-dangling keeps the signature and semantics of `findConnectionByRelationshipId`. The
  ambiguity throw belongs to this spec.
- spec-dangling's `DeleteElementTool` and `MergeElementsTool` call
  `findConnectionsByRelationshipId`. Whichever spec is implemented first adds it.
- `DeleteElementTool` belongs to spec-dangling, as they have confirmed. Their rewrite uses
  `findAllFiguresByElementId` and removes every figure. This plan keeps the FR-12 test
  `deletesEveryFigureOfTheElement`. Because
  no new figure lookup is added, there is no "whoever lands first" step for figures.
  **Ordering constraint:** this plan makes `findFigureByElementId` throw. If that lands
  before spec-dangling's rewrite, the current `DeleteElementTool` calls would throw for an
  element shown twice. Land spec-dangling's `DeleteElementTool` change first, or in the
  same merge.
- spec-dangling's FR-9 now defers to FR-8 here. `remove_figure` by `element_id` is covered
  by S18 and `removeFigureByElementIdRejectsAmbiguous`, and this spec owns those tests.
- Tests that assert removal with `assertNull(findConnectionByRelationshipId(...))`
  (`DeleteConnectionToolTest:54`, `ManageViewContentToolTest:71,191`,
  `RemoveFigureFromViewToolTest:39`) and use `findFigureByElementId`
  (`ModelAccessorTest:57`, `ManageViewContentToolTest:132`, `DuplicateViewToolTest:128`)
  only ever have one drawing, so they stay green. Their methods already declare
  `throws Exception`.

**spec-queryrels**
- `on_existing` on `manage_relationships create` is theirs. They have been asked to mark
  reused items with `reused: true` so the `ConsolidatedTool` change (item 5) counts
  them. The `ConsolidatedTool` change belongs to this spec.

## FR to test mapping

| FR | Tests |
|---|---|
| FR-1 | `drawsSameRelationshipFromSecondFigure`, `omittedFiguresMatchAnyExistingDrawing`, `oneEndGivenMatchesOnThatEndOnly` |
| FR-2 | `rejectsUnknownOnExisting` |
| FR-3 | `samePairWithoutOnExistingFails`, `onExistingReuseReturnsExisting`, `onExistingAddDrawsAnotherLine`, `drawsConnectionWithStyleAndReportsExisting` (updated) |
| FR-4 | `omittedEndWithTwoFiguresFails`, `createsWithUnusedConnectionIdForNewFigurePair`, `usesExplicitFigureIds` (existing), `reportsPerItemErrors` (existing) |
| FR-5 | `summaryResultsCollapseToCounts` (updated), `summaryCountsReusedAndSkippedConnections` |
| FR-6 | `findConnectionByRelationshipIdRejectsAmbiguous`, `updateByRelationshipIdRejectsAmbiguous`, `removeByRelationshipIdRejectsAmbiguous`, `setStyleByRelationshipIdRejectsAmbiguous`, `getConnectionByRelationshipIdRejectsAmbiguous` |
| FR-7 | `connectionIdTargetsOneOfSeveralDrawings` (per tool), existing single-drawing tests |
| FR-8 | `findFigureByElementIdRejectsAmbiguous`, `setStyleByElementIdRejectsAmbiguous`, `removeFigureByElementIdRejectsAmbiguous`, `layoutByElementIdRejectsAmbiguous`, `omittedEndWithTwoFiguresFails` |
| FR-9 | existing single-figure tests in the same classes |
| FR-10 | `relationshipFilterReturnsEveryDrawing` |
| FR-11 | `replayRecreatesDuplicateDrawingsOfARelationship`, `rerunReplayWithSkipCreatesNothing`, `rerunWithReuseIsIdempotent` |
| FR-12 | `deletesEveryFigureOfTheElement` (implementation by spec-dangling's FR-1) |
| FR-13 | `usedInViewsListsEveryFigure` |
| FR-14 | none (descriptions and docs; checked in review) |

## Test cases (JUnit 5, `@RegisterExtension final TestModels models = new TestModels();`)

Fixture used by most of these tests: view V, element E with figures `e1` and `e2`
(`models.figure`), element F with figure `f`, `rel = models.relationship("ServingRelationship", E, F)`,
and existing drawings created with `models.connection(src, tgt, rel)`.

**`util/ModelAccessorTest`**
- `findConnectionsByRelationshipIdReturnsAllInViewOrder`: two drawings give a list of 2 in order; none gives an empty list.
- `findConnectionByRelationshipIdRejectsAmbiguous`: two drawings make it throw, and the message contains both IDs and "pass connection_id". One drawing returns it, none returns null.
- `findFigureByElementIdRejectsAmbiguous`: two figures make it throw, and the message contains both figure IDs and "pass figure_id". One figure returns it.

**`tools/BulkAddRelationshipsToViewToolTest`**
- `drawsSameRelationshipFromSecondFigure` (S1): existing `e1` to `f`, add with `e2`/`f` gives a new ID, no `reused`, and 2 drawings.
- `samePairWithoutOnExistingFails` (S2): existing c1 from `e1` to `f`, add with `e1`/`f` gives an `error` containing c1 and `on_existing`, and 1 drawing.
- `onExistingReuseReturnsExisting` (S3): the same call with `reuse` returns c1 with `reused`, has no `already_exists` key, and there is 1 drawing.
- `onExistingAddDrawsAnotherLine` (S4): the same call with `add` gives a new ID, and there are 2 drawings, both from `e1` to `f`.
- `omittedFiguresMatchAnyExistingDrawing` (S5): existing `e2` to `f`, add with no figure IDs gives an `error`. With `reuse`, it returns that connection.
- `oneEndGivenMatchesOnThatEndOnly` (S6): existing `e1` to `f`, add with only `source_figure_id` `e2` creates one.
- `omittedEndWithTwoFiguresFails` (S7): no drawing, add without `source_figure_id` gives an `error` listing `e1` and `e2` and naming `source_figure_id`, and 0 drawings.
- `createsWithUnusedConnectionIdForNewFigurePair` (S8): "c-2" with `e2`/`f` gives a connection "c-2" whose source is `e2`.
- `rejectsUnknownOnExisting` (S9): `on_existing:"replace"` gives an `error` "on_existing must be 'reuse' or 'add'".
- `rerunWithReuseIsIdempotent` (S12): a 2-item batch (`e1` to `f`, `e2` to `f`) run twice with `reuse`. The second run gives both `reused` with the original IDs, and there are 2 drawings.
- `drawsConnectionWithStyleAndReportsExisting` (update): the second run without `on_existing` now expects an `error`. Add a third run with `reuse` that expects `reused` (replacing the current `already_exists` assertion at line 53) and c's ID.
- `usesExplicitFigureIds`, `reportsPerItemErrors`, `rejectsBadRequests`: existing tests, which must stay green. In `usesExplicitFigureIds`, A has two figures but both IDs are given, so FR-4 does not trigger.

**`tools/ConsolidatedToolTest`**
- `summaryResultsCollapseToCounts` (update): probe `[{id:a},{error:boom},{id:b,skipped:true},{id:c,reused:true}]` gives `count` 4, `succeeded` 1, `reused` 1, `skipped` 1, `failed` 1.

**`tools/ManageViewContentToolTest`**
- `summaryCountsReusedAndSkippedConnections` (S13): `add_connection` with `results:"summary"` and four items (new pair; existing pair with `reuse`; `add` plus a taken `connection_id` plus `on_conflict:"skip"`; unknown relationship) gives `{count:4, succeeded:1, reused:1, skipped:1, failed:1}`.
- `updateByRelationshipIdRejectsAmbiguous` (S14): two drawings, `update_connection` `{relationship_id, line_color}` gives an item `error` with "pass connection_id", and neither colour changes.
- `removeByRelationshipIdRejectsAmbiguous` (S14): two drawings, `remove_connection` by `relationship_id` gives an item `error`, and there are still 2 drawings.
- `removeFigureByElementIdRejectsAmbiguous` (S18): E shown twice, `remove_figure {element_id}` gives an item `error` with "pass figure_id", and both figures remain.

**`tools/UpdateConnectionToolTest` / `DeleteConnectionToolTest` / `GetConnectionToolTest`**
- `connectionIdTargetsOneOfSeveralDrawings` (S15), one per class: two drawings, target c2 by `connection_id`. Only c2 is updated, removed or returned.
- `GetConnectionToolTest.getConnectionByRelationshipIdRejectsAmbiguous` (S14): `execute` throws with "pass connection_id".

**`tools/BulkUpdateFigureAppearanceToolTest`**
- `setStyleByRelationshipIdRejectsAmbiguous` (S14): two drawings give an item `error`, and neither style changes.
- `setStyleByElementIdRejectsAmbiguous` (S18): E shown twice gives an item `error` with "pass figure_id", and neither figure's fill or bounds change.
- `connectionIdTargetsOneOfSeveralDrawings` (S15): styles only c2.

**`tools/GetViewLayoutToolTest`**
- `layoutByElementIdRejectsAmbiguous` (S18): E shown twice, `element_id` makes `execute` throw with "pass figure_id".

**`tools/GetViewConnectionsToolTest`**
- `relationshipFilterReturnsEveryDrawing` (S17): two drawings give 2 entries with distinct `connection_id`.

**`tools/GetViewDefinitionToolTest`**
- `replayRecreatesDuplicateDrawingsOfARelationship` (S10): in `buildView`'s view, add a second `svc` figure, a second `serves` connection from it, and a third `serves` connection repeating the original pair. Replay as `replayIntoAnotherModelReproducesTheDefinition` does, but with `on_existing:"add"` on the `add_connection` step. Assert the definitions are equal (3 `serves` connections, original IDs).
- `rerunReplayWithSkipCreatesNothing` (S11): after the replay, run the `add_connection` step again with `on_existing:"add"` and `on_conflict:"skip"`. Every entry has `skipped`, and the definition is unchanged.
- `replayIntoAnotherModelReproducesTheDefinition`: existing test. The target view is fresh, so it needs no `on_existing` and stays green unchanged.

**`tools/DeleteElementToolTest`** (spec-dangling owns the code change; this test stays here)
- `deletesEveryFigureOfTheElement` (S20): E is shown twice, with a connection from each figure. After `delete`, V has no figure of E and no connections. In a `dry_run`, `figures_to_remove` is 2. If spec-dangling's FR-1 test already asserts the same thing, merge the two into one test rather than keeping both.

**`tools/GetElementAnalysisToolTest`**
- `usedInViewsListsEveryFigure` (S21): E shown twice on V gives 2 `used_in_views` entries with V's ID and the figure IDs `e1` and `e2`.

## Open questions (see the spec)

None. The per-item flag rename (`already_exists` to `reused`) has been decided.

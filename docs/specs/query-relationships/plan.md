# Plan: `manage_relationships query`, access-type names, `on_existing`, type-change carry-over

Spec: `docs/specs/query-relationships/spec.md`. Version stays 3.0.0 (unreleased; no bump).
All paths below are under `za.co.jesseleresche.archi.mcp/` unless absolute.

## Files to change

| File | Change | FRs |
|---|---|---|
| `src/za/co/jesseleresche/archi/mcp/tools/RelationshipAttributes.java` (new, package-private, static only) | The one place for the three type-specific attributes: `read(IArchimateRelationship, Map)` puts `access_type` (name), `influence_strength`, `directed`; `appliesTo(String field, EClass)`; `check(EClass, JsonNode item)` throws "'<field>' does not apply to <Type>" and validates values; `apply(IArchimateRelationship, JsonNode item)` sets them. Access-type name table built from the `IAccessRelationship` constants. | FR-QR-4, 12-16, 22-24 |
| `src/za/co/jesseleresche/archi/mcp/tools/GetViewDefinitionTool.java` | Extract the relationship-entry block of `define()` (lines ~171-178) into package-private `static Map<String, Object> relationshipEntry(IArchimateRelationship r)`, using `RelationshipAttributes.read` instead of the inline `access_type` int. Extract the `model` / selected-model block of `execute` into package-private `static IArchimateModel resolveModel(JsonNode args)`. | FR-QR-4, 10, 11 |
| `src/za/co/jesseleresche/archi/mcp/tools/GetViewConnectionsTool.java` | Replace the `access_type` int (line ~91) with `RelationshipAttributes.read(rel, entry)`. | FR-QR-22 |
| `src/za/co/jesseleresche/archi/mcp/tools/GetConnectionTool.java` | After `relationship_name` (line ~108): `RelationshipAttributes.read(rel, result)`. | FR-QR-22 |
| `src/za/co/jesseleresche/archi/mcp/tools/QueryRelationshipsTool.java` (new, internal helper like `GetRelationshipsBetweenTool`, not registered) | One filter object in, `{count, relationships}` or `{output_file, count}` out. | FR-QR-2..10 |
| `src/za/co/jesseleresche/archi/mcp/tools/ManageRelationshipsTool.java` | Field `query = new QueryRelationshipsTool()`; `case "query": return delegatePerItem(query, items);`; `"query"` in the schema enum and the unknown-operation list; description per FR-QR-16/21 (create gains `access_type` names, `influence_strength?`, `directed?`, `on_existing?`; update gains `influence_strength?`, `directed?` and reports `dropped`). | FR-QR-1, 16, 21 |
| `src/za/co/jesseleresche/archi/mcp/tools/BulkCreateRelationshipsTool.java` | Replace the `asInt()` access-type handling with `RelationshipAttributes.check` and `apply`; add the `on_existing` step. Schema: `access_type` integer-or-string with the correct legend, `influence_strength`, `directed`, `on_existing` enum. | FR-QR-12-20 |
| `src/za/co/jesseleresche/archi/mcp/tools/UpdateRelationshipTool.java` | `applyUpdate`: check before the type swap; carry-over and `dropped` on type change; `apply` replaces the `access_type` block. Schema (`addItemProperties`): `access_type` integer-or-string with names, `influence_strength`, `directed`. | FR-QR-12, 14, 15, 23, 24 |
| Docs sweep: `REQUIREMENTS.md`, `CLAUDE.md`, `README.md`, `docs/view-guide-template.md`, `src/resources/guides/*.md` | Document `query`, access-type names, `influence_strength`/`directed`, `on_existing` and the replay note, `dropped` on type change; tool table gains `query`. Sweep every guide/doc for `access_type` and other enum values and field names the tools don't accept (known: `layered-view-agent-guide.md` ~line 268 uses `source`/`target` instead of `source_id`/`target_id`; its and `application-structure-view-agent-guide.md`'s `"ReadWrite"` are now valid) and for any wrong access-type legend. | FR-QR-16, 21 |

No change to `ToolRegistry` (still 14 tools), `ExchangeDir`, `ModelAccessor` or
`ConsolidatedTool`. The summary's `reused`/`skipped` counters are added to `ConsolidatedTool`
by the multiple-connections spec (it counts `reused: true` as reused and `skipped: true` as
skipped); this plan only emits `reused: true` on reused entries.

Coordination: sibling specs may touch `GetViewDefinitionTool`, `GetViewConnectionsTool`,
`GetConnectionTool` and `ConsolidatedTool`. This change touches only the relationship-entry
block and model lookup in `GetViewDefinitionTool`, and one line each in the two connection
tools.

## Algorithm notes

### Query

- **Model:** `GetViewDefinitionTool.resolveModel(args)` (`model` -> `ModelAccessor.findModel`,
  "Model not found: X"; else `getOpenModel()`, "No model is currently open in Archi").
  Errors become item errors through `delegatePerItem`.
- **Validate filters first:** `type_filter` -> `ModelAccessor.resolveRelationshipClass`, null
  -> "Unknown relationship type: X". `element_id` -> `ArchimateModelUtils.getObjectByID`
  must be an `IArchimateConcept`, else "Element or relationship not found: X".
- **Candidates:** `ModelAccessor.collectAllFromFolders(model, IArchimateRelationship.class)`
  (folder order; same call `between` uses).
- **Match:** `eClass() == resolved`; end check by ID on `getSource()`/`getTarget()`.
- **Views:** `rel.getReferencingDiagramComponents()` (as `DeleteRelationshipTool.applyDelete`,
  proven under `TestModels`), keep `IDiagramModelArchimateConnection`, map to
  `getDiagramModel().getId()` into a `LinkedHashSet`. `on_view` tests `views.isEmpty()`.
  `// ponytail: one scan of referencing components per relationship; fine at ~1.3k rels`
- **Entry:** `GetViewDefinitionTool.relationshipEntry(r)` then `put("views", list)`.
- **Output:** no `output_file` -> `{count, relationships}`. With `output_file` ->
  `ExchangeDir.write(name, MAPPER.writeValueAsString(entries))` (bare array, feeds
  `items_file`) -> `{output_file: path.toString(), count}`.
- **Read-only:** no `syncExec`, no `saveModel`.

### `RelationshipAttributes`

- Name table: `Write -> WRITE_ACCESS`, `Read -> READ_ACCESS`,
  `Unspecified -> UNSPECIFIED_ACCESS`, `ReadWrite -> READ_WRITE_ACCESS`; parse is
  case-insensitive; an integral JSON number must be one of the four constants; anything else
  -> "access_type must be one of Write, Read, Unspecified, ReadWrite or 0-3, got X".
- `appliesTo(field, eClass)`: `access_type` -> Access, `influence_strength` -> Influence,
  `directed` -> Association (`IArchimatePackage.eINSTANCE.getAccessRelationship()` etc.).
- `check(eClass, item)`: for each field present and non-null, throw "'<field>' does not
  apply to <eClass.getName()>" unless `appliesTo`; then validate `access_type` and that
  `directed` is a boolean. Throws before any mutation.
- `apply(rel, item)`: set whatever is present (`hasNonNull`); assumes `check` passed. Takes a
  `JsonNode`, so the carry-over can feed it the old values (below).
- `read(rel, map)`: `access_type` name always for Access; `influence_strength` if non-empty;
  `directed` only if true.

### Create: per-item order

1. Required fields; `id` / `on_conflict` via `ModelAccessor.claimId` (unchanged: an
   existing `id` is skipped or errors here, `on_existing` never consulted).
2. Resolve source, target, type; ArchiMate validity (unchanged).
3. `RelationshipAttributes.check(eClass, item)`; validate `on_existing` value if present.
4. Existing lookup: first relationship in `collectAllFromFolders(model,
   IArchimateRelationship.class)` with `eClass() == eClass`, same source and target. This
   sees relationships created earlier in the batch (added to folders as each item
   completes, inside the same `syncExec`).
   `// ponytail: linear scan per item, O(items x rels); index by (source,type) if batches get large`
   Found + absent -> error; `"reuse"` -> `{id, reused: true, folder_path}` and `continue`;
   `"add"` -> fall through.
5. Create and `RelationshipAttributes.apply` (replacing the old `access_type` block).

### Update

1. `effective = new_type resolved ?: rel.eClass()`; `RelationshipAttributes.check(effective,
   item)` before line ~135's type swap, so a failing item changes nothing.
2. On a real type change (inside the existing swap block): `old = new LinkedHashMap`;
   `RelationshipAttributes.read(rel, old)`; add `specialization` if
   `ModelAccessor.getSpecialization(rel) != null`. For each entry of `old`: if it is one of
   the three attributes and `appliesTo(key, eClass)`, apply it to `newRel` (convert the map
   to a `JsonNode` with `MAPPER.valueToTree` and call `apply`); otherwise put it in `dropped`.
   Explicit item fields are applied afterwards by the existing code path, so they win.
3. After `ModelAccessor.applySpecialization(model, target, item)`: if the item set
   `specialization`, remove `specialization` from `dropped`. Put `dropped` in the entry only
   if non-empty.

Note: each attribute belongs to exactly one type, so step 2's carry-over branch is never
taken by a real type change today; it is kept because it is the same loop that computes
`dropped` (no extra code), so `RelationshipAttributes` stays the single decision point.

## FR -> test mapping

| FR | Tests |
|---|---|
| FR-QR-1 | `ManageRelationshipsToolTest.queryIsPerItem`, `queryListedInSchemaEnum` |
| FR-QR-2 | `QueryRelationshipsToolTest.elementIdMatchesEitherEnd`, `typeFilterIsCaseInsensitive`, `onViewFalseReturnsExactlyUndrawn`, `onViewTrueReturnsExactlyDrawn`, `filtersCombineWithAnd`, `emptyFilterReturnsAllInFolderOrder` |
| FR-QR-3 | `QueryRelationshipsToolTest.unknownTypeIsError`, `unknownElementIdIsError`; `ManageRelationshipsToolTest.queryIsPerItem` |
| FR-QR-4 | `QueryRelationshipsToolTest.undrawnAccessRelationshipHasFullDetail`, `everyAccessTypeIsNamed`, `influenceStrengthAndDirectedReported`, `defaultsOmitted` |
| FR-QR-5 | `QueryRelationshipsToolTest.viewsListedOncePerView`, `undrawnAccessRelationshipHasFullDetail` |
| FR-QR-6 | `QueryRelationshipsToolTest.emptyFilterReturnsAllInFolderOrder`; `ManageRelationshipsToolTest.queryOutputReplaysIntoAnotherModel` |
| FR-QR-7 | `QueryRelationshipsToolTest.undrawnAccessRelationshipHasFullDetail` |
| FR-QR-8 | `QueryRelationshipsToolTest.outputFileWritesBareArray`, `outputFileNeverOverwritesOrAcceptsPath`; `ManageRelationshipsToolTest.queryOutputFileReplaysViaItemsFile` |
| FR-QR-9 | `QueryRelationshipsToolTest.queryIsReadOnly` |
| FR-QR-10 | `QueryRelationshipsToolTest.modelParamReadsWithoutChangingSelection`, `unknownModelIsError`, `noOpenModelIsError` |
| FR-QR-11 | `GetViewDefinitionToolTest.relationshipEntriesUseSharedShape` |
| FR-QR-12 | `BulkCreateRelationshipsToolTest.accessTypeByNameCaseInsensitive`, `accessTypeByIntegerStillWorks`, `badAccessTypeIsItemError`; `UpdateRelationshipToolTest.accessTypeByName`, `badAccessTypeIsItemError` |
| FR-QR-13 | `BulkCreateRelationshipsToolTest.omittedAccessTypeIsWrite` |
| FR-QR-14 | `BulkCreateRelationshipsToolTest.createsInfluenceStrengthAndDirected`; `UpdateRelationshipToolTest.updatesInfluenceStrengthAndDirected` |
| FR-QR-15 | `BulkCreateRelationshipsToolTest.inapplicableFieldsAreItemErrors`; `UpdateRelationshipToolTest.inapplicableFieldsAreItemErrors`, `applicabilityFollowsNewType`, `inapplicableAfterNewTypeChangesNothing` |
| FR-QR-16 | `ManageRelationshipsToolTest.accessTypeLegendMatchesArchiConstants`; docs sweep (manual) |
| FR-QR-17, 18 | `BulkCreateRelationshipsToolTest.duplicateFailsWithoutOnExisting`, `onExistingReuseReturnsExisting`, `onExistingAddCreatesAnother`, `onlySameTypeSameDirectionIsExisting`, `earlierBatchItemsCount`, `badOnExistingIsItemError` |
| FR-QR-19 | `BulkCreateRelationshipsToolTest.existingIdIsOnConflictsJob`, `newIdWithDuplicateEndsUsesOnExisting` |
| FR-QR-20 | `BulkCreateRelationshipsToolTest.onExistingReuseReturnsExisting` (entry flag); summary counter tested by the multiple-connections spec |
| FR-QR-21 | `ManageRelationshipsToolTest.queryListedInSchemaEnum`, `replayIntoPopulatedModelNeedsSkipOrReuse` |
| FR-QR-22 | `GetViewConnectionsToolTest.reportsAccessTypeAsName` (replaces the integer assertion), `reportsStrengthAndDirected`; `GetConnectionToolTest.reportsRelationshipAttributes` |
| FR-QR-23, 24 | `UpdateRelationshipToolTest.typeChangeReportsDroppedAttributes`, `typeChangeKeepsSpecializationSetInSameItem`, `noDroppedKeyWhenNothingDropped` |
| QR-18/19 round trip | `ManageRelationshipsToolTest.queryOutputReplaysIntoAnotherModel`, `queryOutputFileReplaysViaItemsFile` |

## Test list (JUnit 5, `@RegisterExtension final TestModels models = new TestModels();`)

Exchange-folder tests follow `GetViewDefinitionToolTest`: `@TempDir Path exchange` +
`System.setProperty(ExchangeDir.PROPERTY, ...)` in `@BeforeEach`, cleared in `@AfterEach`.
Cross-model tests follow its replay test (`models.newModel`, `ModelAccessor.selectModel`);
target elements get the source IDs via `setId`. Assert stored access types against the
`IAccessRelationship` constants, never literals.

New: `src/test/java/za/co/jesseleresche/archi/mcp/tools/QueryRelationshipsToolTest.java`

- `undrawnAccessRelationshipHasFullDetail()` QR-1.
- `everyAccessTypeIsNamed()` QR-2.
- `viewsListedOncePerView()` QR-3.
- `onViewFalseReturnsExactlyUndrawn()` QR-4.
- `onViewTrueReturnsExactlyDrawn()` QR-5.
- `elementIdMatchesEitherEnd()` QR-6.
- `typeFilterIsCaseInsensitive()` QR-7.
- `unknownTypeIsError()` QR-8.
- `unknownElementIdIsError()` QR-9 (single-item half).
- `filtersCombineWithAnd()` QR-10.
- `emptyFilterReturnsAllInFolderOrder()` QR-11.
- `influenceStrengthAndDirectedReported()` QR-12.
- `defaultsOmitted()` QR-13.
- `modelParamReadsWithoutChangingSelection()` QR-14: by name and by ID; `ModelAccessor.getSelectedModelId()` unchanged.
- `unknownModelIsError()` QR-15.
- `outputFileWritesBareArray()` QR-16.
- `outputFileNeverOverwritesOrAcceptsPath()` QR-17.
- `queryIsReadOnly()` QR-21: `models.saves == 0`.
- `noOpenModelIsError()` QR-22: `models.closeAll()`.

Additions to `src/test/java/za/co/jesseleresche/archi/mcp/tools/ManageRelationshipsToolTest.java`

- `queryIsPerItem()` QR-9: `[{element_id:'ghost'}, {element_id:A}]` -> error then result.
- `queryListedInSchemaEnum()` FR-QR-1/21: enum contains `query`; description mentions `query`, `on_existing`.
- `queryOutputReplaysIntoAnotherModel()` QR-18: compare every attribute on the EMF objects; querying T equals querying S (source relationships undrawn, so `views` is `[]` both sides).
- `queryOutputFileReplaysViaItemsFile()` QR-19.
- `replayIntoPopulatedModelNeedsSkipOrReuse()` QR-37: same IDs + `on_conflict: skip` -> all `skipped: true`; different IDs + `on_existing: reuse` -> all `reused: true`; relationship count unchanged. If the multiple-connections `ConsolidatedTool` change has landed, also assert the `results: "summary"` counts (`skipped` = N, then `reused` = N, `succeeded` = 0); otherwise leave that assertion to that spec's tests.
- `accessTypeLegendMatchesArchiConstants()` FR-QR-16: description and both schemas contain `WRITE_ACCESS + "=Write"`, `READ_ACCESS + "=Read"`, `UNSPECIFIED_ACCESS + "=Unspecified"`, `READ_WRITE_ACCESS + "=ReadWrite"`, built from the constants.

Additions to `src/test/java/za/co/jesseleresche/archi/mcp/tools/BulkCreateRelationshipsToolTest.java`

- `accessTypeByNameCaseInsensitive()` QR-23 (create half).
- `accessTypeByIntegerStillWorks()` QR-24 (existing `createsRelationshipsWithNameAndAccessType` already covers 1; keep it).
- `badAccessTypeIsItemError()` QR-25: `"Sometimes"` and `7`; no relationship created.
- `omittedAccessTypeIsWrite()` QR-26.
- `createsInfluenceStrengthAndDirected()` QR-27 (create half).
- `inapplicableFieldsAreItemErrors()` QR-28: three Serving items each fail with the exact message; a valid fourth item in the batch succeeds.
- `duplicateFailsWithoutOnExisting()` QR-30: exact message with R's ID; relationship count unchanged.
- `onExistingReuseReturnsExisting()` QR-31: `{id, reused: true, folder_path}`; R's name unchanged.
- `onExistingAddCreatesAnother()` QR-32.
- `onlySameTypeSameDirectionIsExisting()` QR-33.
- `earlierBatchItemsCount()` QR-34.
- `existingIdIsOnConflictsJob()` QR-35 first half: `id` of an existing relationship with `on_existing: "add"` still errors (default `on_conflict`) / skips.
- `newIdWithDuplicateEndsUsesOnExisting()` QR-35 second half: absent fails; reuse returns R's ID and "r2" does not exist; add creates "r2".
- `badOnExistingIsItemError()` QR-36.

Note: existing create tests that create the same type between the same pair twice must now pass `on_existing: "add"`; the tdd-agent should run the suite red first to find them.

Additions to `src/test/java/za/co/jesseleresche/archi/mcp/tools/UpdateRelationshipToolTest.java`

- `accessTypeByName()` QR-23 (update half).
- `badAccessTypeIsItemError()` QR-25 (update half): relationship unchanged.
- `updatesInfluenceStrengthAndDirected()` QR-27 (update half).
- `inapplicableFieldsAreItemErrors()` QR-28 (update half).
- `applicabilityFollowsNewType()` QR-29 first half.
- `inapplicableAfterNewTypeChangesNothing()` QR-29 second half: type and ID unchanged.
- `typeChangeReportsDroppedAttributes()` QR-39: the three cases; ID, name, documentation, property and view connection preserved; `dropped` exact.
- `typeChangeKeepsSpecializationSetInSameItem()` FR-QR-24: Access with "Feed" -> Flow with `specialization: "Feed"` in the item -> Flow has specialization "Feed"; `dropped` is `{access_type: "Read"}` only.
- `noDroppedKeyWhenNothingDropped()` QR-40: Serving -> Flow, plain update, same-type `new_type`.

Additions to `src/test/java/za/co/jesseleresche/archi/mcp/tools/GetViewDefinitionToolTest.java`

- `relationshipEntriesUseSharedShape()` QR-20: `access_type: "Read"`, `influence_strength`, `directed`, no `views`. Any existing assertion on an integer `access_type` in definition output changes to the name; replay tests stay green because create accepts names.

Changes to `src/test/java/za/co/jesseleresche/archi/mcp/tools/GetViewConnectionsToolTest.java`

- Line ~60 integer assertion becomes `assertEquals("Read", c.get("access_type").asText())` (QR-38).
- `reportsStrengthAndDirected()` QR-38.

Additions to `src/test/java/za/co/jesseleresche/archi/mcp/tools/GetConnectionToolTest.java`

- `reportsRelationshipAttributes()` QR-38: ReadWrite Access -> `"ReadWrite"`; Influence "+" and directed Association.

`ToolRegistryTest` stays unchanged and must stay green.

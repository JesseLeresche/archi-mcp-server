# Spec: model-wide relationship query (`manage_relationships query`)

Source request: `feature-requests/archi-mcp-query-relationships.md`
Target: v3.0.0 (unreleased, branch `feat/v3-bulk-authoring`)

## Problem (verified against source)

A relationship's full detail is only readable when it is drawn on a view:

| Read path | What it actually returns (verified) |
|---|---|
| `query_model` | Elements only: candidates come from `collectAllFromFolders(model, IArchimateElement.class)`, so `type_filter: "ServingRelationship"` always returns `[]`. |
| `manage_folders list_contents` | `id, name, type, source_id, target_id`. No documentation, properties, specialization or access type. |
| `get_element_analysis` | `relationship_id, relationship_type, relationship_name` plus the other end. No access type, documentation, properties. |
| `manage_relationships between` | One pair per item; `specialization`, `properties` only with `include_properties`; no access type or documentation. |
| `inspect_view definition` | Full replayable entry, but only for relationships drawn on that view. |

Also verified:

- No tool reads or writes an Influence relationship's strength or an Association
  relationship's `directed` flag.
- Archi's access-type constants (confirmed with `javap` on the Archi model jar) are
  `WRITE_ACCESS = 0`, `READ_ACCESS = 1`, `UNSPECIFIED_ACCESS = 2`, `READ_WRITE_ACCESS = 3`.
  The `manage_relationships create` legend says "0=Unspecified, 1=Read, 2=Write,
  3=ReadWrite", which swaps 0 and 2. The `update` schema legend is already correct.
- A new Access relationship's model default is `WRITE_ACCESS` (0), so `create` without
  `access_type` gives **Write**, not Unspecified.
- The bundled guides (`application-structure-view-agent-guide.md`,
  `layered-view-agent-guide.md`) send `access_type: "ReadWrite"` as text. `create` and
  `update` read it with `asInt()`, so the text silently becomes 0 (Write).
- `create` and `update` silently ignore `access_type` on a non-Access relationship.
- `create` always adds a new relationship, even when one of the same type already joins the
  same source and target. Archi's desktop editor asks whether to reuse the existing one.
- `update` with `new_type` builds a new relationship copying only name, documentation and
  properties. The old type's access type, influence strength, direction and the
  specialization are lost without any report.
- `inspect_view connections` reports `access_type` as an integer; `get_connection` doesn't
  report it at all.
- `layered-view-agent-guide.md`'s relationship example (around line 268) uses `source` /
  `target` keys, not the `source_id` / `target_id` that `create` requires.

## Design decision

**A `query` operation on `manage_relationships`**, not a new 15th tool and not a mode on
`query_model`.

- `manage_relationships` already hosts read-only operations (`valid_types`, `between`), so
  the tool count stays at 14.
- `query_model` returns a bare array of element entries with a `layer` field; a relationship
  mode would give one tool two unrelated result shapes.
- A new tool would duplicate the `operation`/`items`/`items_file`/`results` handling that
  `manage_relationships` already has.
- Each item is one filter object, so one call can answer several queries (for example the
  incident relationships of several duplicate elements), as `between` does.

Result entries reuse the relationship entry `inspect_view definition` already emits (one
shared shape), extended with `influence_strength`, `directed` and a read-only `views`.

### Access type is written as a name

Query output (and the shared `inspect_view definition` entry) writes `access_type` as a name:
`"Write"`, `"Read"`, `"Unspecified"` or `"ReadWrite"`. Reasons:

- It replays: `create` and `update` now accept the names.
- It cannot be misread. The wrong integer legend in the create description is exactly how
  values got corrupted; a name means the same thing to every reader.
- It is the form the bundled guides already use.

`create` and `update` still accept the integers 0 to 3 for existing clients. Every output
that reports an access type uses the name: query, `inspect_view definition`,
`inspect_view connections` and `inspect_view get_connection` (one form everywhere; v3.0.0 is
unreleased, so the `connections` / `get_connection` change is not a break for released
clients).

### Filters kept and dropped (YAGNI)

Kept, because the request's workflow and acceptance criteria need them: `element_id`,
`type_filter`, `on_view`, `output_file`, and `model` (user decision; same lookup as
`inspect_view definition`).

Dropped, with the reason:

- `source_id` / `target_id`: `element_id` plus each result's `source_id`/`target_id` cover
  direction; `between` covers a specific pair.
- `folder_id` / `include_subfolders`, `name_search`, `property_key` / `property_value`,
  `specialization`: no step in the reported workflow needs them; each is a few lines to add
  later.
- `include_properties`: properties are always included when present, because replay needs
  them.
- Enrichment of `get_element_analysis` and `between`: `query` with `element_id` returns the
  same relationships with full detail.

---

## User stories

**US-QR-1: Read every relationship with full, replayable detail.**
As an architect's AI assistant restructuring a model, I want to read any relationship's full
attributes (whether or not it is drawn on a view) in the same field names
`manage_relationships create` takes, so that I can delete and re-create relationships, or
copy them to another model, without losing access type, influence strength, direction,
documentation, properties or specialization.

**US-QR-2: Write relationship attributes unambiguously.**
As an AI assistant creating or updating relationships, I want to give access type as a name,
set influence strength and direction, and be told when a field doesn't fit the relationship
type, so that what I send is what the model stores.

**US-QR-3: Don't create duplicate relationships by accident.**
As an AI assistant creating relationships, I want `create` to stop when the same
relationship already exists and let me choose to reuse it or add another, so that the model
doesn't fill with silent duplicates (as Archi's own editor prevents).

## Acceptance scenarios

### Query (`manage_relationships`, `operation: "query"`)

Unless stated otherwise, queries run against the selected open model.

**QR-1: Undrawn relationship returned with full detail.**
Given an Access relationship from element A to data object D, not drawn on any view, with
access type Read, a name, documentation, the property `owner = team-x`, the specialization
"Feed", in folder `Relations/Data`
When I query with `items: {}`
Then its entry has `id`, `type: "AccessRelationship"`, `source_id` = A, `target_id` = D, the
name, the documentation, `folder_path: "Relations/Data"`, `properties: [{key: "owner", value:
"team-x"}]`, `specialization: "Feed"`, `access_type: "Read"`, and `views: []`.

**QR-2: Every access type is named.**
Given four Access relationships with access types Write, Read, Unspecified and ReadWrite
When I query
Then their entries have `access_type` `"Write"`, `"Read"`, `"Unspecified"` and `"ReadWrite"`
respectively.

**QR-3: Views listed for drawn relationships.**
Given relationship R drawn on views V1 and V2, and drawn twice on V1
When I query
Then R's entry has `views` listing V1's ID and V2's ID, each once.

**QR-4: `on_view: false` returns exactly the undrawn relationships.**
Given relationships R1 (drawn on a view) and R2, R3 (not drawn on any view)
When I query with `{on_view: false}`
Then the result holds exactly R2 and R3.

**QR-5: `on_view: true` returns exactly the drawn relationships.**
Given the model from QR-4
When I query with `{on_view: true}`
Then the result holds exactly R1.

**QR-6: `element_id` matches either end.**
Given relationships A→B, C→A and B→C
When I query with `{element_id: A}`
Then the result holds exactly A→B and C→A.

**QR-7: `type_filter` is case-insensitive.**
Given one Access and one Serving relationship
When I query with `{type_filter: "accessrelationship"}`
Then the result holds exactly the Access relationship.

**QR-8: Unknown type is an item error.**
When I query with `{type_filter: "NotARelationship"}`
Then that item's result is an error naming the type, not an empty list.

**QR-9: Unknown `element_id` is an item error; other items are still answered.**
Given element A with one relationship
When I query with items `[{element_id: "ghost"}, {element_id: A}]`
Then the first result is an error naming `ghost` and the second holds A's relationship.

**QR-10: Filters combine with AND.**
Given element A with an outgoing Access relationship drawn on a view, an outgoing Access
relationship drawn on no view, and an outgoing Serving relationship drawn on no view
When I query with `{element_id: A, type_filter: "AccessRelationship", on_view: false}`
Then the result holds exactly the undrawn Access relationship.

**QR-11: No filters returns every relationship.**
Given a model with N relationships across several Relations subfolders
When I query with `items: {}`
Then the result's `count` is N and the entries appear in model folder order.

**QR-12: Influence strength and Association direction are reported.**
Given an Influence relationship with strength "++" and a directed Association relationship
When I query
Then the Influence entry has `influence_strength: "++"` and the Association entry has
`directed: true`.

**QR-13: Default values are omitted.**
Given an Influence relationship with no strength and an undirected Association relationship
When I query
Then neither entry has `influence_strength` nor `directed`, and no other relationship type
has either field.

**QR-14: `model` reads another open model without changing the selection.**
Given open models S (selected) and T, where T has relationship RT
When I query with `{model: <T's name>}` and again with `{model: <T's ID>}`
Then both results hold RT, and S is still the selected model.

**QR-15: Unknown `model` is an item error.**
When I query with `{model: "nope"}`
Then that item's result is the error "Model not found: nope".

**QR-16: `output_file` writes a replayable array.**
Given a model with relationships and no `rels.json` in the exchange folder
When I query with `{output_file: "rels.json"}`
Then the item result is `{output_file: <absolute path>, count: N}` with no inline entries, and
the file holds a JSON array of the N entries (not wrapped in an object), so it can be passed
straight to `manage_relationships create` as `items_file`.

**QR-17: `output_file` never overwrites and never accepts a path.**
Given `rels.json` already exists in the exchange folder
When I query with `{output_file: "rels.json"}`, and separately with
`{output_file: "../rels.json"}`
Then each item result is an error, and the existing file is unchanged.

**QR-18: Query output re-creates relationships exactly in another model.**
Given source model S with an Access (Read), an Influence ("+"), a directed Association and a
Serving relationship with documentation, properties, specialization and subfolders, and
target model T containing elements with the same IDs as S's elements and no relationships
When I query S with `items: {}`, select T, and pass the returned entries as `items` to
`manage_relationships create`
Then T has the same relationships with the same IDs, types, ends, names, documentation,
folder paths, properties, specializations, access types, influence strengths and directed
flags, and querying T returns the same entries as querying S.

**QR-19: Replay through the exchange folder.**
Given the query of QR-18 written with `output_file: "rels.json"`
When I select T and call `manage_relationships create` with `items_file: "rels.json"`
Then the result is the same as QR-18.

**QR-20: Shared entry shape in `inspect_view definition`.**
Given a view showing an Access (Read) relationship, an Influence relationship with strength
"+" and a directed Association
When I run `inspect_view definition` on it
Then those relationship entries carry `access_type: "Read"`, `influence_strength: "+"` and
`directed: true`, and do not carry `views`.

**QR-38: Connection inspection reports access type as a name.**
Given a view showing an Access (ReadWrite) relationship
When I run `inspect_view connections` on the view, and `inspect_view get_connection` for
that relationship
Then both report `access_type: "ReadWrite"`. For an Influence ("+") and a directed
Association connection, both report `influence_strength: "+"` and `directed: true`.

**QR-21: Query is read-only.**
When I run any query
Then the model is not saved and is unchanged.

**QR-22: No open model.**
Given no model is open and no `model` is given
When I run a query
Then the item result is the error "No model is currently open in Archi".

### Create and update attributes

**QR-23: Access type by name, case-insensitive.**
When I create Access relationships with `access_type` `"write"`, `"Read"`, `"UNSPECIFIED"`
and `"ReadWrite"`, and update a fifth from Write to `"read"`
Then they store `WRITE_ACCESS`, `READ_ACCESS`, `UNSPECIFIED_ACCESS`, `READ_WRITE_ACCESS` and
`READ_ACCESS` respectively.

**QR-24: Access type by integer still works.**
When I create an Access relationship with `access_type: 1`
Then it stores `READ_ACCESS`.

**QR-25: Bad access type is an item error.**
When I create (or update) an Access relationship with `access_type: "Sometimes"`, and another
with `access_type: 7`
Then each item fails with an error listing the accepted names and 0 to 3, and nothing is
created or changed for that item.

**QR-26: Omitted access type defaults to Write.**
When I create an Access relationship without `access_type`
Then it stores `WRITE_ACCESS`, and query reports `access_type: "Write"`.

**QR-27: Create and update set influence strength and direction.**
When I create an Influence relationship with `influence_strength: "++"` and an Association
relationship with `directed: true`, then update the Influence strength to `"-"` and the
Association to `directed: false`
Then the stored values follow each step.

**QR-28: Fields that don't apply to the type are item errors.**
When I create a Serving relationship with `access_type`, `influence_strength` or `directed`
(one item each), or update a Serving relationship with any of them
Then each item fails with "'<field>' does not apply to ServingRelationship", nothing is
created or changed for that item, and other items in the batch still succeed.

**QR-29: Applicability on update follows the new type.**
Given an Association relationship R
When I update R with `new_type: "AccessRelationship"` and `access_type: "Read"`
Then R becomes an Access relationship with access type Read.
When I instead update an Access relationship with `new_type: "ServingRelationship"` and
`access_type: "Read"`
Then the item fails with "'access_type' does not apply to ServingRelationship" and the
relationship is unchanged (its type is not changed either).

**QR-39: Changing type carries over what still applies and reports what was dropped.**
Given an Access relationship R (access type Read, specialization "Feed", a name,
documentation, a property, drawn on a view)
When I update R with `new_type: "FlowRelationship"`
Then R is a Flow relationship with the same ID, name, documentation, property and view
connection, and the item result includes `dropped: {access_type: "Read", specialization:
"Feed"}`.
Given an Influence relationship with strength "++"
When I update it with `new_type: "AssociationRelationship"`
Then the result includes `dropped: {influence_strength: "++"}`.
Given a directed Association relationship
When I update it with `new_type: "AccessRelationship"` and `access_type: "Read"`
Then it is an Access relationship with access type Read, and the result includes
`dropped: {directed: true}`.

**QR-40: Nothing dropped, no `dropped` key.**
Given a Serving relationship with no specialization
When I update it with `new_type: "FlowRelationship"`
Then the item result has no `dropped` key. The same holds for any update without `new_type`,
or whose `new_type` equals the current type.

### Create: existing relationships (`on_existing`)

**QR-30: Duplicate create fails by default.**
Given Serving relationship R from A to B
When I create a Serving relationship from A to B without `on_existing`
Then the item fails with `relationship <R's id> already exists between these elements; pass
on_existing: "reuse" or "add"`, and no relationship is created.

**QR-31: `on_existing: "reuse"` returns the existing relationship.**
Given R from QR-30
When I create a Serving relationship from A to B with `on_existing: "reuse"` and a different
name
Then the item result is `{id: <R's id>, reused: true, folder_path: <R's folder path>}`,
no relationship is created, and R is unchanged (its name is not overwritten).

**QR-32: `on_existing: "add"` creates another.**
Given R from QR-30
When I create a Serving relationship from A to B with `on_existing: "add"`
Then a second Serving relationship from A to B is created.

**QR-33: Only same type, same direction counts as existing.**
Given Serving relationship A→B
When I create (without `on_existing`) a Serving relationship B→A, and a Flow relationship A→B
Then both are created.

**QR-34: Earlier items in the same batch count.**
When I create, in one call, two identical Serving relationships A→B without `on_existing`
Then the first is created and the second fails with the "already exists" error naming the
first.

**QR-35: Caller ID conflicts are still `on_conflict`'s job.**
Given relationship R with ID "r1"
When I create an item with `id: "r1"` (with or without `on_existing`)
Then the result is decided by `on_conflict` alone (error, or `{id: "r1", skipped: true}` with
`on_conflict: "skip"`), whatever the item's ends and type.
When I create an item with a new `id: "r2"` that duplicates R's type and ends
Then `on_existing` applies: absent fails, `"reuse"` returns R's ID (not "r2", and "r2" is not
created), `"add"` creates "r2".

**QR-36: Bad `on_existing` value is an item error.**
When I create with `on_existing: "merge"`
Then the item fails with an error listing "reuse" and "add".

**QR-37: Replay into a model that already has the relationships.**
Given target model T that already holds every relationship from a query of S, with the same
IDs
When I replay the query output into T with `on_conflict: "skip"` on each item
Then every item reports `skipped: true` and nothing changes.
Given T holds the same relationships under different IDs
When I replay with `on_existing: "reuse"`
Then every item reports `reused: true` with T's IDs and nothing changes.
With `results: "summary"`, the first replay reports every item under `skipped` and the
second under `reused`; neither counts them as `succeeded`.

## Functional requirements

### Query

- **FR-QR-1** `manage_relationships` accepts `operation: "query"`. Each item is a filter
  object, answered independently; an item failure is reported as that item's `{error}` and
  does not stop other items. `items`, `items_file` and `results` behave as for every other
  operation.
- **FR-QR-2** Supported filters, all optional and combined with AND: `element_id` (the
  relationship's source or target has this ID), `type_filter` (relationship type name,
  case-insensitive), `on_view` (`true`: drawn on at least one view; `false`: drawn on no
  view; absent: either). An empty filter object matches every relationship.
- **FR-QR-3** An unknown `type_filter` is an item error naming the type. An `element_id` that
  names no element or relationship in the model is an item error naming the ID.
- **FR-QR-4** Each matching relationship produces one entry with `id`, `type`, `name` (empty
  string if unset), `documentation` (only if set), `folder_path`, `properties` (only if any,
  as `[{key, value}]` in order), `specialization` (only if set), `source_id`, `target_id`,
  then: `access_type` for every Access relationship, as one of `"Write"`, `"Read"`,
  `"Unspecified"`, `"ReadWrite"`; `influence_strength` for an Influence relationship whose
  strength is non-empty; `directed: true` for a directed Association relationship. These
  are field names and values `manage_relationships create` accepts.
- **FR-QR-5** Each entry also has `views`: the IDs of the views the relationship is drawn on,
  each view once, in model order. `views` is read-only and ignored by `create`.
- **FR-QR-6** Entries are in model folder order. The same query on an unchanged model returns
  identical output.
- **FR-QR-7** Without `output_file`, an item's result is `{count, relationships: [...]}`.
- **FR-QR-8** With `output_file`, the entry array (bare JSON array) is written to that name in
  the exchange folder under the same rules as `inspect_view definition` (bare `.json` name
  only, no paths or symlinks, never overwrites); the item's result is
  `{output_file: <absolute path>, count}`. A rejected name or existing file is an item error.
- **FR-QR-9** Query never mutates or saves the model.
- **FR-QR-10** Optional `model` (name or ID of any open model, same lookup as
  `inspect_view definition`) chooses the model to read; the selection is not changed. An
  unknown model is the item error "Model not found: <value>". Without `model`, the selected
  open model is read.
- **FR-QR-11** `inspect_view definition` relationship entries use the same entry shape as
  FR-QR-4 (including `access_type` as a name, `influence_strength` and `directed`); they do
  not gain `views`.

### Create and update

- **FR-QR-12** `create` and `update` accept `access_type` as a name (`"Write"`, `"Read"`,
  `"Unspecified"`, `"ReadWrite"`, case-insensitive) or an integer 0 to 3 (Archi's constants:
  Write 0, Read 1, Unspecified 2, ReadWrite 3). Anything else is an item error listing the
  accepted values.
- **FR-QR-13** An Access relationship created without `access_type` has access type Write
  (`WRITE_ACCESS`, Archi's model default). Descriptions say so.
- **FR-QR-14** `create` and `update` accept `influence_strength` (string; `""` clears it) for
  Influence relationships and `directed` (boolean) for Association relationships.
- **FR-QR-15** `access_type`, `influence_strength` or `directed` given (non-null) for a
  relationship type it doesn't apply to is an item error "'<field>' does not apply to
  <Type>", the same wording as the style vocabulary. On `update`, applicability is judged
  against the type after `new_type`. The check happens before anything in that item changes
  the model.
- **FR-QR-16** Every description of `access_type` (the `manage_relationships` description
  and the create and update input schemas) lists the names and Archi's integers correctly.
  The bundled guides and docs use only values the tools accept and the correct legend.
- **FR-QR-22** `inspect_view connections` and `inspect_view get_connection` report the
  relationship's `access_type` (as a name), `influence_strength` and `directed` with the
  same rules as query. `connections` currently reports the integer; `get_connection`
  currently reports none of them.
- **FR-QR-23** When `update` changes a relationship's type (`new_type` differs from the
  current type), the old relationship's `access_type`, `influence_strength` and `directed`
  values are carried to the new relationship where they apply to the new type. Those that
  don't apply are dropped, as is the specialization (profiles belong to one concept type).
  Explicit fields in the same item are applied after the carry-over and win.
- **FR-QR-24** The item result of such an update includes `dropped: {<field>: <old value>}`
  listing every dropped value in query's output form (`access_type` always, since every
  Access relationship has one; `influence_strength` only if non-empty; `directed` only if
  true; `specialization` only if set). A specialization re-set by the same item (passing
  `specialization`) is not reported as dropped. Without anything dropped, there is no
  `dropped` key.

  Note: each of the three attributes exists on exactly one relationship type, so in
  practice a real type change always drops them rather than carries them; the carry-over
  path matters only if Archi adds a type sharing an attribute. The requirement is written
  generally so `RelationshipAttributes` stays the single place that decides.

### Create: `on_existing`

- **FR-QR-17** Before creating, `create` looks for an existing relationship of the same type
  with the same source and target (direction matters), including ones created earlier in
  the same call.
- **FR-QR-18** If one exists and `on_existing` is absent, the item fails with
  `relationship <id> already exists between these elements; pass on_existing: "reuse" or
  "add"`. With `"reuse"`, the item result is `{id, reused: true, folder_path}` for the
  existing relationship (the first in folder order if several), and nothing is created or
  changed. With `"add"`, a new relationship is created as usual. Any other value is an item
  error listing `"reuse"` and `"add"`.
- **FR-QR-19** Order of checks per item: caller `id` and `on_conflict` first (an `id` that
  already exists anywhere is decided by `on_conflict` alone and `on_existing` is not
  consulted); then ends, type, validity and field applicability; then `on_existing`. With
  `"reuse"`, a caller-supplied new `id` is not created and the result reports the existing
  relationship's ID.
- **FR-QR-20** Reused items are marked `reused: true`, the same flag
  `manage_view_content add_connection` uses for `on_existing: "reuse"`. The batch summary
  (`results: "summary"`, shape `{count, succeeded, reused, skipped, failed, errors}`, owned
  by the multiple-connections spec) counts `reused: true` entries under `reused` and
  `skipped: true` entries under `skipped`; neither counts as `succeeded`. This spec only
  emits the flag; it does not change the summary code.
- **FR-QR-21** The `manage_relationships` description documents `query`, its filters, that
  its output can be passed to `create` unchanged (inline or via `output_file` then
  `items_file`), `on_existing`, and that replaying into a model that already holds the
  relationships needs `on_conflict: "skip"` (same IDs) or `on_existing: "reuse"` (different
  IDs).

## Known limitations (out of scope)

- Replay order: entries are in folder order. A relationship whose source or target is
  another relationship can only be replayed after that relationship exists, and
  `manage_relationships create` resolves ends with `findElementById`, so it rejects
  relationship-to-relationship ends regardless. Query still reports such relationships.
- Elements are not part of query output; cross-model replay assumes the ends already exist
  in the target with the same IDs.
- `on_existing` matches on type and ends only. `"reuse"` returns the existing relationship
  even if its access type, name or properties differ from the item (as Archi's desktop
  prompt does); use `update` to change it.
- A type change drops the specialization rather than creating a same-named profile for the
  new type; pass `specialization` in the same update item to set one.

## Open questions for the user

None outstanding.

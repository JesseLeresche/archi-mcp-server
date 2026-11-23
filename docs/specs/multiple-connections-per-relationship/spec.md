# Spec: a relationship or element drawn more than once on a view

Source request: `feature-requests/archi-mcp-multiple-connections-per-relationship.md`
Target: v3.0.0 (unreleased, branch `feat/v3-bulk-authoring`)

## Background and defect

Archi lets one relationship appear as several connections on a view, and one element
appear as several figures. For example, an element can be shown twice and the relationship
drawn from each figure. The MCP assumes one drawing of each.

Checked against the source:

- `manage_view_content add_connection` looks for **any** connection of the relationship on
  the view, and if it finds one, returns it with `already_exists: true` and draws nothing.
  It does this even when the caller passes a different `source_figure_id`,
  `target_figure_id` or `connection_id`. The check runs before the `connection_id` claim, so
  an unused `connection_id` does not help.
- `results: "summary"` counts every entry without `error` as `succeeded`, so reused
  (`already_exists`) and `on_conflict: "skip"` (`skipped`) items look like successes. A
  replay can report `failed: 0` even though lines are missing.
- Every lookup that targets a connection by `relationship_id` (`update_connection`,
  `remove_connection`, `manage_appearance set_style`, `inspect_view get_connection`) acts on
  whichever connection of that relationship comes **first** in the view.
- Every lookup that targets a figure by `element_id` acts on the element's **first** figure
  in the view. This covers `manage_appearance set_style`, `remove_figure` and
  `inspect_view layout`, and also the default endpoint when `add_connection` is given no
  figure ID for one end.
- Code that should cover **every** figure of an element also stops at the first one.
  `manage_elements delete` removes only the first figure of the element on each view, so a
  second figure is left pointing at a deleted element. `get_element_analysis`
  `used_in_views` reports only the first figure on each view.
- `inspect_view connections` filtered by `relationship_id` already returns every match. Only
  its parameter description ("the connection") is misleading.
- `inspect_view definition` already lists every connection with its own `connection_id`,
  `source_figure_id` and `target_figure_id`. It is correct today.

## User story

*As an* MCP client author replaying or building views, *I want* to draw a relationship from
any figure on a view, choose explicitly what happens when a line is already drawn, and get a
clear error when an ID I pass is ambiguous, *so that* replayed views are complete and no
lines or figures are lost or changed without anyone noticing.

## Acceptance scenarios

Shared setup: a view V with figures E1 and E2 that both show element E, figure F that shows
element F, and a relationship R from E to F. "Matching drawing" is defined in FR-1.

### add_connection: figure-pair matching and on_existing

**S1: Draw the same relationship from a second figure**
Given R is drawn on V from E1 to F
When `add_connection` is called for R with `source_figure_id` E2 and `target_figure_id` F
Then a new connection from E2 to F is created and returned without `reused`
And V now has two connections for R, with different IDs

**S2: Same figure pair without on_existing fails**
Given R is drawn on V from E1 to F as connection C1
When `add_connection` is called for R with `source_figure_id` E1 and `target_figure_id` F and no `on_existing`
Then the item fails with an error that names C1 and says to pass `on_existing: "reuse"` or `"add"`
And no connection is created

**S3: on_existing "reuse"**
Given R is drawn on V from E1 to F as connection C1
When the call in S2 is made with `on_existing: "reuse"`
Then no connection is created
And the result has `connection_id` C1 and `reused: true`

**S4: on_existing "add"**
Given R is drawn on V from E1 to F as connection C1
When the call in S2 is made with `on_existing: "add"`
Then a second connection from E1 to F is created and returned with a new ID

**S5: Omitted figure IDs match any drawing**
Given R is drawn on V from E2 to F (not from E1)
When `add_connection` is called for R without `source_figure_id` or `target_figure_id`
Then the item fails with the "already drawn as connection ..." error
And with `on_existing: "reuse"` the E2 to F connection is returned with `reused: true`

**S6: One end given, the other omitted**
Given R is drawn on V from E1 to F
When `add_connection` is called for R with only `source_figure_id` E2
Then a new connection from E2 to F is created

**S7: Omitted end with an element shown twice, nothing to match**
Given R is not drawn on V
When `add_connection` is called for R without `source_figure_id`
Then the item fails with an error that lists E1 and E2 and asks for `source_figure_id`
And no connection is created

**S8: Unused connection_id with a new figure pair**
Given R is drawn on V from E1 to F
When `add_connection` is called for R with `source_figure_id` E2, `target_figure_id` F and an unused `connection_id` "c-2"
Then a connection with ID "c-2" is created from E2 to F

**S9: Invalid on_existing**
When `add_connection` is called with `on_existing: "replace"`
Then the item fails with "on_existing must be 'reuse' or 'add'"

### Replay and re-runs

**S10: Replay recreates every drawing**
Given V has R drawn from E1 to F twice and once from E2 to F
When the output of `inspect_view definition` for V is replayed into another model (create elements, create relationships, `add_figure`, then `add_connection` with `on_existing: "add"`)
Then `inspect_view definition` of the replayed view equals the original, including all three connections of R with their IDs

**S11: Re-running a replay with on_conflict "skip" creates nothing**
Given the replay in S10 has already been applied
When the same `add_connection` batch is applied again with `on_existing: "add"` and `on_conflict: "skip"`
Then no connections are created and every item reports `skipped: true` with its original ID

**S12: Re-running with on_existing "reuse"**
Given a batch that draws R from E1 to F and from E2 to F has already been applied
When the same batch is applied again with `on_existing: "reuse"`
Then no connections are created and both items report `reused: true` with their existing IDs

### Summary results

**S13: Summary separates reused, skipped and failed**
Given a batch of `add_connection` items: one draws a new line, one is reused (`on_existing: "reuse"`), one is skipped (`on_conflict: "skip"` on a taken `connection_id`, with `on_existing: "add"`), and one fails
When it runs with `results: "summary"`
Then the summary is `{count: 4, succeeded: 1, reused: 1, skipped: 1, failed: 1, errors: [...]}`

### Targeting a connection by relationship_id

**S14: Ambiguous connection target**
Given R is drawn on V twice, as C1 and C2
When `update_connection`, `remove_connection`, `manage_appearance set_style` or `inspect_view get_connection` is called with `relationship_id` R and no `connection_id`
Then the call (or item) fails with an error that says R has 2 connections on V, lists C1 and C2, and asks for `connection_id`
And nothing is changed

**S15: connection_id still targets one drawing**
Given R is drawn on V twice, as C1 and C2
When any operation in S14 is called with `connection_id` C2
Then it acts on C2 only

**S16: A single drawing is still addressable by relationship_id**
Given R is drawn on V once
When any operation in S14 is called with `relationship_id` R
Then it acts on that connection, as it does today

**S17: The connections listing returns every drawing**
Given R is drawn on V twice
When `inspect_view connections` is called with `relationship_id` R
Then both connections are returned

### Targeting a figure by element_id

**S18: Ambiguous figure target**
Given E is shown on V as E1 and E2
When `manage_appearance set_style`, `manage_view_content remove_figure` or `inspect_view layout` is called with `element_id` E and no `figure_id`
Then the call (or item) fails with an error that says E has 2 figures on V, lists E1 and E2, and asks for `figure_id`
And nothing is changed

**S19: Single figure still addressable by element_id**
Given E is shown on V once
When any operation in S18 is called with `element_id` E
Then it acts on that figure, as it does today

### Operations that cover every figure

**S20: Deleting an element removes every figure of it**
Given E is shown on V as E1 and E2
When `manage_elements delete` deletes E
Then neither E1 nor E2 remains on V, and neither do their connections

**S21: Element analysis lists every figure**
Given E is shown on V as E1 and E2
When `get_element_analysis` is called for E
Then `used_in_views` has one entry per figure (two entries for V, with `figure_id` E1 and E2)

## Functional requirements

- **FR-1** For `add_connection`, an existing connection on the view is a **matching drawing**
  when it shows the same relationship, its source figure is the item's `source_figure_id`
  (if given), and its target figure is the item's `target_figure_id` (if given). An omitted
  end matches any figure.
- **FR-2** `add_connection` accepts `on_existing: "reuse" | "add"`. Any other value fails the
  item with "on_existing must be 'reuse' or 'add'".
- **FR-3** If a matching drawing exists:
  - With no `on_existing`, the item fails with
    `"Relationship <R> is already drawn as connection <C> between these figures; pass on_existing: \"reuse\" or \"add\""`,
    and nothing is created.
  - With `"reuse"`, nothing is created and the first match (in view order) is returned with
    `reused: true`. Any `connection_id`, style or bendpoints in the item are ignored.
  - With `"add"`, the item goes on to FR-4 as if there were no match.
- **FR-4** When a connection is to be drawn, a given figure ID is used as is (validated as
  today). An omitted end uses the element's only figure on the view. If the element has more
  than one figure there, the item fails with the FR-8 error, naming `source_figure_id` or
  `target_figure_id`. If it has none, the item fails as it does today. A supplied
  `connection_id` goes through the existing claim and `on_conflict` rules (unchanged).
- **FR-5** `results: "summary"` reports `{count, succeeded, reused, skipped, failed, errors}`
  for every consolidated tool. `reused` counts entries without `error` that have
  `reused: true`. `skipped` counts entries without `error` that have
  `skipped: true`. `succeeded` counts the remaining entries without `error`.
  `count = succeeded + reused + skipped + failed`.
- **FR-6** When a connection is targeted by `relationship_id` without `connection_id`, and the
  relationship has more than one connection on the view, the operation fails (per item in
  batches) and changes nothing. The error message gives the relationship ID, the number of
  connections, every connection ID, and tells the caller to pass `connection_id`. This
  applies to `update_connection`, `remove_connection`, `manage_appearance set_style` and
  `inspect_view get_connection`.
- **FR-7** Targeting by `connection_id`, or by `relationship_id` when there is exactly one
  drawing, behaves as it does today.
- **FR-8** When a figure is targeted by `element_id` without `figure_id`, and the element has
  more than one figure on the view, the operation fails (per item in batches) and changes
  nothing. The error message gives the element ID, the number of figures, every figure ID,
  and tells the caller to pass `figure_id` (or, in `add_connection`, `source_figure_id` or
  `target_figure_id`). This applies to `manage_appearance set_style`,
  `manage_view_content remove_figure`, `inspect_view layout` and the `add_connection`
  default endpoint (FR-4).
- **FR-9** Targeting by `figure_id`, or by `element_id` when there is exactly one figure,
  behaves as it does today.
- **FR-10** `inspect_view connections` filtered by `relationship_id` returns every connection
  of that relationship on the view. The parameter description says "connections", plural.
- **FR-11** Replaying `inspect_view definition` with `on_existing: "add"` recreates every
  connection, including several drawings of one relationship (and repeated drawings between
  the same figures), with their connection IDs.
- **FR-12** `manage_elements delete` removes **every** figure of the deleted element on every
  view, together with the connections attached to those figures. Its `dry_run` counts
  include all of them.
- **FR-13** `get_element_analysis` `used_in_views` has one entry per figure. An element
  shown twice on a view gives two entries with the same `view_id`.
- **FR-14** The descriptions of `add_connection`, `on_existing`, `results` and the
  `relationship_id`/`element_id` targeting (tool descriptions, schemas, REQUIREMENTS.md,
  README) document FR-1 to FR-9.

## Decisions (made by the user)

- Re-drawing an existing figure pair needs an explicit `on_existing`. Without it the item
  fails, so a caller cannot draw a duplicate line or quietly no-op by accident.
- An omitted figure ID means "any figure" when matching (FR-1). When a line has to be drawn,
  an omitted figure ID must resolve to exactly one figure (FR-4, FR-8).
- An ambiguous `relationship_id` or `element_id` target is an error, never "act on first"
  and never "act on all".
- The summary separates `reused` and `skipped` from `succeeded`. This changes the v3
  summary shape (`succeeded` gets narrower and two fields are added). v3.0.0 is unreleased,
  so there is no compatibility shim.
- This spec owns `element_id` figure targeting across all tools. The dangling-connections
  spec's `remove_figure` first-match requirement has been dropped in favour of FR-8.
- `on_existing` on `manage_relationships create` belongs to the query-relationships spec. It
  marks reused items with `reused: true` so FR-5 counts them.
- The per-item flag `already_exists` is renamed to `reused`, to pair with `skipped` and the
  summary field. v3.0.0 is unreleased, so there is no shim.

## Behaviour changes for existing clients

- A plain `add_connection` re-run (no `on_existing`) used to return `already_exists`. It now
  fails per item. Re-runs need `on_existing: "reuse"`, which returns `reused: true`.
- The per-item `already_exists` flag no longer appears anywhere. It is replaced by `reused`.
- `set_style`, `remove_figure`, `inspect_view layout`, `update_connection`,
  `remove_connection` and `get_connection` now fail where they used to act on the first
  figure or connection.

## Out of scope

- Dangling connections after figure removal. That is covered by
  `docs/specs/figure-removal-dangling-connections/`.

## Open questions for the user

None.

# {View Type} View — Agent Generation Guide

> **Template.** Copy this file to
> `za.co.jesseleresche.archi.mcp/src/resources/guides/{view-type}-view-agent-guide.md`,
> replace every `{...}` placeholder, and delete the blockquote hints (like this one) once each
> section has real content. See [CONTRIBUTING.md](../CONTRIBUTING.md#adding-a-new-view-type-guide)
> for the full checklist, including how to register the guide so agents can fetch it.

Quick-reference for an AI agent generating ArchiMate {View Type} Views via the Archi MCP tools.

---

## 1. Concept

> One paragraph. What real-world question does this view answer? What's the central element or
> relationship it revolves around? Someone who has never seen this view type should understand
> *what it's for* after reading this paragraph alone — see the existing guides' "Concept" sections
> for the level of detail expected.

---

## 2. Element Types

> A table of every ArchiMate element type used in this view, with a one-line note on what role it
> plays. If the view is organized into layers or zones (like the Layered View), break this into
> one sub-table per layer/zone instead of a single flat table.

| Element | ArchiMate Type | Notes |
|---------|---------------|-------|
| {what it represents} | `{ArchiMateType}` | {when/why you'd use it} |

---

## 3. Relationships

> Which relationship types are valid in this view, in which direction, and where. List only the
> relationship types that are actually allowed — an agent should not have to guess whether
> `AssociationRelationship` is acceptable if it isn't.

| Relationship | Type | Direction | Where used |
|---|---|---|---|
| {name} | `{RelationshipType}` | {source} → {target} | {context} |

### Canonical chain / structure

> A short ASCII diagram or ordered list showing the typical relationship chain from one end of
> the view to the other (see the Layered View's "Canonical vertical chain" for the pattern).

```
{Element A} --[{relationship}]--> {Element B}
```

### Rules

> A bullet list of hard constraints — direction rules, "do not skip X", which relationship types
> are forbidden and what to use instead. These get echoed in the Common Mistakes table later, so
> keep them precise and testable.

- {rule}

---

## 4. Nesting / Layer Groups

> Only include this section if elements are visually nested inside a container (a parent
> component, a Node, or a Grouping band). Describe what the container is, how children are placed
> inside it via `parent_figure_id`, and any sizing/arrangement rules. Omit this section entirely
> if the view has no nesting.

### Layout inside the parent

> Left-to-right/top-to-bottom ordering, spacing rules, what goes where.

---

## 5. Connector Routing

> How connections should be routed on the canvas — orthogonal vs straight, which edge of the
> figure they attach to. Keep it to a short table; this is about visual routing, not relationship
> semantics (that's Section 3).

| Connection type | Routing |
|---|---|
| {relationship type} | {orthogonal/straight, attachment points} |

---

## 6. Styling

### Element fills

> Fill/border colors per element type or layer, as hex codes, so the agent can call
> `manage_appearance set_figure` deterministically instead of guessing.

| Element / Layer | Fill | Border |
|-------|------|--------|
| {type/layer} | `#RRGGBB` | `#RRGGBB` |

### Connectors

> Line style (solid/dashed) and arrowhead per relationship type.

| Type | Line | Arrowhead at target |
|------|------|-------------------|
| {relationship} | {solid/dashed} | {arrow style} |

---

## 7. MCP Tool Call Sequence

> The exact, ordered sequence of `manage_*` tool calls an agent should make to build this view
> from scratch, batching where the existing tools support arrays. Number every step — agents
> follow this literally. Call out anything that must NOT be done (e.g. the Layered View guide
> warns against calling `layout_view`, since auto-layout would destroy the nesting structure).

```
1. manage_elements      create   → ...
2. manage_relationships create   → ...
3. manage_views         create   → the view
4. manage_view_content   add_element → ...
5. manage_view_content   add_relationship → ...
6. manage_appearance     set_figure → ...
```

---

## 8. Naming Convention

> Patterns for view names, view documentation, and element names, so multiple agent runs produce
> consistently named artifacts.

| Artefact | Pattern |
|----------|---------|
| View name | `{Subject} {View Type} View` |
| View documentation | `{template sentence}` |
| Element names | {guidance, e.g. avoid generic placeholder names} |

---

## 9. Minimum Valid View Checklist

> A checklist an agent can tick off before returning its result, covering structural completeness
> (all required elements/containers present), relationship correctness (no skipped hops, correct
> direction), and visual correctness (no overlaps, correct nesting). Model this on the existing
> guides' checklists.

- [ ] {check}

---

## 10. Minimal Example (from canonical diagram)

> A complete, minimal, valid worked example: the JSON payloads an agent would send to
> `manage_elements create`, `manage_relationships create`, etc., for the smallest version of this
> view that satisfies the checklist above. This is the fastest way for an agent (or a human
> reviewer) to sanity-check the guide against the actual tool schemas.

### Elements to create

```json
[
  { "name": "{Example Name}", "type": "{ArchiMateType}" }
]
```

### Relationships to create

```json
[
  { "source": "{Example Name}", "target": "{Example Name}", "type": "{RelationshipType}" }
]
```

---

## 11. Common Mistakes

> Pairs of (wrong, correct) drawn directly from the Rules in Section 3 and the Checklist in
> Section 9 — the things agents actually get wrong when they don't have this guide. Keep it
> concrete; "wrong" should be a specific mistake, not a vague warning.

| Wrong | Correct |
|-------|---------|
| {common mistake} | {correct approach} |

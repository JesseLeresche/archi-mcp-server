# Data Model View — Agent Generation Guide

Quick-reference for an AI agent generating ArchiMate Data Model Views via the Archi MCP tools.

---

## 1. Concept

A Data Model View shows the internal structure of a single logical data store (e.g. a database) as a set of **Data Objects**, one per table/entity, nested inside a parent Data Object that represents the store itself. Relationships between the nested Data Objects describe cardinality-style structure (whole/part, or a loose association) — the same relationships an ER diagram would show, expressed in ArchiMate terms.

---

## 2. Element Types

| Element | ArchiMate Type | Notes |
|---------|----------------|-------|
| Data store / database | `DataObject` | Outer container, named e.g. `Database A` |
| Table / entity | `DataObject` | One per table, named e.g. `Database Table A-1`, nested inside the store |

Only one ArchiMate type is used throughout this view — everything is a `DataObject`, whether it represents the whole database or a single table. Naming (and nesting) is what distinguishes container from contents, not element type.

---

## 4. Relationships

Only two relationship types are used between tables. The store-to-table containment itself is expressed purely by **visual nesting** on the view (see Section 5) — the canonical diagram does not draw an explicit line from the store to each table.

| Relationship | Type | Meaning | Notation |
|---|---|---|---|
| **Aggregation** | `AggregationRelationship` | Whole/part where the part *can* exist independently (e.g. a lookup table referenced by another table) | Hollow diamond at the whole (source) end |
| **Association** | `AssociationRelationship` | A loose structural link with no ownership implication (e.g. a foreign-key-style reference) | Plain line, no diamond, no arrowhead |

### Canonical structure

```
Database A (DataObject)                    ← container, nested figures only, no explicit relationship to children
 ├─ Database Table A-1 (DataObject)
 ├─ Database Table A-2 (DataObject)
 └─ Database Table A-3 (DataObject)

Database Table A-1  --[aggregation]-->  Database Table A-2   (A-1 is the whole/source; diamond at A-1)
Database Table A-2  --[association]-->  Database Table A-3   (plain link, no direction implied)
```

### Rules
- Do not invent a Composition/Aggregation relationship from the store to each table unless the source ER model explicitly calls for one — the canonical pattern relies on nesting alone for store→table containment.
- Aggregation direction matters: the diamond sits at the table that is the "whole" (the source in `manage_relationships create`), pointing at the "part" table.
- Use Association only for loose references with no whole/part semantics; do not use it as a catch-all in place of Aggregation when the source model actually implies ownership.

---

## 5. Nesting (Store as Container)

1. Create the parent (store) `DataObject` and all table `DataObject`s with `manage_elements create`.
2. Add the parent's figure to the view first with `manage_view_content add_element`, noting its returned figure ID.
3. Add each table's figure with `parent_figure_id` set to the parent's figure ID.
4. Size the parent figure to contain all table figures with margin on every side.

### Layout inside the parent
- Tables are arranged in a loose grid, ordered to keep aggregation/association lines short and non-crossing where possible.
- Leave enough gap between table figures for the connecting lines and their labels to render without overlapping figures.

---

## 6. Connector Routing

| Connection type | Routing |
|---|---|
| Aggregation | Straight, from edge of whole to edge of part |
| Association | Straight, from edge of source to edge of target |

---

## 7. Styling

### Element fills

| Element | Fill | Border |
|---|---|---|
| Store `DataObject` (container) | `#E6FFFF` | `#333333` |
| Table `DataObject` (nested) | `#FFFFFF` | `#333333` |

### Connectors

| Type | Line | Marker |
|------|------|--------|
| Aggregation | solid | hollow diamond at source (whole) end, no arrowhead at target |
| Association | solid | no diamond, no arrowhead (plain line) |

---

## 8. MCP Tool Call Sequence

Execute in this order. Do not reorder steps.

```
1. manage_elements      create → parent (store) DataObject
2. manage_elements      create → table DataObjects (batch array)
3. manage_relationships create → Aggregation/Association links between tables (per Section 4)
4. manage_views         create → the view
5. manage_view_content  add_element → parent (store) figure
6. manage_view_content  add_element → table figures, each with parent_figure_id = parent figure ID
7. manage_view_content  add_relationship → table-to-table connections
8. manage_appearance    set_figure → apply fill/border colours per Section 7
```

Do NOT call `layout_view` on a Data Model View — auto-layout will pull table figures out of the parent store's nested boundary.

---

## 9. Naming Convention

| Artefact | Pattern |
|----------|---------|
| View name | `{Store} Data Model View` |
| View documentation | `Data model showing the internal table structure of {store}.` |
| Table names | Use the real table name, not generic `A-1` style, and keep the `(Data Object)` suffix out of the element `name` field — it was only a diagram label in the source figure |

---

## 10. Minimum Valid View Checklist

Before returning, confirm:

- [ ] Parent store `DataObject` figure is present and sized to contain all table figures
- [ ] Every table figure has the parent's figure as its `parent_figure_id`
- [ ] No explicit relationship was created from the store to its tables (containment is visual nesting only)
- [ ] Aggregation relationships have the diamond end (source) on the correct "whole" table
- [ ] Association used only for genuinely ownership-free links
- [ ] No table figure overlaps another table figure
- [ ] `layout_view` was not called

---

## 11. Minimal Example (from canonical diagram)

```json
// Elements
[
  { "name": "Database A",         "type": "DataObject" },
  { "name": "Database Table A-1", "type": "DataObject" },
  { "name": "Database Table A-2", "type": "DataObject" },
  { "name": "Database Table A-3", "type": "DataObject" }
]
```

```json
// Relationships
[
  { "source_id": "Database Table A-1", "target_id": "Database Table A-2", "type": "AggregationRelationship" },
  { "source_id": "Database Table A-2", "target_id": "Database Table A-3", "type": "AssociationRelationship" }
]
```

---

## 12. Common Mistakes

| Wrong | Correct |
|-------|---------|
| Creating a Composition/Aggregation from the store to every table | Containment is visual nesting only (`parent_figure_id`) — no model relationship needed |
| Putting the diamond at the "part" end of an aggregation | Diamond always sits at the "whole" (source) end |
| Using `AssociationRelationship` where the source model shows ownership | Use `AggregationRelationship` for whole/part links |
| Appending `(Data Object)` to the element `name` | That text was a diagram label in the source figure, not part of the element name |
| Placing table figures outside the store's boundary | Nest every table figure inside the store figure via `parent_figure_id` |
| Calling `layout_view` after nesting tables | Auto-layout breaks nested figure containment — omit this step |

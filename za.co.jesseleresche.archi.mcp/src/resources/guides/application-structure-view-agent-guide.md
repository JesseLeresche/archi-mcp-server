# Application Structure View — Agent Generation Guide

Quick-reference for an AI agent generating ArchiMate Application Structure Views via the Archi MCP tools.

---

## 1. Concept

An Application Structure View decomposes a single **composite Application Component** into its internal sub-components, shows which **Application Service** each sub-component realizes, and shows which sub-component **accesses** which piece of data. It answers: "what is this application made of, what does each part expose, and what does each part touch?"

---

## 2. Element Types

| Element | ArchiMate Type | Notes |
|---------|----------------|-------|
| Composite/parent application | `ApplicationComponent` | The outer container (e.g. `Application Component A`) |
| Internal sub-component | `ApplicationComponent` | One per internal part (e.g. `Application Component A-1`) |
| Exposed interface/API per sub-component | `ApplicationService` | One realized service per sub-component that exposes one |
| Data touched by a sub-component | `DataObject` | Only sub-components that read/write data get an access relationship |

---

## 3. Relationships

Three relationship types are used.

| Relationship | Type | Direction | Where used |
|---|---|---|---|
| **Composition** | `CompositionRelationship` | Parent component → sub-component | Whole/part — sub-component cannot exist outside the parent |
| **Realization** | `RealizationRelationship` | Sub-component → Application Service it exposes | Sub-component to its own service only |
| **Access** | `AccessRelationship` `access_type=ReadWrite` (or `Read`/`Write` if the source doc specifies) | Sub-component → Data Object | Only where the sub-component actually touches data |

### Canonical chain

```
Application Component A          (parent, composite)
 ├─ Application Component A-1  --[realization]--> Application Service A-1
 ├─ Application Component A-2  --[realization]--> Application Service A-2
 └─ Application Component A-3  --[realization]--> Application Service A-3
                                --[access RW]-----> Data Object A-3
```

### Rules
- Not every sub-component needs a service or a data access — only add the relationship if the source material shows it (e.g. A-1 and A-2 have no data access in the canonical example).
- Realization always points from the sub-component to the service it exposes — never from the parent component directly.
- Access relationships originate at the sub-component that actually performs the read/write, not at the parent.
- Do not draw a relationship between the parent component and the Application Services — that link is implied through the sub-component.

---

## 4. Nesting (Composition on the View)

Sub-components are drawn **physically inside** the parent component's figure on the view, not beside it.

1. Create the parent `ApplicationComponent` and all sub-`ApplicationComponent`s with `manage_elements create`.
2. Create a `CompositionRelationship` in the model from the parent to each sub-component with `manage_relationships create` (`source_id` = parent, `target_id` = sub-component).
3. Add the parent's figure to the view first with `manage_view_content add_element`, noting its returned figure ID.
4. Add each sub-component's figure with `parent_figure_id` set to the parent's figure ID, so it renders nested inside the parent's boundary.
5. Size the parent figure wide/tall enough to contain all sub-component figures with margin — sub-component figures must not touch or cross the parent's border.

### Layout inside the parent
- Sub-components are arranged left-to-right, evenly spaced, inside the parent.
- Application Services are placed **above** the parent component, one per sub-component, roughly centred over the sub-component they realize.
- The Data Object is placed **outside and to the right** of the parent component, at the same vertical band as the sub-components.

---

## 5. Connector Routing

| Connection type | Routing |
|---|---|
| Composition | N/A (expressed by nesting) |
| Realization | Orthogonal, centre-top of sub-component to centre-bottom of service |
| Access | Straight, right-edge of sub-component to left-edge of data object |

---

## 6. Styling

### Element fills

| Element | Fill | Border |
|---|---|---|
| Parent `ApplicationComponent` | `#CCFFFF` | `#006666` |
| Sub `ApplicationComponent` | `#CCFFFF` | `#006666` |
| `ApplicationService` | `#CCFFFF` | `#006666` |
| `DataObject` | `#FFFFFF` | `#666666` |

### Connectors

| Type | Line | Arrowhead at target |
|------|------|---------------------|
| Composition | solid | filled diamond at parent (source) end |
| Realization | dashed | open triangle |
| Access ReadWrite | dashed | open arrow |

---

## 7. MCP Tool Call Sequence

Execute in this order. Do not reorder steps.

```
1. manage_elements      create → parent ApplicationComponent
2. manage_elements      create → sub-ApplicationComponents (batch array)
3. manage_elements      create → ApplicationServices (batch array)
4. manage_elements      create → DataObject(s) referenced
5. manage_relationships create → Composition: parent -> each sub-component
6. manage_relationships create → Realization: each sub-component -> its ApplicationService
7. manage_relationships create → Access: sub-component(s) -> DataObject(s)
8. manage_views         create → the view
9. manage_view_content  add_element → parent component figure
10. manage_view_content add_element → sub-component figures, each with parent_figure_id = parent figure ID
11. manage_view_content add_element → ApplicationService and DataObject figures
12. manage_view_content add_relationship → all connections
13. manage_appearance   set_figure → apply fill/border colours per Section 6
```

Do NOT call `layout_view` on an Application Structure View — auto-layout will pull sub-component figures out of the parent's nested boundary.

---

## 8. Naming Convention

| Artefact | Pattern |
|----------|---------|
| View name | `{Component} Structure View` |
| View documentation | `Structural decomposition of {component}, its exposed services and data access.` |
| Sub-component names | `{Parent Name} {Suffix}` (e.g. `Payments Engine — Ledger Module`), not generic `A-1` style in real models |

---

## 9. Minimum Valid View Checklist

Before returning, confirm:

- [ ] Parent `ApplicationComponent` figure is present and sized to contain all sub-components
- [ ] Every sub-component figure has the parent's figure as its `parent_figure_id`
- [ ] Every sub-component that exposes a service has a Realization to that service
- [ ] Every sub-component that touches data has an Access relationship (correct `access_type`)
- [ ] No relationship connects the parent component directly to a service or data object
- [ ] No sub-component figure overlaps another sub-component figure
- [ ] `layout_view` was not called

---

## 10. Minimal Example (from canonical diagram)

```json
// Elements
[
  { "name": "Application Component A",   "type": "ApplicationComponent" },
  { "name": "Application Component A-1", "type": "ApplicationComponent" },
  { "name": "Application Component A-2", "type": "ApplicationComponent" },
  { "name": "Application Component A-3", "type": "ApplicationComponent" },
  { "name": "Application Service A-1",   "type": "ApplicationService" },
  { "name": "Application Service A-2",   "type": "ApplicationService" },
  { "name": "Application Service A-3",   "type": "ApplicationService" },
  { "name": "Data Object A-3",           "type": "DataObject" }
]
```

```json
// Relationships
[
  { "source_id": "Application Component A",   "target_id": "Application Component A-1", "type": "CompositionRelationship" },
  { "source_id": "Application Component A",   "target_id": "Application Component A-2", "type": "CompositionRelationship" },
  { "source_id": "Application Component A",   "target_id": "Application Component A-3", "type": "CompositionRelationship" },
  { "source_id": "Application Component A-1", "target_id": "Application Service A-1",   "type": "RealizationRelationship" },
  { "source_id": "Application Component A-2", "target_id": "Application Service A-2",   "type": "RealizationRelationship" },
  { "source_id": "Application Component A-3", "target_id": "Application Service A-3",   "type": "RealizationRelationship" },
  { "source_id": "Application Component A-3", "target_id": "Data Object A-3",           "type": "AccessRelationship", "access_type": "ReadWrite" }
]
```

---

## 11. Common Mistakes

| Wrong | Correct |
|-------|---------|
| Drawing a service realization from the parent component | Realization comes from the sub-component that owns the capability |
| Placing sub-component figures beside the parent instead of nested inside | Use `parent_figure_id` so sub-components render inside the parent's boundary |
| Giving every sub-component a Data Object access | Only add Access where the source material shows the sub-component touches data |
| Using `AssociationRelationship` for the parent/sub-component link | Use `CompositionRelationship` — parts cannot exist outside the whole |
| Calling `layout_view` after nesting sub-components | Auto-layout breaks nested figure containment — omit this step |

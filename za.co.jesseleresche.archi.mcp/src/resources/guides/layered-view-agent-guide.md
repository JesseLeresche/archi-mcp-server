# Layered View — Agent Generation Guide

Quick-reference for an AI agent generating ArchiMate Layered Views via the Archi MCP tools.

---

## 1. Concept

A Layered View traces the technology stack top-to-bottom: external actors → business services → processes → application services → applications → technology services → infrastructure. Each layer **serves** the one above it and **realizes** the service it exposes.

---

## 2. Layers (top → bottom)

| # | Label | Purpose |
|---|-------|---------|
| 1 | External Roles and Actors | Customers, partners — who receives value |
| 2 | Business Services | Services the organisation exposes externally |
| 3 | Processes and Internal Roles and Actors | How services are delivered; who does the work; what data is touched |
| 4 | Application Services | GUI / API interfaces supporting processes |
| 5 | Application and Data | Software components + data assets |
| 6 | Technology- and Infrastructure Services | Platform, network, DNS, capacity services |
| 7 | Infrastructure | Servers, nodes, devices, deployed artifacts |

---

## 3. Element Types per Layer

### Layer 1 — External Roles and Actors
| Element | ArchiMate Type |
|---------|---------------|
| External person / organisation | `BusinessActor` |
| Role they play (e.g. Customer) | `BusinessRole` |

### Layer 2 — Business Services
| Element | ArchiMate Type |
|---------|---------------|
| Service offered to external roles | `BusinessService` |

### Layer 3 — Processes and Internal Roles and Actors
| Element | ArchiMate Type |
|---------|---------------|
| Internal staff / system actor | `BusinessActor` |
| Internal role | `BusinessRole` |
| Work step / activity | `BusinessProcess` |
| Business information entity | `BusinessObject` |

### Layer 4 — Application Services
| Element | ArchiMate Type |
|---------|---------------|
| User interface or API | `ApplicationService` |

### Layer 5 — Application and Data
| Element | ArchiMate Type |
|---------|---------------|
| Software component / system | `ApplicationComponent` |
| Data entity / dataset | `DataObject` |

### Layer 6 — Technology Services
| Element | ArchiMate Type |
|---------|---------------|
| Platform / infra service | `TechnologyService` |

### Layer 7 — Infrastructure
| Element | ArchiMate Type |
|---------|---------------|
| Server / platform / device | `Node` |
| Deployed binary / config | `Artifact` |

---

## 4. Relationships

Only four relationship types are used. Direction is fixed.

| Relationship | Type | Direction | Where used |
|---|---|---|---|
| **Assignment** | `AssignmentRelationship` | Actor → Role or Actor → Process | Layers 1 and 3 (horizontal) |
| **Serving** | `ServingRelationship` | Lower element → Upper element | Cross-layer vertical (upward) |
| **Realization** | `RealizationRelationship` | Process/Component/Node → Service it fulfils | Cross-layer vertical (upward) |
| **Access** | `AccessRelationship` accessType=`ReadWrite` | Process/Component/Node → Data object | Within-layer horizontal |

### Canonical vertical chain

```
Node               --[realization]--> TechnologyService
TechnologyService  --[serving]-----> ApplicationComponent
ApplicationComponent --[realization]--> ApplicationService
ApplicationService --[serving]-----> BusinessProcess
BusinessProcess    --[realization]--> BusinessService
BusinessService    --[serving]-----> BusinessRole (external)
```

### Canonical horizontal (within-layer) access chain

```
BusinessProcess    --[access RW]--> BusinessObject       (Layer 3)
ApplicationComponent --[access RW]--> DataObject         (Layer 5)
Node               --[access RW]--> Artifact             (Layer 7)
```

### Rules
- Serving arrows point **upward** (lower layer → upper layer).
- Realization arrows point **upward** toward the realized service.
- Access arrows are **horizontal** within the same layer band.
- Assignment arrows are **horizontal** within the same layer band.
- Do **not** skip layers.

---

## 5. Layer Groups

Each layer is represented on the view as a **Grouping rectangle** (ArchiMate `Grouping` element). All domain elements belonging to that layer are placed **inside** their group on the view.

### Group properties

| Property | Value |
|----------|-------|
| ArchiMate type | `Grouping` |
| Name | Exact layer label from Section 2 (e.g. `Business Services`) |
| One group per layer | Always — even if a layer has only one element |

### How to create groups

1. Create a `Grouping` element in the model for each layer using `manage_elements create`.
2. Add each group to the view using `manage_view_content add_element`.
3. Add domain elements to the view using `manage_view_content add_element` with `parent_figure_id` set to the group's figure ID so they are nested inside it.

### Group sizing and arrangement
- Groups are stacked **top to bottom** in the order defined in Section 2 with no gaps between them.
- Each group spans the **full width** of the diagram.
- Each group is tall enough to contain all its elements without overlap.
- Groups do not overlap each other.

### Element placement within a group
- Elements are placed **left to right** within their group.
- Process/service elements go in the centre; actors/roles to the left; data objects to the right.
- Multiple elements in the same layer are spaced evenly with a consistent gap between them.

---

## 6. Connector Routing

| Connection type | Routing |
|---|---|
| Vertical (serving / realization) | Orthogonal, centre-top to centre-bottom of adjacent elements |
| Horizontal (access / assignment) | Straight, right-edge of source to left-edge of target |

---

## 7. Styling

### Element fills

| Layer | Fill | Border |
|-------|------|--------|
| Business (1–3) | `#FFFFCC` | `#999900` |
| Application (4–5) | `#FFFFFF` | `#666666` |
| Technology (6–7) | `#CCFFCC` | `#009900` |

### Layer groups (Grouping elements)
- Border: dotted, `#999999`
- Fill: transparent
- Label: top-left corner, `#333333`

### Connectors

| Type | Line | Arrowhead at target |
|------|------|-------------------|
| Assignment | solid | filled circle at source |
| Serving | solid | open arrow |
| Realization | dashed | open triangle |
| Access ReadWrite | dashed | open arrow (both ends for RW) |

---

## 8. MCP Tool Call Sequence

Execute in this order. Do not reorder steps.

```
1. manage_elements      create   → all Grouping elements (one per layer, names from Section 2)
2. manage_elements      create   → all domain elements (batch array)
3. manage_relationships create   → all relationships (batch array, order per Section 4)
4. manage_views         create   → the view
5. manage_view_content  add_element → all 7 layer groups (Grouping figures)
6. manage_view_content  add_element → all domain element figures, each with parent_figure_id = their layer group's figure ID
7. manage_view_content  add_relationship → all connections
8. manage_appearance    set_figure → layer groups: dotted border #999999, transparent fill (per Section 7)
9. manage_appearance    set_figure → domain elements: fill/border colours per layer (per Section 7)
```

Do NOT call `layout_view` on a Layered View — auto-layout will destroy the layer group structure. Element positions must respect the group nesting defined in Section 5.

---

## 9. Naming Convention

| Artefact | Pattern |
|----------|---------|
| View name | `{Subject} Layered View` |
| View documentation | `Layered view showing the full stack for {subject/capability}.` |
| Element names | Use domain terminology; avoid generic names like "System A" |

---

## 10. Minimum Valid View Checklist

Before returning, confirm:

- [ ] All 7 layer groups (Grouping elements) are present on the view
- [ ] Every domain element figure has a layer group as its parent figure
- [ ] All 7 layer groups have at least one domain element inside them
- [ ] Continuous serving/realization chain from Layer 7 up to Layer 1
- [ ] No relationship crosses more than one layer
- [ ] All Access relationships are horizontal (same layer)
- [ ] All Serving relationships point upward
- [ ] All Realization relationships point upward
- [ ] No element figures overlap
- [ ] Layer group rectangles cover all elements in their band
- [ ] No element figure is placed outside a layer group

---

## 11. Minimal Example (from canonical diagram)

### Layer groups to create

```json
[
  { "name": "External Roles and Actors",                    "type": "Grouping" },
  { "name": "Business Services",                            "type": "Grouping" },
  { "name": "Processes and Internal Roles and Actors",      "type": "Grouping" },
  { "name": "Application Services",                         "type": "Grouping" },
  { "name": "Application and Data",                         "type": "Grouping" },
  { "name": "Technology- and Infrastructure Services",      "type": "Grouping" },
  { "name": "Infrastructure",                               "type": "Grouping" }
]
```

### Domain elements to create

```json
[
  { "name": "Business Actor",        "type": "BusinessActor"    },
  { "name": "Customer",              "type": "BusinessRole"     },
  { "name": "Business Service A",    "type": "BusinessService"  },
  { "name": "Business Actor",        "type": "BusinessActor"    },
  { "name": "Business Process A",    "type": "BusinessProcess"  },
  { "name": "Business Object",       "type": "BusinessObject"   },
  { "name": "Application Service A", "type": "ApplicationService"   },
  { "name": "Application Component A","type": "ApplicationComponent" },
  { "name": "Data Object",           "type": "DataObject"       },
  { "name": "Technology Service A",  "type": "TechnologyService" },
  { "name": "Platform A",            "type": "Node"             },
  { "name": "Artifact",              "type": "Artifact"         }
]
```

### Relationships to create

```json
[
  { "source": "Business Actor (ext)", "target": "Customer",              "type": "AssignmentRelationship"  },
  { "source": "Business Service A",   "target": "Customer",              "type": "ServingRelationship"     },
  { "source": "Business Process A",   "target": "Business Service A",    "type": "RealizationRelationship" },
  { "source": "Business Actor (int)", "target": "Business Process A",    "type": "AssignmentRelationship"  },
  { "source": "Business Process A",   "target": "Business Object",       "type": "AccessRelationship", "access_type": "ReadWrite" },
  { "source": "Application Service A","target": "Business Process A",    "type": "ServingRelationship"     },
  { "source": "Application Component A","target": "Application Service A","type": "RealizationRelationship"},
  { "source": "Application Component A","target": "Data Object",         "type": "AccessRelationship", "access_type": "ReadWrite" },
  { "source": "Technology Service A", "target": "Application Component A","type": "ServingRelationship"   },
  { "source": "Platform A",           "target": "Technology Service A",  "type": "RealizationRelationship" },
  { "source": "Platform A",           "target": "Artifact",              "type": "AccessRelationship", "access_type": "ReadWrite" }
]
```

---

## 12. Common Mistakes

| Wrong | Correct |
|-------|---------|
| `DataObject` in Layer 3 | `DataObject` belongs in Layer 5 |
| `ServingRelationship` pointing downward | Always source=lower, target=upper |
| `ApplicationComponent` → `BusinessProcess` directly (skips Layer 4) | Insert `ApplicationService` between them |
| `AssociationRelationship` for data access | Use `AccessRelationship` with `ReadWrite` |
| Missing external Actor → Role assignment | Layer 1 must show Actor + Role + assignment |
| `RealizationRelationship` for TechnologyService → ApplicationComponent | Use `ServingRelationship` here |
| Placing domain elements on the view without a parent group | Always nest element figures inside their layer `Grouping` figure via `parent_figure_id` |
| Using a plain rectangle or note as a layer band | Use ArchiMate `Grouping` type, not a generic shape |
| Calling `layout_view` after building the diagram | Auto-layout breaks layer group nesting — omit this step |

# Infrastructure View — Agent Generation Guide

Quick-reference for an AI agent generating ArchiMate Infrastructure Views via the Archi MCP tools.

---

## 1. Concept

An Infrastructure View shows how a **Node** (a piece of compute infrastructure) is built up from its **System Software** and **Device** parts, how that Node realizes a **Technology Service** consumed by an **Application Component**, and how a deployed **Artifact** realizes that same Application Component. It answers: "what does this application run on, and what physical/virtual thing implements the deployed code?"

---

## 2. Element Types

| Element | ArchiMate Type | Notes |
|---------|----------------|-------|
| Software this infrastructure supports | `ApplicationComponent` | Usually already exists from another view — link into it, don't recreate it |
| Deployed binary/config | `Artifact` | What realizes the application component |
| Platform/infra service exposed | `TechnologyService` | Realized by the Node |
| Compute node | `Node` | Container for System Software and Device |
| Runtime environment | `SystemSoftware` | e.g. "Platform sw" — the runtime configuration used by the application |
| Operating system | `SystemSoftware` | Modelled the same type as Platform sw, named as an OS |
| Physical/virtual device | `Device` | Hardware or VM the OS/platform runs on |
| Network | `CommunicationNetwork` | What the Device connects over |

---

## 3. Relationships

Six relationship types are used. This is the densest of the three guides — get the direction of each one right, several are easy to invert.

| Relationship | Type | Direction | Where used |
|---|---|---|---|
| **Realization** | `RealizationRelationship` | Node → Technology Service | Node fulfils the platform service |
| **Realization** | `RealizationRelationship` | Artifact → Application Component | Deployed artifact implements the app |
| **Serving** | `ServingRelationship` | Technology Service → Application Component | The service supports the app |
| **Serving** | `ServingRelationship` | Operating System → Platform sw | OS underpins the runtime/platform software |
| **Assignment** | `AssignmentRelationship` | Node → Artifact | Node hosts/executes the artifact |
| **Assignment** | `AssignmentRelationship` | Device → Operating System | Device runs the OS |
| **Assignment** | `AssignmentRelationship` | Device → Platform sw | Device runs the platform software directly |
| **Aggregation** | `AggregationRelationship` | Node → Platform sw | Node is composed of its platform software |
| **Aggregation** | `AggregationRelationship` | Node → Operating System | Node is composed of its OS |
| **Aggregation** | `AggregationRelationship` | Node → Device | Node is composed of its device |
| **Association** | `AssociationRelationship` | Device → Communication Network | Loose connectivity link |

### Canonical diagram

```
                        Application Component
                          ▲               ▲
                   serving│               │realization
                          │               │
              Technology Service      Artifact
                          ▲               ▲
              realization│               │assignment
                          │               │
                        Node ─────assignment────┐
                          │  \aggregation        │
              aggregation │   \                  │
                          │    \                 │
                    Operating System ──serving──► Platform sw
                          │                       ▲
                    assignment                assignment
                          │                       │
                        Device ───────────────────┘
                          │
                    association
                          │
                Communication Network
```

### The "serving (derived)" line
The source diagram also shows a **dashed "serving (derived)" line directly from Platform sw to Application Component**. This is a **derived relationship** — ArchiMate infers it automatically from the Assignment/Aggregation/Serving chain (Device → Platform sw → Application Component). **Do not create it explicitly** with `manage_relationships create`; it is shown in source material for readability only, not as a relationship to model.

### Rules
- Realization always points from the concrete/lower thing to the abstract thing it fulfils (Node → its Service; Artifact → the Component it implements).
- Serving always points from the server (provider) to the served (consumer) — Technology Service serves the Application Component, Operating System serves Platform sw.
- Aggregation diamonds sit at the Node — Node is the whole; Platform sw, Operating System, and Device are parts nested inside it.
- Assignment is used for active-to-passive/executes-on links: Node assigns the Artifact, Device assigns both System Software elements it runs.
- Do not model the Platform-sw-to-Application-Component "serving (derived)" line explicitly.

---

## 4. Nesting (Node as Container)

Per the canonical pattern, System Software and Device **can be nested inside the Node** figure on the view (this is the modelling convention this view follows, not a strict ArchiMate requirement).

1. Create the `Node`, both `SystemSoftware` elements, and the `Device` with `manage_elements create`.
2. Create the Aggregation relationships from Node to each of them with `manage_relationships create`.
3. Add the Node's figure to the view first with `manage_view_content add_element`, noting its returned figure ID.
4. Add the Platform sw, Operating System, and Device figures with `parent_figure_id` set to the Node's figure ID.
5. Artifact, Technology Service, Application Component, and Communication Network are **not** nested — they sit outside the Node as independent figures, connected by lines.

### Layout
- Application Component at the top.
- Technology Service and Artifact below it, roughly left/right — Technology Service left-of-centre, Artifact far left.
- Node (with Platform sw, Operating System, Device nested inside) below that, centred under Technology Service.
- Communication Network to the right, at the same vertical band as Device.
- Two documentation notes (optional) can be attached near the Node/Artifact area: one explaining that System Software and Device can be nested in Node, one explaining that Platform sw represents the runtime environment used by the application.

---

## 5. Connector Routing

| Connection type | Routing |
|---|---|
| Aggregation | N/A (expressed by nesting inside Node) |
| Realization | Orthogonal, centre-top of source to centre-bottom of target |
| Serving | Orthogonal, centre-top of source to centre-bottom of target |
| Assignment | Straight, from edge of source to edge of target |
| Association | Straight, from edge of source to edge of target |

---

## 6. Styling

### Element fills

| Element | Fill | Border |
|---|---|---|
| `ApplicationComponent` | `#CCFFFF` | `#006666` |
| `Artifact`, `TechnologyService`, `Node`, `SystemSoftware`, `Device`, `CommunicationNetwork` | `#CCFFCC` | `#009900` |

### Connectors

| Type | Line | Arrowhead at target |
|------|------|---------------------|
| Realization | dashed | open triangle |
| Serving | solid | open arrow |
| Assignment | solid | filled circle at source, no arrowhead at target |
| Aggregation | solid | hollow diamond at source (Node) end |
| Association | solid | no marker |

---

## 7. MCP Tool Call Sequence

Execute in this order. Do not reorder steps.

```
1. manage_elements      create → Node, SystemSoftware (x2), Device, Artifact, TechnologyService, CommunicationNetwork
                                  (reuse the existing ApplicationComponent — do not recreate it)
2. manage_relationships create → Realization: Node -> TechnologyService
3. manage_relationships create → Realization: Artifact -> ApplicationComponent
4. manage_relationships create → Serving: TechnologyService -> ApplicationComponent
5. manage_relationships create → Serving: OperatingSystem -> PlatformSw
6. manage_relationships create → Assignment: Node -> Artifact
7. manage_relationships create → Assignment: Device -> OperatingSystem
8. manage_relationships create → Assignment: Device -> PlatformSw
9. manage_relationships create → Aggregation: Node -> PlatformSw, Node -> OperatingSystem, Node -> Device
10. manage_relationships create → Association: Device -> CommunicationNetwork
11. manage_views         create → the view
12. manage_view_content  add_element → ApplicationComponent, TechnologyService, Artifact figures
13. manage_view_content  add_element → Node figure
14. manage_view_content  add_element → PlatformSw, OperatingSystem, Device figures, each with parent_figure_id = Node figure ID
15. manage_view_content  add_element → CommunicationNetwork figure
16. manage_view_content  add_relationship → all connections (skip the derived serving line — see Section 3)
17. manage_appearance    set_figure → apply fill/border colours per Section 6
```

Do NOT call `layout_view` on an Infrastructure View — auto-layout will pull the nested System Software/Device figures out of the Node's boundary.

---

## 8. Naming Convention

| Artefact | Pattern |
|----------|---------|
| View name | `{Application} Infrastructure View` |
| View documentation | `Infrastructure realization for {application}, from device up to application component.` |
| Element names | Use real environment/platform names (e.g. `Prod App Server`, `RHEL 9`, `JVM Runtime`), not generic `Node`/`Device` labels |

---

## 9. Minimum Valid View Checklist

Before returning, confirm:

- [ ] Node figure contains Platform sw, Operating System, and Device as nested figures (`parent_figure_id`)
- [ ] Node realizes exactly one Technology Service, which serves the Application Component
- [ ] Artifact realizes the Application Component (not the Technology Service)
- [ ] Node is assigned to the Artifact
- [ ] Device is assigned to both Operating System and Platform sw
- [ ] Operating System serves Platform sw (not the reverse)
- [ ] Node aggregates Platform sw, Operating System, and Device (diamonds at Node)
- [ ] Device is associated with Communication Network
- [ ] The Platform-sw → Application Component "serving (derived)" line was **not** created explicitly
- [ ] `layout_view` was not called

---

## 10. Minimal Example (from canonical diagram)

```json
// Elements (Application Component assumed to already exist in the model)
[
  { "name": "Node",                              "type": "Node" },
  { "name": "Platform sw",                       "type": "SystemSoftware" },
  { "name": "Operating System",                  "type": "SystemSoftware" },
  { "name": "Device",                            "type": "Device" },
  { "name": "Artifact",                          "type": "Artifact" },
  { "name": "Technology Service",                "type": "TechnologyService" },
  { "name": "Communication Network",             "type": "CommunicationNetwork" }
]
```

```json
// Relationships
[
  { "source_id": "Node",              "target_id": "Technology Service",   "type": "RealizationRelationship" },
  { "source_id": "Artifact",          "target_id": "Application Component","type": "RealizationRelationship" },
  { "source_id": "Technology Service","target_id": "Application Component","type": "ServingRelationship" },
  { "source_id": "Operating System",  "target_id": "Platform sw",          "type": "ServingRelationship" },
  { "source_id": "Node",              "target_id": "Artifact",             "type": "AssignmentRelationship" },
  { "source_id": "Device",            "target_id": "Operating System",     "type": "AssignmentRelationship" },
  { "source_id": "Device",            "target_id": "Platform sw",          "type": "AssignmentRelationship" },
  { "source_id": "Node",              "target_id": "Platform sw",          "type": "AggregationRelationship" },
  { "source_id": "Node",              "target_id": "Operating System",     "type": "AggregationRelationship" },
  { "source_id": "Node",              "target_id": "Device",               "type": "AggregationRelationship" },
  { "source_id": "Device",            "target_id": "Communication Network","type": "AssociationRelationship" }
]
```

---

## 11. Common Mistakes

| Wrong | Correct |
|-------|---------|
| Artifact realizes Technology Service | Artifact realizes **Application Component**; Node realizes Technology Service |
| Platform sw serves Operating System | Direction is reversed — **Operating System serves Platform sw** |
| Creating an explicit relationship for the "serving (derived)" line | It is derived by ArchiMate from the existing chain — do not create it |
| Diamond (aggregation) at Platform sw/OS/Device instead of Node | Node is always the "whole" — diamonds sit at Node |
| Device assigned only to Operating System | Device is assigned to **both** Operating System and Platform sw |
| Leaving System Software/Device as free-floating figures | Nest them inside the Node figure via `parent_figure_id` |
| Calling `layout_view` after nesting | Auto-layout breaks the Node's nested figure containment — omit this step |

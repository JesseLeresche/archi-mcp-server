# Proposed Archi MCP Tools

Identified from the BIAN SD Overview modeling workflow — 341 Service Domain views need creation/auditing/fixing using only MCP tools. These gaps were found when planning that work.

## Priority Ranking

| Priority | Tool | Call Reduction | Complexity |
|----------|------|---------------|------------|
| **1** | `create_sd_overview_view` | ~700 → 142 (5x) | High |
| **2** | `get_view_connections` | Enables auditing (currently impossible) | Medium |
| **3** | `bulk_create_views` | ~142 → ~5 | Low |
| **4** | `bulk_update_figure_appearance` | ~341 → ~36 | Low |
| **5** | `update_view` | Enables doc fixes (currently impossible) | Low |
| **6** | `new_type` on `bulk_update_elements` | ~100 → ~5 | Medium |

---

## 1. `create_sd_overview_view` (Composite)

A high-level tool that creates a complete BIAN SD Overview view from structured input. Combines element creation, relationship creation, view creation, figure placement, connection routing, and styling into one atomic operation.

### Parameters

```json
{
  "name": "create_sd_overview_view",
  "description": "Create a complete BIAN SD Overview view with all elements, relationships, figures, connections, and styling. Atomic — fully succeeds or rolls back.",
  "parameters": {
    "sd_name": {
      "type": "string",
      "description": "Service Domain name (e.g. 'Document Directory')"
    },
    "sd_element_id": {
      "type": "string",
      "description": "ID of existing focal BusinessService element"
    },
    "view_folder_id": {
      "type": "string",
      "description": "Folder for the view"
    },
    "element_folder_id": {
      "type": "string",
      "description": "Folder for new elements"
    },
    "business_area": {
      "type": "string",
      "description": "For view documentation (e.g. 'Business Support')"
    },
    "business_domain": {
      "type": "string",
      "description": "For view documentation (e.g. 'Document Management and Archive')"
    },
    "functional_pattern": {
      "type": "string",
      "description": "FP name (e.g. 'Catalog', 'Manage', 'Fulfill', 'Process', 'Operate')"
    },
    "generic_artifact": {
      "type": "string",
      "description": "GA name (e.g. 'Directory Entry', 'Management Plan')"
    },
    "control_record": {
      "type": "string",
      "description": "CR name (e.g. 'Document Directory Entry')"
    },
    "asset_type": {
      "type": "string",
      "description": "AT name (e.g. 'Document', 'Customer Relationship')"
    },
    "behavior_qualifier_type": {
      "type": "string",
      "description": "BQT name (e.g. 'Property', 'Duty', 'Feature'). Omit if none."
    },
    "behavior_qualifiers": {
      "type": "array",
      "items": { "type": "string" },
      "description": "List of BQ names. Omit or empty if none."
    },
    "reference_information": {
      "type": "array",
      "items": { "type": "string" },
      "description": "List of RI names. Omit or empty if none/NA."
    }
  },
  "required": [
    "sd_name", "sd_element_id", "view_folder_id", "element_folder_id",
    "business_area", "business_domain", "functional_pattern",
    "generic_artifact", "control_record", "asset_type"
  ]
}
```

### Internal Behaviour

The tool should internally:

1. **Create elements** (in `element_folder_id`):
   - `{control_record}_ Analytics Object` (BusinessObject) — AnalyticsObject
   - `{asset_type}` (BusinessObject) — AssetType
   - `{control_record}` (BusinessObject) — ControlRecord
   - `{functional_pattern}` (BusinessInteraction) — FunctionalPattern
   - `{generic_artifact}` (BusinessObject) — GenericArtifact
   - `{behavior_qualifier_type}` (BusinessObject) — BehaviorQualifierType (if provided)
   - Each BQ name (BusinessObject) — BehaviorQualifiers
   - Each RI name (BusinessObject) — ReferenceInformation (skip "NA")
   - `{sd_name}_SD_Operations` (BusinessService) — ServiceGroup
   - `{control_record}_Instantiation` (BusinessService) — ServiceGroup
   - `{control_record}_Invocation` (BusinessService) — ServiceGroup
   - `{control_record}_Reporting` (BusinessService) — ServiceGroup

2. **Create relationships**:
   - FunctionalPattern → GenericArtifact (AccessRelationship, accessType=3 Read/Write)
   - FunctionalPattern → SD (RealizationRelationship)
   - AssetType → SD (RealizationRelationship)
   - ControlRecord → SD (RealizationRelationship)
   - AnalyticsObject → SD (RealizationRelationship)
   - Each ServiceGroup → SD (RealizationRelationship)
   - GenericArtifact → BQT (AggregationRelationship) — if BQT exists
   - ControlRecord → each BQ (AggregationRelationship)
   - BQT → each BQ (AggregationRelationship) — model only, NOT drawn on view
   - ControlRecord → each RI (AggregationRelationship)

3. **Create view** in `view_folder_id`:
   - Name: `{sd_name} SD Overview`
   - Documentation: `SD Overview for {sd_name} - {business_area} > {business_domain}`

4. **Place figures** using the SD Overview layout spec:

   | Element | X | Y | Width | Height |
   |---------|---|---|-------|--------|
   | ServiceDomain | 380 | 320 | 180 | 60 |
   | FunctionalPattern | 380 | 144 | 180 | 55 |
   | GenericArtifact | 620 | 144 | 180 | 55 |
   | BehaviorQualifierType | 876 | 144 | 200 | 55 |
   | AnalyticsObject | 50 | 252 | 227 | 55 |
   | AssetType | 50 | 334 | 227 | 41 |
   | ControlRecord | 620 | 320 | 210 | 55 |
   | ServiceGroup 1 (_SD_Operations) | 96 | 437 | 227 | 41 |
   | ServiceGroup 2 (_Instantiation) | 96 | 497 | 227 | 45 |
   | ServiceGroup 3 (_Invocation) | 96 | 557 | 227 | 40 |
   | ServiceGroup 4 (_Reporting) | 96 | 617 | 227 | 41 |
   | BQ nth | 768 | 408 + (n-1) * 55 | 250 | 45 |
   | RI (if applicable) | 50 | 700 | 227 | 55 |

5. **Draw connections** with bendpoints:

   **Straight connections** (no bendpoints):
   - FunctionalPattern → GenericArtifact (accesses)
   - FunctionalPattern → ServiceDomain (realizes)
   - GenericArtifact → BehaviorQualifierType (aggregates)
   - AssetType → ServiceDomain (realizes)
   - ControlRecord → ServiceDomain (realizes)

   **AnalyticsObject → ServiceDomain** (realizes) — single bendpoint:
   - `startX=258, startY=16, endX=-50, endY=-74`

   **ServiceGroup → ServiceDomain** (realizes) — single bendpoint each:

   | SG | startX | startY | endX | endY |
   |----|--------|--------|------|------|
   | 1 (y=437) | 271 | -1 | 10 | 106 |
   | 2 (y=497) | 271 | -3 | 10 | 166 |
   | 3 (y=557) | 271 | -1 | 10 | 226 |
   | 4 (y=617) | 271 | -1 | 10 | 286 |

   **ControlRecord → BQ** (aggregates) — single bendpoint each:
   - Formula: `startX=-5, startY=(bq_y + 22.5) - (347.5), endX=-173, endY≈0`

   | BQ # | bq_y | startX | startY | endX | endY |
   |------|------|--------|--------|------|------|
   | 1 | 408 | -5 | 85 | -173 | 2 |
   | 2 | 463 | -5 | 145 | -173 | 7 |
   | 3 | 518 | -5 | 193 | -173 | 0 |
   | 4 | 573 | -5 | 253 | -173 | 5 |
   | 5 | 628 | -5 | 301 | -173 | -2 |
   | 6 | 683 | -5 | 361 | -173 | 3 |
   | 7+ | 408+(n-1)*55 | -5 | (bq_y+22.5)-347.5 | -173 | 0 |

   **NOT drawn on view**: BQT → BQ (aggregates) — these exist in the model only.

6. **Style the SD figure**: fill `#F5A623`, border `#D4850F`

### Return Value

```json
{
  "view_id": "id-...",
  "elements_created": 17,
  "relationships_created": 22,
  "figures_placed": 17,
  "connections_drawn": 15
}
```

> **Convention**: `success` is omitted (implicit — errors throw JSON-RPC exceptions). `view_name` is omitted (echoes input). Counts are kept here because they are server-computed aggregates the caller cannot derive.

### Impact

Reduces the per-SD workflow from 5-7 MCP calls to **1 call**. For 142 missing views → 142 calls instead of ~700+.

---

## 2. `get_view_connections`

Return all visual connections (drawn relationships) on a view, with routing/bendpoint data. Currently `get_view_layout` returns figures but not connections.

### Parameters

```json
{
  "name": "get_view_connections",
  "description": "List all visual connections drawn on a view. Returns relationship details, source/target figures, and bendpoint data.",
  "parameters": {
    "view_id": {
      "type": "string",
      "description": "ID of the view to inspect"
    },
    "relationship_id": {
      "type": "string",
      "description": "Optional: return only the connection for this relationship"
    }
  },
  "required": ["view_id"]
}
```

### Return Value

```json
{
  "connections": [
    {
      "connection_id": "id-...",
      "relationship_id": "id-...",
      "relationship_type": "AccessRelationship",
      "access_type": 3,
      "source_element_id": "id-...",
      "source_element_name": "Catalog",
      "source_figure_id": "id-...",
      "target_element_id": "id-...",
      "target_element_name": "Directory Entry",
      "target_figure_id": "id-...",
      "bendpoints": []
    },
    {
      "connection_id": "id-...",
      "relationship_id": "id-...",
      "relationship_type": "RealizationRelationship",
      "source_element_id": "id-...",
      "source_element_name": "Document Directory_SD_Operations",
      "source_figure_id": "id-...",
      "target_element_id": "id-...",
      "target_element_name": "Document Directory",
      "target_figure_id": "id-...",
      "bendpoints": [
        { "startX": 271, "startY": -1, "endX": 10, "endY": 106 }
      ]
    }
  ]
}
```

> **Convention**: `view_id`, `view_name`, `connection_count` omitted from wrapper (echoed input / derivable from array length). Per-connection fields are kept since this is a read tool — the caller needs the data to inspect/audit.

### Impact

Critical for auditing existing views. Without this, there's no way to verify:
- Whether all expected relationships are drawn on a view
- Whether access types are correct (accessType=3 for FP→GA)
- Whether bendpoint routing is correct
- Whether unwanted connections exist (e.g., BQT→BQ should NOT be on view)

---

## 3. `bulk_create_views`

Create multiple views in a single call. Mirrors the `bulk_create_elements` pattern.

### Parameters

```json
{
  "name": "bulk_create_views",
  "description": "Create multiple empty ArchiMate diagram views in a single call. Returns per-view success/error with assigned IDs.",
  "parameters": {
    "views": {
      "type": "array",
      "description": "List of views to create",
      "items": {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "description": "View name"
          },
          "folder_id": {
            "type": "string",
            "description": "Folder to place the view in"
          },
          "documentation": {
            "type": "string",
            "description": "View documentation"
          }
        },
        "required": ["name"]
      }
    }
  },
  "required": ["views"]
}
```

### Return Value

```json
{
  "results": [
    { "id": "id-...", "folder_id": "id-..." },
    { "id": "id-...", "folder_id": "id-..." },
    { "error": "Folder not found: id-bad" }
  ]
}
```

> **Convention**: Per-item results return only server-generated `id` + `folder_id`. No `success` field — its absence of `error` implies success. No echoed `name`.

### Impact

Reduces 142 individual `create_view` calls to ~5 batched calls (one per Business Area).

---

## 4. `bulk_update_figure_appearance`

Style multiple figures across one or more views in a single call.

### Parameters

```json
{
  "name": "bulk_update_figure_appearance",
  "description": "Update visual appearance of multiple figures in a single call. Each entry targets a specific figure on a specific view.",
  "parameters": {
    "updates": {
      "type": "array",
      "description": "List of figure appearance updates",
      "items": {
        "type": "object",
        "properties": {
          "view_id": {
            "type": "string",
            "description": "ID of the view containing the figure"
          },
          "element_id": {
            "type": "string",
            "description": "Element whose figure to style (alternative to figure_id)"
          },
          "figure_id": {
            "type": "string",
            "description": "Figure ID to style (alternative to element_id)"
          },
          "fill_color": {
            "type": "string",
            "description": "Hex color, e.g. '#F5A623'"
          },
          "line_color": {
            "type": "string",
            "description": "Hex color, e.g. '#D4850F'"
          },
          "font_color": {
            "type": "string",
            "description": "Hex color for text"
          },
          "opacity": {
            "type": "integer",
            "minimum": 0,
            "maximum": 255
          },
          "line_width": {
            "type": "integer",
            "minimum": 1
          },
          "text_alignment": {
            "type": "integer",
            "enum": [1, 2, 4],
            "description": "1=left, 2=center, 4=right"
          }
        },
        "required": ["view_id"]
      }
    }
  },
  "required": ["updates"]
}
```

### Return Value

```json
{
  "results": [
    { "figure_id": "id-..." },
    { "figure_id": "id-..." },
    { "error": "Figure not found on view: id-bad" }
  ]
}
```

> **Convention**: Per-item results return only `figure_id`. No `success`, no echoed appearance fields.

### Impact

Reduces 341 individual `update_figure_appearance` calls to ~36 (one batched call per Business Domain).

---

## 5. `update_view`

Update properties of an existing view. Currently no way to modify a view's name or documentation after creation.

### Parameters

```json
{
  "name": "update_view",
  "description": "Update an existing view's name and/or documentation.",
  "parameters": {
    "view_id": {
      "type": "string",
      "description": "ID of the view to update"
    },
    "name": {
      "type": "string",
      "description": "New view name"
    },
    "documentation": {
      "type": "string",
      "description": "New documentation text"
    }
  },
  "required": ["view_id"]
}
```

### Return Value

```json
{
  "view_id": "id-..."
}
```

> **Convention**: Returns only the view ID. No echoed `name`/`documentation`, no `success`.

### Impact

Enables fixing documentation strings on ~100+ existing views that lack them. Currently impossible without XML editing.

---

## 6. Add `new_type` to `bulk_update_elements`

Extend the existing `bulk_update_elements` tool to support type changes, mirroring `update_element`'s `new_type` parameter.

### Changed Parameters

Add these fields to each item in the `updates` array:

```json
{
  "new_type": {
    "type": "string",
    "description": "Change ArchiMate type (e.g. 'Capability' → 'BusinessService'). Creates a new element with a new ID; all view figures and relationships are updated to reference the new element."
  },
  "new_folder_id": {
    "type": "string",
    "description": "When new_type is set: folder to place the new element in. If omitted, same folder as old element."
  }
}
```

### Impact

Batch-fix ~50-100 wrong element types (ServiceGroups typed as BusinessObject, SDs typed as Capability) in a few calls instead of 50-100 individual `update_element` calls.

### Return Value

For standard updates:
```json
{
  "results": [
    { "element_id": "id-..." },
    { "element_id": "id-...", "error": "Element not found: id-bad" }
  ]
}
```

For type-change items:
```json
{
  "results": [
    { "old_id": "id-...", "new_id": "id-...", "folder_id": "id-..." }
  ]
}
```

> **Convention**: Standard updates return only `element_id`. Type changes return `old_id`, `new_id`, `folder_id` (all server-generated/computed). No `success`, no echoed `name`/`type`.

### Note

Type changes have side effects (new element ID, reference updates). The implementation should process each type change sequentially within the bulk call to avoid conflicts, and return both `old_id` and `new_id` for each changed element.

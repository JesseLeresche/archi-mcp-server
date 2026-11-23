# Feature request: model-wide relationship query with full detail (`query_relationships`)

**Plugin:** archi-mcp (Archi MCP server) 3.0.0
**Type:** new read tool (or `relationships` mode on `query_model`)
**Priority:** medium — blocks safe re-creation of relationships not drawn on any view

## Summary

There is no way to read a relationship's full detail unless it is drawn on a view:

| Source | What it returns |
|---|---|
| `query_model` | Elements only (`type_filter: ServingRelationship` returns `[]`). |
| `manage_folders list_contents` on Relations | id, name, type, source_id, target_id — **no** access type, documentation, properties, specialization, influence strength. |
| `get_element_analysis` | Relationship id/type/name and partner — no access type or properties. |
| `manage_relationships between` | Per pair only; properties optional, no access type. |
| `inspect_view definition` | Full detail — but only for relationships drawn on that view. |

## Observed

Consolidating exact duplicates required deleting duplicates and re-creating their incident
relationships on the survivor. 60 of 195 incident relationships were not on any view, and
6 of those were AccessRelationships. Their read/write access type could not be read, so the
six affected clusters (S3, ECR, Application Microservices, BOP Codes, IDS (Accounts),
Corporate Current Account DataObject) were left unmerged rather than risk changing
`Read` / `Write` to `Unspecified`.

## Proposed tool

```json
{ "tool": "query_relationships",
  "type_filter": "AccessRelationship",
  "source_id": "...", "target_id": "...", "element_id": "... (either end)",
  "folder_id": "...", "include_subfolders": true,
  "name_search": "...", "property_key": "...", "property_value": "...",
  "specialization": "...",
  "on_view": true | false,
  "include_properties": true,
  "output_file": "rels.json" }
```

Each result carries the same field names as `manage_relationships create`, so results can
be replayed directly:

`id, type, source_id, target_id, name, documentation, folder_path, properties,
specialization, access_type (Access), influence_strength (Influence), directed (Association)`
— plus read-only `views: [view_id, …]`.

- `on_view: false` returns relationships not drawn on any view (complements
  `validate_model check_orphans`, which covers elements only).
- `output_file` writes to the exchange folder, as `inspect_view definition` does — a full
  model can exceed the inline response limit (this model: ~1,330 relationships).

## Also requested

- `get_element_analysis` and `manage_relationships between`: include `access_type`,
  `influence_strength`, `directed`, `documentation` on each relationship entry.
- `manage_relationships create/update`: accept `influence_strength` and `directed` if not
  already supported, so query output round-trips.

## Acceptance criteria

- Every relationship in the model can be read with all attributes needed to recreate it,
  whether or not it is on a view.
- Feeding `query_relationships` output into `manage_relationships create` (new model)
  reproduces the relationships exactly, including access type.
- `on_view: false` lists exactly the relationships absent from every view.

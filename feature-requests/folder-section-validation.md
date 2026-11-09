# Feature request: prevent (and warn on) objects being filed into the wrong section's folders

**Plugin:** archi-mcp (Archi MCP server)
**Tools:** `manage_relationships` (`create`, `update` / `new_folder_id`), `manage_elements`
(`folder_id` / `folder_path`, `new_folder_id`), `manage_views` (`folder_id`), `manage_folders`
(`create`, `move_element`, `move_view`)
**Type:** enhancement (guardrail)
**Priority:** medium — low effort, high value

## Summary

Make it impossible to accidentally place a relationship into a folder under the **Views**
section (or a view/element into the **Relations** section, etc.). Folder operations should
validate that a folder belongs to the correct top-level section for the object type, and folder
creation should let callers target a section explicitly.

## Motivation / problem

During a session, relationships were accidentally filed into a folder under the Views section
instead of Relations. Root cause: `manage_folders { create }` without a `parent_folder_id`
created the folder under a default section (Views), and that folder id was then passed as
`folder_id` for new relationships — which the tooling accepted without complaint.

ArchiMate's model tree is section-typed (Business / Application / Technology / Motivation /
Strategy / Implementation & Migration for elements, Relations for relationships, Views for
diagrams). A relationship under a Views-section folder is structurally invalid and confusing,
and the Archi UI wouldn't normally allow it.

## Proposed change

1. **Validate placement on write.** In `manage_relationships` (`create`, `new_folder_id`),
   `manage_elements` (`folder_id`/`folder_path`, `new_folder_id`), `manage_views`
   (`folder_id`) and `manage_folders` (`move_element`/`move_view`): reject a target folder whose
   top-level section doesn't match the object kind, with a clear error, e.g.:

   > folder_id … is under section 'Views'; relationships must be placed under the 'Relations'
   > section. Use folder_path "Relations/…" or a Relations-section folder.

2. **Make `create` section-aware.** Add an explicit `section` option to `manage_folders
   { create }` (e.g. `"Relations"`, `"Application"`, `"Views"`). When `parent_folder_id` is
   omitted, require `section` rather than silently defaulting. Document where a bare create lands.
3. **Infer from context where safe.** A relationship created with no folder defaults to the
   Relations root (or a sensible Relations subfolder), never a Views folder.
4. **`folder_path` guardrail.** When resolving/creating via `folder_path`, anchor the path to the
   section implied by the object type, so `folder_path: "Data Flows"` for a relationship resolves
   under Relations, not Views.

## Acceptance criteria

- Creating/moving a relationship into a Views-section folder fails with an actionable error
  (and vice-versa for views/elements).
- `manage_folders { create }` can target a named section; a bare create either requires
  `section` or has clearly documented, type-safe defaults.
- Creating a relationship with no folder never lands it under a Views-section folder.
- Existing valid calls (correct section) are unaffected.

## Implementation notes

- Consolidated `manage_*` tools are pure routing; put the section check in a shared helper used
  by the per-operation tool classes (folder moves, bulk create, view create) rather than in each
  `manage_*` wrapper.
- Section of a folder = walk `eContainer()` up to the top-level folder and compare its
  `FolderType` (`RELATIONS`, `DIAGRAMS`, `BUSINESS`, …) to the one required by the object kind.

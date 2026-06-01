# Feature request: persist the open model to disk (`save_model`)

**Plugin:** archi-mcp (Archi MCP server)
**Type:** new tool
**Priority:** high — without it, MCP-authored model work is not durable

## Summary

The Archi MCP plugin can create and modify models, elements, relationships, views
and folders, and can export views as images — but it has **no tool to save the open
model to disk**. Every change made through the MCP lives only in the running Archi
instance's memory.

## Current behaviour

- All write tools (`create_element`, `create_relationship`, `create_view`,
  `create_folder`, `update_*`, `bulk_*`, …) modify the **in-memory** model.
- `export_view_as_image` writes a PNG, but the underlying `.archimate` model is
  never persisted.
- A model that has never been saved (no `.archimate` file on disk — e.g. a scratch
  or freshly created model) cannot be given a file at all through the MCP.
- Closing Archi, a crash, or restarting the MCP server loses everything created
  via the MCP.

## Impact

- MCP-authored architecture work is **volatile** — not durable, not shareable, and
  not version-controllable.
- A model with no file on disk cannot be committed to a repository, diffed, or
  handed to a teammate.
- Agents and automations that build models via the MCP can only produce
  screenshots, never a durable, editable model artifact.

*Encountered in practice:* two views and 35 elements were built successfully in an
unsaved "Projects" model via the MCP and exported as PNGs — but there was no way to
persist the model itself, so the architecture work could not be version-controlled
alongside the code it documents.

## Proposed feature

A `save_model` tool:

| Call | Behaviour |
|---|---|
| `save_model()` | Save the currently selected model in place, to its existing `.archimate` file. |
| `save_model(path="/abs/path/Model.archimate")` | Save-as: write the model to the given path and associate it with the model (required for models that have no file yet). |

- Returns the resolved file path and whether the file was newly created or updated.
- Errors clearly if the model has no associated file and no `path` is supplied.

### Optional companions

- A dirty/unsaved indicator on `list_models` output, so callers can tell which
  open models have pending changes.
- `save_all_models()` to persist every open model in one call.

## Acceptance criteria

- After `save_model()`, the `.archimate` file on disk reflects all changes made
  through the MCP.
- A model created or modified via the MCP with no prior file can be persisted with
  `save_model(path=…)`.
- `list_models` (or an equivalent) reports an unsaved-changes indicator per model.

## Notes / alternatives

- `export_view_as_image` covers *viewing* the result but not *preserving* or
  *editing* the model — it is not a substitute.
- Folder organisation tools (`create_folder`, `move_element_to_folder`,
  `bulk_move_*`) already exist, so no separate request is needed for those; this
  request is specifically about persistence.

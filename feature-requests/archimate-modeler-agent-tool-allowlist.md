# Feature request: ship a reference `archimate-modeler` agent with the full tool allowlist

**Plugin:** archi-mcp (Archi MCP server)
**Type:** packaging / documentation
**Priority:** medium — the MCP works, but its companion agent under-uses it

## Summary

A Claude Code sub-agent is the natural way to drive this MCP, but the plugin ships no
agent definition. Users hand-write one, and a hand-written `tools:` allowlist almost
always omits most of the ~40 `mcp__archi__*` tools. An agent with a partial allowlist
then reports plugin capabilities as "missing" when they are not.

## Observed problem

A user-level `archimate-modeler` agent built two ArchiMate views successfully, but:

- **Could not organise the model** — it reported *"the Archi MCP exposes no
  folder-creation tool"*, although `create_folder` and the `move_*_to_folder` tools
  exist. The agent simply did not have them in its allowlist.
- **Hand-placed every figure coordinate** — it reported *"no layout/auto-arrange MCP
  tool"*, although `layout_view` exists.
- **Could not export images** — `export_view_as_image` was not in its allowlist, so the
  caller had to export the diagrams.
- **Used 193 tool calls** for two views — none of the `bulk_*` tools were in its
  allowlist.
- **Lacked `validate_model`** despite its own description claiming it "validates models".

Root cause: the agent's `tools:` allowlist contained only 12 of ~40 archi tools. This is
an agent *configuration* gap — but it is invisible until hit, and every hand-written
agent repeats it.

## Proposed change

Ship a **reference `archimate-modeler` agent definition** with the plugin (e.g. under an
`agents/` or `docs/` directory) carrying a complete, curated tool allowlist — or, at
minimum, document the recommended allowlist in the plugin README so users can copy it.

### Recommended `mcp__archi__*` allowlist (complete — 40 tools)

- **Read / query (11):** `list_models`, `select_model`, `query_model`, `get_views`,
  `get_element_analysis`, `get_folder_tree`, `list_folder_contents`, `get_view_layout`,
  `get_view_connections`, `get_connection`, `validate_model`
- **Create (8):** `create_element`, `create_relationship`, `create_view`,
  `create_folder`, `create_sd_overview_view`, `bulk_create_elements`,
  `bulk_create_relationships`, `bulk_create_views`
- **View composition (7):** `add_element_to_view`, `add_relationship_to_view`,
  `bulk_add_elements_to_view`, `bulk_add_relationships_to_view`, `layout_view`,
  `remove_figure_from_view`, `duplicate_view`
- **Update (6):** `update_element`, `update_connection`, `update_view`,
  `update_figure_appearance`, `bulk_update_elements`, `bulk_update_figure_appearance`
- **Organise (4):** `move_element_to_folder`, `move_view_to_folder`,
  `bulk_move_elements_to_folder`, `bulk_move_views_to_folder`
- **Delete (3):** `delete_element`, `delete_view`, `delete_connection`
- **Export (1):** `export_view_as_image`

(Plus `save_model` once that tool exists — see the companion feature request,
`archi-mcp-save-model.md`.)

## Interim fix for an existing hand-written agent

Edit the agent's YAML frontmatter `tools:` line — e.g.
`~/.claude/agents/archimate-modeler.md` — to add the missing `mcp__archi__*` tools
above. It is a one-line change in a markdown file; no code, no plugin rebuild;
effective on the next agent spawn. Optionally add a couple of lines to the agent's
prompt body telling it to *use* folders, `layout_view` and the `bulk_*` tools, so it
does not keep hand-placing coordinates out of habit.

The current (incomplete) allowlist for reference — 12 of 40 archi tools:

```
list_models, select_model, query_model, create_element, create_relationship,
create_view, add_element_to_view, add_relationship_to_view, get_element_analysis,
get_views, update_element, update_figure_appearance
```

## Why it matters

- Stops the companion agent reporting plugin features as missing.
- The agent can then organise models into folders, auto-layout views, export images,
  and use bulk operations — far fewer tool calls, faster, tidier output.
- New users get a working agent out of the box instead of discovering the gaps one
  failure at a time.

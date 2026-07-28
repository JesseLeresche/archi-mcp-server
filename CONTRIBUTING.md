# Contributing to Archi MCP Plugin

Thank you for your interest in contributing. This guide covers everything you need to get started.

## Getting Started

Before you begin, make sure you can build the project locally. Follow the **Building Locally** section in the [README](README.md).

## Workflow

All contributions go through a feature branch and pull request. Direct commits to `main` are not permitted.

### 1. Fork and clone

If you don't have write access, fork the repository first. Then clone:

```bash
git clone https://github.com/JesseLeresche/archi-mcp.git
cd archi-mcp
```

### 2. Create a feature branch

Branch from `main`. Use a short, descriptive name:

```bash
git checkout main
git pull origin main
git checkout -b feature/my-new-tool
```

Branch naming conventions:
- `feature/` — new functionality
- `fix/` — bug fixes
- `docs/` — documentation only changes
- `refactor/` — internal changes with no user-visible effect

### 3. Make your changes

Keep changes focused. A pull request should do one thing. If you find unrelated issues, open a separate branch for them.

**Adding a new tool:**
1. Create `com.archimatetool.mcp/src/com/archimatetool/mcp/tools/YourTool.java` implementing `ITool`
2. Register it in `ToolRegistry.java`
3. Follow the patterns in existing tools — `UiThreadUtil.syncExec()` for all mutations, single `saveModel()` per operation

**Coding conventions:**
- Match the style of the surrounding code
- All model mutations must run inside `UiThreadUtil.syncExec()`
- Bulk operations must handle per-item errors without failing the whole batch
- Return `{success: true/false, error: "..."}` shaped results consistently

**Adding a new view type guide:**

The plugin exposes MCP **Resources** — bundled Markdown guides that teach an AI agent how to
correctly build a specific *kind* of ArchiMate view (e.g. a Layered View or a Data Model View):
which element types to use, which relationships are valid and in which direction, how to nest and
style figures, and the exact `manage_*` tool-call sequence to follow. An agent fetches the
relevant guide via `resources/read` before it starts building that view type, instead of guessing.

This is designed to be extensible — adding support for a new view type is a docs-plus-wiring
change, not new tool code:

1. **Write the guide.** Copy [`docs/view-guide-template.md`](../docs/view-guide-template.md) to
   `za.co.jesseleresche.archi.mcp/src/resources/guides/{view-type}-view-agent-guide.md` and fill
   in every section. The template's blockquote hints explain what belongs in each section; the
   existing guides (`layered-view-agent-guide.md`, `data-model-view-agent-guide.md`,
   `application-structure-view-agent-guide.md`, `infrastructure-view-agent-guide.md`) are the best
   reference for level of detail — a new guide should be as prescriptive as those.

2. **Register it as a resource.** Add a `ResourceDescriptor` entry in
   `za.co.jesseleresche.archi.mcp/src/za/co/jesseleresche/archi/mcp/resources/ResourceRegistry.java`:
   ```java
   list.add(new ResourceDescriptor(
           "archi://guides/{view-type}-view",
           "{View Type} View — Agent Generation Guide",
           "One-line description shown in resources/list — what the view is for and when to fetch it.",
           "text/markdown",
           "/resources/guides/{view-type}-view-agent-guide.md"
   ));
   ```
   The `classpathPath` must match where Tycho places the file on the plugin's classpath — it
   mirrors the `src/resources/...` path.

3. **Point agents at it.** Add a hint to the description of whichever tool creates that view type
   (usually `ManageViewsTool.getDescription()`) telling the agent to fetch the guide first via
   `resources/read` with the URI from step 2 — follow the existing pattern in
   `ManageViewsTool.java`, which lists all four current guides.

4. **Build and verify.** After `mvn clean verify`, confirm the guide is reachable: start Archi
   with the plugin installed and call `resources/list` (it should include your new URI) and
   `resources/read` with that URI (it should return your Markdown content).

### 4. Build and verify

```bash
# macOS / Linux
JAVA_HOME="$(/usr/libexec/java_home -v 21)" mvn clean verify

# Windows
set JAVA_HOME=C:\path\to\jdk-21
mvn clean verify
```

The build must pass with no errors before submitting.

### 5. Commit your changes

Write clear, descriptive commit messages:

```bash
git add <specific files>
git commit -m "Add get_folder_contents tool for listing folder children"
```

Avoid vague messages like "fix bug" or "update code".

### 6. Push and open a pull request

```bash
git push origin feature/my-new-tool
```

Then open a pull request against `main` on GitHub. In the PR description include:

- **What does this change do?** — a short summary
- **Why is it needed?** — motivation or the issue it addresses
- **How was it tested?** — describe how you verified it works in a live Archi instance
- **Anything to watch out for?** — edge cases, limitations, or follow-up work

### 7. Review and merge

A maintainer will review your PR. Be prepared to respond to feedback and push follow-up commits to the same branch. Once approved, the maintainer will merge it.

## Reporting Bugs

Open a [GitHub Issue](https://github.com/JesseLeresche/archi-mcp/issues) with:
- Archi version
- OS and Java version
- Steps to reproduce
- What you expected vs what happened
- Any relevant output from the Archi error log (`Help > About Archi > Installation Details > Configuration`)

## Suggesting Features

Open a GitHub Issue with the `enhancement` label. Describe the use case — what you're trying to do and why the current tools don't cover it.

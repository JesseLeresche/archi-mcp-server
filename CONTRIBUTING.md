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

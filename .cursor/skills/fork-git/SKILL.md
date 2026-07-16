---
name: fork-git
description: >-
  Git commit and push for SmartTublex using Conventional Commits.
  Trigger commands: git commit, commit, commit changes, git push, push,
  push changes, commit push, commit and push, ship. Handles PlexServiceCore
  submodule commits before parent repo. Routed via fork-commands rule.
---

# Fork Git — Commit & Push

Conventional Commits for SmartTublex. Invoked via `commit`, `push`, `commit push` (see [fork-docs/COMMANDS.md](../../../fork-docs/COMMANDS.md)).

## Conventional Commits Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### Types

| Type | Use for |
|------|---------|
| `feat` | New user-facing feature |
| `fix` | Bug fix |
| `docs` | Documentation only (fork-docs, skills, rules, changelogs) |
| `chore` | Tooling, scripts, submodule pointers, deps |
| `refactor` | Code change without feature/fix |
| `test` | Tests only |
| `build` | Gradle, build config |

### Scopes (examples)

| Scope | Area |
|-------|------|
| `fork-docs` | fork-docs/, skills, cursor rules |
| `app` | Android `:app` module |
| `psc` | PlexServiceCore submodule |
| `plex` | Plex integration in the wrapper |
| `build` | Gradle, apk2maven |
| `artifact` | Base APK / Maven artifact refresh notes |

- Scope is optional but preferred when clear.
- Subject: imperative mood, lowercase, no period, max ~72 chars.
- Body: explain **why**, not what (when needed).

### Examples

```
docs(fork-docs): add artifact sync skill and command registry

chore(psc): update PlexServiceCore submodule pointer

feat(plex): add MediaSourceRegistry for Plex routing

build(app): wire smarttube artifact into Android application
```

---

## Commit Workflow

Run these **in parallel** first:

```bash
git status
git diff
git diff --staged
git log -5 --oneline
```

Also check submodule when present:

```bash
git status PlexServiceCore
cd PlexServiceCore && git status -sb && git diff --stat
```

### Submodule order

If **PlexServiceCore** has changes:

1. Commit inside the submodule first.
2. Then commit SmartTublex root (includes updated submodule pointer).

If only root changed, commit root only.

### Steps

1. Analyze all staged/unstaged changes across root and submodule.
2. Draft **one Conventional Commit message per repo** that has changes.
3. Show message(s) to user briefly if ambiguous; otherwise proceed.
4. Stage relevant files (`git add` — never commit secrets).
5. Commit via HEREDOC:

```bash
git commit -m "$(cat <<'EOF'
type(scope): subject

Optional body explaining why.
EOF
)"
```

6. Verify: `git status`

### Safety (mandatory)

- NEVER update git config
- NEVER `--force`, `--hard`, skip hooks unless user explicitly requests
- NEVER `--amend` unless all amend conditions met (user rule)
- NEVER commit `.env`, credentials, secrets — warn user
- Do not commit unless user triggered `commit` command or explicitly asked

---

## Push Workflow

Only when user says `push`, `git push`, or `commit push`.

### Push order

1. Push **PlexServiceCore** first if it has unpushed commits.
2. Push **SmartTublex** root:

```bash
git push -u origin HEAD
```

### Safety

- NEVER force-push to `main`/`master` — warn user if requested
- Do not push unless user triggered `push` or `commit push`

---

## Combined: `commit push`

1. Full commit workflow (all repos with changes).
2. Full push workflow.
3. Report commit SHAs and remote URLs.

---

## Key Paths

| Resource | Path |
|----------|------|
| Changelog | `fork-docs/CHANGELOG.md` |
| Commands | `fork-docs/COMMANDS.md` |

After feature commits, remind user to run `log change` if changelog not yet updated.

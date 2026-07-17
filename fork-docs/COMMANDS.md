# Fork Commands — Quick Reference

Short commands for steering the agent. Say any alias in chat.

The agent matches these via [`.cursor/rules/fork-commands.mdc`](../.cursor/rules/fork-commands.mdc).

---

## Artifact sync

| Say | Action |
|-----|--------|
| **`sync`** / **`sync smarttube`** | Reload SmartTube APK + `installApkArtifact` |
| `refresh artifact`, `update artifact`, `upstream sync`, `reload artifact` | _(same)_ |
| **`artifact status`** | Report installed artifact only — no download |
| `upstream status`, `base status`, `apk status` | _(same)_ |

Skill: [`.cursor/skills/artifact-sync/SKILL.md`](../.cursor/skills/artifact-sync/SKILL.md)

---

## Milestones

| Say | Action |
|-----|--------|
| **`milestone status`** / **`meilenstein status`** | Progress of the **current** milestone (named in chat, else newest `MILESTONE_*.md`) |
| `milestone progress`, `meilenstein progress` | _(same)_ |
| **`next step`** | Outline next open step of the current milestone (discuss before code) |
| `nächster schritt`, `continue milestone` | _(same)_ |

---

## Git (Conventional Commits)

| Say | Action |
|-----|--------|
| **`commit`** | Split into small logical packages; Conventional Commits each (no push) |
| **`push`** | Push to origin (`PlexServiceCore` first if needed) |
| **`commit push`** | Commit (logical splits) then push |

Skill: [`.cursor/skills/fork-git/SKILL.md`](../.cursor/skills/fork-git/SKILL.md)

**Message format:** `type(scope): subject` — e.g. `docs(fork-docs): add artifact sync skill`  
**Splitting:** always one concern per commit; prefer several small packages over one mixed commit.

---

## Documentation

| Say | Action |
|-----|--------|
| **`log change`** | Update `fork-docs/CHANGELOG.md` |
| **`fork help`** | Show this command list |

---

## Examples

```text
sync                   → reload + install SmartTube artifact
artifact status        → what is in ~/.m2 for smarttube?
milestone status       → current milestone progress table
meilenstein status     → _(same)_
next step              → discuss next open milestone step
commit                 → conventional commit (no push)
```

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
| **`plex status`** | Show Plex milestone progress |
| **`next step`** | Outline next open milestone step (discuss before code) |
| `nächster schritt`, `continue milestone` | _(same)_ |

---

## Git (Conventional Commits)

| Say | Action |
|-----|--------|
| **`commit`** | Stage + commit with Conventional Commits message (no push) |
| **`push`** | Push to origin (`PlexServiceCore` first if needed) |
| **`commit push`** | Commit then push |

Skill: [`.cursor/skills/fork-git/SKILL.md`](../.cursor/skills/fork-git/SKILL.md)

**Message format:** `type(scope): subject` — e.g. `docs(fork-docs): add artifact sync skill`

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
plex status            → milestone progress table
next step              → discuss next Plex task
commit                 → conventional commit (no push)
```

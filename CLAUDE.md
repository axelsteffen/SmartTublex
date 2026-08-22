# SmartTublex — Agent Instructions

This project's rules and skills for AI coding agents live entirely in **`.cursor/`**.
That is the single source of truth for both Cursor and Claude Code — do not
duplicate their content here or in any other file. This file is only an index:
when a rule or skill changes, edit it under `.cursor/`, not this list.

**At the start of every task in this repo — and in every subagent prompt that
touches this codebase — read the full linked file(s) below before acting, not
just the one-line summary.** All rules are marked `alwaysApply: true`, meaning
they are binding for the whole session, not opt-in per question.

## Rules (`.cursor/rules/*.mdc`)

| Rule | Governs |
|---|---|
| [graphify.mdc](.cursor/rules/graphify.mdc) | **Read and apply first.** Before using Read/Grep/Glob/Bash to explore the codebase, query the graphify knowledge graph (`graphify query "<question>"`, `graphify path "<A>" "<B>"`, `graphify explain "<concept>"`). Run `graphify update .` after modifying code. |
| [fork-commands.mdc](.cursor/rules/fork-commands.mdc) | Router for short commands (`sync`, `commit`, `push`, `milestone status`, `next step`, `log change`, …) to the matching skill/workflow. Check this before improvising on short or ambiguous input. |
| [fork-upstream-minimal.mdc](.cursor/rules/fork-upstream-minimal.mdc) | Keep SmartTublex isolated from the upstream SmartTube APK artifact — extend via SmartTublex-only classes or inheritance/override, never patch decompiled upstream. |
| [milestone-implementation-workflow.mdc](.cursor/rules/milestone-implementation-workflow.mdc) | Discuss architecture/approach before implementing a milestone step; the planned steps themselves are not up for debate, only *how* to build them. |
| [milestone-progress.mdc](.cursor/rules/milestone-progress.mdc) | One tracking doc per milestone in `fork-docs/milestones/`; TV resource constraints (low RAM/storage); technical docs always in English. |
| [changelog-fork.mdc](.cursor/rules/changelog-fork.mdc) | Keep `fork-docs/CHANGELOG.md` current in the **same turn** as every wrapper-specific change; use it to orient on milestone progress. |

## Skills (`.cursor/skills/*/SKILL.md`)

Invoked via the command registry in `fork-commands.mdc`, not run automatically —
read the full `SKILL.md` before executing its workflow.

| Skill | Trigger commands |
|---|---|
| [artifact-sync](.cursor/skills/artifact-sync/SKILL.md) | `sync`, `sync smarttube`, `refresh artifact`, `upstream sync`, `artifact status`, `upstream status`, … |
| [fork-git](.cursor/skills/fork-git/SKILL.md) | `commit`, `commit changes`, `push`, `push changes`, `commit push`, `ship`, … |

## Related docs

- [fork-docs/COMMANDS.md](fork-docs/COMMANDS.md) — compact command list (`fork help`)
- [fork-docs/README.md](fork-docs/README.md) — full fork-docs layout
- [fork-docs/CHANGELOG.md](fork-docs/CHANGELOG.md) — unreleased/released wrapper changes

## Adding new rules or skills

Add the `.mdc` file under `.cursor/rules/` or the `SKILL.md` under `.cursor/skills/`
as usual, register it in `fork-commands.mdc` (skills) per that rule's own
instructions, then add one row to this file's tables. Never copy rule/skill
content into this file.

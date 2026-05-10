# strictlykeptboy — Claude instructions

Git-backed Android calendar + timeboxing + todolist app. Kotlin + Jetpack Compose + Room cache + JGit. Designed AI-native: every event, task, and recurrence rule is a single hand-editable Markdown-with-TOML-frontmatter file in a Git repo, so Claude can read and write users' schedules the same way they can.

## Architectural decisions (locked)

See `docs/plans/decisions.md` for the full locked-decisions doc. Highlights:

- **Language:** Kotlin only. No Java.
- **UI:** Jetpack Compose + Material3 Expressive. No Android Views.
- **Cache DB:** Room (read-through cache only; Git files are source of truth).
- **Git:** JGit 6.x + apache-sshd-osgi (SSH) + OkHttp (HTTPS+OAuth).
- **Serialization:** ktoml (frontmatter) + kotlinx.serialization.
- **Recurrence:** dmfs lib-recur (RFC5545 RRULE).
- **Markdown:** commonmark.
- **Build front-end:** Google's Android CLI (same as tonearmboy).
- **App ID:** `com.eight87.strictlykeptboy`
- **minSdk:** 26  /  **targetSdk:** 36

## Repo convention this app produces

Each user-data repo this app reads/writes follows the layout in
`docs/plans/decisions.md` §D.3. Highlights for AI agents working on those
repos:

- One file per entity. One entity per file. **Never create an index file.**
- Files are bucketed `<calendars|todolists>/<id>/(events|tasks)/<yyyy>/<mm>/<entity-id>.md`.
- IDs are UUIDv7 (time-sortable).
- File format: TOML frontmatter (between `+++` fences) + Markdown body.
- Add an event = create a new file. Edit an event = rewrite the single file.
- Cancel a recurring instance = create `exceptions/<rule-id>/<yyyy-mm-dd>.md` with `kind = "cancel"`. Never edit the rule file to silently drop dates.
- The app builds its index from a filesystem scan on every git HEAD change.

Each user-data repo this app produces ships with its own `AGENTS.md` /
`CLAUDE.md` describing this convention to agents that work on the
*user's calendar repo*. The one you're reading now is for agents
working on the *strictlykeptboy app itself*.

## Plan files in this repo

Every plan under `docs/plans/` follows the global CLAUDE.md convention:

- Numbered phases with letter prefixes (`Phase A`, `Phase B`, …)
- Sub-step checkboxes per phase (`- [ ] **A.1** …`)
- Tick + commit-id annotation as work lands
- `## Status: ✅ DONE` at top when all phases ticked

Entry point: `docs/plans/main.md`. Deep-dives:

- `docs/plans/decisions.md` — locked architectural decisions
- `docs/plans/data-model.md` — file schemas + AGENTS.md content
- `docs/plans/sync-engine.md` — Git layer + auth + sync orchestration
- `docs/plans/ui-spec.md` — every screen + component + navigation
- `docs/plans/resolver.md` — overlay algorithm + recurrence + common-time
- `docs/plans/templates-demo-wizard.md` — templates + demo content + wizard
- `docs/plans/notifications-sharing-import.md` — notifications + sharing + import/export

## Stack details inherited from tonearmboy

Build pipeline, JDK quirks, AVD targets, mobile-mcp setup, android-skills MCP — identical to tonearmboy. Reference its `CLAUDE.md` for the host-environment notes. Plan documents in this repo assume Phase 0 (host prereqs) is already complete from prior Android work.

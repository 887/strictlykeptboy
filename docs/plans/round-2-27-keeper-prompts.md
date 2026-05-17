# Round 2.27 — Keeper-prompt mechanic + persona realism

## Status: 🚧 IN PROGRESS

> Background: the current "morning cage check for bruises" daily-rituals
> entry is tone-broken — the cage-check ritual was supposed to be the
> boy proving to the keeper that the cage is on, on demand, throughout
> the week. Round 2.27 introduces a first-class **keeper-prompt** event
> kind: a calendar event authored by the dom/keeper persona that demands
> a response from the boy (picture, text update, check-in), is projected
> into the todolist as a task, and stays open until the boy clears it
> by writing a response. Round 2.27 also rebuilds the rich-demo-repo
> with a believable 20-something software engineer's week (sleep blocks,
> gaming, Discord voice nights, weekend convention exception, playtime
> exception) on top of the existing kink-positive cage-keeping spine.

## Locked decisions

- **D-2.27.a — Schema: `requires_response = true` on the existing
  event/recurrence frontmatter, plus a parallel `prompt_kind` enum
  ("photo" | "text" | "check-in") and `prompt_target` ("keeper" |
  "self").** Strictly additive flag; composes with RRULE + inverted +
  supersedence without inventing a new file-type.
- **D-2.27.b — Codec change: extend `EventCodec` / `RecurrenceCodec`
  to round-trip the three new fields.** Optional on read (default
  `false` / `null`); omit on write when default-valued.
- **D-2.27.c — Spawn path: prompts become tasks via the existing
  `FromEventsProjector` (Round 2.16 / D-2.26 plumbing), not a new
  synthetic todolist.** Add `TaskSource.KeeperPrompt`, route
  `requires_response = true` instances through it.
- **D-2.27.d — Persistence: prompt-spawned tasks stay in Today +
  Overdue until cleared.** Materialization stays per-day;
  `isOverdue` does the carry-over.
- **D-2.27.e — Visual cue:** prompt-kind glyph (📸 / 💬 / 🔒) prefix +
  "Keeper" author chip + persistence pill ("open Nd") when open ≥1d.
- **D-2.27.f — Row affordance:** primary "Respond" tap-target opens
  a response sheet; long-press = "mark answered offline".
- **D-2.27.g — Response shape:** `calendars/<cal-id>/cage-check-responses/
  <prompt-id>/<yyyy-mm-dd>.md` — frontmatter: `schema_version`, `id`,
  `kind = "prompt_response"`, `prompt_id`, `prompt_instance_date`,
  `responder`, `created_at`, optional `attachment`. Body free-text.
  Existence of file = CLOSED (mirrors inverted-habit pattern in reverse).
- **D-2.27.h — Recurrence: reuse RRULE.** Spontaneous feel = irregular
  hand-authored one-offs, not a random generator.
- **D-2.27.i — Bruise-check is deleted, not refactored.** The
  inverted-habit `cage-stays-on` recurrence STAYS; what's deleted is
  `routines/recurrences/morning-cage-check.md` (medical tone) and
  `kinky-rituals/recurrences/cage-off-check.md` (replaced by photo prompt).

## Phase A — Schema + codec extension — shipped in commit 48ad3f5

- [x] **A.1** Add `requiresResponse: Boolean = false`,
      `promptKind: PromptKind?`, `promptTarget: PromptTarget?` to the
      event model.
- [x] **A.2** Add enums `PromptKind { Photo, Text, CheckIn }` and
      `PromptTarget { Keeper, Self }` with string `.label` for TOML.
- [x] **A.3** Extend `EventCodec` (encode + decode) to round-trip the
      three new fields. Optional on read; omit on write when default.
- [x] **A.4** Same in `RecurrenceCodec`.
- [x] **A.5** Propagate to `MaterializedInstance`; populate in
      `RecurrenceMaterializer.materialize()` from the rule, and in the
      one-off path from the event.
- [x] **A.6** Round-trip tests in codec tests: fields absent → defaults;
      present → preserved; unknown enum → falls back gracefully.

## Phase B — Resolver + projector wiring — shipped in commit a3ea234

- [x] **B.1** Add `TaskSource.KeeperPrompt` to the `TaskSource` enum
      in `ui/tasks/TaskModels.kt`.
- [x] **B.2** Extend `FromEventsProjector.project()` to route
      `inst.requiresResponse == true` to `TaskSource.KeeperPrompt`.
      Skip self-targeted prompts when boy IS the keeper (single-user
      free-mode); pass author id in.
- [x] **B.3** New pure-function reader `PromptResponseReader.
      listAnsweredInstances(repoRoot, calId, ruleId) → Set<LocalDate>`
      walking `calendars/<cal-id>/cage-check-responses/<rule-id>/*.md`.
      Projector drops instances whose date is in the set.
- [x] **B.4** Unit tests: recurring prompt with yesterday's response
      projects today only; one-off with no response projects exactly
      one task; closed prompt projects zero.

## Phase C — UI signaling for keeper-prompt rows — shipped in commit <CD-HASH>

- [x] **C.1** Extend `TaskRow.kt`: when source == `KeeperPrompt`,
      render prompt-kind glyph before title; render "Keeper" author chip
      right of title; tint 4dp accent strip with source-calendar color.
      Source-calendar tint deferred — accent painted with
      `colorScheme.tertiary` for KeeperPrompts as a clear differentiator.
      Also added `promptKind` / `promptCalendarId` / `promptRuleId`
      fields to `TaskItem` and propagated them from
      `MaterializedInstance` in `FromEventsProjector`.
- [x] **C.2** Persistence pill: `daysOpen = ChronoUnit.DAYS.between(due,
      today)`. When ≥1, render "open Nd" pill in `errorContainer`.
- [x] **C.3** Replace standard checkbox with "Respond" trailing button
      when KeeperPrompt. Long-press routed through new
      `onPromptMarkAnsweredOffline` host callback that writes a
      synthetic `(marked answered offline)` response file.
- [x] **C.4** Strings: `task_prompt_respond`, `task_prompt_keeper_chip`,
      `task_prompt_open_days` (plurals), + sheet labels
      (`task_prompt_sheet_title`, `_reply_label`, `_attachment_label`,
      `_cancel`, `_send`).

## Phase D — Response mechanism — Phase D.1 + D.3 shipped in commit <CD-HASH>

- [x] **D.1** Add `PromptResponseSheet.kt` in `ui/tasks/`. OutlinedTextField
      reply + optional "Attach photo URI" row. Confirm writes the file.
- [x] **D.2** Add `PromptResponseWriter.kt` in `store/`. Writes the
      D-2.27.g shape; JGit auto-commit via existing commit-hook plumbing.
      Shipped in commit a3ea234.
- [x] **D.3** Wire the sheet open trigger from `TaskRow`'s Respond button
      via existing task detail-sheet host. Hoisted `responseSheetTarget`
      state in `TasksDestinationBody`; `MainActivity.writePromptResponse`
      resolves the repo root, calls `PromptResponseWriter.write`, then
      re-indexes the affected repo so the FromEventsProjector picks up
      the new response file on the next tick. Also extended
      `renderTodaySync` in `AppGraph` to carry `requiresResponse` past
      events (D-2.27.d carry-over) + propagate prompt fields onto
      `MaterializedInstance`. Added `requiresResponse` + `promptKindRaw`
      + `promptTargetRaw` columns to `EventRow` / `RecurrenceRuleRow`
      (Room v3 → v4, `fallbackToDestructiveMigration`) so the cache
      → SourcesPublisher path carries the flags. Added a demo
      `keeperPromptDemoTasks` set in `TasksDemoSeed` for AVD smoke
      coverage while the full recurrence-expansion path catches up.
- [x] **D.4** Unit test for the writer: round-trip through reader;
      idempotent on same-date overwrite. Shipped in commit a3ea234.

## Phase E — Refactor cage-check demo content — shipped in commit 1b5e68a

- [x] **E.1** DELETE `rich-demo-repo/calendars/routines/recurrences/
      morning-cage-check.md` (bruise framing).
- [x] **E.2** DELETE `kinky-rituals/recurrences/cage-off-check.md`
      (replaced by photo prompt).
- [x] **E.3** ADD `kinky-rituals/recurrences/cage-photo-sunday.md` —
      weekly Sun 11:00, `requires_response = true`,
      `prompt_kind = "photo"`, `prompt_target = "keeper"`,
      title "send the keeper a cage photo", body in Keeper voice
      ("sunday pic, pet. clear shot, no editing. — Keeper").
- [x] **E.4** ADD `kinky-rituals/recurrences/cage-feels-midweek.md` —
      weekly Wed 14:00, `requires_response = true`,
      `prompt_kind = "text"`, title "how does the cage feel today",
      body asks for a note.
- [x] **E.5** ADD three ONE-OFF prompts on `dom-overlay` for week
      2026-05-17..23 — "send proof you're still caged" Mon 10:30,
      Thu 22:00, Sat 09:15, each `requires_response = true`
      `prompt_kind = "photo"`. Voice: Keeper, terse, affectionate.
- [x] **E.6** Seed one ALREADY-ANSWERED response at
      `kinky-rituals/cage-check-responses/<rule-id>/2026-05-10.md`
      so the demo shows what an answered prompt looks like in history.
- [x] **E.7** Seed one UNANSWERED OVERDUE prompt:
      `dom-overlay/.../2026-05-15-proof-photo.md` (one-off, no
      response file) — guarantees the "open 2d" pill renders.

## Phase F — Persona realism seed (sleep + gaming + discord + exceptions) — shipped in commit 1b5e68a

- [x] **F.1** Sleep blocks: `timeboxes/recurrences/sleep-block.md` —
      daily `RRULE=FREQ=DAILY`, `dtstart = 2026-05-17T23:30:00`,
      `duration = "PT8H"`, title "sleep", `tags = ["sleep", "block"]`.
      Body: "phone on charger, lights out, cage on..."
- [x] **F.2** Wind-down flagpole: `routines/recurrences/wind-down-2300.md`
      (5-min daily 23:00); sanity-check existing morning-alarm.
- [x] **F.3** NEW `calendars/gaming/` calendar (`name = "Gaming"`,
      `emoji = "🎮"`, `color = "#5e548e"`). Recurrences:
      - `helldivers-with-the-guys.md` — Tue + Fri 20:00–22:30.
      - `factorio-solo.md` — Sun 14:00–17:00.
      - `path-of-exile-build-night.md` — Thu 21:00–23:00.
- [x] **F.4** NEW `calendars/voice-chat/` (`name = "Voice chat"`,
      `emoji = "🎧"`, `color = "#3a86ff"`). Recurrences:
      - `dnd-wednesday.md` — Wed 20:00–23:00.
      - `dev-discord-catchup.md` — biweekly Sun 19:00–20:00.
      - `solo-vc-night.md` — Mon 22:00–23:30.
- [x] **F.5** ADD to `work-sprint-25` todolist:
      `2026-05-18-deploy-hotfix.md`, `2026-05-19-write-design-doc-
      prompt-system.md`, `2026-05-20-oncall-handoff-1on1.md`,
      `2026-05-22-manager-1on1.md`.
- [x] **F.6** Convention exception:
      `calendars/vacation/events/2026/05/2026-05-23-software-eng-conf.md`
      covering 2026-05-23 09:00 → 2026-05-24 18:00 with
      `supersedes = ["<kinky-rituals.id>", "<dom-overlay.id>"]`.
- [x] **F.7** Playtime exception: NEW `calendars/play/` (`name = "Play"`,
      `emoji = "🎀"`, `color = "#e07a9b"`); event Sat 21:00–23:30
      "playtime — cage off window", body in Keeper voice. (Play
      TODOLIST already exists from Round 2.26.D; this is the CALENDAR.)

## Phase G — Tests + decisions.md + plan status DONE

- [ ] **G.1** `:app:testDebugUnitTest` green; new tests A.6, B.4, D.4
      pass.
- [ ] **G.2** AVD smoke on `emulator-5558`: Tasks Today shows a keeper-
      prompt row with 📸 glyph + Keeper chip; Overdue shows the E.7
      prompt with "open 2d" pill; Respond opens sheet; writing reply
      makes row disappear; screenshot saved.
- [ ] **G.3** Append `D.120 — keeper-prompt mechanic` to `decisions.md`
      summarising D-2.27.a..i.
- [ ] **G.4** All checkboxes ticked; phase headers carry
      `shipped in commit <hash>` notes; status flipped to ✅ DONE.

## Fan-out plan

1. **Subagent 1 — schema-and-codec (Phase A).** Lands first; pure
   data-layer; unblocks 2.
2. **Subagent 2 — projector-and-reader (Phase B + D.2 + D.4).** Depends
   on 1.
3. **Subagent 3 — ui-signaling-and-sheet (Phase C + D.1 + D.3).** Depends
   on 1 + 2.
4. **Subagent 4 — demo-content (Phase E + Phase F).** Filesystem-
   independent; AVD payoff lands after 1-3.

Orchestrator after fan-out: G.

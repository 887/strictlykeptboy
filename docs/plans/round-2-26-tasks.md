# Round 2.26 — Tasks rebuild: vertical rail + unified day-of feed + demo seed

## Status: 🚧 IN PROGRESS

> Background: the current "Tasks pane" is the `ExpandedNowPlayingTaskBody`
> swipe-up sheet (Tasks destination was removed in Round 2.16.E). User
> reports: horizontal pill chips at top (Combined/Today/Per-list/Shopping),
> zero demo content, no left rail parity with Schedule (D.118) / Reviews.
> Round 2.26 reintroduces Tasks as a first-class content destination with
> a vertical left rail, an honest day-of feed merging timebox bands with
> todolist tasks, and a believable rich-demo-repo seed.

## Locked decisions

- **D-2.26.a — Tasks is a TopDestination again.** Re-add
  `TopDestination.Tasks` to the shell enum (was deleted in Round 2.16.E).
  The swipe-up `ExpandedNowPlayingTaskBody` stays for compatibility but
  becomes a thin re-use of the same view-mode body; the rail-driven
  destination is canonical.
- **D-2.26.b — Filter set: `Today / Upcoming / All / Per-list / Done`.**
  Drop `Combined` (vague), drop `Shopping` (mode is a property of a
  todolist, not a top-level filter — Shopping lists surface inside
  Per-list and render shopping-mode rows there). Five rail items,
  default `Today`.
- **D-2.26.c — Today is unified.** "Today" merges todolist tasks
  (via the existing `forToday(today)` filter) AND the day's
  `RenderedSchedule` bands whose `kind == CalendarKind.Timebox`. The
  feed is ordered: (1) overdue todolist tasks, (2) timeboxes by start
  time interleaved with same-day-due todolist tasks (tasks without a
  time sort to the bottom of their day-bucket), (3) no-due pinned
  standing.
- **D-2.26.d — Visual distinction in the row.** A timebox band renders
  with the same `TaskRow` shape but its leading 4dp accent strip uses
  the calendar's color (not the todolist's), and a small clock glyph
  prefixes the title. Tasks linked to a timebox already render the
  `LinkedTimeboxChip`; an unlinked timebox renders no list-chip,
  instead a calendar-name chip.
- **D-2.26.e — Bottom bar stays Schedule-only.** Now/Next is a
  Schedule-context concern. On Tasks destination the bottom bar is
  empty; "next task due" is shown inline as the first Today section
  header instead.
- **D-2.26.f — Demo data goes in the rich-demo-repo only.** No
  in-process `TasksDemoSeed` injection at app boot when the user is on
  the rich-demo repo (per `project_strictlykeptboy_repo_is_identity`).
  `TasksDemoSeed.kt` stays for unit-test fixtures only.
- **D-2.26.g — Five todolists in rich-demo-repo:** `groceries` (exists,
  Shopping mode), `home` (new), `work-sprint-25` (exists, repurpose as
  `work` semantics — keep id for stability), `routines` (new),
  `play` (new). `cat-care` and `daily-rituals` stay as separate
  routines-adjacent lists; they're already seeded.
- **D-2.26.h — Rail label resolver helper.** Mirror
  `scheduleTabLabelRes` / `reviewsFilterLabelRes` with a new
  `tasksFilterLabelRes(TasksFilter)` in `SkbScheduleRail.kt`
  (label-resolvers live there per the existing pattern).

## Phase A — Rail wiring + filter enum (no behavior change yet)

shipped in commit ad7e9a2

- [x] **A.1** Add `TopDestination.Tasks` back to the enum in
      `app/src/main/java/com/eight87/strictlykeptboy/ui/scaffold/SkbAppShell.kt`
      (icon: `Icons.Filled.CheckCircleOutline` or existing tasks icon).
      Update `TopDestination.labelString()` mapping in `EnumLabels.kt`.
- [x] **A.2** Introduce `enum class TasksFilter { Today, Upcoming, All,
      PerList, Done }` in
      `app/src/main/java/com/eight87/strictlykeptboy/ui/tasks/TasksFilter.kt`
      (new file). Add `tasksFilterLabelRes(f: TasksFilter): Int` in
      `SkbScheduleRail.kt` next to the existing helpers. Add five
      string resources `task_filter_today`, `task_filter_upcoming`,
      `task_filter_all`, `task_filter_per_list`, `task_filter_done` to
      `app/src/main/res/values/strings.xml`.
- [x] **A.3** In `SkbAppShell.kt`, extend the `railItems` `when` to
      build rail items for `TopDestination.Tasks` from
      `TasksFilter.entries`. Hoist `tasksFilter` state alongside the
      existing `reviewsFilter` `rememberSaveable` block.
- [x] **A.4** In the destination-content `when`, add a
      `TopDestination.Tasks -> TasksPane(...)` branch. Re-introduce
      `TasksPane.kt` as a thin composable that delegates to a new
      `TasksDestinationBody(filter, tasksState, scheduleState, ...)`.
      Body is a stub ("filter = X" placeholder) — Subagent 2 fills in
      the unified day-of feed in Phase B.
- [x] **A.5** Wire `TasksFilter.PerList` to expose the existing
      per-list selector inline (re-use `TaskSourceRail.kt` chips, but
      mounted under the rail-driven body, not as a header).
- [x] **A.6** Keep `ExpandedNowPlayingTaskBody.kt` working by switching
      its internal chip-strip to a `Row<FilterChip>` over `TasksFilter`
      instead of `TaskViewTab`; deprecate `TaskViewTab` (leave the enum
      for one round, mark `@Deprecated`).

## Phase B — Unified day-of feed (Today merges timeboxes + tasks)

shipped in commit f1668f5

- [x] **B.1** Add `data class UnifiedTodayItem` in
      `app/src/main/java/com/eight87/strictlykeptboy/ui/tasks/UnifiedToday.kt`
      with a sealed hierarchy: `TaskEntry(TaskItem)` and
      `TimeboxEntry(DayBand /* with kind = Timebox */)`. A common
      `sortKey: java.time.OffsetDateTime?` drives ordering.
- [x] **B.2** Pure-function builder
      `fun buildUnifiedToday(tasks: List<TaskItem>, schedule:
      RenderedSchedule, now: ZonedDateTime): List<UnifiedTodayItem>`
      in the same file. Inputs: today's `forToday(today)` task subset +
      `schedule.bandsFor(today).filter { it.kind == CalendarKind.Timebox
      }`. Output ordering per D-2.26.c.
- [x] **B.3** Hoist a `StateFlow<RenderedSchedule>` (or the existing
      one) into `TasksDestinationBody` via constructor param. When the
      Tasks destination is opened, call `buildUnifiedToday` reactively
      via `combine(tasksState.state, scheduleFlow)`.
- [x] **B.4** Render `Today` filter via a new
      `TaskUnifiedTodayView.kt` composable: a LazyColumn that walks
      the unified list and dispatches `TaskRow` for `TaskEntry` and a
      `TimeboxRow` (Phase C) for `TimeboxEntry`.
- [x] **B.5** Unit tests in `ui/tasks/UnifiedTodayTest.kt`: empty
      schedule + 3 tasks; 2 timeboxes + 1 overdue + 1 due-today;
      ordering invariants.

## Phase C — TaskRow + TimeboxRow polish

- [x] **C.1** Add `TimeboxRow.kt` in `ui/tasks/`. Same outer Surface
      shape as `TaskRow` (4dp accent strip, 64dp row height); leading
      strip is the calendar color, then a `Schedule` icon (Material
      Symbols `Schedule`) in place of the checkbox, then title + range
      chip "HH:mm–HH:mm · CalendarName". Long-press routes to the
      schedule's existing event detail overlay.
- [x] **C.2** Polish `TaskRow.kt`: when `item.isOverdue`, paint the
      title in `colorScheme.error` (not just the due chip). Add a
      Round-2.26 KDoc.
- [x] **C.3** Verify `TaskRow` priority-dot tiers still read at the
      new 64dp height; nudge the priority-dot from 10dp → 12dp for
      tap-target legibility.
- [x] **C.4** Section headers in `TaskUnifiedTodayView`: M3 `Text`
      `titleMedium` color `onSurfaceVariant`. Three sections:
      "Overdue (N)" (only if non-empty), "Today", "Pinned standing"
      (only if non-empty). The first non-empty section's first item is
      preceded by a "next: in N minutes" hint subtitle (D-2.26.e
      inline replacement for the bottom-bar Now/Next).

## Phase D — Rich-demo-repo todolist scaffolding — shipped in commit 1a2820e

- [x] **D.1** Verify existing `todolist.toml` files. Existing:
      `cat-care`, `daily-rituals`, `groceries`, `owner-activity-log`,
      `work-sprint-25`. Need new: `home`, `routines`, `play`.
- [x] **D.2** Create
      `app/src/main/assets/rich-demo-repo/todolists/home/todolist.toml`
      with `kind = "todolist"`, `name = "Home"`, `emoji = "🏠"`,
      `color = "#a3b18a"`, `priority = 300`,
      `tz_id = "Europe/London"`, stable UUIDv7 id.
- [x] **D.3** Create `.../routines/todolist.toml` — `name = "Routines"`,
      `emoji = "🌀"`, `color = "#bc6c25"`, `priority = 200`.
- [x] **D.4** Create `.../play/todolist.toml` — `name = "Play"`,
      `emoji = "🎀"`, `color = "#e07a9b"`, `priority = 150`.
- [x] **D.5** Add `tasks/` empty subtree under each new todolist
      (file system creates on first task write — the bucket dirs ARE
      the index, per `decisions.md` D.3).

## Phase E — Demo task seed files (≥15 tasks across lists) — shipped in commit 1a2820e

All files in `app/src/main/assets/rich-demo-repo/todolists/<list>/tasks/2026/05/<slug>.md`.
Today = 2026-05-17 (per env). All `kind = "task"`, ULID/UUIDv7 ids,
`author = "01900000-0000-7000-8000-0000000000b0"` to match existing seed.

### Due today (4)

- [x] **E.1** `groceries/.../2026-05-17-buy-milk.md` — title "buy milk",
      `due = 2026-05-17T18:00:00+01:00`, body "oat milk preferred. the
      blue carton, not the green one.", `priority = 200`,
      `tags = ["dairy", "today"]`.
- [x] **E.2** `groceries/.../2026-05-17-oat-milk-vine-tomatoes.md` —
      title "oat milk + vine tomatoes", `due = 2026-05-17T18:00:00+01:00`,
      body checklist with `- [ ] oat milk` `- [ ] vine tomatoes (6)`.
- [x] **E.3** `work-sprint-25/.../2026-05-17-implement-feature-xyz.md` —
      title "implement feature xyz", `due = 2026-05-17T17:00:00+01:00`,
      `priority = 600`, body "blocked on review feedback; carve out
      the protocol-handler unit tests today."
- [x] **E.4** `work-sprint-25/.../2026-05-17-code-review-pr-1284.md` —
      title "code review for PR #1284",
      `due = 2026-05-17T16:30:00+01:00`, `priority = 400`,
      `tags = ["review"]`.

### Overdue (3)

- [x] **E.5** `home/.../2026-05-15-call-landlord-leaking-tap.md` —
      title "call landlord re: leaking tap",
      `due = 2026-05-15T10:00:00+01:00`, `priority = 700`,
      body "left voicemail Tuesday. follow up if no reply by EOD."
- [x] **E.6** `work-sprint-25/.../2026-05-14-draft-q3-okr-doc.md` —
      title "draft Q3 OKR doc", `due = 2026-05-14T17:00:00+01:00`,
      `priority = 500`, body "first pass. 3 objectives max, 3 KRs each."
- [x] **E.7** `routines/.../2026-05-12-replace-water-filter.md` —
      title "replace water filter — monthly",
      `due = 2026-05-12T09:00:00+01:00`, `priority = 100`,
      `tags = ["monthly", "kitchen"]`.

### Upcoming this week (4)

- [x] **E.8** `groceries/.../2026-05-18-vine-tomatoes-restock.md` —
      due 2026-05-18, "vine tomatoes — restock".
- [x] **E.9** `play/.../2026-05-20-book-boys-birthday-surprise.md` —
      title "book the boy's birthday surprise",
      `due = 2026-05-20T20:00:00+01:00`, `priority = 800`,
      body "secret. don't write it down somewhere he can see ;3 check
      availability for the rope studio Friday evening."
- [x] **E.10** `home/.../2026-05-19-deep-clean-bathroom.md` —
      title "deep-clean bathroom", `due = 2026-05-19`, `priority = 200`.
- [x] **E.11** `work-sprint-25/.../2026-05-21-spike-storage-layer.md` —
      title "spike: alternative storage layer",
      `due = 2026-05-21T14:00:00+01:00`, `priority = 300`.

### No-due-date (2)

- [x] **E.12** `routines/.../order-new-toothbrush-heads.md` —
      title "order new toothbrush heads", no `due`, `priority = 100`.
- [x] **E.13** `play/.../research-spring-rope-workshop.md` —
      title "research the spring rope workshop", no `due`,
      `priority = 250`, body "amsterdam? berlin? must be a positive,
      consent-forward space. shortlist 3."

### Already completed (2)

- [x] **E.14** `home/.../2026-05-16-water-the-plants.md` —
      title "water the plants",
      `due = 2026-05-16T07:00:00+01:00`,
      `done = true`, `completed_at = 2026-05-16T07:14:00+01:00`.
- [x] **E.15** `work-sprint-25/.../2026-05-16-merge-pr-138.md` — add
      `done = true` + `completed_at` to existing PR-138 file rather
      than creating a duplicate.

### Frontmatter shape (reference)

```toml
+++
schema_version = 1
id = "<uuidv7>"
kind = "task"
created_at = 2026-05-09T08:00:00+01:00
updated_at = 2026-05-17T08:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "..."
todolist_id = "<toml id of the parent todolist>"
due = 2026-05-17T18:00:00+01:00   # omit when no due
priority = 200
tags = ["..."]
+++
optional markdown body
```

## Phase F — Wire schedule-feed merge + verify on AVD

- [ ] **F.1** In `MainActivity` / `AppGraph` composition, pass the
      existing `renderedScheduleFlow` (or equivalent) into
      `TasksDestinationBody` alongside `tasksState`.
- [x] **F.2** Confirm that the rich-demo-repo's
      `calendars/work/events/2026/05/` and `calendars/routines/...`
      already contain `kind = "timebox"` events for today. If not,
      add 2-3 timebox seed events for 2026-05-17.
      **Fold-in note (Round 2.26.DE):** `kind = "timebox"` is a
      calendar-level field (set on `calendar.toml`; parsed by
      `ReposPane.kt` ~L1089), not per-event. No existing rich-demo
      calendar declared `kind = "timebox"`. Resolution: added a new
      dedicated `calendars/timeboxes/` calendar with
      `kind = "timebox"` and three events for 2026-05-17 (09:00–11:00
      deep work, 12:30–13:00 lunch walk, 14:00–14:15 standup).
      Calendar id `0190a0aa-1c1d-7000-8a0a-000000000012`. This is
      what `schedule.bandsFor(today).filter { it.kind ==
      CalendarKind.Timebox }` (Phase B.2) will pick up.
- [ ] **F.3** AVD smoke on phone: install debug APK on `emulator-5558`,
      tap Tasks destination, verify (a) rail shows five rotated labels,
      (b) Today renders ≥4 task rows + ≥1 timebox row, (c) Overdue
      section red, (d) screenshot saved + Read for review.
- [ ] **F.4** Tablet AVD smoke (per `pixel_tablet` requirement): same
      flow, confirm rail + body adapt to 1600×2560@160dpi.

## Phase G — Tests + decisions.md update

- [ ] **G.1** `:app:testDebugUnitTest` green. New tests:
      `UnifiedTodayTest` (Phase B.5), `TasksFilterLabelResTest`,
      `TasksRailItemsTest`.
- [ ] **G.2** Append `D.119 — Tasks rail set` to
      `docs/plans/decisions.md` summarizing D-2.26.a..h.
- [ ] **G.3** Tick all checkboxes in this file, add `shipped in commit
      <hash>` to phase headers, flip status to `✅ DONE`.

## Fan-out plan

1. **Subagent 1 — rail-and-filter (Phase A)**. Lands first. Unblocks 2 + 3.
2. **Subagent 2 — unified-feed (Phase B + C.4)**. Depends on 1.
3. **Subagent 3 — row-polish (Phase C.1..C.3)**. Parallel with 2.
4. **Subagent 4 — demo-seed (Phase D + Phase E + F.2)**. Fully independent.

After all four land, orchestrator runs F.1, F.3, F.4 + G.

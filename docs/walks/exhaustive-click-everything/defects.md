# Exhaustive walk — defect log

Running list of every control that failed the grind-walk. Format + lifecycle in
[`README.md`](README.md). Close a defect only off a clean re-walk (CLAUDE.md
"iterate until clean").

## Open

### D-1 — Schedule (Day / 3-day / Week time-grid) → event bands — major
- **Expected:** every event title is legible; concurrent events are
  distinguishable without text bleeding through / colliding.
- **Actual:** time-overlapping events render with their titles **colliding and
  bleeding through each other** — unreadable. Root: `ScheduleDayView.kt:561-641`
  fixed-3-lane horizontal cascade (Base full-width → Regular +1 step → Timebox
  +2 steps). Concurrent bands in adjacent lanes overlap rectangles, and titles
  are left-aligned single strings drawn over each other. Examples seen on demo
  data: 3-day "Evening briefing" overlaps grey "cage / mo…"; Week "Shower" over
  grey "…ddy good morning", "Morning briefing" bleeds through, "Cage on / Cage
  stays on" collide.
- **Repro:** launch → Week (or 3-day) view on the seeded demo repo, scroll the
  06:30–08:00 morning stack. • **Shot:** /tmp/laragana/skb-c0-week.png,
  skb-c0-launch.png
- **Fix direction:** higher-lane bands must fully occlude (opaque) AND every
  band's title must clip/ellipsize to its *own visible* (non-occluded) width so
  no title is painted into a neighbour's column. Verify across Day/3-day/Week at
  every zoom on the morning-stack demo. Must NOT regress the deliberate
  paint-order (later start on top) behaviour.
- **Evidence (element dump):** at 06:30 five bands report the SAME y=710 —
  "Grooming + breakfast" (w686), "Morning routine" (w78, clipped), "Morning
  alarm" (w539), "Cage on" — stacked on the same pixel row. Sub-note: 5-minute
  events (e.g. "Morning alarm" 06:30–06:35) get their time text clipped to ~6px
  height at low zoom — unreadable; fold into the same fix.
- **Status:** fixed-in main (ScheduleDayView.kt — opaque surface-color backing
  on non-base bands so lower-lane glyphs can't bleed through the alpha fill;
  1017 tests green; AVD-verified clean across Day/3-day/Week at all zooms).
  Pending final clean re-walk on 5554 to confirm no regression + close.

### D-3 — Tasks (pulled-up sheet) → "Done" filter → completed tasks — major
- **Expected:** the Done filter lists tasks the user has completed (history).
- **Actual:** Done is ALWAYS empty. Marked the one-off "buy milk" (Groceries)
  done via the "Mark done?" confirm dialog → it vanished from the active feed and
  did NOT appear under Done. Root: `ExpandedNowPlayingTaskBody.kt`
  `TasksFilter.Done -> TaskStandingView(tasks = visibleTasks.filter { it.done })`
  — completed one-offs are not in `visibleTasks` (they're dropped from the active
  feed), so `filter{it.done}` is always empty. The confirm dialog even promises
  "un-finish it from history" but no history surface exists. Needs a real
  completed-task source (query the repo / done-state store, not the live feed).
- **Repro:** open Tasks sheet → check any one-off task → "Yes, mark done" →
  tap "Done" rail tab → empty. • **Shot:** skb-c0-buymilk.png, skb-c0-done2.png
- **Status:** fixed-in main — new `TaskDoneView` reads `uiState.tasks` (full
  list, not the active feed) + filters `it.done` directly, bypassing
  `sortedForStanding()` (which had silently dropped all one-offs). Most-recently-
  done first; inverted Routines stay out. 7 new `DoneFilterTest` cases green;
  AVD-verified the marked task lands in Done. Pending final clean re-walk to close.
- **Note:** checkbox toggle itself WORKS (raises a confirm dialog). Inverted
  Routines tasks (e.g. "replace water filter") use deviation semantics (D.54) —
  checking them is NOT "done", which is correct, not a bug.

### D-4 — Tasks → "Done" filter → empty-state copy — minor
- **Expected:** empty Done reads like "nothing completed yet" (or similar).
- **Actual:** reads "no standing tasks — good boy ;3" with the bat mascot — that's
  the *Standing* view's empty-state, reused because Done routes through
  `TaskStandingView`. Mislabeled. Fix alongside D-3.
- **Repro:** Tasks sheet → Done tab (with no done tasks). • **Shot:** skb-c0-task-done.png
- **Status:** fixed-in main — `TaskDoneView` uses new strings
  `tasks_empty_done_primary` ("nothing finished yet — keep going ;3") +
  `tasks_empty_done_neutral`; Standing view keeps its own copy. Pending re-walk.

### D-5 — Tasks → mark-done → no done-date on interactively-completed tasks — minor
- **Expected:** a task marked done in-app shows a "done" date in the Done list,
  like file-read done tasks do.
- **Actual:** `TasksViewState.toggleDone` flips `done` but never sets `doneAt`, so
  interactively-completed tasks sort/display without a date prefix (only tasks
  whose `doneAt` came from the file reader show one). Pre-existing; surfaced by
  the D-3 fix. Low priority.
- **Status:** open (follow-up)

### D-6 — Settings → Repositories → account row tap — major
- **Expected:** "Tap an account to open its settings" (the screen's own
  instruction) opens that repo's settings (RepoSettingsScreen exists).
- **Actual:** tapping the account row OR its chevron ("Open account settings") is
  a NO-OP — nothing navigates. Root: `ReposCategory.kt:78` calls `onOpenRepo(cfg)`,
  but `SettingsPane.kt:212` defaults `onOpenRepo = {}` and `ShellContext.kt:35`
  constructs `SettingsAccess()` with the default — the lambda was never wired to
  the existing repos overlay / `RepoSettingsScreen`. So per-account settings
  (identity, remotes, mode) are unreachable from Settings → Repositories.
- **Fix:** wire `SettingsAccess.onOpenRepo` at the composition root (SkbAppShell)
  to open the repos overlay (`reposOpen = true`), ideally pre-selecting `cfg` —
  mirror the existing `onAccountTap = { reposOpen = true }`.
- **Repro:** Settings → Repositories → tap "my calendar" (row or chevron) →
  nothing. • **Shot:** skb-c0-account.png, skb-c0-account2.png
- **Status:** fixed-in main — wired `SettingsAccess.onOpenRepo` at the
  composition root (`SkbAppShell.kt`): tapping an account closes Settings, sets
  `pendingOpenRepoId = cfg.repoId`, opens the repos overlay PRE-SELECTED on that
  repo's settings (`ReposOverlayScreen`/`ReposPane` gained `initialRepoId`). 1017
  tests green; agent AVD-verified. Pending orchestrator re-walk on 5554.

### D-7 — Settings → Repositories → Import/export lists only active repo — minor
- **Expected:** "Pick a repo to import into, or export its calendars" with 2
  repos should let you pick either. **Actual:** only "my calendar" (active repo)
  is shown; "demo · kept-life" has no import/export affordance. Verify intent
  (active-only by design?) vs gap. • **Shot:** skb-c0-repos.png
- **Status:** NOT A BUG (resolved) — `ImportExportScreen` iterates all repos and
  filters out `isDemo = true` entries; "demo · kept-life" is an app-internal
  seeded demo (not a user repo on disk), intentionally excluded from import/export.

### D-8 — Event create → created event not visible on schedule — major (investigate)
- **Expected:** creating an event via "+ New" → fill Title → "Add event" makes it
  appear on the schedule at its time.
- **Actual:** created "QA smoke event" (12:00–12:05, calendar = "my calendar",
  the picker's default/active repo). Sheet dismissed cleanly (no error), but the
  event does NOT appear on SAT 13 Day view at noon (verified at Detail zoom — only
  demo events Hydration check / Sunlight / Saturday cardio are there). Create FORM
  is excellent (Free-form/template tabs, inline title validation, date/time,
  Repeat chips, calendar picker showing both repos, Notes, Private toggle, Add
  event). Root cause unknown — either (a) the write didn't persist, or (b) "my
  calendar" repo's calendar isn't in the schedule's visible/enabled overlay set so
  its events never render. Needs tracing the create→persist→snapshot→render path
  + calendar-visibility defaults for a freshly-written event.
- **Repro:** "+ New" → type title → Add event → check the day at the chosen time.
  • **Shot:** skb-c0-create.png, skb-c0-created.png, skb-c0-detail-noon.png
- **Status:** open (investigate + fix)

### D-9 — Wizard → step 1 welcome illustration renders as a black box — minor/major
- **Expected:** the wizard intro ("let's set up your repo") shows its
  "welcome-wave" illustration/sticker.
- **Actual:** it renders as a solid BLACK rectangle (caption "welcome-wave"
  below). On the light theme it clearly reads as broken — and it's the very first
  onboarding screen. Note: the EMOJI species stickers (Bat 🦇 / Bunny 🐰 on the
  "Pick your sticker pack" step) render fine — so the failure is specific to the
  named non-emoji wizard illustrations (welcome-wave etc.). Likely a missing/
  mis-tinted sticker asset or a load failure in the wizard's illustration
  renderer (vs. the emoji path). Wizard FLOW otherwise works (steps advance,
  pack selectable).
- **Repro:** Repos overlay → "+" (add repo) → Wizard step 1. • **Shot:**
  skb-c0-wizard1.png (black box), skb-c0-wizard2.png (emoji stickers OK)
- **Status:** open

### D-2 — Schedule → Week view → day columns — NOT A BUG (resolved)
- **Resolution:** intended behaviour. `SchedulePane.kt:452-466` — when
  `layoutMode == Stacked`, Week renders the 7 days stacked VERTICALLY (each a
  full-width day grid). The 7-COLUMN `ScheduleWeekView` (Grid layout) is reached
  via the "Switch to grid layout" toggle. Verified on-device: toggling grid →
  proper 7-col MON→SUN week (weekday emoji headers, today highlighted, now-line).
  • **Shot:** skb-c0-weekgrid.png
- **Status:** closed (not a defect)

## Closed

- **D-1** — Schedule band title bleed/collision (Day/3-day/Week). Fixed on main
  (`cd90f97`): opaque surface backing on non-base bands. **Re-walk verified clean**
  2026-06-13 on the merged build — Work-day + morning-stack render opaque, no
  bleed. Shots: skb-c0-rewalk-week.png + fix-d1-week3-small.png.
- **D-3** — Tasks "Done" filter never listed completed tasks. Fixed on main
  (`c0f9867`): new `TaskDoneView` over the full task list. **Re-walk verified**
  2026-06-13 — marked "buy milk" done → it now appears in Done alongside the
  seeded completed tasks. Shot: skb-c0-rewalk-done.png.
- **D-4** — Done empty-state copy ("no standing tasks"). Fixed on main
  (`c0f9867`): dedicated `tasks_empty_done_*` strings. Closed with D-3.
- **D-6** — Settings → Repositories account row was a no-op. Fixed on main:
  wired `SettingsAccess.onOpenRepo` to open the repos overlay pre-selected on the
  tapped repo. **Re-walk verified** 2026-06-14 — tapping "my calendar" opens its
  repo settings (Calendars list + Display + Icon). Shot: skb-c0-d6verify.png.
- **D-7** — Import/export listed only the active repo. **Not a bug** — demo repos
  (`isDemo = true`) are intentionally excluded from import/export.

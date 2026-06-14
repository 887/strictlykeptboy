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
- **Status:** fixed-in main — `toggleDone` now stamps `doneAt = today` (clears on
  un-mark) + an `onTaskMarkedDone` hook wired in MainActivity writes `done`/`doneAt`
  to the task file and reindexes (mirrors D-8). 4 new tests. AVD-verified: an
  interactively-completed task shows "done today" in the Done list. Caveats:
  demo tasks are in-memory only (no disk write), and StandingTask has no `doneAt`
  file field yet (in-memory date shows but doesn't survive reindex) — logged as
  D-10 below. Cherry-picked from 49673da.

### D-10 — StandingTask schema lacks a `doneAt` field — minor (follow-up)
- Surfaced by the D-5 fix: standing (recurring) tasks don't persist a completion
  date because `StandingTask` has no `doneAt` field; the Done list shows the
  in-memory date but it doesn't survive a reindex. Add `doneAt` to the
  `StandingTask` TOML schema + writer to make it durable.
- **Status:** open (low priority)

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
- **Status:** fixed-in main — ROOT CAUSE: `EventCreateController.writeEvent()`
  wrote+committed to git but never ran `Indexer.incrementalScan`, so Room's
  cache (which `SourcesPublisher` reads) never saw the new event → schedule never
  re-rendered it. (Demo-mode hypothesis eliminated; visibility defaults to
  visible.) Fix: added an `onEventWritten` hook on the controller, wired in
  `MainActivity` to call `Indexer.incrementalScan` after write+commit → renders
  immediately, no restart. +1 Robolectric regression test. AVD-verified the
  created event appears same-session. Cherry-picked from afb3b94. Pending
  orchestrator re-walk on 5554.

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
- **Status:** fixed-in main — `BatMascotSticker` in `WizardNavHost.kt` used
  `Icon` (force-tints to content color = black on light bg) for a raster WebP;
  switched to `Image` (no tint), matching `AboutCategory.kt`. Verified full-color
  in BOTH themes (fix-d9-wizard-light.png). 1017 tests green. Cherry-picked from
  e05a181.

### D-11 — Todolist → Upcoming filter is a stub — major (UX)
- **Expected:** "Upcoming" lists FUTURE-dated tasks (what's coming, not today).
- **Actual:** `ExpandedNowPlayingTaskBody.kt` routes Upcoming through
  `TaskCombinedView` filtered to `done == false` — it shows ALL non-done tasks
  (today-dated + no-due included), so Upcoming ≈ All. The most conceptually broken
  filter (code comment even names the missing follow-up). Surfaced by the
  snow-leopard/cheetah todolist walks.
- **Status:** fixed-in main (b32fc32) — new `forUpcoming()` predicate (future-dated,
  not-done, chronological); 7 tests. Pending re-walk.

### D-12 — Todolist → By Repo headers show raw repoId — major (UX)
- **Expected:** group headers show a friendly repo display name ("marc-kept").
- **Actual:** `ExpandedNowPlayingTaskBody.kt` ~line 228 renders `Text(text =
  repoId)` — the raw git slug/path (e.g. "github.com/887/marc-kept"). Use the
  repo's display name (same source the repo settings + switcher use).
- **Status:** fixed-in main (b32fc32) — `repoDisplayNames` map in TasksUiState;
  header resolves displayName with last-segment fallback; 5 tests. Pending re-walk.

### D-13 — Todolist → Done view omits author chip + repo dot — major (UX)
- **Expected:** Done rows show "this was Sir's task" (author chip) + repo dot —
  exactly where a kept user wants to see who assigned the completed item.
- **Actual:** `TaskDoneView.kt` calls `TaskRow` WITHOUT `multiRepo` /
  `activeRepoOwner`, so author chips + repo dots are absent in Done (present in
  Today/All). Thread those params through.
- **Status:** fixed-in main (b32fc32) — threaded `multiRepo`/`activeRepoOwner`
  into `TaskDoneView`→`TaskRow`; chips + dots now show in Done. Pending re-walk.

### D-14 — Todolist → Quick Add velocity: no auto-focus + IME Done not wired — major (UX)
- **Expected:** tapping Quick Add focuses the field + raises the keyboard; the
  keyboard "Done" key submits. Target: 3 interactions to add.
- **Actual:** the Quick Add `OutlinedTextField` has no `focusRequester` (keyboard
  doesn't auto-appear → +1 tap) and `singleLine` IME Done isn't wired to
  `onSubmit` (Done key is a no-op) → 4–5 interactions. Hurts the cheetah
  "fast" path most. Add focusRequester + KeyboardActions(onDone=onSubmit) +
  ImeAction.Done.
- **Status:** fixed-in main (b32fc32) — Quick Add auto-focuses (keyboard raises)
  + IME Done submits (shared submitAction). Pending re-walk.

## Todolist-walk findings (Dustrun / cheetah-fast QA run, 2026-06-14)

### D-16 — Todolist sheet → invisible MiniPlayer overlay eats top ~309px — BLOCKER
- When the sheet is fully expanded (`progress=1f`), `miniAlpha=0f` but the
  MiniPlayer `Box` (height 118dp ≈ 309px) stays in the composition at alpha 0 and
  KEEPS intercepting pointer events in y:0–309. Result: the FIRST task row's
  checkbox (y≈271) and the Today rail center (y≈228) are unreachable — taps don't
  register. `ui/scaffold/NowPlayingSheetHost.kt` ~lines 336–362. (This explains an
  earlier grind anomaly where a top-row checkbox seemed a no-op.) Fix: only
  compose/hit-test the MiniPlayer when `miniAlpha > 0f`.
- **Status:** open — URGENT (fix dispatched)

### D-17 — Todolist → "Mark done?" dialog buttons below 48dp tap target — major
- `TaskRow.kt` AlertDialog TextButtons (~20dp tall) are under the M3 48dp minimum
  → mis-taps at speed. Add `Modifier.defaultMinSize(minHeight = 48.dp)`.
- **Status:** open

### D-18 — Todolist → Quick Add auto-focus races the sheet animation — major
- `TaskQuickAddFab.kt` `LaunchedEffect(Unit){focusRequester.requestFocus()}` runs
  before the ModalBottomSheet settles on FIRST open → keyboard doesn't raise (first
  add = 4 interactions, not 3). Subsequent opens work. Fix: gate focus on
  `sheetState` visibility/settle (or small delay). (Refines D-14.)
- **Status:** open

### D-19 — Todolist → empty Quick Add submit is a silent no-op — minor
- `TaskQuickAddFab.kt` `if (cleaned.isEmpty()) return@submit` — no feedback →
  double-tap confusion. Add a shake/snackbar "Title can't be empty".
- **Status:** open

### D-20 — Todolist → rail order top-heavy for one-handed reach — minor (UX)
- Rail is Today→…→Done top-to-bottom; Today (primary) sits at the hard-to-reach
  top, Done (secondary for the complete flow) lowest. Consider inverting so the
  primary filter is at thumb-native height. `ExpandedNowPlayingTaskBody.kt:125`.
- **Status:** open (design)

### D-21 — Todolist → By Repo blank header for tasks with empty repoId — minor
- `groupBy { it.todolist.repoId }` with empty repoId renders a blank section
  header (seen for demo-mode tasks). Add a fallback label. `ExpandedNowPlayingTaskBody.kt:219`.
- **Status:** open

### D-22 — Todolist UX gaps (cheetah) — polish batch
- U-1 no "all done" delight state on Today; U-2 confirm dialog also fires on
  UN-mark (un-doing should be instant); U-3 Quick Add default target chip isn't
  "Today"; U-4 no haptic on completion; U-5 drag handle small; U-6 no count badge
  on Today rail; U-7 IME Done submits but doesn't dismiss the sheet.
- **Status:** open (polish — pick the high-value ones)

## Todolist-walk findings (Glaciermark / snow-leopard-thorough QA run, 2026-06-14)
_Confirmed working by this walk: D-5, D-11, D-12, D-14 (live) + D-13 (code). B-2
"Today rail dead after Done" and B-4 "first checkbox no-op" are the same dead-zone
as **D-16** (already in fix). New findings below._

### D-23 — Quick Add adds an EVENT, not a todolist task (no list target) — CRITICAL
- The Quick Add sheet's Target row offers only "Today as event" / "Tomorrow as
  event" — there is NO way to add the new item to a TODOLIST. So "Quick add" on the
  todolist sheet doesn't add a task to any list (it creates a calendar event).
  This breaks the core "add a task Sir assigned" flow. (Explains why my earlier
  "cheetah quick task" never showed as a task.) Add a todolist-target chip row
  (pre-select last-used list); keep event-targets as a secondary option.
  `ui/tasks/TaskQuickAddFab.kt`. **Status:** open
### D-24 — All view lost its per-list filter chips — major
- The All tab is a flat combined list with no chip row to filter by todolist
  (can't isolate marc-kept vs marc-work). `TaskPerListView` chips used to exist;
  restore a per-list slicer on All. **Status:** open
### D-25 — Sheet dismisses on ANY downward swipe in content — CRITICAL (UX)
- Swiping down to scroll back up a long Done/Today list DISMISSES the whole sheet
  → user loses place + must re-pull/re-navigate/re-scroll. Restrict dismiss to the
  drag handle (or only when the list is at top). `ui/scaffold/NowPlayingSheetHost.kt`
  nested-scroll. **Status:** open
### D-26 — Today sub-section headers (Overdue/From events/Pinned) not visible — major
- `TaskTodayView.kt` calls `sectionHeader()` but headers don't render live (likely
  empty-section suppression) → 15 overdue tasks are a flat wall with no provenance.
  Show headers (with counts) for non-empty sections. **Status:** open
### D-27 — Completing a task vanishes it with no feedback — major (UX/feel)
- On "Yes, mark done" the task instantly disappears from Today — no strikethrough/
  dim/"moved to Done" beat. The completion moment (esp. Dom-assigned) is the
  product's emotional core. Add ~600ms strikethrough+dim before it slides out.
  **Status:** open
### D-28 — No undo after marking done — major
- No "Marked done — Undo" snackbar; an accidental confirm is permanent unless you
  hunt through Done and re-toggle. Add a 5s undo snackbar. **Status:** open
### D-29 — Upcoming is a flat list, no date grouping — major (UX)
- After D-11 made Upcoming future-only, it still has no "Tomorrow / This week /
  Next week" dividers → items feel "missing". Add date-bucket grouping.
  **Status:** open
### D-30 — Passive-habit ("From events") tasks visually indistinct — moderate
- Standing/recurring ritual items look identical to dated tasks (no gutter accent/
  recurring icon) → blend into the pile. Give them a distinct treatment.
  **Status:** open
### D-31 — Task checkbox has empty contentDescription (a11y) — major
- TalkBack reads the checkbox unlabeled; the title is a sibling, not merged into
  the checkbox semantics → a blind user can't tell which task they're toggling.
  Merge title into the row/checkbox semantics. **Status:** open
### D-32 — AuthorBubble + PriorityDot have no contentDescription (a11y) — minor
- Decorative-only to TalkBack; add semantic labels (author name / priority).
  **Status:** open
### D-33 — Confirm + empty-state copy not identity.toml-driven — enhancement (Phase K)
- "Mark done?" / empty-states are hardcoded; could honor `praise`/`honorific`
  ("Done, good boy?" / "Sir would be pleased"). Ties to D-4/D-15 copy work.
  **Status:** open (enhancement)
### D-34 — Demo data lacks Boy-Keeper-authored TASKS — demo-data gap
- Boy Keeper authors only calendar events, so the D-13 author-chip-in-Done fix
  can't be SEEN in the demo. Seed some Dom-authored todolist tasks. **Status:** open

### D-15 — Todolist → Upcoming empty-state copy is misleading — minor (UX polish)
- The Upcoming filter (now future-only) shows the generic "no tasks anywhere —
  good boy ;3" empty-state when there are no FUTURE tasks — but there ARE tasks
  (today/past), so "anywhere" is wrong. Should be Upcoming-specific, e.g.
  "nothing coming up — good boy ;3" (mirror the D-4 per-filter copy approach).
- Surfaced verifying D-11. • **Shot:** skb-c0-upcoming.png
- **Status:** open (minor)

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
- **D-8** — Created event didn't render on the schedule. Fixed on main: create
  path now triggers `Indexer.incrementalScan` after write+commit (onEventWritten
  hook) so Room's cache + schedule update immediately. **Re-walk verified**
  2026-06-14 — created "D8 rewalk event" → bottom bar shows "Next: D8 rewalk
  event in 10h 22m". Shot: skb-c0-d8verify.png. +1 regression test.
- **D-9** — Wizard welcome illustration rendered as a black box. Fixed on main:
  `Icon`→`Image` (no force-tint) for the raster mascot. Verified full-color both
  themes (agent shot fix-d9-wizard-light.png + merge confirm). Closed.
- **D-5** — Interactive mark-done didn't set `doneAt` (no date in Done list).
  Fixed on main: `toggleDone` stamps `doneAt` + writes it to the file + reindexes.
  **Re-walk verified** 2026-06-14 — "buy milk · done today" shows in Done with the
  date prefix. Shot: skb-c0-d5verify.png. (Spawned D-10 minor follow-up.)
- **D-11..D-14** — Todolist UX wave (b32fc32). **All re-walk-verified** 2026-06-14:
  D-11 Upcoming future-only (empty on past-dated demo vs All ~20; skb-c0-upcoming.png);
  D-12 By-Repo headers show "my calendar"/"demo · kept-life" not slugs (skb-c0-byrepo.png);
  D-13 Done shows repo dots + author accent (skb-c0-donechips.png); D-14 Quick Add
  auto-focuses + IME Done submits (skb-c0-quickadd.png/quickadd2.png). +12 tests.

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

### D-2 — Schedule → Week view → day columns — verify
- **Expected:** Week view shows 7 day columns side-by-side.
- **Actual:** appears to render a single full-width day column (needs
  confirmation — header was scrolled off). Possibly correct (sliding window) or a
  layout regression. • **Shot:** skb-c0-week.png
- **Status:** open (verify in re-walk)

## Closed

_(none yet)_

# Round 2.1 audit — Schedule + Tasks rendering surface

Read-only audit. The user's complaint: "you're not really using the multi
repositories and are making calendars manageable by repository". The
genesis prompt (2026-05-10) frames the app as an overlay of many calendar
sources (morning routine + work/school + dom/sub + clubs + special-event
overrides) authored by multiple people, with toggles and time-window
activation. This audit checks whether the *rendered* surface honors that
intent today.

TL;DR: the **resolver layer was designed exactly right** for multi-repo
overlay — `ActiveSetEvaluator`, `OverlayResolver`, `Renderer` already
fold every active calendar from every repo, apply supersedence, tag
off-schedule events, and produce a single `RenderedSchedule` of
collision-laned bands. But:

- the **data feed** (`AppGraph.snapshot` / `AppGraph.sources`) is a pair
  of empty `MutableStateFlow`s with a `// Phase F→G integration is still
  pending` comment — the Indexer → resolver bridge has never landed
  (`composition/AppGraph.kt:198-219`), so the schedule UI is showing
  whatever stub anyone seeds, not real cross-repo data;
- the **views** project the `DayBand`s as title-only rectangles with no
  per-calendar color, no per-repo affordance, no author chip, no
  timebox-vs-regular glyph, no supersedence/off-schedule glyph;
- there is **no UI surface anywhere** for toggling calendars active /
  inactive, picking which repos overlay, or scoping a calendar to an
  hour window — `activeToggle` / `activeWindows` / `activeHours` exist
  as data fields in `CalendarMeta` and are read by `ActiveSetEvaluator`,
  but nothing in `ui/schedule/` or `ui/tasks/` writes to them;
- tasks are even further from intent: `TasksViewState` is a single flat
  list with no multi-todolist overlay logic, no per-list priorities at
  render time, no time-window scoping, and no link back to the schedule
  for "today's chores from the calendar".

## Per-view findings

### Day view — `ScheduleDayView.kt`

1. **What exists.** 60-dp / hour timeline with hour gutter + clickable
   hour rows (`HourLines`, line 109) and a `BandsLayer` (line 125) that
   paints each band as a `Surface` colored uniformly
   `MaterialTheme.colorScheme.surfaceContainer` (line 139) — every band
   the same neutral grey. Lane width / offset comes from
   `band.totalLanes` / `band.laneIndex` (line 129), which the
   `OverlayResolver` populates correctly. Inside each band: title
   (titleSmall, 1 line, ellipsis) + `HH:MM–HH:MM` (line 150-161). Red
   `NowLine` for today (line 169).
2. **Intent check.**
   - Multi-repo overlay by default: **resolver: yes; UI: blind**. The
     `ScheduleViewState` consumes `snapshotFlow` (multi-repo) and the
     resolver merges them, but the band rendering throws away
     `band.instance.repo` and `band.instance.calendar` — visually one
     repo and 50 repos look identical.
   - Active/inactive toggles per source: **NO UI**. No chip rail, no
     filter sheet, no overflow menu. `CalendarMeta.activeToggle` is
     read by the evaluator but nothing flips it from the schedule pane.
   - Time-window activation: **NO UI**. `activeWindows` /
     `activeHours` are data-layer fields; only `RoutineCalendarConfig`
     writes them at calendar-creation time.
   - Supersedence visibility: **invisible**. `Renderer.filterForViewMode`
     (line 143-149) actively **drops** any band with
     `supersededByCalendar != null`. The user gets no "vacation paused
     your routine" cue at all.
   - Timebox vs regular distinction: **no visual distinction**. Both
     render as the same grey rectangle. `CalendarKind` exists in
     `Types.kt:59` but is never read in `ui/schedule/`.
   - Author chip: **absent from the band**. Author surfaces only in the
     `EventDetailSheet` (line 188-204), requires tapping in.
   - Cross-repo authored events: render iff the cache feeds them in,
     but the dom-vs-sub authorship is invisible until the sheet opens.
   - Off-schedule: `band.offSchedule` is computed (Renderer.kt:124-141)
     and **never read**.
3. **Gap analysis.** Day view is a stub. It shows what the resolver
   gives it but does not telegraph multi-source-ness, supersedence,
   author, or kind. Worst: dropping superseded bands silently breaks
   the user's mental model — they wanted to *see* the routine paused,
   not have it vanish.

### Week view — `ScheduleWeekView.kt`

1. **What exists.** 7 columns × 24 hour rows, horizontal-swipe-to-paginate
   week (line 80-90), today-column highlight (`primaryContainer` bg,
   line 96-98), per-day `WeekNowLine` (line 224). Bands are tiny
   `Surface`s with `labelSmall` title only (line 209-215).
2. **Intent check.** Same as Day, worse — bands are so small that the
   single line of title is often clipped. No room for an author dot,
   no color, no kind glyph, no supersedence treatment.
3. **Gap analysis.** Week view is the worst victim of "one grey
   rectangle per event" because density is highest here and visual
   cues most useful. A 4-px left accent stripe (color-from-calendar)
   would already double the legibility of multi-source overlay.

### Month view — `ScheduleMonthView.kt`

1. **What exists.** 6×7 grid, fixed (so months don't reflow). Each cell
   shows up to 3 chips (`MaxChipsPerCell = 3`, line 37) + "+N more"
   overflow chip. Chips are `secondaryContainer` rectangles with title
   text only (line 143-159). Today highlight + dimmed out-of-month
   days (line 115-119).
2. **Intent check.** Chip color does NOT vary by calendar/repo (every
   chip is `secondaryContainer`). No emoji rendered, no author. Overflow
   chip is a generic "+N more". Supersedence again invisible.
3. **Gap analysis.** Critical for the "Saturday I have a soccer game +
   dom's session + birthday" overlay use case — three chips of the
   same colour communicate nothing about which life-area is colliding.

### Year view — `ScheduleYearView.kt`

1. **What exists.** 12-month heat map (3×4 portrait, 4×3 landscape).
   `intensityForCount` (line 133) shades each day cell by *count* of
   bands (`primary.alpha = 0.2..1.0`). Tap-month navigates to Month
   tab.
2. **Intent check.** Heat-map shades by count only. The most useful
   signal here would be "which calendars contributed to this density" —
   currently invisible. No way to filter the heat map down to a single
   calendar or a single repo.
3. **Gap analysis.** Year is essentially a density visualization with
   no source-attribution layer. Adequate as a navigation aid; falls
   short of the overlay-thinking the user described.

### Agenda / Timebox view — `ScheduleTimeboxView.kt`

1. **What exists.** Today's bands as edge-to-edge cards (~120 dp, "now"
   card 140 dp + 2-dp outline). Title + emoji prefix + time range +
   "N min remaining" for the in-progress card. a11y
   `contentDescription` is well-written (line 99-104).
2. **Intent check.** This view is *closest* to honoring the
   genesis-prompt intent — it's the "what is the bat focused on right
   now" surface — but it still doesn't expose which calendar each block
   came from, doesn't distinguish timebox-from-regular (the view's name
   notwithstanding), and doesn't show "this would have been routine X
   but is currently superseded by vacation overlay Y".
3. **Gap analysis.** The Agenda view is supposed to be the timebox
   surface (per `ScheduleViewState:92-95`). It treats every event as a
   timebox. There is no rendering branch for "this is a regular event,
   not a focus block".

### Tasks — `TasksPane.kt` + 5 sub-views

1. **What exists.** 5 tabs: Combined / Today / PerList / Shopping /
   Standing. `TasksViewState` (60 LOC) is a single
   `MutableStateFlow<TasksUiState>` with `tasks: List<TaskItem>` flat.
   Each `TaskItem` references a `TodolistInfo` (has `repoId`, `name`,
   `colorSeed`, `mode`, `priority`). `TaskRow` (170 LOC) renders a
   4-dp colored left accent (`colorFromSeed`, line 53), checkbox,
   title, list-name chip, due chip, priority dot. `TaskCombinedView`
   simply lists all tasks sorted by `sortedForCombined`.
2. **Intent check.**
   - Multi-todolist overlay: tasks layer is **flat in-memory** — no
     `ActiveSetEvaluator` analogue is invoked for todolists at all.
     The active-set evaluator HAS `activeTodolistsAt` (line 62) but
     `TasksViewState` doesn't use it.
   - Active/inactive toggles per todolist: no UI.
   - Time-window-scoped activation: no UI; `TodolistMeta.activeHours`
     exists in `Types.kt:97` but tasks view never asks the evaluator.
   - Priority overlay: `TodolistInfo.priority` is stored but only used
     by `sortedForShopping` (line 89). Combined view ignores it.
   - Author chip: `TaskItem.author: String = ""` (`TaskModels.kt:45`)
     **never rendered** by `TaskRow`. Identical to events: the bat
     can't see "this chore was added to my list by master".
   - Tasks ↔ timebox integration: **none**. No drag from task to
     timebox, no `TaskSource.FromEvents` reciprocal "today this event
     spawns a task" view. The `FromEvents` source enum exists but no
     adapter writes it.
   - Repo identity: `TodolistInfo.repoId` is stored but never shown.
     User can't tell a chore from their own repo vs from a shared
     household repo.
3. **Gap analysis.** Tasks is structurally further from intent than
   schedule — it doesn't even consult the resolver. Implementation is
   "todo list app shaped like 5 tabs"; the multi-list-overlay-with-
   priorities-and-time-windows promised by the genesis prompt isn't
   even attempted.

## Cross-cutting: the data feed is empty

The most load-bearing finding: `composition/AppGraph.kt:198-219` is
the bridge that *should* push live `RepoSnapshot` + `Renderer.Sources`
from the Room indexer to the schedule pane. It is currently a pair of
empty `MutableStateFlow`s. Comment on line 199:

> Phase F→G integration is still pending: live RepoStore → Room
> bridge has not landed. Until then, the schedule pane and the
> Auto surface both observe these empty flows. When the bridge
> lands, only this section changes.

`grep` for writers shows: nothing pushes to `AppGraph.snapshot` or
`AppGraph.sources`. So even before the *visual* gap-analysis above,
the *plumbing* gap means the schedule renders empty in production
unless a test / demo seed populates it.

This is the single biggest reason the app "doesn't make sense yet" —
the resolver is plumbed for multi-repo overlay but is fed nothing.

## Round 2.1 proposed sub-steps

### Phase 2.1-DataBridge (prereq — gates everything else)

- [ ] **2.1-DataBridge.1** Implement `IndexerSnapshotPublisher` in
      `composition/` that observes `CacheDatabase` DAOs + `RepoStore.state`
      and emits a `RepoSnapshot` (calendars+todolists across **all**
      enabled repos) to `AppGraph.snapshot`. One `combine` per active
      repo, fold the lists, recompute `contentHash`.
- [ ] **2.1-DataBridge.2** Implement `SourcesPublisher` that emits a
      visible-range-windowed `Renderer.Sources` (events + rules +
      exceptions + deviations + overrides) by querying the cache DAOs
      with the schedule pane's currently selected `DateRange`. Wire
      to `AppGraph.sources`.
- [ ] **2.1-DataBridge.3** Unit-test the publishers with two seeded
      repos and assert that `RepoSnapshot.calendars` contains entries
      from both, `Renderer.Sources.events` is the union.
- [ ] **2.1-DataBridge.4** Delete the empty-stub comment block at
      `AppGraph.kt:198-219`.

### Phase 2.1-Schedule

- [ ] **2.1-Schedule.1** Per-calendar color seed. Pipe
      `CalendarMeta.colorSeed` (or fall back to `displayName.hashCode()`)
      from snapshot through to `DayBand` — extend `DayBand` with
      `accentColorSeed: Int`. Apply as 4-dp left stripe in
      `ScheduleDayView.BandsLayer`, full-fill (with chroma reduced) in
      `ScheduleWeekView.DayColumn`, chip background-tint in
      `ScheduleMonthView.MonthCell`, dot in `ScheduleYearView`'s
      heat-map legend.
- [ ] **2.1-Schedule.2** Author chip on the band itself. Add a tiny
      avatar bubble (16 dp circle, initials or stickerpack ref from
      identity.toml) in the top-right corner of every Day/Week/Agenda
      band when `band.instance.author != null` AND the author differs
      from the active repo's owner. Skip on Month for density reasons.
- [ ] **2.1-Schedule.3** Kind glyph. `CalendarKind.Timebox` bands get
      a leading hourglass / target glyph; `CalendarKind.Regular` get
      a calendar dot. Pipe `kind` through `DayBand`.
- [ ] **2.1-Schedule.4** Stop dropping superseded bands. Change
      `Renderer.filterForViewMode` to keep them, render with
      `alpha = 0.35f` + strike-through + a leaf glyph (the user's
      "vacation pauses routine" mental model). Tap → detail sheet
      explains "paused by `<superseding-calendar>` from `<date>`".
- [ ] **2.1-Schedule.5** Off-schedule treatment. `band.offSchedule == true`
      gets a dashed border + small warning glyph. Today: silently
      ignored.
- [ ] **2.1-Schedule.6** **Source-toggle rail**. New composable
      `ScheduleSourceRail` rendered above the day/week/month/agenda
      content (and inside the master pane for two-pane mode). Renders
      a horizontal-scrolling row of `FilterChip`s — one per active
      calendar across all enabled repos. Each chip: emoji, name, color
      dot, selected-state = `activeToggle && in-window-at-now`. Tap to
      flip an in-memory override; long-press opens a calendar settings
      sheet (active windows, active hours, supersedence list). The
      in-memory override layer pipes into a new `OverrideSource` that
      `ActiveSetEvaluator` consults. Persistence to `calendar.toml`
      `active_toggle` lives in `RoutineCalendarConfig` (already there).
- [ ] **2.1-Schedule.7** Repo-grouped collapse. On Month/Year, add a
      "group-by-repo" toggle in the overflow menu that re-colors all
      chips by repo (instead of by calendar) and shows a count badge
      per repo in the source rail. Helps the dom/sub use case ("how
      much am I writing into the sub's repo vs my own").
- [ ] **2.1-Schedule.8** Detail sheet adds a "source" section above
      the existing calendar chip: repo name (with repo icon), calendar
      name, kind (Timebox / Regular), author. Replace the bare
      `band.instance.calendar.id` fallback at `EventDetailSheet.kt:141`
      with the resolved `CalendarMeta.displayName`.
- [ ] **2.1-Schedule.9** Empty-state copy correction. Empty-state now
      says "no events today" which is misleading when the cause is "no
      active calendars". `EmptyScheduleState` should distinguish:
      (a) no repos configured, (b) repos configured but every calendar
      currently inactive, (c) all calendars active but no events in
      this range. Each gets a distinct CTA.
- [ ] **2.1-Schedule.10** Timebox view stops treating every event as a
      timebox. Filter to `CalendarKind.Timebox` bands only; regular
      events go to a secondary "scheduled events on top of your time
      blocks" section below.

### Phase 2.1-Tasks

- [ ] **2.1-Tasks.1** Wire `ActiveSetEvaluator.activeTodolistsAt` into
      `TasksViewState`. Tasks from inactive todolists drop out of the
      Combined view (matches schedule semantics). Add a "show inactive"
      toggle.
- [ ] **2.1-Tasks.2** Source rail for tasks (mirror of 2.1-Schedule.6).
      One chip per todolist; emoji + color seed + name; tap to filter,
      long-press to open list-settings sheet (priority, active-windows,
      active-hours, mode = Standard / Shopping).
- [ ] **2.1-Tasks.3** Render `TaskItem.author` in `TaskRow` — small
      avatar bubble next to the list-name chip when author is
      non-empty and not the active repo's owner. Same visual as
      2.1-Schedule.2.
- [ ] **2.1-Tasks.4** Render `TodolistInfo.repoId` as a tiny repo dot
      on `TaskRow` (matches multi-repo overlay intent). Suppress when
      only one repo is configured.
- [ ] **2.1-Tasks.5** Apply `TodolistInfo.priority` in
      `sortedForCombined` — high-priority lists' tasks float to the
      top of the active section, ahead of due-date ordering for
      same-day items.
- [ ] **2.1-Tasks.6** Time-window-scoped priority bump. When the
      current time is inside a todolist's `activeHours`, its tasks get
      an effective priority boost (e.g. +50). Matches the user's "work
      todolist items become higher priority during work hours" line
      from the genesis prompt.
- [ ] **2.1-Tasks.7** Tasks ↔ timebox integration. Long-press a task in
      Today view → "Schedule as timebox" opens
      `EventCreateController.openSheet` pre-populated with the task
      title + a `relatedTaskId` field. On confirm, the new event also
      mutates the task (link back). Render the linked timebox time on
      the task row as an inline chip ("12:00 today").
- [ ] **2.1-Tasks.8** Reciprocal "spawned-from-event" tasks. The data
      bridge (2.1-DataBridge.2) needs to project recurring chore
      events into `TaskItem(source = TaskSource.FromEvents)` so they
      land in `forToday`. Today the enum value exists but no producer
      writes it.
- [ ] **2.1-Tasks.9** Per-list view shows the list's active-window /
      active-hours summary at the top, plus a "this list is currently
      inactive" banner when the evaluator says so.

### Phase 2.1-Polish

- [ ] **2.1-Polish.1** AVD smoke test: seed three calendar repos
      (morning-routine, work, dom-overlay), three todolists, take
      screenshots of Day/Week/Month/Agenda/Year + Tasks Combined/Today
      under the new source rail. Verify the dom-overlay events show
      author chip + repo dot.
- [ ] **2.1-Polish.2** Resolver unit test: assert that an event from
      repo-A with `author = dom-persona` rendered in repo-B's schedule
      view (because repo-B is the user's active context) still carries
      `author = dom-persona` through to the `DayBand`, so the chip can
      render the cross-repo authorship correctly.
- [ ] **2.1-Polish.3** Resolver unit test: assert supersedence keeps
      the suppressed band in the output (with `supersededByCalendar`
      set), reversing today's `filterForViewMode` drop.

## Key file references

- Resolver pipeline (correct, multi-repo-ready):
  `app/src/main/java/com/eight87/strictlykeptboy/resolver/Renderer.kt:46-114`
  `app/src/main/java/com/eight87/strictlykeptboy/resolver/ActiveSetEvaluator.kt:23-59`
  `app/src/main/java/com/eight87/strictlykeptboy/resolver/OverlayResolver.kt:26-85`
- Empty data bridge (the breakage):
  `app/src/main/java/com/eight87/strictlykeptboy/composition/AppGraph.kt:198-219`
- View-mode-aware filter that hides superseded bands:
  `app/src/main/java/com/eight87/strictlykeptboy/resolver/Renderer.kt:143-150`
- Per-view stub rendering:
  `app/src/main/java/com/eight87/strictlykeptboy/ui/schedule/ScheduleDayView.kt:125-166`
  `app/src/main/java/com/eight87/strictlykeptboy/ui/schedule/ScheduleWeekView.kt:164-222`
  `app/src/main/java/com/eight87/strictlykeptboy/ui/schedule/ScheduleMonthView.kt:101-180`
  `app/src/main/java/com/eight87/strictlykeptboy/ui/schedule/ScheduleTimeboxView.kt:89-142`
  `app/src/main/java/com/eight87/strictlykeptboy/ui/schedule/ScheduleYearView.kt:73-130`
- Author chip lives in detail sheet only:
  `app/src/main/java/com/eight87/strictlykeptboy/ui/schedule/EventDetailSheet.kt:188-204`
- Tasks layer that ignores resolver entirely:
  `app/src/main/java/com/eight87/strictlykeptboy/ui/tasks/TasksViewState.kt:35-60`
  `app/src/main/java/com/eight87/strictlykeptboy/ui/tasks/TaskRow.kt:44-127`

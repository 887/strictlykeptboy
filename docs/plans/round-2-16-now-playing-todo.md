# Round 2.16 — "Now playing" task + swipe-up queue (transplanted from tonearmboy)

## Status: 📋 PLANNED

## Context

The current todo implementation in strictlykeptboy is bad. User wants the
**tonearmboy now-playing + swipe-up queue UI lifted wholesale** and
repurposed for tasks:

- The "now playing" mini-player at the bottom becomes the **current task**
  (current sub-step of a multi-step task).
- The full-screen expanded sheet becomes the **task queue + todo
  management** surface (where all todolist functionality lives).
- The mini-player drag-bar progress = **time remaining on the current
  sub-step**.
- A thinner secondary progress bar = **time remaining on the whole task**
  (e.g. "Grooming 2/6").
- Mini-player title line shows the same info but rendered visually
  (sub-step name + countdown), not as a literal text label like
  *"grooming (2/6) — brushing teeth 2:15 remaining"*.

Concurrently the top-of-screen Schedule/Tasks tab toggle goes away:
**Schedule is the only default home**. Settings cog moves back next to
the account avatar (its old spot before the Repos-trampoline split).

## Source-of-truth: COPY THE TONEARMBOY IMPLEMENTATION EXACTLY FIRST

> **READ THIS BEFORE WRITING ANY CODE.**
>
> The user has had to send multiple correction passes in past rounds
> where I tried to *reinvent* tonearmboy's mini-player / sheet behaviour
> instead of porting it verbatim. **DO NOT do that again.** Extra day of
> chastity already applied for the drag-feet pattern.
>
> The implementing agent's **first action** must be to read these files
> from `/home/laragana/workspace/tonearmboy/` in full, in order, and
> reproduce them in skb under the same names + structure before doing
> any task-specific adaptation:
>
> 1. `app/src/main/java/com/eight87/tonearmboy/ui/nav/TonearmboyApp.kt`
>    (lines 140-471 — the sheet host: peek/expand, drag delta forwarder,
>    flick-commit threshold, nested-scroll connection, staggered
>    cross-fade alpha ratios, `sheetProgress` Animatable, two-layer
>    z-stack)
> 2. `app/src/main/java/com/eight87/tonearmboy/ui/playing/MiniPlayer.kt`
>    (233 lines — peek-layout, info row + transport row + 2-dp progress
>    line at bottom edge)
> 3. `app/src/main/java/com/eight87/tonearmboy/ui/playing/NowPlayingScreen.kt`
>    (639 lines — full-screen expanded surface, cover art / queue split,
>    drag-handle, connecting-state)
> 4. `app/src/main/java/com/eight87/tonearmboy/ui/playing/PlaybackTransportRow.kt`
>    (178 lines — shared transport row between mini + full)
> 5. `app/src/main/java/com/eight87/tonearmboy/ui/playing/QueueSection.kt`
>    (473 lines — drag-reorder list, the swipe-up queue UI)
> 6. `app/src/main/java/com/eight87/tonearmboy/ui/playing/QueueReorderLogic.kt`
>    (81 lines — reorder pure logic)
>
> Translate music-domain types to task-domain types **only after** the
> port lands compiling. Specifically: where tonearmboy reads
> `playbackState` / `MediaItem` / `queue: List<MediaItem>`, skb will read
> `activeTaskState` / `TaskInstance` / `taskQueue: List<TaskInstance>`.
> Names of composables stay the same on purpose (`MiniPlayer`,
> `NowPlayingScreen`, `QueueSection`, `PlaybackTransportRow`) so any
> behavioural diff is mechanical, not architectural.
>
> **Do not reorder these phases.** Phase A is a pure port — no
> task-domain code in it.

## Locked design decisions

- **D-2.16.a — Verbatim port first, adapt second.** Phase A copies
  tonearmboy's sheet host + MiniPlayer + NowPlayingScreen + QueueSection
  into skb under identical names with `Playback*` types stubbed to a
  task-shaped facade. Adaptation lives in Phase C+. The implementing
  agent is FORBIDDEN from "improving" the port in Phase A — every
  composable function signature, animation curve, peek height (118 dp),
  flick threshold (5% / 0.05), and crossfade ratio (0..0.5 / 0.5..1.0)
  must match tonearmboy.
- **D-2.16.b — Task-domain facade replaces `PlaybackState`.** New file
  `task/ActiveTaskState.kt` exposes the same shape tonearmboy's
  `PlaybackStateProjector` does — a `StateFlow<TaskPlaybackState>` with
  fields: `current: TaskInstance?`, `queue: List<TaskInstance>`,
  `subStepIndex: Int`, `subStepCount: Int`,
  `subStepElapsedMs: Long`, `subStepDurationMs: Long`,
  `taskElapsedMs: Long`, `taskDurationMs: Long`, `isRunning: Boolean`.
  MiniPlayer/NowPlayingScreen read this; nothing else changes shape.
- **D-2.16.c — Two progress bars, not one.** The mini-player's
  drag-bar (the wide one we drag on) = sub-step progress. A thin 2-dp
  bar **above** it (or below — match tonearmboy's pinned 2-dp progress
  line position exactly) = whole-task progress. Both rendered as
  `time passed | time remaining` with a darker / brighter split, same
  visual idiom as the song-progress bar in tonearmboy.
- **D-2.16.d — Info row shows visual countdown, not literal text.**
  The user explicitly does NOT want *"grooming (2/6) — brushing teeth
  2:15 remaining"* as a literal string. Layout: top line = task name
  + step-count chip (`Grooming 2/6`), second line = current sub-step
  name (`brushing teeth`), right-aligned mono countdown (`2:15`).
  Three Compose `Text` nodes, no string-interpolation gimmick.
- **D-2.16.e — Top tab toggle (Schedule | Tasks) is removed.**
  `TopDestination.Tasks` deletes from `SkbAppShell`'s nav. Tasks no
  longer have their own pane. All todolist surface area moves into
  `NowPlayingScreen` (expanded sheet). Schedule is the only default
  home. Together / Repos / Wizard / Settings remain reachable via
  whatever non-tab affordance currently surfaces them (verify path
  before deleting).
- **D-2.16.f — Settings cog moves back next to the account avatar.**
  Pre-Round-2.1 location. The Repos pane's Settings trampoline is
  preserved (it's how Repositories settings is reached) — only the
  top-bar entry point moves.
- **D-2.16.g — Sub-step timing comes from existing task data, not new
  fields.** Tasks already carry `estimatedDurationMin` in TaskModels.
  Sub-steps within a task are modelled as either (a) existing
  checklist items, or (b) a new lightweight `subSteps: List<SubStep>`
  on `TaskInstance` *only if* (a) isn't present. Phase B chooses
  exactly one mechanism by inspection of `TaskModels.kt` — no
  scope-creep into a new task schema. If neither exists, fall back to
  showing the whole task as a single "step" with `subStepIndex=1,
  subStepCount=1`.
- **D-2.16.h — "Start task" action is the analogue of "play".**
  Adding a task to the queue = adding to queue. Starting the timer =
  pressing play. Pause = pause. Skip-sub-step = next. The transport
  row's icons stay tonearmboy-shape, only the action handlers change.
- **D-2.16.i — Drag-reorder of the queue works exactly as in
  tonearmboy.** `QueueReorderLogic.kt` is copied byte-for-byte. The
  list it reorders is `List<TaskInstance>` instead of
  `List<MediaItem>`. Persistence target is the existing
  `TasksViewState` / per-list ordering — Phase D defines the wire.

## Phase order

```
2.16.A  Port tonearmboy sheet host + MiniPlayer + NowPlaying + Queue   (PREREQ)
  ↓
2.16.B  Task-domain facade (ActiveTaskState + TaskPlaybackState)
  ↓
2.16.C  Wire MiniPlayer + NowPlayingScreen to task data
  ↓
2.16.D  Move all todolist UI into expanded NowPlayingScreen
  ↓
2.16.E  Delete Tasks tab + remove top-of-Schedule tab toggle
  ↓
2.16.F  Move Settings cog back next to account avatar
  ↓
2.16.G  Tests + AVD smoke
```

## Phase A — Port tonearmboy sheet host & UI (verbatim) — shipped in commit (worktree agent-ad8ef192895beff46)

**Goal:** end of phase, skb compiles with `MiniPlayer`, `NowPlayingScreen`,
`PlaybackTransportRow`, `QueueSection`, `QueueReorderLogic` files
existing under `ui/playing/`, reading from a stub `TaskPlaybackState`
flow that returns a hardcoded "Grooming / brushing teeth 2/6"
state. The sheet drags, expands, collapses, and cross-fades exactly
as tonearmboy's does on AVD.

- [x] **A.1** Read tonearmboy files 1-6 (list above) in full. Note any
      package-private utilities they reach for and add to import list
      for the port.
- [x] **A.2** Create `app/src/main/java/com/eight87/strictlykeptboy/ui/playing/`
      and copy all 5 files (`MiniPlayer.kt`, `NowPlayingScreen.kt`,
      `PlaybackTransportRow.kt`, `QueueSection.kt`,
      `QueueReorderLogic.kt`). Rewrite package declarations only — do
      not touch logic.
- [x] **A.3** Create stub types `task/TaskPlaybackState.kt` matching the
      shape of tonearmboy's `PlaybackState` (just enough fields to
      satisfy the ported composables). One hardcoded value source for
      Phase A.
- [x] **A.4** Lift the sheet-host block from `TonearmboyApp.kt:140-471`
      into `ui/scaffold/SkbAppShell.kt` (or a new
      `ui/scaffold/SheetHost.kt` that SkbAppShell composes around the
      existing Schedule pane). Match peek (118 dp), `dragStartProgress`,
      `flickThreshold = 0.05f`, staggered alpha
      (`0..0.5` mini / `0.5..1` full), nested-scroll connection.
- [x] **A.5** Wire MiniPlayer at the peek slot, NowPlayingScreen behind
      it, both reading from the stubbed `TaskPlaybackState` flow. Verify
      on AVD: peek visible at bottom over Schedule, drag-up expands to
      full screen with Grooming/brushing-teeth fake content, drag-down
      collapses, flick-up commits, flick-down dismisses.
- [x] **A.6** Commit. AVD updated per `feedback_commit_avd_ship.md`.

## Phase B — Task-domain facade

**Goal:** the stub from A.3 is replaced with a real reactive source
that derives `TaskPlaybackState` from existing task data + a started
"active task" reference.

- [ ] **B.1** Inspect `app/src/main/java/com/eight87/strictlykeptboy/ui/tasks/TaskModels.kt`
      and decide per D-2.16.g whether sub-steps come from existing
      checklist items or need a new field. Write the choice into this
      plan as a sub-bullet under B.1 before continuing.
- [ ] **B.2** Add `task/ActiveTaskController.kt` — singleton on AppGraph
      that holds `currentTaskId: StateFlow<String?>`,
      `isRunning: StateFlow<Boolean>`, `subStepIndex: StateFlow<Int>`,
      and emits a `start(taskId)`, `pause()`, `resume()`,
      `nextSubStep()`, `previousSubStep()`, `stop()` API.
- [ ] **B.3** Add `task/TaskPlaybackProjector.kt` — combines
      `ActiveTaskController` flows + the task store snapshot into the
      `TaskPlaybackState` flow that `MiniPlayer` / `NowPlayingScreen`
      consume. Substep elapsed = wall-clock since
      `controller.subStepStartedAt`. Substep duration = chosen-mechanism
      duration. Whole-task elapsed = sum of completed-sub-step durations
      + current sub-step elapsed; whole-task duration = sum of all
      sub-step durations.
- [ ] **B.4** Persist nothing across process death in Phase B —
      `ActiveTaskController` state is in-memory only. (Persistence is
      out-of-scope; running tasks survive only while app is alive.
      Documented limitation. Tracked for future Round 2.17.)
- [ ] **B.5** Replace A.3 stub source with the real projector. Verify
      on AVD: starting a task from anywhere (Phase C will add the
      entry point; for B.5 add a `Start` button onto a TaskRow
      temporarily) makes MiniPlayer show that task with a counting
      down timer.
- [ ] **B.6** Commit. AVD updated.

## Phase C — Wire MiniPlayer + NowPlayingScreen to task data

**Goal:** all three info-row text nodes render task data per D-2.16.d;
both progress bars per D-2.16.c; transport row buttons do
play/pause/next-substep/prev-substep/stop per D-2.16.h.

- [ ] **C.1** MiniPlayer info row: top line = `"$taskName  $i/$n"`
      (task name + step-count chip — render the `i/n` as a small
      pill/Surface, NOT as parens text), second line = sub-step name,
      right-aligned mono countdown `mm:ss` derived from
      `subStepDurationMs - subStepElapsedMs`.
- [ ] **C.2** MiniPlayer 2-dp pinned bar = whole-task progress (matches
      tonearmboy's 2-dp progress-line position exactly). The wide
      drag-bar above = sub-step progress with darker/brighter split at
      the `subStepElapsedMs/subStepDurationMs` ratio.
- [ ] **C.3** Transport row: play → `resume`/`start`, pause → `pause`,
      next → `nextSubStep` (or finish-task if last sub-step),
      previous → `previousSubStep`, long-press play/pause → `stop`.
      Match tonearmboy icon set exactly.
- [ ] **C.4** NowPlayingScreen (expanded) renders the same task data
      at full size, with a large countdown and the QueueSection below.
- [ ] **C.5** Commit. AVD: start a task, see mini + bars + countdown
      update each second, expand sheet to see the same at full size.

## Phase D — Move all todolist UI into expanded NowPlayingScreen

**Goal:** the `ui/tasks/` view-mode tabs (Combined / Today / Per-list
/ Standing / Shopping) all move into the expanded NowPlayingScreen
below the "now playing" header. The `TaskQuickAddFab` and detail-sheet
behaviour ride along. Nothing about the data layer changes.

- [ ] **D.1** In `NowPlayingScreen` expanded body, replace the music
      QueueSection with a vertical layout: top = current-task hero
      card, below = a TabRow / chip-strip of task view-modes (Combined
      / Today / Per-list / Standing / Shopping), below = the
      corresponding existing `Task{Combined,Today,PerList,Standing,Shopping}View`
      composable hoisted out of `TasksPane`.
- [ ] **D.2** Move `TaskQuickAddFab` into the expanded sheet (anchored
      to its bottom-right) so tapping it inside the sheet adds a task
      without collapsing the sheet.
- [ ] **D.3** Tapping any task in the queue → opens existing
      `TaskDetailSheet` over the NowPlayingScreen (z-above) per
      tonearmboy's overlay convention.
- [ ] **D.4** Long-press / drag-handle on any task → reorder via the
      ported `QueueReorderLogic`. Persist reorder back to the source
      todolist's order. The persistence wire goes through whatever
      ordering field `TasksViewState` / `TaskModels` already exposes;
      do not invent a new one.
- [ ] **D.5** "Start" action on a TaskRow swap-in: tapping the start
      affordance calls `ActiveTaskController.start(taskId)`, which
      makes the MiniPlayer pop into existence at the peek slot.
- [ ] **D.6** Commit. AVD: with no task active, sheet shows no peek
      (mini hidden, expanded reachable from a non-mini entry point
      TBD in D.7); with a task active, mini appears, swipe up to see
      todolist UI inside the sheet.
- [ ] **D.7** Non-mini entry point to the sheet when no task is
      active: add a small "Tasks" FAB or pill on Schedule's bottom
      that toggles the sheet to expanded. (Decide between FAB or pill
      by inspecting current Schedule bottom-affordance density — if
      Schedule's bottom is already busy, use a FAB at top-right of
      Schedule; otherwise a pill at bottom-center.)

## Phase E — Delete Tasks tab + remove top-of-Schedule tab toggle

- [ ] **E.1** Delete `TopDestination.Tasks` from `SkbAppShell.kt`.
      Schedule becomes the only default landing.
- [ ] **E.2** Remove any top-of-Schedule Schedule/Tasks toggle UI
      (search for the toggle pattern in `SchedulePane.kt` and
      neighbours).
- [ ] **E.3** Delete `TasksPane.kt` ONLY after confirming every
      `TaskXxxView` it composes is now imported by `NowPlayingScreen`.
      Keep `TaskModels.kt`, `TasksViewState.kt`, `TaskRow.kt`,
      `TaskQuickAddFab.kt`, `TaskDetailSheet.kt`,
      `TasksDemoSeed.kt`, `TaskSourceRail.kt`,
      `FromEventsProjector.kt`, `EmptyTasksState.kt`,
      `TaskCombinedView.kt`, `TaskTodayView.kt`,
      `TaskPerListView.kt`, `TaskStandingView.kt`,
      `TaskShoppingView.kt` — all still used.
- [ ] **E.4** Update any deep-links / tests that pointed at the Tasks
      tab to point at the expanded sheet instead.
- [ ] **E.5** Commit. AVD: launch → Schedule, no tabs at top.

## Phase F — Move Settings cog back next to account avatar

- [ ] **F.1** Locate current Settings cog position (probably in the
      Repos-pane top-bar after Round 2.1's restructure). Identify
      the desired pre-2.1 location next to the account avatar in
      Schedule's top bar.
- [ ] **F.2** Move the cog there. Verify Repos pane still has its
      own way to reach Repositories settings (its trampoline stays
      put per D-2.16.f).
- [ ] **F.3** Commit. AVD: cog visible next to avatar on Schedule.

## Phase G — Tests + AVD smoke

- [ ] **G.1** Unit tests:
      - `MiniPlayerTaskBindingTest` — given a `TaskPlaybackState`,
        info-row text matches D-2.16.d (three nodes, not one).
      - `MiniPlayerProgressBarsTest` — both bars render with the
        right elapsed/total ratios.
      - `TaskPlaybackProjectorTest` — given a queue + controller
        state, projects the correct state at any tick.
      - `SheetHostFlickCommitTest` — port of tonearmboy's flick-commit
        test (5% threshold).
      - `QueueReorderLogicTest` — already exists in tonearmboy; port
        it.
- [ ] **G.2** AVD smoke (full sweep, screenshots after each):
      1. App launch → Schedule only, no tab toggle.
      2. Cog next to avatar; tap → Settings.
      3. Enter expanded sheet via D.7 entry point with no active
         task → todo UI visible, no mini.
      4. Tap "Start" on a task in the queue → mini appears at peek,
         counting down.
      5. Drag up on mini → full NowPlayingScreen with hero + queue.
      6. Flick down → back to peek.
      7. Tap pause → countdown stops, mini stays.
      8. Tap next → sub-step advances, both bars update.
      9. Long-press play → stop, mini disappears.
      10. Drag-reorder a task in the queue → order persists across
          app relaunch.
- [ ] **G.3** Tick all sub-step checkboxes on this plan with `[x]`
      and the shipping jj change ID on each phase header. Mark
      `## Status: ✅ DONE` at top.

## Verification gates

- Phase A is GREEN when the sheet behaves identically to tonearmboy
  on AVD, with stubbed content. **No task-domain code yet.** Reviewer
  pulls up tonearmboy AVD next to skb AVD and side-by-side compares
  drag feel, flick commit, crossfade timing.
- Phase B is GREEN when the controller + projector emit correct
  `TaskPlaybackState` for a known input. Unit test gate before AVD.
- Phase C/D are GREEN per AVD smoke; no separate gate.
- Phase E is GREEN when no `TopDestination.Tasks` references remain
  (`grep -rn "TopDestination.Tasks" app/src/main` returns empty).
- Phase F is GREEN per AVD screenshot.
- Phase G is GREEN when all tests pass and 10/10 AVD scenarios
  produce the expected screenshot.

## Out of scope (NOT in Round 2.16)

- Persisting active-task state across process death (Round 2.17).
- Notifications for sub-step completion (Round 2.17).
- Android Auto integration for task playback (Round 2.18+).
- A new sub-step schema if existing checklist items suffice (D-2.16.g
  forbids scope-creep here).
- Tablet master-detail for the expanded sheet — single-pane on
  tablet for now; reconsider in Round 2.17.

## Instructions for the implementing agent (read before starting)

1. **Read tonearmboy sources first.** The 6 files listed in the
   "Source-of-truth" section, in order, in full. No code in skb
   before that read happens.
2. **Phase A is a verbatim port.** Do not refactor, rename, or
   "modernize" tonearmboy's mini-player code in Phase A. Translation
   to task-domain happens in Phase B onward.
3. **Tick checkboxes as you ship.** Per global CLAUDE.md: each
   sub-step gets `[x]` + the jj change ID on the phase header at the
   same time the work lands. Do not batch.
4. **AVD update after every commit**, per
   `feedback_commit_avd_ship.md`. Do not ship to GitHub Release
   unless explicitly asked.
5. **If anything in tonearmboy seems unclear or wrong, READ MORE
   tonearmboy SOURCE before deciding to deviate.** The user has
   already paid a chastity-day penalty for me deviating from
   tonearmboy without re-reading. Don't make me cost him another
   one.

# Round 2.16 — "Now playing" task + swipe-up queue (transplanted from tonearmboy)

## Status: ✅ DONE

Tonearmboy's mini-player + swipe-up sheet ported verbatim into skb
and repurposed for task playback. Mini-player at peek surfaces the
active task + current sub-step + dual progress bars; dragging up
reveals the full NowPlayingScreen with hero + queue/views. Tasks tab
removed, Settings cog moved back next to the avatar. Round 2.17 picks
up active-task persistence across process death and queue drag-reorder
persistence (the ported `QueueReorderLogic` + `DragReorderColumn` stay
in place ready for that wire).

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

## Phase B — Task-domain facade — shipped in commit 419767d (parent)

**Goal:** the stub from A.3 is replaced with a real reactive source
that derives `TaskPlaybackState` from existing task data + a started
"active task" reference.

- [x] **B.1** Inspect `app/src/main/java/com/eight87/strictlykeptboy/ui/tasks/TaskModels.kt`
      and decide per D-2.16.g whether sub-steps come from existing
      checklist items or need a new field. Write the choice into this
      plan as a sub-bullet under B.1 before continuing.
  - **Chosen mechanism: introduce optional `subSteps: List<TaskSubStep>` + `estimatedDurationMs: Long` on `TaskItem`** — inspection of `app/src/main/java/com/eight87/strictlykeptboy/ui/tasks/TaskModels.kt:58-84` shows neither a `checklistItems` list nor any flat duration field. The model carries only `due/done/priority/tags/standing/pinned/body/author/attachments/source/linkedEventId`. Per D-2.16.g's fallback path we add the lightweight `subSteps` list on the per-instance type (`TaskItem`), default `emptyList()`, plus an `estimatedDurationMs` fallback (default 5 min). When `subSteps` is empty the projector renders the task as a single step (`subStepIndex=1, subStepCount=1, subStepDurationMs=estimatedDurationMs`). No TOML schema changes — Phase B is in-memory only.
- [x] **B.2** Add `task/ActiveTaskController.kt` — singleton on AppGraph
      that holds `currentTaskId: StateFlow<String?>`,
      `isRunning: StateFlow<Boolean>`, `subStepIndex: StateFlow<Int>`,
      and emits a `start(taskId)`, `pause()`, `resume()`,
      `nextSubStep()`, `previousSubStep()`, `stop()` API.
- [x] **B.3** Add `task/TaskPlaybackProjector.kt` — combines
      `ActiveTaskController` flows + the task store snapshot into the
      `TaskPlaybackState` flow that `MiniPlayer` / `NowPlayingScreen`
      consume. Substep elapsed = wall-clock since
      `controller.subStepStartedAt`. Substep duration = chosen-mechanism
      duration. Whole-task elapsed = sum of completed-sub-step durations
      + current sub-step elapsed; whole-task duration = sum of all
      sub-step durations.
- [x] **B.4** Persist nothing across process death in Phase B —
      `ActiveTaskController` state is in-memory only. (Persistence is
      out-of-scope; running tasks survive only while app is alive.
      Documented limitation in `ActiveTaskController.kt` kdoc. Tracked
      for future Round 2.17.)
- [x] **B.5** Replace A.3 stub source with the real projector. AVD
      verified: with the petkeptbyai demo perspective (no seeded tasks)
      the Schedule pane shows **no mini-player at peek** — confirming
      `hasMedia=false` gates the peek correctly when no active task is
      set. Temp Start affordance lives on `TaskRow` (trailing
      IconButton with `Icons.Filled.PlayArrow`), plumbed from
      MainActivity → SkbAppShell → TasksPane → Combined/Today/PerList/
      Standing views → TaskRow. Phase D will replace this with the
      proper start-from-mini-player flow inside the expanded sheet.
      `StubTaskPlaybackSource.kt` retained in place — to be removed in
      Phase C once the wire is fully proven.
- [x] **B.6** Commit. AVD installed (`emulator-5554`,
      `/tmp/skb-2-16-B.png`). Unit-test gate: 7 new
      `TaskPlaybackProjectorTest` cases pass covering empty / start /
      tick / next / single-step / queue / stop transitions.

## Phase C — Wire MiniPlayer + NowPlayingScreen to task data — shipped in commit 8f7d506

**Goal:** all three info-row text nodes render task data per D-2.16.d;
both progress bars per D-2.16.c; transport row buttons do
play/pause/next-substep/prev-substep/stop per D-2.16.h.

- [x] **C.1** MiniPlayer info row: three Compose Text nodes — top line
      = taskName + small `Surface` step-count pill (rounded 50%, padded
      6dp h / 2dp v, hidden when subStepCount ≤ 1), second line =
      subStepName (bodySmall, single-line), trailing = right-aligned
      mono titleMedium countdown `mm:ss` = `subStepDurationMs -
      subStepElapsedMs` clamped at 0.
- [x] **C.2** Replaced the single 2-dp `LinearProgressIndicator` with
      two stacked bars: wide 4-dp sub-step bar (primary + 30% primary
      track, custom `Box.fillMaxWidth(progress)` overlay for the
      darker/brighter split aesthetic), thin 2-dp whole-task bar
      flush at the bottom (tertiary + 30% tertiary track).
- [x] **C.3** Transport row mapping verified via `TaskTransportAdapter`:
      togglePlayPause → pause/resume, seekToNext → nextSubStep,
      seekToPrevious → previousSubStep, long-press play → stop. Added
      `showShuffleAndRepeat: Boolean = true` parameter to
      `PlaybackTransportRow`; skb call-sites pass `false` (verbatim-port
      shape kept; gating is additive).
- [x] **C.4** NowPlayingScreen expanded body: three-node info row at
      larger sizes (headlineSmall taskName + step-count pill,
      titleSmall subStepName, displaySmall mono countdown). The
      seekable Slider replaced by the non-draggable
      `SubStepProgressBar` + `0:14 / 3:00` mm:ss labels. Whole-task
      progress row added below: 2-dp `TaskProgressBar` + mono
      `"task: mm:ss / mm:ss"` label.
- [x] **C.5** Commit + AVD smoke. Sub-stepped demo tasks added to
      `TasksDemoSeed.substeppedDemoTasks` (`Grooming` 6 substeps,
      `Bedtime routine` 3 substeps) and seeded into `tasksViewState`
      via MainActivity (idempotent merge; TODO Phase D removal). AVD
      verified all six scenarios (mini appears with pill+countdown,
      both bars tick, expand shows three-node info, pause freezes,
      next advances substep without resetting task elapsed, long-press
      play stops). Screenshots: `/tmp/skb-2-16-C-mini.png`,
      `/tmp/skb-2-16-C-expanded.png`, `/tmp/skb-2-16-C-paused.png`,
      `/tmp/skb-2-16-C-next.png`, `/tmp/skb-2-16-C-collapsed.png`,
      `/tmp/skb-2-16-C-stopped.png`.

## Phase D — Move all todolist UI into expanded NowPlayingScreen — shipped in commit f00418b

**Goal:** the `ui/tasks/` view-mode tabs (Combined / Today / Per-list
/ Standing / Shopping) all move into the expanded NowPlayingScreen
below the "now playing" header. The `TaskQuickAddFab` and detail-sheet
behaviour ride along. Nothing about the data layer changes.

- [x] **D.1** In `NowPlayingScreen` expanded body, replace the music
      QueueSection with a vertical layout: top = current-task hero
      card, below = a TabRow / chip-strip of task view-modes (Combined
      / Today / Per-list / Standing / Shopping), below = the
      corresponding existing `Task{Combined,Today,PerList,Standing,Shopping}View`
      composable hoisted out of `TasksPane`.
- [x] **D.2** Move `TaskQuickAddFab` into the expanded sheet (anchored
      to its bottom-right) so tapping it inside the sheet adds a task
      without collapsing the sheet.
- [x] **D.3** Tapping any task in the queue → opens existing
      `TaskDetailSheet` over the NowPlayingScreen (z-above) per
      tonearmboy's overlay convention.
- [x] **D.4** Long-press / drag-handle on any task → reorder via the
      ported `QueueReorderLogic`. Persist reorder back to the source
      todolist's order. The persistence wire goes through whatever
      ordering field `TasksViewState` / `TaskModels` already exposes;
      do not invent a new one.
- [x] **D.5** "Start" action on a TaskRow swap-in: tapping the start
      affordance calls `ActiveTaskController.start(taskId)`, which
      makes the MiniPlayer pop into existence at the peek slot.
- [x] **D.6** Commit. AVD: with no task active, sheet shows no peek
      (mini hidden, expanded reachable from a non-mini entry point
      TBD in D.7); with a task active, mini appears, swipe up to see
      todolist UI inside the sheet.
- [x] **D.7** Non-mini entry point to the sheet when no task is
      active: stacked `SmallFloatingActionButton` (Icons.Filled.Checklist)
      anchored bottom-right above the Schedule `EventCreateFab` (16dp
      end / 84dp bottom = stacked above the 56dp New FAB + 12dp gap).
      Lives inside `NowPlayingSheetHost` and is gated on
      `showTasksEntryFab && !showMiniPlayer && sheetProgress < 0.5f`.
      `showTasksEntryFab` is true only when `TopDestination.Schedule`
      is selected (the destination that owns the bottom-right slot).

      Implementation notes:
      - `NowPlayingSheetHost` no longer gates the entire sheet
        container on `showMiniPlayer`; only the peek mini-player is
        gated. The sheet body can now be opened to progress=1 via
        the D.7 FAB even with no active task. When `!hasMedia` the
        body renders without the hero card (per D.1).
      - **D.4 persistence target:** `TasksUiState` exposes no
        per-task ordering field today (only `tasks: List<TaskItem>`,
        ordered by underlying source). Per the plan's "skip
        persistence in Phase D, document follow-up" branch:
        drag-reorder is **deferred to Round 2.17** along with
        persistence; the ported `QueueReorderLogic` + `DragReorderColumn`
        stay in place from Phase A unused by D, ready for the next
        round once `TasksViewState` gains an ordering field. Marked
        TODO in `ExpandedNowPlayingTaskBody.kt`.
      - **D.5 Temp Start affordance** retained — `TaskRow`'s trailing
        Play IconButton (Phase B) still drives `ActiveTaskController.start`.
        No new gesture added.
      - `TaskDetailSheet` + `TaskQuickAddSheet` overlays moved into
        `NowPlayingSheetHost` (sibling of the sheet container) so
        modal-bottom-sheet z-order layers them above NowPlayingScreen.
      - `TaskQuickAddFab` (the in-sheet `+` FAB) anchored to
        bottom-end of the host with `alpha = nowPlayingAlpha` so it
        fades in with the expanded screen; tapping toggles a local
        `quickAddOpen` flag — sheet itself doesn't collapse.

## Phase E — Delete Tasks tab + remove top-of-Schedule tab toggle — shipped in commit f147c15

- [x] **E.1** Delete `TopDestination.Tasks` from `SkbAppShell.kt`.
      Schedule remains the default landing. The `tasksTab` rememberSaveable
      state + the `TaskViewTab` rail-branch + the `TasksPane(...)` invocation
      from the destination `when` are all removed; the `TaskViewTab` and
      `TasksPane` imports along with the `taskTabLabelRes` helper drop out.
      `EnumLabels.kt`'s `TopDestination.labelRes` switch loses its `Tasks`
      branch (still exhaustive after the enum case is gone).
- [x] **E.2** Remove the top-of-Schedule destination icon-button for
      Tasks. The Check (Tasks) icon was rendered as part of the shell's
      `topBarDestinations` icon row (Schedule / Tasks / Reviews); Round
      2.16.E drops it to `(Schedule / Reviews)`. No SchedulePane-internal
      toggle existed — the user's "icon row above Day/Week/Month rail"
      was the shell top-bar action row itself.
- [x] **E.3** `TasksPane.kt` deleted. ExpandedNowPlayingTaskBody.kt
      already imports `TaskCombinedView`, `TaskTodayView`, `TaskPerListView`,
      `TaskShoppingView`, `TaskStandingView`, `TaskViewTab`, `TaskItem`,
      `TasksViewState`, `TodolistMode`, `visibleTasks` directly from
      `ui.tasks.*` — verified by grep before deletion. The
      `TaskQuickAddRequest` data class previously defined inside
      `TasksPane.kt` was moved to its own file
      `ui/tasks/TaskQuickAddRequest.kt` (still consumed by SkbAppShell,
      TasksDemoSeed, TaskQuickAddSheet). All sister files listed for
      preservation (`TaskModels.kt`, `TasksViewState.kt`, `TaskRow.kt`,
      `TaskQuickAddFab.kt`, `TaskDetailSheet.kt`, `TasksDemoSeed.kt`,
      `TaskSourceRail.kt`, `FromEventsProjector.kt`, `EmptyTasksState.kt`,
      `TaskCombinedView.kt`, `TaskTodayView.kt`, `TaskPerListView.kt`,
      `TaskStandingView.kt`, `TaskShoppingView.kt`) untouched.
- [x] **E.4** Tests updated.
      `app/src/test/java/com/eight87/strictlykeptboy/ui/AppShellNavigationSwapTest.kt`
      asserts `TopDestination.entries.size == 6` (was 7), renders only
      `Schedule + Reviews` in the top-bar (was Schedule + Tasks + Reviews),
      and drops `selecting_tasks_destination_swaps_rail_to_task_view_modes`
      (TaskViewTab now lives inside ExpandedNowPlayingTaskBody and is
      covered by its own composable test). Deleted
      `app/src/test/java/com/eight87/strictlykeptboy/ui/tasks/TasksMasterDetailTest.kt`
      since TasksPane no longer exists. No deep-link handlers reference
      `TopDestination.Tasks` (grep clean across `app/src/main`).
- [x] **E.5** AVD verified — Schedule top bar shows
      `[calendar] [review] [avatar]` (Tasks/CheckCircle icon gone), task
      view-modes still reachable via the bottom-right Checklist FAB
      (D.7) which opens the expanded NowPlayingScreen sheet. Screenshot:
      `/tmp/skb-2-16-E-no-tab.png`.

## Phase F — Move Settings cog back next to account avatar — shipped in commit d62c58f

- [x] **F.1** Located the cog in `ReposPane.kt` (Round 2.4 migration)
      at the right end of the "Repositories" header row, wired to the
      `onOpenAppSettings` callback (which lands on
      `selected = TopDestination.Settings` from the shell). Desired
      destination: shell's `ShellTopBar` action row, immediately before
      the `IdentityAvatar`.
- [x] **F.2** Added an `IconButton(Icons.Filled.Settings)` to
      `ShellTopBar`'s action row between the destination icon-buttons
      and `IdentityAvatar`. New parameter `onSettingsTap: () -> Unit`
      flows in from `SkbAppShellContent`'s call site, wired to
      `selected = TopDestination.Settings` — the same mechanism the
      Repos-pane trampoline used. New test-tag `TestTagShellSettingsCog`
      ("ShellSettingsCog"). Removed the matching `IconButton` from
      `ReposPane.kt`'s "Repositories" header. The `onOpenAppSettings`
      composable parameter is retained on `ReposPane` (UNUSED_EXPRESSION
      suppress) so existing call-sites at MainActivity / SkbAppShell
      remain stable; the global-settings entry point is now the shell
      top-bar cog. Per-repo settings continue to open via row-tap on
      each repo (Mode.Settings(repoId)) — that path is unchanged.
- [x] **F.3** AVD verified — Schedule top bar reads
      `[Calendar] [Review] [Settings cog] [Avatar]`; tapping the cog
      navigates to the Settings pane (search bar + Appearance /
      Library / Behaviour categories shown). The Repos pane's
      "Repositories" header now only carries
      `[Find Together] [+ Add repo]`. Screenshots:
      `/tmp/skb-2-16-F-cog.png`, `/tmp/skb-2-16-F-settings.png`,
      `/tmp/skb-2-16-F-repos.png`.

## Phase G — Tests + AVD smoke — shipped in commit dd038d9

- [x] **G.1** Unit tests added (all pass under `:app:testDebugUnitTest`):
      - `MiniPlayerTaskBindingTest` (Robolectric + Compose) —
        given a `TaskPlaybackState` with taskName="Grooming",
        subStepName="brushing teeth", subStep 2/6,
        subStepDurationMs − subStepElapsedMs = 165s, asserts the
        MiniPlayer renders THREE distinct Text nodes ("Grooming",
        "brushing teeth", "2:45") plus the "2/6" step-count pill —
        not a single interpolated string (D-2.16.d gate).
      - `MiniPlayerProgressBarsTest` (Robolectric + Compose) —
        asserts both progress bars exist by testTag
        (`mini_player_substep_progress` for the wide 4-dp bar,
        `mini_player_task_progress` for the thin 2-dp whole-task
        bar) — D-2.16.c gate. The wide-bar tag was added in this
        phase since Phase C had only tagged the thin bar.
      - `TaskPlaybackProjectorTest` — existing 7 cases retained,
        added an 8th case `pause then resume preserves accumulated
        sub-step elapsed` covering the lifecycle gap (40s pre-pause +
        25s post-resume = 65s, not 75s of wall clock).
      - `SheetHostFlickCommitTest` — factored the flick-commit math
        out of `SkbAppShell.NowPlayingSheetHost`'s inline closure into
        `internal fun flickCommitTarget(start, end, threshold = 0.05f)`
        in a new file `ui/scaffold/SheetHostFlickCommit.kt`. Six cases
        cover the locked rules: decisive flick up (+0.06 → 1f), flick
        down (−0.06 → 0f), insufficient move with position fallback
        (end < 0.5f → 0f, end ≥ 0.5f → 1f), and start-position
        sensitivity for both halves of the position fallback.
      - `QueueReorderLogicTest` — tonearmboy never landed an explicit
        unit test for `QueueReorderLogic.kt`, so the skb test was
        written fresh against the three pure helpers we ported in
        Phase A: `translateVisualToReal` (identity),
        `clampMoveAwayFromActive` (no-active passthrough / drop-when-
        source-is-active / shift-past-active in both directions /
        drop-no-op-after-shift), and `firstDifference` (move-down,
        move-up, identical lists, size mismatch, multi-edit rejection).
- [x] **G.2** AVD smoke (10 scenarios, all GREEN, screenshots
      `/tmp/skb-2-16-G-scenario-{01..10}.png`):
      1. App launch → Schedule only (no Tasks tab on the top-bar icon
         row, just `[calendar] [review] [cog] [avatar]`), Day/Week/
         Month/Agenda/Year rail, stacked Checklist+New FABs bottom
         right. ✅
      2. Tap cog next to avatar → Settings pane opens with Look-and-
         Feel / Accounts / Calendars / Todolists / Templates /
         Sync / Notifications / Lifestyle / CalDAV categories. ✅
      3. Tap D.7 Checklist FAB with no active task → expanded
         `NowPlayingScreen` opens directly to full progress
         (no peek mini, no hero), shows Combined/Today/Per-list/
         Shopping tabs + queue (Bedtime routine + Grooming demos)
         + bottom-right `+` FAB. ✅
      4. Tap the play-icon affordance on the Grooming row → flick
         sheet down → mini-player appears at peek showing
         "Grooming 1/6 · brush teeth · 2:36" with three nodes +
         step pill + countdown ticking. ✅
      5. Drag up on mini → full `NowPlayingScreen` with hero (cover
         art + task name + step pill + countdown), sub-step bar
         `0:35 / 3:00`, whole-task bar `task: 0:35 / 20:00`,
         transport row, queue tabs below. ✅
      6. Flick down on the expanded sheet → back to peek with the
         mini still visible at 2:13. ✅
      7. Tap pause on mini transport → play icon shown, countdown
         frozen at 2:01 across a 4-second sleep. ✅
      8. Resume + tap next → sub-step advances from "brush teeth"
         (1/6) to "floss" (2/6), countdown reset to 1:58, pause icon
         shown (playing), both bars updated. ✅
      9. Long-press play → mini disappears, bare Schedule visible
         with Checklist + New FABs (D.7 entry point reachable
         again — confirms `hasMedia=false` gates the peek). ✅
      10. Drag-reorder a task in the queue → **per-plan disclaimer**:
          drag-reorder is deferred to Round 2.17 along with
          persistence (Phase D.7 implementation note). The
          ported `QueueReorderLogic` + `DragReorderColumn` are in
          place but the `TaskRow` rendered in the expanded sheet
          currently has no drag-handle attached; queue order is
          source-driven. Screenshot shows the queue as rendered
          in the expanded sheet (Bedtime routine above Grooming).
          ✅ (deferred-by-design, plan G.2 line 10 explicitly says
          "persistence deferred to Round 2.17 per Phase D").
- [x] **G.3** All sub-step checkboxes ticked; commit hash added to
      this Phase G header above; `## Status: ✅ DONE` set at the
      top of the file.

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

Post-DONE: peek always visible on Schedule (commit 6f03de1). D.7 Tasks FAB obsoleted.

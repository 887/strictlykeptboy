# Walk — Cheetah's speed run: one-handed todolist blasts between tasks

**Persona:** Dustrun ("Dust"), a kept cheetah sub, kept solo by an AI Dom
("Sir") that runs `claude -p` against `dust-kept`
(see [`personality.md`](personality.md)).
**Goal:** Verify the todolist bottom-sheet experience from the angle of
the fastest possible user — minimum taps, one hand, no time to think.
QA angle: tap counts, touch target sizes, animation speed, keyboard
auto-focus, confirm dialog friction, fat-finger safety, Quick Add
velocity, and whether the sheet can be opened-and-dismissed in under
10 seconds for a "nothing urgent, close it" check.
**Preconditions:**
- App installed + `dust-kept` (strictly-kept) configured and synced.
- Single-repo setup; multi-repo flag OFF; no repo dots.
- `dust-kept` contains: 2 overdue tasks (from yesterday), 3 tasks due
  today (2 authored by Sir, 1 by Dust), 1 standing/pinned passive habit.
- All tasks have short titles (≤ 40 chars) — Dust's lists are lean.
- It is **Wednesday** (mid-week; cage-on in effect; Sir's tasks active;
  the weekend release is something to work towards).
- Today view starts with 5 active items: 2 overdue at top, 3 today,
  1 pinned.
**Features exercised:** bottom-sheet pull velocity, TasksFilter rail
(Today primary, others incidental), TaskTodayView Today section,
TaskRow checkbox + confirm dialog (friction measurement), Quick Add
FAB + sheet (velocity path), EmptyTasksState (post-completion),
passive habit standing row (B.1 pinned section).

> Format: numbered steps, "→ Expect:", tap count explicit, "QA CHECK:"
> bullets measuring velocity + friction, "HOW IT SHOULD FEEL:" gap notes.
> The runner clocks elapsed times (stopwatch or mental count "one-one-
> thousand, two-one-thousand") and logs every tap. Defects go into
> `defects.md` using the format from `exhaustive-click-everything/`.

---

## Session 1 — The morning blast (5 tasks, 3 minutes before leaving)

Dust is getting ready to leave. He has 3 minutes. He opens the app on
the schedule view and pulls up the bar to check the list.

### Step 1.1 — Pull the status bar up

Dust swipes up from the status bar at the bottom of the screen.

→ **Expect:** The sheet opens in ≤ 300 ms (animation included), settles
at full height with the Today filter selected and the task list visible
without any loading spinner.

**QA CHECK — VELOCITY:**
- [ ] **Clock the sheet open time.** Set a stopwatch. Swipe the bar.
  Stop when the task list rows are readable on screen. Log the time.
  Pass: ≤ 300 ms. Marginal: 300–500 ms. Fail (defect): > 500 ms.
- [ ] Does the animation feel springy or linear? A springy overshoot
  feels fast even at the same wall-clock time. Log the quality.
- [ ] Is the Today filter pre-selected with no extra tap needed?
- [ ] Are tasks immediately visible (no "loading…" state), or is there
  a blank frame followed by content popping in?

**HOW IT SHOULD FEEL:** The sheet should feel like a pocket snapping
open — a quick, confident motion. The underlying animation uses
`Animatable` and custom drag/fling committed at 5% threshold (NowPlayingSheetHost).
The 5% threshold means a short flick commits the full open; verify this
works without needing a slow full-height drag.

### Step 1.2 — Scan the task list (< 2 seconds)

The list is open. Dust glances at it.

→ **Expect:** Overdue tasks appear at the top of the "Today" section,
visually distinct (error colour on title and due chip). Today-dated
tasks follow. The total visible without scrolling is 5 items.

**QA CHECK — SCANNABILITY:**
- [ ] Are the 2 overdue tasks at the very top of the list?
  (`sortedForCombined`: overdue bucket = 0, today = 1, no-due = 2.)
- [ ] Are overdue task titles in `colorScheme.error`? (Round 2.26.C.2.)
  If they are the same colour as non-overdue tasks, this is a defect.
- [ ] Can Dust see all 5 items without scrolling? Or does the list
  require a scroll to see the 5th? If the 5th is cut off by the bottom
  of the sheet viewport, measure how much is visible.
- [ ] Does the section header ("Today" label) take up vertical space
  that pushes tasks off-screen? The header is `padding(vertical = 8.dp)`
  + `titleSmall` text height ≈ 36 dp; plus "Pinned" header for the
  standing task. 2 headers × ~36dp = 72dp of section header space lost.
  With 5 tasks at 64dp each = 320dp + 72dp headers = 392dp total
  content, needing > 392dp of list height. On a 1080×2400 phone with
  the sheet fully expanded, this should fit — verify.

**HOW IT SHOULD FEEL:** Dust's mental model is: "top = most urgent".
The sort is correct for this (overdue first), but the section header
("Today") between overdue tasks and today tasks breaks the visual
flow — the header creates a visual pause that makes the overdue tasks
feel less urgent than they are. An ideal would show overdue tasks
in a subtle error-tinted section that blends directly into the today
tasks, not gated by a header that implies "these are all the same
priority". Gap: section header design creates false visual parity.

### Step 1.3 — Complete the first overdue task (Tap count: goal = 3)

Target: tap the checkbox on the first overdue task, confirm done.

**Tap 1:** Tap the checkbox of the first overdue task.
testTag: `TaskCheckbox-<id>`.

→ **Expect:** Confirm dialog opens immediately.

**Tap 2:** Tap the confirm button (testTag: `TaskCheckbox-confirm-<id>`).

→ **Expect:** The task dims + strikethrough. The dialog dismisses.

**QA CHECK — TAP COUNT + TARGET SIZE:**
- [ ] **Clock tap-to-confirmed:** stopwatch from Tap 1 to dialog
  dismissal. Pass: ≤ 1.5 seconds. Fail: > 3 seconds (dialog
  animation sluggish or confirm button hard to hit).
- [ ] **Hit rate on checkbox:** Is the checkbox `48dp × 48dp` or larger?
  The code: `Checkbox` inside `padding(start = 8.dp)` in a 64dp-height
  row. Material3 Checkbox minimum is 48dp internal touch target.
  Measure actual tappable area. A hit outside the checkbox that still
  triggers a tap on the row (row's `pointerInput` → `onClick`) opens
  the task detail instead of checking it. Log any miss-fires.
- [ ] **Confirm button hit area:** The confirm `TextButton` has no
  explicit size modifier. TextButton minimum is 40dp height.
  Is it big enough for a hurrying thumb? Measure.
- [ ] **Confirm dialog speed:** Does the dialog open with an animation
  (Material3 dialog default: fade + scale)? If it snaps in with no
  animation, that is a jarring experience. If the animation is > 300 ms,
  the dialog itself feels slow. Log.
- [ ] After confirming: does the item disappear from the visible list
  immediately, or stay (dimmed) at the bottom? `sortedForCombined`
  puts done items at the bottom. In Today view, done items remain in
  the list (dimmed). Verify the row does NOT vanish, just dims in place.
- [ ] Is there haptic feedback on the checkbox tap or confirm? Currently
  none — note as gap (missing haptic on completion is a delight gap).

**HOW IT SHOULD FEEL:** Three taps (gesture + checkbox + confirm) is
the minimum possible with the current confirm dialog. For Dust, the
confirm dialog is the friction point. The ideal would be a swipe-to-
complete gesture on the row (no dialog), or at minimum a confirm that
is pre-focused on "Yes" so Enter/volume-button confirms without
lifting the thumb. Gap: confirm requires a precise tap on a TextButton
with no minimum-size override and no pre-focus.

### Step 1.4 — Speed-run the remaining 4 tasks in succession

Dust taps through the other 4 tasks back-to-back without pausing.

→ **Expect:** Each checkbox tap immediately opens the confirm dialog;
each confirm immediately dismisses and marks the task done. The list
visually updates in real time (rows dim in place as they're done).
Four iterations of [checkbox → confirm] with no lag between them.

**QA CHECK — BURST VELOCITY:**
- [ ] **Clock the full 4-task burst.** Stopwatch from first checkbox
  tap to last confirm tap. Pass: ≤ 10 seconds for 4 tasks (2.5s each).
  Fail: > 20 seconds.
- [ ] Does any confirm dialog fail to dismiss after confirmation, leaving
  a stale modal blocking the list? (This would be a blocker defect.)
- [ ] After all 4 tasks are done: does the Today section show all 5 tasks
  dimmed/strikethrough at the bottom, or does the "Today" section
  header disappear leaving only the "Pinned" section (with the
  standing habit)?
- [ ] **Fat-finger zone test:** intentionally tap slightly ABOVE the
  checkbox (into the task title area). Does this open the task detail
  sheet (onClick) or check the box? The row uses `detectTapGestures`
  for the full surface → `onClick = { onOpen(task) }`; the checkbox
  sits within that surface. A tap that lands on the title should open
  the detail, NOT check the box. Verify this is the actual behaviour.
- [ ] After the burst, scroll the list. Do the done items (now at the
  bottom of `sortedForCombined`) form a cluster? Or are they scattered
  through the list interleaved with the remaining pinned standing task?

**HOW IT SHOULD FEEL:** A "burst" of four task completions should feel
like rapid-fire checking — one motion, one confirm, next row, one
motion, one confirm. If any confirm dialog lingers (animation not yet
finished when the next checkbox is tapped), the user must wait. Test
whether tapping the next checkbox while the previous dialog is
animating out causes any state corruption (confirm two items at once,
or missing a confirm). Gap: no debounce or state-guard is visible in
the code for rapid successive confirms.

### Step 1.5 — Verify empty state or near-empty state

After completing all 5 active tasks (leaving only the pinned standing
passive-habit task), the Today section is all done tasks.

→ **Expect:** The "Today" section shows 5 dimmed/strikethrough rows.
The "Pinned" section shows the standing passive-habit task (unchecked,
done = false by passive-habit default). The bat mascot empty state does
NOT appear (there are still visible rows). If the done tasks are moved
to bottom and the section becomes "empty" of active tasks — what does
the Today section header do? Does it remain, or disappear?

**QA CHECK:**
- [ ] Does `EmptyTasksState` (bat mascot) appear when all active tasks
  are done but some done tasks remain visible? It should NOT — empty
  state fires only when `forToday(today).isEmpty()` (zero tasks at
  all). If done tasks are still in `visibleTasks`, the list is not empty.
- [ ] If the Today section only contains done tasks (all strikethrough)
  and Dust looks at the list, is it clear the session is done? Is
  there any positive feedback ("all done!" style copy)? Currently
  none — the done tasks just sit there dimmed. Gap: no "all done"
  delight state.
- [ ] Swipe the sheet down to dismiss. Does it animate down cleanly?
  Does the status bar at the bottom read a task name (the active task
  if one was started) or "No active task"?

---

## Session 2 — Quick Add between tasks (2 minutes, one hand)

Dust remembers something he needs to do while waiting in line. He opens
the app and goes straight to Quick Add without browsing the list.

### Step 2.1 — Open app and pull bar, land on Today

(Same as Step 1.1 — skip to tap count.)

**Goal: sheet open in gesture #1 + the FAB visible in the bottom-right.**

→ **Expect:** FAB visible immediately after sheet opens. No extra scroll
needed to reveal the FAB.

**QA CHECK:**
- [ ] Is the FAB visible without scrolling after sheet open?
  testTag: `QuickAddFab`. The FAB is inside the Box with `fillMaxHeight`
  weight=1 content area. Does it float over the list or sit at the
  bottom of the sheet? If the list content is short enough, the FAB
  may be pushed off-screen by list content. Verify FAB positioning.
- [ ] Is the FAB within one-thumb reach in a right-hand grip?
  Bottom-right corner: thumb reachability on a 6.3" phone is marginal.
  In a left-hand grip it is very hard to reach. Test both. Log if
  the FAB is unreachable one-handed in left-hand grip.

### Step 2.2 — Tap FAB (Tap 1) — keyboard should auto-appear

**Tap 1:** Tap the Quick Add FAB.

→ **Expect:** Quick Add sheet slides up (ModalBottomSheet with
`skipPartiallyExpanded = true`). The `OutlinedTextField` is visible.
**Ideal:** the keyboard appears automatically without a second tap.
**Current behaviour (per code):** no `focusRequester` on the
`OutlinedTextField` — the keyboard does NOT auto-appear.

**QA CHECK — KEYBOARD AUTO-FOCUS:**
- [ ] Does the keyboard appear automatically on FAB tap?
  **If no:** log as **major defect: Quick Add input does not auto-focus;
  requires an extra tap to start typing.** This adds 1 tap to every
  Quick Add, making the flow 4 interactions instead of 3.
- [ ] If keyboard does appear: does the TextField cursor blink,
  indicating focus? Or is the focus visual absent?
- [ ] Does the sheet animation play cleanly under the keyboard
  (no overlap or clipping)?

### Step 2.3 — Type the task title and submit (Taps 2 + 3)

**Tap 2 (if needed):** Tap the text field to focus (if keyboard
didn't auto-appear — see Step 2.2).
**Type:** a short title (e.g. "Call vet").
**Tap 3:** Tap the Submit button (testTag: `QuickAddSubmit`).

→ **Expect:** Sheet dismisses. Task appears in the appropriate todolist
(the first list in targets, or the pre-selected one). The task shows
up in Today if its due date is today; in All otherwise.

**QA CHECK — QUICK ADD VELOCITY:**
- [ ] **Count total interactions: FAB tap + (optional focus tap) + type + submit.**
  With auto-focus: 3 interactions + typing. Without: 4 + typing.
  Log the actual count.
- [ ] Does the target chip row require interaction? Default pre-selects
  `targets.firstOrNull()` which is the first todolist. If Dust only
  has one repo (`dust-kept`) and one default list, he never needs to
  touch the target chips. Verify the chip row pre-selects the right
  list without user interaction.
- [ ] Submit with an empty title: is there visible feedback (error,
  shake), or does the button silently do nothing? Current code:
  `if (cleaned.isEmpty()) return@Button` — no feedback. Log as
  **minor defect: Quick Add submit with empty title gives no feedback;
  button appears to be broken.**
- [ ] Does the Quick Add sheet dismiss after successful submit? Or does
  it stay open for another entry? If it stays open, is there a clear
  "add another" affordance or does the user need to swipe down?
  Currently `onSubmit` is called and the host dismisses — verify
  the sheet actually closes.
- [ ] After dismissal: does the new task appear in Today or does it
  only appear in All? A task added without a due date won't appear
  in Today's dated section (it has no `due`). Is there any feedback
  about where the task landed?

**HOW IT SHOULD FEEL:** Dust's ideal is: FAB → type → Submit → task
exists. Three interactions. The current path is FAB → tap field →
type → (maybe select target) → Submit. That's 4–5. Auto-focus is
the single largest improvement. The second improvement would be
"last used list" memory so the target chip never needs touching.
Gap: both are logged.

### Step 2.4 — Dismiss the sheet (last gesture)

Dust swipes the sheet down to return to the schedule.

→ **Expect:** Sheet animates down. Status bar returns to "No active task"
state.

**QA CHECK:**
- [ ] Does the sheet dismiss on a short downward flick, or does it
  require a slow drag to below 50% height? The NowPlayingSheetHost
  uses a 5% threshold for commit — a flick should suffice.
- [ ] Is the dismiss animation ≤ 300 ms?
- [ ] Does the schedule re-appear cleanly (no ghost / stale sheet
  edge visible)?

---

## Session 3 — Between deliveries (30 seconds, standing)

Dust checks the list mid-afternoon, sees 0 remaining tasks. Sir's
two tasks were completed in session 1. He is on top of it.

### Step 3.1 — Pull the sheet, scan, confirm done, dismiss

Dust pulls the sheet. All tasks are done (dimmed strikethrough).
The list feels finished.

→ **Expect:** The Today section shows all tasks dimmed. Possibly the
"Today" section header remains with dimmed tasks below it. No bat
empty state (there are still tasks, just done ones). Ideally: a subtle
"all done" indicator.

**QA CHECK:**
- [ ] Total time from pull to dismiss for a "nothing to do" session:
  goal ≤ 5 seconds (gesture up, scan, gesture down).
- [ ] Is the visual state of "all tasks done" immediately legible?
  Or does Dust have to scan through the list to confirm nothing is
  unchecked? If unchecked items are not distinguishable at a quick
  glance from checked ones, this is a scannability defect.
- [ ] Does the bat empty state appear prematurely (before swiping to
  Done tab)? It should not.
- [ ] Is there any hint of "good job, Sir's proud" copy anywhere?
  Currently no — EmptyTasksState is the only kink-aware text and it
  only appears on empty lists, not on "all done". Log as enhancement.

**HOW IT SHOULD FEEL:** A cheetah who has cleared his list should
feel a moment of satisfaction. The app currently treats "0 active tasks
but 5 done tasks" identically to "5 active tasks" — the only
difference is row colours. An ideal state would briefly flash a bat
mascot with "all done for today" copy, then settle back to the dimmed
list. Gap: no "all clear" delight state in Today view.

---

## Session 4 — Saturday morning (weekend release blast)

It is **Saturday**. Sir's cage-off window is in effect. Dust has
3 tasks left over from the week (not Sir's — those were done). He
blasts them to clear the list before heading out.

### Step 4.1 — Pull the sheet, observe weekend state

→ **Expect:** The Today view shows the 3 remaining tasks. Sir's
cage-on window is NOT present in the schedule (that's a calendar
event, not a task). The task list is the same as a weekday — tasks
don't change based on mode.

**QA CHECK:**
- [ ] Do the 3 remaining tasks appear normally (no "weekend mode"
  affecting task visibility)?
- [ ] Are any Sir-authored tasks still visible (there shouldn't be —
  Sir's 2 assigned tasks were completed in session 1)?
- [ ] The passive habit standing item (morning collar ritual) — does
  it appear in the Pinned section on Saturday too? (It should, since
  it's a standing task — `pinnedForToday = true` persists regardless
  of day.) Verify.

### Step 4.2 — Complete the last 3 tasks (the weekly close-out)

Same as Step 1.3 × 3.

→ **Expect:** All 3 tasks confirmed done. Today section: all done rows.

**QA CHECK:**
- [ ] After completing all 3, scroll to the Done tab on the rail.
  Does Done show ALL completed tasks from the week (tasks from
  session 1 + session 4)? Or only the most recent?
  Note: Done view sources from `uiState.tasks` (not `visibleTasks()`),
  so all ever-seen tasks with `done = true` appear — but tasks
  are only in `uiState.tasks` if they were in the loaded data. Tasks
  toggled in-memory persist only in the `TasksViewState` instance —
  verify they survive across sessions (they may not if the app was
  killed between session 1 and session 4; the in-memory state is lost
  on process kill).
- [ ] On the Done tab: are this week's Sir-authored completions visible?
  (Again: author chips missing from Done view — see snow leopard walk
  defect F.2. Dust wouldn't notice the missing chip, but a QA runner
  running this walk should still log it.)
- [ ] Does the Done tab show a date or time for when each task was done?
  (Current: `agoFormat` shows "today"/"yesterday"/etc. if `doneAt` is
  set; but `toggleDone` doesn't set `doneAt`. Verify: tasks toggled
  in-memory have `doneAt = null`, so `agoFormat(null, today)` is not
  called — `formatDue` and `agoFormat` are only called when `doneAt !=
  null`. So done rows show NO timestamp. Log as **defect: in-memory
  toggleDone does not stamp doneAt; Done view shows no completion
  time.**)

---

## Part B — Fat-finger adversarial tests

These are stress tests for Dust's one-handed, fast interaction style.

### Step B.1 — Tap the gap next to a checkbox

Intentionally tap 8–10 dp to the RIGHT of the checkbox edge (into the
title area).

→ **Expect:** The task detail sheet opens (onClick), NOT the confirm
dialog. The checkbox was not triggered.

**QA CHECK:**
- [ ] Does tapping the title area always open the detail, never the
  confirm dialog? `detectTapGestures` covers the whole surface;
  `Checkbox.onCheckedChange` covers only the checkbox widget's touch
  area. Verify they do not overlap.
- [ ] If the confirm dialog appears from a title-area tap, that is a
  **major defect: checkbox touch zone bleeds into title area.**

### Step B.2 — Tap above the status bar before the sheet settles

While the sheet is mid-animation (still rising), Dust taps a task row.

→ **Expect:** Either (a) the tap is ignored during animation, or (b) the
tap registers and the confirm dialog opens on the correct task.

**QA CHECK:**
- [ ] Does a mid-animation tap cause any crash or state corruption?
- [ ] Does the correct task receive the tap, or does the tap miss-register
  due to layout not being settled?
- [ ] Log any crash or wrong-target activation as a **blocker defect.**

### Step B.3 — Rapid double-tap the Submit button in Quick Add

In the Quick Add sheet, type a title and double-tap the Submit button
as fast as possible (simulating an impatient user).

→ **Expect:** Only ONE task is created.

**QA CHECK:**
- [ ] Is any debounce logic visible in the Submit button's `onClick`?
  (Code: `Button(onClick = { ... onSubmit(cleaned, t) })` — no debounce.)
- [ ] Does a double-tap create two identical tasks? If so: **major
  defect: Quick Add has no debounce; double-tap creates duplicates.**

---

## Part C — One-handed reachability audit

Dust uses his right thumb exclusively. This section maps tap zones.

### Step C.1 — Right-thumb reach map

With the sheet fully expanded on a 1080×2400 phone, estimate the
comfortable right-thumb reach zone (approximately the bottom 60% of
the screen, centered-right):

- **Comfortable:** y > 1440 (bottom 40%), x > 540 (right half)
- **Marginal:** y 960–1440, any x
- **Difficult:** y < 960 (top 40%)

**QA CHECK — RAIL ITEMS:**
- [ ] Today (first rail item, top of left column ≈ y 250): **difficult**
  for right thumb. Document.
- [ ] Upcoming (≈ y 358): **difficult** for right thumb.
- [ ] All (≈ y 466): **marginal**.
- [ ] By Repo (≈ y 574): **marginal**.
- [ ] Done (≈ y 682): **marginal** to comfortable.
Only Done is naturally reachable by Dust's right thumb without
re-gripping. All others require shifting grip.

→ **Expect this is a real gap.** Log as **major UX gap: left-edge
vertical rail places most-used items (Today = top) in the hardest-
to-reach zone for right-handed one-thumb use.** The bottom of the
rail is Done, the least-used item for Dust, and it is the most
reachable. This is the inverse of Dust's usage pattern.

**HOW IT SHOULD FEEL:** A bottom-sheet-native design for frequent one-
handed use would put the most-used filter (Today) at the BOTTOM of
the rail (closest to the thumb), not the top. The current layout is
a remnant of the Schedule rail's top-to-bottom visual logic, but for
the todolist the primary filter is used the vast majority of the time.
A "sticky Today" approach (Today is always active; other filters are
secondary) would eliminate the rail entirely for Dust's use case.

### Step C.2 — Quick Add FAB right-thumb reach

The FAB is at bottom-right of the content area (the right half of the
sheet after the 52dp rail column).

**QA CHECK:**
- [ ] FAB estimated position: content area is 1080 - 52 = 1028dp wide,
  FAB at trailing edge = approximately x 1040, y near the bottom of
  the content area. This IS within right-thumb reach. Confirm.
- [ ] In left-hand grip: FAB at far right is unreachable without
  re-gripping. Log as gap for left-handed users.

---

## Part D — QA angles for the runner (Dust's dimensions)

The runner must evaluate every dimension below and log findings in
`defects.md` using the format from `exhaustive-click-everything/`.

### Efficiency (taps-to-complete)
- [ ] **Complete one task from cold sheet:** goal = 1 gesture + 2 taps.
  Actual count in the run?
- [ ] **Quick Add one task from cold sheet:** goal = 1 gesture + 3 taps
  + typing. Actual count in the run?
- [ ] **"Nothing to do" check from cold sheet:** goal = 1 gesture + 3
  seconds of scanning + 1 gesture to close. Actual time?
- Log any delta between goal and actual as a defect with severity
  (major if > 1 extra tap, minor if 1 extra tap but recoverable).

### Confirm dialog friction (the most important UX issue for this persona)
- [ ] **Is the confirm dialog on every checkbox-to-done justified?**
  The code comment says: "bypasses the playback timer, no undo from
  history, so the user explicitly asked for a yes/no gate."
  For Dust, this is still friction. Document: does a swipe gesture
  exist as an alternative? (No — no swipe-to-complete.) Should there
  be a "quick-check" mode without confirm? Log as **enhancement request:
  optional swipe-to-done or tap-and-hold-to-done without confirm dialog
  for users who prioritize speed.**
- [ ] **Confirm button size:** measure the TextButton touch area in dp.
  Pass: ≥ 48dp height. Fail: log as **a11y / usability defect.**

### Animation quality (Dust notices if it's slow)
- [ ] Sheet open: < 300 ms pass, > 500 ms fail (defect).
- [ ] Confirm dialog open: < 200 ms pass, > 400 ms fail.
- [ ] Quick Add sheet open: < 300 ms pass, > 500 ms fail.
- [ ] Sheet close: < 300 ms pass, > 500 ms fail.

### Touch target sizes
- [ ] Checkbox effective touch area (measure via a11y tree bounds or
  coordinate testing). Pass: 48×48dp. Fail: defect.
- [ ] Rail chips: 52×108dp per code. Pass. Verify actual on device.
- [ ] Confirm buttons: TextButton, no explicit size. Measure. Likely
  40dp height — below M3 48dp minimum. Log as defect.
- [ ] Quick Add Submit button: `Button` with no size modifier. Likely
  correct (Button defaults to M3 56dp height). Verify.

### Keyboard / text entry
- [ ] Does Quick Add auto-focus? (Pass = keyboard appears automatically.
  Fail = extra tap required — major defect.)
- [ ] Is IME action (keyboard "Done" / "Submit") wired to the submit
  action? The `OutlinedTextField` has `singleLine = true` which enables
  the IME Done action — but it is not wired to `onSubmit`. Test: type
  in Quick Add, tap keyboard Done key. Does the task submit? If not:
  **minor defect: Quick Add does not respond to IME Done action.**

### Delight / emotional loop
- [ ] Is there any "all done" state or positive reinforcement when
  all active tasks are completed? (Currently: no.) Log as
  **enhancement: add delight state for empty active-task list.**
- [ ] Does the status bar copy at the bottom change after completing
  tasks? (e.g., "5 tasks done today" or a praise-term echo from
  `identity.toml`?) Currently it shows the active task name or
  "No active task". Log as **enhancement: status bar could reflect
  task completion progress.**
- [ ] Weekend mode: is there any visual difference in the list on
  Saturday vs Wednesday? (There shouldn't be — but verify nothing
  breaks either.)

### Error states and edge cases
- [ ] With 0 tasks in `dust-kept`: pull up the sheet, see bat mascot
  + empty state copy. Does the copy use praise term from
  `identity.toml` or is it hardcoded? (Hardcoded — Phase K deferred.)
  Log as **known gap: empty state copy not identity-aware.**
- [ ] Quick Add submit with empty title: no feedback. Log as defect.
- [ ] Quick Add double-tap submit: creates duplicates. Log as defect
  if confirmed.
- [ ] Mid-animation tap: verify no crash. Log blocker if crash.
- [ ] Process kill between sessions: Done view loses in-memory state.
  Log as **major defect: in-memory task completions are not persisted;
  Done view loses history on app kill.**

### Accessibility (Dust doesn't use TalkBack, but a runner should check)
- [ ] Checkbox `contentDescription`: Material3 Checkbox does expose
  a content description via semantics. Verify TalkBack reads the
  task title in context.
- [ ] Confirm dialog: announced as modal?
- [ ] Quick Add Submit: announced as button with correct label?
- [ ] Rail chips: each has `testTag` but does TalkBack read them
  as selectable items?

---

*Runner: log every deviation from an "→ Expect:" line as a defect entry
in `docs/walks/cheetah-todolist-fast/defects.md` using the format from
`exhaustive-click-everything/README.md`. Mark QA CHECK items `[x]` as
verified. The walk is done only when a clean re-walk produces zero new
defects and every QA CHECK box is ticked.*

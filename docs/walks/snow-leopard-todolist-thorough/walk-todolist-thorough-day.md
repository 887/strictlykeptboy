# Walk — Snow leopard's thorough todolist day: depth, Dom-authored tasks, completion rituals

**Persona:** Glaciermark ("Marc"), a kept snow leopard sub, kept solo by
an AI Dom ("Sir") that runs `claude -p` against `marc-kept`
(see [`personality.md`](personality.md)).
**Goal:** Verify the todolist bottom-sheet experience from the angle of
a meticulous user who wants to use *every* rail tab, notice *every*
visual detail, and complete Dom-assigned tasks with the right weight.
QA angle: correctness of data, legibility, confirm dialogs, empty/edge
states, author attribution, By Repo grouping, Done history, and the
emotional loop of a kept boy finishing his Sir's assigned list.
**Preconditions:**
- App installed + `marc-kept` (strictly-kept) and `marc-work` (free)
  both configured and synced.
- `marc-kept` contains at least 8 tasks: 3 due today (2 authored by Sir,
  1 by Marc), 2 overdue, 2 Upcoming (due tomorrow + next week), 1
  standing/pinned task.
- `marc-work` contains at least 3 tasks (all no-due, a mix of priorities).
- Multi-repo flag is on (both repos active).
- It is a **weekday** (cage-on window in effect, Sir's tasks active).
- One task in `marc-work` has a linked timebox event (linked event start
  set for 14:00 today).
- Done list starts empty (nothing completed yet today).
**Features exercised:** bottom-sheet pull (NowPlayingSheetHost),
TasksFilter rail (Today/Upcoming/All/ByRepo/Done), TaskTodayView 3
sections, TaskRow (checkbox, confirm dialog, author chip, due chip,
overdue error colour, linked timebox chip, priority dot, repo dot),
TaskDoneView, TaskPerListView, Quick Add FAB + sheet,
EmptyTasksState (Done before any completions), passive habits (D.54).

> Format: numbered steps, "→ Expect:", "QA CHECK:" bullets,
> "HOW IT SHOULD FEEL:" notes where today falls short.
> The runner logs every deviation from Expect as a defect using the
> `defects.md` format from `exhaustive-click-everything/`.

---

## Part A — Pulling up the sheet (the entry gesture)

### Step A.1 — Pull the status bar into the todolist

Marc is on the main schedule screen. He sees the bottom status bar:
"No active task · Tap to pick one" (or a task name if one is active).
He **swipes up from the status bar** to expand the todolist sheet.

→ **Expect:** The sheet animates up smoothly and settles at
full-screen. The rail (Today / Upcoming / All / By Repo / Done) is
immediately visible on the left edge. The Today filter is selected by
default. The content area shows the Today view with tasks.

**QA CHECK:**
- [ ] Animation speed — does the sheet reach full height in < 350 ms?
  Sluggish expansion (> 500 ms) is a friction defect.
- [ ] Rail labels — are all 5 labels (Today / Upcoming / All / By Repo /
  Done) fully legible? The rotated text is known to clip at narrow
  widths — verify none reads "Toda" or "By Rep".
  testTag: `ExpandedTaskChipStrip`, items `ExpandedTaskChip-Today` etc.
- [ ] Default filter — is Today selected on open? Primary accent
  indicator should be on the Today label.
- [ ] Sheet fill — does the content area fill to the top of the screen,
  with no dead band at the bottom where the Quick Add FAB floats
  in empty space?
- [ ] One-handed reach — can Marc trigger the pull gesture and land on
  the top rail items with one thumb on a standard phone without
  re-gripping? The rail is at the leftmost edge; test whether
  Today (top item) requires a stretch that forces re-grip.
- [ ] Scrim — is there a scrim / dimming effect on the schedule behind
  the sheet? Verify it renders (cosmetic but orientation-anchoring).

**HOW IT SHOULD FEEL:** Pulling the bar up should feel like opening
a familiar drawer — instant and springy, the list already visible as
it rises. Today: the animation exists but the sheet velocity doesn't
currently carry a spring back; it settles linearly. The "dead band at
the bottom" bug (fillParentMaxHeight fix in Round 2.16.A) should be
confirmed resolved.

---

## Part B — Today view: three sections, Dom-authored tasks

### Step B.1 — Read the Today view structure

Marc's eyes scan the Today view. He expects three named sections.

→ **Expect:** Three section headers visible: one for dated/overdue tasks
("Today" or similar), one for "From events" (linked timebox tasks +
pinned standing items), one for "Pinned". Each header is distinct
(`titleSmall` + primary colour).

**QA CHECK:**
- [ ] Are all three section headers present? testTag prefix:
  `TodaySection-<label>`. If only one section is non-empty, the
  others should be absent — not blank headers.
- [ ] Are the overdue tasks visually distinct? The 2 overdue tasks
  (from Preconditions) should appear with title text in
  `colorScheme.error` AND the due chip also in error colour.
- [ ] Are the 3 "dated today" tasks in the "Today" section (not
  mixed into "From events")?
- [ ] The linked-timebox `marc-work` task (due 14:00 today) — does it
  show a `LinkedTimeboxChip` ("14:00 today" in primary colour)?
  testTag: `TaskLinkedTimebox-<id>`.
- [ ] Repo dots — does each task row show a colour-coded repo dot to
  distinguish `marc-kept` tasks from `marc-work` tasks?
  testTag: `TaskRepoDot-<id>`.

**HOW IT SHOULD FEEL:** Marc should be able to read the section headers
and immediately know: "here are my overdue things, here are today's
things, here is what's pinned." Currently there is no visual weight
difference between the section header text and the first task row
title — they use the same `titleSmall` scale. A slightly heavier
header (or a subtle horizontal rule) would help him parse sections at
a glance.

### Step B.2 — Identify Sir's task (author chip)

Marc scans for the two Dom-authored tasks. He expects them to carry
Sir's author chip.

→ **Expect:** Two tasks have a circular `AuthorBubble` widget ("Si"
initials, coloured by seed) in the row's second line — to the right of
the todolist name. testTag: `TaskAuthor-<id>`. The author chip appears
only on foreign-author tasks (Sir's author != Marc's identity string).

**QA CHECK:**
- [ ] Both Sir-authored tasks have an author chip.
- [ ] Marc's own tasks do NOT have an author chip (they're self-authored
  and should be transparent).
- [ ] The initials shown are "Si" (from "sir") or similar — not "?"
  (which would mean the author field is empty or the initials
  extraction failed).
- [ ] Does tapping a Sir-authored task open the detail sheet with
  Sir's body text (the note Sir wrote in identity.toml register
  voice)? Verify the Markdown body renders via Markwon.

**HOW IT SHOULD FEEL:** The author chip today is a small 16dp circle
with initials. For a kept boy checking which tasks are Sir's, this
feels small and easy to miss at a glance. An ideal would be a tertiary
accent strip on the row (instead of or in addition to the bubble) for
Dom-assigned tasks — similar to how KeeperPrompt rows already use
`colorScheme.tertiary`. Gap: no visual treatment distinguishes
Dom-assigned regular tasks from self-authored ones at the row level
beyond the 16dp bubble.

### Step B.3 — Complete a non-Sir task (confirm dialog)

Marc taps the checkbox of one of his own tasks to mark it done.

→ **Expect:** A confirm dialog appears immediately:
- Title: (from `task_confirm_done_title` string resource)
- Body: (from `task_confirm_done_body` string resource)
- Confirm button testTag: `TaskCheckbox-confirm-<id>`
- Cancel button testTag: `TaskCheckbox-cancel-<id>`
Marc taps Confirm.

→ **After confirm:** the task row moves to strikethrough + dim style,
`done = true`. If the task had a due date, the due chip now reads
"done today" (or "done yesterday" etc. from `formatDue`).

**QA CHECK:**
- [ ] Does the confirm dialog appear on the first checkbox tap to done?
  (It should NOT appear on uncheck — unchecking is unguarded.)
- [ ] Dialog title + body — are they non-empty, English, legible?
  If they read `tasks_confirm_done_title` (raw resource key) the
  strings.xml wiring is broken.
- [ ] Confirm button tap — does the task visually update to done
  (strikethrough, `onSurfaceVariant` colour)?
- [ ] Cancel button tap on a separate task — does the checkbox revert
  to unchecked with no state change?
- [ ] After completing, does the task stay in the Today list
  (dimmed/strikethrough), or does it disappear from Today? Verify
  which behaviour the code takes (`sortedForCombined` puts done tasks
  at the bottom of the active+done sort — they should remain visible
  at the bottom of the section, not vanish).
- [ ] a11y — is the confirm dialog announced by TalkBack as a modal
  dialog? (Manual check if a11y scanner is available.)

**HOW IT SHOULD FEEL:** The confirm dialog is intentional (ticket:
"bypasses the playback timer, no undo from history") and Marc
appreciates it. But the dialog copy today is generic — for Sir's
assigned tasks, the ideal would be personalised copy: "Good job,
sweet leopard — mark this one done?" using `identity.toml`'s praise
term. Gap: `EmptyTasksState` hardcodes praise; the confirm dialog does
not pull from `identity.toml` at all.

### Step B.4 — Complete a Sir-assigned task (same confirm, different weight)

Marc finds one of Sir's tasks ("read thirty pages", authored by Sir)
and taps its checkbox.

→ **Expect:** Same confirm dialog flow as Step B.3. The task marks done.

**QA CHECK:**
- [ ] Same as B.3 — confirm dialog appears.
- [ ] Author chip still visible on the row after marking done (the row
  dims but stays in the list).
- [ ] The due chip, if any, reads "done today" after completion.
- [ ] Priority dot (if set on Sir's task) — still visible on the done row?

**HOW IT SHOULD FEEL:** Completing a Sir-assigned item should feel like
a small ritual — a moment of acknowledgement. The generic dialog loses
that. An ideal would be that Dom-assigned tasks get a slightly warmer
confirm copy. Gap: logged, design work needed.

---

## Part C — Upcoming view (due-date planning)

### Step C.1 — Navigate to Upcoming

Marc taps the "Upcoming" rail item.

→ **Expect:** The content area switches to `TaskCombinedView` filtered
to `done == false`. The 2 upcoming tasks (due tomorrow, due next week)
should appear.

**QA CHECK:**
- [ ] Does the Upcoming rail item respond immediately on tap (no lag)?
  testTag: `ExpandedTaskChip-Upcoming`.
- [ ] Are both upcoming tasks (tomorrow + next week) visible?
- [ ] **STUB ALERT (code comment in ExpandedNowPlayingTaskBody.kt):**
  "Stub: Subagent 2 / Phase B routes Upcoming through a dedicated
  upcoming-only filter. For now reuse Combined so the chip is
  functional." — the runner MUST verify this stub is visible as a
  defect: Upcoming currently shows ALL non-done tasks (including
  today-dated ones), not just future-dated ones. Log this as a
  major defect: **Upcoming filter does not scope to future-dated tasks**.
- [ ] Are tasks sorted by due date ascending (soonest first)?
  `sortedForCombined` sorts overdue → due-asc → no-due; the expected
  order is: tomorrow, then next week, then any no-due tasks from the
  `marc-work` list.
- [ ] Section headers — does the Upcoming view show section headers
  (same as Today), or does it render flat? (CombinedView has no
  sections; verify the stub renders without crashing.)
- [ ] Empty state — if the user had no upcoming tasks, does
  `EmptyTasksState` appear with the bat mascot? (Edge test with
  a cleared `marc-work`.)

**HOW IT SHOULD FEEL:** Upcoming should be a clean due-date-sorted list
of what's coming, grouped by day ("Tomorrow", "This week", "Later").
Today it is the Combined view with a done filter — no grouping by day,
no "tomorrow" section header, tasks interleaved by priority. This is the
biggest conceptual gap in the todolist: the Upcoming view isn't really
about upcoming. Gap: Phase B stub must be actioned.

---

## Part D — All view with per-list sub-filter

### Step D.1 — Navigate to All and use the per-list chip strip

Marc taps "All" on the rail.

→ **Expect:** The content area shows `TaskPerListView` with a secondary
chip row at the top. Chip row has one chip per todolist (from both
repos since multi-repo is on). The main list below shows all tasks
across all todolists.

**QA CHECK:**
- [ ] Does the secondary chip row appear? If the chip row is off-screen
  or clipped, this is a layout defect.
- [ ] Does selecting a list chip filter the task list below to that
  list only? testTag of chips: `QuickAddTarget-list-<id>` (if reused)
  or per TaskPerListView implementation.
- [ ] Does deselecting the chip return to "all lists" view?
- [ ] With multi-repo on: do tasks from `marc-kept` and `marc-work` both
  appear?
- [ ] With 10+ tasks, does the list scroll smoothly? Is there a scroll
  indicator or does the list look cut off at the bottom?
- [ ] Are the list-name chips in the task rows rendered (todolist name,
  emoji prefix if set)? testTag: `TaskListChip-<id>`.

**HOW IT SHOULD FEEL:** The secondary chip row should give an instant
"here's my list, let me filter to just that one" feel. Currently the
chip selection state is kept in `rememberSaveable` local to the `All`
branch — switching to another rail tab and back resets the chip
selection. For a thorough user this is annoying (he wants to come back
to the same sub-filter). Gap: per-list chip selection should persist
across tab switches.

---

## Part E — By Repo grouping

### Step E.1 — Navigate to By Repo

Marc taps "By Repo" on the rail. He expects to see tasks grouped by
source repo with human-readable repo names.

→ **Expect:** The content area shows a `LazyColumn` with repo headers
("marc-kept", "marc-work") and tasks underneath each.

**QA CHECK:**
- [ ] Are exactly two repo sections visible (one per repo)?
  testTag of headers: none currently — `item(key = "by-repo-header-$repoId")`.
  Runner should locate via a11y tree or coordinate.
- [ ] **BIG GAP — raw repoId in header text:** The current code
  (`ExpandedNowPlayingTaskBody.kt` line 228) renders `Text(text = repoId)`.
  The `repoId` is the raw Git remote/slug identifier, NOT a
  human-friendly name. Log this as a **major defect**: By Repo headers
  show raw IDs (e.g. "github.com/887/marc-kept") not display names
  (e.g. "marc-kept" or the repo's `display_name` field).
- [ ] Does the header row have sufficient background contrast against the
  task rows below? (Currently `surfaceContainer` background on the
  header + `surface` on rows — verify the visual distinction is clear.)
- [ ] With 8+ tasks across two repos, does the list scroll to reveal the
  second repo section? Or does the first section's tasks push the
  second header off-screen without a scroll indicator?
- [ ] testTags on task rows in By Repo: `by-repo-row-$repoId-<taskId>` —
  confirm the a11y tree sees them.
- [ ] If one repo has 0 tasks visible (edge case: all tasks hidden),
  does the section header appear with an empty body, or does it
  vanish gracefully?

**HOW IT SHOULD FEEL:** By Repo should be a fast mental context-switch:
"here's my personal kept list, here's my work list." The raw repoId
header makes this jarring — it reads like a file path, not a repo name.
The ideal is a display name derived from the repo's `name` field (from
`RepoMeta`). Gap: header text must use repo display name, not raw ID.

---

## Part F — Done view and completion history

### Step F.1 — Navigate to Done (before any completions — empty state)

If Marc switches to Done before completing anything, he sees the empty state.

→ **Expect:** `EmptyTasksState` renders with bat mascot + copy from
`tasks_empty_done_primary` / `tasks_empty_done_neutral`.
testTag: `EmptyTasksBat`, `EmptyTasksMessage`.

**QA CHECK:**
- [ ] Does the bat mascot image load (not broken/blank)?
- [ ] Is the empty state copy legible and non-generic? Does it use
  the praise term from `identity.toml` or is it hardcoded?
  (Current code: hardcoded — Phase K wires identity. Flag if copy
  reads a raw string key.)
- [ ] Does the empty state fill the available height without clipping
  the bat image?

### Step F.2 — Navigate to Done after completing tasks (Step B.3 + B.4)

After completing 3 tasks, Marc taps "Done".

→ **Expect:** `TaskDoneView` shows the 3 completed tasks, sorted
most-recently-done first. Each row has strikethrough title, dimmed
colour, and "done today" in the due/doneAt chip.

**QA CHECK:**
- [ ] Are exactly 3 tasks visible (the ones Marc marked done in B.3/B.4)?
- [ ] Sort: is the most-recently-done task at the top? (Sort is by
  `doneAt` desc, but note: `toggleDone` in `TasksViewState` does NOT
  set `doneAt` — it only flips `done`. Verify if `doneAt` is null on
  in-memory-toggled tasks, and if so, all `doneAt == null` tasks sort
  to the bottom via `LocalDate.MIN` fallback — meaning the in-memory
  "done today" tasks may appear BELOW tasks with a real `doneAt` from
  a previous sync. Log this as a **defect: in-memory toggleDone does
  not stamp doneAt; done order is wrong in Done view.**
- [ ] Does the "done today" or `agoFormat` appear on each row?
  (`agoFormat` renders "today" when `doneAt == today` but again only
  if `doneAt` was set — see above.)
- [ ] Are Sir's authored tasks distinguishable from Marc's in the Done
  view? (Author chip should still appear on done Sir-authored rows.)
  Currently `TaskDoneView` passes no `multiRepo` / `activeRepoOwner`
  params to `TaskRow` — so repo dots and author chips may be ABSENT
  on Done view rows. Log as **defect: Done view does not pass
  multiRepo/activeRepoOwner; author chips and repo dots missing.**
- [ ] Can Marc un-check a done task (tap the checkbox to un-done)?
  The uncheck is unguarded — verify it reverts immediately without
  a confirm dialog.
- [ ] After un-checking, does the task disappear from Done and reappear
  in Today/All?

**HOW IT SHOULD FEEL:** The Done view should feel like a satisfying
ledger — "here's what I accomplished today, including Sir's assigned
items." The absence of author chips in Done view is a gap: Marc loses
the "Sir's task, done" attribution exactly where he most wants to see
it (end-of-day review).

---

## Part G — Quick Add flow

### Step G.1 — Open Quick Add FAB

Marc taps the "+" FAB in the bottom-right corner of the sheet.
testTag: `QuickAddFab`.

→ **Expect:** `TaskQuickAddSheet` opens as a `ModalBottomSheet`
(sits above the todolist sheet). Contains:
- Title text field (testTag: `QuickAddInput`)
- Target selector chip row with each todolist + "Today as event" +
  "Tomorrow as event" chips (testTag prefix: `QuickAddTarget-`)
- Submit button (testTag: `QuickAddSubmit`)

**QA CHECK:**
- [ ] Does the Quick Add sheet open without a lag?
- [ ] Are both todolists (`marc-kept` default list, `marc-work` list)
  available as target chips? With multi-repo on, both should appear.
- [ ] Is one chip pre-selected by default? (Code: `initialTarget`
  defaults to `targets.firstOrNull()` — the first todolist.) If Marc
  most recently added to `marc-kept`, is that pre-selected? Currently
  no "last used" memory — `initialTarget` is the first in the list.
  Log as **minor gap: Quick Add does not remember last-used target
  list.**
- [ ] Does the keyboard appear automatically when the sheet opens so
  Marc can start typing immediately without an extra tap?
  (Currently no `focusRequester` on the `OutlinedTextField` — the
  keyboard does NOT auto-show. Log as **minor defect: Quick Add input
  field does not auto-focus; requires extra tap to start typing.**)
- [ ] Submit with empty title — does Submit silently no-op? Or does it
  show an error? (Code: `if (cleaned.isEmpty()) return@Button` — no
  error shown.) Log as **minor defect: Quick Add submit with empty
  title shows no feedback.**
- [ ] Submit with a valid title — does the sheet dismiss and the task
  appear in the appropriate list?

### Step G.2 — Quick Add "Today as event"

Marc selects "Today as event" target and submits a title.

→ **Expect:** A calendar event for today is created in the active
calendar. The task does not appear in the todolist.

**QA CHECK:**
- [ ] Does the task appear on the Schedule day view (not the todolist)?
- [ ] If it appears in both, that is a routing defect.
- [ ] Dismiss Quick Add sheet — does it animate out cleanly?

---

## Part H — Passive habits (D.54) / inverted tasks in Today view

Marc notices a standing/pinned task ("Morning stretch") in the
Today → Pinned section. This is a passive habit: done-by-default,
deviation = explicit file. It has `standing = true`, `pinnedForToday = true`.

### Step H.1 — Observe standing/pinned task behaviour

→ **Expect:** The task appears in the "Pinned" section of Today.
It has a checkbox. It is NOT ticked (done = false is the default state;
the passive habit model means completion is assumed unless deviating).

**QA CHECK:**
- [ ] Does the standing/pinned task appear in the "Pinned" section
  (not in "Today" or "From events")?
- [ ] Is the checkbox unchecked by default?
- [ ] Tapping the checkbox — does the confirm dialog appear? (It should,
  since ticking = "recording a deviation" in the passive habit mental
  model, though the UI does not distinguish between regular done and
  passive-habit deviation explicitly.)
- [ ] **Design gap to flag:** There is no visual indication that this task
  is a passive habit (done-by-default). The row looks identical to a
  regular task. A user who doesn't know D.54's model may not understand
  why this task appears every day un-checked. Flag as **UX gap: passive
  habit tasks are visually indistinguishable from regular tasks; no
  "default done" indicator.**
- [ ] Does the task survive a tab switch (Today → Done → Today) still
  pinned and still in the Pinned section?

---

## Part I — Weekend observation (release window)

On a Saturday (change device date), Marc opens the sheet.

→ **Expect:** Sir's cage-on window does not appear in the schedule.
The Dom-assigned todolist tasks still appear (they are real tasks, not
passive habits; weekends don't cancel them). The "kept" framing still
applies to the tasks.

**QA CHECK:**
- [ ] Does the rail filter retain its last-selected tab across app
  restarts? (`rememberSaveable` scope — verify the selected filter
  survives a process restart or orientation change.)
- [ ] Multi-repo tasks — on weekends when Sir has fewer tasks, does
  the By Repo view show the correct reduced set?

---

## Part J — QA angles for the runner

The runner must evaluate every dimension below and log findings as
defects in `defects.md` (using the format from
`exhaustive-click-everything/`). A clean walk = zero open defects.

### Efficiency (taps-to-complete)
- Count taps from "sheet closed → task marked done". Today's path:
  pull up bar (1 gesture) + locate task (0 taps) + tap checkbox (1) +
  confirm dialog (1) = 3 taps + 1 gesture. Is this the minimum
  achievable? Could the confirm dialog be skipped for non-Sir tasks?
- Count taps for Quick Add: FAB (1) + type title + select target + submit
  (1). If keyboard auto-focuses: 2 taps total. Without auto-focus: 3 taps.
  Log the current count and the ideal.

### Error prevention
- Can the user accidentally dismiss the sheet (swipe down) mid-completion?
  Does the confirm dialog prevent accidental dismissal?
- Does Quick Add prevent duplicate task creation on double-tap of Submit?

### Consistency
- Are section headers styled consistently across Today / All / By Repo?
  (Today uses `sectionHeader()` helper; By Repo uses an inline `Text`.
  Check for typographic + colour parity.)
- Are the rail chip hit targets consistent? Each chip is 52dp × 108dp
  per code — measure the actual tap zone on device.

### Feedback / affordances
- After marking a task done, is there any haptic or animation feedback
  (beyond the visual strikethrough)? Currently no haptic — note as gap.
- Does the confirm dialog have an animation (Material3 dialog defaults)?
  Verify it appears, does not just snap in.
- Empty Done view — is the bat mascot copy informative ("nothing
  finished yet — get going!") or generic?

### Aesthetic / minimalist
- Is the 4dp left-accent strip on task rows visually distinct but not
  overwhelming? Verify with the colour-seed algorithm that two tasks
  from the same list don't have the same accent colour.
- Is the priority dot (12dp circle) large enough to see at a glance?
  The comment notes it was bumped from 10dp to 12dp for legibility.

### Recognition over recall
- Does the rail make the selected filter obvious (primary accent bar on
  the right edge of the selected chip)? Verify the accent bar renders
  at all selected states.
- Is there any visual indication of how many tasks are in each rail
  section (badge counts)? Currently none — log as enhancement.

### Undo
- There is no undo for a completed task. The only path back is: tap Done
  → find the task → uncheck it. Is this path discoverable? Log as gap.

### Accessibility
- Run TalkBack through the rail — are chip labels announced correctly
  ("Today tab, selected" etc.)?
- Are checkboxes announced with their task title in context ("Checkbox,
  Read thirty pages, unchecked")?
- Is the confirm dialog announced as a modal?
- Are the author chip initials ("Si") meaningful to a screen reader,
  or do they need a content description? Currently the `AuthorBubble`
  has no `contentDescription` — log as **a11y defect.**
- Is the `PriorityDot` accessible? It has no content description — log
  as **a11y defect.**
- Minimum touch target: Material3 recommends 48dp × 48dp. The checkbox
  renders in a `padding(start = 8.dp)` context; measure the actual
  touch target size.

### One-handed reachability (bottom-sheet specific)
- The Quick Add FAB is at the bottom-right of the sheet. After pulling
  the sheet full-screen, can the user tap the FAB with the thumb
  one-handed without re-gripping? Test both left-handed and
  right-handed grip.
- The rail is at the left edge and runs top-to-bottom. Rail items at
  the top (Today) are furthest from the thumb in a right-hand grip.
  Is Today reachable? If not, this is a critical one-handed gap.

### The kept/Dom emotional loop
- Does the Done view, after a day of work, let Marc see which tasks
  were Sir's vs his? (Currently: no — author chips missing from Done
  view.) Is the end-of-day review emotionally satisfying?
- Is there any Dom-voice copy in the task flows (confirm dialog, Quick
  Add, empty state)? Currently only `EmptyTasksState` is kink-aware
  (and even that is hardcoded, not `identity.toml`-driven). The confirm
  dialog and Quick Add are generic. Log all as **enhancement: missing
  identity.toml voice in todolist flows.**
- Weekend: does the lack of Sir's cage-window affect the task list in
  any visible way? (It should not — tasks are separate from calendar
  events.) Verify no weekend-mode state leaks into the task views.

### Edge cases to probe
- Pull up sheet when `marc-kept` has ZERO tasks: does Today show
  `EmptyTasksState`? Does the bat mascot render at correct size within
  the bottom-sheet height constraint?
- Pull up sheet when BOTH repos have zero tasks: same as above; verify
  the By Repo view doesn't crash with an empty map.
- Quick Add with a very long title (> 100 chars): does the row truncate
  with ellipsis in the task list?
- A task with `due = today` AND `done = true` AND `doneAt = null`
  (in-memory toggle): does `formatDue` correctly show "done today" or
  fall back to a wrong date? (See Step F.2 doneAt gap.)
- Rotate device to landscape while the sheet is open: does the rail
  re-layout or clip?

---

*Runner: log every deviation from an "→ Expect:" line as a defect entry
in `docs/walks/snow-leopard-todolist-thorough/defects.md` using the
format from `exhaustive-click-everything/README.md`. Mark QA CHECK
items `[x]` as you verify them. The walk is done only when a clean
re-walk produces zero new defects and every QA CHECK box is ticked.*

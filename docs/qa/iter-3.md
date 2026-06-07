# QA iteration 3 — test-suite repair, event-create UX, final sweep

Date: 2026-06-07. Target: `medium_phone` AVD (API 36) via scrcpy. Built on
iter-2 (`335c030`). Focus: fix the 4 pre-existing red tests (real defects),
implement the carried date/time-picker improvement (F), final surface sweep.

## Phase A — Pre-existing test failures (found in iter-2, fixed here)

All 4 were confirmed red at `6e02d2c` with iter-2 stashed — pre-existing, not
introduced by QA. Each is a genuine defect (stale test or shipped bug):

- [x] **A.1 — `EmptyScheduleStateTest` initials.** Expected `initialsForAuthor(
  "alex-887") == "AS"`, but the helper correctly yields "A8" (A + 8). Stale
  expectation; also `"alex"` is a possible real-name remnant (a
  `pre-name-scrub` backup dir exists). Rewrote with neutral, self-consistent
  fictional names (`mara` / `mara-quinn` → "MQ"). Helper unchanged (it was
  correct).
- [x] **A.2 — `AppShellNavigationSwapTest`.** Expected a `ShellDest-Tasks`
  top-bar button, but commit `1a97a32` ("drop Tasks from top-bar") removed it
  (Tasks moved to the bottom "No active task" bar + FAB). `SkbTopBar` now lists
  only Schedule + Reviews. Updated the test + its doc comment: Tasks is now in
  the `hidden` set; only Schedule + Reviews render as buttons.
- [x] **A.3 / A.4 — `RichDemoSupersedenceTest` + `RichDemoResolverOverlayTest`.**
  Both failed "preserved calendar …005 must not be paused, but was …00a". The
  `vacation` calendar's `supersedes` list included `…005` (kinky-rituals) and
  `…008` (cat-care), both `non_superseable = false` — so a vacation **paused
  feeding the cat**. This violates locked **decision D.76** ("Default-
  nonSuperseable categories: medication, pet-care, vet… vacation never silently
  skips meds or pet feedings") and **D.78 S4**. A misleading 2026-05-27 comment
  even justified "paused wholesale". Fix: marked cat-care + kinky-rituals
  `non_superseable = true`, removed them from `vacation.supersedes`, corrected
  the comment. **Real shipped bug, not just a test fix.**

## Phase B — Improvement implemented (carried F from iter-2)

- [x] **B.1 — Event-create date/time pickers (EC-F).** Start/End were raw,
  editable ISO-8601 text (`2026-06-07T12:00+02:00`). Replaced with a
  `DateTimeField` helper: two buttons (date / time) showing localized values
  ("Fri, 12 Jun 2026" · "12:00"), opening the Material3 `DatePicker` /
  `TimePicker`. Picks preserve the untouched component AND the draft's UTC
  offset. Verified on device: picking Jun 12 updated Start (time preserved) and
  correctly tripped the "End must be after start" validation.

## Phase C — Final surface sweep (no new defects)

- [x] **C.1** Templates — atomic registry + custom-URL field; copy reads
  "Cloning custom sticker packs is coming soon" (iter-1 B.1 fix confirmed).
- [x] **C.2** Mode — role radio + Dom-persona chips (iter-2 FlowRow fix confirmed).
- [x] **C.3** Event-create — calendar chips de-duplicated (iter-2 fix confirmed),
  pickers working.

## Phase D — Carried / logged (not fixed this pass, with rationale)

- [ ] **B.4 — Event-detail timezone redundancy** ("Arctic/Longyearbyen ·
  Longyearbyen"). Cosmetic; needs a product decision on multi-tz display
  format (when to show IANA id vs friendly city, and only-when-differs-from-
  device). Left as a logged minor.
- [ ] **S4 resolver enforcement** — `non_superseable` is parsed
  (`SupersedenceConfig`) but never reaches the resolver's `CalendarMeta`, so
  D.78 S4 ("nonSuperseable calendars ignore all supersedence requests") is not
  enforced in code — preservation currently relies on data discipline (not
  being listed in a `supersedes`). The iter-3 data fix is correct and tests are
  green, but the invariant should be enforced defensively. **Logged in
  `refactor-solid.md` for follow-up (multi-site CalendarMeta plumbing).**
- [ ] **G.1 (from iter-1) — CalDAV engine** is still a "coming soon" placeholder
  while the README sells it as v1. Flagged to owner; out of scope for a QA loop.

## Phase E — Verification

- [x] **E.1** Full `:app:testDebugUnitTest` suite green (was 4 red, now 0).
- [x] **E.2** Event-create + EventDraft tests green after the form change.
- [x] **E.3** Date/time pickers verified live on the AVD.

## Status: ✅ DONE (4 pre-existing reds fixed; date/time pickers shipped; suite green; B.4 + S4 + CalDAV logged)

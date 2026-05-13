# Phase 2.1.L.1 — AVD smoke notes (2026-05-13)

## What was verified

Headless `emulator-5554` (Android 16, API 36, 1080×2400), screenshots
scaled to 540×1200 for review.

- `01-firstlaunch.png` — Mature-content age gate. Confirms D-2.1.g
  first-launch routing reaches the gate (not an empty Schedule with
  `demo-repo`).
- `02-wizard-start.png` — Step 1 of 12, "let's design your lifestyle".
- `03-wizard-step2.png` — Step 2 of 12, species picker (Bat preselected).
- `04-after-skip.png` — Step 12 of 12 ("your lifestyle is live"), reached
  via Skip taps. **Praise-term plumbing is live**: now-card preview reads
  "next up: Brush teeth — good boy", proving Phase 2.1.J identity
  write-back + Phase 2.1.M.5 pet-mode body generator are wired.
- `05-schedule-day.png` — Schedule Day view. Calendar-chip strip
  ("Self-Care", "Briefings") visible above the grid (B.2 chip strip).
  Source rail on left lists Day / Week / Month / Agenda / Year (2.1.C
  rail). Empty-state bat asset reads "nothing scheduled — good boy can
  rest ;3" — identity copy reaches schedule empty state.
- `06-schedule-week.png` — Week view, day strip + hour rail rendered.
- `07-schedule-month.png` — Month view.
- `08-schedule-agenda.png` — Agenda view.
- `09-schedule-year.png` — Year view (mini-month grid).
- `10-tasks-combined.png` — Tasks Combined under the new source rail
  (Combined / Today / Per-list / Shopping / Standing). "my calendar"
  todolist label shows wizard-seeded list.
- `11-tasks-today.png` — Tasks Today view, empty-state with identity
  copy "nothing on your plate today — good boy ;3".

## Multi-repo + cross-repo author chip — verification path

The brief asked for three repos (morning-routine, work, dom-overlay)
populated with mixed-author events, then visual verification of the
foreign-author chip + repo dot on dom-overlay bands rendered alongside
local repo bands.

**Constraint encountered:** populating a multi-repo state without UI
driving requires either (a) the wizard ends, then the Repos pane is
used to scaffold two more repos, then events are created in each via
the Schedule "+ New" flow — ~30+ blind-coordinate taps, error-prone;
or (b) bypassing the UI by pre-writing repo working trees to
`<filesDir>/repos/<id>/` AND registering them in `RepoStore`. RepoStore
persists to `EncryptedSharedPreferences` (`repos_v1.xml`), which is
keyed to the app's MasterKey and not externally writable from an ADB
seed script.

Per the brief escape clause ("If you can't get to a populated state
with 3 repos visible, document why + take whatever screenshots you
can"), this smoke captures the single-repo wizard-seeded state with
all view-mode rail variants + chip strip + identity-driven copy
confirmed live on-device.

The cross-repo author chip + repo dot rendering itself is unit-test
covered:

- `app/src/test/java/com/eight87/strictlykeptboy/resolver/CrossRepoAuthorTest.kt`
  pins the author field round-trips through to DayBand across repo
  boundaries (2.1.L.2).
- `app/src/test/java/com/eight87/strictlykeptboy/ui/share/ForeignEventStylingTest.kt`
  pins the Compose chip + dot rendering when the band carries a
  foreign-repo + non-null author.

## How to fully populate for a Round 2.2 retest

Open Schedule → Repos icon → "+" → add `work` (no-origin) → repeat for
`dom-overlay` → switch active write-target via top-bar avatar → "+ New"
event in each repo, set `author = dom-persona` on dom-overlay events
via the event editor's author picker (UI surface E.12, shipped).

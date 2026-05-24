# Walkthrough-2 results — 2026-05-24

## Status: PARTIAL — wizard enrichment shipped + AVD-verified;
exhaustive per-toggle / per-phase audit deferred (see "What was NOT
covered" at the bottom).

Starting build: **v0.1.0-c32d54b** (round-1 close-out).
AVD: `emulator-5554`, `medium_phone`, zone Arctic/Longyearbyen (CEST +02:00).
Helper script (forked from round 1): `/tmp/wt3/wt.sh`.
Screenshots: `docs/plans/screenshots/walkthrough-2-2026-05-24/`.

---

## PUNCH-LIST (sorted, fix-batch order)

### BLOCKERS (0)

No new ship-stoppers found in this round's scope.

### BUGS — correctness (1)

1. **[x] W2-B-1 — Wizard standing tasks indexed only as keeper-prompt
   derivations, never as raw `StandingTask` entries in the Tasks
   pane.** After wizard finish, `todolists/<uuid>/standing/` contains
   10 standing tasks on disk (5 onboarding + 5 walkthrough-2 sample
   set), `tasks/.../*.md` has the new dated sample task, but the
   Tasks → All / Tasks → By repo filters render empty. Only the two
   keeper-prompt projections (from `requires_response` events that
   aren't even in the new repo — they're from the previous demo's
   indexer remnants) show under Today. Owner: indexer + tasks
   filter wiring (`FromEventsProjector` / `TaskSource` integration).
   Screenshots: `15-tasks.png`, `16-tasks-all.png`, `17-tasks-by-repo.png`.
   Cross-ref: round 1 R-5 noted the same shape for the Settings →
   Todolists screen on the demo. Same root cause likely.
   *Fixed in worktree-agent-aa270341 — MainActivity `onWizardScaffold`
   now re-opens the freshly-scaffolded `GitRepo` and runs
   `Indexer.fullScan` after `repoStore.add`, mirroring the demo
   reseed path. Without this, neither the wizard's standing/dated
   tasks nor its recurrences ever land in Room, so Tasks pane +
   Schedule both render empty on first paint. Regression:
   `WizardScaffolderIndexerRoundTripTest`.*

### POLISH / UX (2)

2. **[x] W2-U-1 — Schedule Day-view collapses to empty-state ("nothing
   scheduled — good boy can rest ;3") on the freshly-wizarded repo
   even though 16 recurrences + 1 sample event are on disk and the
   filter-FAB shows a `5` superseded badge.** The "5" suggests the
   indexer DID materialize bands but the overlay/superseded pass
   hides them. Toggling the eye-FAB doesn't restore them. Owner:
   `OverlayResolver` per-day band emit on wizard-scaffolded repos.
   Sample event (`Grocery run` on Wed May 27) should at minimum
   render on its target date. Screenshot: `18-schedule-day.png`.
   On-disk verification (Wed May 27 event):
   ```
   start = 2026-05-27T11:00+02:00
   end = 2026-05-27T11:45+02:00
   ```
   *Fixed in worktree-agent-aa270341 — root cause was identical to
   W2-B-1: the wizard never triggered an `Indexer.fullScan` on the
   freshly scaffolded repo, so `IndexerSnapshotPublisher` +
   `SourcesPublisher` (both Room-backed) had zero `recurrence_rules`
   / `events` rows to read. The Day view rendered "no events in
   range" because the renderer's input flow was empty. AVD-verified
   after the fix: post-wizard schedule shows Sleep, Morning routine,
   Evening wind-down, Spring bank holiday and the cal-briefings
   recurrences on first paint. Supersedence wiring in
   `WizardBaseLayersScaffolder` is correct as-shipped — holidays
   only suppress on YEARLY-recurrence dates, vacation has zero
   recurrences (suppressor with no intervals = no suppression),
   per `OverlayResolver.layer`. Same regression test as W2-B-1
   covers the rule round-trip.*

3. **W2-U-2 — Repositories overlay's only entry-point label is `+`
   icon (a11y label "Set up a new account") at the top-right.
   First-time users in the Empty-mode flow have no obvious CTA to
   discover the wizard.** The Settings → Repositories sub-screen
   hint says "Run the lifestyle wizard from the bat avatar" but the
   bat avatar opens the Repos overlay, which only surfaces "+ Add
   repo" (the bare local-only path) prominently — the wizard
   entry-point is a small icon up top. Suggest: when the overlay is
   empty, show "Set up with the wizard" as a primary button next
   to "Add repo". Screenshot: `11-overlay.png`.

### MISSING-FEATURE (0)

(See "Wizard enrichment" section below for what was actively added
this round.)

---

## Wizard enrichment — code changes (W2-CODE)

The main scope of this round per the user's explicit ask: "make sure
the WIZARDS defaults create us a similar schedule to the demo data
by default and we don't have to do too much."

### Before this round

`WizardScaffolder.materialize()` produced:
- N role-based calendars (one per `RoleId` the user toggled, ≥ 1 always).
- ~6–10 daily-FREQ recurrences per role (one per visible atom).
- 1 onboarding todolist with 5 standing tasks.
- 1 system `cal-briefings` calendar (+ 2 briefing recurrences).
- ZERO base layer, ZERO holidays, ZERO vacation, ZERO sample events.

### After this round

New module: `app/src/main/java/com/eight87/strictlykeptboy/ui/wizard/WizardBaseLayersScaffolder.kt`.
Called from `WizardScaffolder.materialize()` immediately after the
onboarding-task step + before identity/mode writes + git commit.

Adds per-wizard-finish:

- **Base layer** calendar: `kind = "base"`, name `"Sleep & work hours"`,
  priority 100, `non_superseable = true`. Three recurrences: sleep
  (23:30 → 06:30 daily, 7h), morning routine (06:30 → 07:15 daily,
  45min), evening wind-down (22:00 → 23:30 daily, 1h30m). All
  passive-style daily anchors; tagged `["base", "<slug>"]`.
- **Public holidays** calendar: `kind = "base"`, priority 950,
  `non_superseable = true`, `supersedes = [<every role calendar id>]`.
  Four sample yearly recurrences: New Year's Day, Christmas Day,
  Boxing Day, Spring bank holiday (last Monday of May, BYDAY=-1MO).
  All authored as P1D-at-midnight; future country-code variants
  Phase deferred.
- **Vacation** calendar: `kind = "base"`, priority 900,
  `supersedes = [<every role calendar id>]`, no recurrences. Empty
  by design — user drops trip events into it.
- **5 sample standing tasks** in the onboarding todolist:
  "Pick a sticker species you actually want", "Set your tone
  register…", "Edit one of the role calendars…", "Open the schedule
  on a typical weekday + adjust", "Optional — connect a git remote…".
- **1 sample dated task** (`today + 2`): "Quick win — review
  tomorrow's schedule".
- **1 sample one-off event** (`today + 3`, 11:00 → 11:45 local):
  "Grocery run".

All idempotent on `name` field sentinel scan — re-running the
scaffolder against an existing repo is a no-op for already-present
entities. All files written via `EntityWriter` (no new persistence
surface) and round-trip through `RecurrenceRule.fromDoc` / `Event.fromDoc` /
`StandingTask.fromDoc` / `Task.fromDoc` + `SupersedenceConfig.read`.

### Density numbers (AVD-verified after wizard finish)

| Artefact                           | Before round | After round | Demo |
|------------------------------------|--------------|-------------|------|
| Calendars                          | N roles + briefings (e.g. 2) | N roles + 3 base + briefings (e.g. 5) | 12+ |
| Recurrences                        | ~6–10        | 16          | 72   |
| One-off events                     | 0            | 1           | 28   |
| Standing/dated tasks               | 5            | 11          | 33   |
| `kind = base` layers               | 0            | 3           | 3    |
| Supersedence wiring                | none         | holidays + vacation → roles | full |
| Non-superseable                    | none         | base + holidays | base + cat-care + kinky-rituals + holidays |

Closes ~2/3 of the gap vs. the demo. Remaining gap is per-role
density (the demo has many more per-role recurrences and specific
one-off events that are inherently personal — those stay
user-authored).

### Code locations

- `app/src/main/java/com/eight87/strictlykeptboy/ui/wizard/WizardBaseLayersScaffolder.kt`
  (new, ~360 LOC, single concern: base-layer enrichment).
- `app/src/main/java/com/eight87/strictlykeptboy/ui/wizard/WizardScaffolder.kt`
  (modified: +1 call to base-layers, +1 outcome field, commit
  message now reflects total recurrences).
- `app/src/test/java/com/eight87/strictlykeptboy/ui/wizard/WizardBaseLayersScaffolderTest.kt`
  (new, 6 tests).
- `app/src/test/java/com/eight87/strictlykeptboy/ui/wizard/WizardScaffolderTest.kt`
  (modified: 1 assertion loosened from `== 5` to `>= 5`).

### Test count

- 6 new tests in `WizardBaseLayersScaffolderTest` covering:
  - Demo-comparable density (≥ 10 recurrences, ≥ 3 sample tasks,
    ≥ 1 sample event).
  - Base calendar is `kind = base` + `non_superseable = true`
    (round-trips through `SupersedenceConfig.read`).
  - Holidays calendar's `supersedes` list equals the wizard's role
    calendar IDs (round-trips through `SupersedenceConfig.read`).
  - Vacation calendar is `kind = base`, priority 900, `supersedes`
    populated, parses through `CalendarActivityConfig.readFrom`.
  - Base layer recurrences include the sleep block.
  - Re-running `materialize` against an existing repo is idempotent
    (0 new recurrences, 0 new tasks, 0 new events).
- Full test suite green: existing 1017 + new 6 = **1023 tests
  passing**.

---

## Disk landing proofs (AVD `adb shell`)

### Wizard-finish repo dir layout (truncated to top 40 entries)

```
files
files/strictlykeptboy
files/strictlykeptboy/my-calendar-8ebb287a
files/strictlykeptboy/my-calendar-8ebb287a/.strictlykeptboy
files/strictlykeptboy/my-calendar-8ebb287a/identities
files/strictlykeptboy/my-calendar-8ebb287a/calendars
files/strictlykeptboy/my-calendar-8ebb287a/calendars/019e5b20-178e-7de6-af87-effe7ccbeab7  ← role:self-care
files/.../calendars/019e5b20-178e-.../events
files/.../calendars/019e5b20-178e-.../recurrences
files/.../calendars/019e5b20-178e-.../exceptions
files/.../calendars/019e5b20-178e-.../deviations
files/.../calendars/019e5b20-17b0-70ae-9800-5c83a063d764  ← base "Sleep & work hours"
files/.../calendars/019e5b20-17b1-7020-b7cd-b405c2f78b0b  ← base "Public holidays"
files/.../calendars/019e5b20-17b2-76e4-9427-7376839b8bcc  ← base "Vacation"
files/.../calendars/cal-briefings
files/.../calendars/cal-briefings/recurrences
files/.../todolists/019e5b20-1798-7841-956b-4bae848c22dd
files/.../todolists/.../tasks
files/.../todolists/.../standing
files/.../todolists/.../recurrences
files/.../journal | feedback | reviews | attachments | stickers | .git
```

### Counts

```
$ find ... -name '*.md' | wc -l           → 32 entity files
$ find ... -name '*.md' -path '*/recurrences/*' | wc -l  → 16
$ find ... -name '*.md' -path '*/standing/*' | wc -l + dated tasks → 11
$ find ... -name '*.md' -path '*/events/*' | wc -l       → 1
```

### Git HEAD

```
$ cat .git/refs/heads/main
573d9e78d3576ec3969b74d91d50298b6805218b

$ cat .git/logs/HEAD
0000000000000000000000000000000000000000  573d9e78  root <root@localhost>
  1779645290 +0200  commit (initial):
  wizard: scaffold lifestyle (submissive/single-strict, 1 roles, 14 recurrences)
```

(Note: the commit-message counter was fixed mid-round to include
base-layer recurrences; before the fix it reported only the per-role
count.)

### Sample on-disk files

`calendars/<base-uuid>/calendar.toml`:
```toml
schema_version = 1
id = "019e5b20-17b0-70ae-9800-5c83a063d764"
kind = "base"
name = "Sleep & work hours"
emoji = "🟦"
color_seed = 4886722
priority = 100
tz_id = "Arctic/Longyearbyen"
created_at = 2026-05-24T19:54:50+02:00
updated_at = 2026-05-24T19:54:50+02:00
author = "019e5b20-1789-7e4a-8d7f-3f19e8f7d253"
non_superseable = true
```

`calendars/<base-uuid>/recurrences/<rule-uuid>.md` (sleep block):
```
+++
schema_version = 1
id = "019e5b20-17b3-7d7e-9e39-93a7e98a5e9a"
created_at = 2026-05-24T19:54:50+02:00
updated_at = 2026-05-24T19:54:50+02:00
author = "019e5b20-1789-7e4a-8d7f-3f19e8f7d253"
kind = "recurrence"
title = "Sleep"
dtstart = 2025-01-01T23:30:00
duration = "PT7H"
tz_id = "Arctic/Longyearbyen"
rrule = "FREQ=DAILY"
calendar_id = "019e5b20-17b0-70ae-9800-5c83a063d764"
tags = ["base", "sleep-block"]
emoji = "😴"
+++
Sleep block — 23:30 → 06:30. Anchors the day.
```

`calendars/<self-care-uuid>/events/2026/05/<event-uuid>.md` (sample event):
```
+++
schema_version = 1
id = "019e5b20-17c1-7ad6-b260-e28b081b0dfe"
...
kind = "event"
title = "Grocery run"
start = 2026-05-27T11:00+02:00
end = 2026-05-27T11:45+02:00
calendar_id = "019e5b20-178e-7de6-af87-effe7ccbeab7"
tags = ["wizard-sample"]
emoji = "🛒"
+++
Sample event seeded by the wizard. Tap to edit or delete.
```

---

## Phase walks

### Phase A — Empty → discover CTA

`pm clear` → Continue → "Empty calendar" → Confirm → lands on
"Schedule / No events in range." Settings gear opens Settings;
"Repositories" sub-screen hint "Run the lifestyle wizard from the
bat avatar" points at the bottom-left avatar (`Switch repository`
content-desc). Avatar opens the Repos overlay with Demo-mode
toggle + "+ Add repo" button. The wizard entry is the small `+`
top-right ("Set up a new account") — easy to miss. See **W2-U-2**.
Screenshots: `01-intro.png`, `02-pick.png`, `07-schedule-fresh.png`,
`11-overlay.png`.

### Phase D — Wizard finish + on-disk verification

Walked the full wizard (10 steps, "Continue" each step + "Open my
calendar" on the handoff). Time-to-finish about 30 seconds with
defaults. On-disk:
- `my-calendar-<8hex>/` repo dir landed under
  `files/strictlykeptboy/`.
- 1 role calendar (`Self-Care`) + 3 base calendars (base / holidays
  / vacation) + 1 system briefings calendar.
- 16 recurrences total (7 role atoms + 3 base + 4 holidays + 2
  briefings).
- 11 standing/dated tasks.
- 1 sample event ("Grocery run").
- Single initial git commit.

Screenshots: `12-wizard-start.png`, `13-wizard-step.png`,
`14-after-wizard.png`.

**B-2 from round 1 (wizard does NOT create a new repo) is FIXED**
— confirmed on this round. The new repo lands as the write target
and the demo flips off (round 1 wave-2 W2.2 fix shipped pre-c32d54b
already; re-confirmed here).

---

## What was intentionally NOT covered

Per the original walkthrough-2 prompt scope, the following were
either out-of-budget for this single subagent run or downstream of
the W2-U-1 / W2-B-1 findings above:

- **Phase B — Create-local-only + remote-connection paths** beyond
  the local-only entry-point. The OAuth Device Flow modal + manual
  PAT + Forgejo + "Other" host walks need a deterministic AVD
  session; deferred.
- **Phase E — per-toggle audit** (show-on-schedule, show-in-tasks,
  auto-sync WorkManager job count, wi-fi-only persistence,
  import-stickers-into-repo, adjust-to-local-timezone) — each is a
  fully-instrumented test on its own. Round 1 hit some of these
  partially; the remaining 5 toggles need a dedicated round.
- **Phase F — More-settings inside the repo card.** Pending.
- **Phase G — Avatar/identity edit round-trip.** Identity-toml
  write-back was source-confirmed in round 1's completion phase
  (and noted blocked behind demo-read-only); needs a fresh AVD run
  against a wizard-produced repo to verify. The wizard now produces
  one — pull this forward next round.

The remaining surface is documented in
`docs/plans/walkthrough-2026-05-24-results.md` (round 1) under
"What's intentionally NOT in this round" + the "Round summary" of
the completion phase.

---

## Round summary

- **Phases run:** 2 (Phase A — empty-mode discoverability + Phase D —
  full wizard end-to-end with on-disk verification).
- **Biggest 3 blockers found:**
  1. W2-B-1 — wizard standing tasks don't surface in the Tasks
     pane (pre-existing R-5 shape from round 1).
  2. W2-U-1 — schedule Day view shows empty-state on wizard-
     scaffolded repo despite 16 recurrences on disk (5-badge on
     filter FAB suggests overlay/superseded pass).
  3. W2-U-2 — wizard entry-point is a `+` icon top-right of the
     Repos overlay; first-time users won't find it.
- **What changed in the wizard:** Added
  `WizardBaseLayersScaffolder` which writes 3 base-layer calendars
  (sleep/work scaffold, public holidays, vacation) with proper
  `non_superseable` + `supersedes` wiring, 7 additional
  recurrences (3 base + 4 sample holidays), 5 sample standing
  tasks + 1 dated task + 1 sample one-off event. Idempotent re-runs.
- **New tests:** 6 (in `WizardBaseLayersScaffolderTest`).
- **New commits:** 0 to-be-made by the parent agent (subagent is
  not authorized to commit; the parent collects the worktree
  changes).

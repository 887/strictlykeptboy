# QA iteration 4 — backlog close-out + walk replay

Date: 2026-06-07. Target: `medium_phone` AVD (API 36). Built on iter-3
(`7859807`) + handoff brief (`f8ca558`). Picks up the three logged open items
then replays the 5 user walks, treating every ⚠ / mismatch as a defect.

Standing loop (per global CLAUDE.md + handoff §0): AVD-drive → log → fix →
re-verify on AVD → commit. A fix is done only when a clean re-verification
round finds nothing new.

## Phase A — D.78 S4 resolver enforcement (handoff §2a.2) — shipped in commit (pending)

`non_superseable` is parsed (`SupersedenceConfig`) but never reaches the
resolver, so a non_superseable calendar is preserved only by *not* being listed
in a `supersedes` array — the discipline that broke once (the cat bug, iter-3).
Enforce the invariant in code so the bug class can't recur.

- [x] **A.1** Add `nonSuperseable: Boolean = false` to `resolver/Calendars.kt::CalendarMeta`.
- [x] **A.2** Populate it at the production build sites that read supersedence:
  `composition/CalendarRegistry.kt` + `ui/repos/RepoSettingsHost.kt`.
  (`IndexerSnapshotPublisher` defers richer TOML — keeps the `false` default.)
- [x] **A.3** `resolver/OverlayResolver.kt`: filter non_superseable refs out of
  every suppressor's effective `supersedes` set before building `suppressedByMap`.
- [x] **A.4** Populate `nonSuperseable` in `demo/RichDemoResolverFixture.kt`
  (+ `nonSuperseable` param on the `cal` test factory).
- [x] **A.5** Regression test `nonSuperseableCalendar_ignoresSupersedeRequest`:
  suppressor explicitly lists a non_superseable cal → asserted never tagged
  `supersededByCalendar`.
- [x] **A.6** Full unit suite green (was 0 red, stays 0); AVD smoke — schedule
  renders clean, no crash, FAB cluster intact.

SOLID self-check (code-only, <200 LOC): SRP pass (flag lives on the value type,
enforcement in the one resolver chokepoint); OCP pass (additive field, default
preserves existing behaviour); LSP pass (this *closes* a Liskov-spirit gap —
every suppressor now honours the non_superseable contract); ISP/DIP n/a (pure
data + resolver). No god-module growth.

## Phase B — Chip-row DRY + B.4 timezone display (handoff §2a.3) — shipped in commit (pending)

- [x] **B.1** Extracted shared `ui/components/WrappingChipRow.kt`; routed **all**
  inline `FlowRow` chip groups through it + deleted the wizard's private
  `WrappingChipRow`. Migrated: Sync(×3), Identity(×2), Mode(×2), Appearance(×1),
  Notifications(×2), EventCreateFreeFormForm(×2), EventDetailSheet(×1),
  TaskDetailSheet(×1), TaskQuickAddFab(×1), TogetherInputForm(×2), wizard
  Identity/Templates screens. Unused `FlowRow`/`Arrangement` imports + the
  `@file:OptIn(ExperimentalLayoutApi)` removed where the file no longer needs
  them. AVD-verified: Look&Feel density chips wrap whole-chip ("Spacious" → line 2).
- [x] **B.2** Multi-tz display (B.4): event tz shown ONLY when it differs from the
  device zone; the redundant raw-IANA-id-next-to-city
  ("Arctic/Longyearbyen · Longyearbyen") collapses to the city. Pure
  `zoneSuffixLabel` + device-check `friendlyZoneSuffix`, unit-tested by
  `EventDetailZoneLabelTest` (7 cases incl. the exact reported bug input).
  AVD-verified: "Collar on (home)" time line reads "Sun Jun 7 17:45 – 23:30",
  no redundant zone string.
- [x] **B.3** Full unit suite green (+7 new tz cases); AVD-smoked settings chips +
  event-detail tz line.

SOLID self-check (B): SRP pass (WrappingChipRow = one wrapping concern;
zoneSuffixLabel = one formatting concern); OCP pass (callers pass spacing
overrides, no edits to the component for new groups); ISP/DIP pass (pure
presentation, no state coupling); no god-modules — this *removed* ~60 LOC of
duplication. English-only identifiers.

## Phase C — CalDAV decision + action (handoff §2a.1)

- [ ] **C.1** Audit the current CalDAV surface + the README claim; decide build
  vs soften (default: build, staged as its own plan).
- [ ] **C.2** Execute the decision.

## Phase D — Replay the 5 user walks (handoff §2b)

- [ ] **D.1** us-adhd-freelancer (3 walks)
- [ ] **D.2** au-netball-coordinator (3 walks)
- [ ] **D.3** uk-nhs-nurse (3 walks) — flag CalDAV-dependent steps
- [ ] **D.4** de-privacy-engineer (3 walks) — SSH `NotImplementedError` may surface
- [ ] **D.5** ch-paired-couple (3 walks)

## Status: 🚧 IN PROGRESS

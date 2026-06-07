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

## Phase B — Chip-row DRY + B.4 timezone display (handoff §2a.3)

- [ ] **B.1** Extract one shared `ui/components/WrappingChipRow.kt`; route the ~7
  inline `FlowRow` chip groups + the wizard's private `WrappingChipRow` through it.
- [ ] **B.2** B.4 multi-tz display: stop showing IANA id + friendly city
  redundantly ("Arctic/Longyearbyen · Longyearbyen"); show the friendly form,
  fall back to IANA only when it carries extra info, and only when the event tz
  differs from the device tz.
- [ ] **B.3** Unit green + AVD smoke (event-detail tz line, chip groups wrap).

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

# QA iteration 4 — backlog close-out + walk replay

Date: 2026-06-07. Target: `medium_phone` AVD (API 36). Built on iter-3
(`7859807`) + handoff brief (`f8ca558`). Picks up the three logged open items
then replays the 5 user walks, treating every ⚠ / mismatch as a defect.

Standing loop (per global CLAUDE.md + handoff §0): AVD-drive → log → fix →
re-verify on AVD → commit. A fix is done only when a clean re-verification
round finds nothing new.

## Phase A — D.78 S4 resolver enforcement (handoff §2a.2) — shipped in commit 7394785

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

## Phase B — Chip-row DRY + B.4 timezone display (handoff §2a.3) — shipped in commit 5137087

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

## Phase C — CalDAV decision + action (handoff §2a.1) — shipped in commit f2a633b

- [x] **C.1** Audited. Finding: the CalDAV mirror engine is **already built** —
  ~1665 LOC (`caldav/`: PROPFIND discovery, OkHttp client, iCal codec, ETag-
  journaled pull/push sync worker, scheduler, encrypted stores, Add-Mirror
  wizard) + ~510 LOC tests (incl. a MockWebServer worker test). It was
  **orphaned**: never reached from the shell ("coming soon" disabled stub), and
  the README overclaimed all 5 providers as v1. So this is not build-vs-soften —
  it's *wire the existing tested code + make the README honest*.
- [x] **C.2** Executed:
  - `CalDavCategory` rewritten to host the real `AddMirrorScreen` wizard + list
    configured mirrors; `reposFlow` wired at the SettingsPane call site. Stores
    opened defensively (`runCatching`) so Robolectric / keystore-less devices
    degrade gracefully instead of crashing (fixed the `KeyStoreException`
    regression that surfaced 2 nav tests — re-verified green).
  - Strings de-stubbed (no "coming soon"); honest intro + mode labels added.
  - README reconciled: sync engine implemented+tested, mirror setup live for
    password/app-password servers; OAuth (Google/M365) + boot-scheduling in
    progress (DAVx⁵ = zero-config path, already bridged).
  - Remaining work staged in `docs/plans/caldav-wiring.md` (Phase B scheduler
    boot-wire + Sync-now; Phase C OAuth; Phase D i18n/management).
  - **AVD-verified**: Settings → CalDAV renders the wired surface; "Add a mirror"
    opens the live provider picker (Google/M365/iCloud/Nextcloud/Custom).

SOLID self-check (C): SRP pass (category presents+creates mirrors, engine
untouched); LSP pass (defensive store-open honours "renders in any parent" —
no crash on keystore-less hosts); DIP pass (category → unit-tested
`AddMirrorViewModel`, not concretes); no god-modules. Hardcoded English wizard
copy flagged as caldav-wiring D.1.

## Phase D — Replay the 5 user walks (handoff §2b) — shipped in commit 71af5d7

Method: a thorough code-audit mapped every walk's pass-criteria + ⚠ flags to the
implementation (15 scripts across 5 personas), and the high-signal flows were
AVD-verified directly. Two real defects surfaced + fixed; the two pre-flagged
risks (CalDAV, SSH) were already resolved/graceful.

- [x] **D.0 — Pre-flagged risks cleared.** SSH onboarding (de-privacy) is
  gracefully gated in the UI (`AddRepoNavHost` disabled radio + "Coming soon";
  `forSsh` throws by design — no crash). CalDAV (uk-nurse) wired in Phase C.
- [x] **D.1 — DEFECT (blocker) fixed: active-hours re-ranking.** us-adhd
  walk-phoneonly step 17 expects work-list tasks to outrank same-due tasks
  during work hours. The `effectivePriority` + `ACTIVE_HOURS_PRIORITY_BUMP`
  infra existed (used by `sortedForCombined`) but the newer `buildUnifiedToday`
  today-bucket sorted by due-time/title only. Added an `effectivePriority`-DESC
  tiebreak to the today-bucket sort + regression test
  `activeHours_list_outranks_inactive_list_in_today_bucket`.
- [x] **D.2 — DEFECT (major) fixed: manual sync not in the Repos pane.** Multiple
  walks expect a per-repo sync affordance + a no-origin "nothing to sync" cue.
  Manual sync existed only in the avatar dropdown; `ReposList` suppressed
  `onSyncRepo`. Wired `RepoCard` to surface "Sync now" (remote repos, → real
  `SyncService.startSyncRepo`) and a "Local-only — nothing to sync." hint
  (no-origin). **AVD-verified**: "my calendar" (local-only) shows the hint.
- [x] **D.3 — Audit result: 10/15 walks satisfy their pass-criteria** in code
  (no-origin repo + badge, RRULE+exception, author chips, .ics import, priority-
  collision rendering, German i18n, strictly-kept review-feed + deviations,
  common-time finder, nonSuperseable survival, force-show override). Remaining
  deferrals (OAuth CalDAV providers, SSH transport) are tracked, not broken.

SOLID self-check (D): SRP/OCP pass (re-rank reuses existing `effectivePriority`;
sync button reuses existing `onSyncRepo`→`SyncService` plumbing, no new path);
DIP pass; no god-modules. Both fixes are additive + regression-tested.

## Status: ✅ DONE (3 logged items + 2 walk-audit defects fixed; suite green; AVD-verified; shipped)

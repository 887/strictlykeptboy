# Round 2.24 — Phase AA: Multi-timezone first-class

## Status: DRAFT

## Context

Phase AA from `docs/plans/main.md` (lines 607–617) — reclassified from
"Round 4 — later" to current as Round 2.24. Seven substeps:

- **AA.1** Per-event `tz_id` field (additive, optional, non-breaking)
- **AA.2** Repo default tz in `repo.toml`
- **AA.3** Display-tz toggle in top bar; per-event "pin to event tz" flag
- **AA.4** Multi-tz common-time finder
- **AA.5** DST edge-case test corpus
- **AA.6** `skb tz convert <event-id> <new-tz>` CLI subcommand
- **AA.7** Participant-tz declaration in Together-tab common-time UI

Deep-dives referenced: `data-model.md` DM-L, `resolver.md` RV-H + RV-I,
`ui-spec.md` UI-V.

Who benefits:

- **Travelers** — fly NYC → Berlin, calendar still shows NYC-pinned
  meetings in their actual NYC local time, surrounded by Berlin events.
- **Sub/dom across timezones** — a dom in Berlin issuing a scene at
  20:00 Berlin time to a sub in NYC sees the same event ground-truth
  in both viewers' local frames without mental arithmetic.
- **Conference attendees** — booking sessions in the conference tz
  while local events keep their home-tz pins.
- **Shift workers** — fixed UTC-clock shifts that survive DST changes
  in the local tz.

Today `RecurrenceRule` carries `tz_id` (load-bearing for RRULE
expansion) but `Event` does not — single-instance events implicitly
use `ZoneId.systemDefault()` at render time via `Renderer.renderTz`.
There is no repo-default tz; the renderer's `renderTz` parameter is
the only tz signal, and it has no UI exposure. The Together-tab
common-time finder takes a single `tzId` for the whole query — every
participant is assumed in the same zone.

## Locked design decisions

- **D-2.24.a** — Per-event `tz_id` is **optional and additive**. When
  unset, the event resolves in the repo-default tz at render time.
  When set, the event is "pinned" to its own tz: its
  `start`/`end` instants are interpreted in that zone, then converted
  to the display tz on render. Existing event files round-trip
  unchanged (no `tz_id` written for unpinned events).
- **D-2.24.b** — Repo-default tz lives in
  `<repoRoot>/.strictlykeptboy/repo.toml` as
  `default_tz_id = "Europe/Berlin"`. Absent → fall back to
  `ZoneId.systemDefault()`. Read via the existing TOML reader pattern
  in `RichDemoRegistrar.readRepoMeta` (and a new shared helper to
  avoid the demo registrar being the canonical reader).
- **D-2.24.c** — Display-tz toggle: a chip in the Schedule top-bar
  cycles between "System ($id)" / "Repo default ($id)" / explicit
  picker. Persisted in `CalendarVisibilityPrefs` (closest existing
  prefs class, same logic as Round 2.23's `globalZoomOverride` —
  adding a new prefs file would be ceremony) as
  `displayTzId: String?`. `null` = follow system.
- **D-2.24.d** — Per-event "pin to event tz" flag = the bare presence
  of `tz_id` on the event. No second boolean. Removing the pin =
  delete the field from the TOML.
- **D-2.24.e** — Together-tab common-time finder gets a participant-tz
  column. Each participant entry carries `tz_id`; the finder evaluates
  busy windows in each participant's local tz, intersects in UTC, and
  returns the result in the **viewer's tz** (the query's `tzId`,
  which already exists in `CommonTimeFinder.Query`).
- **D-2.24.f** — `skb tz convert <event-id> <new-tz>` rewrites the
  file with the new `tz_id` AND shifts `start`/`end` instants to
  match. Default behaviour preserves the LOCAL clock time (09:00 in
  old tz → 09:00 in new tz). `--shift-instant` instead preserves the
  absolute UTC instant (09:00 NYC → 15:00 Berlin). Recurring rules
  convert via the rule's `dtstart`.

DST policy decisions (provisional, finalised in Phase E):

- **D-2.24.g (provisional)** — Spring-forward (non-existent local
  time): snap to the next valid instant (`ZonedDateTime.of(...)` with
  `ZoneRules.getValidOffsets()` empty → use the gap's `after()`
  offset, i.e. 02:30 spring → 03:30 wall in CET). Documented + noted
  on the event when this triggers via a `tz_dst_snap` audit field
  (additive, optional).
- **D-2.24.h (provisional)** — Fall-back (duplicated local time):
  prefer the EARLIER occurrence (the pre-transition offset). Java's
  `ZonedDateTime.ofLocal` already does this by default when no
  `preferredOffset` is supplied; we keep the default.

## Phases

### Phase A — Data model: per-event tz_id + repo default_tz_id (AA.1, AA.2) — shipped in commit c1dcac7

- [x] **A.1** Audit `Event` data class: confirm absence of `tz_id`
      (today only `RecurrenceRule` carries it). Add nullable
      `tzId: String?` to `Event` with default `null`; round-trip via
      `toDoc`/`fromDoc` (omit the key when null; write
      `tz_id = "..."` when present).
- [x] **A.2** Extend `Entities.kt` `EventTzRoundTripTest` (new) to
      cover the three cases: (a) round-trips no `tz_id` cleanly, (b)
      round-trips `tz_id = "America/New_York"`, (c) preserves
      unknown/malformed zone strings verbatim (codec does not
      validate; resolver in Phase B handles fallback per D-2.24.a).
- [x] **A.3** Add `defaultTzId: String?` to the in-memory `RepoMeta`
      reader. Extracted `readRepoMeta(repoRoot)` from
      `RichDemoRegistrar` into a shared helper at
      `store/RepoMetaReader.kt` (one method `read(repoRoot: File):
      RepoMetaSnapshot?`). Existing demo registrar now uses the helper.
- [x] **A.4** Tests: `RepoConfigDefaultTzTest` — reads repo.toml with
      `default_tz_id`, confirms exposure; absent → null; missing
      repo.toml → null (no throw); blank → null.
- [x] **A.5** Cache DB schema: confirmed Room schema does NOT store
      per-event tz (`EventRow` has no `tzId`; only `RecurrenceRuleRow`
      carries one, which is correct per RFC5545). No migration needed.

### Phase B — Resolver display conversion — shipped in commit 46bf374

- [x] **B.1** New `resolver/TzResolver.kt`: pure top-level function
      ```
      effectiveDisplayTz(
        eventTz: ZoneId?,        // null = unpinned
        repoDefault: ZoneId?,    // null = repo.toml omitted default_tz_id
        displayOverride: ZoneId?,// null = follow system
        systemDefault: ZoneId,
      ): ZoneId
      ```
      Precedence: `displayOverride ?: repoDefault ?: systemDefault`
      for the **display** side. `eventTz ?: repoDefault ?:
      systemDefault` for the **source** side. Display conversion =
      `instantAtSource.atZone(displaySide)`.
- [x] **B.2** Extend `MaterializedInstance` with `sourceTzId:
      String?` carrying the event's `tz_id` (rule's `tz_id` for
      recurring instances). `effectiveStart`/`effectiveEnd` remain
      `ZonedDateTime` but now the source zone is preserved so the UI
      can decide whether to badge.
- [x] **B.3** `Renderer.render` accepts a new optional
      `displayTz: ZoneId? = null` parameter (overrides `renderTz`).
      Each one-off + rule materialisation re-zones to the resolved
      display tz. `RecurrenceMaterializer` already produces
      `ZonedDateTime` in the rule's tz — keep that as source, convert
      at render lip.
- [x] **B.4** Tests:
      - `TzResolverTest` — full matrix of (eventTz × repoDefault ×
        displayOverride) → expected effective tz.
      - `RendererTzConversionTest` — event with
        `tz_id = "America/New_York"` and `start = 09:00` renders at
        15:00 in a Berlin display.
      - `RecurrenceTzConversionTest` — rule with NY tz materialised
        across spring DST in Berlin display correctly shifts by 4h
        (post-DST) vs 5h (pre-DST).

### Phase C — Display-tz toggle UI (AA.3)

- [ ] **C.1** `CalendarVisibilityPrefs`: add `displayTzId: String?`
      to `VisibilityState`, with `setDisplayTzId(zoneId: String?)`
      mirroring `setGlobalZoomOverride`'s shape.
- [ ] **C.2** New `ui/schedule/DisplayTzChip.kt` — small
      `AssistChip` (or `InputChip`) reading "tz: $shortLabel" placed
      adjacent to `ZoomLevelRow` in the Day/3-day chrome. Tap →
      `ModalBottomSheet` with three quick options + a zone search
      `OutlinedTextField` filtering `ZoneId.getAvailableZoneIds()`.
- [ ] **C.3** Pinned-tz event badge: in band rendering (Day / Week /
      3-day), when `sourceTzId != null` AND
      `ZoneId.of(sourceTzId) != effectiveDisplayTz`, prefix a small
      "✈ <shortLabel>" icon-text inside the band. Short label =
      `ZoneId.of(...).id.substringAfterLast('/')` with `_` →` `.
- [ ] **C.4** Tests:
      - `DisplayTzChipTest` — chip renders, sheet opens, system /
        repo / custom selection routes through the writer.
      - `BandTzBadgeTest` — NY-pinned event in a Berlin display shows
        "✈ New York" badge; same-zone event omits the badge.

### Phase D — Together multi-tz common-time finder (AA.4, AA.7)

- [ ] **D.1** Extend `CommonTimeFinder.Query` with `participantTz:
      Map<RepoRef, ZoneId>` (default empty → all participants in
      query's `tzId`).
- [ ] **D.2** `CommonTimeFinder` algorithm change: convert each
      participant's busy windows to UTC using its declared tz,
      intersect in UTC, return result `ZonedDateTime`s in the query's
      viewer tz. Day-of-week + time-of-day window per-participant
      (current behaviour evaluates window in query tz; preserve that
      for the viewer-facing result, but participant busy comes from
      participant tz).
- [ ] **D.3** `TogetherInputState` + `TogetherInputForm`: add
      `participantTz: Map<String, ZoneId>` (repoId → tz) with a per-
      participant tz dropdown in each repo row. Default per
      participant = repo-default tz (read via `RepoMetaReader`); fall
      back to viewer tz.
- [ ] **D.4** Wire `TogetherViewModel` to feed `participantTz` into
      the finder port.
- [ ] **D.5** Tests:
      - `MultiTzCommonTimeTest` — sub in Berlin (busy 09:00–18:00
        local), dom in NYC (busy 09:00–17:00 local), 1h overlap on a
        Saturday: 18:00–19:00 Berlin / 12:00–13:00 NYC. Viewer in
        Berlin sees `18:00–19:00 Europe/Berlin`. Viewer in NYC sees
        `12:00–13:00 America/New_York`.
      - `TogetherInputFormTzPickerTest` — participant tz dropdown
        renders and updates state.

### Phase E — DST edge-case test corpus (AA.5) — shipped in commit 6072b62

- [x] **E.1** New `DstEdgeCaseTest.kt` (resolver tests):
      - **Spring-forward — non-existent local time.** Event at
        02:30 on `2026-03-29` in `Europe/Berlin`: asserts
        materialisation snaps to `03:30 CEST` per D-2.24.g; audit
        field `tz_dst_snap = "spring_forward"` set on the
        `MaterializedInstance`.
      - **Fall-back — duplicated local time.** Event at `02:30`
        on `2026-10-25` in `Europe/Berlin`: asserts the earlier
        occurrence (CEST `+02:00`) per D-2.24.h.
      - **Cross-tz event spans midnight transition.** Event
        `start=2026-03-28T23:30` NY tz, `end=2026-03-29T01:30` NY tz
        renders in Berlin display as a band crossing local 05:30 →
        06:30 with no missing minutes.
      - **RRULE across spring DST.** Daily rule `tz_id =
        "America/New_York"` from `2026-03-07` to `2026-03-14`
        (US DST = `2026-03-08`): all 8 instances materialise; UTC
        offset changes from `-05:00` to `-04:00` on day 2 onward.
- [x] **E.2** Document the spring/fall policy choice inline in the
      test file (header KDoc references D-2.24.g/h). Also added a
      policy header KDoc in `resolver/TzResolver.kt` so source-side
      readers see the lock without bouncing to the test corpus.
- [x] **E.3** `decisions.md` D.121 promotes D-2.24.g + D-2.24.h from
      "provisional" to "locked" once Phase E passes.

### Phase F — `skb tz convert` CLI (AA.6) — shipped in commit fd6dd0e

- [x] **F.1** New file
      `cli/src/main/kotlin/com/eight87/skb/cli/commands/TzCommands.kt`
      registering `skb tz convert <event-id> <new-tz>
      [--shift-instant] [--repo <root>]`. Wired into `Main.kt`
      dispatch.
- [x] **F.2** Locate the entity file via existing
      `RepoDiscovery` + an id→path lookup (walk events +
      recurrences buckets across every calendar); read via
      `MiniToml`; rewrite via existing `AtomicWriter` + commit via
      `GitCommitter`. Commit message auto-format:
      `tz: convert "<title>" → <new-tz> (<local|instant>)`.
- [x] **F.3** Conversion semantics:
      - Default (no `--shift-instant`): parse `start` + `end` as
        local-clock in old tz, re-emit as same local-clock in new
        tz. `tz_id = <new-tz>` written.
      - `--shift-instant`: parse `start` + `end` as instants in old
        tz, re-emit as the same instant rendered in new tz.
      - Recurring rules: same logic against `dtstart` (bare
        local-datetime form preserved); `rrule` body untouched.
- [x] **F.4** Tests in `cli/src/test/`:
      `TzConvertCommandTest` covering both branches (one-off event +
      recurring rule) and the error cases (unknown event-id, invalid
      zone). Verify the resulting file decodes via `MiniToml` round-
      trip.

### Phase G — Close-out

- [ ] **G.1** Tick all substeps with commit SHAs on each phase header.
- [ ] **G.2** `## Status: ✅ DONE` on this file.
- [ ] **G.3** Append D.120..D.125 to `decisions.md` (verify highest
      currently is D.119 from `a9e7cfb`):
      - D.120 — Per-event `tz_id` is optional + additive (D-2.24.a).
      - D.121 — Repo default tz via `default_tz_id` in repo.toml
        (D-2.24.b).
      - D.122 — Display-tz toggle (D-2.24.c) + pin = presence of
        `tz_id` (D-2.24.d).
      - D.123 — Multi-tz common-time semantics (D-2.24.e).
      - D.124 — `skb tz convert` semantics (D-2.24.f).
      - D.125 — DST policy: spring-forward snap-forward, fall-back
        prefer-earlier (D-2.24.g + D-2.24.h).
- [ ] **G.4** Add Round 2.24 entry to `main.md` (under the Phase AA
      block, tick AA.1..AA.7 with commit SHAs; under Round 2.x
      header, add the round entry referencing this plan file).
- [ ] **G.5** AVD smoke evidence in `docs/qa/2-24/`:
      - `display-tz-chip.png` — chip rendered top of Day view.
      - `pinned-event-badge.png` — NY-pinned event shows "✈ New York"
        on a Berlin display.
      - `together-participant-tz.png` — Together-tab participant
        rows each showing a tz dropdown.
- [ ] **G.6** **DO NOT trigger release** — user will pull when ready.

## SOLID self-check (ongoing — re-evaluate per phase)

- **S** — `TzResolver.kt` is one pure function file (one reason to
  change: the tz resolution policy). `DisplayTzChip.kt` owns the
  picker UI only. `TzCommands.kt` owns the CLI surface only.
- **O** — `MaterializedInstance.sourceTzId` is a new additive field;
  no `when` chains change. Existing call sites that don't care about
  source tz continue to work.
- **L** — `Event.tzId` is nullable; both branches (null + present)
  honour the same "render in display tz" contract.
- **I** — `CommonTimeFinder.Query.participantTz` defaults empty so
  existing callers don't widen their interface. New helper functions
  take only what they need (`ZoneId`, not `RepoConfig`).
- **D** — `RepoMetaReader` is the new abstraction so `Renderer` /
  `TogetherViewModel` / `DisplayTzChip` don't depend on
  `RichDemoRegistrar`'s internals.

## Discipline checklist (CLAUDE.md)

- Build: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk
  ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug`
- Test: `JAVA_HOME=/usr/lib/jvm/java-26-openjdk
  ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:testDebugUnitTest`
- Existing 1126 stay green after each phase.
- One commit per phase; tick checkboxes in the same commit.
- AVD: `emulator-5558` only (per task brief override of the
  CLAUDE.md `emulator-5554` default for this round).
- Never modify `~/.claude/`.

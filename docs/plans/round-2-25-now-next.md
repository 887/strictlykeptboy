# Round 2.25 — Now / Next surface unification

## Status: ✅ DONE

## Context

User feedback (verbatim, 2026-05-17):

> i also don't like we're only showing "no active task" at the bottom..
> sure it's good to know that there is none right now, but we should
> show .. "brush your teeth in 2 hours and 15mins" or something like
> that in UI form like NEXT: "brush your teeth" on the right of the bar
> or something like it.. that would be much much more useful. also when
> we look at the notification of this app that's what i want to show.
> current task and next task. same on its widget

Three surfaces — (a) the bottom `NowPlayingSheetHost` peek row, (b)
the ongoing notification, (c) the homescreen / lockscreen widget(s) —
all need to render the same `Now: <title>` / `Next: <title> · in 2h
15m` pair. One pure resolver derives the snapshot; every surface is a
dumb consumer.

## Locked design decisions

- **D-2.25.a** — Single source of truth: pure
  `NowNextResolver.derive(today, at)` in `resolver/` package. UI /
  notification / widget all consume the same
  `Flow<NowNextSnapshot>` from `AppGraph.nowNextFlow`. No surface
  re-implements the "what's next" logic.
- **D-2.25.b** — "Next" defined as: earliest event in today's
  materialised list whose `start > now`. If today is empty after
  `now`, the resolver looks at tomorrow (so "now → tomorrow's first
  event" still renders something useful at 23:00).
- **D-2.25.c** — Bottom bar layout: left column = active task
  affordance (today's `now` event title, OR existing "Tap to pick
  one" stub when `now == null`); right column = `Next: <emoji>
  <title>` line 1 + `in 2h 15m` line 2. If `next == null`, the right
  column renders nothing — silence is fine.
- **D-2.25.d** — Notification: ongoing
  `NotificationCompat.Builder`, channel `now_next`, priority LOW,
  `setOnlyAlertOnce(true)`, `setOngoing(true)`,
  `category = CATEGORY_STATUS`. Title = `Now: <title>` (or
  `No active task`); body = `Next: <title> · in 2h 15m`. Tap →
  `MainActivity` with `FLAG_ACTIVITY_SINGLE_TOP`. Refreshed on
  indexer pulse, on `next.start` boundary alarm
  (`AlarmManager.setExactAndAllowWhileIdle`), and as a fallback by a
  15-min `PeriodicWorkRequest`.
- **D-2.25.e** — Widget: same Now/Next pair, two-line layout. Shared
  renderer `WidgetRenderer.bindNowNext(remoteViews, snapshot)`.
  Updates triggered by `nowNextFlow` change + the same
  `AlarmManager` boundary alarm used for the notification. Phase
  EEE/VV widget providers both consume.
- **D-2.25.f** — Time-relative copy refreshes locally every 60s
  without re-querying the snapshot (`next.start` is absolute; only
  rendering needs to tick).

## Phases

### Phase A — `NowNextResolver` + `AppGraph.nowNextFlow` — shipped in commit `415b055`

- [x] **A.1** New `resolver/NowNextResolver.kt`: pure `derive(today:
      List<MaterializedInstance>, at: Instant): NowNextSnapshot` +
      `formatRelative(d: Duration): String` helper.
- [x] **A.2** `AppGraph.nowNextFlow: StateFlow<NowNextSnapshot>`
      combining `briefingSource` (today + tomorrow at the cusp) with
      a 60-second ticker. Scope = `appScope`.
- [x] **A.3** `NowNextResolverTest` — 8 cases: empty, all-past,
      mid-event, no-current-no-next-today, next-is-tomorrow,
      next-is-grouped-band-start, next-within-grouped-band,
      multi-calendar ordering.

### Phase B — Bottom-bar `NowNextRow` — shipped in commit `5260036`

- [x] **B.1** Refactor the "No active task / Tap to pick one"
      rendering in `NowPlayingSheetHost.kt`. Left column reads
      `nowNextFlow.now`; right column renders `Next: ...` +
      `in 2h 15m` when `nowNextFlow.next != null`, nothing
      otherwise.
- [x] **B.2** 60s local re-render of `formatRelative(next.start -
      now)` without re-querying the snapshot.
- [x] **B.3** `NowNextRowTest` — Compose test: snapshot with both
      asserts both render; empty snapshot asserts legacy "No active
      task" copy.

### Phase C — Ongoing notification — shipped in commit `3836ebc`

- [x] **C.1** New `notif/NowNextNotificationProvider.kt`: builds the
      ongoing notification from a `NowNextSnapshot`. Channel
      `now_next` (created lazily by the provider).
- [x] **C.2** Indexer-pulse update wire: collect `nowNextFlow` →
      rebuild + post in `AppGraph.parkRuntimes`.
- [ ] **C.3** `AlarmManager` boundary alarm at `next.start` —
      **DEFERRED**: the 60s ticker inside `nowNextFlow` already gives
      sub-minute freshness at the boundary, and exact-alarm
      permission on API 31+ is a per-user opt-in we don't want to
      silently demand. Re-open if user reports stale boundary copy.
- [x] **C.4** `NowNextNotificationProviderTest` (Robolectric) —
      asserts built notification title + content match the snapshot.

### Phase D — Widget surfaces — shipped in commit `b7846dc`

- [x] **D.1** Shared `widget/common/WidgetRenderer.bindNowNext`
      helper.
- [x] **D.2** `NowWidgetProvider.bindAbsent` reads the snapshot from
      `WidgetGraph.nowNextProvider` and surfaces `Next: <title>` +
      `in 2h 15m` into the otherwise-empty subbeat + remaining slots.
      `CountdownWidgetProvider` keeps its pinned-event semantics and
      consumes the same helper when its empty branch is hit (helper
      is available now; full refactor deferred — the user's primary
      ask was for the now/upcoming widget). `AppGraph.parkRuntimes`
      installs the live snapshot reader + force-refreshes the now
      widget on every snapshot tick.
- [x] **D.3** `WidgetNowNextRenderTest` — asserts the formatted
      `(title, relative)` pair and the `RemoteViews.apply` round-trip.

### Phase E — Close-out

- [x] **E.1** Tick A..D with commit SHAs in this plan and in
      `docs/plans/main.md`.
- [x] **E.2** `## Status: ✅ DONE` on this file.
- [x] **E.3** Append D.120..D.125 to `docs/plans/decisions.md`
      mirroring D-2.25.a..f.
- [x] **E.4** Add Round 2.25 entry to `docs/plans/main.md`.

### Round 2.25.x trailer — readable demo by default

User feedback (2026-05-17): "schedule and when we click on the day
tab on view version where we can actually read 5min entries?
currently we can't... automatic currently selects a shit zoom level
that makes our demo data genuinely display like crap." Two fixes
shipped together:

- [x] **X.1** Default schedule view = Schedule (agenda). See
      `app/src/main/java/com/eight87/strictlykeptboy/ui/schedule/ScheduleViewModePrefs.kt`
      `load()` returning `ScheduleViewTab.Schedule`. Existing users
      keep their stored pick (migration is implicit — empty prefs
      only). Test: `ScheduleViewModeDefaultTest`. Decision D.126.
- [x] **X.2** Density-aware Auto zoom. New pure resolver
      `resolver/AutoZoomResolver.kt` picks the smallest zoom level
      where the shortest visible event clears 14 dp; wired into
      `ui/schedule/SchedulePane.kt` (`effectiveZoom` calc). 5-min
      demo content auto-picks level 4 (Spacious / 320 dp/h). Tests:
      `AutoZoomResolverTest`. Decision D.127.
- [x] **X.3** AVD smoke on `emulator-5558`: agenda lands by default,
      Day view Auto = Spacious (level 4) for the dense demo. Evidence
      `docs/qa/2-25/agenda-default.png` + `day-auto-readable.png`.

### Round 2.25.y trailer — grouped-aware Auto zoom (shipped 4bd5e4d)

User feedback (2026-05-17): "that's also why I wanted to group up
chores like brushing your teeth and getting ready for bed.. so in
calendar view with that zoom level you can draw a group around all
those 'getting ready for bed chores' and just show those rather
than the atomic tasks at that zoom level." 2.25.x picked Spacious
on the 5-min atoms, hiding the Round 2.21 Phase F grouping. Y
makes Auto consider effective grouped-band durations so the user
sees a readable "Morning routine · N atoms" band at low zoom.

- [x] **Y.1** `AutoZoomResolver.deriveFromEffectiveBandMinutes`
      added; original `derive` kept intact. Pure resolver, no UI deps.
      Tests: `AutoZoomResolverTest` (5 new cases).
- [x] **Y.2** `effectiveBandMinutesForAutoZoom` adapter in
      `ui/schedule/GroupedDayBand.kt` runs `groupDayBands` per day
      and emits one minute-duration per resulting grouped band.
      Tests: `GroupedDayBandTest` (3 new cases).
- [x] **Y.3** `SchedulePane.effectiveZoom` now feeds the grouped
      adapter into the resolver instead of raw instances.
- [x] **Y.4** Demo grouping coverage extended: `kinky-rituals` +
      `cat-care` got `meta_group_field = "phase"` (was already on
      `routines`) and every sub-15-min recurrence got
      `group = "morning" / "midday" / "evening"` per its dtstart.
- [x] **Y.5** AVD smoke on `emulator-5558`: Day view at Compact
      zoom renders "morning · 5 atoms" collapsed band; tap expands
      it inline. Evidence `docs/qa/2-25/day-grouped-collapsed.png`
      + `day-grouped-expanded.png`. Note: Auto still picks Spacious
      because non-grouped 5-min atoms exist in `dom-overlay` /
      `social` (text-pings) — those force level 4 to satisfy the
      shortest-band rule. That's correct per spec; the user can
      pick Compact/Normal explicitly and SEE the grouping take effect.
- [x] **Y.6** Decision D.128 appended to `docs/plans/decisions.md`.

### Round 2.25.z trailer — p25 Auto zoom + sub-readable tap cue (shipped {SHA_PLACEHOLDER})

User feedback (2026-05-17): "isolated 5-min outliers (dom-overlay
text pings, social check-ins) shouldn't drag Auto to Spacious.
Use 25th-percentile shortest, not minimum. And: when a band
renders below the readability threshold at the current zoom,
give it a tap affordance so the user knows there's more there
to read."

- [x] **Z.1** `AutoZoomResolver.derive` + `deriveFromEffectiveBandMinutes`
      switched from `.min()` to 25th-percentile via private
      `p25Minutes(...)` helper. Pure resolver, no UI deps.
- [x] **Z.2** `AutoZoomResolverTest` rewritten — covers
      n=1/4/8/12 p25 boundaries, the rich-demo shape (12-band
      mix → Normal), grouped-adapter passthrough, empty/default.
- [x] **Z.3** Sub-readable cue in `ScheduleDayView.BandsLayer`:
      `…` glyph at `CenterEnd` for any real band where
      `isSubReadableBand(rawDurationMin, effectiveZoom)`.
      Existing `Surface.onClick` routes to full-screen
      `EventDetailScreen`. Helper + `TestTagSubReadableCue`
      live at the top of `ScheduleDayView.kt`.
- [x] **Z.4** `SubReadableBandTest` — 5 cases covering the dp
      threshold walk-up across zoom levels.
- [x] **Z.5** AVD smoke on `emulator-5558`: wizard → Kept Life
      lands on agenda; Day SUN 17 shows visible 5-min text-ping
      bands with `…` overflow glyphs; tap opens full-screen
      EventDetailScreen ("Breakfast" detail). Auto picked
      Spacious for SUN 17 specifically because even the p25
      band is short on that day (weekend has fewer grouped
      routines + many text pings), but the cue + tap affordance
      now make the dense bands navigable. Evidence:
      `docs/qa/2-25/auto-p25-day-view.png` +
      `docs/qa/2-25/auto-p25-tap-detail.png`.
- [x] **Z.6** Decision D.129 appended to `docs/plans/decisions.md`.

# Round 2.25 — Now / Next surface unification

## Status: DRAFT

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

### Phase C — Ongoing notification — shipped in commit `__pending__`

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

### Phase D — Widget surfaces

- [ ] **D.1** Shared `widget/common/WidgetRenderer.bindNowNext`
      helper.
- [ ] **D.2** Update `NowWidgetProvider.onUpdate` (and, if
      applicable, `CountdownWidgetProvider`) to render the Now/Next
      pair.
- [ ] **D.3** `WidgetNowNextRenderTest` — asserts `RemoteViews`
      `setText` calls for both lines.

### Phase E — Close-out

- [ ] **E.1** Tick A..D with commit SHAs in this plan and in
      `docs/plans/main.md`.
- [ ] **E.2** `## Status: ✅ DONE` on this file.
- [ ] **E.3** Append D.120..D.125 to `docs/plans/decisions.md`
      mirroring D-2.25.a..f.
- [ ] **E.4** Add Round 2.25 entry to `docs/plans/main.md`.

# Round 2.21 — Calendar UX: overlay identity, picker, zoom, grouping

## Status: DRAFT

## Context

User audit on AVD (2026-05-16) surfaced three independent UX gaps in
the Schedule surface — all rooted in the same underlying truth:
**the data model is multi-overlay-aware, but the UI does not give the
user enough sensory information to *tell overlays apart* or to *see
atomic events when zoomed*.**

The three gaps, as the user named them:

1. **Overlays are visually indistinguishable.** The chip strip shows
   calendar names but no color / no emoji — and calendars without an
   `emoji` or `color_seed` set in their `calendar.toml` fall back to
   a stable-but-arbitrary hash. The data model
   (`CalendarActivityConfig`) round-trips both fields to disk
   already, but there is **no in-app editor** to set them, so every
   calendar without a wizard-seeded color/emoji is a guessing game.
2. **The overlay chip strip is a horizontal scroll** above the day
   view. With 11 demo calendars (work / commute / routines / gym /
   kinky-rituals / dom-overlay / social / cat-care / holidays /
   vacation / family-visits) it is a finger-flick to find any given
   chip. User wants a **dedicated overlay-picker button in the top
   bar** (next to the schedule / view selector) that opens a managed
   list: toggle on/off, see source repo, jump to settings.
3. **Day view is too dense to read** at the current 60dp/hour.
   Atomic events (5–15 min routines like "brush teeth") render as
   ~5dp slivers with no readable label. User wants a **user-
   controllable zoom level**, with 1h ≈ half-screen as a target
   density, *and* atomic events grouped under their parent
   meta-activity (e.g. "brush teeth" + "shave" + "moisturize"
   collapsed under "Morning routine" until expanded).

Additionally — and naturally connected — Google Calendar's
**Schedule / Day / 3-day / Week / Month** view-mode set is the
expected feature shape; skb currently has Day / Week / Month / Year.
A **Schedule (agenda-list)** view + **3-day** view land naturally in
this round.

## Locked design decisions

- **D-2.21.a — Calendar identity edits live in a per-calendar
  settings sheet**, written through `CalendarActivityConfig.write`
  to `calendar.toml`, committed to git. Source of truth on disk.
  No new app-local prefs for color / emoji.
- **D-2.21.b — Color picker is a 12-swatch Material palette**
  (Compose `Color` literals) plus a "Custom hex" entry. Twelve is
  enough for 11 demo calendars + a slot — picking from a wheel is
  out of scope.
- **D-2.21.c — Emoji input is a single emoji-keyboard field**
  (Compose `TextField` with `keyboardOptions = KeyboardOptions(
  imeOptions = ImeAction.Done)`). No emoji picker grid — the system
  IME already has one. Validate to a single grapheme cluster on
  save.
- **D-2.21.d — Overlay picker button replaces the horizontal chip
  strip**. The chip strip is removed entirely from above the day
  view; its responsibilities move into the top-bar overlay-picker
  button + sheet. *Rationale:* user explicitly said "i don't want
  those overlays to be scrolable" — keeping both surfaces is double
  work and the chip strip loses to the picker on density.
- **D-2.21.e — Top bar gets two right-side buttons**: view-mode
  selector (existing) and the new overlay-picker. Both icon buttons,
  Material symbols. Active-overlay count rendered as a badge.
- **D-2.21.f — View modes are Google's set + Year**: Schedule
  (agenda list, no time grid), Day, 3-day, Week, Month, Year. Year
  stays. *Rationale:* the user explicitly anchored the spec to
  Google's set *and* asked Year to stay.
- **D-2.21.g — Zoom is a per-overlay preference**, stored in
  `CalendarVisibilityPrefs` keyed by `(repoId, calendarId)`. Each
  calendar carries its own zoom level ∈ {1, 2, 3, 4}, default 2.
  The view-level *effective* zoom for the day grid is
  `max(zoom-of-visible-overlays)` so the densest overlay's
  readability wins. Schedule + Month + Year ignore zoom (they're
  list / month-cell / year-cell layouts). *Rationale:* user said
  per-overlay so they can keep routines-overlay zoomed-in for
  atomic readability without dragging the work-overlay along.
- **D-2.21.h — Zoom levels are**: 1 = 40dp/h (today's tighter form),
  2 = 80dp/h (default; 1h ≈ half a phone-screen), 3 = 160dp/h
  (atomic-event readable), 4 = 320dp/h (planning mode).
- **D-2.21.i — Zoom control is a pinch gesture on the day-view grid
  + a toggle in the overlay-picker sheet** (rounded slider with the
  four named stops). No gesture-only — the user must be able to set
  it deliberately.
- **D-2.21.j — Atomic-event grouping is opt-in per calendar**.
  Calendars carry a new `meta_group_field: String?` in
  `calendar.toml` (e.g. `"category"` / `"parent"` / `"phase"`). When
  set, the resolver groups consecutive atoms in the same group into
  a single visual band whose label is the group name, with a
  caret-to-expand affordance. Default: unset (no grouping). This is
  additive on the existing event schema — events already carry
  arbitrary key/value fields per Phase Q.
- **D-2.21.k — Grouped bands are a UI-only concern**; the underlying
  `MaterializedInstance` list does not change. Renderer receives the
  same instances; `ScheduleDayView` collapses them at render time
  based on `meta_group_field` + adjacency-in-time.

## Phases

### Phase A — Data: identity fields on `calendar.toml` (skb commit `<sha-here>`)

- [ ] **A.1** Confirm `CalendarActivityConfig` already round-trips
  `emoji` + `color_seed`. (Spot-check: yes per
  `resolver/CalendarRegistry.kt:135` + `store/CalendarActivityConfig.kt:28`.)
- [ ] **A.2** Add `meta_group_field: String?` to
  `CalendarActivityConfig` with read + write. Test round-trip.
- [ ] **A.3** Add an `EventGroup` type in `resolver/Types.kt`
  representing the value extracted from `event[meta_group_field]`
  (or `null` if unset). Add it to `MaterializedInstance`.
- [ ] **A.4** Pipe `EventGroup` through `RecurrenceMaterializer` +
  `OverlayResolver` so it reaches `DayBand`.

### Phase B — Calendar identity editor (skb commit `<sha-here>`)

- [ ] **B.1** New composable `ui/calendars/CalendarIdentityEditor.kt`:
  emoji TextField (single grapheme validation) + 12-swatch
  ColorPicker + Custom hex entry.
- [ ] **B.2** Wire into the long-press settings sheet on the existing
  `CalendarFilterChipStrip` (about to be removed in Phase C, but
  re-mounted on the overlay-picker sheet — keep the binding logical).
- [ ] **B.3** Hook save → `CalendarActivityConfig.write` →
  `git add . && git commit -m "calendar: identity for <name>"`.
- [ ] **B.4** Robolectric test: open editor, pick swatch, save,
  assert `calendar.toml` on disk has the new `color_seed = 0xRRGGBB`
  + the chip strip + day-view bands show the new tint after a
  refresh cycle.
- [ ] **B.5** Wizard scaffolds — every wizard-seeded calendar gets a
  preset color + emoji at scaffold-time (today they often don't);
  audit `ui/wizard/TemplateRegistry.kt` + add defaults.

### Phase C — Top-bar overlay picker button (skb commit `<sha-here>`)

- [ ] **C.1** New composable `ui/calendars/OverlayPickerButton.kt`:
  icon button (filter-symbol) + count badge of active overlays.
  Placed in the Schedule top bar, right side, next to the existing
  view-mode selector.
- [ ] **C.2** New composable `ui/calendars/OverlayPickerSheet.kt`:
  modal bottom sheet listing every calendar across every repo,
  grouped by repo header (avatar + repo name). Each row shows:
  emoji + color dot + display name + source-repo chip + toggle.
  Tapping the row's "..." opens the identity editor.
- [ ] **C.3** Delete the horizontal `CalendarFilterChipStrip` from
  above the day view (per D-2.21.d). Migrate its visibility-toggle
  flow into the picker sheet.
- [ ] **C.4** AVD verify: open Schedule → tap overlay-picker → toggle
  3 overlays off → bands disappear → re-open picker → toggle back.
- [ ] **C.5** Test: `OverlayPickerSheetTest` — seeds 2 repos with 5
  calendars each, asserts grouping headers + toggle parity with
  `CalendarVisibilityPrefs`.

### Phase D — Per-overlay zoom control (skb commit `<sha-here>`)

- [ ] **D.1** New `prefs/SchedulePrefs.kt`: `viewMode:
  ScheduleViewMode` (Flow-backed). **Zoom is NOT here** — it's
  per-overlay (per D-2.21.g) and lives in
  `CalendarVisibilityPrefs`.
- [ ] **D.2** Extend `CalendarVisibilityPrefs` with `zoom: Int` ∈
  {1..4} keyed by `(repoId, calendarId)`, default 2. Add migration
  shim that defaults to 2 for any unseen key.
- [ ] **D.3** Replace `private val HourHeight = 60.dp` in
  `ScheduleDayView.kt` with `hourHeight = when (effectiveZoom) { 1
  -> 40.dp; 2 -> 80.dp; 3 -> 160.dp; 4 -> 320.dp }` where
  `effectiveZoom = visibleCalendars.maxOfOrNull { zoomOf(it) } ?:
  2`.
- [ ] **D.4** Add a zoom segmented-control on **each calendar row**
  in the overlay-picker sheet (four stops, current value
  highlighted). Tapping cycles or expands a small popover with the
  four levels.
- [ ] **D.5** Add pinch-to-zoom gesture on the day grid Box →
  **applies to the topmost visible overlay** (the one whose band
  the pinch centers on), so the user can zoom an overlay without
  opening the sheet. Snap to nearest step on release.
- [ ] **D.6** AVD verify: open Day view → bump routines-overlay to
  zoom-3 → routines bands grow tall and readable, work-overlay bands
  stay normal density when routines is hidden. Toggle routines back
  on → grid expands to the routines zoom (effective = max).
- [ ] **D.7** Apply effective-zoom to 3-day + existing Week views.
  Schedule + Month + Year ignore zoom.

### Phase E — Schedule + 3-day view modes (skb commit `<sha-here>`)

- [ ] **E.1** Update `ScheduleViewMode` enum to `{Schedule, Day,
  ThreeDay, Week, Month, Year}` (additive — Year stays).
- [ ] **E.2** New composable `ui/schedule/ScheduleAgendaView.kt`:
  vertical list grouped by day-header, each day's events as
  density-1 rows with emoji + color dot + time + title + group
  badge. Mirrors Google Calendar's Schedule view.
- [ ] **E.3** New composable `ui/schedule/ScheduleThreeDayView.kt`:
  three day-grids side by side. Respects zoom.
- [ ] **E.4** Update the existing top-bar view-mode selector
  (drop-down or menu) to the new six-item set (Year still in).
- [ ] **E.5** AVD verify each mode renders + transitions clean.

### Phase F — Atomic event grouping (skb commit `<sha-here>`)

- [ ] **F.1** Resolver-side: `GroupedDayBand` data class wraps a
  list of `DayBand` sharing the same `EventGroup` AND adjacent in
  time (gap ≤ 5 min). `ScheduleDayView` collapses on render when
  zoom ≤ 2, expands automatically when zoom ≥ 3.
- [ ] **F.2** Visual: collapsed group renders as a single band
  spanning the union of its children's time range, label = group
  name, with a caret + child count ("Morning routine · 5 atoms").
  Tap caret expands inline (the band splits into child bands within
  the same vertical extent).
- [ ] **F.3** Seed the rich-demo `routines` calendar with
  `meta_group_field = "phase"` and tag each atom with
  `phase = "morning" / "midday" / "evening"`.
- [ ] **F.4** AVD verify: open Day view on rich-demo at zoom-2 → see
  3 grouped bands instead of ~15 atoms → zoom in → atoms split out
  → tap caret on a collapsed group → atoms expand.
- [ ] **F.5** Test: `GroupedDayBandTest` exercises adjacency rule +
  the zoom-threshold auto-expand.

### Phase G — Close-out (skb commit `<sha-here>`)

- [ ] **G.1** Tick every Phase A–F box with commit SHA.
- [ ] **G.2** Status: ✅ DONE at top of this file.
- [ ] **G.3** Append D-2.21.a..k to `docs/plans/decisions.md`
  starting from D.99 (highest is currently D.98).
- [ ] **G.4** Add a Round 2.21 entry to `docs/plans/main.md`.
- [ ] **G.5** Full AVD acceptance sweep (zoom + picker + identity
  edit + grouped band + Schedule view + 3-day view) per the
  CLAUDE.md AVD loop.

## Out of scope (called out so the user can pull them in)

- **Drag-to-reschedule on grouped bands.** Today, drag on a band
  reschedules a single event; grouped-band drag semantics ("does it
  move the parent or all children?") is a separable problem; if
  needed, Round 2.22.
- **Color picker wheel.** D-2.21.b locks the 12-swatch palette +
  custom hex; a hue/sat/val wheel can land in a later round.
- **Global zoom override.** Zoom is per-overlay per D-2.21.g; a
  one-tap "level everyone" affordance isn't shipping this round
  (user can set each overlay manually in the picker sheet).
- **Calendar reordering** (drag-to-reorder repos / calendars in the
  picker sheet). Not blocking; ship if cheap, defer if not.
- **Importing color / emoji from external (Android) calendars**
  surfaced by Round 2.18. Today external calendars get their
  account-supplied color via the bridge; user overrides via the new
  editor would *also* round-trip — but the bridge-side write-back
  isn't in scope here.

## Verification gates

- Existing tests stay green; +5–8 new tests across
  `CalendarActivityConfigTest`, `OverlayPickerSheetTest`,
  `ScheduleAgendaViewTest`, `ScheduleThreeDayViewTest`,
  `GroupedDayBandTest`, `SchedulePrefsTest`.
- AVD sweep on the rich-demo repo (since it has the most overlays +
  the routines calendar primed for grouping).
- `:app:assembleDebug` + install + screencap per the CLAUDE.md loop
  at the end of each phase.

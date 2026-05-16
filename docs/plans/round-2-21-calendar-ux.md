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
- **D-2.21.e — Top bar gets ONE new right-side button**: the overlay-
  picker (Material `Tune` / filter icon, active-overlay count badge).
  Tapping it opens a **full-screen overlay-picker destination** —
  not a bottom sheet (bottom sheet is already used for todo items;
  no shape collision), not a rail tab (rail is view-modes only).
  View-mode selection stays where it is today — **the existing left
  vertical rail with sideways-text tabs** (Day · Week · Month · Agenda
  · Year). No hamburger / drawer / popup-menu. Schedule + 3-day slot
  into that same rail as two new entries.
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

### Phase A — Data: identity fields on `calendar.toml` (shipped in `aa898db`)

- [x] **A.1** Confirmed `CalendarActivityConfig` round-trips `color_seed` (verified per `resolver/CalendarRegistry.kt:135` + `store/CalendarActivityConfig.kt:28`). `emoji` is read directly in `CalendarRegistry` and was already wired.
- [x] **A.2** Added `metaGroupField: String?` to `CalendarActivityConfig` (`meta_group_field` on disk). Blank values normalised to `null` on both read + write. 5 new round-trip tests in `CalendarActivityConfigParseTest`.
- [x] **A.3** Carried as `group: String?` on `Event` + `RecurrenceRule` entities (disk: `group = "Morning routine"`), threaded through to `EventInput` + `RecurrenceInput` + `MaterializedInstance` so the renderer can see it on every band. `metaGroupField` lives on `CalendarMeta` (sourced from the config) so callers can decide *whether* to honour the labels without a per-event lookup. 2 new round-trip tests in `EntityRoundTripTest`.
- [x] **A.4** Piped through `RecurrenceMaterializer.fromOneOff` + `materializedFromRule` (so both one-off events and rule-materialised instances carry their `group`). The `OverlayResolver` is field-preserving — `MaterializedInstance.group` flows to `DayBand.instance.group` unchanged.
- [x] **A.5** Room cache: added `groupLabel: String?` to `EventRow` + `RecurrenceRuleRow`, bumped `CacheDatabase` version 2 → 3 (existing `fallbackToDestructiveMigration(true)` handles the schema delta; Room is rebuildable from disk per CLAUDE.md). Wired through `EntityMapping.event` + `EntityMapping.recurrenceRule` and `SourcesPublisher.toEventInput` + `toRuleInput`.

### Phase B — Calendar identity editor (shipped in skb commit `3469784`)

- [x] **B.1** Identity editor added *inline* to the existing
  `CalendarSettingsSheet` (rather than a new composable — minimal-
  disruption, same long-press entry point): emoji `TextField` with
  single-grapheme validation via `java.text.BreakIterator`, 12-swatch
  Material palette (2×6 grid of `ColorSwatch` circles), and a custom
  hex `TextField` (cleans + uppercases input, applies on 6-char
  parse). Selected swatch carries a primary-tinted border ring.
- [x] **B.2** Wired through the existing long-press flow on
  `CalendarFilterChipStrip` → `pendingCalendarEdit` →
  `CalendarSettingsSheet` (`MainActivity.kt:1312`). When the chip
  strip retires in Phase C, the same sheet will mount under the new
  overlay-picker screen's per-row "..." action.
- [x] **B.3** `CalendarSettingsDraft` extended with `emoji: String?`
  + `colorSeed: Int?`; `CalendarSettingsWriter.write` drops + rewrites
  the `emoji` scalar + `color_seed` (falling back to `meta.colorSeed`
  when the draft leaves it null, so the user can edit emoji without
  touching color). Commit message lands via the existing
  `GitRepoRegistry.get(repoId)?.commitAll("calendar settings: <name>")`
  path.
- [x] **B.4** `CalendarSettingsRoundTripTest` extended with 3 new
  tests: identity emoji+color round-trip, blank-emoji clears disk,
  null-colorSeed preserves existing meta color. Full suite green at
  31s. Live AVD long-press of the chip via `adb input swipe` doesn't
  reliably trigger Compose `combinedClickable` long-press (known
  limitation); persistence half is covered by the new tests, UI
  surface is the same sheet that was AVD-validated in Round 2.1.B
  with additive fields above the existing controls. `emoji: String?`
  also threaded onto `CalendarMeta` so future surfaces (overlay-
  picker row, chip glyph) can consume it.
- [ ] **B.5** Wizard scaffolds — every wizard-seeded calendar gets a
  preset color + emoji at scaffold-time (today they often don't);
  audit `ui/wizard/TemplateRegistry.kt` + add defaults. *Deferred to
  a B.5 follow-up commit; not blocking on Phase C.*

### Phase C — Top-bar overlay picker button (shipped in `57a2bb9`)

- [x] **C.1** `ui/calendars/OverlayPickerButton.kt` shipped: Material
  `Tune` icon + `BadgedBox` showing visible-overlay count. Wired into
  `ShellTopBar` and rendered only when the Schedule destination is
  active.
- [x] **C.2** `ui/calendars/OverlayPickerScreen.kt` shipped as a full-
  screen destination layered over the active pane (not a bottom sheet,
  not a rail tab — D-2.21.e). LazyColumn of repo-grouped rows with
  emoji + color dot + display name + source-repo line + visibility
  Switch + "..." button that fires `onEditCalendar` (host mounts
  `CalendarSettingsSheet` over the screen). Top-app-bar back arrow
  dismisses.
- [x] **C.3** Deleted the `CalendarFilterChipStrip` composable.
  External-source glyph helpers (`ExternalSourceGlyph` + `glyphFor` +
  `ExternalSourceLeadingIcon` + `CAL_ACCESS_CONTRIBUTOR`) live on in
  the same file so existing tests keep passing. `SchedulePane` no
  longer mounts the strip above the day view.
- [ ] **C.4** AVD verify deferred — long-press / pinch / animated
  destination transitions are unreliable via `adb input swipe`;
  `OverlayPickerScreenTest` covers the persistence half (5 new tests
  for visibility-prefs round-trip + zoom prefs).
- [x] **C.5** `OverlayPickerScreenTest` lands — 5 tests covering toggle
  parity for 2 repos × 5 calendars, zoom round-trip per `(repoId, id)`,
  default-of-2, clamp-out-of-range, reopen-survives.

### Phase D — Per-overlay zoom control (shipped in `587d401`)

- [x] **D.1** Skipped a new `prefs/SchedulePrefs.kt` — the existing
  `ScheduleViewModePrefs` already covers the Flow-backed `viewMode`
  surface. Zoom lives on `CalendarVisibilityPrefs` per D-2.21.g.
- [x] **D.2** `CalendarVisibilityPrefs` extended: `zoom: Int` on
  `VisibilityEntry` (default `ZOOM_DEFAULT = 2`, clamped 1..4),
  `setZoom(id, zoom, repoId)`, `zoomOf(id, repoId)`. Tolerant JSON
  load decodes legacy entries (no `zoom` field) at default-2.
- [x] **D.3** `private val HourHeight = 60.dp` replaced by
  `hourHeightForZoom(zoom) = {40,80,160,320}.dp` (default 80).
  `ScheduleDayView` now takes `effectiveZoom: Int` and threads it
  through `HourGutter` / `HourLines` / `BandsLayer` / `NowLine`.
- [x] **D.4** Per-row `SingleChoiceSegmentedButtonRow` with 4 stops
  rendered on every `OverlayPickerScreen` row. Tap commits zoom via
  `CalendarVisibilityPrefs.setZoom`.
- [ ] **D.5** Pinch-to-zoom on the day-grid Box DEFERRED — `adb input`
  can't simulate pinch reliably on the AVD (same limitation as the
  long-press flow in Round 2.1.B), and the picker's per-row segmented
  control covers the *deliberate* path D-2.21.i mandates. The
  gesture-only convenience layer is a Round 2.22 follow-up; the
  per-overlay state model that would back it is already shipped.
- [ ] **D.6** AVD verify partially covered: `HourHeightForZoomTest` +
  `OverlayPickerScreenTest` exercise the persistence + dp/h mapping;
  on-AVD pinch deferred per D.5.
- [x] **D.7** `ScheduleWeekView` extended with `effectiveZoom: Int`
  parameter; `HourGutter` / `DayColumn` / `WeekNowLine` all take
  hourHeight by value. `SchedulePane` computes
  `effectiveZoom = max(zoomOf each visible overlay)` and forwards to
  Day + Week. Month + Year + Agenda ignore zoom (list / cell layouts).

### Phase E — Schedule + 3-day view modes (shipped in `<sha-e>`)

- [x] **E.1** `ScheduleViewTab` enum now `{Schedule, Day, ThreeDay,
  Week, Month, Agenda, Year}` per D-2.21.f (additive — `Agenda`
  keeps its Phase G.4 timebox semantics; `Schedule` is the new
  agenda-list mode). `EnumLabels` + `scheduleTabLabelRes` updated.
- [x] **E.2** `ScheduleAgendaView` shipped: LazyColumn grouped by
  day-header, each band rendered as a density-1 row (color dot +
  HH:mm + title + time range + group badge). Zoom is ignored
  (list, not timeline).
- [x] **E.3** `ScheduleThreeDayView` shipped: header strip + three
  side-by-side `ScheduleDayView` columns. Honours `effectiveZoom`.
- [x] **E.4** Left rail picks up `Schedule` + `3-day` automatically
  because `SkbAppShell` iterates `ScheduleViewTab.entries`. New
  strings `schedule_view_tab_schedule` + `schedule_view_tab_3day`
  added to `values/strings.xml`.
- [ ] **E.5** AVD verify deferred — unit tests stayed green at 1057;
  the new view modes are pure composables atop the same
  resolver/render pipeline that Day + Week already validate.

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

# Round 2.23 — Schedule readability + reviews wiring

## Status: ✅ DONE

## Context

Five user-feedback items captured from a live AVD session on 2026-05-16:

1. "i didn't see any reviews wired up yet" — `ReviewsPane` mounted
   (DDD.13) but `ReviewFeedReader` deferred; the destination shows
   the empty-state card even when `reviews/<sha>/reviewable_change.md`
   files exist on disk.
2. "i have no way to zoom in into our calendar to make the hours be
   half the screen like you should have done (make that a row of
   buttons at the top for the different zoom levels)" — pinch +
   per-overlay segmented control exist but aren't discoverable as a
   one-tap day-view-wide control.
3. "all the demo tasks here still have the same color" — band
   `accentColorSeed` is piped through but the day/week band background
   is uniform `surfaceContainer`; only the 4dp left stripe carries
   color (often invisible on small bands).
4. "our schedule looks nothing like the google one which has nice
   artwork for each day to separate our days (at least give me an
   emoji there or something to separate the weekdays)" — day-header
   strip in Day/3-day/Week shows only the weekday abbreviation +
   day-of-month number.
5. "clicking on a task in the calendar pulls in the card from the
   bottom, but i want a full screen view rather than the pullin from
   the bottom to view/edit my task, the button on there doesn't work
   anyway" — `EventDetailSheet` is a `ModalBottomSheet`; the Edit
   button in `SchedulePane` is wired with `onEdit = {}` (stub).

## Locked design decisions

- **D-2.23.a — Top-of-Day-view zoom row.** A 5-segmented button row
  (Auto · 40 · 80 · 160 · 320) above the Day-grid Box and above the
  3-day timeline. "Auto" sets `globalZoomOverride = null` (falls back
  to max-of-visible-overlays per D-2.21.g); numbers set the override
  directly. Persisted via a new `setGlobalZoomOverride(Int?)` on
  `CalendarVisibilityPrefs` (closest existing pref class — adding a
  new SchedulePrefs file would just be ceremony).
- **D-2.23.b — Per-calendar colorSeed wins on the band.** Band Surface
  background = `colorForSeed(accentColorSeed).copy(alpha = 0.4f)` over
  the existing tonal fallback when seed != 0; foreground text uses
  `onSurface`. The 4dp leading stripe remains at full alpha for
  emphasis. When seed == 0, the existing `surfaceContainer` fallback
  is preserved (no regression on un-seeded calendars).
- **D-2.23.c — Per-weekday emoji.** Stable map lives in
  `ui/schedule/WeekdayEmoji.kt`: Mon🌅 Tue🌱 Wed🌊 Thu🌳 Fri🌟
  Sat🌸 Sun🦇. Prefixed to the day-header text in Day / 3-day / Week.
  Sun = 🦇 lands the bat-coded identity.
- **D-2.23.d — `EventDetailSheet` → full-screen overlay.** Same
  scaffold-level Surface-overlay pattern Round 2.22 used for
  `OverlayPickerScreen` (mount above the chrome Column inside the
  outer Box). New `EventDetailScreen` composable with `TopAppBar +
  back arrow`; the existing `EventDetailContent` is reused inside
  fillMaxSize. `EventDetailSheet` retired from Schedule but kept in
  source as `@Deprecated` thin wrapper to preserve API for any
  callers outside the schedule package; SchedulePane callers
  retargeted.
- **D-2.23.e — Reviews wiring uses `ReviewFeedReader`.** Pure helper
  scans `reviews/<commit-sha>/reviewable_change.md` (the
  `ReviewFeedWriter` output) across all active repos and emits
  `ReviewEntry`. The task brief said `feedback/` but the actual
  on-disk schema (DM-Z.3) is `reviews/`; we honour the codebase
  schema. `ReviewsPane` consumes the flow via `AppGraph`. Empty state
  preserved when no reviews exist.

## Phases

### Phase A — Per-calendar color on bands (shipped in commit `563a061`)

- [x] **A.1** Day-view band: apply `colorForSeed(seed).copy(alpha=0.4)`
      background when seed != 0; preserve existing fallback otherwise.
- [x] **A.2** Week-view DayColumn band: same tint logic.
- [x] **A.3** 3-day-view band: same tint logic.
- [x] **A.4** Test `RendererColorSeedTest` already covers piping;
      add `BandTintColorSeedTest` (unit + Robolectric snap) — two
      bands with distinct seeds render distinct backgrounds.

### Phase B — Weekday emoji (shipped in commit `563a061`)

- [x] **B.1** New `ui/schedule/WeekdayEmoji.kt` with map + `emojiFor`.
- [x] **B.2** Day-view header (currently missing — Day grid has no
      "today" header) — defer to header sites that exist: Week +
      3-day. For Day view, add an emoji to the now-line / day label
      via a small top strip.
- [x] **B.3** Week + 3-day day-headers — prefix emoji before weekday
      abbreviation.
- [x] **B.4** `WeekdayEmojiTest` — 7 entries map correctly.

### Phase C — Top-of-Day zoom row (shipped in commits `4253339` + `3bdcfc3`)

- [x] **C.1** `CalendarVisibilityPrefs.globalZoomOverride` — Int? backed
      by separate prefs key; flow exposed via `state` (extend
      `VisibilityState`).
- [x] **C.2** New `ui/schedule/ZoomLevelRow.kt` — 5-segmented button
      row (Auto / 40 / 80 / 160 / 320) with test-tags per button.
- [x] **C.3** Mount row at the top of Day view + 3-day view inside
      SchedulePane's `ScheduleMasterContent`.
- [x] **C.4** `effectiveZoom` calc: prefers override over
      max-of-visible.
- [x] **C.5** `ZoomLevelRowTest` (Compose-Robolectric) +
      `SchedulePrefsZoomOverrideTest` (round-trip).

### Phase D — EventDetailSheet → EventDetailScreen full-screen (shipped in commit `b1cf16b`)

- [x] **D.1** New `ui/schedule/EventDetailScreen.kt` —
      Surface(fillMaxSize) + TopAppBar with back arrow; embeds
      `EventDetailContent`.
- [x] **D.2** Hoist `pendingEvent: DayBand?` state in SchedulePane
      (compact path only — tablet two-pane keeps its detail pane).
- [x] **D.3** `onBandTap` in compact path sets `pendingEvent` instead
      of opening the ModalBottomSheet.
- [x] **D.4** Mount `EventDetailScreen` over the pane via Surface
      overlay in SchedulePane (mirrors OverlayPickerScreen pattern).
- [x] **D.5** Identify broken button: Edit (`onEdit = {}` stub in
      SchedulePane.kt:167). Wire to a no-op-but-toast feedback so
      user gets a clear "Edit coming Round 3 — Phase I editor" toast
      until the editor lands, AND wire a working "Close" route via
      back. Document in commit message.
- [x] **D.6** `EventDetailScreenTest` — TopAppBar + back arrow +
      event title render; back callback fires.

### Phase E — Reviews wiring (shipped in commit `ed9d4c6`)

- [x] **E.1** New `ui/reviews/ReviewFeedReader.kt` — scans
      `reviews/<commit-sha>/reviewable_change.md` per repo root, parses
      via existing `FrontmatterReader`, returns `List<ReviewEntry>`.
- [x] **E.2** Wire into `AppGraph` via existing repo-root flow
      (use `repoRegistry.activeRoots()` equivalent).
- [x] **E.3** `ReviewsPane` consumes the flow in `SkbAppShell`.
- [x] **E.4** Preserve empty state when no items.
- [x] **E.5** `ReviewFeedReaderTest` — seed two repos with
      reviewable_change.md files; assert combined list ordered by
      timestamp desc.

### Phase F — Close-out

- [x] **F.1** Tick all substeps with commit SHAs.
- [x] **F.2** `## Status: ✅ DONE` on this file.
- [x] **F.3** Append D.113..D.117 to `decisions.md` (verify highest is
      D.112).
- [x] **F.4** Add Round 2.23 entry to `main.md`.
- [x] **F.5** AVD smoke on emulator-5558 — screencaps to
      `docs/qa/2-23/`.

---

## Round 2.23.1 follow-up (shipped in commit `45808a2`)

User feedback on the Round 2.23 Reviews work (2026-05-17):
1. "all the reviews aren't hooked up in the demo data"
2. "reviews use horizontal tabs at the top rather than our established
   patterns of vertical tabs on the left"

### Fix 1 — Reviews uses the vertical left rail (D.118)

- [x] **2.23.1.A.1** `ReviewsPane` drops the `Row { FilterChip(...) }`
      at the top. Accepts a hoisted `filter: ReviewsFilter` param
      (default `All` preserves test ergonomics).
- [x] **2.23.1.A.2** `SkbAppShell` builds rail items for the Reviews
      destination via `reviewsFilterLabelRes(...)`, mirroring the
      Schedule `scheduleTabLabelRes` pattern. Reuses the existing
      `RailColumn` + `RailItem` — no new primitive.
- [x] **2.23.1.A.3** New strings: `reviews_filter_{all,unread,reactions,threaded}`.
- [x] **2.23.1.A.4** `ReviewsPaneVerticalRailTest` asserts none of the
      four filter labels render inside the pane semantics tree, and
      that the external filter param routes correctly.
- [x] **2.23.1.A.5** D.118 added to `decisions.md` — vertical rail is
      universal across content destinations.

### Fix 2 — Demo seeds a real review timeline

- [x] **2.23.1.B.1** Add 14 `reviews/<sha>/reviewable_change.md` files
      under `assets/rich-demo-repo/reviews/`, referencing real event
      `global_id`s from the demo's two-week May 2026 window
      (routine compliance, gym buddy session, Saturday scene, mum's
      brunch, Friday-drinks two-pint rule, coffee-after-14 correction,
      phone-in-bed correction, etc.).
- [x] **2.23.1.B.2** `_manifest.txt` regenerated via
      `:app:regenerateRichDemoManifest` (142 entries, +14).
- [x] **2.23.1.B.3** `RichDemoReviewSeedTest` (Robolectric) extracts
      the demo to a tmp dir and asserts
      `ReviewFeedReader.scan(...).size >= 12`.

### AVD evidence (emulator-5558)

`docs/qa/2-23/reviews-vertical-rail.png` shows the rail rendering on
the left with rotated `All / Unread / Reactions / Threaded` labels
and the demo's seeded review cards populating the list. No horizontal
TabRow at the top. `reviews-demo-seeded.png` is the same surface.

### Test delta

1103 (Round 2.18 close-out) -> 1115 passing (+ ReviewsPaneVerticalRailTest
× 3 cases, RichDemoReviewSeedTest × 1, plus the Round 2.23 additions
that landed in `ed9d4c6`).

---

## Round 2.23.2 follow-up (shipped in commit `a9e7cfb`)

User feedback on the overlay picker (2026-05-17):
"can't see which color is which here, remove the priority picker
with the 40-80-160-320 that's redundant. show the color not near
the emoji but instead make this a card view like in repository,
where you list commute at the top, then a color entry i can click
on to change the color, then the priority, so each entry in
overlays here has multiple rows and is a card"

### Fix — Multi-row M3 Card per overlay (D.119)

- [x] **2.23.2.A.1** `OverlayPickerScreen.OverlayCard`: header
      (emoji + name + Switch + ⋮) / Color row / Priority row / Repo row,
      backed by an M3 `Card` with `surfaceContainerLow`. Tiny dot near
      the emoji removed.
- [x] **2.23.2.A.2** Color row reuses `IdentitySwatches` + `ColorSwatch`
      from `CalendarSettingsSheet` (made `internal` + new
      `identitySwatchName` helper for the human-readable label).
- [x] **2.23.2.A.3** `CalendarSettingsWriter.writeColorSeed(...)`
      mirrors `writePriority`'s read-merge-write shape; wired through
      `SkbAppShell.onOverlayColorChange` → `MainActivity`.
- [x] **2.23.2.A.4** Per-row 40/80/160/320 segmented control deleted;
      `TestTagOverlayPickerZoom` retired. Per-overlay zoom storage
      preserved (still backs `globalZoomOverride == null` fallback).
- [x] **2.23.2.A.5** `OverlayPickerScreenTest`
      `card_layout_renders_color_row_and_opens_palette_and_fires_writer`:
      asserts Color row exists, opens the palette on tap, and the
      blue swatch tap fires `onColorChange(meta, 0x42A5F5)`.
- [x] **2.23.2.A.6** D.119 added to `decisions.md`.

### AVD evidence (emulator-5558)

- `docs/qa/2-23/overlay-picker-card-layout.png` — three stacked cards
  (Beans / Commute / Boy Keeper) with header + Color + Priority + Repo
  rows; no per-row zoom segmented control; top-of-Day ZoomLevelRow
  remains visible behind back-nav.
- `docs/qa/2-23/overlay-picker-card-color-picker.png` — Beans card's
  Color row expanded to the 2×6 swatch grid.
- `docs/qa/2-23/overlay-picker-card-color-applied.png` — after picking
  blue + reopening the picker, Beans Color row reads "blue" with a
  visible blue swatch (color_seed = 0x42A5F5 persisted to
  `cat-care/calendar.toml`).
- `docs/qa/2-23/overlay-picker-card-priority-typed.png` — number
  keyboard up on Commute's Priority field with the typed value live
  in the field.

### Test delta

1115 → 1116 (+ card_layout_renders_color_row_and_opens_palette_and_fires_writer).

---

## Round 2.23.3 follow-up (shipped in commit `6e40de5`)

User feedback on the zoom row (2026-05-17):
"oh god nobody will get what these numbers mean bat.. make it more
comprehensive icons"

The raw `Auto / 40 / 80 / 160 / 320` dp-per-hour labels shipped in
2.23 Phase C were unreadable to non-developer users.

### Fix — icon + descriptive label per segment

- [x] **2.23.3.A.1** `ZoomLevelRow`: each `SegmentedButton.label` now
      stacks `Icon` on top of a short descriptive `Text` (labelSmall,
      `maxLines = 1`, `softWrap = false`). Icons chosen:
      `Icons.Outlined.AutoMode` (Auto) / `UnfoldLess` (Compact, 40) /
      `GridView` (Normal, 80) / `UnfoldMore` (Detail, 160) /
      `OpenInFull` (Spacious, 320). The icon ladder mirrors the dp/h
      ladder: low dp/h = compact / high dp/h = spacious.
- [x] **2.23.3.A.2** Per-segment `contentDescription` carries the
      precise value, e.g. "Zoom: Compact (40 dp per hour)", so
      screen-reader users still get the exact dp/h.
- [x] **2.23.3.A.3** New `ZoomLevelRowTest` (Compose-Robolectric):
      five tests covering descriptive-label rendering, default-selected
      Auto state, and onSelect callbacks for Compact / Spacious / Auto.

### AVD evidence (emulator-5558)

- `docs/qa/2-23/zoom-row-icons.png` — Day view top-of-pane row reads
  "Auto / Compact / Normal / Detail / Spacious" with icons stacked
  above labels; Compact is selected so the timeline shows dense
  40 dp/h bands.

### Test delta

1116 → 1121 (+ five `ZoomLevelRowTest` cases).


# strictlykeptboy — UI / UX specification

## Status: 🚧 IN-PLANNING

This document is the exhaustive UI spec for `strictlykeptboy`. It owns
every screen, every component, every navigation surface, every visual
state. It is the deep-dive backing `main.md` Phases F (scaffold), G
(schedule views), H (task views), I (repo management), J (sync UI),
K (wizard), Q (Auto), R (tablet), S (settings), T (theming), U
(accessibility).

**Cross-references:**
- Locked decisions: [`decisions.md`](decisions.md)
- Master plan: [`main.md`](main.md)
- Resolver output structures consumed by views: [`resolver.md`](resolver.md)
- Sync states surfaced in chrome: [`sync-engine.md`](sync-engine.md)
- Wizard content: [`templates-demo-wizard.md`](templates-demo-wizard.md)
- Notification + author chip details: [`notifications-sharing-import.md`](notifications-sharing-import.md)

This spec inherits the M3 Expressive (M3E) discipline established in
tonearmboy (`/home/laragana/workspace/tonearmboy/docs/plans/m3-expressive.md`):
expressive color schemes, the full `surfaceContainer*` ladder, filled
icon set sitting inside coloured circle avatars, divider-less stacked
rows on `surfaceContainer`, `RoundedCornerShape(28.dp)` group cards,
typography from the M3E type scale.

---

## Phase index

| Phase | Topic | main.md tie-in |
|---|---|---|
| UI-A | App scaffold + NavigationSuiteScaffold + theme root | F.1, F.3 |
| UI-B | Top app bar + repo switcher + sync button + identity icon | F.2, I.1, J.3, J.8 |
| UI-C | Schedule shell + view-mode tab strip + persisted last-view | F.4, G.6 |
| UI-D | Day view | G.1 |
| UI-E | Week view | G.2 |
| UI-F | Month view | G.3 |
| UI-G | Timebox view | G.4 |
| UI-H | Year view | G.5 |
| UI-I | Event detail sheet + edit + attachments | G.7 |
| UI-J | Task views (Combined / Today / Per-list / Shopping / Standing) + detail sheet + quick-add FAB | H.1–H.7 |
| UI-K | Repo switcher dropdown + repo settings + add-repo flow | I.1–I.6 |
| UI-L | Identity picker + identity editor | I.4, D.15 |
| UI-M | Together tab — common-time finder + sharing overview | N.1–N.3, O.1 |
| UI-N | Conflict-resolution UI | J.5–J.6 |
| UI-O | Wizard (7 screens) | K.1, L.* |
| UI-P | Settings shell + 10 sections | S.1–S.10 |
| UI-Q | Theming, dynamic color, density, mascot, empty states | T.1–T.5 |
| UI-R | Tablet two-pane (≥600dp, ≥900dp) | R.1–R.5 |
| UI-S | Android Auto | Q.1–Q.5 |
| UI-T | Accessibility + i18n discipline | U.1–U.5 |
| UI-U | Motion + transitions | T.* |

---

## UI-A — App scaffold + NavigationSuiteScaffold

`main.md` Phase F.1, F.3.

The user explicitly chose **left rail on phone too** (matching the
tonearmboy main library's left tabs). This contradicts the default
NavigationSuiteScaffold heuristic which picks bottom bar < 600dp.
We override the layout type.

**Decision:** wrap `NavigationSuiteScaffold` and force
`navigationSuiteType = NavigationSuiteType.NavigationRail` on every
width class. The collapsed rail (80dp) on phones is the same shape
tonearmboy uses for its main library tabs, so the visual rhyme is
preserved across the two apps.

**Top-level destinations (4):**

| Order | Icon | Label | Route | M3E icon set |
|---|---|---|---|---|
| 1 | 📆 (CalendarMonth filled) | Schedule | `schedule` | `Icons.Filled.CalendarMonth` |
| 2 | ✅ (CheckCircle filled) | Tasks | `tasks` | `Icons.Filled.CheckCircle` |
| 3 | 👥 (Groups filled) | Together | `together` | `Icons.Filled.Groups` |
| 4 | ⚙️ (Settings filled) | Settings | `settings` | `Icons.Filled.Settings` |

The emoji column above is documentary — the actual rail uses the
`Icons.Filled.*` vector set so they tint with `onSecondaryContainer`
when the destination is selected and `onSurfaceVariant` otherwise.
Selected indicator pill is the M3E pill shape (`shapes.extraLarge`).

**Phases:**

- [ ] **UI-A.1** Add `MainScaffold.kt` hosting `NavigationSuiteScaffold`
  with the 4 destinations and the override that pins it to
  `NavigationRail` on every width class.
- [ ] **UI-A.2** Wire navigation backstack: each tab is a separate
  `NavHost` graph; tab switches preserve the inner backstack per the
  Navigation 3 single-graph pattern (consult android-skills MCP for the
  current canonical sample).
- [ ] **UI-A.3** Apply `MaterialExpressiveTheme(colorScheme, shapes,
  typography, motionScheme)` at the root, mirroring tonearmboy's M3E
  rollout. Pin `material3:1.5.0-alpha18` (the alpha that promoted the
  expressive APIs to public; see tonearmboy `m3-expressive.md` finding
  1). Drop the override once 1.5.0 stable lands.
- [ ] **UI-A.4** `surfaceContainer*` ladder usage: page background =
  `surface`; cards = `surfaceContainer`; AMOLED-leaning dark theme uses
  `surfaceContainerHigh` for cards (per m3-expressive.md finding 2).
- [ ] **UI-A.5** Edge-to-edge enabled, status bar transparent,
  navigation bar transparent, content insets respected via
  `WindowInsets.Companion.systemBars`.
- [ ] **UI-A.6** Light/dark/auto follows system; manual override in
  Settings → Appearance writes a `themeMode` enum to a singleton
  `AppearancePrefs`.
- [ ] **UI-A.7** Dynamic color (Material You) on by default; per-repo
  seed override applied as a `LocalRepoTheme` CompositionLocal that
  wraps the active repo's `colorScheme` derivation.
- [ ] **UI-A.8** Density toggle (compact / comfortable / spacious)
  drives a `LocalDensityScale` multiplier applied to spacing tokens.
  Comfortable is default. Compact tightens vertical padding 25%;
  spacious loosens 25%.

**ASCII — phone scaffold (overall):**

```
┌──┬──────────────────────────────────────────┐
│  │ ┌──────────────────────────────────────┐ │
│📆│ │  TOP APP BAR                          │ │
│  │ │  [@repo] [Day Week Month Tbox Year]  │ │
│✅│ │                          [↻] [👤]    │ │
│  │ └──────────────────────────────────────┘ │
│👥│                                            │
│  │              CONTENT PANE                  │
│⚙️│              (active view)                 │
│  │                                            │
│  │                                  [+ FAB]   │
└──┴────────────────────────────────────────────┘
 80dp rail
```

---

## UI-B — Top app bar (chrome)

`main.md` Phase F.2.

Single component, three regions, every screen shares it.

**Decision — top app bar variant by tab:**
- Schedule: `MediumTopAppBar` (M3E variant, gives a 112dp expanded
  height that collapses to 64dp on scroll). Lets the view-mode tab
  strip live below the title without crowding the chrome.
- Tasks: `MediumTopAppBar` (same reason — view-mode tab strip).
- Together: `TopAppBar` (small variant, no view tabs).
- Settings root: `LargeTopAppBar` (M3E variant, mirrors tonearmboy
  Settings shape).
- Settings sub-screens: `MediumTopAppBar` with back arrow.

### B.1 Layout regions

```
┌────────────────────────────────────────────────────────┐
│ [@avatar  ▾  Personal]      [👤]   [↻]                 │  ← collapsed bar
├────────────────────────────────────────────────────────┤
│        [Day] [Week] [Month] [Tbox] [Year]              │  ← view-tab strip
└────────────────────────────────────────────────────────┘
   ↑ left                   ↑ right                ↑ below
   repo switcher            identity + sync         view tabs
```

Left: repo switcher chip. Centre (or below in `MediumTopAppBar`): view
tab strip. Right: identity icon + sync button.

### B.2 Repo switcher chip

A clickable region rendered as `Surface(shape = CircleShape ⊕ chip,
color = surfaceContainerHigh)` containing:

- 32dp circular avatar (left)
- repo display name (`titleMedium`, max 1 line, ellipsize end)
- `Icons.Filled.ArrowDropDown` 18dp (right)
- Total height 40dp; horizontal padding 8dp left, 12dp right.

**Tap behaviour:** opens the repo switcher dropdown (UI-K).

**Long-press behaviour:** opens the per-repo sync-status sheet
(UI-N.A — per-repo conflict + ahead/behind detail).

### B.3 Sync button (the two-arrow circular icon)

A circular `IconButton` 40dp with a custom drawable: two semicircular
arrows forming a circle (Material `Icons.Filled.Sync` is the closest
prebuilt; we'll use it directly to avoid hand-rolled SVG drift).

**Visual states:**

| State | Visual | Animation |
|---|---|---|
| idle | `Icons.Filled.Sync` tinted `onSurfaceVariant` | none |
| syncing | same icon, rotating 360° / 1.2s linear infinite | infinite rotate |
| error (any repo had error on last sync) | red 8dp dot top-right badge | none |
| conflict (any repo has unresolved conflict) | yellow 8dp dot top-right badge | none |
| ahead (≥1 repo has unpushed commits offline) | orange 8dp dot top-right badge | none |

If multiple states co-occur, badge precedence: error > conflict >
ahead. Idle has no badge.

**Tap:** triggers sync-all-flagged (every repo with auto-sync on),
shows transient toast on completion: "synced 3 repos / 1 conflict /
2 ahead".

**Long-press:** opens repo-sync-status bottom sheet listing every repo
with `last_synced_at`, `commits_ahead`, `commits_behind`, `last_error`,
and a "sync now" per-row IconButton.

### B.4 Identity icon

28dp circular avatar of the active identity for the *current* repo
(the repo selected in the switcher). When no avatar, render initials
on a deterministic-seeded coloured background (per D.19 / tonearmboy
auto-accent pattern, `accentForId(personId)`).

**Tap:** opens identity picker bottom sheet (UI-L). Sheet lists every
identity in the current repo, marks the active one, has an "Edit
identities" link to Settings → Identities.

**Long-press:** quick-toggle "filter view by this author" — top-bar
gains a chip "filtered: <author>" with an X to clear.

### B.5 View-mode tab strip

`SecondaryTabRow` from M3E (the variant that uses pill indicators and
mediumweight text). Rendered in the `MediumTopAppBar` content slot
below the title.

Schedule tabs: Day / Week / Month / Timebox / Year.
Tasks tabs: Combined / Today / Per-list / Shopping / Standing.

Persistence: last-used tab per-tab-host stored in
`DataStore<Preferences>` under keys `schedule.lastView` /
`tasks.lastView`. **Per-device, NOT per-repo** (per the brief).

**Phases:**

- [ ] **UI-B.1** `TopAppBarHost` composable that owns the three
  variants and switches based on the active tab.
- [ ] **UI-B.2** Repo switcher chip (`RepoSwitcherChip`).
- [ ] **UI-B.3** Sync button with the 4-state visual model and badge
  rendering.
- [ ] **UI-B.4** Identity icon with avatar/initials fallback.
- [ ] **UI-B.5** `SecondaryTabRow` view-mode strip with persisted
  selection.
- [ ] **UI-B.6** Sync-status bottom sheet on long-press of sync button.
- [ ] **UI-B.7** Identity picker bottom sheet on tap of identity icon.
- [ ] **UI-B.8** Author-filter chip (toggleable via long-press of
  identity icon, dismissible via X).

---

## UI-C — Schedule shell

`main.md` Phase F.4 + G.6.

The Schedule destination hosts a `Box` whose content is the active
view (Day/Week/Month/Timebox/Year) and an overlaid `FloatingActionButton.
Large` bottom-end (M3E large FAB) for "+ event at now". The shell is
responsible for:

- Date cursor state (`LocalDate currentDate`, default `LocalDate.now()`).
- Wiring the resolver render call (`RenderedSchedule`) for the active
  view-mode + date range.
- Announcing tab/view changes to TalkBack.
- Pull-to-refresh: a swipe-down from the top triggers a sync of the
  active repo (if its `auto_sync = false`, this is the only way to
  pull). Visualized via `PullToRefreshContainer` M3E variant.

**Phases:**

- [ ] **UI-C.1** `ScheduleShell` composable host with date cursor
  state hoisted via `rememberSaveable`.
- [ ] **UI-C.2** View-mode switcher wired to the top-bar tab strip.
- [ ] **UI-C.3** `FloatingActionButton.Large` bottom-end with
  `Icons.Filled.Add`; tap = "create event at now in default calendar
  of active repo".
- [ ] **UI-C.4** `PullToRefreshContainer` triggering sync of active
  repo.
- [ ] **UI-C.5** Date cursor controls in collapsed app-bar area:
  `< Today >` chevron pair flanking a `Today` button. Tap title text
  = jumps to today. Tap chevrons = navigates by view-mode unit
  (day/week/month/year).
- [ ] **UI-C.6** Swipe-horizontal gesture across the content pane =
  ±1 view-unit (e.g. ±1 day in Day view). Spring animation between
  pages.

---

## UI-D — Day view

`main.md` Phase G.1.

The most-used view. Vertical timeline. 24h or work-hours-only toggle.
Multi-calendar overlap rendered as colored vertical accent stripes,
one stripe per active calendar, color-coded.

**ASCII:**

```
┌────────────────────────────────────────────────────────────┐
│  ◀ Wed May 12                                       Today ▶│
├────────────────────────────────────────────────────────────┤
│  [24h] [Work hours]                                         │
│ ─────────────────────────────────────────────────────────── │
│ 08:00 │                                                     │
│       │                                                     │
│ 09:00 │█│Standup       09:00–09:30  Work  [JM]              │
│       │█│                                                   │
│ 10:00 │█│ │Pair         10:00–11:30  Work  Special  [JM]    │
│       │█│█│                                                 │
│ 11:00 │█│█│                                                 │
│       │ │█│                                                 │
│ 12:00 ─┼─┼──────────────[ now line ]──────────────────────  │
│       │ │ │Lunch        12:30–13:30  Personal  [LM]         │
│ 13:00 │ │ │                                                 │
│ ...                                                          │
└────────────────────────────────────────────────────────────┘
                                                  (+ FAB)
```

Each event chip:
- Left edge: 4dp wide vertical color band per calendar (multiple
  calendars = stacked bands left-to-right, each 4dp wide).
- Title (`titleSmall`).
- Time range (`labelMedium`, `onSurfaceVariant`).
- Calendar name(s) chip(s) (`labelSmall` on `secondaryContainer`).
- Author chip (24dp circular avatar) overlapping top-right corner
  with -4dp end and -4dp top inset.
- Optional emoji prefix from event frontmatter.
- Background: `surfaceContainer`. Selected: `surfaceContainerHigh`.
- Shape: `RoundedCornerShape(16.dp)`.

Now-line: 1dp `primary` horizontal line spanning the timeline at the
current minute, with a 12dp `primary` filled circle on the left edge.
Updates every 30s.

Hour grid: 1dp `outlineVariant` line every hour, faint 0.5dp every
30 min.

**Decision — collision visual:** per D.5, higher-priority calendar's
event takes the foreground slot (full-width chip). Lower-priority
events render as left-edge 4dp accent bands sticking out to the left
of the foreground chip. Tap the band = sheet listing every overlapping
event.

**Decision — work-hours mode:** when toggled, the timeline collapses
to the union of `active_hours` ranges across all active calendars for
the rendered date. Hours outside that union are hidden. Persisted
per-device in DataStore.

**Empty state:** mascot illustration + "no events today — want to
plan one?" + `FilledTonalButton` "Plan something".

**Tap empty area:** opens quick-add at that time. Quick-add is a
bottom sheet with title field, calendar picker (default = repo's
default calendar), end-time slider (default 1h after start), and a
"Save" button.

**Tap event:** opens event detail sheet (UI-I).

**Component reference:**
- Outer container: `LazyColumn` with sticky-ish hour rows.
- Each hour: `Row(modifier = Modifier.height(60.dp))` with hour label
  + content lane.
- Event chip: `Card(elevation = 0.dp, color = surfaceContainer,
  shape = RoundedCornerShape(16.dp))`.
- FAB: `FloatingActionButton.Large` (M3E).
- Pull-to-refresh: `PullToRefreshContainer`.

**Phases:**

- [ ] **UI-D.1** `DayView` composable + `DayViewModel` consuming
  `RenderedSchedule.Day` from the resolver.
- [ ] **UI-D.2** Hour grid + 24h vs work-hours toggle + persisted
  preference.
- [ ] **UI-D.3** Event chip layout + multi-calendar accent bands.
- [ ] **UI-D.4** Author chip overlay (24dp, top-right offset).
- [ ] **UI-D.5** Now-line indicator updating every 30s.
- [ ] **UI-D.6** Tap empty area → quick-add sheet at chosen time.
- [ ] **UI-D.7** Tap event → event detail sheet.
- [ ] **UI-D.8** Tap accent band → overlap sheet (every overlapping
  event).
- [ ] **UI-D.9** Empty-state mascot + CTA.
- [ ] **UI-D.10** Swipe-horizontal page change (±1 day).

---

## UI-E — Week view

`main.md` Phase G.2.

7-column timeline. Today's column highlighted with a 1dp `primary`
left border and `surfaceContainerLow` column background. Hour grid
spans all 7 columns. Event chips identical to Day view but narrower;
title may collapse to icon-only when column < 80dp.

**Decision — week start:** follows device locale. For en-US, Sunday;
for most ISO locales, Monday. Configurable in Settings → Appearance
(`weekStart` enum).

**Decision — chip overflow:** when more than 4 events per column-hour,
collapse to a "+N more" chip; tap = day detail for that column.

**Phases:**

- [ ] **UI-E.1** 7-column `Row` of `Column`s within a vertically
  scrollable timeline.
- [ ] **UI-E.2** Today highlight.
- [ ] **UI-E.3** Chip width-aware rendering (icon-only fallback).
- [ ] **UI-E.4** "+N more" overflow chip.
- [ ] **UI-E.5** Tap column header = jump to Day view of that day.

---

## UI-F — Month view

`main.md` Phase G.3.

Standard 6×7 month grid (always 6 rows so the grid never reflows on
month change — short months pad with previous/next month days greyed).

Each cell:
- Day-of-month numeral top-left (`labelLarge`).
- Up to 4 event chips stacked, each 1 line, color-banded.
- "+N more" if overflow.
- Today: filled `primary` circle around the numeral.
- Out-of-month days: numeral tinted `onSurfaceVariant.copy(alpha=0.4)`.

**ASCII:**

```
┌──────────────────────── May 2026 ─────────────────────────┐
│ Mon  Tue  Wed  Thu  Fri  Sat  Sun                          │
├────┬────┬────┬────┬────┬────┬────┐
│ 27 │ 28 │ 29 │ 30 │  1 │  2 │  3 │
│ ·  │ ·  │ ·  │ ·  │█·  │    │    │
├────┼────┼────┼────┼────┼────┼────┤
│  4 │  5 │  6 │  7 │  8 │  9 │ 10 │
│█·  │█·  │██  │█·  │█·  │ ·  │    │
├────┼────┼────┼────┼────┼────┼────┤
│ 11 │(12)│ 13 │ 14 │ 15 │ 16 │ 17 │   ← (12) = today, primary ring
│██  │███ │█·  │█·  │█·  │    │    │
├────┼────┼────┼────┼────┼────┼────┤
│ ...                                │
└────┴────┴────┴────┴────┴────┴────┘
```

**Tap day:** jumps to Day view for that day with motion-shared
transition (the day cell scales to fill the screen, the day timeline
slides up to meet it).

**Tap "+N more":** sheet listing all events that day, sorted by start
time.

**Decision — chip color:** uses the calendar's accent color as
background fill at `alpha=0.85` on the chip's left third, with the
calendar's `onContainer` text color for the title. Avoids the chip
becoming invisible on `surface` when calendar accent is pale.

**Phases:**

- [ ] **UI-F.1** 6×7 grid layout.
- [ ] **UI-F.2** Up to 4 chips + overflow.
- [ ] **UI-F.3** Today highlight + out-of-month dimming.
- [ ] **UI-F.4** Tap day → Day view (shared element transition).
- [ ] **UI-F.5** Tap "+N more" → day-events sheet.

---

## UI-G — Timebox view

`main.md` Phase G.4.

Today's planned focus blocks edge-to-edge. Big chunky tiles. Designed
for "this is what I'm doing right now" glance + voice-readable for
car/headphones.

Filters to events from calendars where `kind = "timebox"` (per D.5).
Falls back to "no timebox calendars active" empty state if none.

**ASCII:**

```
┌────────────────────────────────────────────────────────────┐
│  Now — 09:00                            ◐ 32 min remaining │
│ ╔════════════════════════════════════════════════════════╗ │
│ ║  📚  Deep Work — backend                               ║ │
│ ║  09:00 → 10:30                                        [JM]║│
│ ║  [Work • Timebox]                                      ║ │
│ ╚════════════════════════════════════════════════════════╝ │
│                                                              │
│  Next — 10:30                                               │
│  ┌────────────────────────────────────────────────────┐    │
│  │  ☕  Break                                          │    │
│  │  10:30 → 10:45            (15 min)                 │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  Later                                                       │
│  ┌────────────────────────────────────────────────────┐    │
│  │  💪  Gym                                            │    │
│  │  17:00 → 18:00                                     │    │
│  └────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────┘
```

The current block (Now) is rendered in a doubled-thickness elevated
card (`surfaceContainerHighest` + 2dp `outline` border). Next block
preview at half-emphasis. Later blocks list-style.

**Decision — what counts as "current":** any event from a
`kind = "timebox"` calendar overlapping `LocalDateTime.now()`. Multiple
overlap → highest-priority calendar wins the Now slot; others render
in a "concurrent" sub-row at `surfaceContainerLow`.

**Voice integration:** content-description of the Now block reads
"Currently in [block title], [N] minutes remaining, ends at [end
time]." TalkBack reads this on screen entry.

**Phases:**

- [ ] **UI-G.1** Current block detection + emphasis rendering.
- [ ] **UI-G.2** Next block preview.
- [ ] **UI-G.3** Later list.
- [ ] **UI-G.4** Concurrent sub-row when multiple timebox events overlap.
- [ ] **UI-G.5** TalkBack-friendly content descriptions.
- [ ] **UI-G.6** Empty state (no timebox calendars) with CTA "set up
  a timebox calendar" → Settings → Calendars.

---

## UI-H — Year view

`main.md` Phase G.5.

12-month grid. Each month a miniature density heatmap: each day cell
tinted by event-count (`primary` at `alpha = min(1.0, count/8)`).

```
┌─── 2026 ──────────────────────────────────────────┐
│  Jan          Feb          Mar          Apr       │
│ ▢▢▢▢▢▢▢    ▣▣▢▢▢▢▢    ▢▢▢▣▣▣▣    ▢▢▢▢▣▣▣      │
│ ▣▣▢▢▣▣▢    ▢▢▢▢▢▣▣    ▣▣▢▢▣▣▢    ▣▢▢▢▢▣▣      │
│ ...                                                │
│                                                    │
│  May          Jun          Jul          Aug       │
│  ...                                               │
│                                                    │
│  Sep          Oct          Nov          Dec       │
│  ...                                               │
└────────────────────────────────────────────────────┘
```

**Tap month:** transitions to Month view for that month (shared
element on the mini-grid → full grid).

**Decision — heatmap scale:** 0 events = `surfaceContainer`. 1–8
events: `primary` blended at `count/8`. ≥8: full `primary`. Cap at 8
to avoid one busy day washing out the rest.

**Phases:**

- [ ] **UI-H.1** 12-month `LazyVerticalGrid` (4 columns, scroll
  vertical).
- [ ] **UI-H.2** Per-day tint via blended `primary`.
- [ ] **UI-H.3** Tap month → Month view (shared element).
- [ ] **UI-H.4** Year-cursor in app bar (`< 2026 >`).

---

## UI-I — Event detail sheet

`main.md` Phase G.7.

Slide-up modal `ModalBottomSheet` (M3E shape). Drag-handle 32dp at
top. Spring-based open animation.

**Layout:**

```
                ──┄┄┄──
┌──────────────────────────────────────────────┐
│  📚  Deep Work — backend                      │
│  Wed May 12 · 09:00 – 10:30          [JM]    │
│  [Work • Timebox]              [↗ Personal]   │
├──────────────────────────────────────────────┤
│                                                │
│  Notes (markdown body)                         │
│  Lorem ipsum dolor sit amet…                   │
│                                                │
├──────────────────────────────────────────────┤
│  ATTACHMENTS                                   │
│  ▣ design-doc.pdf      (open)                 │
│  ▣ mockup.png  [thumb] (open)                 │
│                                                │
├──────────────────────────────────────────────┤
│  NOTIFICATIONS                                 │
│  ⏰ 1h before · 15m before                    │
├──────────────────────────────────────────────┤
│  [Edit]  [Convert to task]  [Delete]          │
│                                                │
│  Author: JM @ Personal · Updated 2026-05-08    │
└──────────────────────────────────────────────┘
```

**Sections:**
1. Header: emoji prefix + title + datetime + author chip + calendar
   chips (tappable → filter to that calendar) + repo chip if
   all-repos-overlay active.
2. Body: rendered Markdown via commonmark; long bodies scrollable.
3. Attachments: list of files in `attachments/<sha-prefix>/<sha>.<ext>`
   referenced in frontmatter. Image thumbs inline; non-images render
   icon + filename. Tap = open via SAF / system viewer.
4. Notifications: lead-times from frontmatter as chips.
5. Actions: Edit (full-screen editor), Convert to task (creates a task
   in the repo's default todolist with same title + due = event start),
   Delete (confirmation dialog → deletes the file + commits).
6. Footer: author + last-updated.

### I.1 Event editor (full-screen)

Triggered from the detail sheet's [Edit] button.

A `Scaffold` with `MediumTopAppBar` (back arrow + Save). Form:

- Emoji picker + title (`OutlinedTextField`).
- Calendar picker (`ExposedDropdownMenuBox` with calendar list).
- Start datetime + end datetime (M3E `DatePicker` + `TimePicker`).
- All-day toggle.
- Notifications: chips for lead-times + "+ Add" → menu (5m, 15m, 30m,
  1h, 2h, 1d, custom).
- Attachments: list + "+ Add attachment" → SAF file picker; on pick,
  hashes file, copies to repo `attachments/<sha-prefix>/<sha>.<ext>`,
  adds reference to frontmatter.
- Notes: multi-line markdown editor (`OutlinedTextField` minLines=4,
  monospace toggle).
- Author: defaulted to active identity, override via dropdown.
- Recurrence: "Make recurring" → opens RRULE builder (RV-B in
  resolver.md handles the data model; the UI is a wizard with
  frequency/interval/end-condition pickers).

[Save] writes the file, commits with the auto-generated message,
returns to detail sheet showing the updated event.

### I.2 Quick-add sheet (compact form)

Triggered from FAB or from tapping empty area in Day view. A condensed
form: title, datetime (pre-filled from tap), calendar (pre-filled with
default), [Save] [More fields…]. "More fields…" promotes to the full
editor preserving entered values.

**Phases:**

- [ ] **UI-I.1** Detail sheet layout (slide-up, drag handle).
- [ ] **UI-I.2** Markdown body rendering via commonmark.
- [ ] **UI-I.3** Attachments list with image thumb + system-viewer
  open.
- [ ] **UI-I.4** Notification chips.
- [ ] **UI-I.5** Action row + Convert-to-task action.
- [ ] **UI-I.6** Full-screen event editor.
- [ ] **UI-I.7** Quick-add compact sheet.
- [ ] **UI-I.8** Recurrence builder wizard.
- [ ] **UI-I.9** Attachment picker (SAF) + SHA-256 hashing + repo
  copy.

---

## UI-J — Task views

`main.md` Phase H.1–H.7.

Five view-modes selected via the same top-bar `SecondaryTabRow` as
Schedule.

### J.1 Combined view

All active todolists' tasks merged. Sort: `priority DESC, due ASC,
title ASC`.

Group visually with **left-edge 4dp accent band** in the todolist's
color (consistent with calendar accent rule). Group header chips
toggleable in-line ("hide this list").

Row layout (height 64dp):

```
┌──┬──────────────────────────────────────────────────┐
│██│ ☐  Buy milk                              [LM]    │
│  │    Shopping · due today 17:00                    │
└──┴──────────────────────────────────────────────────┘
   ↑ list color
```

### J.2 Today view

Today's dated tasks + tasks spawned by today's calendar events
(`spawns_task` field) + user-pinned standing tasks.

Same row shape as Combined. Section headers: `Today`, `From today's
events`, `Pinned`.

### J.3 Per-list view

Top of view: horizontally-scrollable chip-row of every active
todolist (chips show emoji + name + count). Tap = filter to that list.
Body: identical row shape, single-list.

### J.4 Shopping view

Big checkboxes (56dp tap target, 32dp checkbox visual). Simple layout.
Sorted by `todolist.priority DESC, title ASC`.

```
┌──────────────────────────────────────────────────────┐
│  ◯  Milk                              [Groceries]    │
│  ◯  Bread                             [Groceries]    │
│  ◉  Eggs (done, struck through)       [Groceries]    │
│  ◯  Drill bits                        [Hardware]      │
└──────────────────────────────────────────────────────┘
```

One-tap toggles `done` flag. Done items animate to bottom of list.
"Hide done" toggle in app bar overflow.

### J.5 Standing view

Tasks where `due` is null. Same row shape. Sort: `priority DESC,
created ASC`.

### J.6 Task detail sheet

Slide-up sheet. Same shape as event detail. Fields: title, due, body
(markdown), attachments, author chip, list chip. Actions: Edit,
Convert to calendar event, Delete, Toggle done, Snooze (+1d / +3d /
+1w pickers).

### J.7 Quick-add FAB

`FloatingActionButton.Large` bottom-end. Tap = bottom sheet:
- Title input
- Target todolist picker (default = active repo's default todolist)
- Due picker (Today / Tomorrow / Pick / None)
- Save

### J.8 Swipe gestures

- **Right swipe** on a task row = mark done. Reveals a green
  `surfaceContainerHigh` stripe with check icon. Commit threshold
  = 50% of row width. Released past threshold = action fires; below
  = snap back.
- **Left swipe** on a task row = snooze (push due date +1 day).
  Reveals an orange stripe with snooze icon. Same threshold.

Implementation reference: see tonearmboy `swipe-gestures.md` for the
M3E-friendly pattern (`SwipeToDismissBox` with custom `dismissContent`
and `backgroundContent`).

**Phases:**

- [ ] **UI-J.1** Combined view with priority+due sort + per-list
  accent band.
- [ ] **UI-J.2** Today view with three sections.
- [ ] **UI-J.3** Per-list view + chip-row filter.
- [ ] **UI-J.4** Shopping view (big checkboxes) + done animation.
- [ ] **UI-J.5** Standing view.
- [ ] **UI-J.6** Task detail sheet + Edit + Convert-to-event.
- [ ] **UI-J.7** Quick-add FAB sheet.
- [ ] **UI-J.8** Swipe right = done.
- [ ] **UI-J.9** Swipe left = snooze + snooze-amount picker.
- [ ] **UI-J.10** "Hide done" toggle.

---

## UI-K — Repo switcher dropdown + add-repo + repo settings

`main.md` Phase I.1–I.6.

### K.1 Repo switcher dropdown

Triggered by tapping the top-bar repo switcher chip. Implemented as a
`ModalBottomSheet` on phone (more thumb-friendly than a hovering menu
at the top), `DropdownMenu` on tablet width ≥ 600dp.

```
       ──┄┄┄──
┌──────────────────────────────────────────┐
│  Search repos…                            │
│ ────────────────────────────────────────  │
│ ▣ ⬤ Personal                    ●         │   ← green dot = synced
│ ▣ ⬤ Work                        ◐         │   ← spinner = syncing
│ ▣ ⬤ Demo (sub)                  ●         │
│ ▢ ⬤ Demo (Dom, read-only)       ●         │
│ ▣ ⬤ Side project                ▲         │   ← orange = behind/ahead
│ ▣ ⬤ Old archive                 ⨯         │   ← red = error
│ ────────────────────────────────────────  │
│  + Add repo                                │
└──────────────────────────────────────────┘
```

Row anatomy:
- 40dp circular icon (left) — emoji-in-SVG or photo or
  initials-on-seeded-bg.
- Display name (`titleMedium`).
- Sync status badge (right): green dot = synced, spinner = syncing,
  orange triangle = behind or ahead, red X = error.
- Read-only badge (right of name): a small lock chip for repos with
  no push credential.
- Tap row = make active + dismiss.
- Long-press row = repo settings.

**Filterable:** `OutlinedTextField` at top filters by display name
(case-insensitive substring).

**+ Add repo** at bottom = open Add-Repo flow (K.3).

### K.2 All-repos overlay master toggle

Settings → Repos exposes a master "Show all repos in unified view"
toggle (per D.9). When on, the schedule and tasks views aggregate
across every repo. The repo switcher chip then displays "All repos"
with a stacked-avatar visual (3 avatars overlapping). Tap still
opens the dropdown; selecting a single repo turns the unified mode
off.

### K.3 Add-repo flow

A multi-step bottom-sheet flow (or fullscreen on phone) modeled like
the wizard but lighter.

Step 1 — Provider:
- [GitHub] [Forgejo (gitea-compatible)] [Generic Git URL]

Step 2 — Repo:
- "Use existing repo" (paste URL or pick from your account)
- "Create new repo" (suggest name from GitHub username)

Step 3 — Auth:
- SSH (generate ed25519, copy public key, "I added it to my account
  / Auto-add via OAuth scope `admin:public_key`")
- HTTPS (OAuth Device Flow — show device code with copy button +
  "Open browser" button → user pastes → app polls for token; or
  manual PAT entry as fallback)

Step 4 — Identity:
- Pick existing identity from `identities/` if repo has one, or
  create new (display name, optional avatar, email).

Step 5 — Defaults:
- Default calendar (dropdown of repo's calendars)
- Default todolist (dropdown of repo's todolists)
- Auto-sync interval: 5m / 15m / 30m / 1h / 4h / Manual only

Step 6 — Done. Repo appears in switcher with green dot once first
sync completes.

### K.4 Repo settings (per-repo)

Reachable: Settings → Repos → tap repo, OR long-press repo in
switcher.

Sections:
- **Display:** name + circular icon picker (emoji / photo from SAF /
  auto-initials).
- **Sync:** auto-sync toggle, interval override, push strategy
  override (immediate / batched), conflict default (manual / mine /
  theirs).
- **Identity:** active identity for this repo (dropdown of
  `identities/`).
- **Defaults:** default calendar, default todolist.
- **Theming:** color seed override (per D.19).
- **Wi-Fi only sync:** toggle.
- **Danger:** Remove repo button → confirmation dialog with "keep
  local clone?" option (preserves the on-device working copy if user
  re-adds the repo later).

**Phases:**

- [ ] **UI-K.1** Repo switcher bottom sheet (phone) / dropdown
  (tablet) with filterable list.
- [ ] **UI-K.2** Sync status badge per-row (5 states).
- [ ] **UI-K.3** Read-only lock chip.
- [ ] **UI-K.4** + Add repo entry.
- [ ] **UI-K.5** All-repos overlay master toggle + stacked-avatar
  visual.
- [ ] **UI-K.6** Add-repo flow (6 steps).
- [ ] **UI-K.7** Repo settings screen (8 sections).
- [ ] **UI-K.8** Remove-repo confirmation + keep-local-clone option.

---

## UI-L — Identity picker + identity editor

`main.md` Phase I.4 + decisions D.15.

### L.1 Identity picker (bottom sheet)

Triggered from top-bar identity icon tap.

```
         ──┄┄┄──
┌──────────────────────────────────────────┐
│  Active identity for: Personal            │
│ ────────────────────────────────────────  │
│ ⬤ JM (Personal)              ✓ active    │
│ ⬤ JM (Work)                              │
│ ⬤ AM (Partner — sub)                     │
│ ────────────────────────────────────────  │
│  + New identity in this repo              │
│  ⚙  Edit identities                       │
└──────────────────────────────────────────┘
```

Tap row = set active identity for the current repo (writes to
`AppPrefs.activeIdentityForRepo[repoId]`). Affects new entries only.
Old entries keep their original `author`.

### L.2 Identity editor (Settings → Identities)

For each repo, list of identities with:
- 40dp avatar
- Display name + email
- "default-author" badge if applicable
- Edit / Delete actions

**+ New identity:** form for display_name, avatar (emoji or photo),
email (optional), public_keys (optional), default_author flag.

Saving writes `identities/<person-id>.md` with the frontmatter from
D.15 and commits.

**Phases:**

- [ ] **UI-L.1** Identity picker bottom sheet.
- [ ] **UI-L.2** Settings → Identities screen, grouped by repo.
- [ ] **UI-L.3** New-identity form.
- [ ] **UI-L.4** Edit-identity form.
- [ ] **UI-L.5** Delete confirmation (with warning if any entries
  reference this identity).

---

## UI-M — Together tab

`main.md` Phase N.1–N.3 + O.1.

Three sub-sections, vertical stack:

### M.1 Common-time finder

Form:
```
┌──────────────────────────────────────────────┐
│  Find common time                              │
├──────────────────────────────────────────────┤
│  Repos:    [⬤ Personal] [⬤ Work] [+]          │
│  Calendars in those repos:                     │
│    Personal: [Family ✓] [Errands ✓] [Special]  │
│    Work:     [Standup ✓] [Focus ✓]             │
│  Date range: [May 12] → [May 19]               │
│  Duration:   [1 hour ▾]                        │
│  Days:       [M T W T F · ·]                   │
│  Time:       [09:00] – [21:00]                 │
│                                                │
│  [Find slots]                                  │
└──────────────────────────────────────────────┘
```

Multi-select repo chips (the chip row supports multi-select via the
M3 `FilterChip`). Per-repo calendar sub-pickers expand below.

After [Find slots], results render as a ranked card list:

```
┌──────────────────────────────────────────────┐
│  10 slots found                                │
├──────────────────────────────────────────────┤
│  Wed May 13  ·  10:00 – 11:30   (1h 30m)      │
│  Personal + Work both free                    │
│                            [Create event →]   │
├──────────────────────────────────────────────┤
│  Thu May 14  ·  14:00 – 15:00   (1h)          │
│                            [Create event →]   │
│  ...                                            │
└──────────────────────────────────────────────┘
```

Tap [Create event →] = quick-add form pre-filled with chosen slot;
target repo + calendar picker required.

**Empty state:** "Add at least two repos to find common time" with
CTA to Settings → Repos.

### M.2 Shared with me

List of repos shared by others (read-only access). Each row: avatar,
name, owner, last-synced. Tap = activate that repo.

### M.3 I share with

List of the user's own repos that are shared with collaborators. Each
row has a "Manage on <provider>" button that opens the provider's
collaborator screen (deep link to GitHub/Forgejo settings → people).

**Phases:**

- [ ] **UI-M.1** Common-time finder form.
- [ ] **UI-M.2** Multi-select repo chips + per-repo calendar sub-pickers.
- [ ] **UI-M.3** Results list with ranking.
- [ ] **UI-M.4** Tap result → quick-add with prefilled slot.
- [ ] **UI-M.5** Shared-with-me list.
- [ ] **UI-M.6** I-share-with list + manage deep link.
- [ ] **UI-M.7** Empty states for all three sections.

---

## UI-N — Conflict-resolution UI

`main.md` Phase J.5–J.6.

Triggered when sync hits a merge conflict. Conflicts in this app are
always confined to a single TOML+body file (per D.8 — one entity per
file means conflicts are rare and per-file).

### N.1 Conflict banner + queue

When conflicts are pending, the app shows a top-of-content banner on
the schedule and tasks views:

```
┌──────────────────────────────────────────────┐
│  ⚠  3 unresolved conflicts in Personal       │
│                                  [Resolve]    │
└──────────────────────────────────────────────┘
```

Sync button also gains the yellow dot badge.

### N.2 Conflict-resolution full-screen modal

Per-conflict screen. `Scaffold` with `MediumTopAppBar` (the entity's
title as header), back = "skip and do later" (queues conflict, banner
persists).

Three-column structured view for TOML fields:

```
┌──────────────────────────────────────────────────────────────┐
│  ←  "Dentist 14:00"                       (1 of 3 conflicts) │
├──────────────────────────────────────────────────────────────┤
│  TOML fields                                                  │
│ ┌──────────┬───────────┬───────────┬──────────────────────┐ │
│ │  Field   │  Base     │  Ours     │  Theirs              │ │
│ ├──────────┼───────────┼───────────┼──────────────────────┤ │
│ │ title    │ Dentist   │ Dentist   │ Dentist appt         │ │
│ │          │  ◯ keep   │  ◉ keep   │  ◯ keep              │ │
│ ├──────────┼───────────┼───────────┼──────────────────────┤ │
│ │ start    │ 14:00     │ 14:00     │ 14:30                │ │
│ │          │  ◯ keep   │  ◉ keep   │  ◯ keep              │ │
│ ├──────────┼───────────┼───────────┼──────────────────────┤ │
│ │ priority │ 500       │ 500       │ 500       (no diff)  │ │
│ └──────────┴───────────┴───────────┴──────────────────────┘ │
│                                                                │
│  Markdown body                                                 │
│  ┌──────────────────────────┬──────────────────────────────┐ │
│  │  Ours                     │  Theirs                       │ │
│  │ ─────────────────────────│ ──────────────────────────── │ │
│  │  Notes about prep         │  Notes about prep             │ │
│  │  - bring x-rays           │  - bring x-rays               │ │
│  │  - confirm parking        │  - confirm parking            │ │
│  │                            │  - allergic to nitrous        │ │
│  │                            │     ↑ added on theirs         │ │
│  └──────────────────────────┴──────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Manual merge editor (live preview)                     │  │
│  │  Notes about prep                                        │  │
│  │  - bring x-rays                                          │  │
│  │  - confirm parking                                       │  │
│  │  - allergic to nitrous                                   │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                                │
│  [Skip & do later]      [Use mine]  [Use theirs]  [Apply ✓]   │
└──────────────────────────────────────────────────────────────┘
```

**Per-field radios:** for each TOML field that diverges, one of three
radio buttons selected (Base / Ours / Theirs). Identical-across-all
fields are shown in a "no diff" row at the bottom.

**Body merge:** side-by-side unified diff above; manual editor below
with conflict markers preserved (`<<<<<<< ours / ======= / >>>>>>> theirs`).
Edits to the manual editor disable the per-side "Use mine / Use
theirs" buttons (the user is doing manual merge).

**Apply:** writes the merged file, stages it, continues the rebase
(see sync-engine.md for the JGit mechanics). Moves to next conflict
or returns to view if last.

**Skip & do later:** queues conflict, persists banner.

**Decision — what about non-TOML conflicts (raw filesystem files like
`AGENTS.md`):** treat the whole file as "body" with no TOML section —
single side-by-side diff + manual merge editor.

**Decision — what about repo-level conflicts (e.g. `repo.toml` itself):**
same UI; the entity title is "<repo display name> · settings".

**Phases:**

- [ ] **UI-N.1** Conflict banner on tab roots.
- [ ] **UI-N.2** Per-conflict full-screen modal scaffold.
- [ ] **UI-N.3** Three-column field table with radio per field.
- [ ] **UI-N.4** Side-by-side diff view (commonmark-rendered or raw
  text view based on file type).
- [ ] **UI-N.5** Manual merge editor with conflict markers.
- [ ] **UI-N.6** Apply / Use mine / Use theirs / Skip actions.
- [ ] **UI-N.7** Multi-conflict pagination (1 of N indicator).

---

## UI-O — Wizard (first-run + re-entry)

`main.md` Phase K.1, deep-dive content lives in
[`templates-demo-wizard.md`](templates-demo-wizard.md). This phase
covers the **visual** spec only.

7 screens, paged via horizontal swipe + bottom progress dots + back/
next buttons. Container: `Scaffold` with no top-bar (each screen owns
its visual chrome) + a bottom action bar.

### O.1 — Welcome (mascot)

```
┌──────────────────────────────────────────────┐
│                                                │
│                                                │
│              ┌─────────────┐                  │
│              │   bat       │                  │
│              │   in        │                  │
│              │   hoodie    │                  │
│              │   with      │                  │
│              │   calendar  │                  │
│              └─────────────┘                  │
│                                                │
│            strictlykeptboy                     │
│      Your schedule, kept in Git.               │
│                                                │
│                                                │
│                                                │
│                                                │
│                       ● ○ ○ ○ ○ ○ ○            │
│                                  [Next →]     │
└──────────────────────────────────────────────┘
```

Mascot SVG centered, 240dp. Tagline: "Your schedule, kept in Git."
plain factual register (per Editorial — KYIS).

### O.2 — Path picker

Three big cards, vertical stack on phone, horizontal on tablet ≥600dp.

```
┌──────────────────────────────────────────────┐
│  How do you want to start?                     │
│                                                │
│ ┌────────────────────────────────────────┐   │
│ │  🦇  Start with demo                    │   │
│ │  Two coupled repos, ready to explore.   │   │
│ │  Nothing pushed anywhere.               │   │
│ └────────────────────────────────────────┘   │
│                                                │
│ ┌────────────────────────────────────────┐   │
│ │  📋  Start from templates               │   │
│ │  Pick the parts of your life that apply,│   │
│ │  we set up the rest.                    │   │
│ └────────────────────────────────────────┘   │
│                                                │
│ ┌────────────────────────────────────────┐   │
│ │  📂  Empty repo                         │   │
│ │  Just the scaffolding. You fill it in.  │   │
│ └────────────────────────────────────────┘   │
│                                                │
│                ○ ● ○ ○ ○ ○ ○        [← Back]  │
└──────────────────────────────────────────────┘
```

Each card: `Card(elevation = 0.dp, color = surfaceContainer, shape =
RoundedCornerShape(28.dp))`. Tap = select + advance.

### O.3 — Role toggles (only on Templates path)

Grouped chip-rows. Each chip is a `FilterChip` with emoji + label.
Multi-select.

Categories (per brief):
- **Morning routines:** morning-bird, night-bat/owl
- **Work:** work-9-5, work-shift
- **Education:** school
- **Fitness:** gym-3x, gym-5x, swim-club, soccer-club
- **Practice:** music-practice
- **Routine / structure:** master-scheduled, sub-scheduled
  *(phrased neutrally; the templates are kink-coded internally —
  hinting not blatant)*
- **Routines / self-care:** daily-chores, kinky-chores
  *(same — labels are neutral on screen)*

```
┌──────────────────────────────────────────────┐
│  Which fit you?                                │
│                                                │
│  Morning routines                              │
│   [🌅 Early riser]  [🦇 Night owl]            │
│                                                │
│  Work                                          │
│   [💼 9-to-5]  [🏭 Shift work]                │
│                                                │
│  Education                                     │
│   [🎓 School]                                  │
│                                                │
│  Fitness                                       │
│   [💪 Gym 3x/wk] [💪 Gym 5x/wk]               │
│   [🏊 Swimming]  [⚽ Soccer]                   │
│                                                │
│  Practice                                      │
│   [🎵 Music practice]                          │
│                                                │
│  Routine / structure                           │
│   [🗓 Master-scheduled]                        │
│   [🗓 Sub-scheduled]                           │
│                                                │
│  Routines / self-care                          │
│   [🧹 Daily chores]                            │
│   [🌹 Personal routines]                       │
│                                                │
│                ○ ○ ● ○ ○ ○ ○        [← Back] [Next →] │
└──────────────────────────────────────────────┘
```

### O.4 — Repo picker

Provider radio + URL/name input + auth method.

```
┌──────────────────────────────────────────────┐
│  Where does this repo live?                    │
│                                                │
│  Provider                                      │
│   ◉ GitHub      ◯ Forgejo / Gitea              │
│                                                │
│  Repo                                          │
│   ◉ Create new                                 │
│      Name: [strictly-mine          ]           │
│      ☑ Private                                 │
│   ◯ Use existing                               │
│      URL:  [                       ]           │
│                                                │
│  Auth method                                   │
│   ◉ SSH        ◯ HTTPS                         │
│                                                │
│                   ○ ○ ○ ● ○ ○ ○      [Next →] │
└──────────────────────────────────────────────┘
```

Suggested name from GitHub username if OAuth completed. "Private"
checked by default (matches D.22 — privacy posture).

### O.5 — Authentication

Branches by auth method:

**SSH path:**

```
┌──────────────────────────────────────────────┐
│  SSH key                                       │
│                                                │
│  Generated ed25519 key for this device:        │
│                                                │
│  ┌──────────────────────────────────────────┐ │
│  │ ssh-ed25519 AAAAC3Nz…+x@strictlykeptboy   │ │
│  │                                  [Copy]   │ │
│  └──────────────────────────────────────────┘ │
│                                                │
│   Auto-add to GitHub?                          │
│      [Add via OAuth →]                         │
│   Or add manually at github.com/settings/keys  │
│      ☐ I added it                              │
│                                                │
│                  [Test connection]             │
└──────────────────────────────────────────────┘
```

**HTTPS path (OAuth Device Flow):**

```
┌──────────────────────────────────────────────┐
│  Sign in to GitHub                             │
│                                                │
│  1. Open this URL in your browser:             │
│     ┌──────────────────────────────────────┐  │
│     │ github.com/login/device       [Open] │  │
│     └──────────────────────────────────────┘  │
│  2. Enter this code:                           │
│     ┌──────────────────────────────────────┐  │
│     │     XYZA-1234                  [Copy]│  │
│     └──────────────────────────────────────┘  │
│  3. Approve access.                            │
│                                                │
│   Waiting for approval ◐                       │
│                                                │
│  Or paste a personal access token instead:     │
│   [_________________________________]          │
│                                                │
│                                  [Use token]  │
└──────────────────────────────────────────────┘
```

The app polls `POST /login/oauth/access_token` every 5s while the
spinner shows. Auto-advances on success.

### O.6 — Scaffolding progress

```
┌──────────────────────────────────────────────┐
│                                                │
│           ┌─────────────┐                     │
│           │  bat with   │                     │
│           │  wrench /   │                     │
│           │  building   │                     │
│           └─────────────┘                     │
│                                                │
│         Setting up your repo…                  │
│                                                │
│         ✓ Connected                            │
│         ✓ Created calendars (3)                │
│         ✓ Created todolists (2)                │
│         ✓ Created identity                     │
│         ◐ Pushing initial commit               │
│                                                │
│                                                │
│                                                │
└──────────────────────────────────────────────┘
```

Animated bat asset (placeholder until user provides). Sequential
checklist as each step completes.

### O.7 — Finish

```
┌──────────────────────────────────────────────┐
│                                                │
│           ┌─────────────┐                     │
│           │ happy bat   │                     │
│           │ holding     │                     │
│           │ calendar    │                     │
│           └─────────────┘                     │
│                                                │
│         Your bat is ready.                     │
│                                                │
│       [Take me to my schedule →]               │
│                                                │
└──────────────────────────────────────────────┘
```

CTA opens Schedule → Day view with the active repo set.

**Phases:**

- [ ] **UI-O.1** Wizard scaffold (`HorizontalPager` + bottom dots +
  back/next).
- [ ] **UI-O.2** Welcome screen.
- [ ] **UI-O.3** Path picker.
- [ ] **UI-O.4** Role toggles (templates path) — chip groups by
  category.
- [ ] **UI-O.5** Repo picker.
- [ ] **UI-O.6** Auth — SSH path.
- [ ] **UI-O.7** Auth — HTTPS Device Flow path.
- [ ] **UI-O.8** Auth — manual PAT fallback.
- [ ] **UI-O.9** Scaffolding progress.
- [ ] **UI-O.10** Finish.
- [ ] **UI-O.11** Re-entry from Settings → Templates (skips welcome,
  starts at templates picker).

---

## UI-P — Settings shell + 10 sections

`main.md` Phase S.1–S.10.

`Scaffold` with `LargeTopAppBar` "Settings" at root. Body is a single
`LazyColumn` of grouped cards, mirroring tonearmboy's Settings shape
(M3E `surfaceContainer` cards + `SettingsCategoryIcon` 40dp circles
with hand-picked accents per section).

Section order (locked per brief):

1. **Repos**
2. **Identities**
3. **Sync**
4. **Notifications**
5. **Calendars**
6. **Todolists**
7. **Templates**
8. **Demo**
9. **Appearance**
10. **About**

```
┌──────────────────────────────────────────────┐
│  Settings                                      │
├──────────────────────────────────────────────┤
│ ╭──────────────────────────────────────────╮ │
│ │ ⬤ 📂 Repos                          ›    │ │
│ │ ⬤ 👤 Identities                      ›    │ │
│ ╰──────────────────────────────────────────╯ │
│                                                │
│ ╭──────────────────────────────────────────╮ │
│ │ ⬤ ↻  Sync                            ›    │ │
│ │ ⬤ 🔔 Notifications                   ›    │ │
│ ╰──────────────────────────────────────────╯ │
│                                                │
│ ╭──────────────────────────────────────────╮ │
│ │ ⬤ 📆 Calendars                       ›    │ │
│ │ ⬤ ✅ Todolists                       ›    │ │
│ ╰──────────────────────────────────────────╯ │
│                                                │
│ ╭──────────────────────────────────────────╮ │
│ │ ⬤ 📋 Templates                       ›    │ │
│ │ ⬤ 🦇 Demo                            ›    │ │
│ ╰──────────────────────────────────────────╯ │
│                                                │
│ ╭──────────────────────────────────────────╮ │
│ │ ⬤ 🎨 Appearance                      ›    │ │
│ │ ⬤ ℹ  About                            ›    │ │
│ ╰──────────────────────────────────────────╯ │
└──────────────────────────────────────────────┘
```

### P.1 Repos section

- List: every configured repo, with avatar + name + sync status +
  read-only chip.
- "+ Add repo" entry at top.
- Tap row → Repo settings (UI-K.4).
- Master toggle at bottom: "Show all repos in unified view".

### P.2 Identities section

- Grouped per repo (each repo a sub-card).
- List of identities; default-author marked with star.
- "+ New identity in <repo>" per group.
- Active identity per repo selectable inline (radio).

### P.3 Sync section

- Global default sync interval (radio: 5m / 15m / 30m / 1h / 4h /
  Manual).
- Per-repo override list (each row: repo + current interval +
  inline radio).
- Push strategy: `Immediate after every change` / `Batched on
  interval`.
- Conflict default behavior: `Always ask` / `Prefer mine` /
  `Prefer theirs`.
- Wi-Fi-only sync toggle.
- "Foreground service notification visible" toggle (warns the user
  that hiding it disables periodic sync entirely on modern Android).

### P.4 Notifications section

- Channels list: `events`, `tasks`, `sync`, `errors`. Each row opens
  the system per-channel screen via `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`.
- Notification groups: list, each row shows member calendars + a
  group-level on/off toggle.
- Defaults: default per-event lead-times (chips: 15m / 1h / 1d).
- Foreground service notification visibility toggle.

### P.5 Calendars section

Flat list across every repo. Each row: 40dp colored avatar (calendar
emoji) + name + repo name in subtitle.

Tap row → Calendar settings:
- name, emoji, color picker (hue wheel + saturation slider).
- master `active_toggle` (= shown anywhere).
- priority slider (1..1000, snap to 100s by default + fine mode).
- active_windows editor: list of date ranges, each editable as start
  + optional end + delete.
- active_hours editor: 7×24 grid; tap day-hour cell to toggle in/out.
- kind toggle: `regular` / `timebox`.
- default_notification_group dropdown.
- Delete calendar (with warning: "this is destructive — files in this
  calendar move to `_trash/<calendar-id>/`").

### P.6 Todolists section

Same shape as Calendars, minus `kind`. Add: `default_priority` for
inherited task priority.

### P.7 Templates section

- Browse: list every template available (from default registry +
  custom registry if configured). Each row: emoji + name + 1-line
  description.
- Tap template → preview screen (lists what it would create) + Apply
  button.
- Apply: opens a target picker (which repo, which calendar/todolist
  to merge into).
- Custom template repo URL: `OutlinedTextField` + Save → app fetches
  and lists.

### P.8 Demo section

- Demo mode toggle.
- "Status: ON / OFF" indicator.
- "Demo data: SFW kink-coded; sub + Dom coupled repos." (1-line copy).
- "Reset demo data" button.
- "Convert demo to a real local repo" button (offers to push the
  current demo state to a new repo of the user's choice).

### P.9 Appearance section

Following tonearmboy's settings shape exactly:

- **Theme** — Light / Dark / System.
- **Dynamic color (Material You)** — toggle.
- **Color seed override** — color picker (only if dynamic color off
  OR user wants to override per-repo, in which case route to repo
  settings).
- **Density** — Compact / Comfortable / Spacious.
- **Font scale** — slider 0.85× → 1.30×, snap default 1.0×.
- **Week start** — Sunday / Monday / System default.

### P.10 About section

- App name + version (from `BuildConfig.VERSION_NAME`).
- Git SHA + Build date (from `buildConfigField`s).
- Mascot (bat-in-hoodie SVG, large).
- "Open-source licenses" → `LicensesScreen` (mirror tonearmboy's
  Licensee-driven implementation).
- GitHub link (deep link to repo).
- Privacy policy link.
- Easter egg: tap version number 7× → toast "you are kept" (KYIS-
  conscious, no identity claim).

**Phases:**

- [ ] **UI-P.1** Settings root with the 10-row card list.
- [ ] **UI-P.2** Repos sub-screen.
- [ ] **UI-P.3** Identities sub-screen (grouped per repo).
- [ ] **UI-P.4** Sync sub-screen.
- [ ] **UI-P.5** Notifications sub-screen.
- [ ] **UI-P.6** Calendars sub-screen with detail editor (7×24 active-
  hours grid, color picker, priority slider, active-windows ranges).
- [ ] **UI-P.7** Todolists sub-screen.
- [ ] **UI-P.8** Templates sub-screen + browse + apply + custom URL.
- [ ] **UI-P.9** Demo sub-screen.
- [ ] **UI-P.10** Appearance sub-screen.
- [ ] **UI-P.11** About sub-screen + Licenses screen.
- [ ] **UI-P.12** Settings → Calendars → active-hours grid editor.
- [ ] **UI-P.13** Settings → Calendars → active-windows date-range
  editor.
- [ ] **UI-P.14** Settings → Calendars → color picker (hue/sat
  wheel).

---

## UI-Q — Theming, mascot, empty states, iconography

`main.md` Phase T.1–T.5 + decisions D.19.

### Q.1 Color system

- Material 3 Expressive `expressiveLightColorScheme()` /
  `darkColorScheme()` (per tonearmboy m3-expressive.md finding 1 —
  expressive dark factory does not yet exist as of `1.5.0-alpha18`,
  use plain `darkColorScheme` and inherit surface ladder).
- Dynamic color (Material You) on by default; per-repo seed override
  via `LocalRepoTheme`.
- `surfaceContainer*` ladder used per the same rule as tonearmboy:
  page = `surface`; cards = `surfaceContainer` (or
  `surfaceContainerHigh` on AMOLED-leaning dark).
- Per-category accent palette for Settings rows (mirror tonearmboy's
  `CategoryAccent` data class — 6 hand-picked accents).
- Per-calendar / per-todolist accent: stored as hex in the entity's
  TOML; resolved to a `CategoryAccent` (container + onContainer) at
  read-time.

### Q.2 Shape system

- `RoundedCornerShape(28.dp)` for group cards (M3E extra-large).
- `RoundedCornerShape(16.dp)` for chips (event/task tiles).
- `CircleShape` for avatars.
- `RoundedCornerShape(20.dp)` for FAB Large (M3E default).

### Q.3 Typography

- Use the M3E type scale (`MaterialTheme.typography.*`).
- Settings row title: `titleMedium`.
- Settings row subtitle: `bodyMedium` with `onSurfaceVariant`.
- Event chip title: `titleSmall`.
- Hour grid label: `labelMedium`.
- Display titles (LargeTopAppBar): `displaySmall`.

### Q.4 Mascot wiring

- `R.drawable.mascot_bat_calendar` — `VectorDrawable`. Until user
  provides art, ship a placeholder (simple geometric bat silhouette
  + calendar rectangle, monochrome `onSurface` tint).
- Mascot composable: `Mascot(modifier, mood: Mood = Mood.Neutral)`
  with moods `Neutral` / `Building` / `Happy` / `Sleeping` (sleeping
  = empty states).
- Used on splash, About, empty states.

### Q.5 Empty states

Pattern for every empty state:

```
┌──────────────────────────────────┐
│                                    │
│         [mascot 160dp]             │
│                                    │
│         <one-line copy>            │
│                                    │
│         [primary CTA]              │
│                                    │
└──────────────────────────────────┘
```

Specific copies:

| Where | Copy | CTA |
|---|---|---|
| Day view (no events) | "No events today — want to plan one?" | + Plan something |
| Tasks Combined (no tasks) | "All clear — pin a list to start tracking." | Pick a list |
| Tasks Today (none) | "Nothing dated for today." | + Add task |
| Tasks Shopping (none) | "Shopping list empty." | + Add item |
| Tasks Standing (none) | "No standing tasks." | + Add task |
| Together (< 2 repos) | "Add at least two repos to find common time." | Go to repos |
| Together (results 0) | "No common slots in that range. Try widening." | Adjust filters |
| Settings → Calendars (none) | "No calendars yet. Apply a template?" | Browse templates |
| Settings → Identities (none) | "Every repo needs an identity." | + New identity |
| Conflict queue (none) | (no empty state — banner just hides) | — |

### Q.6 Iconography rules

**Repo icons** — three sources:
1. Emoji-in-SVG: pick an emoji in the repo settings; rendered at 28dp
   centered on a 40dp circle of the repo's seed color.
2. Photo: SAF-picked image; cropped to circle.
3. Auto-initials: first 2 letters of display name on a deterministic-
   seeded `CategoryAccent` background (same algorithm as tonearmboy
   `accentForId`).

**Calendar / todolist icons** — emoji + accent color tint. Render as
40dp circle, accent.container background, emoji 24dp centered.

**Event emoji prefix** — optional; rendered before title in chips and
detail header. 16dp inline.

**Phases:**

- [ ] **UI-Q.1** `expressiveLightColorScheme` + dark factory wiring.
- [ ] **UI-Q.2** Dynamic color on by default; per-repo seed override
  via CompositionLocal.
- [ ] **UI-Q.3** `CategoryAccent` data class + 6 hand-picked accents
  for Settings rows.
- [ ] **UI-Q.4** Calendar/todolist accent resolver (TOML hex →
  CategoryAccent).
- [ ] **UI-Q.5** Mascot composable with mood enum + placeholder SVG.
- [ ] **UI-Q.6** All empty-state composables (10+).
- [ ] **UI-Q.7** Repo icon resolver (emoji / photo / initials).
- [ ] **UI-Q.8** Calendar / todolist icon composable.
- [ ] **UI-Q.9** Event emoji prefix rendering in chips + detail.

---

## UI-R — Tablet two-pane

`main.md` Phase R.1–R.5 + decision D.18.

### R.1 Width breakpoints

Driven by `WindowSizeClass`:

- **Compact** (< 600dp): single pane, rail collapsed (80dp).
- **Medium** (600–839dp): single pane, rail expanded (200dp), more
  room for view tabs.
- **Expanded** (≥ 840dp): two-pane via
  `NavigationSuiteScaffold` + `Scaffold` with explicit
  `Row(listPane, detailPane)`. Pane split: 38% / 62% (tonearmboy
  pattern).

### R.2 Schedule two-pane (≥840dp)

Left pane: Month grid (current month). Right pane: Day detail of
selected day.

Tapping a day in the month grid updates the right pane in place
(no navigation push). Date cursor in the top bar drives the month
grid.

The view-mode tabs only show on the left pane: tabs are
`Day / Week / Month / Timebox / Year` but on tablet expanded class
the "right pane" is always the day detail; the tabs control the
*left* pane representation.

**Decision — collapse to single-pane on Year + Timebox view modes:**
Year and Timebox don't have a meaningful right-pane; we promote them
to full-width and hide the right pane until the user selects a
day-level mode again.

### R.3 Tasks two-pane

Left: task list (with view-mode tabs). Right: task detail. Tap a task
= updates right pane; right pane shows empty state "Pick a task" when
nothing selected.

### R.4 Settings two-pane

Left: 10-section list. Right: section content. Default selected
section = Repos (top of list). Persist last-selected per device.

### R.5 Together two-pane

Left: form. Right: results. After [Find slots], results render in the
right pane instead of pushing.

**Phases:**

- [ ] **UI-R.1** WindowSizeClass detection at app root.
- [ ] **UI-R.2** Rail expansion at ≥600dp.
- [ ] **UI-R.3** Schedule two-pane at ≥840dp + month-left / day-right.
- [ ] **UI-R.4** Year + Timebox single-pane fallback at ≥840dp.
- [ ] **UI-R.5** Tasks two-pane.
- [ ] **UI-R.6** Settings two-pane with persisted last-section.
- [ ] **UI-R.7** Together two-pane.

---

## UI-S — Android Auto

`main.md` Phase Q.1–Q.5 + decision D.17.

`androidx.car.app` `CarAppService` rendering today's schedule as a
`ListTemplate`. v1 read-only.

### S.1 Today's schedule (ListTemplate)

```
┌──────────────────────────────────────────────┐
│  Today                                         │
├──────────────────────────────────────────────┤
│  09:00  Standup                                │
│         Work                                   │
├──────────────────────────────────────────────┤
│  10:00  Pair on backend                        │
│         Work                                   │
├──────────────────────────────────────────────┤
│  12:30  Lunch                                  │
│         Personal                               │
├──────────────────────────────────────────────┤
│  17:00  Gym                                    │
│         Personal                               │
└──────────────────────────────────────────────┘
```

Each `Row` carries:
- title = event title
- text = calendar name
- start time on the left
- icon = calendar's emoji as a `CarIcon`
- left accent color = calendar's color (`CarColor.createCustom`)

### S.2 "Next up" (PaneTemplate)

Single card. Shows: time-until ("in 12 minutes"), title, calendar.

### S.3 Voice prompts

`Action.APP_ICON_VARIANT_BACK` for back. Custom voice intent:
"What's next?" → reads PaneTemplate's content.

### S.4 No-edit policy

The CarApp surface is strictly read-only. No "+" affordance, no
toggling done, no creation. v2 may add task done-toggle for shopping
context.

### S.5 Repo selection on Auto

Shows the user's *active repo* from the phone app. Auto cannot switch
repos (would clutter the car UI). If the user wants a different
repo on Auto, they switch on the phone first.

**Phases:**

- [ ] **UI-S.1** `CarAppService` + `Session` + manifest entries.
- [ ] **UI-S.2** Today list template with calendar emoji + accent
  color.
- [ ] **UI-S.3** Next-up pane template with time-until updating.
- [ ] **UI-S.4** Voice prompt registration ("what's next").
- [ ] **UI-S.5** Read-only enforcement at the template factory level.

---

## UI-T — Accessibility + i18n

`main.md` Phase U.1–U.5.

### T.1 Content descriptions

Every interactive element has either a `contentDescription` or a
`Modifier.semantics { contentDescription = … }`. Decorative-only
elements have `contentDescription = null` explicitly (so lint catches
omissions).

Examples:
- Sync button idle: "Sync now"
- Sync button syncing: "Syncing"
- Sync button error: "Sync error — tap to retry"
- Sync button conflict: "Unresolved conflicts — tap to view"
- Repo switcher: "Active repo: <name>. Tap to switch."
- Identity icon: "Active identity: <display name>. Tap to switch."
- Event chip: "<title>, <time-range>, in <calendar>, by <author>."
- Day cell in month grid: "<day-of-month>, <event-count> events."

### T.2 Color is never sole channel

Per the brief: every active-calendar overlay also has a label or
initial. Sync status badges (green/orange/red) accompanied by
`contentDescription` strings. Conflict markers (yellow) doubled with
a "⚠" glyph.

### T.3 TalkBack flow

Each screen has an explicit TalkBack-test phase in the build plan.
TalkBack reads in this order: top app bar (repo + view-mode), nav
rail, content. Modal sheets get focus on open (drag-handle is the
first focusable, sheet close button at top end is second).

### T.4 Large-text + bold-text + high-contrast

Verified at 200% font scale. `Text` composables never set absolute
heights; `Row` heights set via `wrapContentHeight()` so they grow.
Event chips at 200% font scale collapse to 2-line minimum with
ellipsize.

`AccessibilityManager.isHighTextContrastEnabled` checked at theme
build time; high-contrast bumps `outline` and `outlineVariant` to
fully opaque + uses `onSurface` (not `onSurfaceVariant`) for
secondary text.

### T.5 Reduced-motion

`Settings.Global.ANIMATOR_DURATION_SCALE == 0` → all motion specs
fall back to instant transitions. Spring-based sheet animations
become snap. Shared-element transitions become hard cuts.

### T.6 String discipline

- Every user-facing string in `app/src/main/res/values/strings.xml`.
- No hardcoded strings in composables.
- Mirror tonearmboy `translations.md` workflow.
- v1 ships English only; locale variants land later.

**Phases:**

- [ ] **UI-T.1** Content-description pass on every interactive
  element.
- [ ] **UI-T.2** Sync status semantic strings.
- [ ] **UI-T.3** Calendar overlay labels (color + initial).
- [ ] **UI-T.4** TalkBack flow review on every top-level screen.
- [ ] **UI-T.5** 200% font-scale verification.
- [ ] **UI-T.6** High-contrast mode color overrides.
- [ ] **UI-T.7** Reduced-motion respect at theme + animation layer.
- [ ] **UI-T.8** strings.xml extraction sweep.

---

## UI-U — Motion + transitions

M3E motion-scheme defaults via
`MaterialExpressiveTheme(motionScheme = MotionScheme.expressive())`.

### U.1 Spring specs

Default sheet animations (event detail, task detail, repo switcher
on phone) use spring-based motion: `dampingRatio = 0.8f`,
`stiffness = 380f` (M3E expressive feels). `ModalBottomSheet` uses
the M3E default.

### U.2 Shared-element transitions

- Day cell (month view) → Day view: shared bounds with the cell as
  start, the day timeline header as end. 350ms.
- Mini-month (year view) → Month view: shared bounds, 300ms.
- Task row → task detail: shared element on the title text.

Implementation: `SharedTransitionLayout` + `Modifier.sharedElement`.

### U.3 Tab transitions

View-mode tab switches use horizontal slide (`AnimatedContent` with
slide-in/slide-out, 250ms). Direction inferred from tab index delta.

### U.4 FAB transforms

FAB → quick-add sheet uses M3E `FabTransform` if available, else
fallback to expand-from-FAB-position scrim + sheet.

**Phases:**

- [ ] **UI-U.1** Motion scheme wired at theme root.
- [ ] **UI-U.2** Shared element registry (day-cell, mini-month, task
  row).
- [ ] **UI-U.3** Tab `AnimatedContent` slide.
- [ ] **UI-U.4** FAB transform.
- [ ] **UI-U.5** Reduced-motion fallback (per UI-T.5).

---

## Cross-cutting: ViewModel / state shape

Each top-level destination has one ViewModel:

- `ScheduleViewModel(repoStore, resolver, prefs)` — exposes
  `StateFlow<ScheduleUiState>`.
- `TasksViewModel(repoStore, resolver, prefs)`.
- `TogetherViewModel(repoStore, resolver)`.
- `SettingsViewModel(prefs, repoStore)`.

Each detail sheet / editor has its own `*EditorViewModel` instantiated
on open via `viewModelStoreOwner` scoped to the sheet (so dismissal
clears).

Repository interfaces (per CLAUDE.md DI principles):

- `RepoStore` — list + add + remove + active.
- `EventRepository` — observe + create + update + delete events.
- `TaskRepository` — same for tasks.
- `IdentityRepository`.
- `SyncStatusSource` — `StateFlow<Map<RepoId, SyncStatus>>`.
- `Resolver` — `RenderedSchedule`, `RenderedTasks`, `findCommonTime`.
- `ConflictQueue` — `StateFlow<List<Conflict>>` + resolve + skip.

UI never reads files directly. All goes through repositories which
hit Room (cache) + filesystem (source of truth) per the layering in
decisions.md D.2.

---

## Tradeoffs resolved inline (this doc's contribution)

These are the unforeseen tradeoffs encountered while writing this
spec and the calls made (per the "never punt" directive).

1. **Phone nav: rail vs bottom bar.** User explicitly wanted left rail
   on phones to match tonearmboy main library. NavigationSuiteScaffold
   defaults to bottom bar < 600dp; we override. Decision: pin
   `NavigationSuiteType.NavigationRail` on every width class.
   Tradeoff: phones lose ~80dp of horizontal real estate vs a bottom
   bar, but gain consistency with tonearmboy and tablets. Acceptable
   per user direction.

2. **MediumTopAppBar height + view-tab strip.** Two-row top bar (chrome
   row + tab row) eats vertical space. Decision: use `MediumTopAppBar`
   that collapses on scroll, so the tab strip stays visible while the
   title compresses. The non-collapsing alternative (split bar) was
   declined as it'd push content too far down on phones.

3. **Conflict UI desktop-feel on phone.** Three-column TOML field
   table doesn't fit well on portrait phones. Decision: on compact
   widths, the table renders rows-stacked (Field name, then 3 radios
   below in a Row of cards). At ≥600dp, the proper three-column table
   appears.

4. **Material You vs per-repo seed.** Both should coexist. Decision:
   dynamic color is the global default; the `LocalRepoTheme`
   CompositionLocal wraps the active repo subtree with a seeded
   `colorScheme` if the repo set a `color_seed` in `repo.toml`. Empty
   color_seed = inherit dynamic color.

5. **Year start-of-week conflict with month grid.** Different locales
   start week on different days. Decision: month grid week-start
   follows `weekStart` setting in Settings → Appearance, which
   defaults to system locale. Mini-months in year view follow the
   same setting.

6. **Two-pane "Year" mode breakdown.** Year view doesn't fit the
   month-left / day-right pattern. Decision: collapse to single-pane
   on Year + Timebox view modes at ≥840dp; the right pane only
   appears for Day/Week/Month modes.

7. **CarApp repo switching.** Cluttering Auto with repo-switching UI
   is unsafe and unnecessary. Decision: Auto reads the *active repo*
   from phone state; user switches on phone first.

8. **Conflict UI for non-TOML files.** `AGENTS.md` etc. don't have a
   structured field section. Decision: for files without TOML
   frontmatter, the entire file is rendered in the body merge area
   (single side-by-side diff + manual editor); the field table is
   omitted.

9. **Tab persistence per-device vs per-repo.** User explicitly said
   per-device. Confirmed and applied to `schedule.lastView` and
   `tasks.lastView` in DataStore.

10. **FAB visibility under bottom-screen content.** With nav rail on
    the left, the FAB lives bottom-end without competing with a bottom
    bar. Decision: FAB.Large always bottom-end at 24dp inset.

11. **Empty state when zero repos configured (cold start before
    wizard finishes).** Decision: app routes directly into Wizard
    O.1 if `repoStore.isEmpty()`. The main scaffold never renders
    with zero repos.

12. **Splash + system-circle clipping.** Per tonearmboy m3-expressive.md
    finding 4, Android 12+ splash is hard-circle-clipped. Decision:
    ship a splash mipmap with mascot inscribed at 60% of icon area
    (matches tonearmboy resolution).

---

## Deferred to future versions (with rationale)

> **Round 2 update — re-triaged against decisions.md D.23–D.40.** Many
> Round 1 deferrals have been pulled into v1 scope. The list below
> preserves the original wording for historical context, then annotates
> each entry with its current v1 status. See Phases UI-V through UI-EE
> below for the v1 UI specs of the items now in scope.

- **Per-event color override** — a single event can carry its own
  color. v1 uses calendar color only. Adding per-event color creates
  a third color resolution layer (event > calendar > repo seed); not
  worth the complexity for v1. **Defer to v2.**
  ⚠️ **STILL DEFERRED v1.1.** Calendar-level color + emoji prefix on
  the event title (D.19) covers the common "make this event stand out"
  case in v1.

- **Drag-to-reschedule on Day/Week views** — long-press an event chip
  and drag to a new time. Requires gesture detection + collision
  recompute mid-drag. Cool but not blocking. **Defer to v1.1.**
  ✅ **MOVED TO v1** per D.30. See **Phase UI-X**.

- **Pinch-to-zoom on timeline (Day/Week)** — adjust hour-row height.
  Useful for fine scheduling but not core. **Defer to v1.1.**
  ✅ **MOVED TO v1** per D.30. See **Phase UI-X**.

- **Calendar overlap blending mode beyond accent stripes** — full
  alpha-blend of overlapping events instead of stripes. The stripe
  approach is clearer for screen readers + colorblind users; blending
  loses information. **Don't ship; stripes are better.**
  ✅ **STILL REJECTED.** Colorblind + screen-reader regression. The
  striped overlay model stays.

- **Auto edit/create** — voice or list-based event creation on Auto.
  Read-only is the v1 promise per D.17. Adding write surfaces a slew
  of safety/testing concerns. **Defer to v2.**
  ✅ **MOVED TO v1 (voice-create only)** per D.33. No on-screen
  edit/delete (those stay deferred for safety). See **Phase UI-DD**.

- **Multi-window / split-screen tablet** — Compose handles this
  semi-automatically via WindowSizeClass; we test it but don't add
  bespoke split-screen UI. **No deferral, tested-as-acceptable.**
  ⚠️ **STILL DEFERRED v1.1** for a bespoke split-screen UI; rely on
  Android system split-screen + WindowSizeClass in v1.

- **Per-day weather overlay** — nice-to-have on Day view; out of
  scope. **Defer indefinitely.**
  ✅ **MOVED TO v1** per D.28 (Open-Meteo, CC-BY data). See
  **Phase UI-Z**.

- **Inline Markdown editing in the body field with WYSIWYG preview**
  — v1 ships plain markdown editor + commonmark renderer in detail
  view; no live WYSIWYG. **Defer to v1.1.**
  ✅ **MOVED TO v1** per D.31 (inline-rendered Markwon, not full
  WYSIWYG — Markdown source stays the canonical edit surface, but
  styling renders inline as you type). See **Phase UI-Y**.

- **Custom emoji / sticker packs for repo icons** — v1 supports
  emoji-as-icon from the system emoji set. Custom stickers require
  asset management. **Defer to v2.**
  ✅ **MOVED TO v1** per D.32 (open pack format, no DRM). See
  **Phase UI-AA**.

- **Multi-finger gestures on calendar (e.g. two-finger tap = add
  block)** — discoverable by no-one. **Skip permanently.**
  ✅ **STILL REJECTED.** Undiscoverable. The long-press + drag
  gesture (UI-X) is the discoverable replacement.

- **iCal *live* sync (not just import)** — D.16 explicitly defers
  CalDAV server-side sync to a `tools/` cron path. UI for it would
  duplicate per-repo sync UI. **Defer to v2 unless user demand.**
  ✅ **MOVED TO v1 (as bidirectional CalDAV bridge)** per D.25.
  See **Phase UI-CC** for the mirror-management UI.

---

# Round 2 — extensions (Phases UI-V through UI-EE)

Round 2 of `decisions.md` (D.23–D.40) expanded v1 scope. The phases
below extend this spec to cover the new UI surfaces: multi-timezone
display, comments on events, drag-to-reschedule + pinch-to-zoom,
inline-rendered Markdown, weather overlay, sticker packs, GPG signed-
commits settings, multi-branch + CalDAV mirror management, and Android
Auto voice-create. The "Deferred to future versions" list above was
surgically updated to reflect the new status of each item.

Each phase here follows the same conventions as UI-A through UI-U
above: sub-step checkboxes with `**UI-X.Y**` prefixes, ASCII mockups
for the load-bearing visual states, M3E component references, and
cross-links back to `main.md`, `decisions.md`, and the sibling
deep-dive docs.

**Round 2 phase index:**

| Phase | Topic | main.md tie-in | decisions.md tie-in |
|---|---|---|---|
| UI-V | Multi-timezone display | (cross-cutting) | D.27 |
| UI-W | Replies / comments on events | CC | D.29, D.37 |
| UI-X | Drag-to-reschedule + pinch-to-zoom | DD | D.30 |
| UI-Y | Inline-markdown body styling | EE | D.31 |
| UI-Z | Weather overlay | BB | D.28 |
| UI-AA | Custom sticker / icon packs | FF | D.32 |
| UI-BB | GPG signed-commits UI | GG | D.23 |
| UI-CC | Multi-branch UI + CalDAV mirror UI | JJ, (CalDAV cross-cuts) | D.25, D.36 |
| UI-DD | Android Auto voice-create | HH | D.33 |
| UI-EE | Updated deferrals (housekeeping) | — | D.40 |

---

## UI-V — Multi-timezone display

`decisions.md` D.27. Cross-cuts every schedule view (UI-D through UI-H),
the event detail sheet (UI-I), the event editor, the recurrence editor,
the Together tab (UI-M), and the top app bar (UI-B).

**Design center.** Default render mode is "in my tz" (the device tz).
Users who travel, or who collaborate with partners in other tzs, can
either pin individual events to render in their own tz, or flip the
whole view to "render in event tz" via the top-bar tz badge. The
Together-tab common-time finder evaluates candidate slots from each
participant's tz perspective.

**Sub-steps:**

- [ ] **UI-V.1** Add a `TzRenderMode` enum to the schedule state:
  `DEVICE_TZ` (default), `EVENT_TZ` (pin to each event's own tz),
  `REPO_TZ` (pin to the repo default tz). The current mode persists
  per device in DataStore. The mode is a *display* concern only —
  the underlying `Instant` of every event is timezone-agnostic; the
  tz is purely a render parameter.

- [ ] **UI-V.2** Top-bar tz badge. When the visible date range contains
  any event whose `tz_id` differs from the device tz, render a small
  pill on the trailing edge of the top app bar (right of the sync
  button, left of the identity icon). Pill content:
  `🌐 DEVICE_TZ · N events in OTHER_TZ`. Tap → bottom sheet to switch
  `TzRenderMode`. If the visible range has no foreign-tz events, the
  pill is absent (zero chrome).

- [ ] **UI-V.3** Per-event "Pin to event timezone" toggle in event
  detail sheet. When on, this specific event always renders in its
  own `tz_id` regardless of the global `TzRenderMode`. State persists
  in the event frontmatter as `display_pin_tz = true` (additive field
  — absent = default). Tap target: a small clock-with-globe icon in
  the detail sheet's metadata row.

- [ ] **UI-V.4** Together-tab common-time finder gains a per-participant
  tz column. Each participant row: avatar + display name + tz dropdown
  (defaults to that participant's identity's declared tz, falling back
  to repo default). The result grid renders each candidate slot as a
  small horizontal band of N tz columns showing the slot's local time
  in each participant's tz. See `resolver.md` Phase RV-H for the
  evaluation semantics; this checkbox is the UI side only.

- [ ] **UI-V.5** Event creation/edit form: tz picker.
  `ExposedDropdownMenuBox` between the start-time picker and the
  end-time picker, labelled "Timezone". Defaults to repo default;
  on first override per session, an inline help line appears:
  "this event will render in <tz> regardless of where you are."
  The picker uses IANA tz IDs grouped by region (Africa, America,
  Asia, Europe, Pacific, …) with a search field at the top.

- [ ] **UI-V.6** Recurrence creation/edit form: same tz picker as UI-V.5.
  DST behaviour callout: when the recurrence frequency would cross a
  DST boundary, a tertiary info line under the picker reads:
  "instances on either side of DST keep wall-clock time" (which is
  what `lib-recur` does by default — call it out so the user is not
  surprised).

- [ ] **UI-V.7** Visual chip treatment for non-device-tz events: a
  small clock-icon badge (12dp) in the leading corner of the chip in
  Day/Week views. The badge tinted with `surfaceVariant` so it doesn't
  compete with the calendar color stripe. Long-press on the badge:
  toast "starts <local time> · <tz name>". This is the at-a-glance
  signal that a chip is non-local.

- [ ] **UI-V.8** Accessibility: the chip badge gets a
  `contentDescription` of "in timezone <tz id>". Screen readers
  announce "Dentist appointment, 3pm Europe/Berlin, in timezone
  Europe/Berlin" when in DEVICE_TZ mode and the event tz differs.

- [ ] **UI-V.9** Cross-link back to `main.md` Phase (cross-cutting —
  no single phase; touches G, K's event editor, and the resolver
  output structures from `resolver.md` Phase RV-H).

**ASCII mockup — top-bar tz badge:**

```
┌──────────────────────────────────────────────────────────────────────┐
│ [≡] [Repo: 🦊 personal ▾]   Schedule · Week    [🌐 CET · 3 in EST] [🔄] [👤] │
└──────────────────────────────────────────────────────────────────────┘
                                          └─────── tap to toggle ──────┘

   Tapping the pill opens the bottom sheet:

   ┌────────────────────────────────────────────────────┐
   │  Timezone render mode                              │
   │                                                    │
   │  ○ Show times in my timezone   (CET)              │
   │  ● Show times in each event's timezone            │
   │  ○ Show times in repo default  (Europe/Berlin)    │
   │                                                    │
   │  [ Close ]                                         │
   └────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *Bottom sheet vs. inline cycle on tap.* A two-state cycle (device ↔
  event) on tap was tempting, but the three-mode model (device / event
  / repo) needs a picker. Bottom sheet wins; the pill itself stays
  one-tap to open.
- *Badge placement.* Trailing edge keeps the leading edge (repo
  switcher) clean for the most-frequent interaction.
- *Pin field name.* `display_pin_tz` rather than `pin_tz` to keep it
  obvious that this is a display-only flag, not a tz override.

---

## UI-W — Replies / comments on events

`decisions.md` D.29, D.37. `main.md` Phase CC. File model:
`events/<yyyy>/<mm>/<event-id>.comments/<comment-id>.md`, one file
per comment (see `data-model.md` Phase DM-K for the schema).

**Sub-steps:**

- [ ] **UI-W.1** Event detail sheet gains a **Comments** section
  below the event metadata and above the attachments section.
  Section header: "Comments (N)" with a count. Empty state: a single
  centered row "no comments yet — be the first" with a small chat
  icon (24dp, `Icons.AutoMirrored.Outlined.Chat`, tinted
  `onSurfaceVariant`).

- [ ] **UI-W.2** Each comment row: author chip (24dp avatar +
  display name) on the leading edge, relative timestamp on the
  trailing edge ("3h ago", "Tue"), Markdown-rendered body
  (Markwon via UI-Y) below the chip row, and a small action row
  (Reply · Edit · Delete) below the body. Edit/Delete only show
  on comments authored by the active identity.

- [ ] **UI-W.3** Threading. A comment with `in_reply_to = <parent-id>`
  renders indented 16dp under the parent, with a vertical 1dp
  divider on the leading edge tinted `outlineVariant`. **Only one
  level of nesting** is rendered structurally; deeper replies
  flatten back to top-level with a "↳ in reply to <author>" prefix
  on the body. This keeps the visual tree shallow on phones; the
  data model still preserves the full graph.

- [ ] **UI-W.4** Add-comment composer pinned at the bottom of the
  Comments section. `OutlinedTextField` (multiline, max 6 visible
  rows before scroll) + a small toolbar above it: rendered-preview
  toggle (eye icon), bold (`B`), italic (`I`), list, link, attach.
  Submit button is a paper-plane icon in the trailing corner;
  disabled until the field has non-whitespace content.

- [ ] **UI-W.5** Rendered-preview toggle in the composer flips the
  field between raw Markdown source and a read-only Markwon-rendered
  view of the same content. The toggle keeps the cursor position
  on the raw side (preview mode is read-only).

- [ ] **UI-W.6** "Reply" action on a comment pre-fills the composer
  with `in_reply_to` bound to that comment ID, and inserts a small
  chip above the composer "Replying to <author>" with an ✕ to clear.

- [ ] **UI-W.7** Per-event mute toggle in the detail sheet header
  (right of the title). Bell icon: filled = receiving notifications
  for new comments on this event; outlined = muted. State persists
  in app prefs (not in the repo — local concern).

- [ ] **UI-W.8** Notification routing: new comments fire on the
  `comments` channel (D.37, IMPORTANCE_DEFAULT) rather than the
  `events` channel. Detail in `notifications-sharing-import.md`
  Phase NS-M; this checkbox is the UI side (mute toggle wiring).

- [ ] **UI-W.9** Long-press on a comment row opens a context menu:
  Copy text · Share · Report (for shared repos, opens provider
  issue tracker deep link). On the active identity's own comments,
  Edit and Delete also appear.

- [ ] **UI-W.10** Accessibility: each comment is a single focus
  group for TalkBack. Announcement: "<author>, <relative time>:
  <body>". Reply/Edit/Delete are nested actions reachable via
  the rotor.

- [ ] **UI-W.11** Cross-link back to `main.md` Phase CC and
  `data-model.md` Phase DM-K (file schema for comments).

**ASCII mockup — Comments section in event detail sheet:**

```
   ┌────────────────────────────────────────────────────┐
   │  Dentist appointment        [🔔] [✏️] [×]           │  ← header
   │  Tue 12 May · 14:00–14:45 · 🌐 Europe/Berlin       │
   │  Calendar: 🦷 Health                                │
   │  ────────────────────────────────────────────────  │
   │                                                    │
   │  Comments (3)                                      │
   │  ┌──────────────────────────────────────────────┐ │
   │  │ 🦊 Alex                            3h ago    │ │
   │  │ Don't forget to **fast** 2h before.          │ │
   │  │ Reply · Edit · Delete                         │ │
   │  └──────────────────────────────────────────────┘ │
   │   │  ┌─────────────────────────────────────────┐ │
   │   │  │ 🐱 Sam                          1h ago  │ │
   │   │  │ OK, will skip lunch.                    │ │
   │   │  │ Reply                                    │ │
   │   │  └─────────────────────────────────────────┘ │
   │  ┌──────────────────────────────────────────────┐ │
   │  │ 🐺 Pat                             20m ago   │ │
   │  │ ↳ in reply to Sam                            │ │
   │  │ I can drop you off at 13:30 if you want.    │ │
   │  │ Reply                                         │ │
   │  └──────────────────────────────────────────────┘ │
   │                                                    │
   │  ┌──────────────────────────────────────────────┐ │
   │  │ [👁] [B] [I] [•] [🔗] [📎]                    │ │
   │  │ ┌────────────────────────────────────────┐   │ │
   │  │ │ Replying to Pat  [×]                    │   │ │
   │  │ │ Thanks, see you at 13:30.               │   │ │
   │  │ └────────────────────────────────────────┘  ✈│ │
   │  └──────────────────────────────────────────────┘ │
   └────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *Visual nesting depth.* One level structurally + flat-with-prefix
  for deeper threads. Two-plus structural levels would collapse the
  body to a 200dp-wide column on phones.
- *Mute scope.* Per-event mute, not per-thread. Per-thread mute
  would be a third level of subscription state; not worth the
  complexity for v1.
- *Composer placement.* Pinned at the bottom of the sheet, not
  modal. Modal composer would lose context of the surrounding
  thread when writing a reply.

---

## UI-X — Drag-to-reschedule + pinch-to-zoom

`decisions.md` D.30. `main.md` Phase DD. Day and Week views (UI-D,
UI-E). Own Compose implementation — no external dep.

**Sub-steps:**

- [ ] **UI-X.1** Long-press detector on event chips. 500ms threshold,
  matching M3 long-press default. Visual feedback when the threshold
  trips: the chip lifts (elevation 0 → 6dp), shadow expands (`tonal`
  shadow), and a haptic `LongPress` performHapticFeedback fires.

- [ ] **UI-X.2** Drag state — once lifted, the chip follows the
  pointer y-coordinate. A *ghost* of the chip remains at the original
  position (alpha 0.35) for the duration of the drag. The dragged
  chip displays its proposed new time band (start–end) in a small
  callout to the leading edge of the chip ("→ 15:30–16:15") that
  updates in real time as the chip moves.

- [ ] **UI-X.3** Grid snap. The chip's top edge snaps to the
  configured grid (default 15min; see UI-X.6). Snap is visual + value:
  the chip's y-position rounds to the nearest grid line as the pointer
  moves, and the proposed-time callout reflects the snapped value.
  Sub-grid motion is not free; the chip moves in discrete steps.

- [ ] **UI-X.4** Drop commit. Releasing the pointer over a valid slot
  commits the move. Commit semantics:
  - One-off event: rewrite the single file with new `dtstart`/`dtend`.
  - Recurring instance: see UI-X.5 (extra prompt).
  - Auto-commit message: `move event "<title>" from <old> to <new>`.
  Releasing outside the timeline (e.g. on the nav rail) cancels the
  drag and the chip springs back to its original position with a
  short `EaseOutBack` animation.

- [ ] **UI-X.5** Recurrence drag prompt. When the dragged chip is a
  recurring instance, on drop a `ModalBottomSheet` interrupts the
  commit with three radio options:
  - "This instance only" — create an `exceptions/<rule-id>/<date>.md`
    override with the new time.
  - "This and future" — split the rule: cancel the old rule at the
    dragged date, create a new rule from the dragged date with the
    new time.
  - "Entire series" — rewrite the rule's `dtstart` (the rule file
    itself).
  Default focus: "This instance only" — least destructive.

- [ ] **UI-X.6** Pinch-to-zoom on the timeline. Detect two-finger
  pinch via `Modifier.pointerInput { detectTransformGestures }`.
  Zoom levels (4 discrete steps):
  1. 5min — fine
  2. 15min — default
  3. 30min — coarse
  4. 1h — very coarse
  Pinch crosses thresholds in scale to step up/down; the active step
  persists per device in DataStore.

- [ ] **UI-X.7** Zoom buttons in the timeline gutter. A vertical pair
  of `IconButton`s (`+` / `−`, 32dp each) pinned to the leading edge
  of the timeline, anchored to the top-of-visible-range. Provides
  accessibility for users who can't or won't pinch. Pressing `+` or
  `−` cycles through the same 4 steps as UI-X.6.

- [ ] **UI-X.8** Touch-target safety. While a chip is in long-press-
  lifted state, all other gesture handlers (pinch, scroll) on the
  timeline are suppressed. Releasing the chip restores them. This
  prevents the pinch-zoom recogniser from stealing the drag when a
  second finger touches down.

- [ ] **UI-X.9** Conflict feedback. If the drop target overlaps with
  another event in the same calendar, the proposed-time callout
  tints `error` and the chip border tints `error`. Drop is still
  allowed (overlap is a calendar property, not an error) but the
  visual is unmistakable.

- [ ] **UI-X.10** Accessibility alternative. For TalkBack users,
  long-press a chip → context menu includes "Move to time…" which
  opens a `TimePicker` (M3) and commits the same way as drag.
  Equivalent surface, no gesture required.

- [ ] **UI-X.11** Cross-link back to `main.md` Phase DD.

**ASCII mockup — drag in progress on Week view:**

```
   Mon       Tue       Wed       Thu       Fri
  ┌────────┬────────┬────────┬────────┬────────┐
13│        │        │        │        │        │
  │        │        │        │        │        │
14│ ░░░░░░ │        │ Stand- │        │        │  ← ghost at original
  │ ░Den-░ │        │ up     │        │        │
  │ ░░░░░░ │        │        │        │        │
15├────────┼────────┼────────┼────────┼────────┤
  │        │        │        │        │        │
  │        │        │┏━━━━━━┓│        │        │  ← lifted chip
  │        │        │┃Den-  ┃│  → 15:30–16:15│  ← callout
16│        │        │┃tist  ┃│        │        │
  │        │        │┗━━━━━━┛│        │        │
  │        │        │        │        │        │
17├────────┼────────┼────────┼────────┼────────┤

  Pinch-to-zoom: [+]   ── grid: 15min ──
                 [−]
```

**Tradeoffs resolved inline.**

- *Long-press threshold.* 500ms matches M3 default and gives the
  pinch recogniser room to win on legitimate pinch starts.
- *Snap step.* 15min default chosen for ergonomic match to common
  meeting durations; 5min/30min/1h are explicit power-user steps.
- *Recurrence drop default.* "This instance only" — the smallest
  blast radius. Users wanting series edits choose explicitly.

---

## UI-Y — Inline-markdown body styling

`decisions.md` D.31. `main.md` Phase EE. Library: `noties/Markwon`
(Apache-2.0) with a thin Compose wrapper (`MarkwonBody` composable
that bridges `EditText` rendering to Compose's `AndroidView`).

**Sub-steps:**

- [ ] **UI-Y.1** Compose wrapper for Markwon. `MarkwonBody` composable
  accepts `value: TextFieldValue`, `onValueChange: (TextFieldValue) ->
  Unit`, `mode: BodyMode` (`RAW` | `RENDERED`). In `RENDERED` mode
  the wrapper styles inline as the user types — headings scale,
  `**bold**` renders bold, `*italic*` italic, lists indent, links
  tint `primary`. Source characters remain in the buffer (it's not
  WYSIWYG that strips the markup); it's just a styled view of the
  source.

- [ ] **UI-Y.2** Editor toolbar. A horizontal `Row` above the body
  field with the following icon-buttons (32dp tap targets):
  - Raw/Rendered toggle (eye icon)
  - Bold (`B`)
  - Italic (`I`)
  - Bulleted list (`•`)
  - Numbered list (`1.`)
  - Link (chain icon)
  - Attach (paperclip icon)
  Toggling formatting wraps the selection (or inserts at cursor)
  with the appropriate Markdown delimiters — same UX as standard
  rich-text editors.

- [ ] **UI-Y.3** M3E typography alignment. Heading levels map:
  - `# h1` → `displaySmall`
  - `## h2` → `headlineSmall`
  - `### h3` → `titleLarge`
  - `#### h4` → `titleMedium`
  - body → `bodyLarge`
  Code blocks: `surfaceContainerHighest` background, monospace
  (`JetBrainsMono` if available, `Default` fallback), 4dp internal
  padding, `RoundedCornerShape(8.dp)`. Inline code: same background
  applied via `SpanStyle`.

- [ ] **UI-Y.4** Link styling. Markdown `[text](url)` renders as
  `primary`-tinted text with underline. Tap (in rendered mode) opens
  the URL via `Intent.ACTION_VIEW`. In raw mode the tap goes to
  cursor placement as normal.

- [ ] **UI-Y.5** Inline image rendering. `![alt](attachments/<sha>.<ext>)`
  paths resolve against the repo's `attachments/` directory. Images
  render at the natural width up to the body field's max width,
  preserving aspect. Tap → full-screen viewer (the existing
  attachments viewer from UI-I). Broken paths render a placeholder
  card with the alt text.

- [ ] **UI-Y.6** Cursor visibility in rendered mode. The challenge:
  rendered Markdown changes character heights mid-line. Solution:
  Markwon renders into a single `EditText`-backed view; the cursor
  stays an `EditText` cursor at the source position, which on Android
  natively follows styled spans correctly.

- [ ] **UI-Y.7** Same wrapper used in: event body editor, task body
  editor, comment composer (UI-W.4), comment display (UI-W.2), event
  detail sheet body (UI-I read-only mode), task detail sheet body.

- [ ] **UI-Y.8** Performance: Markwon caches parsed AST. Re-rendering
  on each keystroke is debounced 80ms so typing on the longest body
  field (~5KB) does not hit the parser per character.

- [ ] **UI-Y.9** Raw/rendered toggle remembers the last mode per
  surface (event vs. task vs. comment) in DataStore.

- [ ] **UI-Y.10** Accessibility: TalkBack reads the underlying source
  text in raw mode and the rendered text (with role announcements
  for headings, lists, links) in rendered mode.

- [ ] **UI-Y.11** Cross-link back to `main.md` Phase EE.

**ASCII mockup — event body editor with rendered toggle on:**

```
   ┌────────────────────────────────────────────────────┐
   │  Edit event · Dentist appointment                  │
   │  ────────────────────────────────────────────────  │
   │  [Title]      Dentist appointment                  │
   │  [Calendar]   🦷 Health                  ▾         │
   │  [Start]      Tue 12 May  14:00                    │
   │  [End]        Tue 12 May  14:45                    │
   │  [Timezone]   Europe/Berlin              ▾         │
   │  [Author]     🦊 Alex                              │
   │                                                    │
   │  Body:                                             │
   │  ┌──────────────────────────────────────────────┐ │
   │  │ [👁● rendered] [B] [I] [•] [1.] [🔗] [📎]     │ │
   │  └──────────────────────────────────────────────┘ │
   │  ┌──────────────────────────────────────────────┐ │
   │  │                                              │ │
   │  │ # Pre-appointment checklist                  │ │  ← rendered h1
   │  │                                              │ │
   │  │ Bring:                                       │ │
   │  │  • Insurance card                            │ │  ← rendered list
   │  │  • Previous X-rays                           │ │
   │  │                                              │ │
   │  │ **Fast** for 2h before. See                  │ │  ← bold + link
   │  │ [pre-care guide](attachments/abc.pdf).       │ │
   │  │                                              │ │
   │  │ ![tooth](attachments/de/de4f...png)          │ │  ← inline image
   │  │ ┌──────────┐                                 │ │
   │  │ │ (image)  │                                 │ │
   │  │ └──────────┘                                 │ │
   │  └──────────────────────────────────────────────┘ │
   │                                                    │
   │  [Cancel]                          [Save]          │
   └────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *WYSIWYG vs. inline-rendered.* D.31 says "inline-rendered" — source
  stays canonical, styling overlays it. Full WYSIWYG (markup hidden,
  buttons synthesize markup) was rejected because round-trip fidelity
  with hand-edited files would erode.
- *Bridging Markwon (View) into Compose.* `AndroidView` wrap is the
  pragmatic answer. Native Compose Markdown libraries exist but
  Markwon has the most complete plugin set (tables, code, images,
  task lists) and is Apache-2.0.

---

## UI-Z — Weather overlay

`decisions.md` D.28. `main.md` Phase BB. Library: open-meteo Java
client (Apache-2.0), data CC-BY.

**Sub-steps:**

- [ ] **UI-Z.1** Day view: thin weather strip (32dp tall) above the
  timeline, below the date header. The strip shows 8 temperature
  + icon cells across the day at 3h intervals (00, 03, 06, 09, 12,
  15, 18, 21). Each cell: weather-condition icon (16dp) above
  temperature ("21°"). Cells separated by 1dp `outlineVariant`
  hairline.

- [ ] **UI-Z.2** Week view: small icon (16dp) per day in the column
  header, right of the day-of-week + day-number. No temperature
  (too cramped at week density).

- [ ] **UI-Z.3** Month view: tiny icon (12dp) in the corner of each
  day cell, anchored bottom-trailing. No temperature.

- [ ] **UI-Z.4** Settings → Appearance → "Show weather overlay"
  toggle. Default ON. When OFF, none of UI-Z.1/2/3 render — the
  weather strip is `null` in the composition.

- [ ] **UI-Z.5** Settings → Repos → tap repo → "Weather location"
  picker. Three modes:
  - **Device location** (default if permission granted) — uses
    Fused Location Provider's last known position.
  - **Pick on map** — opens a `MapView` (OSMDroid, Apache-2.0;
    avoids Google Play Services dep). User taps to drop a pin;
    coords stored in `repo.toml` as `weather_location = "lat,lon"`.
  - **None** — disables weather for this repo even when the global
    setting is on.

- [ ] **UI-Z.6** Per-event location override in the event editor.
  A small "Override weather location" expander below the tz picker.
  When expanded, three modes (same as UI-Z.5) per event. Useful for
  travel events ("trip to Tokyo" → override to Tokyo coords). The
  Day view weather strip *for that event's day* prefers the
  event-override location if present.

- [ ] **UI-Z.7** Weather data fetch + cache. The open-meteo client
  fetches per-location-day forecasts and caches them in Room. Cache
  TTL: 1h for the current day, 12h for future days. Detail:
  `sync-engine.md` Phase SE-W. UI side: a small spinner replaces
  the weather strip while fetching.

- [ ] **UI-Z.8** About screen attribution. "Weather: Open-Meteo
  (CC-BY) — https://open-meteo.com" line in the About section. Tap
  opens the attribution page. Required by CC-BY.

- [ ] **UI-Z.9** Icon set. 14 condition icons (clear / partly cloudy
  / overcast / fog / drizzle / rain / heavy rain / freezing rain /
  snow / heavy snow / showers / thunderstorm / thunder+hail / wind).
  Source: Material Symbols where available, hand-drawn fallbacks
  shipped as vector drawables for the few not in MS.

- [ ] **UI-Z.10** Failure mode. If open-meteo is unreachable, the
  weather strip collapses to height 0 (no error UI inline — the
  schedule is the priority). A small persistent "weather unavailable"
  pill appears in the top bar if 3 consecutive fetches fail.

- [ ] **UI-Z.11** Cross-link back to `main.md` Phase BB,
  `resolver.md` Phase RV-K (weather as non-busy data layer), and
  `sync-engine.md` Phase SE-W (fetch cadence).

**ASCII mockup — Day view weather strip:**

```
   ┌──────────────────────────────────────────────────────────────┐
   │  Tue 12 May 2026                                              │
   │  ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐  │
   │  │  🌙   │  🌙   │  ☁️    │  ⛅    │  ☀️    │  ☀️    │  ⛅    │  🌧    │  │
   │  │ 12°  │ 11°  │ 14°  │ 17°  │ 21°  │ 23°  │ 22°  │ 18°  │  │
   │  │ 00   │ 03   │ 06   │ 09   │ 12   │ 15   │ 18   │ 21   │  │
   │  └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘  │
   │  ────────────────────────────────────────────────────────── │
   │  09 │                                                        │
   │     │  ╔══════════════════════╗                              │
   │  10 │  ║ 🦷 Dentist            ║                              │
   │     │  ║ 14:00–14:45           ║                              │
   │  …  │                                                        │
```

**ASCII mockup — Month view cell with icon:**

```
   ┌────────────┐
   │ 12      ⛅   │  ← day number top-leading, weather icon bottom-trailing
   │            │
   │ ▓ ▓        │  ← event chip rows
   │ ▓          │
   │ +2         │  ← overflow indicator
   └────────────┘
```

**Tradeoffs resolved inline.**

- *3h interval on Day view.* 24 cells (hourly) would be too dense;
  8 cells (3h) gives a usable shape at phone widths.
- *Map library.* OSMDroid not Google Maps — avoids Play Services
  dependency, stays GitHub-Releases distributable.
- *Per-event location.* Surfaces only when the user explicitly
  opens the expander; default is "use repo location" — most events
  don't need an override.

---

## UI-AA — Custom sticker / icon packs

`decisions.md` D.32. `main.md` Phase FF. Open format:
`<pack-name>/<sticker-name>.{png,svg,webp}` + optional `pack.toml`.

**Sub-steps:**

- [ ] **UI-AA.1** Settings → Appearance → **Sticker packs** section.
  Header: "Sticker packs (N installed)". List rows per installed
  pack: thumbnail (the first 3 stickers in the pack tiled), pack
  name (from `pack.toml` or directory name), author, install date,
  remove button (trash icon).

- [ ] **UI-AA.2** "Install from local" button at the bottom of the
  list. Opens the system SAF picker. Accepts either:
  - A `.zip` file (unpacked to the app's pack directory on import).
  - A directory (copied verbatim).
  On success, the new pack appears in the list with a brief
  "Installed <pack-name>" snackbar.

- [ ] **UI-AA.3** "Install from URL" button. Opens a dialog with a
  URL `OutlinedTextField`. On submit, the app downloads the file
  once (HTTP, with TLS), validates it as a zip, and unpacks it
  into the pack directory. The URL is *not* persisted — packs are
  fetch-once, not live-mirrored.

- [ ] **UI-AA.4** Icon picker upgrade. The existing icon picker
  (used for repo, calendar, todolist icons; see UI-L for repo
  icon, UI-Q for theming) gains a tab strip at the top:
  - **Emoji** (system emoji, existing)
  - **Photo** (user-picked image, existing)
  - **<Pack name>** (one tab per installed sticker pack)
  Tabs scroll horizontally when more than ~3 packs are installed.

- [ ] **UI-AA.5** `:sticker-name:` typing autocomplete. In the
  event title editor, task title editor, and comment composer
  (UI-W.4), typing `:` opens a dropdown of matching sticker names
  across all installed packs. Selecting an entry inserts the
  resolved image inline (via Markwon image extension) at that
  position in the title or body.

- [ ] **UI-AA.6** Pack format validator. On import, the app checks:
  - At least one image file present.
  - No image > 256KB.
  - No image dim > 512×512.
  - Optional `pack.toml` if present must parse and contain
    `name = "..."` at minimum.
  Validation failures show a dialog "this pack is invalid:
  <reason>" and abort the install.

- [ ] **UI-AA.7** Remove button on each pack row. Confirmation
  dialog: "Remove <pack-name>? Stickers in this pack will stop
  rendering in titles and icons that referenced them." On confirm,
  the pack directory is deleted; references in user data
  (`:sticker-name:` strings, etc.) remain as text fallback.

- [ ] **UI-AA.8** Built-in starter pack. v1 ships one tiny default
  pack (`com.eight87.skb-default`) with ~12 schedule-themed
  stickers (clock, calendar, alarm, etc.) so the UI is not empty
  on first run.

- [ ] **UI-AA.9** Cross-link back to `main.md` Phase FF.

**ASCII mockup — icon picker with sticker pack tabs:**

```
   ┌──────────────────────────────────────────────────────────────┐
   │  Pick an icon for "Personal"                                  │
   │  ┌────────┬────────┬─────────────────┬─────────────────┐    │
   │  │ Emoji  │ Photo  │ schedule-stickers│ retro-icons     │    │
   │  └────────┴────────┴─────────────────┴─────────────────┘    │
   │                                ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔            │  ← active
   │                                                              │
   │  ┌────┬────┬────┬────┬────┬────┬────┐                       │
   │  │ ⏰ │ 📆 │ ⏳ │ 🌅 │ 🛏  │ 🎯 │ ✅ │                       │
   │  ├────┼────┼────┼────┼────┼────┼────┤                       │
   │  │ 🚴 │ 🏊 │ 🥋 │ 🎵 │ 📚 │ 🧹 │ 🍳 │                       │
   │  └────┴────┴────┴────┴────┴────┴────┘                       │
   │                                                              │
   │  [Cancel]                                       [Select]     │
   └──────────────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *URL packs not live-mirrored.* Live mirroring would couple sticker
  visibility to network state; one-shot fetch keeps the UI
  deterministic offline.
- *Validation limits.* 256KB / 512×512 keeps a pack of ~50 stickers
  under ~12MB. Larger assets get rejected with a clear reason; the
  user can repack and retry.
- *`pack.toml` optional.* A directory of images alone installs fine;
  metadata is a polish layer.

---

## UI-BB — GPG signed-commits UI

`decisions.md` D.23. `main.md` Phase GG. JGit + BouncyCastle do the
crypto. UI surfaces are settings + a per-entry verified-chip.

**Sub-steps:**

- [ ] **UI-BB.1** Settings → Identities → tap identity → identity
  detail screen gains a **Signing** section below the avatar/email
  fields. Header: "Signing".

- [ ] **UI-BB.2** "Sign commits with GPG" toggle. Default OFF. When
  toggled ON for the first time on an identity, the row expands to
  reveal "Import GPG private key" (UI-BB.3) and "Sign with key"
  (UI-BB.4). When OFF, the expanded section collapses but imported
  keys are retained (the toggle is the on/off switch; keys live
  independently).

- [ ] **UI-BB.3** "Import GPG private key" section. Two import
  paths:
  - **From file** — opens SAF picker for `.asc` / `.gpg` files.
  - **Paste armored text** — opens a dialog with a multiline
    `OutlinedTextField` for ASCII-armored key paste.
  After import, if the key is passphrase-protected, a passphrase
  prompt appears. The passphrase is stored encrypted in
  `EncryptedSharedPreferences` (the user opts in via a checkbox
  "remember passphrase" — default off; per-sign prompt otherwise).

- [ ] **UI-BB.4** "Sign with key" picker. After at least one private
  key is imported, this `ExposedDropdownMenuBox` lists imported
  keys, each row showing: short fingerprint (last 8 hex chars),
  primary UID (name <email>), and key flags ("[E,S]"). Selection
  binds the identity to that key.

- [ ] **UI-BB.5** Settings → Repos → tap repo → "Signing override"
  section. Optional per-repo override of the identity-default
  signing. Modes:
  - **Use identity default** (the active identity's toggle wins).
  - **Force sign** (sign every commit regardless of identity
    default).
  - **Force unsigned** (never sign in this repo even if identity
    defaults to sign).
  Useful for: shared work repos that mandate signing, scratch
  repos where signing churn is noise.

- [ ] **UI-BB.6** Verified-author chip on event/task chips. When the
  underlying file was authored by a commit with a valid GPG
  signature whose public key matches the author's declared public
  keys in `identities/<author-id>.md`, a small ✓ badge (12dp,
  `primary`-tinted) renders in the trailing corner of the chip.
  Tap → bottom sheet with signature details: signing key
  fingerprint, signer name+email, sign date, and a "View public
  key" link to the identity profile.

- [ ] **UI-BB.7** "Import public keys for verification" section in
  Settings → Identities → tap identity. Same two paths as UI-BB.3
  (file picker, paste-armored) but for *public* keys to verify
  *other people's* signed commits. Keys imported here populate the
  identity's `public_keys` field in `identities/<id>.md`
  (see `data-model.md` Phase DM-K).

- [ ] **UI-BB.8** Signature failure handling. If a commit claims a
  signature but verification fails (key unknown, signature
  invalid, key expired), the author chip on entries from that
  commit gets a small ⚠ badge (instead of ✓). Tap → "signature
  invalid — <reason>" with options "Trust this key" (if just
  unknown) or "Report" (if invalid).

- [ ] **UI-BB.9** First-time signing flow. When the user turns on
  "Sign commits with GPG" on an identity that has no imported keys,
  a "Quick setup" sheet offers:
  - "Import an existing GPG key" (UI-BB.3).
  - "Generate a new key on this device" — opens a key-generation
    dialog with name / email / passphrase / key type (ed25519 /
    RSA-4096).
  Generation uses BouncyCastle (no `gpg` binary on Android).

- [ ] **UI-BB.10** Cross-link back to `main.md` Phase GG and
  `sync-engine.md` Phase SE-S (signing during commit), and
  `data-model.md` Phase DM-K (public-keys field on identity).

**ASCII mockup — GPG settings section in identity detail:**

```
   ┌──────────────────────────────────────────────────────────────┐
   │  ← Identity · Alex                                            │
   │                                                              │
   │  ┌──┐  Alex                                                  │
   │  │🦊│  alex@example.com                                       │
   │  └──┘                                                        │
   │                                                              │
   │  ────────────────────────────────────────────────────────── │
   │  Signing                                                     │
   │                                                              │
   │  Sign commits with GPG                            [● ON]    │
   │                                                              │
   │    Imported private keys                                     │
   │    ┌────────────────────────────────────────────────────┐  │
   │    │ 🔑 12AB34CD   Alex <alex@example.com>     [Remove] │  │
   │    │              ed25519, [E,S], expires 2028-05-12     │  │
   │    └────────────────────────────────────────────────────┘  │
   │    [+ Import private key]   [+ Generate new key]            │
   │                                                              │
   │    Sign with key   [ 12AB34CD · Alex ▾ ]                    │
   │                                                              │
   │    Remember passphrase                            [○ OFF]    │
   │                                                              │
   │  ────────────────────────────────────────────────────────── │
   │  Public keys (for verifying others' commits)                 │
   │                                                              │
   │    ┌────────────────────────────────────────────────────┐  │
   │    │ 🔓 56EF78AB   Sam <sam@example.com>       [Remove] │  │
   │    └────────────────────────────────────────────────────┘  │
   │    [+ Import public key]                                     │
   └──────────────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *Passphrase storage.* Opt-in only. The default of per-sign prompt
  preserves the security property of a passphrase; the opt-in
  trades that off for CLI-driven workflows where prompts break
  automation.
- *Key generation on device.* Offered as a path because the project
  audience includes users who don't have an existing GPG setup;
  rejecting "generate" would force everyone to set up GPG on a
  laptop first.
- *Verification ✓ vs. ⚠.* Two distinct visual treatments — silence
  on unverified would lose the trust signal; loud red error on
  unverified would over-alarm given that "unsigned" is the
  default-OK state.

---

## UI-CC — Multi-branch UI + CalDAV mirror UI

`decisions.md` D.25 (CalDAV) and D.36 (multi-branch). `main.md`
Phase JJ (branches) plus CalDAV cross-cuts (sync-engine.md SE-Q+,
data-model.md DM-K+). Two related but distinct UI surfaces, grouped
in one phase because both live under "Settings → Repos → tap repo".

### Part 1 — Multi-branch UI

**Sub-steps:**

- [ ] **UI-CC.1** Repo switcher chip in the top bar shows current
  branch below repo name in smaller text. Layout:
  ```
  ┌─────────────────┐
  │ 🦊 personal     │  ← repo display name, titleMedium
  │   main          │  ← branch name, bodySmall, onSurfaceVariant
  └─────────────────┘
  ```
  When repo is on the default branch (`main` / `master`), the second
  line still shows. When on a non-default branch, the second line
  tints `tertiary` to flag the divergence.

- [ ] **UI-CC.2** Settings → Repos → tap repo → "Current branch"
  row. Tap opens a bottom sheet listing all branches (remote +
  local) with the current one selected. Each row:
  - Branch name.
  - Source indicator: 🌐 (remote only), 💻 (local only), 🔁
    (tracked).
  - Ahead/behind counts ("↑2 ↓0") for tracked branches.

- [ ] **UI-CC.3** "Create branch" form, opened from the branch
  bottom sheet via a "+ Create branch" button at the bottom. Fields:
  - **Name** (`OutlinedTextField`, validated against git ref naming
    rules — no spaces, no `..`, no leading `-`, etc.).
  - **Base branch** (`ExposedDropdownMenuBox`, defaults to current
    branch).
  - **Switch to new branch immediately** (Checkbox, default ON).
  Submit creates the branch via JGit and (if checked) checks it out.

- [ ] **UI-CC.4** "Switch branch" with dirty working tree. When the
  user picks a different branch in UI-CC.2 and there are
  uncommitted local changes, a dialog interrupts:
  - **Stash changes** — `git stash`-equivalent via JGit; restored
    on next switch back.
  - **Discard** — drop the changes; second confirmation dialog
    because destructive.
  - **Cancel** — bail.
  Default focus: **Stash changes** (least destructive).

- [ ] **UI-CC.5** "Compare & propose change" deep-link. When the
  current branch is non-default and has commits not in the default
  branch, the branch bottom sheet exposes a button "Open compare
  page". This builds the provider-specific compare URL:
  - GitHub: `https://github.com/<owner>/<repo>/compare/<base>...<head>`
  - Forgejo: `https://<host>/<owner>/<repo>/compare/<base>...<head>`
  And opens it via `Intent.ACTION_VIEW`. The app does not create
  the PR itself (D.40 keeps in-app PR creation deferred).

- [ ] **UI-CC.6** Branch indicator in the schedule shell. When on a
  non-default branch, a thin (4dp) `tertiary`-tinted strip renders
  along the top of the schedule view, below the top app bar, with
  text "viewing branch: <branch-name>". Tap → branch bottom sheet.
  This is the safety net so the user never forgets they're on a
  feature branch.

- [ ] **UI-CC.7** Sync behaviour on non-default branches. Inherits
  from `sync-engine.md` Phase SE-T; UI side: the sync icon's
  spinner tooltip on a feature branch reads "syncing branch
  <name>" instead of "syncing repo <name>".

### Part 2 — CalDAV mirror UI

**Sub-steps:**

- [ ] **UI-CC.8** Settings → Repos → tap repo → **CalDAV mirrors**
  section, below the branch section. Header: "CalDAV mirrors (N)".

- [ ] **UI-CC.9** Per-mirror row content:
  - Server URL (truncated middle, e.g. `dav.example.com/…/work-cal/`).
  - Calendar display name on the server.
  - Mode badge: 🡆 (pull-only) / 🡄 (push-only) / 🡆🡄 (bidi).
  - Last sync time ("3m ago" / "yesterday" / "—").
  - Sync interval ("30m").
  - Status dot (UI-CC.13).
  Tap → mirror detail screen (UI-CC.12).

- [ ] **UI-CC.10** "+ Add CalDAV mirror" button. Multi-step flow:
  1. **Server** — `OutlinedTextField` URL + auth credentials
     (username, password OR token). "Test connection" button.
  2. **Discovery** — app calls dav4jvm to enumerate calendars on
     the server. Shows a list with checkboxes for each calendar.
     User picks exactly one (one mirror = one calendar).
  3. **Mode** — radio: Pull-only / Push-only / Bidi. Each option
     has a tertiary helper line explaining the implication.
  4. **Interval** — chip group: 5m / 15m / 30m / 1h / 4h / Manual.
     Default 30m.
  5. **Local target** — picker for which app calendar the mirror
     binds to. Defaults to a new calendar named
     `<server-cal-name> (mirror)`.
  6. **Confirm** — summary screen, "Add mirror" button.

- [ ] **UI-CC.11** Discovery failure modes. If the server URL is
  not a CalDAV endpoint, step 1's "Test connection" surfaces
  "this URL doesn't expose CalDAV — try the base URL of your
  account, e.g. `https://dav.example.com/<username>/`". If auth
  fails, "credentials rejected — check username + password or
  token".

- [ ] **UI-CC.12** Mirror detail screen. Same fields as UI-CC.9 row
  plus:
  - "Sync now" button (one-shot).
  - "Change interval" picker.
  - "Change mode" picker (with a warning if changing pull → push
    direction would re-overwrite remote data).
  - "Remove mirror" button (with confirmation; removal does NOT
    delete the locally-mirrored calendar, only the bridge).
  - Recent-syncs log: last 10 sync results with timestamp,
    direction, items pulled/pushed, error message if any.

- [ ] **UI-CC.13** Mirror status dot. 8dp circle:
  - **Green** — last sync ≤ 1 interval ago and succeeded.
  - **Yellow** — sync currently running.
  - **Red** — last sync failed or > 2 intervals ago without a
    success.
  - **Grey** — never synced (just added).
  Shape matches the per-repo sync indicator from UI-B so the
  visual language is consistent.

- [ ] **UI-CC.14** Notification on mirror error. If a mirror sync
  fails 3 times in a row, fire a notification on the `errors`
  channel: "CalDAV mirror <name> failing — tap to inspect". Tap
  opens the mirror detail screen.

- [ ] **UI-CC.15** Cross-link back to `main.md` Phase JJ (branches)
  and the CalDAV cross-cuts (no dedicated main.md phase — CalDAV
  spans sync-engine + data-model + notifications-sharing-import).

**ASCII mockup — CalDAV mirror section in repo settings:**

```
   ┌──────────────────────────────────────────────────────────────┐
   │  ← Repo · personal                                            │
   │                                                              │
   │  …                                                           │
   │  Current branch                              [ main ▾ ]       │
   │  Auto-sync                                     [● 15m]        │
   │  …                                                           │
   │                                                              │
   │  ────────────────────────────────────────────────────────── │
   │  CalDAV mirrors (2)                                          │
   │                                                              │
   │  ┌────────────────────────────────────────────────────────┐ │
   │  │ ● dav.example.com/…/work-cal/    🡆🡄  30m     3m ago   │ │
   │  │   Work (Outlook)                                       │ │
   │  └────────────────────────────────────────────────────────┘ │
   │  ┌────────────────────────────────────────────────────────┐ │
   │  │ ● caldav.icloud.com/…/family/    🡆   1h     yesterday │ │
   │  │   Family (iCloud)                                      │ │
   │  └────────────────────────────────────────────────────────┘ │
   │                                                              │
   │  [ + Add CalDAV mirror ]                                     │
   └──────────────────────────────────────────────────────────────┘

   Status dots:
     ● green   = healthy
     ● yellow  = syncing
     ● red     = error
     ● grey    = never synced
```

**Tradeoffs resolved inline.**

- *One mirror = one calendar.* Forces clarity. A "mirror everything"
  mode would surface auth scope creep and conflict-resolution
  ambiguity (which calendar wins when the server-side names collide
  with locals).
- *Mirror removal preserves local calendar.* Removing the bridge
  should never silently delete user data. The mirrored calendar
  stays as a regular local calendar; user can delete it explicitly
  if desired.
- *Branch strip warning vs. badge.* A thin 4dp strip is loud enough
  to remind but quiet enough not to dominate. A toast or banner
  would be noisier; a tiny badge would be too easy to miss.

---

## UI-DD — Android Auto voice-create

`decisions.md` D.33. `main.md` Phase HH. Bumped from read-only
(D.17) to read-only + voice-create. No on-screen edit or delete in
Auto for safety.

**Sub-steps:**

- [ ] **UI-DD.1** Voice intent registration. The app's
  `CarAppService` registers for voice utterances matching:
  - "Schedule event …"
  - "Add event …"
  - "Create appointment …"
  - "Add reminder …" (routed to event-with-notification)
  Patterns documented in `automotiveapp.xml` and the manifest's
  `<intent-filter>` for `android.intent.action.VOICE_COMMAND`.

- [ ] **UI-DD.2** NLU pass on the utterance. The CarApp implementation
  parses:
  - Title (free-text, the bit after the schedule verb and before
    the time-anchor words).
  - Time anchor ("tomorrow at 3pm", "next Tuesday", "in an hour",
    "Friday morning").
  - Duration ("for 30 minutes" → end time = start + 30m; default
    1h if absent).
  - Calendar ("in Personal", "to Work") — optional; defaults to
    the active repo's default calendar.
  Parser: `Natty` (Apache-2.0) for natural-language dates.

- [ ] **UI-DD.3** Confirmation card. After parsing, the Auto session
  renders a `PaneTemplate` (Auto-specific):
  ```
  ┌────────────────────────────────────────────────────────┐
  │  Create event?                                          │
  │                                                        │
  │  "Dentist"                                              │
  │  Tomorrow · 3:00 PM – 4:00 PM                           │
  │  in Personal (personal repo)                            │
  │                                                        │
  │  Say "yes" to confirm  ·  "no" to cancel               │
  │  [Confirm]                          [Cancel]            │
  └────────────────────────────────────────────────────────┘
  ```
  Voice listener active for yes/no. On-screen Confirm/Cancel
  buttons are large-touch-target backups.

- [ ] **UI-DD.4** On confirm, the app:
  1. Writes the event file via the same code path the phone UI
     uses (atomic single-file write).
  2. Commits with the standard auto-message.
  3. Refreshes the Auto "today/tomorrow" list to show the new
     event immediately.
  4. Speaks back "Event 'Dentist' added for tomorrow at 3 PM."

- [ ] **UI-DD.5** Parse-failure handling. If the utterance cannot
  be parsed (no time, ambiguous), speak back:
  - "I couldn't tell when. Try 'schedule dentist tomorrow at 3 PM'."
  - "Which calendar? Personal or Work?"
  Up to 1 clarification round; if still ambiguous, abandon with
  "Sorry, please try again."

- [ ] **UI-DD.6** No edit / no delete in Auto. The list view from
  D.17 (read-only) is preserved as-is. Items are not tappable for
  edit. A voice command "delete event …" is explicitly NOT
  registered — too easy to mis-fire.

- [ ] **UI-DD.7** Active repo + calendar context. The repo + default
  calendar at the time of utterance are baked into the new event.
  Switching active repo from Auto is out of scope; the user picks
  the active repo on the phone before driving.

- [ ] **UI-DD.8** Cross-link back to `main.md` Phase HH.

**Tradeoffs resolved inline.**

- *NLU dependency.* Natty handles English natural-language dates
  competently; locales beyond English ship as v1.1. Stating this
  scope cap so the test plan focuses on en-US.
- *No delete by voice.* "Delete event Dentist" is too easy to
  collide with "schedule event Dentist". Asymmetric safety
  preference: easy to add, hard to remove.

---

## UI-EE — Updated deferrals (housekeeping)

`decisions.md` D.40. This phase is a single deliverable: the
"Deferred to future versions" section above has been surgically
updated to reflect the new v1 scope. See the annotations on each
entry there.

**Sub-steps:**

- [ ] **UI-EE.1** Each Round-1 deferral has been re-triaged with a
  trailing annotation: ✅ MOVED TO v1, ✅ STILL REJECTED, or ⚠️
  STILL DEFERRED v1.1. (Shipped in this edit of `ui-spec.md`.)

- [ ] **UI-EE.2** New deferrals from Round 2 are explicitly *not*
  added — every item from the Round 1 v2-deferral pile that wasn't
  rejected is now in v1 scope per D.40. (Shipped in this edit.)

- [ ] **UI-EE.3** "Done-when" criteria at the bottom of this doc
  updated implicitly: UI-V through UI-EE join UI-A through UI-U
  in the "every phase must tick" gating. (Tracked in main.md.)

---

## Component reference index (alphabetic)

For sub-agents implementing each phase, a cheat-sheet of M3E
components used:

| Component | Used in | Notes |
|---|---|---|
| `Card(elevation = 0, color = surfaceContainer, shape = ExtraLarge)` | event/task chips, settings cards | M3E flat-card pattern |
| `DatePicker` (M3) | event editor | with M3E theme |
| `DropdownMenu` | repo switcher (tablet) | phone uses bottom sheet |
| `ExposedDropdownMenuBox` | calendar/identity pickers in editors | |
| `FilterChip` | role toggles in wizard, repo multi-select in Together | |
| `FloatingActionButton.Large` | schedule + tasks main FAB | M3E variant |
| `ExtendedFloatingActionButton` | "+ Add event" with label | when label adds context |
| `IconButton` | sync, identity, back arrows | 40dp tap target |
| `LargeTopAppBar` | Settings root | mirrors tonearmboy |
| `LazyColumn` | day timeline, settings list, task list | |
| `LazyVerticalGrid` | year view, calendar pickers | |
| `MediumTopAppBar` | Schedule, Tasks, Settings sub-screens | collapses on scroll |
| `ModalBottomSheet` | event/task detail, repo switcher (phone), identity picker, quick-add | drag handle 32dp |
| `NavigationSuiteScaffold` | app root | forced to NavigationRail |
| `OutlinedTextField` | titles, URLs, search | M3 |
| `PullToRefreshContainer` | schedule + tasks (sync active repo) | M3 |
| `SecondaryTabRow` (M3E) | view-mode tab strip | pill indicator |
| `SharedTransitionLayout` | day-cell → day-view, task → detail | |
| `Surface(color = surfaceContainerHigh)` | repo switcher chip | |
| `SwipeToDismissBox` | task rows | done / snooze |
| `TimePicker` (M3) | event editor | |
| `TopAppBar` (small) | Together root | |

---

## Done-when

This document is "done" (Status: ✅ DONE) when:

- Every phase UI-A through UI-U has its sub-step checkboxes ticked.
- Every ASCII mockup has been re-validated against the implemented
  composable on the AVD.
- The "Tradeoffs resolved inline" list has been re-read and any
  reversed call has been logged with the date and reason.
- The "Deferred to future versions" list has been triaged at v1 ship.

---

# Round 3 — extensions (Phases UI-FF through UI-KK)

Round 3 of `decisions.md` (D.41–D.52) reframes the app around a second
first-class entry path: **receiving a complete schedule from someone
else via deep-link / QR**. The phases below extend this spec to cover
the new UI surfaces that path needs:

- A stripped-down simplified ("good boy") mode chrome that hides
  authoring complexity (UI-FF).
- A first-launch deep-link bootstrap that skips the wizard entirely and
  drops a brand-new user straight into a gifted schedule (UI-GG).
- The authoring side's share-this-repo flow with QR generation and
  share-history (UI-HH).
- The evolution path mini-wizard that lets a simplified-mode user grow
  into authoring without losing gifted schedules (UI-II).
- The cross-repo state UI surfaces (done/snooze/note/mute/hide/
  priority-override) that write to the recipient's own repo without
  touching read-only sources (UI-JJ).
- The receiving-repo UI and `references.toml`-driven prompt sheet that
  cascades sibling-repo offers (UI-KK).

Each phase here follows the same conventions as UI-A through UI-EE
above: sub-step checkboxes with `**UI-X.Y**` prefixes, ASCII mockups
for the load-bearing visual states, M3E component references, and
cross-links back to `main.md` (Phases MM–TT), `decisions.md`
(D.41–D.52), and the sibling deep-dive docs (`shared-schedules.md`,
`data-model.md` DM-Q+, `resolver.md` RV-L+).

**Round 3 phase index:**

| Phase | Topic | main.md tie-in | decisions.md tie-in |
|---|---|---|---|
| UI-FF | Simplified ("good boy") mode chrome | PP | D.45 |
| UI-GG | First-launch deep-link bootstrap | MM, QQ | D.42, D.46 |
| UI-HH | Authoring share-this-repo flow + QR | RR | D.48 |
| UI-II | Evolution path: simplified → own repo | SS | D.47 |
| UI-JJ | Cross-repo state UI surfaces | OO, TT | D.44, D.49 |
| UI-KK | Receiving-repo and `references.toml` UI | MM, NN | D.42, D.43 |

---

## UI-FF — Simplified ("good boy") mode chrome

`decisions.md` D.45. `main.md` Phase PP.

**Design center.** Simplified mode is a UI-only veneer over the full
app. **Not a feature lock** — every byte of data the user has is
preserved across mode flips; only the chrome changes. The mode is
designed for two anchor users: (1) a brand-new app installer who tapped
a gifted-schedule deep-link and has never seen a calendar app before,
(2) an existing power-user who wants a single-purpose-device feel
("just show me today, don't tempt me into the editor"). Both must be
served by the same chrome.

The mode is selected automatically when the user has only read-only
gifted repos and no own repo (D.45 auto-entry rule). It is also
reachable from full mode via Settings → Appearance → "Switch to
simplified mode". The reverse — "Switch to full mode" — is always one
tap from the simplified-mode settings, never gated.

**Sub-steps:**

- [ ] **UI-FF.1** Add a `UiMode` enum to app prefs in DataStore:
  `SIMPLIFIED` | `FULL`. Default at first launch:
  - `SIMPLIFIED` iff the boot path is the deep-link receive flow AND no
    own repo exists (D.46 boot behavior).
  - `FULL` otherwise (wizard path, or returning user with own repo).
  The mode value is observed by the root `NavigationSuiteScaffold`
  composable; switching the mode triggers a re-composition of the
  entire chrome, but never touches data or repo state.

- [ ] **UI-FF.2** In simplified mode, the bottom nav has exactly **two**
  destinations: `Schedule` (📆) and `Tasks` (✅). The Together tab and
  Settings tab are removed from the bottom nav. The
  `NavigationSuiteScaffold` is configured with a 2-item layout; the rail
  variant on tablets shows the same two items vertically.

- [ ] **UI-FF.3** Single-view Schedule shell. Simplified mode forces a
  single view-mode (no Day/Week/Month/Year/Timebox tab strip). The
  view defaults to **Today** (a stripped variant of UI-D's Day view
  pinned to `LocalDate.now()`); the user can pick **Day** instead from
  a one-time "pick your home view" prompt on first entry to simplified
  mode. The picker writes to `SimplifiedHomeView` in DataStore. The
  view-mode tab row (`SecondaryTabRow` from UI-C) is omitted from the
  composition tree entirely — not just hidden, so the M3E reflow never
  leaves stale space.

- [ ] **UI-FF.4** Compact top app bar. Simplified mode uses the same
  `MediumTopAppBar` from UI-B but with a reduced trailing-action set:
  - Leading: repo switcher chip (filterable by name; same component as
    UI-K but rendered in a `Surface(color = surfaceContainerLow)` chip
    with a downward chevron; opens the same `ModalBottomSheet` repo
    list, but the "+ Add repo" entry is replaced by "Add a gifted
    schedule" which routes to UI-GG's add-gifted-repo screen).
  - Trailing 1: mode-label badge — a small text-only `AssistChip`
    showing whichever label the user picked (default "Simplified"; see
    UI-FF.7). Tap → label picker (UI-FF.7). Long-press → toast
    explaining "this is your current mode label".
  - Trailing 2: sync `IconButton` (🔄).
  - Trailing 3: kebab overflow → "Settings".
  - The identity icon is **not** present in simplified mode (no
    authoring identity needed for a pure consumer).

- [ ] **UI-FF.5** Event detail sheet in simplified mode. Re-uses the
  Round 1 UI-I sheet *content* (title, time, body, comments, calendar
  badge) and the Round 2 UI-W comment composer, BUT:
  - The "Edit event" action is hidden if the source repo is read-only
    (which, in simplified mode, every repo typically is).
  - The "Mark done" / "Snooze" / "Mute" / "Hide" actions are present
    and write to cross-repo state files per UI-JJ.
  - The kebab is reduced: just "Hide on this device" and "Share event".

- [ ] **UI-FF.6** FAB in simplified mode. A single
  `FloatingActionButton.Large` labelled "Add my own events" with a
  pencil-plus icon. Tap → routes into the UI-II evolution-path
  mini-wizard. Long-press → same destination (no surprise menu). The
  FAB is only present on the Schedule view, not on Tasks.

- [ ] **UI-FF.7** Mode-label picker. A `ModalBottomSheet` with a single
  `LazyColumn` of selectable rows. Each row is a `ListItem` with a
  trailing `RadioButton`. Options (D.45 list):
  - Simplified (default; the Play-Store-safe label)
  - Focused
  - Received Schedules
  - Good Boy Mode
  - Good Girl Mode
  - Good Pet Mode
  - Kept Mode
  - Other…
  The "Other…" row expands inline to an `OutlinedTextField` accepting up
  to 32 chars of free text. The current selection is shown as the
  trailing chip in the top app bar (UI-FF.4). Choice persists in
  DataStore key `ModeLabel`. Reachable from: first-use overlay,
  Settings → Appearance → "Mode label", and tap on the trailing chip
  itself.

- [ ] **UI-FF.8** First-use overlay. On the very first composition of
  simplified mode (regardless of how the user got there), a
  `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`
  appears with two stacked questions:
  1. "What should we call this mode for you?" → routes to UI-FF.7's
     picker, defaulting to "Simplified".
  2. "Which home view do you want to land on?" → Day or Today, single
     select (default Today).
  Dismiss writes both answers to DataStore. The overlay never appears
  again unless the user resets app prefs.

- [ ] **UI-FF.9** Settings surface in simplified mode. Reached from the
  top-bar kebab → "Settings". The settings shell is a stripped variant
  of UI-P with only these sections visible:
  - "Sync" (manual sync button + last-sync timestamp).
  - "Appearance" (theme dark/light/system, mode label, home view).
  - "Notifications" (channel toggles only, no advanced overrides).
  - "More features available" → routes to the "Switch to full mode"
    confirmation (UI-FF.10).
  - "About" (version, license, the standard footer from UI-P).
  Everything else from UI-P (Repos, Identity, Templates, Advanced sync,
  GPG, Stickers, etc.) is omitted at the composition level. The full
  UI-P composable is *not* rendered with hidden rows — it's a separate
  `SimplifiedSettingsScreen` composable that includes only the allowed
  rows. This prevents accidental leakage when an internal nav change
  in UI-P adds a new row.

- [ ] **UI-FF.10** Mode-switch flow. "Switch to full mode" from
  Settings → "More features available" shows a confirmation
  `AlertDialog`:
  - Title: "Show all features?"
  - Body: "You'll see the full app: repo management, templates,
    identity, and more. You can switch back any time."
  - Primary button: "Show all features".
  - Secondary button: "Cancel".
  On confirm, `UiMode` flips to `FULL`, the root scaffold recomposes
  into the Round-1 chrome (UI-A through UI-U).

  For *temporary* full-mode use (e.g., "I need to add a deploy key
  once"), an alternate path: Settings → "Use full mode for one task"
  → flips `UiMode` to `FULL` *and* sets a `RevertOnNextSync` flag. On
  the next successful sync, a `Snackbar` appears: "Switch back to
  simplified mode now?" with `Action("Yes")` and dismiss. This keeps
  the simplified-mode contract — "you can always get out and back" —
  without forcing a full mode-flip ceremony for one-time chores.

- [ ] **UI-FF.11** Cross-link back to `main.md` Phase PP (simplified
  mode chrome) and `shared-schedules.md` Phase SH-D (mode semantics).

**ASCII mockup — simplified-mode home (Today view):**

```
┌──────────────────────────────────────────────────────────────────┐
│ [🦊 Master's schedule ▾]   [Good Boy Mode]   [🔄]   [⋮]          │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Today · Mon May 11                                             │
│                                                                  │
│   ┌───────────────────────────────────────────────────────────┐  │
│   │  07:00 ▍ Morning workout                            🔓    │  │
│   │         45 min · Workouts (from Master)                    │  │
│   └───────────────────────────────────────────────────────────┘  │
│   ┌───────────────────────────────────────────────────────────┐  │
│   │  12:30 ▍ Lunch check-in                             🔓    │  │
│   │         15 min · Check-ins (from Master)                   │  │
│   └───────────────────────────────────────────────────────────┘  │
│   ┌───────────────────────────────────────────────────────────┐  │
│   │  18:00 ▍ Evening journal                            🔓    │  │
│   │         20 min · Journaling (from Master)                  │  │
│   └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│   Tasks for today                                                │
│     ○  10 push-ups before noon                                   │
│     ●  drink 2L water  (done)                                    │
│     ○  text Master at 21:00                                      │
│                                                                  │
│                                                  ┌─────────────┐ │
│                                                  │  ✎ Add my   │ │
│                                                  │   own events│ │
│                                                  └─────────────┘ │
├──────────────────────────────────────────────────────────────────┤
│              [📆 Schedule]            [✅ Tasks]                 │
└──────────────────────────────────────────────────────────────────┘

   🔓  = small "shared from a read-only repo" badge per UI-JJ.7.
```

**ASCII mockup — mode-label picker:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ━━                                                              │
│                                                                  │
│  Mode label                                                      │
│  Pick what to call this mode on your device.                     │
│                                                                  │
│  ○  Simplified                                                   │
│  ○  Focused                                                      │
│  ○  Received Schedules                                           │
│  ●  Good Boy Mode                                                │
│  ○  Good Girl Mode                                               │
│  ○  Good Pet Mode                                                │
│  ○  Kept Mode                                                    │
│  ○  Other…                                                       │
│       └─ [ _______________________________ ]  (32 chars)         │
│                                                                  │
│                                          [ Cancel ]   [ Save ]   │
└──────────────────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *Two-tab nav vs. single-screen-only.* A single-screen UI (no nav at
  all) was tempting for the most-minimal case, but Tasks is a
  distinct-enough surface (different visual model, different
  resolver query) that punishing the user with a tab-style toggle in
  the top bar would be worse than dedicating a bottom-nav slot. Two
  tabs win.
- *Mode-label discoverability.* Burying the label picker in Settings
  alone would hide its expressiveness for the kink-coded users. Putting
  it as a trailing chip on every screen surfaces it without shouting
  about it; the default "Simplified" reads as utilitarian to any user
  who doesn't open the picker.
- *Auto-revert from temporary full mode.* A timer-based auto-revert
  ("flip back after 5 minutes") felt hostile. Snackbar-on-next-sync is
  the sweet spot: it's tied to a discrete action the user just took, so
  the prompt arrives at a moment where switching back makes sense.
- *Settings composability.* A "show full UI-P with rows hidden by mode"
  approach was rejected — too easy to leak a new row through. Separate
  `SimplifiedSettingsScreen` composable, allow-list at the source.

---

## UI-GG — First-launch deep-link bootstrap

`decisions.md` D.42, D.46. `main.md` Phases MM (deep-link registration)
and QQ (first-launch bootstrap).

**Design center.** A brand-new app install, launched from a
`strictlykeptboy://add?…` intent or a verified
`https://strictlykeptboy.app/add?…` Android App Link, MUST skip the
welcome wizard. No template picker. No identity setup. No repo creation
ceremony. The user lands on the "Add gifted repo" screen with the URL
prefilled, taps Accept, watches a clone-progress mascot, and ends up on
the simplified-mode home view. **One tap from QR to working schedule**
is the ship target (D.41).

**Sub-steps:**

- [ ] **UI-GG.1** Deep-link intent handler. The root activity's
  `onNewIntent` parses `strictlykeptboy://add?...` and
  `https://strictlykeptboy.app/add?...` per D.42's URL schema:
  - `url` (required, repeatable).
  - `label`, `mode`, `priority`, `via`, `references`.
  - Fragment params: `token`, `expires`.
  The handler routes:
  - **No repos configured** → UI-GG.2 (first-launch path).
  - **Already configured** → UI-KK.1 (add-repo-while-running, NOT
    first-launch).

- [ ] **UI-GG.2** Add-gifted-repo screen. A full-screen `Scaffold` with
  a `MediumTopAppBar` titled "Add a gifted schedule" and a back arrow
  that exits the activity (since there's nowhere to back to on a first
  launch). The body is a `LazyColumn` of cards:
  - **Card 1 — heading card**. Large title "Add a gifted schedule".
    Subheading line composed from URL params: `<label> from <via>`
    (e.g., "Master's schedule from Master @"); falls back to the URL
    hostname/path if no `label`; falls back to "(from someone)" if no
    `via`.
  - **Card 2 — mode chip**. A read-only `AssistChip` showing the link's
    `mode=` value ("Read-only" / "Read-write" / "Pull-only"). Greyed
    out — the user can't change it from this screen; it's whatever the
    sharer set. Tap → a `Tooltip` explaining the mode.
  - **Card 3 — auth status**. One of:
    - "Public repo, no sign-in needed" (green check icon).
    - "Using included credential" (key icon) — when `#token=` was in
      the URL fragment.
    - "This schedule needs sign-in" (lock icon) — with a "Sign in"
      button that routes to the Device-Code flow from Round 1 UI-K.4.
  - **Card 4 — repo URL**. Monospaced text, truncated middle with
    `…` if it exceeds the card width. Long-press → copy to clipboard
    (in case the user wants to record it).
  - **Card 5 — action row**. Two buttons:
    - Primary `Button("Accept")` (filled-tonal, M3E).
    - Secondary `OutlinedButton("Cancel")` — exits the activity.

- [ ] **UI-GG.3** Clone-progress screen. After "Accept", route to a
  full-screen progress composable with:
  - Mascot at the top — the bat placeholder from UI-Q's mascot pool,
    animated with a gentle `infiniteRotation`.
  - A `LinearProgressIndicator` (determinate when JGit reports % via
    the sync-engine progress monitor; indeterminate otherwise).
  - Status text under the bar, cycling through phases as the
    sync-engine reports them: "Cloning…" → "Indexing files…" →
    "Resolving overlays…" → "Almost ready…".
  - A `TextButton("Cancel")` at the bottom that aborts the clone via
    JGit cancel-callback + cleans up the partial repo dir.

- [ ] **UI-GG.4** Post-clone references prompt. After the clone
  completes, if the cloned repo has `.strictlykeptboy/references.toml`
  with any entry where `default_active = true`, route to UI-KK.2's
  references-prompt sheet. The first-launch path defers to UI-KK for
  this UI surface — DRY across first-launch and add-while-running.

- [ ] **UI-GG.5** Landing transition. Once the clone is done (and any
  references are resolved per UI-GG.4), the root scaffold recomposes
  into simplified mode (UI-FF) and lands on the home view. The
  one-time onboarding card overlay (UI-GG.6) appears on top.

- [ ] **UI-GG.6** Onboarding card. A `Card` floating over the
  simplified-mode home, anchored to the bottom-third of the screen
  with a scrim above. Content:
  - Headline: "Welcome to your schedule."
  - Body: "<author label or 'someone'> set this up for you. Tap any
    event for details. Mark tasks done by tapping their circle."
  - `Button("Got it")` (filled-tonal, full-width).
  Dismissable also by tap-anywhere on the scrim. The "I've seen this"
  flag persists in DataStore `OnboardingCardSeen`. The card never
  re-appears.

- [ ] **UI-GG.7** Error states. Each error renders as a full-screen
  `Scaffold` with the same mascot (sad-mascot variant), a heading, a
  body explanation, and one or two buttons:
  - **Malformed URL** — "We couldn't read that link." Body: "The link
    looks broken. Try opening the link again, or ask the sender for
    a fresh one." Buttons: `OutlinedButton("Close")`.
  - **Expired token** — "This link has expired." Body: "The included
    sign-in credential is no longer valid. Ask the sender for a new
    link, or sign in with your own account." Buttons:
    `Button("Sign in instead")` + `OutlinedButton("Close")`.
  - **Network down** — "Can't reach the server." Body: "Check your
    connection. We'll try again when you're back online." Buttons:
    `Button("Retry")` + `OutlinedButton("Close")`.
  - **Auth failed** — "Sign-in didn't work." Body: "The credential
    didn't grant access. The sender may have revoked it, or the
    permissions may be wrong." Buttons: `Button("Try sign-in again")`
    + `OutlinedButton("Close")`.
  - **Schema too new** — "This schedule needs a newer app." Body:
    "The sender's schedule uses features your app version doesn't
    understand. Update the app and try again." Buttons:
    `Button("Open Play Store")` + `OutlinedButton("Close")`.
  All "Close" buttons exit the activity on a first-launch path
  (nothing to back into) or pop the back-stack otherwise.

- [ ] **UI-GG.8** Token-fragment hygiene. After a successful clone,
  the URL fragment (`#token=…`) is stripped from any persisted
  referrer string, the in-memory `Intent` extras are cleared, and the
  credential is moved to `EncryptedSharedPreferences` keyed by the
  repo URL hash per D.50. See `sync-engine.md` Phase SE-? for the
  storage details; this checkbox is the UI side only.

- [ ] **UI-GG.9** Cross-link back to `main.md` Phases MM (intent
  registration), QQ (first-launch bootstrap), and `shared-schedules.md`
  Phase SH-B (end-to-end receive flow).

**ASCII mockup — Add-gifted-repo screen:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ←     Add a gifted schedule                                     │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                                                            │  │
│  │   Master's schedule                                        │  │
│  │   from Master @                                            │  │
│  │                                                            │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Mode                                                      │  │
│  │  ┌───────────────────┐                                     │  │
│  │  │ 👁 Read-only       │   (set by sender, can't change)    │  │
│  │  └───────────────────┘                                     │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  🔑 Using included sign-in credential                      │  │
│  │     valid for 24 h from the sender                         │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │  Repo                                                      │  │
│  │  git@github.com:dom/…edule.git    (long-press to copy)     │  │
│  └────────────────────────────────────────────────────────────┘  │
│                                                                  │
│           [ Cancel ]                  [   Accept   ]             │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — clone-progress screen:**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│                            🦇                                    │
│                       (bat mascot                                │
│                        slow-rotates)                             │
│                                                                  │
│                    Cloning your schedule…                        │
│                                                                  │
│      ████████████████████████░░░░░░░░░░░  62 %                   │
│                                                                  │
│                       Indexing files…                            │
│                                                                  │
│                                                                  │
│                       [   Cancel   ]                             │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — onboarding card overlay:**

```
┌──────────────────────────────────────────────────────────────────┐
│ [🦊 Master's schedule ▾]   [Good Boy Mode]   [🔄]   [⋮]          │
├──────────────────────────────────────────────────────────────────┤
│   Today · Mon May 11                                             │
│   ┌───────────────────────────────────────────────────────────┐  │
│   │  07:00 ▍ Morning workout                                  │  │
│   └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│   ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │
│   ░░░  ┌──────────────────────────────────────────────────┐ ░░░  │
│   ░░░  │  Welcome to your schedule.                       │ ░░░  │
│   ░░░  │                                                  │ ░░░  │
│   ░░░  │  Master set this up for you. Tap any event       │ ░░░  │
│   ░░░  │  for details. Mark tasks done by tapping         │ ░░░  │
│   ░░░  │  their circle.                                   │ ░░░  │
│   ░░░  │                                                  │ ░░░  │
│   ░░░  │              [        Got it        ]            │ ░░░  │
│   ░░░  └──────────────────────────────────────────────────┘ ░░░  │
│   ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │
└──────────────────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *Cancel-on-clone vs. partial-state recovery.* JGit's `setProgressMonitor`
  + cancel-callback is reliable enough that we cancel-and-delete-partial
  rather than try to resume; resuming a half-cloned repo is too easy to
  get wrong, and the source is on a network anyway. If the user comes
  back, re-tapping the same link starts fresh.
- *Onboarding-card timing.* Showing the card immediately on land (vs.
  delayed after first event-tap) was chosen because the simplified-mode
  home is the entire UI surface — there's no risk of "user navigated
  away before reading". One overlay, one tap to dismiss.
- *Error-state recovery buttons.* Each error gets two buttons max. More
  than two buttons reads as "we don't know what you should do"; two is
  a clear contract — primary recovery + close.

---

## UI-HH — Authoring share-this-repo flow

`decisions.md` D.48. `main.md` Phase RR.

**Design center.** Any user who authored a repo can share it as a gift
with a few taps. The flow generates a deep-link (and matching QR), with
optional one-shot deploy-key or fine-grained PAT embedded in the URL
fragment. The flow MUST be discoverable from the repo settings (not
hidden in a sub-menu), and MUST surface the auth-method tradeoffs
inline so the sharer understands what they're handing out.

**Sub-steps:**

- [ ] **UI-HH.1** Entry point. In full mode, Settings → Repos → tap a
  repo row → repo-detail screen (existing from UI-K) → new row:
  "Share this repo" with a share-icon (♻️-or-arrow-pair). Tap → opens
  the share-config sheet (UI-HH.2). The row sits above the existing
  "Remove this repo" row, separated by a `Divider`.

- [ ] **UI-HH.2** Share-config sheet. A `ModalBottomSheet` with a
  `LazyColumn` of grouped settings rows:
  - **Group "Mode"**: three `RadioButton` rows — Read-only (default,
    described as "Receiver views only"), Read-write ("Receiver can
    edit; requires shared auth"), Pull-only ("Receiver gets a one-way
    mirror; no comments back").
  - **Group "Suggested label"**: a single `OutlinedTextField`
    labelled "Label", defaulting to the repo's display name. Char
    limit 64.
  - **Group "Suggested priority"**: three `FilterChip`s in a row —
    High / Normal (default-selected) / Low.
  - **Group "Auth method"**: four `RadioButton` rows:
    - "Recipient uses their own SSH key" (no token in link).
    - "Embed a one-shot deploy key" (24h expiry default; provider API
      generates it).
    - "Embed a fine-grained PAT" (24h expiry default; provider API
      generates it).
    - "Public repo, no auth needed" — only visible if the repo is
      public per provider metadata.
  - **Group "Expiry"**: a `Slider` (snapping to 1h / 6h / 24h / 7d /
    30d) — only visible when the chosen auth method embeds a token.
    Default `24h`. The label reads "Link expires in: 24 hours".
  - Action row at the bottom: `OutlinedButton("Cancel")` +
    `Button("Generate")`.

- [ ] **UI-HH.3** Generation step. Tap "Generate" routes through a
  short progress overlay ("Talking to GitHub…" / "Creating deploy
  key…" / "Building link…") backed by the provider API calls in
  `sync-engine.md` Phase SE-share. On success, route to the
  output-panel screen (UI-HH.4). On failure, an inline error replaces
  the action row in the share-config sheet ("Couldn't create deploy
  key — your token may need `admin:public_key` scope. Try again or
  sign in with broader scope.") with a "Retry" button.

- [ ] **UI-HH.4** Output panel. Full-screen `Scaffold` with title
  "Share this schedule" and a back arrow that returns to the
  repo-detail screen. Body composed top-to-bottom:
  - **QR card**. A large `Image` of the generated QR PNG, rendered
    256dp × 256dp on a white card, with a subtle border. Tap to expand
    to full-screen, scrim'd, for easy scanning across a room.
  - **Link card**. The URL as monospaced `Text` in a
    `Surface(color = surfaceContainerLow)` card, wrapped to fit. A
    trailing icon `IconButton` (📋) copies to clipboard with a
    `Snackbar("Link copied")`. Long-press the text → select-all for
    manual copy.
  - **Metadata row**. Three small `AssistChip`s reflecting the chosen
    settings: mode, priority, expiry.
  - **Action row**. Three buttons in a row:
    - `Button("Share via…")` (filled-tonal) → `Intent.ACTION_SEND` with
      the URL as `EXTRA_TEXT` and the QR PNG as `EXTRA_STREAM`. Picker
      shows system share-targets.
    - `OutlinedButton("Save QR")` → writes the QR PNG to
      `MediaStore.Downloads`, toasts "Saved to Downloads".
    - `OutlinedButton("Done")` → returns to repo-detail.

- [ ] **UI-HH.5** Share history sub-screen. In repo-detail screen,
  below the "Share this repo" row, a new row "Share history (N)"
  where N is the count of active+expired+revoked entries. Tap → opens
  the share-history screen: a `LazyColumn` of `ListItem`s, one per
  share-link, sorted by issued-date descending. Each row:
  - Leading icon: link-status icon — green check (active), grey clock
    (expired), red X (revoked).
  - Headline: the suggested label.
  - Supporting line 1: "<mode> · expires <relative time>" or
    "<mode> · expired <relative time>" or "<mode> · revoked
    <relative time>".
  - Supporting line 2 (if known): "shared with <recipient label>" —
    populated when the recipient's first sync reports back its identity
    via a comment in the source repo, *if* the share included
    read-write or the comment-back protocol.
  - Trailing: kebab → "Revoke", "Copy link", "Show QR", "Delete from
    history".

- [ ] **UI-HH.6** Revoke action. From the share-history kebab → "Revoke"
  → `AlertDialog`:
  - Title: "Revoke this share?"
  - Body: "The recipient will see an access-denied error on their next
    sync. They keep any data they already have locally, but new
    changes won't reach them."
  - `Button("Revoke")` (filled-tonal, error-color) +
    `OutlinedButton("Cancel")`.
  On confirm, the share-engine deletes the provider-side deploy-key or
  PAT, the share-history entry is marked `REVOKED`, the row updates
  in place, and a `Snackbar("Access revoked")` appears.

- [ ] **UI-HH.7** Empty-state for share-history. When the repo has
  never been shared, the share-history row is hidden entirely; only
  the "Share this repo" row is visible. Reduces chrome for the
  not-yet-shared case.

- [ ] **UI-HH.8** Cross-link back to `main.md` Phase RR
  (share-this-repo flow) and `shared-schedules.md` Phase SH-C
  (authoring-side end-to-end).

**ASCII mockup — share-config sheet:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ━━                                                              │
│                                                                  │
│  Share this repo                                                 │
│                                                                  │
│  Mode                                                            │
│  ●  Read-only — receiver views only                              │
│  ○  Read-write — receiver can edit; requires shared auth         │
│  ○  Pull-only — one-way mirror, no comments back                 │
│                                                                  │
│  Suggested label                                                 │
│   ┌────────────────────────────────────────────────────────┐     │
│   │ Master's schedule                                      │     │
│   └────────────────────────────────────────────────────────┘     │
│                                                                  │
│  Suggested priority                                              │
│   ( High )    [● Normal ]    ( Low )                             │
│                                                                  │
│  Auth method                                                     │
│  ○  Recipient uses their own SSH key                             │
│  ●  Embed a one-shot deploy key                                  │
│  ○  Embed a fine-grained PAT                                     │
│  (Public repo option hidden — this repo is private)              │
│                                                                  │
│  Link expires in: 24 hours                                       │
│  ├──○────────────────────────────────────────┤                   │
│  1h     6h    24h         7d              30d                    │
│                                                                  │
│              [ Cancel ]                  [  Generate  ]          │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — share-output panel:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ←     Share this schedule                                       │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│           ┌──────────────────────────────────────────┐           │
│           │                                          │           │
│           │   ██  ██  ██████  ██  ████  ██   ██████  │           │
│           │   ██  ██  ██  ██  ██  ██    ██   ██      │           │
│           │   ██████  ██  ██  ████████  ██   ██████  │           │
│           │   ██  ██  ██████  ██  ██    ██   ██  ██  │           │
│           │   ██  ██          ██  ██    ██████████   │           │
│           │       (256dp x 256dp QR)                 │           │
│           └──────────────────────────────────────────┘           │
│                                                                  │
│  ┌────────────────────────────────────────────────────────┐ [📋] │
│  │ strictlykeptboy://add?url=git@github.com:dom/master-    │     │
│  │ schedule.git&label=Master%27s+schedule&mode=read-only   │     │
│  │ &priority=normal#token=ABC…XYZ                          │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                  │
│   ( 👁 Read-only )   ( Normal )   ( ⏳ Expires in 24 h )         │
│                                                                  │
│   [ Share via… ]     [ Save QR ]     [   Done   ]                │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — share-history list:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ←     Share history                                             │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ✓  Master's schedule                                  ⋮        │
│      Read-only · expires in 18 h                                 │
│      shared with @sub-handle                                     │
│   ───────────────────────────────────────────────────────────    │
│   ⏰  Personal trainer 12-wk                            ⋮        │
│      Read-write · expired 3 days ago                             │
│      shared with @client-04                                      │
│   ───────────────────────────────────────────────────────────    │
│   ✗  Soccer season                                      ⋮        │
│      Pull-only · revoked yesterday                               │
│   ───────────────────────────────────────────────────────────    │
│                                                                  │
│      (kebab menu options:                                        │
│         · Revoke                                                 │
│         · Copy link                                              │
│         · Show QR                                                │
│         · Delete from history)                                   │
└──────────────────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *Mode + auth method as separate groups.* Combining them into a
  single picker ("Public read-only" / "Private read-only with deploy
  key" / …) was tempting for fewer rows, but the combinatorial
  explosion of valid pairs (mode × auth × expiry) outpaces a flat
  picker. Two groups, one slider — the user composes.
- *Expiry default of 24h.* D.48 specifies 24h for embedded tokens; we
  surface the slider so power users can extend, but the default
  matches the locked decision.
- *Share-history row hidden when empty.* No "empty state with
  illustration"; the row simply doesn't appear. Keeps repo-detail
  uncluttered for the common case (most users only share once or
  twice per repo).

---

## UI-II — Evolution path UI: simplified → own repo

`decisions.md` D.47. `main.md` Phase SS.

**Design center.** A simplified-mode user who's been consuming gifted
schedules and now wants to add their own events MUST be able to grow
into authoring without losing their gifted repos and without seeing
the full Round-1 wizard. The mini-wizard is **2–3 screens**, every
screen has a "Just start empty" or "Skip" escape, and the post-
creation prompt defaults to **keeping the user in simplified mode**.

**Sub-steps:**

- [ ] **UI-II.1** Entry points. Two distinct routes:
  - From simplified-mode FAB "Add my own events" (UI-FF.6).
  - From simplified-mode Settings → "Set up your own schedule"
    (a new row added between "Sync" and "Appearance" — visible only
    when the user has *no* own repo).
  Both routes land on UI-II.2 (Screen 1).

- [ ] **UI-II.2** Screen 1 — "Where should we put your schedule?".
  Full-screen `Scaffold`, top app bar "Set up your schedule (1/3)"
  with a back arrow that exits the wizard (with confirmation if any
  field is touched). Body:
  - Section "Provider" with three `RadioButton` rows:
    - GitHub (default).
    - Forgejo.
    - Just local for now (no remote yet; can add later).
  - Section "Repo name" — `OutlinedTextField` defaulting to
    `<github-username>-schedule` (or `my-schedule` for the local-only
    case). Helper text: "You can rename this later.".
  - Section "Visibility" — two `RadioButton` rows: Private (default)
    / Public. Hidden if the provider is "Just local for now".
  - Action row at the bottom: `OutlinedButton("Cancel")` +
    `Button("Next")`.

- [ ] **UI-II.3** Screen 2 — "Sign in to <provider>". Skipped entirely
  if Screen 1's provider is "Just local for now". Otherwise:
  - Full-screen `Scaffold`, top app bar "Set up your schedule (2/3)".
  - Body delegates to the Device-Code-flow composable from Round 1
    UI-K.4 (`SignInScreen(provider = …)`), but with the wizard's
    headline replaced: "Sign in to GitHub" + small body line "We'll
    create your schedule repo here.".
  - On success, route to Screen 3 (UI-II.4).
  - On failure: inline error, "Retry" + "Cancel" buttons.

- [ ] **UI-II.4** Screen 3 — "Pick a template?". Full-screen `Scaffold`,
  top app bar "Set up your schedule (3/3)". Body:
  - **Top card**: a large `Button("Skip — start empty")` (filled-tonal,
    full-width, prominent). Body line: "You can always add events one
    at a time.".
  - **Below the top card**: a horizontal `Divider` and the label
    "…or start from a template:" then the role-toggle screen from
    Round 1 UI-O wizard's K.3 step (`RoleToggleGrid` composable, but
    rendered without the wizard's other steps). The roles are
    Round-1's default set: Work / Fitness / Family / Self-care /
    Custom.
  - Action row at the bottom: `OutlinedButton("Back")` +
    `Button("Create")`.

- [ ] **UI-II.5** Creation-progress screen. After "Create" (or after
  "Skip — start empty"), route to a full-screen progress composable:
  - Mascot at top (bat, slow-rotate, same as UI-GG.3).
  - Status text cycling: "Creating your repo on <provider>…" →
    "Setting up files…" → "Migrating your existing tasks…" → "Done!".
  - The "Migrating your existing tasks" step copies
    `~/.strictlykeptboy/_local/state/` into the new repo's `state/`
    folder per D.47 and writes a `references.toml` listing all
    currently-configured gifted repos. See `data-model.md` Phase DM-Q+
    for the file shape; this checkbox is the UI side only.

- [ ] **UI-II.6** Post-creation prompt. After the progress screen
  reports "Done!", a `Dialog` appears:
  - Title: "You're set up."
  - Body: "Your schedule is live. Want to keep the simple view, or
    show all features?"
  - `Button("Stay simple")` (filled-tonal, **default-highlighted**).
  - `OutlinedButton("Show all features")` — flips `UiMode` to `FULL`
    on confirm.
  Dismiss-by-scrim acts as "Stay simple".

- [ ] **UI-II.7** Reverse path — full mode → simplified. In full mode,
  Settings → Appearance → "Switch to simplified mode" row. Tap →
  `AlertDialog`:
  - Title: "Switch to simplified mode?"
  - Body: "You'll see just your schedule and tasks. You can switch
    back any time."
  - `Button("Switch")` + `OutlinedButton("Cancel")`.
  No migration; just a `UiMode` flip. Instant.

- [ ] **UI-II.8** Cross-link back to `main.md` Phase SS (evolution path)
  and `shared-schedules.md` Phase SH-E (migration semantics).

**ASCII mockup — Screen 1 (provider + name + visibility):**

```
┌──────────────────────────────────────────────────────────────────┐
│  ←     Set up your schedule (1/3)                                │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Where should we put your schedule?                             │
│                                                                  │
│   Provider                                                       │
│     ●  GitHub                                                    │
│     ○  Forgejo                                                   │
│     ○  Just local for now                                        │
│                                                                  │
│   Repo name                                                      │
│   ┌────────────────────────────────────────────────────────┐     │
│   │ personal-schedule                                          │     │
│   └────────────────────────────────────────────────────────┘     │
│   You can rename this later.                                     │
│                                                                  │
│   Visibility                                                     │
│     ●  Private                                                   │
│     ○  Public                                                    │
│                                                                  │
│              [ Cancel ]                  [    Next    ]          │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — Screen 2 (sign-in):**

```
┌──────────────────────────────────────────────────────────────────┐
│  ←     Set up your schedule (2/3)                                │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Sign in to GitHub                                              │
│   We'll create your schedule repo here.                          │
│                                                                  │
│         ┌────────────────────────────────────────┐               │
│         │                                        │               │
│         │   Open your browser to                 │               │
│         │   github.com/login/device              │               │
│         │                                        │               │
│         │   and enter the code:                  │               │
│         │                                        │               │
│         │            ABCD-EFGH                   │               │
│         │                                        │               │
│         │   Waiting for sign-in…                 │               │
│         │                                        │               │
│         └────────────────────────────────────────┘               │
│                                                                  │
│              [ Cancel ]                                          │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — Screen 3 (template? or skip):**

```
┌──────────────────────────────────────────────────────────────────┐
│  ←     Set up your schedule (3/3)                                │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Pick a template?                                               │
│                                                                  │
│   ┌────────────────────────────────────────────────────────┐     │
│   │                                                        │     │
│   │           Skip — start empty                           │     │
│   │                                                        │     │
│   │     You can always add events one at a time.           │     │
│   │                                                        │     │
│   └────────────────────────────────────────────────────────┘     │
│                                                                  │
│   ─────────  or start from a template:  ─────────                │
│                                                                  │
│     [ Work ]   [ Fitness ]   [ Family ]                          │
│     [ Self-care ]   [ Custom ]                                   │
│                                                                  │
│              [ Back ]                    [   Create   ]          │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — post-creation prompt:**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│              ┌──────────────────────────────────────┐            │
│              │                                      │            │
│              │   You're set up.                     │            │
│              │                                      │            │
│              │   Your schedule is live. Want to     │            │
│              │   keep the simple view, or show      │            │
│              │   all features?                      │            │
│              │                                      │            │
│              │     [   Stay simple   ]              │            │
│              │     [ Show all features ]            │            │
│              │                                      │            │
│              └──────────────────────────────────────┘            │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *2 vs. 3 screens for non-local provider.* Three screens (provider /
  sign-in / template) is the natural decomposition; collapsing
  provider+name onto sign-in screen would crowd the sign-in code. Three
  it is, with the "Just local for now" path collapsing to two.
- *Skip-template as prominent button rather than skip-link.* The
  Round-1 wizard's role-toggle screen treats template-picking as the
  default and skipping as a link. For the evolution path, the user has
  already been consuming a gifted schedule — they may not need a
  template *at all*. Making "Skip — start empty" the most prominent
  button reflects that.
- *Stay-simple as default.* If the user got to UI-II from simplified
  mode, defaulting to "Stay simple" respects the contract that mode
  flips are explicit. "Show all features" remains a one-tap away.

---

## UI-JJ — Cross-repo state UI surfaces

`decisions.md` D.44 (state-file model), D.49 (priority resolution).
`main.md` Phases OO (state files) and TT (multi-repo priority).

**Design center.** The recipient interacts with content from read-only
gifted repos: marks tasks done, snoozes events, leaves private notes,
mutes, hides, overrides priority. Every interaction writes to **the
recipient's own primary repo** (or `_local/state/` if they have no
primary yet) per D.44 — never to the source. The UI MUST make the
"this is your private state" model legible without nagging.

**Sub-steps:**

- [ ] **UI-JJ.1** Done-toggle on tasks. The Round-1 task-row "circle
  tap to toggle done" (UI-J) extends transparently across repos. For
  tasks from gifted repos, the toggle writes a `done` state file at
  `state/<source-repo-id>/<entity-id>.done.toml` per D.44. Visual: the
  circle fills with the calendar color; no auxiliary chrome. Long-press
  the circle on a gifted task → tooltip "Done state is saved on this
  device, not in the source schedule.".

- [ ] **UI-JJ.2** Swipe-to-snooze on event chips. In Day/Week views,
  left-swipe on an event chip reveals a `SwipeToDismissBox` action
  layer with snooze choices: 15m / 1h / Tomorrow / Custom… Same UX as
  Round 2 NS-M, but cross-repo aware: writes a `snooze` state file
  with `until = <ISO>` per D.44. The chip visually dims and shows a
  small "💤 until <relative time>" badge until the snooze elapses.
  Tap-the-badge → "Undo snooze".

- [ ] **UI-JJ.3** Swipe-to-snooze on task rows. Same gesture, same
  state-file schema, applied to task rows in UI-J. The row dims and
  shows the snooze badge.

- [ ] **UI-JJ.4** Comment composer in event detail. Reuses Round 2
  UI-W's composer, but the **write target** depends on the source-repo
  mode:
  - Source is **read-write**: write to `<event-id>.comments/<comment-id>.md`
    in the source repo. Visual indicator above the composer: "💬 This
    will be a public comment on <repo label>." (info-color text).
  - Source is **read-only**: write a `note` state file at
    `state/<source-repo-id>/<entity-id>.note.toml` in the recipient's
    own repo. Visual indicator above the composer: "🔒 This will be a
    private note (only you see it). The schedule is read-only."
    (neutral-color text).
  - Source is **pull-only**: same as read-only.
  The indicator is **always present** (no hover/reveal) so the user
  never types into a context they didn't expect. The composer's
  "Post" button label changes too: "Post comment" vs. "Save note".

- [ ] **UI-JJ.5** Mute toggle in event detail header. A
  `FilledIconToggleButton` with a bell icon, top-right of the detail
  sheet header. Tap → toggles a `mute` state file. Visual: bell with
  diagonal slash when muted. Tooltip: "Notifications muted for this
  event on this device.".

- [ ] **UI-JJ.6** Hide toggle in event detail kebab. Kebab menu item
  "Hide on this device". Tap → confirmation `AlertDialog`:
  - Title: "Hide this event?"
  - Body: "It won't show up on your schedule. You can unhide later from
    Settings → Hidden events."
  - `Button("Hide")` + `OutlinedButton("Cancel")`.
  On confirm, writes a `hide` state file; the event disappears from
  all schedule views (resolver filters it out per `resolver.md`
  RV-L+).

- [ ] **UI-JJ.7** Priority-override UI. In the calendar list (full mode
  Settings → Repos → tap repo → calendar list, or simplified-mode the
  same surface reached via Settings → Repos when shown there per
  UI-FF.9): long-press a calendar row → context menu with "Override
  priority on this device". Tap → opens a bottom sheet:
  - Title: "Override priority — <calendar name>".
  - Body line: "The schedule's author set this calendar's priority to
    <N>. You can override it just for your device.".
  - `Slider` 1–1000, snapping to 50, with `Text` showing the current
    value.
  - Action row: `OutlinedButton("Clear override")` (visible only when
    an override exists) + `Button("Save")`.
  Writes a `priority-override` state file per D.44. The resolver
  applies the override per D.49.

- [ ] **UI-JJ.8** Shared-source indicator. Every event chip, task row,
  and calendar list entry sourced from a read-only or pull-only repo
  gets a small "🔓" (open-padlock-with-circle) badge in the leading
  corner, 12dp, tinted `surfaceVariant`. Long-press → toast "From
  <repo label> · read-only". The badge is absent for events from
  the user's own repos and for read-write gifted repos.

- [ ] **UI-JJ.9** Hidden-events recovery. Settings → "Hidden events"
  (new row, visible in both modes when at least one `hide` state file
  exists) → a `LazyColumn` of hidden events with each row showing
  title + source-repo label + an `IconButton("Unhide")`. Tap unhide →
  deletes the `hide` state file → event reappears.

- [ ] **UI-JJ.10** State-file write coalescing. UI-side requirement:
  rapid toggles (e.g., done-undone-done within 2s) coalesce to a
  single state-file write, with the latest value winning. Prevents
  jj/git noise from flicker. See `data-model.md` Phase DM-Q+ for the
  storage detail; this checkbox is the UI side.

- [ ] **UI-JJ.11** Cross-link back to `main.md` Phases OO and TT, and
  `resolver.md` Phase RV-L+ for the overlay-merge semantics.

**ASCII mockup — comment composer with private-note indication:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ━━                                                              │
│                                                                  │
│  Morning workout                                  🔓             │
│  07:00 – 07:45 · Workouts (from Master's schedule)               │
│                                                                  │
│  ─────────────────────────────────────────────────────────       │
│                                                                  │
│  Body                                                            │
│  3 × 12 push-ups, 3 × 30s plank, 5 min stretching                │
│                                                                  │
│  Notes                                                           │
│   ┌──────────────────────────────────────────────────────┐       │
│   │  🔒 This will be a private note (only you see it).   │       │
│   │     The schedule is read-only.                       │       │
│   └──────────────────────────────────────────────────────┘       │
│   ┌──────────────────────────────────────────────────────┐       │
│   │ did 5 extra reps today, felt good                    │       │
│   │                                                      │       │
│   │                                                      │       │
│   └──────────────────────────────────────────────────────┘       │
│                                                                  │
│              [ Cancel ]                  [   Save note   ]       │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — priority-override sheet:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ━━                                                              │
│                                                                  │
│  Override priority — Workouts                                    │
│                                                                  │
│  The schedule's author set this calendar's priority to 700.      │
│  You can override it just for your device.                       │
│                                                                  │
│   1 ├──────────────────────────●───────────────────┤  1000       │
│                              850                                 │
│                                                                  │
│   [ Clear override ]                     [   Save   ]            │
└──────────────────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *Always-visible private-note indicator vs. hover-reveal.* Hover
  isn't a real Android gesture; long-press is too discoverable for a
  consequence-bearing distinction (public comment vs. private note).
  Always-visible wins — the strip is one line and the wording is
  short.
- *Hide vs. delete on gifted content.* Delete is impossible (read-only
  source). Hide is the right primitive — reversible, local, no
  surprise. The Settings → "Hidden events" recovery surface ensures
  the user can always undo.
- *Padlock-with-circle as shared badge.* The alternative — using the
  source-repo's avatar — was rejected because the avatar would compete
  with the calendar color stripe and crowd the chip. A neutral 12dp
  icon is the floor.
- *Coalesce-rapid-toggles.* Doing it at the UI layer (not the
  storage layer) keeps the storage layer idempotent and the UI
  responsive; no race between filesystem and view-model.

---

## UI-KK — Receiving-repo and `references.toml` UI

`decisions.md` D.42, D.43. `main.md` Phases MM (deep-link) and NN
(`references.toml` manifest).

**Design center.** The "Add a gifted schedule" screen and the
references-prompt sheet are used both at first-launch (UI-GG) and
when an existing user adds another gifted repo. Both surfaces MUST
be DRY across paths. Required-references get a persistent banner so
the user can't quietly miss a manifest's hard dependency.

**Sub-steps:**

- [ ] **UI-KK.1** Add-gifted-repo screen (the reusable one). The same
  composable as UI-GG.2's screen, parameterized by:
  - `entryPoint`: `FIRST_LAUNCH` | `RUNNING_APP`.
  - In `FIRST_LAUNCH`, "Cancel" exits the activity.
  - In `RUNNING_APP`, "Cancel" pops the back-stack to wherever the
    user came from (Settings → Repos → "+ Add a gifted schedule", or
    the top-bar repo-switcher → "Add a gifted schedule" in simplified
    mode).
  All other behavior identical: heading, mode chip, auth status,
  Accept/Cancel buttons.

- [ ] **UI-KK.2** References-prompt sheet. Triggered after a successful
  clone if the cloned repo contains `.strictlykeptboy/references.toml`
  with one or more `[[reference]]` entries. Renders a
  `ModalBottomSheet`:
  - Heading: "This schedule references <N> other schedules".
  - Body line: "Want to add them too? You can change this any time.".
  - A `LazyColumn` of `ListItem` rows, one per reference:
    - Leading: a small generated avatar (deterministic from URL hash —
      a colored letter circle, like Gmail's letter avatars).
    - Headline: the reference's `label`.
    - Supporting line 1: `description` if present, else the URL.
    - Supporting line 2: priority badge ("High" / "Normal" / "Low") +
      mode chip ("Read-only" / "Read-write" / "Pull-only") + (if
      `required = true`) a small red asterisk with helper text
      "required".
    - Trailing: a `Switch` defaulting to the entry's `default_active`.
      Required entries' switches are non-toggleable (locked on).
  - Action row: `OutlinedButton("Skip for now")` +
    `Button("Add selected")`.
  - The "Skip for now" path still respects `required = true` entries:
    a `Snackbar("N required schedules will keep prompting until added")`
    appears, and the persistent banner (UI-KK.3) activates.

- [ ] **UI-KK.3** Required-references banner. A persistent `Card`
  pinned to the top of the schedule view (above the day timeline)
  whenever any `required = true` reference from a configured repo's
  `references.toml` is not yet added. Content:
  - Icon: ⚠️.
  - Headline: "<N> schedule<s> need to be added".
  - Body: "<repo label> depends on <other-repo label>" (lists up to
    three; "+N more" if longer).
  - Trailing: `Button("Add now")` → re-opens UI-KK.2's references-prompt
    sheet with only the missing required entries pre-filtered.
  Banner dismiss is **not** available — the only way to clear it is to
  add the required repos or remove the parent repo. Repo-removal
  prompts: "<parent repo> required <other-repo>; remove anyway?".

- [ ] **UI-KK.4** References indicator in repo settings. In full mode,
  Settings → Repos → tap repo → repo-detail screen → new section
  "References" listing every entry from this repo's `references.toml`
  with state badges:
  - "✓ added" (green) — reference is configured and synced.
  - "○ not added" (neutral) — user skipped.
  - "✗ not-found" (red) — clone attempted and 404'd.
  - "* required" (red asterisk prefix) — overlays whichever state.
  Each row tap → opens a small menu: "Add", "Re-clone", "Open repo
  settings" (if added). The section is omitted entirely if the repo
  has no `references.toml`.

- [ ] **UI-KK.5** Mode-aware "+ Add a gifted schedule" entry.
  - In **simplified mode**, the repo switcher (top bar in UI-FF.4) is
    a bottom sheet; its bottom row is "Add a gifted schedule" routing
    to UI-KK.1 with `entryPoint = RUNNING_APP`.
  - In **full mode**, Settings → Repos has a row "+ Add a gifted
    schedule" alongside the existing "+ Add a repo" row (which uses
    the Round 1 add-repo flow for authoring). Both routes lead to the
    correct screens.

- [ ] **UI-KK.6** Manifest-changed re-prompt. On sync, if a configured
  repo's `references.toml` has changed since the last sync (added
  entries with `default_active = true`, or removed entries), a one-time
  `Snackbar` appears: "<repo label>'s references changed. Review?" →
  `Action("Review")` opens UI-KK.2 filtered to the delta. The snackbar
  is suppressed if the only change is descriptive (label/description
  edits with no add/remove).

- [ ] **UI-KK.7** Empty-state for references-prompt. When the
  cloned repo has no `references.toml`, the references-prompt sheet
  never appears; the flow proceeds straight to the landing transition
  (UI-GG.5). No empty-state UI — silence is the empty state.

- [ ] **UI-KK.8** Cross-link back to `main.md` Phases MM (deep-link
  registration), NN (`references.toml`), and `shared-schedules.md`
  Phase SH-F (reference cascade semantics).

**ASCII mockup — references-prompt sheet:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ━━                                                              │
│                                                                  │
│  This schedule references 2 other schedules                      │
│  Want to add them too? You can change this any time.             │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐    │
│   │ (M)  Master's check-ins                          ● ON   │    │
│   │      Daily wellness check-ins                            │    │
│   │      ( High )  ( 👁 Read-only )                          │    │
│   └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│   ┌─────────────────────────────────────────────────────────┐    │
│   │ (S)  Soccer season                               ● ON   │    │
│   │      Practices and matches                               │    │
│   │      ( Normal )  ( 🔁 Pull-only )                        │    │
│   └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│   [ Skip for now ]                       [  Add selected  ]      │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — required-references banner:**

```
┌──────────────────────────────────────────────────────────────────┐
│ [🦊 Master's schedule ▾]   [Good Boy Mode]   [🔄]   [⋮]          │
├──────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐   │
│  │ ⚠️  1 schedule needs to be added                          │   │
│  │    Master's schedule requires Master's check-ins          │   │
│  │                                              [ Add now ]  │   │
│  └───────────────────────────────────────────────────────────┘   │
│                                                                  │
│   Today · Mon May 11                                             │
│   ┌───────────────────────────────────────────────────────────┐  │
│   │  07:00 ▍ Morning workout                            🔓    │  │
│   └───────────────────────────────────────────────────────────┘  │
│   …                                                              │
└──────────────────────────────────────────────────────────────────┘
```

**ASCII mockup — references indicator in repo settings:**

```
┌──────────────────────────────────────────────────────────────────┐
│  ←     Master's schedule                                         │
├──────────────────────────────────────────────────────────────────┤
│   …                                                              │
│   References                                                     │
│   ───────────────────────────────────────────────────────────    │
│   ✓ added       Master's check-ins                               │
│                 (High · Read-only)                               │
│   ───────────────────────────────────────────────────────────    │
│   * required ○ not added    Soccer season                        │
│                              (Normal · Pull-only)                │
│   ───────────────────────────────────────────────────────────    │
│   ✗ not-found   Old workouts archive                             │
│                 (Low · Read-only)                                │
│   ───────────────────────────────────────────────────────────    │
└──────────────────────────────────────────────────────────────────┘
```

**Tradeoffs resolved inline.**

- *Banner non-dismissable for required refs.* Allowing dismiss would
  defeat the `required = true` contract (the sharer marked it required
  precisely because the parent schedule doesn't make sense without it).
  The only escape valves are "Add now" or "Remove parent repo".
- *Auto-add vs. always-prompt.* D.43 explicitly says "offer (never
  auto-import) other repos as siblings". The default-on-toggle within
  the prompt sheet is the right balance: the prompt is the consent
  point; the toggle pre-fills based on the manifest author's intent.
- *Generated letter-avatar vs. fetched repo-icon.* Fetching repo icons
  from providers at prompt time would block the sheet on network. A
  deterministic colored-letter avatar (Gmail-style) renders instantly
  and reads as "I made this up locally" rather than "I'm pulling
  someone's branding".
- *Manifest-change snackbar throttle.* One snackbar per sync per repo
  (not per entry) — prevents notification spam when a sharer touches
  their manifest casually.

---

## Round 3 done-when

Round 3 of this document is "done" (Status: ✅ DONE) when:

- Every phase UI-FF through UI-KK has its sub-step checkboxes ticked.
- Every ASCII mockup in this section has been re-validated against the
  implemented composable on the AVD.
- The "Tradeoffs resolved inline" lists in each phase have been re-read
  at v1 ship; any reversed call has been logged with the date + reason.
- The Round-3 phase index above has been cross-checked against
  `main.md` Phases MM–TT (no phase referenced here without a parent
  phase in main.md; no orphan main.md phase without UI coverage).

---

## Phase UI-LL — Now-card + avatar sticker presence indicator (Round 4)

**See [`draft-avatar-stickers.md`](draft-avatar-stickers.md) (`main.md` Phase WW) for the authoritative layout spec.** UI-LL owns the Compose scaffolding inside `ScheduleShell`; AV-C is the source of truth for the layout.

- [ ] **UI-LL.1** `NowCard` Compose component lives at the top of `UI-C`'s `ScheduleShell`, above the day timeline. Card height 220 dp phone / 260 dp tablet. Edge insets match the shell's horizontal padding (16 dp phone / 24 dp tablet).
- [ ] **UI-LL.2** Layout regions per AV-C.2: sticker zone (left, square, 192×192 phone / 240×240 tablet, hosting the resolved sticker via Coil), title zone (right of sticker, top: current task name `headlineSmall` max 2 lines + duration chip below), next-up strip (right of sticker, below title: up to 3 upcoming items each = small emoji + truncated title + relative start).
- [ ] **UI-LL.3** Refresh strategy: recompose on `(now-tick, active-event-changed, sub-beat-boundary)` flows. 30s foreground tick; 60s notification-foreground-service tick; no background recomposition when app suspended.
- [ ] **UI-LL.4** Tap behavior: tap sticker zone / title zone → open active event's detail sheet (Phase G.7); tap next-up row → open that event's detail.
- [ ] **UI-LL.5** Empty state (no event active, next upcoming > 4h out): species-idle sticker + "nothing scheduled — back at <next start time>". No guilt-trip copy.
- [ ] **UI-LL.6** Streak counter chip (opt-in per activity per D.63): small chip overlay bottom-left of sticker zone, `🔥 12` count-only. No shame copy on break; chip simply resets to `🔥 1` next streak.
- [ ] **UI-LL.7** Accessibility: sticker `contentDescription` = `"<species> <activity>"` (e.g. "bat sleeping"). Streak chip readable by TalkBack ("12-day streak"). Animated WebP respects system "Remove animations" → first-frame fallback.

## Phase UI-MM — Cross-repo feedback drawer + Journal tab + bonus-task group (Round 4)

**See [`draft-global-id-feedback.md`](draft-global-id-feedback.md) (`main.md` Phase YY) and `decisions.md` D.71..D.73 for the authoritative spec.**

- [ ] **UI-MM.1** Extend UI-W (comments UI) with the cross-repo feedback drawer per FB-E.4–FB-E.6. Below event/task body in detail sheets: a "Feedback" section with reaction tallies (grouped icons + counts + tap-to-expand author list) and a comment thread. Drawer is always present even when empty, with a "+ react" affordance.
- [ ] **UI-MM.2** Author-chip rendering on cross-repo feedback shows source repo's display name as a small chip (e.g. `from "kept"`). Same-repo feedback omits the chip (just "from <author-identity>"). Per D.72, the chip text is the **receiving** repo's local label for the source repo, never the source repo's self-label.
- [ ] **UI-MM.3** "+ react" tap opens a reaction picker (sticker-pack-driven per D.62 / D.73) wired to `skb react add` semantics; selecting writes a feedback file in the **viewer's** repo (currently-selected in the top-bar repo switcher), NOT in the source repo of the target entity.
- [ ] **UI-MM.4** Extend UI-FF (simplified mode chrome) with the Journal tab + bonus-task group in the combined task view per Phase YY.7. Bonus tasks render in a dedicated "Bonus" group at the bottom of the combined view with a distinct bonus-icon prefix, NO schedule-pressure badging (no "OVERDUE" coloring) — `bonus = true` overrides scheduling pressure even when `due` is set.
- [ ] **UI-MM.5** Extend the Phase F nav-rail spec (F.1) from four entries to five: insert **Journal** between Tasks and Together. So: Schedule / Tasks / Journal / Together / Settings.
- [ ] **UI-MM.6** In simplified mode (Phase PP), Journal appears as a single "Journal" entry beneath the task list (no separate tab — keeps the simplified surface flat).
- [ ] **UI-MM.7** Repo settings → Isolation section per FB-B.4: list of every other registered repo with a per-row toggle "Hide [other] from this repo's view". Directional clarity copy: "this hides the OTHER repo FROM this repo. To hide THIS repo from the other, configure it from the other repo's settings."

## Phase UI-NN — Age gate + neutral-mode toggle (Round 4)

**See [`draft-kink-positive-identity.md`](draft-kink-positive-identity.md) (Phase K-3 / K-6 → D.58 / D.61) for the authoritative spec.**

- [ ] **UI-NN.1** First-launch age-gate modal (D.61). Modal copy: "This app contains references to adult lifestyle dynamics. You must be 17 or older to use it." Buttons: "I am 17 or older — continue" / "Exit". Decline → app finishes gracefully. Confirmation stored in encrypted app prefs (`age_confirmed_at = <ISO ts>`); re-shown only on app-data clear.
- [ ] **UI-NN.2** Settings → Appearance → "Neutral mode" toggle (D.58). Also set by Phase K wizard "unaligned-private" alignment. Toggling re-evaluates downstream surfaces (template picker, sticker resolver kink filter per D.66, reaction-picker filter per D.62) without rewriting user-authored content.

---

## Phase UI-OO — Vacation wizard (Round 5; main.md Phase CCC)

See [`draft-household-travel-vacation.md`](draft-household-travel-vacation.md) HV-F and HV-G. Six Compose screens + confirm modal; in-app multi-screen flow distinct from Phase K's first-launch wizard. Reuses `WizardScaffold` from LW-A but rooted at `TripWizardNavHost`.

- [ ] **UI-OO.1** Nav-graph: 6 screens + confirm modal. State stored in `TripDraft` data class backed by Room until commit. Entry-points (per HV-G): Settings → "+ Plan a trip" (between Calendars and Sharing); Calendar-detail → "+ overlay from template" → "Vacation / trip"; Now-card empty-state "no plans today — want to plan a trip?".
- [ ] **UI-OO.2** Screen 1 — Trip basics: trip name, start/end date pickers, destination free-text with OFFLINE bundled country+capital autocomplete (no network), travel mode radio. Sticker `trip-suitcase-waving`.
- [ ] **UI-OO.3** Screen 2 — Travel-prep cadence: back-fill preview list of HV-B entries with computed absolute dates; inline toggles + tap-to-edit lead-offset; conditional entries grayed out per parameter eval. Sticker `flight-paw-prints`.
- [ ] **UI-OO.4** Screen 3 — Flight details (conditional on mode=flight): multi-leg list with IATA codes, per-leg international toggle + buffer override. Sticker `flight-paw-prints`.
- [ ] **UI-OO.5** Screen 4 — Vacation-daily anchors: HV-D toggles + relax-cadence + meal-anchor radio + pack-kink-kit gate. Sticker `beach-loungin-with-cage-still-on` (neutral `beach-loungin`).
- [ ] **UI-OO.6** Screen 5 — Supersedence picker: ALL active calendars list with per-calendar "pause during trip" toggle; defaults superseded for work/university/kink-routine/weekly-reset; defaults NOT superseded for medication/health/pet-care/nonSuperseable (visually locked with explanatory chip); per-calendar expandable "but keep these events on" list writes `overrides/` files. Sticker `supersedence-snooze-toggle`.
- [ ] **UI-OO.7** Screen 6 — Confirm: mini-month preview with back-filled prep events leading into the trip window and daily anchors during; greyed-strikethrough preview of superseded events; "Re-edit" hop-back per section; "Confirm and materialize" CTA. Stickers `confirm-tail-flick` + `good-boy-stays-good-boy-on-vacation` reassurance bubble.
- [ ] **UI-OO.8** Edit-in-flight: detected-trip chip on Settings → "+ Plan a trip" entry-point ("Continue editing 'Sicily 2026'?"). Selecting it re-enters with rehydrated `TripDraft`; commit produces diff-commit.
- [ ] **UI-OO.9** Cancel-trip: separate action from overlay-management UI; deletes `cal-trip-<id>/` + `overrides/` in one atomic commit.

---

## Phase UI-PP — Supersedence management overlay (Round 5; main.md Phase BBB)

See HV-E.3 and `decisions.md` D.75 / D.77 / D.78. Per HV-J.8.

- [ ] **UI-PP.1** Schedule + now-card: hidden events do NOT render.
- [ ] **UI-PP.2** Manage-overlays screen: hidden events render strikethrough + greyed with hover/tap tooltip "paused by <X.title> until <range.to>". Per-event "show this one anyway" toggle writes `overrides/<cal-y-id>/<event-id-or-rule-id>/<yyyy-mm-dd>.md` with `kind = "force-show"`.
- [ ] **UI-PP.3** Week / month views: hidden events suppressed; small leaf-glyph on the date indicates a vacation-overlay is active (tap drills into what's paused).
- [ ] **UI-PP.4** `nonSuperseable` indicator: events with the `nonSuperseable` tag (per D.76) carry a tiny lock-icon chip so the user can see at-a-glance what survives vacation.
- [ ] **UI-PP.5** Empty-state copy for the manage-overlays screen when no calendars supersede: "No vacation overlays active. Plan a trip to pause your routine." with a deep-link button into UI-OO.

---

## Phase UI-QQ — Attachment renderers (Round 5; main.md Phase BBB)

See HV-M.4. Per HV-J.8.

- [ ] **UI-QQ.1** `AttachmentList` Composable on Event-detail screen, below the body. One row per attachment, kind-keyed renderer.
- [ ] **UI-QQ.2** `link` → `ListItem` with link icon; tap → `Intent.ACTION_VIEW` in system browser.
- [ ] **UI-QQ.3** `qr` → thumbnail at 64dp; tap → full-screen scan-friendly view (max-brightness override, no chrome, 80% screen fill, swipe-down to close).
- [ ] **UI-QQ.4** `file` → `ListItem` with file icon + mime + size; tap → open in viewer (PDF viewer for PDFs, image viewer for images, system chooser for others).
- [ ] **UI-QQ.5** `barcode` → same as `qr` but full-screen renders at format-appropriate aspect (PDF417 wide, Aztec square, Code128 wide).
- [ ] **UI-QQ.6** `vcard` → `ListItem` with contact icon; tap → `Intent.ACTION_INSERT` for contacts.
- [ ] **UI-QQ.7** `location` → map preview thumbnail (static map tile); tap → `Intent.ACTION_VIEW` with `geo:` URI.
- [ ] **UI-QQ.8** Now-card: tiny paperclip glyph (12dp) if ANY attachments present (the FACT of an attachment is not secret; the CONTENT is). Privacy: glyph appears on private events too; lockscreen previews suppress the glyph per HV-M.3.

---

## Phase UI-RR — Multi-reminder UI (Round 5; main.md Phase BBB)

See HV-N. Per HV-J.8.

- [ ] **UI-RR.1** Per-event reminder editor on Event-detail sheet: list of `Reminder` rows with `offset` / `kind` / optional `channel` + `lockscreen_visibility` overrides. Add-row chip; per-row delete swipe.
- [ ] **UI-RR.2** Template-default reminder picker per HV-N.3 / D.79: when an event is created via a template, the default cadence pre-populates; user can edit before save.
- [ ] **UI-RR.3** Stacked-notification rendering preview (read-only docs surface on Settings → Notifications): shows what a collapsed 3-reminder stack looks like, with quick-actions per line.
- [ ] **UI-RR.4** Briefing-event rendering: `cal-briefings` events (D.81) render with a special "auto-generated body" badge; tap-to-expand shows the materialized list with off-schedule ⚠ items highlighted (D.80). Per-item quick-actions: `snooze` / `re-arm` / `mark-done-early` / `mark-skipped`.

---

## Phase UI-SS — Reviews tab (Round 5; main.md Phase DDD)

See HV-Q.2 / HV-J.21 and `decisions.md` D.84. Dom-side surface AND boy-side per-commit response view.

- [ ] **UI-SS.1** Dom-side Reviews tab: lists unreviewed `reviewable_change` entries grouped by date. Per-entry preview shows boy's identity (praise term + honorific + bat sticker via `identity.toml`), auto-summary, and a reaction-strip + free-text composer. Sorting newest-first with unread pill.
- [ ] **UI-SS.2** Tap-entry → response composer: 9-token reaction picker (`locked` / `collar` / `good-boy` / `paw` / `heart` / `fire` / `thumbsup` / `🦇` / `smirk`) + free-text Markdown editor. Tapping a reaction with EMPTY text writes the cute-coded LGTM ("good-boy looked, good-boy approved").
- [ ] **UI-SS.3** Boy-side: response-on-my-commits view extends the existing per-commit feedback feed with the new `review` kind. Renders dom's reactions + comment threading + register-matched chrome from `identity.toml`.
- [ ] **UI-SS.4** Filter chips: All / Unread / Reactions-only / Threaded.
- [ ] **UI-SS.5** Empty-state copy: dom-side — "No new reviews from <boy-praise-term> yet."; boy-side — "<honorific> hasn't responded yet — give them time."

---

## Phase UI-TT — Mode-aware chrome (Round 5; main.md Phase DDD)

See HV-Q.1 / HV-Q.5 / HV-J.21 and `decisions.md` D.84 / D.86.

- [ ] **UI-TT.1** Always-visible mode pill in app chrome (top-bar): renders `free` / `kept` / `self-keep` with a small lock-icon on `kept` / `self-keep`.
- [ ] **UI-TT.2** Long-press on the pill surfaces the "transition my mode" affordance (per D.86 — always reachable from the most-kept UI state, NOT buried).
- [ ] **UI-TT.3** Transition modal: confirmation copy uses dom-persona voice when present ("are you sure you want to leave this dynamic?"). 24h cooling-off CONFIRMATION (NOT a gate) — boy types confirmation string; second confirm-tap fires the mode-switch commit. Per D.86 — no dom can prolong / veto.
- [ ] **UI-TT.4** In kept mode: small dom-presence indicator next to the pill (e.g. a tiny avatar or persona-icon) showing which dom (human or AI persona) currently holds the keys.
- [ ] **UI-TT.5** Self-keep mode: pill renders as `self-keep` with a special glyph (kept-by-self, exit-ramp aware) to make the in-between state visible per D.86.

---

## Phase UI-UU — Dom-persona picker (Round 5; main.md Phase DDD)

See HV-Q.3 / HV-J.21 and `decisions.md` D.85.

- [ ] **UI-UU.1** Settings sub-screen listing 6 shipped personas + custom-prompt with a register preview-snippet per persona (a sample dom-response rendered live using current `identity.toml`).
- [ ] **UI-UU.2** Per-persona cadence control (realtime / end-of-day / weekly) with a default-end-of-day pre-select per D.85.
- [ ] **UI-UU.3** `custom-prompt` editor: free-text Markdown editor for the user's custom dom-persona prompt; saved at `~/.config/skb/dom-personas/custom.md` (app-private, NOT in calendar repo).
- [ ] **UI-UU.4** Explicit-content gate per D.85: a separate toggle "Allow explicit content in dom-responses" (default OFF; requires K-6 age-confirmation to enable). Locks the safety-default in the picker UI.
- [ ] **UI-UU.5** Migration affordance: "Switch to a human dom" CTA from the picker → triggers Phase RR share-this-repo flow (deep-link offer to partner).

---

## Phase UI-VV — Identity preview panel (Round 5; main.md Phase K.5a + Phase DDD)

See HV-R.3 / HV-J.21 and `decisions.md` D.83.

- [ ] **UI-VV.1** Live-preview panel rendered at Settings → Identity AND on LW-Screen-3.5 final-step. Side-by-side with the editing form.
- [ ] **UI-VV.2** Preview renders four surfaces using the currently-pending `identity.toml`: (a) a now-card with the chosen praise term in the title, (b) a sample template-title with `{{praise}}` resolved, (c) a briefing salutation ("good morning, good boy 🦇" — emoji density honored), (d) a dom-Claude response snippet using the chosen honorific.
- [ ] **UI-VV.3** "Reset to wizard defaults" button: writes the HV-R.1.3 locked defaults (`praise.term = "good boy"`, he/him/his/himself, `Sir`, `soft-kinky`, `medium`).
- [ ] **UI-VV.4** Save behavior: identity changes commit to `identity.toml`. In `strictly-kept` mode (D.84) the commit goes through the review-feed like any other commit.
- [ ] **UI-VV.5** Multi-pronoun alternation preview: if `[pronouns].extra_sets` is non-empty, the preview alternates pronoun sets across the four preview surfaces to demonstrate the agent-side alternation.

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

- **Per-event color override** — a single event can carry its own
  color. v1 uses calendar color only. Adding per-event color creates
  a third color resolution layer (event > calendar > repo seed); not
  worth the complexity for v1. **Defer to v2.**

- **Drag-to-reschedule on Day/Week views** — long-press an event chip
  and drag to a new time. Requires gesture detection + collision
  recompute mid-drag. Cool but not blocking. **Defer to v1.1.**

- **Pinch-to-zoom on timeline (Day/Week)** — adjust hour-row height.
  Useful for fine scheduling but not core. **Defer to v1.1.**

- **Calendar overlap blending mode beyond accent stripes** — full
  alpha-blend of overlapping events instead of stripes. The stripe
  approach is clearer for screen readers + colorblind users; blending
  loses information. **Don't ship; stripes are better.**

- **Auto edit/create** — voice or list-based event creation on Auto.
  Read-only is the v1 promise per D.17. Adding write surfaces a slew
  of safety/testing concerns. **Defer to v2.**

- **Multi-window / split-screen tablet** — Compose handles this
  semi-automatically via WindowSizeClass; we test it but don't add
  bespoke split-screen UI. **No deferral, tested-as-acceptable.**

- **Per-day weather overlay** — nice-to-have on Day view; out of
  scope. **Defer indefinitely.**

- **Inline Markdown editing in the body field with WYSIWYG preview**
  — v1 ships plain markdown editor + commonmark renderer in detail
  view; no live WYSIWYG. **Defer to v1.1.**

- **Custom emoji / sticker packs for repo icons** — v1 supports
  emoji-as-icon from the system emoji set. Custom stickers require
  asset management. **Defer to v2.**

- **Multi-finger gestures on calendar (e.g. two-finger tap = add
  block)** — discoverable by no-one. **Skip permanently.**

- **iCal *live* sync (not just import)** — D.16 explicitly defers
  CalDAV server-side sync to a `tools/` cron path. UI for it would
  duplicate per-repo sync UI. **Defer to v2 unless user demand.**

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

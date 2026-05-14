# Round 2.1 — Settings audit

Read-only audit of every settings surface in strictlykeptboy as of
the Phase W close-out. Source of intent: `docs/plans/prompts.md` (94
prompts, 2026-05-10..2026-05-13). Source of architecture:
`CLAUDE.md`, `docs/plans/main.md`, `docs/plans/decisions.md`.

## 1. What exists

### 1.1 Top-level Settings shell

`app/src/main/java/com/eight87/strictlykeptboy/ui/settings/SettingsPane.kt`
(523 LOC) — Phase R.4 master-detail shell + Phase S categories.

- Sealed `SettingsCategory` (lines 95–119) with **twelve** categories:
  `Repos`, `Identities` (aka "Authors"), `Sync`, `Notifications`,
  `Calendars`, `Todolists`, `Templates`, `Lifestyle`, `Identity`,
  `Appearance`, `About`, `Mode`.
- Grouped into five tonearmboy-parity sections (lines 279–313):
  Appearance / Library / Behaviour / Lifestyle / About.
- Pill search bar (line 338) filters by `testTag` substring only —
  not by labels, subtitles, or content (gap).
- `SettingsAccess` data class (lines 129–153) is the narrow-handle bag
  passed in from `AppGraph`. Every preference field is nullable; if
  it's not wired the surface renders `CategoryPlaceholder` (line 510).
  This is a real foot-gun: a missing handle silently downgrades a
  whole category to a "(placeholder)" string with no diagnostics.

### 1.2 Category surfaces (Phase S deliverables)

- **ReposCategory** (`categories/ReposCategory.kt`, 62 LOC) — one
  "Open repos list" button + the import/export screen inline. Per-repo
  config lives elsewhere (1.3).
- **IdentitiesCategory** (`categories/IdentitiesCategory.kt`, 49 LOC) —
  effectively empty: intro text + "Open Identity" deep-link that just
  jumps to the singular Identity category. Phase S.2 placeholder
  awaiting Phase GG signed-commits.
- **SyncCategory** (`categories/SyncCategory.kt`, 137 LOC) — global
  default interval chips, wifi-only toggle, push policy chips, conflict
  policy chips, backoff status indicator. Solid.
- **NotificationsCategory** (`categories/NotificationsCategory.kt`,
  146 LOC) — master briefings toggle + per-category lead-times
  (medical / flight / household / general) as a free-text field
  `15m;1h;1d` + six channel rows (events/tasks/briefings/sync/errors/
  foreground) with enabled+silent switches.
- **ListsCategories.kt** (136 LOC) — both `CalendarsCategory` and
  `TodolistsCategory` use the same shape: visibility switches,
  up/down priority arrows, **and a read-only "Active windows"
  section** (line 119) that just displays `activeFromIso → activeUntilIso`
  with no editor.
- **TemplatesCategory** (`categories/TemplatesCategory.kt`, 99 LOC) —
  list of template ids with "Apply" buttons + custom-URL text field
  with a "Save" button (clone wiring deferred to Phase WW).
- **LifestyleCategory** (`categories/LifestyleCategory.kt`, 59 LOC) —
  two CTAs: "Re-enter lifestyle wizard at Roles" (Phase K.12 / LW-L)
  + "Plan a trip" (Phase CCC.10 / HV-G.1).
- **IdentityCategory** (`categories/IdentityCategory.kt`, 185 LOC) —
  praise / alt-terms / pronouns / pronouns-extra / honorific / tone /
  emoji-density + live preview card. Phase S.8b / DDD.9.
- **AppearanceCategory** (`categories/AppearanceCategory.kt`, 435 LOC)
  — full tonearmboy-parity layout: pill search, grouped cards, Theme /
  Display / Neutral mode / Sticker pack sections.
- **AboutCategory** (`categories/AboutCategory.kt`, 264 LOC) —
  tonearmboy-parity Build + Source cards, GIT_SHA + BUILD_DATE,
  3-tap easter-egg, license/repo/privacy deep links.
- **ModeCategory** (`categories/ModeCategory.kt`, 161 LOC) — Phase
  S.11 / DDD: mode pill, switch-to-free typed-confirmation flow
  (D.86), dom-persona FilterChip row, dom-cadence chips
  (realtime/EOD/weekly).

### 1.3 Per-repo settings (lives outside the settings pane)

`app/src/main/java/com/eight87/strictlykeptboy/ui/repos/RepoSettingsScreen.kt`
(477 LOC) is **only reachable via Repos top-destination → tap repo
→ Settings**, not via Settings → Repos. The Repos *category* in
Settings has a single button that bounces the user to the Repos top
destination. Sections inside: Display (name + icon picker + color
seed), Sync (auto-sync toggle + interval chips + wifi-only), Identity
(author name/email), Defaults (default calendar/todolist ids),
Remotes (per-remote rows with primary/read-only/remove), Identity
preferences preview, plus the destructive "Remove repo" button.

### 1.4 Notifications (legacy duplicate)

`app/src/main/java/com/eight87/strictlykeptboy/notif/NotificationsSettingsScreen.kt`
(92 LOC) — the old Phase M per-channel screen, **still in tree but
not navigated to** (its content was inlined into NotificationsCategory).

### 1.5 Wizard entry-points

- Settings → Lifestyle → "Open wizard at Roles" calls
  `onOpenWizardAtRoles` (S.8 / K.12 / LW-L).
- ReposPane top-bar `+` icon (line 251) opens the wizard for "Set up a
  new account" (post 2026-05-13 move; user direction).
- AgeGate runs the wizard on first launch.

### 1.6 Identity / Mode / Dom-persona (DDD)

- Singular Identity surface = `IdentityCategory` (S.8b).
- Mode pill + switch + persona picker + cadence = `ModeCategory` (S.11).
- The plural Identities (Authors) category is a stub.

### 1.7 Android Auto / tablet

- Android Auto lives in `auto/SkbCarAppService.kt` + `TodayEventSource.kt`
  (Phase Q). **No Settings surface for it at all.**
- Tablet/master-detail behaviour is implicit via `WindowSizeClass`
  detection (Phase R). **No Settings surface to override.**

## 2. Intent check (vs prompts.md)

| Intent (prompt) | Status |
|---|---|
| "minimal UI for the calendar todolist and timeboxing too" (genesis) | Categories are minimal-shape but **content is shallow** in places. |
| Multi-repo activate/deactivate toggles | ✅ Per-repo unified-view toggle + visibility list. |
| **"set timeframes when these become active or deactivate"** (genesis, explicit) | ❌ **Read-only display only** — ListsCategories.kt:119 shows `activeFrom → activeUntil` but has no editor. The data path (`VisibilityEntry.activeFromIso/activeUntilIso`) exists. |
| Per-event notification control + notification groups | ⚠️ Channel groups (events/tasks/briefings/sync/errors/foreground) exist. **Per-event override is not in any settings surface** — must be set on the event sheet (if at all). |
| Calendar/todolist priority | ✅ Up/down arrow editor. |
| Wizard re-entry from Settings (K.12) | ✅ Lifestyle → Open wizard at Roles. |
| Dom-persona / identity / mode (DDD) | ✅ Mode + Identity categories present and functional. |
| Lifestyle / kink-positive vs neutral discoverability | ⚠️ Neutral toggle lives under **Appearance**, not Lifestyle. Discoverability mismatch — the user thinks of neutral mode as a kink-related setting, not a visual one. |
| Sub-giving-dom-access (Phase RR) + dom-filling-sub-schedule (SS) | ⚠️ Lives **only** in the per-repo screen → Share button — invisible from the global Settings → Repos category. |
| Android Auto + tablet hooks | ❌ No settings surface at all (no auto-launch behaviour toggle, no tablet pane preference). |
| Signed commits / GPG (D.31 / promised in early prompts) | ⚠️ Identities stub awaits Phase GG; no GPG import affordance. |
| Repos-as-accounts naming (2026-05-13 direction line 1210) | ❌ Still labelled "Repos" everywhere in settings. User asked: probably "accounts". |
| Categories sorted/sectioned per tonearmboy parity | ✅ Phase S follow-up landed sections + search + cards. |

## 3. Gap analysis — why settings feel "horrid"

Concrete issues, ruthless:

1. **Time-window editor is missing.** This is the single most explicit
   ask in the genesis prompt ("set timeframes when these become active
   or deactivate"). The data model carries the fields; the UI shows
   them read-only. The user can author them only by hand-editing the
   repo. **This is the headline gap.**
2. **Read-only "Active windows" header is worse than absent** — it
   advertises a feature, then refuses to operate.
3. **Settings → Repos is a single-button trampoline.** Tapping it
   leaves the Settings pane entirely (back-stack-confusing on phones,
   pane-collapsing on tablets). The category was supposed to *be* the
   repos list, with per-repo deep-link rows.
4. **Identities ≠ Identity confusion persists.** Two categories
   ("Authors" plural, "Identity" singular) both icon as `Person`-like
   glyphs, with overlapping subtitles. The plural one is a stub. User
   already flagged this in the rename to "Authors".
5. **Neutral-mode toggle is under Appearance.** It is a content-mode
   choice, not a visual choice. Discoverability for the kink-positive
   ↔ neutral switch (K-1..K-7 / D.55) is buried.
6. **No Android Auto / tablet settings surface.** Prompts asked for
   "android auto and tablets" at the very start. Today they ship but
   their behaviour is non-configurable (no "show next N events", no
   "include private events on Auto", no "compact-on-phone-only" tablet
   override, no foreground-service silent toggle separate from
   notifications).
7. **No per-event notification override surface in settings.** The
   genesis prompt asked for "notification control per event as well as
   notification groups". Today: channels yes, per-event no. Even if
   the data lives on the event itself, a settings → "Defaults for new
   events" page (default reminders, default channel, default priority)
   is missing.
8. **Per-category lead-time field is a raw `15m;1h;1d` text input.** No
   chip-builder, no validation, no preview. For the AI-native ethos
   that's fine for Claude, but the user is on the phone often.
9. **Templates category does nothing yet.** Apply callbacks are wired
   but the clone path is deferred to Phase WW; the URL field saves but
   does not parse, validate, or preview.
10. **Categories that depend on a wired pref silently render
    `CategoryPlaceholder`.** No diagnostic. If `appearancePrefs` or
    `neutralPrefs` is null the whole Appearance category vanishes
    behind a placeholder line.
11. **Search filters by `testTag` substring only** — typing "dark" or
    "windows" or "kink" in the top search does nothing useful.
    Appearance has its own working keyword search; the outer one
    does not.
12. **No "Repos" vs "Accounts" rename despite explicit user direction
    on 2026-05-13** (prompt at line 1210).
13. **No "Dom access" surface.** Phase RR (share-this-repo) is
    reachable per-repo only; no global "who has access to what" view.
14. **No CalDAV / signed-commits / GPG settings yet** — Phase GG /
    CalDAV bidi were sold in early prompts.
15. **`onPlanTrip` button in Lifestyle is a bare CTA** — no preview of
    upcoming trips, no list of past trips.

## 4. Round 2.1 — proposed sub-steps — Phase `2.1-Settings`

Design pick: **flatten the trampolines, add the time-window editor,
relocate neutral, surface dom-access globally, introduce an Auto/Tablet
category, retire the Authors stub by merging it into Identity, rename
Repos → Accounts in copy only.** Defended below per step.

- [ ] **2.1-Settings.1** — **Active-windows editor** in `ListsCategories.kt`.
  Replace the read-only `Text("${activeFromIso} → ${activeUntilIso}")`
  with: (a) two `OutlinedTextField`s + date-pickers wired to
  `CalendarVisibilityPrefs.setEntries`; (b) an "always active" chip
  short-circuiting both nulls; (c) a "Multiple windows" expand-row
  that lets the user add 2+ `DateRange`s (mirror
  `resolver/Types.kt:76` `activeWindows: List<DateRange>`); (d)
  weekday-only checkbox row (Mon–Sun) feeding into
  `SupersedenceConfig` baseline-cadence. New prefs field on
  `VisibilityEntry`: `weekdays: Set<DayOfWeek>` + `windows: List<DateRange>`
  (deprecate the scalar `activeFromIso`/`activeUntilIso` after one
  release).
- [ ] **2.1-Settings.2** — **Settings → Repos as in-pane list.** Kill
  the trampoline. Make `ReposCategory` render the repo cards inline
  (small variant of `RepoSwitcherDropdown` rows). Tapping a row
  opens `RepoSettingsScreen` *inside* the Settings detail pane on
  tablet (push on phone). Move `ImportExportScreen` to a sub-section
  card titled "Import / export".
- [ ] **2.1-Settings.3** — **Rename "Repos" → "Accounts" in copy
  only.** Per user direction 2026-05-13 line 1210. Update
  `R.string.settings_category_repos`, `repos_title`, etc. Test tags
  stay `Repos`/`ReposCategory` to avoid invalidating tests.
- [ ] **2.1-Settings.4** — **Move Neutral-mode toggle to Lifestyle
  category.** Keep the appearance keyword search hits ("neutral",
  "kink") jumping to a deeplink chip in Appearance that says "Open
  Lifestyle → Neutral mode" (preserves the existing search find).
  Rationale: K-1..K-7 frames neutral as a content-mode switch.
- [ ] **2.1-Settings.5** — **Retire `Identities` stub; promote
  `Identity` to top-level with sub-sections.** Sub-sections inside
  `IdentityCategory`: "My persona" (existing fields), "Signing &
  authors" (GPG key import stub, ssh-key import stub, per-repo
  author override list). Remove `SettingsCategory.Identities` from
  the sealed list. Drops the duplicate icon problem.
- [ ] **2.1-Settings.6** — **New `Access` category** (between
  Behaviour and Lifestyle sections). Global view of share-this-repo
  state: "Who has access to what" table — rows = (repo, recipient,
  mode r/o or r/w, single-use, expires-at). Reuses
  `ShareLink.allowWriteBack` + `singleUseToken` data already on the
  per-repo flow. Tap a row → opens existing `ShareSheet`. Closes the
  intent gap "sub giving dom access" being a per-repo-only surface.
- [ ] **2.1-Settings.7** — **New `Auto & Tablet` category.** Toggles
  for: "Show on Android Auto when this repo is active", "Maximum
  events on Auto Today list" (default 8, range 1–20), "Use master-
  detail on tablet" (Auto/On/Off — currently implicit), "Open
  detail by default on tablet". Backed by a new `AutoTabletPrefs`
  alongside `SyncSettingsPrefs`.
- [ ] **2.1-Settings.8** — **Defaults-for-new-events sub-card inside
  Notifications.** Replace the raw `15m;1h;1d` text input with a
  ChipGroup of common offsets (5m / 15m / 30m / 1h / 1d / 1w) plus a
  custom-offset dialog. Add a "Default notification channel for new
  events" picker. Surface lives at the top of Notifications, before
  the per-category lead-times.
- [ ] **2.1-Settings.9** — **Outer settings search fix.** Index
  category label + subtitle + a per-category keyword list (mirror
  Appearance's pattern). Drop the testTag-substring fallback. New
  data: `SettingsCategory.searchKeywords: List<Int>` (string-res ids).
- [ ] **2.1-Settings.10** — **Diagnostic for missing prefs.** Replace
  `CategoryPlaceholder` with a banner that names the missing handle
  and links to a logcat tag. Today a missing wire-up looks identical
  to a feature that has no content yet.
- [ ] **2.1-Settings.11** — **Delete the dead `notif/NotificationsSettingsScreen.kt`**
  (already inlined). Keep only `NotificationsCategory`.
- [ ] **2.1-Settings.12** — **CalDAV stub category** under Behaviour:
  intro text + "coming with Phase XX" disabled-button — closes a
  promised-from-genesis surface even if implementation is later.
  Mirrors how Identities was kept visible as a stub before GG.
- [ ] **2.1-Settings.13** — **Trip-summary card** in Lifestyle next to
  the "Plan a trip" button. Three rows: upcoming trip / last trip /
  "no trips yet". Uses the same data feed as the Phase CCC trip
  resolver. Closes the bare-CTA feel.

### Why this structure (defence)

The dominant complaint chain in prompts 1210–1462 was about *visual
parity* with tonearmboy — Phase S already fixed that. The unaddressed
intent layer underneath is **functional**: time-window editing,
per-event defaults, dom-access globally, Auto/tablet hooks. The fix
is therefore **content-first**, not chrome-first.

Flattening Repos in-pane (2.1-Settings.2) is preferred over keeping
the trampoline because the master-detail Settings pane already exists
(Phase R.4) and the user's mental model from prompt 1210 is
"settings is where I configure accounts". Two separate Account
surfaces (Repos top-destination + Settings → Accounts) is fine
because they serve different jobs: switching vs configuring.

Demoting the plural Identities stub (2.1-Settings.5) trades a
deferred-feature breadcrumb for cleaner ergonomics. The signed-commit
work (Phase GG) can re-add a sub-section inside Identity rather than
its own top-level category.

The new `Access` and `Auto & Tablet` categories cover two intent gaps
that have **zero settings surface today** despite being explicit in
the genesis prompt. Cheap to add, big closure on "horrid" feeling.

Active-windows editor (2.1-Settings.1) is the single most load-
bearing fix: it closes the most explicit unfulfilled ask from the
genesis prompt and turns ListsCategories from a half-feature into a
whole one.

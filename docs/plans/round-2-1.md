# Round 2.1 — Make it actually make sense

## Status: PENDING — planning complete, implementation not started

## Why this round exists

The audit found the same pattern in every surface: **data layer correct, UI bindings missing or wrong.** The user's genesis intent — multi-repo overlay, calendars-first, time-window scoping, cross-repo authorship, identity-driven copy — exists in code but does not reach the screen.

Most damning evidence:

- **`AppGraph.snapshot` is an empty `MutableStateFlow`** with a "Phase F→G integration still pending" comment (`composition/AppGraph.kt:198-219`). The production schedule shows empty data; only demo seeds ever rendered. The resolver is correct, multi-repo-ready, and currently observing nothing.
- **`Renderer.filterForViewMode` actively drops superseded bands** (`resolver/Renderer.kt:143-150`) — the user's "vacation pauses routine" mental model is computed and discarded before reaching the view.
- **UI surfaces repos, not calendars.** No per-calendar chip strip, no per-calendar settings sheet. `activeRepoName` defaults to literal `"demo-repo"`. Time-window activation (`activeWindows: List<DateRange>` + `activeHours`) is read-only in `ListsCategories.kt:119` — the data model carries it, the editor does not exist.
- **Identity & Mode prefs never write back to `identity.toml` / `mode.toml`.** Wizard writes them once at scaffold-time; Settings edits live in `SharedPreferences` and never round-trip to disk. DDD.11 (notif bodies read identity) is unwired — zero grep hits for praise terms in `notif/`.
- **First-launch never auto-routes to the wizard.** User lands on an empty Schedule with a `demo-repo` placeholder. Discovery path: age-gate → bat avatar → Repos → "+", which is unobvious.
- **`RECEIVE_BOOT_COMPLETED` permission held but no boot receiver registered.** Reminders die on reboot.
- **Tablet master-detail shipped only for Schedule, Tasks, Settings.** Repos, Wizard, Together, Trip, Reviews, Deeplink, ImportExport, Share have **zero** `WindowSizeClass` adaptation.
- **Notif bodies have zero `IdentityToml` reads.** Per-calendar group toggle prefs exist (`cal.<repoId>.<calId>.enabled`) with zero UI binding. Briefings master toggle controls nothing — no firing path, no body generator, no `cal-briefings` seed.

The fix is not architectural. The fix is wiring, removing stubs, and adding a small set of UI surfaces that the data layer is already prepared for.

## Locked design decisions (Round 2.1)

- **D-2.1.a — Calendars are the user-facing primary; repos are plumbing.** The schedule view shows the union of all enabled calendars across all repos. The top-bar avatar drives the *write* target. The *read* surface is unified by default.
- **D-2.1.b — Per-calendar (not per-repo) enable toggles.** Calendar chips live above the schedule grid; toggling one chip toggles its visibility globally. Per-repo "hide all from this repo" is a derived bulk action, not the primary unit.
- **D-2.1.c — Time-window activation lives in the calendar's own `calendar.toml`** (`activeWindows: List<DateRange>` + `activeHours: List<HourRange>` + `activeToggle: Boolean`). Stored IN the source repo so it round-trips through git. The local `CalendarVisibilityPrefs` becomes a phone-local override layer (visible/hidden on this device only), not the source of truth for active-windows.
- **D-2.1.d — "Active repo" still exists for write-targeting only.** Renamed to `defaultWriteRepoName` internally; user-facing "Accounts" copy in Settings. Repo switching is a write-context switch, not a view-context switch.
- **D-2.1.e — Author chip on every event band when source repo differs from the active write-target repo.** Generalize Phase O's `ForeignEventSourceChip` to all multi-repo views, not just shares.
- **D-2.1.f — Identity & Mode prefs become thin caches over `identity.toml` / `mode.toml`.** Async write-back + commit. Active-repo switch reloads from disk.
- **D-2.1.g — Wizard auto-runs at first launch.** No empty-Schedule placeholder. `defaultWriteRepoName` defaults to `""`, not `"demo-repo"`.
- **D-2.1.h — Submissive alignment defaults to mode=strictly-kept (kept-by-AI).** Other alignments default to free. The wizard gets an explicit Mode screen so this is visible, overrideable, and the default is a real fulfilment of the genesis use-case rather than a silent free-mode emission for everyone.
- **D-2.1.i — Identity-driven copy reaches notifications, briefings, and Auto rows.** Praise term + honorific in body lines; `private = true` collapses to generic. Briefings actually run (07:00 + 21:00 WorkManager) and the `cal-briefings` seed lands at wizard completion.
- **D-2.1.j — Settings restructure is content-first, not chrome-first.** Phase R/S already fixed visual parity with tonearmboy. Round 2.1 closes the functional gaps: time-window editor, in-pane Repos (kill the trampoline), Access (global "who has my repos" view), Auto & Tablet category, dedupe Identity/Identities.

---

## Phase ordering (strict dependency chain)

```
2.1.A  DataBridge          (PREREQ — blocks 2.1.B..2.1.L)
  ↓
2.1.B  Multirepo            ─┐
2.1.C  Schedule rendering   │── parallel; 2.1.C uses chip strip from 2.1.B
2.1.D  Tasks                ─┘
2.1.E  Settings restructure (independent of B/C/D; touches calendar editor → coordinate)
2.1.F  Notifications        (depends on 2.1.A + 2.1.J for identity reads)
2.1.G  Auto                 (depends on 2.1.J for identity copy)
2.1.H  Tablet               (parallel; 2.1.H.1 wizard pane depends on 2.1.I)
2.1.I  First-run + Wizard   (touches MainActivity + WizardNavHost)
2.1.J  Identity write-back  (depends on 2.1.A; unlocks 2.1.F, 2.1.G)
2.1.K  Mode + dom-persona   (depends on 2.1.J; sequenced after 2.1.I.3)
2.1.L  Polish + tests       (gate before main.md tick)
```

---

## Phase 2.1.A — DataBridge (PREREQ) — shipped in commit `99d8762`

Wires the indexer to `AppGraph.snapshot` + `AppGraph.sources`. Without this, every UI fix below is theoretical.

- [x] **2.1.A.1** Implement `IndexerSnapshotPublisher` in `composition/` that observes `CacheDatabase` DAOs + `RepoStore.state` and emits a `RepoSnapshot` (calendars + todolists across **all** enabled repos) to `AppGraph.snapshot`. One `combine` per active repo; fold lists; recompute `contentHash`.
- [x] **2.1.A.2** Implement `SourcesPublisher` that emits a visible-range-windowed `Renderer.Sources` (events + rules + exceptions + deviations + overrides) by querying the cache DAOs with the schedule pane's selected `DateRange`. Wire to `AppGraph.sources`.
- [x] **2.1.A.3** Unit-test both publishers with two seeded repos; assert `RepoSnapshot.calendars` is the union and `Renderer.Sources.events` is the union.
- [x] **2.1.A.4** Delete the empty-stub comment block at `AppGraph.kt:198-219`.

**Known follow-ons (deferred; rolled into Phase 2.1.B):**
- Calendar/todolist metadata richer fields (priority, supersedes, activeWindows, baselineCadenceDays, displayName, colorSeed) come from per-repo TOML files. `IndexerSnapshotPublisher` synthesizes defaults (active=true, priority=500, system tz) from distinct `calendarId`/`todolistId` values in event/rule/task rows. **Phase 2.1.B.1 (`CalendarRegistry`) reads these from `calendars/<id>/calendar.toml` via `RoutineCalendarConfig` + `SupersedenceConfig` and overlays onto the synthesized snapshot.**
- `RecurrenceRuleDao` / `TaskDao` lack `listAllFlow()`; publishers re-emit only via the events-table invalidation pulse. Out of 2.1.A scope (constraint: do not modify `CacheDatabase`); revisit if rule-only/task-only edits show staleness.

## Phase 2.1.B — Multirepo (calendars-first) — shipped in commits `2f14245` (B.1+B.6) + `e47daca`/`93ec030`/`ebfb5d6`/`df04aad` (schema ext + B.2-B.11)

**Schema extension landed.** New sibling codec `CalendarActivityConfig` rounds-trips `active_windows`, `active_hours`, `color_seed` keys in `calendar.toml`. SOLID-S split rationale: visibility-windowing + UI tinting are independent reasons-to-change from routine-library semantics; `CalendarActivityConfig` + `RoutineCalendarConfig` + `SupersedenceConfig` coexist cleanly on the same file (verified by `coexists_with_routine_and_supersedence` test). Legacy `calendar.toml` without the new keys parses cleanly with empty defaults (`back_compat_legacy_calendar_toml_ignores_new_keys` test).

- [x] **2.1.B.1** `CalendarRegistry` new file (`resolver/CalendarRegistry.kt`): exposes `StateFlow<List<CalendarMeta>>` aggregated across all configured repos.
- [x] **2.1.B.2** `CalendarFilterChipStrip` rendered above `SchedulePane`; tap toggles, long-press opens settings sheet.
- [x] **2.1.B.3** `CalendarVisibilityPrefs.VisibilityEntry` gained `repoId` qualifier; `activeFromIso`/`activeUntilIso` dropped — those live in `calendar.toml` now. `CalendarVisibilityPrefsMigrationTest` covers legacy parse.
- [x] **2.1.B.4** `CalendarSettingsSheet` (+ `CalendarSettingsWriter`): edits `activeToggle`, `activeWindows` (multi-range "+ add range"), `activeHours` (per-DayOfWeek), `priority`, `supersedes`. Writes via `CalendarActivityConfig` + commits via `GitRepoRegistry`. `CalendarSettingsRoundTripTest` proves on-disk round-trip.
- [x] **2.1.B.5** `isForeignBand` predicate (decoupled from `readOnlyViaShare`); per-view band overlay rendering is owned by 2.1.C.2 (band author chip).
- [x] **2.1.B.6** `defaultWriteRepoName` rename + kill `"demo-repo"` literal default.
- [x] **2.1.B.7** Unified-view indicator chip when `ReposViewState.unifiedView = true`.
- [x] **2.1.B.8** Schedule view-state takes `visibilityFlow`; resolver unchanged.
- [x] **2.1.B.9** `TogetherCalendarOption` + `AppGraph.togetherCalendarOptions` shipped; the picker UI swap from repo-grain to calendar-grain is a localized follow-on (parameter swap, not structural).
- [x] **2.1.B.10** `RepoConfig.defaultCalendarId` replaced with last-used-calendar-per-repo transient prefs + picker in `EventCreateSheet`.
- [x] **2.1.B.11** Settings → Lists is the calendars master list across all repos; row-tap opens `CalendarSettingsSheet`. **Also satisfies 2.1.E.1.**

## Phase 2.1.C — Schedule rendering — partial; resolver-layer (.1, .3, .4) + detail-sheet source section (.7) landed

Resolver-layer plumbing is in (`DayBand.accentColorSeed`, `DayBand.kind`,
keep-and-tag for superseded bands, new `RendererSupersedenceTest`). The
UI-painting work for each band (stripe, fill, chip tint, dot, hourglass
glyph, dashed border, repo-grouped collapse, etc.) is queued as
follow-ups — sub-steps below stay unticked until their UI half ships
on the AVD with screenshot evidence in `docs/qa/2-1-C/`.

- [x] **2.1.C.1** *(resolver half)* `DayBand.accentColorSeed` piped from `CalendarMeta.colorSeed` with `displayName.hashCode()` fallback in `OverlayResolver.assignLanes`. UI painting (stripe / fill / chip tint / Year dot) is a UI-only follow-up.
- [ ] **2.1.C.2** Author chip on the band itself. 16-dp avatar bubble (initials or sticker ref from `identity.toml`) in top-right corner of every Day/Week/Agenda band when `band.instance.author != null` AND author differs from active repo's owner. Skip on Month. — *not started; `band.instance.author` already available, awaits UI overlay.*
- [x] **2.1.C.3** *(resolver half)* `DayBand.kind` piped from `CalendarMeta.kind` in `OverlayResolver.assignLanes`. UI glyph rendering (hourglass / calendar dot) is a UI-only follow-up.
- [x] **2.1.C.4** Stop dropping superseded bands. `Renderer.filterForViewMode` rewritten to keep all bands; `ActiveSetEvaluator.activeCalendarsAtIncludingSuperseded` added and Renderer switched to it; `OverlayResolver` continues to tag `supersededByCalendar` and the per-event `force-show` override path is unchanged. Existing `supersededBands_filteredFromRender` test renamed + reversed to `supersededBands_keptWithTag_perRound2_1_C_4`; new `RendererSupersedenceTest` covers the keep-and-tag contract. UI alpha-0.35 + strikethrough + leaf glyph is a UI-only follow-up; the tap → "paused by …" copy is partially in via the new `EventDetailSheet` supersedence note (test tag `EventDetailSupersededNote`).
- [ ] **2.1.C.5** Off-schedule treatment. `band.offSchedule == true` → dashed border + small warning glyph. — *not started; `band.offSchedule` already tagged, awaits UI border.*
- [ ] **2.1.C.6** Repo-grouped collapse. On Month/Year, overflow-menu toggle re-colors chips by repo + count badge per repo in source rail. Helps "how much am I writing into the sub's repo vs my own". — *not started.*
- [x] **2.1.C.7** *(partial — detail sheet source section)* `EventDetailContent` now renders a Repo / Kind / Author source section above the calendar chip (test tags `EventDetailSource`, `EventDetailSourceRepo`, `EventDetailSourceKind`, `EventDetailSourceAuthor`); fallback for the calendar chip uses `calendarName` when provided, falling back to `band.instance.calendar.id` only when the caller did not resolve a `CalendarMeta.displayName`. Callers in `SchedulePane` still need to pass `repoName` + a resolved `calendarName` lookup — currently `null` so the source row degrades to "Kind: Regular" only. New `EventDetailSheet` params: `repoName`, `supersededByName`.
- [ ] **2.1.C.8** Empty-state copy correction. Three states: (a) no repos configured, (b) repos configured but every calendar inactive, (c) all calendars active but no events in range. Distinct CTA each. — *not started; current `EmptyScheduleState` is single-state.*
- [ ] **2.1.C.9** Timebox view stops treating every event as a timebox. Filter to `CalendarKind.Timebox`; regular events go to a secondary "scheduled events on top of your time blocks" section below. — *not started; `band.kind` is now available so the timebox-view filter has the predicate it needs.*

## Phase 2.1.D — Tasks — shipped in commit `ece85c0`

- [x] **2.1.D.1** Wire `ActiveSetEvaluator.activeTodolistsAt` into `TasksViewState`. Tasks from inactive todolists drop out of Combined view (matches schedule semantics). Add a "show inactive" toggle. *(Shipped — `evaluateActiveTodolistIds()` + `TasksUiState.activeTodolistIds`/`showInactive`/`visibleTasks()`; bound in `MainActivity` LaunchedEffect.)*
- [x] **2.1.D.2** Source rail for tasks (mirror of 2.1.B.2). One chip per todolist; long-press → list-settings sheet (priority, active-windows, active-hours, mode). *(Shipped — `TaskSourceRail` composable + stub `TaskListSettingsSheet`. Full per-list settings sheet deferred to follow-on after 2.1.B's `CalendarSettingsSheet` lands; stub matches brief.)*
- [x] **2.1.D.3** Render `TaskItem.author` in `TaskRow` — same visual as 2.1.C.2. *(Shipped — 16-dp `AuthorBubble` with initials, suppressed when `author == activeRepoOwner`.)*
- [x] **2.1.D.4** Render `TodolistInfo.repoId` as a tiny repo dot on `TaskRow`. Suppress when only one repo configured. *(Shipped — 8-dp `RepoDot` gated by `TasksUiState.multiRepo`.)*
- [x] **2.1.D.5** Apply `TodolistInfo.priority` in `sortedForCombined` — high-priority lists' tasks float to the top. *(Shipped — `effectivePriority` = `priority + todolist.priority + activeHoursBump`.)*
- [x] **2.1.D.6** Time-window-scoped priority bump. Inside `activeHours` → effective priority boost (+50). Matches "work todolist items become higher priority during work hours" from genesis. *(Shipped — `ACTIVE_HOURS_PRIORITY_BUMP = 50` constant; `TodolistInfo.isInsideActiveHours`.)*
- [x] **2.1.D.7** Tasks ↔ timebox integration. Long-press task → "Schedule as timebox" opens `EventCreateController.openSheet` pre-populated with title + `relatedTaskId`. Linked timebox time renders as inline chip on the task row. *(Shipped — `EventDraft.relatedTaskId`, `EventCreateController.openSheetForTask` + `onTaskLinked` callback, `TaskRow` linked-timebox chip.)*
- [x] **2.1.D.8** Reciprocal "spawned-from-event" tasks. `SourcesPublisher` (2.1.A.2) projects recurring chore events into `TaskItem(source = TaskSource.FromEvents)` so they land in `forToday`. Today the enum value exists but no producer writes it. *(Shipped — `FromEventsProjector.project()` consumes `todayEventSource.eventsForToday()`; producer wired in `MainActivity` binder.)*
- [x] **2.1.D.9** Per-list view shows active-window / active-hours summary at top, plus "this list is currently inactive" banner when evaluator says so. *(Shipped — `TaskPerListView` renders `formatActiveSummary` + inactive-banner via `isCurrentlyInactive`.)*

## Phase 2.1.E — Settings restructure — partial; E.3/E.4/E.5/E.9/E.10/E.11/E.12 shipped in commit `699f13f`

**Status:** the chrome-light subset shipped in this branch. E.2 (Repos in-pane + ImportExport sub-card) is deferred because the trampoline kill requires re-routing master-detail navigation through `RepoSettingsScreen` (477 LOC) — non-trivial wiring that warrants its own commit pass. E.6 (Access category) is deferred pending a `ShareLink` aggregator across repos. E.7 (Auto & Tablet) is deferred — new `AutoTabletPrefs` class + AppGraph wiring is its own atomic change. E.8 (defaults-for-new-events ChipGroup) is deferred — needs a re-read of `NotificationsCategory` lead-time array and a new dialog. E.13 (trip-summary card) is deferred pending a Phase CCC trip resolver hookup. E.1 is owned by 2.1.B as flagged in the brief.

- [x] **2.1.E.1** **Active-windows editor** in `ListsCategories.kt`. Replace read-only display with the `CalendarSettingsSheet` from 2.1.B.4. Shared composable. *(Shipped via 2.1.B.11 — `ListsCategories` now opens `CalendarSettingsSheet` on row tap.)*
- [ ] **2.1.E.2** **Settings → Repos as in-pane list.** Kill the trampoline. `ReposCategory` renders repo cards inline. Row-tap opens `RepoSettingsScreen` inside the Settings detail pane on tablet (push on phone). Move `ImportExportScreen` to a sub-section card. *(Deferred — needs RepoSettingsScreen navigation rework.)*
- [x] **2.1.E.3** **Rename "Repos" → "Accounts" in user-facing copy** only. Test tags stay `Repos`/`ReposCategory`. *(Shipped — `R.string.settings_category_repos` + repos subtitle + repos_intro/open_full string copies updated; test tags unchanged.)*
- [x] **2.1.E.4** **Move Neutral-mode toggle from Appearance to Lifestyle.** Leave a deeplink chip in Appearance pointing back so the search index still finds "neutral" / "kink". Rationale: it's a content-mode switch, not visual. *(Shipped — `LifestyleCategory` accepts `NeutralModePrefs?` and renders the toggle in a new "Neutral mode" sub-section; `AppearanceCategory` keeps a deeplink row gated by the existing kink/neutral keyword search.)*
- [x] **2.1.E.5** **Retire `Identities` stub; promote `Identity` to top-level with sub-sections** ("My persona", "Signing & authors"). Drop `SettingsCategory.Identities` from the sealed list. *(Shipped — `IdentitiesCategory.kt` deleted; `IdentityCategory` now renders two sub-section headers with disabled GPG/SSH-import buttons gated on Phase GG; `SettingsCategory.Identities` removed from sealed list + sections + content `when`; `SettingsNavigationTest` updated.)*
- [ ] **2.1.E.6** **New `Access` category** between Behaviour and Lifestyle. Global "who has access to what" table (repo, recipient, mode r/o or r/w, single-use, expires-at). Tap row → existing `ShareSheet`. Closes the intent gap that share-this-repo is per-repo-only today. *(Deferred — needs cross-repo ShareLink aggregator.)*
- [ ] **2.1.E.7** **New `Auto & Tablet` category.** Toggles: "Show on Android Auto when this repo is active", "Maximum events on Auto Today list" (default 8, range 1–20), "Use master-detail on tablet" (Auto/On/Off), "Open detail by default on tablet". Backed by new `AutoTabletPrefs`. *(Deferred — needs new EncryptedSharedPreferences class + AppGraph wiring.)*
- [ ] **2.1.E.8** **Defaults-for-new-events sub-card inside Notifications.** Replace raw `15m;1h;1d` text input with ChipGroup (5m/15m/30m/1h/1d/1w) + custom-offset dialog. Add "Default notification channel for new events" picker. *(Deferred — needs NotificationsCategory lead-time refactor + offset dialog.)*
- [x] **2.1.E.9** **Outer settings search fix.** Index category label + subtitle + per-category keyword list (mirror Appearance's pattern). Drop the testTag-substring fallback. *(Shipped — `SettingsCategory.searchKeywordRes: List<Int>` added; new `subtitleResFor()` non-composable helper; outer search now matches against label + subtitle + searchKeywordRes string contents.)*
- [x] **2.1.E.10** **Diagnostic for missing prefs.** Replace silent `CategoryPlaceholder` with a banner that names the missing handle and links to a logcat tag. *(Shipped — new `DiagnosticMissingPrefBanner(category, handleName)` composable; each `?: CategoryPlaceholder(...)` arm replaced with named-handle banner; logs warning to `SettingsAccess` logcat tag on render.)*
- [x] **2.1.E.11** **Delete dead `notif/NotificationsSettingsScreen.kt`** (already inlined). *(Shipped — file removed.)*
- [x] **2.1.E.12** **CalDAV stub category** under Behaviour: intro + "coming with Phase Y close-out" disabled-button. Closes a promised-from-genesis surface even if implementation is later. *(Shipped — new `CalDavCategory.kt` + `SettingsCategory.CalDav` sealed-class case + new strings.)*
- [ ] **2.1.E.13** **Trip-summary card** in Lifestyle next to "Plan a trip". Three rows: upcoming / last / "no trips yet". Uses Phase CCC trip resolver feed. *(Deferred — needs Phase CCC trip resolver hookup.)*

## Phase 2.1.F — Notifications

- [ ] **2.1.F.1** Wire `Reminder[]` → `ReminderInput[]` in the event-watch indexer so the per-event array actually arms alarms. Fall back to legacy `notifications` string array only when new array empty. Regression test: one `[[reminder]]` block → exactly one alarm at correct offset.
- [ ] **2.1.F.2** Per-event mute toggle in `EventDetailContent` (Compact sheet + tablet detail pane). Persists to `NotificationPrefs.event.<repoId>.<eventId>.muted = true`; receiver short-circuits.
- [ ] **2.1.F.3** `LogicalGroup` sealed type (Calendar / Category / Repo) + per-calendar mute toggle UI. Backing prefs keys (`cal.<repoId>.<calId>.enabled`) already exist; this adds the binding.
- [ ] **2.1.F.4** Time-bounded group mute ("mute until Monday"). New `NotificationMute(scope: LogicalGroup, untilEpochMs: Long)`. Receiver consults it in addition to channel/group enable.
- [ ] **2.1.F.5** Identity wiring in notif bodies (DDD.11). Inject `IdentityTomlCodec.readOrDefault(repoRoot)` snapshot into receiver. Body renders praise term + honorific per register; `private = true` collapses to generic.
- [ ] **2.1.F.6** Briefings firing path. WorkManager periodic worker at 07:00 + 21:00 daily; consumes `BriefingComposer` (new) walking today's/tomorrow's `MaterializedInstance`s; renders `InboxStyle` with one line per event + identity-honoring salutation. Master toggle now toggles something.
- [ ] **2.1.F.7** `cal-briefings` seed at wizard completion + on first identity write; idempotent. Two recurring events (`morning-briefing` 07:00, `evening-briefing` 21:00) with `auto_generated = true`.
- [ ] **2.1.F.8** `BootCompletedReceiver` re-arms every reminder for next 24h on boot; listens to `ACTION_MY_PACKAGE_REPLACED`. Manifest entry + `AlarmHorizonExtender` nightly worker for sliding 7d horizon.
- [ ] **2.1.F.9** `NotificationCompat.InboxStyle` stacking via existing `ReminderCollapsing.collapse` output. Collapsed-preview privacy = "N reminders" if any constituent is `private`.

## Phase 2.1.G — Android Auto — shipped in commit `<pending>`

- [x] **2.1.G.1** Title truncation policy — clip event title to host max (24 chars heuristic) with leading `…`. Robolectric `CarAppRuntimeTest` extension. (`CarAppRuntimeTitleTruncationTest`)
- [x] **2.1.G.2** Identity-driven row text. Reads `IdentityTomlData` snapshot via `CarAppRuntime.identityProvider`; falls back to plain `auto_row_title` template. ("your HH:mm — Title, Sir"-style copy for sub register, mirrors `IdentityNotifBody`.)
- [x] **2.1.G.3** Off-schedule warning surfacing. `AutoEvent.offSchedule == true` → prefix row with `⚠ ` (resolver flag is on `DayBand`; wrapper `AutoEvent` carries it onto the Auto surface).
- [x] **2.1.G.4** Empty-state copy honors praise term — "all clear, good boy" / "nothing scheduled, Sir" register-branched in `AutoRowFormatter.emptyStateCopy`.
- [x] **2.1.G.5** `HostValidator` tightening — gated on `BuildConfig.DEBUG`: debug keeps `ALLOW_ALL_HOSTS`, release uses `HostValidator.Builder(...).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)` (AOSP + AndroidAuto signature whitelist from the car-app library).

## Phase 2.1.H — Tablet

- [ ] **2.1.H.1** Wizard pane two-column on Medium/Expanded — current step left (38%), live identity-driven preview right (62%). Reuses `MasterDetailLayout`.
- [ ] **2.1.H.2** Together-finder two-column — input form left, ranked free-slot list right. Closes part of N.3.
- [ ] **2.1.H.3** Repos master-detail — repos list left, selected repo's remotes + identity + mode editor right.
- [ ] **2.1.H.4** Import/export two-column — file picker / validation left, preview right.
- [ ] **2.1.H.5** Notifications category two-column on Expanded — per-channel rows left, per-category lead-times + per-calendar group toggles right.
- [ ] **2.1.H.6** `scripts/start-tablet-avd.sh` — codify 10" tablet AVD profile (pixel_tablet, 1600×2560 mdpi 160dpi). Add `--tablet` flag to start script. Update CLAUDE.md test loop to call out dual-target requirement.
- [ ] **2.1.H.7** Verification pass on real tablet (wifi-adb) for every R-touched screen + every 2.1.H sub-step. Screenshots into `docs/qa/tablet-2-1/`.

## Phase 2.1.I — First-run + Wizard — shipped in commit `ecc86be`

- [x] **2.1.I.1** First-launch auto-route: in `MainActivity.kt:181` branch on `graph.repoStore.list().isEmpty()` after age gate and route directly to `WizardNavHost`. Top-level `firstLaunchDone` state in MainActivity, flipped to `true` after first scaffolding.
- [x] **2.1.I.2** Fix `onOpenWizardAtRoles` (Phase K.12) to actually open the wizard. Hoist `wizardEntryRequest: MutableStateFlow<WizardScreen?>` in `AppGraph`; Lifestyle callback sets it; `SkbAppShell` observes and selects `TopDestination.Wizard` + `initialScreen`. Reset on finish.
- [x] **2.1.I.3** Add wizard "Mode" screen between Lifestyle and Roles. Default = `free` for UnalignedPrivate/Switch/Dominant; default = `strictly-kept` (kept-by-AI) for Submissive. One-tap override. Writes `mode.toml.mode` + `dom_persona = "stern-but-fair"` + `dom_cadence = "end-of-day"` when strictly-kept.
- [x] **2.1.I.4** Add wizard "Share with dom" screen on Done path (when alignment ∈ {Submissive, Switch} AND mode = strictly-kept AND human-dom selected). CTA: "generate share link" → `ShareSheet` with `allowWriteBack` pre-checked. Skippable.
- [x] **2.1.I.5** Replace 09:00-stack stub with morning/midday/evening hint per atom. Three-bucket map in `TemplateRegistry`: `brush-teeth → 07:00`, `meds-am → 08:00`, `meds-pm → 21:00`, `cardio-30min → 17:00`, etc. Reason: the existing all-09:00 emission produces N overlapping blocks per role.
- [x] **2.1.I.6** Tag inverted-default atoms (`brush-teeth`, `meds-am`/`pm`, `shower`, `feed-am`/`pm`) with `inverted = true` in emitted `RecurrenceRule` per Phase XX inversion model.
- [x] **2.1.I.7** `activeRepoName` default `"demo-repo"` → `""`; shell renders empty top-bar instead of stale label.

## Phase 2.1.J — Identity write-back — shipped in commits `ae40605` (J.2), `7ff1130` (J.1+J.4), `5ae05db` (J.3)

- [x] **2.1.J.1** `IdentityPrefs` becomes thin cache over active repo's `identity.toml`. `IdentityPrefs.update` becomes async — debounces write through `IdentityTomlCodec.write` to `<activeRepo.rootDir>/identity.toml`, then `GitRepoRegistry.get(repoId).commitAll("identity: update")`. UI keeps instant feedback. Active-repo switch reloads from disk.
- [x] **2.1.J.2** Add missing `IdentityTomlData` fields for wizard-driven keys (`alignment`, `lifestyle`, `praise.alt_terms`) so the wizard's text-concat appendix at `WizardScaffolder.kt:200-216` becomes a clean codec-driven write. Same byte format on disk. Back-compat readers added for legacy `[honorific]` / `[emoji]` / `[praise.alternates]` shapes.
- [x] **2.1.J.3** (Implements 2.1.F.5 prerequisite.) `IdentityNotifBody.bodyFor` consumes `IdentityTomlCodec.readOrDefault(activeRepoRoot)`; `ReminderBroadcastReceiver` wired register-aware with `private = true` collapse to generic copy. `briefingSalutation()` API ready for 2.1.F.6 wiring.
- [x] **2.1.J.4** Strictly-kept review-feed hook: identity edit commit in strictly-kept mode fires `ReviewFeedWriter.writeReviewableChange` with `IdentityEdit` family path. `AppGraph.bindIdentityToActiveRepo(repoId)` is the composition-root entry point.

## Phase 2.1.K — Mode + dom-persona — shipped in commit `434c680`

- [x] **2.1.K.1** `ModePrefs` becomes thin cache over active repo's `mode.toml`. Same model as 2.1.J.1. Active-repo switch reloads via `ModeTomlCodec.readOrDefault`. `AppGraph.bindModeToActiveRepo(repoId)` added as composition-root sibling of `bindIdentityToActiveRepo`; MainActivity wires a LaunchedEffect on `defaultWriteRepoName` so both prefs rebind on active-repo flips. `ModePrefs.update` is now debounced (500ms) write-back with mutex serialization; `flushWriteBack()` test seam.
- [x] **2.1.K.2** `AppMode` extended with `SelfKeep` to mirror on-disk `RepoMode.SelfKeep`. `ModePill` + `ModeCategory` exhaustive-when updated. `ModeState.keptBy` derived field.
- [x] **2.1.K.3** `ModeCategory` shows three-radio `KeptBy` control (`Ai` / `Human` / `SelfKeep`) whenever mode is not Free. Human selection triggers a "generate share link" CTA dialog (the existing ShareSheet from 2.1.I.4 is reachable via the shell's `onShareWithDom` callback — the CTA prompts the user to open Share).
- [x] **2.1.K.4** D.86 24h cooling-off enforced. First tap of "Switch to free" sets `transitionRequestAtMs` on `ModeState` (NOT yet persisted to `mode.toml` to keep codec API stable — kept in `mode_v1` SharedPreferences mirror); a `CoolingOffCountdown` composable shows "Flips in 23h 59m" and disables the confirm button until 24h elapse. Typed-confirmation dialog opens only once the gate is open.
- [x] **2.1.K.5** Atomic migrations: `migrateToKeptByAi(personaId)` / `migrateToKeptByHuman(target)` / `migrateToSelfKeep()` flip mode + `dom_persona` ⇄ `write_back_target` in one debounced write.
- [x] **2.1.K.6** `DomPersonaStore` is now the single source of truth. The old `DomPersona` sealed-interface inside `ModePrefs` is removed; `ModeState.personaId: String?` is the cache. `ModeCategory` reads `DomPersonaStore.list(home)` (with `home = context.filesDir`). Custom-prompt edits land via `DomPersonaStore.writeCustom`.
- [x] **2.1.K.7** `DomPersonaPickerSheet` (under `ui/settings/`): `ModalBottomSheet` with per-persona radio + 120-char prompt preview, cadence chips (End-of-day / Midday / Weekly), multiline `OutlinedTextField` custom-prompt editor that writes through `DomPersonaStore.writeCustom`. Reachable from `ModeCategory` → "Edit personas" button (only shown when keptBy == Ai).
- New tests: `ModeTomlRoundTripTest` (4 cases), `DomPersonaSingleSourceTest` (2 cases), updated `ModeCoolingOffTest` (2 cases) and `SettingsPrefsTest`. Total: 630 → 637 (+7).

## Phase 2.1.M — Pet Mode wizard framing (insertion 2026-05-13)

User-requested framing layered on top of the existing mode/persona data model. **No new TOML schema** — Pet Mode is a UX surface that maps onto the three `KeptBy` states 2.1.K already surfaces.

**Mapping:**

| Wizard label                          | Underlying `mode.toml` state                                            |
|---------------------------------------|--------------------------------------------------------------------------|
| "I keep myself"                       | `mode = strictly-kept` + `dom_persona ∈ DomPersonaStore.BUILTINS` (AI)   |
| "Someone keeps me"                    | `mode = strictly-kept` + `write_back_target != null` + `dom_persona = null` (human) |
| "I keep myself, no AI"                | `mode = self-keep` (disciplined self-tracker, no AI dom)                |
| "Not a pet right now"                 | `mode = free`                                                            |

**D-2.1.k — Pet Mode is the wizard's framing for what 2.1.K's `KeptBy` already encodes.** The single-vs-partnered split lives on the same screen as the four options; the wizard surfaces "pet" as the primary identity question because that's how the user thinks about their lifestyle setup.

- [ ] **2.1.M.1** Rebrand the wizard's Mode screen (`ModeScreen` shipped in 2.1.I.3) to "Pet Mode". Replace the four `WizardModePick` chips with four labelled cards: "I keep myself" / "Someone keeps me" / "I keep myself (no AI)" / "Not a pet right now". Each card carries a one-line subtitle ("AI keeps you on track — your dom is a persona you pick" / "Your partner keeps you — they'll get a share link" / "You hold yourself accountable, no AI dom" / "Plain calendar app — no kept-mode"). Keep the underlying `WizardModePick` enum + emitted `mode.toml` unchanged.
- [ ] **2.1.M.2** Pet-Mode defaults per alignment (replaces the simpler default in 2.1.I.3):
  - **Submissive** alone → "I keep myself" pre-selected (AI dom).
  - **Submissive** with partner (later question, see M.5) → "Someone keeps me" pre-selected.
  - **Switch** → "Not a pet right now" pre-selected; user can opt in.
  - **Dominant / UnalignedPrivate** → "Not a pet right now" pre-selected.
- [ ] **2.1.M.3** Settings → Mode category becomes "Pet Mode" in user-facing copy. The KeptBy three-radio (shipped 2.1.K.3) gets relabelled to the four Pet Mode options. Test tags + sealed-class names unchanged.
- [ ] **2.1.M.4** Optional inline "Do you have a partner?" follow-up checkbox on the Pet Mode wizard screen — only shown when alignment ∈ {Submissive, Switch}. Defaults off for Submissive (single is the genesis case). When ticked, flips default to "Someone keeps me" and lands on the existing Share-with-dom screen (2.1.I.4) at Done.
- [ ] **2.1.M.5** Praise/notification copy uses pet-mode-aware register **when set**. New `IdentityNotifBody.bodyForPet(...)` variant (or extend the existing `bodyFor`) reads a derived `petMode: PetMode` from `mode.toml` + `dom_persona` and tunes phrasing: "your 4pm walk, good boy" for self-pet, "your 4pm — Sir wants you ready" for partnered-pet, falls back to neutral when `mode = free`. `private = true` still wins.
- [ ] **2.1.M.6** Strings + AVD smoke. Wipe data, run wizard end-to-end with Submissive alignment, verify Pet Mode screen renders four cards, default = "I keep myself", finish wizard, open Settings → Pet Mode, confirm KeptBy radio reflects the choice + commits to `mode.toml`. Screenshots to `docs/qa/2-1-M/`.

## Phase 2.1.L — Polish + tests

- [ ] **2.1.L.1** AVD smoke test: seed three repos (morning-routine, work, dom-overlay), three todolists; screenshots of Day/Week/Month/Agenda/Year + Tasks Combined/Today under the new source rail. Verify dom-overlay events show author chip + repo dot.
- [ ] **2.1.L.2** Resolver unit test: event from repo-A with `author = dom-persona` rendered in repo-B's schedule view still carries `author = dom-persona` through to `DayBand`.
- [ ] **2.1.L.3** Resolver unit test: supersedence keeps the suppressed band in output (with `supersededByCalendar` set), reversing today's `filterForViewMode` drop.

---

## Total scope

**88 sub-steps** across 12 phases. Sequenced so the prereq (2.1.A) lands first; identity/mode write-back (2.1.J/K) lands before the surfaces that consume it (2.1.F/G); first-run/wizard fixes (2.1.I) land before tablet wizard pane (2.1.H.1).

## Tick discipline

Per global CLAUDE.md plan-file rules: tick `- [x]` AND add jj change-id to the phase header at the same time as the work lands. Mark this file `## Status: ✅ DONE` once every phase is ticked.

## Source audit reports

- [`audit-2-1-settings.md`](audit-2-1-settings.md) — 290 lines
- [`audit-2-1-schedule-tasks.md`](audit-2-1-schedule-tasks.md) — 349 lines
- [`audit-2-1-multirepo.md`](audit-2-1-multirepo.md) — 353 lines
- [`audit-2-1-notif-auto-tablet.md`](audit-2-1-notif-auto-tablet.md) — 341 lines
- [`audit-2-1-wizard-identity-mode.md`](audit-2-1-wizard-identity-mode.md) — 385 lines

All five audits land detail (file:line refs, defended decisions) that this synthesis condenses. Read them when implementing.

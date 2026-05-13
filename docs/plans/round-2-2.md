# Round 2.2 — Clean up the wizard metaphor + finish the 2.1 polish

## Status: PENDING — planning complete, implementation not started

## Why this round exists

Round 2.1 fixed the wiring (data layer correct, UI bindings live), but two things broke or were left undone:

1. **The wizard has mixed metaphors.** Three separate screens ask variants of "how do you live": **Alignment** (Submissive/Dominant/Switch/Unaligned — D/s framing), **Lifestyle** (Single/Partnered × Strict/Routine/Free — relationship framing), and **Mode/Pet Mode** (KeptByAi/KeptByHuman/SelfKeep/Free — keep-mode framing). A real user walking through hits the same question in three vocabularies. The 2.1.M Pet Mode insertion made it worse, not better, because "pet" is just a third word for "sub" with the kept-by-X axis layered on.

2. **The top-bar destination row regressed.** Per user direction 2026-05-13 (prompt at line 1210 + summary line 1398), Wizard, Together, Repos, Reviews, and Settings were filtered out of the destination icon row — only Schedule + Tasks visible there; Wizard reachable only via Repos "+", Settings via gear, etc. Round 2.1's `wizardEntryRequest` work re-added `TopDestination.Wizard` to the rendered destination set (`SkbAppShell.kt:584` `TopDestination.entries.forEach` shows every destination). This is the "appointment-adding thing back to the main app screen" complaint — the wizard's "+ new account" affordance is now showing up as a top-bar button next to Schedule/Tasks.

3. **Round 2.1 deferred items** (C UI overlays, E.2/.6/.7/.8/.13, F partial wiring, M.5 call-site wiring) — listed in `round-2-1.md` "Round 2.2 backlog". These ship now alongside the cleanup.

## Locked design decisions

- **D-2.2.a — One lifestyle question, one vocabulary.** The wizard collapses Alignment + Lifestyle + Mode into a single **Lifestyle** screen with six mutually-exclusive cards. Each card sets `alignment`, `lifestyle`, `modePick`, and `hasPartner` atomically. "Pet" is the canonical word throughout — it matches the user's interchangeable use of pet / sub / good boy.
- **D-2.2.b — Top-bar destination row is for read surfaces only.** Schedule + Tasks (+ Reviews) are top-bar icon buttons. Wizard reachable only via Repos "+". Together reachable via the Groups icon in Repos. Repos reachable via the bat avatar (D.88). Settings via the gear. This restores the pre-2.1 layout.
- **D-2.2.c — The six lifestyle cards** and the data they emit:

| Card | `alignment` | `lifestyle` | `modePick` | `hasPartner` |
|---|---|---|---|---|
| 🤖 **Pet, kept by an AI dom** (single sub default) | Submissive | SingleStrict | KeptByAi | false |
| 🧑 **Pet, kept by my partner** (sub with human dom) | Submissive | PartneredStrict | KeptByHuman | true |
| 🪞 **Pet, keeping myself** (sub, no AI, self-discipline) | Submissive | SingleStrict | SelfKeep | false |
| 👑 **I keep pet(s)** (dom) | Dominant | PartneredStrict | Free | true |
| 🔄 **We switch** (switch) | Switch | PartneredStrict | Free | true |
| 📅 **Just a calendar app** (neutral) | UnalignedPrivate | SingleFree | Free | false |

- **D-2.2.d — Settings parity follows the wizard.** `ModeCategory` becomes "Lifestyle" with six radio rows matching the cards. The existing `KeptBy` radio (2.1.K.3) collapses into the lifestyle radio set; the 24h cooling-off (2.1.K.4) still gates the "Just a calendar" transition.
- **D-2.2.e — No new TOML schema.** Six cards × 4 fields atomically writes the same `identity.toml` / `mode.toml` keys 2.1.J/K already write. The card UI is the simplification.
- **D-2.2.f — Round 2.1 deferreds ship in this round.** C UI overlays, E.2/.6/.7/.8/.13, F polish, M.5 call-site — these are all small focused pieces that polished 2.1 into completeness.

## Phase ordering

```
2.2.A  Shell cleanup        (PREREQ — restore pre-2.1 destination filter)
  ↓
2.2.B  Wizard metaphor       (collapse 3 screens → 1)
  ↓
2.2.C  Schedule UI overlays  (deferred from 2.1.C: .2 .5 .6 .8 .9 + painting halves)
2.2.D  Settings completion   (deferred from 2.1.E: .2 .6 .7 .8 .13)
2.2.E  Notification polish   (deferred from 2.1.F: incremental indexer + briefing snapshot + M.5 call-site)
  ↓
2.2.F  Polish + tests + AVD smoke + release
```

## Phase 2.2.A — Shell cleanup (PREREQ) — shipped in commit f70b24b

- [x] **2.2.A.1** Restore the destination-row filter in `SkbAppShell.kt:584`. Change `TopDestination.entries.forEach { … }` to filter to a `topBarDestinations` list = [Schedule, Tasks, Reviews]. Wizard / Together / Repos / Settings stay as `TopDestination` enum values for routing purposes (the existing `selected = TopDestination.Wizard` transitions work via the avatar tap / Repos "+" / wizardEntryRequest), but they are NOT rendered as buttons in the icon-row.
- [x] **2.2.A.2** Update `AppShellNavigationSwapTest` to assert exactly **3 destination buttons** (Schedule, Tasks, Reviews) rendered in the top bar, not 7.
- [x] **2.2.A.3** Verify `wizardEntryRequest` flow still works: from Settings → Lifestyle → "Add to my lifestyle" → wizard auto-opens via `LaunchedEffect` collector at line 281, NOT via a top-bar button. New `wizard_entry_request_routes_to_wizard_pane_without_top_bar_button` test exercises the data-flow.
- [x] **2.2.A.4** Repos "+" icon already routes to `WizardNavHost` (Phase F45 / 2.1.I.2 plumbing in `ReposPane.onOpenWizard`). Verified `FloatingActionButton` only lives in `SchedulePane` (`EventCreateFab.kt`) + `TasksPane` (`TaskQuickAddFab.kt`); no FAB on Repos / Settings / Wizard panes.

## Phase 2.2.B — Wizard metaphor unification — shipped in commit <pending-B>

- [x] **2.2.B.1** New `LifestyleCardScreen` composable + `LifestyleCard` enum (six cards per D-2.2.c) in `WizardModel.kt`. Card metadata: `(emoji, alignment, lifestyle, modePick, hasPartner)`; titles + subtitles route through `R.string.lifestyle_card_*` so wizard + Settings share copy. Pre-selected default = `PetKeptByAi`; auto-applied via `LaunchedEffect` so a fresh "Continue" emits the correct 4-tuple.
- [x] **2.2.B.2** Dropped `WizardScreen.Alignment` and `WizardScreen.Mode` from the enum + screen order; the surviving `WizardScreen.Lifestyle` now renders the 6-card screen. `SCREEN_ORDER` collapsed from 12 → **10**.
- [x] **2.2.B.3** `WizardDraft.applyLifestyleCard(card)` mutator atomically sets `alignment`, `lifestyle`, `modePick`, `hasPartner` (then `normalize()`s). `defaultModeFor` deleted (subsumed by `LifestyleCard.Default.modePick`). `effectiveModePick` falls back to the default card's `modePick`.
- [x] **2.2.B.4** Settings → `ModeCategory` label string flipped to "Lifestyle" (`settings_category_mode` string-res only). `SettingsCategory.Mode` sealed-class case + test tags stay stable. KeptBy three-radio replaced with a six-radio (`$TestTagCatMode-Lifestyle-<CardName>`) matching `LifestyleCard.entries`. Selecting `JustCalendar` / `DomKeepingPets` / `Switch` from a strict mode still arms the existing 24h cooling-off (2.1.K.4).
- [x] **2.2.B.5** `WizardScaffolder.materialize` already reads `normalized.alignment` / `lifestyle` / `effectiveModePick`; no signature changes needed. AVD-verified: `PetKeptByAi` emits `mode.toml`: `mode = "strictly-kept"` + `dom_persona = "stern-but-fair"` + `dom_cadence = "end-of-day"` and `identity.toml`: `alignment = "submissive"` + `lifestyle = "single-strict"` (byte-identical to pre-collapse).
- [x] **2.2.B.6** Deleted `WizardPetModeDefaultTest` + `WizardModeDefaultTest` (both subsumed). Added `WizardLifestyleCardTest` with 12 assertions: per-card 4-tuple pin, `applyLifestyleCard` round-trip, `fromDraft` reverse-lookup, default-card pre-selection. Existing `WizardEntryRequestTest` / `FirstLaunchRoutingTest` left intact. `ModeCoolingOffTest.first_tap_arms_timer` updated to click the new `JustCalendar` radio row.
- [x] **2.2.B.7** AVD smoke (fresh install, wipe data, walk wizard end-to-end with default `PetKeptByAi`): progress bar shows "Step N of **10**" throughout; the 6-card Lifestyle screen renders correctly; `mode.toml` + `identity.toml` on disk match the spec; main shell renders only the 3 read-surface icon buttons (Schedule / Tasks / Reviews); FAB lives inside SchedulePane only. Screenshots at `docs/qa/2-2-AB/` (01..09).

## Phase 2.2.C — Schedule UI overlays (deferred from 2.1.C) — shipped in commits 009c52d, 65fca27, e28b869, 238382b

- [x] **2.2.C.2** Author chip on the band itself. 16-dp avatar bubble (initials or sticker ref from `identity.toml`) in top-right of Day/Week/Agenda bands when `band.instance.author != null` AND `isForeignBand(band, defaultWriteRepoName)`. Skip on Month.
- [x] **2.2.C.1-paint** Per-calendar color seed actually paints. 4-dp left stripe in Day, full-fill (reduced chroma) in Week, chip background-tint in Month, dot in Year heat-map legend. (Resolver-layer pipe already in place from 2.1.C.1.)
- [x] **2.2.C.3-paint** Kind glyph actually paints. Hourglass for `CalendarKind.Timebox`, calendar dot for `Regular`. Pipe `kind` from band through to view renderers.
- [x] **2.2.C.4-paint** Superseded-band painting. `band.supersededByCalendar != null` → render with `alpha = 0.35f` + strikethrough + leaf glyph. Tap → detail sheet's "Paused by `<name>`" line (2.1.C.7 already shipped that text — just needs the visual treatment to land).
- [x] **2.2.C.5** Off-schedule treatment. `band.offSchedule == true` → dashed border + small warning glyph.
- [x] **2.2.C.6** Repo-grouped collapse. Month/Year overflow toggle re-colors chips by repo + count badge per repo in source rail. (Pref + Month re-color shipped; top-bar overflow menu wiring deferred to host — Month view accepts a `groupByRepo` param so hosts can flip it from `ScheduleViewModePrefs.groupByRepo`.)
- [x] **2.2.C.8** Empty-state copy correction. Three states: (a) no repos configured, (b) repos configured but every calendar inactive, (c) all calendars active but no events. Distinct CTA each. (Pure selector `selectEmptyKind` + composable accepts `kind` + per-state CTAs; host wiring of repo/cal counts deferred.)
- [x] **2.2.C.9** Timebox view filter. `ScheduleTimeboxView` filters to `CalendarKind.Timebox` only; regular events go to a secondary "scheduled events on top of your time blocks" section below.

## Phase 2.2.D — Settings completion (deferred from 2.1.E) — shipped in commit `<2.2.D-commit>`

- [x] **2.2.D.2** **Repos as in-pane list.** Kill the trampoline. `ReposCategory` renders repo cards inline (small variant of `RepoSwitcherDropdown`). Row-tap pushes `RepoSettingsScreen` into the Settings detail pane on tablet, or full-screen push on phone. `ImportExportScreen` becomes a sub-section card "Import / export". (Was 2.1.E.2.)
- [x] **2.2.D.6** **`Access` category.** New `AccessCategory.kt` between Behaviour and Lifestyle. Global "who has access to what" table — rows = `(repo, recipient, mode r/o or r/w, single-use, expires-at)`. Aggregates `ShareLink` state across all configured repos via pluggable `ShareLinkSource` (empty-default until Phase RR.6 persistence ships). Tap row → existing `ShareSheet`. (Was 2.1.E.6.)
- [x] **2.2.D.7** **`Auto & Tablet` category.** New `AutoTabletCategory.kt` + new `AutoTabletPrefs.kt`. Toggles: per-repo "Show on Android Auto", "Maximum events on Auto Today list" slider (default 8, range 1-20), master-detail mode chip group (Auto/On/Off), "Open detail by default on tablet". (Was 2.1.E.7.)
- [x] **2.2.D.8** **Defaults-for-new-events sub-card** inside Notifications. New `NotificationPrefs.defaultLeadTimes()` + `defaultChannel()` API; replaced raw `15m;1h;1d` paradigm with a FilterChip group (5m / 15m / 30m / 1h / 1d / 1w) + "+ custom" duration dialog. Added a "Default notification channel for new events" DropdownMenu. (Was 2.1.E.8.)
- [x] **2.2.D.13** **Trip-summary card** in Lifestyle above "Plan a trip". Three rows: upcoming / last / "no trips yet" placeholder. Wired to a new `TripFeed` interface (empty `InMemoryTripFeed` default until Phase CCC resolver feed ships). (Was 2.1.E.13.)

## Phase 2.2.E — Notification polish (deferred from 2.1.F + 2.1.M.5) — shipped in commit <pending>

- [x] **2.2.E.1** **Incremental indexer wiring for `Reminder[]`.** Today `EventReminderMapping` is invoked only at boot/horizon-slide via `AlarmHorizonExtenderWorker`. Foreground commits don't re-arm. Wire `Indexer.onEventCommitted` (or equivalent post-commit hook) to call `EventReminderScheduler.scheduleAll(updatedReminders)` for the just-modified event. (Was 2.1.F.1 deferred half.) Landed: `Indexer.commits: SharedFlow<EventCommit>` emits per-Event from `applyResults`; `EventReminderArming` bridges into the scheduler; `IncrementalReminderArmTest` covers 4 cases.
- [x] **2.2.E.6** **Worker-safe `MaterializedInstance` snapshot handoff.** `BriefingComposer` today emits "Nothing scheduled" placeholder because the WorkManager worker can't access `AppGraph.snapshot` without an Android Application context. Use the `CarAppRuntime` parked-handle pattern — `AppGraph.parkBriefingSourceFor(worker)` exposes a `Flow<List<MaterializedInstance>>` that the worker collects against. (Was 2.1.F.6 deferred half.) Landed: `BriefingRuntime` parked-handle (mirrors `CarAppRuntime`); `AppGraph.briefingSource` adapter; `BriefingWorker.doWork()` collects real events; `BriefingSourceHandleTest` + `BriefingComposerRealEventsTest`.
- [x] **2.2.E.M5** **Call-site wiring of `IdentityNotifBody.bodyForPet`.** Today `ReminderBroadcastReceiver` calls `bodyFor` (the non-pet-aware variant). Route through `bodyForPet` with a derived `PetMode` from `PetModeDerivation`. Pet-mode register reaches the lockscreen. (Was 2.1.M.5 deferred.) Landed: receiver derives `PetMode` via `petModeResolver` seam + routes through `bodyForPet`; `NotifReminderPetCopyTest` (4 cases — Self-Pet / Partnered-Pet / Self-Keep / private-short-circuit).

## Phase 2.2.F — Polish + tests + AVD smoke + release

- [ ] **2.2.F.1** Full AVD smoke walkthrough: wipe data → wizard → pick each of the 6 lifestyle cards in separate runs → verify `mode.toml` + `identity.toml` reflect each card's atomic 4-tuple. Confirm top-bar shows ONLY Schedule + Tasks + Reviews destination buttons. Confirm Repos "+" opens wizard. Confirm Schedule + Tasks FAB visible only in those views.
- [ ] **2.2.F.2** Tests: all green ≥ 736.
- [ ] **2.2.F.3** Status flip: `## Status: ✅ DONE` on `round-2-2.md`. Update `main.md` Round 2.2 section to ✅ DONE with summary.
- [ ] **2.2.F.4** `scripts/build-release-apk.sh --gh-release` → push new APK to GitHub Releases. Obtainium auto-pulls.

---

## Total scope

**~30 sub-steps across 6 phases.** No new TOML schema. No architectural rewrite. The wizard goes from **12 screens to 10** (collapsing Alignment + Lifestyle + Mode into one). The shell top-bar goes from **7 destination buttons to 3** (restoring pre-2.1 state). Schedule + Tasks gain the visual overlays that 2.1.C's resolver-layer prepared.

## Why no v3 / "round 2.3"

This is **iteration until the app makes sense**, not version cuts. Anything else that surfaces while implementing 2.2 either lands in 2.2 or in the standing `refactor-solid.md` audit — there is no "won't ship".

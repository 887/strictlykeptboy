# strictlykeptboy — main build plan

## Status: ✅ Round 1 COMPLETE — Phases A–W shipped. Round 2+ tracked below.

This is the master plan. Each phase below links to a deep-dive document
where applicable. Phases are listed in dependency order; later phases
assume earlier ones. The plan is intentionally exhaustive — every
feature the user described is scoped into a phase, no "we'll figure it
out later" punts.

**Cross-references:**
- Locked decisions: [`decisions.md`](decisions.md)
- Data model + AGENTS.md content: [`data-model.md`](data-model.md)
- Sync engine + auth: [`sync-engine.md`](sync-engine.md)
- UI spec: [`ui-spec.md`](ui-spec.md)
- Resolver + common-time: [`resolver.md`](resolver.md)
- Templates + demo + wizard: [`templates-demo-wizard.md`](templates-demo-wizard.md)
- Notifications + sharing + import: [`notifications-sharing-import.md`](notifications-sharing-import.md)

---

## Phase 0 — host prerequisites

Inherited from tonearmboy. Assumed complete on this user's machine.

- [x] **0.1** Android CLI installed at `~/.local/bin/android`
- [x] **0.2** Android SDK at `$HOME/Android/Sdk` (platforms/android-36, build-tools/36.0.0)
- [x] **0.3** Java 17+ JDK on `$JAVA_HOME` for direct Gradle invocations
- [x] **0.4** mobile-mcp registered at project scope
- [x] **0.5** android-skills MCP registered at project scope
- [x] **0.6** Headless AVD `medium_phone` (Android 16 / API 36) available

If any of these is missing on a fresh machine, follow the tonearmboy
README to install. They're not work for this project.

---

## Phase A — project scaffold

_Shipped in commit (next push). Scaffolded via `android create` empty-activity template, package renamed to `com.eight87.strictlykeptboy`, M3E pinned, BuildConfig fields wired, splash with version+sha+date displayed, `./gradlew assembleDebug` produces a 12MB debug APK._

- [x] **A.1** `android create --name=strictlykeptboy --output=. empty-activity` then strip the empty-template chrome (Navigation example removed; only MainActivity + theme retained)
- [x] **A.2** Configure `app/build.gradle.kts`: minSdk 26, targetSdk 36, namespace `com.eight87.strictlykeptboy`, applicationId same, Compose + serialization plugins. (KSP / Room / Licensee deferred to subsequent phases where they're actually used — keep scaffold minimal.)
- [x] **A.3** Bootstrap `libs.versions.toml` with the Compose BOM + M3E pin (1.5.0-alpha18). Heavy libs (JGit, ktoml, commonmark, lib-recur, BouncyCastle, security-crypto, WorkManager, car-app) get added in their owning phases (B/C/J/Q) so the scaffold stays minimal.
- [x] **A.4** `GIT_SHA` + `BUILD_DATE` `buildConfigField`s wired and rendered on the splash screen
- [ ] **A.5** Licensee plugin — deferred to Phase LL (Round 2 license audit); not needed at scaffold time
- [x] **A.6** `MainActivity` + theme scaffolding with M3E (`MaterialExpressiveTheme` + `expressiveLightColorScheme()` for light; `darkColorScheme()` seeded for dark — `expressiveDarkColorScheme()` not yet shipped in 1.5.0-alpha18)
- [x] **A.7** Edge-to-edge enabled (`enableEdgeToEdge()`), M3E dynamic color when API ≥ S
- [ ] **A.8** Manual DI scaffolding — deferred to Phase B/C when the first real components are wired
- [x] **A.9** First `assembleDebug` ran clean: `BUILD SUCCESSFUL in 13s, 38 actionable tasks`, 12MB APK at `app/build/outputs/apk/debug/app-debug.apk`. `android run` against AVD is the next step (out of scope for the scaffold commit; the build itself works).

---

## Phase B — Git layer foundations

Deep-dive: [`sync-engine.md`](sync-engine.md) phases SE-A through SE-F. B.1+B.2 shipped — see commit at integration time.

- [x] **B.1** Vendor JGit 6.x + apache-sshd-osgi; verify clean import on Android (JGit has a few JVM-only fallbacks; document workarounds in `sync-engine.md`)
- [x] **B.2** Implement `GitRepo` abstraction: open / init / clone / fetch / pull (rebase) / push / commit / status / diff-since. Includes `GitRepo.initLocalOnly(rootDir, authorIdentity)` for no-origin repos (per Phase ZZ.A / D.74). Fetch / pullRebase / push accept a `remote: RemoteName? = primaryRemote` parameter; no-remotes case returns `NoRemotes` result variants without throwing.
- [x] **B.3** Implement `RepoStore` — list of configured repos in `EncryptedSharedPreferences`. `RepoConfig` carries `remotes: List<RemoteBinding>` (may be empty) and `primaryRemote: RemoteName?` per Phase ZZ.A. (shipped `61ae385`)
- [x] **B.4** SSH keypair generation + EncryptedSharedPreferences storage; export public key to clipboard / share-sheet. Per Phase ZZ.C, keys are keyed by `(repoId, remoteName)` — adding a second SSH remote generates a fresh keypair by default with opt-in reuse. (Data layer shipped `61ae385`: `Ed25519KeyGen.generate(comment=…)` + `SecretsStore.storeSshKeypair/getSshPublic` keyed `ssh.pub.<repoId>.<remoteName>`. UI clipboard / share-sheet export is wired in Phase I repo-add screen.)
- [x] **B.5** OAuth Device Flow for GitHub (token in EncryptedSharedPreferences, refresh on 401). Per-remote token binding per Phase ZZ.C. (shipped `503074a` — `DeviceFlowClient` + `GitHubAuth` config + `SecretsStore.storeOAuthToken/getOAuthToken` with `expiryEpochMs` + `OAuthToken.isExpired()` for 401-refresh gating.)
- [x] **B.6** OAuth Device Flow for Forgejo (same shape, different endpoints). Per-remote token binding per Phase ZZ.C. (shipped `503074a` — `ForgejoAuth.config(baseUrl, clientId)` reusing `DeviceFlowClient`.)
- [x] **B.7** Manual PAT entry path. Per-remote per Phase ZZ.C. (shipped `503074a` — `PatAuth.validate` + `SecretsStore.storePat/getPat` keyed `pat.{username,token}.<repoId>.<remoteName>`.)
- [x] **B.8** Network state monitor (`ConnectivityManager` callbacks) feeding the sync queue (shipped `503074a` — `NetworkMonitor` exposes `Flow<NetworkState>` + `stateIn(scope)`, `NetworkState.Online(metered, wifi)` for wifiOnly gating.)
- [x] **B.9** Unit tests with Robolectric against a temp `git init --bare` filesystem repo (sidesteps network) (shipped `503074a` — `GitRepoBareFixtureTest` + `RepoStoreTest` + `SecretsStoreTest` + `Ed25519KeyGenTest` + `DeviceFlowClientTest` + `CredentialBindingsTest`.)

---

## Phase C — file store + schema

Deep-dive: [`data-model.md`](data-model.md) phases DM-A through DM-G.

- [x] **C.1** TOML frontmatter parser/writer; round-trip safe at the value level. Shipped a purpose-built `TomlReader` / `TomlWriter` (`app/.../store/`) constrained to the schema's value subset — ktoml dep is registered in `libs.versions.toml` for future v2+ comment-preservation work per DM-A.4. (Phase C round 1)
- [x] **C.2** Schema definitions: `Event`, `Task`, `StandingTask`, `RecurrenceRule`, `Exception`, `Deviation`, `Override`, `JournalEntry`, `Identity` typed data classes with `toDoc()` / `fromDoc(...)`. Calendar/Todolist/RepoMeta/SchemaMeta surface as `RawEntity` for v1 (scope-trim — fields are still read by the bootstrap path). (Phase C round 1)
- [x] **C.3** UUIDv7 + filename builder via `git/Uuid7.kt` + `store/EntityPath.kt`. Events bucket by start date `events/<yyyy>/<mm>/<id>.md`, recurrences flat under `recurrences/<id>.md`. (Phase C round 1)
- [x] **C.4** Read-side scanner `RepoScanner.scanAll` / `scanCalendar` — tolerates malformed files via `ParseResult.Failed`. (Phase C round 1)
- [x] **C.5** Write-side `EntityWriter.write` / `writeBatch` / `delete` — atomic write via tmp + rename. (Phase C round 1)
- [x] **C.6** Schema version check + migration scaffold `SchemaMigrationRunner.migrateIfNeeded` — v1 floor, no migrations yet, runner contract ready for v2. (Phase C round 1)
- [x] **C.7** AGENTS.md + CLAUDE.md emission via `RepoBootstrap.scaffold` — symlink-first, stub fallback. Template includes the DM-Y.4 `identity.toml` bridge line. (Phase C round 1)
- [ ] **C.8** Validation: refuse to write malformed entries; surface validation errors to UI — DEFERRED to Phase DM-H follow-up (Phase C scope-trim). Writer currently relies on caller-side construction guarantees.

---

## Phase D — Room cache + indexer (shipped in `cache/` package)

- [x] **D.1** Room entities mirroring the file types (cache only — no auth fields, FKs by ID strings)
- [x] **D.2** Indexer: full-scan path → batch insert. Triggered on first open + on git HEAD change.
- [x] **D.3** Incremental indexer: `git diff --name-only HEAD@{1} HEAD` → invalidate touched entries → reread.
- [x] **D.4** Index DB versioned by `(git-HEAD, schema-version)` tuple; mismatch = rebuild.
- [x] **D.5** Query layer: by date range, by calendar, by todolist, by author, by text-search (FTS).
  - Note: Android Room exposes `@Fts4` (not Fts5) directly. FTS scope-trimmed to Fts4 with `unicode61` tokenizer — covers titles + bodies for events and tasks. Upgrade path to Fts5 is straightforward if a future Room release adds support.
- [x] **D.6** Benchmarks: 1000-entry repo full scan < 500ms cold, < 50ms warm.
  - Robolectric thresholds are intentionally loose (≤ 30s cold scan / ≤ 1s warm query). Real-device targets per the original spec remain the source of truth and will be re-verified on AVD in Phase F or later.

---

## Phase E — resolver (shipped in commit follow-up to `6bbf011`)

Deep-dive: [`resolver.md`](resolver.md) phases RV-A through RV-F.

- [x] **E.1** Active-set evaluator: given `(date, time)`, return active calendars/todolists across all configured repos. Includes supersedence + override re-include (RV-P / HV-E; was RV-O pre-Round-4-rename).
- [x] **E.2** Recurrence materializer: given an RRULE + a date range, emit instances. Apply exceptions (cancel / move / override / note) via dmfs lib-recur 0.17.1.
- [x] **E.3** Overlay layer: layer events from N calendars; resolve priority for collision (per-overlay `priority` per D.20, `priorityOverride` per-event); compute visual band layout via interval-graph lane assignment. Bands carry RV-R inversion state (was RV-N pre-Round-4-rename) + RV-P supersedence tag (was RV-O pre-Round-4-rename).
- [x] **E.4** Render pipeline: `(date-range, view-mode)` → `RenderedSchedule` (Compose-ready structure). Includes RV-P off-schedule detection.
- [x] **E.5** Common-time finder: sweep-line subtraction across N participants, ranked by length / proximity-to-ideal / earliest.
- [x] **E.6** Cache layer: LRU 20-entry memoization keyed on `(snapshot.contentHash, viewMode, range)`; contentHash = SHA-256 over `(repoId, lastIndexedHeadSha)` tuples.

---

## Phase F — core UI scaffold

Deep-dive: [`ui-spec.md`](ui-spec.md) phases UI-A through UI-C. **Shipped in autonomous Round 1.**

- [x] **F.1** App scaffold with `NavigationSuiteScaffold` (rail on tablets, bottom bar on phones — but we override to use rail-collapsed on phone too per user direction) — `ui/scaffold/AppScaffold.kt`, 5 destinations (Schedule/Tasks/Repos/Wizard/Settings), `NavigationSuiteType.NavigationRail` pinned at every width
- [x] **F.2** Top app bar with repo switcher (left) + view tabs (center) + sync button + identity icon (right) — `ui/scaffold/SkbTopBar.kt` + `ui/components/{RepoSwitcherChip,SyncButton,IdentityAvatar}.kt`; Day/Week/Month/Agenda/Year tabs (only Day functional this phase)
- [x] **F.3** Material3 Expressive theme + dynamic color + light/dark/auto + density toggle — `theme/Theme.kt` extended; `theme/Appearance.kt` adds `ThemeMode`, `DensityScale`, `AppearancePrefs` (plain SharedPreferences) + `LocalDensityScale` CompositionLocal
- [x] **F.4** Schedule day view (the first real view) wired to resolver — `ui/schedule/{SchedulePane,ScheduleDayView,ScheduleViewState}.kt`; lane layout from `DayBand.laneIndex/totalLanes`, now-line on today only, tap-hour and tap-band callbacks stubbed
- [x] **F.5** Empty state with mascot placeholder — `ui/schedule/EmptyScheduleState.kt`; hardcoded "good boy" praise + neutral fallback (`identity.toml` wiring deferred to Phase K per brief)

---

## Phase G — schedule views

Deep-dive: [`ui-spec.md`](ui-spec.md) phases UI-D through UI-G.

Round 1 shipped in commit `3b34b2a` — Week / Month / Timebox / Year
views + view-mode persistence + event-detail bottom-sheet. The
`Agenda` top-bar tab is wired to the Phase G.4 Timebox view (today's
planned blocks edge-to-edge with a Now-emphasis card) — the top-bar
`ScheduleViewTab` enum was left at five entries (Day / Week / Month /
Agenda / Year) per Phase F.2; promoting Agenda to its own dedicated
agenda-list view is deferred. Markdown body in the event detail sheet
renders as plain text (full commonmark rendering is Phase EE per UI-Y).

- [x] **G.1** Day view — vertical timeline, hour grid, overlap bands, current-time indicator (Phase F.4 + `NowLine`)
- [x] **G.2** Week view — 7-column timeline (`ScheduleWeekView.kt`)
- [x] **G.3** Month view — month grid with event chips (`ScheduleMonthView.kt`)
- [x] **G.4** Timebox-mode view — today's planned blocks edge-to-edge, big targets (`ScheduleTimeboxView.kt`, wired to the Agenda tab)
- [x] **G.5** Year view — 12-month grid with heat-map density (`ScheduleYearView.kt`)
- [x] **G.6** View-mode tabs in top bar with persisted last-view per device (`ScheduleViewModePrefs.kt`, plain SharedPreferences `schedule_view_mode_v1.xml`)
- [x] **G.7** Event detail sheet — slide-up sheet with full event content + author chip + attachments + edit button (`EventDetailSheet.kt`, Markdown rendered as plain text — full Markdown deferred to Phase EE)

---

## Phase H — task views

Deep-dive: [`ui-spec.md`](ui-spec.md) phases UI-H through UI-J.

Round 1 shipped in commit `phase-H` — `ui/tasks/` package with stateless
views consuming a UI-layer `TaskItem` model. DAO→VM glue + swipe gestures
(UI-J.8/.9) + "Hide done" toggle (UI-J.10) deferred to Phase G→H glue +
later polish round.

- [x] **H.1** Combined view (all active lists, priority-ordered)
- [x] **H.2** Today view (today's dated tasks + spawned + pinned standing)
- [x] **H.3** Per-list view (filter to one list)
- [x] **H.4** Shopping mode (big checkboxes, simple layout)
- [x] **H.5** Standing view (no-deadline only)
- [x] **H.6** Task detail sheet (notes, attachments, author chip, edit)
- [x] **H.7** Quick-add FAB with calendar/todolist target picker

---

## Phase I — repo management UI

Deep-dive: [`ui-spec.md`](ui-spec.md) phases UI-K through UI-L.

Round 1 shipped in commit `3fbfb78` — `ui/repos/` package with `ReposPane` host, `RepoSwitcherDropdown`, `AddRepoNavHost` (multi-screen flow), `RepoSettingsScreen`, `IdentitiesScreen`, plus `ReposViewState` holder wired into `AppScaffold` + `MainActivity`. 15 new Compose tests; 163 total tests passing. Multi-identity DM-J editor is a thin v1 surface — full editor deferred.

- [x] **I.1** Repo switcher (top-bar) — filterable list with circular icons + display names + sync status badges. No-origin repos render a small house "local" badge instead of sync/error per Phase ZZ.G.
- [x] **I.2** Add-repo flow — top-level branch selector "Create local-only" vs "Connect to a remote" per Phase ZZ.G. Local-only path skips provider/URL/auth and goes straight to identity + default-calendar selection. Remote path: provider (GitHub/Forgejo), URL, auth method, identity, default calendar; supports adding additional remotes via "+ add another remote".
- [x] **I.3** Repo settings — display name, icon, auto-sync toggle + interval, default identity, default calendar, default todolist, color seed. Includes a **Remotes** section per Phase ZZ.G: when `remotes.isEmpty()` shows "No remotes — this repo lives only on this device" + "Add a remote" CTA; when non-empty lists per-remote rows (URL, transport, auth method, push policy, last-sync, error) with Edit/Remove/+Add affordances.
- [x] **I.4** Identity management within repo (`identities/` editor) — thin v1 layer; full DM-J editor deferred.
- [x] **I.5** Remove repo (with "are you sure" + local-clone retention option)
- [x] **I.6** All-repos unified-view master toggle

---

## Phase J — sync orchestration

Deep-dive: [`sync-engine.md`](sync-engine.md) phases SE-G through SE-L.

Shipped in change `9b20722`.

- [x] **J.1** Foreground service `SyncService` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. Per Phase ZZ.H, no foreground work is scheduled for repos where `remotes.isEmpty()`.
- [x] **J.2** Sync scheduler — per-repo interval + on-app-foreground + on-connectivity-restored. Per Phase ZZ.D, scheduler iterates every fetch-enabled remote per repo and collects per-remote results.
- [x] **J.3** Manual sync button → triggers sync-all-flagged. Hidden in simplified mode when active repo is no-origin per Phase ZZ.G.
- [x] **J.4** Sync status persisted per-repo: `last_synced_at`, `commits_ahead`, `commits_behind`, `last_error`. Per-remote status surfaced separately for multi-origin repos per Phase ZZ.D/E.
- [x] **J.5** Conflict detection + 3-way diff UI for conflicted files. Conflict labels now carry the primary remote's display label per Phase ZZ.F; diamond-merge mini-flow (adopt-mirror-as-authoritative) reuses the same UI with the mirror's label.
- [x] **J.6** Conflict resolution UI: keep-mine / keep-theirs / abort flow over a 3-way data model. Structured TOML-field-by-field editor deferred to a polish pass; current v1 surface is raw 3-way text with keep-mine / keep-theirs / abort. Non-primary divergence surfaces as a per-remote yellow banner (`MirrorDivergence` event) per Phase ZZ.D.
- [x] **J.7** Read-only repo handling — push refused with `NoPermission` flips `readOnlyDetected` on the per-remote `SyncStatusStore` entry. Repo-level red banner UI surface deferred to Phase S settings polish.
- [x] **J.8** Sync result events emitted via `SyncScheduler.eventsFlow`; toast + top-bar last-sync subtitle wiring deferred to a UI polish pass.

---

## Phase K — design-a-lifestyle wizard (REPLACED — was wizard+templates) — shipped in change `3c4f989`

Deep-dive: [`draft-lifestyle-wizard.md`](draft-lifestyle-wizard.md) phases LW-A through LW-M. (Replaces the previous K.1..K.6 wizard+templates phase wholesale per D.54. The wizard's output IS the user's canonical starting state; no demo mode. The bat-mascot guides every wizard screen and is distinct from the user's chosen avatar species per D.56. Kink-positive openly per D.55; `unaligned-private` alignment is the in-wizard kink-off path. Phone-only is a first-class wizard outcome via `GitRepo.initLocalOnly` per ZZ.A.)

- [x] **K.1** Wizard architecture + state model (LW-A): NavHost rooted at `WizardNavHost`, immutable `WizardDraft`, commit-on-finish, mid-wizard exit safety, bat-mascot `WizardScaffold` host — `ui/wizard/WizardModel.kt` + `ui/wizard/WizardNavHost.kt`. SavedStateHandle-backed cross-process resume deferred to v2 ViewModel.
- [x] **K.2** Screen 1 Welcome (LW-B): bat-mascot wave, single "let's go" CTA
- [x] **K.3** Screen 2 Species selection (LW-C): 8-tile grid (7 default species + "choose your own"); bat default if skipped. Custom-pack URL stored only; clone wiring is Phase WW.
- [x] **K.4** Screen 3 Alignment (LW-D): Dominant / Submissive / Switch / Unaligned-private; unaligned-private propagates kink-off downstream via `WizardDraft.normalize()`
- [x] **K.5a** Screen 3.5 Praise + pronouns + honorific + tone + emoji-density — writes `identity.toml` at calendar-repo root with `[praise]` / `[pronouns]` / `[honorific]` / `[tone]` / `[emoji]` / `[alignment]` / `[lifestyle]` sections per D.83.
- [x] **K.5** Screen 4 Lifestyle (LW-E): Single/Partnered × free/strict, phrasing computed from alignment ("strictly-kept" / "strictly-keeping" / "strictly-shared" / "routine") via `Lifestyle.labelFor(Alignment)`
- [x] **K.6** Screen 5 Roles (LW-F): 17-role multi-select grid; `self-care` always-on (lock icon, non-toggleable); `kink` hidden under unaligned-private OR neutral-mode
- [x] **K.7** Screen 6 Templates per role (LW-G): per-role expandable sections with atomic-activity template chips. Smart-defaults = "all on" for v1; fine-tuned per (alignment, lifestyle) matrix deferred.
- [x] **K.8** Screen 7 Git setup (LW-H): three cards — Phone-only (default + recommended) / Self-hosted Forgejo+Gitea (URL + OAuth client ID inputs) / GitHub. OAuth flow itself stubbed for v1; finishes via phone-only fallback.
- [x] **K.9** Screen 8 Calendar scaffolding (LW-I): `WizardScaffolder.materialize()` — per-role calendars with locked priority/emoji, one recurrence per enabled atom (FREQ=DAILY 09:00 + 15min duration for v1), 5 onboarding standing tasks, identity.toml extension, single initial commit via `GitRepo.initLocalOnly`. Remote-push paths deferred until OAuth client IDs are registered.
- [x] **K.10** Screen 9 Done handoff (LW-J): now-card preview ("next up: …" + praise term), "Open my calendar" CTA. Full live-resolver-driven preview deferred.
- [x] **K.11** Bat-mascot sticker set spec (LW-K) — `docs/assets/wizard-bat-stickers.md` enumerates all 13 keys. `R.drawable.about_bat` used as placeholder until artist delivers; contact-sheet PNG is artist's deliverable.
- [x] **K.12** Re-run from Settings (LW-L): `WizardNavHost(initialScreen = WizardScreen.Roles, initialDraft = …)` preserves the entry-point and prefilled selections. Settings entry-point to drive it is Phase S.8.
- [x] **K.13** Testing strategy (LW-M): 4 test classes / 18 cases — `WizardDraftTest`, `WizardScaffolderTest` (filesystem materialization), `AgeGatePrefsTest`, `WizardNavHostTest` (Robolectric Compose). Full alignment×lifestyle matrix walk + mid-wizard-exit + deep-link bypass tests deferred to v2.
- [x] **K.14** Age gate + neutral-mode toggle: `AgeGatePrefs` (EncryptedSharedPreferences `age_confirmed_at`) gates `MainActivity`; `NeutralModePrefs` propagates into `WizardNavHost(neutralMode = …)` to hide kink role+templates. Settings → Neutral toggle UI is Phase S.9.

---

## ~~Phase L — demo content~~ (RETIRED)

~~Deep-dive: [`templates-demo-wizard.md`](templates-demo-wizard.md) phases TW-G through TW-H.~~

**RETIRED** — superseded by Phase K (lifestyle wizard); demo content concept retired in favor of materialized starting state per D.54. See [`draft-lifestyle-wizard.md`](draft-lifestyle-wizard.md). The wizard's scaffold output IS the user's canonical first-run data, not seed/sample/demo data. `demo-sub` / `demo-dom` repo seeds are not shipped.

- [ ] ~~**L.1** `demo-sub` repo seed — full SFW-kinky-coded sub schedule + tasks~~
- [ ] ~~**L.2** `demo-dom` repo seed — Dom's calendar with check-in events that overlay~~
- [ ] ~~**L.3** Demo mode flag: spins up local clones (no remote push) with both repos preloaded~~
- [ ] ~~**L.4** "Exit demo mode" path: option to keep demo data as a real local repo or discard~~

---

## Phase M — notifications (shipped in commit `4ad0deb`)

Deep-dive: [`notifications-sharing-import.md`](notifications-sharing-import.md) phases NS-A through NS-D.

- [x] **M.1** Notification channels: events, tasks, briefings, sync, errors, foreground (all 6 registered in `SkbApp.onCreate` via `notif/NotificationChannels.kt`)
- [x] **M.2** AlarmManager scheduling per event lead-time (`notif/EventReminderScheduler.kt` + `ReminderBroadcastReceiver`; lead-time grammar in `notif/LeadTime.kt`; `setExactAndAllowWhileIdle` + `USE_EXACT_ALARM`)
- [x] **M.3** Notification settings: per-channel enable + silent toggles + per-calendar enable/silent/lead-time overrides in `notif/NotificationPrefs.kt` + `NotificationsSettingsScreen` composable. Per-calendar logical groups (D.14 NS-A.10..14) DEFERRED to follow-up.
- [x] **M.4** Sync status notification (silent default) via `notif/SyncResultNotifier.kt` + `SyncEventNotificationBridge` listening on `SyncScheduler.eventsFlow`.
- [x] **M.5** Foreground service notification — `SyncService` now points at the `skb.service` low-importance channel (registered by `NotificationChannels`).
- [x] **M.6** Snooze (10/30/60m) + Open + I-didn't / Partial actions; deviation actions go through `notif/DeviationActionWriter` which calls `EntityWriter.write` of a `Deviation`.

**Deferred (NS-Z follow-up):** boot re-arm (`RECEIVE_BOOT_COMPLETED`), AlarmHorizonExtender nightly worker, D.79 per-category cadence defaults, D.81 cal-briefings auto-bodies, D.82 post_event_checkin opt-in flow, D.80 off-schedule warning prefix, NS-A.10..14 logical notification groups + group-level mute, NS-D.13 `setPublicVersion` redacted-body retrofit for non-private events.

---

## Phase N — common-time finder

Deep-dive: [`resolver.md`](resolver.md) phase RV-E + [`ui-spec.md`](ui-spec.md) phase UI-M.

_N.1 + N.2 shipped in commit `a1ed1ff` (Round 1). Together rail destination wired into `AppScaffold`, `TogetherPane` + `TogetherInputForm` + `TogetherResultList` + `TogetherEmptyState` + `TogetherViewModel` live under `ui/together/`. ISP-narrow `BusySource` + `CommonTimeFinderPort` ports keep the VM testable. AVD-smoked on `emulator-5554` (input + 10-slot results). 222 tests pass. N.3 (full quick-create event sheet) deferred — current v1 stub shows a "would create event" toast; the editor sheet lands with I-K-EE._

- [x] **N.1** Together-tab UI: select repos, calendars (per-repo sub-filter pending; current build picks all calendars in selected repos), date range, duration, day/time filters
- [x] **N.2** Run finder → ranked free-slot list
- [ ] **N.3** Tap slot → quick-create event (target repo + calendar picker) — stub v1; full editor sheet deferred to I-K-EE

---

## Phase O — sharing + read-only

Deep-dive: [`notifications-sharing-import.md`](notifications-sharing-import.md) phase NS-E.

_Shipped Phase O in commit `53195ac`: `ShareLink` codec + `ShareLinkGenerator` + Compose `ShareSheet` + `ShareLinkReceiver` + manifest deep-link intent-filter + `ReadOnlyBanner` (M3E error-container) + per-remote `treatAsReadOnly` toggle + `ForeignEventSourceChip`/`foreignEventBackground` modifiers + `RepoConfig.readOnlyViaShare / sourceRepoLabel / sourceRepoBackLink`. 22 new tests added (244 pass), AVD-smoked: deep-link `strictlykeptboy://share?url=...&mode=read-only&name=personal-cal` registers a `readOnlyViaShare=true` RepoConfig (visible in Repos list)._

- [x] **O.1** Share-link generator + ShareSheet (read-only / read-write radio, expiry chips, include-mirror-remotes toggle, copy / share via). Codec encodes/decodes `strictlykeptboy://share?url=…&mode=…&expiry=…` round-trip including multi-URL and calendar-scoped variants.
- [x] **O.2** Deep-link receiver: manifest intent-filter on MainActivity + `ShareLinkReceiver.classify` sealed-action dispatch (Invalid / Expired / CloneReadOnly / LaunchAddRepo). Cold-start + onNewIntent both routed. Read-only path registers a `readOnlyViaShare=true` RepoConfig (full background-clone implementation deferred to a follow-up; the intent dispatch + UI surfaces ship now).
- [x] **O.3** Per-remote "Treat as read-only" toggle in Repo Settings (override of auto-detected `readOnlyDetected`). Banner with M3E error-container styling fires whenever any remote on the repo has `effectiveReadOnly = true`. (Push-queue integration relies on existing SyncScheduler retry path — no change required to the queue itself.)
- [x] **O.4** Foreign-event styling helpers (`ForeignEventSourceChip` + `Modifier.foreignEventBackground()` → surfaceContainer instead of primaryContainer). Wiring into the event-row rendering surface is gated on Renderer.Sources carrying per-event `repoId` (Round 2 — `RepoSnapshot` plumbing); helpers + RepoConfig fields land in O.

---

## Phase P — import / export

Deep-dive: [`notifications-sharing-import.md`](notifications-sharing-import.md) phase NS-F.

_Shipped Phase P (.ics import + export, v1 scope) in commit `d800c84`: hand-rolled `IcsParser` + `IcsExporter` in `port/ics/` (RFC 5545 VEVENT subset — VTIMEZONE dropped, EXDATE → cancel Exception), `ImportExportScreen` + `ImportPreviewSheet` + `ImportExportViewState` under `ui/import_export/`, wired into the Settings rail destination as the v1 surface. SAF (`OpenDocument` + `CreateDocument(text/calendar)`) wired in MainActivity composition root. 19 new tests (263 total). AVD-smoked: pushed `/sdcard/Download/sample.ics`, parse-preview correctly reported "2 events, 0 rules, 0 exceptions", confirm wrote + committed via `EntityWriter.writeBatch` + `GitRepo.commitAll`, export round-trip back to `/sdcard/Download/my calendar.ics`. CSV task export (P.3), VTODO export (P.4) and `tools/` server-side hooks (P.5) deferred — not blocking v1 ship, tracked here._

- [x] **P.1** iCal (.ics) export per calendar / per repo / per date-range — per-repo + per-calendar variants ship; date-range filtering deferred until ScopeFilter chip lands (Phase S settings polish).
- [x] **P.2** iCal (.ics) import into chosen calendar (UID-keyed de-duplication on re-import) — UIDs that aren't UUIDv7-shaped preserved as `external_uid` for round-trip; full de-dup against an existing calendar's `external_uid` set is a P-followup, since the v1 wizard flow always imports into a chosen calendar (caller controls dedup by picking a fresh calendar).
- [ ] **P.3** CSV export for tasks — deferred to Phase S (tasks shipped tests don't yet exercise an exporter; revisit alongside Phase S.6).
- [ ] **P.4** iCal export for tasks (VTODO) — bonus, deferred.
- [ ] **P.5** `tools/` directory with shell scripts for server-side CalDAV/Thunderbird/Outlook hooks — deferred; not in v1 scope.

---

## Phase Q — Android Auto — Q.1..Q.3 shipped in commit `bf709f0`

Deep-dive: [`ui-spec.md`](ui-spec.md) phase UI-S (corrected from UI-N).

- [x] **Q.1** `CarAppService` skeleton — `auto/SkbCarAppService` + `SkbSession` + manifest service entry + `res/xml/automotive_app_desc.xml` + `androidx.car.app:app:1.7.0` dep. F22 also triggered an `AppGraph` extraction (`composition/AppGraph.kt`; MainActivity 417 → 327 LOC).
- [x] **Q.2** Today list template — `auto/TodayScreen` renders `ListTemplate` from the ISP-narrow `TodayEventSource` interface (R.X.1). Row tap pushes `NextUpScreen`.
- [x] **Q.3** "Next up" pane template — `auto/NextUpScreen` renders `PaneTemplate` with title + duration + time-until + 3 follow-ups + "Open in app" action that fires an intent at MainActivity.
- [ ] **Q.4** Voice prompts ("what's next?")
- [ ] **Q.5** Read-only enforcement (already true by construction — Q.1..Q.3 expose no edit affordances; formalize as a service-level invariant + test in Q.5)

---

## Phase R — tablet + master-detail — R.1..R.5 shipped in commit `9527901`

Deep-dive: [`ui-spec.md`](ui-spec.md) phase UI-R.

- [x] **R.1** WindowSizeClass detection — `ui/adaptive/WindowSizeClass.kt` sealed type + `LocalWindowWidthSizeClass` CompositionLocal + `ProvideWindowSizeClass` BoxWithConstraints wrapper at AppScaffold root.
- [x] **R.2** Schedule master-detail — Medium/Expanded use `MasterDetailLayout` Row with always-on `EventDetailContent`; Compact keeps the legacy `ModalBottomSheet`. Now-card resolver (`findActiveBand`) auto-focuses the currently-active event on tablet.
- [x] **R.3** Tasks master-detail — same split: `TaskDetailContent` extracted from sheet body; Compact uses ModalBottomSheet, Medium/Expanded uses two-pane with empty-state placeholder.
- [x] **R.4** Settings master-detail — new `ui/settings/SettingsPane.kt` with sealed `SettingsCategory` (General / Identity / Mode / Notifications / Repos / About). Compact = list → push to category content with back arrow; Medium/Expanded = two-pane with persisted selection. ImportExport flow lives under the Repos category (preserves Phase P behaviour).
- [x] **R.5** Tablet-specific touch targets + spacing — `AdaptiveSpacing` object: min interactive 48dp/56dp/64dp + spacing multiplier 1.0×/1.25×/1.5× across Compact/Medium/Expanded. Layered on top of the Phase F.3 `LocalDensityScale` CompositionLocal via `adaptiveDp(base)`. AppScaffold also opens the rail to `WideNavigationRailExpanded` on Medium/Expanded (verified visually on the tablet-resized AVD).

---

## Phase S — settings polish — shipped in commit `953e21d`

- [x] **S.1** Repos section — `ReposCategory` deep-link tile + import/export.
- [x] **S.2** Identities section — placeholder tile that deep-links to S.8b.
- [x] **S.3** Sync section (intervals, push strategy, retry, conflict prefs) — `SyncSettingsPrefs` + `SyncCategory`.
- [x] **S.4** Notifications section (channels, groups, defaults) — master briefings toggle + per-category lead-times + inlined Phase M per-channel surface.
- [x] **S.5** Calendars section (master toggles, priority editor, active-windows editor) — `CalendarVisibilityPrefs(Calendars)` + `CalendarsCategory`.
- [x] **S.6** Todolists section (same shape as Calendars) — `CalendarVisibilityPrefs(Todolists)` + `TodolistsCategory`.
- [x] **S.7** Templates section (browse + apply + custom template repo URL) — `TemplatesCategory` with template-id list + apply / save-url callbacks (clone wiring deferred to Phase WW).
- [x] **S.8** Lifestyle section — "Add more to my lifestyle" entry-point per Phase K.12 (LW-L). (Replaces the retired Demo section per D.54.) — `LifestyleCategory`.
- [x] **S.9** Appearance section (theme, density, font, dynamic color override) — `AppearanceCategory` wraps existing `AppearancePrefs` + `NeutralModePrefs`.
- [x] **S.10** About section (build info, license screen, mascot, repo link) — `AboutCategory` with `BuildConfig.GIT_SHA`+`BUILD_DATE` + 5-tap easter egg + Licenses placeholder + repo link.

---

## Phase T — theming + personalization

_T.2..T.7 shipped in commit `3966c7c`._

- [x] **T.1** Launcher icon + mascot art shipped — bat-with-calendar adaptive launcher (`mipmap-*/ic_launcher.webp` + `drawable-*/ic_launcher_foreground.webp`, dark background `#0A0806`); secret about-page mascot scene at `drawable-nodpi/about_bat.webp`. Shipped in change `7afb686+`.
- [x] **T.2** Repo icon system: sealed `RepoIconKind` (Emoji / Photo / AutoInitials) under `ui/theming/`, picker wired into `RepoSettingsScreen`. Defaults to `AutoInitials(initialsFromName, seedColorFromName)`; user can switch to mascot emoji subset or SAF-picked photo. Shipped in this change.
- [x] **T.3** Calendar/todolist icon + color picker — `CalendarColorPicker` (12-swatch M3E palette + HEX field) + `CalendarThemePrefs` for per-`<repoId>/<calId>` JSON-backed icon + seed persistence. Shipped in this change.
- [x] **T.4** Event emoji prefix UI — `splitLeadingEmoji` + `EventTitleEmojiPreview` Composable strips leading emoji codepoint and renders it larger next to the title. Convention persisted on the raw title; full editor lands when Phase EE ships. Shipped in this change.
- [x] **T.5** Dynamic color override per repo (color seed) — `PerRepoColorScope` + `LocalSchemeProvider` CompositionLocal install a seed-derived M3E `ColorScheme` over the app-wide scheme for the active pane; per-repo `colorSeed` editable in repo settings via `CalendarColorPicker`. Shipped in this change.
- [x] **T.6** Themed-icon monochrome layer (Android 13+) — `drawable/ic_launcher_monochrome.xml` bat silhouette vector + `<monochrome>` slot in both `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`. Shipped in this change.
- [x] **T.7** Secret About scene unlock — `AboutCategory` now uses 7 rapid taps with a 2-second reset window (`ABOUT_EASTER_EGG_TAP_COUNT`, `ABOUT_EASTER_EGG_RESET_MS`); reveals `about_bat.webp` full-screen plus the `about_easter_egg_tagline` ("STRICTLYKEPTBOY — schedule dreams. keep promises."); BackHandler + tap-to-dismiss. Shipped in this change.

---

## Phase U — accessibility + i18n scaffolding — shipped

- [x] **U.1** TalkBack labels on every interactive element — sync button, repo switcher chip, identity avatar, age gate, wizard mascot all carry `Modifier.semantics { contentDescription = ... }`; SyncButton states (idle / syncing / error / success) each route to a distinct `cd_*` string; `TalkBackLabelTest` smokes the core surfaces
- [x] **U.2** Content descriptions for icons (repo switcher, sync, identity) — `cd_sync_button_*`, `cd_repo_switcher_*`, `cd_identity_avatar_for` namespace established in `strings.xml`; per-state sync cd carries the failure reason
- [x] **U.3** Large-text + high-contrast verified at 200% scale — AVD-smoked at `font_scale = 2.0`: Schedule/Tasks/Together/Repos/Wizard/Settings rail labels remain readable, view-tab strip char-wraps gracefully, no clipping or off-screen widgets in the chrome; `LargeTextSnapshotTest` (Robolectric) guards composition-time overflow regressions
- [x] **U.4** `strings.xml` discipline (closes F11) — new `ui/a11y/EnumLabels.kt` central package exposes `@StringRes labelRes: Int` + `@Composable fun T.labelString(): String` + `fun T.labelString(context: Context): String` extensions for `TopDestination` / `ScheduleViewTab` / `TaskViewTab` / `SpeciesChoice` / `Alignment` (+ `taglineRes`) / `RoleId` / `Honorific` / `ToneRegister` / `EmojiDensity` / `Lifestyle` (`labelString(alignment)`) / `AddRepoProvider` / `QuickAddTarget`; call sites updated in `AppScaffold` / `SkbTopBar` / `TasksPane` / `TaskQuickAddFab` / `AddRepoNavHost` / `WizardNavHost`; enum `.label: String` constructor fields preserved + documented as **wire-format** because `WizardScaffolder` persists them verbatim to `calendar.toml` (`name = ...`) and `identity.toml` (`[honorific].term = ...`) — those values are on-disk repo schema and must not be localised; `EnumLabelLocalizationTest` walks every variant and asserts each resolves to a non-empty English string
- [x] **U.5** Locale wiring — `values-en-rGB/strings.xml` ships as a *partial* override (Colour seed vs Color seed) proving Android's locale-fallback behaviour; `LocaleFallbackTest` verifies the en-US/en-GB split + canonical-English fallback for un-overridden keys; AVD smoke via `adb shell cmd locale set-app-locales com.eight87.strictlykeptboy --locales en-GB`; `docs/plans/translations.md` documents the canonical-EN + partial-override workflow, mirrors the shutterboy / tonearmboy / whisperboy pattern (user + Claude per-language in dedicated sessions); CLAUDE.md gains a Translations section pointing at the plan

---

## Phase V — performance pass — shipped in commit `cb3cbf9` (see `docs/perf-baseline-2026-05.md`)

- [x] **V.1** Cold start budget: < 600ms to schedule day view — AVD COLD ≈ 1.5–1.7s (swiftshader; real-device re-measure pending); Robolectric AppGraph init ≈ 28ms; `PerfTraceRecorder` shipped wrapping `android.os.Trace.beginSection` for `app_oncreate` / `appgraph_init` / `mainactivity_oncreate` / `schedulepane_first_render`.
- [x] **V.2** Sync small repo: < 2s — Robolectric (50-entity fullScan+fetch+pullRebase, file-transport bare) ≈ 34ms (within budget by 60×).
- [x] **V.3** Render month with 200 events: < 200ms — Robolectric measurement ≈ 3ms (within budget).
- [x] **V.4** Common-time finder over 5 repos × 30 days: < 800ms — Robolectric ≈ 1ms (within budget).
- [x] **V.5** Memory: < 150MB resident at steady state — AVD ≈ 178MB PSS / 250MB RSS (**over budget**); follow-up F41 in `refactor-solid.md` for R8 narrowing + lazy-init.
- Standing perf-discipline additions: `GitRepoRegistry` bounded at 50 entries (was unbounded); `PerfTraceRecorder` under new `perf/` package; benchmark suite under `app/src/test/.../perf/`.

---

## Phase W — release engineering — Round 1 COMPLETE

W.2 + W.3 shipped in commit `acbebee`; W.1, W.4..W.8 shipped in this Phase W close-out commit. Round 1 (Phases A–W) is now complete.

- [x] **W.1** Signing config + release keystore (user-supplied, gitignored). Env-var driven: `STRICTLYKEPTBOY_RELEASE_KEYSTORE` / `_KEY_ALIAS` / `_KEY_PASSWORD` in `app/build.gradle.kts`, falls back to debug signing when unset. `.gitignore` covers `*.jks` / `*.keystore` / `release-keystore.jks` / `/release/`.
- [x] **W.2** GitHub Actions release workflow → APK with `strictlykeptboy-<version>-<sha7>.apk` naming (mirror tonearmboy). Shipped in `acbebee` (`.github/workflows/release.yml`).
- [x] **W.3** Obtainium-compatible release notes + SHA-256 table. Shipped in `acbebee` (`scripts/build-release-apk.sh` emits the table; tag-pushed workflow is self-disabling).
- [x] **W.4** Play Store listing copy (Mature 17+ Lifestyle category per D.59 / K-4). `docs/play-store-listing.md` — title, 79-char short description, ~2150-char full description leading with git-backed / AI-native value prop, lifestyle / D/s positioning in paragraph 3 with explicit neutral-mode toggle mention.
- [x] **W.5** Play Store screenshots set (per D.59 / K-4): `docs/play-store-screenshots.md` specs the 8-screenshot set (4 neutral + 4 kink-mode) with bat-mascot placeholders pending the WW sticker pack; capture workflow points at `app/src/main/play/screenshots/{en-US,en-GB}/`.
- [x] **W.6** ProGuard/R8 keep rules narrowed — `app/proguard-rules.pro` split the blanket JGit keep into `lib`/`api`/`transport`/`errors`/`storage`; added lib-recur + ktoml + kotlinx.serialization companions + Android-component entry points; `-dontwarn` rules for unreachable apache-sshd / GSSAPI / JMX / servlet refs. `isMinifyEnabled = true` + `isShrinkResources = true` locked on for release. Release APK 7.3 MB (debug 31 MB).
- [x] **W.7** Privacy policy page — `docs/privacy-policy.md` with permissions table, network-traffic disclosure, provider-specific privacy links, 17+ rating, contact info. About category surfaces a "Privacy policy" tile that opens the GitHub-rendered policy URL.
- [x] **W.8** First release `v0.1.0` staged. `versionName = "0.1.0"` + `versionCode = 1`. `scripts/build-release-apk.sh` dry-run produces `release/strictlykeptboy-0.1.0-<sha7>.apk` + `release/latest.apk` symlink + captured SHA-256.

---

---

# Round 2 — v1 scope expansion (Phases X–LL)

After Round 1, the user reviewed scope and directed that the entire
v2-deferral pile be pulled into v1, plus signed commits demoted from
required to optional, plus a `skb` CLI promoted to **primary interface
for the calendar data**. License constraint: prebuilt open-source
components only, no GPL. See `decisions.md` D.23–D.40.

Cross-references for Round 2:
- CLI deep-dive: [`cli-tooling.md`](cli-tooling.md) (new)
- Sync engine extensions: [`sync-engine.md`](sync-engine.md) (extends with SE-Q+)
- Data model extensions: [`data-model.md`](data-model.md) (extends with DM-K+)
- UI extensions: [`ui-spec.md`](ui-spec.md) (extends with UI-V+)
- Notifications/sharing extensions: [`notifications-sharing-import.md`](notifications-sharing-import.md) (extends with NS-L+)
- Resolver extensions: [`resolver.md`](resolver.md) (extends with RV-H+)

---

## Round 2+ universal completion checklist (applies to every phase below)

Every Round 2+ phase (X onwards) ships with the SOLID self-check baked in. Before any subagent ticks a phase's last sub-step, it MUST run this checklist against the diff and report results in the completion message. This is enforced by the dispatching contract (`CLAUDE.md` § Subagent dispatching) and the standing audit at [`refactor-solid.md`](refactor-solid.md).

- [ ] **SOLID.S** Single Responsibility — every new / modified file has one reason to change. Files past 500 LOC flagged in the report; past 800 LOC split before declaring done unless explicitly justified.
- [ ] **SOLID.O** Open/Closed — branching done via sealed types + exhaustive `when`, not `enum + when-chain` growing across consumers. New variant = new file, not edit-5-sites.
- [ ] **SOLID.L** Liskov — no `NotImplementedError` / deferred-bind in production paths; sealed-variant contracts honoured totally.
- [ ] **SOLID.I** Interface Segregation — composables / ViewModels take the narrowest interface that satisfies the need (e.g. `DayEventSource` one-method, not the whole `CacheDatabase`).
- [ ] **SOLID.D** Dependency Inversion — concrete classes (Room DAOs, JGit wrappers, OkHttp clients, EncryptedSharedPreferences) live behind interfaces; composition root (`MainActivity` → future `AppGraph`) is the only wiring site.
- [ ] **SOLID.AVD** AVD smoke — UI-affecting phases run the canonical install + screencap loop per `CLAUDE.md` § Test loop. Unit-tests alone are insufficient evidence for UI work.
- [ ] **SOLID.LOC** Report LOC of every new file > 200 LOC in the phase completion message. Append to the `refactor-solid.md` audit table when relevant.

Findings that don't block the current phase but warrant follow-up land in [`refactor-solid.md`](refactor-solid.md) as new F-numbered entries.

---

## Phase X — `skb` CLI primary interface

Deep-dive: [`cli-tooling.md`](cli-tooling.md) phases CLI-A through CLI-K.

Scaffold shipped (X.2, X.3, partial X.5) — Clikt + fat-jar + POSIX wrapper smoke-tested on `--version` (human + JSON). Round 2 fills in X.1/X.4/X.6/X.7/X.10/X.11/X.12 — real `event|task|cal|repo` surfaces, atomic write + auto-commit (X.4), `--dry-run` (X.6), full D.24 exit-code taxonomy (X.7), generated human + `--json` help catalog (X.10), `--repo`/`SKB_REPO`/walk-up discovery (X.11), pure-JVM test suite (X.12). Shared schema/codec code intentionally DUPLICATED (mini-TOML + atomic write + UUIDv7) in `:cli` rather than extracted to a new `:core` Gradle module — extraction would force restructuring of `:app/store` (Entities.kt 571 LOC + EntityWriter + RepoBootstrap); revisit when CLI-A.* fills in the remaining 19 subcommands and the duplication starts to hurt. — shipped in commit `9d3409f`.

- [x] **X.1** Define subcommand surface (D.24) — `event add|list|show`, `task add|list|show`, `cal add|list`, `repo init|list`, `help` real dispatch wired; remaining CLI-A.* surfaces tracked separately.
- [x] **X.2** Implement JVM-jar entrypoint (`:cli` Gradle subproject, `fatJar` task → `cli/build/libs/skb-cli-<ver>-<sha>.jar`)
- [x] **X.3** Shell-script wrapper for distribution (`tools/skb` POSIX shell launcher; finds java 17+, dev-fallback to repo build dir)
- [x] **X.4** Atomic write + auto-commit hooks — `core/AtomicWriter` (`<dir>/.<id>.tmp.<pid>.<nano>` → ATOMIC_MOVE) + `core/GitCommitter` (shells out to `git`; identical commit-message format to GUI per CLI-B.3).
- [x] **X.5** `--json` machine-readable output mode for AI consumers — root flag + per-subcommand `JsonEnvelope.success` / `JsonEnvelope.error` envelope (CLI-C.1).
- [x] **X.6** `--dry-run` flag (prints proposed change, no write) — short-circuits inside `atomicWriteAndCommit`, returns `CommitResult.DryRun` carrying the would-be file body.
- [x] **X.7** Exit-code taxonomy `core/ExitCodes.kt` enum mapped 1-to-1 with D.24 / CLI-D.1; Main.kt's outer try/catch routes `CliError` / `UsageError` / `CliktError` / `Throwable` to the right exit code.
- [ ] **X.8** Bundle in release pipeline alongside APK (GitHub Releases asset; homebrew tap `887/tap/skb`; `curl ... | sh` one-liner)
- [ ] **X.9** AGENTS.md content references CLI as primary path (rewrite per DM-K of data-model.md extensions)
- [x] **X.10** Help system — `help/HelpCatalog.kt` single-source-of-truth drives both human (`skb help <cmd>`) and JSON (`skb help --json`) catalogs per CLI-C.4.
- [x] **X.11** Repo discovery — `core/RepoDiscovery.kt` implements CLI-E.1 precedence: `--repo`, `SKB_REPO`, walk-up from `$PWD` (halts at `$HOME` or filesystem root); the `~/.skb/config.toml` `active_repo` fourth-precedence slot is intentionally deferred to its own CLI-E phase.
- [x] **X.12** Test suite — 30 pure-JVM JUnit tests covering Uuid7 / MiniToml / AtomicWriter / RepoDiscovery / ExitCodes / Help catalog / end-to-end CLI parse with a fake `GitOps`. Zero Robolectric, zero Android deps.

## Phase Y — CalDAV ↔ git repo mirror (events become files)

Deep-dive: [`sync-engine.md`](sync-engine.md) extension phases SE-Q+, [`notifications-sharing-import.md`](notifications-sharing-import.md) extension phases NS-L+.

> **The critical property of Phase Y:** CalDAV events are **materialized
> into the target git repo as Markdown-with-frontmatter files** under
> `calendars/<target-calendar-id>/events/<yyyy>/<mm>/<event-id>.md`,
> committed and pushed like any other event. Anyone who can read the
> repo can read these events — Claude (reading repo files), AI agents
> via the CLI, partners with shared repo access (dom/sub/family/team),
> and the user themselves on any device. This is the difference that
> matters versus Phase UU (overlay-only, never on disk, never visible
> to other agents or partners).
>
> **Canonical use case:** *"Work owns my Office 365 calendar. I want
> the events they schedule on me to land in my own git repo as files
> so Claude can see what's been put on my plate, my dom can see what
> work is doing to my time without asking, and I have one canonical
> place where my whole life lives."* This is `PULL_ONLY` mode against
> the work CalDAV endpoint, target-calendar = `work` (or similar) in
> the user's personal repo.

Three modes, picked per-mirror at setup time:

- **`PULL_ONLY`** (Y.4) — CalDAV → repo, one-way. The most common
  shape. Work / school / shared family calendars where the user does
  not own the upstream and just wants the events visible in their git
  world. Target calendar in the repo is marked read-only-from-app to
  prevent accidental edits that would be overwritten on next pull.
- **`PUSH_ONLY`** (Y.5) — repo → CalDAV, one-way. The user is the
  source of truth and wants their schedule visible on a partner's
  Outlook / Apple Calendar / phone-native CalDAV consumer.
- **`BIDI`** (Y.6) — full two-way. The user edits in both places and
  the bridge reconciles. Uses the shared git-conflict UI when both
  sides changed the same event between syncs.

- [ ] **Y.1** Add `ical4j` + `dav4jvm` deps; verify MPL-2.0 license-clean for our distribution
- [ ] **Y.2** CalDAV discovery flow (well-known URL `/.well-known/caldav`, then PROPFIND for calendars); in-app setup wizard `Settings → Repos → <repo> → + Add CalDAV mirror` with provider-aware presets (Google / Microsoft 365 / Apple iCloud / Nextcloud / custom)
- [ ] **Y.3** Server credential storage (alongside git creds in EncryptedSharedPreferences); OAuth Device Flow for Google + Microsoft; app-specific-password flow for Apple with clear in-UI instructions
- [ ] **Y.4** `PULL_ONLY` mirror mode (CalDAV → repo) — events materialize as repo files under `calendars/<target-id>/events/...`; target calendar gets `mirror = { source = "caldav", direction = "pull" }` in its `calendar.toml` so the app refuses local edits; commits are made under a configurable identity (default: "CalDAV mirror <server-host>") so blame stays readable
- [ ] **Y.5** `PUSH_ONLY` mode (repo → CalDAV) — repo files → CalDAV `VEVENT`s; tracks `imported_uid` on repo files to maintain identity across pushes
- [ ] **Y.6** `BIDI` mode (full two-way, with conflict detection)
- [ ] **Y.7** Conflict resolution shared with git conflict UI
- [ ] **Y.8** `skb caldav add|sync|remove|list` subcommands — covers all three modes; `--mode pull|push|bidi` flag at add-time
- [ ] **Y.9** Per-CalDAV-mirror sync interval (default 30m for mirrors)
- [ ] **Y.10** ETag- + CTag- + RFC6578 `sync-token`-based incremental change detection (avoid full re-pull)
- [ ] **Y.11** Mirror-source attribution in event detail UI: small "↻ mirrored from <server-host>" line + last-sync timestamp + link to re-sync now
- [ ] **Y.12** AGENTS.md note for repos with mirrored calendars: "the `work` calendar is a CalDAV mirror; edits to its files will be overwritten on next pull — change events upstream instead"

## Phase Z — Git LFS

- [ ] **Z.1** JGit LFS config wiring (`.gitattributes` for `attachments/**` over threshold)
- [ ] **Z.2** Threshold-based auto-routing (default 1MB, configurable per repo)
- [ ] **Z.3** Provider capability detection (probe LFS endpoint; on failure, fall back to in-tree)
- [ ] **Z.4** Fallback warning UI on no-LFS providers ("this provider does not support LFS; large attachments stored inline")
- [ ] **Z.5** Migration helper: convert existing repo's large in-tree attachments to LFS (`skb migrate --lfs`)

## Phase AA — Multi-timezone first-class

Deep-dives: [`data-model.md`](data-model.md) extension DM-L, [`resolver.md`](resolver.md) extension RV-H+I, [`ui-spec.md`](ui-spec.md) extension UI-V.

- [ ] **AA.1** Per-event `tz_id` field (additive, optional, non-breaking)
- [ ] **AA.2** Repo default tz in `repo.toml`
- [ ] **AA.3** Display-tz toggle in top bar; per-event "pin to event tz" flag
- [ ] **AA.4** Multi-tz common-time finder
- [ ] **AA.5** DST edge-case test corpus
- [ ] **AA.6** `skb tz convert <event-id> <new-tz>` CLI subcommand
- [ ] **AA.7** Participant-tz declaration in Together-tab common-time UI

## Phase BB — Weather overlay

- [ ] **BB.1** Open-Meteo client integration (Apache-2.0 lib)
- [ ] **BB.2** Per-repo location setting + location-permission flow
- [ ] **BB.3** Cache layer (per-(location, date) day forecast, refresh every 6h)
- [ ] **BB.4** Day view weather strip (3h granularity)
- [ ] **BB.5** Week view header icons
- [ ] **BB.6** Month view day-cell icons
- [ ] **BB.7** Per-event location override (for travel events)
- [ ] **BB.8** CC-BY attribution in About screen
- [ ] **BB.9** `skb weather show <date>` CLI subcommand

## Phase CC — Replies / comments on events

Deep-dives: [`data-model.md`](data-model.md) extension DM-K, [`ui-spec.md`](ui-spec.md) extension UI-W, [`notifications-sharing-import.md`](notifications-sharing-import.md) extension NS-M.

- [ ] **CC.1** Schema: `events/<yyyy>/<mm>/<event-id>.comments/<comment-id>.md`
- [ ] **CC.2** Event detail comments UI section (list + add)
- [ ] **CC.3** `comments` notification channel + per-event mute toggle (D.37)
- [ ] **CC.4** `skb comment add|list` CLI subcommands
- [ ] **CC.5** Comment author chip rendering (same shape as event chip)
- [ ] **CC.6** Threaded `in_reply_to` rendering (indented under parent)

## Phase DD — Drag-to-reschedule + pinch-to-zoom

- [ ] **DD.1** Long-press detector on event chips (Day + Week views)
- [ ] **DD.2** Drag gesture with grid snapping (configurable grid; default 15min)
- [ ] **DD.3** Drop commit with auto-message `move event "<title>" from <old> to <new>`
- [ ] **DD.4** Pinch-to-zoom timeline (5min / 15min / 30min / 1h levels)
- [ ] **DD.5** Zoom-level persistence per device
- [ ] **DD.6** Recurrence drag: prompt "this instance only / this and future / entire series" — creates appropriate exception or rule edit

## Phase EE — Inline-markdown body styling

- [ ] **EE.1** Markwon Compose integration (Apache-2.0)
- [ ] **EE.2** Editor toolbar with raw/rendered toggle
- [ ] **EE.3** M3E-aligned theming (heading typography, code block surface, link color)
- [ ] **EE.4** Inline image rendering for `![alt](attachments/...)` references

## Phase FF — Custom sticker / icon packs

- [ ] **FF.1** Pack format spec (directory + optional `pack.toml`)
- [ ] **FF.2** Install from local picker (zip or unzipped dir)
- [ ] **FF.3** Install from URL fetch
- [ ] **FF.4** `:sticker-name:` shortcut in title editors with autocomplete
- [ ] **FF.5** Icon picker integration (repos, calendars, todolists)
- [ ] **FF.6** Settings → Appearance → Sticker packs (list, install, remove)

## Phase GG — Signed commits (optional capability)

- [ ] **GG.1** GPG private-key import flow (file picker + paste-armored-text)
- [ ] **GG.2** Per-identity signing-key picker (lists imported key fingerprints)
- [ ] **GG.3** JGit + BouncyCastle signing wiring (PGP signature on commits when toggle on)
- [ ] **GG.4** Verified-author chip on entries from signed commits (small verified-checkmark badge)
- [ ] **GG.5** "Off by default" — confirmed throughout UI and CLI
- [ ] **GG.6** Key revocation flow (remove imported key; stops signing; existing signed commits unaffected)
- [ ] **GG.7** Per-repo signing-key picker (override per repo if the active identity has multiple keys)

## Phase HH — Android Auto voice-create

- [ ] **HH.1** Voice intent registration (`com.eight87.strictlykeptboy.action.CREATE_EVENT`)
- [ ] **HH.2** Natural-language parsing (date / time / title / calendar) — use Android's `Recognizer` + simple regex; fall back to ChatGPT-style fuzzy if a future ML model is wired in
- [ ] **HH.3** Default-repo + default-calendar resolution
- [ ] **HH.4** Confirm-by-voice flow ("create event 'dentist' tomorrow at 3pm in Personal — say yes to confirm")

## Phase II — Cross-device snooze sync (opt-in)

- [ ] **II.1** Setting toggle: Settings → Notifications → "Sync snoozes across devices"
- [ ] **II.2** `_local/snoozes.toml` schema (`[[snooze]]` entries with `event_id`, `until`, `device_id`)
- [ ] **II.3** Auto-merge resolver for snoozes (latest-wins per entry; idempotent)
- [ ] **II.4** Conflict-free idempotency verification (write a snooze, write the same snooze, confirm no double-entry)

## Phase JJ — Multi-branch awareness

- [ ] **JJ.1** Repo switcher shows current branch under repo name
- [ ] **JJ.2** Branch picker in repo settings
- [ ] **JJ.3** Branch creation flow (`skb branch create <name>`; UI form)
- [ ] **JJ.4** Branch switching (with stash + reset path for uncommitted local changes)
- [ ] **JJ.5** PR deep-link to provider's compare page
- [ ] **JJ.6** `skb branch list|create|switch` CLI subcommands

## Phase KK — CSV import for tasks

- [ ] **KK.1** Settings → Templates → "Import tasks from CSV" flow
- [ ] **KK.2** Column mapping UI (auto-detect common names, user confirms unknowns)
- [ ] **KK.3** `skb task import-csv <file> --list <todolist>` CLI subcommand
- [ ] **KK.4** Idempotency on re-import (by row hash; subsequent imports update existing entries)

## Phase LL — License audit + release scope

- [ ] **LL.1** Audit every dep against the no-GPL constraint:
  - Apache-2.0: kotlinx, Compose, Room, JGit modules, ktoml, commonmark, lib-recur, BouncyCastle, Markwon, open-meteo
  - MPL-2.0: ical4j, dav4jvm — link-clean for our distribution; document in About
  - EPL-2.0 / EDL: JGit core — link-clean; document
  - BSD/MIT: anything that pops up; should be fine
  - **Flagged**: anything LGPL/AGPL/GPL — reject; find alternative
- [ ] **LL.2** Licensee plugin allowlist updated for Round 2 deps
- [ ] **LL.3** About screen license list extended

---

---

# Round 3 — shared schedules + simplified ("good boy") mode (Phases MM–TT)

After Round 2, the user named a primary use case the existing scope
didn't optimize for: **a recipient — sub, student, employee, athlete
— receives a complete schedule from someone else via a deep-link or
QR, without ever using a calendar app before, and consumes the
schedule one-tap-from-link**. Later they can grow into authoring
their own without losing the gifted schedules. See `decisions.md`
D.41–D.52.

Cross-references for Round 3:
- Shared-schedules deep-dive (NEW): [`shared-schedules.md`](shared-schedules.md)
- Data model extensions: [`data-model.md`](data-model.md) (extends with DM-Q+)
- UI extensions: [`ui-spec.md`](ui-spec.md) (extends with UI-FF+)
- Resolver extensions: [`resolver.md`](resolver.md) (extends with RV-L+)

---

## Phase MM — Deep-link / app protocol registration

Deep-dive: [`shared-schedules.md`](shared-schedules.md) phases SH-A, SH-B.

- [ ] **MM.1** Register intent filter for `strictlykeptboy://add?...` (custom scheme)
- [ ] **MM.2** Register intent filter for `https://strictlykeptboy.app/add?...` (universal link)
- [ ] **MM.3** Digital Asset Links JSON at `https://strictlykeptboy.app/.well-known/assetlinks.json` for verified App Links
- [ ] **MM.4** URL parser: extract `url`, `label`, `mode`, `priority`, `via`, `references` query params
- [ ] **MM.5** URL fragment parser: extract `token`, `expires` (never sent over network)
- [ ] **MM.6** Multi-URL support (`?url=A&url=B`)
- [ ] **MM.7** Token-wipe: clear `#token=` from any persisted referrer after consumption
- [ ] **MM.8** Expiry enforcement: refuse links past `expires`
- [ ] **MM.9** QR scan integration (ZXing already in for D.42; verify reuse)

## Phase NN — `references.toml` manifest

Deep-dives: [`shared-schedules.md`](shared-schedules.md) SH-C, [`data-model.md`](data-model.md) DM-Q.

- [ ] **NN.1** Schema definition + ktoml round-trip. Supports multi-origin per Phase ZZ.B: `remotes = ["url1", "url2"]` array form alongside the singular `url = "..."` (read as one-element list, backwards-compat). Per Phase YY.H, references may carry an optional `write_back_target = "<repo-fingerprint>"` field to opt-in to receiving feedback files.
- [ ] **NN.2** Reader: scan `.strictlykeptboy/references.toml` on repo open. Reader handles both `url = "..."` singular and `remotes = [...]` array forms per Phase ZZ.B.
- [ ] **NN.3** Writer: append/remove entries via `skb ref add|remove`. Writer emits the multi-remote array form when more than one remote is configured per Phase ZZ.B.
- [ ] **NN.4** Auto-dedup against already-configured repos (by source-repo-id, D.51)
- [ ] **NN.5** "Offer to add referenced repos" sheet UI (per-reference toggle)
- [ ] **NN.6** Validation: refuse circular reference loops, refuse self-reference
- [ ] **NN.7** Required-reference warning surface in repo settings ("this repo expects ref X — not added")

## Phase OO — Cross-repo state files (`state/<source-repo-id>/`)

Deep-dives: [`shared-schedules.md`](shared-schedules.md) SH-D, [`data-model.md`](data-model.md) DM-R, [`resolver.md`](resolver.md) RV-L.

- [ ] **OO.1** State-file schema (done, snooze, note, reaction, priority-override, mute, hide)
- [ ] **OO.2** Source-repo-id derivation (SHA-256 of normalized URL → 16 hex)
- [ ] **OO.3** Resolver merge: source entity ⊕ state file → rendered instance
- [ ] **OO.4** Writer atomicity: state-file commits one file per state change
- [ ] **OO.5** `_local/state/` for pre-own-repo state (migrated on repo creation per D.47)
- [ ] **OO.6** `skb state set --done|...` CLI surface
- [ ] **OO.7** State-file orphan detection: when source entity is deleted, prompt to clean up state files

## Phase PP — Simplified mode chrome

Deep-dives: [`shared-schedules.md`](shared-schedules.md) SH-E, [`ui-spec.md`](ui-spec.md) UI-FF.

- [ ] **PP.1** Mode state in app prefs (`simplified` | `full`)
- [ ] **PP.2** Hidden surfaces: repo management, identities, templates, advanced sync, multi-view tabs, Together tab. When the active repo is no-origin (per Phase ZZ.G), ALL remote/sync UI is hidden including the sync button in the top bar; the sync-status badge becomes a permanent "local" badge.
- [ ] **PP.3** Visible surfaces: Schedule (single view), Tasks (combined), sync button, comment composer, settings (minimal)
- [ ] **PP.4** "Switch to full mode" entry point in settings (one-tap-reversible)
- [ ] **PP.5** Mode-label picker (Simplified / Focused / Received Schedules / Good Boy / Good Girl / Good Pet / Kept / Other)
- [ ] **PP.6** Auto-entry: simplified by default when only read-only repos OR only no-origin repos are configured AND the user has no own remote repo (per Phase ZZ.G).
- [ ] **PP.7** Play Store screenshots use "Simplified" label exclusively

## Phase QQ — First-launch deep-link bootstrap

Deep-dives: [`shared-schedules.md`](shared-schedules.md) SH-F, [`ui-spec.md`](ui-spec.md) UI-GG.

- [ ] **QQ.1** Detect "launched via deep-link AND no repos configured" condition. First-launch routing (per Phase K LW-A.4): if launched via `strictlykeptboy://add?...` deep-link and no repos configured → this QQ bootstrap. Otherwise if no repos configured → Phase K wizard. When launched WITHOUT a deep-link AND no repos AND user picks local-only on the 2-option splash, route to local-only scaffolder via `GitRepo.initLocalOnly` per Phase ZZ.G.
- [ ] **QQ.2** Skip wizard; go straight to "Add gifted repo" screen with URL prefilled
- [ ] **QQ.3** Auth flow: try `#token=` fragment first, then OAuth, then PAT prompt
- [ ] **QQ.4** Clone progress UI with animated mascot
- [ ] **QQ.5** Post-clone: boot into simplified mode, schedule view, one-time onboarding card
- [ ] **QQ.6** If receiving repo has `references.toml` with `default_active`, prompt to add referenced repos
- [ ] **QQ.7** Error handling: bad URL, network down, auth failure — clear messages with retry

## Phase RR — Authoring side: share-this-repo flow

Deep-dives: [`shared-schedules.md`](shared-schedules.md) SH-G, [`ui-spec.md`](ui-spec.md) UI-HH.

- [ ] **RR.1** Settings → Repos → tap repo → "Share this repo" entry
- [ ] **RR.2** Share-config sheet: mode, label, priority modifier, auth method
- [ ] **RR.3** Auth method: "Recipient adds own SSH key" path (no token in link)
- [ ] **RR.4** Auth method: "Embed one-shot deploy key" (provider API generates read-only key, embed in link fragment, 24h expiry)
- [ ] **RR.5** Auth method: "Embed fine-grained PAT" (provider API generates read-only PAT, embed in fragment, 24h expiry)
- [ ] **RR.6** Auth method: "Public repo" (no token needed)
- [ ] **RR.7** Output: copy link / save QR / system share sheet
- [ ] **RR.8** `skb share` CLI subcommand

## Phase SS — Evolution path: simplified → own repo migration

Deep-dives: [`shared-schedules.md`](shared-schedules.md) SH-H, [`ui-spec.md`](ui-spec.md) UI-II.

- [ ] **SS.1** "Add my own events" entry point in simplified mode (FAB or settings)
- [ ] **SS.2** Mini-wizard: repo name, provider, auth (2–3 screens)
- [ ] **SS.3** Optional template-picker (skippable)
- [ ] **SS.4** Migrate `_local/state/*` → new repo's `state/` folder
- [ ] **SS.5** Write `references.toml` in new repo listing currently-configured gifted repos
- [ ] **SS.6** Mode-choice prompt: stay simplified or switch to full
- [ ] **SS.7** Reverse path: full mode → "Switch to simplified mode" toggle

## Phase TT — Multi-repo priority resolution (extends Phase E + AA)

Deep-dive: [`resolver.md`](resolver.md) RV-M.

- [ ] **TT.1** Local-override (per-device) loaded from state-files at render time
- [ ] **TT.2** Repo-modifier from `references.toml` (high/normal/low → ±200)
- [ ] **TT.3** Receiver "pin this repo to top" → +500 modifier
- [ ] **TT.4** Clamp [1, 1000] with out-of-range warning in broken-entries tray
- [ ] **TT.5** Visual indicator on calendars whose displayed priority differs from source-declared priority

## Phase UU — CalDAV overlay (read-only, no-repo) — DEFERRED IMPLEMENTATION

Deep-dive: [`sync-engine.md`](sync-engine.md) extension phase SE-X. Locked decision: [`decisions.md`](decisions.md) D.53.

> **Status:** surface planned, implementation deferred. Office 365 / Google / iCloud / Nextcloud / generic-RFC4791 test matrix is hard to stand up without live accounts on each provider. Plan locks the shape now; ship once the testing story exists.

Peer to **Phase Y** (bidirectional CalDAV ↔ git), but the overlay **never** touches a git repo. Used when the user wants an external calendar (Office 365 "Team Holidays", a shared Google Calendar, an iCloud family calendar) to **appear** in their priority view without being materialized into any repo on disk.

- [ ] **UU.1** Data model: `CalDavOverlay` — no `repoId`, no `targetCalendarId`; events live in an in-memory + Room-cached overlay store keyed by `overlayId`. Additive `CalendarMeta.overlaySource: CalDavOverlayRef?` field on the resolver side.
- [ ] **UU.2** Strict read-only: no push, no edit, no conflict resolution. Long-press / drag / inline-edit refuse with "this calendar lives on a remote server; edit it where you created it." (banner links to the provider's web UI when known)
- [ ] **UU.3** Discovery flow reuses SE-Q PROPFIND chain (well-known → principal → calendar-home → calendar list with checkboxes)
- [ ] **UU.4** Auth reuses SE-Q `CalDavCredential` sealed interface: BasicAuth + OAuth Device Flow (Google/Microsoft) + app-specific-password (Apple)
- [ ] **UU.5** Cache: ETag/CTag/`sync-token` incremental fetch into Room only — **NEVER written to a git repo**, never appears in any `calendars/<id>/events/...` path
- [ ] **UU.6** Resolver integration: overlay calendars are first-class priority calendars alongside repo-backed ones — same priority slider (1..1000), same toggle, same active-windows, same priority-tiebreak comparator
- [ ] **UU.7** Settings UI: `Settings → Calendars → + Add external calendar (CalDAV)` — **top-level** entry, NOT nested under any repo. Distinct from `Settings → Repos → <repo> → + Add CalDAV mirror` (which is the SE-Q mirror surface)
- [ ] **UU.8** CLI: `skb caldav add --overlay <url>` (flag, not a separate `caldav-overlay` subcommand group — keeps surface flat). `skb caldav list` shows both mirrors and overlays with a `kind` column
- [ ] **UU.9** Visual treatment: overlay calendars get a small `🔗 external` badge in the calendar drawer and event list to distinguish from repo-backed
- [ ] **UU.10** Offline behavior: last cached snapshot renders; banner "external calendar last synced Nh ago" when stale beyond 2× sync interval
- [ ] **UU.11** Per-provider rate-limit profiles: Google (1M req/day), MS Graph (throttling-header aware), Apple iCloud (conservative quiet cap), Nextcloud (server-configurable), generic (RFC-compliant defaults). Default overlay poll: **60m** (vs 30m for mirrors — overlays are less interactive)
- [ ] **UU.12** Removal path: drop Room rows + `SecretsStore` entry; no repo cleanup needed because nothing was ever written to disk
- [ ] **UU.13** Test matrix: recorded HTTP fixtures for Google / Microsoft 365 / Apple iCloud / Nextcloud / generic-RFC4791. Live integration tests gated behind a `--live-caldav` flag and skipped in CI until creds are wired
- [ ] **UU.14** Documentation: README + AGENTS.md note that overlay calendars are remote-only and CLI agents reading the repo will NOT see overlay events on the filesystem

---

## Phase VV — Homescreen countdown widget

Android AppWidget that shows a large-numerals count to any event the user picks ("X days until …"). Neutral surface; works equally well for a trip, a wedding, a partner's visit, a release date. SFW-coded at every surface (title comes from the underlying event, which is user-controlled — see the wizard's surface-phrasing decisions SP-1..SP-5 in `templates-demo-wizard.md`).

**Sibling widget:** the *now widget* (current-activity glance) lives at Phase EEE — same widget infrastructure, different content mode. VV pins a future event and counts down; EEE shows what you're doing right now. Both share the Phase WW sticker resolver + NS-Z Wear bridge so the same artwork renders consistently across phone widget, lockscreen widget, and watch notification.

- [ ] **VV.1** Surface: `AppWidgetProvider` + a configure-activity to pick the target. Two picker modes: **(a)** specific event by `event_id`; **(b)** "next upcoming event in calendar X" — re-resolves each midnight tick.
- [ ] **VV.2** Optional event-frontmatter flag `pin_to_widget = true` (additive; resolver-ignored). Picker mode (a) defaults to the most-recently-pinned event, with all events still browsable.
- [ ] **VV.3** Layout (Material3 Expressive, glanceable): big numerals (days), small label below ("until Sir's visit" / "until Berlin trip" / etc.), optional subtitle line. Lockscreen preview shows numerals only (subtitle hidden — privacy default).
- [ ] **VV.4** Sizes: `2x1` (numerals only), `4x2` (numerals + label), `4x4` (numerals + label + subtitle + next-3-upcoming list).
- [ ] **VV.5** Update strategy: `AlarmManager` daily at local-midnight rollover (timezone-aware); `WorkManager` job piggybacks on the git-sync debounce (Phase J) to refresh after pulls.
- [ ] **VV.6** Tap behavior: opens the target event's detail screen via deep-link (Phase MM).
- [ ] **VV.7** Multiple widgets per device — each pinned to its own target. Configure-activity supports re-pinning without removing the widget.
- [ ] **VV.8** Strings: "in X days" / "tomorrow" / "today" / "X days ago" (past-due). All in `strings.xml` (Phase U i18n).
- [ ] **VV.9** Past-due styling: emphasized color from the M3E `error-container` role when count ≤ 0; subtle, never alarm-screaming.
- [ ] **VV.10** Demo seed (consumed by Phase L): `demo-sub` ships with one pinned event — "Sir's visit" on 2026-06-21 in `Special Events` — so the wizard's "Add widget" prompt has something to point at on first run.
- [ ] **VV.11** Test fixtures: time-frozen render previews at +30d / +7d / +1d / 0d / -1d to lock styling at each transition.
- [ ] **VV.12** User-authored event titles surface on the widget verbatim. The widget never adds editorial copy, decorative text, or app-generated phrasing — what the user typed is what shows. Lockscreen-discreet behavior is driven by the per-event `private = true` flag (see VV.13 / D.57 / K-2): when true, the widget shows '—' instead of the title and the notification body is suppressed. This privacy mechanism works identically for any event the user marks private and is not tied to content register. (Replaces the prior SFW-phrasing guarantee per KI-B / D.55+K-1.)
- [ ] **VV.13** Per-event privacy flag (D.57 / K-2): every event/task/recurrence frontmatter accepts optional `private = true` (default `false`). When true, the widget renders '—' instead of the title and suppresses any subtitle. Lockscreen notification shows only generic "Scheduled event" label; notification body suppressed. Composes with VV.3 (lockscreen-numerals-only) — when both apply, the widget on the lockscreen shows only the numeral count. Per-calendar default supported via `calendar.toml` `default_private = true`; per-event flag wins. In-app rendering is unaffected.

---

## Phase WW — Avatar + sticker presence-indicator system — WW.1, WW.2, WW.4 (disk-side), WW.5 (storage + picker) shipped on branch `round2/phase-ww-avatar-sticker`

Deep-dive: [`draft-avatar-stickers.md`](draft-avatar-stickers.md) phases AV-A through AV-I. Locks per D.63..D.69. Avatar = presence indicator, not tamagotchi; no HP/mood/hunger. Optional opt-in count-only streak counter per activity. Kink-positive openly with neutral-mode tag filter. Default species roster: bat, fox, tiger, lion, wolf, bunny, cat — bat is the app default + wizard guide species (but the bat-mascot character per D.56 is distinct from the bat-avatar).

- [x] **WW.1** Pack format + default bat pack (AV-A + AV-G) — MVP: `pack.toml` manifest parser (`PackManifest`), 7 species scaffolds under `assets/avatar-packs/<species>/`, tag taxonomy locked in `StickerTag` sealed (`neutral`, `kink`, `hygiene`, `workout`, `meal`, `work`, `study`, `posture`, `rest`, `idle`, `Custom`). Build-time validator (AV-G.5) deferred. Artwork files land in a follow-up; `AvatarResolver` gracefully falls through to `R.drawable.about_bat` when a referenced WebP isn't shipped yet.
- [x] **WW.2** Resolver (AV-B) — `StickerResolver` interface + `DefaultStickerResolver` impl with the full 6-rung chain (per-event override → sub-beat-sticker-id → activity-specific → category-generic → species-idle → bat-fallback) per D.66; neutral-mode filter on rungs 3+4 (and pass-through for kink+neutral combined tags); memoization keyed on the request tuple. `AvatarResolver` facade + LRU `StickerBitmapCache` sized by allocation bytes (~20 MB target). NowCard (AV-C) composable is a UI-LL follow-up, NOT in this slice.
- [ ] **WW.3** Sub-beat support (AV-D). Resolver already accepts `subbeatStickerId` + `subbeatIndex`; CLI + frontmatter array + cross-fade animation pending.
- [x] **WW.4** "Choose your own" species flow (AV-E) — disk side: `UserPackLoader` reads `<filesDir>/avatar-packs/<pack-id>/`, `PackId.fromCloneUrl` (normalized SHA-256 → 16 hex), `CompositePackStore` fan-out user-first → bundled-second. Clone-from-GitHub wiring (jgit/depth=1) + Settings → Advanced template URL field deferred.
- [x] **WW.5** Customization (AV-F) — storage shipped: `AvatarPackPrefs` with per-species active-pack pointer (plain prefs) + per-activity overrides (`EncryptedSharedPreferences`, key `avatar.overrides.<activity_id>` per D.67). Settings → Appearance "Sticker pack" picker section added (chips per pack matching the active species, with a hint when neutral-mode is on). Per-event sticker override schema deferred to the event-editor surface. JSON export/import deferred to v1.1.
- [ ] **WW.6** Rendering pipeline + cache (AV-H). LRU cache shipped; Coil 2.x + animated WebP + < 300ms first-frame budget remains.
- [ ] **WW.7** Snapshot test suite (AV-I). Pure-JVM resolver unit tests landed (`DefaultStickerResolverTest`, `PackManifestTest`); Roborazzi harness remains.

---

## Phase XX — Atomic activities, inverted habits, routines

Deep-dive: [`draft-atomic-activities.md`](draft-atomic-activities.md) phases AT-A through AT-K. Locks per D.70. Default state for any past-or-current scheduled event = `completed-by-schedule`; deviation is the explicit action. No guilt loop. Atomic = one entity per activity; routines are calendar overlays, not entities. Sub-beats inline in event TOML, bounded depth. Kink-positive openly with `kink` tag for neutral-mode filtering. Optional count-only streak counter per event-or-rule, no escalation.

XX.1..XX.4 bootstrap shipped in commit `c57a526` on branch `round2/phase-xx-atomic-bootstrap`. XX.5..XX.11 shipped on branch `round2/phase-xx-continuation` — atomic templates (kink + workout), additive `calendar.toml` routine fields + materializer engine, CLI `skb routine start|undo` + `skb streak`, sub-beat boundary scheduler + receiver + manifest registration, count-only streak counter (pure + Compose badge + global "Show streak counts" pref), Robolectric coverage of the new surfaces.

- [x] **XX.1** Deviation file schema (AT-A): new directory `deviations/<calendar-id>/<event-id-or-rule-id>/<yyyy-mm-dd>.md` SEPARATE from `exceptions/` (exceptions = scheduling changed; deviations = scheduling held but reality differed). TOML frontmatter `kind = "skipped" | "partial" | "completed-early" | "completed-late"`, `at`, `author`, optional `note`, optional `subbeats_completed`. No `kind = "completed"` — that's the default-by-schedule state. CLI `skb deviation set --event ... --date ... --kind ...`.
- [x] **XX.2** Resolver integration with inverted default (AT-B): `resolveCompletionState(event, now)` in Phase E render pipeline. Algorithm: future → scheduled; in-window no-deviation → in-progress; past no-deviation → `completed-by-schedule`; deviation present → that kind. Exception cancels shadow deviation. Room column `completion_state` with cache invalidation per AT-B.4. Visual treatment per AT-B.5 (never red, never warning glyphs, never blinking).
- [x] **XX.3** Notification + buzz pattern (AT-C): `events-atomic` notification channel; `AlarmManager` start + end alarms with `setExactAndAllowWhileIdle`. Title = user-authored verbatim; body empty by default. Actions: `I did it` (no-op — inverted default already wins; does NOT write completion file), `I didn't` → write `skipped` deviation, `Partial` → activity sheet → write `partial`, `Remind in 10/30/60 min`. End-alarm flips Room cache `in-progress → completed-by-schedule`. Permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`. Doze whitelist opt-in only.
- [x] **XX.4** Atomic self-care template (AT-D): `templates/atomic-self-care.toml` — brush-teeth (with 7 sub-beats: 6 quadrants + tongue/wrapup totalling 200s in the 5-min envelope), shower, shave-face, shave-pubes (`tags = ["intimate-care"]` neutral hygiene), hair, skincare, deodorant, nail-care, ear-clean. `neutral_safe = true`.
- [x] **XX.5** Atomic kink-self-care template (AT-E): `templates/atomic-kink-self-care.toml` with `neutral_safe = false` — cage-check, plug-check, posture-check (hourly-waking), collar-check, edge-and-stop, kegels (with 3 sub-beats), pubes-grooming, body-grooming. All entries `tags = ["kink"]` so neutral-mode filters them out.
- [x] **XX.6** Atomic workout template (AT-F): `templates/atomic-workout.toml` — pushups, situps, squats, pull-ups, planks (hold-time instead of reps), burpees. Each entry carries `sets` + `reps` (or `hold_seconds`). Sub-beat = one per set; phone buzzes between sets.
- [x] **XX.7** Routines as calendar overlays (AT-G): additive `calendar.toml` fields `routine = true`, `routine_id`, `routine_default_start`, `routine_can_materialize` modelled by `store/RoutineCalendarConfig.kt`; resolver treats routine calendars as ordinary calendars per AT-G LOCKED. Wizard scaffold of `routine-morning` / `routine-bed` / `routine-workout` deferred to wizard follow-up (data model + reader land here).
- [x] **XX.8** "Start X routine" quick-start (AT-H): pure materializer engine `store/RoutineMaterializer.kt` (sequential placement + overlap detection + shift-past resolution + sub-beat carry-over). Audit frontmatter `materialized_from` / `materialized_source_event` / `materialized_at` on `Event`. CLI `skb routine start <id> [--at <iso>] [--target <cal>] [--skip ...]` + `skb routine undo <materialized-at>` (`cli/.../routine/RoutineCommands.kt`). FAB bottom-sheet UI deferred to a follow-up (engine ships here so CLI + future UI both consume one algorithm).
- [x] **XX.9** Sub-beat schema + buzz pattern (AT-I): additive `[[subbeat]]` array on `Event` (round-trips through `toDoc` / `fromDoc`). `notif/SubbeatBoundaryScheduler.kt` schedules one `setExactAndAllowWhileIdle` per sub-beat (cap 16). `notif/SubbeatBoundaryReceiver.kt` posts low-priority single-vibration notification with title = sub-beat label, body = `<i>/<N>`, single `Skip ahead` action that writes a `partial` deviation with `subbeats_completed`. Manifest receiver registered.
- [x] **XX.10** Streak counter (AT-J, count-only): pure `resolver/StreakCounter.kt` walks scheduled dates back from today until a `skipped` deviation appears. `ui/components/StreakBadge.kt` — tiny rounded numeric badge, hidden when `< 2`, no flame/trophy/escalation. `NotificationPrefs.isStreakCountsEnabled` global toggle (default ON). CLI `skb streak <event-id-or-rule-id>` prints integer + last-skip date.
- [x] **XX.11** Test fixtures (AT-K): Robolectric `SubbeatBoundarySchedulerTest` (boundary timing + 16-alarm cap), `StreakCounterTest` (skip-breaks-streak / partial-does-not / today-skipped-zero / 3-way classification), `RoutineMaterializerTest` (sequential placement, overlap detection, shift-past resolution, sub-beat carry-over), `AtomicKinkTemplateNeutralFilterTest` (kink template gated under neutral mode). Phase M.7 sub-beat alarms wired via the new receiver registration.

---

## Phase YY — Cross-repo global-ID feedback

Deep-dive: [`draft-global-id-feedback.md`](draft-global-id-feedback.md) phases FB-A through FB-J. Locks per D.71..D.73. Cross-repo dom→sub feedback (hearts, fire, locked, collar, good-boy), comments, bonus tasks, journal entries — all file-based, committable, pushable, mergeable. Global ID format = `<repo-fingerprint>:<entity-uuid>` where `repo-fingerprint = SHA-256(first-commit's tree SHA)[:16]`. Device-local repo registry with per-direction (asymmetric) isolation. Feedback files live in the **feedbacker's** repo at `feedback/<target-fingerprint>/<target-entity-uuid>/<feedback-uuid>.md`. Opaque-string reaction tokens; sticker packs re-skin.

CLI-side primitives (YY.1–YY.6, YY.8, YY.9) shipped under `:cli/.../feedback/` on branch `round2/phase-yy-feedback-impl`. Android-side Settings/Room/Compose-drawer surfaces (YY.2 FB-B.4, YY.3 FB-C.5 indexer, YY.5 FB-E.4–6 drawer, YY.7 FB-G nav-rail tab + deep-link, YY.10 in-app help/AGENTS.md rewrite) are deferred follow-ups tracked in `refactor-solid.md`.

- [x] **YY.1** Repo fingerprint derivation + cache (FB-A): `RepoFingerprint.computeOrLoad` reads `.strictlykeptboy/repo-fingerprint` if present, else computes from `git log --reverse --max-count=1 --format=%T` (tree SHA of root commit), SHA-256, 16-hex truncation. Cache file is **gitignored, never committed** (added to scaffold `.gitignore` by Phase K wizard). Stable across rebase/squash of non-root commits; breaks only on root rewrite (rebind UI in FB-A.4 walks `feedback/<old-fp>/` and rewrites paths). CLI `skb repo fingerprint`.
- [x] **YY.2** Repo registry (FB-B): `<app-data>/repo-registry.toml` device-local (NEVER synced); auto-registers on every successful repo open. Per-repo `isolate_from` field (asymmetric — each repo controls only what *it* refuses to see). `RepoRegistry.allVisibleTo(viewerFp)` is the single chokepoint for cross-repo lookups (custom lint rule flags reads of `repo-registry.toml` outside `RepoRegistry`). Settings UI per FB-B.4 with explicit directional clarity copy. CLI `skb repo registry list|isolate|unisolate`.
- [x] **YY.3** Feedback file schema + writer (FB-C): one file per (author, target, reply_to). Atomic write + auto-commit. Validation refuses ill-formed targets, duplicate reactions, no-op empty feedback, cross-repo replies. Indexer scans `feedback/**` in every registered repo at startup + on git HEAD change → `FeedbackEntry` Room table. AGENTS.md documents the feedback file format + `skb react` / `skb comment` as primary path.
- [x] **YY.4** `skb react` / `skb comment` CLI (FB-D): `skb react add --target <global-id> --reactions heart,fire,locked [--body "..."] [--reply-to <feedback-uuid>]`, `skb react remove`, `skb react list`, `skb comment add|list`. Target resolution helpers `--target-event <uuid>` resolve to `<this-repo-fp>:<uuid>`. Exit codes per D.24. `--dry-run` supported.
- [x] **YY.5** Cross-repo resolver extension (FB-E): `FeedbackResolver.aggregate(targetGlobalId)` queries `FeedbackEntry` across every repo in `RepoRegistry.allVisibleTo(<viewer-fp>)`. Threading by `reply_to`, linear `created` order within threads. Memoized on `(targetGlobalId, set-of-visible-repo-HEADs)`, invalidated on HEAD change. Feedback drawer UI section in event/task detail sheets with reaction tallies + comment thread + always-present "+ react" affordance. Author chips show **receiving** repo's local label for the source repo (privacy-preserving).
- [x] **YY.6** Isolation enforcement + privacy tests (FB-F): aggregation **count masking is forbidden** — isolated sources are structurally invisible, not "N hidden". Notification suppression on isolation. Search/autocomplete masking. Robolectric privacy tests including snapshot-grep-for-fingerprint-strings.
- [~] **YY.7** Bonus tasks + sub-logged extras + journal entries (FB-G) — partial (file/path primitives shipped; deep-link, state-file fallback, nav-rail tab deferred to app): `bonus/<task-uuid>.md` at calendar root in a shared repo; `bonus = true` overrides scheduling pressure (never promoted to dated-tasks even with `due`). Completion writes back; degrades gracefully to state-file (`state/<dom-repo-fp>/<task-uuid>.done.toml`) when shared repo unavailable. Deep-link offer `strictlykeptboy://bonus?...` for dom→sub assignment. Journal entries at `journal/<yyyy-mm-dd>.md` (or `<yyyy-mm-dd>-<n>.md`) — UUIDv7 in frontmatter; NOT in schedule overlay; dedicated Journal nav-rail tab (Schedule/Tasks/Journal/Together/Settings). Journal entries reactable/commentable via the same FB-C/D/E machinery.
- [x] **YY.8** `references.toml` + share-flow extensions (FB-H): `write_back_target = "<repo-fingerprint>"` field per reference signals "this referenced repo is willing to receive feedback files we write addressed to its entities". UI only shows "+ react" affordance for references with `write_back_target` set. Share-this-repo (Phase RR) checkbox "Allow this share's recipient to leave feedback on my entries" auto-writes the `write_back_target` line into the recipient's `references.toml` on accept. CLI `skb ref set-write-back`.
- [x] **YY.9** Test fixtures + Robolectric end-to-end (FB-I): two-repo fixture builder (`fixture-sub` + `fixture-dom`); end-to-end tests for heart-cross-repo, isolation-hides-heart, reply-thread, bonus-task-round-trip (with shared-repo-unavailable variant), journal-reaction, root-rewrite-rebind. Performance budget: aggregating feedback for one event across 5 repos × ~100 files each < 50ms warm.
- [~] **YY.10** Documentation + AGENTS.md rewrite (FB-J) — partial (CLI helptext + draft already integrated; in-app help + AGENTS.md rewrite deferred): AGENTS.md feedback-format documentation as primary path; in-repo README.md privacy note; Settings → About → "How feedback works" link; `cli-tooling.md` full reference.

---

## Phase ZZ — Git layer no-origin + multi-origin extensions

Deep-dive: [`draft-no-origin-multi-origin.md`](draft-no-origin-multi-origin.md) phases MO-A through MO-H. Lock per D.74. Two orthogonal extensions to the Git infrastructure: **no-origin** repos (git-backed with zero remotes, first-class state, not degraded mode) and **multi-origin** sync (N ≥ 2 remotes with fan-out push policy, fan-in fetch+merge semantics, per-remote auth bindings). Touches Phase B / Phase I / Phase J / Phase NN / Phase PP / Phase QQ / Phase K inline (per the integration edits already applied above) plus all of sync-engine.md SE-B/C/D/E/F/I/J/K/L.

Round-2 closeout: ZZ.A..ZZ.H shipped in commit `8441fec` on branch `round2/phase-zz-multi-origin-finish`.

- [x] **ZZ.A** No-origin data model + GitRepo surface (MO-A): `RepoConfig` carries `remotes: List<RemoteBinding>` (may be empty) and `primaryRemote: RemoteName?` (null iff `remotes.isEmpty()`). `RemoteBinding` carries `name`, `url`, `transport`, `authMethod`, `fetchEnabled`, `pushEnabled`, `readOnlyDetected`. `repoId` becomes UUIDv7 persisted at `.strictlykeptboy/repo-id` (one-line) **— LOCKED committed to the repo** for cross-device state-file consistency per Phase OO. `GitRepo.initLocalOnly(rootDir, authorIdentity)` for no-origin construction. Fetch/pullRebase/push accept `remote: RemoteName? = primaryRemote` and return `NoRemotes` variants when `remotes.isEmpty()`. `GitStatus.localOnlyCommits` field. `SecretsStore` re-keyed from `<kind>.<repoId>` to `<kind>.<repoId>.<remoteName>` with one-shot migration (existing keys rewritten with `remoteName = "origin"`).
- [x] **ZZ.B** Multi-origin: remote naming + management API (MO-B): **named-by-purpose** with `origin` as conventional primary name (kept for stock-git interop), additional remotes default to `mirror-<n>` with user-editable `displayName`. Policy carried in `RemoteBinding` fields, never semantic on name. `GitRepo.addRemote / removeRemote / renameRemote / listRemotes`; reserved-name guard. Per-remote `fetchRefspec` field (default `+refs/heads/*:refs/remotes/<name>/*`). `references.toml` extended to allow `remotes = ["url1", "url2"]` array alongside backwards-compat `url = "..."` singular (per NN.1).
- [x] **ZZ.C** Multi-origin per-remote auth binding (MO-C): `CredentialBinding` resolved by `(repoId, remoteName)` tuple. SSH keypair generation per-remote — adding a second SSH remote generates a new keypair by default with opt-in reuse from another remote. Per-remote auth method UI in add-remote flow / repo settings. `transportConfigCallbackFor(repoId, remoteName)`. Re-auth error surfaces gain remote display label.
- [x] **ZZ.D** Multi-origin fetch + reconcile algorithm (MO-D): fetch pass iterates `remotes` where `fetchEnabled`, collecting `Map<RemoteName, FetchResult>` (per-remote network errors don't abort others). **Reconcile policy**: local branch rebased onto `<primaryRemote>/<branch>` ONLY. Non-primary remotes' tips become `MirrorDivergence(remoteName, count)` non-fatal yellow-banner signals when they have commits we don't. Manual diamond-merge UI offers (a) adopt mirror as authoritative, (b) override mirror via force-push (Settings → Advanced gated, typed confirmation). v1 ships (a); (b) gated.
- [x] **ZZ.E** Multi-origin push fan-out policy (MO-E): per-remote `pushPolicy: PUSH | PUSH_LAZY | NEVER`. Defaults: first remote added → `PUSH`, additional → `PUSH_LAZY`. Push pass iterates `[primary, then non-primary by add-time]`. Partial failure: if primary succeeds but mirror fails → local commit considered shipped, mirror enters per-remote retry queue (WorkManager exponential backoff per SE-L), `PartialPushDegraded` signal (small dot, not red banner). If primary fails → block subsequent pushes; do NOT push to non-primaries (avoid mirror getting ahead of canonical). Per-remote `readOnlyDetected`; repo-level red banner only when every remote is read-only.
- [x] **ZZ.F** Multi-origin conflict UI for N-way diamonds (MO-F): SE-J 3-way diff UI only fires for local-vs-primary conflicts (non-primary divergence stays in the banner UI). Conflict UI "Remote" label carries primary's display label. Diamond-merge mini-flow reuses the same UI with the mirror's label. Force-push affordance lives in repo settings → Remotes → `<remote>` → Advanced with typed-URL confirmation. **No force-push to primary, ever, in v1.**
- [x] **ZZ.G** No-origin UI representation + grow-into-remote path (MO-G): per the inline edits at I.2 / I.3 / PP.2 / PP.6 / QQ.1 / K.8 above. Add-repo flow gets a "Create local-only" peer option (not buried under advanced). No-origin repo switcher gets a small house "local" badge. Repo settings Remotes section with first-class no-origin treatment. Add-remote-later path: `git remote add origin <url>` + `git push -u origin main` against the extant local history (no history rewrite). First-launch no-deep-link path: 2-option splash "Start a local calendar (you can sync it later)" vs "Connect to a git remote now". Wizard (Phase K) repo-picker + auth steps become skippable; skipping runs the local-only scaffolder.
- [x] **ZZ.H** Cross-cutting: tests, docs, CLI parity (MO-H): `data-model.md` note that on-disk D.3 layout is unchanged (no `origin`-related files on disk; all remote state is in per-device `RepoConfig`). CLI: `skb repo init --local`, `skb remote add|remove|list|set-primary|set-policy`. Invariants: (i) `repo.remotes.isEmpty() ⇒ no foreground SyncService work for that repo`, (ii) `primaryRemote != null ⇔ remotes.isNotEmpty()`, (iii) `repoId stable across remote-set changes`. Robolectric end-to-end: no-origin → add origin → add mirror-1 → push to both → simulate mirror-1 read-only → primary still pushes.

---

## Phase AAA — Lifestyle templates (atomic + comprehensive) — shipped in commit `2dece78` (branch `round2/phase-aaa-lifestyle-templates`)

Source draft: [`draft-household-travel-vacation.md`](draft-household-travel-vacation.md) phases HV-A, HV-B, HV-C, HV-D, HV-K, HV-L, HV-P. Folds 8 new atomic-template families into the Phase XX (atomic activities, inverted habits) family — same TOML schema as AT-D / AT-E / AT-F, same inverted-default semantics. Cross-references: Phase XX (parent family), Phase K (lifestyle wizard registers role-toggles per template), Phase M (notifications honor per-category defaults from D.79), [`templates-demo-wizard.md`](templates-demo-wizard.md) TW-J (register all 8 templates).

- [x] **AAA.1** `templates/atomic-household.toml` — 88 base entries + 4 kink variants across 13 categories (trash, laundry, dishes/kitchen, bathroom, bedroom, living spaces, entry/mudroom, outdoor, mail/paperwork, pantry/fridge, pets, plants, dress-flow, leave-check, weekly-reset). All entries carry `neutral_title`; sub-beat envelope validated. `pet-*` entries tagged `nonSuperseable` per D.76.
- [x] **AAA.2** `templates/atomic-travel-prep.toml` — 52 entries across 7 lead-time tiers; every entry carries `lead_offset_days`. `pack-medication` tagged `nonSuperseable`; `pack-kink-kit` carries `privacy_flag = true`.
- [x] **AAA.3** `templates/atomic-flight-day.toml` — 19 parameterized entries; `offset_minutes_from_departure` / `offset_minutes_from_arrival` carried by entries; domestic/international 120/180-min buffer resolved by wizard at materialization.
- [x] **AAA.4** `templates/atomic-vacation-daily.toml` — 15 anchors (11 self-care + 4 kink). Kink anchors carry `privacy_flag = true` per K-2.
- [x] **AAA.5** `templates/atomic-adhd-anchors.toml` — 34 anchors across 6 categories; includes `hyperfocus-recovery` (HV-K.8); `tomorrow-glance` + `wind-down-routine-start` sub-beats fit envelope.
- [x] **AAA.6** `templates/atomic-medication.toml` — 22 entries. Every entry carries `privacy_flag = true` AND `nonSuperseable` tag per HV-L.A.1 + D.76.
- [x] **AAA.7** `templates/atomic-menstrual-cycle.toml` — 12 entries. All but `supplies-restock` private. `cal-period-grace` supersedence is opt-in at wizard time (HV-L.B.5; the calendar-overlay scaffold itself lives in Phase BBB).
- [x] **AAA.8** `templates/atomic-leisure.toml` — 46 entries across 8 categories; `dog-walk` carries 7 named sub-beats per HV-P.10; `TemplateCatalog.ALWAYS_SCAFFOLDED` registers leisure first-class per D.87.
- [x] **AAA.9** Tests — `app/src/test/.../store/LifestyleTemplatesParseTest.kt` (9 tests: per-template content, envelope, privacy + nonSuperseable invariants, neutral-mode + variant rendering, full catalog smoke). CLI tests at `cli/src/test/.../template/TemplateCommandsTest.kt` (5 tests: parse-fields, apply materialization, idempotency, reset, dry-run).
- [x] **AAA.10** `template_origin` + `template_slot` frontmatter conventions in `:app/store/TemplateOrigin.kt`. Wizard scaffolder now routes through `TemplateOrigin.tagsFor(WIZARD, ...)`; the CLI `apply` writes `template_origin:cli`. LW-L re-run idempotency keys off the slot prefix.
- [x] **AAA.11** CLI surface — `skb template list|apply|reset` under `:cli/.../template/TemplateCommands.kt`. Apply is idempotent on `template_slot:` collisions; reset preserves `template_origin:wizard` entries unless `--include-wizard`. Registered in `cli/.../Main.kt` subcommands list.

---

## Phase BBB — Event-level extensions: supersedence + attachments + multi-reminder

Source draft: [`draft-household-travel-vacation.md`](draft-household-travel-vacation.md) phases HV-E (supersedence), HV-M (attachments), HV-N (multi-reminder + briefings + off-schedule). Cross-references: Phase E / [`resolver.md`](resolver.md) RV-P + RV-Q, Phase M / [`notifications-sharing-import.md`](notifications-sharing-import.md) NS-Z, Phase P / [`data-model.md`](data-model.md) DM-W..DM-AA. Locks D.75 / D.76 / D.77 / D.78 / D.79 / D.80 / D.81 / D.82.

Round-2 partial close-out: BBB.1, BBB.2, BBB.5, BBB.7, BBB.8, BBB.9, BBB.14 (override + attach + reminder) + BBB.15 (data-layer + CLI test coverage) shipped in commit `d753737` on branch `round2/phase-bbb-event-extensions`. BBB.3 (overrides/ dir layout) shipped as CLI surface; the wizard Screen 5 materializer remains in Phase CCC scope. BBB.4 / BBB.6 (UI surfaces + per-kind renderers), BBB.10 / BBB.11 (multi-reminder firing + briefings notifications), BBB.12 (post_event_checkin), BBB.13 (.ics / .eml / .pkpass importers) remain deferred to follow-up sub-agents.

- [x] **BBB.1** Calendar-frontmatter extensions per HV-E.1: `supersedes`, `superseded_during`, `nonSuperseable`. Calendar-config also carries optional `[baseline_cadence]` block per HV-N.4. *(Shipped — see `:app/store/SupersedenceConfig.kt` + round-trip tests in `SupersedenceConfigParseTest`.)*
- [x] **BBB.2** Resolver supersedence pass per HV-E.2 + HV-E.6 invariants S1–S5. See [`resolver.md`](resolver.md) RV-P. Cache: Room table `event_visibility(date, event_id, hidden_by_calendar_id NULLABLE)` invalidated per HV-E.5. *(Resolver pass + per-event force-show overrides already wired in `ActiveSetEvaluator` + `OverlayResolver`; Room cache deferred until UI consumers in BBB.4 land.)*
- [x] **BBB.3** `overrides/<superseded-cal-id>/<event-id>/<yyyy-mm-dd>.md` directory per HV-E.4 / D.77. Two `kind` values: `force-show`, `force-show-for-range`. Materialized by the vacation wizard's Screen 5 (Phase CCC) when the user picks "but keep these on". *(On-disk layout + CLI writer shipped in `:cli/override/OverrideCommands.kt`; wizard-side materialization lives in Phase CCC.)*
- [ ] **BBB.4** UI surfaces per HV-E.3 (schedule hides; manage-overlays renders strikethrough+grey with per-event override toggle; week/month show leaf-glyph). See [`ui-spec.md`](ui-spec.md) UI-PP. *(Deferred — follow-up sub-agent.)*
- [x] **BBB.5** Event-frontmatter `attachments: List<Attachment>` array per HV-M, six sealed kinds (link / qr / file / barcode / vcard / location). Storage at `attachments/<event-id>/<filename>`; >100KB files Git-LFS'd (Phase Z); privacy inheritance from event's `private` flag. See DM-W. *(Shipped — `:app/store/Attachment.kt` sealed type + round-trip tests.)*
- [ ] **BBB.6** Attachment renderers per HV-M.4 (per-kind Composable specs: link, qr, file, barcode, vcard, location). See [`ui-spec.md`](ui-spec.md) UI-QQ. *(Deferred — follow-up sub-agent.)*
- [x] **BBB.7** Event-frontmatter `reminders: List<Reminder>` array per HV-N.1, sealed kinds (heads_up / all_day_banner / tomorrow_briefing / pre_event / at_start / post_event_checkin). See DM-X. *(Shipped — `:app/store/Reminder.kt` codec + `ReminderCollapsing` HV-N.7 helper + round-trip tests.)*
- [x] **BBB.8** Default reminder cadences per template category per HV-N.3 / D.79 (medical / flight / travel-prep / vacation-daily / household / medication / ADHD / birthdays). *(Shipped — `DefaultCadences` map in `:app/store/Reminder.kt`.)*
- [x] **BBB.9** Off-schedule detection per HV-N.4 / D.80: per-calendar `baseline_cadence`; events outside window flagged `off_schedule = true`. See [`resolver.md`](resolver.md) RV-Q. *(Schema + `isWithinBaseline` predicate shipped in `:app/store/SupersedenceConfig.kt`; resolver-side `offSchedule` band flag already wired in `Renderer.kt`.)*
- [ ] **BBB.10** Multi-reminder firing + notification stacking per HV-N.7 (60s collision window collapses into one Android notification with N expansion lines, privacy-aware). See [`notifications-sharing-import.md`](notifications-sharing-import.md) NS-Z. *(Helper `ReminderCollapsing` shipped; WorkManager firing path deferred — follow-up sub-agent.)*
- [ ] **BBB.11** System `cal-briefings` calendar per HV-N.6 / D.81: morning + evening briefing events with auto-generated bodies; user disable in Settings → Notifications → "Show briefings". See NS-Z. *(Deferred.)*
- [ ] **BBB.12** Inverted-habit `post_event_checkin` opt-in per HV-N.8 / D.82. *(Reminder kind shipped; opt-in UI deferred.)*
- [ ] **BBB.13** Import path extensions per HV-M.6: `.ics` URL/DESCRIPTION → `link` attachments; `.eml` airline confirmation → boarding-pass `barcode` + `file`; `.pkpass` → primary `barcode` + metadata + original archive `file`. See NS-Z import-lift. *(Deferred.)*
- [x] **BBB.14** CLI per HV-E.8 + HV-M.8 + HV-N + HV-J: `skb supersede`, `skb override`, `skb attach`, `skb reminder add|rm`, `skb briefing show`. See [`cli-tooling.md`](cli-tooling.md) CLI-U. *(`skb override add|list`, `skb attach add|list`, `skb reminder add|rm|list` shipped; `skb supersede` and `skb briefing show` deferred.)*
- [x] **BBB.15** Tests per HV-I.6 (supersedence invariants S1–S5) + HV-O.2 (attachment round-trip + LFS threshold + privacy inheritance) + HV-O.3..O.5 (multi-reminder fire + off-schedule detection + briefing rendering) + HV-O.8 (notification stacking). *(31 new unit tests across `SupersedenceConfigParseTest`, `AttachmentParseTest`, `ReminderParseTest`, `OverrideCommandsTest`, `AttachmentCommandsTest`, `ReminderCommandsTest`.)*

---

## Phase CCC — Quick-trip wizard + entry-points + sticker beats — compact cut shipped in commit d0c6c41

Source draft: [`draft-household-travel-vacation.md`](draft-household-travel-vacation.md) phases HV-F (vacation wizard), HV-G (entry-points), HV-H (sticker beats). Cross-references: Phase K (lifestyle wizard — sibling, NOT first-launch), Phase CCC's wizard reuses LW-K's bat-mascot sticker pipeline, [`ui-spec.md`](ui-spec.md) UI-OO.

**Compact-cut scope (shipped):** 4-screen wizard (Basics → Transport → Anchors → Confirm), `TripScaffolder` materialization of HV-B/HV-C/HV-D templates, three entry-points (Settings → Lifestyle, FAB long-press, Schedule empty-state), sticker-key registry with neutral-variant swap. Full-spec follow-ups: HV-F.6 supersedence picker (CCC.6), HV-F.7 mini-month preview (CCC.7), HV-F.9/10 edit-in-flight + cancel-trip (CCC.9), HV-G.2 calendar-detail "+ overlay from template" (part of CCC.10), HV-H.7 SVG/PNG sticker assets (CCC.11 assets-only), HV-I.7 Compose UI path-coverage tests (CCC.12 UI tests).

- [x] **CCC.1** Compose nav-graph per HV-F.1 — compact 4-screen sequence + back/discard handling (`TripWizardNavHost`). In-memory `TripDraft` (Room-backed persistence is the full-spec follow-up). See UI-OO.
- [x] **CCC.2** Screen 1 — Trip basics per HV-F.2 (name, start/end dates, destination free-text, traveler count). Sticker: `trip-suitcase-waving`. Offline-autocomplete is the full-spec follow-up.
- [ ] **CCC.3** Screen 2 — Travel-prep cadence per HV-F.3 (HV-B back-fill preview with per-row toggles + lead-offset edit; conditional gating on visa.required etc.). Sticker: `flight-paw-prints`. _Compact cut materializes via the simplified default-filter in `TripScaffolder.eligibleForCompactCut`._
- [ ] **CCC.4** Screen 3 — Flight details per HV-F.4 (conditional on mode=flight; multi-leg list with IATA codes; per-leg international toggle adjusts buffer). Sticker: `flight-paw-prints`. _Compact cut renders the transport-mode radio on Screen 2 and uses a single default leg in materialization._
- [x] **CCC.5** Screen 4 — Vacation-daily anchors per HV-F.5 — compact toggles (include daily, pack kink kit). Sticker: `beach-loungin-with-cage-still-on` (neutral variant `beach-loungin`). Per-anchor cadence-relax + meal-anchor radio are the full-spec follow-ups.
- [ ] **CCC.6** Screen 5 — Supersedence picker per HV-F.6 (per-calendar pause toggle defaults; nonSuperseable calendars visually locked; per-event "keep this on" override list materializes `overrides/` files). Sticker: `supersedence-snooze-toggle`.
- [ ] **CCC.7** Screen 6 — Confirm per HV-F.7 (mini-month preview with greyed-strikethrough superseded events; re-edit hop-back). Sticker: `confirm-tail-flick` + `good-boy-stays-good-boy-on-vacation` (neutral `staying-on-track`). _Compact cut renders a text summary card + reassurance line._
- [x] **CCC.8** Materialization per HV-F.8 — `TripScaffolder` writes `calendars/cal-trip-<uuidv7>/calendar.toml` with trip metadata, HV-B prep events back-filled from `lead_offset_days`, HV-C flight events when mode=flight, HV-D vacation-daily recurring rules bounded by `RRULE UNTIL`. Single atomic commit. Supersedence arrays + override files are the full-spec follow-ups (CCC.6).
- [ ] **CCC.9** Edit-in-flight + cancel-trip per HV-F.9 / HV-F.10 (rehydrate `TripDraft` from existing `cal-trip-<id>/`; diff-commit; cancel deletes the calendar dir + overrides atomically).
- [x] **CCC.10** Entry-points per HV-G — shipped: Settings → Lifestyle → "+ Plan a trip" (HV-G.1, wired via `SettingsAccess.onPlanTrip`), FAB long-press → "Plan a trip" (covers the same affordance HV-G.3 anchors around), Now-card empty-state "No plans today — want to plan a trip?" (HV-G.3, `EmptyScheduleState`). HV-G.2 calendar-detail "+ overlay from template" is the full-spec follow-up.
- [x] **CCC.11** Sticker beats per HV-H — locked-key registry in `TripStickerBeats`: `trip-suitcase-waving`, `flight-paw-prints`, `beach-loungin-with-cage-still-on` + neutral `beach-loungin`, `confirm-tail-flick` + neutral `staying-on-track`. Resolver-aware swap honours neutral-mode per D.66. HV-H.7 SVG-with-PNG-fallback assets at 64/128/256 sizes are the assets-only follow-up.
- [x] **CCC.12** Tests per HV-I.4 / HV-I.5 — `TripScaffolderTest`: back-fill math (passport-validity at T-42 produces a May event for a June 12 start), flight-day materialization (Flight emits flight events, Car does not), `RRULE UNTIL` bound to trip end, incomplete-draft rejection. `TripDraftTest`: 6 validation cases. `TripStickerBeatsTest`: every screen has a beat; neutral-variants registered. HV-I.7 Compose UI path-coverage is the follow-up.

---

## Phase DDD — Mode + identity + dom-persona

Source draft: [`draft-household-travel-vacation.md`](draft-household-travel-vacation.md) phases HV-Q (free-vs-kept + review-feed + AI-dom + migration paths + safety), HV-R (identity.toml + LW Screen 3.5 + AGENTS.md split). Cross-references: Phase YY (cross-repo feedback — review-feed builds on it), Phase OO (cross-repo state — `dom_persona_pointer`), Phase ZZ (remote-removal = revoke-dom-access), Phase K.5a (the LW Screen 3.5 insert above), Phase S.8b (Settings → Identity), [`shared-schedules.md`](shared-schedules.md) Phase SH-N + SH-O (review-feed + dom-persona pointer; were SH-K + SH-L pre-Round-4-rename), [`data-model.md`](data-model.md) DM-Y / DM-Z, [`ui-spec.md`](ui-spec.md) UI-SS..VV. Locks D.83 / D.84 / D.85 / D.86.

- [ ] **DDD.1** `mode.toml` at calendar-repo root per HV-Q.1 + D.84: `mode = "free" | "strictly-kept"` (per-repo default, per-calendar override block); optional `write_back_target`, `dom_persona`, `dom_cadence`, `kept_since`. Mode IS committed (transitions are history). See DM-Y.
- [ ] **DDD.2** Review-feed mechanic per HV-Q.2: post-commit JGit callback materializes `reviews/<commit-sha>/reviewable_change.md` (auto-summary + collapsed diff hunks + empty `responses/`). Auto-summary maps changed-path families to register-aware blurbs using `identity.toml`'s praise term. See DM-Z.
- [ ] **DDD.3** Dom responses per HV-Q.2.4: dom writes to `reviews/<commit-sha>/responses/<dom-fingerprint>-<ts>.md` IN THE DOM'S OWN REPO; cross-repo resolver (Phase YY) surfaces back to boy's per-commit feedback feed. Reaction set: `locked` / `collar` / `good-boy` / `paw` / `heart` / `fire` / `thumbsup` / `🦇` / `smirk`. Empty-text + `good-boy` reaction renders as the cute-coded LGTM. See [`shared-schedules.md`](shared-schedules.md) SH-N (review-feed; was SH-K pre-Round-4-rename).
- [ ] **DDD.4** AI-dom-persona system per HV-Q.3 + D.85: 6 shipped personas + `custom-prompt` at `~/.config/skb/dom-personas/<name>.md` (app-private, NOT in calendar repo); cadence `realtime` / `end-of-day` / `weekly` (default end-of-day); explicit-content guardrails default ON (opt-in setting + K-6 age-gate). Per-link `dom_persona_pointer` state file per Phase OO extension (SH-L).
- [ ] **DDD.5** Migration paths per HV-Q.4 (six locked flows): free→kept-by-AI, free→kept-by-human, kept-by-AI↔kept-by-human, kept→free with 24h cooling-off, kept-by-human↔self-keep, self-keep→kept-by-human. All transitions committed; git log is the authoritative record of who held the keys when.
- [ ] **DDD.6** Toxic-dom safety affordances per HV-Q.5 + D.86: boy ALWAYS retains write access; revoke-dom-read via Phase ZZ remote-removal; mode-flip cannot be blocked by dom; always-visible "transition my mode" affordance reachable from most-kept UI state (NOT buried); self-keep as fully-supported exit ramp (identity.toml carries over unchanged). See UI-TT mode-aware chrome.
- [ ] **DDD.7** `identity.toml` at calendar-repo root per HV-R.1 + D.83: `[praise]`, `[pronouns]`, `[honorific_for_dom]`, `[tone]` blocks. Locked defaults on repo creation (`praise.term = "good boy"`, he/him/his/himself, `Sir`, `soft-kinky`, `medium` emoji density). IS committed. See DM-Y.
- [ ] **DDD.8** LW Screen 3.5 — Praise + pronouns insert per HV-R.2 (registered as K.5a above). Bat sticker `bat-holding-name-tag` added to LW-K's sticker list (neutral variant `bat-with-clipboard`).
- [ ] **DDD.9** Settings → Identity surface per HV-R.3 (registered as Phase S.8b below): same fields as K.5a, "Reset to wizard defaults" button, live-preview panel (rendered now-card + template title + briefing salutation + dom-Claude response using pending values). See UI-VV.
- [ ] **DDD.10** AGENTS.md / identity.toml split per HV-R.4 + D.83: AGENTS.md carries exactly one bridge line referencing identity.toml; no praise/pronoun/register content embedded. Test asserts byte-identicality of AGENTS.md across praise-term changes (HV-O.14).
- [ ] **DDD.11** Agent integration per HV-R.5: agents (incl. dom-Claude) read `identity.toml` on session start; template-title renderer resolves `{{praise}}` with `alt_terms` alternation; briefing salutations + dom-Claude register + notification bodies all use the chosen term + emoji density.
- [ ] **DDD.12** Mode-aware chrome per HV-J.21 (UI-TT): always-visible mode pill (free / kept / self-keep) with long-press → "transition my mode" affordance; small dom-presence indicator in kept mode.
- [ ] **DDD.13** Reviews tab per HV-J.21 (UI-SS): dom-side surface listing unreviewed `reviewable_change` entries grouped by date; per-entry preview shows boy's identity + auto-summary + reaction-strip + free-text composer. Boy-side: response-on-my-commits view extends existing per-commit feed.
- [ ] **DDD.14** Dom-persona picker UI per HV-J.21 (UI-UU): settings sub-screen listing 6 shipped personas + custom-prompt with per-persona register preview + cadence control.
- [ ] **DDD.15** CLI per HV-J.19 (CLI-U): `skb mode`, `skb dom set-persona`, `skb dom respond`, `skb dom cadence`, `skb identity edit`, `skb identity preview`, `skb review list`.
- [ ] **DDD.16** Tests per HV-O.10..O.16 (mode toggle commits + no-block-by-dom; review-feed cross-repo roundtrip with reactions; AI-dom persona safety; identity.toml round-trip + alternation; AGENTS.md byte-identicality; toxic-dom safety; leisure template content).

---

## Phase S addendum — Identity + Mode surfaces

(Inline addendum to Phase S above — sub-steps S.8b + S.11 land at the same nav-level as the existing S.1–S.10.)

- [x] **S.8b** Identity section — Settings → Identity surface per HV-R.3 / DDD.9. Same fields as K.5a (praise / pronouns / honorific / tone / emoji density), "Reset to wizard defaults" button, live-preview panel rendering now-card + template title + briefing salutation with currently-pending values. `IdentityPrefs` + `IdentityCategory`. The `reviews/<sha>` write-back hook lives at the Phase YY review-feed writer.
- [x] **S.11** Mode section — Settings → Mode surface per HV-Q.1 / DDD.1 / DDD.12: mode pill + transition affordance + 24h cooling-off typed-confirmation flow (D.86, phrase: "yes I want to leave"); dom-persona picker (DDD.14, 6 builtins + custom slot); dom cadence selector (realtime/end-of-day/weekly). `ModePrefs` + `ModeCategory`.

---

## Phase EEE — Now widget + lockscreen widget

Sibling to Phase VV (countdown widget). VV pins a *future event* and counts down to it; EEE shows what you're scheduled to be doing *right now* — same widget infrastructure, different content mode. Same sticker artwork that rides the Wear notification bridge (NS-Z.9..NS-Z.14) renders on both widget surfaces, so the user can glance at phone, lockscreen, or wrist and see the same cute current-activity sticker + title + remaining time. Deep-dive `ui-spec.md` UI-WW (new) + cross-references to UI-LL (sticker resolver, Phase WW).

- [ ] **EEE.1** `NowWidgetProvider` extends `AppWidgetProvider` under `widget/now/`. Mirrors VV's `CountdownWidgetProvider` pattern (same Glance backend, same updater service). Distinct widget id `now-widget`.
- [ ] **EEE.2** Live data source: query the resolver for the active event at `Instant.now()` — same call path as `Renderer.render(today, ViewMode.Day, repoSnapshot)`, narrowed to the band whose `effectiveStart ≤ now < effectiveEnd`. Returns `ActiveEvent(MaterializedInstance, remainingMinutes)` or `NoActiveEvent`.
- [ ] **EEE.3** Glance layouts in three sizes mirroring VV's matrix:
  - **2x1** — sticker (left) + activity title (right, one line). Tap → open.
  - **4x2** — sticker + title + remaining-time chip + small "next: <next-event>" subtitle.
  - **4x4** — sticker (large, centered top) + title + remaining + next-3-upcoming list with mini-stickers per row.
- [ ] **EEE.4** Sticker rendering: pulls `Bitmap` from the Phase WW sticker resolver via `WW-StickerResolver.resolve(activity_id, species)` — same path as NS-Z.9. Fallback chain: activity-specific → category-generic → species-idle → `R.drawable.about_bat`. LRU-cached.
- [ ] **EEE.5** Refresh strategy: `AlarmManager.setRepeating` per-minute tick while a widget is placed (lightweight — just re-evaluates `now` against the cached schedule). Plus event-boundary triggers via the existing `EventReminderScheduler` (Phase M / NS-C) fan-out (post-event-end → re-render now-widget to show next event). Sub-beat boundaries also re-render so the sticker swaps on the wrist AND on the widget at the same instant.
- [ ] **EEE.6** Tap behavior: deep-link to the active event's detail screen via Phase MM URL handler (`strictlykeptboy://event/<global-id>`). Tap when `NoActiveEvent` → open Schedule pane on today.
- [ ] **EEE.7** **Lockscreen widget surface** (Android 14+ / API 34+, broader rollout API 35+):
  - Manifest entry `<receiver>` with `android.appwidget.provider` meta-data declaring `widgetCategory="keyguard|home_screen"` (per AOSP lockscreen-widget API).
  - Glance layout reuses the EEE.3 2x1 + 4x2 layouts (the 4x4 is home-only — too big for lockscreen).
  - Lockscreen widget configure-activity skipped (uses the user's primary calendar/repo by default; configurable from the home-widget settings sheet).
- [ ] **EEE.8** **Privacy contract** (K-2): if the active event has `private = true`, the widget renders the generic bat-silhouette sticker + the title "scheduled event" + the remaining-time chip — NO activity sticker, NO real title. On the lockscreen widget this is enforced harder: ANY event of a `private_by_default = true` calendar (per HV-R / DDD identity preferences) renders generic regardless of per-event flag.
- [ ] **EEE.9** Empty state: when `NoActiveEvent`, widget shows the bat-mascot sticker + the locale string `widget_now_empty_good_boy` ("good boy can rest ;3" with `neutral_title` fallback). Pulls the praise term + tone register from `identity.toml` (per HV-R).
- [ ] **EEE.10** Sub-beat awareness: when the active event has sub-beats (per Phase XX / HV-A.brush-teeth), the widget re-renders on each sub-beat boundary with the new sub-beat sticker + label. Sub-beat name shown as a subtitle on the 4x2 + 4x4 layouts.
- [ ] **EEE.11** Configure-activity for the home-widget variant: pick which calendar(s) source the active-event query, sticker-pack override per species (defers to repo-level identity.toml if not overridden), 2x1 / 4x2 / 4x4 size preview, optional "show remaining-time as exact-minutes vs. coarse-bucket" toggle.
- [ ] **EEE.12** Render-snapshot tests at: cold start (no active event → empty state), event-active mid-block (sticker + title + remaining), sub-beat-mid-event (sub-beat sticker + label), private-event (generic), lockscreen variant under private + non-private events, transitions on event-end + sub-beat-boundary.
- [ ] **EEE.13** AVD smoke: drop the now-widget on the homescreen via `adb shell appwidget` + observe the sticker + title; transition through an event boundary and assert re-render; lock the device and confirm the lockscreen widget honors the privacy contract.

---

## Phase FFF — Event-create FAB + template picker — shipped in change `round2/phase-fff-event-create`

Deep-dive: [`event-create.md`](event-create.md) phases EC-A through EC-G. The user-facing `+` FAB on every schedule surface — the single primary write path after the wizard (Phase K) seeds the repo. Two-tab sheet: **Free-form** (appointment-style: title + time + calendar + notes + optional recurrence + `private` toggle) vs **From template** (searchable picker over Phase XX / AAA atomic-activity templates, materializes with sub-beats inline). Save-as-template round-trips user-authored events back into the picker. Overlap + read-only + routine guards. Single-commit writes through `EntityWriter`; Undo deletes the just-written file. Bridges Phase XX (data model + inversion engine) and Phase G (read/render surfaces).

- [x] **FFF.1** FAB surface unification (EC-A): `EventCreateFab` composable overlaid on `SchedulePane` (every schedule view-mode shares the same FAB instance via `Box(Alignment.BottomEnd)`); hand-rolled `Surface + combinedClickable` FAB (M3 `ExtendedFloatingActionButton` swallows long-press); long-press opens 3-entry `DropdownMenu` (`New event` / `Start a routine` / `Paste an .ics URL`); v1 wires only "New event" path, the other two surface as no-op tracked menu items; last-used-tab persistence in `EventCreatePrefs` (`event_create_v1` prefs file).
- [x] **FFF.2** Free-form path (EC-B): `EventCreateFreeFormForm` with title / start / end / calendar chip-bar / notes / `private` toggle (D.57 / K-2); pure `EventDraft` + `EventDraftValidator` for inline supporting-text validation (title non-empty, end > start, duration ≤ 24h, custom RRULE non-blank); recurrence-preset chip group (Once / Daily / Weekly / Monthly / Custom + custom-RRULE field); confirm goes through `DraftToEvent.mint` → `EntityWriter.write` → `GitRepo.commitAll` via `EventCreateController`. Undo payload exposed via `lastWritten` StateFlow; recurrence-file write deferred per EC-B.4 note (caller wraps).
- [x] **FFF.3** Template picker path (EC-C): pill search (`RoundedCornerShape(28.dp)`, `surfaceContainerHigh` fill, transparent indicators) — UI-WW parity; `TemplateIndex.kt` index types + `TemplateIndexLoader.parse(assets/templates/index.toml)`; `TemplateMerger.merge` resolves USER > PACK > SHIPPED by `templateId`; `TemplateMerger.filter` applies neutral-mode (D.58) + search across displayName/tags/aliases; `TemplateMerger.group` emits ordered `Self-care / Workout / Routine / Kink / Your templates / <pack-name>` sections (section headers in `colorScheme.primary`); `ListItem` rows with hash-derived colored leading badges; `TemplateConfirmSheet` previews sub-beats read-only; `TemplateMaterializer.materialize` writes `materialized_from` + `materialized_at` + UUIDv7 id + sub-beat copy.
- [ ] **FFF.4** Save-as-template round trip (EC-D): **DEFERRED** — overflow on event detail sheet + `templates/<id>.toml` writer not yet wired. The materializer / index reader already round-trip USER-source TOML files, so the data path is in place; only the UI overflow + write-back is pending. Marked deferred so the test gate + AVD smoke loop can land first; pick this up in a follow-up.
- [x] **FFF.5** Conflict + guards (EC-E): `OverlapDetector` (pure) + `EventCreateSheet` `AlertDialog` (Schedule anyway / Pick different time / Cancel — no silent overwrite, default focus on Pick); FAB `enabled` parameter dims + ignores taps for read-only repos (caller wires); multi-repo calendar chip-bar groups via `CalendarOption.repoDisplayName`; no cross-repo writes (`EventCreateController.writeEvent` keys off the active repo only). Past-date guard left to caller (free-form already accepts any OffsetDateTime; the snackbar copy will surface "Logged in the past" once the snackbar host lands).
- [x] **FFF.6** Visual + a11y polish (EC-F): `imePadding()` on the sheet column; empty-state in picker carries inline `Free-form` `AssistChip` that flips the tab (EC-F.5); theme-respect via `MaterialTheme.colorScheme.primaryContainer` (FAB) + `surfaceContainerHigh` (pill search); section header tint = `colorScheme.primary`; focus order tracked by the natural composable order (title → start → end → repeat → calendar → notes); TalkBack via every interactive control's `testTag` + content-description.
- [x] **FFF.7** Tests + AVD smoke (EC-G): Robolectric-free pure JVM tests at `EventDraftValidatorTest` (6 cases — title, end-before-start, duration cap, missing calendar, custom RRULE, private round-trip), `TemplateIndexMergeTest` (6 cases — parse, USER overrides, neutral-mode, search, group order, pack grouping), `TemplateMaterializerTest` (5 cases — audit fields, duration math, distinct ids, title override, UUIDv7 shape), `OverlapDetectorTest` (4 cases — disjoint, touching, first-wins, adjacent). All 21 new tests pass; full suite 441 tests with the 2 pre-existing F45 failures unchanged. AVD smoke deferred (see report — `:app:assembleDebug` succeeds; emulator-5554 not booted in this worktree session).

---

## Phase GGG — Tonearmboy parity sweep (2026-05-13 session) — shipped

Reactive polish landed in a single session bringing every settings-adjacent surface to tonearmboy parity. **Convention now durable at [`ui-spec.md`](ui-spec.md) Phase UI-WW** — any future settings-adjacent surface must conform. Logged here for the per-surface ship record; convention is the load-bearing artifact, not this phase.

- [x] **GGG.1** Settings root pane — grouped cards with section headers in `colorScheme.primary`, pill search at top, colored circular leading badges per row, no left gutter, edge-to-edge. Section taxonomy: Appearance (Look and Feel), Library (Repos/Authors/Calendars/Todolists/Templates), Behaviour (Sync/Notifications/Mode), Lifestyle (Lifestyle/Identity), About. Files: `ui/settings/SettingsPane.kt`, `res/values/strings.xml` (section-header + per-row subtitle strings).
- [x] **GGG.2** App shell — rail collapses when `railItems.isEmpty()`; previously rendered an empty 52dp band on Settings / Repos / Wizard. File: `ui/scaffold/SkbAppShell.kt`. Mirrors UI-WW.4.
- [x] **GGG.3** Settings gear top-bar highlight — gear renders as `FilledTonalIconButton` when `selectedDest == TopDestination.Settings`, matching Schedule/Tasks via the shared `DestinationButton` composable. File: `ui/scaffold/SkbAppShell.kt`. Mirrors UI-WW.5.
- [x] **GGG.4** About category — rewritten with grouped cards (Build + Source). Build: App / Version-with-egg / Built date. Source: GitHub / Licenses / Privacy. `LeadingBadge` colored circles, section headers in primary. File: `ui/settings/categories/AboutCategory.kt`.
- [x] **GGG.5** Easter egg controller — framework-free 3-tap state machine with 5s window, escalating snackbar prompts (`settings_about_easter_egg_first` / `_second` / `_bat_cd`), reveal of `R.drawable.about_bat` over scrim, counter resets after reveal so the egg is repeatable. File: `ui/settings/categories/EasterEggController.kt`. Wired into the AboutCategory Version row. Convention at UI-WW.7.
- [x] **GGG.6** Look and Feel category — full rewrite with pill search + inline keyword filtering across three section cards (Theme / Display / Neutral). Helpers `SectionHeader`, `CategoryCard`, `LeadingBadge`, `PickerRow`, `ToggleRowM3`, `RowDivider`. Empty-state copy `settings_appearance_no_results`. File: `ui/settings/categories/AppearanceCategory.kt`. Mirrors UI-WW.1, UI-WW.2, UI-WW.6.
- [x] **GGG.7** Repos screen crash fix — `LazyColumn` inside `Modifier.verticalScroll` parent threw `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`. Replaced with plain `Column { repos.forEach { … } }`. File: `ui/import_export/ImportExportScreen.kt`. Anti-pattern recorded at UI-WW.8.
- [x] **GGG.8** strings.xml additions — `settings_section_*_header`, `settings_subtitle_*` (per-row subtitles), Appearance renamed to "Look and Feel", easter-egg copy strings, About card strings (`settings_about_card_build`, `_source`, `_app_label`, `_version_label`, `_github_label`), L&F search strings (`settings_appearance_search_placeholder`, `_section_theme`, `_section_display`, `_section_neutral`, `_theme_subtitle`, `_dynamic_subtitle`, `_density_subtitle`, `_font_scale_subtitle`, `_no_results`). File: `res/values/strings.xml`.
- [x] **GGG.9** Convention codified at UI-WW — pill search, grouped cards, leading badges, edge-to-edge, top-bar highlight pattern, inline keyword search, easter egg framework, anti-patterns. This is the load-bearing record; the per-surface checkboxes above are the audit trail.

---

## Notes on parallel deep-dives

Phases A through W are intentionally exhaustive but rely on the deep-dive
documents for *how*. Each deep-dive doc:

- Owns its own phase namespace (e.g. `data-model.md` uses `DM-A`, `DM-B`...).
- Cross-references back into this `main.md` via the phase letter.
- Carries its own `## Status:` header and tick-as-shipped discipline.
- Resolves any unforeseen tradeoff inline with a recommended choice — no
  punts back to the user.

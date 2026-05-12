# strictlykeptboy — main build plan

## Status: 🚧 PLANNING — phases listed below; deep-dives shipping in parallel

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
- [ ] **B.3** Implement `RepoStore` — list of configured repos in `EncryptedSharedPreferences`. `RepoConfig` carries `remotes: List<RemoteBinding>` (may be empty) and `primaryRemote: RemoteName?` per Phase ZZ.A.
- [ ] **B.4** SSH keypair generation + EncryptedSharedPreferences storage; export public key to clipboard / share-sheet. Per Phase ZZ.C, keys are keyed by `(repoId, remoteName)` — adding a second SSH remote generates a fresh keypair by default with opt-in reuse.
- [ ] **B.5** OAuth Device Flow for GitHub (token in EncryptedSharedPreferences, refresh on 401). Per-remote token binding per Phase ZZ.C.
- [ ] **B.6** OAuth Device Flow for Forgejo (same shape, different endpoints). Per-remote token binding per Phase ZZ.C.
- [ ] **B.7** Manual PAT entry path. Per-remote per Phase ZZ.C.
- [ ] **B.8** Network state monitor (`ConnectivityManager` callbacks) feeding the sync queue
- [ ] **B.9** Unit tests with Robolectric against a temp `git init --bare` filesystem repo (sidesteps network)

---

## Phase C — file store + schema

Deep-dive: [`data-model.md`](data-model.md) phases DM-A through DM-G.

- [ ] **C.1** TOML frontmatter parser/writer via ktoml; round-trip safe (preserve key ordering, comments where ktoml allows)
- [ ] **C.2** Schema definitions: `EventFile`, `TaskFile`, `RecurrenceFile`, `ExceptionFile`, `CalendarFile`, `TodolistFile`, `IdentityFile`, `RepoMeta`, `SchemaMeta`
- [ ] **C.3** UUIDv7 generator + filename builder (`events/<yyyy>/<mm>/<uuid>.md`)
- [ ] **C.4** Read-side: scan a repo path → produce typed objects
- [ ] **C.5** Write-side: serialize typed objects → file path + body
- [ ] **C.6** Schema version check + migration runner (per `decisions.md` D.20)
- [ ] **C.7** AGENTS.md + CLAUDE.md content templates for produced repos (per `data-model.md`)
- [ ] **C.8** Validation: refuse to write malformed entries; surface validation errors to UI

---

## Phase D — Room cache + indexer

- [ ] **D.1** Room entities mirroring the file types (cache only — no auth fields, FKs by ID strings)
- [ ] **D.2** Indexer: full-scan path → batch insert. Triggered on first open + on git HEAD change.
- [ ] **D.3** Incremental indexer: `git diff --name-only HEAD@{1} HEAD` → invalidate touched entries → reread.
- [ ] **D.4** Index DB versioned by `(git-HEAD, schema-version)` tuple; mismatch = rebuild.
- [ ] **D.5** Query layer: by date range, by calendar, by todolist, by author, by text-search (FTS5).
- [ ] **D.6** Benchmarks: 1000-entry repo full scan < 500ms cold, < 50ms warm.

---

## Phase E — resolver

Deep-dive: [`resolver.md`](resolver.md) phases RV-A through RV-F.

- [ ] **E.1** Active-set evaluator: given `(date, time)`, return active calendars/todolists across all configured repos.
- [ ] **E.2** Recurrence materializer: given an RRULE + a date range, emit instances. Apply exceptions.
- [ ] **E.3** Overlay layer: layer events from N calendars; resolve priority for collision; compute visual band layout.
- [ ] **E.4** Render pipeline: `(date-range, view-mode)` → `RenderedSchedule` (Compose-ready structure).
- [ ] **E.5** Common-time finder.
- [ ] **E.6** Cache layer: memoize render outputs keyed on `(repo-set-snapshot, view-params)`.

---

## Phase F — core UI scaffold

Deep-dive: [`ui-spec.md`](ui-spec.md) phases UI-A through UI-C.

- [ ] **F.1** App scaffold with `NavigationSuiteScaffold` (rail on tablets, bottom bar on phones — but we override to use rail-collapsed on phone too per user direction)
- [ ] **F.2** Top app bar with repo switcher (left) + view tabs (center) + sync button + identity icon (right)
- [ ] **F.3** Material3 Expressive theme + dynamic color + light/dark/auto + density toggle
- [ ] **F.4** Schedule day view (the first real view) wired to resolver
- [ ] **F.5** Empty state with mascot placeholder

---

## Phase G — schedule views

Deep-dive: [`ui-spec.md`](ui-spec.md) phases UI-D through UI-G.

- [ ] **G.1** Day view — vertical timeline, hour grid, overlap bands, current-time indicator
- [ ] **G.2** Week view — 7-column timeline
- [ ] **G.3** Month view — month grid with event chips
- [ ] **G.4** Timebox-mode view — today's planned blocks edge-to-edge, big targets
- [ ] **G.5** Year view — 12-month grid with heat-map density
- [ ] **G.6** View-mode tabs in top bar with persisted last-view per device
- [ ] **G.7** Event detail sheet — slide-up sheet with full event content + author chip + attachments + edit button

---

## Phase H — task views

Deep-dive: [`ui-spec.md`](ui-spec.md) phases UI-H through UI-J.

- [ ] **H.1** Combined view (all active lists, priority-ordered)
- [ ] **H.2** Today view (today's dated tasks + spawned + pinned standing)
- [ ] **H.3** Per-list view (filter to one list)
- [ ] **H.4** Shopping mode (big checkboxes, simple layout)
- [ ] **H.5** Standing view (no-deadline only)
- [ ] **H.6** Task detail sheet (notes, attachments, author chip, edit)
- [ ] **H.7** Quick-add FAB with calendar/todolist target picker

---

## Phase I — repo management UI

Deep-dive: [`ui-spec.md`](ui-spec.md) phases UI-K through UI-L.

- [ ] **I.1** Repo switcher (top-bar) — filterable list with circular icons + display names + sync status badges. No-origin repos render a small house "local" badge instead of sync/error per Phase ZZ.G.
- [ ] **I.2** Add-repo flow — top-level branch selector "Create local-only" vs "Connect to a remote" per Phase ZZ.G. Local-only path skips provider/URL/auth and goes straight to identity + default-calendar selection. Remote path: provider (GitHub/Forgejo), URL, auth method, identity, default calendar; supports adding additional remotes via "+ add another remote".
- [ ] **I.3** Repo settings — display name, icon, auto-sync toggle + interval, default identity, default calendar, default todolist, color seed. Includes a **Remotes** section per Phase ZZ.G: when `remotes.isEmpty()` shows "No remotes — this repo lives only on this device" + "Add a remote" CTA; when non-empty lists per-remote rows (URL, transport, auth method, push policy, last-sync, error) with Edit/Remove/+Add affordances.
- [ ] **I.4** Identity management within repo (`identities/` editor)
- [ ] **I.5** Remove repo (with "are you sure" + local-clone retention option)
- [ ] **I.6** All-repos unified-view master toggle

---

## Phase J — sync orchestration

Deep-dive: [`sync-engine.md`](sync-engine.md) phases SE-G through SE-L.

- [ ] **J.1** Foreground service `SyncService` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. Per Phase ZZ.H, no foreground work is scheduled for repos where `remotes.isEmpty()`.
- [ ] **J.2** Sync scheduler — per-repo interval + on-app-foreground + on-connectivity-restored. Per Phase ZZ.D, scheduler iterates every fetch-enabled remote per repo and collects per-remote results.
- [ ] **J.3** Manual sync button → triggers sync-all-flagged. Hidden in simplified mode when active repo is no-origin per Phase ZZ.G.
- [ ] **J.4** Sync status persisted per-repo: `last_synced_at`, `commits_ahead`, `commits_behind`, `last_error`. Per-remote status surfaced separately for multi-origin repos per Phase ZZ.D/E.
- [ ] **J.5** Conflict detection + 3-way diff UI for conflicted files. Conflict labels now carry the primary remote's display label per Phase ZZ.F; diamond-merge mini-flow (adopt-mirror-as-authoritative) reuses the same UI with the mirror's label.
- [ ] **J.6** Conflict resolution UI: structured-field editor for TOML frontmatter, text editor for body, keep-mine / keep-theirs / merge buttons. Non-primary divergence surfaces as a per-remote yellow banner (`MirrorDivergence`), NOT in this conflict UI, per Phase ZZ.D.
- [ ] **J.7** Read-only repo handling — push refused → queue, show banner. Per-remote `readOnlyDetected` per Phase ZZ.E; repo-level red banner only when every remote is read-only OR the repo is no-origin with no add-remote CTA offered.
- [ ] **J.8** Sync result toasts + last-sync time in top bar

---

## Phase K — design-a-lifestyle wizard (REPLACED — was wizard+templates)

Deep-dive: [`draft-lifestyle-wizard.md`](draft-lifestyle-wizard.md) phases LW-A through LW-M. (Replaces the previous K.1..K.6 wizard+templates phase wholesale per D.54. The wizard's output IS the user's canonical starting state; no demo mode. The bat-mascot guides every wizard screen and is distinct from the user's chosen avatar species per D.56. Kink-positive openly per D.55; `unaligned-private` alignment is the in-wizard kink-off path. Phone-only is a first-class wizard outcome via `GitRepo.initLocalOnly` per ZZ.A.)

- [ ] **K.1** Wizard architecture + state model (LW-A): NavHost rooted at `WizardNavHost`, immutable `WizardDraft`, commit-on-finish, mid-wizard exit safety, bat-mascot `WizardScaffold` host
- [ ] **K.2** Screen 1 Welcome (LW-B): bat-mascot wave, single "let's go" CTA
- [ ] **K.3** Screen 2 Species selection (LW-C): 8-tile grid (7 default species + "choose your own"); bat default if skipped
- [ ] **K.4** Screen 3 Alignment (LW-D): Dominant / Submissive / Switch / Unaligned-private; unaligned-private propagates kink-off downstream
- [ ] **K.5** Screen 4 Lifestyle (LW-E): Single/Partnered × free/strictly-kept/strictly-keeping/strictly-shared, computed from alignment
- [ ] **K.6** Screen 5 Roles (LW-F): 17-role multi-select grid; `self-care` always-on; `kink` auto-on for kinky alignments and hidden under unaligned-private
- [ ] **K.7** Screen 6 Templates per role (LW-G): collapsible sections of atomic-activity templates (from XX) with smart-default toggle matrix per (alignment, lifestyle)
- [ ] **K.8** Screen 7 Git setup (LW-H): three cards — Phone-only (default-highlighted) / Self-hosted Forgejo-Gitea / GitHub
- [ ] **K.9** Screen 8 Calendar scaffolding (LW-I): materialize repo, calendars, recurrences, todolist, identity, README+AGENTS+CLAUDE; single initial commit; push if remote configured
- [ ] **K.10** Screen 9 Done handoff (LW-J): live home-screen preview now-card; bat-mascot waves chosen species peek; "open my calendar" CTA
- [ ] **K.11** Bat-mascot sticker set spec (LW-K): 13-key sticker set; contact sheet for artist at `docs/assets/wizard-bat-stickers.png`
- [ ] **K.12** Re-run from Settings (LW-L): "Add more to my lifestyle" entry; additive semantics — toggling a role OFF hides rather than deletes
- [ ] **K.13** Testing strategy (LW-M): Robolectric path-coverage matrix per (alignment × lifestyle), mid-wizard exit+resume, deep-link bypass, auth-failure recoverability
- [ ] **K.14** Age gate + neutral-mode toggle (K-6 / K-3 per KI): one-time first-launch age gate (Mature-17+), encrypted-prefs `age_confirmed_at`; Settings → Appearance → Neutral mode toggle. Composes with wizard `unaligned-private` alignment.

---

## ~~Phase L — demo content~~ (RETIRED)

~~Deep-dive: [`templates-demo-wizard.md`](templates-demo-wizard.md) phases TW-G through TW-H.~~

**RETIRED** — superseded by Phase K (lifestyle wizard); demo content concept retired in favor of materialized starting state per D.54. See [`draft-lifestyle-wizard.md`](draft-lifestyle-wizard.md). The wizard's scaffold output IS the user's canonical first-run data, not seed/sample/demo data. `demo-sub` / `demo-dom` repo seeds are not shipped.

- [ ] ~~**L.1** `demo-sub` repo seed — full SFW-kinky-coded sub schedule + tasks~~
- [ ] ~~**L.2** `demo-dom` repo seed — Dom's calendar with check-in events that overlay~~
- [ ] ~~**L.3** Demo mode flag: spins up local clones (no remote push) with both repos preloaded~~
- [ ] ~~**L.4** "Exit demo mode" path: option to keep demo data as a real local repo or discard~~

---

## Phase M — notifications

Deep-dive: [`notifications-sharing-import.md`](notifications-sharing-import.md) phases NS-A through NS-D.

- [ ] **M.1** Notification channels: events, tasks, sync, errors
- [ ] **M.2** AlarmManager scheduling per event lead-time
- [ ] **M.3** Notification groups in settings — per-calendar membership, group-level toggles
- [ ] **M.4** Sync status notification (silent default)
- [ ] **M.5** Foreground service notification (low-importance, persistent)
- [ ] **M.6** Snooze + dismiss + open-event actions

---

## Phase N — common-time finder

Deep-dive: [`resolver.md`](resolver.md) phase RV-E + [`ui-spec.md`](ui-spec.md) phase UI-M.

- [ ] **N.1** Together-tab UI: select repos, calendars, date range, duration, day/time filters
- [ ] **N.2** Run finder → ranked free-slot list
- [ ] **N.3** Tap slot → quick-create event (target repo + calendar picker)

---

## Phase O — sharing + read-only

Deep-dive: [`notifications-sharing-import.md`](notifications-sharing-import.md) phase NS-E.

- [ ] **O.1** "Share read access" deep links to provider collaborator screen
- [ ] **O.2** Detect push-rejected on token-without-push → flag repo read-only, show banner
- [ ] **O.3** Author-attribution chip rendering everywhere events/tasks appear

---

## Phase P — import / export

Deep-dive: [`notifications-sharing-import.md`](notifications-sharing-import.md) phase NS-F.

- [ ] **P.1** iCal (.ics) export per calendar / per repo / per date-range
- [ ] **P.2** iCal (.ics) import into chosen calendar (UID-keyed de-duplication on re-import)
- [ ] **P.3** CSV export for tasks
- [ ] **P.4** iCal export for tasks (VTODO) — bonus
- [ ] **P.5** `tools/` directory with shell scripts for server-side CalDAV/Thunderbird/Outlook hooks

---

## Phase Q — Android Auto

Deep-dive: [`ui-spec.md`](ui-spec.md) phase UI-N.

- [ ] **Q.1** `CarAppService` skeleton
- [ ] **Q.2** Today list template
- [ ] **Q.3** "Next up" pane template
- [ ] **Q.4** Voice prompts ("what's next?")
- [ ] **Q.5** Read-only enforcement

---

## Phase R — tablet + master-detail

Deep-dive: [`ui-spec.md`](ui-spec.md) phase UI-O.

- [ ] **R.1** WindowSizeClass detection
- [ ] **R.2** Schedule master-detail (calendar + day detail pane)
- [ ] **R.3** Tasks master-detail (list + task detail pane)
- [ ] **R.4** Settings master-detail
- [ ] **R.5** Tablet-specific touch targets + spacing

---

## Phase S — settings polish

- [ ] **S.1** Repos section
- [ ] **S.2** Identities section
- [ ] **S.3** Sync section (intervals, push strategy, retry, conflict prefs)
- [ ] **S.4** Notifications section (channels, groups, defaults)
- [ ] **S.5** Calendars section (master toggles, priority editor, active-windows editor)
- [ ] **S.6** Todolists section (same shape as Calendars)
- [ ] **S.7** Templates section (browse + apply + custom template repo URL)
- [ ] **S.8** Lifestyle section — "Add more to my lifestyle" entry-point per Phase K.12 (LW-L). (Replaces the retired Demo section per D.54.)
- [ ] **S.9** Appearance section (theme, density, font, dynamic color override)
- [ ] **S.10** About section (build info, license screen, mascot, repo link)

---

## Phase T — theming + personalization

- [x] **T.1** Launcher icon + mascot art shipped — bat-with-calendar adaptive launcher (`mipmap-*/ic_launcher.webp` + `drawable-*/ic_launcher_foreground.webp`, dark background `#0A0806`); secret about-page mascot scene at `drawable-nodpi/about_bat.webp`. Shipped in change `7afb686+`.
- [ ] **T.2** Repo icon system: emoji-in-SVG-wrapper, photo, auto-initials. Default repo icon defaults to the user's chosen avatar species (per Phase K.3 / Phase WW) unless explicitly overridden.
- [ ] **T.3** Calendar/todolist icon + color picker
- [ ] **T.4** Event emoji prefix UI
- [ ] **T.5** Dynamic color override per repo (color seed)
- [ ] **T.6** Themed-icon monochrome layer (Android 13+) — generate silhouette of the bat-with-calendar for `<monochrome>` adaptive-icon slot; currently omitted so themed icons fall back to the system default
- [ ] **T.7** Secret About scene unlock — tap the version number 7 times in `Settings → About` to reveal `about_bat.webp` full-screen with the `STRICTLYKEPTBOY — schedule dreams. keep promises.` tagline rendered below. Easter-egg only; never linked from main UI

---

## Phase U — accessibility + i18n scaffolding

- [ ] **U.1** TalkBack labels on every interactive element
- [ ] **U.2** Content descriptions for icons (repo switcher, sync, identity)
- [ ] **U.3** Large-text + high-contrast verified at 200% scale
- [ ] **U.4** `strings.xml` discipline (no hardcoded user-facing strings) — mirror tonearmboy's stringResource discipline
- [ ] **U.5** Locale wiring (English-only at v1; structure ready for additions)

---

## Phase V — performance pass

- [ ] **V.1** Cold start budget: < 600ms to schedule day view
- [ ] **V.2** Sync small repo: < 2s
- [ ] **V.3** Render month with 200 events: < 200ms
- [ ] **V.4** Common-time finder over 5 repos × 30 days: < 800ms
- [ ] **V.5** Memory: < 150MB resident at steady state

---

## Phase W — release engineering

- [ ] **W.1** Signing config + release keystore (user-supplied, gitignored)
- [ ] **W.2** GitHub Actions release workflow → APK with `strictlykeptboy-<version>-<sha7>.apk` naming (mirror tonearmboy)
- [ ] **W.3** Obtainium-compatible release notes + SHA-256 table
- [ ] **W.4** Play Store listing copy (Mature 17+ Lifestyle category per D.59 / K-4). Leads with unique value prop — git-backed, atomic, AI-native, multi-repo, common-time finder; lifestyle / D/s positioning mentioned in paragraph 2-3, not lead-with.
- [ ] **W.5** Play Store screenshots set (per D.59 / K-4): show both neutral-mode AND kink-mode versions as peer examples (4 of each); polished schedule views with the user-controlled wizard scaffold output.
- [ ] **W.6** ProGuard/R8 keep rules for JGit + lib-recur + ktoml + reflection-using libs
- [ ] **W.7** Privacy policy page
- [ ] **W.8** First release `v0.1.0`

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

## Phase X — `skb` CLI primary interface

Deep-dive: [`cli-tooling.md`](cli-tooling.md) phases CLI-A through CLI-K.

Scaffold shipped (X.2, X.3, partial X.5) — Clikt + fat-jar + POSIX wrapper smoke-tested on `--version` (human + JSON).

- [ ] **X.1** Define subcommand surface (D.24) — stub `event/task/cal/repo` dispatch present; real surfaces ship in their CLI-A.* phases
- [x] **X.2** Implement JVM-jar entrypoint (`:cli` Gradle subproject, `fatJar` task → `cli/build/libs/skb-cli-<ver>-<sha>.jar`)
- [x] **X.3** Shell-script wrapper for distribution (`tools/skb` POSIX shell launcher; finds java 17+, dev-fallback to repo build dir)
- [ ] **X.4** Atomic write + auto-commit hooks (same commit-message format as GUI)
- [x] **X.5** `--json` machine-readable output mode for AI consumers — root flag + envelope shipped for `--version`; per-subcommand JSON lands with each CLI-A.* phase
- [ ] **X.6** `--dry-run` flag (prints proposed change, no write)
- [ ] **X.7** Exit-code taxonomy (0=ok, 1=usage, 2=not-found, 3=conflict, 4=auth, 5=corrupt, 6=schema-mismatch)
- [ ] **X.8** Bundle in release pipeline alongside APK (GitHub Releases asset; homebrew tap `887/tap/skb`; `curl ... | sh` one-liner)
- [ ] **X.9** AGENTS.md content references CLI as primary path (rewrite per DM-K of data-model.md extensions)
- [ ] **X.10** Help system (`skb help <command>`, contextual examples, machine-readable `skb help --json` for AI agents)
- [ ] **X.11** Repo discovery: walk-up to find `.strictlykeptboy/` from CWD; `--repo <path>` override; `SKB_REPO` env var
- [ ] **X.12** Test suite — pure JVM, Robolectric-free

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

## Phase WW — Avatar + sticker presence-indicator system

Deep-dive: [`draft-avatar-stickers.md`](draft-avatar-stickers.md) phases AV-A through AV-I. Locks per D.63..D.69. Avatar = presence indicator, not tamagotchi; no HP/mood/hunger. Optional opt-in count-only streak counter per activity. Kink-positive openly with neutral-mode tag filter. Default species roster: bat, fox, tiger, lion, wolf, bunny, cat — bat is the app default + wizard guide species (but the bat-mascot character per D.56 is distinct from the bat-avatar).

- [ ] **WW.1** Pack format + default bat pack (AV-A + AV-G): WebP @ 512×512, `pack.toml` manifest, `<species>/<activity-id>.webp` layout. Bundled default packs ship in APK assets. Tag taxonomy locked at `neutral`, `kink`, `hygiene`, `workout`, `meal`, `work`, `study`, `posture`, `rest`, `idle`. Build-time validator asserts neutral-set + kink-set + sub-beat IDs present in every default pack.
- [ ] **WW.2** Resolver + now-card (AV-B + AV-C): `StickerResolver` pure function with 6-rung chain (per-event override → sub-beat → activity-specific → category-generic → species-idle → bat-fallback) per D.66. Neutral-mode filter at activity-specific and category-generic rungs. NowCard component at top of `ScheduleShell` (220 dp phone / 260 dp tablet) hosting sticker zone + title zone + next-up strip + optional streak chip. Tap targets per AV-C.5; recompose on 30s tick foreground / 60s notification-foreground.
- [ ] **WW.3** Sub-beat support (AV-D): additive optional `[[subbeat]]` array on event frontmatter (`label`, `duration_seconds`, optional `sticker_id`). Sub-beat clock + smooth 200ms cross-fade. CLI `skb event add --subbeat "<label>:<seconds>"`. (Aligned with Phase XX.I sub-beat schema + buzz pattern.)
- [ ] **WW.4** "Choose your own" species flow (AV-E): clone GitHub repo of stickers to `<app-private>/avatar-packs/<pack-id>/` (pack-id = SHA-256 of normalized clone URL, 16 hex chars). Shallow `--depth=1` clone, validate `pack.toml`, require `activity_id = "idle"`. Auth reuses Phase B credential machinery as a read-only data source. Per D.69, canonical template at `https://github.com/eight87/strictlykeptboy-sticker-pack-template` (overridable in Settings → Advanced).
- [ ] **WW.5** Customization (AV-F): per-activity sticker override UI; storage in app-private `EncryptedSharedPreferences` keyed `avatar.overrides.<activity_id> → <pack-id>:<sticker-activity-id>` — **NOT** committed to user data repo per D.67 (device aesthetic, not life-data). Per-event override IS committed (additive frontmatter field). Export to JSON in Downloads; import deferred to v1.1.
- [ ] **WW.6** Rendering pipeline + cache (AV-H): Coil 2.x single shared `ImageLoader`, LRU ~40 stickers (~20MB), preload next-3 events. Animated WebP via `ImageDecoderDecoder`; respect system "Remove animations" → first-frame fallback. Now-card visible-with-sticker budget < 300ms of schedule first-frame.
- [ ] **WW.7** Snapshot test suite (AV-I): Roborazzi screenshot harness covering cold-start-idle / event-active / sub-beat-mid-event / missing-pack-fallback / bat-fallback / neutral-mode-active / streak-chip-on / TalkBack labels. `FakeClock` injection via DI module (Phase A.8). Pure-JVM resolver unit tests + pack-validator tests + Gradle test for AV-G.5 build-time validator.

---

## Phase XX — Atomic activities, inverted habits, routines

Deep-dive: [`draft-atomic-activities.md`](draft-atomic-activities.md) phases AT-A through AT-K. Locks per D.70. Default state for any past-or-current scheduled event = `completed-by-schedule`; deviation is the explicit action. No guilt loop. Atomic = one entity per activity; routines are calendar overlays, not entities. Sub-beats inline in event TOML, bounded depth. Kink-positive openly with `kink` tag for neutral-mode filtering. Optional count-only streak counter per event-or-rule, no escalation.

- [ ] **XX.1** Deviation file schema (AT-A): new directory `deviations/<calendar-id>/<event-id-or-rule-id>/<yyyy-mm-dd>.md` SEPARATE from `exceptions/` (exceptions = scheduling changed; deviations = scheduling held but reality differed). TOML frontmatter `kind = "skipped" | "partial" | "completed-early" | "completed-late"`, `at`, `author`, optional `note`, optional `subbeats_completed`. No `kind = "completed"` — that's the default-by-schedule state. CLI `skb deviation set --event ... --date ... --kind ...`.
- [ ] **XX.2** Resolver integration with inverted default (AT-B): `resolveCompletionState(event, now)` in Phase E render pipeline. Algorithm: future → scheduled; in-window no-deviation → in-progress; past no-deviation → `completed-by-schedule`; deviation present → that kind. Exception cancels shadow deviation. Room column `completion_state` with cache invalidation per AT-B.4. Visual treatment per AT-B.5 (never red, never warning glyphs, never blinking).
- [ ] **XX.3** Notification + buzz pattern (AT-C): `events-atomic` notification channel; `AlarmManager` start + end alarms with `setExactAndAllowWhileIdle`. Title = user-authored verbatim; body empty by default. Actions: `I did it` (no-op — inverted default already wins; does NOT write completion file), `I didn't` → write `skipped` deviation, `Partial` → activity sheet → write `partial`, `Remind in 10/30/60 min`. End-alarm flips Room cache `in-progress → completed-by-schedule`. Permissions: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`. Doze whitelist opt-in only.
- [ ] **XX.4** Atomic self-care template (AT-D): `templates/atomic-self-care.toml` — brush-teeth (with 7 sub-beats: 6 quadrants + tongue/wrapup totalling 200s in the 5-min envelope), shower, shave-face, shave-pubes (`tags = ["intimate-care"]` neutral hygiene), hair, skincare, deodorant, nail-care, ear-clean. `neutral_safe = true`.
- [ ] **XX.5** Atomic kink-self-care template (AT-E): `templates/atomic-kink-self-care.toml` with `neutral_safe = false` — cage-check, plug-check, posture-check (hourly-waking), collar-check, edge-and-stop, kegels (with 3 sub-beats), pubes-grooming, body-grooming. All entries `tags = ["kink"]` so neutral-mode filters them out.
- [ ] **XX.6** Atomic workout template (AT-F): `templates/atomic-workout.toml` — pushups, situps, squats, pull-ups, planks (hold-time instead of reps), burpees. Each entry carries `sets` + `reps` (or `hold_seconds`). Sub-beat = one per set; phone buzzes between sets.
- [ ] **XX.7** Routines as calendar overlays (AT-G): additive `calendar.toml` fields `routine = true`, `routine_id`, `routine_default_start`, `routine_can_materialize`. Routine calendars default `active_toggle = false` and group at the bottom of the calendar list with a `quick-start` glyph. Wizard scaffolds `routine-morning` / `routine-bed` / `routine-workout` when relevant role-toggles selected.
- [ ] **XX.8** "Start X routine" quick-start (AT-H): FAB bottom-sheet → routine picker → config sheet (start-time, target calendar, per-item checklist). Materialization walks atomics forward back-to-back from start-time using each entry's `duration_minutes`. Adds frontmatter `materialized_from`, `materialized_source_event`, `materialized_at` for audit/undo. Overlap guard surfaces conflict sheet. Single commit `materialize routine "<name>" at <start-time>`. CLI `skb routine start ...` + `skb routine undo <materialized-at>`.
- [ ] **XX.9** Sub-beat schema + buzz pattern (AT-I): event-file additive `[[subbeat]]` array per WW.3. Boundary alarms via `setExactAndAllowWhileIdle` at `event.start + sum(prefix)` for each sub-beat. Single vibration, low-priority notification with title = sub-beat label, body = `<i>/<N>`, single action `Skip ahead` → writes `partial` deviation with `subbeats_completed`. Cap 16 sub-beats per event.
- [ ] **XX.10** Streak counter (AT-J, count-only): per-event-or-rule `streak = consecutive_days_with_no_skip_deviation`. `partial` / `completed-early` / `completed-late` do NOT break the streak; only `skipped` does. Room column `streak_count` recomputed on file change / deviation add-remove / midnight tick. UI: tiny rounded badge, no flame / no trophy / no escalation tier, hidden when `< 2`. Settings → Notifications → "Show streak counts" global toggle (default ON). CLI `skb streak <id>`.
- [ ] **XX.11** Test fixtures (AT-K): Robolectric tests for inversion state transitions, sub-beat boundary timing, routine materialization (sequential + overlap), streak count, neutral-mode filter on kink template application, end-alarm state-flip. Also includes the Phase M notification subhook `M.7 atomic-events channel + sub-beat boundary alarms wired from Phase XX`.

---

## Phase YY — Cross-repo global-ID feedback

Deep-dive: [`draft-global-id-feedback.md`](draft-global-id-feedback.md) phases FB-A through FB-J. Locks per D.71..D.73. Cross-repo dom→sub feedback (hearts, fire, locked, collar, good-boy), comments, bonus tasks, journal entries — all file-based, committable, pushable, mergeable. Global ID format = `<repo-fingerprint>:<entity-uuid>` where `repo-fingerprint = SHA-256(first-commit's tree SHA)[:16]`. Device-local repo registry with per-direction (asymmetric) isolation. Feedback files live in the **feedbacker's** repo at `feedback/<target-fingerprint>/<target-entity-uuid>/<feedback-uuid>.md`. Opaque-string reaction tokens; sticker packs re-skin.

- [ ] **YY.1** Repo fingerprint derivation + cache (FB-A): `RepoFingerprint.computeOrLoad` reads `.strictlykeptboy/repo-fingerprint` if present, else computes from `git log --reverse --max-count=1 --format=%T` (tree SHA of root commit), SHA-256, 16-hex truncation. Cache file is **gitignored, never committed** (added to scaffold `.gitignore` by Phase K wizard). Stable across rebase/squash of non-root commits; breaks only on root rewrite (rebind UI in FB-A.4 walks `feedback/<old-fp>/` and rewrites paths). CLI `skb repo fingerprint`.
- [ ] **YY.2** Repo registry (FB-B): `<app-data>/repo-registry.toml` device-local (NEVER synced); auto-registers on every successful repo open. Per-repo `isolate_from` field (asymmetric — each repo controls only what *it* refuses to see). `RepoRegistry.allVisibleTo(viewerFp)` is the single chokepoint for cross-repo lookups (custom lint rule flags reads of `repo-registry.toml` outside `RepoRegistry`). Settings UI per FB-B.4 with explicit directional clarity copy. CLI `skb repo registry list|isolate|unisolate`.
- [ ] **YY.3** Feedback file schema + writer (FB-C): one file per (author, target, reply_to). Atomic write + auto-commit. Validation refuses ill-formed targets, duplicate reactions, no-op empty feedback, cross-repo replies. Indexer scans `feedback/**` in every registered repo at startup + on git HEAD change → `FeedbackEntry` Room table. AGENTS.md documents the feedback file format + `skb react` / `skb comment` as primary path.
- [ ] **YY.4** `skb react` / `skb comment` CLI (FB-D): `skb react add --target <global-id> --reactions heart,fire,locked [--body "..."] [--reply-to <feedback-uuid>]`, `skb react remove`, `skb react list`, `skb comment add|list`. Target resolution helpers `--target-event <uuid>` resolve to `<this-repo-fp>:<uuid>`. Exit codes per D.24. `--dry-run` supported.
- [ ] **YY.5** Cross-repo resolver extension (FB-E): `FeedbackResolver.aggregate(targetGlobalId)` queries `FeedbackEntry` across every repo in `RepoRegistry.allVisibleTo(<viewer-fp>)`. Threading by `reply_to`, linear `created` order within threads. Memoized on `(targetGlobalId, set-of-visible-repo-HEADs)`, invalidated on HEAD change. Feedback drawer UI section in event/task detail sheets with reaction tallies + comment thread + always-present "+ react" affordance. Author chips show **receiving** repo's local label for the source repo (privacy-preserving).
- [ ] **YY.6** Isolation enforcement + privacy tests (FB-F): aggregation **count masking is forbidden** — isolated sources are structurally invisible, not "N hidden". Notification suppression on isolation. Search/autocomplete masking. Robolectric privacy tests including snapshot-grep-for-fingerprint-strings.
- [ ] **YY.7** Bonus tasks + sub-logged extras + journal entries (FB-G): `bonus/<task-uuid>.md` at calendar root in a shared repo; `bonus = true` overrides scheduling pressure (never promoted to dated-tasks even with `due`). Completion writes back; degrades gracefully to state-file (`state/<dom-repo-fp>/<task-uuid>.done.toml`) when shared repo unavailable. Deep-link offer `strictlykeptboy://bonus?...` for dom→sub assignment. Journal entries at `journal/<yyyy-mm-dd>.md` (or `<yyyy-mm-dd>-<n>.md`) — UUIDv7 in frontmatter; NOT in schedule overlay; dedicated Journal nav-rail tab (Schedule/Tasks/Journal/Together/Settings). Journal entries reactable/commentable via the same FB-C/D/E machinery.
- [ ] **YY.8** `references.toml` + share-flow extensions (FB-H): `write_back_target = "<repo-fingerprint>"` field per reference signals "this referenced repo is willing to receive feedback files we write addressed to its entities". UI only shows "+ react" affordance for references with `write_back_target` set. Share-this-repo (Phase RR) checkbox "Allow this share's recipient to leave feedback on my entries" auto-writes the `write_back_target` line into the recipient's `references.toml` on accept. CLI `skb ref set-write-back`.
- [ ] **YY.9** Test fixtures + Robolectric end-to-end (FB-I): two-repo fixture builder (`fixture-sub` + `fixture-dom`); end-to-end tests for heart-cross-repo, isolation-hides-heart, reply-thread, bonus-task-round-trip (with shared-repo-unavailable variant), journal-reaction, root-rewrite-rebind. Performance budget: aggregating feedback for one event across 5 repos × ~100 files each < 50ms warm.
- [ ] **YY.10** Documentation + AGENTS.md rewrite (FB-J): AGENTS.md feedback-format documentation as primary path; in-repo README.md privacy note; Settings → About → "How feedback works" link; `cli-tooling.md` full reference.

---

## Phase ZZ — Git layer no-origin + multi-origin extensions

Deep-dive: [`draft-no-origin-multi-origin.md`](draft-no-origin-multi-origin.md) phases MO-A through MO-H. Lock per D.74. Two orthogonal extensions to the Git infrastructure: **no-origin** repos (git-backed with zero remotes, first-class state, not degraded mode) and **multi-origin** sync (N ≥ 2 remotes with fan-out push policy, fan-in fetch+merge semantics, per-remote auth bindings). Touches Phase B / Phase I / Phase J / Phase NN / Phase PP / Phase QQ / Phase K inline (per the integration edits already applied above) plus all of sync-engine.md SE-B/C/D/E/F/I/J/K/L.

- [ ] **ZZ.A** No-origin data model + GitRepo surface (MO-A): `RepoConfig` carries `remotes: List<RemoteBinding>` (may be empty) and `primaryRemote: RemoteName?` (null iff `remotes.isEmpty()`). `RemoteBinding` carries `name`, `url`, `transport`, `authMethod`, `fetchEnabled`, `pushEnabled`, `readOnlyDetected`. `repoId` becomes UUIDv7 persisted at `.strictlykeptboy/repo-id` (one-line) **— LOCKED committed to the repo** for cross-device state-file consistency per Phase OO. `GitRepo.initLocalOnly(rootDir, authorIdentity)` for no-origin construction. Fetch/pullRebase/push accept `remote: RemoteName? = primaryRemote` and return `NoRemotes` variants when `remotes.isEmpty()`. `GitStatus.localOnlyCommits` field. `SecretsStore` re-keyed from `<kind>.<repoId>` to `<kind>.<repoId>.<remoteName>` with one-shot migration (existing keys rewritten with `remoteName = "origin"`).
- [ ] **ZZ.B** Multi-origin: remote naming + management API (MO-B): **named-by-purpose** with `origin` as conventional primary name (kept for stock-git interop), additional remotes default to `mirror-<n>` with user-editable `displayName`. Policy carried in `RemoteBinding` fields, never semantic on name. `GitRepo.addRemote / removeRemote / renameRemote / listRemotes`; reserved-name guard. Per-remote `fetchRefspec` field (default `+refs/heads/*:refs/remotes/<name>/*`). `references.toml` extended to allow `remotes = ["url1", "url2"]` array alongside backwards-compat `url = "..."` singular (per NN.1).
- [ ] **ZZ.C** Multi-origin per-remote auth binding (MO-C): `CredentialBinding` resolved by `(repoId, remoteName)` tuple. SSH keypair generation per-remote — adding a second SSH remote generates a new keypair by default with opt-in reuse from another remote. Per-remote auth method UI in add-remote flow / repo settings. `transportConfigCallbackFor(repoId, remoteName)`. Re-auth error surfaces gain remote display label.
- [ ] **ZZ.D** Multi-origin fetch + reconcile algorithm (MO-D): fetch pass iterates `remotes` where `fetchEnabled`, collecting `Map<RemoteName, FetchResult>` (per-remote network errors don't abort others). **Reconcile policy**: local branch rebased onto `<primaryRemote>/<branch>` ONLY. Non-primary remotes' tips become `MirrorDivergence(remoteName, count)` non-fatal yellow-banner signals when they have commits we don't. Manual diamond-merge UI offers (a) adopt mirror as authoritative, (b) override mirror via force-push (Settings → Advanced gated, typed confirmation). v1 ships (a); (b) gated.
- [ ] **ZZ.E** Multi-origin push fan-out policy (MO-E): per-remote `pushPolicy: PUSH | PUSH_LAZY | NEVER`. Defaults: first remote added → `PUSH`, additional → `PUSH_LAZY`. Push pass iterates `[primary, then non-primary by add-time]`. Partial failure: if primary succeeds but mirror fails → local commit considered shipped, mirror enters per-remote retry queue (WorkManager exponential backoff per SE-L), `PartialPushDegraded` signal (small dot, not red banner). If primary fails → block subsequent pushes; do NOT push to non-primaries (avoid mirror getting ahead of canonical). Per-remote `readOnlyDetected`; repo-level red banner only when every remote is read-only.
- [ ] **ZZ.F** Multi-origin conflict UI for N-way diamonds (MO-F): SE-J 3-way diff UI only fires for local-vs-primary conflicts (non-primary divergence stays in the banner UI). Conflict UI "Remote" label carries primary's display label. Diamond-merge mini-flow reuses the same UI with the mirror's label. Force-push affordance lives in repo settings → Remotes → `<remote>` → Advanced with typed-URL confirmation. **No force-push to primary, ever, in v1.**
- [ ] **ZZ.G** No-origin UI representation + grow-into-remote path (MO-G): per the inline edits at I.2 / I.3 / PP.2 / PP.6 / QQ.1 / K.8 above. Add-repo flow gets a "Create local-only" peer option (not buried under advanced). No-origin repo switcher gets a small house "local" badge. Repo settings Remotes section with first-class no-origin treatment. Add-remote-later path: `git remote add origin <url>` + `git push -u origin main` against the extant local history (no history rewrite). First-launch no-deep-link path: 2-option splash "Start a local calendar (you can sync it later)" vs "Connect to a git remote now". Wizard (Phase K) repo-picker + auth steps become skippable; skipping runs the local-only scaffolder.
- [ ] **ZZ.H** Cross-cutting: tests, docs, CLI parity (MO-H): `data-model.md` note that on-disk D.3 layout is unchanged (no `origin`-related files on disk; all remote state is in per-device `RepoConfig`). CLI: `skb repo init --local`, `skb remote add|remove|list|set-primary|set-policy`. Invariants: (i) `repo.remotes.isEmpty() ⇒ no foreground SyncService work for that repo`, (ii) `primaryRemote != null ⇔ remotes.isNotEmpty()`, (iii) `repoId stable across remote-set changes`. Robolectric end-to-end: no-origin → add origin → add mirror-1 → push to both → simulate mirror-1 read-only → primary still pushes.

---

## Notes on parallel deep-dives

Phases A through W are intentionally exhaustive but rely on the deep-dive
documents for *how*. Each deep-dive doc:

- Owns its own phase namespace (e.g. `data-model.md` uses `DM-A`, `DM-B`...).
- Cross-references back into this `main.md` via the phase letter.
- Carries its own `## Status:` header and tick-as-shipped discipline.
- Resolves any unforeseen tradeoff inline with a recommended choice — no
  punts back to the user.

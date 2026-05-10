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

Deep-dive: [`sync-engine.md`](sync-engine.md) phases SE-A through SE-F.

- [ ] **B.1** Vendor JGit 6.x + apache-sshd-osgi; verify clean import on Android (JGit has a few JVM-only fallbacks; document workarounds in `sync-engine.md`)
- [ ] **B.2** Implement `GitRepo` abstraction: open / init / clone / fetch / pull (rebase) / push / commit / status / diff-since
- [ ] **B.3** Implement `RepoStore` — list of configured repos in `EncryptedSharedPreferences`
- [ ] **B.4** SSH keypair generation + EncryptedSharedPreferences storage; export public key to clipboard / share-sheet
- [ ] **B.5** OAuth Device Flow for GitHub (token in EncryptedSharedPreferences, refresh on 401)
- [ ] **B.6** OAuth Device Flow for Forgejo (same shape, different endpoints)
- [ ] **B.7** Manual PAT entry path
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

- [ ] **I.1** Repo switcher (top-bar) — filterable list with circular icons + display names + sync status badges
- [ ] **I.2** Add-repo flow — provider (GitHub/Forgejo), URL, auth method, identity, default calendar
- [ ] **I.3** Repo settings — display name, icon, auto-sync toggle + interval, default identity, default calendar, default todolist, color seed
- [ ] **I.4** Identity management within repo (`identities/` editor)
- [ ] **I.5** Remove repo (with "are you sure" + local-clone retention option)
- [ ] **I.6** All-repos unified-view master toggle

---

## Phase J — sync orchestration

Deep-dive: [`sync-engine.md`](sync-engine.md) phases SE-G through SE-L.

- [ ] **J.1** Foreground service `SyncService` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC`
- [ ] **J.2** Sync scheduler — per-repo interval + on-app-foreground + on-connectivity-restored
- [ ] **J.3** Manual sync button → triggers sync-all-flagged
- [ ] **J.4** Sync status persisted per-repo: `last_synced_at`, `commits_ahead`, `commits_behind`, `last_error`
- [ ] **J.5** Conflict detection + 3-way diff UI for conflicted files
- [ ] **J.6** Conflict resolution UI: structured-field editor for TOML frontmatter, text editor for body, keep-mine / keep-theirs / merge buttons
- [ ] **J.7** Read-only repo handling — push refused → queue, show banner
- [ ] **J.8** Sync result toasts + last-sync time in top bar

---

## Phase K — wizard + templates

Deep-dive: [`templates-demo-wizard.md`](templates-demo-wizard.md) phases TW-A through TW-F.

- [ ] **K.1** First-run wizard screens (welcome, path picker, role toggles, repo picker, auth, scaffold, finish)
- [ ] **K.2** Template registry + composer (role toggle ↔ template set composition)
- [ ] **K.3** Repo scaffolder: generate calendars, todolists, identities, AGENTS.md, CLAUDE.md, README.md, .strictlykeptboy/
- [ ] **K.4** Initial commit + push
- [ ] **K.5** Re-apply template later (Settings → Templates → apply)
- [ ] **K.6** Custom template repo source (configurable URL)

---

## Phase L — demo content

Deep-dive: [`templates-demo-wizard.md`](templates-demo-wizard.md) phases TW-G through TW-H.

- [ ] **L.1** `demo-sub` repo seed — full SFW-kinky-coded sub schedule + tasks
- [ ] **L.2** `demo-dom` repo seed — Dom's calendar with check-in events that overlay
- [ ] **L.3** Demo mode flag: spins up local clones (no remote push) with both repos preloaded
- [ ] **L.4** "Exit demo mode" path: option to keep demo data as a real local repo or discard

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
- [ ] **S.8** Demo section (toggle demo data)
- [ ] **S.9** Appearance section (theme, density, font, dynamic color override)
- [ ] **S.10** About section (build info, license screen, mascot, repo link)

---

## Phase T — theming + personalization

- [ ] **T.1** Mascot SVG asset wiring (placeholder until user provides real art)
- [ ] **T.2** Repo icon system: emoji-in-SVG-wrapper, photo, auto-initials
- [ ] **T.3** Calendar/todolist icon + color picker
- [ ] **T.4** Event emoji prefix UI
- [ ] **T.5** Dynamic color override per repo (color seed)

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
- [ ] **W.4** Play Store listing copy (hinting-not-blatant; aimed at "calendar + timeboxing for professionals and people who like structured schedules")
- [ ] **W.5** Play Store screenshots set (SFW-themed mascot, polished schedule views, no kinky demo content)
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

- [ ] **X.1** Define subcommand surface (D.24)
- [ ] **X.2** Implement JVM-jar entrypoint (shared `:cli` Gradle subproject already planned in NS for tools/skb-cli.jar — promote to top-level `:cli`)
- [ ] **X.3** Shell-script wrapper for distribution (`skb` POSIX shell launcher; finds java, launches jar)
- [ ] **X.4** Atomic write + auto-commit hooks (same commit-message format as GUI)
- [ ] **X.5** `--json` machine-readable output mode for AI consumers
- [ ] **X.6** `--dry-run` flag (prints proposed change, no write)
- [ ] **X.7** Exit-code taxonomy (0=ok, 1=usage, 2=not-found, 3=conflict, 4=auth, 5=corrupt, 6=schema-mismatch)
- [ ] **X.8** Bundle in release pipeline alongside APK (GitHub Releases asset; homebrew tap `887/tap/skb`; `curl ... | sh` one-liner)
- [ ] **X.9** AGENTS.md content references CLI as primary path (rewrite per DM-K of data-model.md extensions)
- [ ] **X.10** Help system (`skb help <command>`, contextual examples, machine-readable `skb help --json` for AI agents)
- [ ] **X.11** Repo discovery: walk-up to find `.strictlykeptboy/` from CWD; `--repo <path>` override; `SKB_REPO` env var
- [ ] **X.12** Test suite — pure JVM, Robolectric-free

## Phase Y — Bidirectional CalDAV

Deep-dive: [`sync-engine.md`](sync-engine.md) extension phases SE-Q+, [`notifications-sharing-import.md`](notifications-sharing-import.md) extension phases NS-L+.

- [ ] **Y.1** Add `ical4j` + `dav4jvm` deps; verify MPL-2.0 license-clean for our distribution
- [ ] **Y.2** CalDAV discovery flow (well-known URL `/.well-known/caldav`, then PROPFIND for calendars)
- [ ] **Y.3** Server credential storage (alongside git creds in EncryptedSharedPreferences)
- [ ] **Y.4** Pull-only mirror calendar mode (CalDAV → repo, read-only target)
- [ ] **Y.5** Push-only export mode (repo → CalDAV)
- [ ] **Y.6** Bidirectional sync mode (full two-way, with conflict detection)
- [ ] **Y.7** Conflict resolution shared with git conflict UI
- [ ] **Y.8** `skb caldav add|sync|remove|list` subcommands
- [ ] **Y.9** Per-CalDAV-mirror sync interval (default 30m)
- [ ] **Y.10** ETag-based change detection (avoid full re-pull)

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

- [ ] **NN.1** Schema definition + ktoml round-trip
- [ ] **NN.2** Reader: scan `.strictlykeptboy/references.toml` on repo open
- [ ] **NN.3** Writer: append/remove entries via `skb ref add|remove`
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
- [ ] **PP.2** Hidden surfaces: repo management, identities, templates, advanced sync, multi-view tabs, Together tab
- [ ] **PP.3** Visible surfaces: Schedule (single view), Tasks (combined), sync button, comment composer, settings (minimal)
- [ ] **PP.4** "Switch to full mode" entry point in settings (one-tap-reversible)
- [ ] **PP.5** Mode-label picker (Simplified / Focused / Received Schedules / Good Boy / Good Girl / Good Pet / Kept / Other)
- [ ] **PP.6** Auto-entry: simplified by default when only read-only repos configured AND no own repo
- [ ] **PP.7** Play Store screenshots use "Simplified" label exclusively

## Phase QQ — First-launch deep-link bootstrap

Deep-dives: [`shared-schedules.md`](shared-schedules.md) SH-F, [`ui-spec.md`](ui-spec.md) UI-GG.

- [ ] **QQ.1** Detect "launched via deep-link AND no repos configured" condition
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

---

## Notes on parallel deep-dives

Phases A through W are intentionally exhaustive but rely on the deep-dive
documents for *how*. Each deep-dive doc:

- Owns its own phase namespace (e.g. `data-model.md` uses `DM-A`, `DM-B`...).
- Cross-references back into this `main.md` via the phase letter.
- Carries its own `## Status:` header and tick-as-shipped discipline.
- Resolves any unforeseen tradeoff inline with a recommended choice — no
  punts back to the user.

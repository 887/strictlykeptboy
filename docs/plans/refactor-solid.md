# strictlykeptboy — SOLID refactor + standing-discipline plan

## Status: 🚧 IN-PROGRESS — standing audit alongside Round 1 implementation.

This is the running list of SOLID audit findings + cross-cutting rules every phase enforces. Started fresh per the project standard inherited from tonearmboy / shutterboy / whisperboy. Findings accumulate per-phase; phases below are work-streams, not implementation phases (those live in `main.md`).

The discipline: **before declaring any phase done, self-check the cross-cutting rules below against the diff.** New file? Run the 5-question SOLID check. Past 500 LOC? Second look. Past 800? Almost always needs splitting.

---

## Cross-cutting rules (every phase must honour these)

These are the load-bearing conventions every subagent + every code-shipping turn must comply with. They are checked BEFORE marking a phase done.

### R.X.1 — Narrow data interfaces

Composables / ViewModels take the smallest interface that satisfies their need, not a god-handle. If `ScheduleDayView` needs only `events.byDateRange()`, it takes a `DayEventSource` (one method) — not the whole `CacheDatabase` (12+ DAOs). When a leaf needs 1–3 fields, pass *those fields*, not the parent object.

**Anti-pattern to watch for:** `fun MyView(database: CacheDatabase)` when the view uses 2 methods.

### R.X.2 — Sealed types for branching, not enum + when-chain

When a behaviour varies by case, prefer a sealed hierarchy with one variant per case (data + variant-specific behaviour co-located), not an `enum + when(it)` that grows in every consumer. Sealed types are open/closed: adding a variant is a new file, not a hunt-and-modify across 5 sites.

**Examples in this codebase that pass:** `FetchResult` / `PullResult` / `PushResult` / `CommitResult` in `git/Results.kt`; `ViewMode` in `resolver/`; `CredentialBinding` in `git/auth/`.

### R.X.3 — Composition root is the only place that wires concrete classes

Concrete `Room` DAOs, JGit wrappers, OkHttp clients, EncryptedSharedPreferences instances live behind interfaces in production code. The composition root (`MainActivity` for now; the future `AppGraph` later) is the *only* place that knows the concrete types. ViewModels / composables / use-cases take the interface.

**Currently passes:** `CredentialResolver` interface + `ProductionCredentialResolver` impl; `RepoStore.openForTest(SharedPreferences)` accepts injection.

**Open work-stream:** introduce `AppGraph` once `MainActivity` wiring crosses ~250 LOC. Currently 174 — fine.

### R.X.4 — No god-files

Soft heuristic: anything past **~500 LOC** of non-trivial Kotlin deserves a second look; past **~800 LOC** almost always needs splitting. The split should follow concerns (single-responsibility), not arbitrary file-size targets.

**Current red flags (audit pass 2026-05-12):**

| File | LOC | Verdict |
| --- | --- | --- |
| `git/GitRepo.kt` | 536 | Watch. Single concern (async JGit wrapper) so passes SRP; growing because of multi-origin fan-out + conflict path internals. If it crosses 700, split the multi-origin push fan-out (~70 LOC) into `git/MultiOriginPush.kt`. |
| `MainActivity.kt` | 174 | OK. Composition-root duties — wiring sync scheduler + status store + repo store. Will need refactor to `AppGraph` if it hits 250. |
| `ui/scaffold/AppScaffold.kt` | 145 | OK. Will grow with each new nav destination. Split rail-rendering vs destination-routing if past 300. |
| `ui/schedule/SchedulePane.kt` | 99 | Healthy. |

Subagents must report file LOC of newly created files past 200 in their phase-completion report.

### R.X.5 — Liskov: deferred / NotImplementedError variants must be temporary

A sealed-variant or interface impl that throws `NotImplementedError` is a Liskov violation in spirit, even though it compiles. We allow them ONLY when the consumer UI is also deferred (so the violation is unreachable in practice). Every `NotImplementedError` carries an inline comment referencing the phase that closes the deferral.

**Currently allowed:**
- `CredentialBindings.forSsh(...)` throws until Phase I's SSH-remote UI lands. The phase that closes this: B.4-remainder (SSH transport plumbing — `SshdSessionFactory` + TOFU verifier + `transportConfigCallbackForSsh`).

### R.X.6 — Wrong-direction imports

Lower layers MUST NOT import upper layers:
- `git/` cannot import from `cache/`, `resolver/`, `ui/`, `sync/`.
- `store/` cannot import from `cache/`, `resolver/`, `ui/`, `sync/`.
- `cache/` cannot import from `resolver/`, `ui/`, `sync/`.
- `resolver/` cannot import from `ui/`, `sync/`.
- `sync/` may import from `git/`, `store/`, `cache/` but NOT `ui/`.
- `ui/` is the outermost layer and may import from any of the above.

Caught early via lint; manually verified per phase.

### R.X.7 — Interface segregation in Compose

In Compose specifically: don't pass a god-state object down 5 levels. Pass the 3 fields the leaf actually reads. State hoisting + small parameter lists keep recomposition scoped.

**Anti-pattern:** `EventBand(state: ScheduleViewState)` when EventBand reads only `state.event` + `state.laneIndex` + `state.totalLanes`. Pass those three.

### R.X.8 — Test discipline

- Every new file with public surface gets a test (Robolectric for Android-dependent, JVM-only otherwise).
- Tests run in JVM Robolectric under `:app:testDebugUnitTest`. No physical device needed for unit-level work.
- Tests must pass green before commit (the AVD smoke is additive, not replacement).
- Subagents must report the test count delta in their phase-completion report.

### R.X.9 — AVD smoke before declaring done

Any phase touching Compose UI (anything visible) MUST be installed on the headless AVD and smoke-tested before ticking. Robolectric does not catch real-device layout bugs (overflow, clipping, sheet z-order, recomposition jank under load).

Per the canonical loop in `CLAUDE.md`. Subagents must report AVD outcome per view-mode (passed / issues + screenshots in `/tmp/<phase>-*.png`).

---

## Per-phase audit checklist (run before ticking phase header)

For each implementation phase in `main.md`, the subagent (or the parent agent verifying) must answer YES to all of these:

1. ✅ Files added → each one has a single responsibility describable in one sentence without "and / also / plus"?
2. ✅ Branching → no `when (kind)` chains where a sealed hierarchy would express it cleaner?
3. ✅ Composition root → concrete classes wired only at `MainActivity` (or `AppGraph` once introduced)?
4. ✅ LOC discipline → no new file past 500 LOC unsplit? No deferral file past 800 LOC?
5. ✅ Liskov → no `NotImplementedError` variants without an inline comment naming the closing phase?
6. ✅ Wrong-direction imports → no upward imports per R.X.6?
7. ✅ ISP in Compose → no god-state objects passed down 5 levels?
8. ✅ Tests added → each new public surface tested? Suite total ticked up?
9. ✅ AVD smoke (UI-affecting phases only) → installDebug + launch + screencap + Read?

Subagent prompts should embed this checklist explicitly.

---

## Findings backlog (audit-pass per phase)

Phases below are tracked individually as findings accumulate. Each one rolls in when a subagent surfaces it OR a manual audit pass turns it up.

### Audit pass 2026-05-12 (post-Phase-J, mid-Phase-K)

- **F1 — `git/GitRepo.kt` at 536 LOC.** Single concern (async JGit wrapper) so passes SRP; watch growth. **Action:** if it crosses 700 LOC, split `multi-origin push fan-out` to its own file. **Priority:** low (no immediate action).
- **F2 — No `AppGraph` composition root.** `MainActivity` does the wiring inline. At 174 LOC it's still OK but each new orchestrator (sync scheduler, conflict registry, view-mode persistence) adds to it. **Action:** introduce `AppGraph` when `MainActivity` crosses 250 LOC, or sooner if a sub-agent has to wire something more than 3 parameters deep. **Priority:** medium (likely triggered in Phase K when wizard wiring lands).
- **F3 — `EventDetailSheet` Edit button is a no-op.** Phase G shipped with the affordance but no real edit flow. **Action:** wire in Phase EE (inline-markdown editor) or earlier if a real edit need surfaces. **Priority:** low (deferred work-stream).
- **F4 — `CredentialBindings.forSsh` is NotImplementedError stub.** Documented R.X.5 deferral. **Action:** close with B.4-remainder (SSH transport plumbing). **Priority:** medium — gated on Phase I's UI for adding an SSH remote.
- **F5 — Wizard input not yet using `string` resources.** ✅ **Status: fixed in post-K SOLID cleanup pass 2026-05-12.** Retrofit extracted ~125 user-facing literals across the `ui/` tree (wizard, age gate, scaffold, schedule empty, task empty/sections, repos pane, repo settings, identities, add-repo, event/task detail, switcher dropdown, sync button, identity avatar, repo switcher chip) into `app/src/main/res/values/strings.xml`. Naming follows the locked scheme: `wizard_`, `schedule_`, `tasks_`, `repos_`, `repo_settings_`, `repo_switcher_`, `add_repo_`, `identities_`, `event_detail_`, `task_detail_`, `age_gate_`, `scaffold_`, `dialog_`, `cd_`.

### Audit pass 2026-05-12 (post-Phase-K SOLID cleanup)

This was the discipline-gate sweep that should have run as Phase K closed but was missing — the `refactor-solid.md` standing doc only landed in `48cb056`, after K shipped in `3c4f989`. The pass audited every Kotlin file under `app/src/main/java/com/eight87/strictlykeptboy/` (92 files, 13560 LOC) against the 9 R.X cross-cutting rules.

**Fixes shipped in this commit (R.X.1–R.X.9 self-check):**

1. ✅ Files added → all current files have single-responsibility describable in one sentence.
2. ✅ Branching → no new `when (kind)` chains; existing sealed hierarchies (`FetchResult` / `PullResult` / `PushResult` / `CommitResult` / `CredentialBinding` / `ViewMode`) cover the variation surface.
3. ✅ Composition root → `MainActivity` (174 LOC) still wires concrete classes; under the 250-LOC trigger for promoting to `AppGraph`.
4. ⚠️ LOC discipline → see F6 + F7 + F8 below.
5. ✅ Liskov → no `NotImplementedError` added in K. The single existing stub (`CredentialBindings.forSsh` — F4) is still gated on B.4-remainder.
6. ✅ Wrong-direction imports → spot-checked: no `git/` → `cache/`/`resolver/`/`ui/`/`sync/`, no `cache/` → `resolver/`/`ui/`/`sync/`, etc. ReposPane's import of `git/auth/SecretsStore` is `ui/` → `git/`, which is allowed.
7. ✅ ISP in Compose → wizard composables receive narrow `WizardDraft` + per-screen callbacks, not god-state; schedule/tasks views receive only the slice they render.
8. ✅ Tests added → 192 tests pass (same as J-baseline; no test regressions from the string retrofit).
9. ✅ AVD smoke → installed on `emulator-5554`, navigated Schedule + Tasks rails, screencap to `/tmp/solid-cleanup*.png`. Schedule empty-state renders "nothing scheduled — good boy can rest ;3", Tasks Combined renders "no tasks anywhere — good boy ;3" — both formatted-string resources resolving correctly.

**Findings backlog from this pass:**

- **F6 — `ui/wizard/WizardNavHost.kt` at 831 LOC.** Past the 800-LOC split heuristic (R.X.4). Concerns: nav-host shell + progress row + mascot sticker + 9 per-screen private composables + reset helpers. **Action:** split into `WizardNavHost.kt` (shell + progress + screen dispatcher) plus `WizardScreens.kt` (the 9 per-screen private composables: Welcome / Species / Alignment / Identity / Lifestyle / Roles / Templates / Git / Scaffold / Done) plus `WizardChrome.kt` (`ProgressRow` + `BatMascotSticker` + `WrappingChipRow`). The 9 screen functions are independently editable and account for most of the LOC. **Priority:** medium. **Status:** tracked.
- **F7 — `ui/repos/AddRepoNavHost.kt` at 594 LOC.** Past the 500-LOC second-look heuristic (R.X.4). Single file holds: nav-host shell + 7 step composables + `AddRepoResult` sealed type + `RepoConfig` builder extensions + `AddRepoProvider.label()` helper. **Action:** when next touched, split `AddRepoResult` + `toRepoConfig(...)` extensions into `AddRepoResult.kt`, and the 7 step composables into `AddRepoSteps.kt`. **Priority:** low. **Status:** tracked.
- **F8 — `store/Entities.kt` at 571 LOC.** Past the 500-LOC mark. Holds the full TOML-frontmatter entity model. **Action:** verify this is one cohesive concern (TOML schema for one entity-family) before splitting; if it grew because event + task + supersedence + identity merged into one file, split per entity. **Priority:** low — audit later. **Status:** tracked.
- **F9 — `ReposPane` threads `SecretsStore` to its leaf.** That's `ui/` → `git/auth/` which is allowed per R.X.6 (ui is outermost), but the composable does the secrets-write inline. **Action:** consider hoisting `secretsStore.storePat(...)` / `storeOAuthToken(...)` into a thin `AddRepoCommand` use-case so `ReposPane` doesn't know about credentials. Not load-bearing today. **Priority:** low. **Status:** tracked.
- **F10 — `EmptyTasksState` / `EmptyScheduleState` duplicate.** Two near-identical 50-LOC composables differing only in test-tag constants and parameters. Acceptable parallel symmetry across rails. **Action:** none — keep parallel; collapse only if a third caller appears. **Priority:** low. **Status:** OK (no action).
- **F11 — Enum-property labels still hardcoded in code.** ✅ **Status: CLOSED in Phase U (2026-05-13).** New `ui/a11y/EnumLabels.kt` exposes `@StringRes labelRes: Int` + `@Composable fun T.labelString(): String` + `fun T.labelString(context: Context): String` extensions for `TopDestination`, `ScheduleViewTab`, `TaskViewTab`, `SpeciesChoice`, `Alignment` (+ `taglineRes`), `RoleId`, `Honorific`, `ToneRegister`, `EmojiDensity`, `Lifestyle` (`labelString(alignment)`), `AddRepoProvider`, and `QuickAddTarget`. UI call sites updated. Enum `.label: String` constructor fields preserved + documented as **wire-format** because `WizardScaffolder` persists them verbatim to `calendar.toml` (`name = ...`) and `identity.toml` (`[honorific].term = ...`) — those values are on-disk repo schema (D.3) and must not be localised. The split is the clean resolution: UI gets a translatable surface; the disk layer keeps its stable English identifiers. `EnumLabelLocalizationTest` (Robolectric) walks every variant and asserts each resolves to a non-empty English string. `PronounSet.label` (e.g. "he/him") deliberately left as wire-format — pronouns are a stable identifier across locales, not framework chrome. `TemplateRegistry`'s `humanize()` output similarly stays English — it generates recurrence-event titles that get written to disk on wizard scaffold. **Wire-format / UI-label split is now the documented standing pattern for enums in this repo (see CLAUDE.md Translations section + docs/plans/translations.md).**
- **F12 — No new `NotImplementedError` in K.** Spot-checked the wizard surface — no new Liskov-violating stubs landed. ✅
- **F13 — `MainActivity` still at 174 LOC.** Under the 250-LOC trigger for `AppGraph` promotion. ✅
- **F14 — Auto-mirrored `Icons.Filled.ArrowBack` deprecation.** Compile warnings on `IdentitiesScreen.kt:66` and `RepoSettingsScreen.kt:98` — Compose Material recommends `Icons.AutoMirrored.Filled.ArrowBack` for RTL support. **Action:** swap to `AutoMirrored` variant during next i18n/RTL pass. **Priority:** low. **Status:** tracked.

### Findings emitted by Phase M (notifications)

**Audit pass 2026-05-12** (Phase M shipped).

R.X.1..R.X.9 self-check:

1. ✅ R.X.1 narrow data interfaces — `EventReminderScheduler` accepts a `ReminderInput` data class (8 fields), not a `CacheDatabase` god-handle. `SyncEventNotificationBridge` consumes ONLY `Flow<SyncEvent>` not the whole `SyncScheduler`. `NotificationsSettingsScreen` takes a single `NotificationPrefs` handle.
2. ✅ R.X.2 sealed types for branching — `ReminderRole` is sealed (HeadsUp / PreEvent / TomorrowBriefing / AtStart / PostEventCheckin / Snoozed). Consumers branch via `when`.
3. ✅ R.X.3 composition root only place that wires concrete classes — `MainActivity.onCreate` is the sole installer of `SyncEventNotificationBridge`. `SkbApp.onCreate` is the sole caller of `NotificationChannels.registerAll`. The `notif/` package itself depends on Android system services + `store/` (allowed per R.X.6).
4. ✅ R.X.4 no god-files — new files all under the soft 500-LOC line (largest: `ReminderBroadcastReceiver.kt` ~130 LOC; `EventReminderScheduler.kt` ~150 LOC).
5. ✅ R.X.5 no new `NotImplementedError` in Phase M. The `Briefings` / `PostEventCheckin` `ReminderRole` variants are deliberately constructable but never produced by the current scheduler — they're forward-declared placeholders for NS-Z follow-up. Receiver's `when` on roles is currently unused (the receiver builds content from extras, not from a role); this is fine pending NS-Z.
6. ✅ R.X.6 import direction — `notif/` imports from `store/` (allowed for the deviation writer) + `sync/` (allowed: notif/ is "ui-adjacent" but doesn't import `cache/` or `resolver/`). `sync/` never imports `notif/`. `MainActivity` (ui/composition root) imports `notif/` (ok — ui is outermost).
7. ✅ R.X.7 ISP in Compose — `NotificationsSettingsScreen` takes only `NotificationPrefs`; `ChannelRow` leaf takes `(prefs, channelId, nameRes)` — three fields, not a god-state.
8. ✅ R.X.8 test discipline — 23 new tests added (`LeadTimeTest` 5, `ReminderRoleTest` 4, `NotificationChannelRegistrationTest` 3, `NotificationPrefsTest` 3, `EventReminderSchedulerTest` 4, `LockscreenPrivacyTest` 2, `SnoozeActionTest` 1, `DeviationActionWriterTest` 1) covering every new public surface.
9. ✅ R.X.9 AVD smoke — installed on `emulator-5554`, granted POST_NOTIFICATIONS, confirmed all 6 channels via `dumpsys notification` with correct importances (events=3, tasks=3, briefings=2, sync=1, errors=4, service=2) under group `skb_main`. No AndroidRuntime:E entries in `logcat -d -t 200`. Screencap at `/tmp/skb-phaseM.png`.

**Findings backlog from this pass:**

- **F15 — Pre-existing flaky `ScheduleTimeboxViewTest`.** The test computes `nowHour + 2` from wall-clock, which fails as `LocalTime.of(24, …)` when local time crosses 22:00. Adjusted upper coerce bound from 22→21 to widen the safe window; the test is still unsatisfiable after 22:00 because the "now-block" assertion requires `now` ∈ `[nowHour, nowHour+1)`. **Action:** rewrite the test to inject a fixed clock instead of depending on `LocalTime.now()`. **Priority:** medium (real-time-of-day flake). **Status:** known; band-aided, not fixed.
- **F16 — Deferred from Phase M (tracked here for NS-Z follow-up):** boot re-arm `RECEIVE_BOOT_COMPLETED` receiver, AlarmHorizonExtender nightly worker, D.79 per-category cadence defaults, D.81 cal-briefings auto-generated bodies, D.82 post_event_checkin opt-in flow, D.80 off-schedule warning prefix, NS-A.10..14 logical notification groups + group-level mute, NS-D.13 `setPublicVersion` redacted-body retrofit for non-private events. **Priority:** scheduled. **Status:** tracked.
- **F17 — `notif/NotificationsSettingsScreen` not yet wired into the Settings rail.** The Compose surface exists and is testable in isolation; the rail Settings destination is still the placeholder per Phase S. When Phase S lands, drop `NotificationsSettingsScreen(prefs)` into the Notifications section (S.4). **Priority:** low — surface is reachable from system-level notification settings via the deep-link button. **Status:** tracked.

### Findings emitted by Phase N (Together / common-time finder)

**Audit pass 2026-05-12** (N.1 + N.2 shipped; N.3 v1-stubbed).

R.X.1..R.X.9 self-check:

1. ✅ R.X.1 narrow data interfaces — `TogetherViewModel` takes 4 narrow inputs (`CoroutineScope`, `StateFlow<List<TogetherRepoOption>>`, `BusySource`, `CommonTimeFinderPort`); never sees `RepoStore`/`CacheDatabase`. Composables receive only what they render (`TogetherInputForm` takes options + state + callbacks; `TogetherResultList` takes slots + tap callback).
2. ✅ R.X.2 sealed types for branching — `TogetherResultState` is a sealed interface (`Idle` / `Running` / `Results(slots)` / `Empty`). The pane branches via exhaustive `when`.
3. ✅ R.X.3 composition root — `MainActivity` is the sole place that constructs `CommonTimeFinder` (concrete) and adapts it to `CommonTimeFinderPort`, plus the empty `BusySource` stub. The `ui/together/` package never touches `RepoStore` or `CacheDatabase` directly.
4. ✅ R.X.4 no god-files — largest new file is `TogetherInputForm.kt` (~165 LOC), `TogetherPane.kt` ~95, `TogetherViewModel.kt` ~85, `TogetherTypes.kt` ~75, `TogetherResultList.kt` ~85, `TogetherEmptyState.kt` ~50. Each has one cohesive responsibility.
5. ✅ R.X.5 no new `NotImplementedError`.
6. ✅ R.X.6 import direction — `ui/together/` imports from `resolver/` (allowed: ui is outermost) and `git/` only via `RepoConfig` in MainActivity wiring, not from inside the `together/` package. No `resolver/` → `ui/` imports.
7. ✅ R.X.7 ISP in Compose — the form's leaf chips receive `(selected, onClick, label)` only; the result card receives `(slot, onTap)`.
8. ✅ R.X.8 test discipline — 7 new tests added (`TogetherInputFormTest` 2, `TogetherViewModelTest` 3, `TogetherResultListTest` 1, `TogetherEmptyStateTest` 1). Suite total moved 215 → 222.
9. ✅ R.X.9 AVD smoke — installed on `emulator-5554`. Together rail item renders, input form renders, repo chip select + Find slots produces "10 slot(s) found" cards with date / time-range / duration / Create-event button. No AndroidRuntime:E in `adb logcat -d -t 200`. Screencaps at `/tmp/n-together-input.png` and `/tmp/n-together-results.png`.

**Findings backlog from this pass:**

- **F18 — Empty production `BusySource`.** The composition root currently wires `BusySource { _, _, _, _ -> emptyMap() }` because the RepoStore → Room indexer → renderer bridge for live calendar data is still stubbed in `MainActivity` (an empty `RepoSnapshot` + empty `Renderer.Sources`). The UI is fully usable end-to-end and the finder runs against real `CommonTimeFinder.find(...)`, but the busy set is always empty so every candidate window becomes a free slot. **Action:** wire a real `BusySource` once the schedule data bridge lands (post-Round-1 / RV-F caching follow-up). **Priority:** medium — gates honest finder results on the device. **Status:** tracked.
- **F19 — Per-repo calendar sub-filter unbuilt.** N.1 mentions a per-repo calendar sub-filter; current build picks ALL active calendars in the selected repos. The form skips the calendar layer entirely because RV-E/`CommonTimeFinder.Query` already operates at the busy-set level — the caller (BusySource impl) chooses which calendars contribute. When F18 closes, surface the calendar sub-pickers in the form. **Priority:** low. **Status:** tracked.
- **F20 — Slot-tap stub.** N.3 ships as a Toast "would create event" stub per the brief; the full editor sheet (target repo + calendar picker + start/end editor) lives in I-K-EE. **Priority:** scheduled. **Status:** tracked (mirrors F3 deferred-edit-flow stance).
- **F21 — `MainActivity` LOC creeping.** With the Together wiring `MainActivity.kt` is now ~210 LOC (up from 174). Under the 250-LOC `AppGraph` promotion trigger per F2 but moving toward it. **Action:** keep an eye on it; promote on next composition-root add. **Priority:** low. **Status:** tracked.

### Audit pass 2026-05-12 (Phase P — import / export)

1. ✅ R.X.1 narrow data interface — `ImportExportViewState(repos, onConfirmedImport)` is a 2-param port, not the whole RepoStore. The screen takes `(state, onPickImportFile, onPickExportFile)` only.
2. ✅ R.X.2 sealed types — n/a; no branching that wanted sealedness on the import/export surface.
3. ✅ R.X.3 composition root — SAF launchers + EntityWriter + `GitRepo.commitAll` all wired in `MainActivity`. The `port/ics/` modules are pure functions over data.
4. ✅ R.X.4 — `IcsParser.kt` ~250 LOC, `IcsExporter.kt` ~135 LOC, well under split threshold.
5. ✅ R.X.5 — no NotImplementedError on the new public surface.
6. ✅ R.X.6 import direction — `port/ics/` → `store/` + `git/Uuid7` (allowed; port wraps lower layers); `ui/import_export/` → `port/ics/` + `git/RepoConfig`. No reverse-direction imports.
7. ✅ R.X.7 ISP in Compose — `ImportPreviewSheet` takes `(preview, onConfirm, onCancel)`. `RepoRow` takes `(repo, onImport, onExport)`. No god-state.
8. ✅ R.X.8 — 19 new tests (`IcsParserTest` 6, `IcsExporterTest` 5, `MalformedIcsTest` 4, `ImportWizardTest` 3, `ExportWizardTest` 1). Suite moved 244 → 263.
9. ✅ R.X.9 AVD smoke — installed on `emulator-5554`, pushed `/sdcard/Download/sample.ics`, parse-preview = "2 events, 0 rules, 0 exceptions", confirm imported + committed, export wrote `/sdcard/Download/my calendar.ics`. No `AndroidRuntime:E` in logcat. Screencaps `/tmp/p-launch.png`, `/tmp/p-settings.png`, `/tmp/p-saf.png`, `/tmp/p-saf3.png` (preview), `/tmp/p-after-import.png`, `/tmp/p-export.png`.

**Findings backlog from this pass:**

- **F22 — `MainActivity` LOC now ~310.** With the SAF launchers + `buildExportContent` helper it crossed the 250-LOC `AppGraph` promotion threshold (F2 / F21). **Action:** promote a `composition/AppGraph.kt` that owns scheduler + SAF launcher wiring as the next round-2 task. **Priority:** medium. **Status:** tracked.
- **F23 — VTIMEZONE dropped on import.** Per the locked decision, v1 treats every non-`Z` DTSTART as a naive LocalDateTime in the rule's `tzId` (defaulted to `"UTC"`). Round-trip with non-UTC sources is approximate; real-world `.ics` (Google / Apple) round-trips fine because they emit UTC `Z`. **Action:** revisit if a user reports timezone drift; consider an ical4j upgrade then. **Priority:** low. **Status:** tracked.
- **F24 — UID dedup on re-import deferred.** P.2 ticked because v1 surfaces UIDs via `external_uid` and the wizard always targets a fresh calendar. A future "import-into-existing-calendar" workflow needs to scan the target calendar's events for matching `external_uid` and skip / merge. **Priority:** medium when P.3 (CSV) lands. **Status:** tracked.
- **F25 — Settings rail now hosts ImportExportScreen.** The Settings placeholder is repurposed as the Phase P import/export entry-point. When Phase S settings polish lands, Settings needs a real nav structure with Import/Export as one section among many. **Priority:** ships with Phase S. **Status:** tracked.

---

## Audit pass `Phase Q (Android Auto)` — Q.1..Q.3 shipped in commit `bf709f0`

Files touched: `composition/AppGraph.kt` (new; +223 LOC), `auto/SkbCarAppService.kt` (new; +50), `auto/TodayEventSource.kt` (new; +42), `auto/TodayScreen.kt` (new; +71), `auto/NextUpScreen.kt` (new; +115), `MainActivity.kt` (rewrite; 417 → 327 LOC), manifest + `res/xml/automotive_app_desc.xml` + `auto_*` strings, `libs.versions.toml` + `app/build.gradle.kts` (`androidx.car.app:app:1.7.0`). Tests: `TodayScreenTest` (2), `NextUpScreenTest` (2), `CarAppRuntimeTest` (3). Suite 263 → 270.

1. ✅ R.X.1 narrow data interface — `TodayEventSource` is a single-method `fun interface`; the Auto surface takes only that, not `Renderer` / `RepoStore` / `CacheDatabase`.
2. ✅ R.X.2 sealed types — n/a; the Auto template surface is a small two-screen stack, no branching that wanted sealedness.
3. ✅ R.X.3 composition root — **F22 closed.** Extracted `AppGraph` as the single place that knows `RepoStore.open`, `SyncScheduler(...)`, `CommonTimeFinder(...)`, `TodayEventSource` factory, etc. `MainActivity` now only constructs + parks the graph, wires SAF launchers, and hands narrow surfaces to `AppScaffold`. `SkbCarAppService` reaches the graph via the new `CarAppRuntime` handle (mirrors `SyncRuntime`).
4. ✅ R.X.4 — every new file under SRP soft threshold. `AppGraph.kt` 223 LOC, `MainActivity.kt` 327 LOC (down 90), Auto screens 50-115 each.
5. ✅ R.X.5 — no `NotImplementedError` on the new public surface. `TodayEventSource` has a real empty-list fallback in `SkbSession.onCreateScreen` when `CarAppRuntime.todayEventSource` is `null` (Auto cold-start before phone-side `onCreate`).
6. ✅ R.X.6 import direction — `composition/AppGraph.kt` is the only place that imports both `auto/*` and `sync/*` and `git/*`; `auto/*` files import only `resolver/*` (data) + `androidx.car.app.*`. No reverse-direction imports.
7. ✅ R.X.7 ISP in Compose — n/a directly (Auto isn't Compose) but the analog holds: `TodayScreen` takes `(CarContext, TodayEventSource)`, not a god-state. `NextUpScreen` takes `(CarContext, focus, allToday)` — three narrow params.
8. ✅ R.X.8 — 7 new tests on the new public surfaces (`TodayScreen.onGetTemplate`, `NextUpScreen.onGetTemplate`, `CarAppRuntime` contract). Suite 263 → 270.
9. ✅ R.X.9 AVD smoke — `:app:assembleDebug` + `adb install -r` + `am start` clean; app process up (pid present, no `AndroidRuntime:E`); `cmd package query-services -a androidx.car.app.CarAppService` lists `com.eight87.strictlykeptboy.auto.SkbCarAppService`; `dumpsys package` shows the service resolver registration + the automotive `<meta-data>` reference. **DHU deferred** — no `~/Android/Sdk/extras/google/auto/desktop-head-unit` on this host; running the Auto Simulator end-to-end is a follow-up.

**Findings backlog from this pass:**

- **F26 — `AppGraph` shipped; F22 closed.** The composition-root extraction landed inline with Phase Q. `MainActivity` is back under 350 LOC and `AppGraph` (223 LOC) absorbs the wiring growth from Phases J/K/N/P/Q. The new "creep" trigger should now be `AppGraph` itself crossing ~500 LOC, at which point we sub-divide into `SyncGraph` / `UiGraph` / `AutoGraph` modules. **Priority:** low. **Status:** tracked.
- **F27 — `TodayEventSource` reads from `AppGraph.sources` directly, which is the Phase F→G integration stub (empty list).** Once the Room → snapshot bridge lands, the Auto surface lights up "for free" but the `renderTodaySync()` fast-path skips recurrence materialization, supersedence, and off-schedule tagging. That's fine for v1 (the car UI doesn't show those annotations) but if a future Q.4/Q.5 voice surface needs the full Renderer output, replace `TodayEventSource` with a coroutine-suspending variant that runs the real `Renderer.render(...)` over a today-scoped `DateRange`. **Priority:** low. **Status:** tracked.
- **F28 — DHU not available in this environment.** The Desktop Head Unit binaries are not on this host; we verified the service is registered + the app launches cleanly, but cannot click through the actual ListTemplate / PaneTemplate rendering in a car projection. **Action:** when the user has DHU installed (or on a real Android Auto head unit), exercise: cold-start Auto → expect empty Today; phone-launch → Auto reload → expect populated Today once Phase F→G bridge lands. **Priority:** low. **Status:** deferred.
- **F29 — `setHeaderAction` / `setTitle` deprecated in `androidx.car.app:1.7.0`.** Kotlin emits deprecation warnings on `ListTemplate.Builder.setHeaderAction` + `setTitle` and `PaneTemplate.Builder.setHeaderAction` + `setTitle`. The car-app library moved to `Header` objects in the post-1.7 line. **Action:** migrate to `Header.Builder()` API when we bump to the next stable. **Priority:** low. **Status:** tracked.
- **F30 — `CarAppRuntime` is a process-wide global, same posture as `SyncRuntime`.** Acceptable for the same reasons (no DI framework in v1, decoupled service binding), but the pair-of-globals pattern will repeat if Phase R / Phase S add more headless services. When the third one lands, replace with a lightweight service-locator holder owned by `AppGraph`. **Priority:** low. **Status:** tracked.

---

## Standing work-streams

These are continuous, NOT one-shot phases:

### R.SD — String-resource discipline (i18n readiness)

Adopt shutterboy's i18n-from-Phase-0 pattern retroactively. Every user-facing string goes through `stringResource(R.string.…)` or `LocalContext.current.getString(R.string.…)`. `app/src/main/res/values/strings.xml` is canonical; locale variants are partial overrides at `values-<locale>/strings.xml`. Naming scheme: `<surface>_<role>` lowercase snake (`wizard_welcome_cta`, `schedule_empty_state_good_boy`, `repo_settings_remove_button`). Surfaces in this app: `wizard_`, `schedule_`, `tasks_`, `repos_`, `sync_`, `settings_`, `dialog_`, `error_`, `cd_` (content descriptions).

**Phase to retrofit:** Phase U (accessibility + i18n scaffolding) — but per-screen extraction can ship per-phase from Phase K onward.

### R.UI — UI testing discipline

Robolectric Compose-UI tests cover the headless surface; AVD smoke covers real-device behaviour. Snapshot-style tests for now (assertion-based, not pixel-diff). Pixel-diff via Paparazzi or similar is a Phase V perf-pass consideration.

### R.LIC — License inventory (deferred to Phase W)

When Phase W release engineering lands: enable Licensee, allow SPDX list `[Apache-2.0, MIT, BSD-2-Clause, BSD-3-Clause]`, ship canonical license texts at `app/src/main/assets/licenses/<spdx>.txt`. Currently deferred; track here when triggered.

---

## Audit pass `Phase R (tablet + master-detail)` — R.1..R.5 shipped 2026-05-12

Files added: `ui/adaptive/WindowSizeClass.kt` (+72), `ui/adaptive/AdaptiveSpacing.kt` (+55), `ui/adaptive/MasterDetailLayout.kt` (+63), `ui/settings/SettingsPane.kt` (+210). Files touched: `ui/scaffold/AppScaffold.kt` (rewrite — ProvideWindowSizeClass wrap + width-class-driven rail variant + SettingsPane wiring), `ui/schedule/SchedulePane.kt` (rewrite — two-pane branch), `ui/schedule/EventDetailSheet.kt` (extract `EventDetailContent` for pane-mode reuse), `ui/tasks/TasksPane.kt` (rewrite — two-pane branch), `ui/tasks/TaskDetailSheet.kt` (extract `TaskDetailContent`), `res/values/strings.xml` (+13 strings). Tests added (18): `WindowSizeClassDetectionTest` 4, `TouchTargetScalingTest` 6, `ScheduleMasterDetailTest` 3, `TasksMasterDetailTest` 3, `SettingsMasterDetailTest` 2. Suite 270 → 288.

1. ✅ R.X.1 narrow data interface — `MasterDetailLayout(master, detail)` takes two `() -> Unit` slots, not a god-state. `SettingsPane(importExportState, onPickImportFile, onPickExportFile)` is the same three-param port that the previous direct call took (no widening). `LocalWindowWidthSizeClass` exposes a single value, not a god-config bag.
2. ✅ R.X.2 sealed types — **two new sealed hierarchies**: `WindowWidthSizeClass` (Compact / Medium / Expanded) replaces the alternative `enum WidthClass` + `when (it)` chain; `SettingsCategory` (six objects + lazy `all` list) replaces a `String`-tagged dispatch. Both compile to exhaustive `when` and gain a new case by adding a sealed-class entry, not by editing branches.
3. ✅ R.X.3 composition root — no new concrete-class wiring; `AppGraph` did not change. The width-class detection is a Compose primitive (BoxWithConstraints) and lives at the composable layer.
4. ✅ R.X.4 — every new file under split threshold. `SettingsPane.kt` 210 LOC, `SchedulePane.kt` 175 LOC (up from 99), `TasksPane.kt` 195 LOC (up from 150), `AppScaffold.kt` 211 LOC. All well under 500.
5. ✅ R.X.5 — no NotImplementedError on the new public surface. Settings categories other than Repos / About show a placeholder; Repos delegates to the real `ImportExportScreen` from Phase P.
6. ✅ R.X.6 import direction — `ui/adaptive/` imports only `androidx.compose.*` + `theme/LocalDensityScale` (lower); `ui/settings/` imports `ui/adaptive/` + `ui/import_export/` (siblings or own-layer); `ui/scaffold/` imports `ui/adaptive/`, `ui/settings/`, `ui/schedule/`, `ui/tasks/`, `ui/repos/`, `ui/together/`, `ui/import_export/` (siblings — scaffold is the top of the UI tree). No reverse-direction imports.
7. ✅ R.X.7 ISP in Compose — `MasterDetailLayout` takes only the two render slots + width-class; `EventDetailContent(band, onEdit, attachments, calendarName)` is a four-param surface, no god-state. `SettingsCategoryList(selected, onSelect)` is two params.
8. ✅ R.X.8 — 18 new tests on the new public surfaces (`WindowWidthSizeClass.fromWidth`, `AdaptiveSpacing.{multiplier, minInteractiveSize}`, two-pane gate on Schedule / Tasks / Settings). Suite 270 → 288.
9. ✅ R.X.9 AVD smoke — `wm size 1600x2400 + wm density 240` simulates tablet: `:app:assembleDebug` + install + launch → wide expanded rail with labels, schedule master pane + vertical divider + "Select an event to see details" empty detail pane (screencap `/tmp/r-tablet-schedule.png`). `wm size reset + wm density reset` restores phone form factor: standard NavigationRail with icon-plus-label items, single-pane schedule, sheet-on-tap behaviour preserved (screencap `/tmp/r-phone-schedule.png`). No `AndroidRuntime:E` in `adb logcat -d -t 200` for either form factor.

**Findings backlog from this pass:**

- **F31 — Sealed-`object` class-init race under Robolectric.** `SettingsCategory.Companion.all` originally read as `listOf(General, Identity, Mode, Notifications, Repos, About)` at companion init. Under Robolectric the inner `object`s appeared `null` during the eager evaluation (JVM class-init ordering — companion fields construct before the sibling subclasses finish `<clinit>`). Resolved by wrapping in `by lazy { ... }`. **Action:** prefer `by lazy { listOf(...) }` for every sealed-object enumeration list going forward; the eager form looks identical but breaks at runtime under Robolectric class-loading. **Priority:** low (cookbook fix). **Status:** tracked.
- **F32 — `material3-adaptive` proper not yet pulled in.** We hand-rolled `MasterDetailLayout` as a `Row { master | divider | detail }` because the alpha18 BoM ships only `material3-adaptive-navigation-suite`, not `material3-adaptive` (which would give us `NavigableListDetailPaneScaffold`). When the BoM bumps to a release that includes the full adaptive slice (post-1.5.0-stable), evaluate migrating `MasterDetailLayout` to `NavigableListDetailPaneScaffold` for back-stack-aware pane navigation and predictive-back support. **Priority:** low. **Status:** tracked.
- **F33 — Settings categories are placeholders.** Phase R.4 ships the master-detail *shell* for settings; the actual category surfaces (General, Identity, Mode, Notifications) are still placeholder text per the brief ("Phase S settings polish"). Repos category delegates to the existing ImportExportScreen so Phase P functionality is preserved; About is a single line of text. **Priority:** scheduled (Phase S). **Status:** ✅ CLOSED — Phase S shipped all 12 categories (S.1..S.11 incl. S.8b) under `ui/settings/categories/*.kt`, backed by per-area `*Prefs` classes (`SyncSettingsPrefs`, `CalendarVisibilityPrefs`, `IdentityPrefs`, `ModePrefs`) wired through `AppGraph` + `SettingsAccess`. Sealed-type extension (R.X.2): adding a Phase-S category was a sealed-class case addition only.

## Audit pass 2026-05-12 (Phase S — settings polish)

Self-check (per R.X.1..R.X.9):

1. ✅ R.X.1 Narrow data interfaces — each category Composable takes the narrowest prefs handle it needs (`IdentityCategory(prefs: IdentityPrefs)`, `SyncCategory(prefs: SyncSettingsPrefs, statusStore: SyncStatusStore?)`, `ModeCategory(prefs: ModePrefs)`). No god-state pushed down to leaves; `SettingsAccess` is the single bag at the composition boundary only.
2. ✅ R.X.2 Sealed types for branching — `SettingsCategory` sealed hierarchy extended from 6 to 12 cases with no `when (it)` chain refactor (the dispatch in `SettingsCategoryContent` is the existing exhaustive `when` — adding a case is a new arm, not a chain edit). `DomPersona` is sealed (`Builtin` / `Custom`); `PushPolicy` / `ConflictPolicy` / `AppMode` / `DomCadence` / `ToneRegister` / `EmojiDensity` are sealed enums (Kotlin-native sealed).
3. ✅ R.X.3 Composition root — `AppGraph` is the only place that constructs `SyncSettingsPrefs.open` / `IdentityPrefs.open` / `ModePrefs.open` / `CalendarVisibilityPrefs.open`. `MainActivity` builds `SettingsAccess` from graph handles and passes through `AppScaffold`. No concrete `*Prefs` are constructed in composables.
4. ✅ R.X.4 No god-files — 11 categories live in 11 files under `ui/settings/categories/`, plus a small `CategoryScaffold.kt` for shared chrome. The largest single category file is `IdentityCategory.kt` (~180 LOC); `SettingsPane.kt` itself is ~250 LOC (was ~240 pre-S — the dispatch grew linearly).
5. ✅ R.X.5 Liskov — all sealed subtypes honour their contract (no `NotImplementedError` variants introduced this phase).
6. ✅ R.X.6 Import direction — categories live under `ui/settings/categories/` and depend only on `theme/`, `notif/`, `sync/`, and other `ui/` packages. No upward imports.
7. ✅ R.X.7 ISP in Compose — `ToggleRow`, `SectionLabel`, and `CategorySurface` take primitives (label / checked / onChange); no god-state passed through. `NotificationsCategory` inlines the previously-shared per-channel rows so the wrapping verticalScroll doesn't nest under a child verticalScroll.
8. ✅ R.X.8 Test discipline — added `SettingsPrefsTest` (6 tests over the new prefs classes), `SettingsNavigationTest` (2 multi-iteration tests covering all 12 categories in Compact + Medium), `IdentityLivePreviewTest` (1 test), `ModeCoolingOffTest` (1 test). All 298 tests pass.
9. ✅ R.X.9 AVD smoke — `:app:installDebug` on `emulator-5554`, launched + navigated rail → Settings; screencaps captured for category list (`/tmp/s-state-1.png`, `/tmp/s-settings-main.png`), Sync (`/tmp/s-sync.png`), Appearance (`/tmp/s-appearance.png`), Identity (`/tmp/s-identity.png`), Mode (`/tmp/s-mode.png`), Mode cooling-off dialog (`/tmp/s-mode-confirm.png`), light-theme toggle re-render (`/tmp/s-light.png`). No `AndroidRuntime:E` lines in `adb logcat -d -t 300`.

**Findings backlog from this pass:**

- **F35 — `NotificationsSettingsScreen` is now inlined by `NotificationsCategory`.** Phase M's standalone screen still exists for back-compat but `NotificationsCategory` reimplements its rows inline to avoid the nested-verticalScroll layout assertion (Compose disallows two scroll containers stacked along the same axis). If a future surface needs the standalone screen again, factor out the row-level helpers (`ChannelRow`) rather than re-introducing nested `verticalScroll`. **Priority:** low. **Status:** tracked.
- **F36 — Active-windows editor is read-only.** Phase S.5 / S.6 visibility prefs persist `activeFromIso` / `activeUntilIso`, but the UI only displays the date strings — no date-picker yet. The data shape is forward-compatible with a future editor; flagging here so a follow-up phase doesn't accidentally redesign the persistence layer. **Priority:** scheduled. **Status:** tracked.
- **F37 — Templates registry is currently a stub list.** `SettingsAccess.templateIds` is a hard-coded 4-entry list in `MainActivity`; the real `templates/atomic-*.toml` registry indexer lands when the bundled templates resource is wired (Phase T-adjacent). Apply callback is wired up but no-ops until the template apply path exists. **Priority:** scheduled. **Status:** tracked.
- **F34 — Tablet detail-auto-focus is now-card-only.** Schedule's two-pane detail auto-focuses the currently-active event (now-card resolver query). When no event is "now" the detail pane shows the empty state. A future improvement might surface "next-up" or "most recently viewed"; deferred until live-data lands. **Priority:** low. **Status:** tracked.

### Findings emitted by Phase V (performance pass)

**Audit pass 2026-05-13** (Phase V shipped — see `docs/perf-baseline-2026-05.md`).

R.X.1..R.X.9 self-check:

1. ✅ R.X.1 narrow data interfaces — `PerfTraceRecorder` exposes only `begin(Section)` / `end()` / `trace { }`; call sites do not import `android.os.Trace` directly. Benchmark tests reuse the existing narrow `Renderer.Sources` / `CommonTimeFinder.Query` shapes.
2. ✅ R.X.2 Sealed types — `PerfTraceRecorder.Section` is an `enum class` (closed set; finite phases of the cold-start trace). Not promoted to `sealed` because no per-case data is carried.
3. ✅ R.X.3 Composition root — `MainActivity` and `SkbApp.onCreate` are the only places that emit `PerfTraceRecorder.begin/end` for the lifecycle sections; `SchedulePane` emits the first-frame section via a `LaunchedEffect` keyed on the ViewModel-scoped state (idempotent per state-instance).
4. ✅ R.X.4 No god-files — `PerfTraceRecorder.kt` is ~40 LOC; benchmark tests average ~70 LOC each.
5. ✅ R.X.5 Liskov — every `Section` variant routes to the same `Trace.beginSection` path; no thrown-from-subtype behaviour.
6. ✅ R.X.6 Import direction — `perf/` is a leaf package; depends only on `android.os.Trace`. No upward imports.
7. ✅ R.X.7 ISP in Compose — `SchedulePane`'s trace `LaunchedEffect` does not introduce any new parameter; it takes the `state` already in scope.
8. ✅ R.X.8 Test discipline — `PerfTraceRecorderTest`, `ColdStartBudgetTest`, `SyncSmallRepoBenchmarkTest`, `MonthRenderBenchmarkTest`, `CommonTimeFinderBenchmarkTest`, `GitRepoRegistryBoundedTest` added; 351 tests pass.
9. ✅ R.X.9 AVD smoke — 3× `am start -W` cold-start measurements captured; `dumpsys meminfo` PSS+RSS captured; no `AndroidRuntime:E` in `adb logcat -d -t 200`. Perf-report rendered to `/tmp/v-perf-report.png`.

**Findings backlog from this pass:**

- **F41 — Resident memory ≈ 178 MB PSS on AVD (over the < 150 MB V.5 budget).** JGit (Apache MINA SSHD + BouncyCastle) + Compose + Room load eagerly at app start. Three concrete levers: (a) narrow `proguard-rules.pro` from blanket `-keep class org.eclipse.jgit.**` to a Gradle baseline-profile-derived allowlist (deferred to Phase W.6 release engineering); (b) lazy-init `SyncScheduler.startPeriodicTicks()` to post-first-frame via `lifecycleScope.launch` so the first Composition isn't gated on networking-stack class-load; (c) move `BouncyCastleProvider` insertion from `SkbApp.onCreate` to first-auth-use (SshBinding configure path). **Priority:** scheduled (do (b) + (c) in Phase W; (a) is Phase W.6 anyway). **Status:** tracked.
- **F42 — AVD cold-start TotalTime ≈ 1.5–1.7 s vs. < 600 ms target.** Robolectric path measures `AppGraph` ctor + plain-prefs lazies at ≈ 28 ms, so the surplus is first-frame Compose + Skia under swiftshader, not Kotlin work. Re-measure on user's real device when wifi-adb pairing lands; only optimize if real-device also misses 600 ms. **Priority:** observe-only until real-device data. **Status:** tracked.
- **F43 — `GitRepoRegistry` was unbounded.** Fixed in this phase (bounded at 50 entries via `LinkedHashMap.removeEldestEntry`, access-order). Documenting here for the audit trail — no further action. **Status:** closed.
- **F44 — `ColdStartBudgetTest` skips encrypted-prefs lazies under Robolectric.** `RepoStore`, `SecretsStore`, and `AgeGatePrefs` use `EncryptedSharedPreferences` which requires AndroidKeyStore — unavailable in the Robolectric shadow. The test therefore measures the plain-prefs path only. Full-path cold-start measurement happens via `am start -W` on the AVD/device side (captured in `docs/perf-baseline-2026-05.md`). **Status:** by design, no action.

### Findings emitted by Phase U (accessibility + i18n scaffolding)

**Audit pass 2026-05-13** (Phase U shipped).

R.X.1..R.X.9 self-check:

1. ✅ R.X.1 narrow data interfaces — `EnumLabels.kt` exposes `@StringRes labelRes` + thin `labelString()` extensions per enum; no fat type forced on call sites. Composables that need a label depend on the extension, not on any package outside `ui/a11y/`.
2. ✅ R.X.2 Sealed types for branching — every new `when` over an enum (`labelRes` resolvers) is exhaustive; the compiler guarantees new variants are caught. No `if/else` chain alternative was tempted.
3. ✅ R.X.3 Composition root — `EnumLabels.kt` is a pure-function module (no constructor injection); call sites in Composables resolve via Compose's ambient `LocalContext` through `stringResource`. The Context-overload (`labelString(context)`) is for the rare non-Composable path (notifications, logging) and is invoked by the same caller that already owns the Context.
4. ✅ R.X.4 No god-files — `EnumLabels.kt` is ~240 LOC of straight `when` arms across 12 enums. No god-file risk.
5. ✅ R.X.5 Liskov — every enum variant routes to a real `@StringRes`; no `0` or `TODO` returns. `Lifestyle.labelString(UnalignedPrivate, strict-X)` falls back to "routine" for Liskov-safe rendering even though that path should never surface in UI.
6. ✅ R.X.6 Import direction — `ui/a11y/` depends on `R` + `ui/wizard/`, `ui/scaffold/`, `ui/tasks/`, `ui/repos/`. All within the `ui/` layer. No upward imports from data/git/store.
7. ✅ R.X.7 ISP in Compose — call sites take a single enum value, call `.labelString()`, get a String. No god-state threading.
8. ✅ R.X.8 Test discipline — `EnumLabelLocalizationTest` (10 tests, walks all 12 enums), `LocaleFallbackTest` (4 tests), `TalkBackLabelTest` (4 tests), `LargeTextSnapshotTest` (3 tests). 344 total tests pass.
9. ✅ R.X.9 AVD smoke — `:app:installDebug` on `emulator-5554`; baseline screenshot `/tmp/skb_baseline_sm.png`; 200% font-scale screenshots `/tmp/skb_2x_sm.png` (Schedule) + `/tmp/skb_2x_tasks_sm.png` (Tasks); en-GB per-app-locale via `cmd locale set-app-locales`, screenshot `/tmp/skb_gb_settings_sm.png`. No `AndroidRuntime:E` in `adb logcat -d -t 200`.

**Findings backlog from this pass:**

- **F38 — Compose `createComposeRule` deprecation.** Compile warnings on `LargeTextSnapshotTest.kt` and `TalkBackLabelTest.kt` recommend migrating to `androidx.compose.ui.test.junit4.v2.createComposeRule` (uses `StandardTestDispatcher` for deterministic coroutine ordering). **Action:** repo-wide sweep when adopting v2 test APIs; not urgent — existing tests are deterministic without explicit advance. **Priority:** low. **Status:** tracked.
- **F39 — `PronounSet.label` and `TemplateRegistry` humanize output are wire-format.** Left out of the `labelRes` sweep on purpose — pronouns ("he/him") are stable identifiers, and the wizard scaffolder writes humanized atom labels ("Brush teeth") into recurrence rule titles persisted to disk. Localising them would break repo round-trip. Documented in `docs/plans/translations.md`. **Status:** by design, no action.
- **F40 — Per-repo locale override deferred.** The wizard could in principle ship one repo with `locale = "en-GB"` in `identity.toml` and another with `locale = "de-DE"`, switching the UI per active repo. Not implemented — Android's per-app locale API is global; per-repo would need a Compose-level `LocalContext` override on every screen. Not load-bearing today. **Priority:** scheduled. **Status:** tracked.

---

## How this doc evolves

- Each phase completion adds findings (or a "no findings" line) under "Audit pass YYYY-MM-DD".
- When a finding rolls up to a real refactor effort, promote it to its own R.A / R.B / … phase here (mirroring tonearmboy's pattern), with sub-step checkboxes and shipped-in-commit annotation.
- When the codebase reaches a steady state (post-Round-1, post-Round-2 polish), this doc moves to `## Status: ✅ DONE` — but never deleted (the discipline survives).

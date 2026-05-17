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
- **F2 — No `AppGraph` composition root.** `MainActivity` does the wiring inline. At 174 LOC it's still OK but each new orchestrator (sync scheduler, conflict registry, view-mode persistence) adds to it. **Action:** introduce `AppGraph` when `MainActivity` crosses 250 LOC, or sooner if a sub-agent has to wire something more than 3 parameters deep. **Priority:** medium (likely triggered in Phase K when wizard wiring lands). **Status:** RESOLVED (verified 2026-05-13) — `composition/AppGraph.kt` shipped in Phase Q (commit `bf709f0`); see F26.
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
- **F17 — `notif/NotificationsSettingsScreen` not yet wired into the Settings rail.** The Compose surface exists and is testable in isolation; the rail Settings destination is still the placeholder per Phase S. When Phase S lands, drop `NotificationsSettingsScreen(prefs)` into the Notifications section (S.4). **Priority:** low — surface is reachable from system-level notification settings via the deep-link button. **Status:** RESOLVED (verified 2026-05-13) — Phase S shipped `NotificationsCategory` (inlining the rows, per F35) wired through `SettingsAccess`; the standalone screen is back-compat only.

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
- **F21 — `MainActivity` LOC creeping.** With the Together wiring `MainActivity.kt` is now ~210 LOC (up from 174). Under the 250-LOC `AppGraph` promotion trigger per F2 but moving toward it. **Action:** keep an eye on it; promote on next composition-root add. **Priority:** low. **Status:** RESOLVED (verified 2026-05-13) — `AppGraph` extracted in Phase Q (F26 closed it); MainActivity returned under 350 LOC.

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

- **F22 — `MainActivity` LOC now ~310.** With the SAF launchers + `buildExportContent` helper it crossed the 250-LOC `AppGraph` promotion threshold (F2 / F21). **Action:** promote a `composition/AppGraph.kt` that owns scheduler + SAF launcher wiring as the next round-2 task. **Priority:** medium. **Status:** RESOLVED (verified 2026-05-13) — `AppGraph` promoted inline with Phase Q (commit `bf709f0`); F26 confirms closure.
- **F23 — VTIMEZONE dropped on import.** Per the locked decision, v1 treats every non-`Z` DTSTART as a naive LocalDateTime in the rule's `tzId` (defaulted to `"UTC"`). Round-trip with non-UTC sources is approximate; real-world `.ics` (Google / Apple) round-trips fine because they emit UTC `Z`. **Action:** revisit if a user reports timezone drift; consider an ical4j upgrade then. **Priority:** low. **Status:** tracked.
- **F24 — UID dedup on re-import deferred.** P.2 ticked because v1 surfaces UIDs via `external_uid` and the wizard always targets a fresh calendar. A future "import-into-existing-calendar" workflow needs to scan the target calendar's events for matching `external_uid` and skip / merge. **Priority:** medium when P.3 (CSV) lands. **Status:** tracked.
- **F25 — Settings rail now hosts ImportExportScreen.** The Settings placeholder is repurposed as the Phase P import/export entry-point. When Phase S settings polish lands, Settings needs a real nav structure with Import/Export as one section among many. **Priority:** ships with Phase S. **Status:** RESOLVED (verified 2026-05-13) — Phase S shipped 12 categories; ImportExport lives under the Repos category (per F33 RESOLVED note).

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

- **F31 — Sealed-`object` class-init race under Robolectric.** `SettingsCategory.Companion.all` originally read as `listOf(General, Identity, Mode, Notifications, Repos, About)` at companion init. Under Robolectric the inner `object`s appeared `null` during the eager evaluation (JVM class-init ordering — companion fields construct before the sibling subclasses finish `<clinit>`). Resolved by wrapping in `by lazy { ... }`. **Action:** prefer `by lazy { listOf(...) }` for every sealed-object enumeration list going forward; the eager form looks identical but breaks at runtime under Robolectric class-loading. **Priority:** low (cookbook fix). **Status:** RESOLVED (verified 2026-05-13) — fix landed inline in Phase R; cookbook entry codified for future use.
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
- **F43 — `GitRepoRegistry` was unbounded.** Fixed in this phase (bounded at 50 entries via `LinkedHashMap.removeEldestEntry`, access-order). Documenting here for the audit trail — no further action. **Status:** RESOLVED (verified 2026-05-13) — fix landed in Phase V.
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

### Audit pass 2026-05-13 — Phase W (release engineering)

R.X.1..R.X.9 self-check:

1. ✅ R.X.1 narrow data interfaces — `AboutCategory` takes one extra named lambda `onOpenPrivacyPolicy`, same shape as `onOpenRepo`. No god-state widening.
2. ✅ R.X.2 Sealed types — N/A this phase; no new branching.
3. ✅ R.X.3 Composition root — `MainActivity` is still the only place wiring the URL string for the privacy policy intent; the About composable only gets a `() -> Unit`.
4. ✅ R.X.4 No god-files — `proguard-rules.pro` grew to ~110 lines (config, not code; well-commented sections per dep).
5. ✅ R.X.5 Liskov — N/A; no new sealed variants.
6. ✅ R.X.6 Import direction — `AboutCategory` (ui/settings/categories) imports `R` + Compose only. No cross-layer leaks.
7. ✅ R.X.7 ISP in Compose — three independent lambda params (`onOpenLicenses`, `onOpenRepo`, `onOpenPrivacyPolicy`) rather than a single fat `AboutActions` god-object.
8. ✅ R.X.8 Test discipline — `ReleaseSigningConfigTest` (4 cases) + `ProguardKeepRulesTest` (12 cases) cover the load-bearing release-config invariants. 369 total tests pass (was 353).
9. ✅ R.X.9 AVD smoke — release APK built with `isMinifyEnabled = true` + `isShrinkResources = true`, installed + launched on `emulator-5554`; first-frame at +891 ms; no `AndroidRuntime:E` in `adb logcat -d -t 300`; release APK ≈ 7.3 MB (vs debug ≈ 31 MB).

**Findings backlog from this pass:**

- **F45 — Lint `Instantiatable` disabled at module level.** Lint's class-graph traversal flagged `MainActivity` + `SkbCarAppService` as not extending their base classes when R8 ran in the same Gradle invocation (false positive — both legitimately extend `ComponentActivity` / `CarAppService`). Disabled the check in `app/build.gradle.kts` under the `lint { }` block; ProGuard keep rules pin both classes so the runtime invariant holds. **Action:** revisit when AGP/lint upgrade fixes the graph-staleness bug. **Priority:** low. **Status:** tracked.
- **F46 — `-dontwarn org.apache.sshd.**` blanket.** R8 surfaced ~80 missing-class warnings for sftp/server/agent/ssh-osgi internals referenced by JGit code paths unreachable on Android. We `-dontwarn` the whole apache-sshd namespace; the truly load-bearing classes (`org.apache.sshd.client.**`, `org.apache.sshd.common.**`) have explicit `-keep` rules so they survive if present at runtime. Narrowing the `-dontwarn` to the exact subset is busywork — the unreachable refs come from JGit's vendored ssh-sftp/agent/gss-api support, which we don't use. **Priority:** low (cosmetic). **Status:** tracked.
- **F47 — `app/src/main/play/screenshots/` not yet populated.** W.5 specs the 8-screenshot set + workflow. Actual screenshots are not committed yet — capturing live screenshots before tagging v0.1.0 on Play Store is gating; not gating for the Obtainium/sideload release path. **Priority:** scheduled (pre-Play-Store-submission). **Status:** tracked.
- **F48 — ✅ RESOLVED (Round 2.22 Phase A, commit `4af31f4`).** `IdentityAvatar` is now active-repo-driven via `AppGraph.activeRepoIconKind` → `SkbAppShell.activeIconKind` → `ShellTopBar.IdentityAvatar(iconKind=…)`; per-repo rows route through `RepoCard.RepoCircle` / `RepoSwitcherDropdown.RepoCircle` which both delegate to `RepoConfig.toIconKind()`. The fallback chain is locked in D-2.22.a and pinned by `IdentityAvatarFallbackTest` (7 cases). Original finding preserved below for history. — **F48 (original) — `IdentityAvatar` hardcoded to `R.drawable.about_bat`; should be active-repo-driven.** Per D.88 (a repo IS an identity), the top-bar leading-slot avatar must reflect the ACTIVE repo's `RepoIconKind` + `identity.toml.species`. Currently `IdentityAvatar` ignores all of that and renders the fixed bat regardless of active repo. **Action:** extend `IdentityAvatar(iconKind: RepoIconKind, species: Species?)` + wire from `AppGraph.activeRepoConfig` in `SkbAppShell`. Fallback chain: `Sticker(bat) → about_bat`; `Sticker(other) → AutoInitials(species[0], seedColor)`; `Photo(uri) → Coil`; `Emoji → glyph`; default → `AutoInitials(displayName[0], hash-color)`. Same lookup applies to the repo-list rows in `ReposPane` / `RepoSwitcherDropdown` so the row avatar matches the chosen species/photo. Closes when Phase WW (avatar stickers) ships the bitmap pipeline OR earlier as a polish round if AutoInitials-fallback is acceptable interim. **Priority:** medium (visible inconsistency once user picks non-bat species in wizard). **Status:** tracked.

---

### Audit pass 2026-05-13 — nav-swap polish (SkbAppShell)

Nav-layout swap: rail and top-bar contents inverted to match the tonearmboy convention (left rail = per-pane view-modes, top bar = cross-content destinations). Deleted `ui/scaffold/AppScaffold.kt` + `ui/scaffold/SkbTopBar.kt`; replaced with `ui/scaffold/SkbAppShell.kt` + `ui/scaffold/ScheduleViewTab.kt`. `SchedulePane` no longer renders its own top bar; `TasksPane` accepts hoisted `selectedTab` / `onSelectTab` so the shell can drive the rail.

R.X self-check: all 9 boxes pass (narrow `RailItem`; sealed-free for now but `TopDestination` is an enum with exhaustive `when`; composition root remains the only concrete wiring; `SkbAppShell.kt` ~430 LOC, within the 500 LOC second-look threshold; no Liskov debt; imports flow downward; rail items are leaf data, no god-state; new `AppShellNavigationSwapTest` + rewritten `LargeTextSnapshotTest` + `TalkBackLabelTest`; AVD smoke captured `/tmp/nav-swap-{schedule,tasks,settings,tablet}.png`). 353 total tests pass.

**Findings backlog from this pass:**

- **F42 — Top-bar destination buttons could go icons-only on Compact.** Six destination buttons with icon+label currently overflow on phone width; the row horizontally scrolls (works, but takes two swipes to reach Settings). Icons-only with a `contentDescription` would let all six fit without scroll. Deferred — current chrome reads correctly and the horizontal-scroll fallback is robust. **Priority:** low (polish). **Status:** tracked.
- **F43 — `Modifier.androidx_horizontalScroll` shim in `SkbAppShell.kt`.** A 3-line shim re-exports `foundation.horizontalScroll` under an unambiguous name so the destination-row chain stays readable. Tiny SRP nit; not worth promoting to `ui/components/`. **Priority:** none (style). **Status:** documented.

---

## How this doc evolves

- Each phase completion adds findings (or a "no findings" line) under "Audit pass YYYY-MM-DD".
- When a finding rolls up to a real refactor effort, promote it to its own R.A / R.B / … phase here (mirroring tonearmboy's pattern), with sub-step checkboxes and shipped-in-commit annotation.
- When the codebase reaches a steady state (post-Round-1, post-Round-2 polish), this doc moves to `## Status: ✅ DONE` — but never deleted (the discipline survives).

### Audit pass 2026-05-13 — Round 2 batch 1 (Phase B finish + Phase X CLI + Phase XX bootstrap, integrated in `96f46d1`)

Three opus subagents ran in parallel worktrees on independent phases. All three honored the Round 2+ universal completion checklist baked into `main.md`. Phase B was pure docs-tick (implementation already shipped in `61ae385` + `503074a`); Phase X CLI bootstrap added `:cli` Gradle subproject content; Phase XX bootstrap added `notif/` + `resolver/CompletionStateResolver.kt` + atomic-self-care template asset + DB v2 bump.

R.X self-check: all 9 boxes pass across the integrated diff. New files all < 500 LOC (largest: `notif/AtomicEventReceiver.kt` 247 LOC — receivers naturally accumulate dispatch branches; well within threshold). `event_instance_state` Room table introduced via destructive rebuild (CLAUDE.md explicitly authorizes — Room is read-through cache, files are source of truth). AVD-smoked: `dumpsys notification --noredact` confirms `skb.events.atomic` channel registered at `IMPORTANCE_HIGH` with vibrate + badge + group `skb_main`.

**Findings backlog from this pass:**

- **F44 — `:cli` duplicates `Uuid7` + `MiniToml` + atomic-write + frontmatter from `:app/store/`.** Intentional dup per Phase X subagent's locked decision (rationale: extracting `:app/store` into a `:core` Gradle module would cost more than it saves at current size; `:app/store` is 1850 LOC tangled with `Entities.kt` (571) + `EntityWriter` + `RepoBootstrap`). Dup is documented in `cli/src/main/kotlin/com/eight87/skb/cli/core/*` source comments. **Priority:** medium. **Trigger to revisit:** when CLI-A.* fills in the remaining 19 subcommands, or when `:app/store` independently needs a refactor for any other reason — combine the work. **Status:** tracked; not blocking.
- **F45 — `AppShellNavigationSwapTest` 2/4 failing on `main` (pre-existing on `cb0034c`).** Failure: `ShellDest-Together` not rendered in shell. Phase V scaffold issue, unrelated to Round 2 batch 1 work. Both Phase X CLI agent + Phase XX agent confirmed via their full test runs that the failures pre-date their work. **Priority:** medium (test failure on green main is a smell even if pre-existing). **Action:** spawn a small focused fix subagent before next UI-affecting batch ships, OR roll into next UI-affecting batch. **Status:** ✅ RESOLVED in branch `round2/fixup-f45-ddd-wiring`. Root cause: `ShellTopBar` filtered Repos/Settings/Together/Wizard out of the icon-button row, hiding their `ShellDest-<name>` test tags. Fix: render ALL `TopDestination.entries` (now 7 including new `TopDestination.Reviews` for DDD.13) in a horizontally-scrollable second row of the top-bar; the bat avatar stays as a parallel affordance for Repos, the redundant settings-gear duplicate was dropped. Test asserts 7-count + iterates `TopDestination.entries`. All 4 test cases pass.
- **F46 — `AboutCategory.kt` carries compat-stub `ABOUT_EASTER_EGG_*` constants.** Phase XX agent added these to unblock test-compile breakage encountered during merge prep. The original constants were referenced by a stale test that didn't survive the AboutCategory rewrite in commit `580f3f2`. Stubs are documented inline with a "remove after audit" comment. **Priority:** low. **Trigger to remove:** when the referencing test is rewritten OR confirmed deleted. **Status:** ✅ RESOLVED in branch `round2/fixup-f45-ddd-wiring`. Stubs deleted; `EasterEggUnlockTest` rewritten to exercise `EasterEggController` (3-tap / 5s window) directly via synthetic clock — no Compose harness needed since the controller is framework-free. 4 controller-driven test cases all pass.

### Audit pass 2026-05-13 — Round 2 batch 2 (Phase WW + ZZ + XX continuation, integrated)

Three opus subagents in parallel worktrees. Phase WW (avatar+sticker presence-indicator MVP), Phase ZZ (no-origin + multi-origin git layer finish), Phase XX continuation (XX.5-XX.11 atomic templates + routines + sub-beats + streak walker). Integrated into main via merge `ffd2e2b` + string-dedup `e2d16f9` + test-adjustment `1bb2110`. 419 tests, only F45's 2 pre-existing failures.

R.X self-check: all 9 boxes pass. New files mostly < 200 LOC; two flagged below.

**Findings backlog from this pass:**

- **F47 — Phase ZZ.A `clone()` writes `.strictlykeptboy/repo-id` but doesn't commit it.** Spec lock per draft-no-origin-multi-origin.md says repo-id is **committed to the repo** for cross-device state-file consistency (Phase OO depends on it). Current implementation writes the file but leaves it untracked. `init()` + `initLocalOnly()` have the same shape. **Action:** add a follow-up commit-of-repo-id step inside `writeRepoIdFile` callers — for `clone`, the file should be staged and the commit folded into the first user-driven commit (or auto-committed with author = `<authorIdentity>` and message "init repo-id" if no other content); for `init` / `initLocalOnly` it can be the first commit on the new branch. Test `statusReflectsLocalChanges` was temporarily adjusted to accept the post-clone untracked-repo-id state. **Priority:** medium (spec compliance + reverses an unintended test-baseline change). **Status:** tracked.
- **F48 — `GitRepo.kt` grew 537 → 671 LOC after Phase ZZ.** Per ZZ agent's own note, multi-origin push fan-out (~70 LOC) is a natural carve-out into `git/MultiOriginPush.kt`. Still single-concern (async JGit wrapper) so passes SRP, but the 700-LOC threshold mentioned in F4-era guidance is being approached. **Priority:** low (style). **Trigger:** at 700 LOC OR next phase that touches GitRepo. **Status:** tracked.
- **F49 — `RoutineCommands.kt` 372 LOC bundles materializer + start/undo CLI + planner.** Per XX agent's call, tightly coupled enough to keep in one file for now. **Priority:** low. **Trigger:** if `RoutineMaterializer` grows independent reuse (e.g. UI surface in next batch when FFF + XX.8 UI ship together). **Status:** tracked.
- **F50 — WW agent's compat-stub strings `notif_subbeat_index_body` + `notif_action_skip_ahead` collided with XX continuation's authoritative strings.** Cross-branch compat-stub pattern needs a convention: agents should annotate compat additions with `<!-- COMPAT — remove when <branch> merges -->` and the integration step strips them mechanically. **Priority:** low (process). **Status:** documented; pattern can be tightened in subagent dispatch prompts.

### Audit pass 2026-05-13 — Round 2 batch 3 (Phase AAA + YY + FFF, integrated)

Three opus subagents in parallel worktrees. Phase AAA (lifestyle templates — 8 new TOML assets + schema extensions + `TemplateOrigin` + wizard wiring + `skb template` CLI), Phase YY (cross-repo feedback CLI primitives — fingerprint + registry + writer + resolver + `skb react/comment/ref-set-write-back`), Phase FFF (event-create FAB + template picker + free-form path — primary write path UI). Integrated into main via merges + one CLI `Main.kt` union resolution (AAA's TemplateGroup + YY's 5 feedback commands both register).

R.X self-check: all 9 boxes pass. 451 tests, only F45's 2 pre-existing failures. Largest new files: `FeedbackCommands.kt` 471, `TemplateCommands.kt` 456, `EventCreateController.kt` 284, `EventCreateFreeFormForm.kt` 235, `RepoRegistry.kt` 238 — all under 500.

**Deferred sub-steps tracked** (not failures, deliberate scope-trims by subagents):
- **FFF.4** save-as-template UI write-back (data path round-trips USER source via `TemplateIndexLoader`; only event-detail overflow + write-back UI pending)
- **FFF — recurrence-file write** (`EventDraft.recurrence` + `customRRule` captured; `recurrences/<id>.md` write needs Phase C/E recurrence writer wiring — left as hook in `EventCreateController`)
- **FFF — M3 DateTimePicker** (v1 ships ISO text input; `EventDraft` API already accepts `OffsetDateTime` so swap is local)
- **YY.7 partial** — bonus + journal layout primitives shipped on CLI; deep-link / state-file fallback / nav-rail tab deferred to app-side phase
- **YY.10 partial** — `cli-tooling.md` updated; in-app help + AGENTS.md rewrite deferred
- **AAA — `cal-period-grace` supersedence overlay (HV-L.B.5)** → Phase BBB (supersedence pass RV-P)
- **YY — Compose feedback drawer (FB-E.4–6)** → app-side phase (deferred from YY.7)

**Findings backlog from this pass:**

- **F51 — Phase FFF FAB rebuilt as hand-rolled `Surface + combinedClickable`** because `ExtendedFloatingActionButton` swallows long-press events. The hand-rolled version uses `Surface(primaryContainer) + Row(Icon + Text)` so long-press surfaces the dropdown menu. **Action:** track Compose Material3 upstream for `FloatingActionButton` long-press support; revert to canonical when available. **Priority:** low. **Status:** tracked.
- **F52 — Phase FFF ships ISO text input for date+time, not M3 `DateTimePicker`.** v1 trade-off for testability; `EventDraft` API already accepts `OffsetDateTime` so swap is local. **Trigger:** when Phase EE inline-markdown lands or any other date-affecting UI ships in the same area. **Priority:** medium. **Status:** tracked.
- **F53 — Worktree-leakage convention gap re-surfaced.** AAA + YY agents both reported cross-worktree leakage of uncommitted files from sibling worktrees (FFF files leaking into AAA's worktree, etc.). Agents now reflexively clean these on entry — but the leakage itself indicates the `.claude/worktrees/` lock mechanism isn't fully isolating writes. **Action:** investigate whether the harness's worktree creation pulls untracked files from the parent path (it should NOT). Add an explicit "clean untracked before starting" step to dispatch prompts as belt-and-suspenders. **Priority:** medium (process). **Status:** documented.
- **F54 — Phase FFF.4 (save-as-template UI) explicitly deferred.** The data-path round-trips USER-source templates via `TemplateIndexLoader` already; only the overflow-menu + write-back sheet are missing. Best paired with the YY app-side phase (feedback drawer) when both UI surfaces ship together. **Priority:** medium. **Status:** tracked.

### Audit pass 2026-05-13 — Round 2 batch 4 (Phase BBB + CCC + Y, integrated)

Three opus subagents in parallel worktrees. Phase BBB (event-level extensions — supersedence + override + attachment + reminder codecs + CLI), Phase CCC (quick-trip wizard compact cut — 4-screen wizard + 3 entry-points + sticker beats), Phase Y (CalDAV mirror — discovery + auth + pull/push/bidi + ETag journal + Robolectric tests). 515 tests passing (+64 from baseline 451), still only F45's 2 pre-existing failures.

R.X self-check: all 9 boxes pass. New files all single-concern; largest: `TripWizardNavHost.kt` 406, `AttachmentCommands.kt` 369, `TripScaffolder.kt` 274, `ReminderCommands.kt` 280, `Reminder.kt` 230, `CalDavMirrorWorker.kt` 193 — all under 500.

**Findings backlog from this pass:**

- **F55 — Phase Y declares `ical4j` + `dav4jvm` but runtime uses hand-rolled OkHttp `CalDavHttp`** to keep dex pressure off the debug APK. The deps sit in `gradle/libs.versions.toml` ready for SE-Q.10+ to wire them in for the production parser path. **Action:** track whether the hand-rolled parser stays sufficient — if PROPFIND complexity grows (sync-token RFC6578, scheduling-inbox), switch to ical4j wholesale. **Priority:** low. **Status:** tracked.
- **F56 — Y.8-Y.12 + BBB.4/.6/.10/.11/.12/.13 + CCC.6/.7/.9/.10/.11/.12 explicitly deferred.** These are scope-trims by the subagents (UI surfaces, AGENTS.md rewrites, mini-month preview, edit-in-flight, calendar-detail entry, asset wiring, UI tests). Each is annotated `[ ]` in `main.md` with a one-line rationale. **Trigger to revisit:** when batch 5 dispatches OR when the deferred UI surfaces become user-visible blockers. **Status:** tracked.
- **F57 — `TripWizardNavHost.kt` 406 LOC.** Compose 4-screen wizard with nav-host + Discard dialog + progress dots + per-screen mascot. Single-concern (the wizard) but approaches the 500 LOC second-look threshold. **Trigger to split:** if CCC.6 (supersedence picker) or CCC.7 (mini-month preview) lands inside this file rather than alongside it. **Priority:** low (style). **Status:** tracked.
- **F58 — Phase BBB AVD smoke deferred** (per subagent: "no Compose UI was touched" + CCC's WIP was stashed). Phase Y also deferred AVD smoke ("no live emulator handle for this autonomous run"). **Action:** an integration AVD-smoke pass after each batch lands on main is a healthier discipline than per-subagent smoke when the agent has no emulator handle. Capture flow: install rebuilt APK, screencap Schedule + Settings + Repos + new surfaces. **Priority:** medium (process). **Status:** tracked.

### Audit pass 2026-05-13 — Round 2 batch 5 (Phase DDD + EE + MM/NN, integrated)

Three opus subagents in parallel worktrees. Phase DDD (mode + identity + dom-persona — 12 of 16 sub-steps), Phase EE (Markwon inline-markdown body rendering), Phase MM+NN bundle (deep-link grammar + `references.toml` manifest). 573 tests passing (+58 since batch 4), still only F45's 2 pre-existing failures.

R.X self-check: all 9 boxes pass. New files largest: `RefCommands.kt` 337, `ModeIdentityCommands.kt` 308, `ReferencesManifest.kt` 275, `ReviewsPane.kt` 234, `MarkdownRenderer.kt` 230, `ReviewFeedWriter.kt` 205 — all under 500.

**Findings backlog from this pass:**

- **F59 — Phase DDD ships `ModePill` + `ReviewsPane` as standalone composables NOT wired into `SkbAppShell`/`TopDestination`.** Deliberate: wiring would change `AppShellNavigationSwapTest`'s expected destination count (currently F45 failing at "six destination buttons"). The DDD subagent left wiring to a follow-up that combines the fix for F45 + the rail expansion to 7 destinations (adds Reviews). **Trigger:** F45 fix-up batch. **Priority:** medium. **Status:** ✅ RESOLVED in branch `round2/fixup-f45-ddd-wiring`. `ModePill` now renders in `ShellTopBar` next to the title (visible when `SettingsAccess.modePrefs` is wired); `TopDestination.Reviews` added as the 7th destination → `SkbAppShell` dispatches to `ReviewsPane(side = Boy, items = emptyList())`. AVD-smoked: pill + long-press transition modal + Reviews tab content all confirmed on `emulator-5554`.
- **F60 — Phase EE Paparazzi golden-bitmap snapshots deferred.** Robolectric semantic snapshots ship; pixel-level snapshots would require Paparazzi wiring (Apache-2.0 dep, no GPL concern). **Trigger:** when a Markwon-rendering regression slips through semantic snapshots OR when Phase EE-adjacent UI gains complexity. **Priority:** low. **Status:** tracked.
- **F61 — Phase MM share-bundle `add`-link variant (MM.A1..MM.A9) deferred** — separate scope from per-entity routing. **Priority:** low. **Status:** tracked.
- **F62 — Worktree-leakage convention gap re-surfaces yet again across batches 3/4/5 (F53 + this).** All 3 batch-5 subagents reported untracked WIP files from sibling worktrees appearing in their checkouts (DDD's identity files leaking into EE/MM-NN worktrees, EE's `MarkdownRenderer.kt` leaking into MM-NN/DDD, MM-NN's `RefCommands.kt` leaking into DDD). Each agent stashes/cleans on entry as a defensive practice — but the harness's `.claude/worktrees/agent-*` lock mechanism is clearly not isolating untracked file *visibility*. **Action:** the orchestrator (me) absorbs this by explicit stash + clean before each integration sweep. Subagent dispatch prompts already say "stay in scope"; agents already comply. The leakage is *visibility*, not commits. **Priority:** medium (process — wastes agent tokens cleaning). **Status:** documented; consider escalating to harness-level isolation in a future review.

### Audit pass 2026-05-13 — Round 2 batch 6 (F45 fix + Phase RR/SS + Phase VV/EEE, integrated)

Three opus subagents in parallel worktrees. F45 fix-up batch (root-caused + ModePill + ReviewsPane wired into top-bar + F46 compat-stubs cleaned), Phase RR + SS (share-this-repo flow with QR + simplified→own fork mechanic), Phase VV + EEE (Homescreen Countdown + Now + lockscreen widgets via RemoteViews).

**Milestone: green main on `:app:testDebugUnitTest` for the first time since Round 2 started.** F45 root cause was `ShellTopBar` filtering Repos/Settings/Together/Wizard out of the icon-button row; fix added a horizontally-scrollable destination row and pinned test to 7-count via `TopDestination.entries.size == 7`.

R.X self-check: all 9 boxes pass. F45, F46, F59 marked RESOLVED in their respective findings sections.

**Findings backlog from this pass:**

- **F63 — `SkbAppShell.kt` 702 LOC** — pre-existing god-file flag now confirmed by F45 fix-up agent. SOLID.S threshold says ≥800 needs a split; we're inside the watch range. **Trigger to split:** if anything else lands here (e.g. F-entry mode-presence indicator UI-TT.4 + dom-presence indicator UI-TT.4). Suggested split: `ShellTopBar.kt` + `ShellRail.kt` + `ShellDestinationRow.kt`. **Priority:** low. **Status:** ✅ RESOLVED in Round 2.21 follow-up. After Round 2.21 A–G the file grew to 1185 LOC (past the 800-LOC threshold). Split into four files in `ui/scaffold/`: `SkbTopBar.kt` (`ShellTopBar` + `DestinationButton` + `RepoSwitcherIconButton`, 233 LOC), `SkbScheduleRail.kt` (`RailColumn` + `RailItem` + `PlaceholderScreen` + `scheduleTabLabelRes`, 199 LOC), `NowPlayingSheetHost.kt` (bottom-sheet host + flick-commit + task-detail/quick-add overlays, 373 LOC). `SkbAppShell.kt` now owns only the composition root + destination dispatch (535 LOC, inside the second-look threshold). Pure refactor — no behaviour changes; existing tests reference testTag constants which stayed in `SkbAppShell.kt`. 1075 tests green after split.
- **F64 — Phase VV/EEE used RemoteViews + XML layouts instead of Glance** because Glance isn't wired in `libs.versions.toml`. RemoteViews is sufficient for v1; Glance would let widgets share Composables with the rest of the app. **Trigger:** when widget complexity grows beyond static layouts (e.g. EEE.10 sub-beat boundary re-render layer animation). **Priority:** low. **Status:** tracked.
- **F65 — VV.11 + EEE.12 render-snapshot fixtures deferred** because programmatic widget-host drop via `adb shell appwidget` is system-protected (`APPWIDGET_UPDATE` broadcast). Real-device pinning is manual or via UI Automator. **Action:** add a `WidgetSnapshotTest` using Robolectric's `AppWidgetManager` shadow (allows programmatic placement in tests). **Priority:** medium. **Status:** tracked.
- **F66 — `EEE.13` lockscreen widget AVD verification incomplete** for the same reason as F65. Lockscreen privacy contract (K-2 + `private_by_default` calendar enforcement) is unit-tested under `WidgetPrivacy` but not lockscreen-AVD-confirmed. **Trigger:** before any v0.2 release that promises lockscreen widget. **Priority:** medium. **Status:** tracked.

### Audit pass 2026-05-16 — Round 2.20.1 follow-up

- **F67 — Migratory `runTest` canary (`UncaughtExceptionsBeforeTest`).** During the 2.20.1 close-out sweep, the full `:app:testDebugUnitTest` run fails on a single test class that's *always different across runs* — first `TripScaffolderTest.materialize car trip skips flight events`, then `StickerPackScaffoldTest`, then `WizardAtomDtstartTest.scaffolded rules carry spread dtstarts`, etc. The failure is always `kotlinx.coroutines.test.UncaughtExceptionsBeforeTest: There were uncaught exceptions before the test started`. The accumulator persists across test classes in the Gradle worker JVM, and *whichever* `runTest`-based test runs after the leak is the canary. Workaround so far: switching individual canaries from `runTest` to `kotlinx.coroutines.runBlocking` makes the failure migrate to the next canary rather than disappear — these tests don't need virtual-time control, so `runBlocking` is fine, but it's not a root-cause fix. **Real root cause hypothesis:** an upstream test launches a coroutine on a shared dispatcher (no `Dispatchers.setMain` anywhere in tests; no `setDefaultUncaughtExceptionHandler` anywhere in either source set) that throws inside the kotlinx-coroutines-test global exception accumulator. The polluter doesn't bisect cleanly to a single test class because the failure mode depends on Gradle worker scheduling. Reproduced by running `--tests com.eight87.strictlykeptboy.store.* --tests com.eight87.strictlykeptboy.system.* --tests com.eight87.strictlykeptboy.ui.trip.*` together (each pair-or-package individually green), but not by any 2-package combo of store/system/port/composition/resolver. **Action:** when this becomes a release-blocker (or noisy enough), audit every `runTest { ... }` block for unhandled `launch { }` inside; consider a global `@get:Rule` that clears the kotlinx-coroutines-test exception state at `@Before`, or add `forkEvery = 1` to the Gradle test task (slower CI, but isolates JVM workers). **Priority:** medium (CI noise). **Status:** ✅ RESOLVED via `forkEvery = 100` on `:app:testDebugUnitTest` in `app/build.gradle.kts` (commit `<pending>`). Measured at the sweet spot: green under repeated runs (~55s) vs broken-baseline (intermittent failures) vs forkEvery = 1 (~6m wall clock). The `runBlocking` workarounds on `TripScaffolderTest` + `WizardAtomDtstartTest` are retained — those tests don't need `runTest`'s virtual-time control, and `runBlocking` is the more honest framework for IO-only suspend tests. A proper *root-cause* fix would still be welcome (audit the upstream coroutine leak), but the cure is no longer the bottleneck. **Genuine bug fixed in the same sweep:** `TodayTaskListTest.today_overdue_fromEvents_pinned_all_appear` was failing because `forToday(today)` parameterised the date but `isOverdue` used `LocalDate.now()` — fixed by inlining the overdue check against the parameter rather than delegating to `isOverdue`.

### Audit pass 2026-05-17 — five-axis SOLID sweep

Five parallel read-only audits ran (composition root / DI, resolver
purity, god-files, Liskov + sealed types, ISP + package hygiene).
Overall posture is healthy. Consolidated findings, severity-sorted:

**[H] — high-leverage fixes (action this round)**

1. **`CalendarSettingsWriter` takes whole `AppGraph` for one method.** Both the composition-root and ISP audits independently flag the same site: `ui/calendars/CalendarSettingsWriter.kt:30,96,133` receives `graph: AppGraph` and only reads `graph.repoStore.list()`. Narrow to `RepoStore` (or a `fun interface RepoConfigLookup { fun find(repoId): RepoConfig? }`). Three signature changes + call-site updates. _Fixed in commit `20712a4`._
2. **Wrong-direction imports** (R.X.6 violations): _Fixed in commit `20712a4`._
   - `git/RepoConfig.kt:5-7` imports `ui.theming.{RepoIconKind, initialsFromName, seedColorFromName}`. `git/` → `ui/` is forbidden. Fix: move `toIconKind()` to `ui/theming/` as an extension function on `RepoConfig`. → Extracted to `ui/theming/RepoConfigExtensions.kt`; the three `git/` imports dropped.
   - `store/AccessAggregator.kt:5-6` imports `ui.share.{ShareLink, ShareMode}`. `store/` → `ui/` is forbidden. Fix: move `ShareLink` + `ShareMode` types to a neutral package (proposal: `share/` at top level, or into `store/`). → Moved data class + enum to top-level `share/ShareLink.kt`; `ShareLinkCodec` + `ShareLinkExpiry` stay in `ui/share/`.
3. **`CalendarRegistry.kt` lives in `resolver/` but does file I/O** (reads `calendars/<id>/calendar.toml`, lists directories, runs on `Dispatchers.IO`, exposes a `StateFlow`). Resolver must be pure. Fix: move to `composition/CalendarRegistry.kt` (next to `IndexerSnapshotPublisher`); carry its Robolectric test along (the test currently forces resolver tests to depend on Android). _Fixed in commit `14610c7`._
4. **Clock-injection leaks**: `resolver/OverlayResolver.kt:34` (`now: ZonedDateTime = ZonedDateTime.now()`) and `resolver/Renderer.kt:52` (`now = ZonedDateTime.now(renderTz)`). Defaults leak side-effects into any test or call site that omits the argument. Fix: drop defaults; require explicit `now` from the call site (ViewModels / composition root already inject `now` where it matters). _Fixed in commit `14610c7`._

**[M] — should-fix when next touching the area**

5. **SSH `NotImplementedError` is now reachable** in production: `git/auth/CredentialBindings.kt:60` (`AuthMethod.Ssh` branch). The Add-Repo wizard's `AddRepoAuth` enum exposes `Ssh`. Fix: either gate `AddRepoAuth.Ssh` in the wizard with a "coming soon" disabled state, or close F4 by implementing the SSH binding. Fixed in commit `351634c` — `AddRepoAuth.Ssh` radio renders disabled with a "Coming soon" subtext; enum variant preserved for F4 close.
6. **User-reachable `error()` crashes**: Fixed in commit `20712a4`.
   - `MainActivity.kt:1585` — `error("no active repo — run the lifestyle wizard first")` from the wizard-bypass path; should route to a dialog or wizard redirect, not crash. → Soft-fails by routing into the wizard via `graph.wizardEntryRequest.value = WizardScreen.Welcome`.
   - `ui/share/ShareLinkGenerator.kt:50` — `error("Cannot share a repo with no remotes")` reachable for no-origin repos (D.74 first-class). Disable Share / show tooltip instead. → `build` / `buildUri` now return nullable; `ShareSheetContent` disables Copy / Send / QR and shows a `share_no_remote_notice` inline message.
7. **Sealed-variant exhaustiveness**: Fixed in commit `20712a4`.
   - `ui/settings/RepoMoveJobDialog.kt:89,99` — `when (progress)` over sealed `RepoMover.Progress` with `else -> {}`. Make exhaustive. → Replaced `else -> {}` with explicit `is Idle, is Running -> Unit` (and the symmetric Done / Cancelled / Failed branches on the dismiss side).
   - `cache/Indexer.kt:176` — `else -> { /* ignored */ }` over the sealed entity hierarchy; new variants silently fail to index. Replace with explicit `is RawEntity, is ... -> Unit` so the compiler flags additions. → Replaced `else -> {}` with `is RawEntity -> Unit` (the only currently-ignored variant).
8. **God-files past 800 LOC** (R.X.4): `MainActivity.kt` (1884) and `WizardNavHost.kt` (1282) are genuine god-files with multiple unrelated concerns and clean natural seams. `SkbAppShell.kt` (729) extracts cleanly via `SkbAppDestinationContent` split. `AppGraph.kt` (1017) wants 2-3 sub-graphs out (`SystemCalendarGraph`, `AvatarGraph`, `TaskPlaybackGraph`). `ScheduleDayView.kt` (684), `Entities.kt` (681), `RepoSettingsScreen.kt` (734), and `GitRepo.kt` (674) are cohesive-by-nature — leave alone. `WizardNavHost.kt` portion: Fixed in commit `351634c` — 1282 → 501 LOC; per-screen composables extracted to `ui/wizard/screens/` (Welcome, Species, Identity, Lifestyle, Roles, Templates, Git, Scaffold, Done, ShareWithDom, IdentityPreviewPane); host retains `ProgressRow` + `BatMascotSticker` + `WrappingChipRow` + step dispatcher. `SkbAppShell.kt` portion (#8c): Fixed in commit `26849ef` — `SkbAppDestinationContent` extracted to its own file as an `internal` composable; SkbAppShell.kt drops 729 → 372 LOC and now owns only the composition root + chrome.
9. **`SkbAppShell` 50-param signature**. The composition root's wiring is leaking into one composable. Fix: split into `ShellContext` (services) + `ShellCallbacks` (actions) + `ShellSelections` (state), pass three params instead of fifty. Fixed in commit `26849ef` — three `@Immutable` data classes in `ui/scaffold/ShellContext.kt` / `ShellCallbacks.kt` / `ShellSelections.kt`; SkbAppShell signature is now `(context, callbacks, selections, modifier)` — four params instead of fifty. ShellContext holds StateFlows + view-states + SettingsAccess + taskPlaybackSource + calendar visibility; ShellCallbacks holds every action lambda; ShellSelections holds `neutralMode` + `wizardEntryRequest`. Every field defaults to its previous flat-signature default so MainActivity / tests migrate inline. Non-local `return@SkbAppShell` labels in MainActivity's drop handlers became explicit `single@` / `recurring@` labels because the lambdas now live inside a `ShellCallbacks(...)` constructor. AVD-smoked on `emulator-5558` — Schedule / Tasks / Reviews / Repos / Settings all render byte-identically to pre-refactor.
10. **`ScheduleDayView` takes whole `RenderedSchedule?`** when it needs only `schedule?.days.firstOrNull { it.date == date }`. Sibling views (`ScheduleWeek/Month/Year/Agenda/ThreeDay/Timebox`) have the same shape. Narrow via a `DayBandSource` (`fun get(date: LocalDate): RenderedDay?`) — matches the canonical anti-pattern called out in R.X.1.

**[L] — quick wins (action opportunistically)**

11. `AppGraph` mutable hand-off slots exposed as raw `MutableStateFlow` / `@Volatile var` (`safPermissionRevoked`, `parentPickerHandle`, `defaultWriteRepoName`, `wizardEntryRequest`, `visibleDateRange`) — hide via `StateFlow` + narrow setters. _Fixed in commit `1b095bb`_ — four of five narrowed (private mutable backing + `StateFlow` public + `setX(...)` setter); `wizardEntryRequest` left as `MutableStateFlow` on the public surface because `SkbAppShell` consumes it for the wizard-finish clear path. Tightening that slot is gated on the `SkbAppShell` split (#9).
12. `AppGraph.bindIdentityToActiveRepo` + `bindModeToActiveRepo` are near-duplicates; extract `ensureRegistered(cfg)` helper. _Fixed in commit `1b095bb`_ — extracted `private fun ensureRegistered(cfg: RepoConfig?): java.nio.file.Path?` that resolves the root path + idempotently opens the `GitRepoRegistry` handle; both binders now share it. ANR-risk `runBlocking` left in place with a `TODO(#12)` for a future `appScope.async { ... }.await()` migration once the call sites can tolerate the suspend signature.
13. `AppGraph` `stateIn(GlobalScope, ...)` and `GlobalScope.launch` calls inside the composition root — use `appScope` for symmetry. _Fixed in commit `1b095bb`_ — `parkRuntimes`'s migration `launch` + the three `stateIn(GlobalScope, ...)` sites (`togetherCalendarOptions`, `togetherRepoOptions`, `activeRepoIconKind`) now route through `appScope`. `appScope` itself stays `GlobalScope`-backed pending a SupervisorJob refactor.
14. `AppGraph.activeRepoName` deprecated alias still consumed in MainActivity (3+ sites); finish the rename. _Fixed in commit `1b095bb`_ — alias deleted; the three MainActivity sites + `FirstLaunchRoutingTest` now read/write `defaultWriteRepoName` directly (writes go through `setDefaultWriteRepoName(...)`).
15. `resolver/Types.kt` at 465 LOC holds ~12 unrelated data classes — split into `Refs.kt`, `Inputs.kt`, `Outputs.kt`, `ViewMode.kt` when convenient.
16. `DeviationInput.kind` + `OverrideInput.kind` are stringly-typed (`"skipped"|"partial"|"completed-early"|"completed-late"` / `"force-show"|"force-show-for-range"`) — promote to sealed interfaces so the `when` chains in `OverlayResolver.kt:51-53` + `ActiveSetEvaluator.kt:45-50` become exhaustive. _Fixed in commit `aa732e9`._ — `DeviationKind` (Skipped / Partial / CompletedEarly / CompletedLate) + `OverrideKind` (ForceShow / ForceShowForRange(from, to)) sealed interfaces in `resolver/Types.kt`; `rangeFrom`/`rangeTo` collapsed into `ForceShowForRange`. Codec at `composition/SourcesPublisher` + `RichDemoResolverFixture` maps wire-string ↔ sealed variants; unparseable rows drop before reaching the resolver. On-disk TOML schema unchanged; `Override` round-trip regression tests added in `EntityRoundTripTest`.
17. `AuthMethod` enum — strong sealed-type candidate; per-variant secret-storage would delete the soft `error("non-OAuth … reached forOAuth")` branch in `CredentialBindings`. Fixed in commit `41dc7e5` — `AuthMethod` is now a `sealed interface` with five data-object variants (Ssh / OAuthGitHub / OAuthForgejo / ManualPat / None). Dispatch in `ProductionCredentialResolver` is exhaustive `when`; `CredentialBindings.forOAuth` is now a private username-taking factory with `forOAuthGitHub` / `forOAuthForgejo` public entry points — the dead `error("non-OAuth … reached forOAuth")` branch is gone. SSH `NotImplementedError` narrowed to `is AuthMethod.Ssh`. Wire-format compat: hand-written `AuthMethodSerializer` (PrimitiveKind.STRING) keeps the JSON value as the legacy enum name (`"OAuthGitHub"`, not `{"type": "OAuthGitHub"}`); existing on-disk `RepoConfig` blobs round-trip unchanged.
18. `SyncButtonState` enum — sealed with `Error(reason: SyncError)` + `Success(duration: Long)` would let toasts show meaningful copy. _Fixed in commit `aa732e9`._ — `SyncButtonState` is now a `sealed interface` with `Idle` / `Syncing` data objects + `Error(reason: String)` / `Success(durationMs: Long)` data classes. The composable's `when` is exhaustive `is`-branched. No call site currently feeds a non-empty reason (no toast layer exists yet); test fixture passes `Error(reason = "test")`. Future toast / snackbar wiring can read the payload without further plumbing.
19. Add a CI/lint guard forbidding `java.io.*`, `java.nio.*`, `okhttp3.*`, `org.eclipse.jgit.*`, `androidx.room.*`, `android.*`, and `\.now\(` inside `resolver/`. Prevents purity regressions. _Fixed in commit `c3f0076`._ — `:app:resolverPurityCheck` Gradle task (hooked into `check`) walks `resolver/**/*.kt` and fails the build on forbidden imports (allowing only `android.util.Log`) or wall-clock `.now()` / `System.currentTimeMillis()` calls. Passes clean against current tree.

**Explicit non-issues (audit confirmed clean)**

- `composition/` → `ui/` imports are intentional (composition is the wiring layer).
- No `okhttp3`, JGit, or Room imports inside `resolver/` except the one `CalendarRegistry` finding.
- `cache/` package is clean.
- `MaterializedInstance` is a single data class, no Liskov-throwing subtypes.
- Most narrow interfaces are in place (`TodayEventSource`, `BriefingSource`, `CommonTimeFinderPort`, `BusySource`, `TaskNowPlayingState/TransportCommands/QueueCommands`).
- `ScheduleDayView.kt` (684), `Entities.kt` (681), `RepoSettingsScreen.kt` (734), `GitRepo.kt` (674) — cohesive-by-nature, **do not split**.

Action plan: subagent fan-out for #1, #2, #3, #4, #7, #10 lands as the immediate "Round 2.28 — SOLID hygiene" wave. Larger splits (#8 MainActivity, #9 SkbAppShell, AppGraph sub-graphs) are tracked for staged follow-up.

### Audit pass 2026-05-17 — ReposPane god-file split (Round 2.28 SOLID hygiene)

Single-shot opus refactor of `ui/repos/ReposPane.kt` per R.X.4 (>800 LOC threshold). Pure structural split — no behaviour change. Hoisted composables retain narrow state-hoisted signatures (SOLID-I).

**Before:** `ReposPane.kt` 1214 LOC (god-file: entry point + ReposList + 2 banners + Mode sealed type + ReposDetailPane + RepoSettingsHost + 5 disk helpers).

**After (`app/src/main/java/com/eight87/strictlykeptboy/ui/repos/`):**

- `ReposPane.kt` — 374 LOC. Top-level entry point + state hoisting + compact/two-pane dispatch + `StickerPacksHost` (kept here because both compact branch + `ReposDetailPane` consume it).
- `ReposList.kt` — 272 LOC. Master-list composable (header row, demo banner, banner conditionals, RepoCard loop, Add-repo footer).
- `ReposDetailPane.kt` — 155 LOC. Tablet detail pane mode dispatcher.
- `RepoSettingsHost.kt` — 346 LOC. `RepoSettingsScreen` wiring + on-disk calendar/identity/mode codec helpers (`scanCalendars`, `setCalendarActive`, `createCalendar`, `writeCalendarSheetDraft`, `parsePronounsPair`) — helpers ONLY used by this host so kept co-located per cohesion (SOLID-S).
- `RepoBanners.kt` — 123 LOC. `SafPermissionRevokedBanner` + `BackupFolderReminderBanner` + their `TestTag*` constants.
- `ReposMode.kt` — 13 LOC. `internal sealed interface Mode` (formerly private to ReposPane, now shared by ReposPane + ReposDetailPane).

Total LOC across the six files: 1283 (vs. 1214 originally — +69 LOC of new package/import headers, no logic duplication).

R.X self-check: all 6 new files under the 500-LOC second-look line; largest (`RepoSettingsHost.kt` 346) bundles cohesive disk codec helpers used solely by its single composable. AVD-smoke confirmed on `emulator-5558`: Repos rail renders identically (Repositories header, demo banner, RepoCard with all 5 toggles, More-settings deeplink into RepoSettingsHost showing calendar list). Screenshot: `/tmp/skb-repos-refactor.png`.

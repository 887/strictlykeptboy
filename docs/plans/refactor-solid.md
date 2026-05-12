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
- **F11 — Enum-property labels still hardcoded in code.** `TopDestination.label` ("Schedule"/"Tasks"/"Repos"/"Wizard"/"Settings"), `ScheduleViewTab.label` ("Day"/"Week"/"Month"/"Agenda"/"Year"), `AddRepoProvider.label()` ("GitHub" / "Forgejo / Gitea" / etc), `QuickAddTarget.TodayEvent.label` / `.TomorrowEvent.label`, and the wizard model enums (`Lifestyle.labelFor`, `Alignment.label`, `Honorific.label`, `PronounSet.label`, `ToneRegister.label`, `EmojiDensity.label`, `SpeciesChoice.label`, `RoleId.label`, `TemplateRegistry`) all expose `.label: String` from non-Composable code. Resourcing them requires Composable-resolution helpers (`@Composable fun TopDestination.label(): String = stringResource(...)`) or context-bound resolvers. **Action:** Phase U accessibility/i18n sweep — introduce `@Composable fun <T>.labelString(): String` extensions per enum and update call sites. **Priority:** medium (blocks full i18n but not user-visible-string-coverage today since the words are short framework labels). **Status:** tracked for Phase U.
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

## How this doc evolves

- Each phase completion adds findings (or a "no findings" line) under "Audit pass YYYY-MM-DD".
- When a finding rolls up to a real refactor effort, promote it to its own R.A / R.B / … phase here (mirroring tonearmboy's pattern), with sub-step checkboxes and shipped-in-commit annotation.
- When the codebase reaches a steady state (post-Round-1, post-Round-2 polish), this doc moves to `## Status: ✅ DONE` — but never deleted (the discipline survives).

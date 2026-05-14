# Audit: Wizard + Identity + Mode + Dom-persona — Round 2.1

> Read-only audit, 2026-05-13. Source-of-intent: `docs/plans/prompts.md`
> (94 prompts, genesis 2026-05-10). Source-of-architecture: `main.md`
> Phases K / DDD / SS / RR / AAA, `decisions.md` D.54..D.86 + K-1..K-7.

## 1. First-run flow — what actually happens

`MainActivity.onCreate` (`MainActivity.kt:96-354`) is the entry point.

1. **Age gate** (`AgeGateScreen` at `ui/wizard/AgeGateScreen.kt:36`,
   prefs at `AgeGatePrefs.kt:15`). `EncryptedSharedPreferences` stores
   `age_confirmed_at`. Accept → composition flips `ageOk = true`.
   Decline → `finish()` exits the activity. Back-gesture is captured
   and treated as decline. **This part works correctly.**
2. **What loads next**: `SkbAppShell` is started at the default
   `selected = TopDestination.Schedule` (the default of
   `rememberSaveable`/initial routing in `SkbAppShell.kt:270-360`).
   There is **no first-launch detection** — the app never auto-routes
   to the wizard when `repoStore.list().isEmpty()`. The user sees an
   empty Schedule pane with a `demo-repo` placeholder name (the
   `AppGraph.activeRepoName` default at `AppGraph.kt:206`).
3. The user must **discover the wizard themselves**: either by
   tapping the top-bar bat avatar (routes to Repos, `SkbAppShell.kt:305`)
   then the "+" button in `ReposPane`, or by tapping `TopDestination.Wizard`
   in the destination row (filtered out of the visible icon-row per
   `prompts.md:1307` / F45 fix-up — Wizard / Together / Repos / Settings
   are no longer in the icon row). Effectively the only path is
   bat-avatar → Repos → "+" → wizard.
4. **When the wizard finishes** (`MainActivity.kt:285-322`):
   `WizardScaffolder.materialize` builds a brand-new repo dir under
   `filesDir/repos/<name>-<8-hex>`, runs `RepoBootstrap.scaffold`
   (writes AGENTS.md / CLAUDE.md / `identity.toml` / `mode.toml` /
   per-role `calendars/<id>/calendar.toml` + onboarding todolist),
   overlays wizard-driven calendar TOML, emits one daily recurrence
   per enabled atom (FREQ=DAILY 09:00 PT15M), seeds 5 onboarding
   standing tasks, appends `[honorific]`/`[tone]`/`[emoji]`/
   `[alignment]`/`[lifestyle]` to `identity.toml`, then `git init`
   + single commit. `RepoConfig` is added to `RepoStore` and
   `activeRepoName` is flipped to the chosen displayName.
5. **Time-to-first-meaningful-screen** — counted in user taps:
   age-gate accept (1) → bat avatar (1) → "+" in Repos (1) → 8 wizard
   screens (Welcome, Species, Alignment, Identity, Lifestyle, Roles,
   Templates, Git, then Scaffold runs, then Done CTA) (9 taps min if
   defaults are accepted) → Done "Open my calendar" (1). **~12 taps
   from cold start to a populated Schedule pane**, but only if the
   user knows to hunt the bat icon. A first-launch user with no
   prior context will end up staring at an empty Schedule with the
   "demo-repo" label and no obvious hint. **This is the dominant
   "doesn't make sense yet" pain point.**

## 2. Wizard screens

`WizardNavHost.kt:71-82` declares the linear screen order. All 10
screens are implemented in the same file as private composables.

| # | Screen | File:line | Produces |
|---|---|---|---|
| 1 | Welcome | `WizardNavHost.kt:289` | nothing — CTA only |
| 2 | Species | `WizardNavHost.kt:317` | `WizardDraft.species` (8 enum values, custom-pack URL deferred) |
| 3 | Alignment | `WizardNavHost.kt:367` | `WizardDraft.alignment` (Dominant / Submissive / Switch / UnalignedPrivate) |
| 3.5 | Identity (praise + pronouns + honorific + tone + emoji) | `WizardNavHost.kt:396` | praise chips (10 defaults), pronouns radio (4), honorific (8, only if Submissive or Switch), tone chips (5), emoji chips (4) + live preview card |
| 4 | Lifestyle | `WizardNavHost.kt:501` | Single/Partnered × free/strict; phrasing derived from alignment via `Lifestyle.labelFor()` |
| 5 | Roles | `WizardNavHost.kt:543` | Multi-select grid of 17 roles; SelfCare locked-on; Kink hidden under neutralMode or UnalignedPrivate |
| 6 | Templates | `WizardNavHost.kt:601` | Per-role chip rows over `TemplateRegistry.visibleTemplatesFor`; smart-default = all-on if unset |
| 7 | Git | `WizardNavHost.kt:655` | Phone-only (default) / Self-hosted Forgejo / GitHub. OAuth flow is stubbed — falls back to phone-only |
| 8 | Scaffold | `WizardNavHost.kt:756` | Drives `WizardScaffolder.materialize` (the only place the wizard touches disk) |
| 9 | Done | `WizardNavHost.kt:784` | "Now card" preview + "Open my calendar" CTA |

**Materialization** (`WizardScaffolder.kt:61-246`) is real, not stubbed:

- Atomic activities: yes — every enabled atom becomes one
  `RecurrenceRule` with `[template]` + `TemplateOrigin.WIZARD` tags
  (`WizardScaffolder.kt:153-167`). The shared tagging means
  `skb template reset` and the LW-L re-run-from-Settings idempotency
  check can both find these entries.
- Inverted habits: **not wired by the wizard.** The recurrence rules
  are emitted with no `inverted = true` or `completed-by-schedule`
  semantics — the wizard treats every atom as a plain daily event.
  `draft-atomic-activities.md` defines the inversion model and Phase
  XX implements the engine, but the wizard isn't tagging templates
  as inverted-habits when it should (`brush-teeth`, `meds-am` are
  textbook inverted-default).
- Supersedence stubs: **not present in wizard output.** Vacation
  overlay / overrides directories (`overrides/<event-id>.md`) are
  created on demand by `TripScaffolder`, not by the lifestyle
  wizard. The trip-wizard is a separate overlay path (Phase CCC).
- Identity.toml extension: the wizard appends `[honorific]` / `[tone]`
  / `[emoji]` / `[alignment]` / `[lifestyle]` sections in a text
  concat after `RepoBootstrap` writes the base file
  (`WizardScaffolder.kt:200-216`). This is fragile — a future
  IdentityTomlCodec rewrite-in-place will wipe the appended sections
  unless those fields move into `IdentityTomlData`.
- Mode.toml: written by `RepoBootstrap.scaffold` as `ModeTomlData.Default`
  (mode=free, no dom-persona, no cadence). The wizard never asks
  the user which mode they want to start in.

## 3. Identity / mode / dom-persona

### Identity

- Per-repo source-of-truth: `identity.toml`. Codec at
  `store/IdentityToml.kt:59`, locked defaults at
  `IdentityToml.kt:37` (`"good boy"`, he/him, "Sir", "soft-kinky",
  "medium"). `IdentityPronouns.HeHim/SheHer/TheyThem`.
- App-side cache: `ui/settings/IdentityPrefs.kt:35` —
  `SharedPreferences` JSON blob with `IdentityState`. **This is a
  parallel state object, NOT a `Flow<IdentityTomlData>` of the active
  repo's `identity.toml`.** Editing in Settings updates `IdentityPrefs`
  only; it does NOT write back to the active repo's `identity.toml`.
  Comment in `IdentityCategory.kt:38-41` admits "In `strictly-kept`
  mode the persist path emits a `reviews/<sha>` entry … that wiring
  lives in the caller side" — but no caller is wired. **DDD.11 is
  unticked and Identity edits today are stranded in prefs.**
- UI: `IdentityCategory.kt:43` is a flat scroll of `OutlinedTextField`s
  (praise, alt-terms CSV, pronouns string, pronouns-extra CSV,
  honorific) + tone chip row + emoji chip row + reset-to-defaults
  + preview card. Reachable via Settings → Identity. Discoverable
  (one of the surfaces) but un-wired to disk.
- Wizard → identity.toml: the wizard's `WizardScaffolder` does write
  identity.toml at scaffold time. So **first-run produces a real
  identity.toml; subsequent edits in Settings do not update it.**
- Agent integration (DDD.11): `IdentityTomlCodec.readOrDefault` is
  the read API, but no caller — notifications + briefings still
  hardcode "good boy" register (verified earlier in `F.5` empty-state
  comment in `main.md:147`). **Not wired.**

### Mode

- Per-repo source-of-truth: `mode.toml`. Codec at `store/ModeToml.kt:74`,
  `RepoMode` enum at `ModeToml.kt:19`. Three wire values: `free`,
  `strictly-kept`, `self-keep`. **Note:** the design doc lists six
  modes (free / strictly-kept / kept-by-AI / kept-by-human / self-keep,
  plus migration flows) but only three are encoded. The
  kept-by-AI vs kept-by-human distinction is implicit in
  `dom_persona` (set → AI dom if persona is one of the 6 builtins;
  human dom = `write_back_target` set to a real repo fingerprint).
- App-side cache: `ui/settings/ModePrefs.kt:59` — JSON-blob
  `ModeState` with `mode = Free|StrictlyKept`, cadence, persona id.
  **Cache has only two modes** (`AppMode.Free`/`StrictlyKept`) —
  drops self-keep entirely. Migration paths DDD.5 are unticked;
  only two of six flows wired.
- Mode-transition UI: `ModeCategory.kt:47` shows mode pill,
  switch-to-strictly-kept button (one tap, no confirmation —
  asymmetric per D.86 design), switch-to-free button (typed-
  confirmation "yes I want to leave" per `ModePrefs.FREE_CONFIRMATION_PHRASE`).
  Always-visible mode-pill is also in the top-bar (DDD.12 ✅,
  `ui/scaffold/ModePill.kt`).
- 24h cooling-off: design says 24h, code says typed-confirmation
  only (`ModeCategory.kt:127`). **The "24h" requirement is NOT
  enforced** — confirmation is instant once the phrase is typed.
- ModePrefs is app-wide, not per-repo. Mode is supposed to be
  per-repo (`mode.toml`); the editing surface ignores `activeRepoName`
  and never writes `mode.toml`. **Mode edits are stranded in prefs
  identically to Identity.**

### Dom-persona

- Builtin store: `store/DomPersonaStore.kt:30` ships 6 personas with
  seed prompts to `~/.config/skb/dom-personas/<id>.md` + a `custom`
  slot. Builtins land via `ensureBuiltins` (idempotent).
- Picker UI: a `FilterChip` row inside `ModeCategory.kt:85-93`. No
  per-persona prompt preview, no cadence-by-persona, no custom-
  prompt editor, no explicit-content gate. **DDD.14 is unticked
  and the surface is one chip row — not a real picker.**
- The `DomPersonaStore` (filesystem) and `ModePrefs.DomPersona`
  (SharedPreferences enum) are **two parallel sources of truth**
  for personas. The Settings UI consults `ModePrefs.availablePersonas`,
  never `DomPersonaStore.list(home)`. The filesystem store is
  effectively unused at runtime.

## 4. Re-entry from Settings — Phase K.12 / S.8

- Entry point: Settings → Lifestyle → "Open wizard at Roles" button
  (`LifestyleCategory.kt:40-45`).
- Wired through `SettingsAccess.onOpenWizardAtRoles` (`SettingsPane.kt:144`)
  → `selectedTag = SettingsCategory.Identity.testTag` (sic — opens
  the **Identity** category, not the wizard). **This is broken**
  — the callback name is `onOpenWizardAtRoles` but the implementation
  routes to the Identity settings tab, not to `WizardNavHost(initialScreen = Roles, initialDraft = …)`.
  The wizard re-entry path documented in K.12 is shipped at the
  composable level (`WizardNavHost` accepts `initialScreen` +
  `initialDraft`) but no caller passes them — the user gets thrown
  to the wrong settings category instead.
- Discoverability: Settings → Lifestyle is reachable but the button
  label "Open wizard at Roles" presumes the user knows what "Roles"
  means without context.

## 5. Sub-shares-with-dom flow — Phases RR + SS

- Share-link generator: `ShareLinkGenerator.kt`. `SharePolicy` carries
  `allowWriteBack` flag (`ShareLinkGenerator.kt:28`) which sets
  `ShareLink.allowWriteBack`. The share sheet (`ShareSheet.kt:87`)
  has a checkbox for it.
- Recipient side: `ShareAcceptResolver.kt:104-113` writes the
  `references.toml` plan with `writeBackTarget = if (link.allowWriteBack) srcFp else null`.
- Repo fork: `RepoForker.kt:87` writes `writeBackTarget = sourceFingerprint`.
  Failure modes are real (`ForkOutcome.Failed`).
- **End-to-end clickability**: yes, the share-this-repo Sheet + the
  fork dialog are wired, but no first-class "I want my dom to schedule
  for me" framing exists. The user has to know that:
  1. Generate share link (with `allowWriteBack` toggled)
  2. Send to dom via QR or out-of-band
  3. Dom installs app, accepts share link
  4. Dom commits to their fork
  5. Sub gets dom's commits via Phase YY review-feed
- The flow exists but is buried inside Repos → repo-detail → share.
  **The wizard does not propose "do you want your schedule kept by a
  dom?"** at first-run — a critical gap given the prompts genesis
  said "dom fills sub's schedule" is the canonical use case
  (`prompts.md:21-22`, "I might have a sub who gives me access to
  their calendar and whoms calendars and timebox I fill with naughty things").

## 6. Intent check vs `prompts.md`

- **"the dom fills the sub's schedule"** (prompts.md:21-22): the
  wizard doesn't model this at all. There's no "are you the
  schedule-author or schedule-recipient?" branch. The Alignment
  screen (Dominant / Submissive / Switch / UnalignedPrivate) is the
  closest proxy, but it only changes copy register — it doesn't
  set `mode.toml.mode = strictly-kept` or seed a share-link card
  on the Done screen, doesn't suggest "shall we set up a share-link
  for your keeper now?", and doesn't pre-seed `dom_persona` if the
  user picks Submissive.
- **"strictly-kept single is kept by AI dom"** (prompts.md:1054):
  the wizard never offers "kept by an AI dom" as a starting mode.
  Submissive alignment + mode=free is the default emission. The
  user has to navigate to Settings → Mode → "Switch to strictly-
  kept" after first-run.
- **"choose pronouns and praise in the wizard"** (prompts.md:1055):
  ✅ shipped at Screen 3.5 (K.5a).
- **"wizard only on first start or new account"** (prompts.md:1210):
  ⚠ wizard is in the top destination list as `TopDestination.Wizard`
  but is filtered out of the icon row per the F45 fix-up. Path is
  bat-avatar → Repos → "+" only. Aligns with user intent BUT the
  app does not auto-launch the wizard on first run when
  `repoStore.list().isEmpty()`. The user's stated intent at
  `prompts.md:1210` was "should be shown when we first start the
  app". **First-launch auto-route is missing.**
- **"accounts not repositories"** (prompts.md:1210): the user
  asked for "Accounts" labelling; code still says "Repos". Cosmetic.
- **"I want my boy fully scheduled"** (prompts.md:1054): only
  partially. The wizard emits 6 atoms × N roles per day with stub
  09:00 dtstart — not actually a full waking schedule. Every atom
  starts at 09:00, so the day's "schedule" is N overlapping 15-min
  blocks. `WizardScaffolder.kt:148-151` is the comment "v1 uses a
  generic 09:00 dtstart + 15-minute duration for every atom".

## 7. Gap analysis

| Gap | Severity | Where |
|---|---|---|
| First-launch never auto-routes to wizard | **critical** | `MainActivity.kt:181` falls through to `SkbAppShell` with empty repoStore |
| `onOpenWizardAtRoles` re-entry routes to Identity, not the wizard | **critical** | `SettingsPane.kt:187` |
| Identity edits in Settings don't write back to active repo's `identity.toml` | **critical** | `IdentityPrefs.kt:42` + `IdentityCategory.kt` |
| Mode edits in Settings don't write back to active repo's `mode.toml` | **critical** | `ModePrefs.kt:66` |
| Every wizard atom uses 09:00 dtstart — schedule is unusable | high | `WizardScaffolder.kt:148` |
| Inverted-habit semantics not tagged by wizard | high | `WizardScaffolder.kt:138-168` |
| Wizard never proposes mode=strictly-kept even when alignment=Submissive | high | wizard has no Mode screen |
| Wizard never proposes "share with my dom" on Done screen | high | wizard has no share branch |
| 24h cooling-off is typed-confirmation only, no real 24h gate | medium | `ModeCategory.kt:127`, D.86 says 24h |
| Six modes truncated to three at wire + two at prefs | medium | `ModeToml.kt:19` vs `ModePrefs.kt:21` |
| `DomPersonaStore` (filesystem) is parallel-dead to `ModePrefs.DomPersona` | medium | two sources of truth |
| Dom-persona picker is one chip row, no preview / no editor (DDD.14) | medium | `ModeCategory.kt:85` |
| Agent integration into notifications + briefings (DDD.11) | medium | unwired |
| `activeRepoName` defaults to "demo-repo" string with no backing repo | low | `AppGraph.kt:206` |
| "Accounts" vs "Repos" labelling | low | per user intent prompts.md:1210 |

## 8. Round 2.1 proposed sub-steps

The fix is sequenced to land the **critical wiring fixes first**
(make Identity + Mode actually persist to the repo and make first-run
take the user to the wizard) before adding new wizard screens. No
new fields, no new TOML schema changes — wire what's already there.

### Phase 2.1-Wizard

- [ ] **2.1-Wizard.1** First-launch auto-route: in `MainActivity.kt:181`
  branch on `graph.repoStore.list().isEmpty()` after the age gate
  and route directly to `WizardNavHost` (not via `SkbAppShell`).
  Pass `onFinish = { /* now show shell, selected = Schedule */ }`.
  Decision: a top-level state `firstLaunchDone` in MainActivity,
  flipped to `true` after first scaffolding. Alternative (boot to
  Shell and overlay the wizard) rejected because the empty Schedule
  screen briefly flashes and confuses the user.
- [ ] **2.1-Wizard.2** Fix `onOpenWizardAtRoles` to actually open the
  wizard. Decision: hoist a `wizardEntryRequest: MutableStateFlow<WizardScreen?>`
  in `AppGraph`, set to `WizardScreen.Roles` in the Lifestyle
  callback, observed in `SkbAppShell.onSelectDest = TopDestination.Wizard`
  + `initialScreen = request.value`. Reset to null on wizard finish.
- [ ] **2.1-Wizard.3** Add a wizard "Mode" screen between Lifestyle
  and Roles. Default = `free` for UnalignedPrivate/Switch/Dominant,
  default = `strictly-kept` (kept-by-AI) for Submissive. One-tap
  override. Writes `mode.toml.mode` + `dom_persona = "stern-but-fair"`
  + `dom_cadence = "end-of-day"` when strictly-kept. Reason this
  screen is required: prompts genesis defines AI-dom as the default
  for solo Submissive users, but the wizard currently emits mode=free
  for everyone.
- [ ] **2.1-Wizard.4** Add a wizard "Share with dom" screen on the
  Done path (only when alignment∈{Submissive, Switch} AND mode=strictly-kept
  AND human-dom selected). One CTA: "generate share link" — drops
  into `ShareSheet` with `allowWriteBack` pre-checked. Skippable.
- [ ] **2.1-Wizard.5** Replace the 09:00 dtstart stub with a
  morning/midday/evening hint per atom. Decision: hard-code a
  three-bucket map in `TemplateRegistry` (`brush-teeth → 07:00`,
  `meds-am → 08:00`, `meds-pm → 21:00`, `cardio-30min → 17:00`,
  etc.). Defer fine-tuning to a future per-(alignment, lifestyle)
  matrix. Reason: the existing all-09:00 emission produces N
  overlapping blocks per role, unusable as a "good boy is fully
  scheduled" outcome.
- [ ] **2.1-Wizard.6** Tag the wizard's inverted-default atoms
  (`brush-teeth`, `meds-am`/`pm`, `shower`, `feed-am`/`pm`, etc.)
  with `inverted = true` in the emitted `RecurrenceRule` per the
  Phase XX inversion model.
- [ ] **2.1-Wizard.7** `activeRepoName` default of `"demo-repo"`
  becomes `""` and the shell renders an empty top-bar instead of
  a stale label. Removes the placeholder confusion when the user
  somehow bypasses the wizard.

### Phase 2.1-Identity

- [ ] **2.1-Identity.1** Make `IdentityPrefs` a thin cache over the
  active repo's `identity.toml`. Decision: `IdentityPrefs.update`
  becomes async — debounces a write through `IdentityTomlCodec.write`
  to `<activeRepo.rootDir>/identity.toml`, then commits via
  `GitRepoRegistry.get(repoId).commitAll("identity: update")`.
  The cache layer stays so the UI keeps its instant feedback. On
  active-repo switch, reload from disk via `IdentityTomlCodec.readOrDefault`.
- [ ] **2.1-Identity.2** Add the missing `IdentityTomlData` fields
  for wizard-driven keys (`alignment`, `lifestyle`, `praise.alt_terms`)
  so the wizard's text-concat appendix at
  `WizardScaffolder.kt:200-216` can be replaced with a clean
  codec-driven write. Same byte format on disk.
- [ ] **2.1-Identity.3** Wire DDD.11 — `NotificationPrefs.bodyFor`
  + `BriefingRenderer` consume `IdentityTomlCodec.readOrDefault(activeRepoRoot)`.
  Praise term + honorific surface in notification bodies; pronouns
  thread through briefing salutations.
- [ ] **2.1-Identity.4** Strictly-kept review-feed hook: when an
  identity edit commits in strictly-kept mode, fire
  `ReviewFeedWriter.writeReviewableChange` with `IdentityEdit`
  family path. Already supported by the writer; just call it.

### Phase 2.1-Mode

- [ ] **2.1-Mode.1** `ModePrefs` becomes a thin cache over active
  repo's `mode.toml`. Same wiring model as 2.1-Identity.1. Active-
  repo switch reloads from disk via `ModeTomlCodec.readOrDefault`.
- [ ] **2.1-Mode.2** Expand `ModePrefs.AppMode` to include
  `SelfKeep` matching `RepoMode.SelfKeep` on disk. Truncating to
  two cases loses a real user state.
- [ ] **2.1-Mode.3** Surface the kept-by-AI vs kept-by-human
  distinction in `ModeCategory`. Decision: derive a `KeptBy`
  computed field — `KeptBy.Ai` iff `dom_persona ∈ DomPersonaStore.BUILTINS`,
  `KeptBy.Human` iff `write_back_target != null` and `dom_persona == null`,
  `KeptBy.SelfKeep` iff `mode == self-keep`. Three radio-buttons
  under the mode pill; selecting Human prompts "generate share link"
  CTA (same as wizard 2.1-Wizard.4).
- [ ] **2.1-Mode.4** Enforce the D.86 24h cooling-off properly:
  on first tap of "switch to free", set `mode_transition_request_at_ms`
  in `ModePrefs`. The actual flip is blocked for 24h; the typed-
  confirmation dialog still fires but only enables the confirm
  button when `now - request_at_ms >= 24h`. Visible countdown.
- [ ] **2.1-Mode.5** Migration paths DDD.5: wire kept-by-AI ↔
  kept-by-human (flip `dom_persona` to/from `null`; set/unset
  `write_back_target`). Self-keep ramp = single button. Other
  flows already covered by 2.1-Mode.3.
- [ ] **2.1-Mode.6** Make `DomPersonaStore` the single source of
  truth. Decision: delete the `DomPersona` enum encoding from
  `ModePrefs` (or keep just `personaId: String`) and have
  `ModeCategory.availablePersonas` call `DomPersonaStore.list(home)`.
  Custom-prompt edits go through `DomPersonaStore.writeCustom`.
- [ ] **2.1-Mode.7** Full dom-persona picker per DDD.14:
  bottom-sheet with per-persona prompt preview, cadence override,
  custom-prompt editor (multiline OutlinedTextField backed by
  `DomPersonaStore.writeCustom`), explicit-content gate behind
  the existing age confirmation. Reachable from `ModeCategory` →
  "Edit personas" button.

## Status

This is an audit, not an implementation plan with checkboxes
shipping yet. All boxes are `[ ]`. When 2.1-Wizard / 2.1-Identity
/ 2.1-Mode lands, tick boxes here at the same time as the work,
add the jj change-id to each phase header, and flip status to
`## Status: ✅ DONE`.

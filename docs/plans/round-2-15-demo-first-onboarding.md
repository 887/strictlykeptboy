# Round 2.15 — Demo-first onboarding

## Status

In flight. No phases ticked yet.

## Context

The current first-launch flow is an 11-step wizard that asks the user
to pick a sticker pack, customize identity (praise / pronouns /
honorific / tone / emoji density), commit to plain-vs-kink framing,
pick a lifestyle card, pick role(s), pick templates, and configure a
Git remote — all before the user has seen a single calendar event or
task. That's the wrong order. We don't know yet if the user is
keeping the app. Asking them to design the lifestyle layer before
seeing what the app can do is asking too much.

It also contradicts our positioning: strictlykeptboy is kink-positive
openly (decisions D.55 + K-1..K-7). The current `FramingChoice` screen
("Just a calendar app" vs "Kink framing") implicitly apologises for the
kink framing. We don't need it. If a user wants a plain calendar there
are a million options elsewhere.

**Intended flow after Round 2.15:**

1. **Step 1 (Manifesto):** one screen with the bat in the spotlight and
   one paragraph: "strictlykeptboy is a calendar + todo app for kinky
   people. If you're not into that, you'll be happier with a plain
   calendar app." Continue / Back-out.
2. **Step 2 (Pick your demo):** five-card perspective picker (the
   existing Lifestyle cards), each card seeds a read-only demo repo
   pre-populated with that perspective's self-care, todos, routines,
   and identity. Selection ships the user straight into the demo
   experience.
3. **Demo experience:** the app behaves normally, but the user is
   inside a read-only demo repo. The top-bar avatar shows the bat. A
   subtle banner on Schedule/Tasks reads "Demo mode — make your own
   calendar →".
4. **Exit demo / make your own:** the existing 11-step wizard is the
   "build my first real repo" path, reached from Repositories →
   "Make my own calendar" (or via the demo banner CTA). The big wizard
   stays — its complexity is justified *once the user has committed*.
5. **Demo toggle:** Repositories view gains a prominent "Demo mode"
   row with on/off switch. Toggling off without user repos routes to
   the long wizard; toggling on restores the last-selected demo
   perspective.

## Locked decisions

- **D-2.15.a — Onboarding is openly kink-positive.** No
  plain-vs-kink picker on first launch. Step 1 says so plainly.
  (Honors D.55 / K-1..K-7.)
- **D-2.15.b — Default species in demo mode is Bat.** Always. The
  species carousel stays only in the long wizard for real repos.
- **D-2.15.c — Demo repos live under `<filesDir>/demo-repos/<perspective>/`
  and are regenerated from code each time demo mode is enabled.** They
  are not git-pushed, not surfaced in the Access category, not
  editable through the UI (read-only banner on every detail sheet).
- **D-2.15.d — Demo + user repos coexist.** Repositories view lists
  demo repos as their own section, badged "Demo · read-only". When
  the user creates their first real repo, the demo repos stay
  available unless the user toggles demo mode off.
- **D-2.15.e — Identity in demo mode is fixed per perspective.**
  Praise terms / pronouns / honorific / tone are baked into the
  perspective preset (e.g. PetAi → "good boy" + he/him + Sir +
  warm-neutral). Customization belongs to the long wizard, not the
  demo flow.
- **D-2.15.f — `FramingChoice` screen is removed.** With it:
  `LifestyleCard.JustCalendar`, `WizardDraft.wantsKinkFraming`, all
  related strings + tests + skip logic in `goNext`/`goBack`.
- **D-2.15.g — The long wizard stays at its current 10 steps.** This
  round does not shrink it; the round shrinks the *first impression*
  by routing past it until the user opts in.
- **D-2.15.h — First-launch routing:** if no user repos AND no demo
  perspective chosen yet → `IntroWizardHost`. If no user repos AND
  demo perspective set → directly into demo Schedule. If ≥1 user repo
  → Schedule against the active user repo (existing behavior).

## Phase order (strict dependency chain)

```
A  Demo infra      (prereq — prefs + seeder skeleton; blocks B..F)
  ↓
B  Intro wizard   (2-step flow; depends on A)
C  Demo chrome    (banner, avatar menu, Repos toggle; depends on A+B)
D  Drop framing   (remove FramingChoice + JustCalendar; independent of A/B)
E  Demo content   (per-perspective seed bodies; depends on A)
F  Tests + AVD    (gate before main.md tick)
```

## Phase A — Demo-mode infrastructure (PREREQ)

- [ ] **A.1** Add `prefs/DemoModePrefs.kt`: `isDemoMode: Boolean`,
      `selectedPerspective: DemoPerspective?`, persisted via
      `EncryptedSharedPreferences` (consistent with the rest of the
      prefs layer). Expose as `StateFlow` so the UI reacts.
- [ ] **A.2** Add `demo/DemoPerspective.kt` — sealed/enum with the
      five values: `PetAi`, `PetHuman`, `SelfKeep`, `DomKeeper`,
      `Switch`. Each carries an id (stable, persisted), a label
      string-resource id, a blurb string-resource id, and an emoji.
- [ ] **A.3** Add `demo/DemoRepoSeeder.kt`. Inputs:
      `parentDir: Path`, `perspective: DemoPerspective`, `author:
      AuthorIdentity`. Outputs a fully-formed repo at
      `parentDir/<perspective.id>/`: `identity.toml` + `mode.toml`
      preset to the perspective + bucketed `calendars/`, `todolists/`,
      `events/` files. Returns the repo root path. Idempotent — wipes
      and rewrites the dir each call.
- [ ] **A.4** Extend `RepoStore` with `demoRepos: StateFlow<List<RepoEntry>>`
      computed from `DemoModePrefs` + the on-disk demo dirs. A demo
      entry has `readOnly = true` and `kind = RepoKind.Demo` (new enum
      case). Hide demo entries from Access category enumeration.
- [ ] **A.5** Add `RepoKind.Demo` to the existing repo-kind sealed
      type (or equivalent), and route any "is read-only?" check
      through it. Detail sheets and edit menus must respect this.

## Phase B — Intro wizard (2-step)

- [ ] **B.1** Add `ui/wizard/intro/IntroWizardHost.kt`. Two screens:
      `IntroManifestoScreen` + `IntroDemoPickerScreen`. NOT a fork of
      the existing `WizardNavHost`; deliberately separate so the long
      wizard stays untouched.
- [ ] **B.2** `IntroManifestoScreen`: bat hero (large `about_bat`
      drawable, centered), title "strictlykeptboy", body paragraph
      naming the app's intent (calendar + todo for kinky people) and
      the bail-out hint. Single "Continue" button — no Back.
- [ ] **B.3** `IntroDemoPickerScreen`: reuses the existing five
      `LifestyleCard` composables (minus `JustCalendar`, removed in
      Phase D). Tap-to-select. Header copy: "Pick a perspective to
      explore. Demo data is read-only and you can switch any time."
- [ ] **B.4** On selection: call `DemoRepoSeeder.seed(...)`, write
      `DemoModePrefs.isDemoMode = true` + `selectedPerspective = X`,
      navigate to Schedule. No further wizard steps.
- [ ] **B.5** First-launch routing in `MainActivity`: branch on
      `RepoStore.userRepos.isEmpty() && DemoModePrefs.selectedPerspective == null`
      → IntroWizardHost; `RepoStore.userRepos.isEmpty() && demo set`
      → Schedule (demo); else Schedule (active user repo).
- [ ] **B.6** AVD smoke: fresh install lands on the manifesto, tap
      Continue → perspective picker, tap a card → Schedule populated
      with that perspective's seeded events.

## Phase C — Demo-mode chrome

- [ ] **C.1** Add a slim banner composable `DemoModeBanner` that
      renders above Schedule + Tasks when active. Copy: "Demo mode —
      tap to make your own calendar." Tapping routes to Repositories.
- [ ] **C.2** Top-bar avatar menu: add "Exit demo mode" item when
      `DemoModePrefs.isDemoMode == true`. Tapping disables demo mode
      and routes to Repositories.
- [ ] **C.3** Repositories view: add a "Demo mode" row at the top of
      the list with a Material Switch. Toggling off → if no user repos,
      open the long wizard; if user repos exist, drop to first user
      repo. Toggling on → restore last perspective; if none, route to
      IntroDemoPickerScreen.
- [ ] **C.4** Demo repo rows render with a "Demo · read-only" badge
      and are not editable (no rename / no remote-config / no delete).
- [ ] **C.5** Every detail sheet (event / task) reuses the existing
      `ReadOnlyBanner` when the active repo is demo — no new banner
      needed, just the predicate.

## Phase D — Drop the FramingChoice screen

- [ ] **D.1** Remove `WizardScreen.FramingChoice` from `SCREEN_ORDER`
      and from the `when (current)` switch in `WizardNavHost`.
- [ ] **D.2** Remove `FramingChoiceScreen` composable + its strings
      (`wizard_framing_prompt`, `wizard_framing_blurb`,
      `wizard_framing_plain_*`, `wizard_framing_kink_*`).
- [ ] **D.3** Remove `LifestyleCard.JustCalendar` from the enum and
      from `LifestyleCard.fromDraft` / `applyLifestyleCard` paths.
      Update tests that assert on JustCalendar.
- [ ] **D.4** Remove `WizardDraft.wantsKinkFraming` and the
      `stepIsSkipped` / skip logic in `goNext` / `goBack`.
- [ ] **D.5** `LifestyleCardScreen`: filter on JustCalendar is no
      longer needed; LaunchedEffect's matched==JustCalendar back-nav
      handling can be deleted with it.
- [ ] **D.6** Remove the explainer banner string
      `wizard_lifestyle_language_explainer` only if it referenced the
      now-removed framing concept — otherwise keep.
- [ ] **D.7** Update Step counter: long wizard goes 11 → 10 steps.

## Phase E — Demo content per perspective

Each E.x materializes a *complete* working demo: calendars
(self-care, work, leisure), todo lists, events on today + the next
two days, identity preset, mode preset.

- [ ] **E.1** `PetAi`: identity = good boy / he/him / Sir / warm-neutral.
      Mode = strictly-kept, dom-persona = stern-but-fair. Seeded
      events: morning routine 07:00, water break 10:00, lunch 12:00,
      walk 18:00. Tasks: "shower + dress", "10min journaling".
- [ ] **E.2** `PetHuman`: identity = good boy / he/him / Daddy /
      soft-kinky. Mode = strictly-kept, dom-persona = (placeholder
      human dom share). Same routine spine + an "ask Daddy about X"
      task to demonstrate the share-with-dom flow.
- [ ] **E.3** `SelfKeep`: identity = champ / he/him / no honorific /
      playful. Mode = self-keep. Same routine spine; no honorific
      anywhere, encouragement-coded.
- [ ] **E.4** `DomKeeper`: identity = star / they/them / no honorific /
      warm-neutral. Mode = free + keeper-role. Calendars for "my pets"
      with a couple of seeded pet routines visible.
- [ ] **E.5** `Switch`: identity = good boy / they/them / Sir /
      warm-neutral. Mode = switch (toggleable). Dual calendars
      visible.
- [ ] **E.6** Each seed contains a `README.md` at repo root explaining
      "this is a demo repo, edits won't stick" and pointing to the
      Repositories toggle.

## Phase F — Tests + AVD verification

- [ ] **F.1** `DemoModePrefsTest` — round-trip + null-state default.
- [ ] **F.2** `DemoRepoSeederTest` (Robolectric) — for each
      perspective: seed runs, repo materializes, identity.toml +
      mode.toml round-trip, ≥1 event + ≥1 task land in the index.
- [ ] **F.3** `IntroRoutingTest` — fresh install lands on manifesto;
      seeded-demo lands on Schedule; user-repo lands on Schedule.
- [ ] **F.4** `WizardSkipFramingTest` — removing FramingChoice
      doesn't break goNext / goBack for any alignment.
- [ ] **F.5** AVD smoke (per CLAUDE.md):
  - [ ] Fresh install → manifesto → demo picker → Schedule with
        seeded events visible
  - [ ] Top-bar avatar → "Exit demo mode" → Repositories with switch
        off + long wizard CTA
  - [ ] Long wizard from Repositories → finishes → demo repo still
        present alongside the new user repo
  - [ ] Toggle demo back on → restore perspective + events visible
  - [ ] Make sure the existing 573+ unit tests still pass

## What is intentionally NOT in scope

- **Demo data localization.** English-only on the first ship of the
  demo perspectives; the i18n workflow can pick it up after.
- **AI-generated demo art.** Demo bat uses the existing
  `about_bat` drawable. The species carousel is unaffected.
- **Demo sharing.** Demo repos cannot be shared (the share sheet
  short-circuits for `RepoKind.Demo`).
- **Editing demo events.** Long-press / edit on a demo event opens
  the detail sheet read-only; no write paths are exposed.

## Cross-references

- Existing wizard: `app/src/main/java/com/eight87/strictlykeptboy/ui/wizard/WizardNavHost.kt`
- Lifestyle cards: same file, `LifestyleCardScreen`
- RepoStore: `app/src/main/java/com/eight87/strictlykeptboy/store/RepoStore.kt`
- First-launch routing: `app/src/main/java/com/eight87/strictlykeptboy/MainActivity.kt`
- Round 2.14 (most recent ship): commit `2b58c84`

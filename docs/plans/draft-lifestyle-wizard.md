# strictlykeptboy — design-a-lifestyle wizard (draft)

## Status: ✅ INTEGRATED — see `main.md` Phase K (REPLACED the previous wizard+templates phase) and Phase L (RETIRED, demo content concept retired). `decisions.md` D.54 / D.55 / D.56. Wizard content references TW-A / TW-B / TW-E in `templates-demo-wizard.md`; TW-C / TW-D / TW-G / TW-H retired. Original draft content preserved below for reference.

## Framing

This draft **replaces** Phase K (wizard + templates) and Phase L
(demo content) in `main.md` wholesale. It also **supersedes** the
majority of `templates-demo-wizard.md` Phase TW-D (wizard flow) and
the entirety of `templates-demo-wizard.md` Phase TW-C/TW-G (demo
content / demo mode).

Project intent locks driving this draft (treated as given, not
re-debated):

- **No empty-app start. No demo mode.** Every install ends with a
  living, populated repo whose contents are the user's actual
  starting state. Demo mode (TW-C, TW-G, old L.1..L.4) retires.
- **The wizard IS the start.** First launch → wizard → user has
  calendars, recurrences, a todolist, and a now-card populated with
  something happening right now. The wizard's output is canonical
  user data, not "sample data to replace later".
- **A bat-mascot guide stands on every wizard screen** as a
  telegram/discord-sticker-style companion (Claude-the-builder
  persona). The bat-mascot is **distinct from** the user's chosen
  avatar species — the user picks a species on Screen 2, but the
  bat-mascot stays bat-mascot throughout the wizard. After the
  wizard ends, the user's avatar species takes over for in-app
  mascot surfaces; the bat-mascot is reserved for guided flows
  (wizard, re-run-from-settings, future onboarding flows).
- **Kink-positive openly.** No SFW euphemism layer in the wizard
  itself. Alignment / lifestyle / role / template prompts say what
  they mean: dominant / submissive / switch, single / partnered,
  strictly-kept / strictly-keeping / strictly-shared / free. The
  TW-D "SP-1..SP-9 SFW phrasing rules" retire as enforced wizard
  copy constraints; their concerns survive only for **lockscreen /
  widget / Play-Store-screenshot surfaces** (where another agent
  owns the neutral-mode plumbing). The wizard's Settings screen
  exposes the kink-on/neutral toggle; default is kink-on.
- **Species roster (LOCK):** bat, fox, tiger, lion, wolf, bunny, cat
  for the seven defaults shipped in-box. Plus "choose your own"
  which jumps to Agent 1's GitHub-sticker-repo clone flow — this
  draft calls into that flow but doesn't redesign it. **Avatar
  species ≠ bat-mascot.** Default avatar (if the user skips
  selection) is bat — chosen because it matches the launcher icon
  and the bat-mascot guide, minimizing first-run cognitive load.
- **No Wear OS.** Not in scope here, not in scope anywhere.

This draft locks every decision below. There is no
open-questions wall. Each phase's checkboxes are the granular work.

---

## Phase LW-A — Wizard architecture + state model

Foundation: nav, viewmodel, commit-on-finish semantics.

- [ ] **LW-A.1** Compose Navigation graph rooted at
  `WizardNavHost` with one destination per screen
  (`welcome`, `species`, `alignment`, `lifestyle`, `roles`,
  `templates`, `git`, `scaffold`, `done`). Back-stack preserved.
  System back gesture goes to previous screen except on
  `welcome` (no-op) and `done` (no-op — wizard is finished).
- [ ] **LW-A.2** `WizardViewModel` (`hiltViewModel()` once DI is
  introduced in Phase B/C; until then a plain
  `viewModel()` per `MainActivity`-scoped Compose) holds the
  draft state as a single immutable `WizardDraft` data class:

  ```kotlin
  data class WizardDraft(
      val species: SpeciesChoice = SpeciesChoice.Bat, // default if skipped
      val alignment: Alignment = Alignment.UnalignedPrivate,
      val lifestyle: Lifestyle = Lifestyle.SingleFree,
      val roles: Set<RoleId> = emptySet(),
      val templatesPerRole: Map<RoleId, Set<TemplateAtomId>> = emptyMap(),
      val gitChoice: GitChoice = GitChoice.PhoneOnly,
      val kinkPositiveToggle: Boolean = true, // exposed in Settings post-wizard
      val timezone: ZoneId = ZoneId.systemDefault(),
      val locale: Locale = Locale.getDefault(),
  )
  ```

  All mutations go through typed actions (`SelectSpecies`,
  `ToggleRole(roleId)`, etc.). Disk writes happen **only** on
  finish (Phase LW-I).
- [ ] **LW-A.3** Skip-policy: `skip` button visible only on
  cosmetic screens — `species` (defaults to bat) and `templates`
  (defaults: smart-defaults already toggled). Critical screens
  (`alignment`, `lifestyle`, `roles`, `git`) have no skip — they
  have explicit "continue" with at least one valid selection.
  `welcome` and `done` have no skip (welcome has "let's go",
  done has "open my calendar").
- [ ] **LW-A.4** First-launch routing: `MainActivity` startup
  checks `RepoStore.hasAnyRepo()`. If false → wizard. If true →
  normal home. **Exception:** if `MainActivity` was launched via
  a Phase MM deep-link intent (`strictlykeptboy://add?...`), the
  Phase QQ first-launch deep-link bootstrap takes precedence
  over the wizard (gifted-schedule recipients skip the wizard
  per Round-3 D.46). Documented inline in `main.md` integration
  notes (Phase QQ.1 wording change).
- [ ] **LW-A.5** Mid-wizard exit safety: closing the app mid-wizard
  preserves `WizardDraft` in a `SavedStateHandle`-backed
  `ProcessLifecycleOwner`-scoped store so resuming returns to
  the last screen with the partial draft intact. If the user
  force-closes and reopens 7+ days later, the draft is dropped
  (avoid stale-state confusion) and the wizard restarts from
  Screen 1.
- [ ] **LW-A.6** Bat-mascot host component:
  `@Composable WizardScaffold(screen: WizardScreen, content)` —
  draws the screen content with a bat-mascot sticker pinned
  top-right (phone) / right-rail (tablet ≥600dp). The sticker
  pose/mood is a slot driven by the `WizardScreen` enum so
  each screen passes its own mood key.
  Slot key → asset path resolution is `drawable/wiz_bat_<key>.webp`
  (LW-K names every key).
- [ ] **LW-A.7** Robolectric test: navigate every linear path
  forward+back, assert `WizardDraft` invariants hold and that
  no disk writes occur until Phase LW-I "finish" action.

---

## Phase LW-B — Screen 1: Welcome

- [ ] **LW-B.1** Layout: centered column. Bat-mascot wave sticker
  (`wiz_bat_welcome_wave`) at ~30% height; tagline below in
  display-medium typography:
  *"let's design your lifestyle — by the end you'll have a
  living calendar, not an empty app."*
- [ ] **LW-B.2** Single primary CTA button: "let's go" →
  navigates to `species`.
- [ ] **LW-B.3** No skip, no back. The system-back from
  this screen is a no-op (we are at the wizard root).
- [ ] **LW-B.4** Bottom-tucked small text: "you can change any
  of this later in Settings → Add more to my lifestyle" — sets
  expectations early.

---

## Phase LW-C — Screen 2: Species selection

- [ ] **LW-C.1** Layout: 2-column grid (phone) / 4-column (tablet)
  of 8 tiles — 7 species + 1 "choose your own" tile. Each tile
  shows the species' default idle sticker (`stickers/<species>/idle.webp`)
  and the species name beneath.
- [ ] **LW-C.2** Species roster locked: `bat`, `fox`, `tiger`,
  `lion`, `wolf`, `bunny`, `cat`. Tile order: bat first (matches
  launcher + bat-mascot guide), then alphabetical:
  `bat | bunny | cat | fox | lion | tiger | wolf | choose-your-own`.
- [ ] **LW-C.3** "Choose your own" tile → navigates to Agent 1's
  GitHub-sticker-repo clone flow (`StickerPackInstallActivity`
  from Phase FF). On return, if a pack was installed, the
  picker shows the pack's `idle` sticker and treats it as the
  selected species. If the user cancels, returns to the grid.
- [ ] **LW-C.4** Tap-to-select with single-selection radio
  semantics. Selected tile gets the M3E selected container
  treatment. Skip button at the top-right (LW-A.3) — skipping
  sets species to bat.
- [ ] **LW-C.5** Bat-mascot reaction sticker on selection:
  `wiz_bat_species_greeting_<species>` (e.g.
  `wiz_bat_species_greeting_fox` shows the bat-mascot sniffing
  noses with a fox stand-in). Caption updates inline:
  *"oh — `<species>` suits you ;3"*. The reaction sticker
  appears at the top-right slot from LW-A.6.
- [ ] **LW-C.6** "Continue" button enabled once a tile is
  selected (or via skip — bat default).

---

## Phase LW-D — Screen 3: Alignment

- [ ] **LW-D.1** Four large cards in a 2x2 grid:
  - **Dominant** — "you set the rules"
  - **Submissive** — "you follow the rules"
  - **Switch** — "you do both — depending"
  - **Unaligned (private)** — "skip the kink framing entirely"
- [ ] **LW-D.2** Single-select, no skip. "Continue" enables on
  first tap.
- [ ] **LW-D.3** Unaligned-private is the **kink-off entry-point**:
  it sets `WizardDraft.kinkPositiveToggle = false` and propagates
  to downstream screens (Screen 4 lifestyle options strip
  strictly-kept/keeping phrasing; Screen 5 hides the `kink` role
  entirely; Screen 6 hides kink templates). Other three
  alignments leave `kinkPositiveToggle = true` (default).
- [ ] **LW-D.4** Bat-mascot reaction stickers per choice:
  - Dominant → `wiz_bat_align_dom_bow` (bat-mascot does a slight
    deferential bow / chin tilt up)
  - Submissive → `wiz_bat_align_sub_collar` (bat-mascot tugs at
    a collar around its own neck, eyes-up)
  - Switch → `wiz_bat_align_switch_both` (bat-mascot one-paw-bow
    one-paw-collar, half-and-half pose)
  - Unaligned → `wiz_bat_align_unaligned_nod` (respectful nod,
    no kink iconography)
- [ ] **LW-D.5** Caption per selection (one-liner) is screen-local,
  not a separate screen: *"got it — `<alignment>` mode ;3"*.

---

## Phase LW-E — Screen 4: Lifestyle

- [ ] **LW-E.1** Two top-level cards: **Single** / **Partnered**.
  Tap to expand inline (Compose `AnimatedVisibility`) showing
  sub-options:
  - Single → `single-free` | `single-strictly-kept` (sub) /
    `single-strictly-keeping` (dom) / `single-strictly-shared` (switch)
  - Partnered → `partnered-free` | `partnered-strictly-kept` /
    `partnered-strictly-keeping` / `partnered-strictly-shared`
- [ ] **LW-E.2** Sub-option labels are **computed from the
  alignment on Screen 3**:
  - alignment=submissive → "strictly-kept"
  - alignment=dominant → "strictly-keeping"
  - alignment=switch → "strictly-shared"
  - alignment=unaligned-private → sub-options are only
    `single-free` / `partnered-free` / `partnered-routine`
    (kink-free phrasing; "routine" replaces "strictly-X" for the
    unaligned-private surface). No strictly-X surface ever
    leaks under unaligned-private.
- [ ] **LW-E.3** Single-select across both expanded cards
  (so only one of the up-to-four sub-options is chosen).
  "Continue" enables on selection. No skip.
- [ ] **LW-E.4** Bat-mascot reaction stickers:
  - free → `wiz_bat_life_free_stretch` (bat-mascot stretching,
    casual posture)
  - strictly-kept → `wiz_bat_life_kept_kneel` (bat-mascot
    half-kneeling, hands behind back)
  - strictly-keeping → `wiz_bat_life_keeping_clipboard`
    (bat-mascot holding a clipboard, attentive)
  - strictly-shared → `wiz_bat_life_shared_handshake` (two-paw
    handshake-with-self pose)
  - routine (unaligned-private only) →
    `wiz_bat_life_routine_calendar` (bat-mascot holding a small
    calendar tile, neutral)

---

## Phase LW-F — Screen 5: Roles

- [ ] **LW-F.1** Multi-select toggle grid. Role roster (LOCK):

  | id | label | always-on? |
  |---|---|---|
  | `work` | Work | no |
  | `university` | University | no |
  | `freelance` | Freelance | no |
  | `workout` | Workout | no |
  | `study` | Study | no |
  | `kink` | Kink | auto if alignment ≠ unaligned-private |
  | `social` | Social | no |
  | `family` | Family | no |
  | `creative` | Creative | no |
  | `hobby` | Hobby | no |
  | `recovery` | Recovery | no |
  | `spirituality` | Spirituality | no |
  | `health` | Health | no |
  | `finance` | Finance | no |
  | `household` | Household | no |
  | `pet-care` | Pet Care | no |
  | `self-care` | Self-Care | **always on**, not toggleable |

- [ ] **LW-F.2** `self-care` is auto-selected and rendered as a
  pinned non-toggleable chip at the top of the grid (with a small
  lock icon and tooltip "everyone gets self-care"). This guarantees
  every wizard outcome includes at least one populated calendar
  even if the user un-selects everything else.
- [ ] **LW-F.3** `kink` is auto-toggled on (and visible) when
  alignment is dom/sub/switch; **hidden entirely** when alignment
  is unaligned-private. User can untoggle `kink` even in kinky
  alignments — the role is offered, not forced.
- [ ] **LW-F.4** Guidance: a soft suggestion above the grid says
  "pick 3–8 that feel typical for you" — not enforced; the user
  may select any count ≥ 0 (combined with mandatory self-care).
- [ ] **LW-F.5** Bat-mascot sticker: `wiz_bat_roles_notebook` —
  bat-mascot peering at a notebook, pencil to chin, "let me see
  what you're up to" energy.
- [ ] **LW-F.6** "Continue" enables once at least self-care is
  on (always true).

---

## Phase LW-G — Screen 6: Templates per role

- [ ] **LW-G.1** Layout: for each selected role, a collapsible
  section (Compose `Card` with expandable content). Each section
  lists that role's atomic-activity templates as individually
  toggleable rows. Smart-defaults pre-toggle per matrix below.
- [ ] **LW-G.2** Template registry — atomic activities per role
  (LOCK; cross-reference: these become Agent 2's template files
  in `templates/<role>/<atom>.toml`):

  - `self-care` (always-on, smart-default ALL on): brush-teeth,
    shower, shave, skincare, hydration-check, sunlight-10min,
    bedtime-wind-down
  - `workout`: pushups, situps, squats, pull-ups, planks,
    cardio-30min, stretch-15min, foam-roll
  - `study`: focus-block-25min (pomodoro), deep-work-90min,
    review-flashcards, read-20pg, weekly-review
  - `work`: deep-work-am, deep-work-pm, inbox-triage,
    stand-up-15min, weekly-planning, weekly-shutdown
  - `university`: lecture-block, lab-block, assignment-block,
    office-hours, weekly-review
  - `freelance`: client-block, invoicing, weekly-pipeline-review,
    deep-work-block
  - `kink` (hidden if unaligned-private): cage-check, plug-check,
    posture-check, collar-check, edge-and-stop, kegels, grooming,
    weigh-in, journal-entry, check-in-with-keeper
  - `social`: call-friend, coffee-with-someone, send-a-message,
    plan-meetup
  - `family`: call-family, family-meal, family-event
  - `creative`: practice-instrument, draw-15min, write-page,
    edit-photos
  - `hobby`: hobby-block-1h (generic; user renames in-app)
  - `recovery`: nap-20min, meditation-10min, walk-20min, bath,
    therapy-prep
  - `spirituality`: morning-practice, evening-practice,
    weekly-service, scripture-read
  - `health`: meds-am, meds-pm, vitamins, doctor-followup,
    weigh-in (also surfaced under kink if both roles selected),
    bloodwork-quarterly
  - `finance`: weekly-budget-review, monthly-budget-close,
    invoices-out, expenses-in
  - `household`: dishes, laundry, trash-out, groceries,
    deep-clean-weekly, bills-out
  - `pet-care`: feed-am, feed-pm, walk-am, walk-pm,
    litter-clean, vet-followup

- [ ] **LW-G.3** Smart-default toggle matrix per
  `(alignment, lifestyle)` (LOCK):

  - **submissive + strictly-kept**: all self-care on, all kink
    atoms on (cage/plug/posture/collar/edge/kegels), workout
    pushups+situps+squats+planks on, household dishes+laundry
    on, journal-entry on, check-in-with-keeper on.
  - **submissive + free** OR **submissive + single-free**:
    self-care on, kink atoms half on (collar+grooming+journal),
    workout/household sparse defaults.
  - **dominant + strictly-keeping**: self-care on, kink atoms
    `check-in-with-keeper` (rotated to "check-in-with-kept"
    label at render time when alignment=dom), `journal-entry`,
    `posture-check` (for kept partner planning), workout on.
  - **switch + strictly-shared**: self-care on, kink atoms
    on for both sides (full set), workout on.
  - **unaligned-private + any**: kink role hidden; defaults are
    self-care + workout-basic + study/work depending on roles
    chosen. No kink atoms exposed.
  - **partnered-***: family role default-on if selected; partner
    check-in atoms ride along.

- [ ] **LW-G.4** Bat-mascot sticker: `wiz_bat_templates_checklist` —
  bat-mascot ticking items on a small checklist clipboard.
- [ ] **LW-G.5** Skip button (LW-A.3): if skipped, smart-defaults
  hold; nothing is force-deselected.
- [ ] **LW-G.6** Per-row caption (small text under atom name) is
  the atom's one-line purpose, pulled from Agent 2's template
  metadata. E.g. `cage-check`: "morning and evening status check".

---

## Phase LW-H — Screen 7: Git setup

- [ ] **LW-H.1** Three cards stacked vertically, each with title,
  one-paragraph explanation, and a "use this" CTA. Cards
  intentionally not radio-buttons — each is a self-contained
  choice with its own inline flow.

  - **Phone-only** (recommended, default-highlighted)
    - Title: "keep it on this phone"
    - Body: "git-backed locally — full history, hand-editable,
      future-proof. You can add a remote later from Settings
      without losing anything."
    - CTA: "use phone-only" → goes to Screen 8 (scaffold) with
      `GitChoice.PhoneOnly`
    - Bat sticker overlay: `wiz_bat_git_phone_thumbsup`
      (thumbs-up while pointing at a phone), caption
      *"click-click — your stuff stays on the phone for now,
      totally fine ;3"*

  - **Self-hosted (Forgejo / Gitea)**
    - Title: "your own forge"
    - Body: "if you run a Forgejo or Gitea server, plug it in
      here. We'll generate a key or use OAuth."
    - CTA: "connect Forgejo" → opens existing Phase B SE-D
      OAuth Device Flow for Forgejo (or PAT fallback). On
      success: `GitChoice.SelfHostedForgejo(url)` and
      proceed to Screen 8.
    - Bat sticker overlay: `wiz_bat_git_selfhost_server`
      (bat-mascot patting a stylized server cabinet),
      caption *"if you've got your own forge, plug it in here"*
    - Footer link: "what's a forge?" → in-app docs page.

  - **GitHub**
    - Title: "sign in with GitHub"
    - Body: "private repo on github.com — works fine, and you can
      share it with a partner later."
    - CTA: "sign in with GitHub" → opens existing Phase B SE-D
      OAuth Device Flow for GitHub. On success:
      `GitChoice.GitHub(account)` and proceed to Screen 8.
    - Bat sticker overlay: `wiz_bat_git_github_octocat`
      (bat-mascot fist-bumping a generic-octocat silhouette,
      no GitHub-trademarked iconography re-used), caption
      *"github works, sign in once"*

- [ ] **LW-H.2** Phone-only path calls
  `GitRepo.initLocalOnly(rootDir, authorIdentity)` from
  `draft-no-origin-multi-origin.md` Phase MO-A.2. No remote is
  configured. Repo settings later expose "add a remote" per
  MO-G.3.
- [ ] **LW-H.3** Self-hosted / GitHub paths: on auth success the
  draft proceeds without yet creating the remote repo —
  remote-repo creation happens in Screen 8 (scaffold) so that
  failure during remote creation can be recovered to phone-only
  fallback without leaving the user in an inconsistent state.
- [ ] **LW-H.4** No skip on this screen — the user must pick a
  card. Phone-only is the safe default; if the user is
  flailing, the recommended-highlighting nudges toward it.
- [ ] **LW-H.5** Auth failure handling: any auth error returns
  the user to the three-card screen with the failed card's
  CTA showing an inline error chip ("sign-in cancelled — try
  again, or use phone-only"). Phone-only stays one tap away
  at all times.

---

## Phase LW-I — Screen 8: Calendar scaffolding (materialization)

This is the screen where everything becomes real on disk. The user
sees a brief progress UI while the wizard writes files. **All
disk writes happen here, not earlier.**

- [ ] **LW-I.1** Trigger: entering this screen kicks off the
  scaffold job in `WizardViewModel` via a `Channel<ScaffoldStep>`
  that streams progress events to the UI.
- [ ] **LW-I.2** Repo creation:
  - `GitChoice.PhoneOnly` → `GitRepo.initLocalOnly(...)`
  - `GitChoice.GitHub` / `GitChoice.SelfHostedForgejo` → create
    remote repo via provider API (Phase B SE-D), then
    `GitRepo.cloneFresh` into local cache, then proceed.
- [ ] **LW-I.3** Identity write: `identities/<bat-or-species>.md`
  with `display_name = "<species capitalized>"` (default; user
  edits later), `default_author = true`. Repo `user.name` /
  `user.email` set per D.7 / D.15.
- [ ] **LW-I.4** Calendar materialization: for each selected role
  (including auto-included `self-care`), create one calendar
  folder `calendars/cal-<role>/` with `calendar.toml` filled from
  Agent 2's per-role calendar template (color, emoji,
  priority defaults, notification group). Locked priority map:

  | role | priority | emoji |
  |---|---|---|
  | self-care | 600 | 🪥 |
  | kink | 700 | 🖤 |
  | work | 550 | 💼 |
  | university | 550 | 🎓 |
  | freelance | 540 | 🧰 |
  | workout | 500 | 🏋️ |
  | study | 500 | 📚 |
  | health | 650 | 🩺 |
  | household | 450 | 🧺 |
  | pet-care | 600 | 🐾 |
  | family | 500 | 👪 |
  | social | 400 | ☕ |
  | creative | 400 | 🎨 |
  | hobby | 380 | 🎲 |
  | recovery | 600 | 🛁 |
  | spirituality | 500 | 🕯️ |
  | finance | 450 | 💸 |

- [ ] **LW-I.5** Recurrence materialization: for each toggled
  atomic-activity template, create
  `calendars/cal-<role>/recurrences/<rule-id>.md` with the
  atom's template-provided default RRULE, dtstart, duration,
  notifications, and body. Time-of-day defaults pulled from
  Agent 2's per-atom registry (e.g. `brush-teeth` defaults to
  `07:30` and `22:00` — two recurrences from one atom is
  allowed; the registry says so).
- [ ] **LW-I.6** Todolist materialization: create one todolist
  `todolists/todo-onboarding/` with `todolist.toml` and seed
  five standing tasks at `standing/<uuid>.md`:
  1. "Open the app every day for 3 days" (no due, priority 700)
  2. "Customize a sticker" (priority 500)
  3. "Add your own atomic activity" (priority 500)
  4. "Invite a partner (or skip)" (priority 400)
  5. "Share a calendar with a partner (or skip)" (priority 400)
- [ ] **LW-I.7** Repo-level files: `README.md` (one paragraph
  describing this is a strictlykeptboy calendar repo, with a
  link to AGENTS.md), `AGENTS.md` + `CLAUDE.md` from Agent 2's
  data-model.md DM-G content + DM-K CLI-first additions, plus
  `.strictlykeptboy/schema.toml` and `repo.toml` (D.3 layout).
- [ ] **LW-I.8** Initial commit: single commit, message
  `wizard: scaffold lifestyle (<alignment>/<lifestyle>, <N> roles, <M> recurrences)`.
- [ ] **LW-I.9** Push (if remote configured): push the initial
  commit. Push failure does **not** unwind the local repo —
  the user keeps the local content, push enters the retry
  queue, the wizard banner shows "your phone has everything;
  we'll push when we can reach `<remote>`".
- [ ] **LW-I.10** Bat-mascot during the scaffold animation:
  `wiz_bat_scaffold_pleased` (bat-mascot watching a stylized
  calendar fill up with little dots, mildly delighted), caption
  *"watching the calendar fill up — bat is pleased ;3"*.
- [ ] **LW-I.11** Progress UI: a vertical list of bullet rows
  (calendars, recurrences, todolist, identity, commit, push)
  each transitioning from spinner → checkmark. Total step time
  budget < 3s on phone-only path; < 8s on remote-creation paths.
- [ ] **LW-I.12** Error handling: any non-push failure shows a
  full-screen "something went sideways" with the bat-mascot
  sticker `wiz_bat_scaffold_oops` (mild apologetic) and a
  "try again" / "switch to phone-only" pair of buttons.

---

## Phase LW-J — Screen 9: Done (handoff)

- [ ] **LW-J.1** Layout: top-half is a **live home-screen
  preview** — actually the real home screen rendered in a
  bounded surface, with the now-card populated by whatever
  event is currently active per the materialized calendars
  (resolver runs against the freshly-written repo; cold-start
  budget < 600ms per D.21 still applies). If nothing is
  active right now, the now-card shows "next up: <next event>"
  instead.
- [ ] **LW-J.2** Bottom-half: bat-mascot waving sticker
  (`wiz_bat_handoff_wave`) with a small inset of the chosen
  species sticker (`stickers/<species>/peek.webp`) peeking in
  from the side — explicit handoff iconography: bat-mascot
  passes the user off to their chosen avatar species.
- [ ] **LW-J.3** Caption: *"your lifestyle is live. `<species>`
  takes it from here ;3"*
- [ ] **LW-J.4** Single CTA: "open my calendar" → dismisses the
  wizard, navigates to the day-view at today.
- [ ] **LW-J.5** Soft secondary CTA, smaller: "show me what got
  set up" → opens a one-screen summary listing every calendar
  + todolist + recurrence count created, for users who want
  to verify before diving in.

---

## Phase LW-K — Bat-mascot sticker set spec

This phase enumerates every bat-mascot sticker the wizard needs
so an artist can produce them as a single coherent set, distinct
from the per-species sticker packs.

Asset naming convention: `drawable/wiz_bat_<key>.webp`. All
stickers are bat-mascot (not bat-avatar — there's only one
bat-mascot character, the Claude-the-builder persona). Suggested
canvas: 512×512 transparent, M3E-friendly palette aligned with
the launcher icon's dark-amber-on-near-black scheme.

- [ ] **LW-K.1** `wiz_bat_welcome_wave` — bat-mascot full-body
  wave, both wings up
- [ ] **LW-K.2** Species-greeting set (×7, one per default
  species): `wiz_bat_species_greeting_bat`,
  `..._bunny`, `..._cat`, `..._fox`, `..._lion`, `..._tiger`,
  `..._wolf` — each shows the bat-mascot sniffing-noses /
  paw-shake with a stylized species silhouette
- [ ] **LW-K.3** Alignment-reaction set (×4):
  `wiz_bat_align_dom_bow`, `wiz_bat_align_sub_collar`,
  `wiz_bat_align_switch_both`, `wiz_bat_align_unaligned_nod`
- [ ] **LW-K.4** Lifestyle-reaction set (×5):
  `wiz_bat_life_free_stretch`, `wiz_bat_life_kept_kneel`,
  `wiz_bat_life_keeping_clipboard`,
  `wiz_bat_life_shared_handshake`,
  `wiz_bat_life_routine_calendar`
- [ ] **LW-K.5** `wiz_bat_roles_notebook` — bat-mascot at a
  desk with notebook + pencil, intent peering
- [ ] **LW-K.6** `wiz_bat_templates_checklist` — bat-mascot
  ticking items on a clipboard
- [ ] **LW-K.7** Git-setup overlay set (×3):
  `wiz_bat_git_phone_thumbsup`,
  `wiz_bat_git_selfhost_server`,
  `wiz_bat_git_github_octocat`
- [ ] **LW-K.8** Scaffold-progress set (×2):
  `wiz_bat_scaffold_pleased`, `wiz_bat_scaffold_oops`
- [ ] **LW-K.9** `wiz_bat_handoff_wave` — bat-mascot waving as
  the chosen species silhouette steps into the frame from the
  side; also rendered with an overlay slot for the chosen
  species `peek.webp` at runtime
- [ ] **LW-K.10** Re-run-from-Settings entry sticker
  `wiz_bat_rerun_returns` — bat-mascot peeking back in with
  the notebook, "you wanted more?" energy (used in Phase LW-L)
- [ ] **LW-K.11** Documentation: produce a single sticker-set
  reference image (contact sheet) for the artist showing all
  poses on one page; ship in `docs/assets/wizard-bat-stickers.png`

---

## Phase LW-L — Re-run from Settings (additive)

- [ ] **LW-L.1** Settings → "Add more to my lifestyle" entry,
  visible only when at least one repo exists (i.e. after the
  initial wizard ran). Opens the wizard nav graph but seeded
  with the current `WizardDraft` reconstructed from the active
  primary repo's existing calendars / templates metadata
  (`template_origin` frontmatter field per TW-A.2 survives —
  this is one of the bits of TW-* that does NOT retire).
- [ ] **LW-L.2** Re-run entry-point: starts at Screen 5 (Roles)
  with current selections checked, NOT at Screen 1. Earlier
  screens (welcome / species / alignment / lifestyle) are
  reachable via a small "change the basics too" link at the
  top of the roles screen, which opens a separate
  "edit-basics" flow scoped to those four screens only.
- [ ] **LW-L.3** Additive semantics:
  - **Toggling a role ON** adds its calendar + smart-default
    templates (writes new files, never overwrites).
  - **Toggling a role OFF** does **NOT** delete the existing
    calendar or its files. Instead a confirm dialog says: "you
    can hide this calendar in overlay settings instead — turning
    it off here won't delete it. Hide it now?" with options
    *Hide* (sets `active_toggle = false` on the calendar's
    `calendar.toml`), *Keep visible* (no-op), or *Cancel*.
- [ ] **LW-L.4** Toggling a template within a role on/off has
  the same soft semantics — toggling ON creates a new
  recurrence file (if not already present, idempotent by
  `template_origin + template_slot` lookup); toggling OFF
  prompts to hide-vs-keep, where hide sets the recurrence's
  `active = false` (additive field added to the recurrence
  schema; no migration needed because it's optional).
- [ ] **LW-L.5** Bat-mascot returns for the re-run flow with
  `wiz_bat_rerun_returns` on screen entry, then the standard
  per-screen stickers from LW-K thereafter.
- [ ] **LW-L.6** Commit messages for re-run-driven changes:
  `wizard-rerun: add role <X>` / `wizard-rerun: enable
  template <atom> in <role>` / `wizard-rerun: hide calendar
  cal-<role>`.

---

## Phase LW-M — Testing strategy

- [ ] **LW-M.1** Robolectric path-coverage matrix: for every
  combination of `(alignment ∈ {dom, sub, switch, unaligned}) ×
  (lifestyle ∈ valid sub-options for that alignment)` — 4×4
  minus the unaligned-restricted cases ≈ 14 combos — walk the
  wizard end-to-end with default species (bat), default role
  set (auto-included only), default templates (smart-defaults
  hold), phone-only git. Assert after Phase LW-I:
  - Correct calendars exist on disk (D.3 paths)
  - Correct recurrence files exist per smart-default matrix
    (LW-G.3)
  - `todo-onboarding` exists with five standing tasks
  - `kink` role files exist iff alignment ≠ unaligned-private
  - Single initial commit, no push attempted in phone-only path
- [ ] **LW-M.2** Snapshot test the Screen 9 home-screen preview
  rendering for two canonical cases:
  - Mid-morning weekday (now-card shows the workout/work
    block per smart-defaults)
  - 03:00 Sunday (now-card shows "next up: brush-teeth at
    07:30" or similar — verifies the empty-now path)
- [ ] **LW-M.3** Mid-wizard exit + resume test: enter wizard,
  fill through Screen 4, kill the process, reopen, assert
  Screen 5 is the resume target with prior state intact.
- [ ] **LW-M.4** Re-run-from-Settings additive test: complete
  wizard with minimal selection (self-care only), open
  re-run, toggle on `workout` + `kink` (alignment=sub),
  assert two new calendars + new recurrences exist, original
  self-care files untouched.
- [ ] **LW-M.5** Hide-vs-delete test: complete wizard with
  workout on, re-run, toggle workout off, pick "Hide",
  assert calendar files still exist on disk and
  `active_toggle = false` in `calendar.toml`.
- [ ] **LW-M.6** Skip-policy test: launch wizard, hit skip on
  Screen 2 (species), assert species=bat applied; on Screen
  6 (templates), assert smart-defaults persist into the
  scaffold output.
- [ ] **LW-M.7** Unaligned-private isolation test: alignment=
  unaligned, lifestyle=routine, assert Screen 5 has no
  `kink` row, Screen 6 has no kink templates, scaffold
  produces no `cal-kink/` directory.
- [ ] **LW-M.8** Deep-link bypass test: launch
  `strictlykeptboy://add?url=...` intent on a fresh install,
  assert Phase QQ flow runs and wizard is NOT entered.
- [ ] **LW-M.9** Auth-failure recoverability test on Screen 7:
  mock GitHub OAuth to fail, assert user lands back on the
  three-card screen with inline error chip, then proceeds via
  phone-only successfully.

---

## Integration notes

When approved and folded in, the following edits are required.

### `docs/plans/main.md`

- **Replace Phase K wholesale** with this draft's Phase LW-A
  through LW-L (renumbered into K.1..K.N in main.md's
  letter-prefix scheme). The existing K.1..K.6 sub-steps retire.
- **Replace Phase L wholesale**. Demo mode is gone. The "demo
  content" purpose is subsumed into the wizard's scaffold output
  (the user's first run is the user's real data, not demo data).
  L.1 (`demo-sub`), L.2 (`demo-dom`), L.3 (demo-mode flag), L.4
  ("Exit demo mode") all retire. Replace the Phase L section
  with a one-paragraph note: "Demo mode retired — see Phase K
  wizard, which materializes a living calendar on first launch."
- **Phase QQ.1** wording change: clarify wizard-vs-deep-link
  precedence per LW-A.4. The first-launch routing reads:
  "If launched via `strictlykeptboy://add?...` deep-link and no
  repos configured → Phase QQ bootstrap. Otherwise if no repos
  configured → Phase K wizard."
- **Phase S.8** ("Demo section") retires — remove the bullet.
  Replace with "**S.8** Lifestyle section — 'Add more to my
  lifestyle' entry-point per Phase LW-L."
- **Phase T.2** (repo icon system) gains a note: per-species
  default icon comes from the wizard species choice unless
  overridden.

### `docs/plans/templates-demo-wizard.md`

**Sections that survive (with minor edits):**

- TW-A (template registry shape) — survives unchanged. The
  `template_origin` + `template_slot` frontmatter conventions
  are exactly what LW-I.5 and LW-L.4 depend on.
- TW-B (template content for the 14 named templates) —
  survives as **content source** for the per-atom template
  files that Agent 2 owns and LW-G.2 references. The TW-B
  template *names* are reorganized: instead of the original
  14 wholesale-lifestyle templates (`morning-bird`,
  `night-bat-owl`, etc.), the registry pivots to
  atomic-activities indexed by role. The wholesale-lifestyle
  templates become **smart-default presets** consumed by
  LW-G.3, not standalone toggles in the wizard.
- TW-E (role-toggle composition rules) — survives as
  background design rationale; the active composition rules
  are now LW-G.3.
- TW-F (wiring back into main plan) — retires (replaced by
  this draft's Integration notes section).

**Sections that retire:**

- TW-C (demo content paired repos: `demo-sub` + `demo-dom`) —
  retires entirely. No demo mode.
- TW-D (wizard flow as previously designed) — retires; this
  draft is the new wizard design.
- TW-G (demo specifics referenced by old main L) — retires.
- TW-H (open verification) — retires.

**Mark the file** with `## Status: PARTIALLY SUPERSEDED — see
draft-lifestyle-wizard.md` at the top, and explicitly mark
each retired section with `<!-- retired by LW; see
draft-lifestyle-wizard.md -->`.

### `docs/plans/decisions.md`

Add three new locked decisions at the end of Round 3 (or as a
fresh "Round 4 — lifestyle wizard locks" section):

- **D.54 — No empty-app start; wizard is mandatory on first
  launch.** Every first-launch path lands the user on a
  populated repo. The wizard's output is the user's canonical
  starting state, not seed/sample/demo data. Phone-only is a
  first-class wizard outcome (no remote required to finish).
  Demo mode is retired; the old `demo-sub` / `demo-dom` repo
  seeds are not shipped.
- **D.55 — Kink-positive default.** Wizard copy uses direct
  kink vocabulary (dominant / submissive / switch / strictly-kept
  / strictly-keeping / strictly-shared). The neutral-mode
  toggle is exposed as a Settings option (default off, i.e.
  kink-positive on). The "unaligned-private" alignment is the
  kink-off entry-point within the wizard itself and propagates
  to all downstream wizard screens (hides the kink role,
  hides kink templates, replaces strictly-X phrasing with
  "routine"). SFW phrasing constraints apply only to surfaces
  that leak outside the app: lockscreen widgets, Play-Store
  screenshots, neutral-mode toggles — not to in-wizard copy.
- **D.56 — Bat-mascot vs avatar-species are distinct
  characters.** The bat-mascot (Claude-the-builder persona) is
  the wizard guide on every wizard screen and on any future
  guided-onboarding surface. The user's avatar species
  (selected on wizard Screen 2 from {bat, fox, tiger, lion,
  wolf, bunny, cat, or a "choose your own" sticker repo}) is a
  separate character that takes over in-app mascot surfaces
  after the wizard ends. The default avatar (if the user
  skips Screen 2) is bat — coincidentally same species as the
  bat-mascot, but a distinct character.

---

## Summary

Twelve phases (LW-A through LW-M, with LW-K as the asset
spec and LW-M as testing). Locks include: kink-positive
default vocabulary throughout the wizard with unaligned-private
as the in-wizard kink-off path; bat-mascot as a separate
character from the user's avatar species; phone-only as a
first-class wizard outcome via `GitRepo.initLocalOnly`; demo
mode retired wholesale; smart-default template matrix per
alignment×lifestyle pre-toggling Screen 6; soft additive
semantics for re-running the wizard from Settings (toggling a
role off hides rather than deletes); a 13-key bat-mascot
sticker set spec for the artist; and a Robolectric path-
coverage matrix asserting correct calendars/recurrences/
todolist exist per alignment×lifestyle combo after Screen 8
materialization.

# strictlykeptboy — avatar + sticker presence-indicator draft

## Status: ✅ INTEGRATED — see `main.md` Phase WW and `decisions.md` D.63–D.69. UI scaffold at `ui-spec.md` Phase UI-LL. CLI surface at `cli-tooling.md` CLI-L.6–CLI-L.10. Original draft content preserved below for reference.

## Framing

This document scopes a new top-level phase to be inserted into `main.md`
after Phase VV (Homescreen countdown widget). The phase delivers the
**avatar + sticker presence-indicator system**: a now-card at the top of
the home screen showing a large sticker of the user's species doing the
current scheduled activity, plus the surrounding pipeline (pack format,
resolution chain, sub-beat support, "choose your own" species clone
flow, customization, default catalog, rendering pipeline, snapshot
tests).

**Locked intent (do not relitigate):**
- Avatar = **presence indicator**, not tamagotchi. No HP / mood /
  hunger. Sticker reflects what the user is *scheduled to be doing*
  right now, rendered cute.
- Optional **streak counter per activity** — count-only, no shame
  escalation, no nagging copy. Disabled by default; per-activity opt-in.
- **Kink-positive openly.** Default packs include kink-coded variants
  (cage, collar, harness, plug, posture) as first-class entries with a
  `kink` tag. A neutral-mode toggle (planned by Agent 5) filters on the
  per-sticker `neutral` / `kink` tag.
- **Default species roster shipped in-app:** bat, fox, tiger, lion,
  wolf, bunny, cat. **Bat is the default species.**
- **"Choose your own" species** = clone a GitHub repo of stickers into
  an app-private directory and register it as a selectable species.
- Android-only. Phone + tablet only. No Wear OS.

**Cross-references:**
- `decisions.md` D.5 (active-window resolver — drives current-activity
  pick), D.19 (theming, mascot stays bat), D.32 / Phase FF (existing
  sticker-pack format spec — this phase **supersedes** FF's pack-format
  bullet with the avatar-specific format and FF gets retargeted to
  emoji-in-title stickers only).
- `main.md` Phase F (UI scaffold), G (schedule views — now-card sits
  above day view content), T (theming + bat mascot), FF (sticker packs
  — retarget per note above), VV (homescreen widget — adjacent surface,
  independent codepath).
- `ui-spec.md` UI-C (schedule shell — now-card lands at the top of the
  shell, above the day timeline).

---

## Phase AV-A — Pack format + on-disk layout

- [ ] **AV-A.1** Lock pack format: **WebP** stickers at canonical
  512×512 px. Static WebP for idle/static activities; **animated WebP**
  permitted (and preferred) for motion activities (workout, walk,
  brushing-teeth). All packs render through the same Coil decoder; the
  resolver does not branch on static-vs-animated.
- [ ] **AV-A.2** Lock on-disk layout:
  `<pack-root>/pack.toml` + `<pack-root>/stickers/<activity-id>.webp`.
  Default packs ship under `app/src/main/assets/avatar-packs/<species>/`
  inside the APK. User-supplied packs cloned to
  `<app-private>/avatar-packs/<pack-id>/` where `<pack-id>` =
  SHA-256(normalized clone URL) truncated to 16 hex chars (mirrors
  D.51 source-repo-id discipline).
- [ ] **AV-A.3** Lock `pack.toml` schema:
  ```toml
  schema_version = 1
  name = "Bat — default cute"
  species = "bat"            # one of bat|fox|tiger|lion|wolf|bunny|cat|<custom-slug>
  author = "eight87"
  license = "CC-BY-4.0"
  style = "chibi"            # cute|chibi|realistic — free-form, no enum lock

  [[sticker]]
  activity_id = "sleep"
  file = "stickers/sleep.webp"
  tags = ["neutral"]         # ["neutral"] | ["kink"] | ["neutral","hygiene"] | ...
  animated = false           # hint, not enforced; decoder auto-detects

  [[sticker]]
  activity_id = "cage-check"
  file = "stickers/cage-check.webp"
  tags = ["kink", "posture"]
  ```
- [ ] **AV-A.4** Tag taxonomy locked at: `neutral`, `kink`, `hygiene`,
  `workout`, `meal`, `work`, `study`, `posture`, `rest`, `idle`. New
  tags allowed in user packs — unknown tags pass through and render
  unless explicitly filtered.
- [ ] **AV-A.5** Pack-validator routine: validate `pack.toml` schema
  version, required fields, file references resolvable on disk,
  duplicate `activity_id` rejected. Surface validation failures in the
  pack-install UI as a per-line error list.
- [ ] **AV-A.6** ProGuard/R8 keep-rules for the asset folder
  (`avatar-packs/**`) — assets must survive shrinking.

## Phase AV-B — Activity-to-sticker resolution chain

- [ ] **AV-B.1** Lock resolution order (top-down, first match wins):
  1. **Per-event override** — `sticker_id` field on the event's TOML
     frontmatter (additive, optional; resolver-ignored elsewhere).
  2. **Active sub-beat's `sticker_id`** (see Phase AV-D) — wins over
     event-level when a sub-beat is currently active.
  3. **Activity-specific sticker in the active pack** — match
     `event.activity_id` (or `event.category` if `activity_id` is
     unset) against `pack.toml` `[[sticker]].activity_id`.
  4. **Category-generic sticker in the active pack** — e.g.
     `activity_id = "workout-pushup"` falls back to
     `activity_id = "workout"` if the specific variant is missing.
     Category-generic mapping table locked in AV-G.
  5. **Species-idle** — `activity_id = "idle"` from the active pack.
  6. **Bat-fallback** — `activity_id = "idle"` from the shipped default
     bat pack. Bat-fallback is guaranteed to exist because the bat pack
     is bundled in the APK and validated at build time.
- [ ] **AV-B.2** Implement `StickerResolver` — pure function
  `(eventInstance, subBeatState, activePack, neutralMode, now) →
  StickerRef`. Memoized on `(activity_id, sub-beat index, pack id,
  neutral-mode)` for hot-path render skipping.
- [ ] **AV-B.3** Neutral-mode filter — when on, treat any `[[sticker]]`
  whose `tags` contains `"kink"` (and does **not** also contain
  `"neutral"`) as invisible to steps 3 and 4; the resolver falls
  through to step 5 (species-idle). Step 6 bat-fallback always honours
  the active neutral-mode setting too.
- [ ] **AV-B.4** Edge case: no event active right now → resolver enters
  at step 5 (species-idle).
- [ ] **AV-B.5** Edge case: overlapping events at the same moment (D.5
  priority overlap) → resolver uses the higher-priority event's
  `activity_id`. Ties broken by latest-start-time (matches D.5
  comparator).

## Phase AV-C — Now-card home layout

- [ ] **AV-C.1** Compose component `NowCard` lives at the top of the
  schedule shell (above the day timeline), hosted inside `UI-C`'s
  `ScheduleShell`. Card height: `220.dp` phone, `260.dp` tablet. Edge
  insets match the shell's horizontal padding (`16.dp` phone,
  `24.dp` tablet).
- [ ] **AV-C.2** Layout regions:
  - **Sticker zone** — left, square, 192×192 dp phone / 240×240 tablet.
    Hosts the resolved sticker via Coil.
  - **Title zone** — right of sticker, top: current task name (M3E
    `headlineSmall`, max 2 lines, ellipsize), under it: live duration
    chip (e.g. "23m left" using `bodyMedium`, M3E secondary container).
  - **Next-up strip** — right of sticker, below title: vertical list of
    up to 3 upcoming items, each row = small emoji + truncated title +
    relative start ("in 1h 20m"). Uses M3E `bodySmall`.
- [ ] **AV-C.3** Side ribbon (existing — out of this phase's scope to
  build, but lock the placement): the day/week/month/todolists tab strip
  remains in the top bar as defined by UI-B.5; the now-card does **not**
  duplicate it. Overlay-priority indicator (small badge bottom-right of
  sticker zone, e.g. `999 ▲`) when the active event has a non-default
  priority. Tap badge → priority-tiebreak sheet (already defined in
  UI-G).
- [ ] **AV-C.4** Now-card refresh strategy: recomposes on
  `(now-tick, active-event-changed, sub-beat-boundary)` flows. Now-tick
  cadence: every 30s while app foreground, every 60s while
  notification-foreground-service is alive. No background recomposition
  when app suspended.
- [ ] **AV-C.5** Tap behavior: tap sticker zone → open active event's
  detail sheet (Phase G.7). Tap next-up row → open that event's detail
  sheet. Tap title zone → open active event's detail sheet (same target
  as sticker — large hit area).
- [ ] **AV-C.6** Empty state: when no event is active and the next
  upcoming is more than 4h out, render species-idle sticker + copy
  "nothing scheduled — back at <next start time>". No tamagotchi
  guilt-trips.
- [ ] **AV-C.7** Streak counter (opt-in per activity): when the user has
  enabled a streak for the currently-active activity (per-activity
  toggle in Settings → Appearance → Avatar → Streaks), render a
  small chip overlay on the sticker zone bottom-left: `🔥 12` (12
  consecutive days). Count-only. No shame copy if broken — chip simply
  resets to `🔥 1` next time the streak starts again.
- [ ] **AV-C.8** Accessibility: sticker zone `contentDescription` =
  `"<species> <activity>"` (e.g. "bat sleeping"). Streak chip readable
  by TalkBack ("12-day streak"). Animated WebP respects the system
  "Remove animations" accessibility setting → falls back to first
  frame.

## Phase AV-D — Sub-beat sticker support

- [ ] **AV-D.1** Lock event-frontmatter schema for sub-beats (additive,
  optional, fully backward-compatible — events without `[[subbeat]]`
  behave exactly as today):
  ```toml
  [[subbeat]]
  label = "upper-left"
  duration_seconds = 30
  sticker_id = "brush-teeth-upper-left"   # optional override

  [[subbeat]]
  label = "upper-right"
  duration_seconds = 30
  sticker_id = "brush-teeth-upper-right"

  # ...repeated for each quadrant
  ```
- [ ] **AV-D.2** Sub-beat clock: when the active event has `[[subbeat]]`
  entries, the resolver computes `(now - event_start) % total_subbeats`
  and emits the matching sub-beat to `StickerResolver`. Sub-beats run
  sequentially; total duration = sum of `duration_seconds`; if shorter
  than the parent event, the cycle loops; if longer, the cycle truncates
  at event end.
- [ ] **AV-D.3** Sticker swap is smooth: 200ms cross-fade via
  `Crossfade` Compose, no jarring snap.
- [ ] **AV-D.4** Sub-beat lookup chain: sub-beat's `sticker_id` (if
  present) → otherwise `<parent-activity-id>-<sub-beat-label>` against
  the active pack → otherwise the parent activity's sticker (i.e. fall
  back up to AV-B step 3).
- [ ] **AV-D.5** Optional audible tick / haptic on sub-beat boundary —
  off by default; toggle in Settings → Appearance → Avatar → "Pulse on
  sub-beat boundary" (haptic only; no audio — audio is its own can of
  worms).
- [ ] **AV-D.6** CLI surface: `skb event add` accepts `--subbeat
  "<label>:<seconds>"` repeated; `skb event show` renders sub-beats in
  the JSON output under `subbeats: [...]`.

## Phase AV-E — "Choose your own" species flow

- [ ] **AV-E.1** Entry point: Settings → Appearance → Avatar → "Add
  custom species pack". Input field accepts HTTPS or SSH GitHub URL.
- [ ] **AV-E.2** Validation pipeline (run before persisting):
  1. URL parse + normalize (mirrors D.51 rules).
  2. Shallow clone (`--depth=1`) into `<app-private>/avatar-packs/<pack-id>/`.
  3. Locate `pack.toml` at clone root → validate per AV-A.5.
  4. Confirm at least one `[[sticker]]` with `activity_id = "idle"`
     exists (idle is the species-fallback rung — required).
  5. Surface result: success → register species; failure → red banner
     with line-level error list, leave clone in place but unregistered
     until "Retry" or "Remove".
- [ ] **AV-E.3** Auth: reuse the per-repo credential machinery from
  Phase B (SSH key or OAuth token). A custom species pack repo is
  registered as a **read-only data source** (refuses push always),
  separate from calendar-data repos.
- [ ] **AV-E.4** Update path: "Update pack" button in pack settings
  runs `git pull --ff-only`; failure on non-fast-forward falls back to
  a fresh clone after user confirmation.
- [ ] **AV-E.5** "Fork the default" affordance: a button labelled "Make
  my own pack" links to the canonical template repo
  `https://github.com/eight87/strictlykeptboy-sticker-pack-template`.
  **Locked-as-default:** template repo URL is hardcoded as a build-time
  constant `STICKER_PACK_TEMPLATE_URL`; overridable via app settings
  → Advanced → "Custom template repo URL" for users who want to fork
  from a different starting point. Repo will be created during the
  AV phase implementation.
- [ ] **AV-E.6** Removal path: tap pack → "Remove" → deletes the clone
  dir + unregisters; if it was the active species, falls back to bat.
- [ ] **AV-E.7** Multi-pack-per-species support: more than one pack may
  declare the same `species`. **Locked-as-default:** when multiple
  packs share a species, the most-recently-added pack wins as the
  active default; user can override per-species in Settings →
  Appearance → Avatar → "Active pack for <species>".

## Phase AV-F — Customization + per-activity overrides

- [ ] **AV-F.1** Per-activity sticker override UI: Settings →
  Appearance → Avatar → "Customize stickers" → list of all activities
  in the active pack + an "override with" picker per row (other
  packs / other species).
- [ ] **AV-F.2** Override storage: **app-private
  `EncryptedSharedPreferences` keyed `avatar.overrides.<activity_id>
  → <pack-id>:<sticker-activity-id>`**. **Locked-as-default:** NOT
  committed to the user data repo. Rationale: device aesthetic, not
  life-data; would be device-specific noise across multi-device users;
  and a partner/dom reading the calendar repo has no business with
  the user's per-device sticker prefs. Documented in AGENTS.md
  template under "what is NOT in this repo".
- [ ] **AV-F.3** Override resolver hook: `StickerResolver` consults
  the overrides map between AV-B step 1 (per-event override) and
  AV-B step 2 (sub-beat) — i.e. event-level overrides outrank
  device-level overrides, which outrank pack-level defaults.
- [ ] **AV-F.4** Reset path: "Reset all overrides" button in the same
  settings screen; per-activity "Reset this one" affordance on each
  row.
- [ ] **AV-F.5** Export/import overrides (advanced): "Export overrides
  to file" → JSON file in Downloads; "Import overrides from file" →
  picker + validation + merge-confirm. **Locked-as-default:** v1 ships
  export only; import lands in v1.1 (parser must validate referenced
  pack-ids exist on this device, which is a discovery problem worth
  deferring).

## Phase AV-G — Default sticker catalog (atomic activities)

Every default species pack (bat, fox, tiger, lion, wolf, bunny, cat)
MUST include stickers for the activity IDs below. Artwork creation is
implementation work outside this plan's scope; the lock is the **list
of required IDs**.

- [ ] **AV-G.1** Neutral set (mandatory in every pack, tagged
  `neutral`):
  - `sleep`, `wake`, `idle`, `focus`
  - `brush-teeth`, `shower`, `shave`
  - `eat-breakfast`, `eat-lunch`, `eat-dinner`
  - `work`, `study`
  - `workout-pushup`, `workout-situp`, `workout-squat`
  - `walk`
- [ ] **AV-G.2** Kink set (mandatory in every pack, tagged `kink`;
  filtered out in neutral mode):
  - `cage-check`, `posture-check`, `collar-check`, `harness-check`,
    `plug-check`, `edge-and-stop`
- [ ] **AV-G.3** Category-generic mapping locked (used by AV-B step 4):
  - `workout-*` → `workout-pushup` if specific variant missing
  - `eat-*` → `eat-lunch` (the neutral "any meal" sticker)
  - `*-check` → `posture-check` (the neutral "any kink check" sticker)
  - Any unknown `activity_id` → species-idle (AV-B step 5).
- [ ] **AV-G.4** Sub-beat sticker IDs MUST exist for the canonical
  sub-beat-bearing activities in the default packs:
  - `brush-teeth-upper-left`, `brush-teeth-upper-right`,
    `brush-teeth-lower-left`, `brush-teeth-lower-right`,
    `brush-teeth-tongue`, `brush-teeth-finish` (the 6-quadrant
    toothbrush sequence)
  - `workout-pushup-down`, `workout-pushup-up` (animated pair)
- [ ] **AV-G.5** Build-time validator: a Gradle task scans every
  bundled default pack and asserts AV-G.1 + AV-G.2 + AV-G.4 are
  present. Build fails on missing IDs. Prevents shipping with gaps.
- [ ] **AV-G.6** Licensing: default packs ship under CC-BY-4.0; About
  screen lists artist credit per pack. **Locked-as-default:** one
  artist per species pack is fine; if multiple contributors, the
  `author` field is a comma-separated list and About renders the
  same way.

## Phase AV-H — Rendering pipeline + cache

- [ ] **AV-H.1** Use **Coil 2.x** (Apache-2.0, license-clean per D.40)
  for all sticker loading. Single shared `ImageLoader` instance on the
  app graph, configured with:
  - Memory cache: LRU sized to ~40 stickers (target ≈ 40 × 512KB ≈
    20 MB). Tunable via `BuildConfig.AVATAR_LRU_SIZE`.
  - Disk cache: off for bundled assets (already on disk); on for
    cloned-pack stickers (Coil-managed default location).
  - `ImageDecoderDecoder` registered (Android 28+) for animated WebP;
    `GifDecoder` not needed.
- [ ] **AV-H.2** Preload strategy: on now-card recomposition, kick off
  preload requests for `[current sticker, next event sticker, next
  event +1 sticker, next event +2 sticker]`. Misses fall back to a
  shimmer placeholder until decode completes.
- [ ] **AV-H.3** Animated WebP playback control: respect system
  "Remove animations" → first-frame fallback. Otherwise loop
  indefinitely while sticker is on screen; pause when off-screen
  (Compose `DisposableEffect`).
- [ ] **AV-H.4** Memory budget audit: log `ImageLoader.memoryCache.size`
  on each now-card recomposition in debug builds; emit a Logcat
  warning if it exceeds 25 MB sustained.
- [ ] **AV-H.5** Cold-start budget: now-card visible with resolved
  sticker within `300ms` of schedule view first-frame (Phase V.1
  has 600ms total → now-card is half that envelope).
- [ ] **AV-H.6** Sticker swap budget: AV-D.3 cross-fade completes in
  200ms with no dropped frames at 60Hz.

## Phase AV-I — Test fixtures + snapshot suite

- [ ] **AV-I.1** Roborazzi (Apache-2.0) screenshot harness in the
  `:app` androidTest source set; one fixture per below scenario:
  - **cold start, idle** — no active event, no upcoming for >4h →
    bat species-idle sticker, "nothing scheduled" copy.
  - **event-active** — fake "brush-teeth" event running, no sub-beat
    → bat `brush-teeth` sticker, "Brush teeth" title, "1m 23s left"
    duration chip.
  - **sub-beat mid-event** — same brush-teeth event, frozen clock
    1m 35s in → bat `brush-teeth-upper-right` sticker (2nd quadrant
    of 6 × 30s, with 5s carry-over).
  - **missing-pack fallback** — synthetic event with
    `activity_id = "nonsense-xyz"` → bat species-idle (AV-B step 5).
  - **bat-fallback** — active species set to a custom pack with no
    matching sticker AND no idle sticker → bat species-idle (AV-B
    step 6).
  - **neutral-mode active** — event with `activity_id = "cage-check"`
    + neutral-mode on → species-idle (kink sticker filtered, falls
    through to step 5).
  - **streak chip on** — opt-in streak active, 12-day count → chip
    renders bottom-left of sticker zone.
  - **TalkBack labels** — verify content-description strings per AV-C.8.
- [ ] **AV-I.2** Time-frozen clock fixture: `FakeClock` injected via
  the DI module that ships in Phase A.8; tests pin `now` to a known
  instant per scenario.
- [ ] **AV-I.3** Resolver unit tests (pure JVM, Robolectric-free) cover
  every step transition in AV-B (per-event override hit, sub-beat
  hit, activity hit, category-generic hit, species-idle hit,
  bat-fallback hit) + the neutral-mode filter at each rung.
- [ ] **AV-I.4** Pack-validator unit tests: malformed `pack.toml`
  (missing required field, duplicate activity_id, file not found, bad
  schema version) → each produces the expected validation error.
- [ ] **AV-I.5** Build-time default-pack validator (AV-G.5) gets a
  Gradle test that asserts a synthetic incomplete pack (missing
  `cage-check`, say) fails the validator.

---

## Integration notes

These edits land via the integration agent later. **Do not apply them
from this draft** — the integration agent owns the lock-step
modifications.

### `main.md`
- Insert new section after Phase VV and before "Notes on parallel
  deep-dives":
  ```
  ## Phase WW — Avatar + sticker presence-indicator
  Deep-dive: draft-avatar-stickers.md (to be promoted to
  avatar-stickers.md on integration). Phases AV-A through AV-I.
  - [ ] **WW.1** Ship pack format + default bat pack (AV-A + AV-G)
  - [ ] **WW.2** Ship resolver + now-card (AV-B + AV-C)
  - [ ] **WW.3** Ship sub-beat support (AV-D)
  - [ ] **WW.4** Ship "choose your own" species flow (AV-E)
  - [ ] **WW.5** Ship customization (AV-F)
  - [ ] **WW.6** Ship rendering pipeline + cache (AV-H)
  - [ ] **WW.7** Ship snapshot test suite (AV-I)
  ```
- Update Round 2 cross-reference table to include the new doc
  alongside `cli-tooling.md` etc.

### `ui-spec.md`
- Extend UI-C (`ScheduleShell`) sub-steps to host `NowCard` at the top,
  reserving the documented 220 dp / 260 dp slot before the day timeline.
- Add new phase **UI-LL — Now-card** (or next free letter) referencing
  AV-C as the authoritative layout spec; UI-LL owns the Compose
  scaffolding inside the shell. AV-C remains the source of truth for
  the layout.

### `decisions.md`
- Add the following new decisions block at the end (D.54 onward):
  - **D.54** Avatar is a presence indicator, not a tamagotchi. Streak
    counter is the only quantified element; count-only, opt-in, no
    shame copy.
  - **D.55** Default species roster: bat, fox, tiger, lion, wolf,
    bunny, cat. Bat is the app default + wizard guide species.
  - **D.56** Sticker pack format: WebP @ 512×512, `pack.toml` manifest,
    `<species>/<activity-id>.webp` layout. Bundled default packs in
    APK assets; user packs cloned to app-private dir keyed by
    SHA-256(URL).
  - **D.57** Resolution chain order: per-event override → device
    override → sub-beat → activity-specific → category-generic →
    species-idle → bat-fallback. Neutral-mode filters at the
    activity-specific + category-generic rungs.
  - **D.58** Device-level sticker overrides are app-private (NOT
    committed to user data repo). Per-event sticker overrides ARE
    committed (additive frontmatter field).
  - **D.59** Sub-beats are an additive optional event frontmatter
    array (`[[subbeat]]`); events without sub-beats behave exactly as
    today.
  - **D.60** "Choose your own" species clones a GitHub repo into an
    app-private dir; canonical template at
    `https://github.com/eight87/strictlykeptboy-sticker-pack-template`.

### `cli-tooling.md`
- Extend with a new sub-surface:
  - `skb sticker list-packs` — list installed packs with species + style
  - `skb sticker set-active <species> <pack-id>` — pick active pack
    per species
  - `skb sticker add-pack <url>` — clone a custom pack (mirrors AV-E
    headlessly; same validation chain)
  - `skb sticker validate <pack-dir>` — run AV-A.5 validator against a
    local pack
  - `skb sticker show <activity-id> [--neutral]` — print the resolved
    sticker file path for an activity (handy for Claude when
    composing event-detail responses)
  - All under exit-code taxonomy + `--json` per D.24.

### `templates-demo-wizard.md`
- Wizard adds a "Pick your species" step (after the welcome step,
  before path-pick). Bat preselected. The bat wizard guide itself
  remains bat regardless of the user's pick (continuity).
- `demo-sub` repo seed adds an event with `[[subbeat]]` entries (the
  6-quadrant toothbrush) and an event with a `sticker_id` override
  (per-event override path) so AV-B step 1 is demoed out of the box.
- Wizard streak-onboarding card: "Want a streak counter for daily
  hygiene?" → opt-in toggle for `brush-teeth` streak. Single screen,
  skippable.

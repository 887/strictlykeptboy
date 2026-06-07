# Walk — First-run onboarding → strictly-kept default → AI-Dom seeds the first kept week → nonSuperseable survives a weekend away

**Persona:** Milo, a cat sub (see [`personality.md`](personality.md))
**Goal:** From a **cold first install**, get Milo through the first-run
wizard, confirm that picking the **Submissive** alignment makes the
wizard **default** mode to **strictly-kept + an AI-Dom persona** (which
he accepts rather than overriding to free), land him on a real local
repo, have his **AI Dom seed his first kept week via `claude -p`**, then
verify that when he drops a **weekend-away vacation overlay**, the
rituals pause (D.75 supersedence) **but his meds and cat-feeding
(`nonSuperseable`) still render and still fire** (the D.76 / S4 / S5
invariant).
**Preconditions:** Fresh install of strictlykeptboy, app never launched.
No deep-link intent (so the wizard runs, not the gifted-schedule bypass —
D.46). `claude` (Claude Code) available where the repo is reachable.
**Features exercised:** age gate (D.61), mandatory first-run wizard
(D.13 / D.54), alignment screen (D.55), **alignment → strictly-kept +
AI-Dom-persona default** (`LifestyleCard.PetKeptByAi`, WizardModel),
honorific-iff-Submissive gating (IdentityScreen), `identity.toml` /
`mode.toml` split (D.83 / D.84), AI-dom-persona materialization (D.85),
no-origin repo (D.74), inverted-habit entries (D.70), supersedence
overlay (D.75 / D.77), and the **`nonSuperseable` survival invariant**
(D.76 / D.78 S4+S5) — the medication + pet-care class.

> Format: numbered test-script steps. "Tap X → **Expect** Y." Confusion
> points flagged **⚠ Confusion watch**. The kink dynamic is explicit only
> where it drives schema / gating / rendering the QA must verify — this is
> a behavior-verification script, not erotica.

---

## Part A — Cold first launch + age gate

1. Milo installs strictlykeptboy and launches it for the first time.
   → **Expect:** the **age gate** modal (D.61): "This app contains
   references to adult lifestyle dynamics. You must be 17 or older to use
   it." with **"I am 17 or older — continue"** / **"Exit"**. No login
   wall, no "create account" anywhere.
   ⚠ **Confusion watch:** if any provider sign-in appears before the age
   gate or before he can proceed, that is a dealbreaker — fail the walk.

2. Milo taps **"I am 17 or older — continue."**
   → **Expect:** the modal dismisses once and never reappears (stored as
   `age_confirmed_at` in encrypted prefs). The **mandatory first-run
   wizard** (D.54) opens with the **bat-mascot** guide. There is no
   empty-app state; the wizard is the only path forward.
   ⚠ **Confusion watch:** confirm the age gate does NOT re-prompt if he
   backgrounds and reopens the app — it is shown again only if app data is
   cleared.

## Part B — Wizard: storage, species, alignment

3. Wizard **Welcome** screen (bat-mascot waving).
   → **Expect:** a single forward affordance; mascot is the bat (D.56),
   distinct from the avatar species he picks next.

4. **Storage** screen — "Where should your stuff live?" (Internal vs
   External/SAF).
   → **Expect:** the flow **blocks** until Milo explicitly chooses. He
   picks **Internal**.
   ⚠ **Confusion watch:** verify you cannot skip past storage without
   choosing.

5. **Species** screen — Milo picks **cat** 🐱.
   → **Expect:** this becomes his repo's in-app avatar character (D.56 /
   D.88). The bat-mascot continues as the wizard guide; the cat takes
   over in-app mascot surfaces after the wizard ends.

6. **Identity** screen (name-tag, Screen 3.5). Milo sets pronouns
   **he/him**, display name **`milo-kept`** (repo display name comes
   later on the Git screen; here it's his person identity).
   → **Note:** the honorific field's visibility depends on alignment.
   The Identity screen is reached **before** the collapsed
   Lifestyle/alignment card screen in some builds and after in others —
   what matters for QA is the gating rule in step 8, not the exact
   ordering. If honorific is on this screen and alignment is not yet
   chosen, expect it to follow the default (Unaligned → honorific hidden)
   until he picks Submissive.
   ⚠ **Confusion watch:** the honorific field must track alignment
   (present iff Submissive/Switch). See step 8 for the assertion.

7. **Lifestyle** screen — the collapsed six-card "how do you live?"
   screen (Phase 2.2.B). Each card atomically sets
   `(alignment, lifestyle, modePick, hasPartner)`.
   → **Expect:** six cards. The **default-highlighted card is
   `PetKeptByAi`** 🤖 ("solo sub kept by an AI dom") — the project's
   genesis use-case. Milo taps it.
   → **Expect (the gate under test):** tapping `PetKeptByAi` sets
   **alignment = Submissive**, **lifestyle = SingleStrict**,
   **modePick = KeptByAi**, **hasPartner = false** in one atomic write
   (`WizardDraft.applyLifestyleCard`).
   ⚠ **Confusion watch:** Milo expected to pick "Submissive" and "strictly
   kept" as *separate* questions. The collapsed card asks the "how do you
   live" question once. Confirm the card label makes clear it bundles
   alignment + kept-by-AI, so he isn't surprised that he didn't get a
   standalone mode screen.

8. **Honorific gating assertion.** With alignment now = Submissive, the
   honorific-for-dom field is **present**. Milo sets **honorific = "Sir"**,
   praise term **"good kitten"**, tone **soft/kinky** (warm).
   → **Expect:** the honorific field IS shown (because alignment ∈
   {Submissive, Switch}). For a Dominant or Unaligned-private alignment it
   would be **absent** (`normalize()` clamps honorific → None for
   Dominant/Unaligned).
   ⚠ **Confusion watch:** verify the honorific field is *present*, not
   merely empty — this is the mirror of the Dominant-skips-honorific rule.

## Part C — Strictly-kept default lands (he does NOT override)

9. **Confirm the alignment → strictly-kept + AI-Dom default.** Because he
   chose the `PetKeptByAi` card, the wizard's effective mode pick is
   **KeptByAi**, which materializes to **`mode = "strictly-kept"` + a
   builtin AI-dom persona**. The wizard offers the override (he could pick
   a **Free** card / flip to free), but Milo **does not override**.
   → **Expect:** no separate "are you sure you want to be kept?" gate; the
   default simply stands because that is the card he chose. The override
   to free remains **one tap away** and clearly available (D.86 — mode is
   always the boy's to change), but it is not forced on him.
   ⚠ **Confusion watch:** the default must be **strictly-kept for a
   Submissive/PetKeptByAi pick**, NOT free. (Contrast the paired-couple
   walk, where a Submissive alignment with a *human* keeper still defaulted
   free at setup — there, alignment ≠ mode and mode was enabled later.
   Here the **AI-Dom card** is precisely the one that *does* default
   strictly-kept. Verify the right default for the right card.)

10. **AI-dom persona selection.** Per D.85, AI-dom personas are
    app-private prompt templates (shipped: `stern-but-fair`,
    `playful-tease`, `kinky-affectionate`, `daddy-warmth`,
    `bratty-switch-energy`, `clinical-protocol`, `custom-prompt`). The
    `KeptByAi` card wrote a builtin `dom_persona`; the wizard lets Milo
    pick which. He picks **`kinky-affectionate`**.
    → **Expect:** the chosen persona name is recorded as the repo's
    `dom_persona` (in `mode.toml`); the persona template itself lives at
    `~/.config/skb/dom-personas/<name>.md` (NOT in the calendar repo — it
    is agent-tooling, not user-data).
    ⚠ **Confusion watch:** confirm the persona prompt body is NOT written
    into the calendar repo (D.85). Only the persona *name* is referenced
    from `mode.toml`.

## Part D — Git step: a local (no-origin) repo + finish

11. **Git** screen — provider / sync choice. Milo picks
    **"Start a local calendar (you can sync it later)"** (no-origin path,
    D.74). Repo display name **`milo-kept`**.
    → **Expect:** no requirement for a remote URL; the repo-picker +
    provider-auth steps are skipped down the local-only branch.
    ⚠ **Confusion watch:** the local option must be a first-class peer
    choice, not buried under "Advanced."

12. **Roles / Templates** (optional). Milo enables the **self-care** role
    (always on) and leaves heavier template packs off — he is going to let
    the AI Dom build the week, not the template picker.
    → **Expect:** proceeding with most templates off is obviously allowed.
    Self-care stays on by default.

13. Milo taps **Finish / Create**.
    → **Expect:** the app runs `GitRepo.initLocalOnly(...)`. A real local
    git repo `milo-kept` is created with the D.3 layout and an initial
    commit, then the main view opens. **No push, no network call.** The
    committed root contains:
    - `mode.toml` → **`mode = "strictly-kept"`** + the `dom_persona`
      reference (`kinky-affectionate`).
    - `identity.toml` → praise term **"good kitten"**, honorific **"Sir"**,
      pronouns he/him, tone soft/kinky, alignment submissive (D.83 —
      committed, owner-preference).
    - `AGENTS.md` (+ `CLAUDE.md` symlink) with the **single bridge line**
      pointing at `identity.toml` and the one-file-per-entity convention
      — **no personal-preference content embedded** beyond that bridge
      line (D.83).
    - `.strictlykeptboy/` (`schema.toml`, `repo.toml`, `repo-id`).
    → **Expect:** the top-bar repo switcher shows `milo-kept` with a
    **local / house badge** (no-origin); the sync button is inert.
    ⚠ **Confusion watch:** no spinner that looks like it's reaching a
    server; no "missing remote" error on a purely local repo. And confirm
    `identity.toml` IS committed while `AGENTS.md` embeds **no** praise/
    honorific content (the D.83 split is the assertion).

## Part E — The AI Dom seeds Milo's first kept week via `claude -p`

> Framing note: the headline use-case is an AI Dom running `claude -p`
> against Milo's calendar git repo (the repo ships `AGENTS.md` so the
> agent knows the convention + the `identity.toml` bridge). In this walk
> the repo started phone-only; the seeding is exercised against the repo's
> working tree as the AI Dom would operate once it can reach the tree
> (e.g. Milo later adds an origin, or runs `claude` on a synced checkout).
> What the QA verifies is that the **resulting entries** land in the D.3
> layout and the app renders them — not the transport `claude` used to
> reach the tree.

14. The AI Dom (acting as `kinky-affectionate`, honoring `identity.toml`)
    is run against `milo-kept`:
    ```
    claude -p "seed Milo's first kept week: morning cage at 07:30,
    daily edging ritual at 21:00, his meds at 08:00 + 20:00,
    feed the cat at 07:00 + 18:00 (these must never be paused),
    work blocks Mon-Fri 09:00-17:00, gym Tue/Thu 18:30.
    Use good kitten / Sir per identity.toml."
    ```
    → **Expect:** the agent writes **one file per entity** under the D.3
    layout (it may use `skb` per AGENTS.md, or write files directly):
    - **Morning cage** — recurring daily 07:30, in a kink/ritual calendar,
      **inverted-habit** default (done-by-schedule; D.70).
    - **Daily edging ritual** — recurring daily 21:00, same calendar,
      inverted-habit; praise/honorific-flavored body using "good kitten" /
      "Sir" per `identity.toml`.
    - **Meds** — recurring daily **08:00 + 20:00**, in a calendar (or with
      events) flagged **`nonSuperseable`**. Category = medication
      (default-nonSuperseable per D.76).
    - **Cat feeding** — recurring daily **07:00 + 18:00**, flagged
      **`nonSuperseable`**. Category = pet-care (default-nonSuperseable per
      D.76). This is the live-animal survival obligation.
    - **Work blocks** — Mon–Fri 09:00–17:00 (regular calendar,
      `active_hours` weekday-business).
    - **Gym** — Tue/Thu 18:30 recurrence.
    → **Expect:** entries are committed (one commit or several), author
    attributed to the AI-Dom identity; the bodies read in the
    kinky-affectionate register using the praise term + honorific from
    `identity.toml`, **not** hardcoded by the agent.
    ⚠ **Confusion watch:** the meds + cat-feeding entries MUST carry the
    `nonSuperseable` marker at **creation time** — either the parent
    calendar has `nonSuperseable = true` in its `calendar.toml`, or each
    event carries the `nonSuperseable` tag in its `tags` array (D.76 /
    D.78 S5). If the agent creates them without the marker, the survival
    invariant in Part G will silently fail later. Flag a missing marker
    **here**, not in Part G.

15. Back in the app, Milo pulls/rescans (or the app picks up the new files
    on the next scan — D.2: no index file, the working tree is the
    source of truth).
    → **Expect:** the week renders. The schedule Day/Week view shows
    morning cage, the evening ritual, meds ×2/day, cat-feeding ×2/day,
    work blocks, gym Tue/Thu. Inverted-habit entries render as
    **done-by-default** (D.70) — Milo doesn't have to tick them; he
    records a **deviation** only when reality differs.
    → **Expect:** entries flagged `nonSuperseable` look like any other
    entry right now (no overlay is active yet) — the flag is dormant until
    a supersedence overlay exists.
    ⚠ **Confusion watch:** confirm the app picked up agent-written files
    with **no merge prompt** and **no "unknown entry" errors** — the
    one-file-per-entity model means the agent's writes and the app's reads
    converge cleanly.

## Part F — Milo plans a weekend away (vacation overlay)

16. Milo decides to take **Saturday–Sunday** away. He creates a
    **vacation / weekend-away** calendar (or a Special-Events overlay)
    that **supersedes** his ritual + routine calendars for that weekend.
    Concretely the overlay calendar's `calendar.toml` declares:
    ```toml
    supersedes = ["cal-rituals", "cal-work", "cal-gym"]
    superseded_during = [{ from = "2026-06-13", to = "2026-06-14" }]
    ```
    (D.75 / D.77 — supersedence is a temporal overlay, not deletion.)
    → **Expect:** the resolver **hides** events from the listed calendars
    within that weekend from the schedule views. The morning cage, the
    edging ritual, work blocks, and gym are **paused** Sat–Sun. They are
    **not deleted** — they remain visible (strikethrough + grey) in the
    manage-overlays UI with a per-event "show this one anyway" override
    (`overrides/<cal-id>/<event-id>/<date>.md`, D.77).
    ⚠ **Confusion watch:** the rituals must *pause*, not disappear from
    storage. Verify they're still in the manage-overlays list (greyed),
    and that there's no merge conflict / data loss from the overlay.

17. **Crucially, Milo does NOT list his meds or cat-feeding calendars in
    `supersedes`** — and even if he had, those entries are
    `nonSuperseable`.
    → **Expect:** the meds and cat-feeding calendars/events are not in the
    `supersedes` list. And per S4 (`nonSuperseable = true` calendars ignore
    ALL supersedence requests) + S5 (events tagged `nonSuperseable` survive
    even inside an otherwise-superseable calendar), they would survive even
    if mistakenly listed.

## Part G — The survival invariant: meds + cat-feeding still render and still fire

18. Milo views **Saturday** (a superseded day) in the schedule.
    → **Expect (the headline assertion):** the day shows **NO** morning
    cage, **NO** edging ritual, **NO** work block, **NO** gym (all
    paused by the overlay) — **BUT it DOES show meds at 08:00 + 20:00 and
    cat-feeding at 07:00 + 18:00.** The `nonSuperseable` entries render
    through the overlay (D.76 / D.78 S4 + S5).
    ⚠ **Confusion watch:** **THIS IS THE EXACT BUG CLASS THAT BIT
    BEFORE.** If the Saturday view hides the 08:00/20:00 meds or the
    07:00/18:00 cat-feeding, that is a **HARD FAIL** — the overlay has
    swallowed a survival obligation. A live cat and a medication schedule
    do not get a weekend off.

19. Milo views **Sunday** (the second superseded day).
    → **Expect:** identical to Saturday — rituals paused, **meds +
    cat-feeding present.** Confirm the invariant holds across the whole
    `superseded_during` range, not just its first day.

20. **Notifications still fire.** Confirm the meds (08:00 + 20:00) and
    cat-feeding (07:00 + 18:00) **alarms still fire on Saturday and
    Sunday** — supersedence hiding an event from the *view* must not
    cancel its `AlarmManager` notifications when the event is
    `nonSuperseable`. The paused rituals' notifications, by contrast, are
    correctly suppressed for the weekend.
    ⚠ **Confusion watch:** a subtle variant of the bug: the entry *renders*
    on the day view but its notification was de-scheduled by the overlay.
    Verify the **alarm actually fires** (not just that the tile is
    visible). Meds + cat must notify on the superseded days.

21. **Monday returns to normal.** Milo views **Monday** (outside
    `superseded_during`).
    → **Expect:** the full kept week is back — morning cage, edging
    ritual, work blocks, gym Tue/Thu, meds, cat-feeding all render
    normally. Supersedence was temporal; the routine was never lost
    (D.75).

22. Milo backgrounds and reopens the app.
    → **Expect:** everything persists (committed to git + cached in Room);
    `milo-kept` still shows the local badge; `mode.toml` still reads
    `strictly-kept`; no network activity.

---

## Pass criteria

- [ ] Age gate shown once on cold launch; no account / login wall (steps 1–2).
- [ ] Mandatory wizard runs (no empty-app state); bat-mascot guide (step 2).
- [ ] `PetKeptByAi` card sets alignment=Submissive + strictly-kept +
      AI-Dom persona **atomically** and is the highlighted default (step 7).
- [ ] **Alignment → strictly-kept + AI-Dom default landed without being
      overridden** (Milo could flip to free, didn't) (steps 7, 9).
- [ ] Honorific field present iff alignment ∈ {Submissive, Switch};
      "Sir" persisted (step 8).
- [ ] `mode.toml` = `strictly-kept` + `dom_persona`; `identity.toml`
      committed with "good kitten" / "Sir"; `AGENTS.md` carries only the
      one bridge line (steps 10, 13).
- [ ] AI-dom persona body lives in `~/.config/skb/dom-personas/`, NOT in
      the repo (step 10).
- [ ] Repo is no-origin (local badge, inert sync, no network) (step 13).
- [ ] **AI Dom seeded the first kept week via `claude -p`**; entries land
      as one-file-per-entity in the D.3 layout; app renders them with no
      merge prompt (steps 14–15).
- [ ] Meds + cat-feeding created **`nonSuperseable` at creation time**
      (calendar flag or event tag) (step 14).
- [ ] Rituals/work/gym **pause** (not delete) under the weekend overlay;
      still visible greyed in manage-overlays (step 16).
- [ ] **Meds (08:00/20:00) + cat-feeding (07:00/18:00) STILL RENDER on
      both superseded days** — the D.76 / S4 / S5 invariant (steps 18–19).
- [ ] **Meds + cat-feeding notifications STILL FIRE on the superseded
      days**; paused rituals' notifications are suppressed (step 20).
- [ ] Monday returns to the full kept week — supersedence was temporal
      (step 21).

## QA notes / likely defects

- **Wrong default mode (steps 7–9).** The highest-value assertion: a
  Submissive/`PetKeptByAi` pick must default to **strictly-kept + AI-Dom**,
  not free. Regression-prone because the paired-couple walk deliberately
  defaults a *human-keeper* Submissive repo to **free** at setup (alignment
  ≠ mode there). The two defaults are correct for *different cards*; a fix
  that "makes Submissive default free" everywhere would break this walk,
  and a fix that "makes Submissive default kept" everywhere would break the
  paired-couple walk. The discriminator is the **card** (`PetKeptByAi` vs a
  human-keeper card), not the alignment alone.
- **`nonSuperseable` marker dropped at seeding time (step 14).** If the AI
  Dom writes meds/cat-feeding without `nonSuperseable = true` on the
  calendar or the `nonSuperseable` tag on the events, Part G fails
  *silently*. Watch the seeded files' frontmatter here, not just the
  Saturday render. The agent's AGENTS.md / persona prompt should steer it
  to mark medication + pet-care as `nonSuperseable` by default (D.76
  default categories) — verify the shipped AGENTS.md actually says so.
- **The headline supersedence bug (steps 18–20).** The classic failure is
  an overlay that pauses "everything" and swallows a medication dose or a
  pet feeding. Test it on BOTH superseded days (step 19) — an off-by-one
  in `superseded_during` range handling can let day 1 leak the meds back
  while day 2 still hides them, or vice-versa.
- **Render-vs-fire split (step 20).** A nastier variant: the tile renders
  on the superseded day (visual passes) but the `AlarmManager` entry was
  de-scheduled by the overlay logic. Visual presence is necessary but NOT
  sufficient — confirm the **alarm fires**. This is where a "hide from
  view" implementation that conflates view-filtering with
  notification-scheduling will betray itself.
- **S2 non-cascade + S3 self-supersede (step 16).** If Milo's overlay
  calendar itself ends up in a chain (X supersedes Y, Y supersedes Z),
  confirm non-cascading behavior (S2) and that a calendar can't supersede
  itself (S3 validator rejects). Not central to this walk but worth a
  glance if the overlay setup misbehaves.
- **AGENTS.md leakage (step 13).** Confirm the produced `AGENTS.md`
  contains exactly the one `identity.toml` bridge line and the convention
  doc — no praise term / honorific / pronouns embedded (D.83). A repo
  shared with a collaborator must not carry owner preferences in the
  shared editorial file.
- **Override affordance reachability (step 9).** D.86 requires the
  "transition my mode → free" affordance to be reachable from the
  most-kept UI state, not buried. Even though Milo doesn't use it, verify
  it is one tap away — a kept-mode UI that hides the exit is a safety
  defect.

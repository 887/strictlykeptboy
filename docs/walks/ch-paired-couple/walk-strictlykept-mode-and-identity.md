# Walk — strictly-kept mode + identity, routine authoring, a deviation

> Persona: **Lena Hofmann** (kept partner, repo `lena-routine`) and
> **Théo Brunner** (keeper). Zürich. SFW-but-charged, consenting-adult
> lifestyle organisation — see `personality.md`. Keep it warm and
> structural: routine, ritual, praise, honorific. Never explicit.
>
> Preconditions: `walk-paired-repos-setup.md` complete. Both repos exist,
> mutually shared with write access, both currently in **free** mode.
> `lena-routine` alignment = Submissive.
>
> Goal: (A) turn on **strictly-kept** mode on `lena-routine` via
> `mode.toml`; (B) configure **`identity.toml`** (honorific / praise /
> pronouns / tone register) through the wizard, in **French values with
> English keys**; (C) Théo authors a **recurring daily routine** as
> **passive / inverted habits**; (D) Lena **misses one item** and it is
> recorded as a single **deviation file** (no destroyed series, no
> shame).
>
> Format: numbered steps, "tap X → expect Y", **[QA]** = confusion point.

---

## Part A — Enable strictly-kept mode (`mode.toml`)

> Contract (D.84): strictly-kept does **not** block edits. Lena's (and
> Théo's) commits to `lena-routine` still apply **immediately** — no
> approval gate, no veto. What changes: every commit materialises a
> `reviews/<commit-sha>/reviewable_change.md` entry that Théo consumes via
> cross-repo feedback (Phase YY). This is the only behaviour difference.

1. Lena activates `lena-routine` (top-bar repo switcher) → **Settings →**
   the per-repo **Mode** control. **Expect** current value **free**.
2. Set mode → **strictly-kept**. **Expect** the change writes
   `mode = "strictly-kept"` to **`mode.toml`** at repo root and that this
   is **committed** (mode transitions ARE history, D.84). **[QA]** verify
   the schema key is English (`mode = "strictly-kept"`), not localised.
3. **Expect** an always-visible **"transition my mode" / take-back
   affordance** reachable even from the most-kept UI state (D.86) — i.e.
   Lena can see, right now, how she'd go *back* to free. **[QA]** this
   must NOT be buried; flag if it's hard to find. This is the consent
   safety surface.
4. **Expect NO blocking gate appears** when Lena (or Théo) next edits the
   repo. Make a trivial edit → it applies immediately. **[QA]** confirm
   there is no "waiting for Dom approval" state anywhere — that would
   violate D.84/D.86.
5. After the next commit, **expect** a `reviews/<sha>/reviewable_change.md`
   to materialise in `lena-routine` (D.84 / DM-Z.1) with a register-aware
   `auto_summary` that uses Lena's praise term. (Set in Part B; right now
   it uses the default until identity is configured.)
6. **Privacy interaction:** mark one event `private = true` and commit →
   **expect** that entity is **skipped** from the review feed entirely
   (SH-N.8 / D.57). **[QA]** confirm a private event never appears in
   Théo's review feed even in strictly-kept mode.

## Part B — Configure `identity.toml` via the wizard

> `identity.toml` blocks (DM-Y.2): `[praise]` (`term`, `alt_terms`),
> `[pronouns]`, `[honorific_for_dom]` (`term`), `[tone]` (`register`,
> `emoji_density`). It IS committed (D.83). AGENTS.md only references it
> via the single bridge line. Lena chose **Submissive** alignment, so the
> honorific block is offered.

7. Open the identity wizard (re-run wizard / Settings → Identity →
   "Praise + pronouns", the LW Screen 3.5 surface, TW-K.1). **Expect**
   the current defaults pre-filled: `praise.term = "good boy"`, pronouns
   he/him/his, `honorific_for_dom.term = "Sir"`, `tone.register =
   "soft-kinky"`, `emoji_density = "medium"` (DM-Y.3).
8. **Pronouns:** change to **she/her/hers/herself**. → **expect** written
   to `[pronouns]` block.
9. **Praise term:** set primary to **`mon trésor`**; add an alternation
   set **`ma douce`** as `alt_terms` (multi-select chips, TW-K.2). →
   **expect** `[praise] term = "mon trésor"`, `alt_terms = ["ma douce"]`.
   **[QA]** verify the **value** is French but the **keys** (`term`,
   `alt_terms`) stay English — this is the central i18n test (D.83,
   English-only schema rule). Flag any localised key immediately.
10. **Honorific-for-dom:** set to **`Monsieur`** (custom value; default
    chips were Sir/Daddy/Master/Mistress/Owner/Keeper/Captain). →
    **expect** `[honorific_for_dom] term = "Monsieur"`. **[QA]** confirm
    the field is present (Submissive alignment) and accepts a custom
    French value.
11. **Tone register:** keep **soft-kinky** (default for kinky alignments).
    Emoji density **medium**.
12. Save → **expect** `identity.toml` rewritten and **committed**, and the
    **AGENTS.md bridge line unchanged** (still exactly one line pointing
    at identity.toml; no praise/honorific copied into AGENTS.md — D.83).
    **[QA]** diff AGENTS.md: it must NOT contain "mon trésor" / "Monsieur"
    / pronouns. Personal preference lives only in identity.toml.
13. **Expect** chrome + briefings now address Lena using her identity:
    salutations / now-card / morning-briefing use **`mon trésor`** (or
    alternating `ma douce`), register soft-kinky. **[QA]** verify the
    praise term actually surfaces in at least one chrome location (now-card
    or briefing salutation) — proves identity.toml is wired, not just
    stored.
14. **Théo's view of Lena's identity:** on Théo's device, switch active
    repo to `lena-routine` → **expect** the top-bar identity context +
    any dom-side review copy address her as **mon trésor / Monsieur**,
    because each repo carries its own identity.toml and Théo's dom-Claude
    register reads *Lena's* file when acting in her repo (D.88).
    **[QA]** confirm Théo authoring INTO Lena's repo picks up Lena's
    identity.toml, not Théo's.

## Part C — Théo authors a recurring daily routine as passive habits

> Inverted-habit model (D.70): **default state for any past-or-current
> scheduled event = `completed-by-schedule`.** No ticking to "prove"
> adherence. A miss is the explicit action. Routines are calendar
> overlays (`calendar.toml`: `routine`, `routine_id`,
> `routine_default_start`, `routine_can_materialize`), not entities.

15. Théo activates `lena-routine`, creates a calendar **"Routine
    quotidienne"** → set it as a **routine overlay** (`routine = true`).
    **[QA]** verify the calendar-level `routine` flag is set and English-
    keyed despite the French calendar *name*.
16. Théo authors recurring daily entries as **inverted / passive habits**,
    e.g.:
    - **07:00 réveil + ancrage matinal** (morning anchor), daily RRULE.
    - **13:00 pause déjeuner** (lunch), weekdays.
    - **18:30 mouvement** (movement / workout), Mon/Wed/Fri.
    - **22:30 routine du soir** (evening wind-down), daily.
    For each: set recurrence (RFC5545 RRULE) and **mark it inverted /
    passive** (default-by-schedule). **Expect** each is **one file per
    entity** under `calendars/<id>/events/<yyyy>/<mm>/<uuid>.md` (D.3),
    authored by **Théo** (author chip), in **Lena's** repo.
17. Sync → Lena pulls. **Expect** today's routine renders on Lena's
    Schedule with each past/current item shown **completed-by-schedule by
    default** — **no checkboxes to tick**, no "0/4 done" nag. **[QA]** the
    headline test: confirm the routine reads as *done by default*, not as
    an empty to-do list. A streak counter, if shown, is count-only with
    no shame copy (D.63).
18. **Expect** the review feed (Part A): Théo's authoring commits each
    materialised a `reviews/<sha>/reviewable_change.md` Lena's repo, which
    Théo also sees consumed on his side via cross-repo feedback. He can
    react (e.g. `good-boy`/`collar`/`heart`) — an empty-text `good-boy`
    reaction renders as the cute LGTM (D.84). **[QA]** confirm reactions
    are tokens written to **Théo's own repo** (feedbacker's repo), not
    into Lena's (D.73).

## Part D — A missed item becomes a deviation (not a destroyed series)

> Deviation (D.70): `deviations/<calendar-id>/<entity-id>/<yyyy-mm-dd>.md`,
> `kind ∈ {skipped, partial, completed-early, completed-late}`. It is a
> **post-hoc reality report**, separate from `exceptions/` (which is a
> scheduling-side cancel). The recurring rule is **never edited** to drop
> a date.

19. Lena slept through the **07:00 ancrage matinal** today. On her
    Schedule, open that instance → **expect** an affordance like "I didn't
    do this / deviation" (NOT a generic delete). Choose **skipped**.
20. **Expect** this writes a single file
    `deviations/<routine-cal>/<morning-anchor-uuid>/<today>.md` with
    `kind = "skipped"` (D.70). **[QA]** verify:
    - The **recurring rule file is untouched** (no silent date drop).
    - **No `exceptions/` file** is created (a miss is a deviation, not a
      cancel). Confusing the two is a likely user/QA error — flag the
      semantics in the UI copy.
21. **Expect** only **today's 07:00** shows the deviation; **tomorrow's
    07:00 is intact** and still defaults to completed-by-schedule. The
    series survives. **[QA]** confirm the rest of the recurrence is
    unaffected.
22. **Partial / off-schedule variants:** redo for a **partial** (e.g.
    Lena did a short movement session) → `kind = "partial"`; and for
    **completed-late** → `kind = "completed-late"`. **[QA]** confirm each
    distinct `kind` writes the correct value and renders distinctly.
23. **Théo sees the deviation, low-drama:** Théo activates `lena-routine`
    → today's anchor shows the **skipped** deviation. Strictly-kept means
    he can react via cross-repo feedback (e.g. a gentle `heart` or a
    free-text note in his own repo). **[QA]** confirm there is **no
    punitive UI**, no "streak broken" modal, no automatic escalation
    (D.70 / D.85 — AI-dom never withholds responses punitively).

---

## Exit state

- `lena-routine`: **strictly-kept** (`mode.toml` committed),
  `identity.toml` = mon trésor / ma douce / Monsieur / she-her /
  soft-kinky (French values, English keys, committed). AGENTS.md still one
  bridge line.
- A **routine overlay** authored by Théo runs as **passive / inverted
  habits**, rendering done-by-default on Lena's schedule.
- One **deviation file** records today's missed morning anchor; the
  recurring series is intact; the review feed reflects it with no
  punitive surface.

## Confusion points to watch (summary for QA)

- **[QA]** Strictly-kept ≠ approval gate. Edits still apply immediately;
  the only delta is the review feed (Part A, steps 2–5).
- **[QA]** "Transition my mode" / take-back affordance must be reachable
  from the most-kept state, never buried (step 3) — the consent safety.
- **[QA]** English schema keys, French values throughout identity.toml
  (steps 9–10) — the central i18n test.
- **[QA]** AGENTS.md must not absorb identity.toml content (step 12).
- **[QA]** Théo authoring into Lena's repo uses *Lena's* identity.toml
  (step 14).
- **[QA]** Routine reads as done-by-default, not an empty to-do list
  (step 17). No streak shame.
- **[QA]** A miss = a `deviations/` file, NOT an `exceptions/` cancel, and
  the rule file stays untouched (steps 20–21). UI copy should make this
  distinction obvious.
- **[QA]** No punitive UI on a deviation (step 23).

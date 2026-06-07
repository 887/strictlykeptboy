# Walk — AI Dom nightly authoring: `claude -p` lays out the day, app syncs + renders, a work trip supersedes the rituals

**Persona:** Caspian ("Cas"), a kept fox sub, kept *solo* by an AI Dom
("Sir") that runs `claude -p` against his repo `cas-kept`
(see [`personality.md`](personality.md)).
**Goal:** Verify the killer use-case end to end — (A) preconditions:
`cas-kept` is **strictly-kept** with an `identity.toml`; (B) Sir runs a
**nightly `claude -p` authoring pass** that lands kinky + mundane entries
as **one-file-per-entity commits** in `cas-kept`; (C) the app **syncs and
renders** them, kink events carrying an **author chip** (foreign-source,
authored by Sir); (D) a **work trip triggers supersedence** — cage/edging
rituals **pause** (greyed, not dropped) for the trip window while
**meds + hydration survive** because they are `nonSuperseable`; rituals
**resume** after the trip.
**Preconditions:** `cas-kept` exists, **strictly-kept**, an **HTTPS remote
(PAT/OAuth — SSH transport is gated "coming soon" in the app)**, alignment
**Submissive**, app installed + the repo synced once. A local clone of
`cas-kept` exists on Cas's laptop where `claude -p` runs.
**Features exercised:** `claude -p` repo authoring against `AGENTS.md`
(DM-G / D.24), one-file-per-entity commits (D.3), author chips (D.15),
strictly-kept mode + review feed (D.84), `identity.toml` (D.83),
inverted/passive habits (D.54/D.70), supersedence overlay (D.75–D.78),
`nonSuperseable` (D.76).

> Framing: SFW-but-charged. Cage-on / cage-off / edging / collar / kept
> check-in are **schedule entries** with kink-coded *values*; every
> schema *key* is English. Keep it warm and structural. Never explicit.
> Format: numbered steps, "→ Expect:", "⚠ Confusion watch:".

---

## Part A — Confirm the kept repo is ready (strictly-kept + identity)

1. Cas activates `cas-kept` (top-bar repo switcher) → **Settings → Mode**.
   → **Expect:** mode reads **strictly-kept**, backed by `mode = "strictly-kept"`
   in **`mode.toml`** at repo root (D.84).
   ⚠ **Confusion watch:** the always-visible **"transition my mode" /
   take-back affordance** (D.86) must be reachable from this most-kept
   state — Cas can see right now how he'd go back to `free`. If buried,
   flag it. This is the consent safety surface, and it matters *more* when
   the keeper is an autonomous agent.

2. Settings → Identity ("Praise + pronouns").
   → **Expect:** `identity.toml` reads `praise.term = "good fox"`,
   `alt_terms = ["sweet kit"]`, `honorific_for_dom.term = "Sir"`,
   `pronouns` he/him, `tone.register = "soft-kinky"`, `emoji_density =
   "medium"` (D.83). **All schema keys English; kink flavour only in the
   values.**
   ⚠ **Confusion watch:** flag immediately if any key is localised /
   kink-coded (e.g. a `cage` or `kink` *key* rather than value). The
   English-only schema rule is non-negotiable.

3. Open `AGENTS.md` at repo root (Files / detail, or on the laptop clone).
   → **Expect:** it documents the one-file-per-entity TOML-frontmatter +
   Markdown convention (DM-G) and contains exactly **one bridge line**
   pointing at `identity.toml` ("See `identity.toml` … use these when
   generating content for this user.") — **no** praise term / honorific
   copied into AGENTS.md (D.83).
   ⚠ **Confusion watch:** if `AGENTS.md` embeds "good fox" / "Sir" /
   pronouns, that is a D.83 violation — Sir would then read identity from
   the wrong file. Flag it.

4. Confirm Sir has a repo identity.
   → **Expect:** `identities/<sir-person-id>.md` exists with
   `display_name = "Sir"` (and `default_author = false` — Cas's own
   identity is the `default_author`). This is what makes Sir-authored
   entries carry a **distinct author chip** (D.15/D.88). There is **no
   app-level second account** — Sir is a repo identity only.
   ⚠ **Confusion watch:** if there's only one identity in the repo, every
   entry will chip as Cas and the "authored by Sir" signal collapses —
   flag a missing Sir identity.

## Part B — Sir's nightly `claude -p` authoring pass

> This is the spine. On the laptop, Cas runs a single `claude -p`
> invocation against the local clone of `cas-kept`. Sir reads `AGENTS.md`
> + `identity.toml`, then authors tomorrow as **one file per entity**,
> commits, and pushes. The app picks it up on next sync.

5. **The invocation.** Cas runs (from inside the `cas-kept` clone):

   ```bash
   claude -p "You are Sir, keeping Cas. Read AGENTS.md and identity.toml.
   Lay out tomorrow (2026-06-09) for him in this repo. Author as the 'Sir'
   identity (author = the Sir person-id). One file per entity, TOML
   frontmatter + Markdown body, bucketed per the convention.

   On calendar cal-ritual (the kept rituals, inverted/passive habits):
     - 07:00 collar ritual (morning), 10 min
     - 07:15 cage-on for the day, all-day window ending 22:00
     - 21:30 edging ritual, 20 min
     - 22:00 cage-off + kept evening check-in, 15 min
   On calendar cal-work: 09:00-12:00 deep-work block, 13:30-17:00 meetings.
   On calendar cal-life: 12:30 lunch, 18:00 gym.
   On calendar cal-health (nonSuperseable): 08:00 + 20:00 meds, hourly hydration.

   Use my praise term and honorific from identity.toml in the bodies.
   Commit each as one-file-per-entity; do not write an index file."
   ```

   → **Expect:** `claude -p` reads the repo, writes new entity files, and
   commits — no API, no .ics; it authors *files*. Because `AGENTS.md`
   documents the convention and `identity.toml` carries the register, the
   entries are well-formed on the first pass and addressed correctly
   ("Good fox — cage on for the day, Sir's watching." style bodies).
   ⚠ **Confusion watch:** if Sir writes an **index file**, edits a
   recurrence rule to inject a one-off, or dumps all of tomorrow into a
   single file, that violates D.3 (one entity per file, the bucket dirs
   *are* the index). Flag it — and it means `AGENTS.md` under-specified
   the convention.

6. **Inspect the resulting files.** Each new entity is one file under
   `calendars/<cal-id>/events/2026/06/<uuidv7>.md`. A kink entry should
   look like (the **collar ritual**):

   ```
   +++
   author = "sir"
   calendar = "cal-ritual"
   start = "2026-06-09T07:00:00+02:00"
   duration_minutes = 10
   title = "Collar ritual"
   inverted = true
   tags = ["ritual", "collar", "kept"]
   private = true
   +++
   Good fox — collar on first thing. Sir expects it before coffee.
   ```

   and the **cage-on window**:

   ```
   +++
   author = "sir"
   calendar = "cal-ritual"
   start = "2026-06-09T07:15:00+02:00"
   end = "2026-06-09T22:00:00+02:00"
   title = "Cage on (for the day)"
   inverted = true
   tags = ["ritual", "cage", "kept"]
   private = true
   +++
   On until 22:00 check-in. Sweet kit stays locked through the gym block.
   ```

   and a **`nonSuperseable` meds** entry:

   ```
   +++
   author = "sir"
   calendar = "cal-health"
   start = "2026-06-09T08:00:00+02:00"
   duration_minutes = 5
   title = "Morning meds"
   inverted = true
   nonSuperseable = true
   tags = ["medication", "nonSuperseable"]
   +++
   Non-negotiable. This stays on even when the rituals pause.
   ```

   → **Expect:** every schema **key** is English (`author`, `calendar`,
   `start`, `inverted`, `tags`, `nonSuperseable`, `private`); kink flavour
   lives only in **values** (`"collar"`, `"cage"`, `"kept"`) and the
   Markdown body. `author = "sir"` on the kink entries; `inverted = true`
   marks them passive habits (D.54); `private = true` on kink entries
   (D.57); `nonSuperseable = true` on the health entries (D.76).
   ⚠ **Confusion watch:** a kink-coded **key** (e.g. `cage_on = true` as a
   bespoke field, or a German/locale jargon key) is a hard fail. Also flag
   if Sir omitted `inverted` on the rituals (they'd then read as a to-do
   list to tick, not done-by-default).

7. **Push + sync.** Sir's pass pushed to the GitHub remote. On Cas's
   phone, pull-to-sync `cas-kept`.
   → **Expect:** the new files land; the next-day schedule populates.
   Because mode is strictly-kept, each of Sir's commits also materialised
   a `reviews/<sha>/reviewable_change.md` (D.84) — informational, **not a
   gate**. Cas's view is not blocked waiting on anyone.
   ⚠ **Confusion watch:** there must be **no "waiting for Dom approval"
   state** anywhere (D.84). Sir authored, it applied, the app renders it.
   If a gate appears, fail the walk.

## Part C — The app renders Sir's day, kink entries carrying an author chip

8. Cas opens the **Schedule → Day** view for 2026-06-09.
   → **Expect:** the day shows, interleaved: 07:00 collar ritual, the
   07:15→22:00 cage-on window, 09:00 deep-work, 12:30 lunch, 13:30
   meetings, 18:00 gym, 21:30 edging ritual, 22:00 cage-off + check-in,
   plus 08:00/20:00 meds and hydration. Mundane and kinky on one schedule.

9. **Author chips.** Every event tile carries a 24dp circular **author
   chip** (D.15). On the kink + work entries Sir authored, the chip is
   **Sir's** avatar/initials — foreign-source, authored by Sir — visibly
   distinct from anything Cas authored himself.
   → **Expect:** the cage/edging/collar/check-in tiles chip as **Sir**;
   any entry Cas added himself chips as **Cas**. Tap the top-bar
   **"filter by author"** dropdown → filtering to **Sir** isolates exactly
   the nightly-authored set.
   ⚠ **Confusion watch:** if the kink entries chip as **Cas** (the
   `default_author`) instead of **Sir**, attribution resolved off the
   committer/default instead of the `author` frontmatter field — flag it;
   the "kept by Sir" signal is the whole point. Also confirm a **private**
   kink entry (D.57) still renders its title *in-app* (privacy only
   affects lockscreen/widgets, not the in-app tile).

10. **Done-by-default, no nag.** The 07:00 collar ritual and the cage-on
    window are `inverted = true` → they render **completed-by-schedule by
    default** (D.54/D.70) — **no checkbox to tick**, no "0/4 rituals done"
    counter.
    → **Expect:** the rituals read as *done by default*. A streak counter,
    if shown, is count-only with no shame copy (D.63).
    ⚠ **Confusion watch:** if the rituals render as an empty to-do list
    demanding ticks, that's a D.54 inversion failure — flag it. Cas being
    kept ≠ Cas proving adherence box-by-box.

11. **(Optional) a deviation, low-drama.** Say Cas missed the 21:30 edging
    ritual. Open that instance → choose **"I didn't do this / deviation" →
    skipped**.
    → **Expect:** one file `deviations/cal-ritual/<edging-uuid>/2026-06-09.md`
    with `kind = "skipped"` (D.70). The recurrence rule is untouched; **no
    `exceptions/` file**; tomorrow's instance is intact. No punitive UI,
    no "streak broken" modal (D.85 — the AI Dom never withholds/escalates
    punitively).
    ⚠ **Confusion watch:** a miss must be a `deviations/` file, **not** an
    `exceptions/` cancel, and must **not** edit the rule (D.77). Flag any
    confusion in the UI copy between "I didn't do it" and "cancel this".

## Part D — A work trip supersedes the rituals (meds survive)

> Supersedence (D.75): a trip calendar declares `supersedes = [...]` +
> `superseded_during = [{from, to}]`; the resolver **hides** the listed
> calendars' events in that window — greyed/strikethrough in
> manage-overlays, hidden from the day view — **without deleting** them.
> `nonSuperseable` (D.76) calendars/events **ignore** supersedence.

12. Cas (or Sir, in a follow-up `claude -p`) creates a **trip overlay**
    calendar `cal-trip-berlin` for **2026-06-11 → 2026-06-13**, with
    `calendar.toml`:

    ```
    +++
    name = "Work trip — Berlin"
    supersedes = ["cal-ritual"]
    superseded_during = [{ from = "2026-06-11", to = "2026-06-13" }]
    +++
    ```

    → **Expect:** the schema keys are English; `supersedes` lists the
    **ritual** calendar (cage/edging/collar/check-in) — **not** `cal-health`.
    ⚠ **Confusion watch:** if Sir/Cas adds `cal-health` to `supersedes`,
    the validator/resolver must still keep its `nonSuperseable` entries on
    (D.76 S4/S5). But the *correct* authoring is to omit it — flag if the
    UI/agent suggests pausing health.

13. Open **Schedule → Day** for **2026-06-12** (mid-trip).
    → **Expect:** the **cage-on window, collar ritual, edging ritual, and
    kept check-in are GONE from the day view** — paused, because
    `cal-ritual` is superseded for 06-11→06-13. The work-trip entries show
    instead.
    ⚠ **Confusion watch:** the rituals must be **hidden, not deleted**. If
    the app silently *dropped* or *cancelled* them (an `exceptions/` write,
    a rule edit), that's the dealbreaker D.75 failure — fail the walk.

14. **Meds + hydration survive.** Still on 2026-06-12, look at the health
    entries.
    → **Expect:** **08:00/20:00 meds and hourly hydration are STILL ON**
    the day view (D.76) — `nonSuperseable = true` (and `cal-health`'s
    own `nonSuperseable` calendar flag) make them ignore the trip's
    supersedence. A vacation/trip never silently skips meds.
    ⚠ **Confusion watch:** if meds vanished alongside the rituals, the
    `nonSuperseable` survival rule (D.76 S5) is broken — fail the walk.
    This is the single highest-stakes assertion in the script.

15. **The rituals are *paused*, not lost — verify in manage-overlays.**
    Open the **manage-overlays** UI for the trip window.
    → **Expect:** the superseded `cal-ritual` entries appear
    **strikethrough + greyed** (D.75), each with a per-event **"show this
    one anyway" override**. They are visibly *paused*, not erased. Tap a
    "show anyway" on one entry → it writes
    `overrides/cal-ritual/<event-id>/2026-06-12.md` with
    `kind = "force-show"` (D.77) and that single instance re-renders on the
    day view while the rest stay paused.
    ⚠ **Confusion watch:** confirm the override writes to `overrides/`,
    **not** `exceptions/` (cancel) or `deviations/` (reality-differed) —
    three sibling dirs, three distinct semantics (D.77). Flag any UI that
    conflates them.

16. **Resume after the trip.** Open **Schedule → Day** for **2026-06-14**
    (after the trip window).
    → **Expect:** the cage-on window, collar ritual, edging ritual, and
    kept check-in are **back on the day view automatically** — supersedence
    is a temporal overlay scoped to `superseded_during`, so the routine
    resumes the moment the window ends. No re-authoring, no recreate.
    ⚠ **Confusion watch:** if the rituals do **not** come back on 06-14,
    either the date window was authored wrong or supersedence is behaving
    like deletion — flag it.

---

## Exit state

- `cas-kept`: **strictly-kept** (`mode.toml` committed), `identity.toml` =
  good fox / sweet kit / Sir / he-him / soft-kinky (English keys, kink
  flavour in values, committed). `AGENTS.md` still one bridge line.
- A **nightly `claude -p` pass by Sir** landed tomorrow as
  **one-file-per-entity commits** — cage-on/cage-off, collar, edging,
  kept check-in (kink, `inverted`, `private`) plus work/meals/gym/meds —
  each authored as **Sir** and rendering with **Sir's author chip**.
- A **work-trip overlay** **supersedes** `cal-ritual` for its window: the
  rituals render **paused (greyed, hidden from the day view, present in
  manage-overlays with force-show overrides)**, **not deleted**; **meds +
  hydration survive** as `nonSuperseable`; the rituals **resume
  automatically** after the trip.

## Pass criteria

- [ ] `cas-kept` is **strictly-kept**, take-back affordance reachable (not buried).
- [ ] `identity.toml` keys English, kink flavour only in values; `AGENTS.md` = one bridge line, no embedded identity.
- [ ] Sir has a distinct `identities/` entry (`default_author = false`).
- [ ] A single `claude -p` pass authors tomorrow as **one file per entity** (no index file, no rule-edits), well-formed TOML+Markdown.
- [ ] Kink entries carry `author = "sir"`, `inverted = true`, `private = true`; meds carry `nonSuperseable = true`; **all keys English**.
- [ ] No "waiting for Dom approval" gate; edits/authoring apply immediately (review feed is informational only).
- [ ] Schedule renders kinky + mundane interleaved; kink tiles chip as **Sir** (foreign-source); author filter isolates Sir's set.
- [ ] Rituals render **done-by-default** (inverted), no tick-list, no shame counter.
- [ ] A missed ritual writes a `deviations/` file (not `exceptions/`), rule untouched, no punitive UI.
- [ ] A trip overlay **supersedes** `cal-ritual`: rituals **paused/greyed (not deleted)** in the window, **resume automatically** after.
- [ ] **Meds + hydration survive supersedence** (`nonSuperseable`) — the highest-stakes assertion.
- [ ] "Show this one anyway" writes to `overrides/` (`kind = "force-show"`), distinct from `exceptions/` and `deviations/`.

## QA notes / likely defects

- **Attribution off the committer, not `author` frontmatter (step 9).**
  Easy bug: chips resolve to the git committer or the `default_author`
  instead of the entity's `author = "sir"` field — collapsing the "kept by
  Sir" signal. Highest-value chip test.
- **Supersedence implemented as deletion/cancel (step 13).** If pausing a
  routine writes `exceptions/` or edits the rule, the routine is *lost*,
  not paused — the exact D.75 dealbreaker. Verify the rule files and
  `exceptions/` dir are untouched after a trip is added.
- **`nonSuperseable` not honoured (step 14).** Meds vanishing with the
  rituals is the most dangerous failure in the app. Verify both the
  calendar-level `nonSuperseable` flag and the per-event tag survive
  (D.76 S4 + S5).
- **`claude -p` authoring drift (steps 5–6).** Watch for: an index file
  (violates D.3), a kink-coded or locale-coded schema *key* (violates
  English-only schema), a missing `inverted` flag (rituals become a
  to-do list), or identity read from `AGENTS.md` instead of
  `identity.toml`. Each points at an under-specified `AGENTS.md`.
- **`overrides/` vs `exceptions/` vs `deviations/` confusion (steps 11,
  15).** Three sibling dirs, three semantics (D.77). UI copy must make
  "cancel" vs "didn't do it" vs "show anyway despite pause" distinct.
- **A review-feed gate creeping in under strictly-kept (step 7).** There
  must be no approval/veto state; the review feed is informational. A gate
  here is also a D.86 consent-safety violation since the keeper is an agent.
- **No second-phone assumption.** Unlike the paired-couple walk, there is
  no dom device. Any UI that *requires* a human collaborator to push (a
  "pending from your keeper's device" state) is wrong for this persona —
  the keeper is a `claude -p` run, period.

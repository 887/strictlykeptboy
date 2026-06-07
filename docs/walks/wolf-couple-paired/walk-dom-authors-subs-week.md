# Walk — Dom authors the sub's week via `claude -p`, sub completes rituals into the review feed

**Persona:** Fenrir (Dom wolf) & Rem (sub wolf), a gay couple with paired
repos. See [`personality.md`](personality.md).

**Goal:** Exercise the app's agent-native + kept-mode spine end-to-end.
Fenrir runs an **AI Dom persona** (`claude -p`) against Rem's shared repo
to lay out Rem's week (rituals + chores + their shared life), pushes; Rem's
app syncs and shows the Dom-authored events with a **foreign-author chip**;
a **common-time date night** is found across both calendars; Rem **logs
ritual completion** which **materialises into the review feed** Fenrir
reads. Verify: author chips on foreign events, common-time finder,
review-feed round-trip, strictly-kept framing.

**Preconditions:**
- Two devices (or two AVDs): Fenrir's phone + Rem's phone. Fenrir also has
  a **Linux laptop** with `claude` (Claude Code CLI) installed and an SSH
  key that can clone `rem-kept`.
- Both repos already exist, **mutually shared with write access**, both on
  each device's repo picker — i.e. the
  [`ch-paired-couple/walk-paired-repos-setup.md`](../ch-paired-couple/walk-paired-repos-setup.md)
  equivalent is done for `rem-kept` (Rem, HTTPS PAT/OAuth, **strictly-kept**,
  alignment Submissive) and `fenrir-calendar` (Fenrir, HTTPS PAT/OAuth, **free**,
  alignment Dominant).
- `rem-kept`'s `identity.toml`: praise `good wolf`, honorific `Alpha`,
  pronouns he/him, tone soft-kinky. `mode.toml` = `strictly-kept`.

**Features exercised:** AGENTS.md one-file-per-entity convention
(DM-G / D.3), `claude -p` agent authoring, per-commit author attribution +
foreign-author chip (D.88), strictly-kept review feed (D.84 / SH-N),
dom-persona pointer (D.85 / SH-O), common-time finder (RV-E / UI-M),
inverted/passive habits + ritual completion (D.54).

> Format: numbered steps, "→ Expect:", "⚠ Confusion watch:". Keep it warm
> and structural — ritual / chore / completion / review — never sexual.

---

## Part A — Fenrir points an AI Dom persona at Rem's repo (laptop)

1. On his **laptop**, Fenrir clones Rem's shared repo over SSH:
   `git clone git@github.com:rem/rem-kept.git`.
   → **Expect:** the clone contains the standard layout — `AGENTS.md`,
   `CLAUDE.md` (symlink → AGENTS.md), `.strictlykeptboy/`, `identity.toml`
   (praise `good wolf`, honorific `Alpha`), `mode.toml` (`strictly-kept`),
   `calendars/`, `todolists/` (DM-G / D.3).
   ⚠ **Confusion watch:** if `CLAUDE.md` is a real file rather than a
   symlink to `AGENTS.md`, or `identity.toml` is missing, the agent won't
   pick up Rem's register — flag it (DM-G.1 / D.83).

2. Fenrir reads `AGENTS.md` (or lets `claude` read it).
   → **Expect:** it states the rules an agent must follow — one file per
   entity, **never create an index file**, IDs are UUIDv7, file format is
   TOML frontmatter (`+++` fenced) + Markdown body, and a **single bridge
   line** pointing at `identity.toml` for praise/pronouns/honorific (D.83).
   ⚠ **Confusion watch:** if `AGENTS.md` embeds the praise term / honorific
   inline (instead of just the one bridge line to `identity.toml`), that's
   a D.83 violation — the personal preference must live in `identity.toml`,
   not `AGENTS.md`. Flag it.

3. Fenrir selects his **AI Dom persona**. On his app (phone) he opens
   **Settings → Mode → Dom persona** (DomPersonaPickerSheet) and confirms a
   **`custom-prompt`** persona is bound to the `rem-kept` link with cadence
   (e.g. `end-of-day`).
   → **Expect:** the choice is recorded device-locally in
   `.strictlykeptboy/ai-dom-pointer.toml` (NOT committed; SH-O.1 / SH-O.6),
   with `repo_fingerprint`, `persona_name = custom-prompt`,
   `persona_file_path = ~/.config/skb/dom-personas/<name>.md`, `cadence`.
   ⚠ **Confusion watch:** if the persona name lands in a **committed** file
   (anywhere in git history), that's a D.85 / SH-O.6 leak — persona names
   are kink-coded and must stay device-local. Flag hard.
   ⚠ **Confusion watch:** the six builtin personas (`stern-but-fair`,
   `playful-tease`, `kinky-affectionate`, `daddy-warmth`,
   `bratty-switch-energy`, `clinical-protocol`) should be listed; Fenrir's
   wolf-Dom register is the **`custom-prompt`** slot — confirm both the
   builtins AND a custom slot are offered (D.85).

4. Fenrir runs the AI Dom persona against the clone from his laptop:
   ```
   claude -p "Lay out good wolf's week in this repo. Morning cage ritual
   daily 07:00 (10 min). Evening edging ritual Tue + Thu 21:00. Our date
   night Friday evening (placeholder — we'll pin the time from the
   common-time finder). Gym Mon/Wed/Fri 18:00. Groceries Saturday. Use the
   praise term and honorific from identity.toml. One file per entity."
   ```
   → **Expect:** `claude` reads `AGENTS.md` + `identity.toml`, then writes
   **one file per entity** under `calendars/<cal-id>/events/<yyyy>/<mm>/`
   (or `recurrences/` for the repeating rituals/chores), each with `+++`
   TOML frontmatter (`schema_version = 1`, UUIDv7 `id`, `start`, `author`
   = Fenrir's identity) + a Markdown body. The recurring rituals/chores
   land as **recurrence rules**, not 365 one-off files.
   ⚠ **Confusion watch:** if the agent creates an **index file** (e.g.
   `events.json`, `week.md` summary) the convention is violated — AGENTS.md
   explicitly forbids it (DM-C / D.3). Flag.
   ⚠ **Confusion watch:** the kink rituals (cage, edging) must be written
   with the **same first-class structure** as the chores (gym, groceries) —
   TOML keys stay English (`title`, `start`, `rrule`); the kink flavour is
   only in the **values / body** ("Morning cage", "Evening edging"). If any
   schema **key** got renamed to a kink term, fail the English-schema rule.

5. Fenrir eyeballs the diff (`git diff` / file list), then commits + pushes:
   `git add -A && git commit -m "good wolf's week" && git push`.
   → **Expect:** the push lands on `rem-kept`'s remote. Because Fenrir
   committed, the commit's **author = Fenrir** even though the repo is
   Rem's (D.88). The kept events now exist on the remote.
   ⚠ **Confusion watch:** if the push is **rejected** because the app
   marked the repo read-only for Fenrir, the mutual-write setup is broken —
   Fenrir has collaborator write; the clone is over his own SSH key. Flag
   (this proves the share, mirrors `walk-paired-repos-setup` Part C).

## Part B — Rem syncs; foreign-author chips on the Dom-authored events

6. On **Rem's phone**, open the app (active repo `rem-kept`) and tap the
   top-bar **sync** button.
   → **Expect:** the new events pull in. Rem's Schedule (Day/Week) now shows
   **Morning cage 07:00** daily, **Evening edging 21:00** Tue + Thu, a
   **Friday date-night placeholder**, **Gym** Mon/Wed/Fri 18:00, and a
   Saturday **Groceries** chore.
   ⚠ **Confusion watch:** if a merge-conflict prompt appears for events Rem
   never touched, the one-file-per-entity invariant is being violated
   somewhere (D.3 promises conflict-free adds). Flag.

7. Inspect a Dom-authored event (e.g. **Morning cage**).
   → **Expect:** it carries a **foreign-author chip** identifying **Fenrir**
   as the author (his initials / persona avatar), **distinct from** the fact
   that the repo is Rem's. This author-vs-owner distinction is the whole
   point of a Dom-authored calendar (D.88).
   ⚠ **Confusion watch:** if the event shows **Rem** as author (or no chip
   at all), attribution is wrong — the kept events must visibly read as
   "from Alpha", not "I wrote this myself." Fail the walk if foreign events
   are indistinguishable from self-authored ones.

8. Confirm the **register** is correct in chrome/briefing.
   → **Expect:** the app addresses Rem using his `identity.toml` praise term
   (`good wolf`) and recognises the honorific `Alpha` where a briefing
   salutation appears (D.83 / D.88). Top-bar avatar = the active repo
   (`rem-kept`) identity.
   ⚠ **Confusion watch:** German/English kink jargon must not appear as a
   **schema key** anywhere; `good wolf` / `Alpha` are **values**. If a
   settings label exposes a raw schema key, flag.

9. Rem adds a personal event himself — "Coffee with a friend, Sun 11:00" —
   via the FAB into `rem-kept`.
   → **Expect:** this event's author chip shows **Rem**. Two authors, one
   repo, both chips correct (D.88). Sync → it appears on Fenrir's device too.
   ⚠ **Confusion watch:** verify the self-authored event is visually
   **NOT** wearing the foreign-author chip — only Fenrir's events are
   foreign to Rem.

## Part C — Common-time finder → pin the Friday date night across both repos

> The common-time finder lives on the **Together tab** (UI-M.1) and needs
> **≥ 2 repos** both readable by the querying device — true here (mutual
> write). It builds a busy set across selected repos × calendars × range,
> subtracts busy, returns ranked free slots (RV-E).

10. On Rem's phone, open the **Together tab → Common-time finder**.
    → **Expect:** the form (NOT the "add at least two repos" empty state —
    both repos are present).
    ⚠ **Confusion watch:** if the Together tab is hidden, the device is in
    **simplified mode** (SH-E.2 hides Together). Rem has his OWN repo
    `rem-kept`, so he should be in **full** mode — if he's in simplified,
    that's wrong here. Flag.

11. **Repos:** multi-select **`rem-kept`** + **`fenrir-calendar`**.
    **Calendars:** include Rem's routine (so the rituals count as busy) and
    Fenrir's work calendar. **Range:** this Fri. **Duration:** 2h.
    **Time:** 18:00–23:00. Optional **ideal** ~19:30. Tap **Find slots**.
    → **Expect:** ranked free 2h Friday-evening slots where **both repos are
    free** (e.g. "Fri · 19:30–21:30 — rem-kept + fenrir-calendar both free").
    ⚠ **Confusion watch:** the rituals Fenrir's agent wrote (cage 07:00,
    edging Tue/Thu 21:00) are **passive/inverted habits** but still
    **scheduled events** — they must count as **busy** in the finder. If
    passive habits are treated as free, flag (RV-E).

12. Tap **[Create event →]** on the chosen slot → pre-filled quick-add.
    Target a shared "Date nights" calendar (sensible: in `fenrir-calendar`,
    free mode, mutual write), title **"Date night"**. Optionally
    `private = true` (D.57). Save.
    → **Expect:** the date-night event created; **author chip = whoever
    created it** (Rem here). Sync → it appears on **both** devices, and the
    Friday **placeholder** from Part A can be reconciled/replaced with the
    pinned time.
    ⚠ **Confusion watch:** if Rem targeted `fenrir-calendar`, confirm his
    **write access** let the commit land (no read-only block) — proves the
    mutual-write link (mirrors date-night walk step 9).

## Part D — Rem completes a ritual → it materialises into Fenrir's review feed

> strictly-kept (D.84 / SH-N): Rem's edits apply **immediately** (no veto,
> no approval gate). Every commit to `rem-kept` materialises
> `reviews/<commit-sha>/reviewable_change.md` via an in-app post-commit
> callback; Fenrir consumes it via the cross-repo "Reviews" surface.

13. Tomorrow morning, Rem **completes the Morning cage ritual**. Because
    these are **inverted/passive habits** (D.54), default = `completed-by-
    schedule` — so "completion" here means Rem confirms it on schedule (and
    a **deviation** would be the explicit file if he'd missed it). For an
    explicit logged completion, Rem long-presses the event → **Mark
    complete** (or ticks the ritual's task-form circle).
    → **Expect:** the completion is written as a single entity file +
    committed to `rem-kept`. No event is destroyed; the recurrence is
    intact.
    ⚠ **Confusion watch:** completing a ritual must NOT silently rewrite the
    recurrence rule to drop a date — completion/deviation is its own file
    (D.54 / D.3). If the rule file changes, flag.

14. **Review-feed materialisation.** On that commit, confirm the app wrote
    `reviews/<commit-sha>/reviewable_change.md` in `rem-kept` (strictly-kept
    only).
    → **Expect:** the review entry exists, auto-summarised using Rem's
    praise term (`good wolf`) per the register-aware summary (SH-N.2 /
    DM-Z), with an empty `responses/` subdir and a collapsed Markdown diff
    of the change.
    ⚠ **Confusion watch:** if `rem-kept` were `free` mode, **no** review
    entry should materialise. Materialisation tracks the **repo's mode**,
    not the event's purpose (mirrors date-night walk step 17). Confirm the
    feed only appears because the repo is strictly-kept.
    ⚠ **Confusion watch:** if Rem's completion event carries `private =
    true` (D.57 / K-2), it must be **suppressed** from the review feed
    (SH-N.8) — verify a private entry never materialises a review.

15. **Round-trip to Fenrir.** On **Fenrir's phone**, sync and open the
    **Reviews** surface (cross-repo feedback, newest-first, unread pill).
    → **Expect:** Rem's ritual-completion commit appears as a `review` entry
    Fenrir can read — the round-trip is closed (SH-N.3). Fenrir taps it.
    ⚠ **Confusion watch:** there must be **no approval gate / no veto** — Rem's
    completion already applied; Fenrir is *reviewing*, not *authorising*.
    If the UI presents an "approve/reject" control over Rem's change, that
    violates D.84 / D.86. Flag hard.

16. **Fenrir responds.** Fenrir reacts to the review with `good-boy` (empty
    text).
    → **Expect:** the response is written to Fenrir's **OWN** repo at
    `reviews/<commit-sha>/responses/<fenrir-fingerprint>-<ts>.md` (write-
    back-target = dom's repo; SH-N.4), and renders on Rem's side as the
    cute-coded **LGTM** (empty text + `good-boy` reaction; SH-N.5 / D.84).
    ⚠ **Confusion watch:** the response must NOT be written into Rem's
    `rem-kept` repo — it lands in Fenrir's repo and surfaces cross-repo. If
    Fenrir's app pushes to `rem-kept`, the write-back-target is wrong.

## Part E — Consent / control invariant (must always hold)

17. Rem opens **Settings** from the most-kept UI state and confirms the
    **"transition my mode"** affordance is reachable (not buried; D.86).
    → **Expect:** Rem can begin a `strictly-kept → free` transition; it
    requires a **24h cooling-off confirmation by Rem** — a **boy-side**
    confirmation, NOT a dom-gate Fenrir can veto or prolong. Fenrir's app
    would get only a **neutral** notification on flip (no veto UI; SH-N.7).
    ⚠ **Confusion watch:** if anywhere Fenrir can block/delay Rem's mode
    transition, or Rem can't reach the affordance, that is a D.86 violation —
    the hardest invariant in the app. Fail the walk.

---

## Pass criteria

- [ ] `claude -p` (Fenrir's AI Dom persona) authored Rem's week into
      `rem-kept` as **one file per entity** (rituals as recurrences, no
      index file), reading `AGENTS.md` + `identity.toml`.
- [ ] Dom-persona selection recorded device-local in
      `.strictlykeptboy/ai-dom-pointer.toml`, **never committed**.
- [ ] Dom-authored events show a **foreign-author chip = Fenrir**; Rem's
      own events show **Rem**; the two are visually distinct.
- [ ] Schema **keys** stayed English; `good wolf` / `Alpha` appear only as
      **values** / register, never as keys.
- [ ] Common-time finder swept **both** repos (passive habits counted as
      busy) and produced a ranked Friday evening; date night booked across
      the mutual-write link and visible on both devices.
- [ ] Rem's ritual completion is its own file (recurrence intact, no
      silent drop) and materialised a `reviews/<sha>/reviewable_change.md`
      **because the repo is strictly-kept**.
- [ ] The review round-tripped to Fenrir with **no approval gate / no
      veto**; his `good-boy` response landed in **his** repo and rendered as
      LGTM on Rem's side.
- [ ] Rem's **"transition my mode"** affordance is reachable and
      **non-blockable** by Fenrir (D.86).

## QA notes / likely defects

- **Author chip is the load-bearing signal of this walk.** Watch step 7
  hardest: a Dom-authored event that reads as self-authored (missing /
  wrong chip) erases the entire dynamic. This is the single most likely
  high-value defect.
- Watch step 3/step 4 for any **commit** of the persona name or pointer
  file — `ai-dom-pointer.toml` and `~/.config/skb/dom-personas/*` must be
  device-local + gitignored (D.85 / SH-O.6). A kink-coded persona name in
  git history is a privacy leak.
- Watch step 4 for the agent emitting an **index file** or inlining
  recurring rituals as hundreds of one-off files — both break the
  one-file-per-entity + recurrence conventions (D.3 / DM-C).
- Watch step 4/step 8 for **schema-key localisation**: kink terms belong in
  values/body only; `title`/`start`/`rrule` stay English (English-only
  schema rule).
- Watch step 11 for passive/inverted habits being treated as **free** in
  the common-time finder — they are scheduled events and must read as busy
  (RV-E).
- Watch step 14/step 15 for **mode confusion**: review feed materialises on
  the **repo's** mode (strictly-kept), not the event's purpose; a `free`
  repo must produce **no** review entry; `private = true` entries must be
  **suppressed** (SH-N.8).
- Watch step 15/step 16 for any **approve/reject/veto** control over Rem's
  change — strictly-kept is a *review* surface, not an *approval gate*
  (D.84). Any gating UI is a hard defect.
- Watch step 17 for the consent invariant: the "transition my mode"
  affordance must be reachable from the most-kept state and **never**
  blockable by the Dom (D.86). This is non-negotiable.

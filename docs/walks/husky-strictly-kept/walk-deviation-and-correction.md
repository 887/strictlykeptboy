# Walk — kept-by-AI: identity-copy notification → a skipped ritual → deviation → review feed → the AI Dom's corrective re-authoring

**Persona:** Koda, a husky sub in strictly-kept (kept-by-AI) mode
(see [`personality.md`](personality.md)).
**Goal:** Verify the full kept-by-AI loop end to end — (A) strictly-kept +
`identity.toml` copy reaches a notification / daily briefing in **Sir's
voice**; (B) Koda **skips** an evening ritual and a single **deviation**
file (`kind = skipped`) is written with the recurring rule untouched;
(C) that deviation materialises into the **review feed** the AI Dom reads;
(D) the **AI Dom re-authors** the schedule by running `claude -p` against
the repo — a make-up task tomorrow + a tightened cage window, in Sir's
voice; (E) the corrective entries **sync** and Koda's **tomorrow** shows the
make-up task + firmer schedule with identity copy intact.
**Preconditions:** `koda-kept` repo exists, **strictly-kept** mode, kept
**by an AI Dom** (`mode.toml`: `dom_persona = "stern-but-fair"`,
`dom_cadence = "end-of-day"`, `kept_since` set, `write_back_target` =
the AI Dom's checkout). `identity.toml`: praise `good boy`, honorific
`Sir`, tone register **firm**, pronouns he/him, emoji **low**, alignment
**Submissive**. An **Evening rituals** routine calendar already authored
as passive / inverted habits, including a **21:00 cage check** and a
**21:30 edging** ritual on a daily RRULE. Device online; the AI Dom's
checkout is on the laptop with `claude -p` available.
**Features exercised:** strictly-kept mode contract (D.84), `identity.toml`
vs `AGENTS.md` split (D.83), identity-driven briefing / notification copy
(D.81 + D.120), inverted-habit default-complete (D.54), deviation files
(D.54 / `DeviationValidator`), `post_event_checkin` quick-action (D.82),
review feed (`ReviewFeedWriter` / `reviews/<sha>/reviewable_change.md`),
AI-dom-persona corrective loop (D.85), kept-by-AI mode derivation
(`PetModeDerivation`).

---

## Part A — Identity copy reaches a notification / briefing in Sir's voice

> Contract: `identity.toml` (praise `good boy`, honorific `Sir`, tone
> `firm`) drives chrome + briefing + notification copy (D.81 / D.120). The
> bridge line in `AGENTS.md` points at `identity.toml` but never embeds the
> copy (D.83). The point of Part A is to prove the copy *reaches a live
> notification surface*, not just that it's stored.

1. Koda activates `koda-kept` (top-bar repo switcher) and opens the
   **Schedule → Day** view.
   → **Expect:** the top-bar avatar + identity context is `koda-kept`;
   chrome salutation / now-card addresses him with the praise term
   **`good boy`** (tone **firm**, low emoji).
   ⚠ **Confusion watch:** if chrome shows a generic salutation (no praise
   term), `identity.toml` isn't wired into chrome — flag it; the rest of
   the walk depends on this copy path.

2. Confirm the mode + keeper derivation. Settings → Mode (per-repo).
   → **Expect:** mode **strictly-kept**; the keeper is the **AI Dom**
   (`dom_persona` set, `dom_cadence = end-of-day`) — `PetModeDerivation`
   resolves this as **kept-by-AI** (`SelfPet` / `KeptBy.Ai`), not
   kept-by-a-human. The always-visible **"transition my mode"** affordance
   is reachable from here (D.86).
   ⚠ **Confusion watch:** if "transition my mode" is buried or absent, that
   is the consent-safety dealbreaker — fail the walk.

3. Verify the `AGENTS.md` / `identity.toml` split (D.83). Open the repo's
   `AGENTS.md`.
   → **Expect:** exactly **one bridge line** pointing at `identity.toml`
   (e.g. *"See `identity.toml` at repo root for the user's praise term,
   pronouns, and tone register …"*). It must **NOT** contain `good boy`,
   `Sir`, or the pronouns.
   ⚠ **Confusion watch:** any praise term / honorific copied into
   `AGENTS.md` is a D.83 violation — flag immediately. Personal preference
   lives only in `identity.toml`.

4. Force the **evening briefing** (D.81 `cal-briefings` → `evening-briefing`,
   default 21:00). Either wait for 21:00 or trigger the briefing for today
   from Settings → Notifications → "Show briefings" (or fast-forward device
   clock to 21:00 on the AVD).
   → **Expect:** a notification / briefing whose body is auto-generated from
   the upcoming-window query, addressing Koda in **Sir's voice** with the
   praise term — e.g. **"Good boy — Sir's 21:00 cage check is due."** The
   21:00 cage check + 21:30 edging both appear as upcoming items.
   ⚠ **Confusion watch:** if the briefing copy is plain ("Cage check at
   21:00") with no praise term / honorific, the identity copy did not reach
   the notification surface — this is the **central Part A failure**; flag
   it hard. Briefing bodies are auto-generated and non-user-editable (D.81);
   an edit attempt should offer "regenerate?".

5. (Schema-leak check.) Inspect `identity.toml` on disk.
   → **Expect:** English keys, kink-flavoured values: `[praise] term =
   "good boy"`, `[honorific_for_dom] term = "Sir"`, `[tone] register =
   "firm"`, `emoji_density = "low"`, pronouns he/him.
   ⚠ **Confusion watch:** any localised or kink-coded **key** (vs value) is
   the English-only-schema violation — flag it. Flavour belongs in values
   and prose only.

## Part B — Koda skips an evening ritual → a deviation file (kind = skipped)

> Inverted-habit model (D.54): default state for any past-or-current
> scheduled ritual = `completed-by-schedule`. A miss is the explicit
> action — a single `deviations/<calendar-id>/<entity-id>/<yyyy-mm-dd>.md`
> with `kind ∈ {skipped, partial, late}` (`DeviationValidator`). The
> recurring rule is **never** edited to drop a date, and **no
> `exceptions/`** file is created (a miss is a deviation, not a cancel).

6. Before any miss, look at today's **Evening rituals** on the Schedule.
   → **Expect:** the past/current 21:00 cage check + 21:30 edging render as
   **completed-by-schedule by default** — no checkboxes to tick, no "0/2
   done" nag, no streak-shame copy (D.54 / D.63).
   ⚠ **Confusion watch:** if the routine reads as an empty to-do list to be
   ticked, the inverted-habit default isn't applied — flag it.

7. Koda **skips** the **21:30 edging** ritual tonight. Two paths, verify the
   one your build surfaces:
   - **(a) post-event check-in (D.82):** if `post_event_checkin` is opted in
     on that ritual, a notification fires after 21:30 with quick-actions
     **Yes / No / Partial / Remind me again**. Koda taps **No**.
   - **(b) manual:** open the 21:30 instance on the Schedule → choose the
     "I didn't do this / deviation" affordance (NOT a generic delete) →
     pick **skipped**.
   → **Expect (either path):** a single file
   `deviations/<evening-cal>/<edging-uuid>/<today>.md` with
   `kind = "skipped"` and `at` set to now (`DeviationValidator` requires
   `at` not in the future, and a real scheduled occurrence on that date).
   ⚠ **Confusion watch:** the **No** quick-action must write a `skipped`
   deviation (not silently do nothing); **Partial** would instead write
   `kind = "partial"` with a sub-beat picker (D.82). Confusing skip vs
   partial is a likely user/QA error — the copy should distinguish them.

8. Verify the deviation is a *report*, not a destructive edit.
   → **Expect:**
   - The recurring **rule file is untouched** (no silent date drop).
   - **No `exceptions/` file** was created (a miss is a deviation, not a
     cancel — these are distinct dirs).
   - **Only tonight's 21:30** shows the deviation; **tomorrow's 21:30 is
     intact** and still defaults to completed-by-schedule. The series
     survives.
   ⚠ **Confusion watch:** if the rule lost a date, or an `exceptions/` file
   appeared, the semantics are wrong — flag hard. UI copy should make the
   deviation-vs-cancel distinction obvious.

9. **No punitive UI on the miss.**
   → **Expect:** no "streak broken" modal, no shame copy, no automatic
   escalation. The deviation renders calmly (D.54 / D.85).
   ⚠ **Confusion watch:** any punitive surface here is a hard fail.

## Part C — The deviation materialises into the review feed

> Strictly-kept (D.84): every commit materialises
> `reviews/<commit-sha>/reviewable_change.md` in Koda's repo, consumed by
> the keeper. `ReviewFeedWriter` classifies the changed paths;
> `/deviations/` paths summarise as **"logged N deviation(s)"**
> (`PathFamily.Deviation`).

10. After the skip commit lands, inspect the repo.
    → **Expect:** a `reviews/<commit-sha>/reviewable_change.md` exists whose
    `auto_summary` references the deviation — e.g. **"logged 1 deviation"** —
    and whose register-aware copy uses Koda's praise term where the summary
    addresses him (the writer reads `identity.toml`).
    ⚠ **Confusion watch:** if no review entry materialised for the skip
    commit, the strictly-kept review hook didn't fire — flag it (Part D's
    corrective loop depends on the AI Dom seeing this).

11. Open the in-app **review feed** surface (UI-SS / `ReviewFeedReader`,
    boy-side view of his own feed).
    → **Expect:** today's 21:30 edging shows the **skipped** deviation entry;
    no approval gate, no veto, no punitive banner — strictly-kept never
    blocks (D.84).
    ⚠ **Confusion watch:** any "waiting for Dom approval" state is a D.84
    violation — edits + deviations apply immediately; the review feed is the
    *only* delta.

12. (Privacy interaction, optional.) If Koda marked any ritual
    `private = true`, confirm it is **skipped from the review feed** entirely
    (SH-N.8 / D.57) — the AI Dom never sees a private entity.

## Part D — The AI Dom re-authors the schedule via `claude -p`

> Kept-by-AI (D.85): the AI Dom is an app-private persona prompt
> (`~/.config/skb/dom-personas/stern-but-fair.md`), NOT in the calendar
> repo. It runs **outside the app**, against the repo checkout, by invoking
> `claude -p`. It reads `AGENTS.md` (one-file-per-entity convention) + the
> bridge line → `identity.toml` (Sir's voice), then authors corrective
> entries (via `skb` or direct file writes), commits, and pushes. It never
> withholds responses punitively and never proposes a mode change Koda
> didn't ask for.

13. On the laptop checkout of `koda-kept`, the AI Dom runs (end-of-day
    cadence — `dom_cadence = "end-of-day"`):
    ```
    claude -p "Koda skipped last night's edging ritual. Read the review
    feed and identity.toml, then: add a make-up edging session tomorrow
    evening, and tighten tomorrow's cage-check window. Author the entries
    in Sir's voice per identity.toml. Commit and push."
    ```
    → **Expect:** `claude -p` reads `AGENTS.md` → the `identity.toml` bridge
    line → `identity.toml` (praise `good boy`, honorific `Sir`, tone
    `firm`), and reads the review-feed deviation from Part C.
    ⚠ **Confusion watch:** if `AGENTS.md` doesn't lead the agent to the
    `skb`/file convention + the `identity.toml` bridge, the AI Dom can't
    author correctly — that traces back to a Part A/Step 3 defect.

14. The AI Dom **authors the make-up task**.
    → **Expect:** a new task / event file appears under tomorrow's date
    (e.g. `todolists/<list>/tasks/<yyyy>/<mm>/<task-id>.md` or an event in
    the Evening rituals calendar) — **one file per entity**, UUIDv7 id,
    `author` pointing at the **AI Dom's** identity, body in **Sir's voice**
    (firm tone, praise term where natural). Title e.g. *"Make-up edging
    session — Sir expects this, good boy."*
    ⚠ **Confusion watch:** the make-up must be a **new entity file**, not an
    edit that silently rewrites the skipped instance or the recurring rule.

15. The AI Dom **tightens tomorrow's cage-check window**. Because the rule
    file must not be silently rewritten for one date, this is a
    **per-occurrence override** (`exceptions/<rule-id>/<tomorrow>.md`,
    `kind = "override"`) narrowing tomorrow's cage-check window — NOT an edit
    to the daily rule.
    → **Expect:** an `exceptions/<cage-rule>/<tomorrow>.md` override file
    (firmer/earlier window), the **daily rule untouched**, only **tomorrow's**
    cage check affected.
    ⚠ **Confusion watch:** if the AI Dom edited the recurrence rule directly
    (changing every future cage check), that's the D.6 "never silently
    rewrite the rule" violation — flag it. The correction is for *tomorrow*,
    per the prompt.

16. The AI Dom **commits + pushes**.
    → **Expect:** corrective commits land on the remote; commit `author` =
    the AI Dom's identity (visible later as author chips). Because the AI Dom
    writes to `koda-kept` itself, these commits also each materialise their
    own `reviews/<sha>/reviewable_change.md` — the loop is symmetric.
    ⚠ **Confusion watch:** the AI Dom must **not** withhold or go silent as a
    "punishment" (D.85); a corrective authoring pass is the expected, only
    response.

## Part E — The correction syncs; Koda's tomorrow shows it in Sir's voice

17. Back on the phone, Koda syncs `koda-kept` (top-bar two-arrow icon, or
    auto-sync interval).
    → **Expect:** fetch + rebase pulls the AI Dom's corrective commits; a
    result toast (e.g. "synced 1 repo"). No conflict (one-file-per-entity).

18. Koda opens **tomorrow** on the Schedule + the combined **Today/Tasks**
    view for tomorrow.
    → **Expect:**
    - The **make-up edging session** appears as a new entity tomorrow
      evening, authored by the **AI Dom** (author chip), body rendered
      M3E-themed Markdown in **Sir's firm voice** with the praise term.
    - Tomorrow's **cage check** shows the **tightened** window (the
      `override`), while later days keep the original window — the rule
      survived.
    ⚠ **Confusion watch:** if the make-up didn't materialise or the cage
    window change leaked into every future day, the correction loop is
    broken — flag which.

19. Force tomorrow's **evening briefing** (or fast-forward to 21:00 tomorrow
    on the AVD).
    → **Expect:** the briefing now lists the **make-up session** and the
    **tightened cage check**, still addressing Koda in Sir's voice —
    e.g. **"Good boy — Sir added a make-up session at 21:30, and tonight's
    cage check is at 20:45."** Identity copy + the AI Dom's correction are
    both present in one surface.
    ⚠ **Confusion watch:** if the new entries appear but the praise term /
    honorific dropped out of the briefing for AI-authored entries, the
    identity copy isn't applied uniformly — flag it.

20. Confirm the original series is intact and the dynamic stayed consensual.
    → **Expect:** the daily Evening rituals rule is unchanged; only
    *tomorrow* carries the override; yesterday carries the lone `skipped`
    deviation; mode is still **strictly-kept** (the AI Dom did **not** flip
    Koda's mode), and "transition my mode" is still reachable (D.85 / D.86).
    ⚠ **Confusion watch:** any AI-Dom-initiated mode change is a hard fail.

---

## Exit state

- `koda-kept`: **strictly-kept**, kept-by-AI (`dom_persona` set);
  `identity.toml` = good boy / Sir / firm / he-him / low emoji (English
  keys, kink flavour in values, committed). `AGENTS.md` still one bridge
  line.
- A notification / evening briefing addressed Koda in **Sir's voice** using
  the praise term — identity copy reached a live notification surface.
- One `deviations/.../<today>.md` (`kind = skipped`) records the missed
  21:30 edging; the recurring rule is untouched; no `exceptions/` cancel;
  no punitive UI.
- That deviation materialised into `reviews/<sha>/reviewable_change.md`
  ("logged 1 deviation") and into the in-app review feed.
- The **AI Dom**, via `claude -p`, authored a **make-up session** (new
  entity, tomorrow) + a **cage-window override** (`exceptions/.../<tomorrow>`,
  not a rule rewrite), in **Sir's voice**, committed + pushed.
- Koda's **tomorrow** + tomorrow's briefing show the make-up task and the
  firmer cage window with identity copy intact; the series survives; mode
  unchanged.

## Pass criteria

- [ ] Mode strictly-kept + kept-by-AI derivation correct; "transition my
      mode" reachable (steps 2).
- [ ] `AGENTS.md` is one bridge line; no praise/honorific embedded (step 3).
- [ ] A notification / briefing reached Koda in **Sir's voice** with the
      praise term — identity copy is wired, not just stored (steps 1, 4).
- [ ] `identity.toml` keys English, values kink-flavoured (step 5).
- [ ] Routine reads done-by-default; no ticking, no streak-shame (step 6).
- [ ] Skip writes one `deviations/.../<date>.md` `kind = skipped`; rule
      untouched; no `exceptions/` cancel; only today affected (steps 7–8).
- [ ] No punitive UI on the miss (step 9).
- [ ] Deviation materialised into `reviews/<sha>/reviewable_change.md`
      ("logged 1 deviation") + the in-app review feed; no approval gate
      (steps 10–11).
- [ ] AI Dom via `claude -p` authored a make-up entity (new file, Sir's
      voice) + a per-occurrence cage-window override (NOT a rule rewrite),
      committed + pushed (steps 13–16).
- [ ] Correction synced; tomorrow shows the make-up + tightened window with
      identity copy; series intact; mode unchanged (steps 17–20).

## QA notes / likely defects

- **Identity copy not reaching notifications/briefings (steps 1, 4, 19)** is
  the highest-value defect to hunt: chrome may read `identity.toml` while
  the `cal-briefings` auto-generator and the Now/Next notification provider
  use a plain template. Verify the praise term + honorific in an actual
  fired notification, not just the now-card.
- **Skip vs partial vs cancel confusion (steps 7–8):** the `No`
  quick-action must write `skipped`; `Partial` writes `partial`; neither may
  touch `exceptions/` or the rule. Watch that the UI copy distinguishes "I
  didn't do this" (deviation) from "cancel this occurrence" (exception).
- **AI Dom editing the recurrence rule instead of writing an override
  (step 15)** is the subtle correctness trap — a tightened cage window for
  *tomorrow* must be an `exceptions/<rule>/<tomorrow>.md` override, not a
  rule rewrite that changes every future day.
- **AGENTS.md absorbing identity copy (step 3)** both violates D.83 and
  starves the AI Dom of the bridge it needs to write in Sir's voice — one
  defect, two symptoms.
- **AI Dom going silent / flipping mode (steps 16, 20):** D.85 forbids
  punitive withholding and unsolicited mode changes; either is a hard fail.
- **Review hook not firing on a deviation commit (step 10):** confirm
  `ReviewFeedWriter` runs for deviation-only commits, not just identity/mode
  edits — `PathFamily.Deviation` classification must be exercised.
- The AI Dom runs **outside the app** (`claude -p` on the checkout); the app
  only has to (a) emit the deviation + review feed it reads and (b) render
  the corrective entries after sync. Keep the app-side vs agent-side
  responsibilities clear when triaging a failure.

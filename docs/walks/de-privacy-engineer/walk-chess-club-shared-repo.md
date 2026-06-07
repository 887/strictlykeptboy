# Walk — chess club shared repo (Tobias, de-DE)

> Add the shared **chess club / Schachverein** repo `schachverein/termine`
> (other committee members have write access) → set a **per-repo
> identity** for author attribution → add a **recurring club night**
> (Vereinsabend) → see **author chips** from other members on their
> events → resolve overlapping commits via the **overlay / sync**.
>
> Persona: see `personality.md`. He already onboarded `leben`
> (see `walk-forgejo-ssh-onboarding.md`); the device already has an
> ed25519 key flow proven and his Forgejo host key pinned.
>
> Feature grounding: add-repo = `ui-spec.md` UI-K.3; per-repo identity =
> UI-K.4 Identity section + `identity.toml` (CLAUDE.md repo convention);
> author chips = `shared-schedules.md` (author chip = avatar, rendered
> with the **receiving repo's local label**) + `resolver.md` (`author:
> PersonId?` on resolved instances); recurrence = RFC5545 RRULE in a
> rule file, exceptions/overrides as files; overlay + priority =
> `resolver.md` RV-A/RV-C + effective-priority RV-M.
>
> Schema-identifier rule: the repo is a *chess club* in prose, but all
> calendar/file identifiers stay **English**. German appears only in
> display names, event titles, and de-DE UI strings.

## Pre-conditions

- `leben` already configured and syncing (prior walk).
- Forgejo host key already pinned (`forgejo.reichenbach.example`).
- Tobias has **write** access to `schachverein/termine`; two other
  committee members ("M. Hoffmann", "S. Bauer") also have write access
  and have already committed some events.
- The shared repo already contains an `identities/` folder with the
  other members' identities and an English-id club calendar
  (display name "Vereinstermine").

## Steps

1. He opens the **top-bar repo switcher** → taps **+ Add repo**
   (UI-K.4 entry) → expects the 6-step Add-repo flow (UI-K.3).

2. **Provider**: taps **Forgejo (gitea-compatible)** → advances.

3. **Repo**: **"Use existing repo"**, pastes
   `git@forgejo.reichenbach.example:schachverein/termine.git`
   → expects it recognized as SSH on the **same host he already
   pinned**.
   ⚠️ QA: since the host `forgejo.reichenbach.example` is already in
   `known_hosts.json`, there must be **no second TOFU prompt** — the
   existing pin should satisfy verification silently.

4. **Auth**: he picks **SSH** → expects the option to **reuse the
   existing on-device key** OR generate a new one.
   - Keys are stored per `<repo-id>` (`sha256(remoteUrl).take(12)`), so
     a *different* repo URL = a *different* key slot by default.
     ⚠️ QA: clarify the UX here — does the app generate a **fresh
     ed25519 key for this repo** (separate `ssh.priv.<repo-id>`), or
     offer to reuse `leben`'s key? Per SE-C the keying is per-repo, so
     the expected default is a **new key for `termine`**. Confirm the
     wording doesn't mislead him into thinking one key covers both.

5. He generates a **new ed25519 key for `termine`**, **Copy**s the
   public key, adds it to his Forgejo account on his laptop (his
   Forgejo account already has the `leben` key; this just adds a second),
   then taps **"I added it to my account"** → advances.

6. **Identity step** (Step 4): the repo *has* an `identities/` folder,
   so he can pick an existing identity or create new. He **creates a new
   identity for this repo**: display name **"Tobias R. (Vorstand)"**,
   his email → expects it written to his identity entry and selected as
   the active identity for `termine`.
   - Rationale: in the club repo he wants committee posts
     distinguishable; "Vorstand" (committee) is **display text**, not a
     schema field — the identifier stays a normal PersonId.
   ⚠️ QA: confirm the per-repo identity is independent — his `leben`
   identity ("Tobias") must be unchanged; the switcher must show
   different author attribution per repo.

7. **Defaults** (Step 5): default calendar = the club's
   "Vereinstermine" calendar; auto-sync = 15m → saved.

8. **Done** (Step 6) → first sync clones `termine` → expects the repo in
   the switcher with a green dot, and a **read/write** repo (no
   read-only lock chip, since he has write access).
   ⚠️ QA: contrast with a read-only member — UI-K.3 shows a **read-only
   lock chip** for pull-only repos. Tobias (write) must NOT show that
   chip; verify the access level is detected correctly.

9. He opens the **Schedule** for `termine` → expects to see existing
   events committed by the other members, each carrying an **author
   chip** (the other member's avatar/label) per `shared-schedules.md`.
   ⚠️ QA: author chips must use the **receiving repo's local label** for
   each author (resolved from `identities/`), not a raw email or a bare
   PersonId hash.

10. He decides to add the weekly **club night**. He creates a new event
    on the "Vereinstermine" calendar:
    - Title (German display text): **"Vereinsabend"**
    - Body (Markdown): a short note, e.g. blitz + analysis
    - Recurrence: **weekly on Wednesdays, 19:00–22:00** → expects the
      recurrence builder (UI-I.8) to author an **RFC5545 RRULE**
      (`FREQ=WEEKLY;BYDAY=WE`) into a **recurrence rule file**, not a
      pile of materialized events.
    ⚠️ QA: confirm a single rule file is written (no per-week event
    spam); the schema keys are English (`rrule`, `dtstart`, etc.) even
    though the title is German.

11. He saves → expects a **commit** authored as **"Tobias R.
    (Vorstand)"** and an outgoing **push** over SSH on next sync, with
    the new event appearing under **his** author chip.

12. Meanwhile committee member **S. Bauer** (write access, separately)
    pushes an event to the same calendar — a one-off **"Mannschaftskampf
    Auswärts"** (away team match) on an upcoming Saturday. Tobias taps
    the **manual sync button** → expects a **pull** that brings in
    Bauer's event.
    ⚠️ QA — conflict-free-by-design check: per CLAUDE.md, **one file per
    entity, no index files**, so Tobias's `Vereinsabend` rule file and
    Bauer's match-event file are **different files** → the merge must be
    **conflict-free** (no Git merge markers, no "resolve conflict"
    dialog). Verify the sync completes clean.

13. → expects both events now visible on the `termine` Schedule:
    Tobias's Wednesday Vereinsabend recurrence **and** Bauer's Saturday
    match, each with its **own author chip** (Tobias vs Bauer).
    ⚠️ QA: two different authors on the same calendar must render two
    distinct chips; do not collapse to "last committer".

14. A scheduling overlap arises: the committee wants the Vereinsabend
    **moved** one week for a holiday. Rather than edit the rule (which
    would silently drop the date), Tobias **cancels that single
    instance** → expects an **exception file**
    `exceptions/<rule-id>/<yyyy-mm-dd>.md` with `kind = "cancel"`
    written (per CLAUDE.md convention), leaving the RRULE intact.
    ⚠️ QA: confirm a *cancel exception file* is created — the rule file
    must NOT be mutated to remove the date. He will `git log` this on
    his laptop to check.

15. He then adds an **override** for the moved date (same week, shifted
    day) → expects an `overrides/<event-id>.md` or an `override`
    exception (field-level replacement, resolver RV-B.3) — NOT a second
    competing recurrence.

16. Now he wants to see **club events layered over his personal life**.
    He goes to **Settings → Repos → "Show all repos in unified view"**
    (K.2 master toggle) → expects the repo switcher to show **"All
    repos"** with a stacked-avatar visual, and the Schedule to
    **aggregate `leben` + `termine`**.

17. → expects the **overlay resolver** (RV-A/RV-C) to render his
    personal `leben` events and the club `termine` events together,
    with **priority tiebreak** deciding primary vs secondary in any
    overlapping slot, and **author chips** distinguishing his own posts
    from committee posts.
    ⚠️ QA: in the unified view, an event Tobias authored in `termine`
    and an event Bauer authored must both show correct chips; his
    `leben` events (single author) should also be unambiguous.

18. If the Wednesday Vereinsabend collides with a personal `leben`
    event, he resolves the *display* priority via **per-calendar
    priority** — e.g. boosting the club calendar — using **Settings →
    Repos → tap source repo → tap calendar → "Boost / lower priority"**
    (writes a `priority-override.toml` keyed by calendar-id, RV-M /
    `shared-schedules.md`).
    → expects the club night to render as **primary** in the overlap
    after the boost, with the personal event demoted to secondary (not
    deleted).
    ⚠️ QA: the boost must write a **calendar-id-keyed override file**
    (different shape from entity exceptions) and the effective priority
    must recompute (authored + repo modifier + pin, clamped 1..1000,
    RV-M). Verify the overlap resolves the intended way and nothing is
    dropped.

## Success criteria

- Shared `termine` repo added over SSH, **no second TOFU prompt**
  (host already pinned), write access detected (no read-only chip).
- Per-repo identity "Tobias R. (Vorstand)" set independently of his
  `leben` identity.
- Vereinsabend authored as a **single RRULE rule file**, English schema
  keys, German title.
- Other members' events show **author chips** with the receiving repo's
  local labels.
- A concurrent push from another committee member merges
  **conflict-free** (one-file-per-entity), both events visible with
  distinct chips.
- Single-instance change uses a **cancel exception file** + override
  file, never a mutated rule.
- Unified all-repos overlay renders `leben` + `termine` together with
  correct priority resolution and per-author chips.

## Confusion points collected for QA

- Step 3: already-pinned host → no duplicate TOFU prompt.
- Step 4: per-repo key keying — does it reuse or generate? wording must
  be unambiguous.
- Step 6: per-repo identity independence from `leben`.
- Step 8: write vs read-only chip detection.
- Step 9/13: author chips use local labels; two authors = two chips.
- Step 12: concurrent push merges conflict-free (no merge markers).
- Step 14: single-instance cancel = exception file, not rule mutation.
- Step 18: priority boost writes calendar-id-keyed override; effective
  priority recomputes; nothing dropped.

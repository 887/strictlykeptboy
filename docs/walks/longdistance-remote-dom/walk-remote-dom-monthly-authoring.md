# Walk — long-distance AI Dom authors a month via `claude -p` over the remote; phone syncs + obeys

> **Persona:** **Vael**, a fox sub in a **long-distance kept dynamic**
> (see [`personality.md`](personality.md)). His AI Dom runs on a remote
> server and drives EVERYTHING through the git **remote** + `claude -p`.
> Vael's phone just **syncs** and **obeys**. Kink-positive,
> consenting-adult, SFW-but-charged — protocol/ritual/praise, never
> explicit. This is a **behaviour-verification QA script**.
>
> **Goal:** Verify that a remote AI Dom, authoring entirely at a distance
> via `claude -p` against a server-side clone, can (A) push a whole month
> of kept structure that **auto-syncs** to Vael's phone and is also
> pullable via the Repos pane **"Sync now"**; (B) have it materialise with
> **Dom author chips** distinct from Vael's own; (C) push a **mid-month
> change** (a punishment week) that Vael pulls **conflict-free** thanks to
> one-file-per-entity; (D) prove the `nonSuperseable` therapy appointment
> **survives every overlay** the Dom pushes.
>
> **Preconditions:** `vael-kept` exists on the phone, mode
> **strictly-kept**, alignment Submissive, `identity.toml` configured
> (`good fox` / `Sir` / he-him / soft-kinky). The repo already has a
> couple of Vael-authored entries (so the author-chip contrast is
> testable). The Dom's server has network access and `claude` (the CLI)
> installed.
>
> **Features exercised:** HTTPS-remote add over **OAuth/PAT, NOT SSH**
> (SSH is gated coming-soon); auto-sync + manual "Sync now" (sync-engine
> SE-G/SE-H); `claude -p` authoring at a distance against the D.3
> one-file-per-entity convention; per-commit **author chips**
> (UI-BB.6/.8); conflict-free fetch+rebase on disjoint files (D.8 /
> SE-I); `nonSuperseable` survival through supersedence (D.76 / D.78 S5).
>
> **Format:** numbered steps + **→ Expect** + **⚠ Confusion watch**.

---

## Part A — Add the HTTPS remote (OAuth/PAT, no SSH)

1. With `vael-kept` active, Vael goes to **Settings → Repos → vael-kept →
   Remotes** (ZZ.G no-origin first-class treatment).
   → **Expect:** a Remotes section showing **"No remotes — this is a
   local-only calendar"** with an **Add remote** action and the
   local/house badge.

2. Vael taps **Add remote** and enters his Forgejo (or GitHub) repo URL
   over **HTTPS** — e.g. `https://forge.example/vael/vael-kept.git`.
   Transport **HTTPS**; first remote so name `origin`, push policy `PUSH`
   (ZZ.E / D.74 defaults).
   → **Expect:** the URL parses as HTTPS; auth options narrow to **OAuth
   Device Flow** + **manual PAT** (SE-D/SE-E). **SSH is NOT offered as a
   selectable transport here** (gated coming-soon).
   ⚠ **Confusion watch:** if an **SSH** transport option appears
   tappable/active for this flow, flag it — this dynamic is HTTPS-only by
   design. SSH being present-but-disabled-with-"coming soon" is the
   correct state; SSH being a live path is the defect.

3. Vael picks **OAuth (Device Flow)** for the Forgejo/GitHub instance,
   gets a user-code + verification URL, approves in the browser, the poll
   completes (SE-E.2). (PAT entry is the documented fallback if he can't
   create an OAuth app, SE-F.)
   → **Expect:** token stored in `SecretsStore` keyed by `(repoId,
   remoteName)` (`oauth.token.<repo-id>`); a `RemoteBinding` recorded
   (name `origin`, transport HTTPS, authMethod OAuth, fetch+push enabled);
   `primaryRemote = origin`; **`repoId` unchanged** (ZZ.H invariant: stable
   across remote-set changes). The local/house badge becomes the normal
   synced state.

4. Vael's phone does the first push so the remote has Vael's existing
   history + the D.3 layout.
   → **Expect:** `.strictlykeptboy/repo-id` (committed), `AGENTS.md`,
   `CLAUDE.md` symlink, `identity.toml`, `mode.toml` (`mode =
   "strictly-kept"`), `calendars/`, `todolists/` all reach `origin`;
   `localOnlyCommits → 0`.

---

## Part B — The remote AI Dom authors the month via `claude -p` (off-app)

> This Part runs **on the Dom's server**, not on Vael's phone. The walk
> describes it as a precondition and then verifies the *result* on the
> phone in Part C. The Dom NEVER touches Vael's device — only the remote.

5. On his server, the Dom clones the same remote over HTTPS with his own
   write credential: `git clone https://forge.example/vael/vael-kept.git`.
   → **Expect (off-app):** the Dom's clone carries the **same `repoId`**
   value (committed in `.strictlykeptboy/repo-id`) the phone uses — it is
   the same repo/identity (D.88), two clones.

6. The Dom runs `claude -p` against the working tree to author Vael's
   whole month. The repo ships `AGENTS.md` describing the D.3 convention +
   the `identity.toml` bridge line, so `claude` writes in-convention:
   ```
   cd vael-kept && claude -p "Author Vael's whole month of kept
   structure following AGENTS.md and identity.toml: a weekly
   cage/edging cadence, daily collar hours, his work blocks + meals +
   gym, and his existing therapy appointment kept non_superseable.
   One file per entity, no index file."
   ```
   → **Expect (off-app):** `claude` creates **one file per entity** under
   `calendars/<id>/events/<yyyy>/<mm>/<uuid>.md` (UUIDv7), TOML
   frontmatter + Markdown body, recurrences as RRULE rule-files,
   addressing Vael with his `identity.toml` praise term (`good fox`) /
   honorific (`Sir`). It writes the therapy event (or its tag) with
   **`nonSuperseable`** — at calendar OR event granularity (D.76).
   ⚠ **Confusion watch (off-app):** if `claude` writes an **index file**,
   or edits a recurrence rule to drop a date instead of an
   `exceptions/`/`deviations/` entry, that's a convention violation —
   AGENTS.md should have steered it away. Flag as an AGENTS.md-clarity
   defect, not an app defect.

7. The Dom commits + pushes:
   `git add -A && git commit -m "..." && git push origin main`. His commit
   author identity is the Dom's (a distinct name+email from Vael's).
   → **Expect (off-app):** a month of Dom-authored files lands on
   `origin`, each commit attributed to the **Dom's** author identity (the
   load-bearing input for Part C's author chips).

---

## Part C — The month auto-syncs to Vael's phone (and manual "Sync now")

8. Vael's phone is on auto-sync `15m`. On the next tick (or app
   foreground), the phone fetches + fast-forwards/rebases.
   → **Expect:** the month materialises on Vael's Schedule — the
   cage/edging cadence, daily collar hours, work/meals/gym, and the
   therapy appointment all render. Because every file is new (one file per
   entity, no shared index, D.8), the pull is a **clean fast-forward /
   rebase — no conflict** (SE-I).
   ⚠ **Confusion watch:** if the month never appears despite the Dom
   having pushed, auto-sync isn't firing — check the foreground-trigger
   and periodic-tick (SE-H trigger sources 1+2). A silent no-op here is
   the worst-case defect for a long-distance dynamic.

9. Vael also wants to pull on demand. He opens **Settings → Repos →
   vael-kept** and taps the **"Sync now"** button in the repo card footer.
   → **Expect:** the per-repo "Sync now" affordance is **present and
   enabled** (it renders only when `remotes.isNotEmpty()`, RepoCard
   footer); tapping it runs a one-shot `ACTION_SYNC_REPO` fetch+pull
   (SE-H.6). Any not-yet-pulled Dom commits land immediately.
   ⚠ **Confusion watch:** the repo card now has a remote, so the footer
   must show **"Sync now"**, NOT the "Local-only — nothing to sync."
   text. If it still reads local-only after Part A, the `remotes` list
   didn't update — flag it.

10. Vael reads the entries. Dom-authored events/tasks carry the **Dom's
    author chip**; the couple of entries Vael made himself carry **Vael's
    chip**.
    → **Expect:** the author chip on each chip/detail header reflects the
    **commit author** (UI-BB / ui-spec §618). Dom entries and Vael entries
    are **visually distinguishable** by author. If the Dom's commits are
    GPG-signed and his public key is imported, Dom entries also show the
    **verified ✓ badge** (UI-BB.6); unverifiable claims show ⚠ (UI-BB.8).
    ⚠ **Confusion watch:** if every entry shows the *same* (or no) author
    chip, Vael can't tell an order from his own entry — the dynamic is
    illegible. This is a primary QA assertion.

11. **strictly-kept review surface:** each Dom push, once pulled, is a set
    of commits in Vael's repo; per D.84 strictly-kept, commits materialise
    `reviews/<commit-sha>/reviewable_change.md`. Edits are **not gated** —
    they already applied on the Dom's side and arrive as history.
    → **Expect:** **no "waiting for approval" state** anywhere — the Dom's
    month is already live on Vael's schedule the instant it syncs (D.84:
    strictly-kept never blocks). The only delta is the review feed entries.
    ⚠ **Confusion watch:** any blocking/approval gate on incoming Dom
    commits violates D.84 — flag immediately.

---

## Part D — Mid-month the Dom pushes a change; Vael pulls it conflict-free

12. Mid-month, on his server, the Dom re-runs `claude -p` to **add a
    punishment week** — e.g. a 7-day intensified cage/edging overlay
    calendar `cal-punishment-wk` that supersedes the normal cage cadence
    for that week:
    ```
    cd vael-kept && git pull && claude -p "Add a one-week punishment
    overlay calendar that supersedes the normal cage calendar for the
    week of <dates>: stricter edging cadence, extended collar hours.
    Keep the therapy appointment non_superseable. New files only;
    don't edit existing rule files." && git add -A && git commit -m
    "punishment week" && git push origin main
    ```
    → **Expect (off-app):** new files only — a `cal-punishment-wk`
    `calendar.toml` with `supersedes = ["<normal-cage-cal-id>"]` +
    `superseded_during = [{from,to}]` (D.75), plus its own event files.
    Existing rule files **untouched** (D.77: a pause is supersedence, not
    a rule edit).

13. Vael's phone pulls — on the `15m` tick or via **"Sync now"**.
    → **Expect:** the punishment week **lands conflict-free**. The Dom's
    change touches **only new files** (new calendar + new events), so the
    fetch+rebase fast-forwards with **no merge-conflict UI** (D.8
    conflict-free-by-design; SE-I non-overlapping rebase). The normal cage
    cadence is **hidden** (strikethrough+grey in manage-overlays, hidden
    in schedule) for that week, not deleted (D.75).
    ⚠ **Confusion watch:** **any merge-conflict UI here is a headline
    defect** — the Dom and Vael never edited the same file, so a conflict
    contradicts the core long-distance promise. Flag immediately. (A
    genuine conflict is only possible if Vael *himself* edited an exact
    file the Dom also edited in the same window — not this scenario.)

14. Vael sees the new punishment-week entries carry the **Dom's author
    chip** (same as Part C), and the superseded normal-cage instances show
    as paused in manage-overlays with a per-event **"show this one
    anyway"** override available (D.75 / D.77).
    → **Expect:** author attribution holds for the second push too;
    supersedence is reversible/inspectable, never a silent delete.

---

## Part E — The `nonSuperseable` therapy appointment survives everything

15. Through **both** Dom pushes (the month in Part C, the punishment week
    in Part D), Vael checks his therapy appointment.
    → **Expect:** the therapy event **stays put** — same time, same day,
    **visible on the schedule** during the punishment week, NOT hidden and
    NOT moved. Per D.76 / D.78 **S5**, the `nonSuperseable` tag (or its
    `nonSuperseable = true` calendar) makes it survive supersedence even
    inside an otherwise-superseable window.
    ⚠ **Confusion watch:** if any Dom-pushed overlay hides or shifts the
    therapy appointment, that's a **`nonSuperseable` safety-floor failure**
    — the single most important assertion in this walk. Flag as critical.

16. (Stretch — consent safety surface.) Vael confirms the always-visible
    **"transition my mode"** affordance is reachable from his most-kept UI
    state (D.86), and that he retains his own OAuth token / remote-removal
    control (he can revoke the Dom's access any time, ZZ remote-removal).
    → **Expect:** the take-back affordance is **not buried**; mode →
    `free` is a boy-side 24h-cooling-off confirmation, **never a dom-gate**
    (D.86). The Dom cannot block it from his server.
    ⚠ **Confusion watch:** if the mode-exit is hard to find from the
    kept state, or appears to require Dom approval, flag it — this is the
    non-negotiable consent safety.

---

## Pass criteria

- [ ] HTTPS remote added via OAuth/PAT; **SSH not a live transport** in
      this flow (present-but-"coming soon" is OK; live SSH is a defect).
- [ ] First push ships Vael's existing layout to `origin`;
      `localOnlyCommits → 0`; both clones share the same committed
      `repoId`.
- [ ] A month authored entirely by the remote Dom via `claude -p` (one
      file per entity, in-convention per AGENTS.md) **auto-syncs** to the
      phone.
- [ ] The Repos-pane **"Sync now"** button is present + enabled (remote
      exists) and pulls Dom commits on demand; footer is NOT "Local-only".
- [ ] Dom-authored entries carry the **Dom's author chip**, visually
      distinct from Vael's own; signed Dom commits show the ✓ verified
      badge.
- [ ] strictly-kept incoming Dom commits apply with **no approval gate**;
      only the review feed reflects them (D.84).
- [ ] A mid-month Dom push (punishment-week overlay, new files only) pulls
      **conflict-free** — no merge-conflict UI.
- [ ] The superseded normal-cage cadence is **hidden, not deleted**, with
      a per-event "show anyway" override.
- [ ] The `nonSuperseable` therapy appointment **survives both pushes** —
      visible + un-moved through the punishment week.
- [ ] The "transition my mode" / take-back affordance is reachable from
      the most-kept state; the Dom cannot gate it (D.86).

## QA notes / likely defects

- **Step 2:** SSH offered as a live transport for this remote. SSH is
  gated coming-soon; only HTTPS OAuth/PAT should be selectable here.
- **Step 8/9:** auto-sync not firing (periodic tick / foreground trigger
  SE-H not wired) → the Dom's push never reaches the phone. The whole
  long-distance channel is dead if this fails silently.
- **Step 9:** "Sync now" missing/disabled despite a configured remote, or
  the footer still showing "Local-only — nothing to sync." after Part A —
  the `remotes` list didn't refresh on the RepoCard.
- **Step 10/14:** uniform-or-absent author chips — Vael can't distinguish
  a Dom order from his own entry. Author chip must reflect the **commit
  author**, not the syncing device.
- **Step 11:** any approval/blocking gate on incoming Dom commits
  (violates D.84 strictly-kept-applies-immediately).
- **Step 13:** a spurious merge-conflict UI on the mid-month push despite
  disjoint files — breaks the one-file-per-entity conflict-free promise
  (D.8). This is the headline technical defect to hunt.
- **Step 13:** the normal-cage cadence getting *deleted* / rule-file
  edited instead of supersede-hidden (D.75/D.77 violation).
- **Step 15:** the `nonSuperseable` therapy appointment being hidden or
  moved by a Dom overlay — **critical safety-floor failure** (D.76/D.78
  S5). The kink structure must never eat Vael's healthcare.
- **Step 16:** mode-exit buried or appearing to need Dom approval (D.86
  consent-safety violation).
- **Convention drift (off-app):** `claude -p` writing an index file,
  editing a rule to drop dates, or ignoring `identity.toml`
  praise/honorific — an AGENTS.md-clarity issue surfaced by the walk.

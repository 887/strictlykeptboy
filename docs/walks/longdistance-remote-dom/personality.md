# Persona — Vael, a fox sub in a long-distance kept dynamic

> One of the `docs/walks/` scenario personas. Used to drive realistic
> end-to-end test scripts against strictlykeptboy. Names and details are
> invented furry-kink handles; this is not a real person. Kink-positive,
> consenting-adult lifestyle organisation — see `decisions.md` K-1..K-7.
> SFW-but-charged: structure, ritual, protocol, praise, honorific. The
> walk it backs is a **behaviour-verification QA script**, never erotica.

## Snapshot

- **Handle:** Vael (a fox). Sub / "kept boy".
- **Pronouns:** he/him.
- **Repo:** `vael-kept` — a single repo that IS his identity (D.88). Mode
  **strictly-kept** (D.84); alignment Submissive.
- **Dynamic:** **long-distance**. His Dom is an **AI Dom** running on a
  remote server (another machine entirely — not Vael's phone). Vael and
  the Dom never share a device. *Everything* the Dom does to Vael's
  schedule happens through the **git remote** + `claude -p` run against a
  server-side clone of the repo. Vael's phone's only jobs are **sync**
  and **obey**.
- **Devices:** one phone (Pixel, Android 16 / API 36) with
  strictlykeptboy. No laptop in the loop — the Dom's machine is the only
  other clone, and Vael never touches it.
- **Remote:** `vael-kept` has **one HTTPS remote** — a Forgejo instance
  (could equally be GitHub), authed via **OAuth Device Flow / PAT**, NOT
  SSH. (SSH transport is gated "coming soon" in the app, so this dynamic
  rides HTTPS PAT/OAuth on purpose.)

## The dynamic shape (why the texture matters for QA)

- **The Dom is remote and asynchronous.** He is not tapping Vael's phone.
  He clones `vael-kept` on his server, runs `claude -p "<instruction>"`
  against the working tree to author/modify Vael's structure, commits,
  and pushes. Vael experiences the dynamic as *files arriving over sync*.
  The headline QA question: does a month of Dom-authored structure, made
  entirely at a distance via `claude -p` + push, **materialise correctly
  on Vael's phone with the right author attribution**?
- **Sync is the whole channel.** Auto-sync pulls the Dom's pushes on the
  configured interval; Vael can also force a pull via the Repos pane
  **"Sync now"** button. Both paths must work. A missed pull = the Dom's
  instruction never reaches him, which in-fiction reads as "I didn't get
  my orders" — so reliable, observable sync is load-bearing, not cosmetic.
- **One file per entity is what makes long-distance safe.** Because every
  event/task/rule is its own file (D.3, no index file), the Dom can push a
  whole month and then push a mid-month change and Vael's pull
  **fast-forwards / rebases clean** — no conflict, because the Dom and
  Vael never edit the same file at the same second. This conflict-free
  property is the single technical promise the whole long-distance
  dynamic rests on.
- **Author attribution is consent-legible.** Vael needs to see, at a
  glance, which entries the *Dom* authored vs which he created himself.
  The **author chip** on each event/task chip is how he reads "this is an
  order" vs "this is mine". If Dom-authored entries don't carry a
  distinct author chip, the dynamic is illegible.
- **Some things the Dom must never override.** Vael has a real therapy
  appointment. It is tagged `nonSuperseable` (D.76). No overlay the Dom
  pushes — not a punishment week, not a cage-intensive block — may hide
  or move it. This is the safety floor: the kink structure never eats
  Vael's healthcare.

## Repo + identity / mode choices

- **Repo:** `vael-kept`, mode **strictly-kept**, alignment Submissive.
  `identity.toml`: praise term `good fox`, honorific-for-dom `Sir`,
  pronouns he/him, tone register `soft-kinky`, emoji density medium
  (English keys, kink flavour in values only — D.83).
- **Remote:** one Forgejo (or GitHub) HTTPS remote named `origin`, OAuth
  Device Flow (PAT fallback), auto-sync `15m`, any-network. The Dom's
  server has its own clone of the same remote with its own write
  credential. **No SSH** (gated coming-soon).
- **The Dom's side is out of app scope.** The Dom is a `claude -p`
  invocation on a server against a clone of `vael-kept`. The walk
  verifies the *phone's* observable behaviour (sync, materialisation,
  author chips, conflict-free pulls, `nonSuperseable` survival); the
  server-side `claude -p` runs are described as off-app preconditions.

## Frustrations to watch for (QA gold)

- Any moment a Dom push is committed+pushed to `origin` but **never
  surfaces on Vael's phone** — auto-sync silently not firing, or "Sync
  now" not actually fetching.
- "Sync now" missing / disabled on a repo that clearly has a remote.
- Dom-authored entries that **don't carry a distinct author chip**, so
  Vael can't tell an order from his own entry.
- A mid-month Dom change producing a **spurious merge conflict** despite
  one-file-per-entity (would break the long-distance promise outright).
- The therapy appointment getting **hidden or moved** by a Dom-pushed
  overlay (a `nonSuperseable` failure — the safety floor).
- SSH being offered/required anywhere in this flow (it's gated
  coming-soon; this dynamic is HTTPS-only).

## What success looks like

- Vael configures one HTTPS remote on `vael-kept` from the phone, OAuth
  (or PAT), no SSH.
- The remote AI Dom, on his own server, runs `claude -p` to author a full
  month of kept structure and pushes it; it **auto-syncs** to Vael's
  phone (and a manual **"Sync now"** pulls it on demand).
- The month materialises with **Dom author chips**; Vael's own entries
  carry his chip — the two are visually distinct.
- Mid-month the Dom pushes a punishment-week change; Vael pulls it
  **conflict-free** and sees it land.
- Through every overlay, Vael's `nonSuperseable` therapy appointment
  stays put — visible, un-moved, un-hidden.

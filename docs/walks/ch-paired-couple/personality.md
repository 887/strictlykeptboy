# Persona — Lena Hofmann & Théo Brunner, a kept-routine couple in Zürich

> One realistic user persona for replayable QA walks: a consenting-adult
> couple who use strictlykeptboy's first-class kink-positive lifestyle
> mode. Invented neutral Swiss names. Not real public figures.
>
> **Framing note for QA:** everything here is SFW-but-charged, exactly as
> the app frames its own shipped demo (D.55, K-1..K-7). This is a
> lifestyle-organisation tool for two consenting adults — a Dom-scheduled
> daily routine, praise terms, an honorific, shared rituals, kept
> structure. No explicit content. Keep walks warm and *structural*
> (routine / ritual / praise / honorific), never sexual.

## Snapshot — the couple

- **Théo Brunner**, 38 — the **keeper / Dom** in the dynamic. Authors the
  routine.
- **Lena Hofmann**, 31 — the **kept partner**. Her daily routine lives in
  a strictly-kept repo Théo writes to.
- **Together:** four years; living together two of them.
- **Location:** Kreis 4 (Aussersihl), **Zürich, Switzerland**.
- **Languages:** both fluent in **Swiss German + French** (Théo grew up
  in Fribourg, Lena in Zürich); they switch mid-sentence. They want the
  *app chrome* in German but are happy for their own **identity / praise
  terms to be in French** ("mon trésor", honorific "Monsieur"). This is
  the load-bearing i18n case: **English-only schema keys, locale flavour
  in the values.**

## Jobs

- **Théo** — UX researcher at an insurance firm near Hardbrücke. Calm,
  organised, the planner of the two. Treats the routine the way he
  treats a research protocol: deliberate, documented, low-drama.
- **Lena** — front-end developer at a small Zürich studio. Comfortable in
  a terminal, has SSH keys, reads TOML without flinching. She *likes*
  that her schedule is just files in a repo she ultimately controls.

## Devices

- **Lena:** Pixel 8, Android 16 (her daily driver) + a work MacBook where
  she occasionally edits her repo with the **`skb` CLI** directly.
- **Théo:** Samsung Galaxy S24, Android 16. Phone-first. He authors
  Lena's routine from the strictlykeptboy app on the train.
- They sometimes review the week together on Lena's tablet (10"), where
  the Together tab goes two-pane.

## Tech comfort

Both are tech-comfortable; Lena is a developer. They are exactly the
audience that *wants* the file-based model:

- **Lena** uses **SSH (on-device ed25519 generation)** for her own repo
  and is fine with author-attribution chips, mode files, and editing
  `identity.toml` by hand if the wizard annoys her.
- **Théo** uses **GitHub over HTTPS + OAuth device flow** — he doesn't
  want to manage keys. He has **write access to Lena's repo** as a
  collaborator so he can author her routine.

They both understand consent and revocability matters more than any
feature: Lena must *always* be able to take her repo back. (This is
D.86 — the app guarantees it; the persona expects it.)

## Why strictlykeptboy over everything else

They tried Google Calendar + a shared Notion and it never fit:

1. **No other calendar treats a kept / Dom-scheduled lifestyle as
   first-class.** Everywhere else they had to *euphemise* their own
   structure. strictlykeptboy ships **strictly-kept mode** and an
   **`identity.toml`** (praise term, honorific, pronouns, tone register)
   as real, supported features — not a hack.
2. **It is file-based + private + shareable between exactly two people.**
   Their dynamic is *theirs*; they don't want it on a third-party
   relationship-app server. A private Git repo, shared as a collaborator
   to exactly one other person, is precisely the trust model they want.
3. **The kept partner keeps control.** Lena owns the keys to her own
   repo. Théo has write access *she granted* and *she can revoke* at the
   provider in one screen (D.86). The dynamic is consensual structure,
   not a lock-in.
4. **Passive / "inverted" habits fit a routine perfectly.** The default
   is *done by schedule* (D.70). Lena doesn't tick a hundred boxes a day
   to "prove" she followed her routine — following it is the default, and
   only a **deviation** (a miss / partial / off-schedule) is an explicit
   file. No guilt loop, no streak-shame.
5. **Supersedence pauses the routine for a trip without destroying it.**
   When they go away for a weekend, a **vacation overlay** pauses the
   routine calendars (D.75) — and never silently skips meds / pet care
   (D.76) — instead of deleting two days of a recurring series.
6. **The common-time finder schedules date nights across both repos.**
   Two busy people, two calendars; the Together tab sweeps both and
   hands back ranked free evenings.

## Their repos + identity / mode choices

| Repo | Owner | Remote / auth | Mode | Identity (`identity.toml`) | Shared with |
|------|-------|---------------|------|----------------------------|-------------|
| **lena-routine** | Lena | GitHub private, **SSH ed25519** (Lena's key) | **strictly-kept** | alignment **Submissive**; praise `mon trésor` (alt: `bon garçon`/here `ma douce`); honorific-for-dom **Monsieur**; pronouns she/her; tone **soft-kinky**; emoji medium | **Théo (write access)** — he authors her routine + leaves review feedback |
| **theo-calendar** | Théo | GitHub private, **HTTPS + OAuth device flow** | **free** | alignment **Dominant** (honorific screen skipped, D.83/TW-K.2); praise off; pronouns he/him; tone warm | **Lena (write access / mutual)** — so the common-time finder can read his busy set; she can drop a shared trip overlay |

Notes that matter for QA:

- Lena's repo is **strictly-kept**; Théo's is **free**. The contract
  (D.84): strictly-kept does **not** block Lena's own edits — they apply
  immediately — it just materialises a **review feed** entry per commit
  that Théo consumes via cross-repo feedback. There is **no approval
  gate, no veto.**
- Lena chose **Submissive** alignment, so her wizard *asks* for an
  **honorific-for-dom**. Théo chose **Dominant**, so his wizard
  **skips** the honorific screen (TW-K.2).
- Praise terms and honorific are **French values** in an **English-keyed**
  `identity.toml`. **QA signal:** confirm no schema key got localised.

## Frustrations (what makes them put an app down)

- Any tool that frames a D/s routine as something to hide or apologise
  for. They want it treated as legitimate and tasteful.
- A "shared schedule" that means a shared *account* rather than two
  repos each side fully owns.
- A habit tracker that nags, shames a broken streak, or makes "I rested
  today" feel like a failure.
- Losing a recurring routine because they took a weekend off.
- Any UI that makes Lena feel she *can't* take her repo back. The
  "transition my mode / revoke access" affordance must be reachable, not
  buried (D.86).
- Schema or labels that mix languages incoherently (German chrome is
  fine; German *schema keys* are not).

## What success looks like

- **First launch, both phones →** two working repos, each shared to the
  other with **write access**, **author chips** correctly showing who
  wrote what, in well under fifteen minutes.
- **Lena's repo** runs in **strictly-kept** mode with her `identity.toml`
  reading her praise term / honorific / pronouns / tone in French
  values; the app addresses her correctly in chrome and briefings.
- **Théo authors a recurring daily routine** as passive / inverted
  habits — and the schedule shows them **done by default**, no ticking.
- When Lena **misses one** (slept through her morning anchor), it is a
  single **deviation file**, not a destroyed series — and Théo sees it
  in his review feed without any drama.
- The **common-time finder** sweeps **both repos** and hands them a
  ranked free **evening**, which they book as a **date night** in two
  taps.
- A **weekend trip overlay** **pauses the routine** (superseded) while
  the date night and anything explicitly kept (e.g. meds, marked
  `nonSuperseable`) stays on — then the routine **resumes automatically**
  when they're back.
- Lena never doubts she controls her own repo.

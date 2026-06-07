# Persona — Caspian ("Cas"), a kept fox sub kept by an AI Dom

> One realistic user persona for replayable QA walks: a consenting adult
> who is kept *solo* — there is no human partner with a second phone.
> His keeper is an **AI Dom ("Sir")** that runs `claude -p` against his
> calendar Git repo nightly. Invented neutral kink-character name (a
> fox). Not a real person.
>
> **Framing note for QA:** everything here is SFW-but-charged, exactly as
> the app frames its own shipped surface (D.55, K-1..K-7). This is a
> lifestyle-organisation tool for a consenting adult — an AI-Dom-authored
> daily routine carrying cage on/off windows, edging rituals, a collar
> ritual, praise terms and an honorific, mixed in with the mundane
> (work blocks, meals, gym, meds). No explicit content. Keep walks warm
> and *structural* (routine / ritual / praise / honorific / cage-on /
> cage-off as schedule entries), never sexual.

## Snapshot — Cas + Sir

- **Caspian ("Cas")**, 29 — the **kept fox sub**. Solo. His daily life
  lives in a single strictly-kept repo, `cas-kept`.
- **"Sir"** — an **AI Dom**, not a person. Sir is a `claude -p`
  invocation (D.24/D.85) that Cas runs against `cas-kept` every night to
  lay out the next day. Sir has its own `identities/<person-id>.md`
  entry in the repo, so every entry Sir writes carries Sir's **author
  chip** — Cas can see at a glance what *he* wrote vs. what *Sir* wrote.
- **No second phone.** This is the load-bearing difference from the
  paired-couple persona: keeping is done by an agent run locally, not a
  human collaborator pushing from a Galaxy. The "dom side" is a CLI
  prompt, not a second app install.
- **Location:** lives alone; works hybrid (3 office days, 2 home days).

## The dynamic, as the app actually models it

Cas is kept *by his calendar*. Each night Sir authors the next day as
**one file per entity** (D.3) into `cas-kept`:

- **Kinky structure** — a `cal-ritual` calendar carrying **cage-on** /
  **cage-off** windows, **edging** rituals, a morning **collar** ritual,
  a **kept evening check-in**. These are inverted/passive habits (D.54):
  default state = done-by-schedule, a miss is an explicit `deviations/`
  file.
- **Mundane structure** — `cal-work` (work blocks), `cal-life` (meals,
  gym), and a `cal-health` calendar carrying **meds** + **hydration**
  that is marked `nonSuperseable` (D.76).

Sir's authoring is the killer use-case: *the AI Dom reads and writes a
submissive's calendar Git repo by running `claude -p` against it.* The
repo ships `AGENTS.md` describing the one-file-per-entity TOML-frontmatter
+ Markdown convention (DM-G), and an `identity.toml` (D.83) carrying
Cas's praise term / honorific / pronouns / tone — so Sir addresses Cas
correctly when it authors.

## Jobs

- **Cas** — backend engineer at a mid-size SaaS shop. Comfortable in a
  terminal, has SSH keys, reads TOML without flinching. He *wants* his
  keeping to be just files in a repo he ultimately controls, and he
  *wants* the Dom to be an agent he can run on demand rather than a
  human he has to coordinate with.

## Devices

- **Cas:** Pixel 8, Android 16 (daily driver) where the strictlykeptboy
  app renders his schedule + a Linux laptop where he runs
  `claude -p` against a local clone of `cas-kept` (and occasionally the
  `skb` CLI directly). The nightly authoring loop happens on the laptop;
  the phone is the consumer surface that **syncs and renders** what Sir
  wrote.

## Tech comfort

High. Cas is exactly the audience the file-based model is for:

- Adds `cas-kept`'s remote over **HTTPS (PAT/OAuth)** — in-app SSH transport is gated coming-soon.
- Reads author-attribution chips, `mode.toml`, `identity.toml`,
  `supersedes` lists, and `deviations/` files without a wizard holding
  his hand.
- Trusts the model precisely *because* it is files: the AI Dom can only
  ever do what a commit to his own repo can do, and he can `git revert`
  or revoke at will (D.86). The keeping is consensual structure, not
  lock-in.

## Why strictlykeptboy over everything else

He tried Google Calendar + a habit tracker and it never fit:

1. **No other calendar lets an AI Dom *author* a kept routine as
   first-class data.** Everywhere else, a `claude -p` agent would have to
   reverse-engineer an opaque API or an .ics blob. Here the repo ships
   `AGENTS.md` + `identity.toml` so `claude -p` writes well-formed
   cage-on/edging/meds entries on the first try.
2. **Author chips show who kept him.** Sir's entries carry Sir's author
   chip (D.15); Cas's own carry his. He can filter by author and see the
   keeping as a visible, attributable layer on his own calendar.
3. **He keeps control.** Cas owns the key to `cas-kept`. Sir is a
   `claude -p` run *he* triggers, against *his* repo. He can revert any
   commit, flip `mode.toml` back to `free` (D.86), or stop running the
   agent — no lock-in, no dom-gate.
4. **Passive / "inverted" habits fit a kept routine perfectly (D.54).**
   The default is *done by schedule* (D.70). Cas doesn't tick a hundred
   boxes to "prove" he stayed caged or did his evening check-in; that is
   the default, and only a **deviation** (a miss/partial) is an explicit
   file. No guilt loop, no streak-shame.
5. **Supersedence pauses the rituals for a work trip without destroying
   them (D.75).** When Cas travels for work, a trip overlay **pauses**
   the cage/edging rituals for the trip window — *greyed, not deleted* —
   while **meds + hydration** (`nonSuperseable`, D.76) **stay on**. The
   rituals resume automatically when he's back.

## His repo + identity / mode choices

| Repo | Owner | Remote / auth | Mode | Identity (`identity.toml`) | Authored by |
|------|-------|---------------|------|----------------------------|-------------|
| **cas-kept** | Cas | GitHub private, **HTTPS (PAT/OAuth)** — SSH gated coming-soon | **strictly-kept** | alignment **Submissive**; praise `good fox` (alt: `sweet kit`); honorific-for-dom **Sir**; pronouns he/him; tone **soft-kinky**; emoji medium | **Cas** (own edits) + **Sir** (the `claude -p` AI Dom, its own `identities/` entry) |

Notes that matter for QA:

- `cas-kept` is **strictly-kept** (D.84). The contract: strictly-kept
  does **not** block Cas's own edits — they apply immediately — it just
  materialises a `reviews/<sha>/reviewable_change.md` per commit. There
  is **no approval gate, no veto**, and since the "dom" is an agent Cas
  runs, the review feed is informational, not a permission wall.
- Cas chose **Submissive** alignment, so the wizard asks for an
  **honorific-for-dom** (`Sir`).
- Praise term / honorific are **English values** in an **English-keyed**
  `identity.toml` (this persona is English-only on both axes — the
  schema-key test still holds: kink flavour goes in **values**, never
  identifiers).
- **Sir is a repo identity, not an app account** (D.88). There is no
  second user. The author chip distinguishes Cas-authored from
  Sir-authored entries purely by the `author` frontmatter field
  resolving to a different `identities/<person-id>.md`.

## Frustrations (what makes him put an app down)

- Any tool that frames a D/s kept routine as something to hide or
  apologise for. He wants it treated as legitimate and tasteful.
- A calendar an AI agent *can't* author cleanly — opaque APIs, no
  documented file convention, no `identity.toml` for the agent to read.
- A habit tracker that nags, shames a broken streak, or makes "I rested
  today" feel like a failure.
- Losing his recurring rituals because he took a work trip — supersedence
  must *pause*, never *delete*.
- Meds or hydration silently disappearing because a vacation overlay
  paused everything indiscriminately. `nonSuperseable` must hold.
- Any UI that makes him feel he can't take his repo back (D.86).
- Schema or labels that mix kink flavour into identifiers (English-only
  schema rule).

## What success looks like

- **Nightly `claude -p` authoring loop works.** Cas runs one
  `claude -p "lay out tomorrow…"` against `cas-kept`; Sir lands kinky +
  mundane entries as **one-file-per-entity commits**, each well-formed
  TOML-frontmatter + Markdown, authored as Sir.
- **The app syncs and renders Sir's work.** On Cas's phone the next-day
  schedule shows cage-on/cage-off windows, the edging ritual, the collar
  ritual, the work blocks, meals, gym, and meds — each kink event
  carrying an **author chip** identifying it as foreign-source, authored
  by **Sir**.
- **Strictly-kept mode is on** (`mode.toml` committed), and Cas's own
  edits still apply immediately — the only delta is the review feed.
- **A work trip triggers supersedence.** A trip overlay **pauses** the
  `cal-ritual` cage/edging entries for the trip window — they go
  **greyed/strikethrough in manage-overlays, hidden from the day view**,
  *not* deleted — while **meds + hydration stay on** because they are
  `nonSuperseable`. Rituals resume automatically after the trip.
- Cas never doubts he controls his own repo, and never has to euphemise
  his own structure.

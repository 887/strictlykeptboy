# Persona — Milo, a cat sub doing first-run onboarding

> One of the `docs/walks/` scenario personas. Used to drive a realistic
> end-to-end QA test script against strictlykeptboy. Names and details
> are invented furry-kink handles; this is **not** a real person. Per
> the project's hard rule, persona handles are furry-species names only
> (cats / dogs / foxes / wolves), never a real person's name or email.

## Snapshot

- **Handle:** Milo (a cat sub — "kitten" in his own words)
- **Species avatar:** cat 🐱
- **Pronouns:** he/him
- **Alignment:** **Submissive**
- **Relationship shape this walk:** **solo sub kept by an AI Dom.** No
  human partner / keeper repo in this scenario — the Dom is a `claude -p`
  agent that reads and writes Milo's calendar git repo directly. This is
  the project's genesis use-case (`LifestyleCard.PetKeptByAi`, the wizard
  default card).
- **Tech comfort:** Comfortable enough. Has used GitHub before, can run a
  terminal, has `claude` (Claude Code) installed on his laptop. Brand-new
  to strictlykeptboy — this is a **cold first install**, app never
  launched. He is NOT going to hand-author a calendar; the whole appeal
  is that the AI Dom seeds the structure for him.
- **Devices:**
  - Primary: a Pixel phone (Android 16 / API 36) — where the app runs.
  - A Linux laptop where `claude` runs against the same git repo (in this
    walk the repo is phone-only/no-origin to start; the `claude -p`
    seeding is described against the on-device working tree as the AI Dom
    would operate once the repo is reachable — see the walk's framing
    note in step 14).
- **Real-life anchor that matters for QA:** **Milo has an actual cat to
  feed.** A live animal at 07:00 and 18:00 every day. This is not kink
  flavor — it is a `nonSuperseable` survival obligation, exactly the
  category (pet-care) the D.76 invariant protects. It sits in the same
  bucket as his meds.

## What Milo wants out of the app

- He picked **Submissive** on the alignment screen because that is who he
  is, and he wants the app to *lean into* it — he is the target audience
  D.55 describes, and a neutral default would underserve him.
- He wants the AI Dom to **own the structure**: build his first kept
  week for him (morning cage, a daily edging ritual, work blocks, gym),
  so he can just *follow* the schedule rather than design one. The
  inverted-habit model (D.70 — done-by-default, deviation is an explicit
  note) is exactly the relationship he wants with his own routine.
- He wants to be able to take a weekend away without his whole life
  unravelling — the rituals should pause for the trip (D.75 supersedence),
  but the two things that *cannot* lapse — **his meds and feeding his
  cat** — must still fire (D.76). He has been bitten before by calendar
  tools that "paused everything" for a vacation overlay and silently
  dropped a medication dose. That exact bug class is what this walk
  verifies the app does NOT have.

## The kink shape (why the texture matters for QA)

This is a behavior-verification script, not erotica. The kink dynamic is
explicit only insofar as it drives **schema + entries + gating** the QA
has to confirm:

- **Alignment → mode default is the gate under test.** Picking
  Submissive must make the wizard *default* mode to strictly-kept + an
  AI-Dom persona (the `PetKeptByAi` card), while still letting Milo
  override to free. He doesn't override — we verify the default landed.
- **Identity is owner-preference, not chrome.** Praise term ("good
  kitten"), honorific ("Sir"), tone register — these persist into
  `identity.toml` (D.83), committed, separate from `AGENTS.md`.
- **The AI Dom is a real write path.** `claude -p` editing the repo is
  the headline use-case, not a side feature. The seeded week must land as
  real one-file-per-entity entries the app then renders.
- **`nonSuperseable` is a survival invariant, not a preference.** Meds +
  cat-feeding survive the vacation overlay. If a weekend-away overlay
  ever hides a medication dose or a cat feeding, that is a **hard fail** —
  it is the D.76 / S4 / S5 invariant and the exact bug that bit before.

## Tone for the script

Warm, kink-positive, plain about the dynamic. The praise term is "good
kitten"; the honorific is "Sir". No SFW-laundering of in-app copy (D.55:
SFW phrasing applies only to surfaces that leak outside the app). Schema
identifiers stay English-only (`nonSuperseable`, `strictly-kept`,
`pet-care`); the kink flavor lives in values and prose, never in
field names.

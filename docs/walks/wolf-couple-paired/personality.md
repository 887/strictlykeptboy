# Persona — Fenrir & Rem, an AI-Dom-scheduled wolf couple

> One realistic user persona for replayable QA walks: a consenting-adult
> gay couple who use strictlykeptboy's first-class kink-positive
> lifestyle mode AND its agent-native angle — the Dom offloads scheduling
> to an **AI Dom persona** he runs with `claude -p` against his sub's
> shared repo. Invented furry-kink handles (wolves). Not real people.
>
> **Framing note for QA:** everything here is SFW-but-charged, exactly as
> the app frames its own shipped demo (D.55, K-1..K-7). This is a
> lifestyle-organisation tool for two consenting adults — a Dom-scheduled
> kept routine, praise terms, an honorific, shared rituals, kept
> structure, plus mundane life (gym, groceries, date night). No explicit
> content. Keep walks warm and **structural** (ritual / chore / routine /
> praise / honorific / review), never sexual.

## Snapshot — the couple

- **Fenrir**, 34 — the **Dom wolf** in the dynamic. He authors Rem's
  kept week, but he is a busy human Dom — he does NOT hand-place every
  event. He runs an **AI Dom persona** (`claude -p`) against Rem's shared
  repo to lay out the week, then reviews + pushes.
- **Rem**, 28 — the **kept sub wolf**. His daily routine, rituals, and
  chores live in a strictly-kept repo Fenrir writes to. Rem owns the repo
  and the keys; the dynamic is consensual structure, not lock-in.
- **Together:** three years; living together for one. A gay couple.
- **Handles:** they use furry handles between themselves — Fenrir keeps
  Rem as his wolf. The honorific Rem uses for Fenrir is **"Alpha"**; the
  praise term Fenrir's AI persona uses is **"good wolf"** (alt: `pup`).

## Jobs

- **Fenrir** — DevOps engineer. Lives in a terminal, has `claude` on his
  laptop and the strictlykeptboy app on his phone. He treats Rem's week
  the way he treats infra: declare the desired state, let the agent
  reconcile it, review the diff, ship. The `claude -p` workflow is not a
  novelty to him; it's how he already works.
- **Rem** — graphic designer. Comfortable on his phone, not a terminal
  person. He wants his week *handed to him*, kept, with the kink and the
  chores both on it — and he wants to know, at a glance, **which events
  Fenrir (or Fenrir's AI persona) authored** vs. the ones he added.

## Devices

- **Fenrir:** Pixel 9 (Android 16) for the app + a **Linux laptop** where
  he runs `claude -p` against Rem's cloned repo. The laptop is where the
  AI Dom authoring happens; the phone is where he reviews the review feed.
- **Rem:** Samsung Galaxy S24 (Android 16). Phone-only. He never opens a
  terminal — the app is his whole surface.
- They occasionally review the week together on Rem's tablet (10"), where
  the Together tab goes two-pane.

## Tech comfort

Split, deliberately — this couple is the load-bearing case for the
agent-native angle:

- **Fenrir** is the power user. He authenticates Rem's repo over **HTTPS (PAT/OAuth; SSH gated coming-soon)**,
  clones it to his laptop, points a **custom-prompt AI Dom persona** at it
  (`~/.config/skb/dom-personas/`, D.85), runs `claude -p`, eyeballs the
  result, pushes.
- **Rem** is phone-only and does NOT author his own kept routine — that's
  the point of the dynamic. He syncs, reads, completes rituals, and
  occasionally adds a personal event. He must be able to take his repo
  back at any time (D.86); the persona expects that guarantee.

## Why strictlykeptboy over everything else

They tried a shared Google Calendar and it never fit:

1. **No other calendar lets a Dom drive a sub's schedule with an AI
   agent.** strictlykeptboy's repos ship an `AGENTS.md` describing the
   one-file-per-entity TOML+Markdown convention, so `claude -p` can read
   and write Rem's whole week the same way Rem can. The killer use-case is
   literally "Fenrir runs an AI Dom persona on Rem's repo." Everywhere
   else, the calendar is an opaque server.
2. **It treats a kept / Dom-scheduled lifestyle as first-class.**
   strictly-kept mode + `identity.toml` (praise `good wolf`, honorific
   `Alpha`, tone, pronouns) are real shipped features, not a hack.
3. **The author chip distinguishes who wrote an event from whose repo it
   is.** When Fenrir's AI persona writes "morning cage 07:00" into Rem's
   repo, Rem's app shows a **foreign-author chip** (Fenrir's
   initials/avatar) on that event — so Rem always knows the kept events
   came from his Dom, not from himself.
4. **The kept partner keeps control.** Rem owns his SSH key. He can revoke
   Fenrir's access in one provider screen (D.86). Consensual structure.
5. **strictly-kept materialises a review feed, not an approval gate.**
   Rem's edits apply immediately (no veto). Each commit materialises a
   `reviews/<sha>/reviewable_change.md` that Fenrir consumes — so when Rem
   completes a ritual, Fenrir sees it round-trip without any drama (D.84).
6. **The common-time finder schedules their date night across both
   repos.** Two wolves, two calendars; the Together tab sweeps both and
   hands back a ranked free Friday evening.

## Their repos + identity / mode choices

| Repo | Owner | Remote / auth | Mode | Identity (`identity.toml`) | Shared with |
|------|-------|---------------|------|----------------------------|-------------|
| **rem-kept** | Rem | GitHub private, **HTTPS (PAT/OAuth)** — SSH gated coming-soon | **strictly-kept** | alignment **Submissive**; praise `good wolf` (alt `pup`); honorific-for-dom **Alpha**; pronouns he/him; tone **soft-kinky**; emoji medium | **Fenrir (write access)** — he (and his AI persona) author Rem's week + read the review feed |
| **fenrir-calendar** | Fenrir | GitHub private, **HTTPS (PAT/OAuth)** | **free** | alignment **Dominant** (honorific screen skipped, D.83/TW-K.2); praise off; pronouns he/him; tone warm | **Rem (write access / mutual)** — so the common-time finder can read Fenrir's busy set + Rem can drop a shared overlay |

Notes that matter for QA:

- Rem's repo is **strictly-kept**; Fenrir's is **free**. The contract
  (D.84): strictly-kept does **not** block Rem's own edits — they apply
  immediately — it just materialises a **review feed** entry per commit
  that Fenrir consumes. There is **no approval gate, no veto.**
- The **AI Dom persona** Fenrir points at `rem-kept` is a `custom-prompt`
  persona (D.85 / SH-O.2), recorded device-locally in
  `.strictlykeptboy/ai-dom-pointer.toml` on **Fenrir's** device — NEVER
  committed (persona names are kink-coded; D.85 / SH-O.6). The six shipped
  personas (`stern-but-fair`, `playful-tease`, `kinky-affectionate`,
  `daddy-warmth`, `bratty-switch-energy`, `clinical-protocol`) are the
  builtins; Fenrir's wolf-Dom register lives in the `custom-prompt` slot.
- Praise term and honorific are **values** (`good wolf`, `Alpha`) in an
  **English-keyed** `identity.toml`. **QA signal:** confirm no schema key
  got renamed to a kink term — keys stay English, flavour lives in values.
- Author attribution is **per-repo / per-commit**, derived from the
  committing identity — there is no global app account (D.88). A
  `claude -p` commit Fenrir pushes carries **Fenrir** as author, even
  though it lands in Rem's repo.

## Frustrations (what makes them put an app down)

- Any tool that frames a D/s routine as something to hide or apologise for.
- A calendar an agent can't read/write — the whole point is `claude -p`.
- A "Dom-authored" event that looks like the sub wrote it himself (no
  author chip) — that erases the dynamic.
- A strictly-kept mode that actually **blocks** the sub's own edits, or
  lets the Dom **veto** — that would be coercion, not kept structure.
- A habit tracker that nags or shames a broken streak.
- Schema or labels that leak kink terms into schema **keys** (German or
  English jargon as a column name is equally wrong).
- Any UI that makes Rem feel he can't take his repo back (D.86).

## What success looks like

- **Paired repos:** `rem-kept` and `fenrir-calendar`, each shared to the
  other with **write access**, both on each device's repo picker, no
  read-only chip.
- **Fenrir runs `claude -p`** with his wolf-Dom persona against the cloned
  `rem-kept`, laying out Rem's week — morning cage, Tue/Thu evening
  edging ritual, Friday date night, gym, groceries — entries land as
  one-file-per-entity, he reviews, pushes.
- **Rem's app syncs** and shows the Dom-authored kink + chore events with
  a **foreign-author chip** (Fenrir's persona/initials) — distinct from
  the events Rem adds himself.
- The **common-time finder** sweeps **both repos** and hands back a ranked
  free Friday evening, booked as **date night** in two taps.
- **Rem completes a ritual** (ticks the morning cage / logs the Tue
  edging), which materialises into the **review feed** Fenrir reads on his
  phone — a clean round-trip, no veto, no gate.
- The kept framing (strictly-kept mode, `Alpha`, `good wolf`) is correct
  and tasteful throughout; English schema keys never localise to kink
  terms.
- Rem never doubts he controls his own repo.

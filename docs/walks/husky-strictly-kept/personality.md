# Persona — Koda, a husky sub kept by an AI Dom

> One realistic user persona for replayable QA walks: a single
> consenting adult who runs strictlykeptboy's **strictly-kept, kept-by-AI**
> path — the project's genesis use-case (an AI Dom reads and writes the
> sub's calendar repo by running `claude -p` against it).
>
> **Framing note for QA:** everything here is SFW-but-charged, exactly as
> the app frames its own shipped demo (D.55, K-1..K-7). This is a
> lifestyle-organisation tool for a consenting adult — a Dom-authored
> daily routine, a praise term, an honorific, kept rituals, corrective
> re-authoring after a slip. **No explicit content.** Keep walks warm and
> *structural* (routine / ritual / praise / honorific / deviation /
> correction), never sexual. The kink dynamic is named and verified at
> the level of *which files get written and what copy appears* — this is
> a behaviour-verification QA script, not erotica.

## Snapshot — the sub and his keeper

- **Koda**, 29 — a **husky sub** (furry-coded pet name; not a real
  person). His daily life lives in a strictly-kept repo that an **AI Dom**
  authors and corrects on his behalf. He addresses the Dom as **"Sir"**.
- **The Dom is not a person** — it's an AI persona (`dom_persona` in
  `mode.toml`, one of the shipped builtins per D.85) that reads and writes
  Koda's repo by running `claude -p` against the checkout. Koda granted it
  via the kept-by-AI wizard card (`LifestyleCard.PetKeptByAi`, the default
  card). There is no second human; the "review feed" is consumed by the AI
  Dom on its next pass.
- **Solo:** Koda lives alone. The structure is something he sets up for
  himself and lets the AI Dom hold him to.
- **Location:** a flat in **Leeds, UK**.

## Job

- Koda is a **backend engineer** at a logistics startup. Comfortable in a
  terminal, has SSH keys, reads TOML without flinching, and runs
  `claude -p` against his own repos for other things already — so pointing
  an AI Dom at a calendar repo is a natural extension, not a leap.

## Devices

- **Pixel 7a, Android 16** — his daily driver; the strictlykeptboy app
  lives here and surfaces the notifications + daily briefings.
- **A Linux laptop** where the repo is checked out and where the **AI Dom
  runs**: `claude -p "..."` invoked against the working tree, which reads
  `AGENTS.md` (the one-file-per-entity TOML+Markdown convention) and the
  bridge line pointing at `identity.toml`, then authors entries with the
  `skb` CLI (or by writing files directly) and commits + pushes.

## Tech comfort

Koda is exactly the audience the file-based, AI-native model was built for:

- He **wants** his schedule to be plain files in a private Git repo he
  ultimately controls.
- He is fine with author-attribution chips, `mode.toml`, `identity.toml`,
  and reading a `deviations/<id>/<date>.md` file to see exactly what got
  recorded when he slipped.
- He understands that the AI Dom only has the power he granted it, and that
  **he always retains write access to his own repo** and can drop back to
  `free` mode at any time (D.86). Consent + revocability matter more to him
  than any single feature.

## Why strictlykeptboy over everything else

He tried a habit-tracker app and a shared Google Calendar; neither fit:

1. **Nothing else treats a kept / Dom-scheduled lifestyle as
   first-class.** Everywhere else he had to euphemise his own structure.
   strictlykeptboy ships **strictly-kept mode**, a **kept-by-AI** wizard
   card, and an **`identity.toml`** (praise term, honorific, pronouns,
   tone register) as real, supported features — not a hack.
2. **The AI Dom can actually read and write the schedule.** Because every
   event / task / deviation is one hand-editable file and the repo ships
   an `AGENTS.md`, an AI Dom running `claude -p` against the checkout can
   author a corrective ("add a make-up session tomorrow + tighten the cage
   window") and it lands as real entries that sync to his phone. No API,
   no integration — just files and an agent.
3. **It's file-based + private + his.** A private Git repo Koda owns the
   keys to; the AI Dom acts only inside it; nothing about his dynamic
   lives on a third-party relationship server.
4. **Passive / "inverted" habits fit a kept routine perfectly (D.54).**
   The default is *done by schedule*. Koda doesn't tick a hundred boxes a
   day to "prove" he obeyed; following the routine is the default, and only
   a **deviation** (a miss / partial / late) is an explicit file. No guilt
   loop, no streak-shame — but a real, visible record the AI Dom can act on.
5. **A slip becomes a correction, not a punishment.** When Koda skips an
   evening ritual, the deviation materialises into the **review feed** the
   AI Dom reads on its next pass; the Dom **re-authors** the schedule (a
   make-up task, a firmer window) in *Sir's voice* (driven by
   `identity.toml`), and Koda's tomorrow reflects it. The loop is
   corrective and low-drama (D.85 — the AI Dom never withholds responses
   punitively).

## His repo + identity / mode choices

| Repo | Owner | Remote / auth | Mode | `mode.toml` keeper fields | Identity (`identity.toml`) |
|------|-------|---------------|------|---------------------------|----------------------------|
| **koda-kept** | Koda | GitHub private, **HTTPS (PAT/OAuth)** — SSH gated coming-soon | **strictly-kept** | `dom_persona = "stern-but-fair"`, `dom_cadence = "end-of-day"`, `kept_since = <ISO>`, `write_back_target` = the AI Dom's checkout/branch | alignment **Submissive**; praise **`good boy`**; honorific-for-dom **`Sir`**; pronouns he/him; tone register **firm**; emoji **low** |

Notes that matter for QA:

- **Mode = strictly-kept (D.84).** This does **not** block Koda's own
  edits — they apply immediately, no approval gate, no veto. The only
  delta is that every commit materialises a
  `reviews/<commit-sha>/reviewable_change.md` entry that the AI Dom
  consumes. Deviations show up there too (`PathFamily.Deviation` →
  "logged N deviation(s)").
- **`identity.toml` drives the copy** in notifications + daily briefings:
  praise `good boy`, honorific `Sir`, tone register **firm**. The
  load-bearing QA signal is that this copy actually *reaches a
  notification / briefing* — e.g. "Good boy — Sir's 21:00 cage check is
  due" — proving identity.toml is wired, not just stored.
- **English-only schema keys, kink flavour in the values.** Keys stay
  `praise.term` / `honorific_for_dom.term` / `tone.register`; values are
  `good boy` / `Sir` / `firm`. **QA signal:** confirm no schema key got
  localised or kink-coded — flavour lives only in values + prose.
- **`AGENTS.md` carries exactly one bridge line** pointing at
  `identity.toml` (D.83). It must NOT embed the praise term / honorific.
  The AI Dom reads the bridge line, opens `identity.toml`, and writes in
  Sir's voice from there.

## Frustrations (what makes him put an app down)

- Any tool that frames a D/s routine as something to hide or apologise for.
- A habit tracker that nags, shames a broken streak, or makes "I rested"
  feel like a failure.
- An "AI feature" that can only *read* the calendar and not actually
  *author corrections* into it — a glorified summariser. Koda's whole
  reason for being here is the write-back loop.
- A slip turning into a punitive modal / "streak broken!" drama, or the AI
  Dom going silent as a punishment (a D.85 violation).
- Any UI that makes him feel he *can't* take his repo back or drop to
  `free` — the "transition my mode" affordance must be reachable, not
  buried (D.86).
- Schema or labels that leak kink jargon or another locale into schema
  *keys* (firm-tone values are fine; kink-coded keys are not).

## What success looks like

- **koda-kept** runs in **strictly-kept** mode with `dom_persona` set
  (kept-by-AI), `identity.toml` reading `good boy` / `Sir` / firm.
- A **notification or daily briefing** addresses him in Sir's voice —
  "Good boy — Sir's 21:00 cage check is due" — proving identity.toml
  reaches the copy.
- When Koda **skips** the evening ritual, it is a single
  `deviations/<cal>/<entity>/<date>.md` file with `kind = "skipped"`, the
  recurring rule untouched, no punitive UI — and it materialises into the
  **review feed**.
- The **AI Dom re-authors** the schedule via `claude -p` against the repo:
  a **make-up task tomorrow** + a **tightened cage window**, authored in
  Sir's voice, committed + pushed.
- Koda's **tomorrow** shows the make-up task and the firmer schedule, with
  identity copy intact — the full deviation → review-feed → correction loop
  closes on his phone.
- Koda never doubts he controls his own repo, and the AI Dom never
  punishes by withholding.

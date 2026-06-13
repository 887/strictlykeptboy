# Persona — Glaciermark ("Marc"), a kept snow leopard sub kept by an AI Dom

> One realistic user persona for replayable QA walks: a consenting adult
> who is kept *solo* — there is no human partner with a second phone.
> His keeper is an **AI Dom ("Sir")** that runs `claude -p` against his
> calendar Git repo nightly. Invented neutral kink-character name
> (a snow leopard). Not a real person.
>
> **Framing note for QA:** everything here is SFW-but-charged, exactly as
> the app frames its own shipped surface (D.55, K-1..K-7). This is a
> lifestyle-organisation tool for a consenting adult — a Dom-authored
> daily routine with cage-on/off windows, rituals, praise terms, an
> honorific, mixed in with the mundane (deep-work blocks, errands,
> reading). The keeper assigns several todolist items alongside the
> schedule; completing them is part of being a good kept boy.
> **No explicit content.** Keep walks warm and *structural*, never sexual.
> The kink dynamic is named and verified at the level of *which files get
> written and what copy appears* — this is a behaviour-verification QA
> script, not erotica.

## Snapshot — Marc + Sir

- **Glaciermark ("Marc")**, 32 — a **kept snow leopard sub**. Solo. His
  daily life lives in a strictly-kept repo, `marc-kept`. He carries
  Sir's task assignments alongside his own list, and the difference
  matters to him: he can see at a glance which items are Dom-assigned
  (Sir's author chip) versus self-authored.
- **"Sir"** — an **AI Dom**, not a person. Sir is a `claude -p`
  invocation (D.24/D.85) that Marc runs nightly against `marc-kept` to
  lay out the next day. Sir has its own `identities/<person-id>.md`
  entry so every task Sir writes carries Sir's **author chip**.
- **No second phone.** The keeping is done by an agent Marc runs; the
  "dom side" is a CLI prompt, not a second app install.
- **Location:** lives alone in a mountain-adjacent city; works from home.

## The dynamic, as the app models it

Marc is thorough by nature — he wants his todolist organized. Sir assigns
a handful of todolist items each evening alongside the schedule:
"submit expense report", "read thirty pages", "tidy the desk before
cage-on". These land in `marc-kept` as **one-file-per-entity TOML task
files** authored by Sir, carrying `author = "sir"`. Marc adds his own
tasks on top during the day. At the end of the day, Marc reviews
**Done** to confirm Sir's assigned items were ticked off — the Dom reads
the review feed from what got completed and what didn't.

Marc uses every rail tab. He is the user who reads the section headers,
reorders by list, uses the By Repo view to compare todolist tasks from
two repos he keeps, and notices if a due-date chip shows the wrong day.
He would rather tap three times and be certain than tap once and wonder.

## Jobs

- **Marc** — data analyst / independent consultant. Structured, precise.
  Comfortable in a terminal, reads TOML without flinching. Values
  completeness and traceability.

## Devices

- **Pixel 8a, Android 16** — daily driver. The strictlykeptboy app
  renders his schedule + todolist.
- **A Linux desktop** where the repo is checked out and Sir runs
  `claude -p` against `marc-kept`.

## Tech comfort

High. Marc is exactly the audience the file-based model is for:

- Adds `marc-kept`'s remote over **HTTPS (PAT/OAuth)** — SSH transport
  is gated coming-soon.
- Reads author-attribution chips, `mode.toml`, `identity.toml`,
  `deviations/` files without hand-holding.
- Trusts the model *because* it is files — he can `git revert` any
  commit, read any file, know exactly what was changed and by whom.
- Uses **two repos**: `marc-kept` (personal keeping) and `marc-work`
  (professional task lists). Multi-repo overlay flag is on; per-repo
  dots appear on task rows.

## His repo + identity / mode choices

| Repo | Owner | Mode | Identity |
|------|-------|------|----------|
| **marc-kept** | Marc | **strictly-kept** | alignment Submissive; praise `good boy` (alt: `sweet leopard`); honorific-for-dom `Sir`; pronouns he/him; tone `soft-kinky`; emoji medium |
| **marc-work** | Marc | **free** | — professional context, no lifestyle layer |

Notes for QA:

- `marc-kept` is **strictly-kept** (D.84). Marc's own edits apply
  immediately; each commit materialises a review entry.
- Sir assigns tasks via `claude -p` nightly; they arrive in
  `marc-kept`'s todolist with `author = "sir"` — Marc sees them with a
  Sir-authored chip.
- `marc-work` tasks flow in via multi-repo; they show a distinct repo
  dot. These tasks have no keeping dynamic.
- Marc uses **passive/inverted habits** (D.54) for standing rituals
  (morning stretch, evening cage check-in). These appear in the task
  list's Today section under the "From events" header as standing items
  pinned for today. Their completion model is inverse: deviation is the
  explicit write, not the tick.
- Weekend = release window. Sir's cage-on window does not appear on
  Saturdays and Sundays. Marc completes the remaining Dom-assigned tasks
  on his own initiative anyway, because good boys do.

## Frustrations (what makes him put the app down)

- A todolist view that doesn't tell him where each item came from.
- Checking a task "done" without any confirmation — he wants a
  moment to confirm, especially for Dom-assigned items (he wants to
  feel the completion, not accidentally swipe past it).
- The Done view that doesn't show *which* items were Sir's versus his.
- A Quick Add flow that makes him pick the target list every time with
  no memory of last used.
- The Upcoming tab routing through Combined instead of a real
  due-date-sorted view — he finds items "missing" that are actually
  there, just sorted unexpectedly.
- Any rail label he can't read because the text is clipped.
- Empty state that gives him no clue what to do next.
- By Repo headers showing raw repo IDs instead of friendly names.

## What success looks like

- He pulls up the bottom sheet, sees Today's tasks cleanly split into
  "dated", "from events", and "pinned" sections.
- Sir's author chip is visible on assigned tasks; tapping a task shows
  the full body (a note Sir wrote in `identity.toml` voice).
- The "Mark done?" dialog appears on every checkbox-to-done action;
  he confirms; the item moves to Done with a `doneAt` timestamp.
- Done view is sorted most-recent-first and lets him see "submitted
  expense report — done today" as the top item.
- By Repo shows `marc-kept` and `marc-work` in distinct sections with
  human-readable names, not raw IDs.
- Quick Add pre-fills the last target list so he can type and submit.
- The rail labels (Today / Upcoming / All / By Repo / Done) are
  fully legible, not clipped.

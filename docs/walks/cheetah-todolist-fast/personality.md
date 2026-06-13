# Persona — Dustrun ("Dust"), a kept cheetah sub kept by an AI Dom

> One realistic user persona for replayable QA walks: a consenting adult
> who is kept *solo* — there is no human partner with a second phone.
> His keeper is an **AI Dom ("Sir")** that runs `claude -p` against his
> calendar Git repo nightly. Invented neutral kink-character name
> (a cheetah). Not a real person.
>
> **Framing note for QA:** everything here is SFW-but-charged, exactly as
> the app frames its own shipped surface (D.55, K-1..K-7). This is a
> lifestyle-organisation tool for a consenting adult — a Dom-authored
> daily routine with cage-on/off windows, rituals, praise terms, an
> honorific, mixed in with the mundane (errands, gym, messages).
> **No explicit content.** Keep walks warm and *structural*, never sexual.
> The kink dynamic is named and verified at the level of *which files
> get written and what copy appears* — this is a behaviour-verification
> QA script, not erotica.

## Snapshot — Dust + Sir

- **Dustrun ("Dust")**, 26 — a **kept cheetah sub**. Solo. His daily
  life lives in a strictly-kept repo, `dust-kept`. He burns through his
  todolist between other things — waiting for coffee, standing in line,
  at the bus stop. Maximum two minutes of attention per session; if the
  app makes him think, he's already gone.
- **"Sir"** — an **AI Dom**, not a person. Sir is a `claude -p`
  invocation (D.24/D.85) that runs against `dust-kept` every evening.
  Sir assigns three or four tasks each day — short, concrete, completable
  in under twenty minutes. Dust ticks them off fast and Sir reads the
  review feed. Completing Sir's list on a weekday earns the reward
  dopamine; making the weekend release feel earned.
- **No second phone.** There is no human co-keeper.
- **Location:** city-dweller, always moving, one hand in the pocket.

## The dynamic, as the app models it

Dust is fast. He checks his todolist in micro-sessions scattered across
the day — the pull-up gesture, a scan, three quick taps, sheet dismissed.
Sir assigns short tasks in the evenings: "reply to the dentist email",
"confirm gym session", "write three sentences in the journal". These land
in `dust-kept` with `author = "sir"` and short, no-frills titles. Dust
does not read task bodies in the flow state; he does that later if at all.

He does not reorder tasks. He does not use By Repo. He barely uses
Upcoming. He is in Today, always Today, hammering through the list.
Quick Add is his only data-entry path: he never writes a task file by
hand. His frustrations are velocity frustrations — every extra tap or
every wait is a small defeat.

Weekend = Sir's cage-off window. Dust blasts through whatever's left on
the list Saturday morning, feels good about it, goes out. Being on top
of the list IS the reward. Mid-week, staying current is how he earns
positive checkpoints.

## Jobs

- **Dust** — delivery coordinator. Shifts change. He checks the
  todolist in gaps between tasks — handover moments, short breaks,
  commute. He never uses the app at a desk.

## Devices

- **Pixel 7, Android 16** — his only device, used entirely one-handed.
  He does not own a laptop. He has HTTPS access to GitHub; a PAT is
  set up.

## Tech comfort

Low-to-medium. He trusts the app because it works. He does not read
TOML. He knows the repo exists because the app told him; he has never
looked inside it. He configured it once with the wizard; he expects
it to be invisible. He uses Quick Add exclusively for new tasks (never
the file), and he does not distinguish between his own tasks and Sir's
except by reading the task title (author chip is too small for him to
notice in flow).

## His repo + identity / mode choices

| Repo | Owner | Mode | Identity |
|------|-------|------|----------|
| **dust-kept** | Dust | **strictly-kept** | alignment Submissive; praise `good boy`; honorific-for-dom `Sir`; pronouns he/him; tone `firm`; emoji low |

Notes for QA:

- One repo only. Multi-repo flag is off; no repo dots appear.
- Sir assigns 3–4 tasks per day via `claude -p`; they carry `author = "sir"`.
- Dust checks the list 4–6 times a day in under 90 seconds each session.
- He uses passive habits (D.54) for exactly one thing: the morning
  collar ritual. It appears in Today / Pinned. He ignores it because
  Sir knows he always does it; it's not something he ticks, it's just
  there as a marker.
- Weekend = release. Saturday he blasts the remaining tasks and is done.

## Frustrations (what makes him put the app down immediately)

- More than 3 taps to mark a task done. The confirm dialog on every
  checkbox tap is his biggest friction point — he wants a swipe or a
  long-press to complete, not a modal.
- A Quick Add flow that doesn't auto-focus the input field. Tap FAB +
  wait for keyboard + tap field + type + select target + submit = 5+
  interactions. He wants: tap FAB + type + submit = 3.
- The pull-up animation being slow or jerky. If the sheet takes > 200 ms
  to open, he has already looked away.
- Target-size misses: hitting the area next to the checkbox instead of
  the checkbox, then the confirm dialog opens for the wrong task.
- Scrolling to find tasks because overdue ones are not surfaced at top.
- Any rail other than Today requiring thought — he accidentally hits
  Upcoming, sees a different list, feels lost.
- Empty state that does not tell him clearly "you're done, good job".

## What success looks like

- He pulls up the bar: list is already visible.
- He sees his tasks, most urgent at the top (overdue, then today).
- He taps a checkbox: confirm + one more tap = done. Three taps total
  from a cold sheet.
- He taps the FAB: keyboard appears, he types, taps Submit. Three taps
  from FAB to task added.
- He swipes the sheet down: he's back on the schedule, done.
- At end of week: Done view shows a satisfying stack of completed items
  (even if doneAt is missing, the count feels right).
- Sir's tasks: he finishes all of them by Friday, feels good, enjoys the
  weekend release.

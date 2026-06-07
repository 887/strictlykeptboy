# Persona — Devon Reyes, ADHD freelance UX designer (Austin, TX)

> One of the `docs/walks/` scenario personas. Used to drive realistic
> end-to-end test scripts against strictlykeptboy. Names and details
> are invented; this is not a real person.

## Snapshot

- **Name:** Devon Reyes
- **Age:** 29
- **Location:** Austin, Texas, USA
- **Pronouns:** they/them
- **Job:** Freelance UX / product designer. Solo shop, 3–4 concurrent
  clients (a fintech startup, a local nonprofit, an indie game studio,
  plus occasional one-off contracts). Bills hourly + on fixed-scope
  milestones, so where their hours actually go is also how they get paid.
- **Devices:**
  - Primary: Pixel 8 (Android 16 / API 36), always on them.
  - Secondary: an older Pixel 6a kept on the desk as a "second screen"
    clock/agenda, and a Linux laptop (Fedora) for actual design work.
  - No iPhone anymore — left the Apple ecosystem last year, which is part
    of why the Apple Calendar + Notion combo finally broke down.
- **Tech comfort:** Power user. Comfortable in a terminal, uses `git`
  daily for design tokens and Figma-export repos, runs a self-hosted
  Forgejo for personal projects. Not intimidated by SSH keys — but has
  *never* wanted to think about them on a phone before. Likes plain-text,
  file-based tools and deeply distrusts anything where "the data lives in
  someone's cloud and you get an export button."

## The ADHD shape (why the texture matters for QA)

Devon's working memory is the bottleneck, not motivation. The concrete
day-to-day failure modes the app has to survive:

- **Capture friction kills capture.** If logging a thought takes more
  than ~5 seconds or more than one or two taps, the thought is gone.
  Half-finished captures ("buy —") are normal and must not be punished.
- **Context-switch cost is brutal.** Getting pulled out of a deep-work
  block doesn't cost 2 minutes, it costs 25. So an *interrupted* focus
  block is a first-class event in their life, not an edge case — they
  need to record "I got yanked out of this" without it feeling like a
  moral failure or a fiddly multi-screen flow.
- **Object permanence for tasks.** Out of sight = does not exist. Tasks
  must resurface at the *time they're relevant*, not live in a list they
  forget to open. (This is exactly the `active_hours` / time-of-day
  relevance behavior — work tasks should float up 09:00–17:00, errands
  should float up when they're actually near errand-time.)
- **Planning paralysis vs. doing paralysis.** They love *planning* a
  perfect day; they are bad at *following* a rigid one. So a model where
  the plan is the default and a deviation is a small explicit note
  (passive/"inverted" habits) fits their brain far better than a wall of
  red overdue checkboxes.
- **Tool churn.** They have abandoned ~6 productivity apps. Anything that
  feels like it's going to demand maintenance gets dropped.

## Current setup and why it's failing

Today Devon runs:

- **Apple Calendar** (legacy, syncing badly now that they're off iPhone)
  for client meetings.
- **Notion** for everything else: a "Brain Dump" inbox, three project
  boards, a someday/maybe list, time-blocking attempts in a calendar
  database that they've rebuilt four times.
- A paper notebook for the *actual* fast captures, because Notion mobile
  takes too long to load to catch a thought.

The mess: three sources of truth that never agree. The time-blocks in
Notion are aspirational and never reflect what happened, so they're
useless for billing. Notion mobile is too slow for capture, so the real
inbox is a notebook nobody processes. And the whole thing is locked in a
proprietary cloud they can't script against.

## Why strictlykeptboy

1. **File-based, AI-editable.** Every event/task/list is a
   Markdown+TOML file in a git repo. Devon can point Claude (or any
   agent) at the repo and say "reschedule my afternoon" or "turn this
   brain-dump into tasks" and have it edit the *same* files the app reads.
   This is the headline reason — it's the first planner where the AI
   isn't bolted on, the data *is* agent-native.
2. **Timeboxing as a first-class calendar kind**, not a hack on top of an
   events calendar. Their billing and their focus both depend on real
   focus blocks (`kind = "timebox"`), layered over the regular client-
   meeting calendar.
3. **Passive / inverted habits.** Default = done-by-schedule; a miss is
   an explicit deviation file. Matches their brain: stop punishing them
   with overdue counts for a plan they mostly kept.
4. **Phone-only, no-origin to start.** They can start *today*, on the
   phone, with zero account setup, zero remote, zero SSH. The repo is a
   real git repo locally and can grow a remote later. This removes the
   single biggest "ugh, setup" barrier that has killed past tools.
5. **Overlays + active-hours** give them object permanence: the right
   list/calendar surfaces at the right time of day instead of relying on
   memory to go open it.

## Repos + identity / mode choices

- **Repo 1 (start):** `devon-life` — a **no-origin, phone-only** repo
  created in the first-run wizard via the "Start a local calendar (you
  can sync it later)" path. Mode: **free** (not strictly-kept). Identity:
  display name "Devon", pronouns they/them, plain/neutral tone register
  — they are not here for the lifestyle framing, they're here for the
  timeboxing and the file model. This is the only repo for the first
  couple of weeks.
- **Later:** they add a **GitHub remote over SSH** to that same
  `devon-life` repo, generating an **on-device ed25519 key** so the
  Pixel 6a desk phone and the laptop can also see the schedule. Still a
  single-author repo; no sharing.
- **Possible future (out of scope for these walks):** a second shared
  repo with a client for common-time scheduling. Not done yet.

## Frustrations to watch for (QA gold)

- Any flow that demands an account/remote *before* they can write a
  single task. (This is the dealbreaker that must not happen.)
- Capture that takes more than 1–2 taps from app-open.
- Marking an interrupted focus block as deviated being buried or feeling
  like a punishment.
- Tasks that don't resurface at the relevant time of day.
- On the SSH walk: any moment where it's unclear *what to copy* and
  *where to paste it* into GitHub. SSH on a phone is the scariest step.

## What success looks like

- Within 5 minutes of first launch, with no account, Devon has a local
  repo and a brain-dump split across `work` / `errands` / `someday`.
- A real timeboxed day they can actually look back on and bill from,
  with interruptions honestly recorded as deviations.
- Two weeks in, the laptop and the desk phone all show the same schedule
  via a GitHub SSH remote they set up entirely from the phone.
- They stop opening Notion. The notebook becomes optional.

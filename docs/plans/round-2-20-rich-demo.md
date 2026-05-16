# Round 2.20 — Rich demo data: a fully-kept life

## Context

The Round 2.15 demo seeder (`app/src/main/java/com/eight87/strictlykeptboy/demo/DemoRepoSeeder.kt`) wires up six thin per-perspective repos — each a `WizardScaffolder.materialize()` of a `LifestyleCard` preset with identity / mode / one or two starter calendars baked in. That's plenty to demo *the wizard*, but it's not enough to demo *the app*: a new user opening the demo today sees an almost-empty schedule, no overlays interacting, no supersedence, no inverted habits firing, no markdown bodies of any depth, no todolists populated, no AI-dom presence on the timeline. The value-proposition of "AI-keep my whole life" doesn't survive a 30-second tour, because there's no life on screen.

This round authors **one** demo repo — single coherent perspective, 14 calendar days deep, ~120+ events across ~11 calendars, 5 populated todolists, supersedence overlays, inverted habits, AI-dom-authored check-ins, and a Sunday-meets-daddy heartbeat — and bundles it inside the APK so first-launch reveals an alive, opinionated, kept life immediately.

### User intent (verbatim)

> "the next plan should probably be starting with amazing demo data. i want a full calendar planned out boy - multiple overlays. you doing your 9-5 which, with commuting, brushing your teeth, when you should be in bed, going to the gym, kinky activities like putting on your cage, things scheduled in by the AI dom, a few filled todo lists. your job should be a junior software developer role so schedule in one meetings and sprints. also you should own a cat you need to feet and have at least 1 meetups with your daddy/owner each week. however you should also be reminded to text them and keep an activity log or update your todolist here so your owner can comment on it. if you're owned by an AI dom they should schedule you to go sozialize(AI shouldn't make us lonely) but also make us kinky. but most importantly they should schedule our life to be functional. like we all have work, eat, sleep, dress ourselfes and so on. also vaccations (put a convention in there but also a local trip), some public holidays, some family visits.. this plan is neext"
>
> "i need this demo data idealy build into the app in a subfolder like if it was a real repo already with a demo claude md and so on so we can test it and see if everything works as intended. we will ship it app-internally though."

### What "good demo data" means here

A friend installs the build the user hands them, walks the onboarding once, lands in the schedule view, and *without a word of explanation* sees: morning routine stacked on top of work-hours which has a sprint planning meeting which has the AI-dom-overlay nudging "send selfie 15:00" which has a gym block after which has the cat-feed at 20:30 which has the owner-report at 21:00 — and a week-2 vacation band visibly *pausing* the routine calendars without deleting them, while medication-equivalent rituals (cage stays on) survive the pause. They tap into the owner-activity-log todolist and see three days of believable entries. The app sells itself.

## Locked design decisions

- **D-2.20.a — Single rich perspective, not five sparse ones.** The Round 2.15 picker can keep its six thin demos for users who want to compare alignments; this rich demo is additive, the new default, and exists to show off the *whole feature surface* coherently. Five sparse perspectives spread effort too thin to ever land a believable life; one perspective at this depth costs the same as five at 1/5 the depth and is dramatically more legible.
- **D-2.20.b — Bundled in `app/src/main/assets/rich-demo-repo/` as raw file tree.** Mirrors the on-disk repo layout (`calendars/<id>/events/<yyyy>/<mm>/<event-id>.md`, etc.) so the seeder is a `copyRecursively`, not a generator. Authoring is just writing files; reviewing is just reading them; updating is just editing them. No code-as-config drift.
- **D-2.20.c — Seeded once at first launch, idempotent.** A `RichDemoSeeder` checks a `pref_rich_demo_seeded` flag, copies assets to `filesDir/demo-repos/rich-demo/` if absent, no-ops otherwise. User can "Reset demo" from a settings hook (out of scope here) to wipe + re-seed.
- **D-2.20.d — Alignment baseline: gay-male-sub kept by AI dom, junior software developer role.** Per user's explicit ask. Praise vocab + cage rituals + AI-dom overlay are first-class content; this is the perspective skb is *for*. Other alignments remain reachable via Round 2.15's picker.
- **D-2.20.e — 14-day window anchored to a constant seed-date `2026-05-15` (Friday).** Reproducible across seeds; the German public holiday Christi Himmelfahrt fell on Thu 2026-05-14 (day -1, intentionally rendered as a recent event for context), Pfingstmontag (Whit Monday) falls on Mon 2026-05-25 (in-window day 10). The convention vacation lands days 12–15 (Wed 27 → Sat 30); the local weekend trip lands days 6–7 (Wed 21 — no, recompute: Wed 20 → Thu 21 → reanchor to Sat 23–Sun 24, days 8–9). Event filenames embed real ISO dates so file paths are stable from build to build. The seeder does NOT rebase dates to "today at seed time" — D-2.20.e prefers reproducibility over freshness, with a follow-up plan slot to add a date-rebase mode later if needed.
- **D-2.20.f — ~11 calendars.** `work`, `commute`, `routines`, `gym`, `kinky-rituals`, `dom-overlay`, `social`, `cat-care`, `holidays`, `vacation`, `family-visits`. Each justified by a distinct slice of the user's day; merging any two would either bury content (work+commute makes the rail unreadable) or break the supersedence story (vacation MUST be its own calendar to supersede the others).
- **D-2.20.g — 5 todolists.** `daily-rituals`, `work-sprint-25`, `groceries`, `cat-care`, `owner-activity-log`. Each demonstrates a distinct todolist pattern: standing recurring sub-stepped checklist (daily-rituals); sprint-board with mixed states (work-sprint-25); flat shopping list (groceries); mixed dated + standing care tasks (cat-care); append-only diary the dom reads (owner-activity-log).
- **D-2.20.h — Each calendar has at least one routine (recurrence) and, where relevant, participates in supersedence (as target or as superseder).** No empty calendars; no calendar that exists "just to fill a slot". If a calendar can't justify both a recurrence and at least three concrete events in 14 days, it merges into a sibling.
- **D-2.20.i — `owner-activity-log` is the canonical user-writes-dom-reads channel.** Sub-step: every day's checklist includes "write tomorrow's log entry before bed". Pre-seeded with three days of believable retrospective entries (cage-on time, water intake, mood, kink-readiness, work mood, what the AI dom commented in reply) so users see how a fully-loaded activity log looks. Markdown body uses headings + bullet lists + blockquotes (the dom-reply rendered as a blockquote) — exercises Phase EE Markwon end-to-end.
- **D-2.20.j — App-internal only.** Lives in `filesDir/`, not user-data, never appears in the Push/Pull UI, never exported via Settings → Export, never pushed to GitHub Releases. `repo.toml` carries `kind = "demo"` and the repo-list row shows it under a "Demo" divider with a subtle pill, not mixed with real repos.
- **D-2.20.l — UK setting + AI dom name "Boy Keeper" (user override 2026-05-16; supersedes any German references + "Cassiel" naming in earlier plan text).** Authored Phase A content uses UK locale (London-ish city, Spring bank holiday Mon 2026-05-25, tube/overground commute, Brighton weekend as local trip, DevConf 2026 — Berlin as the convention trip), English-only body copy, metric units (km, kg, °C), and the AI dom's display name is **"Boy Keeper"** (casual "BK" / "Keeper" / addressed as "Sir") everywhere — in `identities/ai-dom.md`, `identity.toml`, the dom-overlay calendar name + every dom-overlay event body, every blockquoted reply in `owner-activity-log`, and the README/AGENTS.md.
- **D-2.20.k — The rich demo repo ships its own `AGENTS.md` + `CLAUDE.md` symlink.** Per D.83 conventions, AGENTS.md describes schema + the bridge line to identity.toml; the body explicitly tells any Claude session working on this repo "this is the demo — content is fictional, identity is locked, do not edit identity.toml". CLAUDE.md is a symlink to AGENTS.md (round-tripped via `java.nio.file.Files.createSymbolicLink`, per the CLAUDE.md note on symlink correctness). Symlink-in-APK doesn't exist (APK assets are flat files); seeder creates the symlink on extraction.

## Phase order

- **Phase A — Demo content authoring.** Write every `.md` and `.toml` file under `app/src/main/assets/rich-demo-repo/`. The meat of the plan; ~50 sub-steps because each calendar + each todolist + each meta file is its own concrete deliverable.
- **Phase B — Seeder code.** `RichDemoSeeder.kt` + asset extraction + idempotency flag + symlink reconstruction.
- **Phase C — Picker integration.** New perspective row in the Round 2.15 demo picker; default-selected on first run.
- **Phase D — Tests + AVD smoke.** Unit-test the seeder, parsability, overlay resolution; AVD-walk the demo.
- **Phase E — Plan close-out.** Tick, status DONE, update `main.md` index.

## Phase A — Demo content authoring — shipped in 7fc80ef

Target: ~50 sub-steps; every sub-step yields one or a small bundle of concrete files at concrete paths under `app/src/main/assets/rich-demo-repo/`.

### A.1–A.6 Repo meta

- [x] **A.1** Write `app/src/main/assets/rich-demo-repo/AGENTS.md` — schema description per D.83 conventions, bridge line to `identity.toml`, demo-mode disclaimer ("this is fictional content, identity is locked").
- [x] **A.2** Write `app/src/main/assets/rich-demo-repo/.claudemd-is-symlink` marker (zero-byte file) — sentinel the seeder reads to know to `Files.createSymbolicLink` `CLAUDE.md → AGENTS.md` at extraction time (APK assets can't carry real symlinks).
- [x] **A.3** Write `app/src/main/assets/rich-demo-repo/README.md` — human-facing description; explains this is the demo repo, lists the calendars + todolists, gives a one-line tour.
- [x] **A.4** Write `app/src/main/assets/rich-demo-repo/.strictlykeptboy/schema.toml` (schema_version, app_min_version, last_writer = "demo-seeder").
- [x] **A.5** Write `app/src/main/assets/rich-demo-repo/.strictlykeptboy/repo.toml` — `displayName = "Demo Life"`, `kind = "demo"`, `defaultCalendarId = "routines"`, `defaultTodolistId = "daily-rituals"`, color seed.
- [x] **A.6** Write `app/src/main/assets/rich-demo-repo/identity.toml` (praise terms `["good boy", "sweet thing"]`, pronouns `he/him`, honorific `Sir` for the AI dom, tone `soft-kinky`, emoji density `medium`, alignment `gay-male-sub`, lifestyle `pet-kept-by-ai`) and `mode.toml` (`repo_default = "strictly-kept"`).
- [x] **A.7** Write `app/src/main/assets/rich-demo-repo/identities/demo-boy.md` (display name "Demo Boy", default-author flag true) and `identities/ai-dom.md` (display name "Cassiel", marked as AI-dom-author per D.83 conventions).

### A.8–A.13 `work` calendar (Cubicle 14, junior software developer)

- [x] **A.8** Write `calendars/work/calendar.toml` — displayName "Work — Cubicle 14", priority 600, color, `active_hours = [{day=mon..fri, from=09:00, to=17:00}]`, `baseline_cadence` set so off-hours work events get flagged.
- [x] **A.9** Write `calendars/work/recurrences/daily-standup.md` (RRULE: FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR at 09:00 for 15min, dtstart 2026-05-15).
- [x] **A.10** Write `calendars/work/recurrences/lunch-break.md` (daily 12:00–13:00 weekdays) and `calendars/work/recurrences/sprint-planning.md` (every other Tuesday 10:00–11:00 starting 2026-05-19) and `calendars/work/recurrences/sprint-retro.md` (every other Thursday 14:00–15:00) and `calendars/work/recurrences/sprint-demo.md` (every other Friday 15:00–16:00).
- [x] **A.11** Write 6–8 one-off `calendars/work/events/2026/05/<uuid7>.md` events spanning the window: pair-programming session, design-review with senior, 1:1 with manager, deploy-rehearsal, on-call shadow, all-hands. Bodies are believable junior-dev markdown notes (links, code blocks, action items).
- [x] **A.12** Write `calendars/work/exceptions/daily-standup/2026-05-25.md` with `kind = "cancel"` (Whit Monday — public holiday, no standup).
- [x] **A.13** Write `calendars/work/exceptions/lunch-break/2026-05-22.md` with `kind = "override"` body "team lunch — Vietnamese place" (one-off variant exercises the override path).

### A.14–A.16 `commute` calendar

- [x] **A.14** Write `calendars/commute/calendar.toml` (displayName "Commute", priority 400, color, no active_hours — overlay everywhere weekdays).
- [x] **A.15** Write `calendars/commute/recurrences/commute-in.md` (Mon–Fri 08:00–08:50, body "S-Bahn → office") and `calendars/commute/recurrences/commute-out.md` (Mon–Fri 17:30–18:20).
- [x] **A.16** Write 2 one-off `calendars/commute/events/2026/05/*.md` for a delayed-train deviation note and a "biked in today" override.

### A.17–A.20 `routines` calendar

- [x] **A.17** Write `calendars/routines/calendar.toml` (priority 500, no active_hours).
- [x] **A.18** Write `calendars/routines/recurrences/morning-alarm.md` (daily 06:30), `morning-cage-check.md` (daily 06:35, 5min), `morning-brush-teeth.md` (daily 06:40, 5min), `morning-dressing.md` (daily 06:50, 10min), `morning-breakfast.md` (daily 07:00, 20min).
- [x] **A.19** Write `calendars/routines/recurrences/evening-dinner.md` (daily 20:00), `evening-brush-teeth.md` (daily 23:00), `evening-skincare.md` (daily 23:10), `evening-windown.md` (daily 23:15), `lights-out.md` (daily 23:30, inverted habit — see A.46).
- [x] **A.20** Write `calendars/routines/recurrences/laundry.md` (Sun 11:00) and `cleaning.md` (Sat 09:30) and `meal-prep.md` (Sun 16:00, 90min).

### A.21–A.23 `gym` calendar

- [x] **A.21** Write `calendars/gym/calendar.toml` (priority 450).
- [x] **A.22** Write `calendars/gym/recurrences/strength-mon.md` (Mon 18:30–20:00), `strength-wed.md` (Wed 18:30–20:00), `strength-fri.md` (Fri 18:30–20:00 — body holds the lifting plan as a markdown table of sets × reps × weight).
- [x] **A.23** Write `calendars/gym/recurrences/saturday-cardio.md` (Sat 10:00–11:00) and 1 one-off "leg day buddy session" event.

### A.24–A.27 `kinky-rituals` calendar (nonSuperseable)

- [x] **A.24** Write `calendars/kinky-rituals/calendar.toml` (priority 700, `nonSuperseable = true` per D.76 — cage rituals survive vacation; tone-soft kinky vocabulary in displayName "Rituals").
- [x] **A.25** Write `calendars/kinky-rituals/recurrences/cage-on.md` (daily 06:35, inverted habit, body explains the ritual + dom's expectation), `cage-off-check.md` (daily 21:00 — confirms cage stayed on; deviation if off).
- [x] **A.26** Write `calendars/kinky-rituals/recurrences/plug-evening.md` (Tue + Thu 21:30–23:00), `edging-session.md` (Wed 22:00–22:45, dom-scheduled), `kink-journal.md` (Sun 18:00, 30min).
- [x] **A.27** Write 1 one-off `events/2026/05/<uuid>.md` "Saturday scene — collar + cage all night" (day 9, Sat 2026-05-23, 20:00).

### A.28–A.30 `dom-overlay` calendar (the AI dom's voice on the schedule)

- [x] **A.28** Write `calendars/dom-overlay/calendar.toml` (priority 800 — overlays *on top of* everything else, author all events as `ai-dom`, displayName "Cassiel ♥").
- [x] **A.29** Write `recurrences/good-morning-text.md` (daily 07:15 — "text owner good morning"), `lunch-check-in.md` (weekdays 12:15 — "ate yet, boy?"), `afternoon-selfie.md` (weekdays 15:00 — "send selfie"), `evening-report.md` (daily 21:00 — "report check-in: cage status, mood, day-rating").
- [x] **A.30** Write 4 one-off dom-overlay events with rich bodies: a Fri-night socialization mandate ("you're meeting friends at the bar tonight — no excuses, here's the pre-game pep talk"), a mid-week kink scene scheduling note, a praise note ("good boy yesterday — you got everything done"), and a corrective note ("missed lights-out twice this week — earlier wind-down starting tomorrow"). Bodies showcase blockquotes (the dom's voice), bold ("**this is non-negotiable**"), and inline emoji.

### A.31–A.33 `social` calendar

- [x] **A.31** Write `calendars/social/calendar.toml` (priority 500, color).
- [x] **A.32** Write `recurrences/text-daddy-morning.md` (daily 07:30 — quick "good morning Daddy" nudge, separate from the dom-overlay's good-morning), `weekly-daddy-meetup.md` (Sun 11:00–14:00 — brunch with Daddy/owner), `family-call.md` (Sun 19:00 — weekly call with mom).
- [x] **A.33** Write 3 one-off events: "Friday drinks at Roses" (day 1, Fri 2026-05-15, 19:00, ties to the dom-overlay mandate), "coffee with Lukas" (day 4, Mon 2026-05-18, 17:30), "queer book club" (day 8, Fri 2026-05-22, 19:30).

### A.34–A.36 `cat-care` calendar (nonSuperseable)

- [x] **A.34** Write `calendars/cat-care/calendar.toml` (priority 750, `nonSuperseable = true` — cat eats even during vacation, body has a "cat-sitter handover" override note).
- [x] **A.35** Write `recurrences/feed-morning.md` (daily 07:20), `feed-evening.md` (daily 20:30), `litter-sun.md` (Sun 11:30), `litter-wed.md` (Wed 19:00), `flea-treatment.md` (first-of-month 09:00).
- [x] **A.36** Write 2 one-off events: "vet appointment — annual checkup" (day 11, Tue 2026-05-26, 16:00, body has prep checklist + the cat's name "Wasabi") and "cat-sitter handover" (day 12, Wed 2026-05-27, 08:00).

### A.37–A.38 `holidays` calendar

- [x] **A.37** Write `calendars/holidays/calendar.toml` (priority 950, `nonSuperseable = true` — holidays are facts, vacations don't hide them).
- [x] **A.38** Write `events/2026/05/pfingstmontag.md` (Mon 2026-05-25, all-day, German Whit Monday — body explains the holiday) and a recent context-event `events/2026/05/christi-himmelfahrt.md` (Thu 2026-05-14, all-day, day -1 — for context only; demonstrates the all-day banner).

### A.39–A.42 `vacation` calendar (the supersedence demo)

- [x] **A.39** Write `calendars/vacation/calendar.toml` (priority 900, `supersedes = ["work", "commute", "gym", "dom-overlay", "social"]` per D.75–D.78 — pauses those calendars during vacation windows; KEEPS `kinky-rituals`, `cat-care`, `holidays`, `routines/morning-cage-check`, `routines/evening-cage-check` by NOT listing them).
- [x] **A.40** Write `events/2026/05/convention-trip.md` (days 12–15, Wed 2026-05-27 → Sat 2026-05-30 all-day, body explains "DevConf Berlin", agenda links as markdown, packing-list reference).
- [x] **A.41** Write `events/2026/05/local-weekend-trip.md` (days 8–9, Sat 2026-05-23 → Sun 2026-05-24 — though weekend already pauses work, this still supersedes `social/weekly-daddy-meetup` and brings explicit "out of town" semantics; body holds the destination "Hiddensee weekend").
- [x] **A.42** Write `calendars/social/overrides/vacation/weekly-daddy-meetup/2026-05-31.md` with `kind = "force-show"` — the Sunday AFTER the convention, daddy meetup explicitly re-surfaces (demonstrates the override mechanism per D.77).

### A.43–A.45 `family-visits` calendar

- [x] **A.43** Write `calendars/family-visits/calendar.toml` (priority 550).
- [x] **A.44** Write `events/2026/05/moms-sunday-brunch.md` (Sun 2026-05-17, day 3, 12:00–14:30) and `dads-birthday-dinner.md` (Sat 2026-05-23, day 9, 18:00–21:00 — collides with the local trip on purpose, body says "drove back in for the evening").
- [x] **A.45** Write `events/2026/05/grandmas-anniversary.md` (Sun 2026-05-24, day 10, 15:00, all-afternoon — markdown body has heading + photo placeholder + bullet list of "people to greet").

### A.46–A.48 Inverted habits (D.54) — at least 5

- [x] **A.46** Write `calendars/routines/recurrences/no-phone-after-22.md` (inverted habit, daily 22:00 — default state completed-by-schedule; deviation file format documented in body). Write one deviation: `calendars/routines/deviations/no-phone-after-22/2026-05-14.md` ("scrolled until 22:40 — owner reminded me").
- [x] **A.47** Write `recurrences/no-coffee-after-14.md` (inverted habit, daily 14:00), `cage-stays-on.md` (inverted habit, daily 23:30 — kinky-rituals folder), `bedtime-by-2330.md` (inverted habit, daily 23:30 — routines folder), `no-skipping-breakfast.md` (inverted habit, daily 07:00).
- [x] **A.48** Write 3 more deviation files spread across the window to show the deviation-history UI populated: `kinky-rituals/deviations/cage-stays-on/2026-05-16.md` ("cage off 14:00–14:30 — shower"), `routines/deviations/bedtime-by-2330/2026-05-19.md` ("up until 01:00 — sprint emergency"), `routines/deviations/no-skipping-breakfast/2026-05-20.md` ("ran out the door").

### A.49–A.55 Todolists

- [x] **A.49** Write `todolists/daily-rituals/todolist.toml` (displayName "Daily rituals", priority 600).
- [x] **A.50** Write `todolists/daily-rituals/recurrences/morning-checklist.md` (daily recurring task with sub-steps: cage check, teeth, vitamins, breakfast, cat fed, dressed) and `evening-checklist.md` (daily: report to owner, journal, lights-out, tomorrow's log entry written).
- [x] **A.51** Write `todolists/work-sprint-25/todolist.toml` (displayName "Sprint 25 board", priority 700) + 7 task files in `tasks/2026/05/`: 2× "in progress" (auth bug, profile-screen accessibility), 2× "needs review" (PR #142 settings refactor, PR #138 onboarding copy), 1× "blocked" (waiting on DevOps for staging DB), 2× "todo" (write unit tests for resolver, document new RRULE handling). Bodies use markdown headings + bullet acceptance criteria + a sprint-board ASCII table in one task to exercise Markwon table rendering (Phase EE).
- [x] **A.52** Write `todolists/groceries/todolist.toml` + `tasks/2026/05/<uuid>.md` flat shopping list of 12 items (some checked, some not) — single task with markdown checkbox sub-list, body demonstrates inline checkbox rendering.
- [x] **A.53** Write `todolists/cat-care/todolist.toml` + 4 tasks: "Refill kibble (Wasabi's brand)", "Book vet annual" (dated, completed retroactively), "Order flea treatment", "Brush Wasabi 3× this week" (recurring sub-stepped).
- [x] **A.54** Write `todolists/owner-activity-log/todolist.toml` (displayName "Activity log for Sir") + 3 pre-seeded daily entries (`tasks/2026/05/2026-05-12.md`, `2026-05-13.md`, `2026-05-14.md`) — each body has headings (Sleep, Cage, Food, Mood, Kink-readiness, Work, Reflection), bullet bodies, and a final blockquote rendering the AI dom's reaction ("> good boy. cage timing was tight. tomorrow earlier wind-down."). The 4th entry (`2026-05-15.md` — seed-anchor day) is empty with placeholder headings — the evening-checklist task points at it.
- [x] **A.55** Write `todolists/owner-activity-log/recurrences/daily-entry.md` — recurring task "write today's activity log" (daily 21:30), body explains the format the dom expects.

### A.56–A.58 Cross-cutting polish

- [x] **A.56** Add 4 attachments-by-reference (no real binaries) — markdown body images referencing `attachments/<sha-prefix>/<sha>.png` paths that don't exist; per CLAUDE.md "today the syntax round-trips through Markwon as a text-only fallback when the attachment isn't resolvable" — this is the deliberate test case for that fallback.
- [x] **A.57** Add `reviews/<sample-commit-sha>/reviewable_change.md` (one sample per D.84 — a strictly-kept-mode review entry the dom can react to; body has a "locked / collar / good-boy / paw / heart / fire / thumbsup / 🦇 / smirk" legend so users see the reaction surface even before Phase YY ships fully).
- [x] **A.58** Walk the whole asset tree, verify every event/task file parses against `docs/plans/data-model.md` schemas (frontmatter fences correct, UUIDv7 IDs, RRULEs valid per RFC5545, no dangling `supersedes` references, no two events with the same UUID).

## Phase B — Seeder (shipped in 6adfeb6)

- [x] **B.1** Added `RichDemoSeeder.kt` at `app/src/main/java/com/eight87/strictlykeptboy/demo/RichDemoSeeder.kt` — `suspend fun seedIfNeeded(parentDir: File): Result<File>` extracts the bundled asset tree under `<parentDir>/rich-demo/`. Surface also exposes `isSeeded()` + `resetSeededFlag()` for downstream reset hooks. All I/O on `Dispatchers.IO`.
- [x] **B.2** Asset enumeration via `_manifest.txt` (one POSIX relative path per line). `Files.copy(AssetManager.open(...), target, REPLACE_EXISTING)` per entry; intermediate dirs created with `Files.createDirectories`. **Note:** AGP's default `androidResources.ignoreAssetsPattern` strips dot-prefixed paths from APK assets; relaxed the pattern in `app/build.gradle.kts` so `.strictlykeptboy/repo.toml` survives packaging. The symlink-marker file was renamed from `.claudemd-is-symlink` → `claudemd-is-symlink.marker` to dodge a separate aapt2 quirk on individual dotfiles.
- [x] **B.3** Symlink reconstruction in `reconstructSymlink()` — on marker presence, deletes the marker, then `Files.createSymbolicLink(repoRoot/CLAUDE.md, repoRoot/AGENTS.md)`. Falls back to copying AGENTS.md → CLAUDE.md on `UnsupportedOperationException` / `FileSystemException` / `IOException` so emulator FAT mounts still produce a readable CLAUDE.md.
- [x] **B.4** Idempotency via `pref_rich_demo_seeded_v1` SharedPreferences boolean (versioned — bump suffix to force re-seed). `resetSeededFlag()` clears it.
- [x] **B.5** Integration with `RepoStore` left to **Phase C** — Phase B intentionally exposes the seeder only via `AppGraph.richDemoSeeder` (lazy singleton, plain non-encrypted prefs file `rich_demo_seeder_v1`); the picker UI that calls it lives in C.
- [x] **B.6** Crash-safe: `seedIfNeeded` is `runCatching` — on failure the partial `rich-demo/` directory is `deleteRecursively()`'d before re-throwing so the next call starts clean.

Bonus (not in original B numbering, kept under B):

- [x] **B.7** Authoring helper `./gradlew :app:regenerateRichDemoManifest` in `app/build.gradle.kts` — walks `src/main/assets/rich-demo-repo/`, regenerates `_manifest.txt` (sorted, POSIX paths). NOT wired into the build graph; run on demand after editing demo content.
- [x] **B.8** Unit tests: `RichDemoSeederTest` (3 cases: first-extract / no-op-on-second-call / reset-re-enables-reseed), `RichDemoSeederSymlinkTest` (CLAUDE.md → AGENTS.md symlink content equality + isSymbolicLink assertion), `RichDemoManifestCoverageTest` (recursive `AssetManager.list()` walk equals the `_manifest.txt` set — catches forgotten manifest regeneration).

## Phase C — Picker integration

- [ ] **C.1** Extend `LifestyleCard` (or sibling enum) with a `RichDemo` perspective row labeled "Kept Life — full week" + subtitle "the example we ship that shows everything", displayed FIRST in the picker.
- [ ] **C.2** In the Round 2.15 demo-perspective picker UI, render the rich-demo row with a distinct "Recommended" pill + a longer descriptive paragraph (the others stay one-line).
- [ ] **C.3** Default the picker selection to `RichDemo` on first launch.
- [ ] **C.4** When `RichDemo` is selected, dispatch to `RichDemoSeeder.seed()` instead of `DemoRepoSeeder.seed()`; both writers share the same `RepoStore.register` tail so the rest of the app sees them identically.

## Phase D — Tests + AVD smoke

- [ ] **D.1** `RichDemoSeederIdempotencyTest` — seeds twice into the same tmp dir, asserts second call no-ops, asserts pref flag is set.
- [ ] **D.2** `RichDemoSeederSymlinkTest` — asserts `CLAUDE.md` is a symlink to `AGENTS.md` after extraction (round-trips per CLAUDE.md's symlink-correctness rule).
- [ ] **D.3** `RichDemoManifestCoverageTest` — asserts the generated `_manifest.txt` matches the actual asset tree (no missing/extra files), catches authors who add a file but forget to rerun the gradle task.
- [ ] **D.4** `RichDemoCalendarParsabilityTest` — loads every `.md`/`.toml` under the extracted repo through the existing parser, asserts zero parse errors.
- [ ] **D.5** `RichDemoResolverOverlayTest` — picks a representative `(date, time)` slot (e.g. Wed 2026-05-20 12:15) and asserts the resolver returns the expected stack: work/lunch-break + dom-overlay/lunch-check-in. Two more slots: a vacation-superseded slot (Thu 2026-05-28 09:00 — work hidden, kinky-rituals/cat-care preserved) and a holiday slot (Mon 2026-05-25 09:00 — work standup cancelled by exception + Pfingstmontag banner).
- [ ] **D.6** `RichDemoInvertedHabitTest` — asserts the 5 inverted habits resolve to `completed-by-schedule` for slots without deviations, and to `deviation` for the seeded deviation files.
- [ ] **D.7** `RichDemoSupersedenceTest` — asserts vacation correctly hides `work/commute/gym/dom-overlay/social` for days 12–15 but NOT `kinky-rituals/cat-care/holidays/routines.morning-cage-check`. Asserts the `force-show` override on `weekly-daddy-meetup/2026-05-31.md` resurfaces that occurrence.
- [ ] **D.8** AVD smoke loop (canonical per CLAUDE.md): build debug APK, install on `emulator-5554`, launch, walk through onboarding picking the rich demo, land in schedule view, screenshot, verify visually: routine + dom-overlay + work all visible Day 1; vacation-band visible Days 12–15 with work/commute faded out per the supersedence UI.
- [ ] **D.9** AVD smoke: open the `owner-activity-log` todolist, tap into the 2026-05-13 entry, verify markdown rendering shows headings + bullets + the dom-reply blockquote correctly under Markwon.
- [ ] **D.10** AVD smoke: confirm the rich-demo repo appears in the repo list under a "Demo" section with the demo-mode chrome pill (per Round 2.15.C), confirms Push/Pull buttons hidden, confirms Settings → Export does not list it.

## Phase E — Plan close-out

- [ ] **E.1** Tick every Phase A–D checkbox above as work lands; add jj change ID to each phase header (alphabetic prefix per global CLAUDE.md).
- [ ] **E.2** Add `## Status: ✅ DONE` at the top of this file once all checkboxes are ticked.
- [ ] **E.3** Add a one-line entry in `docs/plans/main.md` under Round 2 pointing at this plan and noting the rich-demo repo as the new default first-launch experience.

## Verification

**AVD scenarios** (each captured as `/tmp/skb-r220-<scenario>.png` and read back):

1. First-launch onboarding ends on the schedule view with seed-date 2026-05-15 rendered, ≥5 overlapping calendar bands visible in the day-view rail.
2. Scroll forward to 2026-05-27; vacation band stretches days 12–15; work + commute + gym + dom-overlay events struck-through-grey in the manage-overlays UI; kinky-rituals + cat-care + holidays render normally.
3. Open `owner-activity-log` todolist; pre-seeded entries 2026-05-12/13/14 render full markdown including the blockquoted dom-reply.
4. Open `work-sprint-25` todolist; sprint-board task renders the markdown table without overflow under bodyMedium scale.
5. Tap into the cage-stays-on inverted habit on Day 2 (2026-05-16), see the seeded deviation displayed; tap into Day 3 (no deviation) and see the default completed-by-schedule check.
6. Settings → Repos → "Demo Life" row shows a "Demo" pill and no remote / Push / Pull affordances.

**Unit-test gates** (all from Phase D):

- `RichDemoSeederIdempotencyTest`, `RichDemoSeederSymlinkTest`, `RichDemoManifestCoverageTest`, `RichDemoCalendarParsabilityTest`, `RichDemoResolverOverlayTest`, `RichDemoInvertedHabitTest`, `RichDemoSupersedenceTest` all green under `:app:testDebugUnitTest`.

**SOLID self-check on `RichDemoSeeder` diff:**

- S: single responsibility (asset → filesystem). DemoRepoSeeder stays the per-perspective wizard-scaffolder; RichDemoSeeder is the asset-copier. Don't merge them.
- O/L: not applicable (no sealed hierarchies introduced).
- I: takes `AssetManager` + `File`, not a god-Context.
- D: registers with the existing `RepoStore` interface, not `RepoStore`'s concrete class directly.

## What is intentionally NOT in scope

- Multiple rich perspectives (lesbian-dom-keeping-pets full week, self-kept full week, etc.). One perspective at this depth is the whole budget; siblings are a future round if user asks.
- Translating demo content to non-English locales. English-only per the translations doc; if a non-English locale ships before this content rebases, the demo stays English (canonical) and the rest of the app still gets `values-<bcp47>` overrides.
- Pushing demo data to GitHub Releases / surfacing it in Export. App-internal only per D-2.20.j.
- Replacing Round 2.15's 6-perspective demo seeder. Additive: rich demo is the default + recommended row; the six thin perspectives stay reachable for alignment comparison.
- Dynamic date rebasing ("seed the demo with today as day 1"). Reproducibility wins over freshness in this round; revisit if user-feedback shows the static 2026-05-15 anchor is confusing once we ship past mid-2026.
- Attaching real image binaries to the demo. Asset size budget; image-reference fallback rendering is what we exercise instead (per A.56).

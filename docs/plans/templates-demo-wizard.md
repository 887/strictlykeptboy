# strictlykeptboy — templates, demo content, wizard

## Status: 🚧 PARTIALLY SUPERSEDED — wizard owned by [`draft-lifestyle-wizard.md`](draft-lifestyle-wizard.md) (now Phase K), template *picker UI* owned by [`event-create.md`](event-create.md) (Phase FFF), kink-positive identity owned by [`draft-kink-positive-identity.md`](draft-kink-positive-identity.md) (D.55–D.62). This file's surviving content is template *content* + `template_origin` / `template_slot` conventions only.

**Round 4 integration notes (see `decisions.md` Round 4):**

- **TW-C / TW-D / TW-G / TW-H are RETIRED** — superseded by Phase K
  (lifestyle wizard) in `main.md` and the LW-A..LW-M phases in
  `draft-lifestyle-wizard.md`. Demo mode is gone; the wizard's
  output IS the user's canonical starting state per D.54.
- **TW-A / TW-B / TW-E survive** as content/rationale references.
  `template_origin` + `template_slot` frontmatter conventions
  carry forward (Phase K LW-L re-run-from-Settings depends on
  them). TW-B template content is reorganized in Phase XX (atomic
  activities) as the new authoritative template set indexed by
  role.
- **TW-F retires** (replaced by integration notes inside
  `draft-lifestyle-wizard.md`).
- **SP-1..SP-9 surface-phrasing register is RETRACTED/REPLACED in
  Round 4.** SP-3, SP-4, SP-8, SP-9 fully retracted/replaced with
  direct kink phrasing per D.55 / K-1. SP-1, SP-2, SP-5 kept with
  revised justification (no longer SFW-deniability framing — they
  remain because they're good defaults for the audience). SP-6,
  SP-7 untouched (architectural). The surface-phrasing register is
  now kink-positive direct; the neutral-mode toggle (D.58 / K-3)
  preserves a peer-equal SFW path.
- **VV.12 SFW phrasing guarantee** (formerly cross-referenced from
  SP-9) is replaced by verbatim user-content rendering + the per-
  event `private = true` flag per D.57 / K-2. See `main.md` VV.12
  and VV.13.

This document specifies (1) every starter **template** the app emits
into a user repo, (2) the two **demo repos** (`demo-sub` + `demo-dom`)
shipped for the no-setup demo path, (3) the first-run **wizard** flow
screen-by-screen, and (4) the **role-toggle composition** rules that
turn a set of toggles into a deterministic, idempotent, repo-shaped
filesystem write.

It corresponds to:

- `main.md` Phase **K** — wizard + templates
- `main.md` Phase **L** — demo content

…and uses phase prefix **TW-**.

It assumes:

- File layout per `decisions.md` D.3.
- TOML+Markdown schema per `decisions.md` D.4 / `data-model.md` (or, in
  its absence, the schema sketch in the SA-5 brief — both align).
- UUIDv7 IDs minted at apply time (D.1). Examples below use placeholder
  hex prefixes like `01HZ…` and human-recognizable suffixes
  (`…wake01`) so a reader can trace which slot each file fills.
- Recurrence engine is `lib-recur` (RFC5545); RRULEs in examples are
  spec-compliant.
- Active-windows + active-hours per D.5 — used in `school-semester`
  and `soccer-club`.
- All surface text (titles, calendar names, event subjects) is
  **SFW-readable**. Body Markdown can be candid for `kinky-chores`
  only, since bodies are not surfaced in app-store screenshots.

**Surface-phrasing decisions (resolved inline, applied throughout):**

- **Decision SP-1:** Dom identity in demo + `master-scheduled` is
  display-named **"Sir"**. The repo identity ID is `sir`. Rationale:
  one-syllable, neutral-readable as a respectful address, common in
  e.g. mil-adjacent / formal contexts; recruiter-safe; charged in the
  context but plausibly deniable. *(**Replaced KI-A.1 / D.55** — same
  default, different justification: it's the default because it fits
  the lifestyle, not because it's plausibly deniable. Users pick any
  name — "Master", "Owner", "Daddy", "Mistress", "Goddess", "Coach",
  or a regular name — with no euphemism nudging from the UI.)*
- **Decision SP-2:** Sub identity in demo is **"Bat"** (mascot).
  Identity ID `bat`. Rationale: mascot fit; reads as a nickname.
  *(**Kept KI-A.2** — mascot tie-in stands; no SFW justification
  needed. Users pick any sub name — "boy", "pet", "kitten", "pup",
  "slave", "toy" — directly from the wizard with no euphemism.)*
- ~~**Decision SP-3:** Recurring couple-events are titled
  **"Check-in"**, **"Weekly review"**, **"Date night"**, **"Monthly
  call"**. No "Master", "Owner", "Boy", "Sub" in titles or calendar
  names. The `master-scheduled` calendar is displayed as **"Co-schedule
  (with Sir)"** in the sub's repo and **"Co-schedule (with Bat)"** in
  the Dom's repo.~~ *(**RETRACTED KI-A.3 / D.55** — wizard now offers
  direct kink-coded titles as defaults when the user picks a D/s-coded
  alignment: "Inspection", "Cage check", "Protocol review", "Collar
  maintenance", "Service report", etc., alongside the neutral
  variants. Calendar can be named "Sir's schedule for boy", "Master's
  protocols", or anything the user picks. No locked SFW-readable
  title list. Neutral-mode toggle re-suggests the neutral variants
  as defaults; user-authored titles already in the repo are never
  rewritten.)*
- ~~**Decision SP-4:** `kinky-chores` titles use grooming/health/posture
  language only. Bodies may say "report compliance via the daily
  check-in", "Sir prefers …", etc.~~ *(**RETRACTED KI-A.4 / D.55** —
  titles can be candid too. "Edge denial log", "Cage check Mon/Wed/
  Fri", "Posture drill 18:00", "Service kneel before bed", "Protocol
  violation tracker" — all valid title-surface content. Body remains
  free-form Markdown as always.)*
- **Decision SP-5:** When a single sub-side template applies and no
  Dom is configured, `sub-scheduled` references "your partner" in
  prose (no Dom-identity ID needed); when Dom is also configured,
  references resolve to the configured identity display name.
  *(**Replaced KI-A.5 / D.55** — defaults to "your Dom" by default
  (most common case for the template); user can change to "your
  partner", "your Owner", "your Master", "your Mistress", "your
  Daddy", etc. in the template-apply screen. Neutral-mode users get
  "your partner" as the default.)*

**Tradeoffs resolved inline:**

- **Tradeoff T-1: composing two morning templates.** If a user toggles
  both `morning-bird` and `night-bat-owl`, two wake events would
  collide. **Resolved:** the toggle UI is mutually exclusive within
  the *Morning routine* group (radio, not checkbox). Composer enforces
  via the toggle-group spec (TW-D.4). Same for *Work* and *Education*
  groups.
- **Tradeoff T-2: per-template calendars vs one big "Routine"
  calendar.** Two templates writing into one calendar makes
  re-applying messy (idempotency by template-id needs per-template
  scope). **Resolved:** every template gets its own calendar(s) or
  todolist(s); composition = side-by-side, not merge-into-one. The
  `Special Events` calendar is the only cross-template shared
  calendar, created exactly once by whichever template runs first.
- **Tradeoff T-3: `applied-templates.toml` location — repo or
  app-prefs.** Repo-side survives reinstall + multi-device. App-side
  doesn't bloat repo. **Resolved:** repo-side at
  `.strictlykeptboy/applied-templates.toml`, mirrored to app prefs as
  cache. Repo wins on conflict.
- **Tradeoff T-4: demo "fake remote".** Demo repos must look like real
  repos for UI realism but never push. **Resolved:** demo repos are
  initialized as plain local Git repos (not bare, no remote
  configured); the app's repo entry has `mode = "demo"` which suppresses
  the sync service for that repo and shows a "Demo — local only" badge.
- **Tradeoff T-5: re-apply with rename.** If user renamed a template's
  calendar, re-applying must not resurrect the old name.
  **Resolved:** idempotency key is `(template-id, slot-id)` recorded in
  `applied-templates.toml` against the *current* calendar/todolist
  UUID — even after rename. Composer reads the recorded UUID and
  writes new entries into the same folder regardless of display name.

---

## Phase TW-A — template registry shape

- [ ] **TW-A.1** Define `TemplateManifest` Kotlin data class:
      `id: String`, `version: Int`, `displayName: String`,
      `group: ToggleGroup`, `mutuallyExclusiveGroup: Boolean`,
      `description: String`, `slots: List<TemplateSlot>`,
      `notificationGroups: List<TemplateNotificationGroup>`.
- [ ] **TW-A.2** Define `TemplateSlot` sealed class:
      `CalendarSlot`, `TodolistSlot`, `EventSlot`, `RecurrenceSlot`,
      `TaskSlot`, `StandingTaskSlot`, `ChoreRecurrenceSlot`,
      `IdentitySlot`. Each slot has a stable `slotId: String` (used
      for idempotency) and a `parentSlotId: String?` (e.g. event slot
      points at its calendar slot).
- [ ] **TW-A.3** Templates are static Kotlin objects in
      `app/src/main/kotlin/com/eight87/strictlykeptboy/templates/`,
      one file per template (`MorningBirdTemplate.kt` etc.).
- [ ] **TW-A.4** A `TemplateRegistry` exposes
      `all(): List<TemplateManifest>` and `byId(id): TemplateManifest`.
- [ ] **TW-A.5** Each `slotId` is human-grep-able:
      `cal.morning-bird.timebox`, `evt.morning-bird.timebox.wake`,
      `rec.morning-bird.timebox.wake`. Stable across versions.
- [ ] **TW-A.6** `applied-templates.toml` schema:
      ```toml
      [applied."morning-bird"]
      version = 1
      applied_at = "2026-05-10T14:23:11Z"
      slots = [
        { slot = "cal.morning-bird.timebox", entity_id = "01HZAB..." },
        { slot = "rec.morning-bird.timebox.wake", entity_id = "01HZAC..." },
      ]
      ```
- [ ] **TW-A.7** Template version bumps run a slot-by-slot diff against
      the recorded manifest version: new slots → create; missing slots
      from the new manifest → leave (user may have edited); changed
      slots → leave existing user file alone, log a "template-update
      available" badge.

---

## Phase TW-B — template content (the 14)

For each template below: a header section, a one-line purpose, the
**toggle group**, the **calendars or todolists created**, a **table of
events / recurrences / tasks**, and **at least one full-content example
file** as TOML+Markdown.

### TW-B.1 `morning-bird`

**Purpose:** wake-early, daylight-anchored daily routine. Wake 06:00,
exercise, breakfast, two work blocks, evening wind-down, bed 22:00.
**Group:** Morning routine. **Mutually exclusive** with `night-bat-owl`.

**Calendars (2):**

| slot | display name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.morning-bird.timebox` | "Morning Routine — Timebox" | timebox | 600 | 🌅 | `#F2A93B` | `routine` |
| `cal.morning-bird.regular` | "Morning Routine — Reminders" | regular | 400 | 🔔 | `#F2C57B` | `routine-reminders` |

**Notification groups created:**

- `routine` (default lead-times: `["10m", "0m"]`)
- `routine-reminders` (default lead-times: `["5m"]`)

**Recurrences (in `cal.morning-bird.timebox`):**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.morning-bird.timebox.wake`        | Wake & light                    | `FREQ=DAILY` | `06:00` | 15m |
| `rec.morning-bird.timebox.exercise`    | Exercise / yoga                 | `FREQ=DAILY` | `06:15` | 30m |
| `rec.morning-bird.timebox.breakfast`   | Breakfast                       | `FREQ=DAILY` | `07:00` | 30m |
| `rec.morning-bird.timebox.work-am`     | Focus block — morning           | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` | `09:00` | 3h |
| `rec.morning-bird.timebox.lunch`       | Lunch                           | `FREQ=DAILY` | `12:30` | 1h |
| `rec.morning-bird.timebox.work-pm`     | Focus block — afternoon         | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` | `13:30` | 3.5h |
| `rec.morning-bird.timebox.wind-down`   | Wind-down                       | `FREQ=DAILY` | `21:00` | 1h |
| `rec.morning-bird.timebox.bed`         | Bed                             | `FREQ=DAILY` | `22:00` | 0 (point) |

**Recurrences (in `cal.morning-bird.regular`):**

| slot | title | RRULE | dtstart | notes |
|---|---|---|---|---|
| `rec.morning-bird.regular.water-am`    | Water reminder                  | `FREQ=DAILY` | `06:30` | point event |
| `rec.morning-bird.regular.stretch`     | Stretch break                   | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` | `11:00` | 5m |
| `rec.morning-bird.regular.daylight`    | Outside / daylight 10 min       | `FREQ=DAILY` | `12:15` | 10m |
| `rec.morning-bird.regular.screens-off` | Screens-off cue                 | `FREQ=DAILY` | `21:30` | point event |

**Example file (full TOML+Markdown):**

`calendars/01HZ…mbtimebox/calendar.toml`:

```toml
+++
id = "01HZ-MB-TIMEBOX-XXXXXXXXXXXX"
name = "Morning Routine — Timebox"
emoji = "🌅"
color = "#F2A93B"
kind = "timebox"
priority = 600
active_toggle = true
active_windows = []
active_hours = []
default_notification_group = "routine"
template_origin = "morning-bird@1"
+++
```

`calendars/01HZ…mbtimebox/recurrences/01HZ…wake.md`:

```toml
+++
id = "01HZ-MB-TIMEBOX-WAKE-XXXXXXXX"
calendar_id = "01HZ-MB-TIMEBOX-XXXXXXXXXXXX"
summary = "Wake & light"
location = ""
default_duration = "PT15M"
dtstart = "2026-05-10T06:00:00"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY"
notifications = ["0m"]
emoji = "🌅"
template_origin = "morning-bird@1"
template_slot = "rec.morning-bird.timebox.wake"
+++

# Wake & light

First fifteen minutes after the alarm. No phone. Open a window or step
onto the balcony. Bright light to anchor the circadian rhythm.

Replaces snooze. If you slip past 06:15, that's fine — log it in the
body and move on.
```

---

### TW-B.2 `night-bat-owl`

**Purpose:** late-cycle daily routine. Wake 11:00, slow morning, work
14:00–22:00, late dinner, late gym, bed 03:00. Bat-coded language in
event titles.
**Group:** Morning routine. **Mutually exclusive** with `morning-bird`.

**Calendars (2):**

| slot | name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.night-bat-owl.timebox` | "Night Routine — Timebox" | timebox | 600 | 🦇 | `#5B4B8A` | `routine` |
| `cal.night-bat-owl.regular` | "Night Routine — Reminders" | regular | 400 | 🌙 | `#8E7BC4` | `routine-reminders` |

**Recurrences (in `cal.night-bat-owl.timebox`):**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.night-bat-owl.timebox.wake`         | Wake (slow)                    | `FREQ=DAILY` | `11:00` | 15m |
| `rec.night-bat-owl.timebox.morning`      | Slow morning                   | `FREQ=DAILY` | `11:15` | 1h45m |
| `rec.night-bat-owl.timebox.echolocation` | Echolocation hour (focus)      | `FREQ=DAILY` | `14:00` | 2h |
| `rec.night-bat-owl.timebox.work-eve`     | Roost work block               | `FREQ=DAILY` | `16:30` | 5h30m |
| `rec.night-bat-owl.timebox.dinner`       | Dinner                         | `FREQ=DAILY` | `22:30` | 30m |
| `rec.night-bat-owl.timebox.gym`          | Gym (night flight)             | `FREQ=DAILY` | `23:00` | 1h30m |
| `rec.night-bat-owl.timebox.decompress`   | Decompression                  | `FREQ=DAILY` | `01:00` | 2h |
| `rec.night-bat-owl.timebox.bed`          | Roost (bed)                    | `FREQ=DAILY` | `03:00` | 0 |

**Recurrences (in `cal.night-bat-owl.regular`):**

| slot | title | RRULE | dtstart |
|---|---|---|---|
| `rec.night-bat-owl.regular.water-1` | Water reminder | `FREQ=DAILY` | `11:30` |
| `rec.night-bat-owl.regular.water-2` | Water reminder | `FREQ=DAILY` | `17:00` |
| `rec.night-bat-owl.regular.daylight` | Daylight ten (yes, you need it) | `FREQ=DAILY` | `13:00` |
| `rec.night-bat-owl.regular.screens-dim` | Screens dim / blue-block | `FREQ=DAILY` | `02:00` |

**Example file:**

`calendars/01HZ…nbtimebox/recurrences/01HZ…echo.md`:

```toml
+++
id = "01HZ-NBO-TIMEBOX-ECHO-XXXXXXXX"
calendar_id = "01HZ-NBO-TIMEBOX-XXXXXXXXXXX"
summary = "Echolocation hour (focus)"
location = ""
default_duration = "PT2H"
dtstart = "2026-05-10T14:00:00"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY"
notifications = ["10m", "0m"]
emoji = "🦇"
template_origin = "night-bat-owl@1"
template_slot = "rec.night-bat-owl.timebox.echolocation"
+++

# Echolocation hour

Two-hour pure focus block. No meetings, no chat, no inbox.
Single task. The point is to ping the day with one strong signal
before the work block proper at 16:30.
```

---

### TW-B.3 `work-9-5`

**Purpose:** standard Mon–Fri 09:00–17:00 office calendar with lunch
12:30 and a 15:00 coffee.
**Group:** Work. **Mutually exclusive** with `work-shift`.

**Calendars (1):**

| slot | name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.work-9-5.regular` | "Work" | regular | 700 | 💼 | `#3A6FB0` | `work` |

**Notification groups:** `work` (defaults `["15m", "0m"]`).

**Active-hours on calendar:** `[{day=mon..fri, from="08:30", to="18:00"}]`
so it dims outside work hours per resolver D.5.

**Recurrences:**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.work-9-5.day`    | Work day                | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` | `09:00` | 8h |
| `rec.work-9-5.lunch`  | Lunch                   | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` | `12:30` | 30m |
| `rec.work-9-5.coffee` | Afternoon coffee        | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` | `15:00` | 15m |
| `rec.work-9-5.standup`| Team stand-up           | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` | `09:30` | 15m |

**Example file:**

`calendars/01HZ…work/calendar.toml`:

```toml
+++
id = "01HZ-W95-XXXXXXXXXXXX"
name = "Work"
emoji = "💼"
color = "#3A6FB0"
kind = "regular"
priority = 700
active_toggle = true
active_windows = []
active_hours = [
  { day = "mon", from = "08:30", to = "18:00" },
  { day = "tue", from = "08:30", to = "18:00" },
  { day = "wed", from = "08:30", to = "18:00" },
  { day = "thu", from = "08:30", to = "18:00" },
  { day = "fri", from = "08:30", to = "18:00" },
]
default_notification_group = "work"
template_origin = "work-9-5@1"
+++
```

---

### TW-B.4 `work-shift`

**Purpose:** rotating shift pattern. Example pattern shipped: 4-on /
4-off rotating between Early, Late, Night.
**Group:** Work. **Mutually exclusive** with `work-9-5`.

**Calendars (1):**

| slot | name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.work-shift.regular` | "Work — Shift" | regular | 700 | 🔁 | `#5A8E3F` | `work` |

**Recurrences:** the 4-on/4-off pattern is encoded as three weekly
RRULEs with `BYWEEKNO` modulation and a `dtstart` anchor 2026-05-04
(Monday). The composer documents in the file body that the user
should re-anchor `dtstart` to the start of their next cycle.

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.work-shift.early` | Early shift | `FREQ=DAILY;INTERVAL=8;COUNT=200` (start day 0–3 of cycle) | `2026-05-04T06:00` | 8h |
| `rec.work-shift.late`  | Late shift  | `FREQ=DAILY;INTERVAL=8;COUNT=200` (start day 16–19 of cycle equivalent) | `2026-05-20T14:00` | 8h |
| `rec.work-shift.night` | Night shift | `FREQ=DAILY;INTERVAL=8;COUNT=200` | `2026-06-05T22:00` | 8h |

**Decision SP-6:** *(**Kept KI-A.6** — architectural, carries forward
unchanged.)* because RFC5545 doesn't natively express "4-on /
4-off across 3 rotating shifts", the composer ships three concurrent
DAILY-INTERVAL=8 rules (one per shift, offset). Body of each rule
explains the offset and tells the user "edit `dtstart` to match your
own rotation start". This is honest about the limit instead of cooking
up a bespoke RRULE extension.

**Example file:**

`calendars/01HZ…ws/recurrences/01HZ…early.md`:

```toml
+++
id = "01HZ-WS-EARLY-XXXXXXXXXXX"
calendar_id = "01HZ-WS-XXXXXXXXXXXX"
summary = "Early shift"
default_duration = "PT8H"
dtstart = "2026-05-04T06:00:00"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY;INTERVAL=8;COUNT=200"
notifications = ["1h", "15m"]
template_origin = "work-shift@1"
template_slot = "rec.work-shift.early"
+++

# Early shift

This rule recurs every 8 days starting from `dtstart`. The shipped
template uses 2026-05-04 as a placeholder anchor — **edit `dtstart`
to the first early-shift day of your own rotation**. The companion
"Late shift" and "Night shift" rules are offset accordingly; re-anchor
all three together.

If your rotation isn't 4-on/4-off, the simplest path is: delete the
shipped rules and create one rule per shift you actually work, then
copy this `template_slot` value into the new rule's frontmatter so
re-applying the template doesn't recreate the originals.
```

---

### TW-B.5 `school-semester`

**Purpose:** Mon–Fri class timetable + evening study blocks; weekend
free. Uses `active_windows` to scope the calendar to the semester.
**Group:** Education. (Single template in this group; not mutually
exclusive against anything.)

**Calendars (2):**

| slot | name | kind | priority | emoji | color | notif group | active_windows |
|---|---|---|---|---|---|---|---|
| `cal.school.classes` | "Classes" | regular | 750 | 🎓 | `#A33C5C` | `school` | `[{start="2026-09-01", end="2027-01-31"}, {start="2027-02-15", end="2027-06-30"}]` |
| `cal.school.study` | "Study blocks" | timebox | 500 | 📚 | `#C56F8B` | `school` | (same) |

**Notification group:** `school` (defaults `["30m", "10m"]`).

**Recurrences in `cal.school.classes` (example timetable):**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.school.classes.mon1` | Linear Algebra (lecture) | `FREQ=WEEKLY;BYDAY=MO` | `09:00` | 1h30m |
| `rec.school.classes.mon2` | Linear Algebra (tutorial) | `FREQ=WEEKLY;BYDAY=MO` | `11:00` | 1h30m |
| `rec.school.classes.tue1` | Programming I | `FREQ=WEEKLY;BYDAY=TU` | `09:00` | 1h30m |
| `rec.school.classes.tue2` | Programming I (lab) | `FREQ=WEEKLY;BYDAY=TU` | `13:00` | 2h |
| `rec.school.classes.wed1` | Discrete Math | `FREQ=WEEKLY;BYDAY=WE` | `10:00` | 1h30m |
| `rec.school.classes.thu1` | Algorithms | `FREQ=WEEKLY;BYDAY=TH` | `09:00` | 1h30m |
| `rec.school.classes.thu2` | Algorithms (tutorial) | `FREQ=WEEKLY;BYDAY=TH` | `11:00` | 1h30m |
| `rec.school.classes.fri1` | English for CS | `FREQ=WEEKLY;BYDAY=FR` | `13:00` | 1h30m |

**Recurrences in `cal.school.study`:**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.school.study.eve` | Evening study block | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH` | `19:00` | 2h |
| `rec.school.study.fri` | Friday review | `FREQ=WEEKLY;BYDAY=FR` | `16:00` | 1h |

**Example file:**

`calendars/01HZ…sclass/calendar.toml`:

```toml
+++
id = "01HZ-SCH-CLS-XXXXXXXXXX"
name = "Classes"
emoji = "🎓"
color = "#A33C5C"
kind = "regular"
priority = 750
active_toggle = true
active_windows = [
  { start = "2026-09-01", end = "2027-01-31" },
  { start = "2027-02-15", end = "2027-06-30" },
]
active_hours = []
default_notification_group = "school"
template_origin = "school-semester@1"
+++
```

---

### TW-B.6 `gym-3x`

**Purpose:** Monday/Wednesday/Friday 18:00–19:30 sessions, with a
"warmup playlist" 17:50 cue ten minutes before.
**Group:** Fitness. **Mutually exclusive** with `gym-5x` (within the
"Gym daily-frequency" sub-group; the other Fitness toggles
`swim-club`, `soccer-club`, `music-practice` are independent).

**Calendars (1):**

| slot | name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.gym-3x.regular` | "Gym" | regular | 550 | 🏋️ | `#C25450` | `fitness` |

**Notification group:** `fitness` (defaults `["30m", "10m"]`).

**Recurrences:**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.gym-3x.warmup` | Warmup playlist | `FREQ=WEEKLY;BYDAY=MO,WE,FR` | `17:50` | 10m |
| `rec.gym-3x.session` | Gym session | `FREQ=WEEKLY;BYDAY=MO,WE,FR` | `18:00` | 1h30m |

**Todolists (1):** `tdl.gym-3x` — "Gym log" with standing tasks for
PR tracking ("Squat PR — current", "Deadlift PR — current",
"Bench PR — current"). Tasks have `done = false`,
`auto_done_at_eod = false`.

**Example file:**

`calendars/01HZ…g3/recurrences/01HZ…session.md`:

```toml
+++
id = "01HZ-G3X-SESSION-XXXXXXXX"
calendar_id = "01HZ-G3X-XXXXXXXXXXXX"
summary = "Gym session"
default_duration = "PT1H30M"
dtstart = "2026-05-11T18:00:00"
tzid = "Europe/Berlin"
rrule = "FREQ=WEEKLY;BYDAY=MO,WE,FR"
notifications = ["30m", "10m"]
emoji = "🏋️"
spawns_task = "tdl.gym-3x"
template_origin = "gym-3x@1"
template_slot = "rec.gym-3x.session"
+++

# Gym session

90 minutes. Default split: Mon push, Wed pull, Fri legs. Edit the
notes per session to log sets/reps; the spawned task in "Gym log"
appears on each session day for quick log entry.
```

---

### TW-B.7 `gym-5x`

**Purpose:** Mon–Fri 06:30–07:30 morning gym variant.
**Group:** Fitness, sub-group "Gym daily-frequency". Mutually exclusive
with `gym-3x`.

**Calendars (1):**

| slot | name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.gym-5x.regular` | "Gym" | regular | 550 | 🏋️ | `#C25450` | `fitness` |

**Recurrences:**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.gym-5x.session` | Morning gym | `FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR` | `06:30` | 1h |

**Todolists (1):** `tdl.gym-5x` — same shape as `tdl.gym-3x`.

---

### TW-B.8 `swim-club`

**Purpose:** Tue/Thu 19:00–20:30 + Sat 10:00–11:30 swim sessions.
**Group:** Fitness. Independent.

**Calendars (1):**

| slot | name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.swim-club.regular` | "Swim club" | regular | 540 | 🏊 | `#2F8FAA` | `fitness` |

**Recurrences:**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.swim-club.weekday` | Pool — weekday | `FREQ=WEEKLY;BYDAY=TU,TH` | `19:00` | 1h30m |
| `rec.swim-club.weekend` | Pool — Saturday | `FREQ=WEEKLY;BYDAY=SA` | `10:00` | 1h30m |

**Example recurrence file:**

`calendars/01HZ…sw/recurrences/01HZ…weekday.md`:

```toml
+++
id = "01HZ-SW-WEEKDAY-XXXXXXX"
calendar_id = "01HZ-SW-XXXXXXXXXXXXX"
summary = "Pool — weekday"
location = "Local swim hall"
default_duration = "PT1H30M"
dtstart = "2026-05-12T19:00:00"
tzid = "Europe/Berlin"
rrule = "FREQ=WEEKLY;BYDAY=TU,TH"
notifications = ["1h", "15m"]
emoji = "🏊"
template_origin = "swim-club@1"
template_slot = "rec.swim-club.weekday"
+++

# Pool — weekday

Default kit: trunks, cap, goggles, towel. Pre-pack the night before.
```

---

### TW-B.9 `soccer-club`

**Purpose:** Wed practice + Sat game; in-season only via
`active_windows` (default August–May).
**Group:** Fitness. Independent.

**Calendars (1):**

| slot | name | kind | priority | emoji | color | notif group | active_windows |
|---|---|---|---|---|---|---|---|
| `cal.soccer-club.regular` | "Soccer club" | regular | 540 | ⚽ | `#1F7A1F` | `fitness` | `[{start="2026-08-15", end="2027-05-31"}]` |

**Recurrences:**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.soccer-club.practice` | Practice | `FREQ=WEEKLY;BYDAY=WE` | `19:00` | 1h30m |
| `rec.soccer-club.game` | Game | `FREQ=WEEKLY;BYDAY=SA` | `14:00` | 2h |

---

### TW-B.10 `music-practice`

**Purpose:** daily practice 18:00–19:00 + weekly lesson Sat 11:00.
**Group:** Practice. Independent.

**Calendars (1):**

| slot | name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.music-practice.regular` | "Music practice" | regular | 530 | 🎼 | `#7A3FA0` | `practice` |

**Notification group:** `practice` (defaults `["10m"]`).

**Recurrences:**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.music-practice.daily` | Practice (1h) | `FREQ=DAILY` | `18:00` | 1h |
| `rec.music-practice.lesson` | Lesson | `FREQ=WEEKLY;BYDAY=SA` | `11:00` | 1h |

**Todolist (1):** `tdl.music-practice` with standing tasks "current
piece", "scales focus this month", "pieces to memorize".

**Example file:**

`calendars/01HZ…mp/recurrences/01HZ…daily.md`:

```toml
+++
id = "01HZ-MP-DAILY-XXXXXXX"
calendar_id = "01HZ-MP-XXXXXXXXXXX"
summary = "Practice (1h)"
default_duration = "PT1H"
dtstart = "2026-05-10T18:00:00"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY"
notifications = ["10m"]
emoji = "🎼"
spawns_task = "tdl.music-practice"
template_origin = "music-practice@1"
template_slot = "rec.music-practice.daily"
+++

# Practice (1h)

15m scales / 15m current piece / 15m sight reading / 15m memorization
or recording. Note any sticking points in the spawned task; review
weekly with the lesson task.
```

---

### TW-B.11 `master-scheduled`

**Purpose:** the calendar a partner ("Sir") maintains *for* the user
("Bat"). Twice-daily check-ins, weekly review, weekly date-night,
monthly long call. SFW surface phrasing.
**Group:** Structure / relationship. Independent of `sub-scheduled`
but typically toggled together.

**Where it lives:** when this template applies in the *partner's* repo
(the Dom's), it creates the calendar there. When it applies in the
*user's* repo (the sub's), it creates a *companion read-only-overlay*
calendar that points at the partner's repo for source data. **Decision
SP-7** *(**Kept KI-A.7** — architectural, carries forward unchanged.)***:** in v1, both sides simply create the calendar as a regular
calendar in the *applying* repo. Cross-repo overlay (sub seeing Sir's
calendar in their own repo) is achieved through D.9's all-repos
overlay view, not by writing the overlay calendar twice.

**Calendars (1):**

| slot | name | kind | priority | emoji | color | notif group |
|---|---|---|---|---|---|---|
| `cal.master-scheduled.regular` | "Co-schedule (with partner)" | regular | 800 | 🤝 | `#7B1FA2` | `partner` |

**Notification group:** `partner` (defaults `["10m", "0m"]`).

**Recurrences:**

| slot | title | RRULE | dtstart | duration |
|---|---|---|---|---|
| `rec.master-scheduled.checkin-am` | Morning check-in | `FREQ=DAILY` | `08:00` | 15m |
| `rec.master-scheduled.checkin-pm` | Evening check-in | `FREQ=DAILY` | `21:00` | 15m |
| `rec.master-scheduled.review`     | Weekly review     | `FREQ=WEEKLY;BYDAY=SU` | `19:00` | 45m |
| `rec.master-scheduled.date-night` | Date night        | `FREQ=WEEKLY;BYDAY=SA` | `19:00` | 3h |
| `rec.master-scheduled.monthly`    | Monthly call      | `FREQ=MONTHLY;BYDAY=1SA` | `15:00` | 1h30m |

**Example file:**

`calendars/01HZ…ms/recurrences/01HZ…checkin-pm.md`:

```toml
+++
id = "01HZ-MS-CKPM-XXXXXXX"
calendar_id = "01HZ-MS-XXXXXXXXXXX"
summary = "Evening check-in"
default_duration = "PT15M"
dtstart = "2026-05-10T21:00:00"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY"
notifications = ["10m", "0m"]
emoji = "🤝"
template_origin = "master-scheduled@1"
template_slot = "rec.master-scheduled.checkin-pm"
+++

# Evening check-in

Fifteen minutes. Day-summary, anything outstanding, anything for
tomorrow. Short call or message — whichever you've agreed.
```

---

### TW-B.12 `sub-scheduled`

**Purpose:** companion to `master-scheduled` — sits on the *user*'s
side as tasks (not events). Tasks for: report by 08:30, evening
report by 21:30, weekly assignments by Sat 18:00, prep date-night by
Sat 17:00. Standing tasks: "outfit choice goes through partner",
"ask before X". SFW-readable.
**Group:** Structure / relationship. Independent of `master-scheduled`.

**Todolists (1):**

| slot | name | priority | emoji | color | active_hours |
|---|---|---|---|---|---|
| `tdl.sub-scheduled` | "Co-schedule tasks" | 750 | 🤝 | `#7B1FA2` | `[]` |

**Recurring tasks (chores):**

| slot | title | RRULE | dtstart | due-time |
|---|---|---|---|---|
| `task.sub-scheduled.morning-report` | Morning report | `FREQ=DAILY` | `2026-05-10` | due 08:30 |
| `task.sub-scheduled.evening-report` | Evening report | `FREQ=DAILY` | `2026-05-10` | due 21:30 |
| `task.sub-scheduled.weekly-assignments` | Complete weekly assignments | `FREQ=WEEKLY;BYDAY=SA` | `2026-05-16` | due 18:00 |
| `task.sub-scheduled.date-night-prep` | Prep for date night | `FREQ=WEEKLY;BYDAY=SA` | `2026-05-16` | due 17:00 |

**Standing tasks:**

| slot | title |
|---|---|
| `stask.sub-scheduled.outfit` | Outfit choice — confirm with partner |
| `stask.sub-scheduled.ask-before` | Ask before non-routine purchases |
| `stask.sub-scheduled.weekly-questions` | Carry weekly questions list to review |

**Example standing-task file:**

`todolists/01HZ…ss/standing/01HZ…outfit.md`:

```toml
+++
id = "01HZ-SS-OUTFIT-XXXXXXX"
todolist_id = "01HZ-SS-XXXXXXXXXXX"
title = "Outfit choice — confirm with partner"
done = false
auto_done_at_eod = false
priority = 750
author = "bat"
template_origin = "sub-scheduled@1"
template_slot = "stask.sub-scheduled.outfit"
+++

# Outfit choice — confirm with partner

Standing reminder, not a daily task. Confirm choice for any non-routine
outing (work-formal, dinner out, traveling). Routine days don't need
checking.
```

**Example recurring-task file:**

`todolists/01HZ…ss/recurrences/01HZ…morning-report.md`:

```toml
+++
id = "01HZ-SS-AMRPT-XXXXXXX"
todolist_id = "01HZ-SS-XXXXXXXXXXX"
title = "Morning report"
default_due_time = "08:30"
dtstart = "2026-05-10"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY"
priority = 750
auto_done_at_eod = false
template_origin = "sub-scheduled@1"
template_slot = "task.sub-scheduled.morning-report"
+++

# Morning report

Brief — wake, sleep quality, any blockers for the day, anything you
want to flag before the day starts. Whatever channel you've agreed on.
Mark done after sending; the daily check-in event at 08:00 is the
reminder window.
```

---

### TW-B.13 `daily-chores`

**Purpose:** ordinary household chores. Recurring tasks: dishes daily,
laundry Sun, groceries Wed+Sat, vacuum weekly, trash out Mon eve,
plants Tue+Fri. Standing tasks for low-frequency things.
**Group:** Chores. Independent.

**Todolists (1):**

| slot | name | priority | emoji | color |
|---|---|---|---|---|
| `tdl.daily-chores` | "Chores" | 400 | 🧹 | `#7C7C7C` |

**Recurring tasks:**

| slot | title | RRULE | dtstart | due |
|---|---|---|---|---|
| `task.daily-chores.dishes` | Dishes | `FREQ=DAILY` | `2026-05-10` | EoD |
| `task.daily-chores.laundry` | Laundry | `FREQ=WEEKLY;BYDAY=SU` | `2026-05-10` | EoD |
| `task.daily-chores.groceries-mid` | Groceries (mid-week) | `FREQ=WEEKLY;BYDAY=WE` | `2026-05-13` | EoD |
| `task.daily-chores.groceries-wknd` | Groceries (weekend) | `FREQ=WEEKLY;BYDAY=SA` | `2026-05-16` | EoD |
| `task.daily-chores.vacuum` | Vacuum | `FREQ=WEEKLY;BYDAY=SA` | `2026-05-16` | EoD |
| `task.daily-chores.trash` | Take trash out | `FREQ=WEEKLY;BYDAY=MO` | `2026-05-11` | due 19:00 |
| `task.daily-chores.plants` | Water plants | `FREQ=WEEKLY;BYDAY=TU,FR` | `2026-05-12` | EoD |

**Standing tasks:**

| slot | title |
|---|---|
| `stask.daily-chores.parents` | Call parents |
| `stask.daily-chores.passport` | Renew passport |
| `stask.daily-chores.dentist` | Schedule next dentist visit |
| `stask.daily-chores.haircut` | Schedule next haircut |

**Example recurring-task file:**

`todolists/01HZ…dc/recurrences/01HZ…dishes.md`:

```toml
+++
id = "01HZ-DC-DISH-XXXXXXX"
todolist_id = "01HZ-DC-XXXXXXXXXXX"
title = "Dishes"
default_due_time = "23:59"
dtstart = "2026-05-10"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY"
priority = 400
auto_done_at_eod = true
template_origin = "daily-chores@1"
template_slot = "task.daily-chores.dishes"
+++

# Dishes

Daily. `auto_done_at_eod = true`, so missed days don't carry forward
as a backlog mountain — they just close out at midnight.
```

---

### TW-B.14 `kinky-chores`

**Purpose:** structured grooming + health + posture practice.
SFW-titled; bodies a touch more candid.
**Group:** Self-care. Independent.

**Todolists (1):**

| slot | name | priority | emoji | color |
|---|---|---|---|---|
| `tdl.kinky-chores` | "Self-care" | 600 | 🧴 | `#9C5BB1` |

**Recurring tasks:**

| slot | title | RRULE | dtstart | due |
|---|---|---|---|---|
| `task.kinky-chores.groom-am` | Morning grooming | `FREQ=DAILY` | `2026-05-10` | due 07:30 |
| `task.kinky-chores.groom-pm` | Evening grooming | `FREQ=DAILY` | `2026-05-10` | due 22:30 |
| `task.kinky-chores.haircare` | Weekly haircare | `FREQ=WEEKLY;BYDAY=SU` | `2026-05-10` | EoD |
| `task.kinky-chores.selfcare-day` | Monthly self-care day | `FREQ=MONTHLY;BYDAY=1SU` | `2026-06-07` | EoD |
| `task.kinky-chores.posture` | Posture check | `FREQ=DAILY` | `2026-05-10` | due 12:30 |
| `task.kinky-chores.hydration-1` | Hydration check | `FREQ=DAILY` | `2026-05-10` | due 09:00 |
| `task.kinky-chores.hydration-2` | Hydration check | `FREQ=DAILY` | `2026-05-10` | due 12:00 |
| `task.kinky-chores.hydration-3` | Hydration check | `FREQ=DAILY` | `2026-05-10` | due 16:00 |
| `task.kinky-chores.hydration-4` | Hydration check | `FREQ=DAILY` | `2026-05-10` | due 20:00 |
| `task.kinky-chores.discipline` | Workout — discipline session | `FREQ=WEEKLY;BYDAY=MO,WE,FR` | `2026-05-11` | due 07:00 |

**Standing tasks:**

| slot | title |
|---|---|
| `stask.kinky-chores.products` | Restock self-care products |
| `stask.kinky-chores.posture-cue` | Set posture cue at desk |
| `stask.kinky-chores.review` | Bring self-care notes to weekly review |

**Example recurring-task file (body slightly more candid, SFW title):**

`todolists/01HZ…kc/recurrences/01HZ…groom-am.md`:

```toml
+++
id = "01HZ-KC-AMGRM-XXXXXXX"
todolist_id = "01HZ-KC-XXXXXXXXXXX"
title = "Morning grooming"
default_due_time = "07:30"
dtstart = "2026-05-10"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY"
priority = 600
auto_done_at_eod = false
template_origin = "kinky-chores@1"
template_slot = "task.kinky-chores.groom-am"
+++

# Morning grooming

Full routine before the morning report at 08:30. Sir prefers the
shower-then-skin-then-hair order; trim weekly per "Weekly haircare".
Report compliance via the daily morning check-in — don't tick this
done before then.

If something gets skipped, log a note in this body and flag it at the
next check-in. Honest > tidy.
```

---

## ~~Phase TW-C — demo content (paired repos)~~ — RETIRED

~~Phase TW-C — demo content (paired repos)~~

**RETIRED** — superseded by Phase K (lifestyle wizard) per D.54;
demo mode + `demo-sub` / `demo-dom` repo seeds retire. The wizard
materializes a living calendar on first launch instead. Content
below preserved for historical/rationale reference only. See
[`draft-lifestyle-wizard.md`](draft-lifestyle-wizard.md).

<!-- retired by LW; see draft-lifestyle-wizard.md -->

### Original section continues below for reference

The demo path spins up **two coupled local-only Git repos** so the user
can explore the multi-repo + read-only-overlay feature without setting
up GitHub/Forgejo. Per Tradeoff T-4 above, each repo has `mode = "demo"`
in its app-side config row, suppressing the sync service.

### TW-C.1 `demo-sub` repo — overview

**Identity:** `bat` ("Bat"). Default-author. Mascot persona.

**Templates applied (composed in this order alphabetically, T-2):**

- `daily-chores`
- `gym-3x`
- `kinky-chores`
- `morning-bird`
- `sub-scheduled`

**Plus one extra calendar created directly (not via template):**
`Special Events` (priority 999, regular, emoji 🎉, color `#E94B6F`,
`default_notification_group = "special"`).

**Plus one extra identity:** `sir` ("Sir") — added so that some events
in the sub's repo can be authored by Sir (modeling the case where the
Dom has push access). `default_author = false`.

**Seed events for upcoming month (May 10 – June 10, 2026):**

| date | time | title | author | calendar |
|---|---|---|---|---|
| 2026-05-12 | 14:00 | Dentist | bat | Special Events |
| 2026-05-16 | 19:00 | Date night | sir | Co-schedule (with Sir)¹ |
| 2026-05-17 | 19:00 | Weekly review | sir | Co-schedule (with Sir)¹ |
| 2026-05-22 | 18:00 | Friend's birthday party | bat | Special Events |
| 2026-05-25 | 09:00 | (off — holiday) | bat | Special Events |
| 2026-05-30 | 11:00 | Brunch with M. | sir | Co-schedule (with Sir)¹ |
| 2026-06-06 | 15:00 | Monthly call | sir | Co-schedule (with Sir)¹ |
| 2026-06-08 | 09:00 | Eye-doctor appointment | bat | Special Events |
| 2026-06-10 | 18:00 | Cinema with friends | bat | Special Events |
| 2026-06-21 | 17:30 | Sir's visit | bat | Special Events² |

² **Decision SP-9 (countdown widget seed, Phase VV):** the "Sir's visit"
event ships with `pin_to_widget = true` so the demo's first-run "Add
widget?" prompt has a ready target. Title is SFW-readable; the
countdown widget reads "42 days until Sir's visit" without further
copy. Same SFW guarantee as VV.12.
*(**Replaced KI-A.9 / D.55, D.57** — seed kept; the SFW guarantee on
widget copy is RETRACTED (see KI-B / `main.md` VV.12 replacement).
The widget renders whatever the user authored verbatim. Lockscreen-
discreet behavior comes from the per-event `private = true` flag
(D.57 / VV.13), not from content-register filtering. In kink-default
wizard mode, the seed may instead be "Sir's inspection visit",
"Collar ceremony", etc.)* This entire SP-9 anchor is moot under Phase
K wizard / D.54 anyway — demo mode retires and the wizard
materializes the user's actual lifestyle on first launch, not seed
events.

¹ The sub's repo also has `master-scheduled` partially applied —
**Decision SP-8:** *(**Replaced KI-A.8 / D.55** — calendar name +
seeded titles now kink-coded by default ("Sir's schedule for Bat",
"Inspection", "Cage check", "Service report"). The neutral-mode
demo path uses the older "Co-schedule" / "Check-in" wording. This
entire SP-8 anchor is moot under D.54 — demo mode retires; see
Phase K wizard.)* when the demo composer runs `sub-scheduled` in a
repo that has *no* `master-scheduled`, it also lays down a *thin*
`Co-schedule (with Sir)` calendar with just the four named one-offs
above + the recurring "Date night" + "Weekly review" + "Monthly call"
authored by `sir`. This is so the demo shows what cross-author events
look like without forcing the user to also enable `master-scheduled`
in their own repo. Daily check-ins are *not* duplicated here — those
live in `demo-dom` and surface via the all-repos overlay view.

(Recurring events from the applied templates fill the rest of the
month — wake, gym Mon/Wed/Fri, etc. — and are already covered by the
template content above.)

**Seed standing tasks (unique to demo, beyond the templates):**

| slot | title | todolist |
|---|---|---|
| `demo.stask.bat.flat-fix` | Fix the squeaky cabinet door | Chores |
| `demo.stask.bat.book-flight` | Book flight for Sir's visit (June 21) | Chores |

### TW-C.2 `demo-sub` — example files

`identities/bat.md`:

```toml
+++
id = "bat"
display_name = "Bat"
avatar_emoji = "🦇"
email = "bat@strictlykeptboy.local"
public_keys = []
default_author = true
+++

# Bat

The mascot, here as the demo user. This is the identity stamped on
every entry created by the user in demo mode.
```

`identities/sir.md`:

```toml
+++
id = "sir"
display_name = "Sir"
avatar_emoji = "🎩"
email = "sir@strictlykeptboy.local"
public_keys = []
default_author = false
+++

# Sir

The partner identity. In demo mode, used to author the events Sir
adds to Bat's calendar (modeling shared-write access). In real use,
this is whatever person you co-schedule with.
```

`calendars/01HZ-DEMO-SUB-SPECIAL/calendar.toml`:

```toml
+++
id = "01HZ-DEMO-SUB-SPECIAL-XXXXX"
name = "Special Events"
emoji = "🎉"
color = "#E94B6F"
kind = "regular"
priority = 999
active_toggle = true
active_windows = []
active_hours = []
default_notification_group = "special"
template_origin = "demo-extra@1"
+++
```

`calendars/01HZ-DEMO-SUB-SPECIAL/events/2026/05/01HZ-DEMO-DENTIST.md` (one-off event, full body):

```toml
+++
id = "01HZ-DEMO-DENTIST-XXXXXXX"
title = "Dentist"
start = "2026-05-12T14:00:00"
end = "2026-05-12T14:45:00"
all_day = false
location = "Dr. Renner — Hauptstraße 14"
author = "bat"
calendar_id = "01HZ-DEMO-SUB-SPECIAL-XXXXX"
notifications = ["1d", "1h", "15m"]
emoji = "🦷"
+++

# Dentist

Six-month checkup. Bring insurance card. Took the early afternoon
slot to avoid evening Gym overlap.
```

`todolists/01HZ-DEMO-SUB-CHORES/standing/01HZ-DEMO-FLIGHT.md`:

```toml
+++
id = "01HZ-DEMO-FLIGHT-XXXXX"
todolist_id = "01HZ-DEMO-SUB-CHORES-XXXX"
title = "Book flight for Sir's visit (June 21)"
done = false
auto_done_at_eod = false
priority = 700
author = "bat"
+++

# Book flight for Sir's visit

Window seat. Confirm dates with Sir at the next weekly review before
booking. Aim for nonstop; the layover routes have been miserable.
```

`todolists/01HZ-DEMO-SUB-CHORES/todolist.toml`:

```toml
+++
id = "01HZ-DEMO-SUB-CHORES-XXXX"
name = "Chores"
emoji = "🧹"
color = "#7C7C7C"
priority = 400
active_toggle = true
active_windows = []
active_hours = []
template_origin = "daily-chores@1"
+++
```

### TW-C.3 `demo-dom` repo — overview

**Identity:** `sir` ("Sir"). Default-author.
**Plus identity:** `bat` ("Bat") — `default_author = false`, present
so Sir's repo knows who Bat is when surfacing co-events.

**Templates applied (alphabetical):**

- `daily-chores`
- `master-scheduled`
- `work-9-5`

**Plus one extra calendar:** `Social` (priority 700, regular, emoji
🍷, color `#B27F4A`).

**Seed events for upcoming month:**

| date | time | title | author | calendar |
|---|---|---|---|---|
| 2026-05-15 | 19:30 | Drinks with K. | sir | Social |
| 2026-05-16 | 19:00 | Date night | sir | Co-schedule (with Bat) |
| 2026-05-17 | 19:00 | Weekly review | sir | Co-schedule (with Bat) |
| 2026-05-23 | 20:00 | Theater | sir | Social |
| 2026-05-30 | 11:00 | Brunch with M. | sir | Co-schedule (with Bat) |
| 2026-06-06 | 15:00 | Monthly call | sir | Co-schedule (with Bat) |
| 2026-06-09 | 18:00 | Office happy-hour | sir | Social |

(Daily check-ins, weekday work, daily chores — covered by templates.)

### TW-C.4 `demo-dom` — example files

`identities/sir.md`:

```toml
+++
id = "sir"
display_name = "Sir"
avatar_emoji = "🎩"
email = "sir@strictlykeptboy.local"
public_keys = []
default_author = true
+++

# Sir

The partner who maintains a co-schedule with Bat. Default author for
this repo.
```

`calendars/01HZ-DEMO-DOM-MS/calendar.toml`:

```toml
+++
id = "01HZ-DEMO-DOM-MS-XXXXX"
name = "Co-schedule (with Bat)"
emoji = "🤝"
color = "#7B1FA2"
kind = "regular"
priority = 800
active_toggle = true
active_windows = []
active_hours = []
default_notification_group = "partner"
template_origin = "master-scheduled@1"
+++
```

`calendars/01HZ-DEMO-DOM-MS/recurrences/01HZ-DEMO-DOM-CKAM.md`:

```toml
+++
id = "01HZ-DEMO-DOM-CKAM-XXX"
calendar_id = "01HZ-DEMO-DOM-MS-XXXXX"
summary = "Morning check-in"
default_duration = "PT15M"
dtstart = "2026-05-10T08:00:00"
tzid = "Europe/Berlin"
rrule = "FREQ=DAILY"
notifications = ["10m", "0m"]
emoji = "🤝"
template_origin = "master-scheduled@1"
template_slot = "rec.master-scheduled.checkin-am"
+++

# Morning check-in

Daily 08:00. Bat reports first; Sir acknowledges and adjusts the day
if needed. Short.
```

`calendars/01HZ-DEMO-DOM-MS/events/2026/05/01HZ-DEMO-DOM-DATE.md` (one-off event):

```toml
+++
id = "01HZ-DEMO-DOM-DATE-XXX"
title = "Date night"
start = "2026-05-16T19:00:00"
end = "2026-05-16T22:00:00"
all_day = false
location = "Bat's place"
author = "sir"
calendar_id = "01HZ-DEMO-DOM-MS-XXXXX"
notifications = ["1d", "1h"]
emoji = "🍷"
+++

# Date night

Cooking together at Bat's. Sir picks the wine.
```

`todolists/01HZ-DEMO-DOM-CHORES/todolist.toml`:

```toml
+++
id = "01HZ-DEMO-DOM-CHORES-XXX"
name = "Chores"
emoji = "🧹"
color = "#7C7C7C"
priority = 400
active_toggle = true
active_windows = []
active_hours = []
template_origin = "daily-chores@1"
+++
```

`todolists/01HZ-DEMO-DOM-CHORES/standing/01HZ-DEMO-DOM-VISIT.md`:

```toml
+++
id = "01HZ-DEMO-DOM-VISIT-XXX"
todolist_id = "01HZ-DEMO-DOM-CHORES-XXX"
title = "Plan agenda for Bat's visit (June 21–28)"
done = false
auto_done_at_eod = false
priority = 700
author = "sir"
+++

# Plan agenda for Bat's visit

Confirm flight times with Bat at the next weekly review. Restaurant
reservations: pencil in two for the weekend, leave weeknights open.
```

### TW-C.5 demo coupling rules

- Both repos share complementary one-off events on the same dates
  (Date night 2026-05-16, Weekly review 2026-05-17, Brunch 2026-05-30,
  Monthly call 2026-06-06). The all-repos overlay view shows them as
  one merged item on each date with a "from 2 repos" badge.
- Demo mode adds both repos to the user's repo list pre-configured. The
  active repo is `demo-sub`; `demo-dom` is in read-only mode (the demo
  layer simulates this by setting `auto_sync = false` and a UI flag
  `demo_read_only = true` that disables the in-app edit affordances on
  events in that repo).
- Exit-demo-mode (per `main.md` L.4) keeps both repos as plain local
  repos the user can later push to a real provider, or wipes them.

---

## ~~Phase TW-D — wizard flow~~ — RETIRED

**RETIRED** — superseded by Phase K (lifestyle wizard, LW-A..LW-M
in [`draft-lifestyle-wizard.md`](draft-lifestyle-wizard.md)) per
D.54. The new wizard is kink-positive openly per D.55 / K-1; no
SP-1..SP-9 SFW euphemism layer in the wizard itself (those
concerns apply only to lockscreen/widget/Play-Store surfaces, with
the per-event `private = true` flag per D.57 driving lockscreen
discretion). Content below preserved for historical/rationale
reference only.

<!-- retired by LW; see draft-lifestyle-wizard.md -->

### Original section continues below for reference

## Phase TW-D (retired) — wizard flow

Phase prefix: TW-D. Each screen below maps to a Compose `Screen`
composable under
`app/src/main/kotlin/com/eight87/strictlykeptboy/wizard/`.

- [ ] **TW-D.1** Wizard navigation = a single `WizardNavHost` with a
      typed `WizardStep` enum and a `WizardState` view-model holding
      every accumulated answer. Back-stack respected. State survives
      process death (`SavedStateHandle`).
- [ ] **TW-D.2** Wizard exits to the Schedule screen on finish; can
      also be re-entered from `Settings → Templates → Apply` (TW-D.9).

### TW-D.3 Screen W1 — Welcome

- Mascot SVG centered (`R.drawable.ic_mascot_welcome` placeholder
  until real art arrives — see `main.md` Phase T.1).
- App name "strictlykeptboy" beneath mascot in display-large.
- One-line tagline: *"A calendar that lives in your Git repo."*
- Single primary button "Get started".
- Skip-link in top-right "Skip wizard" → goes straight to W4 with
  defaults (no templates).

### TW-D.4 Screen W2 — Path picker

Three full-width cards stacked vertically. Each card 96dp tall with
icon + title + one-line description.

| icon | title | description | next |
|---|---|---|---|
| 🦇 | Try the demo (no setup) | "Spin up two example repos locally — see the app fully populated." | scaffolds demo + jumps to W7 |
| 🧰 | Start with templates | "Pick the routines that fit and we'll build a starter repo." | W3 |
| 📄 | Start with empty repo | "Just a git repo with the schema files. You'll add everything yourself." | W4 (with `templates = []`) |

### TW-D.5 Screen W3 — Role toggles

Sectioned scroll. Each section is a `ToggleGroup` with a heading and
a one-line group description. Toggles inside a *mutually-exclusive*
group are radio-style (single-select); independent groups are
checkbox-style.

**Sections (with toggles, descriptions, and which template each fires):**

**Morning routine** — *radio (mutually exclusive)*

- ⭕ "Up with the sun" — *Wake early, daylight-anchored day.* → `morning-bird`
- ⭕ "Up with the moon" — *Late cycle, evening-and-night focus.* → `night-bat-owl`
- ⭕ "(none)" — skip this group

**Work** — *radio*

- ⭕ "Office hours (Mon–Fri 09–17)" — *Standard weekday work day.* → `work-9-5`
- ⭕ "Shift work" — *Rotating early/late/night shifts.* → `work-shift`
- ⭕ "(none)"

**Education** — *checkbox*

- ☐ "School / university semester" — *Class timetable + study blocks, in-semester only.* → `school-semester`

**Fitness** — *checkbox + sub-radio*

- *Gym frequency* — *radio*: ⭕ "3×/week (Mon/Wed/Fri eve)" → `gym-3x` ⭕ "5×/week (mornings)" → `gym-5x` ⭕ "(none)"
- ☐ "Swim club (Tue/Thu eve + Sat morning)" → `swim-club`
- ☐ "Soccer club (in-season Wed/Sat)" → `soccer-club`

**Practice** — *checkbox*

- ☐ "Daily music practice + weekly lesson" → `music-practice`

**Structure / relationship** — *checkbox*

- ☐ "Co-scheduled with a partner (you set check-ins)" → `master-scheduled`
  - sub-text: *"You'll add daily check-ins, a weekly review, and date night to your calendar."*
- ☐ "Receive a co-schedule from a partner" → `sub-scheduled`
  - sub-text: *"Companion task list — morning report, evening report, weekly assignments."*

**Chores** — *checkbox*

- ☐ "Daily / weekly chores" → `daily-chores`

**Self-care** — *checkbox*

- ☐ "Structured self-care routine (grooming, hydration, posture)" → `kinky-chores`

Each toggle has a chevron expander revealing: full template
description, list of calendars/todolists it creates, list of
recurrences, default notification group. Closed by default to keep
the screen scannable.

Bottom bar: "← Back" / "Continue →".

### TW-D.6 Screen W4 — Repo picker

Two top-level cards:

- "Create new repo" (default)
- "Use existing repo"

**Create new repo — fields:**

- Provider radio: GitHub / Forgejo
- Forgejo URL field (only when Forgejo selected)
- Repo name field — default `<github-username>-strictlykeptboy` if
  GitHub username is detectable from prior auth state, else
  `my-strictlykeptboy`
- Visibility radio: Private (default) / Public
- "Use HTTPS" / "Use SSH" radio (default HTTPS — easier OAuth path)

**Use existing repo — fields:**

- Repo URL field
- Auto-detected provider from URL (warning if can't detect)
- Same HTTPS/SSH choice

Bottom bar: "← Back" / "Continue →".

### TW-D.7 Screen W5 — Authentication

Branches on transport.

**HTTPS-OAuth path:**

- Header "Sign in to GitHub" (or Forgejo).
- Body: "We'll show you an 8-character code. Open the URL in your
  browser and paste the code there."
- Big primary button "Open in browser" — launches Custom Tab to the
  device-flow URL.
- Below, a 8-character code displayed in monospaced display-large with
  a copy button.
- Polling indicator under the code: "Waiting for confirmation…" with
  a spinner. Auto-advances on success.
- "Use a personal access token instead" link → opens a paste field.

**SSH path:**

- Header "Set up an SSH key".
- Body: "We've generated a fresh ed25519 key on this device. Add it to
  your GitHub account."
- Public-key block (selectable, monospaced) with copy button.
- Two primary buttons: "Upload to GitHub automatically" (uses the
  scope-`admin:public_key` token from a prior OAuth, or triggers OAuth
  inline if missing) / "I'll add it manually".
- "I'll add it manually" reveals the URL `https://github.com/settings/keys`
  and an "I've added it" confirm button.

Bottom bar shows a "Skip auth — set up later" link that lets the user
finish into demo-style local-only mode (a real local repo, not pushed).

### TW-D.8 Screen W6 — Scaffolding progress

- Mascot animation placeholder (`R.drawable.ic_mascot_busy`) — bat
  carrying a notebook, looping.
- Status list updates step-by-step:
  1. "Creating repo on GitHub…" ✓
  2. "Initializing local clone…" ✓
  3. "Writing AGENTS.md, CLAUDE.md, README.md…" ✓
  4. "Adding identities…" ✓
  5. "Applying templates: morning-bird, daily-chores…" ✓
  6. "Initial commit…" ✓
  7. "Pushing…" ✓
- On error: red status line + "Retry" + "Skip and continue" buttons.
- On success: auto-advance to W7 after 600ms.

### TW-D.9 Screen W7 — Finish

- Mascot SVG (happy variant, `ic_mascot_done`).
- Headline "Your bat is ready."
- Body "We've set up <repo-display-name>. You can apply more templates
  any time from Settings → Templates."
- Primary button "Open Schedule" → exits wizard to Schedule day view.
- Secondary link "What did we add?" → expands a summary list showing
  every calendar, todolist, recurrence count.

### TW-D.10 Re-entry — `Settings → Templates → Apply`

Same screen as W3, but:

- A repo-picker chip at the top: "Applying to: <repo-name> ▾".
- Templates already applied (per `applied-templates.toml`) are
  rendered as toggled-on with a "✓ already applied" subtitle and a
  "remove" affordance (which deletes the template's slot files; user
  must confirm).
- The "Continue" button leads directly to a confirmation screen
  (no auth screen — already authenticated) showing "We'll add: …
  We'll skip already-applied: …", then to a scaled-down W6 progress,
  then back to Settings.

---

## Phase TW-E — role-toggle composition rules

- [ ] **TW-E.1** **Apply order: alphabetical by `template.id`.**
      Deterministic and idempotent: rerunning the same set produces
      identical file contents (modulo UUIDv7 minting on first run,
      which is recorded in `applied-templates.toml`).
- [ ] **TW-E.2** **Per-template scope.** Each template owns its own
      calendars/todolists. Composer never merges two templates into
      one calendar (Tradeoff T-2).
- [ ] **TW-E.3** **Naming.** Display names use the template's chosen
      name verbatim. If a calendar with the same display name *already
      exists in the repo* and was *not* created by this template's
      slot, the new calendar is named `<name> (2)` and a banner is
      surfaced after scaffold: "We added 'Gym (2)' because 'Gym'
      already existed — rename or merge from Settings → Calendars."
- [ ] **TW-E.4** **ID strategy.** Every entity gets a fresh UUIDv7 at
      apply time. The `(template_id, slot_id) → uuid` mapping is
      recorded in `.strictlykeptboy/applied-templates.toml`.
      Re-applying reads this map: if a slot's recorded UUID still
      points at an existing file, **skip** (idempotent); if the file
      is missing (user deleted it), recreate with a *new* UUID and
      update the map.
- [ ] **TW-E.5** **Mutually-exclusive groups (Tradeoff T-1):**
      Morning-routine, Work, and Gym-frequency are radio groups in the
      wizard (TW-D.5); the composer additionally validates that no
      pair from those sets is requested in the same apply call and
      throws a `MutuallyExclusiveTemplatesError` if both are set.
- [ ] **TW-E.6** **Notification-group merging.** Multiple templates
      can request the same `notif_group` (e.g. `routine`). Composer
      treats the group as a set: first template to request it creates
      it with its default lead-times; later templates joining the same
      group inherit those lead-times. User can edit lead-times in
      Settings → Notifications post-apply.
- [ ] **TW-E.7** **Special-events calendar (Tradeoff T-2 carve-out):**
      Demo composer (and only the demo composer) creates the
      `Special Events` calendar directly, not via a template. It is
      registered in `applied-templates.toml` under a synthetic
      `demo-extra@1` template id so re-running the demo composer
      doesn't duplicate it.
- [ ] **TW-E.8** **Identity bootstrap.** Composer ensures exactly one
      identity is `default_author = true`. If the repo already has
      identities, composer doesn't add `bat` or `sir` automatically
      *unless* a template specifically references them (only the demo
      composer does). Templates other than `master-scheduled` and
      `sub-scheduled` are identity-agnostic — events get authored by
      whatever identity is currently `default_author = true`.
- [ ] **TW-E.9** **Failure handling.** Composer is transactional at
      the *commit* level: build the entire fileset in a temp tree,
      validate every file parses, then move into place and create one
      Git commit "scaffold: apply templates <list>". On any error
      mid-scaffold, abort and leave the repo in its prior state.
- [ ] **TW-E.10** **Re-apply diff preview.** When the user re-applies
      a template via Settings → Templates, before committing the
      composer renders a diff preview: "Will create N files; will
      skip M existing slots". User confirms. Same transactional commit
      semantics on confirm.

---

## Phase TW-F — wiring back into main plan

- [ ] **TW-F.1** Cross-link: `main.md` Phase K (wizard + templates)
      consumes phases TW-A, TW-B, TW-D, TW-E.
- [ ] **TW-F.2** Cross-link: `main.md` Phase L (demo content) consumes
      phase TW-C.
- [ ] **TW-F.3** Implementation deliverables:
      - `templates/` Kotlin package with one file per template (14)
      - `templates/Composer.kt` — applies a template set to a repo
      - `templates/AppliedManifest.kt` — read/write the
        `.strictlykeptboy/applied-templates.toml` file
      - `templates/demo/DemoBootstrap.kt` — creates the two demo repos
      - `wizard/` Compose package with the seven screens (W1–W7) +
        re-entry screen
- [ ] **TW-F.4** Tests:
      - Unit: each template's manifest validates (every slot has unique
        id, every recurrence's RRULE parses via lib-recur, every
        calendar's color is a valid hex).
      - Integration: composer applied to an empty repo produces files
        whose round-trip read/write is byte-identical.
      - Integration: re-applying a template is a no-op (commit count
        unchanged).
      - Integration: demo bootstrap produces two repos whose
        cross-references (Date night, Weekly review, Brunch, Monthly
        call) match on date+time.
      - UI smoke (mobile-mcp): wizard end-to-end with templates path
        produces a populated Schedule view.

---

## ~~Phase TW-G — demo specifics (referenced by main Phase L)~~ — RETIRED

**RETIRED** — Phase L (demo content) is retired per D.54; demo
mode is gone. The wizard's scaffold output IS the user's first-run
data. Content below preserved for reference only.

<!-- retired by LW; see draft-lifestyle-wizard.md -->

### Original section continues below for reference

## Phase TW-G (retired) — demo specifics

- [ ] **TW-G.1** Demo bootstrap is a one-shot operation gated by an
      app-prefs flag `demo_initialized`. Re-running the demo path in
      the wizard offers "Reset demo" if already initialized.
- [ ] **TW-G.2** Demo repos live under
      `${context.filesDir}/demos/demo-sub` and `…/demo-dom`. Both are
      `git init`'d (non-bare), with two commits each: the initial
      scaffold commit + a "demo content" commit. No remotes
      configured.
- [ ] **TW-G.3** Demo identity files (`bat.md`, `sir.md`) include
      empty `public_keys = []` — demo never pushes, so keys are
      irrelevant. Real-mode identities populate keys from the active
      auth setup.
- [ ] **TW-G.4** Demo events for the upcoming month are rebased to
      *the actual current month* at scaffold time. Concretely, the
      composer reads `LocalDate.now()` and shifts every dtstart so
      that the calendar always looks "live". This avoids users
      installing the app a year after release and seeing a 2026 demo.
- [ ] **TW-G.5** "Exit demo mode" (main L.4) presents three options:
      "Keep both repos as local-only" (drops `mode = "demo"` flag,
      enables manual edit), "Push demo-sub to a real provider"
      (re-enters wizard W4–W6 targeting `demo-sub`), "Wipe demo"
      (deletes both directories + repo entries).

---

## ~~Phase TW-H — open verification~~ — RETIRED

**RETIRED** per D.54 (demo verification scope moot — Phase K
wizard testing strategy LW-M.1..LW-M.9 replaces it). Content
preserved for reference.

<!-- retired by LW; see draft-lifestyle-wizard.md -->

### Original section continues below for reference

## Phase TW-H (retired) — open verification

- [ ] **TW-H.1** Read-back: confirm all 14 templates have content
      sections, an example file, and slot tables. (Verified at write
      time — count: 14.)
- [ ] **TW-H.2** Confirm SFW surface: every template title, calendar
      name, todolist name, and event/task title is recruiter-readable
      neutral. (Verified at write time — surface phrasing decisions
      SP-1 through SP-8 applied throughout.)
- [ ] **TW-H.3** Confirm cross-reference: every example TOML file
      uses fields consistent with the schema sketch in the SA-5 brief
      and `decisions.md` D.4. Once `data-model.md` ships from SA-1,
      sweep this doc for any field-name drift.
- [ ] **TW-H.4** Confirm decisions.md compliance: no decision in this
      doc contradicts D.1–D.22. (Self-check: D.13 wizard flow
      preserved; D.5 active-windows used by `school-semester` and
      `soccer-club`; D.11 todolist model used by `sub-scheduled`,
      `daily-chores`, `kinky-chores`, `gym-3x`, `gym-5x`,
      `music-practice`; D.6 RRULE strings spec-compliant; D.14
      notification-group semantics applied.)

---

## Decisions summary (pulled inline above; restated here)

| ID | Decision | Rationale |
|---|---|---|
| SP-1 | Dom display name "Sir" (id `sir`) | Recruiter-safe; one-syllable; charged-in-context |
| SP-2 | Sub display name "Bat" (id `bat`) | Mascot fit; reads as nickname |
| SP-3 | Couple-events titled neutrally (Check-in / Weekly review / Date night / Monthly call) | SFW-readable surface; charge in the structure |
| SP-4 | `kinky-chores` SFW titles, candid bodies | Bodies aren't surfaced in store screenshots |
| SP-5 | `sub-scheduled` references "your partner" when no Dom configured | Avoids requiring identity bootstrap on this template |
| SP-6 | `work-shift` ships three concurrent DAILY-INTERVAL=8 RRULEs with body-doc explanation | RFC5545 doesn't natively express 4-on/4-off rotation; honest > magic |
| SP-7 | `master-scheduled` writes its calendar in the *applying* repo only; cross-repo overlay via D.9 | Avoids double-writes; v1-friendly |
| SP-8 | `sub-scheduled` in demo also lays a thin `Co-schedule (with Sir)` calendar with one-off cross-author events | Lets demo show cross-author behavior without forcing both relationship templates on |
| T-1 | Morning-routine, Work, Gym-frequency are radio groups | Prevents collision between mutually-exclusive templates |
| T-2 | Each template owns its own calendars/todolists | Idempotency tractable; no merge-magic |
| T-3 | `applied-templates.toml` lives in repo (with app-prefs cache mirror) | Survives reinstall + multi-device |
| T-4 | Demo repos are real local Git repos with `mode = "demo"` flag | UI realism without push side-effects |
| T-5 | Idempotency keyed on recorded UUID, not display name | Survives user-renames |

**Round 4 retraction-status (see top of this file):**

| ID | Status | Reference |
|---|---|---|
| SP-1 | Replaced (same default, different rationale) | KI-A.1 / D.55 |
| SP-2 | Kept | KI-A.2 / D.55 |
| SP-3 | RETRACTED | KI-A.3 / D.55 |
| SP-4 | RETRACTED | KI-A.4 / D.55 |
| SP-5 | Replaced (default = "your Dom" w/ override) | KI-A.5 / D.55 |
| SP-6 | Kept (architectural) | KI-A.6 |
| SP-7 | Kept (architectural) | KI-A.7 |
| SP-8 | Replaced (kink-coded defaults) — moot under D.54 | KI-A.8 / D.55 / D.54 |
| SP-9 | Replaced (SFW guarantee retracted) — moot under D.54 | KI-A.9 / KI-B / D.57 / D.54 |

---

## Phase TW-I — atomic activity templates (Round 4 addition)

See [`draft-atomic-activities.md`](draft-atomic-activities.md)
phases AT-D / AT-E / AT-F for the authoritative content. Three new
template files ship at scaffold time per Phase K (lifestyle
wizard) and Phase XX (atomic activities, inverted habits):

- `templates/atomic-self-care.toml` — `neutral_safe = true`. Brush-
  teeth (with 7 sub-beats totalling 200s in the 5-min envelope),
  shower, shave-face, shave-pubes (`tags = ["intimate-care"]`
  neutral hygiene, NOT `kink`), hair, skincare, deodorant, nail-
  care, ear-clean. See AT-D.
- `templates/atomic-kink-self-care.toml` — `neutral_safe = false`,
  every entry `tags = ["kink"]`. Cage-check (3×/day default),
  plug-check (2×/day default), posture-check (hourly-waking),
  collar-check, edge-and-stop, kegels (with 3 sub-beats), pubes-
  grooming, body-grooming. See AT-E.
- `templates/atomic-workout.toml` — `neutral_safe = true`. Pushups,
  situps, squats, pull-ups, planks (hold-seconds instead of reps),
  burpees. Sub-beat = one per set; phone buzzes between sets. See
  AT-F.

**Wizard composition rule (Phase K LW-G):** the smart-default
toggle matrix per `(alignment, lifestyle)` pre-toggles the
relevant atomic-activity entries per role. Neutral-mode (D.58 /
K-3) hides every `neutral_safe = false` template and skips
entries tagged `kink` from any template that would otherwise
apply.

**Default-routine scaffolding (AT-G.2):** when the relevant role
toggle is on, scaffold `routine-morning`, `routine-bed`,
`routine-workout` calendars with `routine = true`,
`active_toggle = false`, pre-populated with atomic events from
the selected templates. Materialize-on-demand via Phase XX.8
("Start X routine" quick-start) keeps these from double-rendering.

**Sub-beat sticker IDs:** the brush-teeth 6-quadrant sub-beat
sequence + the workout-pushup down/up animated pair must exist in
every default avatar pack per Phase WW.1 (AV-G.4) build-time
validator.

**Wizard "Pick your species" step + per-event sticker override
demo:** the new species-pick step lands inside Phase K Screen 2
(LW-C, was originally proposed in AV integration notes). Bat
preselected by default per D.64.

---

## Phase TW-J — Lifestyle templates (Round 5; main.md Phase AAA)

See [`draft-household-travel-vacation.md`](draft-household-travel-vacation.md) phases HV-A / HV-B / HV-C / HV-D / HV-K / HV-L / HV-P for the authoritative content. Eight new template files ship at scaffold time per Phase K and Phase XX. All carry `neutral_title` per entry and follow the AT-D / AT-E / AT-F schema. Per HV-J.1 / HV-J.11 / HV-J.20.

- [ ] **TW-J.1** `templates/atomic-household.toml` per HV-A. `neutral_safe = true`. ~88 base entries + 4 kink variants across 13 categories (trash variants, laundry cycle with sub-beats, dishes/kitchen, bathroom with quadrant sub-beats, bedroom, living spaces, entry/mudroom, outdoor, mail/paperwork, pantry/fridge, pets, seasonal, misc). Wizard role-toggle: "household chores".
- [ ] **TW-J.2** `templates/atomic-travel-prep.toml` per HV-B. `neutral_safe = true`. ~52 entries across 7 lead-time tiers (T-90d/30d/14d/7d/3d/1d/0). Parameterized by `TripDraft` — lead-offsets back-fill to absolute dates at materialization. Conditional entries gated on `visa.required`, `kink_kit.requested`, etc.
- [ ] **TW-J.3** `templates/atomic-flight-day.toml` per HV-C. `neutral_safe = true`. 19 parameterized entries per flight leg (wake-up, transport, airport-arrive, check-in, security, lounge, board, taxi, fly, arrive, customs, baggage, hotel-shuttle). Pre-airport buffer 120/180 min toggle.
- [ ] **TW-J.4** `templates/atomic-vacation-daily.toml` per HV-D. `neutral_safe = false` (4 kink anchors included). 15 anchors: 11 self-care + 4 kink. Slob-drift prevention; meal-anchor configurable; kink anchors filtered when neutral-mode or `unaligned-private`.
- [ ] **TW-J.5** `templates/atomic-adhd-anchors.toml` per HV-K. `neutral_safe = true`. 34 anchors across 6 categories: hydration 5, food 5, meds adherence 8, body-state 5, cognitive 4, sleep-hygiene 4, maintenance 6 (incl. hyperfocus-recovery trigger). Wizard role-toggle: "ADHD anchors".
- [ ] **TW-J.6** `templates/atomic-medication.toml` per HV-L.A. `neutral_safe = true`; ALL entries `privacy_flag = true` by default; ALL entries carry the `nonSuperseable` tag per D.76. 22 management entries (supply chain, emergency stash, vaccinations, specialist follow-ups, preventative screenings, bodywork). Wizard role-toggle: "managing meds" → surfaces a per-med wizard sheet for adherence entries from HV-K.4.
- [ ] **TW-J.7** `templates/atomic-menstrual-cycle.toml` per HV-L.B. `neutral_safe = true`; ALL entries `privacy_flag = true` by default. 12 entries (cycle anchors, contraception, preventative screenings, supplies). Wizard role-toggle: "menstrual cycle"; sub-toggle "pause kink-care during period?" wires `cal-period-grace` per HV-L.B.5.
- [ ] **TW-J.8** `templates/atomic-leisure.toml` per HV-P + D.87. `neutral_safe = false` (caged-play affordances; gated). ~46 entries across 8 categories (passive-consumption 10, active-play 8, movement-play 8, social-recreation 8, solo-decompress 7, caged-play-affordances 3 incl. one kink variant, recovery-leisure 4, long-cycle-leisure-cadence 4). The `dog-walk` atomic carries 7 named sub-beats (leash / poop-bag / water-for-dog / route-pick / actual-walk / post-walk-dog-water / paw-wipe). ALWAYS scaffolded (leisure first-class). Role-toggle "has a dog" enables `dog-walk` + `dog-play-time` family; role-toggle "owns a video-game console / PC gaming" enables `video-games-session` family incl. kink variant *"good boy gets video-game time while caged"* (gated on K-mode); sub-toggle "kink leisure affordances" gates cage-comfort-check + locked-leisure-marker + safeword-aware-leisure (default OFF until K-mode ON).
- [ ] **TW-J.9** System `cal-briefings` calendar per HV-N.6 / D.81. Auto-scaffolded by wizard at default `active_toggle = true` (user disable in Settings → Notifications). Two recurring events (`morning-briefing` 07:00 daily, `evening-briefing` 21:00 daily) with auto-generated bodies (NS-Z.7); non-user-editable.

---

## Phase TW-K — LW Screen 3.5 (Round 5; main.md Phase K.5a + Phase DDD)

See HV-R.2 / HV-J.20 and `decisions.md` D.83.

- [ ] **TW-K.1** New wizard screen **LW Screen 3.5 — Praise + pronouns** inserted between LW-D Alignment and LW-E Lifestyle. Writes `identity.toml` at calendar-repo root on save.
- [ ] **TW-K.2** Fields per HV-R.2: praise picker (10 default chips + custom, multi-select for alternation → `[praise].alt_terms`); pronouns (he/she/they/it/custom + extra sets); honorific (Sir/Daddy/Master/Mistress/Owner/Keeper/Captain/custom — SKIPPED if alignment = dominant or unaligned-private); tone register (soft-kinky default for kinky alignments, warm-neutral for unaligned-private); emoji density (default medium).
- [ ] **TW-K.3** Bat-mascot sticker `bat-holding-name-tag` (ears tilted, fang-flash) — NEW LW-K sticker beat added to LW-K's sticker list; neutral variant `bat-with-clipboard` for `tone.register = "warm-neutral"` and below.
- [ ] **TW-K.4** Locked default on repo creation (when wizard skipped or for first-launch defaults per HV-R.1.3): `praise.term = "good boy"`, pronouns he/him/his/himself, `Sir`, `soft-kinky`, `medium` emoji density.

---

## Phase TW-L — Vacation wizard sticker beats (Round 5; main.md Phase CCC)

See HV-H. Per HV-J.20. SIX new bat-mascot sticker beats added to LW-K's sprite sheet:

- [ ] **TW-L.1** `trip-suitcase-waving` — bat with tiny suitcase waving paw. Used on HV-F.2 (Screen 1 — Trip basics).
- [ ] **TW-L.2** `flight-paw-prints` — paw prints curving across a cloud. Used on HV-F.3 + HV-F.4 (Screens 2 + 3).
- [ ] **TW-L.3** `beach-loungin-with-cage-still-on` — bat on a pool float, tiny cage visible. NEUTRAL VARIANT `beach-loungin` (no cage). Switched at render-time by K-mode. Used on HV-F.5 (Screen 4).
- [ ] **TW-L.4** `supersedence-snooze-toggle` — bat tucking a tiny calendar under a blanket. Used on HV-F.6 (Screen 5).
- [ ] **TW-L.5** `confirm-tail-flick` — bat tail-flick with thumbs-up paw. Used on HV-F.7 (Screen 6 — Confirm).
- [ ] **TW-L.6** `good-boy-stays-good-boy-on-vacation` — bat in sunglasses brushing tiny teeth with a determined face. NEUTRAL VARIANT `staying-on-track`. Used on HV-F.7 reassurance bubble.
- [ ] **TW-L.7** All new sticker assets follow LW-K's SVG-with-PNG-fallback pipeline, 64×64 + 128×128 + 256×256 export sizes.

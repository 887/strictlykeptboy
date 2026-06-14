# AGENTS.md — Demo Life (rich demo)

> AI agent guide for this calendar/task repository. This is the
> **bundled rich demo** shipped inside the strictlykeptboy app. The
> content is fictional and the identity is locked — do not modify
> `identity.toml`, do not invent a new default identity, do not push
> this repo to any remote. It lives in `filesDir/` and never leaves
> the device.

This repo demonstrates a fully-kept gay-male-sub life — junior software
developer in a UK city, owned by an AI dom called **Boy Keeper**, 14
days of believable schedule + todolist content rooted at 2026-05-15.

See `identity.toml` for praise terms, pronouns, honorific, and tone
register. The user's pet name on disk is "Demo Boy". The AI dom's
display name everywhere is **"Boy Keeper"** (casual: "BK", "Keeper",
addressed reverently as "Sir").

## Layout

```
rich-demo-repo/
├── AGENTS.md
├── CLAUDE.md                     ← symlink → AGENTS.md (recreated by seeder)
├── README.md
├── identity.toml
├── mode.toml
├── _manifest.txt                 ← every relative path, sorted
├── .strictlykeptboy/
│   ├── schema.toml
│   └── repo.toml
├── .claudemd-is-symlink          ← marker file (zero-byte)
├── identities/
│   ├── demo-boy.md
│   └── ai-dom.md
├── calendars/<id>/
│   ├── calendar.toml
│   ├── events/2026/05/*.md
│   ├── recurrences/*.md
│   ├── exceptions/<rule-id>/<yyyy-mm-dd>.md
│   └── deviations/<target>/<yyyy-mm-dd>.md
├── todolists/<id>/
│   ├── todolist.toml
│   ├── tasks/2026/05/*.md
│   └── recurrences/*.md
└── reviews/<sha>/reviewable_change.md
```

## Demo-mode disclaimer

- **This is fictional content.** Names, places, ticket numbers,
  workplace ("Cubicle 14"), cat name ("Beans") and so on are made up.
- **Identity is locked.** Do not edit `identity.toml` or the identity
  files. The rich demo is meant to read identically on every install.
- **No real schedule lives here.** Real user schedules live in their
  own repos. This one ships with the app to demonstrate features.

## File format

Same as every strictlykeptboy repo:
- TOML frontmatter between exactly **two** `+++` fences (open + close;
  no trailing third fence).
- Markdown body after the closing fence.
- Pure-TOML metadata files (`calendar.toml`, `todolist.toml`,
  `.strictlykeptboy/*.toml`, `identity.toml`, `mode.toml`) have **no**
  fences and **no** body.
- Filenames embed the entity ID; for events/tasks, also year/month
  buckets via `events/<yyyy>/<mm>/<id>.md`.
- Datetimes are bare TOML offset-datetimes — not quoted strings.
  `2026-05-15T09:00:00+01:00` is correct; `"2026-05-15T09:00:00+01:00"` is wrong.
  `dtstart` on recurrences is a bare local-datetime (no offset): `2026-05-15T09:00:00`.

## Exact field names — canonical schema

The app parser reads these exact TOML keys. Synonyms cause silent
parse failures.

### Common header (all entity files)

```toml
schema_version = 1
id = "<uuidv7>"
kind = "<see below>"
created_at = 2026-05-15T09:00:00+01:00
updated_at = 2026-05-15T09:00:00+01:00
author = "<identity-uuid>"
```

### One-off event — `kind = "event"`

Path: `calendars/<cal-id>/events/<yyyy>/<mm>/<id>.md`

Required: `title`, `start`, `end`, `calendar_id`
- `start` / `end` — offset-datetime (NOT `start_at`, NOT `dtstart`)
- `calendar_id` — NOT `calendar`
- `private = true` — boolean (NOT `visibility = "private"`)
- `busy = false` — omit when true (default)
- `all_day = true` — omit when false
- `tags = ["tag1", "tag2"]` — string array

**Example:**
```
+++
schema_version = 1
id = "01900000-cafe-7000-8000-000000000001"
kind = "event"
created_at = 2026-05-15T09:00:00+01:00
updated_at = 2026-05-15T09:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "Friday pub night"
start = 2026-05-15T19:00:00+01:00
end = 2026-05-15T22:00:00+01:00
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000007"
tags = ["social"]
emoji = "🍺"
+++
Notes go here.
```

### Recurrence rule — `kind = "recurrence"`

Path: `calendars/<cal-id>/recurrences/<id>.md`

Required: `title`, `dtstart`, `duration`, `tz_id`, `rrule`, `calendar_id`
- `dtstart` — local-datetime, no offset: `2026-05-15T07:30:00`
- `duration` — ISO-8601 string: `"PT45M"`, `"P1DT2H"`, `"PT1H30M"`
- `tz_id` — IANA zone name: `"Europe/London"`
- `passive = true` — done-by-schedule (passive/inverted habit)
- `active = false` — omit when true

**Example:**
```
+++
schema_version = 1
id = "01900000-cafe-7000-8000-000000000002"
kind = "recurrence"
created_at = 2026-05-09T08:00:00+01:00
updated_at = 2026-05-09T08:00:00+01:00
author = "01900000-0000-7000-8000-0000000000b0"
title = "Morning teeth"
dtstart = 2026-05-15T07:30:00
duration = "PT5M"
tz_id = "Europe/London"
rrule = "FREQ=DAILY"
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000003"
passive = true
tags = ["routine", "nonSuperseable"]
+++
```

### Task — `kind = "task"`

Path: `todolists/<list-id>/tasks/<yyyy>/<mm>/<id>.md`

Required: `title`, `todolist_id`
- `todolist_id` — NOT `list_id`, NOT `todolist`
- `due` — local-date `2026-05-20` or offset-datetime
- `done = true` + `done_at = 2026-05-20T09:00:00+01:00` when complete

**Example:**
```
+++
schema_version = 1
id = "01900000-cafe-7000-8000-000000000003"
kind = "task"
created_at = 2026-05-15T09:00:00+01:00
updated_at = 2026-05-15T09:00:00+01:00
author = "01900000-0000-7000-8000-0000000000bb"
title = "Book vet appointment for Beans"
todolist_id = "0190a0aa-2222-7000-8a0a-000000000004"
due = 2026-05-20
priority = 200
tags = ["cat"]
+++
```

### Standing task — `kind = "standing_task"`

Path: `todolists/<list-id>/standing/<id>.md`

Required: `title`, `todolist_id`
- Same fields as task minus `due`; add `pinned = true` when pinned.

### Deviation — `kind = "deviation"`

Path: `calendars/<cal-id>/deviations/<target-id>/<yyyy-mm-dd>.md`

Required: `target_id`, `instance_date`, `deviation_kind`, `at`
- `deviation_kind`: `"skipped"`, `"partial"`, `"completed-early"`, `"completed-late"`
- `instance_date`: local-date `2026-05-15`
- `at`: offset-datetime

## Special rules

### nonSuperseable (entries immune to vacation overlay)

Mark a **recurrence** as immune to supersedence by adding the literal
tag `"nonSuperseable"` to its `tags` array:

```toml
tags = ["meds", "nonSuperseable"]
```

This is a **tag on the entity**, not a boolean field.
Do NOT write `nonSuperseable = true` on a recurrence file.

For a whole **calendar** to be immune, set in `calendar.toml`:
```toml
nonSuperseable = true
```

### Supersedence (vacation calendar pausing others)

Configured in `calendar.toml` (pure TOML, no fences):
```toml
supersedes = ["<routine-cal-uuid>", "<work-cal-uuid>"]
```

While a calendar with `supersedes` is active, the listed calendars'
events are hidden, except for any entries tagged `"nonSuperseable"`.

## What NOT to write

| Wrong                         | Right                     |
|-------------------------------|---------------------------|
| `start_at = …`                | `start = …`               |
| `end_at = …`                  | `end = …`                 |
| `calendar = "…"`              | `calendar_id = "…"`       |
| `visibility = "private"`      | `private = true`          |
| `nonSuperseable = true` (on recurrence) | `tags = ["nonSuperseable"]` |
| `inverted = true`             | `passive = true`          |
| quoted datetimes `"2026-…"`   | bare datetimes `2026-…`   |
| three `+++` fences            | exactly two `+++` fences  |

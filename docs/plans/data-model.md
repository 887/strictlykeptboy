# strictlykeptboy — data model deep-dive

## Status: 🚧 IN-PLANNING

This is the field-by-field, file-by-file specification for everything that
lives inside a user-data repository produced or consumed by
strictlykeptboy. It is the authoritative supplement to
[`decisions.md`](decisions.md) §D.3, §D.4, §D.6, §D.15, §D.20.

Every section is decided. Where an unforeseen tradeoff appeared during
elaboration, the resolution is inline as **Decision:** with a rationale —
no punts back to the user, no `TBD`. Phases below carry the prefix `DM-`
and map onto `Phase C` of [`main.md`](main.md).

**Cross-reference table** — each `DM-` phase ↔ `main.md` Phase C sub-step:

| DM phase | main.md sub-step | Topic |
|---|---|---|
| DM-A | C.1 | TOML frontmatter parser/writer |
| DM-B | C.2 | Schema typed objects (every entity) |
| DM-C | C.3 | UUIDv7 + filename builder + bucketing |
| DM-D | C.4 | Read-side scanner |
| DM-E | C.5 | Write-side serializer |
| DM-F | C.6 | Schema versioning + migration runner |
| DM-G | C.7 | AGENTS.md / CLAUDE.md content emitted into user repos |
| DM-H | C.8 | Validation + broken-entries tray |

---

## Phase DM-A — frontmatter parser / writer

Goal: a round-trip-safe TOML frontmatter parser sitting on top of `ktoml`,
plus a body splitter, plus a writer that preserves key ordering and
trailing whitespace conventions.

- [ ] **DM-A.1** Implement `FrontmatterDoc` data class: `frontmatter:
      TomlTable`, `body: String`, `eolStyle: EolStyle`, `keyOrder:
      List<String>`, `trailingNewlines: Int`.
- [ ] **DM-A.2** Implement `FrontmatterReader.parse(text: String):
      FrontmatterDoc`. Strict format:
  - File MUST start with the literal three-character fence `+++`
    followed by a newline (LF or CRLF).
  - TOML body follows until a closing line containing exactly `+++`
    (no leading whitespace, no trailing chars besides the newline).
  - Markdown body follows after the closing fence.
  - **Decision:** if the file does NOT start with `+++`, treat the
    whole file as `body` with an empty frontmatter table — but flag
    it as `MalformedKind.NoFrontmatter` so the validator (DM-H) can
    raise it as a broken entry. Rationale: tolerant reads keep
    hand-written notes from disappearing; the validator is where we
    enforce.
- [ ] **DM-A.3** Implement `FrontmatterWriter.serialize(doc:
      FrontmatterDoc): String`. Rules:
  - Always emit LF; if `eolStyle = CRLF`, post-process to CRLF for the
    whole file. **Decision:** default to LF on writes regardless of
    incoming style — rationale: cross-platform Git repos with mixed
    EOLs are a footgun; we normalise.
  - Always emit fence `+++\n`, then TOML body, then `+++\n`, then
    body. Body always ends with exactly one trailing newline.
  - Preserve `keyOrder` for top-level keys present at read time. New
    keys append in declared schema order (DM-B).
- [ ] **DM-A.4** Comment preservation: ktoml's `KtomlConf` does not
      retain comments across parse/write. **Decision:** v1 explicitly
      drops comments inside frontmatter on app rewrite; document this
      in `AGENTS.md` so a hand-author knows comments survive only if
      no app write touches the file. Rationale: building a comment-
      preserving TOML round-tripper is a multi-week project; not in v1
      scope. Track as a future schema-version-independent concern.
- [ ] **DM-A.5** Property-based round-trip tests: for every entity
      schema (DM-B), generate random valid instances, serialise →
      parse → assert structurally equal.

```toml
# Example frontmatter fence layout (the fences are literal, body below):
+++
schema_version = 1
id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"
kind = "event"
+++
```

```markdown
+++
schema_version = 1
id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"
kind = "event"
title = "Dentist"
start = 2026-05-12T14:00:00+02:00
end = 2026-05-12T14:45:00+02:00
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000001"
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"
+++

Reminder: bring last X-rays. Park behind the building (gate code 4488).
```

---

## Phase DM-B — schema definitions (typed objects)

Every entity has: a Kotlin sealed-class hierarchy `EntityFile`, a
serialiser to `FrontmatterDoc`, a deserialiser from `FrontmatterDoc`, a
validator (DM-H), and a worked example below.

Common header fields shared by every entity file:

| Field | Type | Required | Default | Validation |
|---|---|---|---|---|
| `schema_version` | int | yes | (current = 1) | `1 ≤ v ≤ LATEST_SCHEMA` |
| `id` | string (UUIDv7) | yes | — | RFC 9562 UUIDv7 lowercase |
| `kind` | string enum | yes | — | one of `event` / `recurrence` / `exception` / `task` / `standing_task` / `task_recurrence` / `calendar` / `todolist` / `identity` / `repo_meta` / `schema_meta` |
| `created_at` | offset-datetime | yes | now | RFC 3339 |
| `updated_at` | offset-datetime | yes | now | RFC 3339, `≥ created_at` |
| `author` | string (UUIDv7) | yes for content; optional for meta | active identity | must reference an existing `identities/<id>.md` |

**Decision:** UUIDv7 used everywhere — no separate slug field. Rationale:
filenames already sort chronologically by ID, so a separate slug invites
duplication and rename hazards. UI surfaces are keyed off the `title`
field (DM-B.1) for display; the ID is opaque.

### DM-B.1 — Event (`events/<yyyy>/<mm>/<id>.md`)

- [ ] **DM-B.1.1** Define `EventFile` data class.
- [ ] **DM-B.1.2** Validator covers all fields below.
- [ ] **DM-B.1.3** Worked example written to disk for golden-file test.

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| (header fields) | — | — | — | DM-B header |
| `kind` | `"event"` | yes | — | constant |
| `title` | string | yes | — | non-empty, ≤ 200 chars |
| `start` | offset-datetime | yes | — | RFC 3339 |
| `end` | offset-datetime | yes | — | `≥ start`; if all-day, see `all_day` |
| `all_day` | bool | no | false | when true, `start`/`end` MAY be local-date strings |
| `calendar_id` | string (UUIDv7) | yes | — | FK to `calendars/<id>/calendar.toml` |
| `location` | string | no | — | free text |
| `location_geo` | inline table | no | — | `{ lat = 0.0, lon = 0.0 }` |
| `attendees` | array of strings | no | `[]` | identity IDs (`identities/<id>.md`) or free-form names |
| `notifications` | array of strings | no | `[]` | duration shorthand (`"1h"`, `"15m"`, `"1d"`) |
| `notification_group` | string | no | calendar's default | overrides calendar group |
| `attachments` | array of inline tables | no | `[]` | see DM-B.10 |
| `spawns_task` | string (UUIDv7) | no | — | task ID this event creates / refreshes |
| `spawns_task_in_todolist` | string (UUIDv7) | no | — | required iff `spawns_task` is set |
| `tags` | array of strings | no | `[]` | lowercase, `[a-z0-9_-]+` |
| `color` | string | no | calendar color | `#rrggbb` |
| `emoji` | string | no | — | one grapheme cluster |
| `priority_override` | int | no | — | 1..1000; overrides calendar priority for this event |
| `busy` | bool | no | true | false = transparent (does not block common-time) |
| `external_uid` | string | no | — | iCal UID for round-tripping `.ics` |

**Decision:** `start`/`end` are stored as offset-datetimes in the event's
authoring timezone offset, NOT as `local-datetime + tz`. Rationale: TOML
offset-datetime parses unambiguously across all libs; local-datetime +
tz forces every consumer to ship a tz database. The offset is recorded
at creation time, so historical events are stable across DST transitions
in display. The `tz_id` field (IANA name) is OPTIONAL and only set on
recurrence rules where DST-aware expansion matters (DM-B.3).

```toml
+++
schema_version = 1
id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"
kind = "event"
created_at = 2026-05-09T18:30:00+02:00
updated_at = 2026-05-09T18:30:00+02:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

title = "Dentist — 6-month checkup"
start = 2026-05-12T14:00:00+02:00
end = 2026-05-12T14:45:00+02:00
all_day = false
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000001"
location = "Dr. Köhler, Hauptstraße 14, Stuttgart"
location_geo = { lat = 48.7758, lon = 9.1829 }
notifications = ["1d", "2h", "15m"]
notification_group = "personal-health"
tags = ["health", "dental"]
emoji = "🦷"
busy = true
external_uid = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001@strictlykeptboy"

[[attachments]]
sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
filename = "last-xray.png"
mime = "image/png"
size = 184320
+++

Reminder: bring the last X-ray printout. Park behind the building, gate
code 4488. Booked at 2025-11-12 by phone. Insurance card needed.
```

### DM-B.2 — Recurrence rule (`calendars/<cal>/recurrences/<rule-id>.md`)

- [ ] **DM-B.2.1** Define `RecurrenceFile` data class.
- [ ] **DM-B.2.2** Round-trip RRULE through `org.dmfs:lib-recur`.
- [ ] **DM-B.2.3** Validator: RRULE parses; `dtstart` valid; `tz_id`
      exists in IANA tz database.

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| (header fields) | — | — | — | — |
| `kind` | `"recurrence"` | yes | — | constant |
| `title` | string | yes | — | as event |
| `dtstart` | local-datetime | yes | — | first occurrence start (TZID applied) |
| `duration` | string | yes | — | ISO 8601 duration, e.g. `"PT45M"` |
| `tz_id` | string | yes | device tz | IANA tz name |
| `rrule` | string | yes | — | RFC5545, e.g. `"FREQ=WEEKLY;BYDAY=TU;UNTIL=20271231T235959Z"` |
| `rdate` | array of local-datetime | no | `[]` | additional explicit instances |
| `exdate` | array of local-datetime | no | `[]` | inline exclusions; persistent exceptions still preferred |
| `calendar_id` | string (UUIDv7) | yes | — | FK |
| `location` | string | no | — | as event |
| `notifications` | array of strings | no | `[]` | applies to every instance |
| `notification_group` | string | no | calendar default | — |
| `attachments` | array of inline tables | no | `[]` | as event |
| `spawns_task` | string (UUIDv7) | no | — | task spawned per occurrence |
| `spawns_task_in_todolist` | string (UUIDv7) | no | — | as event |
| `tags` / `color` / `emoji` / `busy` / `priority_override` | as event | no | as event | — |

**Decision:** prefer persistent `exceptions/<rule-id>/<yyyy-mm-dd>.md`
files over inline `exdate`/`rdate` arrays. The inline arrays exist for
iCal import compatibility only. The app's UI never writes to those
arrays. Rationale: persistent exception files give per-instance
traceability, are git-mergeable per file, and let humans annotate why
an instance was changed.

```toml
+++
schema_version = 1
id = "0190d4ab-2b7a-7c50-9c1e-cccccccccccc"
kind = "recurrence"
created_at = 2026-01-04T10:00:00+01:00
updated_at = 2026-01-04T10:00:00+01:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

title = "Standup"
dtstart = 2026-01-05T09:30:00
duration = "PT15M"
tz_id = "Europe/Berlin"
rrule = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000010"
location = "Meet — work-room"
notifications = ["5m"]
tags = ["work", "ritual"]
emoji = "🧍"
busy = true
+++

Daily standup — 5 minutes per person, blockers first, no demos.
```

### DM-B.3 — Exception (`calendars/<cal>/exceptions/<rule-id>/<yyyy-mm-dd>.md`)

- [ ] **DM-B.3.1** Define `ExceptionFile` data class with `kind` enum.
- [ ] **DM-B.3.2** Resolver consults exceptions when materialising
      recurrences (see [`resolver.md`](resolver.md)).

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| (header fields) | — | — | — | — |
| `kind` | `"exception"` | yes | — | constant |
| `rule_id` | string (UUIDv7) | yes | — | FK to recurrence file |
| `instance_date` | local-date | yes | — | the original instance's local date in the rule's `tz_id` |
| `mode` | string enum | yes | — | `cancel` / `override` / `note` |
| `override_start` | offset-datetime | when mode=override | — | replaces start |
| `override_end` | offset-datetime | when mode=override | — | replaces end |
| `override_title` | string | no | — | when mode=override or note |
| `override_location` | string | no | — | — |
| `override_notifications` | array of strings | no | — | — |
| `override_attachments` | array of inline tables | no | — | — |

**Decision:** the filename uses the `instance_date` as the resolver's
key (NOT the override's new date). Rationale: cancellation needs to
identify *which* original instance was suppressed even after
override_start moves the time; binding to instance_date keeps the file
discoverable and idempotent across re-edits.

```toml
+++
schema_version = 1
id = "0190d4cd-3333-7c50-9c1e-eeeeeeeeeeee"
kind = "exception"
created_at = 2026-05-08T22:14:00+02:00
updated_at = 2026-05-08T22:14:00+02:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

rule_id = "0190d4ab-2b7a-7c50-9c1e-cccccccccccc"
instance_date = 2026-05-11
mode = "cancel"
+++

Standup cancelled — public holiday (Whit Monday).
```

```toml
+++
schema_version = 1
id = "0190d4ce-4444-7c50-9c1e-ffffffffffff"
kind = "exception"
created_at = 2026-05-09T07:00:00+02:00
updated_at = 2026-05-09T07:00:00+02:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

rule_id = "0190d4ab-2b7a-7c50-9c1e-cccccccccccc"
instance_date = 2026-05-12
mode = "override"
override_start = 2026-05-12T10:00:00+02:00
override_end = 2026-05-12T10:30:00+02:00
override_title = "Standup (extended for retro recap)"
+++

Moved 30 minutes later and extended to 30 minutes for last sprint's
retro recap. Owner: Anna.
```

### DM-B.4 — Calendar metadata (`calendars/<cal>/calendar.toml`)

This is a TOML-only file (no Markdown body). The frontmatter fence is
omitted — `calendar.toml` is parsed as raw TOML.

**Decision:** the *only* files without Markdown body are the four
metadata files: `calendar.toml`, `todolist.toml`, `repo.toml`,
`schema.toml`. Rationale: metadata is structurally pure config; a body
would invite drift between human notes and the source-of-truth fields,
and these files are typically displayed as a settings sheet, not a
detail page.

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `schema_version` | int | yes | 1 | — |
| `id` | string (UUIDv7) | yes | — | matches the folder name |
| `kind` | `"calendar"` | yes | — | constant |
| `name` | string | yes | — | display name |
| `emoji` | string | no | `"📆"` | one grapheme |
| `color` | string | no | `"#5b8def"` | `#rrggbb` |
| `kind_mode` | string enum | no | `"regular"` | `regular` / `timebox` |
| `priority` | int | no | 500 | 1..1000 |
| `active_toggle` | bool | no | true | UI toggle |
| `default_notification_group` | string | no | `"events"` | channel/group identifier |
| `default_busy` | bool | no | true | new events default |
| `tz_id` | string | no | device tz | IANA name; default for new recurrences |
| `created_at` / `updated_at` / `author` | as header | yes | — | — |
| `[[active_windows]]` | array of tables | no | `[]` | `start = 2026-05-01`, `end = 2026-08-31`; `end` optional |
| `[[active_hours]]` | array of tables | no | `[]` | `day = "mon"`, `from = "09:00"`, `to = "17:00"` |

**Decision:** `kind_mode` rather than `kind` for the regular/timebox
discriminator on calendars — `kind` is reserved across all entities for
the entity-type discriminator (`"calendar"`). Rationale: keeps a single
parse-time entity-type field uniform across schemas.

```toml
schema_version = 1
id = "0190a0aa-1c1d-7000-8a0a-000000000010"
kind = "calendar"
created_at = 2026-01-01T00:00:00+01:00
updated_at = 2026-01-01T00:00:00+01:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

name = "Work"
emoji = "💼"
color = "#3a7bd5"
kind_mode = "regular"
priority = 600
active_toggle = true
default_notification_group = "work"
default_busy = true
tz_id = "Europe/Berlin"

[[active_windows]]
start = 2026-01-06
end = 2026-12-22

[[active_hours]]
day = "mon"
from = "09:00"
to = "17:00"
[[active_hours]]
day = "tue"
from = "09:00"
to = "17:00"
[[active_hours]]
day = "wed"
from = "09:00"
to = "17:00"
[[active_hours]]
day = "thu"
from = "09:00"
to = "17:00"
[[active_hours]]
day = "fri"
from = "09:00"
to = "15:00"
```

### DM-B.5 — Todolist metadata (`todolists/<id>/todolist.toml`)

Same shape as calendar metadata minus `kind_mode`, plus:

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `kind` | `"todolist"` | yes | — | constant |
| `default_auto_done_at_eod` | bool | no | false | template default for new tasks |
| `default_priority` | int | no | 500 | inherited by tasks |
| `default_notification_group` | string | no | `"tasks"` | — |
| `shopping_mode_eligible` | bool | no | false | gates the shopping view |

```toml
schema_version = 1
id = "0190a0aa-2222-7000-8a0a-000000000020"
kind = "todolist"
created_at = 2026-01-01T00:00:00+01:00
updated_at = 2026-01-01T00:00:00+01:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

name = "Daily chores"
emoji = "🧹"
color = "#88c057"
priority = 400
active_toggle = true
default_priority = 400
default_auto_done_at_eod = true
default_notification_group = "tasks"
shopping_mode_eligible = false
tz_id = "Europe/Berlin"

[[active_hours]]
day = "mon"
from = "06:00"
to = "22:00"
# (mon..sun rows omitted for brevity in the example; in practice all
# seven days are listed when active_hours is present at all)
```

### DM-B.6 — Task (`todolists/<list>/tasks/<yyyy>/<mm>/<id>.md`)

- [ ] **DM-B.6.1** Define `TaskFile` data class.
- [ ] **DM-B.6.2** Body conventions: top-level `- [ ]` / `- [x]`
      checkboxes at the start of the body are parsed as the canonical
      done state ONLY when `body_checkbox_authoritative = true`.
      **Decision:** default `body_checkbox_authoritative = false` —
      rationale: humans expect the frontmatter `done` flag to be the
      truth; the body checkbox is a visual cue. Setting the flag true
      lets agents that prefer "edit the checkbox" workflows opt in.

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| (header fields) | — | — | — | — |
| `kind` | `"task"` | yes | — | constant |
| `title` | string | yes | — | ≤ 200 chars |
| `todolist_id` | string (UUIDv7) | yes | — | FK |
| `due` | offset-datetime OR local-date | no | — | when missing → undated; but undated tasks belong in `standing/` |
| `start_after` | offset-datetime | no | — | hide from views until this time |
| `done` | bool | no | false | — |
| `done_at` | offset-datetime | no | — | required iff `done = true` |
| `auto_done_at_eod` | bool | no | inherits todolist default | — |
| `priority` | int | no | inherits todolist | 1..1000 |
| `tags` | array of strings | no | `[]` | — |
| `notifications` | array of strings | no | `[]` | lead-times, like events |
| `notification_group` | string | no | todolist default | — |
| `attachments` | array of inline tables | no | `[]` | — |
| `spawned_by_event` | string (UUIDv7) | no | — | reciprocal of `Event.spawns_task` |
| `spawned_by_recurrence` | string (UUIDv7) | no | — | reciprocal for recurrence-spawned tasks |
| `spawn_instance_date` | local-date | no | — | required iff `spawned_by_recurrence` is set |
| `body_checkbox_authoritative` | bool | no | false | see DM-B.6.2 |

```toml
+++
schema_version = 1
id = "0190d500-aaaa-7c50-9c1e-bbbbbbbbbbbb"
kind = "task"
created_at = 2026-05-09T07:32:00+02:00
updated_at = 2026-05-09T07:32:00+02:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

title = "Buy milk + bread + eggs"
todolist_id = "0190a0aa-2222-7000-8a0a-000000000022"
due = 2026-05-09T18:30:00+02:00
done = false
auto_done_at_eod = true
priority = 450
tags = ["shopping", "groceries"]
notifications = ["30m"]
spawned_by_event = "0190d4ee-1234-7c50-9c1e-aaaaaaaaaaaa"
+++

Shopping list:

- [ ] Milk (2L, lactose-free)
- [ ] Bread (whole wheat)
- [ ] Eggs (10-pack, free-range)

Aldi or Rewe; Aldi closes 20:00, Rewe closes 22:00.
```

### DM-B.7 — Standing task (`todolists/<list>/standing/<id>.md`)

Same fields as `Task` but `kind = "standing_task"` and `due` /
`spawned_by_*` / `spawn_instance_date` are forbidden.

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `kind` | `"standing_task"` | yes | — | constant |
| `pinned` | bool | no | false | promotes to "Today" view when true |

```toml
+++
schema_version = 1
id = "0190d600-aaaa-7c50-9c1e-cccccccccccc"
kind = "standing_task"
created_at = 2026-04-12T20:00:00+02:00
updated_at = 2026-04-12T20:00:00+02:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

title = "Resharpen the kitchen knives"
todolist_id = "0190a0aa-2222-7000-8a0a-000000000022"
done = false
priority = 200
tags = ["home", "maintenance"]
pinned = false
+++

Whetstone is in the bottom drawer next to the dishwasher. Aim for once
a quarter; record the date in the body when done so the cadence is
visible.
```

### DM-B.8 — Task recurrence (`todolists/<list>/recurrences/<rule-id>.md`)

- [ ] **DM-B.8.1** Define `TaskRecurrenceFile`.
- [ ] **DM-B.8.2** Resolver materialises into spawned task instances
      lazily for the requested date range; instances are NOT persisted
      unless the user explicitly "completes" one (which writes a
      concrete task file at `tasks/<yyyy>/<mm>/<id>.md` carrying
      `spawned_by_recurrence`).

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| (header) | — | — | — | — |
| `kind` | `"task_recurrence"` | yes | — | — |
| `title` | string | yes | — | — |
| `dtstart` | local-datetime | yes | — | first instance's due moment |
| `tz_id` | string | yes | — | — |
| `rrule` | string | yes | — | RFC5545 |
| `rdate` / `exdate` | as recurrence | no | `[]` | — |
| `todolist_id` | string (UUIDv7) | yes | — | — |
| `priority` | int | no | inherits | — |
| `notifications` | array of strings | no | `[]` | — |
| `auto_done_at_eod` | bool | no | inherits | — |
| `tags` | array of strings | no | `[]` | — |

```toml
+++
schema_version = 1
id = "0190d700-1111-7c50-9c1e-dddddddddddd"
kind = "task_recurrence"
created_at = 2026-01-04T10:00:00+01:00
updated_at = 2026-01-04T10:00:00+01:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

title = "Take out the bins"
todolist_id = "0190a0aa-2222-7000-8a0a-000000000022"
dtstart = 2026-01-05T19:00:00
tz_id = "Europe/Berlin"
rrule = "FREQ=WEEKLY;BYDAY=MO"
priority = 300
notifications = ["1h"]
auto_done_at_eod = true
tags = ["chore", "weekly"]
+++

Yellow bin every Monday evening. Black bin every other Monday (offset
by one week — see `Take out the black bin` rule for that one).
```

### DM-B.9 — Identity (`identities/<id>.md`)

- [ ] **DM-B.9.1** Define `IdentityFile`.
- [ ] **DM-B.9.2** Enforce exactly-one `default_author = true` on save
      (DM-G).

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `schema_version` | int | yes | 1 | — |
| `id` | string (UUIDv7) | yes | — | matches filename |
| `kind` | `"identity"` | yes | — | — |
| `display_name` | string | yes | — | — |
| `email` | string | no | `<id>@strictlykeptboy.local` | used for git author |
| `avatar` | string | no | — | path to `attachments/<sha-prefix>/<sha>.<ext>` OR a single emoji grapheme |
| `default_author` | bool | no | false | exactly one identity per repo MUST have `true` |
| `public_keys` | array of inline tables | no | `[]` | `{ kind = "ssh-ed25519", key = "AAAA…", comment = "phone" }` |
| `pronouns` | string | no | — | free-form |
| `created_at` / `updated_at` / `author` | as header | yes | — | self-author allowed (`author = id`) for the seed identity |

**Decision:** the seed identity (the one written by the wizard at repo
init) self-references in `author`. Rationale: chicken-and-egg — there
is no other identity yet. Validator allows `author == id` only for
identity files.

```toml
+++
schema_version = 1
id = "01900000-0000-7000-8000-aaaaaaaaaaaa"
kind = "identity"
created_at = 2026-01-01T00:00:00+01:00
updated_at = 2026-01-01T00:00:00+01:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

display_name = "Alex"
email = "alex@example.com"
avatar = "🦊"
default_author = true
pronouns = "he/him"

[[public_keys]]
kind = "ssh-ed25519"
key = "AAAAC3NzaC1lZDI1NTE5AAAAIE+Q3Cz+sPOYx8Qy0M0Hx0lUJM+S7pqDJfgB+IhP3aXh"
comment = "pixel-7-strictlykeptboy"
+++

Daily driver identity. Stamps every event/task in this repo unless I
explicitly switch in Settings → Identities. Added the Pixel 7 SSH key
on 2026-01-01.
```

### DM-B.10 — Attachment reference (inline table)

Attachments are stored at `attachments/<sha-prefix>/<sha256>.<ext>`
where `<sha-prefix>` is the first 2 hex chars of the SHA-256. Files are
content-addressed; the original filename, mime type, and size travel
with the *referencing* entity, not with the bytes on disk.

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `sha256` | string (64 hex) | yes | — | lowercase |
| `filename` | string | yes | — | original filename, ≤ 255 chars |
| `mime` | string | yes | — | e.g. `image/png` |
| `size` | int | yes | — | bytes; matches the file on disk |
| `caption` | string | no | — | UI alt-text |

**Decision:** the on-disk filename uses the original filename's
extension (`.png`, `.pdf`). Rationale: lets the OS/tooling open the
file directly without metadata round-trips; the SHA-256 stem keeps
collisions impossible.

**Decision:** if the same bytes are referenced by multiple entities,
the file on disk is shared (single copy). Removing one reference does
NOT delete the on-disk file. A garbage-collect step (`tools/gc.sh`,
v1.x) sweeps unreferenced attachments. Rationale: aggressive deletion
makes a single failed scan corrupt history; sweep is opt-in.

### DM-B.11 — Repo metadata (`.strictlykeptboy/repo.toml`)

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `schema_version` | int | yes | 1 | — |
| `id` | string (UUIDv7) | yes | — | repo's stable ID, separate from URL |
| `kind` | `"repo_meta"` | yes | — | — |
| `name` | string | yes | — | display name |
| `emoji` | string | no | `"🗂️"` | — |
| `color_seed` | string | no | `"#5b8def"` | drives M3 dynamic-color override |
| `default_identity` | string (UUIDv7) | yes | — | FK to identities |
| `default_calendar` | string (UUIDv7) | no | — | FK |
| `default_todolist` | string (UUIDv7) | no | — | FK |
| `created_at` | offset-datetime | yes | — | — |
| `app_min_version` | string | no | — | semver minimum app version that wrote this repo |

```toml
schema_version = 1
id = "0190a000-0000-7000-8000-000000000001"
kind = "repo_meta"
created_at = 2026-01-01T00:00:00+01:00
app_min_version = "0.1.0"

name = "Alex personal"
emoji = "🦊"
color_seed = "#a06ee1"
default_identity = "01900000-0000-7000-8000-aaaaaaaaaaaa"
default_calendar = "0190a0aa-1c1d-7000-8a0a-000000000001"
default_todolist = "0190a0aa-2222-7000-8a0a-000000000022"
```

### DM-B.12 — Schema metadata (`.strictlykeptboy/schema.toml`)

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `schema_version` | int | yes | 1 | the live schema version of THIS repo |
| `kind` | `"schema_meta"` | yes | — | — |
| `app_min_version` | string | no | — | minimum app version that can read this repo |
| `last_writer` | inline table | yes | — | `{ app = "strictlykeptboy", version = "0.1.0", at = 2026-05-09T07:00:00+02:00 }` |
| `migration_history` | array of inline tables | no | `[]` | append-only history |

```toml
schema_version = 1
kind = "schema_meta"
app_min_version = "0.1.0"
last_writer = { app = "strictlykeptboy", version = "0.1.0", at = 2026-05-09T07:00:00+02:00 }

[[migration_history]]
from = 0
to = 1
at = 2026-01-01T00:00:00+01:00
app_version = "0.1.0"
note = "initial scaffold"
```

---

## Phase DM-C — UUIDv7 + filename builder + bucketing

- [ ] **DM-C.1** UUIDv7 generator: 48-bit Unix-millisecond timestamp
      prefix + 12-bit random + 62-bit random (per RFC 9562). Implement
      atop `java.security.SecureRandom`. Output lowercase canonical
      form.
- [ ] **DM-C.2** Monotonic guard: within the same millisecond,
      increment the random tail to keep ordering stable. **Decision:**
      use a per-process monotonic counter for the lower 12 bits of
      `rand_a` (RFC 9562 method 1). Rationale: prevents same-ms
      collisions in scripted bulk imports.
- [ ] **DM-C.3** Filename builder:
  - Events: `calendars/<cal-id>/events/<yyyy>/<mm>/<event-id>.md`
    where `yyyy` and `mm` are derived from the event's `start` in the
    calendar's `tz_id`.
  - Tasks (dated): `todolists/<list-id>/tasks/<yyyy>/<mm>/<task-id>.md`
    where `yyyy`/`mm` come from `due`'s local date. If `due` is
    missing → file belongs in `standing/` (writer rejects this
    combination).
  - Standing tasks: `todolists/<list-id>/standing/<task-id>.md`.
  - Recurrences (event): `calendars/<cal-id>/recurrences/<rule-id>.md`.
  - Recurrences (task): `todolists/<list-id>/recurrences/<rule-id>.md`.
  - Exceptions: `calendars/<cal-id>/exceptions/<rule-id>/<yyyy-mm-dd>.md`.
- [ ] **DM-C.4** Rationale for bucketing baked into AGENTS.md:
  - **Why monthly buckets:** keeps directory listings under ~100
    files in normal use (a busy month is ~50 events). Year-only
    bucketing was considered and rejected — a 5-year-old repo would
    have 2000+ files in a flat year/ dir, slow to scan on cheap
    hardware. Day-level bucketing was considered and rejected — too
    many empty directories.
- [ ] **DM-C.5** Move-on-edit rule: if an event's `start` is edited
      across a month boundary, the writer MUST move the file to the
      new bucket and `git rm` the old path in the same commit.

---

## Phase DM-D — read-side scanner

- [ ] **DM-D.1** `RepoScanner.scan(rootPath): RepoSnapshot`. Walks
      the configured subtree:
  - `.strictlykeptboy/schema.toml`, `.strictlykeptboy/repo.toml`
  - `identities/*.md`
  - `calendars/*/calendar.toml`
  - `calendars/*/events/**/*.md`
  - `calendars/*/recurrences/*.md`
  - `calendars/*/exceptions/*/*.md`
  - `todolists/*/todolist.toml`
  - `todolists/*/tasks/**/*.md`
  - `todolists/*/standing/*.md`
  - `todolists/*/recurrences/*.md`
  - `attachments/**/*` (catalogued, not parsed)
- [ ] **DM-D.2** Each parsed file produces an `EntityFile` typed
      object OR a `MalformedEntry` carrying `path`, `reason`, and the
      raw bytes for the broken-entries tray (DM-H).
- [ ] **DM-D.3** Skip rules (silently): hidden files (`.*`), files
      not ending in `.md` or `.toml` in the structured directories,
      anything inside `.git/`, anything inside top-level `tools/`.
- [ ] **DM-D.4** Performance: streaming walk; entity decode happens on
      a worker thread pool sized to `Runtime.availableProcessors()`.
      Cold scan target: < 500ms for a 1000-entry repo (per
      `decisions.md` D.21).
- [ ] **DM-D.5** Output: `RepoSnapshot { headSha, schemaMeta,
      repoMeta, identities, calendars, todolists, attachments,
      malformed }`.

---

## Phase DM-E — write-side serialiser

- [ ] **DM-E.1** `RepoWriter.put(entity)` — single-entity writes.
      Sequence:
  1. Validate entity (DM-H).
  2. Resolve canonical path (DM-C).
  3. If the entity already exists at a *different* path (e.g.
     month-bucket move), `git mv` semantics: delete old, write new.
  4. Update `updated_at` on the entity.
  5. Bump `schema_meta.last_writer`.
  6. Stage + commit with message per [`decisions.md`](decisions.md)
     §D.8 (`<verb> <kind> "<title>" in <calendar/todolist-name>`).
- [ ] **DM-E.2** Batched writes: `RepoWriter.atomic(block)` collects
      operations and emits a single commit. Used by template apply
      (Phase K).
- [ ] **DM-E.3** Identity invariant: on every save touching
      `identities/`, scan all identity files; if more than one has
      `default_author = true`, the writer keeps the most recently
      `updated_at` true and flips the rest to false. Documented in
      AGENTS.md so hand-edits know the rule.
- [ ] **DM-E.4** Cross-reference invariant: when writing an `Event`
      with `spawns_task = X`, the writer MUST also ensure a Task with
      `id = X` exists with `spawned_by_event = <event-id>` (creating
      it if missing); deletion of the event prompts the user to also
      delete the task or unlink it. **Decision:** unlinking is the
      default (clears `spawned_by_event` on the task); explicit
      "delete the spawned task too" is a checkbox in the confirm
      dialog. Rationale: orphaned tasks are recoverable; mass-deleted
      tasks are not.
- [ ] **DM-E.5** Recurrence-rule edits: the writer NEVER auto-cleans
      `exceptions/<rule-id>/*` when editing the rule. If a rule
      changes such that some exceptions would no longer apply (e.g.
      the rule's BYDAY excludes Wednesdays but a Wednesday exception
      exists), the writer logs a warning to the broken-entries tray
      but does not delete the file.

---

## Phase DM-F — schema versioning + migration runner

Per [`decisions.md`](decisions.md) §D.20:

- [ ] **DM-F.1** Constants: `LATEST_SCHEMA = 1`, `MIN_READABLE_SCHEMA
      = 1`. Bumped per release in lockstep with migration code.
- [ ] **DM-F.2** Open-time check (`SchemaGate.check(repo)`):
  - If no `.strictlykeptboy/schema.toml` exists → repo is *not*
    strictlykeptboy-managed; offer to "adopt" it (creates the file at
    `schema_version = LATEST_SCHEMA`, no other changes).
  - If `repo.schema_version > LATEST_SCHEMA` → refuse to write; show
    "this repo was written by a newer strictlykeptboy. Update the app
    or open in read-only mode." Read-only mode renders entries via
    forward-compatible best-effort parsing (unknown frontmatter keys
    surfaced in a `Other` panel of the detail view; see DM-H.4).
  - If `repo.schema_version < LATEST_SCHEMA` → run migrations
    sequentially (`migrateV1ToV2`, `migrateV2ToV3`, …) and commit each
    with message `migrate schema v<old> → v<new>`. Each migration is
    idempotent.
  - If `repo.schema_version == LATEST_SCHEMA` → open normally.
- [ ] **DM-F.3** Migration runner contract:
  ```kotlin
  interface SchemaMigration {
      val from: Int; val to: Int
      fun apply(repo: GitRepo): MigrationResult
      fun isAlreadyApplied(repo: GitRepo): Boolean
  }
  ```
  Idempotency: `apply` MUST detect a previously-completed run via
  shape inspection (e.g. "all task files already have field X") and
  short-circuit to a no-op.
- [ ] **DM-F.4** Migration commit body convention: every migration
      writes a `MIGRATION-NOTES.md` entry to the repo root that
      survives across runs (append-only). **Decision:** keep the
      migration log AT the repo root rather than inside
      `.strictlykeptboy/`. Rationale: it's user-facing — the user
      should see "your repo was upgraded" the next time they `git
      pull` from a desktop.
- [ ] **DM-F.5** v1 ships with no migrations (it IS the floor).
      First migration will be v1 → v2 when v2 is defined.
- [ ] **DM-F.6** Open-question deferred to a future schema bump:
  - **Comment preservation in TOML frontmatter** — drops at v1,
    likely returns at v2 once we ship a custom comment-aware writer.
  - **Per-entity timezone overrides on one-off events** — v1 stores
    the offset only; v2 may add `tz_id` on `Event` if user feedback
    surfaces DST issues with single events that span DST transitions.
  - **Internationalised tag namespaces** — v1 enforces ASCII
    `[a-z0-9_-]+`; v2 may relax to Unicode if needed.

---

## Phase DM-G — AGENTS.md / CLAUDE.md content emitted into user repos

> **Superseded by [Phase DM-O](#phase-dm-o--agentsmd-rewrite-for-cli-primary-path)
> — see Round 2 extensions below.** The file-format-first AGENTS.md
> originally drafted in DM-G.3 is retained here for historical context,
> but the `skb` CLI is now the primary recommended write path per
> [decisions D.24](decisions.md#d24--skb-cli-the-primary-interface-for-calendar-data).
> The wizard writes the DM-O content into every new repo. The DM-G
> mechanics (symlink/stub fallback in DM-G.1, scaffold-time placeholder
> substitution in DM-G.3, commonmark-lint acceptance test in DM-G.4,
> bump-on-schema-change rule in DM-G.5) all carry forward unchanged —
> only the **content** of the rendered AGENTS.md changes.

This is the AI-agent guide that strictlykeptboy writes into every
user-data repo. It targets a hypothetical Claude session that's been
asked to add/edit/cancel calendar entries directly on disk.

- [ ] **DM-G.1** `CLAUDE.md` is a relative symlink → `AGENTS.md`. On
      filesystems without symlink support (some Android external
      storage paths), the writer falls back to a one-line stub
      pointing readers at `AGENTS.md`. **Decision:** symlink first,
      stub-file second; the stub literally reads `See AGENTS.md.` so
      that a tool reading `CLAUDE.md` directly still has actionable
      content.
- [ ] **DM-G.2** `README.md` is human-facing (not duplicated here);
      its content is generated by the wizard with the chosen repo
      name + brief.
- [ ] **DM-G.3** The literal `AGENTS.md` content the app writes is
      below. The placeholders `{{REPO_NAME}}`, `{{TZ_ID}}`,
      `{{DEFAULT_IDENTITY_ID}}`, `{{DEFAULT_CALENDAR_ID}}`,
      `{{DEFAULT_TODOLIST_ID}}` are filled at scaffold time by the
      wizard.

```markdown
# AGENTS.md — `{{REPO_NAME}}`

> AI agent guide for this calendar/task repository. If you are an AI
> tool (Claude, Copilot, Aider, etc.) acting on this repo, read this
> file end to end before writing anything.

This repo is managed by **strictlykeptboy**, an Android app that uses
a Git repository as the canonical store for calendar events,
todolists, and recurrence rules. Every entity lives as a single
hand-editable file. The app reads and writes the same files you do —
there is no hidden index.

## Ground rules

1. **One file per entity. One entity per file.** Never bundle two
   events in the same file.
2. **Never create an index file** (no `events.json`, no `tasks.csv`,
   no `summary.md`). Indexes are merge-conflict factories. The app
   re-scans the repo on every git HEAD change.
3. **IDs are UUIDv7, lowercase, canonical form.** Filenames embed the
   ID. Never invent an ID by hand — generate one (e.g. `uuidgen` with
   the v7 flag, or `python -c "import uuid; print(uuid.uuid7())"` on
   3.13+).
4. **File format = TOML frontmatter + Markdown body, fenced by
   `+++`.** The frontmatter is structured; the body is yours to
   write notes, links, and reminders.
5. **Schema version is `1`.** Every entity file starts with
   `schema_version = 1`. If you produce content for a future schema,
   bump only after coordinating with the app's migration code.
6. **Comments inside TOML frontmatter are dropped on next app
   write.** Put commentary in the Markdown body.

## Repo layout

```
{{REPO_NAME}}/
├── README.md
├── AGENTS.md                      ← this file
├── CLAUDE.md                      ← symlink → AGENTS.md
├── .strictlykeptboy/
│   ├── schema.toml                schema_version, last_writer
│   └── repo.toml                  repo display name, defaults
├── identities/
│   └── <person-id>.md             one identity per person
├── calendars/
│   └── <calendar-id>/
│       ├── calendar.toml          metadata for this calendar
│       ├── events/<yyyy>/<mm>/    one-off events
│       ├── recurrences/           RRULE-bearing rules
│       └── exceptions/<rule-id>/  per-occurrence overrides
├── todolists/
│   └── <todolist-id>/
│       ├── todolist.toml
│       ├── tasks/<yyyy>/<mm>/     dated tasks
│       ├── standing/              undated tasks
│       └── recurrences/           recurring chores
└── attachments/
    └── <2-hex-prefix>/<sha256>.<ext>
```

The `<yyyy>/<mm>/` bucketing applies to events and dated tasks —
the year and month come from the entity's `start` (events) or `due`
(tasks) **in the calendar/todolist's `tz_id`**, not UTC.

## Defaults for this repo

- Default identity (use as `author = "..."` unless a different person
  is the actual author): `{{DEFAULT_IDENTITY_ID}}`.
- Default calendar (use as `calendar_id = "..."` for one-off events
  unless the user named another): `{{DEFAULT_CALENDAR_ID}}`.
- Default todolist (use as `todolist_id = "..."` for tasks unless
  the user named another): `{{DEFAULT_TODOLIST_ID}}`.
- Default timezone (use as `tz_id = "..."` for recurrences unless
  the user named another): `{{TZ_ID}}`.

## Frontmatter format

A complete event file looks like this:

```
+++
schema_version = 1
id = "<uuidv7>"
kind = "event"
created_at = 2026-05-09T18:30:00+02:00
updated_at = 2026-05-09T18:30:00+02:00
author = "{{DEFAULT_IDENTITY_ID}}"

title = "Dentist"
start = 2026-05-12T14:00:00+02:00
end = 2026-05-12T14:45:00+02:00
calendar_id = "{{DEFAULT_CALENDAR_ID}}"
notifications = ["1d", "15m"]
tags = ["health"]
+++

Free-form Markdown body — notes, instructions, anything the user
finds useful when looking at this event later.
```

The fences are literal `+++` on a line by themselves. No leading
whitespace. No alternative fences (`---` is YAML; we don't use YAML).

## How to add a one-off event

1. Generate a fresh UUIDv7. Call it `EVT_ID`.
2. Pick the calendar's UUIDv7 (`CAL_ID`). Default:
   `{{DEFAULT_CALENDAR_ID}}`.
3. Compute the bucket: `<yyyy>/<mm>` from the event's `start` in the
   calendar's tz.
4. Write the file at
   `calendars/<CAL_ID>/events/<yyyy>/<mm>/<EVT_ID>.md`.
5. `kind = "event"`. Required: `title`, `start`, `end`,
   `calendar_id`, `author`, header fields.
6. Commit with a message like
   `add event "Dentist 14:00" in Personal`.

## How to edit an event

1. Locate the file by `id` (filename) or by scanning for `title`.
2. Edit fields in place. Update `updated_at` to the current
   offset-datetime.
3. **If you change `start` to a different month**, move the file to
   the new `<yyyy>/<mm>/` bucket in the same commit
   (`git mv old new`).
4. Commit:
   `update event "Dentist 14:30" in Personal`.

## How to delete an event

1. `git rm` the file.
2. Commit: `remove event "<title>" from <calendar>`.
3. If the event had `spawns_task = X`, decide whether to also remove
   the task at `todolists/<list>/.../<X>.md` or to keep it as a
   standalone item. The default is "unlink": clear `spawned_by_event`
   on the task and keep it.

## How to cancel ONE instance of a recurring event

DO NOT edit the recurrence rule to drop a single date.

1. Locate the recurrence file at
   `calendars/<CAL_ID>/recurrences/<RULE_ID>.md`. Note its `tz_id`.
2. Compute the local date of the instance you want to cancel (in the
   rule's tz). Call it `YYYY-MM-DD`.
3. Create
   `calendars/<CAL_ID>/exceptions/<RULE_ID>/<YYYY-MM-DD>.md`:

```
+++
schema_version = 1
id = "<fresh-uuidv7>"
kind = "exception"
created_at = <now>
updated_at = <now>
author = "{{DEFAULT_IDENTITY_ID}}"

rule_id = "<RULE_ID>"
instance_date = <YYYY-MM-DD>
mode = "cancel"
+++

Reason: <why the user is cancelling this instance>.
```

4. Commit:
   `cancel recurring event "<title>" on <YYYY-MM-DD>`.

## How to override ONE instance of a recurring event

Same as cancel, but `mode = "override"` and add the override fields:

```
mode = "override"
override_start = 2026-05-12T10:00:00+02:00
override_end = 2026-05-12T10:30:00+02:00
override_title = "Standup (extended for retro)"
```

The `instance_date` is still the *original* instance's local date in
the rule's tz — even if you're moving the time. That's how the
resolver knows which occurrence you're patching.

## How to add a task

Pick the destination:

- **Dated** (has a `due`): write
  `todolists/<LIST_ID>/tasks/<yyyy>/<mm>/<TASK_ID>.md` with
  `kind = "task"`, `due = ...`.
- **Standing** (no deadline): write
  `todolists/<LIST_ID>/standing/<TASK_ID>.md` with
  `kind = "standing_task"`.

Required fields beyond the header: `title`, `todolist_id`,
plus `due` for dated tasks.

The body can contain a checkbox list — but the canonical
done-state is the `done` boolean in frontmatter (and `done_at`
when set).

## How to mark a task done

Set in frontmatter:
```
done = true
done_at = 2026-05-09T19:14:00+02:00
```
You may ALSO tick the body's `- [ ]` to `- [x]`. Re-render is fine.

## How to spawn a task from a recurring event

In the recurrence file, set `spawns_task = "<TASK_ID>"` and
`spawns_task_in_todolist = "<LIST_ID>"`. The app materialises one
task per instance lazily; you do not need to write task files for
each occurrence yourself.

## Identities

- One file per person at `identities/<person-id>.md`.
- Exactly ONE identity has `default_author = true`. The app fixes
  this up on save; if you write multiple `default_author = true`,
  the most recently `updated_at` wins on the next app write.
- Use the identity's `id` in the `author` field of every entity you
  create.

## Attachments

- Stored at `attachments/<2-hex-prefix>/<sha256>.<ext>`.
- Compute the SHA-256 of the file bytes; lowercase hex.
- The `<2-hex-prefix>` is the first two hex characters of the
  SHA-256 (256 buckets).
- Reference the attachment from an event/task's frontmatter via:
  ```
  [[attachments]]
  sha256 = "<full-sha256-hex>"
  filename = "scan.pdf"
  mime = "application/pdf"
  size = 184320
  ```
- The same bytes referenced by multiple entities = one file on disk.
- Never delete from `attachments/` directly; if you remove a
  reference, the file stays — a periodic GC pass cleans up.

## Validation rules

The app refuses to write malformed entries. If you write something
malformed by hand, the app surfaces it in a "broken entries" tray on
next scan, **but does not auto-delete it**. To recover, fix the file
and let the app re-read.

A file is malformed if any of:

- `+++` fences missing or asymmetric.
- `schema_version` missing or not an integer.
- `id` missing or not a valid UUIDv7.
- `kind` missing or not a recognised value.
- A required field for the `kind` is missing.
- A foreign-key field references a non-existent entity (e.g.
  `calendar_id` to a calendar that doesn't exist).
- `end < start` on an event.
- `done = true` without `done_at`.
- An exception's `rule_id` doesn't match an existing recurrence.

## Commit message conventions

The app stamps commits as:

- `add event "<title>" in <calendar-name>`
- `update event "<title>" in <calendar-name>`
- `remove event "<title>" from <calendar-name>`
- `cancel recurring event "<title>" on <YYYY-MM-DD>`
- `override recurring event "<title>" on <YYYY-MM-DD>`
- `add task "<title>" in <todolist-name>`
- `complete task "<title>" in <todolist-name>`
- `migrate schema v<old> → v<new>`

If you commit by hand, keeping these prefixes makes the app's "recent
changes" UI render cleanly. Free-form messages still work.

## Things you should NOT do

- Do **not** create files at the repo root that aren't in the schema
  (the app ignores them but they clutter the working tree).
- Do **not** rename calendar or todolist folders by hand. The folder
  name IS the entity ID. Use the app's "rename" UI which keeps the
  folder name and changes only the `name` field.
- Do **not** rewrite history (`git rebase -i`, `filter-branch`)
  unless you really know what you're doing — the app keys cache
  invalidation on `HEAD` parent walks and rewriting can force a
  full rescan.
- Do **not** commit secrets to attachments. Repos may be private at
  the provider level, but cloned worktrees on shared machines are
  not encrypted.

## Where to find more

- App-side architectural reference:
  https://github.com/eight87/strictlykeptboy (the planning docs in
  `docs/plans/` are the source of truth for the schema and resolver).
```

- [ ] **DM-G.4** Acceptance test: render the AGENTS.md template, lint
      the resulting markdown with commonmark, assert headings layout
      stable across releases.
- [ ] **DM-G.5** Bump-the-AGENTS rule: when `LATEST_SCHEMA` changes,
      every existing repo gets an updated `AGENTS.md` written
      atomically as part of the migration commit. Diffs are visible
      to the user via normal git tools.

---

## Phase DM-H — validation + broken-entries tray

- [ ] **DM-H.1** `EntityValidator.validate(file): ValidationResult`.
      Returns `Ok` or `Err(reasons: List<ValidationReason>)`.
- [ ] **DM-H.2** Reasons (closed enum):
  - `MissingFrontmatter`
  - `MalformedToml`
  - `MissingField(name)`
  - `WrongType(name, expected, got)`
  - `InvalidUuidv7(name)`
  - `UnknownKind(value)`
  - `EndBeforeStart`
  - `DoneWithoutDoneAt`
  - `MissingFK(name, target)`
  - `OrphanException(rule_id)`
  - `MultipleDefaultAuthors`
  - `OutOfRange(name, value, range)`
  - `InvalidRrule(message)`
  - `InvalidTzId(value)`
  - `SchemaTooNew(version)`
  - `SchemaTooOld(version)`
- [ ] **DM-H.3** Validator runs at three points: read-time (scanner
      collects malformed → tray), write-time (writer refuses to save
      malformed entities), migration-time (migrations re-validate
      every touched entity).
- [ ] **DM-H.4** Forward-compat reads: unknown frontmatter keys are
      preserved into a `_unknown: TomlTable` field on the typed
      object so a write round-trip doesn't drop them. **Decision:**
      ALL writers must merge `_unknown` back into the output before
      serialising. Rationale: lets a future v2-aware app write fields
      that a v1 app round-trips losslessly.
- [ ] **DM-H.5** Broken-entries tray UI: a list view at
      `Settings → Repos → <repo> → Broken entries`, showing path +
      reasons + a "open in editor" button. The app NEVER auto-deletes
      a broken file; user must explicitly fix or remove.
- [ ] **DM-H.6** Crash-resistance: parse-time exceptions become
      `MalformedEntry` instances, not propagated up. The scanner
      must complete on any input.

---

## Phase DM-I — cross-references and rename/delete propagation

(Maps onto C.4 + C.5 secondary work; sub-step of E.4 in
`main.md` for the Event ↔ Task spawn UI flow.)

- [ ] **DM-I.1** `CrossRefIndex` built during scan: forward map
      `Event.id → spawns_task.id` and reverse map
      `Task.id → spawned_by_event.id`.
- [ ] **DM-I.2** Resolver consults the index when rendering an event
      tile (shows a "↳ buys X" affordance) or a task tile (shows a
      "from event Y" backlink).
- [ ] **DM-I.3** Rename of a calendar's display `name`: only updates
      `calendars/<id>/calendar.toml`'s `name` field. The folder name
      is `<id>` and never changes. Same for todolists.
- [ ] **DM-I.4** Delete of a calendar (folder removal): refused by the
      writer if events still reference it from `spawned_by_event` in
      another folder. **Decision:** require explicit
      `--cascade-delete` semantics in the UI confirm dialog: "Delete
      calendar 'Work' AND its 142 events AND unlink 7 spawned tasks?"
- [ ] **DM-I.5** Move of an event to a different calendar: writer
      changes `calendar_id` AND moves the file from
      `calendars/<old>/events/.../<id>.md` to
      `calendars/<new>/events/.../<id>.md` in one commit.
- [ ] **DM-I.6** Delete of a recurrence rule: writer refuses while
      `exceptions/<rule-id>/` is non-empty. Force-delete cascades the
      exceptions folder; UI confirms with the count.

---

## Phase DM-J — body conventions + AI-friendliness

- [ ] **DM-J.1** Body MUST be valid CommonMark. Anything that fails
      `commonmark` parse → validator's `MalformedBody` reason
      (non-fatal warning; entry remains usable).
- [ ] **DM-J.2** Top-of-body checkboxes (`- [ ]` / `- [x]`) parsed
      and surfaced in detail UI. When `body_checkbox_authoritative =
      true` on a task, the *first* top-level checkbox's state mirrors
      the `done` flag on the next write (and vice versa).
- [ ] **DM-J.3** AI-encouraged conventions documented in AGENTS.md:
  - Free-form notes belong in the body.
  - URLs welcome; the detail view auto-links them.
  - Inline images via standard markdown
    `![alt](attachments/ab/abcd...png)` — the app resolves these by
    walking the path.
  - Reminders to self, planning context, "agent leftover" notes
    — all fine in the body. Do not put them in TOML.
- [ ] **DM-J.4** App preserves body verbatim across reads/writes. The
      only auto-edit is the optional checkbox sync (DM-J.2). No
      reflowing, no normalisation, no trailing-whitespace stripping.

---

## Worked-example index (golden files)

For each example below, the file at the indicated path is committed
to `app/src/test/resources/data-model-goldens/` and used as a
parser+writer round-trip golden. Listed here so DM-A.5 has a concrete
test surface.

| # | Path | Phase | Demonstrates |
|---|---|---|---|
| 1 | `events/2026/05/0190d4a0-….md` | DM-B.1 | one-off event with attachment + tags |
| 2 | `recurrences/0190d4ab-….md` | DM-B.2 | weekly RRULE recurrence |
| 3 | `exceptions/0190d4ab-…/2026-05-11.md` | DM-B.3 | cancel exception |
| 4 | `exceptions/0190d4ab-…/2026-05-12.md` | DM-B.3 | override exception |
| 5 | `calendars/<id>/calendar.toml` | DM-B.4 | calendar metadata with active_hours |
| 6 | `todolists/<id>/todolist.toml` | DM-B.5 | todolist metadata |
| 7 | `tasks/2026/05/0190d500-….md` | DM-B.6 | dated task spawned by event |
| 8 | `standing/0190d600-….md` | DM-B.7 | standing task |
| 9 | `recurrences/0190d700-….md` | DM-B.8 | recurring chore |
| 10 | `identities/01900000-….md` | DM-B.9 | seed identity with SSH key |
| 11 | `.strictlykeptboy/repo.toml` | DM-B.11 | repo metadata |
| 12 | `.strictlykeptboy/schema.toml` | DM-B.12 | schema metadata |

All twelve are spelled out above; the golden tree is reproducible
from this document.

---

# Round 2 — extensions (Phases DM-K through DM-P)

Round 2 of [`decisions.md`](decisions.md) (D.23–D.40) expands v1 scope
with comments-on-events, multi-timezone first-class support, optional
GPG signing, bidirectional CalDAV mirroring, multi-branch awareness,
and a `skb` CLI promoted to **primary interface for calendar data**.
Phases DM-K through DM-P below extend the data model to support these
without bumping the schema version — every new field and new file kind
is **additive and optional**, so v1.0 repos remain valid and v1
readers gracefully ignore what they don't understand (per DM-H.4's
`_unknown` round-trip rule).

Cross-references to [`main.md`](main.md): these phases land alongside
**Phase CC** (CLI tooling), **Phase AA** (CalDAV bridge), **Phase GG**
(GPG signing), and **Phase JJ** (multi-branch UI). See `cli-tooling.md`,
`sync-engine.md` extensions (SE-Q+), `ui-spec.md` extensions (UI-V+),
and `notifications-sharing-import.md` extensions (NS-L+) for the
companion subagent deep-dives.

| DM phase | Topic | Decisions ref | main.md ref |
|---|---|---|---|
| DM-K | Comments / replies on events | D.29 | CC, AA |
| DM-L | Multi-timezone fields | D.27 | resolver RV-H, UI |
| DM-M | Identity extensions for GPG | D.23 | GG |
| DM-N | CalDAV mirror + branch-state metadata | D.25, D.36 | AA, JJ |
| DM-O | AGENTS.md rewrite for CLI-primary path | D.24 | CC |
| DM-P | Updated deferrals roll-up | — | — |

---

## Phase DM-K — comments / replies on events (D.29)

Per [decisions D.29](decisions.md#d29--repliescomments-on-events-in-v1),
events carry a sibling directory of one-file-per-comment markdown
files. This is the standard "no merge conflicts" pattern applied to
threaded discussion: two participants commenting concurrently produce
two new files, never a conflict on an existing file. The directory is
discovered by name (sibling whose name is `<event-id>.comments`), not
by an index.

- [ ] **DM-K.1** Define `CommentFile` data class (sealed-class member
      of `EntityFile`) with `kind = "comment"`.
- [ ] **DM-K.2** Filename + path: comments for event
      `calendars/<cal>/events/<yyyy>/<mm>/<event-id>.md` live at
      `calendars/<cal>/events/<yyyy>/<mm>/<event-id>.comments/<comment-id>.md`.
      The directory name MUST end with the literal suffix
      `.comments/`; that suffix is the discoverability key the scanner
      (DM-D extension) looks for when walking the events tree.
- [ ] **DM-K.3** UUIDv7 for `<comment-id>`. Same generator as DM-C.1.
      Time-prefix means a directory listing displays comments in
      creation order without an explicit sort step.
- [ ] **DM-K.4** First-class entity status: comments are read by the
      scanner (DM-D extension), written by the writer (DM-E
      extension), indexed by Room (`comments` table keyed by
      `event_id`), surfaced in search, and exposed by `skb comment
      add|list|edit|delete` (per [DM-O](#phase-dm-o--agentsmd-rewrite-for-cli-primary-path)
      and `cli-tooling.md`). They are not "metadata" — they are
      entities like events or tasks.
- [ ] **DM-K.5** **Deletion semantics: hard-delete, no tombstone.**
      Deleting a comment removes its file and commits with message
      `remove comment on "<event-title>" by <author>`. **Decision:**
      hard-delete is the only mode for v1 comments. Rationale:
      (a) preserves the no-merge-conflict invariant — deletion of a
      comment touches exactly one file, never edits a shared index;
      (b) git history is the audit log — `git log --follow
      <comment-path>` recovers the content if needed;
      (c) edit history specifically is recoverable via `git log -p`
      on the comment file, since edits rewrite the same path. The
      alternative — writing a tombstone file — would still require a
      sibling write and would clutter the directory permanently with
      no recovery benefit beyond what git already provides.
- [ ] **DM-K.6** Edit semantics: edits rewrite the comment file in
      place and bump `edited_at`. The `created_at` field is
      immutable post-creation. The writer refuses to clear or
      backdate `edited_at`.
- [ ] **DM-K.7** Threading: optional `in_reply_to` field references
      another comment's UUIDv7 within the same `.comments/`
      directory. The resolver builds the reply tree at read time. A
      cycle in `in_reply_to` is a validation error
      (`CycleInReplyChain`) — flag via the broken-entries tray
      (DM-H).
- [ ] **DM-K.8** Notifications: per
      [D.37](decisions.md#d37--comments--replies-notification-channel),
      comments route to the `comments` notification channel. The
      per-event mute toggle (UI surface UI-V+ in `ui-spec.md`) stores
      its state in `_local/snoozes.toml` so it sync-or-not depending
      on D.34 opt-in — comment mutes follow the same opt-in rule.
- [ ] **DM-K.9** Comment count in event tile: the resolver tile
      builder counts the files in `<event-id>.comments/` and surfaces
      "💬 N" on the event chip when N > 0. Cached in Room; busted on
      git HEAD change like every other scan-derived cache.
- [ ] **DM-K.10** Validation reasons added to DM-H.2 enum:
      `CycleInReplyChain`, `OrphanComment` (the parent event file
      doesn't exist), `EditedAtBeforeCreatedAt`,
      `EditedAtMissingWhenContentChanged` (the writer enforces this;
      hand-edits are flagged not blocked).
- [ ] **DM-K.11** Worked example committed as golden file at
      `app/src/test/resources/data-model-goldens/comments/`.

### DM-K — schema table

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `schema_version` | int | yes | 1 | additive — old repos have no comments and that's fine |
| `id` | string (UUIDv7) | yes | — | filename embeds this |
| `kind` | `"comment"` | yes | — | constant |
| `event_id` | string (UUIDv7) | yes | — | FK to the parent event file (the file whose `<event-id>` is the prefix of the `.comments/` directory). MUST match the path. |
| `author` | string (UUIDv7) | yes | active identity | FK to `identities/<id>.md` |
| `created_at` | offset-datetime | yes | now | RFC 3339, immutable post-write |
| `edited_at` | offset-datetime | no | — | set on first edit; `≥ created_at`; required iff body content changed |
| `in_reply_to` | string (UUIDv7) | no | — | FK to another comment in the same `.comments/` dir; absent for top-level |

### DM-K — worked example

Path: `calendars/0190a0aa-1c1d-7000-8a0a-000000000010/events/2026/05/0190d4a0-7fab-7c50-9c1e-2b7a44f6f001.comments/0190d4ff-0001-7c50-9c1e-cccccccccc01.md`

```toml
+++
schema_version = 1
id = "0190d4ff-0001-7c50-9c1e-cccccccccc01"
kind = "comment"
event_id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"
created_at = 2026-05-10T18:42:00+02:00
+++

Heads up — Dr. Köhler's office moved last month. New address is
Hauptstraße 14 (was 12). Same gate code (4488).
```

A reply to the above comment, in the same directory:

Path: `calendars/0190a0aa-1c1d-7000-8a0a-000000000010/events/2026/05/0190d4a0-7fab-7c50-9c1e-2b7a44f6f001.comments/0190d4ff-0002-7c50-9c1e-cccccccccc02.md`

```toml
+++
schema_version = 1
id = "0190d4ff-0002-7c50-9c1e-cccccccccc02"
kind = "comment"
event_id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"
author = "01900000-0000-7000-8000-bbbbbbbbbbbb"
created_at = 2026-05-10T18:50:00+02:00
edited_at = 2026-05-10T18:55:00+02:00
in_reply_to = "0190d4ff-0001-7c50-9c1e-cccccccccc01"
+++

Thanks — updated the location field on the event. Also bumped the
1-day reminder to 2-day since traffic at the new address is worse.
```

---

## Phase DM-L — multi-timezone fields (D.27)

Per [decisions D.27](decisions.md#d27--multi-timezone-first-class-in-v1),
v1 promotes timezones to first-class. The schema additions are
**purely additive and optional** — every field below has a default
that reproduces the v1.0 behaviour, so the schema version stays at 1
and no migration is required.

The existing offset-datetime storage (DM-B.1 decision) is preserved:
the offset captures the wall-clock at write time and is what the
resolver uses for display. `tz_id` is the **symbolic IANA name** that
lets the renderer (a) name the zone ("Pacific Time" instead of
"−07:00"), (b) DST-aware-expand recurrences in their authoring zone,
and (c) feed the multi-tz common-time finder
([resolver RV-H](resolver.md)).

- [ ] **DM-L.1** Add optional `tz_id` to `Event` frontmatter
      ([DM-B.1](#dm-b1--event-eventsyyyymmidmd)). Type: string, IANA
      tz name (validated against `java.time.ZoneId.getAvailableZoneIds()`).
      Absent → resolver falls through the resolution order below.
- [ ] **DM-L.2** Document — and re-affirm — the existing `tz_id` on
      `Recurrence` ([DM-B.2](#dm-b2--recurrence-rule-calendarscalrecurrencesrule-idmd))
      and `TaskRecurrence` ([DM-B.8](#dm-b8--task-recurrence-todolistslistrecurrencesrule-idmd)).
      Round 2 elevates this from "required, defaults to device tz" to
      "required, defaults to event.tz_id || calendar.default_tz ||
      repo.default_tz || device tz" at scaffold time. Stored value
      remains required on the file.
- [ ] **DM-L.3** Add optional `default_tz` to
      `.strictlykeptboy/repo.toml` ([DM-B.11](#dm-b11--repo-metadata-strictlykeptboyrepotoml)).
      Type: string, IANA tz name. Absent → device tz at the time of
      each read (yes, that varies — that's the intended fallback when
      no per-repo default has been set). The wizard writes this field
      at repo creation, defaulting to the device tz, but the user can
      change it later.
- [ ] **DM-L.4** Add optional `default_tz` to
      `calendars/<cal>/calendar.toml` ([DM-B.4](#dm-b4--calendar-metadata-calendarscalcalendartoml)).
      Per-calendar override of the repo default. Useful for a "Work
      (NYC)" calendar inside a Berlin-default repo. Absent → resolver
      falls through to repo default.
- [ ] **DM-L.5** **Resolution order** for the "what zone does this
      entity live in?" question, in order of preference:
      1. `event.tz_id` (or `recurrence.tz_id`, which is required —
         so for recurrences this always wins).
      2. `calendar.default_tz` (or `todolist.default_tz` for tasks).
      3. `repo.default_tz`.
      4. Device tz at read time.
      The resolver evaluates this chain once per entity per render
      and caches the answer in Room until the next HEAD change.
- [ ] **DM-L.6** Display semantics: events render at their stored
      offset (already DM-B.1's decision) but the **zone label** shown
      next to the time uses the resolved `tz_id` from DM-L.5. A NYC
      event in a Berlin repo therefore displays as
      `14:00 EDT (−04:00)` rather than just `14:00 −04:00`.
- [ ] **DM-L.7** Storage invariant: when the writer creates a new
      entity with a `tz_id`, it MUST also stamp the offset that
      corresponds to that tz_id at the entity's local datetime. The
      offset and the tz_id therefore agree at write time; later DST
      transitions are handled by the **renderer** which re-derives
      the wall-clock from `(tz_id, offset, instant)`. This is the
      contract `resolver.md` RV-H relies on.
- [ ] **DM-L.8** Validation: `InvalidTzId(value)` already exists in
      DM-H.2 — extend its application to the new fields. Absent
      `tz_id` is never an error (the field is optional).
- [ ] **DM-L.9** Forward-compat: a v1.0 reader encountering `tz_id`
      on an event preserves it via the `_unknown` round-trip rule
      (DM-H.4) and uses the offset alone for display — degraded but
      correct.
- [ ] **DM-L.10** Worked example committed as golden file (see
      below).

### DM-L — schema-table delta

`Event` (extends DM-B.1):

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `tz_id` | string | no | resolver chain | IANA tz name; e.g. `"America/New_York"` |

`calendar.toml` (extends DM-B.4 — replaces the existing optional
`tz_id` row's semantics; field name stays `tz_id` for backwards
compat OR we add a new alias):

**Decision:** keep `tz_id` on `calendar.toml` as the existing
backwards-compatible field name, AND accept `default_tz` as a
synonym written by Round 2 code. Reader normalises to `tz_id`
internally. Rationale: avoids breaking the v1.0 schema while
clarifying intent in the wizard-written file. The writer emits
`tz_id` (single source of truth in the parser); subagents are free
to read either spelling.

`repo.toml` (extends DM-B.11):

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `default_tz` | string | no | — | IANA tz name; falls through to device tz when absent |

### DM-L — worked example: NYC event in a Berlin-default repo

`.strictlykeptboy/repo.toml`:

```toml
schema_version = 1
id = "0190a000-0000-7000-8000-000000000001"
kind = "repo_meta"
created_at = 2026-01-01T00:00:00+01:00

name = "Alex personal"
default_identity = "01900000-0000-7000-8000-aaaaaaaaaaaa"
default_calendar = "0190a0aa-1c1d-7000-8a0a-000000000001"
default_tz = "Europe/Berlin"
```

`calendars/<work-nyc-trip>/calendar.toml`:

```toml
schema_version = 1
id = "0190a0aa-1c1d-7000-8a0a-000000000033"
kind = "calendar"
created_at = 2026-04-01T00:00:00+02:00
updated_at = 2026-04-01T00:00:00+02:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

name = "NYC trip"
emoji = "🗽"
color = "#3a7bd5"
priority = 700
tz_id = "America/New_York"   # per-calendar default (Round 2 alias: default_tz)
```

`calendars/<work-nyc-trip>/events/2026/06/<event-id>.md`:

```toml
+++
schema_version = 1
id = "0190e500-aaaa-7c50-9c1e-000000000aaa"
kind = "event"
created_at = 2026-05-09T18:30:00+02:00
updated_at = 2026-05-09T18:30:00+02:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

title = "Client kickoff — Acme"
start = 2026-06-15T09:30:00-04:00
end = 2026-06-15T11:00:00-04:00
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000033"
tz_id = "America/New_York"
location = "350 5th Ave, NYC"
+++

In-person kickoff at the Empire State office. Coffee booked at the
ground-floor café from 09:00.
```

Resolver behaviour: the Berlin-default repo renders this event with
the EDT zone label ("09:30 EDT (−04:00)"), and the top bar shows the
non-device-tz badge per D.27. If the user toggles "Render in my tz",
the resolver converts to `15:30 CEST (+02:00)` for display while the
stored offset stays untouched.

---

## Phase DM-M — identity extensions for GPG (D.23)

Per [decisions D.23](decisions.md#d23--signed-commits-optional-capability-not-required-identity),
signed commits are an **optional capability** layered on top of the
existing identity model. The data-model surface is small: a few
optional fingerprint fields on `identities/<id>.md`. **No private key
material ever enters the repo.** Private keys live exclusively in
`EncryptedSharedPreferences`, keyed by fingerprint. The fingerprint
written in the identity file is the **lookup key** into the keystore,
not the secret.

- [ ] **DM-M.1** Add optional `gpg_signing_key_fingerprint` (string,
      hex uppercase, typically 40 chars) to
      [DM-B.9](#dm-b9--identity-identitiesidmd). When set, JGit's
      commit path consults
      `EncryptedSharedPreferences["gpg:<fingerprint>"]` for the
      private key blob and signs the commit. Absent → unsigned
      commit (default per D.23).
- [ ] **DM-M.2** Add optional `ssh_signing_key_path` (string, repo-
      relative path OR special token `"keystore:<fingerprint>"`) for
      users who prefer SSH-key-based git signing
      (`gpg.format = ssh`). The path-form is for users who already
      keep an SSH key on disk somewhere managed (rare on Android,
      common on dev machines for `skb` users); the `keystore:` form
      is the default and resolves through
      `EncryptedSharedPreferences["ssh:<fingerprint>"]`.
- [ ] **DM-M.3** Add optional `verified_public_keys` (array of inline
      tables) — used to verify **other people's** signed commits
      claimed under this identity (rare in single-user repos, useful
      in shared ones). Each entry:
      `{ kind = "gpg" | "ssh", fingerprint = "...", added_at = ..., note = "..." }`.
- [ ] **DM-M.4** Multiple verified public keys per identity:
      explicitly supported. A user may have a work GPG key and a
      personal GPG key both bound to the same `<person-id>`; both
      are accepted as valid signers for that identity. Rationale:
      mirrors how GitHub lets one user attach multiple signing keys
      to one account.
- [ ] **DM-M.5** Keystore separation: the **private key blob** is
      stored at
      `EncryptedSharedPreferences["<kind>:<fingerprint>"]` (where
      kind is `gpg` or `ssh`). The `EncryptedSharedPreferences`
      backing key is the Android Keystore master key (D.22). The
      file-level fingerprint is the lookup key only; possessing the
      fingerprint without the keystore unlock yields no signing
      capability.
- [ ] **DM-M.6** Import UX surface: see UI-V+ in `ui-spec.md` for
      Settings → Identities → "Sign commits" toggle and the "Import
      GPG private key" + "Import SSH signing key" flows. The data
      model's job is just to hold the fingerprint pointer.
- [ ] **DM-M.7** Validation: `InvalidFingerprint(name)` added to
      DM-H.2 — fingerprint must be hex, length 16 (short) or 40
      (full SHA-1) or 64 (SHA-256) for GPG; SSH SHA-256 fingerprints
      are 43-char base64. The validator accepts any of these forms
      and the keystore lookup normalises.
- [ ] **DM-M.8** No migration: the fields are optional and absent
      from v1.0 identity files; v1.0 readers preserve them via
      `_unknown` round-trip (DM-H.4).
- [ ] **DM-M.9** Worked example committed as golden file (see
      below).

### DM-M — schema-table delta on `IdentityFile` (extends DM-B.9)

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `gpg_signing_key_fingerprint` | string | no | — | hex fingerprint of the GPG key in the keystore that signs commits authored under this identity |
| `ssh_signing_key_path` | string | no | — | `keystore:<fingerprint>` (default form) OR a repo-relative path |
| `verified_public_keys` | array of inline tables | no | `[]` | `{ kind, fingerprint, added_at, note }` — keys accepted as valid signers for this identity |

### DM-M — worked example

```toml
+++
schema_version = 1
id = "01900000-0000-7000-8000-aaaaaaaaaaaa"
kind = "identity"
created_at = 2026-01-01T00:00:00+01:00
updated_at = 2026-05-10T12:00:00+02:00
author = "01900000-0000-7000-8000-aaaaaaaaaaaa"

display_name = "Alex"
email = "alex@example.com"
avatar = "🦊"
default_author = true
pronouns = "he/him"

gpg_signing_key_fingerprint = "ABCD1234EF567890ABCD1234EF567890ABCD1234"
ssh_signing_key_path = "keystore:SHA256:Qm5tQz5/sM0Hx0lUJM+S7pqDJfgB+IhP3aXh"

[[public_keys]]
kind = "ssh-ed25519"
key = "AAAAC3NzaC1lZDI1NTE5AAAAIE+Q3Cz+sPOYx8Qy0M0Hx0lUJM+S7pqDJfgB+IhP3aXh"
comment = "pixel-7-strictlykeptboy"

[[verified_public_keys]]
kind = "gpg"
fingerprint = "ABCD1234EF567890ABCD1234EF567890ABCD1234"
added_at = 2026-01-01T00:00:00+01:00
note = "primary signing key (mobile + dev)"

[[verified_public_keys]]
kind = "gpg"
fingerprint = "9876FEDC54321098765432109876FEDC54321098"
added_at = 2026-03-15T00:00:00+01:00
note = "work-only signing key"
+++

Daily driver identity. Signs commits on Android with the primary
GPG key; the work-only key signs commits made on the laptop via
`skb`. Both fingerprints registered with GitHub.
```

---

## Phase DM-N — branch and CalDAV mirror metadata (D.25, D.36)

Round 2 adds two new files under `.strictlykeptboy/` for bookkeeping
that is **per-clone** (not part of the schema users sync between each
other but still git-tracked so it survives clone-to-clone within one
user's devices). Both are TOML-only (no markdown body), like the
other files under `.strictlykeptboy/`.

Branch state is intentionally git-tracked rather than ignored: the
user's preferred default branch should survive a fresh clone. CalDAV
mirror configuration is git-tracked (no secrets) — the **credentials**
for each mirror live in `EncryptedSharedPreferences`, keyed by mirror
`id`.

- [ ] **DM-N.1** Define `CaldavMirrorsFile` schema for
      `.strictlykeptboy/caldav-mirrors.toml`. TOML-only.
- [ ] **DM-N.2** Define `BranchStateFile` schema for
      `.strictlykeptboy/branch-state.toml`. TOML-only.
- [ ] **DM-N.3** Both files are absent in v1.0 repos and absent in v1
      repos that don't use the features. Scanner (DM-D) treats their
      absence as default-state, not an error.
- [ ] **DM-N.4** Validation: `InvalidMirrorMode(value)`,
      `InvalidSyncInterval(value)`, `UnknownBranch(name)` added to
      DM-H.2.
- [ ] **DM-N.5** Credentials never appear in these files. The mirror
      `id` (UUIDv7) is the keystore lookup key:
      `EncryptedSharedPreferences["caldav:<mirror-id>"]` holds
      `{username, password OR oauth_token}`.
- [ ] **DM-N.6** Round-trip: when no mirror is configured, the writer
      does not create `caldav-mirrors.toml` at all (avoids cluttering
      v1.0 users' diff with an empty `[[mirror]]` array).
- [ ] **DM-N.7** `branch-state.toml` is created by the sync engine
      (SE-Q+ in `sync-engine.md`) on first multi-branch operation.
      Before any branch operation occurs, the file is absent and the
      default branch is determined by `git symbolic-ref refs/remotes/origin/HEAD`.
- [ ] **DM-N.8** Worked examples committed as golden files.

### DM-N.1 — `.strictlykeptboy/caldav-mirrors.toml`

| Field (under `[[mirror]]`) | Type | Required | Default | Notes |
|---|---|---|---|---|
| `id` | string (UUIDv7) | yes | — | stable mirror ID; keystore lookup key |
| `calendar_id` | string (UUIDv7) | yes | — | FK to the repo calendar this mirror is bound to |
| `server_url` | string | yes | — | CalDAV collection URL |
| `mode` | string enum | yes | — | `"pull-only"` / `"push-only"` / `"bidi"` |
| `sync_interval_minutes` | int | yes | 30 | `5..1440` |
| `last_synced_at` | offset-datetime | no | — | set by the sync engine |
| `last_etag` | string | no | — | CalDAV ETag for incremental sync |
| `display_name` | string | no | derived | UI label override (optional; defaults to "Mirror of <calendar>") |

```toml
schema_version = 1
kind = "caldav_mirrors"

[[mirror]]
id = "0190f001-0000-7c50-9c1e-mmmmmmmm0001"
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000010"
server_url = "https://caldav.example.com/dav/calendars/user/work/"
mode = "bidi"
sync_interval_minutes = 30
last_synced_at = 2026-05-10T12:00:00Z
last_etag = "W/\"abc123\""
display_name = "Work (Nextcloud)"

[[mirror]]
id = "0190f001-0000-7c50-9c1e-mmmmmmmm0002"
calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000022"
server_url = "https://www.google.com/calendar/dav/alex@example.com/events/"
mode = "pull-only"
sync_interval_minutes = 60
last_synced_at = 2026-05-10T11:45:00Z
display_name = "Google Calendar (read-only mirror)"
```

### DM-N.2 — `.strictlykeptboy/branch-state.toml`

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `schema_version` | int | yes | 1 | — |
| `kind` | `"branch_state"` | yes | — | constant |
| `default_branch` | string | yes | — | the branch the app opens by default for this clone (e.g. `"main"`) |
| `[remote.<name>]` | table | no | — | per-remote bookkeeping |
| `[remote.<name>].default` | string | no | — | the remote's default branch |

```toml
schema_version = 1
kind = "branch_state"
default_branch = "main"

[remote.origin]
default = "main"
```

When the user creates `claude/plan-2026-q3` for an AI agent session
([D.36](decisions.md#d36--multi-branch-awareness-in-v1)) the branch
appears in `git branch` but `branch-state.toml` is unchanged —
`default_branch` is the **preferred** branch, not the **current** one
(which is just `HEAD`).

---

## Phase DM-O — AGENTS.md rewrite for CLI-primary path

This phase **replaces the rendered AGENTS.md template** from DM-G.3
with a CLI-first version per [D.24](decisions.md#d24--skb-cli-the-primary-interface-for-calendar-data).
The wizard, the schema-migration writer, and the
"bump-the-AGENTS rule" (DM-G.5) all switch to writing the DM-O
content. Every cross-reference to the old DM-G.3 template in this
plan or in sibling plans is to be retargeted at DM-O.

The mechanics (placeholder substitution, symlink → CLAUDE.md
fallback, commonmark lint acceptance) carry forward from DM-G
unchanged. **Only the literal text the wizard writes changes.**

- [ ] **DM-O.1** Replace the AGENTS.md template content emitted at
      scaffold time with the markdown block below. Placeholders are
      identical to DM-G.3: `{{REPO_NAME}}`, `{{TZ_ID}}`,
      `{{DEFAULT_IDENTITY_ID}}`, `{{DEFAULT_CALENDAR_ID}}`,
      `{{DEFAULT_TODOLIST_ID}}`. Add `{{SKB_VERSION}}` for the
      bundled `skb` binary version stamped into the doc footer.
- [ ] **DM-O.2** Acceptance test: render the template with sample
      placeholders, lint with commonmark, assert no broken links,
      assert every `skb …` example parses successfully via
      `skb --dry-run --shell-check` (a new CLI subcommand
      introduced in `cli-tooling.md`).
- [ ] **DM-O.3** The rewrite ships in the same migration pass that
      lands Round 2; existing v1.0 repos get the new AGENTS.md
      written atomically alongside any other schema-no-op-but-
      content-bumped changes. Commit message: `update AGENTS.md to
      CLI-primary guidance`.
- [ ] **DM-O.4** Sibling-plan retargeting: DM-G entries that
      mentioned AGENTS.md content now point here. `templates-demo-
      wizard.md` similarly retargets the wizard's emit step.
- [ ] **DM-O.5** Multi-agent etiquette section (new): tells AI
      sessions that the human-merge-via-PR model is the expected
      collaboration pattern when multiple agents work on the same
      repo concurrently. Recommends `skb branch create
      claude/<task>` per D.36.

### DM-O — the rendered AGENTS.md content

```markdown
# AGENTS.md — `{{REPO_NAME}}`

> AI agent guide for this calendar/task repository. If you are an AI
> tool (Claude, Copilot, Aider, etc.) acting on this repo, read this
> file end to end before writing anything.

This repo is managed by **strictlykeptboy**, an Android app + CLI
that uses a Git repository as the canonical store for calendar
events, todolists, recurrence rules, and event comments. Every
entity lives as a single hand-editable file. The app, the CLI, and
you all read and write the same files — there is no hidden index.

## TL;DR for AI agents

**Use the `skb` CLI for every write.** Direct file editing works,
but `skb` handles validation, atomic single-file writes, automatic
git commits with conventional messages, attachment SHA-addressing,
month-bucket moves on edit, recurrence-exception placement, and the
identity / default-calendar plumbing for you. The file format
section below exists so you can read and diff confidently — not so
you have to write the format by hand.

```sh
# Add a one-off event in the default calendar
skb event add --title "Dentist" \
              --start 2026-05-12T14:00 \
              --end   2026-05-12T14:45

# Mark a task done
skb task done --title "Buy milk"   # or --id <UUID>

# Comment on an event
skb comment add --event-title "Dentist" \
                --body "Bring last X-ray"

# Find free time
skb find-free --duration 1h --range 2w

# Discover the full surface
skb help --json | jq
```

Every `skb` command supports `--json` for machine-readable output,
`--dry-run` for preview without writing, and exits with a
documented code:
`0`=ok, `1`=usage, `2`=not-found, `3`=conflict, `4`=auth,
`5`=corrupt, `6`=schema-mismatch.

## Primary path: the `skb` CLI

`skb` (bundled version: `{{SKB_VERSION}}`) is a small Kotlin/JVM
binary distributed alongside the Android app. It operates on the
same Git repo the app does, with the same file conventions. If
`skb` is in your `$PATH`, prefer it over file edits.

### Most common commands (cheat sheet)

| Goal | Command |
|---|---|
| Add a one-off event | `skb event add --title T --start S --end E [--calendar NAME]` |
| Add an all-day event | `skb event add --title T --date YYYY-MM-DD --all-day` |
| Edit an event | `skb event edit --id ID --start S --end E` (any field via flag) |
| Cancel a recurring instance | `skb recurrence cancel-instance --rule ID --date YYYY-MM-DD --reason "..."` |
| Override a recurring instance | `skb recurrence cancel-instance --rule ID --date YYYY-MM-DD --override-start S --override-end E` |
| Add a dated task | `skb task add --title T --due YYYY-MM-DD [--list NAME]` |
| Add a standing task | `skb task add --title T --standing [--list NAME]` |
| Mark a task done | `skb task done --id ID` |
| Add a comment | `skb comment add --event ID --body "..."` |
| List comments | `skb comment list --event ID [--json]` |
| Today / week / month view | `skb show today` / `skb week` / `skb month 2026-05` |
| Free-time finder | `skb find-free --duration 1h --range 2w [--repos A,B]` |
| Apply a template | `skb apply-template gym-3x [--list "Workouts"]` |
| Sync (fetch+rebase+push) | `skb sync` or `skb sync --all` |
| Switch / create a branch | `skb branch create claude/<task>` / `skb branch switch main` |
| Mirror an external CalDAV | `skb caldav add --url ... --calendar NAME --mode bidi` |
| Weather for a date | `skb weather show 2026-05-12` |
| Validate the repo | `skb verify` |
| Discover the full surface | `skb help --json` |

For full flag reference, run `skb <command> --help`. For machine-
readable surface discovery, `skb help --json` emits every command,
flag, type, and exit-code mapping in a single JSON blob — point it
at your prompt context to ground your tool-use.

### When NOT to use the CLI

- The CLI isn't installed in the current environment. Direct file
  edits are then the fallback (see below).
- You need to do something the CLI doesn't expose (rare — file an
  issue at the strictlykeptboy repo, listed in "Where to find more"
  below).
- You are reading/diffing — `git`, `grep`, and any editor work
  natively against the file format.

## Repository structure — the data the CLI operates on

```
{{REPO_NAME}}/
├── README.md
├── AGENTS.md                      ← this file
├── CLAUDE.md                      ← symlink → AGENTS.md
├── .strictlykeptboy/
│   ├── schema.toml                schema_version, last_writer
│   ├── repo.toml                  repo display name, defaults, default_tz
│   ├── caldav-mirrors.toml        external CalDAV mirrors (optional)
│   └── branch-state.toml          preferred branch per remote (optional)
├── identities/
│   └── <person-id>.md             one identity per person; optional GPG
│                                  signing fingerprint pointer
├── calendars/
│   └── <calendar-id>/
│       ├── calendar.toml          metadata for this calendar
│       ├── events/<yyyy>/<mm>/    one-off events
│       │   └── <event-id>.md
│       │   └── <event-id>.comments/   ← per-event comments (D.29)
│       │       └── <comment-id>.md
│       ├── recurrences/           RRULE-bearing rules
│       └── exceptions/<rule-id>/  per-occurrence overrides
├── todolists/
│   └── <todolist-id>/
│       ├── todolist.toml
│       ├── tasks/<yyyy>/<mm>/     dated tasks
│       ├── standing/              undated tasks
│       └── recurrences/           recurring chores
└── attachments/
    └── <2-hex-prefix>/<sha256>.<ext>
```

The `<yyyy>/<mm>/` bucketing applies to events and dated tasks —
the year and month come from the entity's `start` (events) or `due`
(tasks) **in the calendar/todolist's `tz_id`**, not UTC. `skb`
handles the bucket math; if you're hand-editing, see the "Direct
file editing" section below.

## Defaults for this repo

- Default identity: `{{DEFAULT_IDENTITY_ID}}`.
- Default calendar: `{{DEFAULT_CALENDAR_ID}}`.
- Default todolist: `{{DEFAULT_TODOLIST_ID}}`.
- Default timezone: `{{TZ_ID}}`.

`skb` uses these automatically. When hand-editing, plug them into
the `author`, `calendar_id`, `todolist_id`, and `tz_id` fields.

## Direct file editing — valid fallback when the CLI is unavailable

The CLI is the preferred write path because it handles validation,
atomic single-file writes, auto-commits with conventional messages,
attachment SHA-addressing, month-bucket moves on edit, recurrence-
exception placement, and the identity/default-calendar plumbing.

Direct file editing is **valid** — the app and `skb` will read what
you write — but you become responsible for everything `skb` does
for you. Specifically:

1. Generate a fresh UUIDv7 for any new entity. The CLI uses an
   RFC 9562 generator; if you're hand-editing, `python -c "import
   uuid; print(uuid.uuid7())"` (Python 3.13+) or `uuidgen --v7` on
   recent util-linux work fine. Lowercase canonical form.
2. Build the path yourself: events go to
   `calendars/<cal-id>/events/<yyyy>/<mm>/<event-id>.md` where
   `<yyyy>/<mm>` comes from the event's `start` in the calendar's
   `tz_id` (NOT UTC).
3. Stamp `created_at`, `updated_at`, `author`, `schema_version = 1`,
   and the entity-specific `kind` discriminator.
4. Commit with a message in the convention the app uses (see
   "Commit message conventions" below) so the "recent changes" UI
   renders cleanly. Free-form messages still work — they just look
   less tidy.
5. On `start` edits that cross a month boundary, `git mv` the file
   to the new bucket in the same commit.
6. On event delete with a `spawns_task = X`, decide whether to
   unlink the task or also delete it. `skb event delete` prompts;
   if hand-editing, default to "unlink" (clear `spawned_by_event`
   on the task).

If any of this feels error-prone — that's why `skb` exists.

## The hard rules (apply to both `skb` and hand-edits)

1. **One file per entity. One entity per file.** Never bundle two
   events in the same file. `skb` enforces this; hand-edits must
   respect it.
2. **Never create an index file.** No `events.json`, no
   `tasks.csv`, no `summary.md`. Indexes are merge-conflict
   factories. The app and `skb` rebuild any aggregate they need
   from a filesystem scan on every git HEAD change.
3. **IDs are UUIDv7, lowercase, canonical form.** Filenames embed
   the ID.
4. **File format = TOML frontmatter + Markdown body, fenced by
   `+++`.** The frontmatter is structured; the body is yours. The
   four metadata files (`.strictlykeptboy/schema.toml`,
   `.strictlykeptboy/repo.toml`, `calendars/<id>/calendar.toml`,
   `todolists/<id>/todolist.toml`, plus the Round-2-added
   `caldav-mirrors.toml` and `branch-state.toml`) are TOML-only
   (no `+++` fences, no body).
5. **Schema version is `1`.** Every entity file starts with
   `schema_version = 1`. Round 2 added new optional fields without
   bumping the schema.
6. **Comments inside TOML frontmatter are dropped on next app or
   `skb` write.** Put commentary in the Markdown body, or as an
   event-comment file under `<event-id>.comments/`.

## Examples — CLI primary, file content secondary

### Example 1 — add an event

CLI (preferred):

```sh
skb event add \
  --calendar "Personal" \
  --title "Dentist — 6-month checkup" \
  --start 2026-05-12T14:00 \
  --end   2026-05-12T14:45 \
  --location "Dr. Köhler, Hauptstraße 14, Stuttgart" \
  --notification 1d --notification 2h --notification 15m \
  --tag health --tag dental \
  --emoji 🦷
# stdout: created event 0190d4a0-7fab-7c50-9c1e-2b7a44f6f001
#         in Personal at 2026/05/, committed as
#         "add event \"Dentist — 6-month checkup 14:00\" in Personal"
```

Resulting file (`skb` wrote this for you; shown for read/diff):

`calendars/0190a0aa-1c1d-7000-8a0a-000000000001/events/2026/05/0190d4a0-7fab-7c50-9c1e-2b7a44f6f001.md`

```
+++
schema_version = 1
id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"
kind = "event"
created_at = 2026-05-09T18:30:00+02:00
updated_at = 2026-05-09T18:30:00+02:00
author = "{{DEFAULT_IDENTITY_ID}}"

title = "Dentist — 6-month checkup"
start = 2026-05-12T14:00:00+02:00
end = 2026-05-12T14:45:00+02:00
calendar_id = "{{DEFAULT_CALENDAR_ID}}"
location = "Dr. Köhler, Hauptstraße 14, Stuttgart"
notifications = ["1d", "2h", "15m"]
tags = ["health", "dental"]
emoji = "🦷"
+++

(empty body — add notes via `skb event edit --id … --body-append …`
or by editing the file directly between the closing `+++` and EOF.)
```

### Example 2 — cancel one instance of a recurring event

CLI (preferred):

```sh
skb recurrence cancel-instance \
  --rule "Standup" \
  --date 2026-05-11 \
  --reason "Whit Monday (public holiday)"
# stdout: cancelled instance 2026-05-11 of recurrence
#         0190d4ab-2b7a-7c50-9c1e-cccccccccccc, committed as
#         "cancel recurring event \"Standup\" on 2026-05-11"
```

DO NOT edit the recurrence rule file to drop a date — that loses
history and is hard for the resolver to interpret. The CLI writes
an exception file; if hand-editing, the equivalent is to create
`calendars/<CAL_ID>/exceptions/<RULE_ID>/2026-05-11.md` with
`kind = "exception"`, `mode = "cancel"`, `instance_date = 2026-05-11`,
and the standard header fields.

### Example 3 — add a comment to an event

CLI (preferred):

```sh
skb comment add \
  --event-title "Dentist" \
  --body "Dr. Köhler's office moved — new address Hauptstraße 14."
# stdout: created comment 0190d4ff-0001-7c50-9c1e-cccccccccc01
#         on 0190d4a0-…, committed as
#         "add comment on \"Dentist — 6-month checkup\""

skb comment list --event-title "Dentist" --json
# stdout: [{"id":"…","author":"…","created_at":"…","body":"…"}, …]
```

Resulting file (`skb` wrote this; shown for read/diff):

`calendars/<cal>/events/2026/05/0190d4a0-….comments/0190d4ff-0001-7c50-9c1e-cccccccccc01.md`

```
+++
schema_version = 1
id = "0190d4ff-0001-7c50-9c1e-cccccccccc01"
kind = "comment"
event_id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001"
author = "{{DEFAULT_IDENTITY_ID}}"
created_at = 2026-05-10T18:42:00+02:00
+++

Dr. Köhler's office moved — new address Hauptstraße 14.
```

Comments are first-class entities — readable, writable, searchable.
Two agents commenting concurrently produce two files; no merge
conflicts.

## Multi-agent etiquette

When multiple agents (or an agent and a human) work on the same
repo concurrently:

- **Use a branch.** Run `skb branch create claude/<short-task>` at
  the start of your session. The app and `skb` are branch-aware
  per D.36. The human merges via PR at the provider side (GitHub
  / Forgejo).
- **One commit per logical change** is the default with `skb`. If
  you're hand-editing, keep commits small — single-file changes
  are conflict-free.
- **Never rewrite history.** No `git rebase -i`, no
  `filter-branch`. The app's HEAD-walk cache invalidation depends
  on monotonic history.
- **Comment, don't overwrite.** If the user has a note in an event
  body and you want to add something, append, or use the
  `<event-id>.comments/` directory.
- **Identity attribution matters.** Every entity carries
  `author = "<person-id>"`. If you're acting under a separate
  agent identity, the wizard or the user will have created an
  identity file for you. Use that `id` in the `author` field
  (and `skb` does this automatically when you set
  `SKB_ACTIVE_IDENTITY=<id>` in your environment).

## Validation rules (the validator the app and `skb` share)

The app and `skb` refuse to write malformed entries. If you write
something malformed by hand, the app surfaces it in a "broken
entries" tray on next scan, **but does not auto-delete it**. Fix
the file and let the app re-read; or run `skb verify --fix-paths`
which corrects path-bucket mismatches automatically.

A file is malformed if any of:

- `+++` fences missing or asymmetric.
- `schema_version` missing or not an integer.
- `id` missing or not a valid UUIDv7.
- `kind` missing or not a recognised value.
- A required field for the `kind` is missing.
- A foreign-key field references a non-existent entity.
- `end < start` on an event.
- `done = true` without `done_at`.
- An exception's `rule_id` doesn't match an existing recurrence.
- A comment's `event_id` doesn't match the parent event file.
- A comment's `in_reply_to` creates a cycle.

## Commit message conventions

The app and `skb` stamp commits as:

- `add event "<title>" in <calendar-name>`
- `update event "<title>" in <calendar-name>`
- `remove event "<title>" from <calendar-name>`
- `cancel recurring event "<title>" on <YYYY-MM-DD>`
- `override recurring event "<title>" on <YYYY-MM-DD>`
- `add task "<title>" in <todolist-name>`
- `complete task "<title>" in <todolist-name>`
- `add comment on "<event-title>"`
- `remove comment on "<event-title>" by <author>`
- `migrate schema v<old> → v<new>`

Free-form messages still work — they just look less tidy in the
app's "recent changes" UI.

## Things you should NOT do

- Do **not** create files at the repo root that aren't in the
  schema.
- Do **not** rename calendar or todolist folders by hand — the
  folder name IS the entity ID. Use `skb cal edit --id … --name
  …`.
- Do **not** rewrite history.
- Do **not** commit secrets to attachments. Repos may be private
  at the provider level, but cloned worktrees on shared machines
  are not encrypted at rest.
- Do **not** edit `.strictlykeptboy/caldav-mirrors.toml`'s
  `last_synced_at` or `last_etag` by hand. Those are bookkeeping
  fields the sync engine owns.
- Do **not** put private keys anywhere in the repo. The
  `gpg_signing_key_fingerprint` field on an identity is a
  **lookup key into the device's encrypted keystore** — the
  private bytes never enter the file.

## Where to find more

- App-side architectural reference (planning docs):
  https://github.com/eight87/strictlykeptboy/tree/main/docs/plans
- `skb` CLI reference: `skb help --json` (machine-readable) or
  `skb <command> --help` (human).
- Decisions doc:
  https://github.com/eight87/strictlykeptboy/blob/main/docs/plans/decisions.md
- File schema reference:
  https://github.com/eight87/strictlykeptboy/blob/main/docs/plans/data-model.md
```

---

## Phase DM-P — updated deferrals roll-up

This phase **explicitly closes the loop** between Round 1's open-
questions list and Round 2's scope expansion. It is a no-code phase
— its deliverable is the surgical update applied to the
"[Open questions deferred to future schema versions](#open-questions-deferred-to-future-schema-versions)"
section below.

- [ ] **DM-P.1** Audit the Round 1 deferrals list (six items) against
      [decisions.md](decisions.md) D.23–D.40. For each:
      - Mark ✅ **MOVED TO v1** if Round 2 promoted it (with a
        pointer to the new DM-phase that specs the field).
      - Mark ⚠️ **STILL DEFERRED v1.1** if Round 2 left it out (with
        a one-sentence reason why).
- [ ] **DM-P.2** Outcomes:
      - Item 1 (comment-preserving TOML writer): ⚠️ STILL DEFERRED.
        ktoml limitation persists; both write paths (app + `skb`)
        would need it; multi-week work unjustified.
      - Item 2 (per-event `tz_id`): ✅ MOVED TO v1 — see
        [DM-L](#phase-dm-l--multi-timezone-fields-d27).
      - Item 3 (Unicode tags): ⚠️ STILL DEFERRED. Round 2 added no
        use case that flips the calculus.
      - Item 4 (body-checkbox ↔ done default flip): ⚠️ STILL
        DEFERRED. Decision needs usage data including `skb`-driven
        agent sessions; revisit after v1 ships.
      - Item 5 (soft-delete via `_trash/`): ⚠️ STILL DEFERRED. Git
        history covers undo for entities and the new DM-K comments
        alike.
      - Item 6 (in-app attachment GC): ⚠️ STILL DEFERRED. Measure
        orphan accumulation in real v1 repos first.
- [ ] **DM-P.3** Cross-link `cli-tooling.md` CLI surface to the
      deferred-items list — `skb verify` reports issues that the
      v1.1 in-app GC would auto-fix, so users on v1 have a
      one-command workaround in the meantime.
- [ ] **DM-P.4** Acceptance: the deferrals section below contains
      exactly one ✅ MOVED entry and five ⚠️ STILL DEFERRED entries
      after this phase lands.

---

## Open questions deferred to future schema versions

These were resolved for v1 by intentionally not addressing them. Each
has a tracking note pointing back to its DM-* phase. Round 2 scope
expansion (D.23–D.40) **promoted some items into v1** — those are
marked ✅ MOVED below. Round 2 also **confirmed others as still
deferred** — those are marked ⚠️ STILL DEFERRED v1.1.

1. ⚠️ **STILL DEFERRED v1.1 — Comment preservation in TOML
   frontmatter** (DM-A.4). v1 drops comments on rewrite. v1.1 / v2
   candidate: ship a comment-aware TOML writer. Rationale for
   continued deferral: ktoml still does not support it, the
   write-path now also runs through `skb` (DM-O) which would need the
   same support, and Round 2 added enough scope that a multi-week
   round-tripper project is still not justified.
2. ✅ **MOVED TO v1 — Per-event `tz_id`** (was DM-B.1 / DM-F.6 open
   question; now spec'd in **[Phase DM-L](#phase-dm-l--multi-timezone-fields-d27)**).
   D.27 promotes multi-timezone to first-class. Schema_version stays
   1 because the field is additive and optional — repos without it
   continue to work unchanged.
3. ⚠️ **STILL DEFERRED v1.1 — Unicode tags** (DM-F.6). v1 still
   enforces ASCII `[a-z0-9_-]+`. Rationale: ASCII-only avoids
   normalisation bugs and case-folding traps in the search index;
   Round 2 did not surface a use case that flips this calculus.
4. ⚠️ **STILL DEFERRED v1.1 — Body-checkbox ↔ `done` two-way sync
   as default** (DM-B.6, DM-J.2). v1 ships opt-in via
   `body_checkbox_authoritative`. We will flip the default only
   after we see usage data on how AI agents (including `skb`-driven
   Claude sessions per DM-O) treat tasks in practice.
5. ⚠️ **STILL DEFERRED v1.1 — Soft-delete vs hard-delete** (DM-I.4,
   and now also DM-K for comments). v1 hard-deletes on confirm.
   Tombstone files in `_trash/` add surface area without proportional
   value; git history already provides undo for both entities and
   comments. Comment hard-delete is specifically locked into DM-K to
   preserve the no-merge-conflict invariant.
6. ⚠️ **STILL DEFERRED v1.1 — Attachment GC trigger** (DM-B.10). v1
   leaves orphaned attachments in place; sweep is a manual
   `tools/gc.sh` (or `skb gc attachments` per DM-O). v1.1 may ship
   in-app GC after we measure how often orphans actually accumulate
   in real repos.

---

## Status footer

Every phase above is sub-step-checkbox-tickable. As DM-* work lands,
tick the boxes here and add the jj change ID to the phase header per
the global plan-file convention. When all DM-* phases are ticked, flip
the top-of-file status from `🚧 IN-PLANNING` to `✅ DONE`.

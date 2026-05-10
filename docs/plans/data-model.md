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

## Open questions deferred to future schema versions

These were resolved for v1 by intentionally not addressing them. Each
has a tracking note pointing back to its DM-* phase.

1. **Comment preservation in TOML frontmatter** (DM-A.4). v1 drops
   comments on rewrite. v2 candidate: ship a comment-aware TOML
   writer. Rationale for deferral: building a comment-preserving
   round-tripper is multi-week work and ktoml does not support it
   today; not worth blocking v1.
2. **Per-event `tz_id`** (DM-B.1, DM-F.6). One-off events store an
   offset only. v2 candidate: optional `tz_id` for events that must
   "follow the user across timezones". Rationale: user research will
   tell us whether this is a real complaint or a theoretical one; v1
   ships without and we listen.
3. **Unicode tags** (DM-F.6). v1 enforces ASCII. v2 candidate:
   Unicode normalisation per Annex 31. Rationale: ASCII-only avoids
   normalisation bugs and case-folding traps in the search index;
   not worth the complexity at v1.
4. **Body-checkbox ↔ `done` two-way sync as default** (DM-B.6,
   DM-J.2). v1 ships opt-in via `body_checkbox_authoritative`. v2
   may flip the default after we see how AI agents treat tasks in
   practice.
5. **Soft-delete vs hard-delete** (DM-I.4). v1 hard-deletes on
   confirm. v2 candidate: tombstone files in `_trash/` for undo.
   Rationale: git history already provides undo; tombstones add
   surface area without proportional value at v1.
6. **Attachment GC trigger** (DM-B.10). v1 leaves orphaned
   attachments in place; sweep is a manual `tools/gc.sh`. v2 may
   ship in-app GC after we measure how often orphans actually
   accumulate.

---

## Status footer

Every phase above is sub-step-checkbox-tickable. As DM-* work lands,
tick the boxes here and add the jj change ID to the phase header per
the global plan-file convention. When all DM-* phases are ticked, flip
the top-of-file status from `🚧 IN-PLANNING` to `✅ DONE`.

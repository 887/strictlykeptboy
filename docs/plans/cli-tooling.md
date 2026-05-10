# strictlykeptboy — `skb` CLI deep-dive

## Status: 🚧 IN-PLANNING

This is the authoritative specification for the `skb` command-line
interface, the **primary write path** into a strictlykeptboy data repo.
Per [`decisions.md`](decisions.md) D.24, `skb` and the Android GUI are
peers — the GUI is the human's window onto the data; `skb` is the
AI agent's (Claude's) and the power-user's window. They write the same
files via the same atomic-write + auto-commit invariants.

Every section is decided. Tradeoffs that surfaced during elaboration
are resolved inline as **Decision:** lines with rationale — no punts,
no `TBD`. All phases here carry the prefix `CLI-` and map onto Phase X
of [`main.md`](main.md).

**Cross-reference table** — each `CLI-` phase ↔ `main.md` Phase X
sub-step (where applicable):

| CLI phase | main.md sub-step | Topic |
|---|---|---|
| CLI-A | X.1 | Subcommand surface (every command spec) |
| CLI-B | X.4, X.6 | Cross-cutting writes: atomic, auto-commit, dry-run |
| CLI-C | X.5, X.10 | `--json` output schema + help system |
| CLI-D | X.7 | Error model + exit codes |
| CLI-E | X.11 | Repo discovery + config + identity binding |
| CLI-F | — | Transactions (multi-command commits) |
| CLI-G | X.9 | AGENTS.md CLI-first content (coordinated with DM-K) |
| CLI-H | X.2, X.3, X.8 | JVM module + shell wrapper + distribution |
| CLI-I | X.12 | Test strategy |
| CLI-J | — | Logging, editor integration, stdin piping, completions |
| CLI-K | — | Future hooks (daemon, MCP server, websockets) |

---

## Phase CLI-A — subcommand surface

Goal: every command Claude (or a human) might invoke is specified
end-to-end: synopsis, args, flags, stdout (human + `--json`), stderr,
exit codes, behavior notes, worked example. Every write command obeys
CLI-B (atomic + auto-commit). Every command supports `--json`,
`--repo`, `--dry-run`, `--verbose`, `--quiet`, `--no-color`, plus the
command-specific flags below.

- [ ] **CLI-A.1** Event surface (`event add | list | edit | show | cancel | delete`)
- [ ] **CLI-A.2** Task surface (`task add | done | undone | list | show | edit | delete | import-csv`)
- [ ] **CLI-A.3** Recurrence surface (`recurrence add | edit | show | cancel-instance | override-instance`)
- [ ] **CLI-A.4** Calendar surface (`cal add | list | edit | show`)
- [ ] **CLI-A.5** Todolist surface (`list add | list | edit | show`)
- [ ] **CLI-A.6** Identity surface (`identity list | set-active | create | edit | gpg-import | gpg-list`)
- [ ] **CLI-A.7** Attachment surface (`attach add | list | show | rm`)
- [ ] **CLI-A.8** View surface (`show <date>` / `week` / `month`)
- [ ] **CLI-A.9** Free-time finder (`find-free`)
- [ ] **CLI-A.10** Sync surface (`sync`)
- [ ] **CLI-A.11** Templates (`apply-template`)
- [ ] **CLI-A.12** Migrate (`migrate`)
- [ ] **CLI-A.13** Verify (`verify`)
- [ ] **CLI-A.14** Comments (`comment add | list | show | edit | delete`)
- [ ] **CLI-A.15** Weather (`weather show`)
- [ ] **CLI-A.16** Timezone (`tz convert` / `tz list-zones`)
- [ ] **CLI-A.17** CalDAV (`caldav add | sync | remove | list`)
- [ ] **CLI-A.18** Branches (`branch list | create | switch | delete`)
- [ ] **CLI-A.19** Repos (`repo add | list | switch-active | remove | init`)
- [ ] **CLI-A.20** Help system (`help [<command>]`)
- [ ] **CLI-A.21** Transactions (`tx start | commit | abort | status | history`)
- [ ] **CLI-A.22** Self-update (`self-update`) + `--version`

### CLI-A.1 — `skb event …`

#### `skb event add`

**Synopsis:** `skb event add --title <s> --start <iso> --end <iso> [--calendar <name-or-id>] [flags]`

**Description:** Create a new one-off event file at
`calendars/<cal-id>/events/<yyyy>/<mm>/<event-id>.md`. Writes atomically
and commits.

**Positional args:** none.

**Flags:**

| Flag | Type | Default | Notes |
|---|---|---|---|
| `--title` | string | required | event title, ≤ 200 chars |
| `--start` | offset-datetime | required | RFC 3339, e.g. `2026-05-12T14:00:00+02:00` |
| `--end` | offset-datetime | `--start + --duration` | mutually exclusive with `--duration` |
| `--duration` | ISO 8601 duration | — | e.g. `PT45M`; mutually exclusive with `--end` |
| `--calendar` | string | repo default | display name OR UUIDv7; ambiguity → exit 1 |
| `--all-day` | bool flag | false | if set, `--start`/`--end` parsed as local-date |
| `--location` | string | — | free text |
| `--geo` | `lat,lon` | — | parsed as `{lat=..., lon=...}` |
| `--attendees` | comma-list | `[]` | identity IDs or free-form names |
| `--notify` | comma-list | `[]` | lead-times: `1h,15m,1d` |
| `--notification-group` | string | calendar default | overrides |
| `--tag` | repeatable string | `[]` | each must match `[a-z0-9_-]+` |
| `--color` | `#rrggbb` | calendar color | |
| `--emoji` | string (one grapheme) | — | |
| `--priority-override` | int 1..1000 | — | |
| `--busy / --free` | bool | `--busy` | `--free` = transparent (not in find-free) |
| `--external-uid` | string | — | iCal UID, for round-trip |
| `--spawns-task` | UUIDv7 | — | requires `--spawns-task-in-list` |
| `--spawns-task-in-list` | string | — | display name or UUIDv7 |
| `--body` | string | — | inline body content |
| `--body-file` | path | — | read body from file |
| `--body-editor` | bool flag | false | open `$EDITOR`/`$VISUAL` |
| `--from-stdin` | bool flag | false | read full TOML+body from stdin |
| `--id` | UUIDv7 | auto-gen | **idempotency**: if `--id` set and file already exists, behaves as `event edit` |
| `--author` | UUIDv7 | active identity | identity ID |
| `--no-commit` | bool flag | false | stage but don't commit; for `tx` batches |
| `--dry-run` | bool flag | false | print proposed file, do not write |
| `--json` | bool flag | false | machine-readable output |

**Stdout (human):**

```text
$ skb event add --title "Dentist" --start 2026-05-12T14:00:00+02:00 --duration PT45M --calendar Personal --notify 1d,15m --tag health
✓ added event "Dentist"
  id:        0190d4a0-7fab-7c50-9c1e-2b7a44f6f001
  when:      Tue 2026-05-12  14:00–14:45 (Europe/Berlin)
  calendar:  Personal
  file:      calendars/0190a0aa-1c1d-7000-8a0a-000000000001/events/2026/05/0190d4a0-7fab-7c50-9c1e-2b7a44f6f001.md
  commit:    7c4b9e2  "add event \"Dentist\" in Personal"
```

**Stdout (`--json`):**

```text
$ skb event add --title "Dentist" --start 2026-05-12T14:00:00+02:00 --duration PT45M --calendar Personal --json
{
  "version": 1,
  "command": "event.add",
  "result": {
    "id": "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001",
    "kind": "event",
    "title": "Dentist",
    "start": "2026-05-12T14:00:00+02:00",
    "end": "2026-05-12T14:45:00+02:00",
    "calendar_id": "0190a0aa-1c1d-7000-8a0a-000000000001",
    "calendar_name": "Personal",
    "path": "calendars/0190a0aa-1c1d-7000-8a0a-000000000001/events/2026/05/0190d4a0-7fab-7c50-9c1e-2b7a44f6f001.md",
    "commit": "7c4b9e2a8d1f4e0b3c2a1f9e8d7c6b5a4e3d2c1b",
    "created": true
  }
}
```

**Stderr:** validation failures (`end before start`, unknown calendar,
malformed `--start`, ID collision when `--id` provided and existing
file's `kind != "event"`).

**Exit codes:** 0 ok, 1 usage, 2 not-found (`--calendar` doesn't
resolve), 3 conflict (`--id` collides with a non-event file), 5 corrupt
(repo broken), 6 schema-mismatch.

**Behavior notes:**
- Atomic write: temp file `events/.../.<id>.md.tmp.<pid>` → rename.
- Auto-commit message: `add event "<title>" in <calendar-name>`.
- Idempotency: `--id X` + existing file = update (returns
  `"created": false` in JSON).
- `--dry-run` prints the would-be file contents to stdout under a
  `result.preview` key in `--json` mode.
- The bucket year/month is derived from `--start` in the calendar's
  `tz_id` (per DM-C.3).

**Worked example:**

```text
$ skb event add \
    --title "Standup retro recap" \
    --start 2026-05-12T10:00:00+02:00 \
    --duration PT30M \
    --calendar Work \
    --notify 5m \
    --tag work,ritual \
    --emoji 🧍 \
    --body "Owner: Anna. Bring last sprint's retro notes."
✓ added event "Standup retro recap"
  id:        0190d4cf-5555-7c50-9c1e-aaaaaaaaaaaa
  when:      Tue 2026-05-12  10:00–10:30 (Europe/Berlin)
  calendar:  Work
  file:      calendars/.../events/2026/05/0190d4cf-5555-7c50-9c1e-aaaaaaaaaaaa.md
  commit:    a3f2e1b
```

#### `skb event list`

**Synopsis:** `skb event list [--calendar <name-or-id>] [--from <date>] [--to <date>] [--tag <t>] [--author <id>] [--limit N]`

**Description:** List events matching filters. Default range: today ±
30 days. Default sort: `start` ascending.

**Flags:**

| Flag | Type | Default | Notes |
|---|---|---|---|
| `--calendar` | string | (all) | filter to one calendar |
| `--from` | date | today − 30d | inclusive |
| `--to` | date | today + 30d | inclusive |
| `--tag` | repeatable | — | filter to events containing any tag |
| `--author` | UUIDv7 | — | filter to one author |
| `--text` | string | — | substring search over title + body |
| `--limit` | int | 100 | cap result count |
| `--sort` | enum | `start` | `start` / `created` / `updated` |
| `--reverse` | bool | false | descending |
| `--include-cancelled` | bool | false | include cancelled recurring instances |

**Stdout (human):**

```text
$ skb event list --from 2026-05-10 --to 2026-05-14
3 events  (Mon 2026-05-11 – Wed 2026-05-13, Europe/Berlin)

Mon 2026-05-11
  09:30–09:45  Standup                            [Work]      0190d4ab…
  18:00–19:00  Gym                                [Personal]  0190d4b1…

Tue 2026-05-12
  10:00–10:30  Standup (extended for retro)       [Work]      override of 0190d4ab…
  14:00–14:45  Dentist — 6-month checkup          [Personal]  0190d4a0…
```

**Stdout (`--json`):** array under `result.events`. Each entry has the
full frontmatter dict plus computed fields `path`, `commit_last_touch`,
`is_recurrence_instance`, `is_override`, `rule_id` (if instance).

```text
$ skb event list --from 2026-05-12 --to 2026-05-12 --calendar Personal --json
{
  "version": 1,
  "command": "event.list",
  "result": {
    "count": 1,
    "events": [
      {
        "id": "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001",
        "kind": "event",
        "title": "Dentist — 6-month checkup",
        "start": "2026-05-12T14:00:00+02:00",
        "end": "2026-05-12T14:45:00+02:00",
        "calendar_id": "0190a0aa-1c1d-7000-8a0a-000000000001",
        "calendar_name": "Personal",
        "tags": ["health", "dental"],
        "is_recurrence_instance": false,
        "path": "calendars/.../2026/05/0190d4a0-….md"
      }
    ]
  }
}
```

**Exit codes:** 0, 1 (bad filter), 2 (calendar not found).

#### `skb event show`

**Synopsis:** `skb event show <id-or-prefix> [--body-only | --frontmatter-only | --raw]`

**Description:** Show a single event. `<id-or-prefix>` matches against
the full UUIDv7 with a unique prefix of ≥ 8 hex chars accepted.

**Stdout (human):**

```text
$ skb event show 0190d4a0
0190d4a0-7fab-7c50-9c1e-2b7a44f6f001 · event · Personal

  Title:     Dentist — 6-month checkup
  When:      Tue 2026-05-12  14:00–14:45 (Europe/Berlin)
  Where:     Dr. Köhler, Hauptstraße 14, Stuttgart
  Notify:    1d, 2h, 15m
  Tags:      health, dental
  Author:    Alex (01900000-…-aaaaaaaaaaaa)
  Created:   2026-05-09T18:30:00+02:00
  Updated:   2026-05-09T18:30:00+02:00
  Attachments:
    - last-xray.png  (180 KB, image/png)  sha:9f86d081…

  Body
  ──────────────────────────────────────────────────────────
  Reminder: bring the last X-ray printout. Park behind the
  building, gate code 4488. Booked at 2025-11-12 by phone.
  Insurance card needed.
```

**Stdout (`--json`):** the entire frontmatter dict + `body` string +
`path`.

**Flags:**
- `--raw`: emit the file's bytes verbatim (including `+++` fences).
  Useful for AI diffing.
- `--body-only` / `--frontmatter-only`: scope.

**Exit codes:** 0, 2 (not found), 1 (ambiguous prefix).

#### `skb event edit`

**Synopsis:** `skb event edit <id> [field-flags...]`

**Description:** Edit fields on an existing event. Same flag set as
`event add`. Unspecified fields preserved. Setting a value to the empty
string clears it (e.g. `--location ""` removes location).

**Decision:** the writer always bumps `updated_at` to "now" on every
edit, regardless of whether any field actually changed. Rationale: a
spurious no-op edit becomes traceable in git history; AI agents
occasionally re-run identical edits as part of retries and we want
those to be visible as commits, not invisible.

**Special flags:**
- `--move-to-calendar <cal>`: re-files the event under the new
  calendar's tree. Single commit, file moves via `git mv`.
- `--add-tag <t>` / `--remove-tag <t>`: incremental tag editing
  without re-typing the full list.

**Stdout:** same shape as `add`, with `"created": false`.

**Exit codes:** 0, 1, 2, 3 (concurrent modify — file changed on disk
since opening; rare but caught via mtime+sha precheck), 6.

#### `skb event cancel`

**Synopsis:** `skb event cancel <id>`

**Description:** Mark a one-off event as cancelled by setting
`status = "cancelled"` in frontmatter (a v1 addition: events gain an
optional `status` enum with values `confirmed` (default), `cancelled`,
`tentative`; see DM-K coordination).

**Decision:** `cancel` does NOT delete the file. Rationale: cancelled
events still need to render struck-through in views and stay in the
audit trail. To remove the file entirely use `event delete`.

**Stdout (human):** `✓ cancelled event "<title>"` + commit SHA.

**Commit message:** `cancel event "<title>" in <calendar-name>`.

**Exit codes:** 0, 2.

#### `skb event delete`

**Synopsis:** `skb event delete <id> [--cascade-tasks]`

**Description:** Remove the event file from the repo.

**Flags:**
- `--cascade-tasks`: also delete the task referenced by `spawns_task`.
  Default: unlink (clear `spawned_by_event` on the task; keep the task).

**Stdout:** `✓ deleted event "<title>"` + commit SHA.

**Commit message:** `remove event "<title>" from <calendar-name>`.

**Exit codes:** 0, 2.

---

### CLI-A.2 — `skb task …`

#### `skb task add`

**Synopsis:** `skb task add --title <s> [--list <name-or-id>] [--due <iso-or-date>] [--standing] [flags]`

**Flags:**

| Flag | Type | Default | Notes |
|---|---|---|---|
| `--title` | string | required | |
| `--list` | string | repo default | display name or UUIDv7 |
| `--due` | datetime/date | — | offset-datetime or local-date |
| `--start-after` | offset-datetime | — | hide until |
| `--standing` | bool flag | false | force into `standing/`; rejects `--due` |
| `--priority` | int 1..1000 | list default | |
| `--auto-done-eod` | bool | list default | |
| `--tag` | repeatable | `[]` | |
| `--notify` | comma-list | `[]` | |
| `--notification-group` | string | list default | |
| `--spawned-by-event` | UUIDv7 | — | |
| `--spawned-by-recurrence` | UUIDv7 | — | requires `--spawn-instance-date` |
| `--spawn-instance-date` | date | — | |
| `--pinned` | bool flag | false | standing-only |
| `--body` / `--body-file` / `--body-editor` / `--from-stdin` | as event | — | |
| `--id` | UUIDv7 | auto-gen | idempotency |
| `--author` / `--no-commit` / `--dry-run` / `--json` | as event | | |

**Stdout (human):**

```text
$ skb task add --title "Buy milk" --list "Daily chores" --due 2026-05-09T18:30:00+02:00 --notify 30m
✓ added task "Buy milk"
  id:        0190d500-aaaa-7c50-9c1e-bbbbbbbbbbbb
  due:       Sat 2026-05-09  18:30 (Europe/Berlin)
  list:      Daily chores
  file:      todolists/.../tasks/2026/05/0190d500-….md
  commit:    9c1a2b3  "add task \"Buy milk\" in Daily chores"
```

**Commit message:** `add task "<title>" in <todolist-name>`.

#### `skb task done`

**Synopsis:** `skb task done <id> [--at <iso>]`

**Description:** Set `done = true` and `done_at = <at>` (default: now).

**Decision:** running `task done` on an already-done task succeeds with
exit 0 and a no-op commit suppression (writer detects no change and
skips the commit; `--json` returns `"changed": false`). Rationale:
idempotency for AI retries.

**Stdout:** `✓ completed task "<title>"` + commit (or
`(no change — already done)`).

**Commit message:** `complete task "<title>" in <todolist-name>`.

#### `skb task undone`

**Synopsis:** `skb task undone <id>`

Clears `done` + `done_at`. Commit message:
`reopen task "<title>" in <todolist-name>`.

#### `skb task list`

**Synopsis:** `skb task list [--list <name>] [--from <date>] [--to <date>] [--status <enum>] [--tag <t>] [--limit N]`

**Flags:**
- `--status`: `open` (default) / `done` / `all`.
- Includes resolver-materialised recurrence instances by default; toggle
  with `--exclude-recurrence-instances`.

**Stdout (human):**

```text
$ skb task list --list "Daily chores" --from 2026-05-09 --to 2026-05-09
2 open tasks

Sat 2026-05-09  Daily chores
  [ ]  18:30   Buy milk                          0190d500-…
  [ ]  --      Resharpen the kitchen knives      0190d600-…  (standing, pinned)
```

#### `skb task show`

Same shape as `event show`. Body checkbox state surfaced under a
`checkboxes:` block in human stdout.

#### `skb task edit`

Same shape as `event edit`. Special flags:
- `--move-to-list <l>`: re-file under a different todolist.
- `--promote-standing` / `--demote-to-standing`: convert between
  `task` and `standing_task` kinds; the latter clears `due`.

#### `skb task delete`

**Synopsis:** `skb task delete <id> [--unlink-event]`

`--unlink-event` clears `spawns_task` on the parent event before
deleting the task. Default behaviour: warn if the task is linked from
an event, exit 3 unless `--unlink-event` is passed or `--force` is set.

#### `skb task import-csv`

**Synopsis:** `skb task import-csv <file> --list <todolist> [--mapping <key=col,key=col>] [--id-from <col>]`

**Description:** Bulk-import tasks from a CSV file. First-row header
required. Auto-detected columns: `title|task|todo`, `due|due_date`,
`done|completed`, `priority`, `list|todolist`, `tags`.

**Flags:**
- `--mapping`: explicit field-to-column mapping for non-standard CSVs.
- `--id-from <col>`: use a column as the UUIDv7 (must be valid UUIDv7);
  on re-import this gives row-level idempotency.
- `--row-hash-idempotency`: derive a deterministic UUIDv7-equivalent
  from row hash (per D.38 + Phase KK.4).

**Commit message:** `import N tasks from <basename(file)> into <list>` — single batched commit, not one per task.

**Stdout (human):** progress summary.

```text
$ skb task import-csv ./groceries.csv --list "Shopping"
Mapping detected:
  title       <- "Item"
  due         <- (none)
  priority    <- "Pri"
✓ imported 23 tasks (0 updated, 23 created)
  commit:  4f2a8b1  "import 23 tasks from groceries.csv into Shopping"
```

**Exit codes:** 0, 1 (CSV malformed), 2 (list not found), 5 (target
file already exists with same UUID but different kind).

---

### CLI-A.3 — `skb recurrence …`

#### `skb recurrence add`

**Synopsis:** `skb recurrence add --title <s> --calendar <c> --dtstart <local-iso> --duration <iso8601> --rrule <s> --tz <iana> [flags]`

**Flags:**

| Flag | Type | Default | Notes |
|---|---|---|---|
| `--title` | string | required | |
| `--calendar` | string | — | OR `--list` for task recurrence |
| `--list` | string | — | mutually exclusive with `--calendar` |
| `--dtstart` | local-datetime | required | `2026-01-05T09:30:00` (no offset) |
| `--duration` | ISO 8601 | required for events | e.g. `PT15M` |
| `--rrule` | string | required | RFC 5545 |
| `--tz` | IANA name | repo default | `Europe/Berlin` |
| `--rdate` | comma-list of local-datetime | `[]` | |
| `--exdate` | comma-list of local-datetime | `[]` | |
| `--notify` | comma-list | `[]` | applies to every instance |
| `--spawns-task` / `--spawns-task-in-list` | as event | — | recurrence-spawned tasks |
| `--tag` / `--color` / `--emoji` / `--busy/--free` / `--priority-override` | as event | | |
| `--body` / `--body-file` / `--body-editor` / `--from-stdin` | as event | | |
| `--id` / `--author` / `--no-commit` / `--dry-run` / `--json` | as event | | |

**Decision:** the `--rrule` value is validated through `lib-recur` at
parse time. Invalid RRULE → exit 1 with the parser's error message on
stderr. Rationale: surfacing structured validation early saves the AI
agent from writing a file the GUI will later flag as broken.

**Stdout (human):**

```text
$ skb recurrence add \
    --title "Standup" \
    --calendar Work \
    --dtstart 2026-01-05T09:30:00 \
    --duration PT15M \
    --rrule "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" \
    --tz Europe/Berlin \
    --notify 5m
✓ added recurrence "Standup"
  id:        0190d4ab-2b7a-7c50-9c1e-cccccccccccc
  rule:      FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR
  next 3:    Mon 2026-01-05 09:30
             Tue 2026-01-06 09:30
             Wed 2026-01-07 09:30
  file:      calendars/.../recurrences/0190d4ab-….md
  commit:    f8e1d2a  "add recurrence \"Standup\" in Work"
```

**Commit message:** `add recurrence "<title>" in <calendar-or-list>`.

#### `skb recurrence edit`

**Synopsis:** `skb recurrence edit <rule-id> [flags]`

**Decision:** editing `--rrule` or `--dtstart` to values that would
invalidate existing `exceptions/<rule-id>/*` files does NOT auto-delete
those files. The writer warns to stderr and lists the now-orphaned
exception paths. Per DM-E.5. Rationale: deleting user data on schema
changes is too violent; the user can `skb verify` to see the orphans
and decide.

**Special flags:**
- `--split-from <local-date>`: instead of editing the rule in place,
  this creates a NEW rule starting at `<local-date>` carrying the
  edited fields, and adds an `UNTIL=<local-date-1>` to the old rule.
  Per [`decisions.md`](decisions.md) D.6 — "changing a rule's dtstart
  after the fact is treated as a new rule".

**Commit message:** `update recurrence "<title>" in <calendar>` or
`split recurrence "<title>" at <date>`.

#### `skb recurrence show`

Same shape as `event show`. Additionally prints the next 5 occurrences
(toggled by `--next N`).

#### `skb recurrence cancel-instance`

**Synopsis:** `skb recurrence cancel-instance <rule-id> <yyyy-mm-dd> [--reason <s>]`

**Description:** Writes
`calendars/<cal>/exceptions/<rule-id>/<yyyy-mm-dd>.md` with `mode =
"cancel"`. `--reason` populates the body.

**Stdout:**

```text
$ skb recurrence cancel-instance 0190d4ab 2026-05-11 --reason "public holiday — Whit Monday"
✓ cancelled instance of "Standup" on 2026-05-11
  file:    calendars/.../exceptions/0190d4ab-…/2026-05-11.md
  commit:  3a2b1c4  "cancel recurring event \"Standup\" on 2026-05-11"
```

**Idempotency:** if the exception file already exists with `mode =
"cancel"`, exit 0, no commit. If it exists with `mode = "override"`,
exit 3 unless `--force` (which switches mode to cancel; commit message
`cancel-override recurring event "<title>" on <date>`).

#### `skb recurrence override-instance`

**Synopsis:** `skb recurrence override-instance <rule-id> <yyyy-mm-dd> [--start <iso>] [--end <iso>] [--title <s>] [--location <s>] [--notify <list>]`

**Description:** Writes the same exception folder but with `mode =
"override"` and the override fields. The `instance_date` is the
*original* local date in the rule's `tz_id`, NOT the new start's date —
per DM-B.3 decision.

**Commit message:** `override recurring event "<title>" on <date>`.

---

### CLI-A.4 — `skb cal …` (calendars)

#### `skb cal add`

**Synopsis:** `skb cal add --name <s> [--emoji <e>] [--color #rgb] [--priority N] [--kind regular|timebox] [--tz <iana>]`

**Stdout:**

```text
$ skb cal add --name "Personal" --emoji 🏠 --color "#a06ee1" --priority 500
✓ added calendar "Personal"
  id:       0190e000-1111-7000-8a0a-000000000001
  folder:   calendars/0190e000-1111-7000-8a0a-000000000001/
  commit:   2e4d6a8  "add calendar \"Personal\""
```

**Commit message:** `add calendar "<name>"`.

#### `skb cal list`

Lists every calendar in the active repo with `id`, `name`, `emoji`,
`color`, `priority`, `active_toggle`, `kind_mode`, event count, last
write timestamp.

```text
$ skb cal list
4 calendars
  💼  Work        priority 600  events 142  active   id 0190a0aa-…-0010
  🏠  Personal    priority 500  events  87  active   id 0190a0aa-…-0001
  🏋   Gym         priority 300  events  21  inactive id 0190a0aa-…-0030
  🎂  Birthdays   priority 999  events  12  active   id 0190a0aa-…-0099
```

#### `skb cal edit`

Edits any field in `calendar.toml`. Special flags:
- `--add-active-window <start>[..<end>]`
- `--remove-active-window <start>[..<end>]`
- `--add-active-hours <day>=<from>-<to>` (e.g. `mon=09:00-17:00`)
- `--clear-active-windows` / `--clear-active-hours`
- `--rename <new-name>`: changes the `name` field only. Per DM-I.3 the
  folder is never renamed.

#### `skb cal show`

Same shape as `event show` but for the calendar metadata file. Includes
a `stats:` block: event count, recurrence count, exception count.

---

### CLI-A.5 — `skb list …` (todolists)

**Decision:** the subcommand is `list` (not `todolist` or `tl`) for
typing economy. The shadowing of `skb event list` is resolved by
position: the literal word `list` after `skb` selects the todolist
subcommand only when followed by `add|list|edit|show`; otherwise (e.g.
`skb event list ...`) it's a subcommand of `event`. Rationale: this
mirrors how `git remote` and `git branch` reuse verb names contextually
without confusion.

Same shape as `skb cal`:

- `skb list add --name <s> [--emoji <e>] [--color <c>] [--priority N] [--default-auto-done-eod] [--shopping-eligible]`
- `skb list list`
- `skb list edit <id> [flags]`
- `skb list show <id>`

Commit messages: `add todolist "<name>"`, etc.

---

### CLI-A.6 — `skb identity …`

#### `skb identity list`

```text
$ skb identity list
2 identities (active: Alex)
  🦊  Alex      01900000-…-aaaaaaaaaaaa   default-author   alex@example.com
                ssh-ed25519: pixel-7-strictlykeptboy
                gpg: F8C1 2E4A 9D3B 7C50  (signing enabled)
  🐺  Work-Self 0190f000-…-bbbbbbbbbbbb                    alex@work.example
```

#### `skb identity set-active <id-or-name>`

Updates `~/.skb/config.toml` `active_identity_per_repo[<repo-path>] =
<id>`. Does NOT commit (it's a client-side preference, not repo data).

#### `skb identity create`

**Synopsis:** `skb identity create --display-name <s> [--email <e>] [--avatar <emoji-or-path>] [--pronouns <s>] [--default-author]`

Writes `identities/<id>.md`. Commit message:
`add identity "<display-name>"`.

**Decision:** if `--default-author` is set, the writer flips every
other identity's `default_author = false` in the same commit. Per DM-E.3.

#### `skb identity edit`

Same field flags as create, with `--add-public-key <kind>=<key>` /
`--remove-public-key <fingerprint>` for SSH key management.

#### `skb identity gpg-import`

**Synopsis:** `skb identity gpg-import <id> [--key-file <path> | --armored-stdin]`

**Description:** Import a GPG private key into
`EncryptedSharedPreferences` (on Android) or `~/.skb/keys/` (on
desktop, mode 0600). Stores key fingerprint in the identity's
frontmatter under `[[gpg_keys]]`. Enables signing for that identity.

**Decision:** desktop-side key storage is plain mode-0600 file under
`~/.skb/keys/<fingerprint>.asc.aes` encrypted with a passphrase
derived from `~/.skb/master.key` (generated on first run, mode 0600).
Rationale: matching Android's EncryptedSharedPreferences exactly is
overkill on desktop; mode-0600 + AES-GCM with a separately-stored
master key is the standard Unix pattern (mirrors `gh`, `git-credential-
store --file`, etc.). Per D.23.

**Commit message:** `add gpg key <fpr-short> to identity "<name>"`.

#### `skb identity gpg-list`

Lists imported GPG keys per identity.

---

### CLI-A.7 — `skb attach …`

#### `skb attach add`

**Synopsis:** `skb attach add <file> --to <event-or-task-id> [--caption <s>]`

**Description:** Hashes the file (SHA-256), writes to
`attachments/<2-hex-prefix>/<sha>.<ext>`, and adds an `[[attachments]]`
inline table to the target event/task's frontmatter.

**Decision:** if the file size exceeds the per-repo LFS threshold
(default 1MB; D.26), the writer also stages a `.gitattributes` update
on first encounter. Per Phase Z. On providers without LFS support, the
writer warns to stderr but proceeds with in-tree storage.

**Stdout:**

```text
$ skb attach add ~/Downloads/scan.pdf --to 0190d4a0
✓ attached scan.pdf to event "Dentist — 6-month checkup"
  sha256:    9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
  size:      180 KB
  path:      attachments/9f/9f86d081….pdf
  commit:    8d3e2c1  "attach scan.pdf to event \"Dentist…\""
```

#### `skb attach list`

Lists attachments globally OR for a specific entity (`--to <id>`).
Includes `referenced_by` count for global listings.

#### `skb attach show <sha-or-prefix>`

Prints the file's path on disk, mime, size, all referencing entities.
`--cat` streams the bytes to stdout (binary-safe; warn if stdout is a
TTY).

#### `skb attach rm`

**Synopsis:** `skb attach rm <sha-or-prefix> --from <entity-id>`

**Description:** Removes the `[[attachments]]` table from the
referencing entity. Does NOT delete the on-disk file (D.B.10
decision). Use `skb verify --gc-attachments` (CLI-A.13) to sweep.

---

### CLI-A.8 — `skb show <date>` / `skb week` / `skb month`

#### `skb show <date>`

**Synopsis:** `skb show [<date>] [--calendar <c>] [--list <l>] [--tz <iana>]`

**Description:** Day summary for `<date>` (default: today). Renders all
active events + due tasks across active calendars/todolists in the repo
(or filtered by flag), per the resolver (`resolver.md`).

**Stdout (human):**

```text
$ skb show 2026-05-12
Tue 2026-05-12 (Europe/Berlin)

🌤  9°C → 18°C   (weather: 4 of 8h sun, 0mm precip)

08:00 ─┐
       │
09:00  │  🧍 Standup                   Work       0190d4ab… (recurrence)
09:30 ─┘
       │
10:00  ├──────────────────────────────────────────────────
       │  🧍 Standup (extended for retro)  Work    override
10:30 ─┤
       │
14:00 ─┐
       │  🦷 Dentist — 6-month checkup  Personal  0190d4a0…
14:45 ─┘

Tasks due today
  [ ]  18:30  Buy milk + bread + eggs   Daily chores   0190d500…
  [ ]  --     Resharpen kitchen knives  Daily chores   0190d600… (standing)
```

**Stdout (`--json`):** structured day-render with `events[]`,
`tasks[]`, `weather` (when D.28 weather is enabled).

#### `skb week <date>`

7-day window starting at the Monday of `<date>`'s ISO week. Same flags.

#### `skb month <date>`

Whole month containing `<date>`. Calendar grid in human stdout; full
list in JSON.

---

### CLI-A.9 — `skb find-free`

**Synopsis:** `skb find-free --duration <iso8601> [--repos <r1,r2>] [--calendars <c1,c2>] [--range <iso-period>] [--from <date>] [--to <date>] [--days <mon,tue,…>] [--hours <from>-<to>] [--participants <id1@repo,id2@repo>] [--limit N]`

**Description:** Multi-repo common-time finder per D.10 + D.27. Computes
the busy set across selected repos+calendars, inverts to free slots ≥
`--duration`, ranks per the resolver, prints top `--limit` (default 5).

**Flags:**

| Flag | Type | Default | Notes |
|---|---|---|---|
| `--duration` | ISO 8601 | required | minimum slot length |
| `--repos` | comma-list | active repo only | repo names from `~/.skb/config.toml` |
| `--calendars` | comma-list | all active | per-repo dotted form `<repo>:<cal-name>` to disambiguate |
| `--range` | ISO 8601 period | `P2W` | from now |
| `--from` / `--to` | date | derived from `--range` | overrides `--range` |
| `--days` | comma-list | mon..fri | day-of-week filter |
| `--hours` | `HH:MM-HH:MM` | `09:00-17:00` | per-day time-of-day window |
| `--participants` | comma-list | — | each `<identity-id>@<repo>` declares its own tz/working-hours via repo defaults |
| `--limit` | int | 5 | top-N slots |
| `--ideal-time` | `HH:MM` | — | rank closer-to-ideal slots higher |

**Stdout (human):**

```text
$ skb find-free --duration PT1H --repos alex,sam --range P2W --hours 10:00-18:00
Top 5 common slots over 2 weeks (alex, sam, 10:00–18:00):

1.  Wed 2026-05-13  14:00–15:30   90m   (90 min slot — longest in range)
2.  Thu 2026-05-14  10:30–12:00   90m
3.  Mon 2026-05-18  15:00–16:00   60m
4.  Wed 2026-05-20  11:30–12:30   60m
5.  Thu 2026-05-21  14:00–15:00   60m

Create event in any of these slots:
  skb event add --title <s> --start <slot.start> --duration <PT1H> --calendar <c>
```

**Stdout (`--json`):** ranked `slots[]` with `{start, end, duration_iso,
score, score_breakdown: {contiguity, ideal_time_distance, earliness}}`.

**Exit codes:** 0, 1, 2 (unknown repo).

---

### CLI-A.10 — `skb sync`

**Synopsis:** `skb sync [--all | <repo>] [--push | --pull-only] [--force] [--rebase|--merge]`

**Description:** Fetch + rebase + push for the named repo (or all
auto-sync-enabled repos with `--all`). Mirrors GUI sync (D.8). Default
strategy: rebase. `--force` allows non-fast-forward push (with confirm
prompt unless `--yes`).

**Stdout (human):**

```text
$ skb sync --all
syncing alex …
  fetch: 3 new commits, 0 conflicts
  rebase: ok
  push: 2 local commits → origin/main
syncing sam …
  fetch: up to date
  push: nothing to push

✓ 2/2 repos synced, 0 conflicts
```

**Conflict handling:** if rebase fails with conflicts, exit 3 and emit
JSON-or-human conflict list:

```text
$ skb sync --all
syncing alex …
  fetch: 1 new commit, 1 conflict
  rebase: CONFLICT in calendars/.../2026/05/0190d4a0-….md

Resolve in app, or:
  skb event show 0190d4a0 --raw   # show local
  skb event show 0190d4a0 --raw --theirs  # show remote
  skb event resolve 0190d4a0 --keep-mine|--keep-theirs|--editor

exit 3
```

**Decision:** `skb event resolve` is a sub-flow of `event` not its own
top-level. It's a conflict-resolution alias added under each
write-capable subcommand. Listed in help under "conflict resolution".
Rationale: keeping conflict commands attached to the entity they
resolve matches the GUI's per-file conflict view (J.5).

---

### CLI-A.11 — `skb apply-template`

**Synopsis:** `skb apply-template <template-name> [--repo <r>] [--target-calendar <c>] [--target-list <l>] [--params <k=v,k=v>]`

**Description:** Applies a template from the configured template repo
(default: built-in registry shipped with the JAR). Idempotent: re-applying
merges by ID, leaves existing edits alone (D.13).

**Stdout (human):**

```text
$ skb apply-template gym-3x --target-calendar Personal
applying template "gym-3x" …
  + recurrence: "Gym — push day"   (Mon 18:00)
  + recurrence: "Gym — pull day"   (Wed 18:00)
  + recurrence: "Gym — legs day"   (Fri 18:00)
  ~ recurrence: "Mobility warmup" (already exists, unchanged)

✓ applied template "gym-3x" — 3 added, 0 updated, 1 unchanged
  commit:  6e2a4d3  "apply template gym-3x to Personal"
```

**Flags:**
- `--list-templates`: print every available template name + description.
- `--params`: pass key=value pairs into the template (e.g.
  `--params gym_days=2,start_hour=18:00`).

**Commit message:** `apply template <name> to <calendar-or-list>`.

---

### CLI-A.12 — `skb migrate`

**Synopsis:** `skb migrate [--to <version>] [--check]`

**Description:** Runs the schema migration runner (DM-F). With
`--check`, prints the current vs latest schema version and exits 0 if
they match, exit 6 otherwise.

**Stdout (human):**

```text
$ skb migrate --check
repo schema_version:  1
latest schema_version: 1
✓ up to date
```

```text
$ skb migrate
migrating schema v1 → v2 …
  - 87 events: added optional tz_id field (no-op for explicit-offset entries)
  - 21 recurrences: re-validated
  - 12 tasks: re-validated
✓ migrated v1 → v2
  commit:  1a2b3c4  "migrate schema v1 → v2"

migration notes appended to MIGRATION-NOTES.md
```

**Exit codes:** 0, 6 (cannot read schema), 5 (corrupt).

---

### CLI-A.13 — `skb verify`

**Synopsis:** `skb verify [--gc-attachments] [--fix-default-author] [--strict]`

**Description:** Integrity check across the full repo:

1. Parse every entity file → collect malformed entries.
2. Cross-reference check: `calendar_id`, `todolist_id`,
   `spawned_by_event` / `spawns_task`, `rule_id` on exceptions.
3. Attachment GC: list on-disk attachments unreferenced by any entity.
4. Identity invariant: exactly one `default_author = true`.
5. Schema-version match.

**Flags:**
- `--gc-attachments`: actually `git rm` unreferenced attachments
  (commit: `gc N unreferenced attachments`).
- `--fix-default-author`: writer-side fix per DM-E.3 (commit: `fix
  multiple default_author identities`).
- `--strict`: include CommonMark body warnings.

**Stdout (human):**

```text
$ skb verify
scanning 247 entities …

✓ frontmatter parse:    247 / 247 ok
✓ cross-refs:           clean
✗ default-author:       2 identities flagged as default_author
                          - 01900000-…-aaaaaaaaaaaa  (Alex)         updated 2026-05-09
                          - 0190f000-…-bbbbbbbbbbbb  (Work-Self)    updated 2026-04-12
                          → run `skb verify --fix-default-author` to keep Alex (most recent).
⚠  attachments:          3 unreferenced files (320 KB total)
                          - attachments/9f/9f86…  scan.pdf  180 KB
                          - attachments/3d/3def…  meme.png   42 KB
                          - attachments/77/7700…  notes.md   98 KB
                          → run `skb verify --gc-attachments` to remove.
✓ schema:               v1 (latest)

2 warnings, 0 errors
exit 0
```

**Decision:** `verify` returns exit 0 on warnings, exit 5 only on
parse errors / corrupt files. Rationale: warnings shouldn't break CI
pipelines that run `skb verify` as a health check; errors should.

**Stdout (`--json`):** structured `result.checks[]` with `name`,
`status` (`ok|warning|error`), `details`.

---

### CLI-A.14 — `skb comment …` (D.29)

Comments live alongside events at
`events/<yyyy>/<mm>/<event-id>.comments/<comment-id>.md`.

#### `skb comment add`

**Synopsis:** `skb comment add --event <id> [--in-reply-to <comment-id>] [--body <s>|--body-file <p>|--body-editor|--from-stdin]`

```text
$ skb comment add --event 0190d4a0 --body "Confirmed appointment — see you Tuesday."
✓ added comment to event "Dentist — 6-month checkup"
  id:      0190f100-aaaa-7c50-9c1e-aaaaaaaaaaaa
  file:    calendars/.../2026/05/0190d4a0-….comments/0190f100-….md
  commit:  e2c4a8b  "comment on event \"Dentist…\""
```

**Commit message:** `comment on event "<title>"` (matches D.29 +
NS-M).

#### `skb comment list --event <id> [--in-reply-to <comment-id>] [--limit N]`

Lists comments, threaded by `in_reply_to` (indented in human stdout;
nested structure in JSON).

#### `skb comment show <comment-id>`

Same shape as `event show`.

#### `skb comment edit <comment-id> [--body <s>|...]`

Edits the body; bumps `updated_at`. Commit:
`edit comment on event "<title>"`.

#### `skb comment delete <comment-id>`

`git rm`s the file. Commit: `remove comment from event "<title>"`.

---

### CLI-A.15 — `skb weather show`

**Synopsis:** `skb weather show <date> [--location LAT,LON] [--days N] [--units metric|imperial]`

**Description:** Fetches forecast from Open-Meteo (D.28), prints
forecast. Cached per repo in `~/.skb/cache/weather/<lat>,<lon>/<date>.json`
(6h TTL).

**Stdout:**

```text
$ skb weather show 2026-05-12
Tue 2026-05-12  (48.78°N, 9.18°E — Stuttgart)

  09:00   9°C   ☀️    0.0mm
  12:00  14°C   ⛅    0.0mm
  15:00  18°C   ☀️    0.0mm
  18:00  16°C   🌤️    0.0mm
  21:00  11°C   🌙    0.0mm

high: 18°C  low: 7°C   precip: 0.0mm   wind: 8 km/h SW

Data: open-meteo.com (CC BY 4.0)
```

**Flags:** `--days N` for multi-day; default 1.

**Exit codes:** 0, 1 (bad coords), 7 (network).

---

### CLI-A.16 — `skb tz …`

#### `skb tz convert <event-id> <new-tz>`

**Synopsis:** `skb tz convert <event-id> <iana-tz> [--also-set-tz-id]`

**Description:** Rewrites the event's `start`/`end` to the new IANA tz
offset (instant preserved). With `--also-set-tz-id`, also writes
`tz_id = <new-tz>` (per D.27 + DM-L).

**Stdout:**

```text
$ skb tz convert 0190d4a0 America/Los_Angeles
✓ converted event "Dentist — 6-month checkup"
  before:   2026-05-12T14:00:00+02:00 → 14:45+02:00
  after:    2026-05-12T05:00:00-07:00 → 05:45-07:00  (America/Los_Angeles)
  commit:   b9e2c1d  "tz-convert event \"Dentist…\" to America/Los_Angeles"
```

#### `skb tz list-zones [--filter <substring>]`

Prints the IANA tz database list. Useful for AI agents that need to
verify a zone name before passing it.

---

### CLI-A.17 — `skb caldav …` (D.25)

#### `skb caldav add`

**Synopsis:** `skb caldav add --url <s> --username <s> [--password-stdin] --calendar <local-cal> --mode <pull|push|bidi>`

Adds a CalDAV mirror. Credential stored in
`EncryptedSharedPreferences` (Android) or `~/.skb/keys/caldav-<hash>`
(desktop).

```text
$ echo 'app-password-xxx' | skb caldav add \
    --url https://nextcloud.example/remote.php/dav/calendars/alex/work \
    --username alex \
    --password-stdin \
    --calendar Work \
    --mode bidi
✓ added CalDAV mirror "Work" (bidi)
  url:      https://nextcloud.example/remote.php/dav/calendars/alex/work
  next sync: in 30m (or run `skb caldav sync Work`)
```

#### `skb caldav sync [<calendar>|--all]`

Triggers an immediate caldav sync for one or all mirrors. Exit 3 on
conflicts (resolved via `skb event resolve` per CLI-A.10).

#### `skb caldav remove <calendar>`

Removes the mirror config. Does NOT delete the local mirror calendar's
contents (use `skb cal delete` for that).

#### `skb caldav list`

Lists all configured CalDAV mirrors per repo.

---

### CLI-A.18 — `skb branch …` (D.36)

#### `skb branch list`

```text
$ skb branch list
* main
  claude/plan-2026-q3
  experiment-priority-tweak
```

#### `skb branch create <name> [--checkout]`

Creates a new branch. With `--checkout`, also switches.

**Decision:** `branch create` is non-destructive — never overwrites an
existing branch. Use `--force` to reset an existing branch ref.
Rationale: matches `git branch` default behavior; surprising
overwrites are AI-agent-unfriendly.

#### `skb branch switch <name> [--stash-local]`

Switches branch. If uncommitted changes exist, exit 3 unless
`--stash-local` (which `git stash` first; reapplies on switch-back via
a follow-up command).

**Decision:** `--stash-local` is intentionally explicit. Rationale:
silent stashing makes AI-agent debugging harder; surface the action.

#### `skb branch delete <name> [--force]`

Refuses to delete the current branch or `main`/`master`. `--force`
required if the branch has unmerged commits.

---

### CLI-A.19 — `skb repo …`

#### `skb repo init <path> [--name <s>] [--identity <id-or-name>] [--default-calendar <s>] [--default-list <s>] [--default-tz <iana>] [--no-scaffold]`

**Description:** Creates a new strictlykeptboy repo at `<path>`:
`git init`, scaffolds `.strictlykeptboy/{schema,repo}.toml`,
`identities/`, `AGENTS.md`, `CLAUDE.md`, `README.md`. Unless
`--no-scaffold`, also seeds a default calendar + todolist matching the
provided names.

**Stdout:**

```text
$ skb repo init ~/my-cal --name "Alex personal" --identity Alex --default-calendar Personal --default-list Chores --default-tz Europe/Berlin
✓ initialized repo at /home/alex/my-cal
  repo id:         0190a000-0000-7000-8000-000000000001
  identity:        Alex (01900000-…-aaaaaaaaaaaa)
  default cal:     Personal
  default list:    Chores
  default tz:      Europe/Berlin
  commit:          initial scaffold (a1b2c3d)
```

**Commit message (initial):** `initial scaffold for "<repo-name>"`.

#### `skb repo add <url-or-path> [--name <s>] [--auto-sync] [--default-identity <id>]`

Adds an existing repo (clone if URL, register if local path) to
`~/.skb/config.toml`. For URLs, supports `https://`, `ssh://`, and
shorthand `gh:user/repo` / `forgejo:host/user/repo`.

#### `skb repo list`

```text
$ skb repo list
2 repos (active: alex)

* alex     /home/alex/my-cal              auto-sync 15m   2 ahead
  sam      /home/alex/shared/sam-cal      pull-only       up-to-date  (read-only)
```

#### `skb repo switch-active <name>`

Sets the active repo for subsequent commands without `--repo`.
Persisted to `~/.skb/config.toml`.

#### `skb repo remove <name> [--keep-clone]`

Removes a repo from config. By default keeps the local clone;
`--delete-clone` removes the directory.

---

### CLI-A.20 — `skb help`

#### `skb help [<command>]`

Prints help. Default: top-level command listing. With `<command>`:
detailed help for that subcommand including all flags, exit codes,
worked example.

#### `skb help --json`

**Description:** Emits a structured catalog of EVERY subcommand, its
flags (with types and defaults), exit codes, and JSON output shape.
**This is the AI-agent discovery surface.** An agent calls this once
and learns the entire CLI without parsing human text.

**Stdout (`--json`):**

```text
$ skb help --json
{
  "version": 1,
  "command": "help",
  "result": {
    "skb_version": "0.1.0-a3f2e1b",
    "schema_version": 1,
    "commands": [
      {
        "name": "event.add",
        "synopsis": "skb event add --title <s> --start <iso> ...",
        "description": "Create a new one-off event.",
        "flags": [
          {"name": "--title", "type": "string", "required": true, "description": "event title, <= 200 chars"},
          {"name": "--start", "type": "offset-datetime", "required": true, "description": "RFC 3339"},
          {"name": "--end", "type": "offset-datetime", "required": false, "description": "mutually exclusive with --duration"},
          {"name": "--duration", "type": "iso8601-duration", "required": false, "description": "mutually exclusive with --end"},
          {"name": "--calendar", "type": "string", "required": false, "default": "<repo-default>"},
          ...
        ],
        "exit_codes": [0, 1, 2, 3, 5, 6],
        "json_output_schema": {
          "type": "object",
          "properties": {
            "id": {"type": "string", "format": "uuidv7"},
            "kind": {"const": "event"},
            "title": {"type": "string"},
            "start": {"type": "string", "format": "date-time"},
            "end": {"type": "string", "format": "date-time"},
            "calendar_id": {"type": "string", "format": "uuidv7"},
            "calendar_name": {"type": "string"},
            "path": {"type": "string"},
            "commit": {"type": "string"},
            "created": {"type": "boolean"}
          },
          "required": ["id", "kind", "title", "start", "end", "calendar_id", "path"]
        },
        "idempotency": {
          "key": "--id",
          "rule": "if --id matches an existing event file, the command updates rather than creates"
        }
      },
      ...
    ]
  }
}
```

**Exit codes:** 0.

---

### CLI-A.21 — `skb tx …` (transactions)

Per CLI-F. Multi-command batching for AI agents that want N edits in
one commit.

#### `skb tx start [--message <s>]`

Creates `.strictlykeptboy/tx.lock` (a TOML file: `pid`, `started_at`,
`message`, `commands[]`). Subsequent write commands in the same repo
detect the lockfile and append to `commands[]` instead of committing.

**Stdout:**

```text
$ skb tx start --message "reschedule Q3 standups for vacation week"
✓ transaction started
  message:  reschedule Q3 standups for vacation week
  lock:     /home/alex/my-cal/.strictlykeptboy/tx.lock
  pid:      12345

run further `skb` write commands; finish with `skb tx commit` or `skb tx abort`.
```

#### `skb tx commit`

Writes a single commit containing every staged change. Commit message
defaults to the tx's `--message`; override with `--message` here.

#### `skb tx abort`

`git reset` to pre-tx state; deletes the lockfile. Stashed-and-popped
under the hood.

#### `skb tx status`

Prints staged commands + diff summary.

#### `skb tx history [--limit N] [--this-cli-only]`

Per the brief's "Transcript mode": prints the last N commits authored
by `skb`, with a structured view (`event_id`, action, frontmatter diff
summary). Default scope: this CLI's commits (matched by author email
suffix `@strictlykeptboy.local` OR commit message prefix). Useful for
AI agents to recover context after a crash.

```text
$ skb tx history --limit 5
5 recent skb commits:

7c4b9e2  2026-05-09 18:30  add event "Dentist" in Personal
  + calendars/.../events/2026/05/0190d4a0-….md  (created)

3a2b1c4  2026-05-09 18:25  cancel recurring event "Standup" on 2026-05-11
  + calendars/.../exceptions/0190d4ab-…/2026-05-11.md  (created)

9c1a2b3  2026-05-09 18:20  add task "Buy milk" in Daily chores
  + todolists/.../tasks/2026/05/0190d500-….md  (created)

…
```

**Decision:** the lockfile is staged-but-uncommitted (lives in the
working tree, gitignored via auto-generated
`.strictlykeptboy/.gitignore`). Rationale: committing the lock would
itself be a write that violates the "one commit at end" promise.

---

### CLI-A.22 — `skb self-update` and `skb --version`

#### `skb --version`

```text
$ skb --version
skb 0.1.0-a3f2e1b
  jvm:     21.0.2 (Temurin)
  schema:  1
  jar:     /home/alex/.skb/skb-cli.jar
```

JSON form via `--version --json`.

#### `skb self-update [--check-only] [--prerelease]`

Polls GitHub Releases for the latest `skb-*` asset, downloads, verifies
SHA-256 against the release notes table, replaces in-place. With
`--check-only`, just prints whether an update is available.

**Decision:** `self-update` updates the JAR + the wrapper script (the
wrapper is small; rewriting it on update is safe and lets us ship
wrapper bugfixes). Rationale: a stuck-old wrapper can't be fixed
without manual reinstall otherwise.

---

## Phase CLI-B — atomic writes + auto-commit + dry-run

- [ ] **CLI-B.1** Every write goes through `RepoWriter` (DM-E). The CLI
  layer is a thin caller of that same class. No CLI-specific write
  paths; this guarantees parity with the GUI.
- [ ] **CLI-B.2** Temp file naming: `<dir>/.<id>.<ext>.tmp.<pid>.<nano>`.
  Atomic via `Files.move(source, target, ATOMIC_MOVE)`. Per DM-E.1.
- [ ] **CLI-B.3** Auto-commit message format (verbatim, matches GUI per
  D.8 + DM-E):
  - Events: `add|update|remove event "<title>" in|from <calendar-name>`
  - Recurrences: `add|update recurrence "<title>" in <calendar-name>`
  - Recurrence exceptions: `cancel recurring event "<title>" on <YYYY-MM-DD>`, `override recurring event "<title>" on <YYYY-MM-DD>`
  - Tasks: `add|update|remove task "<title>" in|from <todolist-name>`, `complete|reopen task "<title>" in <todolist-name>`
  - Calendars: `add|update|remove calendar "<name>"`
  - Todolists: `add|update|remove todolist "<name>"`
  - Identities: `add|update|remove identity "<display-name>"`, `add gpg key <fpr-short> to identity "<name>"`
  - Attachments: `attach <filename> to <kind> "<title>"`, `detach <filename> from <kind> "<title>"`
  - Comments: `comment|edit comment|remove comment on event "<title>"`
  - Templates: `apply template <name> to <calendar-or-list>`
  - Migration: `migrate schema v<old> → v<new>`
  - GC: `gc <N> unreferenced attachments`
- [ ] **CLI-B.4** `--no-commit` stages files (via `git add`) but does
  not commit. Equivalent to running inside `skb tx start ... tx commit`
  manually.
- [ ] **CLI-B.5** `--dry-run` short-circuits before any filesystem
  write. Prints the proposed file contents (or diff for edits) and the
  would-be commit message. Exit code mirrors what the real run would
  produce.
- [ ] **CLI-B.6** Git author identity per commit: `user.name = <active
  identity display_name>`, `user.email = <identity email or
  <id>@strictlykeptboy.local>` (D.8). If GPG signing is enabled for the
  identity (D.23), commits are signed; otherwise unsigned.
- [ ] **CLI-B.7** Concurrent-write detection: before writing, the
  writer reads the existing file (if present), records its sha256,
  writes the new content, then re-reads and re-shas the original path
  to detect mid-flight modification (a foreign editor saved
  concurrently). On mismatch: exit 3 with `concurrent modification —
  rerun with `--force` to override`.
- [ ] **CLI-B.8** Post-commit hook: emit `skb-post-write` event on a
  Unix domain socket at `~/.skb/run/events.sock` IF a listener is
  attached (used by the future `skb daemon` mode; CLI-K). Silently
  no-op otherwise.

---

## Phase CLI-C — `--json` output schema + help system

- [ ] **CLI-C.1** Top-level JSON envelope (every command):
  ```json
  {
    "version": 1,
    "command": "<subcommand-dotted-path>",
    "result": { /* command-specific payload */ }
  }
  ```
  On error:
  ```json
  {
    "version": 1,
    "command": "<subcommand-dotted-path>",
    "error": {
      "code": "<exit-code-name>",
      "message": "<human-readable>",
      "details": { /* command-specific */ }
    }
  }
  ```
- [ ] **CLI-C.2** `error.code` enum: `usage`, `not_found`, `conflict`,
  `auth`, `corrupt`, `schema_mismatch`, `network`, `internal`. Maps
  1-to-1 with exit codes per CLI-D.
- [ ] **CLI-C.3** Schemas published as JSON Schema documents bundled
  in the JAR at `META-INF/skb/schemas/<command>.json`. The
  `skb help --json` output includes a `$ref` to each schema. Schemas
  ship in the GitHub Releases tarball as a separate `skb-schemas.tar.gz`
  for tooling consumers.
- [ ] **CLI-C.4** Human-help system: `skb help` lists top-level commands
  with one-line synopsis; `skb help <cmd>` prints full per-command
  help (synopsis, flags table, exit codes, worked example). The help
  text is GENERATED from the same metadata that drives `--json` help —
  one source of truth.
- [ ] **CLI-C.5** Help is also embedded in every command via
  `--help`/`-h` (per-command), matching POSIX convention.
- [ ] **CLI-C.6** **Decision:** in `--json` mode, stderr stays empty
  on success; all output goes to stdout. On error, stderr STILL gets
  the JSON error envelope (so streaming consumers that only watch
  stderr can detect failures). Rationale: dual stream avoids forcing
  consumers to parse stdout-then-stderr to distinguish success from
  failure.
- [ ] **CLI-C.7** `--quiet` suppresses human-stdout summaries but keeps
  `--json` output. Useful for CI pipelines that just want exit codes.

---

## Phase CLI-D — error model + exit codes

- [ ] **CLI-D.1** Exit-code taxonomy (verbatim from D.24):

| Code | Name | When |
|---|---|---|
| 0 | ok | command succeeded |
| 1 | usage | bad flags, bad arg parse, ambiguous shorthand |
| 2 | not-found | named calendar/list/event/etc. doesn't exist |
| 3 | conflict | git conflict, concurrent modify, already-exists with mismatched kind |
| 4 | auth | OAuth/PAT/SSH credential failure during sync/caldav |
| 5 | corrupt | malformed repo, parse error in critical file |
| 6 | schema-mismatch | repo version > supported, or migration needed and refused |
| 7 | network | offline, DNS, timeout (sync, weather, caldav, self-update) |
| 8 | internal | uncaught exception (last-resort bucket) |

- [ ] **CLI-D.2** Every exit path emits stderr context:
  ```text
  error: <one-line>
  hint:  <suggested command>
  ```
  E.g.:
  ```text
  error: calendar "Persnoal" not found
  hint:  did you mean "Personal"? (try: skb cal list)
  exit 2
  ```
- [ ] **CLI-D.3** Levenshtein-suggestion on `not-found` errors (≤ 3
  edit distance triggers a hint).
- [ ] **CLI-D.4** Internal errors (8): always include a stack trace
  in `--verbose` mode; in default mode, log to
  `~/.skb/logs/<date>.log` and print the log path on stderr.
- [ ] **CLI-D.5** Error type taxonomy in the JSON `error.details` for
  every code, so AI agents can branch programmatically:
  - `usage.details = { flag?: string, problem: "missing"|"invalid"|"conflicting" }`
  - `not_found.details = { kind: "calendar"|"todolist"|"event"|..., query: string, suggestions: [string] }`
  - `conflict.details = { kind: "git"|"concurrent_modify"|"already_exists", path?: string, our_sha?: string, their_sha?: string }`
  - `auth.details = { transport: "ssh"|"https"|"caldav", host: string }`
  - `schema_mismatch.details = { repo_version: int, supported_max: int, supported_min: int }`
- [ ] **CLI-D.6** Network errors surface the underlying URL/host
  whenever safe (token-bearing URLs are redacted).

---

## Phase CLI-E — repo discovery + config + identity binding

- [ ] **CLI-E.1** **Repo discovery precedence** (highest → lowest):
  1. `--repo <path>` flag (absolute or relative).
  2. `SKB_REPO` environment variable.
  3. Walk-up from `$PWD` until a `.strictlykeptboy/` directory is
     found.
  4. Active repo from `~/.skb/config.toml`'s `active_repo`.
  5. Fail with exit 2 `not_found` + hint `run \`skb repo list\` to see
     configured repos, or \`skb repo init <path>\` to create one`.
- [ ] **CLI-E.2** Walk-up halts at filesystem root or at `$HOME` (to
  avoid surprising matches above the user's home). Symlinks followed.
- [ ] **CLI-E.3** Global config at `~/.skb/config.toml`:

  ```toml
  schema_version = 1
  active_repo = "alex"
  default_editor = "$EDITOR"   # falls back to vi
  json_default = false         # if true, --json is implicit

  [[repo]]
  name = "alex"
  path = "/home/alex/my-cal"
  url = "git@github.com:alex/calendar.git"
  auto_sync = true
  auto_sync_interval = "15m"

  [[repo]]
  name = "sam"
  path = "/home/alex/shared/sam-cal"
  url = "https://forgejo.example/sam/cal"
  auto_sync = false

  [active_identity_per_repo]
  "alex" = "01900000-0000-7000-8000-aaaaaaaaaaaa"
  "sam"  = "0190f000-0000-7000-8000-bbbbbbbbbbbb"

  [signing]
  # if a key is configured here, commits are signed for that identity
  "01900000-…-aaaaaaaaaaaa" = "F8C12E4A9D3B7C50"

  [logging]
  level = "info"
  dir = "~/.skb/logs"
  ```

- [ ] **CLI-E.4** Per-repo override at `<repo>/.strictlykeptboy/cli.toml`
  (optional). Same shape, repo-scoped fields only. **Decision:** the
  per-repo file overrides global for that repo's commands. Rationale:
  lets a contributor working on a shared repo override defaults
  without polluting their home config.
- [ ] **CLI-E.5** Identity binding: every write command resolves the
  active identity in this order:
  1. `--author <id>` flag.
  2. `active_identity_per_repo[<repo-name>]` in global config.
  3. The repo's `default_identity` from `repo.toml`.
  4. Exit 2 if none resolves.
- [ ] **CLI-E.6** Env vars consumed by `skb`:
  - `SKB_REPO` — repo path (CLI-E.1).
  - `SKB_CONFIG` — alt path to global config (default `~/.skb/config.toml`).
  - `SKB_JSON` — when set to a truthy value, equivalent to `--json` on every command.
  - `EDITOR` / `VISUAL` — body editor (CLI-J.3).
  - `SKB_NO_COLOR` — equivalent to `--no-color`.
  - `SKB_LOG_LEVEL` — `error|warn|info|debug|trace`.
- [ ] **CLI-E.7** First-run bootstrap: if `~/.skb/config.toml` doesn't
  exist, `skb` creates it with safe defaults on first invocation. No
  interactive prompt — non-interactive friendliness for AI agents.

---

## Phase CLI-F — transactions (multi-command commits)

- [ ] **CLI-F.1** Lockfile path: `<repo>/.strictlykeptboy/tx.lock`.
  Format: TOML, schema-versioned, gitignored.

  ```toml
  schema_version = 1
  pid = 12345
  started_at = 2026-05-09T18:30:00+02:00
  started_by_identity = "01900000-…-aaaaaaaaaaaa"
  message = "reschedule Q3 standups for vacation week"

  [[command]]
  cmd = "event.edit"
  args = ["0190d4a0", "--start", "2026-05-13T10:00:00+02:00"]
  staged_at = 2026-05-09T18:30:05+02:00
  files = ["calendars/.../events/2026/05/0190d4a0-….md"]
  ```

- [ ] **CLI-F.2** Stale-lock detection: if `pid` is not alive AND
  `started_at` is older than 1 hour, the next `skb tx start` prints a
  warning, prompts for `--force-clear` (non-interactive: refuses,
  exit 3), and on confirmation runs `tx abort` semantics.
- [ ] **CLI-F.3** Inside a tx, every write command stages to the
  working tree but skips `git commit`. The lockfile records each
  command for `tx status` / `tx history` visibility.
- [ ] **CLI-F.4** `tx commit` reads the lockfile, runs `git add -A` on
  the recorded paths, commits with the tx's message, deletes the
  lockfile.
- [ ] **CLI-F.5** `tx abort` runs `git stash --include-untracked` then
  `git stash drop`, restoring pre-tx state. Deletes the lockfile.
- [ ] **CLI-F.6** Tx interacts cleanly with `--dry-run`: dry-run
  inside a tx prints what WOULD be staged but does not modify the
  lockfile. Useful for "preview my batch" workflows.
- [ ] **CLI-F.7** Tx + sync: `skb sync` inside a tx refuses (exit 1
  with hint `commit or abort the open transaction first`). Rationale:
  syncing mid-batch leaves a half-committed state on the remote.
- [ ] **CLI-F.8** Nested tx forbidden: starting a tx while one is
  active → exit 3.

---

## Phase CLI-G — AGENTS.md CLI-first content

This phase coordinates with DM-K of [`data-model.md`](data-model.md)'s
Round 2 extension. SA-9 rewrites the AGENTS.md template to lead with
`skb` commands, file format second. CLI-G specifies what those
CLI-side instructions look like.

- [ ] **CLI-G.1** Template addition to AGENTS.md (top section, before
  "Repo layout"):

  ```markdown
  ## Preferred write path: `skb` CLI

  If `skb` is on your PATH, **use it first** for every write.
  It produces atomic, single-commit changes that exactly match the
  file format the strictlykeptboy app expects.

  Add an event:
  ```sh
  skb event add --title "Dentist" --start 2026-05-12T14:00:00+02:00 \
      --duration PT45M --calendar Personal
  ```

  List events:
  ```sh
  skb event list --from 2026-05-10 --to 2026-05-14 --json
  ```

  Cancel one instance of a recurring event:
  ```sh
  skb recurrence cancel-instance <rule-id> 2026-05-11 \
      --reason "public holiday"
  ```

  Discover the full surface programmatically:
  ```sh
  skb help --json
  ```

  Idempotency: every write command accepts `--id <uuidv7>`; running
  the same command twice with the same `--id` updates instead of
  duplicating. Safe for retry loops.

  Batch multiple writes into a single commit:
  ```sh
  skb tx start --message "reschedule Q3 standups for vacation week"
  skb recurrence cancel-instance <rule-id> 2026-07-15 --no-commit
  skb recurrence cancel-instance <rule-id> 2026-07-16 --no-commit
  skb tx commit
  ```

  If `skb` is NOT available, fall back to writing files directly per
  the schema below. The file format is the contract; `skb` is the
  ergonomic path.
  ```

- [ ] **CLI-G.2** The "How to add a one-off event" / "How to edit" /
  etc. sections in AGENTS.md each gain a leading `skb` invocation
  block, with the file-by-hand path below as fallback.
- [ ] **CLI-G.3** A new "CLI exit codes" section in AGENTS.md
  enumerates the 0..8 taxonomy so AI agents writing retry logic know
  which codes are retryable (4, 7 → retryable; 1, 2, 3, 5, 6, 8 →
  not).
- [ ] **CLI-G.4** A new "Idempotency cheat-sheet" section:
  - `event add --id <X>` → upsert
  - `task done <id>` → no-op if already done
  - `recurrence cancel-instance <rule> <date>` → no-op if already cancelled
  - `apply-template <name>` → merges by ID, leaves edits alone
  - `attach add <file> --to <id>` → keys on sha256, no-op if reference exists
- [ ] **CLI-G.5** The scaffolder writes a `.skb-aware` marker file
  inside `.strictlykeptboy/` so AI tools running outside the GUI can
  cheaply detect "this is a strictlykeptboy repo and `skb` understands
  it". File content: single line `skb-schema-version=1`.

---

## Phase CLI-H — JVM module + shell wrapper + distribution

- [ ] **CLI-H.1** Gradle subproject `:cli`. Pure JVM, no Android
  dependencies. Shares `:core` with the Android app (where `:core`
  houses `RepoWriter`, `RepoScanner`, schema typed objects, RRULE
  expansion, common-time finder, etc.).
- [ ] **CLI-H.2** Arg parser: `com.github.ajalt.clikt:clikt` (Apache-2.0).
  Battle-tested, supports nested subcommands, generates help. Compose-
  friendly is irrelevant here; we want strict POSIX-ish parsing.
- [ ] **CLI-H.3** JSON: `kotlinx.serialization` (already in the stack).
- [ ] **CLI-H.4** TOML: `ktoml` (already in the stack) — same parser
  as the app, so round-trip parity is automatic.
- [ ] **CLI-H.5** Git: JGit 6.x (same as the app). `:cli` depends on
  `:core` which depends on JGit; no duplicate transitive.
- [ ] **CLI-H.6** Build: `./gradlew :cli:shadowJar` produces
  `cli/build/libs/skb-cli-<version>.jar` (fat JAR with all deps).
  Main class: `com.eight87.skb.cli.Main`.
- [ ] **CLI-H.7** Shell wrapper `skb` (POSIX `sh`, ~50 lines):

  ```sh
  #!/bin/sh
  # skb — strictlykeptboy CLI launcher
  set -e

  SKB_HOME="${SKB_HOME:-$HOME/.skb}"
  SKB_JAR="${SKB_JAR:-$SKB_HOME/skb-cli.jar}"
  if [ ! -f "$SKB_JAR" ] && [ -f "/usr/local/share/skb/skb-cli.jar" ]; then
      SKB_JAR="/usr/local/share/skb/skb-cli.jar"
  fi
  if [ ! -f "$SKB_JAR" ]; then
      echo "error: skb-cli.jar not found at $SKB_HOME/skb-cli.jar or /usr/local/share/skb/" >&2
      echo "hint:  reinstall with: curl -fsSL https://github.com/887/strictlykeptboy/releases/latest/download/install.sh | sh" >&2
      exit 8
  fi

  # locate java: $JAVA_HOME, bundled JRE, then PATH
  if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
      JAVA="$JAVA_HOME/bin/java"
  elif [ -x "$SKB_HOME/bundled-jre/bin/java" ]; then
      JAVA="$SKB_HOME/bundled-jre/bin/java"
  elif command -v java >/dev/null 2>&1; then
      JAVA="java"
  else
      echo "error: no java found; install Temurin 17+ or use --with-jre install variant" >&2
      exit 8
  fi

  # version check (must be >= 17)
  JV=$("$JAVA" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
  if [ "$JV" -lt 17 ]; then
      echo "error: java $JV found, but skb requires java 17+" >&2
      exit 8
  fi

  exec "$JAVA" -XX:+UseSerialGC -Xshare:auto -jar "$SKB_JAR" "$@"
  ```

- [ ] **CLI-H.8** Bundled JRE variant: `--with-jre` install option
  downloads a Temurin 21 JRE alongside the JAR. Sized ~50MB
  uncompressed; ships as `skb-<version>-<os>-<arch>-with-jre.tar.gz`.
  Default variant ships without JRE (`skb-<version>.tar.gz`, ~12MB)
  and relies on system Java.
- [ ] **CLI-H.9** Install paths:
  - One-liner: `curl -fsSL https://github.com/887/strictlykeptboy/releases/latest/download/install.sh | sh`.
    Script checks for Java; installs to `~/.skb/`; adds shell-rc
    sourcing of `~/.skb/env` if not present; symlinks the wrapper to
    `~/.local/bin/skb` (or `/usr/local/bin/skb` with `--system`).
  - Homebrew: `brew install 887/tap/skb`. Tap formula installs JAR
    and wrapper.
  - Manual: download tarball, extract, add to PATH.
- [ ] **CLI-H.10** Version naming: `skb <semver>-<sha7>` matching the
  APK convention (W.2). The same Gradle subproject also stamps
  `skb-cli-<version>-<sha7>.jar`.
- [ ] **CLI-H.11** GitHub Actions release workflow: a single workflow
  builds APK + skb-cli JAR + tarballs + install.sh, signs them with
  the project signing key, uploads to the GitHub Release. Per X.8.
- [ ] **CLI-H.12** Windows: ship a `skb.cmd` wrapper alongside the
  POSIX `skb` for users running under cmd/PowerShell. WSL users use
  the POSIX one. Native Windows is best-effort in v1; primary
  platforms are Linux + macOS.
- [ ] **CLI-H.13** Shell completions: ship `skb.bash`, `skb.zsh`,
  `skb.fish` files in the tarball under `completions/`. Generated via
  clikt's completion-generator. Install script wires the right shell
  to source them.
- [ ] **CLI-H.14** Self-update mechanic (CLI-A.22): the wrapper itself
  does NOT update; `skb self-update` is implemented in the JAR and
  also rewrites the wrapper script. SHA-256 verified against the
  release's `SHA256SUMS` file before replacement.
- [ ] **CLI-H.15** Cold-start budget: `skb --version` < 800ms on a
  cold cache, < 200ms warm. `-Xshare:auto` (CDS, class data sharing)
  helps; if it proves slow in Claude's loop, the Kotlin/Native binary
  path (D.24, "later") becomes the followup. Tracked as a v1.1
  optimisation.

---

## Phase CLI-I — testing

- [ ] **CLI-I.1** Pure JVM unit tests in `:cli`. No Android, no
  Robolectric. JUnit 5 + kotlin.test.
- [ ] **CLI-I.2** Integration tests against a temp `git init` repo on
  disk per test. Each test boots a fresh tempdir, runs `skb repo
  init`, then exercises commands. Teardown removes the dir.
- [ ] **CLI-I.3** Golden-output tests: every command's `--json` output
  is captured against a fixture under
  `cli/src/test/resources/goldens/<command>/<scenario>.json`. Tests
  fail on output drift; updating fixtures is an explicit
  `./gradlew :cli:updateGoldens` task to avoid accidental
  rubber-stamping. Per `decisions.md`-style discipline.
- [ ] **CLI-I.4** AI-roundtrip test: scripted scenario
  ```text
  add → list → show → edit → show → cancel → show
  ```
  asserts state at each step. Run against both event and task surfaces.
- [ ] **CLI-I.5** Property-based tests for the JSON envelope: every
  command, when handed a random valid arg set, produces a JSON output
  that validates against its declared schema (CLI-C.3).
- [ ] **CLI-I.6** Concurrency tests: two `skb` processes writing the
  same repo simultaneously. The losing writer must exit 3
  `conflict.concurrent_modify` cleanly; the winner commits.
- [ ] **CLI-I.7** Tx tests: tx start → multiple writes → abort verifies
  no commits. tx start → multiple writes → commit verifies single
  commit containing every change.
- [ ] **CLI-I.8** Migration test: a v0 (synthetic) repo gets migrated
  to v1, every entity re-validated.
- [ ] **CLI-I.9** Help-completeness gate: a CI check parses
  `skb help --json`, asserts every declared command also has a JSON
  Schema fixture under `META-INF/skb/schemas/`. Prevents
  drift between code and documentation.
- [ ] **CLI-I.10** Bash + zsh + fish completion smoke tests: source
  the completion file in each shell's CI container; assert
  `complete -p skb` returns success.
- [ ] **CLI-I.11** Cross-platform CI matrix: Linux (Ubuntu 22.04),
  macOS (latest), Windows (PowerShell). Wrapper installs and `skb
  --version` runs on all three.

---

## Phase CLI-J — logging, editor, stdin piping, completions

- [ ] **CLI-J.1** Structured logs at `~/.skb/logs/<yyyy-mm-dd>.log`
  (rotated daily). Format: JSON lines, each entry:
  ```json
  {"ts":"2026-05-09T18:30:00+02:00","level":"info","cmd":"event.add","msg":"committed","commit":"7c4b9e2","exit":0,"duration_ms":142}
  ```
- [ ] **CLI-J.2** `--verbose` / `-v` raises stdout log level to
  `debug`; `--quiet` / `-q` lowers to `error`. Per-invocation only;
  config default in `~/.skb/config.toml`.
- [ ] **CLI-J.3** Editor integration: `--body-editor` opens
  `$VISUAL` / `$EDITOR` (in that order; fallback `vi`) on a tempfile
  prefilled with `# Markdown body for <kind> "<title>" — save & exit
  to commit; empty file aborts.`. On editor exit non-zero or empty
  file, abort with exit 1.
- [ ] **CLI-J.4** Stdin piping: `--from-stdin` reads stdin as a full
  frontmatter+body document (`+++` fenced) and uses it as the event/
  task/etc. payload. Other flags become refinements/overrides. Lets
  Claude pipe a complete `+++\n...\n+++\n<body>\n` blob directly.

  ```text
  $ cat <<'EOF' | skb event add --from-stdin
  +++
  title = "Dentist"
  start = 2026-05-12T14:00:00+02:00
  end = 2026-05-12T14:45:00+02:00
  calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000001"
  +++

  Bring last X-ray. Park behind building.
  EOF
  ✓ added event "Dentist" …
  ```

- [ ] **CLI-J.5** Shell completions wired per CLI-H.13. Completion
  data sourced from `skb help --json` so it's always in sync.
- [ ] **CLI-J.6** `--no-color` (and `NO_COLOR` env per the
  no-color.org convention) disables ANSI codes in stdout/stderr.
- [ ] **CLI-J.7** Auto-pager: when stdout is a TTY and output exceeds
  the terminal height, pipe through `$PAGER` (default `less -R`);
  disabled by `--no-pager` or when `--json` is set (JSON output should
  always be raw).

---

## Phase CLI-K — future hooks

These are explicit extension points reserved for v1.1+. None are
implemented in v1, but the v1 architecture deliberately leaves them
non-blocked.

- [ ] **CLI-K.1** `skb daemon` watch mode. A long-lived process that
  watches `<repo>/` via `inotify`/`FSEvents`/`ReadDirectoryChangesW`,
  auto-runs `skb sync` on remote-push events, and emits change events
  on the `~/.skb/run/events.sock` Unix domain socket (set up by
  CLI-B.8 in v1). v1 contribution: the socket emitter; the daemon
  listener ships in v1.1.
- [ ] **CLI-K.2** `skb serve` — a local MCP (Model Context Protocol)
  server that exposes every `skb` command as a structured tool. AI
  agents that prefer MCP over CLI invocation get type-checked tool
  calls. v1 contribution: the `--json` envelope is already shaped
  like an MCP tool response, so wrapping it in v1.1 is mechanical.
- [ ] **CLI-K.3** WebSocket subscription for UI ↔ CLI cooperation: the
  Android app, when running, optionally exposes a localhost-only
  WebSocket on a discoverable port. `skb` can subscribe to receive
  realtime updates ("the user just dragged event X to a new time")
  and apply them. v1 contribution: nothing concrete; v1.1 owns the
  protocol design.
- [ ] **CLI-K.4** `skb plugin add <name>` — a plugin loader that drops
  command jars into `~/.skb/plugins/` and registers them as
  subcommands. v1 contribution: the clikt-based parser is plugin-
  friendly already; we deliberately don't expose plugin discovery in
  v1.
- [ ] **CLI-K.5** Kotlin/Native binary path. Per D.24, "later, if
  startup-time on JVM proves heavy in Claude's loop." Decision
  forwarding to v1.1: if JVM cold start exceeds 800ms despite CDS
  tuning, ship a Kotlin/Native build for Linux + macOS as a
  `skb-native-<os>-<arch>` artifact alongside the JAR variant. The
  shell wrapper auto-prefers the native binary if present.

---

## Cross-cutting tradeoffs resolved inline

1. **Argument parser choice (CLI-H.2).** Considered: clikt, Picocli,
   kotlinx-cli, hand-rolled. **Resolved: clikt.** Rationale: nested
   subcommand model maps cleanly onto our `skb <noun> <verb>` shape,
   help generation is built in, footprint is small (~200KB), license
   Apache-2.0. Picocli rejected — Java-flavored DSL; kotlinx-cli
   rejected — abandoned upstream; hand-rolled rejected — reinventing
   POSIX parsing is a footgun.

2. **JVM vs Kotlin/Native for v1 (CLI-H.6, CLI-K.5).** Considered:
   ship native-first to minimise cold start; ship JVM-first to share
   `:core` with Android. **Resolved: JVM-first.** Rationale: the
   shared `:core` module deduplicates RepoWriter/RepoScanner/RRULE
   expansion between Android and CLI, which is the highest-value
   invariant. Cold start is mitigated with `-Xshare:auto` (CDS) and
   `-XX:+UseSerialGC`; the v1.1 escape hatch (CLI-K.5) is documented.

3. **Subcommand naming: `skb list` for todolists (CLI-A.5).**
   Considered: `skb todolist`, `skb tl`, `skb list`. **Resolved:
   `list`.** Rationale: matches the rest of the verbs (`cal`,
   `tz`, `tx`); the shadowing with `skb event list` is resolved by
   position. Documented loudly in `skb help`.

4. **Lockfile vs sql-style transactions (CLI-F.1).** Considered:
   client-side SQLite transactions, lockfile, git stash dance.
   **Resolved: lockfile** because it survives process crashes (you
   can read it from another process to see what's in flight) and
   integrates naturally with the per-write-file model. Stale-lock
   detection (CLI-F.2) handles the crash case.

5. **Auto-commit per write vs deferred commit (CLI-B.4).**
   Considered: only commit on `tx commit`. **Resolved: commit per
   write by default; opt-out via `--no-commit` or explicit tx.**
   Rationale: matches the GUI's per-action-commit model and gives
   AI agents observable atomicity without forcing them to manage tx
   state. Power users who want batches use `tx`.

6. **JSON-on-stderr-on-error duplication (CLI-C.6).** Considered:
   JSON only on stdout, errors as plain text on stderr. **Resolved:
   JSON envelope on stderr too when in `--json` mode.** Rationale:
   downstream consumers polling stderr (e.g. a pipe reader) shouldn't
   have to switch parsing modes mid-stream. Cost: a few extra bytes;
   benefit: uniform machine consumption.

7. **`event cancel` vs `event delete` (CLI-A.1).** Considered:
   collapse to one verb. **Resolved: keep both.** Cancel sets a
   status field and preserves the file (audit trail, struck-through
   render); delete removes the file. AI agents needing
   destructive-remove call `delete` explicitly; cancellation is the
   safe default verb when intent is "this is no longer happening".

8. **Concurrent-modify detection: mtime vs sha (CLI-B.7).**
   **Resolved: sha + size precheck before write.** Mtime alone is
   unreliable across filesystems; sha is exact. Cost: one extra
   read per write; acceptable. Mtime as a cheap fast-path is fine;
   sha confirms.

9. **GPG key storage on desktop (CLI-A.6 gpg-import).** **Resolved:
   AES-GCM-encrypted file at `~/.skb/keys/<fpr>.asc.aes` keyed by
   `~/.skb/master.key` (generated on first run, mode 0600).**
   Rationale: matches the GUI's EncryptedSharedPreferences security
   posture as closely as a desktop process can; standard Unix
   pattern. Not as strong as a hardware-backed Android keystore, but
   on desktop the user can use ssh-agent / gpg-agent for stronger
   options later.

10. **Idempotency surface (CLI-A.1, CLI-G.4).** **Resolved:
    every write command accepts `--id <uuidv7>`; same `--id` means
    update-or-no-op.** Rationale: AI agents inevitably retry; making
    every retry safe is more valuable than command-level retry-once
    contracts. The `task done` no-op-on-already-done special case
    extends the same principle to state transitions.

11. **CSV import as bulk vs per-row commits (CLI-A.2 import-csv).**
    **Resolved: single batched commit per import.** Rationale: a
    CSV import is one user-intent; N commits would clutter history
    and slow down per-commit hooks (e.g. signing). Per-row failure
    still rolls back the whole import via `tx`-style staging.

12. **Help auto-pager interaction with `--json` (CLI-J.7).**
    **Resolved: `--json` disables the pager unconditionally.**
    Rationale: machine consumers don't want pagination; humans
    asking for JSON are likely piping to `jq` and don't either.

---

## Open hooks deferred to v1.1+

Tracked here so a future agent reading this file knows what's
intentionally not in v1 (rather than "we forgot"):

1. **Kotlin/Native binary** (CLI-K.5) — escape hatch for cold-start
   regressions; ship if JVM CDS proves insufficient.
2. **MCP server `skb serve`** (CLI-K.2) — typed AI tool surface.
3. **`skb daemon` watch mode** (CLI-K.1) — auto-sync on filesystem
   events. The Unix-domain-socket emitter (CLI-B.8) is in v1; the
   listener daemon is v1.1.
4. **WebSocket UI ↔ CLI cooperation** (CLI-K.3) — realtime
   bidirectional updates between the Android app and a desktop
   `skb` session.
5. **Plugin loader** (CLI-K.4) — `~/.skb/plugins/<name>.jar`
   subcommand registration. Architecture is plugin-friendly already;
   discovery surface is not exposed.
6. **`skb serve --mcp` SSE transport** — once MCP arrives, add SSE
   alongside stdio.
7. **Native Windows wrapper polish** (CLI-H.12) — v1 ships a basic
   `.cmd` wrapper; native CDS tuning + bundled JRE for Windows is
   v1.1.
8. **Per-command Markdown body templates** — `skb event add` could
   accept a `--body-template <name>` flag pulling from a templates
   registry; deferred. Workaround: `--body-file <path>` covers the
   90% case.

---

## Status footer

Every CLI-* phase above is sub-step-checkbox-tickable. As work lands,
tick the boxes here and add the jj change ID to the phase header per
the global plan-file convention. When all CLI-* phases are ticked,
flip the top-of-file status from `🚧 IN-PLANNING` to `✅ DONE`.

Cross-link back to [`main.md`](main.md) Phase X. Cross-coordinated with
[`data-model.md`](data-model.md) Phase DM-K (AGENTS.md CLI-first
rewrite) and [`sync-engine.md`](sync-engine.md) Phase SE-Q+ (signed
commits, CalDAV, LFS).

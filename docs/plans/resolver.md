# strictlykeptboy — resolver + common-time finder

## Status: 🚧 IN-PLANNING

Owns the algorithmic core of the app: how N active calendars × M active
todolists × time-window rules × priorities × RRULE recurrences ×
per-occurrence exceptions × special-event overrides collapse into a
deterministic, renderable schedule for any `(date, view-mode)`. Also
the common-time finder.

This document corresponds to **Phase E** in `main.md` (`E.1`–`E.6`) and
**Phase N** (common-time finder UI consumes RV-E). Cross-links back
into `main.md` via the phase letter prefixes below.

Locked-decision references: `decisions.md` D.5 (overlay/priority), D.6
(recurrence), D.10 (common-time finder), D.11 (todolists), D.21
(performance budgets).

**Phase prefix:** `RV-` (Resolver/View).

---

## Cross-reference table

| Resolver phase | main.md phase | Decisions ref |
|---|---|---|
| RV-A — active-set evaluator | E.1 | D.5 |
| RV-B — recurrence materializer | E.2 | D.6 |
| RV-C — overlay + priority + render pipeline | E.3, E.4 | D.5 |
| RV-D — view-mode adapters | E.4 + G.1–G.5 | D.5, D.21 |
| RV-E — common-time finder | E.5 + N.1–N.3 | D.10, D.21 |
| RV-F — caching + invalidation | E.6 | D.21 |
| RV-G — testing strategy | (cross-cuts) | D.21 |

---

## Vocabulary (locked, used throughout this doc)

- **Configured repo** — a repo the user added in Settings → Repos.
- **Active calendar** — a calendar whose `active_toggle = true` AND
  whose `active_windows` cover the query date AND whose `active_hours`
  cover the query time. Same shape for todolists.
- **Materialized event** — a concrete `(start, end, fields…)` instance.
  One-off events are already materialized in their file. Recurrences
  are materialized lazily by RV-B for the queried date range.
- **Resolved instance** — a materialized event annotated with its
  source calendar, repo, author, and post-exception fields. The unit
  the renderer consumes.
- **Slot** — a contiguous time interval in the rendered output during
  which the same set of resolved instances overlaps. Slot boundaries
  are at every event start/end among the active set.
- **Primary / secondary / overflow** — within a slot, the highest
  priority instance is *primary*, the next K-1 are *secondary*
  (banded/dimmed), the rest are absorbed into an *overflow* count
  (UI shows `+N`). K (band capacity) is view-dependent (RV-D).
- **Render pipeline** — RV-A → RV-B → RV-C → RV-D, optionally cached
  by RV-F.

**Decision (terminology lock):** the resolver speaks in *resolved
instances* and *slots*. Compose-side data classes mirror these names
1:1. Rationale: collapses two parallel vocabularies (events-vs-slots
in the data layer; cards-vs-bands in the UI layer) into one term-set
that survives the boundary cleanly.

---

## Phase RV-A — active-set evaluator

> main.md → E.1. decisions.md → D.5.

**Goal:** given `(queryDate, queryTime)` and the snapshot of every
configured repo, return the set of currently-active calendars and
todolists.

### RV-A pseudo-code

```kotlin
data class CalendarMeta(
    val id: CalendarId,
    val repoId: RepoId,
    val priority: Int,                 // 1..1000
    val activeToggle: Boolean,
    val activeWindows: List<DateRange>,// empty = always
    val activeHours: List<HourRange>,  // empty = all hours
    val tzId: ZoneId,                  // device tz unless overridden
    val kind: CalendarKind,            // Regular | Timebox
)

data class DateRange(val start: LocalDate, val end: LocalDate?) // end null = open-ended
data class HourRange(val day: DayOfWeek, val from: LocalTime, val to: LocalTime)
                                       // to may be ≤ from (midnight rollover)

fun isActive(c: CalendarMeta, at: ZonedDateTime): Boolean {
    if (!c.activeToggle) return false
    val local = at.withZoneSameInstant(c.tzId)
    val date  = local.toLocalDate()
    val time  = local.toLocalTime()
    val dow   = local.dayOfWeek

    val inWindow = c.activeWindows.isEmpty() ||
        c.activeWindows.any { w ->
            !date.isBefore(w.start) && (w.end == null || !date.isAfter(w.end))
        }
    if (!inWindow) return false

    val inHours = c.activeHours.isEmpty() ||
        c.activeHours.any { h -> matchesHour(h, dow, time) }
    return inHours
}

fun matchesHour(h: HourRange, dow: DayOfWeek, t: LocalTime): Boolean =
    if (h.to > h.from) {
        // simple case: 09:00..17:00 on `dow`
        h.day == dow && !t.isBefore(h.from) && t.isBefore(h.to)
    } else if (h.to == h.from) {
        // zero-length range = never active (explicit pseudo-disable)
        false
    } else {
        // midnight rollover: 22:00..06:00 means 22:00..24:00 on dow
        // OR 00:00..06:00 on dow+1
        (h.day == dow            && !t.isBefore(h.from)) ||
        (h.day == dow.minus(1)   && t.isBefore(h.to))
    }
```

**Decision (empty windows = always):** missing or `[]` `active_windows`
means "this calendar is in effect on every date." Same for
`active_hours` for "every hour." Rationale: the common case is an
always-on calendar; users should not have to enumerate the year.

**Decision (range semantics):** date ranges are inclusive on both
ends. Hour ranges are inclusive on `from`, exclusive on `to`. Rationale:
matches what users mean when they type "09:00..17:00" (17:00 is the
clock-out moment, not still working).

**Decision (timezone):** every calendar carries an optional `tzId`;
default = device tz. The resolver evaluates active-windows / active-
hours in the calendar's tz, not the device tz. Rationale: a "work
hours 09:00–17:00 Mon–Fri" calendar configured in Berlin should not
shift just because the user's phone is briefly in New York. v1 does
NOT expose tz override in the UI (D.6: device-tz default), but the
resolver respects per-entity tz when set in the file (forward-compat).

**Decision (DST):** date arithmetic uses `java.time` which already
handles DST boundaries. The 1h-spring-forward gap and 1h-fall-back
overlap are handled as: query times that land in the gap are treated
as the closest representable instant after the gap; times in the
overlap match BOTH offsets and are considered active if either
matches (so a "09:00–17:00" rule covers both 09:00 EDT and 09:00
EST when fall-back happens at 02:00). Test cases below.

**Decision (midnight rollover):** an `HourRange` with `to ≤ from`
(but not `to == from`) means the range crosses midnight. The
`matchesHour` predicate splits it into two intervals on consecutive
days. Rationale: night-shift workers and night-bat/owl roles need
"22:00..06:00" without contortions.

### RV-A worked examples

**Example 1 — Always-on personal calendar:**
- Config: `active_windows = []`, `active_hours = []`.
- Any query → active.

**Example 2 — Summer-only multi-window:**
- Windows: `[2026-05-01..2026-08-31]`, `[2026-09-15..]` (open-ended).
- Query 2026-07-15 → active (first window).
- Query 2026-09-01 → not active (gap).
- Query 2027-03-01 → active (open-ended second window).

**Example 3 — Work hours:**
- Hours: `[(MON, 09:00..17:00)]`.
- Query Mon 12:00 → active.
- Query Mon 17:00 → not active (exclusive on `to`).
- Query Sat 12:00 → not active.

**Example 4 — Night shift across midnight:**
- Hours: `[(FRI, 22:00..06:00)]`.
- Query Fri 23:30 → active (matches `dow == FRI && t ≥ 22:00`).
- Query Sat 02:00 → active (matches `dow.minus(1) == FRI && t < 06:00`).
- Query Sat 06:01 → not active.

**Example 5 — DST fall-back overlap:**
- 2026-11-01 02:00 EDT == 2026-11-01 01:00 EST (1h overlap).
- Calendar tz = `America/New_York`, hours `[(SUN, 01:00..03:00)]`.
- Query at the first occurrence of 01:30 (EDT) → active.
- Query at the second occurrence of 01:30 (EST) → active.
- Test fixture pins both `Instant`s and expects `true`.

### RV-A phase steps

- [ ] **RV-A.1** Define `CalendarMeta`, `TodolistMeta`, `DateRange`, `HourRange` data classes in `core/resolver/Meta.kt`. Pure Kotlin; no Android deps.
- [ ] **RV-A.2** Implement `isActive(meta, at: ZonedDateTime): Boolean` per pseudo-code above. Identical predicate for calendars and todolists (same active-window shape per D.5/D.11).
- [ ] **RV-A.3** Implement `activeSet(allMetas, at): ActiveSet` returning `(activeCalendars: List<CalendarMeta>, activeTodolists: List<TodolistMeta>)`. Stable order: by `(priority desc, calendarId lex asc)` for determinism.
- [ ] **RV-A.4** Property tests (`kotest`): generate random windows + queries, assert `isActive` is monotone in toggle, idempotent, never throws.
- [ ] **RV-A.5** Edge-case unit tests: empty windows, multi-range, open-ended, midnight rollover, DST spring-forward gap, DST fall-back overlap, zero-length hour range = never active.
- [ ] **RV-A.6** Microbench: `activeSet` over 50 calendars in < 1ms (it's a pure Bool fold; no allocations on the hot path).

---

## Phase RV-B — recurrence materializer

> main.md → E.2. decisions.md → D.6.

**Goal:** given an `RRULE` from a `recurrences/<rule-id>.md` file plus
the queried date range, emit the materialized instances after applying
exceptions.

### RV-B inputs

```kotlin
data class RecurrenceFile(
    val ruleId: RuleId,
    val calendarId: CalendarId,
    val dtstart: ZonedDateTime,
    val duration: Duration,            // event length
    val rrule: String,                 // RFC5545 RRULE string
    val tzId: ZoneId,                  // resolved tz (D.6: device tz default)
    val baseFields: EventFields,       // title, body, emoji, etc.
)

sealed class ExceptionFile {
    abstract val ruleId: RuleId
    abstract val occurrenceDate: LocalDate
    data class Cancel(...) : ExceptionFile()
    data class Override(... val overrideFields: Map<String, Any?>) : ExceptionFile()
    data class Note(... val bodyAppend: String) : ExceptionFile()
}
```

### RV-B supported RRULE field set (v1)

Backed by `org.dmfs:lib-recur`. **Supported v1:**
`FREQ`, `INTERVAL`, `BYDAY`, `BYMONTHDAY`, `BYMONTH`, `BYSETPOS`,
`COUNT`, `UNTIL`, `WKST`.

**Excluded v1** (lib-recur supports them, but UI/templates won't emit
them; resolver tolerates them by passing through to lib-recur):
`BYHOUR`, `BYMINUTE`, `BYSECOND`, `BYWEEKNO`, `BYYEARDAY`. Rationale:
tonearm of complexity vs. real-world usage. Users who hand-write
these in a markdown file get correct expansion via lib-recur, but
the wizard/template authoring path doesn't expose them.

**Decision (COUNT vs UNTIL):** RFC5545 says they're mutually
exclusive. Resolver enforces: if both present, `COUNT` wins and
emits a warning to the validator (see C.8 in main.md). Rationale:
defensive — lib-recur would already throw, but we want a clear
user-facing message rather than a stacktrace.

### RV-B pseudo-code

```kotlin
fun materialize(
    rec: RecurrenceFile,
    range: ClosedRange<ZonedDateTime>,
    exceptions: List<ExceptionFile>, // pre-loaded for this ruleId
): List<MaterializedEvent> {

    // 1. Use lib-recur to enumerate occurrence start instants in range.
    val rule = RecurrenceRule(rec.rrule)
    val it   = rule.iterator(rec.dtstart.toMillis(), TimeZone.getTimeZone(rec.tzId.id))
    val starts = mutableListOf<ZonedDateTime>()
    it.fastForward(range.start.toMillis())
    while (it.hasNext()) {
        val ms = it.nextMillis()
        val zdt = Instant.ofEpochMilli(ms).atZone(rec.tzId)
        if (zdt > range.endInclusive) break
        starts += zdt
    }

    // 2. Index exceptions by occurrence date (in the rule's tz).
    val excByDate = exceptions.associateBy { it.occurrenceDate }

    // 3. For each start, apply exception (if any) and emit.
    return starts.mapNotNull { start ->
        val date = start.toLocalDate()
        when (val ex = excByDate[date]) {
            null              -> base(rec, start)
            is Cancel         -> null
            is Override       -> base(rec, start).withOverride(ex.overrideFields)
            is Note           -> base(rec, start).withBodyAppend(ex.bodyAppend)
        }
    }
}
```

### RV-B exception semantics

- `cancel` — remove the instance. Used for "I'm not doing standup
  this Friday." The base rule file is left alone.
- `override` — replace specific frontmatter keys for this occurrence.
  Example: shifted standup from 10:00 → 10:30 only on this date.
  Override fields by key; non-listed keys inherit from the base.
  Body, if provided, replaces (does not append) the base body.
- `note` — keep the instance with all fields intact, but append the
  exception's body to the rendered detail-sheet body. Used for
  "remember to bring snacks" reminders that don't change the schedule.

**Decision (override granularity):** `override` is a key-level merge,
not a deep merge. If the override sets `notifications = ["5m"]`, that
replaces the entire `notifications` array, it doesn't merge entries.
Rationale: predictable; users can copy-paste full arrays from the
base if they want additivity. Deep-merge is a footgun (what does
"merging" two reminder arrays even mean?).

**Decision (override + recurrence rule edited later):** if the user
edits the base `RecurrenceFile` (say changes title from "Standup" to
"Daily Sync"), existing `override` exceptions inherit the new title
unless they explicitly set their own. Rationale: matches how iCal
treats `RECURRENCE-ID`-based modifications.

### RV-B worked example

Base rule: standup, daily Mon–Fri, 09:00–09:15, 60 days from
2026-05-04.

Exceptions:
- `2026-05-13.md` — kind=`cancel`. (Public holiday.)
- `2026-05-20.md` — kind=`override`, `start_time = "10:00"`,
  `duration = "30m"`. (Special long standup.)
- `2026-05-27.md` — kind=`note`, body=`Bring slides`. (Reminder.)

Materializing range `[2026-05-11..2026-05-29]`:

| Date | Output |
|---|---|
| 2026-05-11 Mon | 09:00–09:15 Standup |
| 2026-05-12 Tue | 09:00–09:15 Standup |
| 2026-05-13 Wed | (cancelled — no instance) |
| 2026-05-14 Thu | 09:00–09:15 Standup |
| 2026-05-15 Fri | 09:00–09:15 Standup |
| 2026-05-18 Mon | 09:00–09:15 Standup |
| 2026-05-19 Tue | 09:00–09:15 Standup |
| 2026-05-20 Wed | 10:00–10:30 Standup (overridden) |
| 2026-05-21 Thu | 09:00–09:15 Standup |
| 2026-05-22 Fri | 09:00–09:15 Standup |
| 2026-05-25 Mon | 09:00–09:15 Standup |
| 2026-05-26 Tue | 09:00–09:15 Standup |
| 2026-05-27 Wed | 09:00–09:15 Standup, body+="Bring slides" |
| 2026-05-28 Thu | 09:00–09:15 Standup |
| 2026-05-29 Fri | 09:00–09:15 Standup |

### RV-B phase steps

- [ ] **RV-B.1** Wire `org.dmfs:lib-recur` (already locked in `decisions.md` D.1). Smoke-test rule iteration on JVM.
- [ ] **RV-B.2** Implement `RecurrenceMaterializer.materialize(rec, range, exceptions)` per pseudo-code.
- [ ] **RV-B.3** Implement field-overlay merge for `override` exceptions (key-level replacement, no deep merge).
- [ ] **RV-B.4** `note` body append: append a horizontal rule + the exception body to the base body for the rendered detail sheet (raw file is unchanged; this is render-only).
- [ ] **RV-B.5** COUNT vs UNTIL conflict handling: prefer COUNT, log validator warning.
- [ ] **RV-B.6** Test against lib-recur's RFC5545 vector tests (BYDAY, BYSETPOS like "last Friday of the month", BYMONTHDAY=-1).
- [ ] **RV-B.7** Custom DST tests: a 02:30 daily event on 2026-03-08 (US spring-forward) — does lib-recur skip it or shift it? Decision: shift to 03:30 local for that one day (lib-recur default), document in test.
- [ ] **RV-B.8** COUNT termination test: rule with COUNT=10 across a 1-year query range emits exactly 10 instances regardless of UNTIL.
- [ ] **RV-B.9** Range-clipped iteration: `fastForward` to range start; never enumerate all-time history. Performance assertion: a 10-year-old daily rule queried for 7 days = ≤ 7 iterations beyond fastForward seek.

---

## Phase RV-C — overlay rendering for a date range

> main.md → E.3, E.4. decisions.md → D.5.

**Goal:** given the active calendars and their materialized events for
a date range, produce a `RenderedSchedule` that view-mode adapters can
consume directly.

### RV-C data shapes

```kotlin
data class ResolvedInstance(
    val instanceId: String,            // stable: "<file-id>" or "<rule-id>@<date>"
    val calendar: CalendarMeta,
    val repoId: RepoId,
    val author: PersonId?,
    val start: ZonedDateTime,
    val end: ZonedDateTime,            // start + duration
    val title: String,
    val emoji: String?,
    val bodyMarkdown: String,
    val color: Color?,                 // null = inherit from calendar
    val spawnedTaskId: TaskId?,        // D.11: linked task
    val isAllDay: Boolean,
    val source: InstanceSource,        // OneOff | RecurrenceInstance(ruleId, occurrenceDate)
    val notifications: List<Duration>,
)

data class RenderedSlot(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val primary: ResolvedInstance,
    val secondary: List<ResolvedInstance>, // length ≤ K-1
    val overflow: Int,                     // count beyond K
)

data class RenderedSchedule(
    val rangeStart: ZonedDateTime,
    val rangeEnd: ZonedDateTime,
    val slots: List<RenderedSlot>,         // sorted by start asc, non-overlapping
    val emptyDays: List<LocalDate>,        // for view-mode shortcut
    val sourceDigest: SnapshotDigest,      // for cache key (RV-F)
)
```

### RV-C algorithm — slot construction

The renderer takes a sequence of resolved instances (potentially
overlapping in time) and produces a sequence of non-overlapping slots
where each slot's content is constant.

```kotlin
fun render(
    instances: List<ResolvedInstance>,
    bandCapacity: Int,                 // K, view-dependent (RV-D)
): List<RenderedSlot> {

    if (instances.isEmpty()) return emptyList()

    // 1. Collect all boundary instants (every start, every end).
    val boundaries = sortedSetOf<ZonedDateTime>()
    instances.forEach { boundaries += it.start; boundaries += it.end }
    val pts = boundaries.toList()

    // 2. For each [pts[i], pts[i+1]) interval, find covering instances.
    val slots = mutableListOf<RenderedSlot>()
    for (i in 0 until pts.size - 1) {
        val s = pts[i]; val e = pts[i + 1]
        if (s == e) continue
        val cover = instances.filter { it.start <= s && it.end >= e }
        if (cover.isEmpty()) continue

        // 3. Resolve priority among `cover`.
        val ranked = cover.sortedWith(
            compareByDescending<ResolvedInstance> { it.calendar.priority }
                .thenBy { it.calendar.id.toString() }   // deterministic tiebreak
        )
        val primary    = ranked[0]
        val secondary  = ranked.drop(1).take(bandCapacity - 1)
        val overflow   = (ranked.size - 1 - secondary.size).coerceAtLeast(0)
        slots += RenderedSlot(s, e, primary, secondary, overflow)
    }

    // 4. Coalesce adjacent slots with identical (primary, secondary, overflow).
    return coalesceAdjacent(slots)
}
```

**Decision (priority tiebreak):** higher `priority` wins. Ties broken
by `calendar.id` lexicographic ascending. Rationale: D.5 specifies
deterministic tiebreak; calendar UUID is the only stable, repo-
shareable key. UUIDv7 has time-prefix, so newer calendars sort later
— if two have identical priority the older calendar wins, which
matches "I configured this calendar first, it should be primary by
default."

**Decision (special events naturally fall out):** D.5 says "Special
Events" is just a calendar with `priority = 999`. Verification: in
`render`, for any slot containing a vacation event (priority 999) and
a regular work event (priority 500), `ranked[0]` is vacation, work
becomes secondary. **No special-cased code path needed.** This is
the design intent of the priority model and the test suite explicitly
asserts it (RV-G.4).

**Decision (band capacity K):** view-dependent.
- Day view: K = 4 (primary + 3 banded). Day timeline cell is wide.
- Week view: K = 3.
- Month chip: K = 3 (primary chip + up to 2 secondary chips, then `+N`).
- Year heat map: K = 1 (only count matters, not bands).

Rationale: balance between visual richness and tile size. Settable
in code; configurable per-user is a v2 feature.

**Decision (slot coalescing):** after slot construction, adjacent
slots whose `(primary.id, secondary.ids, overflow)` tuple is identical
are merged into one slot with the union range. Rationale: avoids
emitting 12 zero-difference slots when an event spans noon (where
nothing else starts/ends). Cheaper to render.

**Decision (all-day events):** an `isAllDay = true` event has start
= 00:00 local, end = 24:00 local in the calendar's tz. They sit in
the timeline alongside timed events. The day-view UI segregates them
into a top "all-day" strip; the resolver does NOT do this segregation
— it's a view-mode decision (RV-D). Rationale: keep the resolver
output independent of view chrome.

**Decision (zero-duration events):** `start == end`. They are
*point* events: included in the slot model as a "marker" slot with
`start == end`, which by convention the renderer turns into a single
indicator pin. Common-time finder treats them as point-busy (not
free-slot-consuming, see RV-E).

### RV-C todolist overlay

Tasks render alongside calendar events in the same slot model, but
only in:
- The Today task view (RV-D Today list combiner).
- The Day-view timeline if the task has a `due` time-of-day (rendered
  as a thin marker line, not a band).

For a slot's `primary` selection, tasks compete with events on the
same priority field; in v1 we render task markers OUTSIDE the
primary/secondary band model (as a thin chevron at the slot start).
Rationale: bands are for "this is happening now" — a task is "this
is due now," semantically different. The user complaints from
mixing them in calendar tools (Google Calendar's task strip) confirm
the segregation.

### RV-C phase steps

- [ ] **RV-C.1** Implement `Renderer.render(instances, bandCapacity)` per pseudo-code.
- [ ] **RV-C.2** Implement `coalesceAdjacent` slot merger.
- [ ] **RV-C.3** Implement `priorityTiebreak` Comparator (priority desc, then calendar.id lex asc). Single named comparator; reused by view adapters.
- [ ] **RV-C.4** Implement `instancesForRange(activeCalendars, range)` — pull one-off events from Room (already indexed by day) + materialized recurrence instances (RV-B). Apply `spawns_task` annotation.
- [ ] **RV-C.5** Worked-example tests: vacation 999 over work 500 → vacation primary, work secondary; multi-overlap with > K → overflow count.
- [ ] **RV-C.6** Tiebreak determinism test: same priority + ids in any input order → same output (sort is stable on `id`).
- [ ] **RV-C.7** All-day + timed event coexistence test.

---

## Phase RV-D — view-mode adapters

> main.md → E.4 + G.1–G.5. decisions.md → D.5, D.21.

**Goal:** turn a `RenderedSchedule` (or 7 / 30 / 365 of them) into
view-mode-ready data for Compose.

### RV-D Day view

- Input: `RenderedSchedule` for a single date.
- Output: list of `DayCell(hour, slotsInThisHour)` for hours 00..23.
- Render: vertical timeline; each slot draws a card whose vertical
  extent is `start..end`. Secondary instances rendered as right-edge
  bands (8dp wide each, dimmed). Overflow rendered as `+N` chip at
  the bottom of the primary card.
- All-day strip: top of view, horizontal chip strip of all-day events
  (filtered by `isAllDay`).
- Task markers: thin chevrons at slot start times for tasks due in
  range.

### RV-D Week view

- Input: 7 `RenderedSchedule`s (one per day) sharing a tz.
- Output: 7 columns × 24 rows.
- Same per-column algorithm as Day view but K=3 to fit narrower
  columns.
- Cross-day events: `WeekRangedEvent(startDay, endDay)` rendered as
  a horizontal band across the day headers.

### RV-D Month view

- Input: 28..31 `RenderedSchedule`s.
- Per day, collapse to a chip list:
  - Top primary's title (truncated to N chars) as the lead chip.
  - Up to K-1 secondary chips (calendar-color tinted).
  - `+overflow` chip if more.
- Heat-color background: count-based (0 events: surface, 1–3:
  surfaceVariant, 4–7: secondaryContainer, 8+: primaryContainer).

### RV-D Timebox-mode view

- Input: today's `RenderedSchedule` filtered to `kind == Timebox`
  calendars.
- Output: edge-to-edge stacked blocks, big tap targets, "now / next"
  banner. Non-timebox calendars are explicitly hidden in this view.

### RV-D Year view

- Input: 365 `RenderedSchedule`s.
- Per day, collapse to a single density int (count of slots in day,
  not events).
- Render: 12 mini-month grids; each cell colored by density bucket.

### RV-D Today task view (combiner)

- Input: today's `RenderedSchedule` (for spawned tasks) + every
  active todolist's tasks for today (dated) + standing pinned tasks.
- Output: priority-sorted task list. Sort: `(task.priority desc,
  task.due asc, task.id lex)`.
- `spawns_task` events contribute their linked task to this list
  (the task is loaded from its file in the todolist).

### RV-D phase steps

- [ ] **RV-D.1** Day adapter (`RenderedSchedule → DayLayout`).
- [ ] **RV-D.2** Week adapter (`List<RenderedSchedule> → WeekLayout`).
- [ ] **RV-D.3** Month adapter (`List<RenderedSchedule> → MonthLayout`).
- [ ] **RV-D.4** Timebox adapter (filters to `kind == Timebox` then reuses Day adapter).
- [ ] **RV-D.5** Year adapter (`List<RenderedSchedule> → YearDensity`).
- [ ] **RV-D.6** Today task combiner (events + dated tasks + standing pinned).
- [ ] **RV-D.7** Each adapter is a pure function on the rendered output (no I/O), so unit-testable without Android.

---

## Phase RV-E — common-time finder

> main.md → E.5 + N.1–N.3. decisions.md → D.10.

**Goal:** given a multi-repo busy set, find ranked free slots
satisfying a duration + filter set.

### RV-E inputs

```kotlin
data class CommonTimeQuery(
    val repos: List<RepoId>,
    val calendarFilter: Map<RepoId, Set<CalendarId>>?, // null = all active
    val range: ClosedRange<LocalDate>,                 // inclusive
    val duration: Duration,                            // e.g. PT1H30M
    val daysOfWeek: Set<DayOfWeek>?,                   // null = all
    val timeOfDay: ClosedRange<LocalTime>?,            // null = whole day
    val idealTimeOfDay: LocalTime?,                    // for ranking only
    val tzId: ZoneId,                                  // ranking tz
    val topK: Int = 20,
)
```

### RV-E algorithm

```kotlin
fun findCommonTime(q: CommonTimeQuery): List<FreeSlot> {

    // 1. Build busy set across selected repos × calendars × range.
    val activeCals = q.repos.flatMap { repo ->
        loadCalendars(repo).filter { c ->
            c.activeToggle && (q.calendarFilter?.get(repo)?.contains(c.id) ?: true)
        }
    }
    val busyInstances = activeCals.flatMap { cal ->
        eventsAndRecurrencesForRange(cal, q.range)
    }

    // 2. Project busy intervals into the (range × DOW × TOD) candidate window.
    val candidateWindows = enumerateCandidateWindows(q)
        // = list of [start..end) day-fragments matching dow + tod filters

    // 3. Subtract busy intervals from each candidate window.
    val freeSlots = mutableListOf<FreeSlot>()
    for (cw in candidateWindows) {
        freeSlots += subtractBusy(cw, busyInstances)
    }

    // 4. Filter to slots ≥ duration.
    val viable = freeSlots.filter { it.length >= q.duration }
        // for slots > duration, also emit one slot per `duration`-aligned
        // sub-window (so a 4h free block yields multiple 1h options
        // ranked separately when ranking matters)

    // 5. Rank.
    return viable.sortedWith(
        compareByDescending<FreeSlot> { it.length }                    // contiguity
            .thenBy { idealDistance(it, q.idealTimeOfDay) }            // proximity
            .thenBy { it.start }                                       // earliest
    ).take(q.topK)
}

fun subtractBusy(window: TimeRange, busy: List<ResolvedInstance>): List<FreeSlot> {
    // sweep-line: walk busy intervals overlapping `window`, emit gaps.
    val overlapping = busy
        .filter { it.end > window.start && it.start < window.end }
        .sortedBy { it.start }
    val gaps = mutableListOf<FreeSlot>()
    var cursor = window.start
    for (b in overlapping) {
        val bStart = maxOf(b.start, window.start)
        val bEnd   = minOf(b.end,   window.end)
        if (bStart > cursor) gaps += FreeSlot(cursor, bStart)
        cursor = maxOf(cursor, bEnd)
    }
    if (cursor < window.end) gaps += FreeSlot(cursor, window.end)
    return gaps
}
```

### RV-E edge-case rules

- **All-day events:** `isAllDay` events translate to `00:00..24:00`
  in the calendar's tz before subtraction. They block any same-day
  candidate window entirely.
- **Zero-duration events:** point-busy, do NOT consume free time.
  They appear in the busy index but contribute zero length to the
  subtraction. This means a 09:00 standup of duration 0 (a "marker")
  doesn't block 09:00–10:00 from being free.
- **Cross-day events (e.g. 23:00–02:00):** split into two intervals
  at the day boundary before being subtracted from per-day candidate
  windows.
- **Timezones:** v1 assumes all repos run in the device tz (D.10 +
  D.6). The query's `tzId` is the device tz. The resolver uses each
  calendar's stored tz when materializing recurrence, then converts
  to the query tz before subtraction. v2 will surface multi-tz
  scheduling once we have a UX for it.
- **Empty candidate windows:** if `daysOfWeek` excludes every day in
  the range, return `[]` (not an error).
- **Duration > any single candidate window length:** return `[]`. We
  do not span the time-of-day filter (e.g. with TOD=09:00..17:00 and
  duration=10h, no slot can satisfy because no day fragment is 10h).

### RV-E ranking — proximity-to-ideal

```kotlin
fun idealDistance(slot: FreeSlot, ideal: LocalTime?): Long {
    if (ideal == null) return 0L
    val slotMid = slot.start.plus(slot.length.dividedBy(2)).toLocalTime()
    return Duration.between(ideal, slotMid).abs().toMillis()
}
```

**Decision (proximity metric):** distance from slot midpoint to ideal.
Rationale: more honest than start-distance (a 4h slot starting an
hour before ideal isn't worse than a 1h slot starting at ideal, and
midpoint surfaces this). Tie-broken by `slot.start` (earliest date),
not by length again.

### RV-E worked example

Query: 2 repos, 1.5h slot, 2026-05-11..2026-05-15, weekdays, TOD
09:00..17:00, ideal 14:00, top 5.

Busy in repo A: standup daily 09:00–09:15, lunch 12:00–13:00.
Busy in repo B: gym Mon/Wed/Fri 17:00–18:00 (outside TOD, ignored),
deep-work blocks Tue/Thu 10:00–12:00.

Candidate windows: 5 days × 09:00..17:00 = 5 × 8h = 40h total.

Subtract busy → free slots (per day, simplified):
- Mon: 09:15–12:00 (2.75h), 13:00–17:00 (4h)
- Tue: 09:15–10:00 (0.75h × not viable), 13:00–17:00 (4h)
- Wed: 09:15–12:00 (2.75h), 13:00–17:00 (4h)
- Thu: 09:15–10:00 (0.75h × not viable), 13:00–17:00 (4h)
- Fri: 09:15–12:00 (2.75h), 13:00–17:00 (4h)

Viable (≥ 1.5h): 8 slots.
Rank (length desc, then proximity to 14:00 midpoint, then earliest):
1. Mon 13:00–17:00 (4h, midpoint 15:00, |15:00-14:00|=1h, earliest)
2. Tue 13:00–17:00 (4h, midpoint 15:00, …, +1d)
3. Wed 13:00–17:00 (4h, …)
4. Thu 13:00–17:00 (4h, …)
5. Fri 13:00–17:00 (4h, …)

If we want sub-window emission for tighter slots, also produce e.g.
"Mon 13:30–15:00" with midpoint 14:15 (closer to ideal). v1 does
NOT do this — we emit one slot per maximal free interval. **Decision
(maximal vs aligned):** emit maximal free intervals only; rely on
contiguity ranking. Rationale: simpler API; users picking from the
list naturally pick the maximal block and adjust on the create-event
step. Aligned-sub-window emission is a v2 nicety.

### RV-E phase steps

- [ ] **RV-E.1** Define `CommonTimeQuery`, `FreeSlot` data classes.
- [ ] **RV-E.2** Implement `enumerateCandidateWindows(q)` per (date × dow × tod) triple.
- [ ] **RV-E.3** Implement `subtractBusy` sweep-line.
- [ ] **RV-E.4** Implement `findCommonTime` orchestration.
- [ ] **RV-E.5** Implement ranking comparator (length desc, idealDistance asc, start asc).
- [ ] **RV-E.6** Edge-case tests: empty range, no candidate windows, duration > window, all-day blockers, point-busy non-blocking.
- [ ] **RV-E.7** Performance test: 5 repos × 30 days × 200 events each → < 800ms (D.21 budget).
- [ ] **RV-E.8** Cross-day event splitting test (23:00–02:00 spanning Mon→Tue).

---

## Phase RV-F — caching + invalidation

> main.md → E.6. decisions.md → D.21.

**Goal:** memoize `RenderedSchedule` outputs so repeat queries (e.g.
swiping through days) are sub-50ms.

### RV-F cache key

```kotlin
data class RenderCacheKey(
    val viewParams: ViewParams,           // (mode, range, repoSet)
    val activeCalSnapshot: SnapshotDigest,// SHA-256 of (id, priority, toggle, windows, hours) tuples
    val gitHeadPerRepo: Map<RepoId, ObjectId>,
)
```

The key incorporates everything that can change rendering output:
- `viewParams`: trivially affects output.
- `activeCalSnapshot`: priority/toggle/window/hour edits invalidate.
- `gitHeadPerRepo`: any commit invalidates entries for that repo.

**Decision (digest of active-cal snapshot):** SHA-256 over a sorted
canonical form: `(repoId, calId, priority, activeToggle,
activeWindowsCanonical, activeHoursCanonical)` joined by `\n`.
Rationale: a single int change in one calendar's priority must
invalidate the cache without having to enumerate every cached entry.
A digest collapses the compare to a single equality check.

### RV-F LRU policy

- **Budget:** 50 entries (rough rule: ~8 phone-screens of swipe
  history). Each entry is a `RenderedSchedule` ≈ 4–40KB depending on
  density → ~ 1MB worst case. Negligible vs the 150MB resident budget
  in D.21.
- **Eviction:** Java's `LinkedHashMap(accessOrder=true)` with a
  `removeEldestEntry` override. Standard.
- **Thread-safety:** wrap in a `Mutex` (Compose dispatches on
  Main but the resolver runs in a `Dispatchers.Default` flow).

### RV-F invalidation triggers

| Trigger | Effect |
|---|---|
| Git HEAD changes for repo X | Drop every entry whose key contains `repoId X` |
| Calendar/todolist toggle, priority, window, hour edit | Drop everything (snapshot digest changes) |
| Repo added/removed | Drop everything |
| Schema migration | Drop everything |
| Memory pressure (`onTrimMemory(LEVEL_RUNNING_LOW)`) | Drop half (LRU eldest) |

### RV-F phase steps

- [ ] **RV-F.1** Implement `SnapshotDigest` calculator (canonical bytes → SHA-256).
- [ ] **RV-F.2** Implement `RenderCache` (LRU, mutex-guarded).
- [ ] **RV-F.3** Wire invalidation: bus event from Git layer (HEAD-change), bus event from settings layer (toggle/priority/window).
- [ ] **RV-F.4** Wire `onTrimMemory` half-flush.
- [ ] **RV-F.5** Cache-hit metric for benchmark phase (V.3).

---

## Phase RV-G — testing strategy

> Cross-cuts every other phase. Pure-Kotlin unit tests under
> `core/resolver/src/test/`.

### RV-G test layers

- **L1 — pure predicates:** `isActive`, `matchesHour`,
  `priorityTiebreak`, `idealDistance`. Hand-written cases + property
  tests with `kotest`.
- **L2 — algorithm units:** `RecurrenceMaterializer`, `Renderer`,
  `subtractBusy`. Worked examples in this doc become test fixtures.
- **L3 — integration:** in-memory repos (no JGit), synthesized
  calendars + recurrences + exceptions, assert end-to-end
  `RenderedSchedule` output for golden cases.
- **L4 — performance:** synthesized 200-event month + 1000-event
  year + 5-repo finder query. Asserts D.21 budgets on the JVM (not
  device — the device version runs in `:benchmark` module gated to
  CI).

### RV-G specific test fixtures

- [ ] **RV-G.1** Active-set property tests (RV-A.4).
- [ ] **RV-G.2** lib-recur RFC5545 vector replay (RV-B.6).
- [ ] **RV-G.3** Override-merge fixture: base rule + override → merged instance has overridden keys, inherits the rest.
- [ ] **RV-G.4** Special-events validation: `priority = 999` vacation over `priority = 500` work; assert vacation primary, work secondary, no special-cased code path executed (track via a debug counter on a "special-event branch" — counter must be zero).
- [ ] **RV-G.5** Tiebreak determinism: shuffle input, stable output across 1000 iterations.
- [ ] **RV-G.6** Common-time golden case (the worked example above).
- [ ] **RV-G.7** Cross-day common-time (23:00–02:00 split).
- [ ] **RV-G.8** Cache invalidation: edit toggle, query, assert cold; query again, assert warm (hit).
- [ ] **RV-G.9** Cache invalidation on git HEAD change: simulate by manually flipping the per-repo HEAD in the cache key.
- [ ] **RV-G.10** Spawned-task linkage test: event with `spawns_task` shows the task in Today combiner.
- [ ] **RV-G.11** Cross-repo aggregation test: 2 repos, master toggle on → both render in unified view; master toggle off → only the active-repo renders.

---

## Tradeoffs resolved inline (summary)

The following design tradeoffs surfaced during planning. Each was
resolved by the planning agent per the "make the call" directive in
`decisions.md`.

| Tradeoff | Choice | Why |
|---|---|---|
| Empty `active_windows`/`active_hours` semantics | "always active" | Most common case; users would otherwise enumerate years. |
| Hour range inclusivity | `[from, to)` | Matches "work 9–17" as clock-out at 17. |
| Per-entity tz vs device tz | Per-entity tz, default device | Forward-compat for D.6 future flag; resolver respects file. |
| DST fall-back overlap | Match both offsets | Avoids invisible gaps in night-shift schedules. |
| RRULE field set v1 | FREQ/INTERVAL/BYDAY/BYMONTHDAY/BYMONTH/BYSETPOS/COUNT/UNTIL/WKST | Covers wizard/template needs; lib-recur tolerates the rest pass-through. |
| COUNT vs UNTIL conflict | COUNT wins; warn | Defensive vs lib-recur exception. |
| Override merge depth | Key-level replacement | Predictable; deep-merge of arrays is undefined. |
| Slot tiebreak | priority desc, calendar.id lex asc | Deterministic per D.5; UUIDv7 time-prefix → "older calendar wins on tie." |
| Special-event branch | None — falls out of priority | D.5 verification; tested via debug counter. |
| Band capacity K | View-dependent (4/3/3/1) | Visual richness vs tile size. |
| Slot coalescing | Yes, after construction | Avoids zero-difference spam. |
| All-day handling | Resolver emits 00:00–24:00; view chrome segregates | Keeps resolver view-agnostic. |
| Zero-duration events | Point-busy in finder, marker in renderer | Doesn't consume free time. |
| Task overlay vs band | Tasks render outside primary/secondary bands as markers | Semantically distinct from "happening now." |
| Common-time slot emission | Maximal intervals only, not aligned sub-windows | Simpler; ranking handles the rest. |
| Common-time proximity metric | midpoint-to-ideal distance | Honest about which slot is "near 14:00" given variable lengths. |
| Cache key digest | SHA-256 of canonicalized active-cal tuples | Single equality check vs per-entry compare. |
| Cache budget | 50 entries | ~1MB worst case; negligible vs 150MB resident budget. |

## Correctness-vs-performance calls

These are the places where I traded a small correctness compromise for
a large performance win. None of them affects user-visible behavior
in any case the v1 wizard or templates can produce.

1. **Recurrence iteration is range-clipped via `fastForward`.**
   A 10-year-old daily rule queried for a single day does not
   enumerate 3650 instances internally — lib-recur's `fastForward`
   skips ahead. **Edge:** if a malformed RRULE has weird BYSETPOS
   semantics where the Nth occurrence is sensitive to history, the
   `fastForward` may technically diverge from the spec. Mitigation:
   lib-recur is well-tested in DAVx⁵; this is theoretical, not
   observed. Tests assert behavior matches lib-recur's own vectors.

2. **Slot coalescing assumes secondary list equality is set-equality
   on instance ids.** I implemented it as ordered-list equality
   (cheaper). **Edge:** if two adjacent slots have the same instances
   in different secondary order (impossible given our priority sort,
   but defensively), they would not coalesce and we'd emit a
   redundant slot. Mitigation: priority sort is total + stable, so
   identical instance-set ⇒ identical secondary order. The defensive
   case cannot occur in our pipeline.

3. **Common-time finder emits maximal free intervals only.** A 4h
   free block does not produce sub-window options (Mon 13:00–14:30,
   Mon 13:30–15:00, …). **Edge:** if user's ideal time is 14:00
   inside a 4h block 13:00–17:00, we rank by midpoint (15:00), not
   by best-aligned 1.5h sub-window (which would be 13:15–14:45,
   midpoint 14:00). User picks 13:00–17:00 from the list and
   adjusts on the create-event step. v2 may emit aligned
   sub-windows.

4. **Active-set is computed once per render**, not once per slot.
   The active set is captured at the *render-call* instant and used
   throughout. **Edge:** an `active_hours` boundary that crosses
   inside the rendered range (e.g. user renders a 12-hour window
   straddling a 17:00 work-hours boundary) means the calendar is
   shown as active for the whole window even after 17:00.
   Mitigation: this is a deliberate semantic — D.5 says "active-
   windows only filter which calendars are even *considered*; once
   a calendar is active for the slot, all its events render." The
   doc is now precise: active-hours determine *consideration*, not
   per-event masking. (See RV-A worked example 3 commentary.)

5. **Snapshot digest uses SHA-256 over canonical bytes.** Tiny
   collision risk vs comparing each entry. **Edge:** a SHA-256
   collision would mean a stale render. Mitigation: SHA-256
   collisions are not a credible failure mode for app
   correctness.

---

## Out-of-scope (explicit)

- **Multi-tz scheduling for common-time** (deferred to v2 per D.10).
- **Aligned sub-window emission** in common-time (deferred per
  RV-E.5 commentary).
- **CalDAV-side resolver semantics** — out of scope per D.16; v1
  exports plain iCal which third-party clients resolve themselves.
- **Editing the resolver's primary/secondary choice** at the slot
  level (e.g. "show this work event over the vacation just for
  today") — v2 feature; current workaround is per-occurrence
  exception override on the recurrence rule.

---

## Performance audit (RV against D.21)

| Budget (D.21) | RV mechanism | Margin |
|---|---|---|
| Render month with 200 events: < 200ms cold | Day-indexed Room cache (D.5) + range-clipped RRULE iter (RV-B.9) + single active-set eval (RV-A.6) + linear slot construction (RV-C.1) | Comfortable; expected 50–100ms cold on Pixel 6a. |
| Render same: < 50ms cached | RenderCache LRU (RV-F.2) + snapshot digest equality (RV-F.1) | Comfortable; cache hit is `Map<Key, Schedule>` lookup. |
| Common-time finder, 5 repos × 30 days, < 800ms | Sweep-line subtraction is O((events + windows) log) (RV-E.3) | Comfortable; expected 300–500ms with 1000 total events. |
| Cold-start to schedule day view: < 600ms | Resolver is sub-50ms once Room is loaded; the budget is mostly Room hydration. |  |

---

## Cross-references back to main.md

- RV-A → main.md `E.1` (active-set evaluator).
- RV-B → main.md `E.2` (recurrence materializer).
- RV-C → main.md `E.3, E.4` (overlay + render pipeline).
- RV-D → main.md `E.4`, `G.1`–`G.5`, `H.1`–`H.2` (view adapters,
  Today task combiner).
- RV-E → main.md `E.5`, `N.1`–`N.3` (common-time finder + UI).
- RV-F → main.md `E.6` (cache).
- RV-G → main.md `V.3, V.4` (performance budgets verified).

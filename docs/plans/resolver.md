# strictlykeptboy — resolver + common-time finder

## Status: ✅ DECIDED — ready for implementation.

Phase E (Round 1) + Phase AA / BB / Y (Round 2) + Phase OO / TT (Round 3) + Phase XX / YY (Round 4) + Phase BBB (Round 5) resolver-side mechanics are fully specified. Every phase below carries sub-step checkboxes; every previously-open question has been locked with an inline **Decision** + **Rationale**. Subagents may implement any RV-* phase without further user input.

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
| RV-H — multi-timezone semantics | AA.1–AA.5 | D.27, D.6, D.25 |
| RV-I — multi-tz common-time finder | AA.4, AA.7, N | D.10, D.27 |
| RV-J — weather overlay (non-busy layer) | BB.1, BB.3–BB.7 | D.28 |
| RV-K — Round 2 out-of-scope housekeeping | (cross-cuts) | — |
| RV-L — cross-repo state-file overlay | OO | D.44 |
| RV-M — multi-repo priority resolution | TT | D.49, D.5 |
| RV-N — source-repo-id matching + dedup | OO + TT | D.51 |
| RV-O — Round 3 out-of-scope housekeeping | (cross-cuts) | — |
| RV-R — inverted-default completion + cross-repo feedback overlay | XX + YY | D.70, D.71, D.72, D.73 |
| RV-P — supersedence pass | BBB | D.75, D.76, D.77, D.78 |
| RV-Q — off-schedule detection | BBB | D.80, D.81 |

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

# Round 2 — extensions (Phases RV-H through RV-K)

These phases extend the Round 1 resolver design with multi-timezone
semantics, a multi-tz common-time finder, a non-busy weather data
layer, and a surgical update to the out-of-scope section. They
correspond to **Phase AA** (multi-tz), **Phase BB** (weather), and
**Phase Y** (CalDAV — only the resolver-side semantics; the pull
mechanics live in `sync-engine.md` SE-Q) in `main.md`.

Locked-decision references: D.25 (CalDAV bridge), D.27 (multi-tz
first-class), D.28 (weather overlay).

**Promotion summary (Round 1 → Round 2):**

| Concern | Round 1 status | Round 2 status |
|---|---|---|
| Per-entity tz | Resolver respects file, UI does not expose | UI exposes per-event + per-recurrence + repo-default tz (D.27) |
| Multi-tz common-time | Out-of-scope (deferred v2) | **In-scope** — RV-I formalizes algorithm |
| CalDAV resolver semantics | Out-of-scope per D.16 | **In-scope** as additional active calendars (D.25) — pull mechanics in SE-Q, resolver treats mirror calendars identically to native (RV-H tz logic applies) |
| Weather overlay | Not modeled | **New** non-busy data layer (RV-J) — does not affect active-set or busy-set |
| Aligned sub-window emission in common-time | Out-of-scope | Still deferred (RV-K) |
| Per-slot UI override of primary | Out-of-scope | Still deferred (RV-K) |

---

## Phase RV-H — Multi-timezone semantics (D.27)

> main.md → AA.1, AA.2, AA.3, AA.5. decisions.md → D.27, also D.6
> (recurrence tz) and D.25 (CalDAV mirror calendars participate).

**Goal:** make the entire RV-A → RV-B → RV-C → RV-D pipeline
tz-aware at every step, with crisp resolution rules and DST behavior
documented and tested.

### RV-H tz-resolution order (locked)

For any field, the tz used by the resolver is the **first non-null**
in this order:

1. `event.tz_id` (or `recurrence.tz_id` for recurring events; for
   exceptions, the exception's tz overrides only if explicitly set,
   otherwise inherits from the rule).
2. `calendar.default_tz` (set in `calendar.toml`; optional).
3. `repo.default_tz` (set in `repo.toml`; optional; defaults to
   device tz at scaffold time per D.27).
4. **Device tz** (`ZoneId.systemDefault()` at the resolver-call
   instant).

Each field has its own resolution evaluation — there is no single
"event tz." Concretely:

- `event.start` / `event.end` are interpreted in the event's resolved
  tz (the "**event tz**").
- `active_windows` / `active_hours` on a calendar are interpreted in
  the calendar's resolved tz (the "**calendar tz**"), which is
  `calendar.default_tz || repo.default_tz || device-tz`. Note that
  `event.tz_id` does NOT participate here — the calendar's
  activeness is a property of the calendar, not of the events on it.
- Recurrence expansion (RRULE iteration) uses the recurrence's
  resolved tz (the "**rule tz**") for DST math, per D.6.
- The renderer converts every resolved instance to the **render tz**
  before slot construction. The render tz comes from the UI mode (see
  RV-H.6).

**Decision (calendar tz ≠ event tz, deliberately):** a "work calendar
active 09:00–17:00 in Europe/Berlin" should activate based on Berlin's
clock regardless of where the events on it are scheduled. The most
common case is a user travelling to NYC: their Berlin work calendar's
9–17 active window should still cover the Berlin morning (3am NYC),
because that's when their colleagues are working and emails are
arriving. Rationale: a calendar models *whose schedule it represents*,
not where individual events sit.

**Corollary:** if a user actually wants "active when I personally am
in work hours wherever I am," they set `calendar.default_tz` empty
(falls through to device-tz, which roams with the phone).

### RV-H active-set in calendar tz

Restating RV-A.2 with the multi-tz lens:

```kotlin
fun isActive(c: CalendarMeta, at: ZonedDateTime): Boolean {
    if (!c.activeToggle) return false
    val calTz = c.tzId  // resolved per chain: calendar.default_tz || repo || device
    val local = at.withZoneSameInstant(calTz)
    // ...rest identical to RV-A.2...
}
```

No change to the predicate body — RV-A already passed `c.tzId` through.
What's new in RV-H is **the chain that produces `c.tzId`** (RV-H.1
below) and the **explicit documentation** that events do not influence
calendar activeness.

### RV-H recurrence materialization in rule tz

Restating RV-B with the multi-tz lens:

- lib-recur iterates in the rule tz (`rec.tzId`), which is
  `recurrence.tz_id || calendar.default_tz || repo.default_tz ||
  device-tz` at file-load time.
- Each emitted `start: ZonedDateTime` is in the rule tz.
- For exceptions: the `occurrenceDate` index key is computed in the
  **rule tz**, not the event tz, not the render tz. (An override
  exception's own `tz_id`, if set, only affects the override's
  start time, not which base occurrence it matches.)
- DST in lib-recur: spring-forward gap → lib-recur shifts the
  occurrence forward by the gap (standard behavior); fall-back overlap
  → lib-recur picks the first occurrence (standard). Both are
  documented in RV-B.7 already; the multi-tz extension does not
  change this.

### RV-H render-tz conversion

```kotlin
data class RenderTz(val zone: ZoneId, val origin: RenderTzOrigin)

enum class RenderTzOrigin { Device, Pinned, Participant }

fun toRenderTz(inst: ResolvedInstance, renderTz: RenderTz): ResolvedInstance =
    inst.copy(
        start = inst.start.withZoneSameInstant(renderTz.zone),
        end   = inst.end.withZoneSameInstant(renderTz.zone),
    )
```

Renderer converts at the **slot-construction input** boundary: every
instance for the date range is converted to render tz *before* the
sweep that finds slot boundaries. This is the only sane way to
construct slots — boundaries are `Instant`-equivalent regardless of
tz, but `.toLocalDate()` for "which day cell does this belong to"
must use render tz. Rationale: a 23:00 Berlin event is "today"
to a Berlin viewer and "today" to an NYC viewer's afternoon — the
day-cell assignment is render-tz-dependent.

### RV-H DST transition handling (render-side)

Beyond lib-recur's RRULE-side DST handling (RV-B.7), the renderer
also needs to handle DST in the **render tz** for visual continuity:

```kotlin
fun checkRenderTzDstContinuity(
    slots: List<RenderedSlot>,
    renderTz: ZoneId,
    range: ClosedRange<LocalDate>,
): List<RenderAnnotation> {
    val rules = renderTz.rules
    val annotations = mutableListOf<RenderAnnotation>()
    var cursor = range.start.atStartOfDay(renderTz).toInstant()
    val end = range.endInclusive.plusDays(1).atStartOfDay(renderTz).toInstant()
    while (cursor < end) {
        val next = rules.nextTransition(cursor) ?: break
        if (next.instant >= end) break
        annotations += RenderAnnotation.DstTransition(
            at = next.instant.atZone(renderTz),
            kind = if (next.duration.isNegative) DstKind.FallBack else DstKind.SpringForward,
            magnitude = next.duration.abs(),
        )
        cursor = next.instant.plusSeconds(1)
    }
    return annotations
}
```

`RenderAnnotation.DstTransition` decorates the day-view timeline with
a thin band ("DST → spring forward 1h" or "DST ← fall back 1h"), so
no event silently teleports or duplicates in the user's view. The
data is part of the `RenderedSchedule.annotations: List<RenderAnnotation>`
output channel (RV-H.5).

### RV-H all-day events across tz

An `isAllDay = true` event has start = 00:00 calendar-tz, end =
24:00 calendar-tz. When rendered in a different tz:

- Berlin all-day event rendered in NYC: 00:00 Berlin == 18:00 prev-
  day NYC; 24:00 Berlin == 18:00 same-day NYC. **The event spans two
  NYC day cells.**
- Resolver emits TWO `RenderedSlot`s with `isAllDayContinuation = true`
  on the second cell, and the Day/Week/Month adapters render the
  banded continuation. The single underlying `ResolvedInstance.id`
  is preserved, so tap-to-edit goes to the same file.

**Decision (all-day rendering tz):** the *source-of-truth tz* for an
all-day event is the **calendar's** tz, not the event's `tz_id`. An
all-day event is conceptually "the whole day on this calendar," and
the calendar is the entity with a stable timezone. Per-event `tz_id`
on an all-day event is *ignored* for rendering purposes (it remains
in the file for future use). Rationale: a "vacation day" on a Berlin
work calendar means "the whole Berlin-day," not "the whole event-tz-
day"; otherwise re-pinning the event tz would shift which day cell
holds the vacation.

### RV-H pinned-tz per-event

`event.pin_render_tz: Boolean` (optional, default false). When true,
the day/week/month view shows that event in **its** tz regardless of
the user's selected render tz, with a small overlay label "in
Europe/Berlin." Rationale: travel events ("flight departing CET")
should not silently shift to the destination's tz when the user
arrives.

Implementation: in the renderer's pre-conversion step,
`pin_render_tz == true` skips `toRenderTz` for that instance and
keeps its native tz; the adapter labels the chip.

### RV-H test corpus (≥ 10 scenarios)

Locked test fixtures for RV-H. All live under
`core/resolver/src/test/.../tz/`:

1. **Berlin→NYC same-day event:** event at 09:00 Europe/Berlin on
   2026-06-15, rendered in America/New_York. Expected slot: 03:00 NYC.
2. **Sydney→London evening meeting:** event at 19:00 Australia/Sydney
   on 2026-06-15, rendered in Europe/London. Expected: 10:00 London
   (during BST; AEST is UTC+10, BST is UTC+1).
3. **DST fall-back overlap (US):** 2026-11-01 01:30 America/New_York
   appears twice (EDT then EST). An event scheduled at 01:30 EDT
   renders at the first 01:30; an event at 01:30 EST renders at the
   second 01:30. Day view shows both, separated by the DST annotation
   band.
4. **DST spring-forward gap (US):** 2026-03-08 02:30 America/New_York
   does not exist. lib-recur shifts a daily 02:30 event to 03:30
   on that date only; resolver renders at 03:30 with annotation.
5. **Ambiguous local 02:30 during fall-back:** a one-off file with
   `start = "2026-11-01T01:30:00"` in `America/New_York` is
   ambiguous in source. Decision: parser picks the *first* occurrence
   (EDT) per `java.time.ZonedDateTime.ofLocal(..., preferredOffset)`
   with `preferredOffset = null` (java.time default is "earlier
   offset"); validator emits a warning.
6. **Zero-duration event at DST transition:** point event at
   2026-11-01T01:30 EDT does not duplicate; the second 01:30 EST
   pass has no instance. Test asserts exactly one instance.
7. **All-day event straddling render-tz days:** Berlin all-day on
   2026-06-15 rendered in NYC → two NYC slots tagged
   `isAllDayContinuation`.
8. **Active-hours in calendar tz vs device tz:** Berlin calendar
   active 09:00–17:00; device in `America/New_York`. Query at NYC
   03:30 == Berlin 09:30 → calendar **active**. Query at NYC 13:30
   == Berlin 19:30 → calendar **not active**.
9. **Pinned-tz event:** event in Europe/Berlin with
   `pin_render_tz = true`, render tz = America/New_York. Chip shows
   Berlin time + "in Europe/Berlin" label; not converted.
10. **Recurrence tz ≠ calendar tz:** RRULE in Asia/Tokyo
    `FREQ=WEEKLY;BYDAY=FR` on a calendar in Europe/Berlin. The Friday
    determination is in Tokyo time; an instance at 23:00 JST Friday
    is 16:00 CEST Friday (still Friday in Berlin) — but an instance
    at 02:00 JST Friday is 19:00 CET Thursday (Thursday in Berlin).
    Both render correctly under their respective Tokyo Friday
    occurrences; the calendar-tz only affects active-hour evaluation,
    not which day the recurrence fires.
11. **CalDAV mirror calendar tz inheritance:** a pull-only mirror of
    a Google Calendar whose server tz is `America/Los_Angeles` lands
    in the repo with `calendar.default_tz = America/Los_Angeles`.
    RV-H tz-resolution chain treats this identically to a native
    calendar with that default. Per D.25, mirror calendars are
    not special-cased in the resolver.
12. **Per-event tz_id override on a non-pinned event:** event tz set
    to Asia/Tokyo on a Europe/Berlin calendar, render in
    America/New_York. The event's `start/end` are interpreted in
    Tokyo, then converted to NYC for rendering. Calendar activeness
    is still evaluated in Berlin tz.

### RV-H phase steps

- [ ] **RV-H.1** Implement tz-resolution chain: `resolveTz(event, calendar, repo): ZoneId` and analogous `resolveTzForRecurrence`, `resolveTzForCalendar`. Pure function; covered by unit tests for each fall-through level.
- [ ] **RV-H.2** Update `EventLoader` / `RecurrenceLoader` / `CalendarLoader` (file-store → resolver bridge) to populate the resolved `tzId` field on `CalendarMeta`, `RecurrenceFile`, `MaterializedEvent` per the chain at load time.
- [ ] **RV-H.3** Update `EventFields` schema in `data-model.md` cross-link: add optional `tz_id` and `pin_render_tz` to event frontmatter (the schema work itself is DM-L; RV-H consumes it).
- [ ] **RV-H.4** Renderer pre-step: convert every `ResolvedInstance` to render tz (`toRenderTz`) **except** those with `pin_render_tz = true`. Document at top of `Renderer.render` that input may be in mixed tz; output is render-tz-normalized except for pinned.
- [ ] **RV-H.5** Add `RenderedSchedule.annotations: List<RenderAnnotation>` channel; populate with `DstTransition` entries via `checkRenderTzDstContinuity`.
- [ ] **RV-H.6** Add `RenderTz` parameter to view-mode adapters (RV-D): adapters take render tz from the UI ViewModel (UI-V's display-tz toggle). Default = device tz.
- [ ] **RV-H.7** All-day continuation handling: when a non-pinned all-day event's calendar-tz day spans two render-tz days, emit two slots with `isAllDayContinuation` flag on the second.
- [ ] **RV-H.8** Update `RenderCacheKey` (RV-F.1) to include `renderTzId` and `pinnedEventTzDigest` (a tiny digest of which events are pinned). Without this, switching the display tz would serve stale cached schedules.
- [ ] **RV-H.9** Ten-scenario test corpus (RV-H test corpus above), each as an explicit fixture under `core/resolver/src/test/.../tz/Scenario01_BerlinNyc.kt` … `Scenario12_*.kt`.
- [ ] **RV-H.10** Property test: round-trip tz conversion of any `ResolvedInstance` through `toRenderTz` and back to source tz returns an instance whose `Instant` is exactly equal.
- [ ] **RV-H.11** Performance: tz-conversion adds ≤ 5% overhead vs a single-tz render on the RV-G L4 200-event-month fixture. Java's `ZoneId.getRules()` is cached so this is a non-issue, but assert it.
- [ ] **RV-H.12** Update `RV-A` worked example commentary to reference RV-H tz-resolution chain (no behavior change; just a cross-link in code comments).

---

## Phase RV-I — Multi-timezone common-time finder (D.27)

> main.md → AA.4, AA.7, and N (common-time UI). decisions.md → D.10,
> D.27.

**Goal:** extend RV-E's common-time finder so each participant is
evaluated in **their own** tz, then results are surfaced in the
requesting user's tz.

### RV-I new inputs

```kotlin
data class Participant(
    val id: ParticipantId,                 // stable; for ranking + result label
    val displayName: String,
    val tzId: ZoneId,
    val workingHours: List<HourRange>,     // in their tz; empty = 24/7
    val preferredTimeOfDay: ClosedRange<LocalTime>?, // for ranking
    val busySetSource: BusySetSource,      // local repo, CalDAV mirror, or static iCal feed
)

data class MultiTzCommonTimeQuery(
    val participants: List<Participant>,   // includes the requester
    val requesterId: ParticipantId,        // results displayed in their tz
    val range: ClosedRange<LocalDate>,
    val duration: Duration,
    val topK: Int = 20,
    val nearFitsIfNoExact: Boolean = true, // fall back to one-hour-over windows
)

data class MultiTzFreeSlot(
    val startUtc: Instant,
    val endUtc: Instant,
    val perParticipantLocal: Map<ParticipantId, LocalDateTimeRange>,
    val isNearFit: Boolean,                // true iff this slot is outside someone's working hours by ≤ 1h
    val nearFitDetails: List<ParticipantId>,// who it's a near-fit for
    val rankScore: Double,
)
```

### RV-I algorithm

```kotlin
fun findMultiTzCommonTime(q: MultiTzCommonTimeQuery): List<MultiTzFreeSlot> {

    // 1. For each participant, compute their busy-set in THEIR tz, then
    //    project to UTC instants for set operations.
    val busyByParticipant: Map<ParticipantId, List<UtcInterval>> =
        q.participants.associate { p ->
            val localBusy = loadBusySet(p.busySetSource, q.range, p.tzId)
                              // returns intervals in p.tzId already
            p.id to localBusy.map { it.toUtc() }
        }

    // 2. For each participant, compute their available windows in their
    //    tz (working hours intersected with range), then project to UTC.
    val availableByParticipant: Map<ParticipantId, List<UtcInterval>> =
        q.participants.associate { p ->
            val windows = enumerateWorkingHours(p.workingHours, q.range, p.tzId)
            val free = subtractAll(windows, busyByParticipant[p.id]!!)
            p.id to free.map { it.toUtc() }
        }

    // 3. Intersect across all participants in UTC instant-space.
    var commonFree = availableByParticipant.values.first()
    for (other in availableByParticipant.values.drop(1)) {
        commonFree = intersectIntervals(commonFree, other)
    }

    // 4. Filter to slots ≥ duration.
    val exactFits = commonFree.filter { it.length >= q.duration }

    // 5. If empty and nearFitsIfNoExact, broaden each participant's
    //    working hours by ±1h on each side and recompute.
    val nearFits: List<MultiTzFreeSlot> =
        if (exactFits.isEmpty() && q.nearFitsIfNoExact)
            computeNearFits(q, busyByParticipant, broadenBy = Duration.ofHours(1))
        else emptyList()

    val all = exactFits.map { it.toSlot(q, isNearFit = false) } + nearFits

    // 6. Rank.
    return all.sortedByDescending { rank(it, q) }.take(q.topK)
}

fun rank(slot: MultiTzFreeSlot, q: MultiTzCommonTimeQuery): Double {
    val nInPreferredTod = q.participants.count { p ->
        val local = slot.perParticipantLocal[p.id]!!
        p.preferredTimeOfDay?.let { tod ->
            local.midpoint.toLocalTime() in tod
        } ?: true
    }
    val preferredFraction = nInPreferredTod.toDouble() / q.participants.size
    val earlinessBonus = 1.0 / (1.0 + daysFromRangeStart(slot, q.range))
    val nearFitPenalty = if (slot.isNearFit) 0.5 else 1.0
    return (preferredFraction * 10.0 + earlinessBonus) * nearFitPenalty
}
```

### RV-I edge cases (locked)

- **Zero participants:** undefined; caller must pass ≥ 1. The
  validator on the UI layer (UI-V multi-tz Together-tab) enforces.
- **One participant:** degenerates to RV-E single-tz finder; returns
  their free windows ranked normally. Cheap fast-path: skip the
  intersection.
- **Participants in same tz:** algorithm still runs correctly; the
  per-participant local-time mapping is identical. No special case.
- **Participant with no working hours:** treated as available 24/7
  in their tz. Common case: "asynchronous teammate" who flags any
  time as fine.
- **Working hours that cross midnight:** participant's working hours
  carry the same midnight-rollover semantics as `HourRange` in RV-A.
  Sliced into two intervals before UTC projection.
- **Participant in DST transition during range:** UTC projection
  handles this correctly; `working 09:00–17:00 their-tz` on the
  spring-forward day yields a 7h UTC window (not 8h) for that
  participant, intersecting tighter for others. Test scenario in
  RV-I.8.
- **No overlap at all + no near-fits:** return empty list with a
  caller-visible flag (`exhausted = true`). UI surfaces "no shared
  window across the date range; try widening the range or marking
  yourselves async."
- **Near-fit definition:** broaden each participant's working hours
  by ±1h, recompute intersection. Any resulting slot that overlaps
  at least one participant's broadened-but-not-original hours is
  flagged `isNearFit`, with `nearFitDetails` listing whose hours
  are violated.
- **Duration > any individual working window:** mirror RV-E rule —
  we don't span across multiple windows. Return `[]` (or near-fits
  if those join two windows after broadening by 1h, but typical
  participants have ≥ 8h working windows).
- **CalDAV mirror busy-set:** D.25 mirrors land as additional active
  calendars; their events flow into `loadBusySet` identically to
  native events. No special path.
- **Conflicting tz declarations:** if `participant.tzId` differs from
  the participant's own repo's `repo.default_tz`, the participant's
  declared `tzId` wins (RV-I overrides on a per-query basis).

### RV-I display rules

Results are surfaced **in the requester's tz** as primary text, with
a secondary line per participant:

```
Mon 2026-06-15  14:00–15:30  (your tz, Europe/Berlin)
  Alice: 08:00–09:30 America/New_York
  Boris: 22:00–23:30 Asia/Tokyo  ← outside preferred 09:00–18:00 ⚠
```

The ⚠ surfaces near-fits and out-of-preferred-time slots without
hiding them. UI taps "Schedule" → routes to quick-create event in
the requester's repo.

### RV-I worked example: 3-participant Berlin / NYC / Sydney

Query:
- Participants: A (Berlin, working 09:00–18:00), B (NYC, working
  08:00–17:00), C (Sydney, working 09:00–17:00).
- Range: 2026-06-15..2026-06-19 (Mon..Fri).
- Duration: 1h.
- Top 5.

Tz offsets in June 2026: Berlin CEST = UTC+2, NYC EDT = UTC-4,
Sydney AEST = UTC+10.

Working hours converted to UTC (per day, Mon..Fri):
- A: 09:00–18:00 CEST = 07:00–16:00 UTC.
- B: 08:00–17:00 EDT  = 12:00–21:00 UTC.
- C: 09:00–17:00 AEST = 23:00 prev-day–07:00 UTC.

Intersect A ∩ B in UTC: max(07:00, 12:00)..min(16:00, 21:00) =
12:00..16:00 UTC.

Intersect (A ∩ B) ∩ C: C is 23:00 prev-day..07:00 UTC, no overlap
with 12:00..16:00 UTC. **Exact-fit intersection is empty.**

Near-fits (broaden each by ±1h):
- A: 06:00..17:00 UTC.
- B: 11:00..22:00 UTC.
- C: 22:00 prev-day..08:00 UTC.

(A ∩ B) broadened = 11:00..17:00 UTC. ∩ C broadened: no overlap
(C ends at 08:00 UTC).

Broaden by ±2h (escalation step, not in the locked algorithm but
shown for illustration): still no overlap on weekdays. The algorithm
returns `exhausted = true`. The UI surfaces:

> No shared working-hours overlap was found across the requested
> range. Sydney is ~14h ahead of NYC; consider asynchronous
> communication or pick a participant to mark as async.

Counter-example with B replaced by D (London, 09:00–17:00 BST):
- A: 09:00–18:00 CEST = 07:00–16:00 UTC.
- D: 09:00–17:00 BST  = 08:00–16:00 UTC.
- C: 09:00–17:00 AEST = 23:00 prev-day–07:00 UTC.

(A ∩ D) = 08:00–16:00 UTC. ∩ C = no overlap exact. Near-fit broaden:
C → 22:00 prev-day–08:00 UTC; intersect with 08:00–16:00 UTC at the
single instant 08:00 — 0-length, no fit. Still exhausted.

Counter-example with C replaced by E (Mumbai, 09:00–17:00 IST):
- IST = UTC+5:30. E: 09:00–17:00 IST = 03:30–11:30 UTC.
- (A ∩ D) ∩ E = 08:00..11:30 UTC = **3.5 hours of overlap** —
  multiple 1h slots, ranked Mon first.

This worked-example explicitly shows that the algorithm correctly
returns no-fit when geometrically impossible (Sydney + Berlin +
NYC at standard working hours) and returns fits when a tz pair is
closer (Berlin + London + Mumbai).

### RV-I phase steps

- [ ] **RV-I.1** Define `Participant`, `MultiTzCommonTimeQuery`, `MultiTzFreeSlot`, `UtcInterval`, `LocalDateTimeRange` data classes in `core/resolver/multitz/`.
- [ ] **RV-I.2** Implement `BusySetSource` resolver: `LocalRepo(repoId)`, `CalDavMirror(mirrorId)`, `StaticICalFeed(url)`. Each returns intervals in a declared tz.
- [ ] **RV-I.3** Implement `enumerateWorkingHours(hours, range, tz)`: produces per-day intervals in tz, then projects to UTC.
- [ ] **RV-I.4** Implement `intersectIntervals(a, b)`: sweep-line intersection in UTC, O(n+m).
- [ ] **RV-I.5** Implement `computeNearFits` with the ±1h broadening rule.
- [ ] **RV-I.6** Implement `rank` per pseudo-code (preferred-tod fraction × 10 + earliness bonus, × near-fit penalty).
- [ ] **RV-I.7** Implement orchestration `findMultiTzCommonTime` per pseudo-code.
- [ ] **RV-I.8** Test fixtures: the 3-participant Berlin/NYC/Sydney worked example (exhausted case), the Berlin/London/Mumbai counter-example (3.5h overlap), single-participant fast-path, same-tz participants, DST-transition day for one participant, async participant (no working hours), mid-range tz change is **not** supported (a participant's tz is fixed per query — document explicitly).
- [ ] **RV-I.9** Performance test: 5 participants × 14 days × 100 busy-events each → < 800ms on JVM, < 1.5s on Pixel 6a. The hot path is sweep intersection, linear in events.
- [ ] **RV-I.10** Update `RenderCacheKey` (RV-F.1) for the multi-tz finder: query results cached by `(query-hash, busy-set-digest-per-participant)`. Invalidation on any participant's busy-set change.

---

## Phase RV-J — Weather overlay as a non-busy data layer (D.28)

> main.md → BB.1, BB.3, BB.4, BB.5, BB.6, BB.7. decisions.md → D.28.

**Goal:** introduce the **non-busy data layer** concept to the
resolver. Weather is the first such layer. Non-busy layers decorate
the rendered schedule but do NOT participate in active-set or
busy-set computation. They flow through a parallel output channel
on `RenderedSchedule`.

### RV-J non-busy layer concept (locked)

A non-busy layer is a function from `(location, time-range, render-tz)
→ Decoration`. Decorations are surfaced via `RenderedSchedule`'s
secondary output channels and consumed by view-mode adapters
alongside the primary slot list. They **never** influence:

- which calendars are active (RV-A unchanged),
- which events are busy (RV-E / RV-I subtraction unchanged),
- slot construction or priority (RV-C unchanged).

Future non-busy layers (sun/moon phases, holidays-as-decoration,
sport-event banners) can plug into the same channel without resolver
core changes.

### RV-J data shapes

```kotlin
data class WeatherProviderConfig(
    val location: GeoLocation,             // (lat, lon, optional name)
    val granularity: Duration = Duration.ofHours(3),
    val units: TemperatureUnits = Celsius,
)

data class HourForecast(
    val instant: Instant,                  // forecast start
    val tempC: Double,
    val conditionCode: WeatherConditionCode, // WMO code or normalized enum
    val precipMm: Double,
    val windKph: Double,
)

interface WeatherProvider {
    suspend fun forecastFor(
        config: WeatherProviderConfig,
        dateRange: ClosedRange<LocalDate>,
        renderTz: ZoneId,
    ): List<HourForecast>
}

data class WeatherStrip(
    val date: LocalDate,                   // in render tz
    val location: GeoLocation,
    val hours: List<HourForecast>,         // 8 entries/day at 3h granularity
)

data class RenderedSchedule(
    // ...existing fields...
    val annotations: List<RenderAnnotation>,
    val weatherStrips: List<WeatherStrip>, // one per rendered day, or empty
)
```

### RV-J integration into the pipeline

```
RV-A active-set  ──┐
RV-B recurrence  ──┤
                   ├─→ RV-C slot construction ──→ RV-D adapters
RV-J weather     ──┘                             ↑
   (parallel, non-blocking)                       (adapters consume strips)
```

The weather fetch runs **in parallel** with RV-A/B/C via a
`coroutineScope { val schedule = async { renderSlots() }; val weather
= async { fetchWeather() }; RenderedSchedule(slots = schedule.await(),
weatherStrips = weather.await()) }`. If the weather fetch fails
(offline, API down), `weatherStrips = emptyList()` and the rest of
the render is unaffected. Rationale: weather is decoration, never
load-bearing.

### RV-J cache + invalidation

- **Cache layer:** Room table `weather_cache` keyed by `(lat, lon,
  date, granularity, units)`. Stores `List<HourForecast>` serialized
  as JSON (compact; ≤ 8 rows × ~80 bytes ≈ 640 bytes per day per
  location).
- **Freshness:** 6h TTL. Stale entries returned with `isStale = true`
  flag and a background refresh kicked off.
- **Opportunistic refresh:** on `ConnectivityManager.NetworkCallback.
  onAvailable`, refresh any stale-or-expiring-within-1h entries for
  the currently-rendered range.
- **Eviction:** entries older than `today - 7d` are dropped on a
  weekly maintenance pass (sync engine SE-Q maintenance hook).

### RV-J multi-location handling

Multiple active calendars may declare different `weather_location`s
(via D.28: per-repo location, optional per-event override). The
resolver applies these rules:

1. **Per render day:** select the **repo default** location of the
   primary repo for the rendered date. The "primary repo" is the
   repo whose calendar provides the most active-hours coverage that
   day (deterministic tiebreak: lex on `repoId`). Rationale: a single
   weather strip per day avoids visual clutter; multiple strips
   create a "whose weather?" decision problem.
2. **Per event:** if an individual event has `event.location` (a
   geographic location, distinct from a generic location string),
   the event detail sheet shows that event's forecast as a chip in
   the detail UI (NOT in the day-view strip). This is a per-event
   embellishment, not a render-pipeline concern.
3. **Travel events:** when `event.location ≠ primary-repo location`
   AND the event is the slot's `primary`, the day-view strip is
   **dual-banded** for that slot's time range: above strip shows
   primary-repo weather, below strip shows event-location weather.
   This is the one exception to "one location per day," scoped
   tightly to the visual extent of the travel event.

### RV-J locked decisions

**Decision (non-busy layer abstraction):** the data-layer concept is
formalized as `interface RenderDecorationProvider { fun decorate(...):
RenderDecoration }`. Weather is the first implementation. Rationale:
v2 will add at least sun/moon, holidays-as-decoration, and
sport-event banners; bake the seam now.

**Decision (location is repo-default, not calendar-default):** D.28
puts the location at the repo level. Calendars within a repo share
the location. Rationale: weather follows the user, not the calendar
category; "work calendar in Berlin" vs "personal calendar in Berlin"
both want the same Berlin weather.

**Decision (Open-Meteo as v1 provider):** D.28 already locks
Open-Meteo. RV-J abstracts behind `WeatherProvider` so a future
NOAA/MetOffice provider can drop in.

**Decision (3h granularity in v1, hourly in v2):** day strip shows
8 entries per day (every 3h: 00, 03, 06, 09, 12, 15, 18, 21).
Hourly granularity (24 entries) was considered but adds visual noise
without proportional value at typical strip widths on phones.

**Decision (fail open):** all weather paths fail open. A failed
fetch produces `emptyList()`. No error chrome in the day view; a
single "weather unavailable" hint appears in the day-view overflow
menu.

### RV-J phase steps

- [ ] **RV-J.1** Define `WeatherProviderConfig`, `HourForecast`, `WeatherStrip` data classes; `WeatherProvider` interface; `RenderDecorationProvider` umbrella.
- [ ] **RV-J.2** Implement `OpenMeteoProvider` (Apache-2.0 client). API call: `https://api.open-meteo.com/v1/forecast?latitude=…&longitude=…&hourly=temperature_2m,weather_code,precipitation,wind_speed_10m&timezone=auto`. Parse JSON → `HourForecast`. Apply 3h decimation.
- [ ] **RV-J.3** Room cache: `WeatherCacheEntity(lat, lon, date, granularity, units, payloadJson, fetchedAt, isStale)`. DAO with `get(lat,lon,date)` and `put(...)` and `purgeOlderThan(date)`.
- [ ] **RV-J.4** Add `RenderedSchedule.weatherStrips` and `RenderedSchedule.annotations` channels (the latter shared with RV-H.5).
- [ ] **RV-J.5** Implement parallel fetch in `Resolver.renderRange`: `coroutineScope { async slots; async weather }`. Weather failure isolated.
- [ ] **RV-J.6** Day-view adapter (RV-D.1 extension): consume `weatherStrips` → top-of-timeline strip. Week/Month adapters: consume aggregated icons.
- [ ] **RV-J.7** Per-day primary-repo-location selection algorithm; tiebreak on `repoId`.
- [ ] **RV-J.8** Travel-event dual-band rendering for `event.location ≠ primary-repo location` slots.
- [ ] **RV-J.9** Opportunistic refresh on `NetworkCallback.onAvailable`; weekly purge.
- [ ] **RV-J.10** Update `RenderCacheKey` (RV-F.1) to include `weatherCacheDigest`: SHA-256 of the `(location, date) → fetchedAt` map for the rendered range. Without this, the rendered schedule could be served from cache with stale weather strips.
- [ ] **RV-J.11** Tests: provider success, provider failure → empty strips, multi-location day → repo-default chosen, travel-event dual-band, stale-then-fresh refresh path.
- [ ] **RV-J.12** Performance: weather fetch ≤ 300ms on cache hit (Room read), ≤ 1.5s on cold network (Open-Meteo p95). The parallel-with-slots dispatch hides this latency from the visible D.21 budget.

---

## Phase RV-K — Updated out-of-scope section

> Surgical update to the existing "Out-of-scope (explicit)" block
> below. New phases promoted in (CalDAV resolver semantics, multi-tz
> common-time) and a residual deferred-list curated.

### RV-K promotions (Round 1 → Round 2)

- ✅ **Multi-tz common-time** — promoted to v1 via RV-I. Was: "deferred
  to v2 per D.10." Now: D.10 + D.27 jointly define the v1 surface.
- ✅ **CalDAV-side resolver semantics** — promoted to v1 via RV-H. Was:
  "out of scope per D.16." Now: per D.25 the resolver treats CalDAV
  mirror calendars as additional active calendars, with tz inheritance
  per RV-H tz-resolution chain. The pull-side mechanics (PROPFIND,
  ETag, conflict, push) live in `sync-engine.md` SE-Q+; the resolver
  does NOT distinguish mirror vs native.

### RV-K still-deferred (locked)

- ⚠ **Aligned sub-window emission in common-time** — the v1 finder
  (RV-E single-tz and RV-I multi-tz) emits **maximal free intervals
  only**, not duration-aligned sub-windows. A 4h common window does
  not produce four 1h sub-window options ranked separately. Users
  pick the maximal block and adjust at the quick-create step
  (N.3 / UI-V). Rationale: simpler API; the ranking already surfaces
  the right blocks. Aligned-sub-window emission stays a v2 nicety.
- ⚠ **Per-slot UI override of primary choice** — the resolver's
  priority tiebreak (RV-C: priority desc, calendar.id lex asc) is
  deterministic. Users cannot say "for *this slot only*, show work
  over vacation"; they can only change calendar priority globally,
  or use a per-occurrence recurrence exception override. Rationale:
  per-slot UI overrides would require a new schema field and a new
  rendering branch, with no clear cleanup story. v2 candidate.

### RV-K phase steps

- [ ] **RV-K.1** Surgical edit to the "Out-of-scope (explicit)" block below: remove the promoted-to-v1 items, retain and re-word the still-deferred items with the rationale from RV-K above. (Tracked here as a single doc edit; will land alongside RV-H/I/J.)
- [ ] **RV-K.2** Update the `Cross-reference table` (top of this doc) to add rows for RV-H, RV-I, RV-J with their main.md → AA/BB/Y links and D.25/D.27/D.28 references.
- [ ] **RV-K.3** Update the `Tradeoffs resolved inline (summary)` table with the new RV-H/I/J tradeoffs (per-tz fall-through, near-fit broadening, repo-default location).
- [ ] **RV-K.4** Update the `Correctness-vs-performance calls` list with one new entry: the **weather fetch fails open**, accepting a small UX regression (no strip) for never blocking the render pipeline.
- [ ] **RV-K.5** Update `Performance audit` table with RV-I budget (5 participants × 14 days × 100 events < 800ms JVM) and RV-J budget (weather fetch ≤ 300ms cache hit, 1.5s cold, parallel-with-slots so not on the critical path).

---

# Round 3 — extensions (Phases RV-L through RV-O)

Round 3 introduces **shared schedules**: a receiver (sub, client,
student, employee, team-member) consumes a schedule authored in
someone else's repo without ever writing to it. Every per-receiver
interaction (done-checks, snoozes, private notes, reactions, mutes,
hides, priority overrides) lands as a **state file** in the receiver's
own repo (or `_local/state/` if no own repo exists yet). The resolver
merges state files with source entities at render time.

Locked-decision references for this round: `decisions.md` D.41
(shared schedules as a first-class entry path), D.43
(`references.toml`), **D.44** (cross-repo state files — the core
unlock), **D.49** (multi-repo priority resolution with modifiers),
**D.51** (source-repo-id stability + normalization).

Main-plan cross-link: this round corresponds to `main.md`
**Phase OO** (cross-repo state files) and **Phase TT** (multi-repo
priority resolution). RV-L is the resolver-side mechanics of OO; RV-M
is the resolver-side mechanics of TT; RV-N is the matching/dedup
algorithm shared by both; RV-O is the housekeeping update to the
out-of-scope block.

**Phase prefix continues:** `RV-`.

### Cross-reference table (Round 3 additions)

| Resolver phase | main.md phase | Decisions ref |
|---|---|---|
| RV-L — cross-repo state-file overlay | OO | D.44 |
| RV-M — multi-repo priority resolution | TT | D.49, D.5 |
| RV-N — source-repo-id matching + dedup | OO + TT | D.51 |
| RV-O — out-of-scope housekeeping | (cross-cuts) | — |

---

## Phase RV-L — Cross-repo state-file overlay (D.44)

> main.md → Phase OO. decisions.md → D.44.

**Goal:** the resolver merges state files from the receiver's own
repo (or `_local/state/`) with source entities from every configured
read-only / read-write repo at render time. The merge is a per-entity
overlay producing a **resolved instance** that already reflects the
receiver's done/snooze/note/reaction/mute/hide state and any local
priority-override.

State files NEVER mutate the source repo's bytes. They live in the
receiver's repo at `state/<source-repo-id>/<entity-id>.<state-kind>.toml`
(per D.44). The resolver indexes them once per HEAD change and looks
them up during render.

### RV-L inputs (per render snapshot)

The per-render snapshot extends the RV-A snapshot with state-file
data:

- The active-set of calendars/todolists from every configured repo
  (RV-A output).
- For each active calendar: its events, recurrences, exceptions
  (RV-B materializer inputs).
- **New:** the receiver's own-repo state files at
  `state/<source-repo-id>/*.toml`. The receiver's own repo is the
  one flagged `is_own = true` in app prefs (D.47).
- **New:** OR the pre-own-repo `_local/state/` files. These live
  outside any repo, in app-private storage at
  `~/.strictlykeptboy/local-state/<source-repo-id>/*.toml`. Used
  when the receiver has only read-only gifted repos and no own repo
  yet (the simplified-mode default at first launch — D.45, D.46).
  When the receiver later sets up their own repo (D.47), the migration
  step copies these files into the new own-repo's `state/` tree.

**Decision (single state source at a time):** exactly one of
`own-repo/state/` or `_local/state/` is consulted per render, never
both. Rationale: avoids "where did this done-mark go after I added
my own repo?" confusion. The migration in D.47 is a one-shot move
(not copy), so the union is also the right answer.

**Decision (state files for write-capable repos use their OWN
repo):** if the receiver also has read-write access to a calendar
they're consuming (e.g. they share a repo with their Dom and both
have write), the resolver STILL prefers a state file in the
receiver's own repo over a comment/edit in the source. Rationale:
state files are the universal mechanism; consistency wins over
"native" edits for done/snooze/etc. that are inherently per-user.

### RV-L state-file lookup index

A Room cache table populated during the indexer phase:

```kotlin
@Entity(
    tableName = "state_index",
    primaryKeys = ["sourceRepoId", "sourceEntityId", "stateKind"],
)
data class StateIndexEntity(
    val sourceRepoId: String,    // 16 hex chars, see RV-N
    val sourceEntityId: String,  // UUIDv7 of the source entity
    val stateKind: String,       // "done" | "snooze" | "note" | "reaction" | "mute" | "hide" | "priority-override"
    val filePath: String,        // relative to state-source root
    val occurrenceDate: String?, // for recurrence-instance scoping, ISO date; null = applies to non-recurring or to whole rule
    val payloadHash: String,     // sha256 of parsed payload for cache invalidation
    val mtimeMillis: Long,
)
```

DAO operations:

- `lookup(sourceRepoId, sourceEntityId, stateKind) → StateIndexEntity?` — O(1) via primary key.
- `lookupAllForEntity(sourceRepoId, sourceEntityId) → List<StateIndexEntity>` — covering index over the first two columns.
- `lookupRuleInstance(sourceRepoId, ruleId, isoDate, stateKind) → StateIndexEntity?` — covers the recurrence-instance done case (see edge case below).

The indexer scans the receiver's `state/` tree on every HEAD change
(or the `_local/state/` directory's mtime when no own repo). Scan
cost is bounded because state files live under one directory tree
per source repo.

### RV-L merge algorithm (Kotlin-flavored pseudo-code)

```kotlin
data class ResolvedInstance(
    val sourceEntityId: EntityId,
    val sourceRepoId: RepoId,
    val calendarId: CalendarId,
    val title: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val priority: Int,
    val done: Boolean = false,
    val doneAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val privateNote: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val muted: Boolean = false,
    // hide is not a flag — hidden entities are dropped entirely (null return)
)

enum class StateKind { DONE, SNOOZE, NOTE, REACTION, PRIORITY_OVERRIDE, MUTE, HIDE }

/**
 * Apply the receiver-side overlay to a single source entity. Returns
 * null if the entity is hidden by an active hide state file.
 *
 * Priority-override is NOT applied here; it's applied earlier, at
 * calendar-set construction time (see RV-L priority-override hoist).
 */
fun resolveWithState(
    sourceEntity: ResolvedInstance,
    stateFiles: Map<StateKind, StateFile>,
    now: Instant,
): ResolvedInstance? {
    var result = sourceEntity

    // HIDE first — short-circuit if hidden.
    stateFiles[StateKind.HIDE]?.let { hide ->
        if (hide.isActiveAt(now, sourceEntity.start)) return null
    }

    stateFiles[StateKind.DONE]?.let { done ->
        result = result.copy(done = true, doneAt = done.doneAt)
    }
    stateFiles[StateKind.SNOOZE]?.let { snooze ->
        if (snooze.until > now) {
            result = result.copy(snoozedUntil = snooze.until)
        }
    }
    stateFiles[StateKind.NOTE]?.let { note ->
        result = result.copy(privateNote = note.body)
    }
    stateFiles[StateKind.REACTION]?.let { reaction ->
        result = result.copy(
            reactions = result.reactions + Reaction(reaction.emoji, reaction.author),
        )
    }
    stateFiles[StateKind.MUTE]?.let { mute ->
        if (mute.isActiveAt(now)) {
            result = result.copy(muted = true)
        }
    }
    // PRIORITY_OVERRIDE handled in calendar-set construction, see below.
    return result
}

fun Resolver.renderRange(
    range: ClosedRange<LocalDate>,
    snapshot: Snapshot,
): RenderedSchedule {
    val activeSet = evaluateActiveSet(snapshot, range)           // RV-A
    val priced = applyCalendarPriorityOverrides(activeSet, snapshot.stateIndex)  // RV-L priority hoist + RV-M
    val materialized = materializeAll(priced, range)             // RV-B
    val now = Clock.systemDefaultZone().instant()
    val overlaid = materialized.mapNotNull { instance ->
        val stateFiles = snapshot.stateIndex.lookupAllForEntity(
            instance.sourceRepoId, instance.sourceEntityId,
        ).toStateMap(occurrenceDate = instance.start.toLocalDate())
        resolveWithState(instance, stateFiles, now)
    }
    return buildSlots(overlaid)                                   // RV-C
}
```

Lookup is O(1) per `(sourceRepoId, sourceEntityId, stateKind)`. For
a rendered month with 200 events × 7 state kinds, that's 1400 O(1)
lookups — well under the D.21 200ms budget.

### RV-L priority-override hoist

`state/<source-repo-id>/<calendar-id>.priority-override.toml` is
NOT a per-instance overlay; it overrides the **calendar's** priority
before recurrence expansion happens. Rationale: priority drives the
active-set evaluator (RV-A) and slot construction (RV-C) which run
BEFORE instances exist; hoisting the override to calendar-set
construction time preserves the existing render pipeline.

```kotlin
fun applyCalendarPriorityOverrides(
    activeSet: ActiveSet,
    stateIndex: StateIndexDao,
): ActiveSet = activeSet.map { calendar ->
    val override = stateIndex.lookup(
        sourceRepoId = calendar.repoId,
        sourceEntityId = calendar.id.value,     // calendars are entities too
        stateKind = "priority-override",
    )?.let { readPriorityOverride(it.filePath) }

    val effectivePriority = computeEffectivePriority(  // see RV-M
        calendar = calendar,
        overrideValue = override?.priority,
        repoModifier = calendar.repoModifier,
        repoPin = calendar.repoPin,
    )
    calendar.copy(effectivePriority = effectivePriority)
}
```

### RV-L cross-repo dedup

When two configured repos contain the same entity (e.g. the receiver
forked their Dom's repo and both have a "morning workout" event
sharing the same UUIDv7 — uncommon but possible per D.44 fork-and-edit
scenarios), the resolver must pick one to render.

**Decision (dedup priority):** prefer the read-write repo's version;
tiebreak by repo-add-order (older repo wins; same rule as RV-C's
priority tiebreak).

```kotlin
fun dedupeBySourceEntityId(
    instances: List<ResolvedInstance>,
    repoMeta: Map<RepoId, RepoMeta>,
): List<ResolvedInstance> = instances
    .groupBy { it.sourceEntityId }
    .mapValues { (_, group) ->
        group.minWithOrNull(
            compareBy<ResolvedInstance> { instance ->
                // read-write before read-only
                if (repoMeta[instance.sourceRepoId]?.mode == RepoMode.ReadWrite) 0 else 1
            }.thenBy { instance ->
                repoMeta[instance.sourceRepoId]?.addOrder ?: Int.MAX_VALUE
            },
        )!!
    }
    .values
    .toList()
```

The dedup runs AFTER state-file overlay so that a hidden-in-one-repo
twin doesn't accidentally survive via its sibling. Per UUIDv7, true
duplicates only arise from explicit forking; the algorithm degrades
gracefully (one wins; no double-render) when this rare case fires.

### RV-L edge cases (locked)

**Edge case: state file references a deleted upstream entity.**
Receiver marked a Dom-assigned task done; later the Dom deleted the
task. The state file now points at nothing.

- Resolver detects the dangling reference (lookup of the source
  entity returns null) during indexer scan.
- The state file is NOT auto-deleted from the receiver's repo — that
  would destroy history.
- Surface in a **debug-only orphaned-state tray** (Settings →
  Diagnostics → Orphaned state files). One row per orphan with
  `source_repo_id`, `source_entity_id`, `state_kind`, `mtime`.
- If `mtime` is older than **30 days**, fire a one-shot notification
  (channel `errors`, low importance): "N orphaned state references
  for repo <label> older than 30 days. Review and clean up?" Tapping
  opens the tray.
- Manual cleanup actions in the tray: "Delete this state file" (one
  row) / "Delete all orphaned for this repo" (bulk).

**Edge case: hide state file on a recurring task / rule.**
The receiver wants to stop seeing the Dom's "weigh-in" recurring task.

- A `state_kind = "hide"` file keyed on the **rule id** (not an
  instance date) hides **ALL future instances** of the rule.
- Optional `hidden_until = "2026-12-31"` field: hide expires at that
  date; instances on/after `hidden_until` render again.
- Optional `hide_past = false` (default): past instances continue to
  render unless `hide_past = true`. Rationale: hiding the future is
  the common case (don't show me this anymore); rewriting visible
  history is the rare case and requires explicit opt-in.
- The hide check (`isActiveAt`) in the algorithm above evaluates:
  - `instance.start >= hide.createdAt` (always hide forward from
    when the hide was added) OR `hide.hidePast`.
  - AND (`hide.hiddenUntil == null` OR `instance.start < hiddenUntil`).
- A hide keyed on a **specific instance date** (filename
  `<rule-id>.<yyyy-mm-dd>.hide.toml`) hides only that one instance
  — same scoping as a recurrence exception (D.6).

**Edge case: done state file on a recurring task instance.**
The receiver completes "Monday workout" for one Monday but not the
next.

- The state file is keyed on the **rule id + occurrence date**:
  filename `<rule-id>.<yyyy-mm-dd>.done.toml`.
- The state index records `occurrenceDate = "<yyyy-mm-dd>"`.
- The done state applies only to the matching `(rule, date)` pair.
- Other instances of the rule render undone.
- The state-map builder for an instance materialized at
  `ZonedDateTime(date, time)` queries
  `lookupRuleInstance(sourceRepoId, ruleId, date, DONE)` first; if
  null, falls back to `lookup(sourceRepoId, ruleId, DONE)` (which
  matches a rule-level done — rare but legal for "I'm done with this
  whole chore for all time, but don't hide it"). The rule-level form
  exists but is discouraged; the per-instance form is the norm.

**Edge case: snooze across midnight / DST.**
Snooze `until` is stored as an absolute `Instant` (ISO-8601 with
offset), not a local time. The merge compares `Instant` directly;
DST and tz changes do not shift the snooze.

**Edge case: mute on an event that recurs hourly.**
A `mute` state file at the rule level suppresses notifications for
every instance, but does NOT hide them from the schedule view. The
`muted = true` flag flows to the notification scheduler (NOTI doc),
not to the renderer.

**Edge case: state file with unknown `state_kind`.**
Forward-compat: future schema versions may add kinds (e.g.
`completed-by-someone-else`, `acknowledged`). v1 logs and ignores
unknown kinds. The file remains untouched in the receiver's repo.
The indexer's `state_kind` column accepts arbitrary strings.

### RV-L worked example

Rendering Monday 2026-05-11 in simplified mode. Receiver "sub" has
configured:

- **Dom-repo** (read-only, `priority_modifier = "high"`):
  - Event `01HZ-DENTIST` "Dentist @ 14:00" in calendar `Personal-Dom` (priority 500).
  - Event `01HZ-CHECKIN` "Evening check-in @ 21:00" in calendar `Daily-Dom` (priority 700).
  - Event `01HZ-COFFEE` "Coffee with friend @ 10:00" in calendar `Personal-Dom`.
  - Task `01HZ-WORKOUT-MON` "Monday workout" in todolist `Workouts-Dom`. (Done by sub.)
  - Calendar `Personal-Dom` has a priority-override state file dropping its effective priority to 300.
- **Own repo** (read-write, no modifier):
  - Event `01HZ-LUNCH` "Lunch with mom @ 12:30" in calendar `Personal-Mine`.
  - Event `01HZ-WALK` "Evening walk @ 19:00" in calendar `Personal-Mine`.
- **Own repo state tree:**
  - `state/<dom-repo-id>/01HZ-WORKOUT-MON.done.toml` — done at 07:30.
  - `state/<dom-repo-id>/Personal-Dom.priority-override.toml` — priority = 300.

Render walk-through:

1. **Active-set evaluator (RV-A)** returns `{Personal-Dom, Daily-Dom, Workouts-Dom, Personal-Mine}` for Monday.
2. **Priority-override hoist (RV-L)** rewrites `Personal-Dom`'s effective priority from 500 to 300. The other calendars keep their authored values.
3. **Repo modifier (RV-M)** applies `high = +200` to every Dom-repo calendar. Effective priorities:
   - `Personal-Dom`: 300 (override is final; modifier ignored — see RV-M order).
   - `Daily-Dom`: 700 + 200 = 900.
   - `Workouts-Dom`: (todolist priority, e.g. 500) + 200 = 700.
   - `Personal-Mine`: 500 (no modifier).
4. **Recurrence materializer (RV-B)** expands any RRULEs into instances. No recurrences in this example.
5. **Cross-repo dedup (RV-L)** runs; no duplicates.
6. **State overlay (RV-L)** per instance:
   - `01HZ-DENTIST` → no state files → unchanged.
   - `01HZ-CHECKIN` → no state files → unchanged.
   - `01HZ-COFFEE` → no state files → unchanged.
   - `01HZ-WORKOUT-MON` → `done` overlay → `done = true, doneAt = 2026-05-11T07:30+02:00`.
   - `01HZ-LUNCH`, `01HZ-WALK` → no state files → unchanged.
7. **Slot construction (RV-C)** lays them out. Within any overlapping slot, the highest effective-priority calendar's instance is `primary`.
8. **Renderer (RV-D)** draws Day view; `01HZ-WORKOUT-MON` shows with a checkmark and strikethrough.

Receiver never wrote to the Dom-repo. The Dom never sees the done
state. Tomorrow's `01HZ-WORKOUT-TUE` instance of the same recurrence
rule (if one existed) would render undone — done is per-instance.

### RV-L phase steps

- [ ] **RV-L.1** Define data classes: `StateFile`, `StateKind`, `Reaction`; extend `ResolvedInstance` with `done`, `doneAt`, `snoozedUntil`, `privateNote`, `reactions`, `muted` fields.
- [ ] **RV-L.2** Define `StateIndexEntity` Room schema + DAO with `lookup`, `lookupAllForEntity`, `lookupRuleInstance`, `lookupOrphaned` (for the orphan tray), `insert`, `delete`.
- [ ] **RV-L.3** Implement the state-source resolver: `own-repo/state/` if `is_own = true` configured, else `_local/state/`. Exactly one source per render snapshot.
- [ ] **RV-L.4** Indexer pass: scan state directory on HEAD-change (own repo) or directory-mtime change (`_local/state/`); populate `state_index`; emit invalidation event to RV-F.
- [ ] **RV-L.5** Implement `resolveWithState` per the pseudo-code; unit-test each StateKind branch.
- [ ] **RV-L.6** Implement `applyCalendarPriorityOverrides` (the priority-override hoist) called from `Resolver.renderRange` between RV-A and RV-B.
- [ ] **RV-L.7** Implement `dedupeBySourceEntityId` post-materialization; read-write-first, then add-order tiebreak; unit-test with synthetic fork scenario.
- [ ] **RV-L.8** Wire `RenderedSchedule` to flow the new fields (done, snoozedUntil, privateNote, reactions, muted) to view-mode adapters; UI consumes per `ui-spec.md` Phase UI-FF+.
- [ ] **RV-L.9** Orphan detection: indexer flags state files whose `(sourceRepoId, sourceEntityId)` doesn't resolve to any configured repo + entity; populate `state_index.orphaned = true`.
- [ ] **RV-L.10** 30-day orphan-age notification scheduler: on indexer pass, if any orphan's `mtime < now - 30d` and no notification fired this week, dispatch one (channel `errors`).
- [ ] **RV-L.11** Hide-rule edge case: implement `HideState.isActiveAt(now, instanceStart)` with `hidden_until`, `hide_past`, and `createdAt` semantics; unit-test forward-from-now, hidden-until expiry, per-instance hide vs rule-level hide.
- [ ] **RV-L.12** Done-on-recurrence edge case: filename routing `<rule-id>.<yyyy-mm-dd>.done.toml` → `occurrenceDate` column; state-map builder prefers per-instance over rule-level done; unit-test both forms.
- [ ] **RV-L.13** Migration on D.47 "switch to own repo" path: copy `_local/state/*` into the new own-repo's `state/` tree as one commit `migrate local state → repo state`; delete `_local/state/` only on commit success.
- [ ] **RV-L.14** Tests: full worked example (4 events, 1 done state file, 1 priority-override state file) — render once with state, once without state, diff the output and assert exactly the expected fields changed.
- [ ] **RV-L.15** Performance: render month with 200 events + 100 state files < 200ms cold on Pixel 6a (D.21). Hot cache (RV-F) < 50ms.

### RV-L locked decisions

**Decision (overlay vs in-place merge):** state files OVERLAY at
render time; they never get written back into the source repo (even
if write access exists). Rationale: the cross-repo state model only
works if "done" lives separately from "source." Conflating them
re-introduces merge conflicts on the source repo.

**Decision (state on calendars too, not just events/tasks):**
priority-override and mute can target a whole calendar (priority
override) or a whole todolist (mute notifications for this list).
`state_index` indexes calendars by their UUIDv7 the same way it
indexes events/tasks. No special-casing in the algorithm.

**Decision (one state file per (entity, kind), not append-log):**
re-doing a "done" rewrites the single `done.toml` file. Re-marking
mute extends the existing file. Rationale: keeps the one-entity-per-
file invariant; idempotent merges on git (latest writer wins).
Append-log was considered (one file per "done event" with
timestamps) but the merge story is identical and the file count
explodes.

---

## Phase RV-M — Multi-repo priority resolution (D.49)

> main.md → Phase TT. decisions.md → D.49, D.5.

**Goal:** extend RV-A's active-set evaluator (and RV-C's slot
primary/secondary tiebreak) with the multi-repo priority modifier
system. Calendars from a "high-priority" reference repo float above
calendars from a "low-priority" reference repo, with the receiver
retaining per-calendar override authority via state files and a
per-device "pin to top" toggle.

### RV-M priority stack

Five tiers, evaluated in this precise order at render time per
calendar:

1. **Local priority-override state file** (per-device, in the
   receiver's own repo or `_local/state/`):
   `state/<source-repo-id>/<calendar-id>.priority-override.toml`.
   If present → use its `priority` value as the final effective
   priority, clamped to `[1, 1000]`. SKIP tiers 2–4 entirely.
2. **Authored calendar priority** in the source repo's
   `calendar.toml`. The "source of truth" for the calendar's intent.
3. **Repo modifier** from the receiver's `references.toml`:
   - `high` → `+200`
   - `normal` → `0`
   - `low` → `-200`
   Applied uniformly to every calendar in the referenced repo. The
   receiver authors this in their own repo's `references.toml`.
4. **Repo-pin modifier** (per-device, NOT in the repo):
   - Pinned → `+500`
   - Not pinned → `0`
   Stored in app prefs (the receiver's device). Per D.49: "pin to top"
   adds a +500 modifier to the repo's reference, no per-calendar
   config needed.
5. **Default** if no calendar priority was authored: `500`.

After all tiers: **clamp to `[1, 1000]`**.

```kotlin
fun computeEffectivePriority(
    calendar: CalendarMeta,
    overrideValue: Int?,           // tier 1
    repoModifier: PriorityModifier,// tier 3, from references.toml
    repoPin: Boolean,              // tier 4, from app prefs
): Int {
    overrideValue?.let { return it.coerceIn(1, 1000) }   // tier 1 short-circuit

    val authored = calendar.authoredPriority ?: 500       // tier 2 / 5
    val repoBonus = when (repoModifier) {
        PriorityModifier.HIGH -> 200
        PriorityModifier.NORMAL -> 0
        PriorityModifier.LOW -> -200
    }
    val pinBonus = if (repoPin) 500 else 0
    return (authored + repoBonus + pinBonus).coerceIn(1, 1000)
}
```

### RV-M worked example

Receiver-sub has these configured:

- **Dom-repo** referenced with `priority_modifier = "high"` in sub's
  own-repo `references.toml`. Sub has also pinned Dom-repo to top in
  app prefs.
  - Calendar `Workouts-Dom` authored at priority 500.
  - Calendar `Daily-Dom` authored at priority 700.
- **Soccer-club-repo** referenced with `priority_modifier = "normal"`,
  not pinned.
  - Calendar `Soccer-Practice` authored at priority 400.
- **Own repo**, no modifier (own-repo is always treated as
  `normal` / not-pinned implicitly; the modifier system is for
  references).
  - Calendar `Personal-Mine` authored at priority 500.

Effective priorities (no local overrides):

| Calendar | Authored | Repo modifier | Pin | Effective |
|---|---|---|---|---|
| `Workouts-Dom` | 500 | +200 | +500 | clamp(1200) = **1000** |
| `Daily-Dom` | 700 | +200 | +500 | clamp(1400) = **1000** |
| `Soccer-Practice` | 400 | 0 | 0 | clamp(400) = **400** |
| `Personal-Mine` | 500 | 0 | 0 | clamp(500) = **500** |

Now the receiver adds a priority-override state file
`state/<dom-repo-id>/Workouts-Dom.priority-override.toml` with
`priority = 100` (they want workouts buried so they're not the slot
primary every morning):

| Calendar | Override | Final effective |
|---|---|---|
| `Workouts-Dom` | 100 | **100** (override wins; repo modifier + pin ignored) |

The other calendars are unaffected.

### RV-M tiebreak on equal effective priority

When two calendars produce the same effective priority for a slot
(e.g. Dom-Daily and Soccer-Practice both end up at 1000 after pin
ceiling), RV-C's tiebreak chain extends:

1. **Effective priority desc** (RV-M output).
2. **Repo-add-order asc** (older repo wins).
3. **Calendar id lex asc** (RV-C original tiebreak, now the third
   key).

Rationale: repo-add-order is deterministic + stable across devices
that share the same own-repo (because `references.toml` records
references in insertion order). Calendar-id-lex remains the final
backstop for two calendars in the same repo.

### RV-M visual indicator (resolver outputs for UI)

The calendar list in Settings → Calendars (per `ui-spec.md`
UI-FF+) shows both numbers when they differ. The resolver exposes a
projection for that UI:

```kotlin
data class CalendarPriorityProjection(
    val calendarId: CalendarId,
    val authoredPriority: Int?,        // tier 2; null if defaulted
    val repoModifier: PriorityModifier,// tier 3
    val repoPin: Boolean,              // tier 4
    val overrideValue: Int?,           // tier 1
    val effectivePriority: Int,        // final, clamped
) {
    val showsDelta: Boolean = overrideValue != null
        || (authoredPriority ?: 500) != effectivePriority
}
```

UI consumes `CalendarPriorityProjection` from the resolver; resolver
does not own UI. The projection guarantees the UI never has to
re-derive the stack — single source of truth.

### RV-M phase steps

- [ ] **RV-M.1** Define `PriorityModifier` enum (`HIGH`, `NORMAL`, `LOW`); parse from `references.toml` per-reference `priority_modifier` field.
- [ ] **RV-M.2** Define `CalendarMeta.repoModifier` (loaded from references.toml) and `CalendarMeta.repoPin` (loaded from app prefs) and `CalendarMeta.authoredPriority` (loaded from source calendar.toml).
- [ ] **RV-M.3** Implement `computeEffectivePriority` per the pseudo-code; unit-test all 5 tiers + clamping + override short-circuit.
- [ ] **RV-M.4** Wire `applyCalendarPriorityOverrides` (RV-L.6) to call `computeEffectivePriority`; `ActiveSet` entries carry `effectivePriority` from here on.
- [ ] **RV-M.5** Extend RV-C slot tiebreak: `effectivePriority desc → repoAddOrder asc → calendarId lex asc`. Unit-test with synthetic equal-priority cross-repo overlap.
- [ ] **RV-M.6** Define `CalendarPriorityProjection`; expose via a resolver query function for Settings UI consumption.
- [ ] **RV-M.7** App-prefs schema: `repo_pin_<repoId> = true|false` (DataStore-Preferences). Toggle round-trips through the resolver via an invalidation hook (RV-F).
- [ ] **RV-M.8** Tests: receiver pins Dom-repo → all Dom calendars float to ≥700 effective; receiver overrides one → that one drops; un-pin → others fall back.
- [ ] **RV-M.9** Tests: clamping boundary cases — authored 900 + high (+200) + pin (+500) = 1600 → clamps to 1000; authored 100 + low (-200) = -100 → clamps to 1.
- [ ] **RV-M.10** Documentation: AGENTS.md (per data-model.md) gains a section on how Claude should reason about effective vs authored priority when creating calendars.

### RV-M locked decisions

**Decision (override is full short-circuit, not additive):** the
local priority-override state file's `priority` value is the FINAL
effective priority. Repo modifier and pin do not apply on top.
Rationale: overrides are "I know better than the stack" — the user
intent is "this calendar's priority is exactly N now, regardless of
where it came from." Additive overrides were considered and
rejected (less predictable; user has to do mental math).

**Decision (own-repo is implicitly `normal` + unpinned):** the
receiver's own repo has no `references.toml` entry pointing at
itself; thus no modifier or pin. Calendars in the own repo use
authored priority directly (tiers 2/5). Rationale: own-repo is the
default frame of reference; modifying it relative to itself is
nonsensical.

**Decision (pin is per-device, modifier is per-repo-stored):**
modifier lives in the receiver's `references.toml` (so it survives
device-swap when the receiver shares their own repo across devices).
Pin lives in app prefs (so device-specific contexts — one device is
"focus on Dom" mode, another is "work mode" — don't bleed into
shared state). Same precedent as D.34 cross-device snooze sync
being opt-in.

**Decision (no UI per-slot priority override):** RV-K already
locked "per-slot UI override of primary choice" as deferred. RV-M
keeps that lock — the priority-override state file is per-calendar,
not per-slot. Rationale: per-slot would need either a new state-kind
or instance-level priority overrides; both blow the schema budget.

---

## Phase RV-N — Source-repo-id matching + dedup (D.51)

> main.md → Phase OO + TT. decisions.md → D.51.

**Goal:** specify how the resolver USES the `source_repo_id` derived
per data-model.md DM-S (where the derivation rule is canonically
defined). RV-N covers: when the id is computed, how the resolver
matches it against configured repos, how the resolver handles
already-configured collisions, and how it handles rename detection.

### RV-N when the id is computed

- **At repo-add time**, in either flow:
  - GUI: Settings → Repos → "Add repo" → URL → app normalizes +
    hashes (per the rules below) → resulting id stored alongside the
    repo entry in the per-app repo registry.
  - Deep-link receive (D.42, D.46): same normalization + hashing on
    the URL extracted from the deep-link query parameter.
  - `skb accept <url>` (D.52): same.
- **At state-file load time** (indexer scan, RV-L.4):
  - The `source_repo_id` field is read from each state file's TOML
    frontmatter.
  - Matched against the in-memory map `{ id → configuredRepo }`.
  - On match: state file links to the configured repo's entity
    space; `state_index` row inserted normally.
  - On no match: state file is ORPHANED (RV-L.9). Indexer marks
    `state_index.orphaned = true`. The state file still lives in
    the repo (history-preserving); render pipeline ignores orphans.

### RV-N normalization (canonical, per D.51)

The receiver and sender MUST produce identical ids for the same
logical repo regardless of URL form. Normalization steps (apply in
order):

1. Trim leading + trailing whitespace.
2. **Lowercase** the whole URL.
3. Strip scheme prefixes by rewriting to a `host/path` form:
   - `https://` → strip.
   - `http://` → strip.
   - `git+ssh://` → strip.
   - `ssh://git@` → strip (becomes `host/owner/repo` form).
   - `git@host:owner/repo` → rewrite to `host/owner/repo`.
4. Strip trailing `/`.
5. Strip trailing `.git`.
6. Strip query string (everything from `?` onward).
7. Strip fragment (everything from `#` onward — this also strips
   the deep-link `#token=...` payload from id calculation, which is
   essential per D.42).
8. SHA-256 of the resulting UTF-8 bytes.
9. Take first **16 hex chars** of the digest. Lowercase hex.

```kotlin
fun sourceRepoIdOf(url: String): String {
    var s = url.trim().lowercase()
    // Strip schemes (order matters: longest prefix first).
    val schemePrefixes = listOf("https://", "http://", "git+ssh://", "ssh://git@")
    for (p in schemePrefixes) {
        if (s.startsWith(p)) { s = s.removePrefix(p); break }
    }
    // Rewrite git@host:owner/repo → host/owner/repo
    val gitAt = Regex("""^git@([^:]+):(.+)$""").matchEntire(s)
    if (gitAt != null) s = "${gitAt.groupValues[1]}/${gitAt.groupValues[2]}"
    // Strip query + fragment.
    s = s.substringBefore("?").substringBefore("#")
    // Strip trailing slash and .git.
    s = s.trimEnd('/')
    if (s.endsWith(".git")) s = s.dropLast(4)
    // Hash + truncate.
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.substring(0, 16)
}
```

### RV-N test corpus (locked unit tests for this function)

Each row asserts the input URL normalizes to the same final string
(column 2), and thus produces the same id (column 3 is illustrative;
real test asserts `sourceRepoIdOf(input) == sourceRepoIdOf(canonical)`).

| Input URL | Normalized | Same id as canonical? |
|---|---|---|
| `git@github.com:dom/master-schedule.git` | `github.com/dom/master-schedule` | canonical |
| `https://github.com/dom/master-schedule` | `github.com/dom/master-schedule` | ✅ same |
| `https://GitHub.com/Dom/master-schedule/` | `github.com/dom/master-schedule` | ✅ same |
| `https://github.com/dom/master-schedule.git?token=xyz#frag` | `github.com/dom/master-schedule` | ✅ same |
| `HTTPS://GITHUB.COM/dom/master-schedule.git` | `github.com/dom/master-schedule` | ✅ same |
| `ssh://git@github.com/dom/master-schedule.git` | `github.com/dom/master-schedule` | ✅ same |
| `git+ssh://git@github.com/dom/master-schedule.git` | `github.com/dom/master-schedule` | ✅ same |
| `  https://github.com/dom/master-schedule  ` (whitespace) | `github.com/dom/master-schedule` | ✅ same |
| `https://gitlab.com/dom/master-schedule` | `gitlab.com/dom/master-schedule` | ❌ DIFFERENT (host) |
| `https://github.com/dom/Master-Schedule` | `github.com/dom/master-schedule` | ✅ same (lowercase normalization — see locked decision below) |
| `https://github.com/dom2/master-schedule` | `github.com/dom2/master-schedule` | ❌ DIFFERENT (owner) |
| `https://github.com/dom/master-schedule-v2` | `github.com/dom/master-schedule-v2` | ❌ DIFFERENT (repo name) |

### RV-N already-configured collision

When a receiver tries to add a repo whose computed id matches an
already-configured repo:

- The add operation **fails** with code `EREPO_DUPLICATE`.
- The error surface shows the existing entry: label, URL,
  added-when, mode (read-only / read-write / pull-only).
- The receiver gets two buttons: "Open existing" (navigates to
  Settings → Repos → that entry) and "Cancel."
- Rationale: silent dedup would mask the user's "I want a second
  copy" intent (which is wrong; there's only ever one logical repo
  per id) AND silently inherit the existing entry's auth /
  modifier, which could surprise.

For deep-link receive (D.42), the same collision check fires before
the clone runs. The bootstrap UX (D.46) handles this by skipping the
clone and dropping the user into the existing entry's view.

```kotlin
fun addRepo(url: String, ...): Result<RepoEntry> {
    val id = sourceRepoIdOf(url)
    repoRegistry.findById(id)?.let { existing ->
        return Result.failure(RepoDuplicateError(existing))
    }
    // ... proceed with clone ...
}
```

### RV-N rename detection

When sync against a previously-working repo returns **HTTP 404 / git
"repository not found"** (and the repo was previously cloneable —
i.e. not a fresh credential failure), the resolver flags the repo as
`moved?`:

- Repo registry entry gains `status = MOVED_QUESTIONMARK`,
  `lastSeenAt = <timestamp>`.
- In Settings → Repos, the entry shows a "Moved?" pill with a tap
  action: "The repo at this URL is no longer reachable. Has it
  moved?"
- User flow:
  - Enter new URL → app validates clone access → if the **new URL's
    `source_repo_id` matches the old URL's `source_repo_id`**, no
    rebind needed; just update the URL.
  - If the new URL's id is DIFFERENT (e.g. owner renamed → owner is
    part of the URL → different id), the receiver runs:
    `skb state rebind <old-id> <new-url>`. This:
    1. Computes the new id from the new URL.
    2. Renames `state/<old-id>/` to `state/<new-id>/` in the
       receiver's own repo. One commit
       `rebind state from <old-id-prefix> to <new-id-prefix>`.
    3. Rewrites every state file's `source_repo_url` frontmatter
       field (not the entity id refs — those stay).
    4. Updates the in-memory configured-repo registry.
- The receiver's own repo's `references.toml` entry for the moved
  repo also updates its `url` field to the new URL. Same commit.
- The old `source_repo_id` is NOT carried as an alias — after
  rebind, the new id is canonical, full stop. Rationale: aliases
  multiply the matching surface and create ambiguity if the old URL
  is ever re-issued by the provider.

```kotlin
fun rebindRepo(oldId: RepoId, newUrl: String): Result<Unit> {
    val newId = sourceRepoIdOf(newUrl)
    if (newId == oldId) {
        // Same id; just refresh the URL field.
        return refreshUrlOnly(oldId, newUrl)
    }
    repoRegistry.findById(newId)?.let { existing ->
        return Result.failure(RepoDuplicateError(existing))
    }
    fileSystem.move("state/$oldId/", "state/$newId/")
    rewriteStateFileUrls(newId, newUrl)
    referencesToml.updateUrl(oldId, newUrl)
    repoRegistry.rebind(oldId, newId, newUrl)
    git.commit("rebind state from ${oldId.prefix} to ${newId.prefix}")
    return Result.success(Unit)
}
```

### RV-N phase steps

- [ ] **RV-N.1** Implement `sourceRepoIdOf(url)` per the pseudo-code with the locked normalization order; unit-test against the RV-N test corpus.
- [ ] **RV-N.2** Wire `sourceRepoIdOf` at every repo-add entry point: GUI flow, deep-link receive, `skb accept`, `references.toml` reference-add prompts.
- [ ] **RV-N.3** Implement `addRepo` duplicate-detection (EREPO_DUPLICATE); surface "Open existing" path.
- [ ] **RV-N.4** Implement orphan detection at indexer pass: state files whose `source_repo_id` doesn't match any configured repo → `state_index.orphaned = true` (RV-L.9 sibling).
- [ ] **RV-N.5** Implement rename detection: clone/fetch 404 on previously-working repo → `RepoEntry.status = MOVED_QUESTIONMARK`; UI surfaces "Moved?" pill (`ui-spec.md` UI-FF+).
- [ ] **RV-N.6** Implement `rebindRepo` per the pseudo-code; CLI command `skb state rebind <old-id> <new-url>` calls into this; auto-commits the move + rewrite.
- [ ] **RV-N.7** Tests: every row of the RV-N test corpus produces the expected normalized form and the expected same/different id.
- [ ] **RV-N.8** Tests: rebind round-trip — receiver has 3 state files for old-id → rebind to new-id → all 3 files now under `state/<new-id>/` with `source_repo_url` rewritten → render still finds them.
- [ ] **RV-N.9** Tests: collision — add repo A, then attempt to add repo B with normalized-equal URL → EREPO_DUPLICATE with A surfaced.
- [ ] **RV-N.10** Documentation: data-model.md DM-S references RV-N for the canonical algorithm + corpus; AGENTS.md mentions the normalization so Claude doesn't try to construct repo ids by hand.

### RV-N locked decisions

**Decision (lowercase the whole URL, not just host):** GitHub treats
`Dom/master-schedule` and `dom/master-schedule` as the same repo
(case-insensitive owner + repo names since 2019). Forgejo defaults
similarly. Lowercasing the whole URL produces consistent ids across
the typo-tolerance behavior of providers. Cost: a hypothetical
case-sensitive provider would suffer collisions. Verdict: GitHub +
Forgejo are 99% of v1's surface; the cost is acceptable and the
benefit (id stability across `Dom/X` and `dom/X` typing) is
material.

**Decision (16 hex chars, not full SHA-256):** 64 bits of entropy
is overkill for the address space of "repos the receiver has
configured" (typically 1–20). 16 hex chars (64 bits) collision
probability for 20 repos ≈ 10⁻¹⁷. Cost: future-proofing margin
against attack — a malicious actor crafting two URLs with colliding
truncated hashes. Verdict: irrelevant — `source_repo_id` is not a
security boundary; the URL itself is.

**Decision (no alias chain on rebind):** after `skb state rebind`,
the old id is dead. Rationale per body above: alias chains create
ambiguity if the provider re-issues the old URL (rare but
possible). The rebind is a destructive, intentional, one-commit
operation.

**Decision (rename detection ONLY on 404, not on other errors):**
clone/fetch errors come in many flavors (auth failure, network
unreachable, DNS, rate limit). Only 404 / "repository not found"
fires the `moved?` flag. Rationale: auth failures and network blips
must not be misclassified as renames — that would cause spurious
"Moved?" pills and bait users into rebinding to nothing.

---

## Phase RV-O — Updated out-of-scope section (Round 3 housekeeping)

> Surgical update to the existing "Out-of-scope (explicit)" block.
> RV-K (Round 2) edited it once; RV-O (Round 3) edits it again,
> additively. The block below this phase is the authoritative result.

### RV-O changes to "Out-of-scope (explicit)"

- ✅ **MOVED TO v1**: cross-repo state-file overlay. Was implicit in
  the Round 1/Round 2 "multi-repo aggregation" framing (D.9). Now
  formalized as **RV-L** under D.44. The overlay is the core unlock
  for shared-schedules — without it, Round 3's gifted-schedule UX
  is just a clone-and-view feature.
- ✅ **STILL IN-SCOPE per Round 2**: multi-tz common-time (RV-I)
  retains its Round 2 status. Round 3 does not change this.
- ⚠ **STILL DEFERRED** (Round 2 lock carries forward):
  - **Aligned sub-window emission in common-time** — per RV-K.
    v1 finders emit maximal free intervals; users adjust at
    quick-create.
  - **Per-slot UI override of primary** — per RV-K. The
    priority-override state file in RV-L is per-CALENDAR; per-slot
    overrides remain v2.
- ⚠ **NEW DEFERRAL (Round 3)**: **rule-instance-level
  priority-override**. The receiver cannot set a different priority
  for "this Monday's workout" vs "next Monday's workout" of the same
  recurrence rule. State files for `priority-override` exist only
  at the calendar level (per RV-L priority-override hoist), not at
  the per-instance level.
  - Use case: rare. Common case is "this whole calendar is low
    priority for me," not "this one occurrence specifically should
    swap rank."
  - Schema cost: significant. Per-instance priority overrides
    would need either a new state-kind keyed on
    `<rule-id>.<yyyy-mm-dd>.priority-override.toml`, or a generalized
    per-instance state mechanism. Either expansion warrants its own
    Round.
  - **Defer to v1.1.** When v1 ships and the use case shows up in
    real telemetry-substitute (user feedback), evaluate then.

### RV-O phase steps

- [ ] **RV-O.1** Edit the "Out-of-scope (explicit)" block below: keep the existing Round 2 entries (RV-K's edits), add the four new Round 3 entries (cross-repo overlay promoted, Round 2 deferrals carried forward, rule-instance priority-override deferred).
- [ ] **RV-O.2** Update the `Cross-reference table` at the top of this doc to add rows RV-L (Phase OO, D.44), RV-M (Phase TT, D.49 + D.5), RV-N (Phase OO + TT, D.51), RV-O (cross-cuts).
- [ ] **RV-O.3** Update the `Performance audit` table with: RV-L budget (200 events + 100 state files < 200ms cold, < 50ms hot); RV-M is zero-cost (constant per calendar; runs once at active-set construction); RV-N is O(1) per repo-add (one hash) — not on the render hot path.
- [ ] **RV-O.4** Cross-link this doc back to `main.md` Phases OO and TT in the closing `Cross-references back to main.md` list.
- [ ] **RV-O.5** Mark Round 3 phases ticked on this doc as work lands; jj change-id per `~/.claude/CLAUDE.md` convention.

### RV-O locked decisions

**Decision (defer rule-instance priority-override, don't reject):**
the use case is rare but legitimate (one specific occurrence is a
big deal; the rest are routine). Reject-vs-defer matters because
"rejected" signals a permanent no and "deferred" signals "we know,
not now." This is the latter.

**Decision (state files for `priority-override` are calendar-only,
not entity-level):** confirms RV-L's scope. A receiver who wants
"this one event in particular at higher priority" can use a
per-occurrence recurrence exception override (D.6) — that goes in
the SOURCE repo if write access exists. The state-file route is
calendar-wide.

---

## Out-of-scope (explicit) — updated by RV-K

- ✅ **Multi-tz common-time** — **promoted to v1** via RV-I. (Was
  deferred to v2 per D.10.)
- ✅ **CalDAV-side resolver semantics** — **promoted to v1** via
  RV-H. Resolver treats CalDAV mirror calendars (D.25) as additional
  active calendars sharing the RV-H tz-resolution chain; pull
  mechanics live in `sync-engine.md` SE-Q+.
- ⚠ **Aligned sub-window emission** in common-time (RV-E + RV-I) —
  still deferred. Finders emit maximal free intervals; users adjust
  at quick-create. v2 candidate.
- ⚠ **Per-slot UI override** of primary/secondary choice — still
  deferred. Priority tiebreak is deterministic globally; per-slot
  overrides are a v2 candidate. Current workaround: per-occurrence
  recurrence exception override.

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
- RV-H → main.md `Phase AA` (multi-timezone semantics).
- RV-I → main.md `Phase AA` + `Phase N` (multi-tz common-time finder).
- RV-J → main.md `Phase BB` (weather overlay as a non-busy data layer).
- RV-K → cross-cuts (Round 2 out-of-scope housekeeping).
- RV-L → main.md `Phase OO` (cross-repo state-file overlay).
- RV-M → main.md `Phase TT` (multi-repo priority resolution).
- RV-N → main.md `Phase OO` + `Phase TT` (source-repo-id matching + dedup).
- RV-O → cross-cuts (Round 3 out-of-scope housekeeping).
- RV-R → main.md `Phase XX` + `Phase YY` (inverted-default completion + cross-repo feedback overlay; Round 4).
- RV-P → main.md `Phase BBB` (supersedence pass; Round 5).
- RV-Q → main.md `Phase BBB` (off-schedule detection; Round 5).

---

## Phase RV-R — Inverted-default completion state + cross-repo feedback overlay (Round 4)

> **Phase-letter note:** originally drafted as a second `RV-N` (collision with the Source-repo-id phase above). **Decision (rename):** this Round-4 block is renumbered to **RV-R** so each phase letter is unique across the doc. The sub-step prefixes (`RV-R.1` … `RV-R.10`) keep their original numbering. **Rationale:** the global CLAUDE.md plan-file rule demands unambiguous `<prefix>.N` references; two RV-N phases would break sub-agent dispatch. RV-P and RV-Q are already taken by Round 5; RV-R is the next free letter.

**See [`draft-atomic-activities.md`](draft-atomic-activities.md) (`main.md` Phase XX) + [`draft-global-id-feedback.md`](draft-global-id-feedback.md) (`main.md` Phase YY) and `decisions.md` D.70 / D.71 / D.72 / D.73 for the authoritative spec.**

### Inverted-default completion state (D.70)

- [ ] **RV-R.1** `resolveCompletionState(event, now): CompletionState` step added to the render pipeline (after recurrence materialization, before overlay layering). Returns one of: `scheduled | completed-by-schedule | skipped | partial | completed-early | completed-late | in-progress`.
- [ ] **RV-R.2** Algorithm: (1) `event.start > now` → `scheduled`. (2) `event.start <= now < event.end` and no deviation file → `in-progress`. (3) `now >= event.end` and no deviation file → `completed-by-schedule` (THE inversion). (4) any past-or-current event WITH a deviation file → that file's `kind`.
- [ ] **RV-R.3** Recurrence + exception interaction: deviation files for recurring events live under `<rule-id>/<yyyy-mm-dd>.md`; resolver looks up by `(rule-id, occurrence-date)`. An `exceptions/<rule-id>/<yyyy-mm-dd>.md` with `kind = "cancel"` shadows any deviation (cancelled occurrences never had a scheduled state to deviate from).
- [ ] **RV-R.4** Cache: Room column `completion_state` on `event_instances`. Keyed on `(repo, entity-id-or-rule-id, occurrence-date)`. Invalidated when (a) underlying file changes, (b) deviation file for that (entity, date) is added/removed, (c) `now` crosses `event.start` / `event.end` (handled by per-event AlarmManager state-tick from Phase XX.3).
- [ ] **RV-R.5** Visual treatment table:
  - `completed-by-schedule` → checked tile, dimmed slightly, no celebration animation, no "great job" copy.
  - `skipped` → outline-only tile, muted color, no red, no warning glyph.
  - `partial` → half-filled tile.
  - `completed-early` / `completed-late` → checked tile with small clock-offset glyph.
  - `in-progress` → soft progress arc; never blinking, never attention-grabbing.
- [ ] **RV-R.6** Sub-beat resolution for the active event: when `(now - event.start) ∈ [Σ_{j<i} d_j, Σ_{j≤i} d_j)`, sub-beat `i` is active. Sub-beats cycle if their total duration is shorter than the parent event; they truncate at event end if longer. Sub-beat state feeds the `StickerResolver` per D.66. **Decision (bounded depth):** sub-beat nesting is at most **1 level deep** (parent event → flat list of sub-beats); a sub-beat MUST NOT itself contain sub-beats. **Rationale:** D.70 atomic-activities models sub-beats as a flat sequence within an event; nested beats double-encode structure that recurring events + exceptions already handle. Validator rejects nested sub-beats with `EBEAT_NESTED`.

### Cross-repo feedback overlay (D.71 / D.72 / D.73)

- [ ] **RV-R.7** Extend RV-L (cross-repo state-file overlay) with a parallel feedback-file overlay. Every cross-repo lookup goes through `RepoRegistry.allVisibleTo(viewerFp)` — the single chokepoint enforcing per-direction isolation per D.72.
- [ ] **RV-R.8** `FeedbackResolver.aggregate(targetGlobalId): AggregatedFeedback` queries the `FeedbackEntry` Room index across every repo in `allVisibleTo(viewerFp)`. Returns `{reactionsByToken: Map<String, List<Author>>, comments: List<Comment>, threads: List<Thread>}`. Threading by `reply_to`, linear `created` order within threads.
- [ ] **RV-R.9** Aggregation memoization keyed on `(targetGlobalId, set-of-visible-repo-HEADs)`. Invalidated when any visible repo's HEAD changes (incremental indexer hook).
- [ ] **RV-R.10** Isolation invariants verified by privacy tests (Phase YY.6 / FB-F.5): no count masking ("N hidden" leak forbidden); notification suppression; search/autocomplete masking; author-chip rendering uses the receiving repo's local label.

---

## Phase RV-P — Supersedence pass (Round 5; main.md Phase BBB)

See [`draft-household-travel-vacation.md`](draft-household-travel-vacation.md) HV-E and `decisions.md` D.75 / D.76 / D.77 / D.78 for the authoritative spec. A NEW resolver pass layered on top of the existing overlay-priority resolution (RV-D). A calendar may temporally pause other calendars without deleting their content (vacation pauses routine; medical/pet calendars never paused).

- [ ] **RV-P.1** New resolver pass `supersedencePass(candidateEvents, activeCalendars, date)` inserted AFTER RV-D's overlay-priority pass and BEFORE final render. Pass marks events `hidden-by-supersedence(X)` rather than removing them so the manage-overlays UI can still render them strikethrough+grey.
- [ ] **RV-P.2** Algorithm per HV-E.2:
  ```
  for each candidate event E on date D from calendar Y:
    if any active calendar X exists where:
        X.supersedes contains Y.id
        AND (X.superseded_during is empty OR
             some range in X.superseded_during covers D)
        AND Y.nonSuperseable == false
        AND E.tags does not contain "nonSuperseable"
        AND no overrides/<Y.id>/<E.id>/<D>.md with kind="force-show" exists
        AND no overrides/<Y.id>/<E.id>/range covering D with kind="force-show-for-range" exists
    then:
      mark E as hidden-by-supersedence(X)
  ```
  Tiebreak when multiple X candidates supersede the same Y on D: highest-priority X per TT-priority wins as the attribution; the hide-result is the same. Per D.78 / S1.
- [ ] **RV-P.3** Invariants enforced (D.78): **S1** at-most-one hiding calendar; **S2** non-cascading (resolver checks direct relationships only); **S3** self-supersede rejected by validator; **S4** `nonSuperseable = true` calendars ignore all supersedence; **S5** events with `nonSuperseable` tag survive even inside superseable calendars.
- [ ] **RV-P.4** `overrides/<cal-id>/<event-id>/<yyyy-mm-dd>.md` (D.77) — opt-out from supersedence. Two `kind` values: `force-show` (single date), `force-show-for-range` (date range). Parallel to `exceptions/` (cancel) and `deviations/` (reported-not-done).
- [ ] **RV-P.5** Room cache table `event_visibility(repo, date, event_id, hidden_by_calendar_id NULLABLE)` (HV-E.5). Invalidated when (a) any calendar's `supersedes` / `superseded_during` changes, (b) any `nonSuperseable` flag flips, (c) any `overrides/` file added/removed.
- [ ] **RV-P.6** UI consumer surfaces (per [`ui-spec.md`](ui-spec.md) UI-PP): schedule + now-card hide; manage-overlays renders strikethrough+grey with hover/tap tooltip "paused by <X.title> until <range.to>" + per-event force-show toggle; week/month view shows small leaf-glyph indicating an active vacation overlay.
- [ ] **RV-P.7** Tests per HV-I.6: red/green fixtures for each invariant S1..S5; priority tiebreak when two overlays supersede the same calendar on the same date; per-event override survives; `nonSuperseable` calendar + event-tag both honored.

---

## Phase RV-Q — Off-schedule detection (Round 5; main.md Phase BBB)

See [`draft-household-travel-vacation.md`](draft-household-travel-vacation.md) HV-N.4 and `decisions.md` D.80. Detects events that fall outside their parent calendar's declared `baseline_cadence` and flags them for briefing surface highlighting.

- [ ] **RV-Q.1** Calendar-config schema extension per HV-N.4 (also DM-AA): optional `[baseline_cadence]` block carrying `weekdays = [...]`, `window = ["HH:MM", "HH:MM"]`, `timezone = "..."`. Missing block = no flagging.
- [ ] **RV-Q.2** Detection algorithm: for each materialized event E on calendar Y with baseline B, set `off_schedule = true` iff `(weekday(E.start in B.timezone), local_time(E.start in B.timezone)) ∉ (B.weekdays × B.window)`. Per-calendar; non-cascading (no promotion to other events on the same date).
- [ ] **RV-Q.3** Briefing-surface integration (D.81): the `tomorrow_briefing` body generator (NS-Z) prefixes off-schedule items with ⚠ and a contextual hint like *"(off-schedule — work calendar normally runs 9-17)"*.
- [ ] **RV-Q.4** Room cache: `event_instances.off_schedule` boolean column. Invalidated when (a) event time changes, (b) calendar's `baseline_cadence` block changes, (c) calendar's `timezone` changes.
- [ ] **RV-Q.5** Tests per HV-O.4: Sat-14:00 doctor event on a Mon-Fri 9-17 baseline calendar → `off_schedule = true`; day-before `tomorrow_briefing` body contains ⚠ prefix.

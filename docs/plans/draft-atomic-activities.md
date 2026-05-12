# strictlykeptboy — atomic activities, inverted habits, routines (DRAFT)

## Status: ✅ INTEGRATED — see `main.md` Phase XX and `decisions.md` D.70 (with D.68 covering sub-beats). Data-model edits at `data-model.md` Phase DM-M. Resolver edits at `resolver.md` Phase RV-N. Template content surface at `templates-demo-wizard.md` Phase TW-I. CLI surface at `cli-tooling.md` CLI-L.1–CLI-L.5. Original draft content preserved below for reference.

---

## Framing

This draft locks the **inverted habit model** as the project's
core behavioral differentiator and specifies the atomic-activity
+ routine layer that sits on top of the existing calendar/event
model (Phases C–E + L of `main.md`).

**The inversion (LOCKED).** Default state for any scheduled event
in the past or present is `completed-by-schedule`. The user does
nothing — the schedule itself is the receipt. Deviation is the
explicit action: the user only intervenes when they *didn't* do the
thing, or did it partially, or did it off-schedule. This is the
opposite of Finch / Habitica / streak-pet apps, which require the
user to tap "done" to score dopamine, then punish skipped taps
with guilt-coded UI. We do not do guilt. We do not do escalation.

**Atomic = one entity per activity (LOCKED).** "Bed routine" is
NOT an event. "Brush teeth" is. "Cage check" is. A bed routine is
a *calendar overlay* whose events are atomic activities. Routines
are quick-starts that materialize their atomic items into the
current timebox. The atomic granularity is what lets sub-beats
(toothbrushing → 6 quadrants), sticker badges, and per-activity
streak counters work cleanly.

**Sub-beat depth (LOCKED).** Some atomics decompose into timed
sub-beats inline in the event TOML (not separate entities). The
phone buzzes once per sub-beat boundary; the notification text
shows the current sub-beat label. Sub-beats are bounded depth (no
sub-sub-beats); deeper structure becomes its own atomic event.

**Kink-positive openly (LOCKED).** Atomic templates include
kink-coded activities (`cage-check`, `plug-check`, `posture-check`,
`collar-check`, `edge-and-stop`, `kegels`, `pubes-grooming`,
`body-grooming`) ALONGSIDE neutral activities (`brush-teeth`,
`shower`, `shave-face`, `hair`, `skincare`, `deodorant`,
`nail-care`, `ear-clean`). Each kink-coded entry carries
`tags = ["kink"]` so the neutral-mode toggle (Agent 5) can
filter them out at template-application time. Sticker IDs and
titles for kink-coded entries are user-renameable; lockscreen
notifications surface only the user-authored title (whatever they
named it), never editorial copy.

**No Wear OS (LOCKED).** Phone notification + lockscreen-aware
buzz via `AlarmManager`. No watch app, no foreground service for
notifications (alarms are fire-and-forget). The text "watch buzz"
in the user's prose maps to "phone buzz" in implementation.

**Optional streak counter, count-only (LOCKED).** Derived from
deviation files. Per-event-or-rule integer
(`consecutive_days_with_no_skip_deviation`). Cached in Room.
Surfaced as a tiny number badge on the now-card. No flames, no
color escalation, no "you broke your streak" modals.

---

## Phase AT-A — deviation file schema

New directory, separate from `exceptions/`. Justification: exceptions
mean *scheduling changed* (this occurrence was cancelled or
overridden); deviations mean *scheduling held but reality differed*
(the event was scheduled, the user reports it didn't go as
scheduled). Conflating them would make the streak counter and the
resolver's inverted-default logic ambiguous.

- [ ] **AT-A.1** Path layout:
      `deviations/<calendar-id>/<event-id-or-rule-id>/<yyyy-mm-dd>.md`
      One file per (entity, day). For a recurring rule, the
      sub-path is `<rule-id>/<yyyy-mm-dd>.md`; for a one-off
      event, `<event-id>/<yyyy-mm-dd>.md` (date matches the event's
      scheduled local date). LOCKED.
- [ ] **AT-A.2** TOML frontmatter schema (LOCKED):
      ```toml
      +++
      schema_version = 1
      kind = "skipped" | "partial" | "completed-early" | "completed-late"
      at = "2026-05-12T07:14:00+02:00"   # ISO datetime, required
      author = "<person-id>"              # required, same as event author rules
      note = "..."                         # optional free text
      subbeats_completed = ["upper-left-molars", "fronts-top"]  # optional, only meaningful for partial
      +++

      Body free-form Markdown.
      ```
- [ ] **AT-A.3** `kind` semantics LOCKED:
      - `skipped` — user actively reports they didn't do it.
      - `partial` — started but didn't finish all sub-beats / duration.
        `subbeats_completed` array names the labels the user ticked.
      - `completed-early` — done before `at` of the scheduled event;
        `at` field carries the actual completion time.
      - `completed-late` — done after the scheduled end; `at` carries
        actual completion time.
      No `kind = "completed"` — that's the default-by-schedule state
      and writing a file for it would be redundant and would balloon
      the repo.
- [ ] **AT-A.4** Atomic-write + auto-commit message format:
      `deviation <kind> on "<event-title>" <yyyy-mm-dd>`.
- [ ] **AT-A.5** ktoml round-trip tests for each `kind`.
- [ ] **AT-A.6** Validation: refuse to write a deviation for a future
      date (clock check vs. `at`); refuse a deviation for an event
      that doesn't exist in the resolver's view at that date (catches
      typos in entity-id segment).
- [ ] **AT-A.7** CLI: `skb deviation set --event <id> --date <yyyy-mm-dd>
      --kind skipped|partial|completed-early|completed-late [--at <iso>]
      [--note "..."]`. Default `--at` is `now`.

---

## Phase AT-B — resolver integration (inverted default)

Extends Phase E (`resolver.md` RV-A..F) with deviation-aware state
resolution and bakes the inversion into the rendered output.

- [ ] **AT-B.1** Add a `resolveCompletionState(event, now)` step to
      RV-D's render pipeline. Returns one of:
      `scheduled` | `completed-by-schedule` | `skipped` | `partial`
      | `completed-early` | `completed-late` | `in-progress`.
- [ ] **AT-B.2** Algorithm LOCKED:
      1. If `event.start > now` → `scheduled`.
      2. Else if `event.start <= now < event.end` and no deviation
         file → `in-progress` (UI may show a soft "happening now"
         marker; default styling is still affirmative, not nagging).
      3. Else if `now >= event.end` and no deviation file →
         `completed-by-schedule` (THE inversion).
      4. Else (any past-or-current event WITH a deviation file) →
         that file's `kind`.
- [ ] **AT-B.3** Recurrence handling: deviation files for recurring
      events live under `<rule-id>/<yyyy-mm-dd>.md`; resolver looks
      up by `(rule-id, occurrence-date)`. An `exceptions/<rule-id>/<yyyy-mm-dd>.md`
      with `kind = "cancel"` shadows any deviation (cancelled
      occurrences never had a scheduled state to deviate from).
- [ ] **AT-B.4** Cache: add a derived column `completion_state` to
      the Room `event_instances` table. Keyed on
      `(repo, entity-id-or-rule-id, occurrence-date)`. Invalidated
      when (a) the underlying file changes, (b) the deviation file
      for that (entity, date) is added/removed, or (c) `now` crosses
      `event.start` / `event.end` (handled by a per-event
      AlarmManager state-tick).
- [ ] **AT-B.5** Visual treatment LOCKED:
      - `completed-by-schedule` → checked tile, dimmed slightly, no
        celebration animation, no "great job" copy.
      - `skipped` → outline-only tile, muted color, no red, no warning
        glyph.
      - `partial` → half-filled tile.
      - `completed-early` / `completed-late` → checked tile with a
        small clock-offset glyph.
      - `in-progress` → soft progress arc; never blinking, never
        attention-grabbing.
- [ ] **AT-B.6** Unit tests: every state transition in AT-B.2.

---

## Phase AT-C — notification + buzz pattern

`AlarmManager`-only (no foreground service). One alarm at event
start, one at event end, plus one per sub-beat boundary (Phase
AT-F).

- [ ] **AT-C.1** Notification channel: `events-atomic` (separate
      from the generic `events` channel from Phase M). Importance
      `IMPORTANCE_HIGH` for lockscreen-visible buzz; user can
      downgrade in system settings.
- [ ] **AT-C.2** Start-of-event alarm: `setExactAndAllowWhileIdle`
      at `event.start`. Notification content:
      - **Title** = user-authored event title verbatim (whatever the
        user named it, including kink-coded labels if they chose
        them). LOCKED: no editorial wrapping, no app-generated copy.
      - **Body** = empty by default. (Lockscreen-safe: nothing the
        app generates can surface non-SFW phrasing on a lockscreen
        preview. Mirrors VV.12.)
      - **Actions** = `I did it` / `I didn't` / `Partial` /
        `Remind in 10/30/60 min`.
- [ ] **AT-C.3** End-of-event alarm: `setExactAndAllowWhileIdle` at
      `event.end`. Same content; this is the inversion's safety
      net — if the user hasn't acted, the schedule "auto-completes"
      and the notification simply dismisses itself silently. The
      end-alarm exists primarily to flip the Room cache state from
      `in-progress` to `completed-by-schedule` without requiring an
      app foreground tick.
- [ ] **AT-C.4** Action handlers LOCKED:
      - `I did it` → dismiss notification, no-op (default-by-schedule
        already wins). Does NOT write a `completed` deviation file —
        that's the whole point of the inversion.
      - `I didn't` → write `deviations/.../<date>.md` with
        `kind = "skipped"`, `at = now`. Auto-commit. Dismiss
        notification.
      - `Partial` → open a tiny activity sheet (one screen, one text
        field for `note`, sub-beat checkboxes if the event has
        sub-beats); on submit write `kind = "partial"`. Dismiss.
      - `Remind in 10/30/60 min` → cancel current notification,
        schedule a fresh `setExactAndAllowWhileIdle` at `now + N min`
        with the same content. Does not write a deviation; treats it
        as "still trying."
- [ ] **AT-C.5** Permissions: `POST_NOTIFICATIONS` (already requested
      in Phase M); `SCHEDULE_EXACT_ALARM` (Android 12+, request once
      with a clear rationale screen — "we use exact alarms so your
      schedule doesn't drift on idle"); `USE_FULL_SCREEN_INTENT`
      not used (no full-screen takeover; lockscreen banner only).
- [ ] **AT-C.6** Doze + battery-optimization handling: request
      whitelist exemption only if the user opts into "make sure my
      schedule never misses a beat" (Settings → Notifications →
      Atomic activities → "Ignore battery optimizations"). Default
      OFF; the inversion model is forgiving of missed alarms (worst
      case, the user simply sees `completed-by-schedule` after the
      fact and has to opt-in to mark a skip if they care).
- [ ] **AT-C.7** Tests: Robolectric harness with `ShadowAlarmManager`
      asserting alarms scheduled at correct times; intent-handler
      tests for each of the four action paths.

---

## Phase AT-D — atomic self-care template

New template file at `templates/atomic-self-care.toml`. Format:
TOML throughout (not Markdown-with-frontmatter) because templates
are scaffolding-time data, not user-entity files. LOCKED format.

- [ ] **AT-D.1** File path: `templates/atomic-self-care.toml`.
- [ ] **AT-D.2** Schema header:
      ```toml
      schema_version = 1
      template_id = "atomic-self-care"
      display_name = "Atomic self-care"
      category = "self-care"
      neutral_safe = true   # appears in neutral mode
      ```
- [ ] **AT-D.3** Entries (LOCKED). Each is one atomic event-template:
      ```toml
      [[entry]]
      id = "brush-teeth"
      title = "Brush teeth"
      duration_minutes = 5
      sticker_id = "tooth"
      category = "self-care"
      default_cadence = "twice-daily"   # default-on slots: 07:00, 22:00
      [[entry.subbeat]]
      label = "Upper-left molars"
      duration_seconds = 25
      sticker_id = "brush-upper-left"
      [[entry.subbeat]]
      label = "Upper-right molars"
      duration_seconds = 25
      sticker_id = "brush-upper-right"
      [[entry.subbeat]]
      label = "Lower-left molars"
      duration_seconds = 25
      sticker_id = "brush-lower-left"
      [[entry.subbeat]]
      label = "Lower-right molars"
      duration_seconds = 25
      sticker_id = "brush-lower-right"
      [[entry.subbeat]]
      label = "Fronts (top)"
      duration_seconds = 25
      sticker_id = "brush-fronts-top"
      [[entry.subbeat]]
      label = "Fronts (bottom)"
      duration_seconds = 25
      sticker_id = "brush-fronts-bottom"
      [[entry.subbeat]]
      label = "Tongue + spit-out wrap-up"
      duration_seconds = 50
      sticker_id = "brush-wrapup"

      [[entry]]
      id = "shower"
      title = "Shower"
      duration_minutes = 10
      sticker_id = "shower"
      default_cadence = "daily"

      [[entry]]
      id = "shave-face"
      title = "Shave (face)"
      duration_minutes = 5
      sticker_id = "razor"
      default_cadence = "biweekly"  # every 14 days

      [[entry]]
      id = "shave-pubes"
      title = "Shave (pubic)"
      duration_minutes = 10
      sticker_id = "razor"
      default_cadence = "every-14-days"
      tags = ["intimate-care"]  # NOT "kink" — neutral hygiene

      [[entry]]
      id = "hair"
      title = "Hair"
      duration_minutes = 5
      sticker_id = "comb"
      default_cadence = "daily"

      [[entry]]
      id = "skincare"
      title = "Skincare"
      duration_minutes = 3
      sticker_id = "skincare"
      default_cadence = "twice-daily"

      [[entry]]
      id = "deodorant"
      title = "Deodorant"
      duration_minutes = 1
      sticker_id = "deodorant"
      default_cadence = "daily"

      [[entry]]
      id = "nail-care"
      title = "Nail care"
      duration_minutes = 5
      sticker_id = "nail"
      default_cadence = "weekly"

      [[entry]]
      id = "ear-clean"
      title = "Ear clean"
      duration_minutes = 3
      sticker_id = "ear"
      default_cadence = "weekly"
      ```
- [ ] **AT-D.4** Sub-beat total for `brush-teeth` = 6×25 + 50 = 200s
      ≈ 3m20s, leaving slack inside the 5-minute envelope for
      rinsing/etc. LOCKED — the sub-beat sum should always be ≤ the
      atomic's `duration_minutes`; validator enforces.

---

## Phase AT-E — atomic kink-self-care template

New template file at `templates/atomic-kink-self-care.toml`. Every
entry carries `tags = ["kink"]` so the neutral-mode toggle (Agent 5)
filters them out at template-application time.

- [ ] **AT-E.1** File path: `templates/atomic-kink-self-care.toml`.
- [ ] **AT-E.2** Schema header:
      ```toml
      schema_version = 1
      template_id = "atomic-kink-self-care"
      display_name = "Atomic kink self-care"
      category = "self-care"
      neutral_safe = false   # hidden in neutral mode
      ```
- [ ] **AT-E.3** Entries (LOCKED, all tagged `kink`):
      ```toml
      [[entry]]
      id = "cage-check"
      title = "Cage check"
      duration_minutes = 2
      sticker_id = "cage"
      default_cadence = "configurable"   # default 3×/day at 08:00, 14:00, 22:00
      default_times = ["08:00", "14:00", "22:00"]
      tags = ["kink"]

      [[entry]]
      id = "plug-check"
      title = "Plug check"
      duration_minutes = 5
      sticker_id = "plug"
      default_cadence = "configurable"  # default 2×/day
      default_times = ["09:00", "21:00"]
      tags = ["kink"]

      [[entry]]
      id = "posture-check"
      title = "Posture check"
      duration_minutes = 1
      sticker_id = "posture"
      default_cadence = "hourly-waking"  # 08:00..22:00 hourly
      tags = ["kink"]

      [[entry]]
      id = "collar-check"
      title = "Collar check"
      duration_minutes = 2
      sticker_id = "collar"
      default_cadence = "daily"
      default_times = ["08:00"]
      tags = ["kink"]

      [[entry]]
      id = "edge-and-stop"
      title = "Edge and stop"
      duration_minutes = 10
      sticker_id = "edge"
      default_cadence = "configurable"  # default every other evening
      default_times = ["22:30"]
      tags = ["kink"]

      [[entry]]
      id = "kegels"
      title = "Kegels"
      duration_minutes = 5
      sticker_id = "kegel"
      default_cadence = "daily"
      default_times = ["07:30"]
      tags = ["kink"]
      [[entry.subbeat]]
      label = "Slow squeezes (10 reps)"
      duration_seconds = 90
      [[entry.subbeat]]
      label = "Fast pulses (20 reps)"
      duration_seconds = 60
      [[entry.subbeat]]
      label = "Hold (30s × 3)"
      duration_seconds = 150

      [[entry]]
      id = "pubes-grooming"
      title = "Pubes grooming"
      duration_minutes = 10
      sticker_id = "trim"
      default_cadence = "every-14-days"
      tags = ["kink"]

      [[entry]]
      id = "body-grooming"
      title = "Body grooming"
      duration_minutes = 15
      sticker_id = "trim"
      default_cadence = "weekly"
      tags = ["kink"]
      ```
- [ ] **AT-E.4** All entries support per-instance cadence override
      at scaffold time (wizard surface, Phase K). The
      `default_cadence = "configurable"` value just means "ask the
      user during scaffold"; everything else is a sensible default
      that scaffolds without asking.

---

## Phase AT-F — atomic workout template

New template file at `templates/atomic-workout.toml`. Each entry
is its own atomic event with `sets` and `reps` fields. Workouts
are NOT tagged `kink` (they're neutral by default); a separate
template can layer kink-tagged variants later.

- [ ] **AT-F.1** File path: `templates/atomic-workout.toml`.
- [ ] **AT-F.2** Schema header:
      ```toml
      schema_version = 1
      template_id = "atomic-workout"
      display_name = "Atomic workout"
      category = "fitness"
      neutral_safe = true
      ```
- [ ] **AT-F.3** Entries (LOCKED defaults; configurable at scaffold):
      ```toml
      [[entry]]
      id = "pushups"
      title = "Push-ups"
      duration_minutes = 3
      sticker_id = "pushup"
      sets = 2
      reps = 40                # configurable, default 40
      default_cadence = "daily"
      default_times = ["07:00"]

      [[entry]]
      id = "situps"
      title = "Sit-ups"
      duration_minutes = 3
      sticker_id = "situp"
      sets = 2
      reps = 40
      default_cadence = "daily"
      default_times = ["07:05"]

      [[entry]]
      id = "squats"
      title = "Squats"
      duration_minutes = 3
      sticker_id = "squat"
      sets = 2
      reps = 40
      default_cadence = "daily"
      default_times = ["07:10"]

      [[entry]]
      id = "pull-ups"
      title = "Pull-ups"
      duration_minutes = 3
      sticker_id = "pullup"
      sets = 2
      reps = 10
      default_cadence = "daily"
      default_times = ["07:15"]

      [[entry]]
      id = "planks"
      title = "Planks"
      duration_minutes = 4
      sticker_id = "plank"
      sets = 3
      hold_seconds = 60        # plank uses hold-time instead of reps
      default_cadence = "daily"
      default_times = ["07:20"]

      [[entry]]
      id = "burpees"
      title = "Burpees"
      duration_minutes = 3
      sticker_id = "burpee"
      sets = 2
      reps = 20
      default_cadence = "daily"
      default_times = ["07:25"]
      ```
- [ ] **AT-F.4** Sub-beat semantics for workouts (LOCKED): one
      sub-beat per set. `subbeat[i].label = "Set i/N — <reps> reps"`,
      `subbeat[i].duration_seconds = round(duration_minutes*60 / sets)`.
      The phone buzzes between sets so the user knows to start the
      next set without watching a timer.

---

## Phase AT-G — routines as calendar overlays

A routine is **NOT** an entity type. It's an ordinary calendar
with `routine = true` set in its `calendar.toml` and a tag
indicating which routine it is. The events inside are atomic.
Materialization-on-demand is what makes routines feel like quick-
starts even though they're "just" calendars under the hood.

- [ ] **AT-G.1** Extend `calendar.toml` schema (additive, optional):
      ```toml
      routine = true
      routine_id = "morning" | "bed" | "workout" | "<custom>"
      routine_default_start = "07:00"   # used when "start at default time"
      routine_can_materialize = true    # quick-start enabled
      ```
      No new entity kind. Resolver treats routine calendars as
      regular calendars for overlay/priority purposes.
- [ ] **AT-G.2** Default routine calendars scaffolded by wizard
      (when relevant role-toggles selected):
      - `routine-morning` — populated from `atomic-self-care` +
        neutral entries from `atomic-kink-self-care` if kink toggle
        on.
      - `routine-bed` — `brush-teeth`, `skincare`, `cage-check` (if
        kink), `kegels` (if kink), `plug-check` (if kink).
      - `routine-workout` — populated from `atomic-workout`.
- [ ] **AT-G.3** Routine calendars default to `active_toggle = false`.
      They exist as a *library*; events from them only materialize
      via AT-H. (Without this, the routine's atomic events would
      double-render alongside the materialized copies.)
- [ ] **AT-G.4** Visual treatment in the calendar drawer: routine
      calendars get a small `quick-start` glyph and group at the
      bottom of the calendar list (separated by a divider).

---

## Phase AT-H — "Start X routine" quick-start

UI action that materializes a routine's atomic items into the
current timebox, back-to-back from the chosen start time, using
each event's `duration_minutes`. The materialized events are real
event files in the target calendar.

- [ ] **AT-H.1** Surface: bottom-sheet from the FAB labeled
      "Start routine". Lists every calendar with
      `routine_can_materialize = true`. Tap a routine → open
      configuration sheet.
- [ ] **AT-H.2** Configuration sheet content:
      - Header: routine name + duration sum.
      - Start-time picker: defaults to `now`, presets for "now",
        "in 15min", "<routine_default_start>".
      - Target calendar picker: defaults to the user's default
        calendar; one-tap override.
      - Per-item checklist of the routine's atomic events, all
        pre-checked; tap to deselect any. Each row shows title +
        duration + sticker.
      - "Start now" CTA + "Cancel".
- [ ] **AT-H.3** Materialization algorithm (LOCKED):
      1. Read selected entries in routine-calendar order.
      2. Walk forward from start-time, assigning each entry
         `[cursor, cursor + duration)`.
      3. Write each as a new event file in the target calendar
         under `events/<yyyy>/<mm>/<uuid>.md`.
      4. Frontmatter on each materialized event:
         ```toml
         materialized_from = "<routine-id>"           # required
         materialized_source_event = "<source-entity-id>"  # the routine-calendar template event
         materialized_at = "<iso datetime of materialization>"
         ```
         These fields are additive; resolver ignores them; they
         exist for audit (the user can see "where did this event
         come from?") and for the `skb routine undo <materialized-at>`
         command (AT-H.6).
      5. Single git commit:
         `materialize routine "<routine-name>" at <start-time>`.
- [ ] **AT-H.4** Overlap guard: if the destination window already
      has events, the algorithm DOES NOT silently overwrite. It
      surfaces a conflict sheet showing the existing events and
      offers (a) shift the routine forward past them, (b) skip the
      overlapping atomic items, (c) cancel materialization. LOCKED.
- [ ] **AT-H.5** Sub-beats carry over: materialized event files
      preserve the `[[subbeat]]` array verbatim from the source.
- [ ] **AT-H.6** CLI: `skb routine start <routine-id>
      [--at <iso>] [--target <calendar-id>] [--skip <entry-id> ...]`.
      Plus `skb routine undo <materialized-at>` — deletes all event
      files with matching `materialized_at`, single revert commit.
- [ ] **AT-H.7** Tests: place routine at a clear window → all events
      sequential, no overlap; place routine over existing events →
      conflict sheet path; sub-beat preservation; undo command
      deletes the right files.

---

## Phase AT-I — sub-beat schema + buzz pattern

Sub-beats are inline TOML arrays in the event frontmatter (or in
template entries). One phone buzz per sub-beat boundary.

- [ ] **AT-I.1** Event-file schema extension (additive):
      ```toml
      [[subbeat]]
      label = "Upper-left molars"
      duration_seconds = 25
      sticker_id = "brush-upper-left"
      ```
      Multiple `[[subbeat]]` blocks. Order is rendering order.
      Sum of `duration_seconds` SHOULD be ≤ event's
      `duration_minutes * 60`; validator warns if not (doesn't
      refuse — user might want padding/sub-beats with gaps).
- [ ] **AT-I.2** Boundary alarms: at `event.start + sum(prefix)` for
      each sub-beat, schedule a `setExactAndAllowWhileIdle`. Single
      vibration, low-priority notification (no full-screen
      banner). Notification content:
      - Title = current sub-beat `label`.
      - Body = `<i> / <N>` index.
      - One action: `Skip ahead` → cancel remaining sub-beat alarms
        for this event (treats it as `partial`; writes
        `kind = "partial"` deviation with `subbeats_completed = [...]`
        for the ones the user got past).
- [ ] **AT-I.3** Sub-beat alarm budget: cap at 16 sub-beats per
      event (matches Android's reasonable alarm-count expectations
      per app and is far above any realistic atomic decomposition).
- [ ] **AT-I.4** Render in event detail sheet: vertical list of
      sub-beats, each row with the label + duration + sticker;
      current sub-beat highlighted during in-progress.
- [ ] **AT-I.5** Tests: 3-sub-beat event in Robolectric →
      `ShadowAlarmManager` shows 3 boundary alarms at correct
      offsets; `Skip ahead` action writes the right partial
      deviation.

---

## Phase AT-J — streak counter (count-only, no escalation)

Derived from deviation files. Cached in Room. Surfaced as a tiny
number badge on the now-card. No flames, no escalation, no "broke
your streak" copy. The badge is OPTIONAL — Settings →
Notifications → "Show streak counts on event tiles" toggles all
streak badges off globally.

- [ ] **AT-J.1** Computation (LOCKED, per-event-or-rule):
      `streak = consecutive_days_with_no_skip_deviation`, walking
      backward from "today" until a `skipped` deviation appears,
      counting only days where the entity was scheduled to occur.
      `partial` does NOT break the streak (the user did *something*);
      `completed-early` / `completed-late` do NOT break it.
- [ ] **AT-J.2** Storage: Room column `streak_count` on the event
      instance row, recomputed when (a) the entity file changes, (b)
      a deviation file under that entity is added/removed, or (c)
      the local day rolls (midnight tick — `AlarmManager`).
- [ ] **AT-J.3** UI: tiny rounded badge with the integer, no
      icon-prefix (no flame, no trophy). Color matches the tile's
      base; no escalation tier. Hidden when `streak_count < 2` (a
      streak of 1 isn't a streak yet).
- [ ] **AT-J.4** Global toggle: Settings → Notifications → "Show
      streak counts" (default ON, but one tap to disable forever).
- [ ] **AT-J.5** No notifications, no nags, no widgets on streaks.
      The badge is glanceable only.
- [ ] **AT-J.6** CLI: `skb streak <event-id-or-rule-id>` →
      prints integer + last-skip date.

---

## Phase AT-K — test fixtures

Robolectric tests covering the inversion's surface area, the
sub-beat alarm path, and routine materialization.

- [ ] **AT-K.1** Resolver inversion test: scaffold a repo with one
      event scheduled yesterday at 09:00–09:05, no deviation file;
      assert `resolveCompletionState` returns
      `completed-by-schedule`.
- [ ] **AT-K.2** Resolver skipped test: same event, write
      `deviations/<cal>/<event-id>/<yesterday>.md` with
      `kind = "skipped"`; assert returns `skipped`.
- [ ] **AT-K.3** Resolver partial test: write
      `kind = "partial"` with `subbeats_completed = ["upper-left-molars"]`;
      assert returns `partial` and the sub-beat array round-trips.
- [ ] **AT-K.4** Sub-beat boundary timing test: 4-sub-beat event
      with `duration_seconds = [25, 25, 25, 25]` starting at T;
      assert `ShadowAlarmManager` registers alarms at T, T+25,
      T+50, T+75 (event start + each subbeat boundary).
- [ ] **AT-K.5** Routine materialization sequential test:
      scaffold `routine-morning` with 3 atomics (5+10+3 = 18 min);
      run `skb routine start --at 07:00`; assert three event files
      created at 07:00, 07:05, 07:15, no overlap, all with
      `materialized_from = "morning"`.
- [ ] **AT-K.6** Routine materialization overlap test: pre-populate
      the target window with a conflicting event; assert conflict
      sheet is surfaced (test the conflict-detection function
      directly; UI test is out of scope for Robolectric).
- [ ] **AT-K.7** Streak count test: schedule a daily recurring event
      from D-7 through today; write a `skipped` deviation on D-3;
      assert `streak_count` for today = 3 (D-2, D-1, today).
- [ ] **AT-K.8** Neutral-mode filter test: apply
      `atomic-kink-self-care` template with neutral mode on; assert
      no events created (all entries are `tags = ["kink"]`).
- [ ] **AT-K.9** End-alarm state-flip test: schedule an event
      ending at T; advance Robolectric clock past T with no
      deviation; assert Room `completion_state` flips from
      `in-progress` to `completed-by-schedule`.

---

## Integration notes

The following edits to existing planning docs are required for
this draft to be picked up by the rest of the project. Each entry
lists the file, the section to amend, and the exact change.

### `main.md`

Insert a new phase block **between Phase L (demo content) and
Phase M (notifications)**, prefixed `Phase ATL` (Atomic Layer —
short letters keep typeable):

- New `Phase ATL — atomic activities, inverted habits, routines`.
- The phase body lists AT-A..AT-K sub-phases as ticks-to-do,
  linking to this `draft-atomic-activities.md` for the deep-dive.
- Cross-reference line in the main.md preamble cross-refs block:
  `Atomic activities + inverted habits: \`draft-atomic-activities.md\``.

Phase M (notifications) gains one sub-step: `M.7 — atomic-events
channel + sub-beat boundary alarms wired from Phase ATL`.

Phase L (demo content) gains one sub-step: `L.5 — seed demo-sub
with materialized routine-morning run + a sample skipped
deviation + a sample partial deviation, to exercise the
inversion's UI states in the demo`.

### `data-model.md`

Add a new section **DM-M — deviations + sub-beats** after the
existing DM-L (multi-tz). Content:

- **Deviation file schema.** Full schema from Phase AT-A above
  (path layout, TOML frontmatter, `kind` enum, optional fields).
  State explicitly that this folder is **separate** from
  `exceptions/` and explain the semantic difference (exceptions =
  scheduling-side change; deviations = post-hoc reality report).
- **Sub-beat schema.** Inline `[[subbeat]]` array on event files
  (and on template entries). Fields: `label`, `duration_seconds`,
  optional `sticker_id`. Constraint: sum ≤ event duration
  (validator warns).
- **Materialization audit fields.** Additive optional fields on
  event frontmatter: `materialized_from`, `materialized_source_event`,
  `materialized_at`. Resolver ignores these; they exist for
  routine undo + audit.
- **Routine calendar fields.** Additive optional fields on
  `calendar.toml`: `routine`, `routine_id`,
  `routine_default_start`, `routine_can_materialize`. Resolver
  treats routine calendars as ordinary calendars.

### `resolver.md`

Add a new section **RV-N — inverted-default completion state**
after the existing RV-M (multi-repo priority). Content:

- The `resolveCompletionState` function from Phase AT-B.
- The 4-rule algorithm (future = scheduled; in-window no-deviation
  = in-progress; past no-deviation = completed-by-schedule;
  deviation present = deviation kind).
- Recurrence + exception interaction (exception cancels shadow
  deviations).
- Cache invalidation triggers.
- Visual treatment table for each state (from AT-B.5).

### `templates-demo-wizard.md`

Add a new section **TW-I — atomic activity templates** after the
existing TW-H (demo content). Content:

- File-path inventory: `templates/atomic-self-care.toml`,
  `templates/atomic-kink-self-care.toml`,
  `templates/atomic-workout.toml`.
- Full content from Phases AT-D, AT-E, AT-F embedded.
- Wizard composition rule: role-toggle "kinky-chores" enables
  `atomic-kink-self-care`; role-toggle "daily-chores" enables
  `atomic-self-care`; role-toggles "gym-3x" / "gym-5x" enable
  `atomic-workout`.
- Neutral-mode toggle in the wizard hides every `neutral_safe = false`
  template and skips entries tagged `kink` from any template that
  would otherwise apply.
- Default-routine scaffolding (AT-G.2) — when the relevant role
  toggle is on, scaffold `routine-morning`, `routine-bed`,
  `routine-workout` calendars with `routine = true` and
  `active_toggle = false`, pre-populated with atomic events from
  the selected templates.

Mark the SP-1..SP-9 SFW-phrasing rules as **RETRACTED** at the
top of the file (per the user's note that another agent is
handling that retraction; this draft just records the
expectation so downstream agents don't accidentally reapply the
rules to the new atomic templates).

### `decisions.md`

Add a new locked decision **D.54 — Inverted habit model + atomic
activities** at the end of Round 3 (after D.53). Content
summary:

- Default state for any past-or-current scheduled event =
  `completed-by-schedule`. Deviation is the explicit action.
- Rationale: differentiator vs. Finch / Habitica. No guilt loop.
  Watch buzz / phone notification fires at event-start; user only
  acts if they didn't do the thing.
- Atomic = one entity per activity. Routines are calendar
  overlays, not entities.
- Sub-beats inline in event TOML; bounded depth.
- Kink-positive openly; neutral-mode toggle filters by `kink` tag.
- Optional streak counter, count-only, no escalation.
- No Wear OS; `AlarmManager` only.
- Deviation files at `deviations/<calendar-id>/<entity-id>/<yyyy-mm-dd>.md`,
  schema `kind ∈ {skipped, partial, completed-early, completed-late}`.
- Cross-reference: `draft-atomic-activities.md` for full mechanics.

This locks the inversion as a project decision so future agents
do not re-propose a Finch-style positive-confirmation model.

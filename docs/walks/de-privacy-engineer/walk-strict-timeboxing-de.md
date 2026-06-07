# Walk — strict timeboxing, de-DE (Tobias)

> Build a strict German-locale **timebox day** of focus blocks → layer
> it over his **work** and **club** calendars with **priorities** → use
> a **vacation / supersedence overlay** for a chess-tournament weekend
> so his weekday routine is paused without deleting anything.
>
> Persona: see `personality.md`. Repos `leben` and `termine` already
> configured (prior walks). System locale **de-DE**.
>
> Feature grounding: two calendar kinds (regular vs **timebox**) =
> README + `resolver.md` (both layered); overlay + active-windows +
> active-hours + priority = `resolver.md` RV-A/RV-C; vacation overlay =
> a calendar at **priority 999** (RV-C decision) + the **supersedence
> pass** RV-P (`main.md` Phase BBB / decisions D.75–D.78), which pauses
> routine calendars via a `supersedes` field with per-event
> `overrides/<event-id>.md` opt-outs (CLAUDE.md repo convention);
> effective priority = RV-M (authored + repo modifier + pin, 1..1000).
>
> Schema-identifier rule: timebox/calendar **ids stay English**; German
> only in display names, block titles, and de-DE UI strings.

## Pre-conditions

- `leben` has a **work** regular-calendar (display "Arbeit") and a
  **chores** todolist.
- `termine` has the club calendar (display "Vereinstermine") with the
  weekly Vereinsabend (from the prior walk).
- "Show all repos in unified view" is **on**.

## Part A — Build the strict timebox day

1. He opens the repo switcher → selects **`leben`** → opens **Settings →
   Repos → leben → Defaults** (or the calendar management surface) and
   **creates a new calendar of kind = timebox** named (display)
   **"Fokus"** → expects the calendar created with an **English id**
   (e.g. `focus`) and **kind = timebox**, distinct from regular
   calendars.
   ⚠️ QA: confirm the timebox kind is selectable at create-time and that
   the persisted `calendar.toml` uses English `kind`/`id` values even
   though the display name is German "Fokus".

2. On the **Fokus** timebox calendar he authors strict focus blocks for
   a normal workday (German display titles):
   - **"Tiefe Arbeit"** (deep work) 09:00–11:00
   - **"E-Mail & Admin"** 11:00–11:30
   - **"Tiefe Arbeit II"** 13:00–15:00
   - **"Lernen / Rust"** 17:00–18:00
   Recurrence: **weekdays** (`FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR`)
   → expects each block stored as a recurring entity (RRULE rule file),
   rendered as a **timebox layer** (focus blocks), not as
   location/context events.
   ⚠️ QA: timebox events must render in the timebox lane/style, visually
   distinct from regular events (resolver renders both kinds layered).

3. He sets the Fokus calendar's **active-hours** to working hours and
   leaves **active-windows** empty (= always active, per resolver
   "empty windows = always") → expects the timebox layer to apply every
   weekday with no date-window restriction.
   ⚠️ QA: confirm empty `active_windows` is treated as "always", not
   "never" (RV-A.2 decision).

4. He switches to the **unified all-repos Schedule** → expects to see
   three layers resolved together:
   - **Arbeit** (regular, `leben`) — meetings/context
   - **Fokus** (timebox, `leben`) — focus blocks
   - **Vereinstermine** (regular, `termine`) — club night
   all rendered for the same day, with author chips and the timebox
   blocks layered over the regular events.

5. He notices his 09:00 "Tiefe Arbeit" block overlaps a recurring work
   standup on **Arbeit**. He wants focus to **win the primary slot**, so
   he boosts the **Fokus** calendar priority via **Settings → Repos →
   leben → Fokus → "Boost priority"** → expects an effective-priority
   bump (RV-M: authored + pin, clamped 1..1000) so Fokus renders
   **primary** and the standup demotes to **secondary** — neither is
   deleted.
   ⚠️ QA: priority tiebreak — higher effective priority wins, ties
   broken by older-calendar/lex-id (RV-C decision). Confirm Fokus is
   `ranked[0]` in the overlap after the boost, standup still visible as
   secondary.

6. (Optional consistency check) He sets the **Arbeit** calendar's
   active-hours to 09:00–18:00 so personal evening time isn't cluttered
   by work context → expects work events outside those hours suppressed
   in the resolved view while the underlying files are untouched.

## Part B — Vacation / supersedence overlay for a tournament weekend

He has a chess tournament **Fri–Sun** out of town. He wants his weekday
Fokus routine and Arbeit context **paused** for those days — without
deleting the recurrences — and the tournament shown instead.

7. He creates a **vacation/supersedence overlay** for the tournament
   weekend. Per the model, a vacation overlay is a calendar/event with
   **priority 999** that **supersedes** routine calendars (RV-C +
   RV-P). He creates (display) **"Turnier-Wochenende"** spanning
   Fri 00:00 → Sun 23:59 with a `supersedes` list naming the routine
   calendars (Fokus, Arbeit) → expects it authored as files (English
   schema keys: `supersedes`, `priority = 999`).
   ⚠️ QA: confirm the overlay is authored with `priority = 999` and a
   **`supersedes` field**, not by deleting/editing the Fokus/Arbeit
   rules. German is display-only.

8. He opens the Schedule for the **tournament Saturday** → expects the
   **supersedence pass (RV-P)** to **pause** the superseded routine
   calendars for those dates: Fokus focus blocks and Arbeit context do
   **not** render, the **Turnier-Wochenende** overlay renders as
   primary (priority 999).
   ⚠️ QA: superseded routines must be **hidden for the overlay span
   only** — the day *before* and *after* the weekend must still show the
   normal Fokus/Arbeit routine (the underlying recurrences are intact).

9. The club's **Vereinsabend** falls on the Wednesday *before* the
   tournament, outside the overlay span → expects it **unaffected**
   (still shows, still authored by him). Only Fri–Sun are superseded.
   ⚠️ QA: supersedence is **date-scoped**; events outside the span are
   untouched, and the `termine` repo's calendar is not in the
   `supersedes` list anyway.

10. There's **one** weekday-routine item he still wants during the
    tournament — a 07:00 "Lernen / Rust"-style review he refuses to
    skip. He adds a per-event **opt-out** so that single block survives
    the supersedence → expects an **`overrides/<event-id>.md`** file
    (CLAUDE.md supersedence convention) that re-surfaces just that
    block while the rest of the routine stays paused.
    ⚠️ QA: confirm the per-event opt-out is a **file under
    `overrides/`**, and that exactly that one block reappears in the
    resolved view while sibling Fokus blocks remain superseded.

11. After the tournament weekend passes (Monday), he opens the Schedule
    → expects his **normal Fokus + Arbeit routine to resume
    automatically** — the supersedence was bounded by the overlay span,
    so nothing needed to be "turned back on".
    ⚠️ QA: this is the payoff for a privacy/files user — verify
    **resumption is automatic** and no recurrence was mutated. He'll
    `git log` to confirm the Fokus/Arbeit rule files are byte-identical
    to before the tournament.

12. He taps the **manual sync button** → expects all of this (the Fokus
    timebox rules, the priority-override file, the supersedence overlay,
    the `overrides/` opt-out, the cancel/exception files) to push to his
    Forgejo as **plain files**, conflict-free, readable on his laptop.
    ⚠️ QA: confirm the pushed tree matches expectations — one file per
    entity, English schema keys throughout, German strictly in display
    text. No index files written.

## Success criteria

- A **timebox** calendar "Fokus" created with English id/kind, German
  display name; focus blocks render in the timebox layer, distinct from
  regular events.
- Unified view layers Arbeit (regular) + Fokus (timebox) + Vereinstermine
  (club) with correct **priority resolution** (boosted Fokus wins
  primary; demoted items still visible).
- Empty `active_windows` = always; active-hours bound rendering without
  touching files.
- A **priority-999 supersedence overlay** pauses the named routine
  calendars **for the tournament span only**; out-of-span events
  (Vereinsabend) untouched.
- A per-event **`overrides/` opt-out** keeps one block alive during the
  overlay; routine **resumes automatically** afterward with rule files
  unchanged.
- Everything syncs to his self-hosted Forgejo as plain, English-keyed
  files — conflict-free, no index files.

## Confusion points collected for QA

- Step 1: timebox kind selectable; English id/kind vs German display.
- Step 2/4: timebox layer visually distinct from regular events.
- Step 3: empty active_windows = always (not never).
- Step 5: priority boost → Fokus primary, standup secondary, nothing
  deleted.
- Step 7: vacation overlay authored as priority-999 + `supersedes`
  field, not by editing routine rules.
- Step 8/9: supersedence is date-scoped; out-of-span days/events
  untouched.
- Step 10: per-event opt-out = `overrides/<event-id>.md` file; exactly
  one block resurfaces.
- Step 11: automatic resumption after the span; rule files byte-identical.
- Step 12: pushed tree = one-file-per-entity, English keys, no index
  files.

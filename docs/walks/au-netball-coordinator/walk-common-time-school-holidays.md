# Walk — Common-time finder for a holiday clinic + school-holiday vacation overlay

> Persona: Marnie Faulkner (see `personality.md`). Goal: use the
> **common-time finder across the Family + Club repos** to find and book
> a **school-holiday netball clinic** slot, then drop a **vacation /
> supersedence overlay** over the school-holiday week so her **routine
> calendars pause** while the one-off clinic (and anything she explicitly
> keeps) stays on. Replayable as a QA script.
>
> Features exercised: common-time finder (D.10, RV-E, UI-M.1–M.4),
> multi-repo overlay, create-event-from-slot, vacation overlay /
> supersedence (D.75–D.78, UI-OO), `nonSuperseable` lock, per-event
> override / "keep this on" (D.77, `overrides/`).
>
> Confusion risks flagged **[QA RISK]**.

## Preconditions

- Walks 1 + 2 done: she has the phone-only **Family** repo and the shared
  **Wynnum Waders Netball** repo (with recurring Tuesday training).
- Both repos have realistic content: Family has kid logistics; Club has
  the recurring training + a couple of volunteers' events.
- She is online enough to sync the club repo at the end.

## Part A — Find a common time for the holiday clinic

1. From the left nav rail she opens the **Together** tab (common-time +
   sharing overview) → expects the **common-time finder** form, or — if
   fewer than two repos were ever added — an empty state *"Add at least
   two repos to find common time"*. With two repos she expects the form.

2. **Repos row.** She sees multi-select repo chips. She selects
   **Family** and **Wynnum Waders Netball** (both highlighted) → expects
   per-repo **calendar sub-pickers** to expand below each.
   - **[QA RISK]** She wants to find time when *she* and *the courts* are
     both free. Make sure she can include the **club Training calendar**
     (so it counts existing trainings as busy) and her **Family** calendar
     (so kid logistics count as busy).

3. **Calendar sub-pickers.** Under Family she ticks **Family**; under the
   club she ticks **Training** (and any "Court bookings" calendar if
   present) → expects only ticked calendars to feed the busy-set.

4. **Constraints.** She sets:
   - **Date range** = the **school-holiday week** (e.g. the upcoming
     two-week QLD term break, or the first week of it).
   - **Duration** = **2 hours** (a clinic block).
   - **Days** = she leaves Mon–Fri on (clinics run on weekdays).
   - **Time window** = **09:00 – 15:00** (school-hours-ish so parents can
     drop and go).
   She taps **Find slots**.

5. **Results.** She expects a **ranked card list** — "N slots found", each
   card showing a date, a time range, the duration, and a "both free"
   style note, with a **[Create event →]** action. Ranking favours
   longer / earlier / closer-to-ideal slots.
   - **[QA RISK]** The busy-set must subtract the **recurring Tuesday
     training** materialised inside the holiday week *unless* that Tuesday
     was cancelled in walk 2. I.e. the finder must respect RRULE +
     exceptions, not just one-off events. If a cancelled-Tuesday slot is
     wrongly marked busy (or a live training is wrongly marked free), note
     it.

6. She picks a good slot (say **Wed 10:00–12:00**) and taps
   **[Create event →]** → expects a **quick-add pre-filled** with that
   date/time, and a **required target repo + calendar picker** (the finder
   doesn't assume where to write).

7. She sets target **repo = Wynnum Waders Netball**, **calendar =
   Training** (or a "Clinics" calendar), title **"School Holiday Clinic
   — U11s"** → taps **Save** → expects the event written + committed under
   her **"Marnie F (Coord)"** identity, and the finder/quick-add to
   dismiss.
   - **[QA RISK]** Confirm it lands in the **club** repo (shared) not the
     Family repo — she wants the other volunteers to see the clinic. Watch
     for the target picker defaulting to the wrong repo.

8. She opens **Schedule** for that holiday week → expects the new clinic
   on Wednesday with **her author chip**, sitting alongside whatever else
   is live that week.

## Part B — Vacation overlay so routines pause for the holidays

> The family is also taking a few days away during the break, and her
> daily routines (self-care reminders, errands cadence, etc.) shouldn't
> nag her while school's out. She wants those **paused**, not deleted —
> and the clinic + anything essential **kept on**.

9. She opens **Settings** and finds **+ Plan a trip** (between Calendars
   and Sharing), or uses the calendar-detail **"+ overlay from template →
   Vacation / trip"** entry → expects the **trip / vacation wizard** (a
   distinct multi-screen flow, bat-mascot scaffold).
   - **[QA RISK]** She thinks "pause my routine for the holidays", not
     "supersedence overlay". The entry-point + screens must use the plain
     "trip / pause" language. Note any screen that leads with the word
     *supersedence* as the primary label.

10. **Trip basics.** She names it **"School Holidays — Term break"** and
    sets the **window** to the school-holiday dates → expects the window
    to drive which days get paused.

11. **Daily anchors screen.** She sets a relaxed cadence / meal anchors as
    she likes (this is the away-days feel) → expects these to be optional;
    she can breeze past them.

12. **Supersedence picker (the key screen).** She sees **all active
    calendars** with a per-calendar **"pause during trip"** toggle.
    Expects sensible defaults:
    - Routine-style calendars (self-care routine, errands cadence,
      weekly-reset) → **default paused**.
    - Anything tagged `nonSuperseable` (per D.76 — e.g. a medication or
      essential-health calendar, if she has one) → **visually locked**
      with an explanatory chip; it **cannot** be paused.
    - **[QA RISK]** Confirm the `nonSuperseable` items render with a lock
      and an explanation, and that toggling them is genuinely impossible.
      She should be able to *see at a glance* what survives the holiday.

13. She wants the **club Training** calendar to also pause for that week
    (no normal training in the break) **but keep the one-off clinic on**.
    She toggles **Training → pause**, then expands its **"but keep these
    events on"** list and ticks the **"School Holiday Clinic — U11s"**
    event → expects this to write an **`overrides/<event-id>.md`** opt-out
    so the clinic stays visible even though its parent calendar is paused.
    - **[QA RISK]** This is the crux: paused calendar + per-event keep-on
      override. If pausing Training also hides the clinic with no way to
      re-include it, that fails her real need. Verify the override file is
      written and the clinic survives.

14. **Confirm screen.** She expects a **mini month-preview** of the
    holiday week showing: the **clinic still on**, the **superseded
    routine events greyed/struck-through**, and any kept-on overrides
    visible. She reviews, taps **Confirm and materialize**.
    - **[QA RISK]** The preview must clearly distinguish *paused* (grey /
      strikethrough) from *kept on* (normal). If superseded events look
      identical to live ones, she can't trust it.

15. **Back on Schedule, holiday week.** She expects:
    - Routine / errands / weekly-reset events for that week **suppressed**
      (not present, or shown as paused) — *not deleted from history*.
    - The **clinic** present and normal.
    - `nonSuperseable` items still present.
    - On **Week / Month** views, a small **leaf glyph** on the affected
      dates indicating a vacation overlay is active; tapping it drills into
      *what's paused*.

16. **Spot-check a week OUTSIDE the holiday window** → expects all routine
    calendars **back to normal** there. The overlay is temporal — it
    pauses only inside its window, and nothing was destroyed. The
    recurring Tuesday training resumes after the break.
    - **[QA RISK]** Supersedence invariant: outside the window everything
      is untouched. If routines vanish *after* the holiday too, the overlay
      window is being applied wrong.

17. **Sync.** She taps per-repo **sync** on the club repo → expects the
    clinic event + the `overrides/` opt-out to push so the other
    volunteers see the clinic and know normal training is paused that week.
    (Family repo stays local — no sync, by design.)

## Success criteria

- Common-time finder ranked **real free slots across Family + Club**,
  correctly respecting the recurring training **and** its cancelled
  instance.
- Clinic created **from a slot**, written into the **shared club repo**
  with her author chip.
- Vacation overlay **pauses routine calendars only within the holiday
  window**, leaves `nonSuperseable` items locked-on, and keeps the clinic
  on via a per-event **override**.
- Nothing is deleted; weeks outside the window are untouched.
- Club-side changes sync; Family stays phone-only.

## Notes for QA replay

- Seed both repos with enough recurring + one-off content that the finder
  has a non-trivial busy-set, and so the supersedence picker has both
  pause-by-default and `nonSuperseable` calendars to show.
- On disk after Part B, assert: the routine events are **not** deleted
  (supersedence is an overlay, not a delete), and an
  `overrides/<clinic-event-id>.md` exists keeping the clinic on.
- This walk pairs the finder (Part A) with supersedence (Part B) because
  that's her actual term-break workflow: book the one special thing, pause
  the everyday things.

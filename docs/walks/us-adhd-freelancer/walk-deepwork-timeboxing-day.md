# Walk — Deep-work timeboxing day: build focus blocks, overlay on the work calendar, survive an interruption

**Persona:** Devon Reyes (see [`personality.md`](personality.md))
**Goal:** Plan one real deep-work day — build a timebox calendar of focus
blocks, layer it over the regular client-meeting calendar, drive it with
overlay toggles + priorities, and honestly record a focus block that gets
interrupted.
**Preconditions:** The `devon-life` no-origin repo from
[`walk-phoneonly-quickstart.md`](walk-phoneonly-quickstart.md) exists and
is active. Still phone-only, no remote.
**Features exercised:** two calendar kinds regular vs timebox (D.5 / UI-G),
overlay scheduling with priority + active_toggle + active_windows (D.5),
Timebox view mode (UI-G / RV-D), resolver layering (Phase E), passive /
inverted-habit deviation for an interrupted block (D.54 / Phase XX),
Markdown bodies (Phase EE).

---

## Part A — Set up the two calendars

1. Devon opens **Settings → Calendars** (or Schedule + the add-calendar
   entry point).
   → **Expect:** calendar list. Likely empty (the quickstart only made
   todolists). Empty/CTA state.

2. Create the regular meeting calendar. **New calendar** → name
   **"Client meetings"**, kind = **regular**, pick a color, priority
   leave default (~500).
   → **Expect:** `calendars/client-meetings/calendar.toml` with
   `kind = "regular"`, committed locally.

3. Create the timebox calendar. **New calendar** → name **"Deep work"**,
   kind = **timebox**, distinct color, priority **set higher, e.g. 700**
   (focus blocks should win the foreground when they collide visually).
   → **Expect:** `calendars/deep-work/calendar.toml` with
   `kind = "timebox"`, `priority = 700`.
   ⚠ **Confusion watch:** the regular vs timebox **kind** toggle (per
   ui-spec UI line ~1501) must be obvious at create time. If Devon can't
   tell which kind they picked, flag it.

4. Add a couple of real client meetings to **Client meetings** for today:
   - 11:00–11:30 "Fintech standup"
   - 15:00–16:00 "Nonprofit review call"
   → **Expect:** two one-off events under
   `calendars/client-meetings/events/<yyyy>/<mm>/`.

---

## Part B — Build the focus-block day

5. Devon switches the Schedule top-bar view-mode tab to **Timebox**.
   → **Expect:** Timebox view (UI-G). Since "Deep work" has no events yet
   and is the only timebox calendar, the empty state "no timebox events"
   with CTA appears (UI-G.6). Non-timebox calendars (Client meetings) are
   explicitly hidden in this view (RV-D).
   ⚠ **Confusion watch:** the meetings must NOT show here — Timebox view
   is focus-blocks-only. If "Fintech standup" appears, flag it.

6. Devon plans the day by adding focus blocks to **Deep work**:
   - 09:00–11:00 "Onboarding flow — fintech (DEEP)"
   - 11:30–12:30 "Game studio settings-menu wireframe"
   - 13:30–15:00 "Accessibility audit PDF — nonprofit"
   - 16:00–17:30 "Admin + invoices (shallow)"
   → **Expect:** four timebox events under `calendars/deep-work/...`.
   Note the deliberate gaps around the 11:00 standup and the 15:00 call
   so the focus blocks and meetings interleave rather than collide.

7. Add a Markdown body to the 09:00 block:
   ```
   ## Single goal
   Ship the revised onboarding flow to the client.

   - phone on Do Not Disturb
   - no Slack until 11:00
   ```
   → **Expect:** body persists; detail sheet renders heading + list themed
   (Phase EE).

8. Devon switches back to the **Day** view-mode tab.
   → **Expect:** the resolver (Phase E) builds the unified day: BOTH
   calendars layered — regular meetings AND timebox blocks render together
   (D.5: resolver renders both kinds layered). Devon sees standup at 11:00
   and the call at 15:00 sitting between their focus blocks.

---

## Part C — Drive it with overlays

9. Devon wants to see *only* focus blocks for a moment to sanity-check the
   plan. In the overlay/calendar toggle surface (per-repo overlay list),
   they toggle **Client meetings → off** (`active_toggle = false`).
   → **Expect:** Day view now shows only "Deep work" blocks; meetings
   disappear from the unified view. The toggle is non-destructive (the
   events still exist on disk; they're just filtered out of the active set
   per D.5 resolver).
   ⚠ **Confusion watch:** toggling a calendar off must not delete or
   "cancel" anything — it's a view filter. Verify the meetings come back.

10. Devon toggles **Client meetings → on** again.
    → **Expect:** meetings reappear, layered with the focus blocks.

11. **Priority collision test.** Devon deliberately drags/edits the 16:00
    "Admin + invoices" focus block to 15:00–16:00 so it now overlaps the
    "Nonprofit review call" (regular, priority ~500) — Deep work is
    priority 700.
    → **Expect:** per D.5 visual-collision rule, the higher-priority
    calendar (Deep work, 700) wins the **foreground** slot; the lower-
    priority meeting renders **banded/dimmed at the edge**. Tapping the
    slot opens a detail sheet showing **both** overlapping events.
    ⚠ **Confusion watch:** the lower-priority event must remain visible
    (dimmed/banded), not vanish — losing the meeting silently would be a
    scheduling-trust bug. Then Devon moves Admin back to 16:00.

12. **active_windows sanity (optional).** Devon sets the "Deep work"
    calendar `active_windows` to a date range that *excludes* a future
    vacation week, leaving today inside the window.
    → **Expect:** today is unaffected (today ∈ window); the focus blocks
    still render. (Full vacation/supersedence behavior is its own walk —
    here we just confirm an active window covering today doesn't hide
    today's blocks.)

---

## Part D — The interruption (the important part for this persona)

Devon is mid-09:00 "Onboarding flow (DEEP)" block. At 09:40 the fintech
client calls with a production issue and yanks them out for 40 minutes.
For an ADHD freelancer this is the make-or-break flow: recording the
interruption must be fast and must not feel like a punishment.

13. Devon opens the in-progress 09:00 focus block (e.g. from the Now/Next
    surface or by tapping the block in Day/Timebox view).
    → **Expect:** the detail sheet for the active block, showing it as
    currently in-progress.

14. Devon records that the block was interrupted. Using the passive /
    inverted-habit model (D.54 / Phase XX): the block's default state is
    **completed-by-schedule**, and a miss/interruption is an **explicit
    deviation** — Devon marks a deviation ("interrupted" / "cut short"),
    optionally noting "client prod call, ~40 min."
    → **Expect:** a deviation file is written, e.g.
    `deviations/<id>/<yyyy-mm-dd>.md` for this block, capturing the
    deviation kind + note. The block is NOT deleted and is NOT turned into
    a wall of red overdue — it's annotated as a deviation from the plan.
    ⚠ **Confusion watch (HIGH VALUE):** this is the persona's core need.
    Flag if (a) recording the interruption takes more than a couple of
    taps, (b) the language is punitive ("FAILED"/"MISSED" shaming),
    (c) there's no place to note *why*, or (d) it forces deleting/
    rescheduling the block instead of just annotating it.

15. Devon returns from the call and wants to salvage the afternoon. They
    drag the 13:30 "Accessibility audit PDF" block earlier / extend a
    block to reclaim the lost focus time.
    → **Expect:** drag-to-reschedule (Phase DD) moves the block; the file
    is rewritten; the unified Day view re-resolves with the new times.

16. End of day, Devon looks back. They open the day in Day/Timebox view.
    → **Expect:** the plan is visible with the 09:00 block clearly marked
    as deviated/interrupted, the rest completed-by-schedule. This is a
    record Devon can actually trust for billing — the deviation note tells
    them the prod call ate ~40 min of the fintech focus block.

---

## Pass criteria

- [ ] Two calendars created with the correct kinds (regular vs timebox).
- [ ] Timebox view shows ONLY timebox events; Day view layers both kinds.
- [ ] Overlay toggle hides/shows a calendar non-destructively.
- [ ] Priority collision: higher-priority block wins foreground, lower
      stays visible (dimmed/banded), detail sheet shows both.
- [ ] Interruption recorded as an explicit deviation, fast and non-
      punitive, with a free-text "why," without deleting the block.
- [ ] Drag-to-reschedule re-resolves the day.

## QA notes / likely defects

- Step 5: meetings leaking into Timebox view.
- Step 11: lower-priority event vanishing instead of dimming.
- Step 14: deviation flow being slow, buried, or shame-coded — this is
  the single most persona-critical check in the whole walk set.

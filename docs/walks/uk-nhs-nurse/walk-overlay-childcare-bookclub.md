# Walk — Childcare + book-club overlays, spot a clash

> Persona: **Priya Whitlock**, NHS staff nurse, Manchester. See
> [`personality.md`](personality.md). Goal of this walk: add a
> **childcare** overlay and a **book-club** overlay (each with its own
> priority + active windows), then toggle them on against her shifts to
> **spot the clash** between a late shift and the second-Thursday book
> club — weeks in advance, the whole point of the app for her.
>
> Features exercised (all real): per-repo overlay calendars with
> priority / active_toggle / active_windows / active_hours (D.5); regular
> calendars (D.5 `kind`); RFC5545 recurrence rule for the monthly book
> club (D.6 / Phase Y recurrence); overlay resolver + visible collision
> rendering (Phase E resolver, D.5); top-bar overlay toggles + per-repo
> overlay surface (Round 2.5); Day/Week schedule view (Phase G).
>
> Format: numbered test-script steps. **she taps X → expects Y.**
> ⚠ marks a confusion point worth a QA note.

## Preconditions

- Walk 1 done: repo `life` exists, with `shifts` (roster) and `home`
  (Google BIDI mirror) calendars already populated and visible.
- It is, say, early in the month; the next book club is the **second
  Thursday**, and the roster has Priya on a **late shift**
  (13:00–21:30) that day.

## Steps

### A. Add the `childcare` overlay calendar

1. She opens the **calendar / overlay management** surface (per-repo
   overlay list, Round 2.5) and chooses **add a new calendar** → expects
   a create-calendar sheet asking name, kind, colour, priority.
2. She names it **`childcare`**, kind **regular**, gives it a distinct
   colour, sets a **low-ish priority** (e.g. 300 — lower than `shifts`)
   → expects the resolver to let `shifts` win the foreground slot on a
   collision while `childcare` still renders banded/dimmed (D.5).
   ⚠ **QA:** Priya thinks in "what matters most" not numbers 1–1000.
   Verify priority is presented in human terms (or with a clear
   high/normal/low affordance), not as a bare integer she must guess.
3. She sets an **active window** spanning the school term (e.g.
   `start = "2026-09-02"`, term end) so childcare only overlays during
   term → expects a date-range picker, and expects the calendar to be
   considered only inside that window (D.5 active_windows).
   ⚠ **QA:** Active-windows only gate *whether the calendar is
   considered*, they do not clip individual instances (D.5). Confirm the
   UI copy doesn't imply it trims events, which would mislead her.
4. She adds a couple of childcare entries for the book-club week — e.g.
   "Niamh → after-school club" Mon/Tue, "Tom's mum has Niamh" Wed, and
   importantly **nothing arranged for the book-club Thursday late** →
   expects these to save into `childcare` and appear on the timeline.

### B. Add the `bookclub` overlay calendar with a monthly recurrence

5. She adds another calendar **`bookclub`**, kind **regular**, a third
   colour, **low priority** (e.g. 200) → expects it created as its own
   toggleable overlay.
6. She creates the book-club event: **"Book club"**, **19:30–21:30**, on
   the **second Thursday**, and sets it to **repeat monthly on the
   second Thursday** → expects a recurrence option that produces an
   RFC5545 monthly-by-weekday rule (D.6 — stored as a recurrence rule,
   materialised lazily at view-time, not 12 copies on disk).
   ⚠ **QA:** "Second Thursday of every month" is a `BYDAY=2TH` style
   rule. Verify the recurrence editor can express *nth-weekday-of-month*
   (not just "every 4 weeks", which would drift). If it can't, that's a
   real gap for this persona — flag it.
7. She saves → expects the book-club event to appear on the second
   Thursday of *this* month **and** subsequent months as she scrolls
   forward (lazy materialisation), with no stray instances on wrong
   Thursdays.

### C. Toggle overlays + spot the clash

8. She returns to the **schedule** and ensures the relevant overlays are
   toggled **on** — `shifts`, `childcare`, `bookclub` (and `home`) — via
   the per-repo overlay toggles → expects each to layer onto the same
   Day/Week view, each in its own colour.
   ⚠ **QA:** With 4 overlays on a Week view, verify legibility — colours
   distinguishable, bands not unreadable. Round 2.23 added per-calendar
   `colorSeed` band backgrounds + zoom; confirm a 4-overlay week is
   still readable at default zoom.
9. She navigates to the **book-club Thursday** → expects to see the
   **late shift (13:00–21:30)** and **Book club (19:30–21:30)**
   occupying the **same evening slot**, rendered as a **visible
   collision**: the higher-priority `shifts` event takes the foreground,
   `bookclub` renders banded/dimmed at the edge (D.5 collision render).
   ⚠ **QA:** This collision *is the product* for Priya. Verify it is
   genuinely **eye-catching** at a glance on a Week view — not something
   she only discovers by tapping. If the dimmed band is too subtle to
   notice while scanning the week, that's the single most important
   finding in this walk.
10. She **taps the slot** → expects the detail sheet to show **every
    overlapping event** in that slot (the late shift *and* book club),
    per D.5 "Detail sheet on tap shows every overlapping event".
11. She also notices the **childcare** layer shows **nothing arranged**
    for that Thursday evening → expects the absence to be visible too
    (i.e. she can see at a glance that even if she went, Niamh has no
    cover for a 19:30 book club on a day she's working till 21:30).
    ⚠ **QA:** "Visible absence" is harder than visible presence. Note
    whether the empty childcare slot communicates "no cover arranged" or
    just looks like an empty calendar — this is a UX judgement call for
    the persona, worth flagging rather than asserting a pass.

### D. Confirm overlays toggle independently

12. She toggles **`bookclub` off** → expects the book-club band to
    disappear from the timeline and the late shift to occupy the slot
    cleanly (active_toggle = false hides it everywhere, D.5).
13. She toggles it back **on** → expects the clash to reappear,
    confirming the toggle is non-destructive (just a view filter, not a
    delete).

## Success criteria

- `childcare` and `bookclub` exist as independent overlay calendars with
  their own priority + active windows.
- Book club is a single monthly *nth-Thursday* recurrence, not copies.
- On the book-club Thursday, the late-shift × book-club clash is
  **visible at a glance** on the Week view, with the lower-priority book
  club banded behind the foreground shift.
- Tapping the slot lists every overlapping event.
- Overlays toggle on/off independently and non-destructively.

## Open confusion points gathered (for QA triage)

- Priority presented as a raw 1–1000 integer vs human high/normal/low
  (step 2).
- Active-window copy implying it clips events (step 3).
- Recurrence editor expressing *nth-weekday-of-month* vs only every-N-
  weeks (step 6).
- Legibility of a 4-overlay Week view (step 8).
- **Clash visible-at-a-glance** vs only-on-tap — the headline finding
  (step 9).
- "No childcare cover" communicated as meaningful absence vs blank
  calendar (step 11).

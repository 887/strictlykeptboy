# Walk — Handle a shift swap (Thursday late → Saturday early)

> Persona: **Priya Whitlock**, NHS staff nurse, Manchester. See
> [`personality.md`](personality.md). Goal of this walk: a colleague
> asks Priya to **swap her Thursday late for a Saturday early**. She
> makes the edit in strictlykeptboy with the *minimum* change — editing
> the single affected occurrence, not the whole pattern — confirms it
> **pushes back via CalDAV** so Tom sees it on Google, and **re-checks
> for new clashes** (does the freed-up Thursday now let her make book
> club? does the new Saturday early clash with anything at home?).
>
> Features exercised (all real): recurrence rule + **exceptions-as-files**
> (D.6 — `cancel` / `override` / `note` at
> `exceptions/<rule-id>/<yyyy-mm-dd>.md`); single-file edit semantics
> (D.3 — edit one occurrence = one file, never rewrite the rule);
> CalDAV **BIDI** push-back (Phase Y.5/Y.6); overlay clash re-check
> (D.5 resolver); conflict UI shared between git + CalDAV (Y.7).
>
> Format: numbered test-script steps. **she taps X → expects Y.**
> ⚠ marks a confusion point worth a QA note.

## Preconditions

- Walks 1 + 2 done. `shifts`, `home`, `childcare`, `bookclub` all live
  in repo `life`.
- The roster Thursday holds a **late shift (13:00–21:30)** — this is
  the one to give away. It is the same Thursday as **book club** (the
  clash from Walk 2).
- The target **Saturday** is currently a day off; she'll take an **early
  (07:00–15:30)** instead.
- How the Thursday shift is stored depends on Walk-1 path:
  - If imported via **.ics**, each shift is its own event file (no
    recurrence rule) → swapping = **edit / move that one event**.
  - If pulled via **CalDAV** as a recurring pattern, the Thursday is an
    instance of a recurrence rule → swapping = **add an exception**
    (`cancel`/`override`) for that date.
  This walk covers both, because both are real for this persona.

## Steps

### A. Open the Thursday shift

1. She opens the **schedule**, navigates to the book-club Thursday, and
   **taps the late shift** → expects the **event detail sheet** with
   start/end, calendar (`shifts`), and an **Edit** affordance (G.7).
   ⚠ **QA:** If `shifts` is a **CalDAV pull-only** mirror, it's marked
   read-only-from-app (Y.4) — Edit should be **disabled / explained**
   ("this shift is mirrored from <server>; change it upstream"), per the
   pull-only contract. Verify she gets a *clear reason*, not a silent
   no-op. For the **.ics-imported** case, Edit should be available.

### B. Path 1 — .ics-imported shift (single event move)

2. (Imported case) She taps **Edit** on the Thursday late → expects an
   event editor with the date/time fields.
3. She changes the **date to Saturday** and the **time to 07:00–15:30**
   (the early) → expects to save and have the **single event file
   rewritten** in place (D.3 "edit an event = rewrite the single file"),
   with the author/attribution preserved.
   ⚠ **QA:** Verify the event keeps its identity (same id / file moves
   to the correct `events/<yyyy>/<mm>/` bucket for the new date) rather
   than orphaning the old file and creating a duplicate. Bucket path is
   start-date-derived (C.3) — confirm a cross-month move re-buckets
   cleanly.
4. She returns to the schedule → expects the **Thursday late to be gone**
   and a **Saturday early** to appear.

### C. Path 2 — recurring shift (exception file)

> If the roster came in as a recurrence (CalDAV) and is *editable*
> (BIDI, not pull-only), the correct minimal change is an exception for
> the one date — never editing the rule and silently dropping the date.

5. (Recurring case) She taps **Edit** on the Thursday instance → expects
   the app to ask whether to change **"only this occurrence"** vs
   **"this and all future"** (D.6 — editing one instance must not
   rewrite the rule).
   ⚠ **QA:** This prompt is load-bearing. Verify it appears and that
   **"only this occurrence"** is the obvious default for a one-off swap.
   A nurse must not accidentally change every future Thursday.
6. She picks **only this occurrence** and edits it to Saturday
   07:00–15:30 → expects the app to write an **`override` exception
   file** at `exceptions/<rule-id>/<yyyy-mm-dd>.md` for that Thursday
   (D.6), suppressing the Thursday instance and placing the moved
   occurrence on Saturday — the **rule file itself untouched**.
   ⚠ **QA:** Confirm no edit to the recurrence rule file (no silent date
   drop). The Thursday should vanish via the exception, not by the rule
   changing — this is the "shift swap = one exception file" property the
   whole design rests on (D.3 / D.6). Flag if the rule file is touched.
7. She returns to the schedule → expects the **Thursday instance gone**
   (suppressed by the exception) and the moved occurrence on **Saturday**,
   while **next month's** second-Thursday-equivalent shift (if the rule
   recurs) is **unaffected**.

### D. Push back via CalDAV so Tom sees it

8. She triggers a **sync** (manual sync button, or waits for the
   per-mirror interval) → expects the change to **push to Google over
   CalDAV** for any BIDI-mirrored calendar, so the swap propagates to
   her Google account and thus to Tom's shared view (Y.5/Y.6).
   ⚠ **QA:** Clarify *which* calendar pushes. Her **shifts** are likely
   pull-only or .ics (not pushed to Google); her **home** (`home`) is
   the BIDI one. If she wants Tom to see her shifts, the shifts must be
   in a calendar that *is* pushed. Verify the app makes it clear whether
   the swapped shift will actually reach Google, or whether she'd need
   shifts in a push-enabled calendar. This is a genuine
   gotcha — document the observed routing.
9. (If the upstream changed the same event meanwhile) She expects, on
   sync, a **conflict surfaced via the shared conflict UI** (Y.7 →
   `ConflictSource.CALDAV` into the git conflict registry) with
   keep-mine / keep-theirs / abort (J.5/J.6) — labelled with the CalDAV
   server, not a cryptic git message.
   ⚠ **QA:** Verify a CalDAV conflict reads in plain language for a
   non-technical user (what changed, on which side) rather than a raw
   3-way TOML/text diff. The v1 conflict surface is raw 3-way text
   (J.6) — flag whether that's acceptable for this persona or needs the
   plain-language framing.

### E. Re-check for new clashes

10. She navigates back to the **(now freed) Thursday** → expects the
    late shift to be gone, so **book club (19:30) no longer collides
    with a shift** — the slot should read as *available for book club*.
    ⚠ **QA:** Verify the previously-shown clash is **cleared** in the
    overlay view immediately after the swap (resolver recompute), not
    stale. The payoff of the whole exercise is "I can make book club
    now" showing up at a glance.
11. She checks **childcare** for that Thursday → expects to confirm
    Niamh's cover question is now simpler (she's off, so she can do
    book club / Tom can do bedtime) — visible on the `childcare`
    overlay.
12. She navigates to the **new Saturday early** → expects to **scan for
    fresh clashes**: does 07:00–15:30 collide with anything in `home`
    (Tom's football? a school thing? Niamh's swimming?) or `childcare`?
    Any collision should render as a **visible band** exactly as in
    Walk 2 (D.5).
    ⚠ **QA:** The whole point of swapping in-app rather than just texting
    the colleague is *catching the new clash*. Verify a Saturday-morning
    collision (e.g. Niamh's Saturday swim at 09:00 in `home`) shows up
    immediately on the new early shift. If she has to go hunting, the
    swap workflow loses its value.

## Success criteria

- The Thursday late is removed with the **minimal correct change**:
  a single-file event edit (.ics path) **or** a single `override`
  exception file (recurring path) — never a rule rewrite that drops the
  date silently.
- The moved Saturday early appears, correctly bucketed.
- The change pushes out via the BIDI CalDAV mirror (with clear feedback
  about *which* calendar reaches Google).
- The old Thursday clash with book club is **cleared**; any new
  Saturday-morning clash is **surfaced**.
- Any CalDAV conflict is surfaced via the shared, labelled conflict UI.

## Open confusion points gathered (for QA triage)

- Edit disabled on a pull-only mirrored shift, with a clear reason
  (step 1).
- Cross-month event re-bucketing on a single-event move (step 3).
- "Only this occurrence" prompt present + sensibly defaulted (step 5).
- Exception file written, rule file untouched (step 6) — the keystone.
- Which calendar actually pushes to Google — shifts vs `home` (step 8).
- Plain-language CalDAV conflict vs raw 3-way diff (step 9).
- Cleared old clash + surfaced new Saturday clash recompute promptly
  (steps 10, 12).

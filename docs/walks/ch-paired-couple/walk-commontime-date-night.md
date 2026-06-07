# Walk — common-time date night + a trip that pauses the routine

> Persona: **Lena Hofmann** + **Théo Brunner**, Zürich. SFW-but-charged,
> consenting-adult lifestyle organisation — see `personality.md`. Keep it
> warm and structural.
>
> Preconditions: `walk-paired-repos-setup.md` and
> `walk-strictlykept-mode-and-identity.md` complete. Both repos exist,
> mutually shared with write access, both on each device's repo picker.
> `lena-routine` is strictly-kept with a passive daily routine;
> `theo-calendar` is free.
>
> Goal: (A) use the **common-time finder** (Together tab) across **both
> repos** to find a free evening and book a **date night**; (B) drop a
> **vacation / supersedence overlay** for a weekend trip that **pauses the
> routine** without destroying it, while keeping the date night and
> anything marked `nonSuperseable` (meds / pet care) on; (C) confirm the
> routine **resumes** automatically afterwards.
>
> Format: numbered steps, "tap X → expect Y", **[QA]** = confusion point.

---

## Part A — Common-time finder → date night across both repos

> The common-time finder lives on the **Together tab** (UI-M.1) and needs
> **≥ 2 repos** (RV-E). It builds a busy set across selected repos ×
> calendars × range, subtracts busy, returns ranked free slots
> (contiguity → proximity-to-ideal → earliest). Both repos must be
> readable by the device running the query — true here, since they share
> mutually.

1. On Lena's device, open the **Together tab** → **M.1 Common-time
   finder**. **Expect** the form. **[QA]** if fewer than two repos were
   added, expect the empty state "Add at least two repos to find common
   time" — confirm it does NOT show here (both repos present).
2. **Repos:** multi-select chips → tick **`lena-routine`** and
   **`theo-calendar`** (FilterChip multi-select, UI-M.2). **Expect** both
   selected.
3. **Calendars in those repos:** expand per-repo sub-pickers. For
   `lena-routine` include the **Routine quotidienne** overlay (so the
   routine counts as busy); for `theo-calendar` include his work calendar.
   **[QA]** confirm only *active-toggled* calendars are offered (RV-E
   filters on `activeToggle`).
4. **Date range:** this Thu → Sun. **Duration:** **2 hours**. **Days:**
   leave all on. **Time:** **18:00 – 23:00** (an evening). Optionally set
   an **ideal time** ~19:30 for ranking. Tap **Find slots**.
5. **Expect** a ranked card list of free 2h evening slots where **both
   repos are free** (e.g. "Fri · 19:30 – 21:30 (2h) — lena-routine +
   theo-calendar both free"). **[QA]** verify the routine's 18:30
   movement / 22:30 evening entries correctly **carved out** busy time —
   i.e. inverted/passive habits still count as busy in the finder (they
   are scheduled events). Flag if passive habits are wrongly treated as
   free.
6. **Expect** ranking: longer/contiguous slots first, then nearest to the
   ideal 19:30, then earliest (RV-E). **[QA]** sanity-check the top slot
   isn't an obviously worse one.
7. Tap **[Create event →]** on the chosen Friday slot → **expect** a
   quick-add form **pre-filled** with that date/time, requiring a
   **target repo + calendar** pick (UI-M.4).
8. Target it at a **shared / joint** context. Sensible choice:
   `theo-calendar` (free mode, mutual write) under a "Together / Date
   nights" calendar, titled **"Soirée à deux"**. Optionally set
   `private = true` (D.57) so the lockscreen shows only "Scheduled event".
   Save → **expect** the date night event created, with the **author
   chip** = whoever created it (Lena here).
9. Sync → **expect** it appears on **both** devices' schedules. **[QA]**
   if Lena targeted `theo-calendar`, confirm her **write access** let the
   commit land (no read-only block) — proves the mutual-write setup.

## Part B — Weekend trip overlay that supersedes the routine

> Supersedence (D.75–D.78) is a **temporal overlay, not deletion**. A
> vacation calendar declares `supersedes = [...]` +
> `superseded_during = [{from, to}]` in its `calendar.toml`; the resolver
> **hides** the listed calendars within the range. Superseded events are
> **not deleted** — they show strikethrough+grey in manage-overlays with a
> per-event "show this one anyway" override. `nonSuperseable` (calendar
> flag or event tag) survives — meds / pet care never silently skipped.

10. Create a **vacation calendar** for the trip — e.g. in
    `theo-calendar` (free, mutual write so either can manage it), named
    **"Weekend Grindelwald"**, covering **Fri 17:00 → Sun 20:00**.
11. Configure the overlay: set it to **supersede the routine** — i.e. add
    `supersedes = ["<lena-routine: Routine quotidienne cal-id>"]` and
    `superseded_during = [{from = <Fri 17:00>, to = <Sun 20:00>}]` in the
    vacation calendar's `calendar.toml`. The UI surface is a "pause these
    calendars while I'm away" picker. **[QA]** verify:
    - The plain-language UI label ("pause my routine while away") is what
      Lena sees, even if the field is `supersedes`. Flag if the raw word
      "supersedence" leaks into user copy.
    - The schema keys (`supersedes`, `superseded_during`, `from`, `to`)
      are English despite the German/French calendar name.
12. **Expect** the resolver to **hide** the routine entries within Fri
    17:00 – Sun 20:00 on **both** schedules (D.75). **[QA]** the routine
    items in that window vanish from the day/week view — but...
13. ...open **manage-overlays** → **expect** the superseded routine items
    still visible there as **strikethrough + grey**, each with a per-event
    **"show this one anyway"** override (D.75 / D.77). **[QA]** confirm
    they are hidden-not-deleted; the routine still exists on disk.
14. **`nonSuperseable` survival test:** ensure an entry that must never be
    skipped — say a **medication** event, or **pet feeding** — is marked
    `nonSuperseable` (calendar flag or event tag; D.76). **Expect** it
    **still renders** inside the trip window despite the supersedence
    (S4/S5, D.78). **[QA]** this is the safety invariant: vacation must
    not silently skip meds / pet care. Verify it shows.
15. **Date-night survival:** the **"Soirée à deux"** from Part A lives in
    a *different* calendar (date-nights), which is **not** in the
    `supersedes` list → **expect** it **still renders** during the trip
    window. **[QA]** confirm only the *listed* routine calendar is paused;
    unlisted calendars (date night) are unaffected (S1/S2 — non-cascading).
16. **Per-event force-show (override):** suppose Lena *does* want her
    22:30 evening wind-down one of the trip nights → use the
    **"show this one anyway"** override on that instance. **Expect** an
    `overrides/<superseded-cal-id>/<event-id>/<yyyy-mm-dd>.md` with
    `kind = "force-show"` (D.77), and that single instance reappears while
    the rest stay paused. **[QA]** confirm the override is one file, one
    instance, and doesn't un-pause the whole routine.
17. **Author / mode interaction:** because the vacation calendar is in
    `theo-calendar` (free mode), no review-feed entry is required for it.
    If instead the overlay were authored into `lena-routine` (strictly-
    kept), **expect** a `reviews/<sha>/reviewable_change.md` to
    materialise (D.84). **[QA]** confirm review-feed materialisation
    tracks the *repo's* mode, not the calendar's purpose.

## Part C — Routine resumes after the trip

18. Advance to **Sun 20:00+** (or after the `superseded_during` window) →
    **expect** the routine entries render normally again on both schedules
    — the overlay's range ended, nothing was deleted, the recurrence just
    reappears (D.75). **[QA]** confirm **no manual "un-pause" step** was
    needed; the temporal range did it.
19. **Expect** Monday's 07:00 ancrage matinal is back, defaulting to
    **completed-by-schedule** (inverted habit), with no residue from the
    paused weekend. **[QA]** confirm the paused days produced **no
    spurious deviations** — supersedence hid them, it did not mark them
    skipped. (Hidden ≠ missed. This is the key semantic difference from
    the previous walk's deviation flow.)

---

## Exit state

- A **date night** ("Soirée à deux") booked from a common-time slot
  across both repos, visible on both devices, surviving the trip overlay.
- A **trip overlay** ("Weekend Grindelwald") that **paused the routine**
  Fri 17:00 – Sun 20:00 via supersedence, kept `nonSuperseable`
  meds/pet-care on, allowed a per-event force-show, and **auto-resumed**
  afterwards with no spurious deviations.

## Confusion points to watch (summary for QA)

- **[QA]** Common-time finder needs ≥ 2 repos; both must be readable
  (mutual-write setup makes this work) — steps 1, 9.
- **[QA]** Passive / inverted habits must count as **busy** in the finder
  (step 5).
- **[QA]** Plain-language "pause my routine" label vs raw "supersedence"
  in copy (step 11). Schema keys English despite localised calendar names.
- **[QA]** Superseded = hidden-not-deleted; still in manage-overlays,
  still on disk (steps 12–13).
- **[QA]** `nonSuperseable` meds / pet care survive the pause — the safety
  invariant (step 14).
- **[QA]** Only *listed* calendars pause; non-cascading; date night and
  unlisted calendars unaffected (step 15).
- **[QA]** Per-event force-show is one `overrides/` file, one instance
  (step 16).
- **[QA]** Review-feed materialisation follows the *repo's* mode, not the
  calendar's purpose (step 17).
- **[QA]** Routine auto-resumes by range end with **no spurious
  deviations** — hidden ≠ missed (steps 18–19).

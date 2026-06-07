# Walk — Shared club repo + recurring Tuesday training + skip one week

> Persona: Marnie Faulkner (see `personality.md`). Goal: **connect the
> shared Wynnum Waders Netball repo** via **GitHub HTTPS (OAuth device
> flow)**, add a **recurring Tuesday training** (RRULE), **cancel one
> instance** for a public holiday (exception-as-a-file), and confirm
> **author chips** on shared events. Replayable as a QA script.
>
> Features exercised: add-repo remote path (UI-I.2 / I.3), GitHub OAuth
> device flow (B.5), shared / collaborator repo, RRULE builder
> (recurrence, RV-B), exception cancel = `exceptions/<rule-id>/<date>.md`
> (UI / D.3), author chips (UI-BB.6), sync.
>
> Confusion risks flagged **[QA RISK]**.

## Preconditions

- Walk 1 done: she already has a phone-only `Family` repo.
- She is **online** (the OAuth device flow + clone need network).
- She has a GitHub account and is already a **collaborator** with write
  access on the club repo (set up by a previous treasurer).
- The shared repo already has a couple of events committed by another
  volunteer (e.g. **Priya**) so author-chip differentiation is visible.

## Steps

1. From the **Schedule** view she opens **Repos** in the left nav rail
   (or **Settings → Repos**) and taps **Add repo** → expects the
   add-repo flow with a top-level branch choice: **Create local-only**
   vs **Connect to a remote**.

2. She taps **Connect to a remote** → expects to choose a **provider**.
   She picks **GitHub**.

3. She enters the repo **URL** (the club's GitHub HTTPS URL) → expects a
   URL field and a **Next** that validates the shape.
   - **[QA RISK]** She'll paste the browser URL of the repo. The app must
     accept the normal `https://github.com/<org>/<repo>` form (and ideally
     a `.git` suffix). If it demands an exact clone URL she doesn't have
     handy, note it.

4. **Auth method.** She chooses **GitHub OAuth (device flow)** rather than
   SSH or PAT (she's never made an SSH key) → expects a **device-flow
   screen**: a short **user code**, a **"Open github.com" button**, and a
   waiting/polling state.

5. She taps **Open github.com**, the browser opens the device-activation
   page, she **enters the code and approves** → returns to the app →
   expects the app to detect approval (it's polling), store the token in
   EncryptedSharedPreferences keyed per `(repoId, remoteName)`, and move
   on. No password was ever typed in-app.
   - **[QA RISK]** If she's slow on the github.com side, the polling state
     must keep waiting (and ideally show "still waiting…"), not error out.
     Also: a token expiry later should silently refresh on 401, not bounce
     her to re-auth mid-task.

6. **Identity for this repo.** She sets the per-repo identity to
   **"Marnie F (Coord)"** → expects this to be the author stamped on her
   commits here, distinct from her `Family` identity. She picks a
   **default calendar** (e.g. the club's `Training` calendar) → taps
   **Finish**.

7. The app **clones** the repo → expects a progress indicator, then the
   new repo appearing in the **top-bar repo switcher** with a **sync
   status badge** (not the local/house badge — this one has a remote).

8. She switches the active repo to **Wynnum Waders Netball** via the
   top-bar switcher and opens **Schedule** → expects to see the events
   another volunteer already committed (e.g. Priya's), each with an
   **author chip showing Priya's avatar/identity**, not hers.
   - **[QA RISK]** The author chips on pre-existing events must resolve to
     the *original committer*, not be re-stamped as Marnie just because she
     cloned. This is the whole reason she left Google Calendar.

9. **Add the recurring training.** She taps **+ / FAB** → quick-add →
   she taps **More fields…** to reach the full event editor (she needs
   recurrence) → expects the full-screen editor.

10. She fills: title **"Under-11s Training"**, calendar **Training**,
    start **Tue 5:30pm**, end **Tue 6:30pm** → then taps **Make
    recurring** → expects the **RRULE builder** (frequency / interval /
    end-condition pickers).
    - **[QA RISK]** She thinks in "every Tuesday during term", not in
      RFC5545. The builder must let her express *weekly, on Tuesday,
      until <end-of-term date>* with plain pickers. Note any place it
      shows raw `FREQ=WEEKLY;BYDAY=TU` text instead of plain language.

11. She sets **Frequency = Weekly**, **Interval = 1**, **Repeat on =
    Tuesday**, **Ends = on a date** = the last week of term → taps
    **Done** on the builder, then **Save** on the editor → expects the
    editor to write the **recurrence rule file** (one rule file, no
    materialised per-week copies), commit it with an auto message under
    her **"Marnie F (Coord)"** identity, and return to Schedule.

12. She switches schedule view to **Month** → expects a Tuesday training
    chip on **every Tuesday** within the term window (materialised at
    view-time by the resolver), each carrying **her** author chip.
    - **[QA RISK]** Confirm there is exactly *one* rule file behind all
      those chips (no week-by-week files). If she later edited the series
      she'd expect to touch one thing.

13. **Skip the public-holiday week.** One Tuesday lands on a Queensland
    public holiday — no training that week. She taps **that single
    Tuesday's training chip** → event detail → she chooses to
    **delete / cancel this occurrence**. Because it's a recurring
    instance she expects a prompt offering **"This instance only" / "This
    and future" / "Entire series"** (default focus on the least
    destructive **This instance only**).

14. She picks **This instance only** → expects the app to create an
    **`exceptions/<rule-id>/<that-date>.md`** file with `kind = "cancel"`
    and commit it. **The rule file itself must NOT be edited** to silently
    drop the date.
    - **[QA RISK]** This is core data-model behaviour. If the app silently
      rewrites the rule's RRULE or deletes the rule, that's a bug. The
      cancellation must be an additive exception file.

15. Back on **Month** view → expects the holiday Tuesday's training chip
    to now be **gone** (resolver applies the cancel exception), while
    **every other Tuesday stays**. Spot-check the prior and following
    Tuesday are intact.

16. **Sync to the others.** She taps the **per-repo sync** affordance (in
    the repo card / Repos pane) → expects a push of her new rule file +
    the exception file, and an updated **last-synced** + ahead/behind
    counts going to zero.
    - **[QA RISK]** If Priya pushed something in the meantime, she should
      get a clean rebase (one-file-per-entity = conflict-free by design),
      not a merge-conflict wall over a calendar change.

17. (Optional verification) She opens the cancelled Tuesday's date again →
    expects either no training event, or a clearly-marked
    "cancelled this week" state — **not** a phantom event she has to
    delete again.

## Success criteria

- Club repo connected via **GitHub HTTPS OAuth device flow**, no SSH key,
  no in-app password.
- Pre-existing events show **other volunteers'** author chips; her new
  events show **"Marnie F (Coord)"**.
- One **recurrence rule file** drives all Tuesday trainings.
- One Tuesday cancelled via an **exception file**, rule untouched, only
  that week disappears.
- Changes **sync** back to the shared repo for the other volunteers.

## Notes for QA replay

- Seed the shared repo with at least one event committed under a
  different identity before this walk, or step 8's author-chip check can't
  be verified.
- The device-flow step (4–5) needs a real or stubbed GitHub OAuth app;
  for an automated replay, stub the polling response.
- Watch step 14 closely on disk: assert the presence of
  `exceptions/<rule-id>/<date>.md` and the *absence* of any edit to the
  rule file.

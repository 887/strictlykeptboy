# Walk — Onboarding + a phone-only family calendar

> Persona: Marnie Faulkner (see `personality.md`). Goal: first launch,
> get through the setup wizard, create a **phone-only (no-origin) family
> calendar**, and add a couple of this-week events — all with **no
> sign-in**. Replayable as a QA script.
>
> Features exercised: mandatory first-launch wizard (D.54), Unaligned /
> private alignment (K.4), phone-only / no-origin repo (D.74, K.8),
> quick-add event (UI-I.2), Schedule view.
>
> Format: numbered steps. `she taps X → expects Y`. Confusion risks are
> flagged **[QA RISK]**.

## Preconditions

- Fresh install of strictlykeptboy, never launched.
- Device offline is fine (this whole walk is local — a good airplane-mode
  smoke test).
- No GitHub account in play this walk.

## Steps

1. She launches the app for the first time → expects a **splash** (bat
   mascot, build/version line) then the **Welcome screen** of the setup
   wizard. The wizard is mandatory on first launch; there is no empty-app
   shortcut.

2. **Welcome screen.** She sees the bat mascot waving and a single
   "let's go" CTA. She taps **let's go** → expects to advance to species
   selection.

3. **Species selection (8 tiles).** She doesn't care about the avatar
   creature; she taps the **bat** tile (the default) → expects to advance
   to Alignment.
   - **[QA RISK]** If she skips without choosing, the bat default must
     apply silently — she should never be blocked here.

4. **Alignment screen.** Four options: Dominant / Submissive / Switch /
   **Unaligned (private)**. She wants none of the lifestyle framing, so
   she taps **Unaligned (private)** → expects this to turn the kink /
   lifestyle surfaces **off** downstream (it sets neutral mode via
   `WizardDraft.normalize()`).
   - **[QA RISK]** After choosing Unaligned-private, the praise /
     honorific / pronoun screen and the `kink` role tile must be
     suppressed. If she still gets asked for a "praise term" or sees a
     kink template, that's a bug for this persona.

5. **Lifestyle screen.** She sees Single / Partnered × free / strict.
   Her partner exists but this is *her* household calendar, and she wants
   the plain version. She picks **Partnered** + **free** → expects the
   phrasing to compute to a neutral "routine" register, not "strictly-kept".

6. **Roles screen (multi-select grid).** `self-care` is always-on and
   locked (she can't toggle it). She's setting up *family logistics*, so
   she picks **Family / Household** and maybe **Errands** → expects her
   selections to drive which calendars get scaffolded later. She taps
   **Continue**.
   - **[QA RISK]** The `kink` tile must be hidden here (neutral mode from
     step 4). Note if it appears.

7. **Templates-per-role screen.** Each chosen role expands with atomic
   template chips, all on by default. She glances, leaves them on (she'll
   prune later), taps **Continue** → expects to reach Git setup.

8. **Git setup screen (three cards).** She sees:
   - **Phone-only** (default + recommended)
   - Self-hosted Forgejo / Gitea (URL + OAuth client ID)
   - GitHub

   This is the moment that sold her on the app: she does **not** have to
   sign in. She taps **Phone-only** → expects the wizard to proceed with
   no provider / URL / auth prompts at all.
   - **[QA RISK]** "Phone-only" must be visibly the recommended default
     and must require zero further auth taps. If tapping it still routes
     her into an OAuth code screen, that's a hard fail for this persona.

9. **Calendar scaffolding (the wizard materialises).** The wizard runs
   `WizardScaffolder.materialize()` → expects: per-role calendars created
   (Family/Household, Errands, self-care), a small set of onboarding
   standing tasks, an `identity.toml` written in neutral register, and a
   single initial commit via the **local-only** repo init (no remote).
   She expects a brief progress indicator, not a network spinner.

10. **Done / handoff screen.** She sees a "next up" now-card preview and
    an **Open my calendar** CTA. She taps **Open my calendar** → expects
    to land on the **Schedule** view (agenda is the default schedule view).

11. **First look at Schedule.** She expects today's date, the left nav
    rail (Schedule / Tasks / Repos / Wizard / Settings), and a top bar
    with the **repo switcher** showing her newly-created repo. The view is
    mostly empty (just any seeded onboarding items) → that's expected.

12. She wants this repo named clearly. She taps the **repo switcher**
    in the top bar → expects a filterable list with circular repo icons.
    Her new repo should carry a small **"local" house badge** (no
    sync/error badge) because it has no remote.
    - **[QA RISK]** Confirm the local/house badge renders and that **no
      sync button / error badge** shows for a no-origin repo. A sync
      affordance on a phone-only repo would confuse her ("sync to what?").

13. She opens **Repo settings** (long-press or settings affordance on the
    repo) → expects to edit **display name** to `Family`, set an icon /
    color seed, and see a **Remotes** section that reads *"No remotes —
    this repo lives only on this device"* with an **Add a remote** CTA she
    can ignore. She saves → expects the top-bar switcher to now show
    `Family`.

14. Now she adds this week's events. From the **Schedule** view she taps
    the **+ / FAB** (or taps an empty hour in Day view) → expects the
    **quick-add sheet**: title, datetime (pre-filled from the tap),
    calendar (pre-filled to the default), **Save** / **More fields…**.

15. **Event 1 — "Tilly netball training".** She types the title, sets
    **Tue 5:00pm–6:00pm**, leaves calendar = Family → taps **Save** →
    expects the sheet to dismiss and the event to appear on the day band.
    (She'll later move *training proper* into the shared club repo —
    walk 2 — but for now it's a family reminder.)

16. **Event 2 — "Cooper AFL game".** She taps **+** again, title +
    **Sat 9:00am–10:30am**, Family calendar → **Save** → expects it on
    Saturday's agenda.

17. She opens **Event 1** to sanity-check → expects the **event detail**
    (full-screen) with title, datetime, an **author chip** showing her
    identity ("Marnie") @ Family, and Edit / Convert-to-task / Delete
    actions. The author chip confirms attribution works even on a solo
    local repo.

18. She backs out to **Schedule** and switches the schedule view to
    **Week** via the top-bar view tabs → expects both events laid out on
    Tue and Sat with no double-booking and no errors.

## Success criteria

- Reached a working calendar with **zero sign-in** and zero network.
- The repo is **phone-only**: house badge present, no sync button.
- Neutral mode held all the way through (no praise/honorific/kink prompts).
- Two events created via quick-add, both visible in Day and Week, each
  carrying her **author chip**.

## Notes for QA replay

- This is the single best **airplane-mode** smoke walk — nothing here
  should touch the network. Any network call is a bug.
- Watch step 4 → step 8 specifically: the neutral-mode and phone-only
  paths are where a wrong branch would drag her into account setup or
  lifestyle framing she explicitly opted out of.

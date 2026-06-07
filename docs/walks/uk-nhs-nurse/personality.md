# Persona — Priya Whitlock, NHS staff nurse (Manchester)

> One of the user-walk personas for **strictlykeptboy**. Invented,
> neutral, SFW. Used to ground QA walks against the real feature set
> (overlay scheduling, CalDAV client sync, .ics import, recurrence +
> exceptions-as-files, git-backed private repos). Not a real person.

## Snapshot

- **Name:** Priya Whitlock
- **Age:** 34
- **Location:** Manchester, UK (Chorlton; works at a city-centre NHS
  trust hospital)
- **Job:** Band 5 staff nurse on a busy acute medical ward. Rotating
  shifts — **earlies** (07:00–15:30), **lates** (13:00–21:30), and
  **nights** (20:30–08:00) — published as a four-week roster, with
  the usual run of last-minute swaps and "can you cover?" texts.
- **Household:** Lives with her partner, Tom (works regular office
  hours), and their daughter Niamh (6, in Year 1). Childcare is a
  juggle: after-school club some days, Tom's mum on others, a
  childminder on the days neither of them can cover.
- **Hobby anchor:** A monthly **book club** — second Thursday of the
  month, 19:30, rotating between members' houses. It is the one
  recurring thing that is *hers*, and the thing that most often
  collides with a late shift.

## Devices + tech comfort

- **Phone:** Pixel 7a (Android 16). Her main everything-device.
- **Tablet:** an older Samsung tablet that lives on the kitchen
  counter, mostly for recipes and Niamh's cartoons — but she'd happily
  glance at "the week" on it if it looked right on the bigger screen.
- **Comfort level:** Moderate. She installs apps without fear, follows
  a setup wizard fine, can paste a URL and tap through an OAuth login.
  She is **not** a coder — "git", "repo", "frontmatter", and "RRULE"
  mean nothing to her, and the app must never make her care. She has
  heard of "syncing your calendar" because she set up Google on her
  phone once.

## Voice

> "I don't need clever. I need to look at one screen and *know*
> whether I can say yes to book club this month, or whether I'm on a
> late and Tom's already got football. Right now I've got the trust's
> roster in one app, Google Calendar for home, a WhatsApp group for
> childcare, and a book-club thread that lives in my head. By the time
> I've cross-checked all four I've missed the window to swap. I want
> the clashes to *show up*, not make me hunt for them."

## What she uses now (and why it fails her)

1. **The NHS / trust roster app + an exported roster.** Her shifts are
   published there. It shows shifts but nothing else — no home life, no
   book club, no childcare. It's a silo.
2. **Google Calendar.** Home stuff, Niamh's school, the odd reminder.
   It does *not* know her shifts unless she hand-types them, which she
   stopped doing months ago because the roster changes.
3. **A childcare WhatsApp group** with Tom and his mum — "who's got
   Niamh Thursday?" — re-litigated weekly, never written down anywhere
   she can *see against her shifts*.
4. **Book club in her head.** Second Thursday. She forgets it's a late
   week until someone messages the group.

The failure mode is always the same: **the four sources never sit on
one screen**, so clashes only surface when it's too late to do anything
about them — a swap she can't get cover for, a book club she has to bail
on at 18:00.

## Why strictlykeptboy beats the NHS-app + Google-Calendar combo

- **Overlay clash-spotting in one view.** strictlykeptboy's overlay
  model lets her run *shifts*, *childcare*, and *book club* as separate
  toggleable calendars layered on one day/week, each with its own
  priority and active windows. Two things in the same slot render as a
  visible collision — exactly the "show me the clash" she's missing.
  (Real feature: D.5 overlay / priority / active-window model.)
- **CalDAV client sync to the calendars she already keeps.** She points
  the app at her **Google** calendar over CalDAV (bidirectional client
  sync is in v1 — Google / Microsoft / Apple / Nextcloud / DAVx⁵). Home
  life she already maintains in Google keeps flowing both ways, no
  re-typing. (Real feature: Phase Y CalDAV mirror, BIDI mode.)
- **Her shifts become real, visible entries.** The roster comes in as an
  **.ics import** (or, where the trust exposes a CalDAV feed, as a
  pull-only CalDAV mirror) and materialises as actual events in her own
  store — not trapped in a silo app. (Real features: Phase P .ics
  import; Phase Y `PULL_ONLY` mirror.)
- **Private and file-based, on her terms.** Her schedule is hers. She
  can run it as a **phone-only repo with no remote at all** (no-origin
  repos are first-class) if she's wary of putting her life on a server,
  or back it up to a private git remote later. Either way nothing about
  her shifts or her family is anyone else's product. (Real features:
  no-origin repos D.74 / Phase ZZ; per-event `private` flag.)
- **A shift swap is one small edit, not a rebuild.** Because recurrence
  lives in a rule and deviations live as **exception files**, swapping a
  Thursday late for a Saturday early is "change this one occurrence",
  not "delete and recreate the whole pattern". (Real feature: D.6
  recurrence + exceptions-as-files.)

## Repos, sync + identity / mode choices

Priya keeps things simple — **one repo**, named **`life`**, in the
neutral / "free" mode (she has no interest in the lifestyle-wizard
personas; she ticks the neutral-mode toggle at the age gate and never
sees that surface again).

- **Repo:** `life` — her single personal repo.
- **Origin choice:** starts **no-origin** (phone-only) on day one
  because she wants to try it before trusting a server. Mentally flagged
  as "add a private GitHub backup once I trust it" — a later session,
  not the first walk.
- **Identity:** plain — display name "Priya", neutral tone. No
  honorific / praise register (that's the lifestyle surface she opted
  out of).
- **Calendars inside `life`:**
  - `shifts` — **regular** calendar, **pull-only** target for the
    imported roster (marked read-only-from-app so she doesn't
    accidentally edit a mirrored shift). High priority — work wins the
    foreground slot when it collides.
  - `home` — **regular** calendar, the **BIDI CalDAV mirror** of her
    Google calendar (school runs, Tom's football, GP appointments).
  - `childcare` — **regular** overlay calendar, lower priority, active
    windows around school terms.
  - `bookclub` — **regular** overlay calendar, low priority, just the
    monthly recurrence.
- **Sync setup:** Google over CalDAV (OAuth device flow) for `home`;
  `.ics` import (or trust CalDAV pull) for `shifts`; `childcare` +
  `bookclub` authored locally.

## Frustrations strictlykeptboy must respect

- **Irregular shifts** mean any calendar that assumes 9-to-5 is useless
  to her. (Active-hours / active-windows must cope with night shifts
  that cross midnight.)
- **Last-minute swaps** are constant. Editing one occurrence must be
  fast and obvious, and must push back out so Tom (who sees her Google)
  knows.
- **Childcare clashes** are the real cost of a bad shift. She needs the
  childcare overlay *visible against* shifts, not in a separate app.
- **She bails on book club** when she doesn't spot a late-week collision
  early. The whole point is to catch it weeks out.

## What success looks like

> "I open the app on the bus, see next week, and the late shift on the
> book-club Thursday is sitting right on top of book club, lit up as a
> clash. I message the group to swap *that morning*, not at half six
> when I'm already in scrubs. When I do swap a shift, I change one thing
> and Tom sees it on his phone without me having to tell him. I never
> have to re-type a roster, and the whole of my life — work, Niamh,
> book club — is on one screen that's actually *mine*."

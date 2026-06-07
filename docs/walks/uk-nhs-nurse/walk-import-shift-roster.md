# Walk — Import the shift roster (first launch + setup)

> Persona: **Priya Whitlock**, NHS staff nurse, Manchester. See
> [`personality.md`](personality.md). Goal of this walk: from a cold
> install, get her rotating shift roster into strictlykeptboy as real,
> visible events — via **.ics import** and/or a **CalDAV pull mirror** —
> and connect her **Google** calendar bidirectionally over CalDAV.
>
> Features exercised (all real): age gate + neutral-mode toggle (K.14);
> setup wizard (Phase K); no-origin repo creation (Phase ZZ / I.2);
> .ics import (Phase P / P.2); CalDAV mirror setup wizard, Google OAuth,
> `PULL_ONLY` + `BIDI` modes (Phase Y); schedule day/week view (Phase G).
>
> Format: numbered test-script steps. **she taps X → expects Y.**
> ⚠ marks a confusion point worth a QA note.

## Preconditions

- Fresh install of strictlykeptboy on the Pixel 7a (Android 16). No
  repos configured.
- Priya has her four-week roster as a file `roster.ics` already in
  `Downloads` (the trust's roster portal let her "export to calendar").
- She has a Google account whose calendar holds her home life.

## Steps

### A. First launch + age gate + neutral mode

1. She taps the strictlykeptboy icon → expects the app to open on a
   **first-run / age-gate** screen (17+ positioning, `AgeGatePrefs`),
   not straight into a calendar.
2. She reads the gate, confirms she's over 17 → expects a visible
   **"Neutral mode"** toggle on or near the same screen.
   ⚠ **QA:** A nurse here for a shift planner must be able to find the
   neutral-mode toggle *without* first seeing kink-coded role/template
   copy. Verify neutral toggle is reachable at/with the age gate, before
   any persona content renders.
3. She turns **Neutral mode ON** → expects all lifestyle role + template
   surfaces to be suppressed for the rest of the wizard (per K.14 →
   `WizardNavHost(neutralMode = true)`).
4. She continues → expects to land in the **setup wizard**.

### B. Create her repo (no-origin / phone-only)

5. Wizard reaches repo creation. She expects a clear top-level choice:
   **"Create local-only"** vs **"Connect to a remote"** (I.2 / ZZ.G).
6. She picks **Create local-only** → expects the flow to **skip**
   provider / URL / auth entirely and go straight to identity +
   default-calendar selection.
   ⚠ **QA:** Confirm the local-only branch never shows a git/SSH/OAuth
   screen. Priya must not see "remote", "SSH key", or "PAT" at all on
   this path.
7. She names the repo **`life`** and sets display name **"Priya"**,
   neutral tone → expects no honorific / praise-register prompts
   (suppressed by neutral mode).
8. She finishes the wizard → expects to land on an **empty schedule**
   with the repo `life` selected in the top-bar repo switcher, showing a
   small **"local" / house badge** (no sync button for a no-origin repo,
   per ZZ.G / J.3).
   ⚠ **QA:** Verify the manual **sync button is hidden** (or disabled)
   while the active repo has no remote — a visible sync button she can't
   use would confuse her.

### C. Import the roster from the .ics file

9. She opens **Settings → Repos → `life`** (or the rail Settings
   destination) and finds **Import / Export** under the Repos category
   (Phase P surface, R.4 places it under Repos) → expects an
   **Import** entry point.
10. She taps **Import** → expects an Android file picker (SAF
    `OpenDocument`) → she selects `Downloads/roster.ics`.
11. The file is chosen → expects an **Import preview sheet**
    (`ImportPreviewSheet`) summarising what was parsed, e.g.
    *"N events, 0 rules, 0 exceptions"* (matching the Phase P
    parse-preview shape).
    ⚠ **QA:** A four-week roster is ~20+ shift events, some **crossing
    midnight** (nights 20:30→08:00). Verify the parser counts these
    correctly and the preview times look right for an overnight event.
    Note VTIMEZONE is dropped in v1 (P) — confirm the displayed times
    are still sensible in device tz (Europe/London) and flag if a night
    shift looks shifted by an hour.
12. The preview asks **which calendar** to import into. There's no
    `shifts` calendar yet → she expects to be able to **create / choose
    a target calendar** named `shifts` here (or the importer creates the
    chosen calendar).
    ⚠ **QA:** If the v1 importer requires the target calendar to already
    exist, this is a dead-end for a first-time user. Verify there is a
    create-calendar affordance in the import flow, or document that she
    must create `shifts` first (and surface *where*).
13. She confirms the import → expects the events to be **written +
    committed** into `life` under the `shifts` calendar
    (`EntityWriter.writeBatch` + commit), and the sheet to dismiss back
    to a success state.
14. She opens the **schedule (Day/Week view)** and navigates to a date
    she knows she's working → expects to **see the shift as a real
    event** (title, start/end) on the timeline.
    ⚠ **QA:** Verify an **overnight night shift** renders correctly —
    either spanning to the next morning or clearly indicated — in both
    Day and Week views, with the current-time `NowLine` not breaking it.

### D. Connect Google over CalDAV (home life, two-way)

> This is the second source: her *home* Google calendar, kept in sync
> both ways so school runs / Tom's football / GP appts appear alongside
> shifts and anything she adds shows up on Google too.

15. She goes to **Settings → Repos → `life` → "+ Add CalDAV mirror"**
    (Phase Y.2) → expects a **provider-preset** picker: Google /
    Microsoft 365 / Apple iCloud / Nextcloud / custom.
16. She picks **Google** → expects an **OAuth device-flow** login
    (Y.3 reuses the git `DeviceFlowClient`): a code + a "open this URL,
    enter this code, approve" instruction.
    ⚠ **QA:** Device-flow on a phone is awkward (the URL+code dance on
    the same device you're approving from). Verify the in-app
    instructions are clear and that returning to the app after approval
    advances the flow without a manual refresh.
17. After approval she expects **CalDAV discovery** to run
    (`/.well-known/caldav` → PROPFIND) and **list her Google calendars**
    so she can choose which one is "home".
18. She selects her main Google calendar and chooses a **target calendar
    in `life`** named **`home`**, and a **mode** of **BIDI** (two-way)
    → expects the mirror to be created and an initial pull to populate
    `home` with her existing Google events.
    ⚠ **QA:** Verify the mode picker explains the three modes in plain
    language. Priya should understand "BIDI = changes flow both ways"
    without knowing the word; flag if the copy is jargon-y.
19. She returns to the **schedule** → expects to now see **two layers**:
    `shifts` (from the .ics import) and `home` (from Google), both
    rendering on the same Day/Week timeline.

### E. (Optional) Roster as a CalDAV pull instead of a file

> If her trust exposes a CalDAV feed for the roster, she'd rather mirror
> it than re-import a file every four weeks. This is the canonical
> `PULL_ONLY` use case from Phase Y.

20. She repeats **"+ Add CalDAV mirror"**, picks **custom** (or
    Microsoft 365, if the trust runs Exchange), enters the trust CalDAV
    URL + credentials, and chooses mode **PULL_ONLY** with target
    calendar `shifts` → expects the `shifts` calendar to be marked
    **read-only-from-app** (`mirror = { source = "caldav", direction =
    "pull" }`, per Y.4) so she can't accidentally edit a mirrored shift.
    ⚠ **QA:** If she set up `shifts` via .ics import in step 13 *and*
    then attaches a pull mirror to the same calendar, verify the app
    handles the overlap sanely (de-dup on UID / `external_uid`, per P.2 /
    Y.5) rather than doubling every shift. This is a likely real-world
    collision — flag the observed behaviour.

## Success criteria

- Repo `life` exists as a phone-only (no-origin) repo, neutral mode.
- Shift roster events are visible in the schedule, overnight shifts
  rendering correctly.
- Google `home` calendar is mirrored BIDI and its events render on the
  same timeline as shifts.
- No git / SSH / coding concept was ever forced on her.

## Open confusion points gathered (for QA triage)

- Neutral-mode toggle discoverability at the age gate (step 2).
- Sync button visibility on a no-origin repo (step 8).
- Target-calendar creation *inside* the import flow (step 12).
- Overnight (cross-midnight) shift rendering + VTIMEZONE-dropped times
  (steps 11, 14).
- Device-flow OAuth on the same phone (step 16).
- .ics-imported `shifts` vs CalDAV-pull `shifts` de-dup (step 20).

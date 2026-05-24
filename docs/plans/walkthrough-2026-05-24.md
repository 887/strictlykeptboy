# Walkthrough — comprehensive AVD first-launch audit

## Status: PENDING — dispatch to an opus subagent in worktree mode.

## Why this exists

User feedback (2026-05-24): the app is shipping fast but no one has
sat down and pretended to be a first-time installer. Settings half-
work, demo data has gaps, the wizard finishes into a confused
schedule, supersedence + TZ + the new toggles have all landed in
isolation. Time to actually USE it end-to-end and write down
everything that's broken / weird / unimplemented / unhooked.

The deliverable from this walkthrough is **not bug fixes** — it's a
prioritized punch-list with screenshots that a follow-up round can
work through.

## Constraints for the running agent

- Run in a **worktree** so the main checkout stays usable while
  the walkthrough is in flight.
- Treat the AVD `emulator-5554` (medium_phone) as the primary
  target. The canonical loop is in `CLAUDE.md`:
  ```bash
  JAVA_HOME=/usr/lib/jvm/java-26-openjdk ANDROID_HOME=$HOME/Android/Sdk ./gradlew :app:assembleDebug
  ~/Android/Sdk/platform-tools/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
  ~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am start -n com.eight87.strictlykeptboy/.MainActivity
  ~/Android/Sdk/platform-tools/adb -s emulator-5554 exec-out screencap -p | magick - -resize 50% /tmp/skb-<step>.png
  ```
  Native AVD is 1080×2400; the resized PNGs are 540×1200. Multiply
  tap coords from the resized image by 2 to drive `adb input tap`.
- Prefer `mobile-mcp` when loaded (a11y tree + tap-by-label) — it's
  precise where coord taps drift. Fall back to `adb input tap`.
- **Start from a clean install every time.** Between phases:
  ```bash
  ~/Android/Sdk/platform-tools/adb -s emulator-5554 shell pm clear com.eight87.strictlykeptboy
  ```
  Then re-launch. This is the only way to honestly test the
  first-launch path.
- **Take a screenshot at every interesting state**, named after
  the phase + step (e.g. `walkthrough-A2-wizard-welcome.png`).
  Pile them into `/tmp/walkthrough-2026-05-24/`. The final report
  should reference them inline.
- **Do not fix anything inline** unless a single-line change is
  the only thing standing between you and the next step. Log the
  bug, work around it, keep walking. Fix-batch comes in a
  follow-up round.
- The user is in CEST (`Arctic/Longyearbyen` on AVD). Demo data
  is authored in `Europe/London`. The new `adjustToLocalTimezone`
  toggle (default on) re-anchors at parse time. Watch for any
  off-by-one-hour symptoms.

## Output — what to commit

A single new file at `docs/plans/walkthrough-2026-05-24-results.md`
that mirrors the phase structure below and, for each step:

- `- [x]` ticked when the step ran cleanly,
- `- [!]` tagged when a bug was hit, with:
  - one-line symptom
  - what was expected
  - what actually happened
  - severity: `blocker` / `bug` / `polish` / `missing-feature`
  - screenshot path
  - guess at the file:line owner (grep + Read, do not start
    a fix)

Plus a `## Punch-list` section at the top that's the sorted
union of `[!]` entries, severity-first. That's what the next
round will work from.

## Phase A — first-launch happy path (fresh install, demo on)

- [ ] **A.1** `pm clear` + `am start`. Land on `IntroScreen`.
      Verify the bat avatar renders, the body copy is the one
      from `strings.xml`, and the Continue button works.
- [ ] **A.2** Step through the two intro screens. Note whether
      the age gate appears + whether the gate accepts the
      default "yes I'm 17+" path.
- [ ] **A.3** First Schedule render. Check: any visible bands?
      What date is "today" according to the rail? Does the
      `Now` tab show anything?
- [ ] **A.4** Day tab — full 24h. Routine stack should appear
      (Morning alarm 06:30, Cage on 06:35, Brush teeth 06:40,
      Get dressed 06:50, No skipping breakfast 07:00, …).
      Verify times match the device's wall-clock, not London's.
      Open one band → detail sheet, confirm body renders.
- [ ] **A.5** 3-day + Week + Month tabs. Look for misalignment
      between tabs (different bands on the same day across
      views = bug).
- [ ] **A.6** Floating action cluster — Layout FAB (stacked /
      grid), Filter FAB (Important / Active / Routine), the
      new Superseded FAB (Hidden / Strikethrough). Tap each,
      confirm visual change + state persists across tab
      switches.
- [ ] **A.7** "+ New" FAB → event-create sheet. Try creating
      an event today at 14:00 for 30 min. Save, verify it
      appears on the Schedule and writes to the demo repo's
      working tree. (Demo is read-only — expect a guard /
      warning. If silent failure, that's a bug.)

## Phase B — Brighton + DevConf supersedence

- [ ] **B.1** Day tab on Sun May 24 (Brighton weekend).
      Hidden mode: expect Brighton 10:00-20:00, Sleep,
      Feed Beans, Cage stays on; NO routine stack.
      Strikethrough mode: routine stack reappears at 0.35
      alpha + LineThrough.
- [ ] **B.2** Day tab on Mon May 25 (UK Spring bank holiday).
      Expect public-holiday all-day band + sleep + cat-care
      + kinky-rituals; NO work/commute/dom-overlay/standup.
- [ ] **B.3** Day tab on Wed May 27 (DevConf Berlin starts
      07:00 Europe/Berlin, `pin_timezone = true`). Verify
      DevConf band starts at 07:00 in the device's local
      time IF AND ONLY IF the device is CET; otherwise it
      should be offset (Berlin 07:00 ≠ London 06:00 in BST,
      etc.). If the band is mistimed for the device's zone,
      log a TZ-pin bug.
- [ ] **B.4** Cat-sitter handover Wed 08:00 — should render
      even though work is superseded by DevConf.
- [ ] **B.5** Edge case: tap a superseded band in
      strikethrough mode. Does the detail sheet open? Should.
- [ ] **B.6** Z-order check: pick a day where the routine
      stack is dense (Tue May 26 morning). Verify later
      bands paint on TOP of earlier ones (the 2026-05-24
      paint-order fix). No earlier band hiding the next
      band's title.

## Phase C — Tasks pane

- [ ] **C.1** Tap the Tasks bottom-nav. Confirm `drawTasksFrom`
      defaults true now — the demo repo's todolists should
      appear (no empty state).
- [ ] **C.2** Today filter vs All filter vs Inverted filter.
      Verify the prompt-photo entries are gone (we removed
      "send proof you're still caged" from demo). Note any
      remaining keeper prompts that look weird.
- [ ] **C.3** Tap a passive-habit task. Confirm it's marked
      completed-by-schedule until a deviation is written.
- [ ] **C.4** Tap "Respond" on a keeper-prompt task. The
      sheet should open. Try sending a text response and a
      mark-answered-offline; both flows.

## Phase D — Settings — every category

- [ ] **D.1** Open Settings (gear). Scroll the full list.
      Note any category that crashes, renders empty, or
      has a label without a body.
- [ ] **D.2** Repos category — should open inline now
      (no trampoline). Confirm RepoCard renders for the
      demo repo with all switches:
      - Show on schedule
      - Show in tasks (default ON)
      - Auto-sync
      - Wi-Fi only
      - Import stickers into repo
      - **Adjust to local timezone** (default ON, the new
        one) — flip it OFF, confirm the warning copy /
        prompt explains what changes. Then flip back ON.
- [ ] **D.3** Lists category — calendar list editor.
      Verify each demo calendar has its priority +
      `supersedes` list intact. Try editing one (e.g.
      bump a calendar's priority by 5). Save, exit
      settings, confirm Schedule re-renders + new
      priority took effect (z-order tiebreaker shift).
- [ ] **D.4** Identity category — confirm Round 2.1.J
      write-back works. Edit praise term. Wait for
      debounce, then `git -C <demo-repo-root> log --oneline`
      from a terminal should show a new commit. Demo is
      read-only — expect the write to be guarded; the
      bug is silent failure, not the guard.
- [ ] **D.5** Lifestyle category — Mode toggle (free /
      strictly-kept), Neutral-mode toggle, alignment.
      Verify the cooling-off countdown if you try to
      flip strictly-kept → free within 24h.
- [ ] **D.6** Notifications category. Main toggle on/off.
      Tap a sub-toggle (per-event mute, briefings).
      Schedule a reminder for +2 min, lock the AVD,
      wait — does it fire? If yes, does the body use
      the identity's praise term?
- [ ] **D.7** Appearance category — theme + density.
      Switch through each, look for any pane that
      ignores the change.
- [ ] **D.8** Access category — sharing recipients per
      repo. With only the demo repo + no shares, expect
      an empty state.
- [ ] **D.9** Auto & Tablet category — toggles render?
      Any deep-link chips? Note unhooked toggles.
- [ ] **D.10** Import / Export category. Trigger an export
      to a SAF folder. Verify a `.zip` / `.tar.gz` lands.

## Phase E — Wizard, fresh repo

- [ ] **E.1** From Repos category, tap "Add repo". Walk
      every wizard step:
      - Welcome
      - Alignment (try Submissive — should default mode
        = strictly-kept, persona = stern-but-fair)
      - Mode override
      - Identity (praise, pronouns, honorific)
      - Storage (internal vs SAF)
      - Templates (morning / midday / evening atom map —
        the post-2.1.I.5 layout)
      - Dom-share screen
      - Finish
- [ ] **E.2** After Finish, the new repo should be the
      default write target. Confirm the top-bar avatar
      flipped + the Schedule re-renders with the wizard-
      seeded routines (not all stacked at 09:00).
- [ ] **E.3** Open Schedule for next Monday — verify the
      seeded morning / midday / evening blocks landed
      in their proper hours.

## Phase F — Briefings + alarms

- [ ] **F.1** Settings → Notifications → Briefings master
      on. Confirm the morning + evening WorkManager jobs
      are registered (`adb shell dumpsys jobscheduler |
      grep strictlykeptboy`).
- [ ] **F.2** Set AVD clock to 06:59. Wait. The 07:00
      briefing should fire with identity-honoring copy.
      Reset clock.
- [ ] **F.3** Schedule a reminder for +5 min. `adb reboot`
      the emulator. After it comes back up, the reminder
      should still fire at its scheduled time (Phase F.8
      `BootCompletedReceiver`).

## Phase G — Tablet pane

Skip if no `pixel_tablet` AVD is up. Otherwise:

- [ ] **G.1** `scripts/start-tablet-avd.sh`, install the
      same APK, launch.
- [ ] **G.2** Schedule pane should be two-column (master-
      detail) at Expanded width-class. Tap a band — detail
      renders in the right pane, not as a sheet.
- [ ] **G.3** Repeat for Tasks, Settings, Wizard,
      Together, Repos, Import/Export, Notifications.
      Log any pane still rendering as a one-column phone
      layout under Expanded width — that's the gap from
      Round 2.1.H.

## Phase H — Cross-cutting checks

- [ ] **H.1** Toggle `adjustToLocalTimezone` OFF on the
      demo repo. Switch Schedule view. Confirm bands
      shift to London wall-clock. Toggle back ON,
      confirm they shift back to device wall-clock.
- [ ] **H.2** Open DevConf-Berlin recurrence in detail.
      Confirm `pin_timezone = true` is honored — band
      stays at 07:00 Berlin regardless of repo-level
      toggle.
- [ ] **H.3** Filter FAB — turn off all three (Important,
      Active, Routine). Expect at least one to be forced
      back on (the `anyOn()` guard). Otherwise: bug.
- [ ] **H.4** Repos category — toggle a repo's "Show on
      schedule" off. Bands from that repo should disappear
      from Schedule (not just be hidden).
- [ ] **H.5** Repos category — change the write-target
      repo. Confirm the top-bar avatar flips + the "+
      New" FAB writes into the new target.
- [ ] **H.6** Backgrounding: home-button out of the app,
      wait 30s, come back. Schedule should restore to
      the same state. No crash, no flicker.

## Phase I — Edge cases + accessibility

- [ ] **I.1** Rotate the device. Phone landscape — does
      the Schedule survive?
- [ ] **I.2** Font scale 200% via system settings.
      Settings list should still be readable; band titles
      may truncate but should not crash.
- [ ] **I.3** TalkBack on. Walk the Schedule FAB cluster
      + the bottom-nav. Confirm each surface has a
      meaningful `contentDescription`.
- [ ] **I.4** Deep-link: `adb shell am start
      strictlykeptboy://event/<some-event-id>` — does
      the app route to the right detail sheet?

## Phase J — Report-out

- [ ] **J.1** Write `docs/plans/walkthrough-2026-05-24-results.md`
      with the structure above + the `## Punch-list`
      header.
- [ ] **J.2** Commit the results file + the `/tmp/walkthrough-…`
      screenshots (copy them into `docs/plans/screenshots/walkthrough-2026-05-24/`
      first — don't leave them in `/tmp`).
- [ ] **J.3** Tag the commit `walkthrough-2026-05-24-baseline`
      so a follow-up round can `git diff` against it after
      the fix-batch.

## What's intentionally NOT in scope

- Driving CalDAV sync against a real DAVx⁵ install.
- Driving the GitHub OAuth Device Flow against a real
  GitHub account.
- Android Auto / Wear / lockscreen surfaces — those need
  their own targeted runs.
- Tablet master-detail completeness — covered as a
  binary check in Phase G; full pane-by-pane gap audit
  is its own round.
- Performance / cold-start timing.
- Real-device wifi-adb runs (the AVD is the contract for
  this walkthrough).

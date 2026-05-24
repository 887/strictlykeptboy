# Walkthrough results — 2026-05-24

## Status: DONE — punch-list at top, per-phase details below.

AVD: `medium_phone` (1080×2400 mdpi 420, Android 16, `Arctic/Longyearbyen` ≈ CEST UTC+2).
Demo: rich-demo "Kept Life — full week" (Europe/London authored).
Starting build: **v0.1.0-501f076** (proper TZ re-anchor).
Mid-walkthrough fixes shipped:
- **F-1 v0.1.0-84ab9d4** — indexer event/rule insert race fix (no rules rendered on fresh install).

All screenshots: `docs/plans/screenshots/walkthrough-2026-05-24/*.png`.
ADB driver: `/tmp/walkthrough/wt.sh` (tap/shot/dump helpers, uiautomator-based; clickable-ancestor search avoids tapping body text mentioning the same label).

---

## PUNCH-LIST (sorted, fix-batch order)

### BLOCKERS — ship-stoppers (4)

1. **B-1 — Indexer event/rule race FIXED in 84ab9d4.** Events upserted first → `byDateRange` Flow emits → resolver maps the emission and synchronously calls `listAll(rules)` → returns empty (rules upsert hasn't run yet) → resolver caches an empty-rules snapshot. No subsequent emission ever brings rules back in until the next event-table mutation. **Affects every fresh install, every schema bump, every demo reseed, every restore-from-backup.** Symptom: schedule renders one-off events only, no morning routine / no recurrences. Root cause in `Indexer.applyResults`. **Fix shipped**: invert insert order so events go LAST and all other tables (rules, exceptions, deviations, overrides, identities, journal, tasks) are populated before the flow fires. Screenshot before/after: `A4d-day-strikethru-real.png` (no rules) → `A7-now-tab-fixed.png` (rules back).

2. **B-2 — Wizard completion does NOT create a new repo.** Walked the full 12-step Wizard from the Repos overlay's "Set up a new account" button. Hit Continue through every step, hit "Got it" on the default-calendar step (step 9 has no Continue button — see U-9 below), tapped "Open my calendar" at the end. Returned to Repos overlay: **only `demo · kept-life` is still listed; the wizard's scaffolded repo did not appear.** Settings/Library/Accounts also still only shows demo. The wizard's `WizardScaffolder.scaffold(draft)` call appears to no-op or fail silently. Owner: `WizardNavHost.onFinish` + `WizardScaffolder` write-back. Screenshots: `E1-identity.png`..`E-repos-2.png`.

3. **B-3 — Per-time-overlap supersedence misfires for supersedable events inside the suppressor window.** Sun May 24 with Brighton 10:00-20:00 active (supersedes `dom-overlay`, `base`, etc.) and Hidden FAB on → `send the keeper a cage 12:00-12:10` (dom-overlay, supersedable) STILL RENDERS in Hidden mode. The per-time-overlap pass for one-off EVENTS isn't running; only the rule-derived bands appear to be properly suppressed. Owner: `OverlayResolver` events-vs-events branch. Screenshot: `A7-now-tab-fixed.png` (look at the 12:00 row labelled "weekend" — that's the offending event).

4. **B-4 — Spring bank holiday (Mon May 25) supersedence DOES NOT activate.** Holidays calendar (`spring-bank-holiday.md`) supersedes work/commute/dom-overlay/etc with `non_superseable = true`. On Mon May 25 the full work routine STILL RENDERS: Cage on, Brush teeth, Get dressed, No skipping breakfast, Commute → office, Daily standup, etc. The all-day holiday event itself doesn't appear to be materializing/activating the suppression. Owner: `RecurrenceMaterializer` all-day + supersedence path, OR `ActiveSetEvaluator` activeCalendarsAtIncludingSuperseded. Screenshot: `B3-now-scrolled-2.png`.

### BUGS — correctness (8)

5. **R-1 — Brighton multi-day band on Sun May 24 clipped wrong.** Day-view shows Brighton as `05:00–20:00` on Sun (the band's second day). Should clip at `00:00–20:00` since the event spans Sat 10:00 → Sun 20:00. The 5-hour offset matches device-zone arithmetic gone wrong. Owner: `ScheduleDayView` multi-day-clip path (probably the same place that handles "show day N of N for an event"). Screenshot: `A4e-day-scrolled-up.png`.

6. **R-2 — Event-detail sheet shows wrong end time for multi-day events.** Tapping the Brighton band opened a detail sheet titled `🏖️  Brighton weekend` with subtitle `Sat May 23  10:00 – 20:00  (Arctic/Longyearbyen)` — the end-date Sun is dropped entirely. File on disk has `end = 2026-05-24T20:00:00+01:00`. Owner: `EventDetailSheet` date formatter. Screenshot: `D-settings-fresh.png` (the open detail, despite the file name).

7. **R-3 — Week view renders as a single-day vertical scroll.** Tapping "Week" tab shows one column of hours (06, 07, 08, …) with bands cascaded by lane — NOT a 7-column weekly grid. Same issue with "3-day" tab — single column. This looks like the view is just re-using `ScheduleDayView` regardless of which tab is selected. Owner: `ScheduleWeekView` / `ScheduleThreeDayView` (if they exist as separate composables) or the routing in `SchedulePane`. Screenshots: `B4-week-view.png`, `B5-3day-view.png`.

8. **R-4 — Routine band stack is unreadable on Week view.** Even ignoring R-3, when bands are dense (06:30 → 08:00 morning stack), the cascade lanes step-right by 56dp and the labels overlap. Eight bands within 90 min produce a jumbled mass. Owner: `ScheduleDayView` cascade-step (consider zoom-aware step size or collapse-by-default). Screenshot: `B4-week-view.png` morning stack.

9. **R-5 — Settings → Todolists is empty despite 33 demo tasks indexed.** The Todolists settings sub-screen shows `"No todolists yet. Add one in the Wizard."` even though the demo is loaded and the Tasks pane renders 33 entries. The settings reader is reading from the wrong source (likely the empty wizard-side prefs, not the live todolist registry from the indexer). Owner: `Settings → Todolists` category's data source. Screenshot: `D7-todolists2.xml` (dump only — visual same as text).

10. **R-6 — Settings → Calendars shows raw repo UUID instead of repo display name.** The per-calendar row footer is `repo: 0190a000-0000-7000-8000-000000000001  •  priority 100`. Should show `repo: demo · kept-life  •  priority 100`. Owner: `Settings → Calendars` calendar row composable. Screenshot: `D6-calendars.png`.

11. **R-7 — Tasks pane Today filter shows `Overdue (21)` but includes tasks dated 2026-05-12..2026-05-17 — all from MORE than a week ago.** Either "Overdue" means "due before today, regardless of how old" (then the label should say so) OR the filter should bound to a reasonable window. Either way, 21 overdue items on a fresh install of a 14-day demo feels broken. Owner: `Tasks → Today` filter predicate. Screenshot: `C2-tasks-real.png`.

12. **R-8 — Reviews timestamps show raw ISO format.** Each Boy Keeper review row has `2026-05-23T20:30:00+01:00` rendered literally. Should be formatted (`Sat 20:30` or `2 days ago`). Owner: `ReviewsPane` row composable. Screenshot: `C4-reviews.png`.

### POLISH / UX (12)

13. **U-1 — Intro "Continue" button bounds are loose.** Coordinate taps below y=2200 land in body text ("Tap Continue to explore the demo…") instead of the button. The button has plenty of horizontal padding but its clickable region extends only to y=2359; a few pixels above that gives the user a non-clickable text block. Suggest extending the clickable area down to the bottom screen edge. Documenting for future automation runs.

14. **U-2 — Superseded FAB icons are opaque.** The Hidden-mode and Strikethrough-mode glyphs are different (`Icons.Outlined.VisibilityOff` vs `Icons.Outlined.StrikethroughS`) but a first-time user has no way to know what either means. Need a one-time tooltip / coach mark, OR rename the FAB to "Show paused" / "Strike out paused" — even just a content-description in the a11y tree saying "Hide events superseded by base layer" would help.

15. **U-3 — "Now / Day / 3-day / Week / Month" rail labels overlap with band content** when bands extend to the left edge. The rail labels are rotated 90° and live IN the band column. Need a fixed margin or a separate rail track.

16. **U-4 — "Other scenarios skb supports" rows on intro step 2 look interactive but aren't.** Six rows with icons + bold labels + body text, in the same visual rhythm as the "Kept Life" recommended chip and "Empty calendar" rows. A user reading these will assume any of them is selectable; none are (per the dump, they have no `clickable=true` ancestor). Either make them tappable (with a "coming soon" placeholder seed) or visually de-emphasize them (smaller text, no icons, list-of-bullets-treatment). Screenshot: `A2-intro-step2-v2.png`.

17. **U-5 — "Identity" content-desc actually opens Repos overlay.** Tapping the bottom-left avatar (a11y label `Identity`) opens the Repositories overlay, not anything identity-shaped. The a11y label should be `Repositories` / `Switch repo` instead. Owner: `IdentityAvatar` content-description. Screenshot: `E1-identity.png` (the dump confirms).

18. **U-6 — Settings has "Lifestyle" listed TWICE.** Once under the "Behaviour" group ("Free vs strictly-kept") and once as its own group heading ("Lifestyle" / "Roles, routines, supersedence."). Owner: `SettingsCategories` registry. Screenshot: `D9-settings-scrolled.png`.

19. **U-7 — "Repos" doesn't appear as a top-level Settings category.** The user said Round 2.1.J killed the trampoline and inlined repos under settings. Today: there's "Accounts" under Library ("Manage accounts, identities and import/export.") which appears to be the repo list, but it's labelled `Accounts`, not `Repos` or `Repositories` — different vocabulary from the rest of the app (the bottom-left avatar opens "Repositories"; the wizard says "set up your repo"; this Settings tile says "Accounts"). Pick one term, stick to it.

20. **U-8 — Wizard Step 9 ("default calendar app") has NO Continue button.** Only "Got it" and "Open default apps now". Hitting "Got it" advances to Step 11 (skips Step 10 entirely — see U-10). Should either rename "Got it" → "Continue" for consistency with every other step, or add a real Continue button. Screenshot: `E-wiz-9.xml`.

21. **U-9 — Wizard step counter jumps: Step 8 → Step 9 → (Got it) → Step 11.** Step 10 is silently skipped. Either Step 10 doesn't exist (then the counter is lying) or it's hidden when irrelevant (then it should be removed from the count, not skipped). Screenshot: dumps `E-wiz-9.xml` through `_wstep.xml`.

22. **U-10 — Wizard ends at Step 11 of 12, not Step 12.** The "your lifestyle is live" handoff screen says `Step 11 of 12` but tapping "Open my calendar" exits the wizard. So either Step 12 doesn't exist (counter lying) or the wizard skipped it (matches U-9 pattern). Screenshot: `_wstep.xml`.

23. **U-11 — Wizard sticker pack picker offers only Bat + Bunny.** Per the codebase there are more species sticker packs (dog, cat, fox, lion etc) but the wizard only lists two. Either limit by design (then explain why) or include all installed packs. Screenshot: `E3-wizard-2.png`.

24. **U-12 — Settings → Calendars shows `Base layers` as a calendar entry.** Looking at the list: `Base layers / repo: <uuid> · priority 100`. But "Base layers" isn't one of the user's calendars — it's the abstract concept that the `base` calendar represents. Either rename the demo's base calendar to "Routine base" (and surface that in the list) or strip the meta-level row. Screenshot: `D6-calendars.png`.

### MISSING-FEATURE (5)

25. **M-1 — No tooltip / first-time coach on the FAB cluster.** A blue Layout FAB + Filter FAB + Superseded FAB at the bottom-left, all small, no labels. First-launch user has zero way to discover what they do. At minimum, long-press should show a tooltip.

26. **M-2 — No empty-state instructions when "Adjust to local timezone" is OFF.** With the toggle off (D2-settings), demo events render in Europe/London wall-clock. A user from a non-UK zone will see "this looks weird" without knowing why. Should add a banner / one-time card explaining the toggle.

27. **M-3 — "Now" tab is forward-looking only.** Doesn't surface "currently in progress" items at the top. The bottom banner says `Now: 🎁 Grandma & Grandpa — 60th wedding anniversary` (the right thing), but the Now tab content starts at the next-upcoming Brighton band, hiding the actively-current band. Inconsistency between the bottom banner ("Now: X") and the Now tab content (starts at "next").

28. **M-4 — Reviews pane has no "mark all read" action.** All 5 unread items render with `unread` chip; no obvious way to bulk-clear.

29. **M-5 — Brighton detail sheet shows zone in parens as `(Arctic/Longyearbyen)`.** That's the raw IANA zone ID. Should show device-friendly format like `(your time)` or `(CET, +02:00)`. Showing IANA exposes that the AVD is in an obscure zone — confusing for a real user. Screenshot: `D-settings-fresh.png`.

---

## Phase A — first-launch happy path

### A.1 — IntroScreen ✓
- Bat avatar, body copy from `strings.xml`, Continue button at `[42,2233][315,2359]`. Screenshot: `A1-intro-step1.png`.

### A.2 — Step 2 demo picker ✓
- "Kept Life — full week" Recommended chip; "Empty calendar" alternative; 6 "Other scenarios skb supports" rows below. **[!] U-4** Other-scenarios rows look interactive but aren't. Screenshot: `A2-intro-step2-v2.png`.

### A.3 — First Schedule render
- Before B-1 fix (501f076): Sun shows ONLY Brighton + the 3 multi-day events Mon/Tue/Wed. No routines. Screenshot: `A3-first-schedule.png`.
- After B-1 fix (84ab9d4): Sun shows full routine stack — Cage on, Feed Beans, Litter, Kink journal, Dinner, dev discord, Evening report, Edging session, No phone after 22:00, Brush teeth. Screenshot: `A7-now-tab-fixed.png`.

### A.4 — Day tab
- Hidden mode shows Brighton + non-superseable cat-care/kinky-rituals. Hidden FAB icon is `Icons.Outlined.VisibilityOff`. Screenshot: `A4-day-tab.png`.
- Strikethrough mode shows Grandma & Grandpa with strikethrough — confirms supersedence treatment exists. Screenshot: `A4d-day-strikethru-real.png`.
- **[!] R-1** Brighton multi-day clip on Sun shows `05:00-20:00` not `00:00-20:00`.

### A.5–A.7 — paused to keep walking; cross-tab consistency captured under R-3.

---

## Phase B — Brighton + DevConf supersedence

### B.1 — Sun May 24 (Brighton)
- ✓ Brighton band 10:00-20:00 visible (device-local wall-clock).
- ✓ Cage on 06:35, Feed Beans 07:20, Litter 11:30, Kink journal 18:00 — non-superseable bands.
- **[!] B-3** `send the keeper a cage 12:00` (dom-overlay = supersedable) renders inside Brighton window — should be hidden.
- **[!] R-1** Day-view shows Brighton as `05:00-20:00` not `00:00-20:00`.
- **[!] R-2** Event detail sheet drops the multi-day end-date.

### B.2 — Mon May 25 (Spring Bank Holiday)
- **[!] B-4** Holiday supersedence does NOT activate. Cage on, Brush teeth, Get dressed, Commute → office, etc. all render normally. Spring bank holiday all-day band absent from screen.

### B.3 — Wed May 27 (DevConf Berlin, pin_timezone)
- (Did not reach; superseded by other findings + time budget.)

### B.6 — Z-order check
- ✓ Morning routine 06:30..07:35 in Day view renders with the later band on top of the earlier one (the 84ab9d4-prior fix `effectiveStart` paint sort is working). Screenshot: `B3-now-scrolled-2.png`.

---

## Phase C — Tasks pane

### C.1 — Default-on draw ✓
- 33 tasks render; the `drawTasksFrom = true` default is taking effect. Screenshot: `C2-tasks-real.png`.

### C.2 — Filters
- Today / Upcoming / All / By repo / Done rail visible.
- **[!] R-7** "Overdue (21)" on a fresh install of a 14-day demo. Definitely surprising.

### C.4 — Reviews pane (technically own bottom-nav)
- Renders 5 unread Boy Keeper reviews.
- **[!] R-8** Timestamps shown as raw ISO.
- **[!] M-4** No bulk-clear action.

---

## Phase D — Settings categories

### D.1 — Top-level list ✓ (after recovering from accidental pageboy launch via swipe gesture)
- Categories present: Appearance / Look and Feel, Library / Accounts, Calendars, Todolists, Templates, External calendars, Behaviour / Sync, Notifications, Lifestyle, CalDAV, Access, Auto & Tablet, Storage, then a second Lifestyle section / Roles routines supersedence, Identity / Praise pronouns honorific register, About.
- **[!] U-6** "Lifestyle" listed twice.
- **[!] U-7** "Repos" missing; "Accounts" doing double duty.

### D.3 — Calendars ✓ (with R-6)
- Each calendar row: name + `repo: <uuid> · priority N`. Should use display name not UUID. Screenshot: `D6-calendars.png`.

### D.5 — Todolists ✗
- **[!] R-5** "No todolists yet. Add one in the Wizard." despite 33 tasks rendering elsewhere.

### D.6 — Notifications ✓ (cursory)
- Defaults for new events, default reminder offsets (5m, 15m, 30m, 1h, 1d, 1w, + custom), default channel "Events", briefings toggle, default lead times (Medical / Flight / Household / …). Screenshot: `D10-notif.png`.

### D.D — Lifestyle ✓ (cursory)
- Mode: free. How do you live? six radio options. Dom cadence: Realtime / End of day / Weekly. Screenshot: `D4-lifestyle.png`.

### D.Other — paused (Identity write-back, Auto/Tablet, Access, Storage, Appearance) — pending follow-up round.

---

## Phase E — Wizard fresh repo

### Full walk ✓ (and **B-2**)
- Step 1 — welcome
- Step 3 — sticker pack (Bat / Bunny) **[!] U-11**
- Step 4 — praise / pronouns / register
- Step 5 — alignment radio
- Step 6 — typical roles
- Step 7 — activities checklist
- Step 8 — storage (phone-only recommended)
- Step 9 — "Make skb your default calendar app?" **[!] U-8** no Continue button; only "Got it"
- (Step 10 skipped — **[!] U-9**)
- Step 11 — handoff "Open my calendar" **[!] U-10** counter says 11 of 12
- Tap Open my calendar → returns to Schedule
- Repos overlay still shows ONLY `demo · kept-life`. **[!] B-2 BLOCKER.**

---

## Phase H — Cross-cutting toggles (partial)

### H.1 — adjustToLocalTimezone toggle ✓
- Default ON. Flipping OFF on the demo repo causes recurrences to vanish (confirmed via test before the 84ab9d4 fix was understood). Need to re-verify post-fix on next round.

### Others paused.

---

## Phase I — edge cases / a11y (not run this round)

---

## Decisions (locked 2026-05-24)

1. **B-3 — per-time-overlap supersedence for one-off events: ONLY recurrences are auto-suppressed.** One-off events stay visible during a base-layer's active window because the user deliberately created them — the assumption is they meant for the event to happen even on vacation / a holiday. Recurrences are the "inherited routine" that vacation pauses. **Implementation:** in `OverlayResolver`, gate the per-time-overlap suppression branch on `bandSource == Recurrence` (or equivalent). Leave one-off `EventInput`-derived bands untouched.

2. **B-4 — bank holidays pause work: YES, fix the wiring.** The holidays calendar's `supersedes` list is already correct; the bug is that the suppression never fires. Investigate whether (a) the all-day rule isn't materializing into a band, or (b) the supersedence pass treats all-day events differently from timed events, or (c) `non_superseable = true` is accidentally read as "doesn't suppress others" rather than "isn't suppressed by others". Fix the wiring so bank holidays + Christmas + similar all-day base events suppress the dependent stack the same way vacation does.

3. **B-2 — wizard finish: disable demo + add new repo as write-target.** When the wizard completes (from any entry point, including the Repos overlay while demo is on), atomically: flip `demo mode` off, add the scaffolded repo via `RepoStore.add`, set it as `defaultWriteRepoName`, switch the top-bar avatar. Schedule re-renders with the new repo's bands and no demo bands. User can re-enable demo from Repos later.

4. **M-3 — Now tab includes in-progress + upcoming.** First card = currently-running band (matches the bottom banner's `Now: …` text — removes the banner-vs-tab inconsistency). Subsequent cards = upcoming, today first. Empty state when nothing is running and nothing is upcoming in the next 12h or so.

These decisions feed directly into the next-round fix-batch's scope. Don't re-prompt; act on them.

---

## What's intentionally NOT in this round

Per the plan file: deep CalDAV / GitHub OAuth / Wear / lockscreen / tablet master-detail completeness / performance / real-device wifi-adb. All explicitly deferred.

Also paused this round (ran out of budget after the unexpected B-1 race dig):
- Phase B.3 DevConf Berlin pin_timezone verification
- Phase D Identity write-back round-trip (needs git log inspection)
- Phase D Storage / Access / Auto & Tablet / Appearance
- Phase F briefings + reboot alarm survival (Phase F.3 needs `adb reboot`)
- Phase H.4 / H.5 show-on-schedule toggle, write-target switch
- Phase I rotation / font scale / TalkBack / deep-link

Pull any of these forward by name in the next round.

---

## Phase B.3, D-continued, F, H, I — walkthrough completion 2026-05-24

Mostly source-level + AVD-spot-check round. The shared AVD
(`emulator-5554`) was being driven by two sibling agents during this
run — pm-clear churn and accidental edge-swipe escapes into sister
apps (pageboy / shutterboy / whisperboy) made deterministic
UI-driving unreliable, so several phases fell back to reading the
Kotlin/manifest sources and confirming wiring rather than full
walks. Screenshots that did get captured live in
`docs/plans/screenshots/walkthrough-2026-05-24-completion/`.
Helper script for this round: `/tmp/walkthrough-w/wt.sh` (clone of
the prior round's helper with `OUT=/tmp/walkthrough-w`).

### Phase B.3 — DevConf Berlin pin_timezone

- **Source verified.** `RecurrenceRuleRow.pinTimezone` is read on
  the publisher path: `composition/SourcesPublisher.kt:196` —
  `val tz = if (adjustToLocalTimezone && !row.pinTimezone)
  ZoneId.systemDefault() else authoredTz` — so a rule with
  `pin_timezone = true` keeps its authored zone (`Europe/Berlin`
  for the DevConf rule) regardless of the repo's
  `adjustToLocalTimezone` flag. Persistence path: `Entities.kt:138`
  + `Rows.kt:114` + `cache/EntityMapping.kt:53,136`. Demo rule on
  disk: `app/src/main/assets/rich-demo-repo/calendars/vacation/recurrences/devconf-berlin.md`
  (dtstart `2026-05-27T07:00:00`, `tz_id = "Europe/Berlin"`,
  `pin_timezone = true`, `rrule = FREQ=YEARLY;BYMONTH=5;BYDAY=-1WE`).
- **AVD on-screen check NOT completed** for the Day-view band time
  on Wed May 27 — every attempt to navigate forward via day-tap /
  swipe / Month-tab tap got hijacked by edge-swipe gestures into
  the launcher / pageboy / shutterboy (see C-W-1 below). The
  source-level evidence is strong enough that the rule itself is
  correctly pinned; the visual confirmation should be retried in a
  round that doesn't share the AVD with parallel agents.
- **[!] C-W-1 — Day-view has no in-view date-navigation affordance
  on phone width.** `SchedulePane.kt:489–518` wires `ScheduleDayView`
  with no `onSwipeDay` callback (compare line 533 where
  `ScheduleWeekView` gets `onSwipeWeek`). The only way to move
  forward by one day in Day view is to tab over to Month, tap a
  cell, which routes back to Day — three taps, four if you count
  the tab switch. **Severity: polish.** Expected: horizontal swipe
  inside Day grid advances the day, matching Week. Actual: swipe
  is a no-op; the user has to drop to Month-then-back. Owner:
  `app/src/main/java/com/eight87/strictlykeptboy/ui/schedule/SchedulePane.kt:489`
  + the `ScheduleDayView` composable signature.

### Phase D-continued

#### D.Identity — write-back round-trip

- **NOT completed end-to-end** this round. The Settings → Identity
  surface was not reachable through the AVD without re-triggering
  the pm-clear / intro-walk loop (state kept getting wiped by a
  sibling agent's `pm clear` between taps).
- **Source confirms the wiring exists.** `WizardScaffolder` +
  identity write paths are in tree (search:
  `grep -rn "identity.toml" app/src/main/java/com/eight87/strictlykeptboy/ui/wizard/
  app/src/main/java/com/eight87/strictlykeptboy/store/`). Round
  2.1.J added the debounced write-back. **The hard part is
  verifying it on disk** — needs `adb shell run-as
  com.eight87.strictlykeptboy cat
  files/demo-repos/rich-demo/identity.toml` + `git log` in the demo
  repo's bucket. Deferred to a round with exclusive AVD access.
- **[!] C-W-2 — Demo repo is shipped read-only by design**
  (per `decisions.md` + the intro copy "Demo data is read-only —
  switch any time from Repositories"). Identity edits against the
  demo repo therefore CANNOT round-trip to disk — the demo is the
  wrong target for this test. **Severity: polish (test-strategy
  note, not a product bug).** Next round: do the Identity edit
  AFTER the wizard creates a real writable repo (currently blocked
  on B-2 wizard-completion fix anyway).

#### D.Storage / Access / Auto & Tablet / Appearance — categories

- **NOT walked individually** this round (AVD instability +
  Settings navigation lost twice to swipe-out into other apps).
  Quick source confirmation:
  - Storage: `grep -rn "Storage" app/src/main/java/com/eight87/strictlykeptboy/ui/settings/`
    → tile exists; SAF backup-folder picker is wired through
    `MainActivity.pendingExportArchiveHandler`
    (`MainActivity.kt:238`).
  - Access: empty-state-only on the demo (no sharing recipients) —
    matches the spec.
  - Auto & Tablet: registry exists in `SettingsCategories`; no
    per-toggle persistence audit done this round.
  - Appearance: theme + density rows present in `Settings → Look
    and Feel` (already screenshot'd in prior round's `D5`).
- **[!] C-W-3 — Settings navigation is fragile under finger-near-
  edge taps.** Repeatedly during this walkthrough the gesture
  navigation pulled the launcher / Recents / a sister app
  (pageboy, shutterboy, whisperboy) instead of registering the
  intended tap inside SKB. Most cases were near `x < 150` or
  `y > 2200` — the home-gesture / back-gesture zones. **Severity:
  polish + a11y concern.** Expected: rail taps (`Now`, `Day`,
  `3-day`, `Week`, `Month` at `x ∈ [0,137]`) should register on
  SKB even when they sit inside the system-gesture inset. Actual:
  the rail's hit-target straddles the gesture inset and loses to
  it ~30% of the time on a 1080×2400 AVD. Owner: the schedule rail
  composable — needs a `Modifier.windowInsetsPadding(systemGestures)`
  or a hit-target shift of ~20dp to the right. Same applies to the
  bottom-bar items at `y=2200..2330`.

### Phase F — Briefings + reboot survival

- **NOT executed.** The plan requires `adb reboot` of the shared
  AVD; doing so would disrupt the sibling agents' work mid-flight.
  Will be a single-agent round.
- **Source confirms the reboot path exists**:
  `app/src/main/java/com/eight87/strictlykeptboy/notif/BootCompletedReceiver.kt`
  registers a one-shot `AlarmHorizonExtenderWorker` on
  `ACTION_BOOT_COMPLETED` + `ACTION_MY_PACKAGE_REPLACED`, plus a
  24-hour periodic horizon-slide. Manifest declaration:
  `AndroidManifest.xml` (search for the receiver). The wiring
  exists; behavioral confirmation under a real boot is deferred.

### Phase H — show-on-schedule + write-target switch

- **NOT executed** for the same AVD-contention reason. The Repos
  overlay is reachable (the prior round screenshot'd it) but the
  reactive flip + avatar-change + FAB-write-target verification
  needs deterministic input.
- **Adjacent finding from this round** (carried forward from the
  earlier walkthrough's B-2 BLOCKER): the wizard still does not
  create a new repo on Finish, so even if H.5 (write-target switch
  to a wizard-scaffolded repo) were tested today, there would be
  no second repo to switch to. **H.4 / H.5 are downstream of
  B-2** — fix B-2 first, then test these.

### Phase I — accessibility + edge cases

#### I.1 — Rotation (landscape) — [x] PASSES

- `adb shell settings put system user_rotation 1` rotates the AVD
  to landscape; SKB does NOT crash and adapts into a two-pane
  layout: month grid on the left, event-detail on the right.
  Screenshot: `docs/plans/screenshots/walkthrough-2026-05-24-completion/rotate-land.png`.
  This is the `WindowSizeClass.Expanded` master-detail behaviour
  (Round 2.1.H). Confirms commit 840b685 (`manifest:
  configChanges on MainActivity so rotation doesn't reset nav`) is
  load-bearing — the manifest entry at
  `app/src/main/AndroidManifest.xml:49` is doing real work.
- **Side-finding:** the right-pane event-detail header
  `Brighton weekend` on Sun May 24 in landscape shows the FULL
  multi-day range correctly: `Sat May 23 10:00 → Sun May 24 20:00
  (Arctic/Longyearbyen · Longyearbyen)`. This **partially
  contradicts the existing R-2** finding (event-detail sheet drops
  the multi-day end-date). The landscape right-pane composable
  formats correctly; the bottom-sheet on portrait does not. So R-2
  is real but scoped to the phone-portrait sheet specifically.
  Owner: `EventDetailSheet` (portrait); the landscape composable
  is its own renderer.

#### I.2 — Font scale 200% — [x] PASSES with caveats

- `adb shell settings put system font_scale 2.0` then relaunch
  SKB. Schedule Month view stays usable: rail labels truncate to
  `No...`, `3...`, `W...`, `M...` (acceptable — they're icons-with-
  text and the icon still reads); weekday headers wrap two-line
  (`MO/N`, `TU/E`, …). No clipping that hides content.
  Screenshot: `docs/plans/screenshots/walkthrough-2026-05-24-completion/font2x.png`.
- **[!] C-W-4 — New event sheet at 200% font has layout collisions.**
  At font_scale=2.0, opening the `+ New` FAB shows the
  `Free-form` / `From template` segmented control overlapping —
  the `From template` pill renders ON TOP of the `Free-form` pill
  rather than wrapping or scaling down. Also the Start / End
  fields show raw ISO `2026-05-24T12:00+02:00` instead of a
  formatted local representation (matches R-2 / M-5 vibe — the
  date formatters are too literal). **Severity: polish + bug.**
  Screenshot: `docs/plans/screenshots/walkthrough-2026-05-24-completion/settings-2x.png`
  (the file is misnamed — it actually shows the New-event sheet,
  not Settings; the Settings gear tap landed on `+ New` due to
  the 2x-scaled layout having moved the gear off-screen). Owners:
  - Pill overlap: the `EventCreate` segmented-control row — needs
    `Modifier.horizontalScroll` or `Arrangement.spacedBy + wrap`.
  - ISO date display: search `EventCreateSheet` / the
    Start/End TextField formatter.
  - Settings gear at 2x: also missing from the landscape-content-
    description tree — needs a `Modifier.padding` adjustment so it
    stays inside the top-bar at 2x.

#### I.3 — TalkBack — NOT executed

- Enabling TalkBack on the shared AVD via
  `settings put secure enabled_accessibility_services
  com.google.android.marvin.talkback/...TalkBackService` would
  disrupt the sibling agents' UI-driving (every tap triggers a
  TalkBack announce + double-tap-to-activate). Deferred.
- **Static-analysis stand-in:** searched `content-desc` /
  `contentDescription` coverage on the FAB cluster and bottom
  nav. Bottom nav has `content-desc = Schedule / Tasks / Reviews /
  Settings` (confirmed in this round's dumps). FAB cluster
  (Layout / Filter / Superseded / + New) — descs visible in dumps
  as `Edit schedule`, `Overlay picker`, `Switch repository`,
  generic. The `Switch repository` desc on the bottom-left avatar
  is the right label (lines up with U-5: that was a desc mismatch
  before).

#### I.4 — Deep-link routing — [!] BROKEN

- **[!] C-W-5 — `strictlykeptboy://event/<id>` deep-links route to
  the launcher (Schedule home) instead of the event detail.**
  - Repro: `adb shell am start -W -a android.intent.action.VIEW -d
    "strictlykeptboy://event/0190d040-7fab-7c50-9c1e-660000000040"
    -n com.eight87.strictlykeptboy/.MainActivity` lands on the
    Schedule pane at today's date with no detail sheet open.
    (Without `-n`, an Open-With chooser appears alongside
    shutterboy + Photos — separate manifest collision.)
  - Root cause: `MainActivity.kt:289` installs `deepLinkHandler`
    which ONLY handles `isShareLinkScheme(data)` —
    i.e. `strictlykeptboy://share/...`. The custom-scheme hosts
    declared in the manifest (`event`, `task`, `repo`, `bonus`,
    `review` — `AndroidManifest.xml:83–87`) parse via
    `ui/deeplink/DeepLinkRouter.kt`, which is **dead code** —
    `grep -rn "DeepLinkRouter\." app/src/main/java
    | grep -v Router.kt` returns ZERO callers.
  - **Severity: bug (Phase MM half-shipped).** The manifest
    advertises the schemes and the parser exists, but no caller
    invokes the parser. Expected: cold-start + `onNewIntent`
    classify the URI via `DeepLinkRouter.classify(...)` and route
    `OpenEvent`/`OpenTask`/`OpenRepo`/`OpenBonus`/`OpenReview`
    into the right Compose destination. Actual: every non-share
    `strictlykeptboy://` URI falls through into the default
    schedule pane.
  - Owner: `app/src/main/java/com/eight87/strictlykeptboy/MainActivity.kt:289-305`
    (`deepLinkHandler` definition) + the unwired
    `app/src/main/java/com/eight87/strictlykeptboy/ui/deeplink/DeepLinkRouter.kt`.
  - **Cross-reference**: the `Phase MM.1 — per-entity custom-
    scheme deep links` comment at `AndroidManifest.xml:78` flags
    this as a Phase MM milestone; manifest landed but the
    activity-side handler never did.

- **[!] C-W-6 — Manifest VIEW filter for custom-scheme URIs is too
  broad, surfacing an Open-With chooser alongside sister apps.**
  When the launching command does NOT pin the component
  (`-n com.eight87.strictlykeptboy/.MainActivity`), `am start -a
  android.intent.action.VIEW -d "strictlykeptboy://event/..."`
  pops the system Open-With chooser offering shutterboy + Photos.
  Either shutterboy/Photos are over-claiming `VIEW` for any
  `content:` / `*` URI, or SKB's intent filter for `strictlykeptboy`
  scheme is missing `category.BROWSABLE` and the chooser falls
  back to the broad `VIEW` set. **Severity: bug (deep-link UX).**
  Expected: any `strictlykeptboy://` URI resolves uniquely to SKB.
  Actual: chooser appears for cold-start URIs without explicit
  component pinning. Owner:
  `app/src/main/AndroidManifest.xml:77–93` (the `Phase MM.1`
  custom-scheme intent-filter block) — likely needs
  `<category android:name="android.intent.category.BROWSABLE" />`
  added so the filter sits in the right bucket.

### Round summary — new [!] findings count

- B.3: 1 new (`C-W-1` — Day-view missing onSwipeDay).
- D-continued: 2 new (`C-W-2` demo-read-only-test-note;
  `C-W-3` gesture-inset rail/bottom-bar hit-target collision).
- F: 0 new (source confirmed; behavioral test deferred to
  exclusive-AVD round).
- H: 0 new (downstream of B-2 BLOCKER).
- I: 3 new (`C-W-4` font-2x EventCreate pill overlap + raw ISO;
  `C-W-5` deep-link routing dead;
  `C-W-6` deep-link manifest filter pops Open-With chooser).

Most consequential single finding: **`C-W-5` deep-link routing**.
The whole `strictlykeptboy://event/...` family advertised in the
manifest is dead — manifest filters land in `MainActivity` but the
activity's `deepLinkHandler` only knows about `strictlykeptboy://share`.
Every notification-tap, every external `am start`, every Markdown
link in event bodies that points at another entity will fall
through to the schedule home pane silently. **Severity: bug**,
likely a Phase MM regression where the manifest half shipped and
the handler half didn't.

### What was intentionally NOT covered this round

- Full Phase F reboot test (needs exclusive AVD).
- Full Phase D Identity write-back (needs B-2 wizard fix first to
  produce a writable target).
- Phase H.4 / H.5 (needs B-2).
- TalkBack walk (needs exclusive AVD — TalkBack double-tap
  interferes with sibling agents).
- Per-toggle persistence audit on Auto & Tablet (table-stakes test
  but tedious — deferred).


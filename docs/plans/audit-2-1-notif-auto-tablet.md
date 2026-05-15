# Audit 2.1 — Notifications, Android Auto, Tablet adaptation

## Status: OBSOLETE — superseded by round-2-1.md ticks

Read-only audit of the three surfaces the user called out in the genesis prompt
("per-event notification control", "notification groups to toggle etc", "also
android auto and tablets") plus the lifestyle wizard's morning/evening briefings
that should honor `identity.toml`. Scope: as-shipped state on `main`
(2026-05-13), against the intent in `prompts.md` and the architecture in
`main.md` phases M / Q / R / S.4 / BBB.

## 1. Notifications

### 1.1 Channels and groups (Phase M.1 — shipped)

`notif/NotificationChannels.kt` registers seven channels under a single
`NotificationChannelGroup` (`skb_main`):

| id | importance | lockscreen | vibrate | badge |
|---|---|---|---|---|
| `skb.events` | DEFAULT | PUBLIC | yes | yes |
| `skb.events.atomic` | HIGH | PUBLIC | yes | yes |
| `skb.tasks` | DEFAULT | PUBLIC | yes | yes |
| `skb.briefings` | LOW | PUBLIC | no | no |
| `skb.sync` | MIN | SECRET | no | no |
| `skb.errors` | HIGH | PUBLIC | yes | yes |
| `skb.service` (foreground) | LOW | SECRET | no | no |

`ALL` list is used by registration test. Group is just an OS-side display
grouping ("strictlykeptboy" heading in system Settings) — it is NOT a
user-facing app-level "notification group" concept the user can toggle on/off.

### 1.2 Per-event override path (per the user's intent — partial)

There is **no per-event mute toggle in the UI today.** What exists:

- `Reminder` data class (`store/Reminder.kt`) round-trips a per-event
  `[[reminder]]` TOML array with `offset`, `kind`, optional per-reminder
  `channel`, and optional per-reminder `lockscreenVisibility` override. CLI
  surface (`skb reminder add|rm|list`) shipped in BBB.14. Tests round-trip
  cleanly (`ReminderParseTest`).
- `EventReminderScheduler` (M.2) consumes a `ReminderInput` snapshot and arms
  `AlarmManager.setExactAndAllowWhileIdle` alarms keyed
  `(repoId, eventId, leadTime)`. Idempotent re-schedule replaces in place.
  Cancel-all-for-event exists (`cancelFor`).
- `ReminderBroadcastReceiver` posts the notification, honors per-channel
  enable, and respects `EXTRA_PRIVATE` by swapping in a generic title +
  `setPublicVersion`. Snooze 10/30/60 + "I didn't" + "Partial" actions all
  wired through `DeviationActionWriter` (M.6 — shipped).

**Gap:** the per-event reminder array shipped on the **data + CLI** layer. No
event-detail UI exposes "mute reminders for this event" or "edit this event's
reminders" — BBB.4 + BBB.6 (UI surfaces + per-kind renderers) are deferred.

### 1.3 Notification group toggles (per the user's intent — MISSING)

The user explicitly asked for "notification groups to toggle etc" — e.g.
"mute all work-calendar reminders until Monday".

What ships:

- Per-channel enable + silent toggles via `NotificationPrefs.setChannelEnabled`
  / `setChannelSilent` (M.3) — the UI surface is `NotificationsCategory.kt`.
- Per-`(repoId, calendarId)` enable + silent + lead-time-override prefs in
  `NotificationPrefs.isCalendarEnabled / isCalendarSilent /
  calendarLeadTimes`. **Backing storage shipped; there is no Settings UI
  binding to them.** They can only be flipped programmatically today.
- Per-template-category default lead-times (`categoryLeadTimes`) — Settings
  surface for `medical` / `flight` / `household` / `general` shipped in S.4.

**Missing — deferred per main.md Phase M's "Deferred (NS-Z follow-up)" line and
plan NS-A.10..14:**

- `NotificationLogicalGroup` data class (NS-A.10) — not started.
- Per-calendar group toggle UI — `isCalendarEnabled` keys exist, no
  composable surface.
- Time-bounded group mute ("until Monday") — no model, no UI, no scheduler.
- `setGroup` / `setGroupSummary` / `InboxStyle` not called anywhere in the
  notification builder (verified by repo-wide grep — zero hits). The
  Android-level grouping that would collapse a multi-fire burst is **not
  wired**.

### 1.4 Identity.toml integration (MISSING)

Notif builders do **not** read `IdentityToml` at all. `notif/` package has zero
references to `praiseTerm`, `honorificForDom`, or `IdentityTomlCodec` (verified
by grep). Notification body uses `R.string.notif_event_role_pre` —
locale-static. No salutation, praise term, register-tone branching in the
notif path. Plan main.md DDD.11 calls this out as deferred:

> "alternation logic + briefing salutation + notification bodies remain to wire
> into NS-* + briefing renderers."

### 1.5 Briefings (MISSING end-to-end)

`BRIEFINGS` channel exists at LOW importance. `NotificationPrefs` has a master
`isBriefingsEnabled()` toggle (default ON), surfaced in
`NotificationsCategory`. **Nothing fires briefings today:**

- No `cal-briefings` system calendar materialized at wizard time
  (BBB.11 deferred).
- No `BriefingComposer` or auto-body generator (NS-Z.13..NS-Z.17 not started).
- `ReminderKind.TomorrowBriefing` is recognized by the codec, but the firing
  path (BBB.10) is deferred — `ReminderCollapsing` shipped as a pure helper,
  no WorkManager invocation.
- No morning + evening default 07:00 / 21:00 `FREQ=DAILY` events seeded.

### 1.6 Multi-reminder collapsing (BBB.10 — helper only)

`ReminderCollapsing.collapse(fires: List<Instant>)` groups fire-instants into
60-second buckets returning ordered `Bucket(anchor, members)` lists. **Pure,
tested, and unused.** No scheduler call site invokes it; no notification
builder consumes its output via `InboxStyle`. BBB.10 marked deferred — "WorkManager
firing path deferred".

### 1.7 Boot re-arm (deferred)

`RECEIVE_BOOT_COMPLETED` permission is declared in the manifest, but **no
receiver class registered** (verified — manifest declares only
`ReminderBroadcastReceiver`, `AtomicEventReceiver`, `SubbeatBoundaryReceiver`,
both widget providers). After a phone reboot every alarm scheduled before the
reboot is lost. Listed under M's "Deferred" line + NS-Z.1.

## 2. Android Auto

### 2.1 What ships (Phase Q.1..Q.3, Q.5)

- `auto/SkbCarAppService.kt` — `CarAppService` with `ALLOW_ALL_HOSTS_validator`
  (relaxed for v1 — production tightening listed in the file's own comment).
- `auto/SkbSession.kt` — entry session; reads `CarAppRuntime.todayEventSource`
  parked by `AppGraph.parkRuntimes` and falls back to an empty source if cold-
  started before the phone app.
- `auto/TodayScreen.kt` — `ListTemplate` of today's `MaterializedInstance`s
  sorted by `effectiveStart`, one `Row` per instance with `HH:mm + title` +
  secondary `emoji ?: calendar.id`. Empty-state via `setNoItemsMessage`.
- `auto/NextUpScreen.kt` — `PaneTemplate` with title row, duration row,
  time-until row, up-to-3 follow-up rows, and an "Open in app" pane action
  that dispatches `Intent.ACTION_VIEW` at `MainActivity`. No edit actions.
- `auto/TodayEventSource.kt` — ISP-narrow `fun interface` returning today's
  `List<MaterializedInstance>`. Wired through `AppGraph.todayEventSource ⇒
  renderTodaySync()`.
- Manifest has `androidx.car.app.category.IOT`, `automotive_app_desc.xml`,
  `minCarApiLevel=1`.

Read-only enforced by construction (no `OnClickListener` writes anywhere in
Q.1..Q.3). `CarAppRuntimeTest` covers the read-only template contract (Q.5).

### 2.2 Voice intent (Q.4 / Phase HH — DEFERRED, confirmed)

Confirmed deferred. Zero references to `RecognizerIntent`, `SpeechRecognizer`,
or `CarAppService.onCarConfigurationChanged` voice hooks. The "Open in app"
pane action is the only outbound intent.

### 2.3 Today's schedule, drive-friendly?

Yes for the **read** path: title + time + secondary line are large-text-only;
no complex layouts. **Concern:** the rendered title is
`getString(R.string.auto_row_title, time, evt.title)` — if a user's event title
is long (e.g. an atomic-routine sub-step name), no truncation policy is applied
in code. Android Auto's template host clips at host-defined widths, so this is
probably OK in practice but is not explicitly tested.

**No identity-driven tone in Auto either** — same gap as Section 1.4. The
driving sub would not hear "your 4pm, Sir" from the Auto surface; it'd hear
the raw event title.

## 3. Tablet adaptation

### 3.1 Phase R master-detail (R.1..R.5 — shipped)

- `ui/adaptive/WindowSizeClass.kt` — sealed `WindowWidthSizeClass`
  (Compact / Medium / Expanded) + `LocalWindowWidthSizeClass` CompositionLocal
  + `ProvideWindowSizeClass` `BoxWithConstraints` wrapper at the AppScaffold
  root.
- `ui/adaptive/MasterDetailLayout.kt` — hand-rolled 38/62 split `Row`,
  gutter widens on Expanded. Explicitly avoids `material3-adaptive` alpha.
- `ui/adaptive/AdaptiveSpacing.kt` — interactive target 48/56/64 dp +
  spacing multiplier 1.0×/1.25×/1.5× × Compact/Medium/Expanded, layered on
  top of `LocalDensityScale` via `adaptiveDp(base)`.

### 3.2 Per-screen coverage (audit)

Files referencing `WindowSizeClass` / `MasterDetailLayout`:

- `ui/scaffold/SkbAppShell.kt` — rail expansion on Medium/Expanded.
- `ui/schedule/SchedulePane.kt` — R.2 schedule master-detail (now-card
  auto-focus).
- `ui/tasks/TasksPane.kt` — R.3 tasks master-detail.
- `ui/settings/SettingsPane.kt` — R.4 settings master-detail.

**Screens with NO width-class adaptation:** `ui/repos/`, `ui/wizard/`,
`ui/together/`, `ui/trip/`, `ui/reviews/`, `ui/deeplink/`, `ui/import_export/`,
`ui/share/`. On a 10" tablet, these screens stretch their phone-shaped Column
across the full width — usable but visually undertuned. The Together-finder
and the Lifestyle wizard especially deserve a two-pane treatment (form left,
results / preview right).

### 3.3 Notifications settings on tablet

`NotificationsCategory` is a single `Column + verticalScroll`. On Expanded it
displays at column-width inside the SettingsPane detail pane (62% of screen).
That's acceptable but doesn't split the per-channel surface from the
per-category cadence surface into two columns. Easy R-follow-up if desired.

### 3.4 Wifi-adb + tablet posture

CLAUDE.md confirms wifi-adb to the phone as the long-term test target.
**There is no tablet AVD in `scripts/start-avd.sh` or in CLAUDE.md's test
loop** — Phase R was verified by resizing the existing `medium_phone` AVD per
the R.5 phase note ("verified visually on the tablet-resized AVD"). The
user has access to a real tablet, but no codified test loop targets it.

## 4. Gap analysis

### Shipped end-to-end

- Phase M.1..M.6 channels + per-channel UI + Alarm scheduling + snooze
  + deviation actions + sync + foreground.
- Atomic / sub-beat alarm path (XX.3 / XX.9).
- `Reminder` codec + CLI + collision-collapse helper (BBB.7 / BBB.8 /
  BBB.9 / BBB.14 / BBB.15).
- Phase Q.1..Q.3 + Q.5 Android Auto Today + NextUp.
- Phase R.1..R.5 tablet master-detail for schedule / tasks / settings.

### Claimed shipped but doesn't fire end-to-end

- **Briefings** — channel + master toggle + per-category lead-time prefs
  shipped, but no `cal-briefings` calendar is seeded, no body generator runs,
  no `WorkManager` job fires. Master toggle controls nothing today.
- **Per-event reminder array** — round-trips on disk + CLI, but never armed
  by `EventReminderScheduler` from the watcher path (the watcher still feeds
  legacy `ReminderInput.leadTimes` from `notifications` frontmatter or
  `DefaultCadences`, not the new `[[reminder]]` array). Trace the call sites:
  `EventReminderScheduler.scheduleAll` is invoked, but no producer maps
  `Reminder` → `ReminderInput`.

### Stubbed

- `RECEIVE_BOOT_COMPLETED` permission held, no receiver — alarms lost on
  reboot.
- `NotificationLogicalGroup` (NS-A.10..14) — data class not even drafted.
- D.79 per-category cadence "use at template-apply time" — `DefaultCadences`
  exists, no consumer in the materializer.
- D.81 cal-briefings auto-bodies — not started.
- D.82 `post_event_checkin` opt-in UI — `ReminderKind.PostEventCheckin`
  parsed, no opt-in surface.
- D.80 off-schedule warning prefix — no `⚠` glyph branch in any builder.
- NS-D.13 `setPublicVersion` redacted-body retrofit — only private events get
  `setPublicVersion`; non-private events get nothing.

### Identity layer — fully unwired

Notif + Auto + briefings all bypass `IdentityToml`. The user's wizard-driven
honorific / praise term / pronouns / register choices land in `IdentityPrefs`
+ `identity.toml`, but the user never hears them back from the notification
surface — the surface where they'd matter most.

## 5. Round 2.1 proposed sub-steps

### Phase 2.1-Notif

- [ ] **2.1-Notif.1** Wire `Reminder[]` → `ReminderInput[]` in the event-watch
      indexer so the per-event array actually arms alarms. Fall back to the
      legacy `notifications` string array only when the new array is empty.
      Add a regression test asserting that an event with one `[[reminder]]`
      block schedules exactly one alarm at the correct offset.
- [ ] **2.1-Notif.2** Per-event mute toggle in `EventDetailContent` (Compact
      sheet + tablet detail pane). Persists to `NotificationPrefs` under
      `event.<repoId>.<eventId>.muted = true`; receiver short-circuits.
- [ ] **2.1-Notif.3** Logical-group data class + per-calendar mute toggle UI.
      Backing keys (`cal.<repoId>.<calId>.enabled`) already exist in
      `NotificationPrefs`; add a `LogicalGroup` sealed type (Calendar /
      Category / Repo) and a settings surface that lists every loaded
      calendar with an enable + silent + lead-time-override row.
- [ ] **2.1-Notif.4** Time-bounded group mute ("mute until Monday"). New
      `NotificationMute(scope: LogicalGroup, untilEpochMs: Long)` row;
      `ReminderBroadcastReceiver` consults it in addition to channel/group
      enable. Stored in the same prefs file.
- [ ] **2.1-Notif.5** Identity wiring in notif bodies. Inject an
      `IdentityTomlCodec.readOrDefault(repoRoot)` snapshot into the receiver
      (via the existing `CarAppRuntime`-pattern parked handle). Body renders
      praise term + honorific per register; `private = true` still wins and
      collapses to generic copy.
- [ ] **2.1-Notif.6** Briefings firing path. WorkManager periodic worker at
      07:00 + 21:00 daily; consumes `BriefingComposer` (new) walking today's
      / tomorrow's `MaterializedInstance`s, renders `InboxStyle` with one
      line per event, identity-honoring salutation. Master toggle now
      actually toggles something.
- [ ] **2.1-Notif.7** `cal-briefings` seed at wizard completion + on first
      identity write; idempotent. Two recurring events
      (`morning-briefing` 07:00, `evening-briefing` 21:00) with
      `auto_generated = true`.
- [ ] **2.1-Notif.8** `BootCompletedReceiver` re-arms every reminder for the
      next 24h on boot + listens to `ACTION_MY_PACKAGE_REPLACED` for app
      upgrade. Manifest entry + `AlarmHorizonExtender` nightly worker for the
      sliding 7d horizon.
- [ ] **2.1-Notif.9** `NotificationCompat.InboxStyle` stacking. Call site
      consumes `ReminderCollapsing.collapse` output; collapsed-preview
      privacy = "N reminders" if any constituent has `private = true`.

### Phase 2.1-Auto

- [ ] **2.1-Auto.1** Title truncation policy — clip event title to host max
      (24 chars heuristic) with a leading `…` when truncated; tested by a
      Robolectric `CarAppRuntimeTest` extension.
- [ ] **2.1-Auto.2** Identity-driven row text — opt-in (the driving sub
      probably wants `"your 4pm, Sir"`-style copy). Reads
      `IdentityPrefs.tone`; falls back to plain `auto_row_title` template.
- [ ] **2.1-Auto.3** Off-schedule warning surfacing — when the resolver
      flags an instance as off-schedule (RV-Q), prefix the row with `⚠`.
- [ ] **2.1-Auto.4** Empty-state copy honors `IdentityPrefs.praiseTerm` —
      "all clear, good boy" / "nothing scheduled, Sir" register-branched.
- [ ] **2.1-Auto.5** `HostValidator` tightening — switch from
      `ALLOW_ALL_HOSTS_VALIDATOR` to the AOSP + AndroidAuto signature
      whitelist for release builds; keep ALL_HOSTS for `debug`.

### Phase 2.1-Tablet

- [ ] **2.1-Tablet.1** Wizard pane two-column on Medium/Expanded — current
      step left (38%), live identity-driven preview right (62%). Reuses
      `MasterDetailLayout`. Closes the gap that the lifestyle wizard is the
      first surface a tablet user sees.
- [ ] **2.1-Tablet.2** Together-finder two-column on Medium/Expanded — input
      form left, ranked free-slot list right. Closes part of N.3.
- [ ] **2.1-Tablet.3** Repos master-detail — repos list left, selected repo's
      remotes + identity + mode editor right.
- [ ] **2.1-Tablet.4** Import/export wizard two-column — file picker /
      validation left, preview-of-events-to-import right.
- [ ] **2.1-Tablet.5** Notifications category two-column on Expanded — per-
      channel rows left, per-category lead-times + per-calendar group toggles
      right.
- [ ] **2.1-Tablet.6** `scripts/start-tablet-avd.sh` — codify a 10" tablet
      AVD profile (1600×2560 mdpi 160dpi or pixel_tablet) + add a
      `--tablet` flag to the existing start script. Update CLAUDE.md test
      loop to call out the dual-target requirement for any phase that
      claims tablet adaptation.
- [ ] **2.1-Tablet.7** Verification pass on the real tablet (wifi-adb) for
      every R-touched screen + every 2.1-Tablet sub-step above; capture
      screenshots into `docs/qa/tablet-2-1/`.

## Status

This audit is read-only — no source files modified.

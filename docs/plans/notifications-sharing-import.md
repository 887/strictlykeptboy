# notifications-sharing-import — deep-dive plan

## Status: 🚧 IN-PLANNING

Owns the deep mechanics behind `main.md` Phases **M** (notifications),
**O** (sharing + read-only) and **P** (import/export). Cross-link
discipline:

| `main.md` phase | NS phases here |
|---|---|
| Phase M — notifications | NS-A, NS-B, NS-C, NS-D |
| Phase O — sharing + read-only | NS-E, NS-F, NS-G |
| Phase P — import/export | NS-H, NS-I, NS-J, NS-K |

All architectural choices below derive from `decisions.md`. The locked
decisions that govern this doc most directly are **D.14** (notifications),
**D.15** (identity + attribution), **D.16** (import/export) and **D.9**
(multi-repo + read-only sharing). Where this doc resolves an unforeseen
tradeoff, the resolution is written as a `**Decision:**` line inline —
this doc never punts back to the user.

---

# Part 1 — Notifications

## Phase NS-A — notification channels + groups

Android notification channels are user-visible OS-level toggles
(introduced in Android O = our minSdk 26, so we're always on the
channel-first model). Strictlykeptboy ships **five** channels and one
**channel group** to cluster them in system settings.

- [ ] **NS-A.1** Define a single `NotificationChannelGroup` with id
      `skb_main` and name `"strictlykeptboy"`. All five channels live
      under this group so the system Settings → Notifications screen
      shows them collapsed under one heading rather than scattered
      between unrelated app channels.
- [ ] **NS-A.2** Channel `skb.events`:
      - `id = "skb.events"`
      - `name = "Events"` (localized)
      - `description = "Reminders for upcoming calendar events"`
      - `importance = IMPORTANCE_DEFAULT`
      - `setSound(defaultNotificationSound, audioAttributesNotification)`
      - `enableVibration(true)`, default vibration pattern
      - `enableLights(true)`, light color = M3 secondary (we pick a
        neutral cyan rather than red so the LED isn't read as "alert")
      - `lockscreenVisibility = VISIBILITY_PUBLIC`
      - `setShowBadge(true)`
      - `setBypassDnd(false)` (events do not bypass DND by default;
        per-channel user override possible via system Settings)
- [ ] **NS-A.3** Channel `skb.tasks`:
      - Same as `skb.events` but `name = "Tasks"`.
      - `IMPORTANCE_DEFAULT`, sound, vibration, public lockscreen.
      - **Decision:** tasks reuse the same vibration pattern as events
        (single short pulse). Differentiating vibrations is a UX wart
        few users actually decode. Channel name + content text carry
        the differentiation.
- [ ] **NS-A.4** Channel `skb.sync`:
      - `name = "Sync results"`
      - `IMPORTANCE_LOW`
      - `setSound(null, null)` — no sound
      - `enableVibration(false)`
      - `enableLights(false)`
      - `lockscreenVisibility = VISIBILITY_SECRET` (sync is
        operational noise; should not appear on the lockscreen)
      - `setShowBadge(false)`
- [ ] **NS-A.5** Channel `skb.errors`:
      - `name = "Errors"`
      - `IMPORTANCE_HIGH` (heads-up popup)
      - default sound, vibration, lights
      - `lockscreenVisibility = VISIBILITY_PUBLIC`
      - `setBypassDnd(false)` (still respect DND — auth errors aren't
        emergencies; user can promote to bypass-DND in system settings
        if they want).
- [ ] **NS-A.6** Channel `skb.service`:
      - `name = "Background sync"`
      - `IMPORTANCE_MIN` (no heads-up, no sound, dim icon)
      - `setShowBadge(false)`
      - `lockscreenVisibility = VISIBILITY_SECRET`
      - This channel is used **exclusively** by the persistent
        foreground-service notification.
- [ ] **NS-A.7** Idempotent registration: register all channels +
      group on every `App.onCreate` via
      `NotificationManagerCompat.createNotificationChannelGroup` and
      `createNotificationChannel`. The OS dedupes by id; calling
      every cold-start is the documented pattern.
- [ ] **NS-A.8** `IMPORTANCE_*` is the **initial** importance. Once
      the channel exists, the user owns it — the app cannot programmatically
      raise importance afterwards. This is fine; we never want to
      override the user. Document this in the Settings → Notifications
      screen with an explanatory line: "Channel importance is owned by
      the system. Tap to open system settings."
- [ ] **NS-A.9** Channel deletion: do not delete channels on uninstall
      logic; rely on Android to clean them. If we ever want to rename
      a channel, do it via a NEW id (`skb.events.v2`) and orphan the
      old one — Android disallows renaming an existing channel id's
      visible name silently across versions if user has touched it.

### Logical "notification groups" (D.14) — DISTINCT from Android channels

Per `decisions.md` D.14 the user-facing concept of "notification groups"
is the user's logical bucketing — *Work, Personal, Fitness,
Relationship, Chores* — and is **not** the same thing as Android
`NotificationChannel` or `NotificationChannelGroup`.

- [ ] **NS-A.10** Define `NotificationLogicalGroup` data class
      (id, display_name, default_importance_override, muted_bool).
      Stored in app prefs (`EncryptedSharedPreferences` is unnecessary
      for this — plain `DataStore<Preferences>`).
- [ ] **NS-A.11** Mapping: each `calendars/<calendar-id>/calendar.toml`
      carries a `default_notification_group = "<group-id>"` field
      (already locked in D.5's calendar metadata; re-stated here).
- [ ] **NS-A.12** Default groups shipped on first run:
      `work`, `personal`, `fitness`, `relationship`, `chores`. User
      can rename / add / remove via Settings → Notifications → Groups.
- [ ] **NS-A.13** Group-level toggle: "Mute Fitness group" — when on,
      every notification whose source calendar resolves to that group
      is suppressed at post-time, regardless of the per-event config.
- [ ] **NS-A.14** Group-level importance override: a group can set
      `importance_override` to one of {min, low, default, high}. When
      posting a notification belonging to a group with an override,
      the post-time `Importance` shifts accordingly *for that
      notification only* (Android channels remain fixed; we use
      `setPriority` + the channel-importance interaction is what we
      get on 26+ — channel importance is the ceiling on 26+, but we
      can lower via `setPriority` and skip auto-bundling). **Decision:**
      group-importance can only LOWER, never raise above the channel
      ceiling. Raising would require a new channel; we won't proliferate
      channels for this. Document the limitation in the group editor.

---

## Phase NS-B — per-event notification config + lead-time grammar

- [ ] **NS-B.1** Frontmatter shape:

      ```toml
      +++
      id = "0190f8a3-7c2c-7c10-9100-c5c0f9e3b811"
      title = "Dentist"
      start = "2026-05-12T14:00:00+02:00"
      end   = "2026-05-12T15:00:00+02:00"
      calendar = "personal"
      author = "alex"
      notifications = ["1d", "1h", "15m"]
      +++

      Bring the X-rays.
      ```

      The `notifications` array is OPTIONAL. Missing or `[]` =
      respect the calendar's default lead-times; if the calendar has
      no defaults, no notifications fire.

- [ ] **NS-B.2** Calendar-level defaults: `calendars/<id>/calendar.toml`
      carries `default_notifications = ["15m"]` etc. Per-event override
      replaces — does not merge with — calendar defaults. **Decision:**
      replace-not-merge keeps the model predictable; users who want to
      add a single warning on top of defaults can spell out the full
      list once.

- [ ] **NS-B.3** Lead-time grammar (regex `^(\d+)([mhdw])$`):
      - `m` = minutes (1..59 typical, but allow up to 1440)
      - `h` = hours (1..72)
      - `d` = days (1..30)
      - `w` = weeks (1..8)
      - Examples: `5m`, `30m`, `1h`, `2h`, `1d`, `2d`, `1w`.
      - **Decision:** no compound `1h30m`. One unit per entry. Users
        wanting 90 minutes write `90m`. Keeps parser trivial and
        round-trip lossless.
      - **Decision:** also accept `now` as a literal (lead-time = 0).
        Rare but useful for "alarm at start time." Stored as `0m` on
        write-back; accepted as either on read.

- [ ] **NS-B.4** Parser implementation: pure-Kotlin function
      `parseLeadTime(s: String): Duration?` returning `kotlin.time.Duration`.
      Returns null on invalid; UI surfaces validation error inline
      ("invalid lead-time: '`5x`'") next to the chip in the editor.

- [ ] **NS-B.5** Editor UI: chips of existing lead-times with `×` to
      remove + a chip-row of common presets (`5m`, `15m`, `1h`, `1d`,
      `1w`) + a free-text input. Adding from preset = append.

- [ ] **NS-B.6** Per-event override of importance / channel / group is
      OPTIONAL frontmatter:

      ```toml
      notifications = ["15m"]
      notification_importance = "high"        # one of: min,low,default,high
      notification_channel = "events"         # one of: events, tasks, errors
      notification_group = "work"             # group id; overrides calendar default
      ```

      **Decision:** these three keys are escape-hatches; the typical
      event uses none. Validation: `notification_channel` must be one
      of {events, tasks, errors} (sync/service forbidden — those are
      not user-bound channels).

- [ ] **NS-B.7** Recurrence interaction: per-recurrence
      `notifications` lives in the rule file's frontmatter. Each
      materialized instance inherits from the rule. An exception of
      kind `override` may carry a `notifications` array which replaces
      the inherited one for that single instance.

- [ ] **NS-B.8** Task notifications: `due` is the anchor. Tasks
      inherit per-todolist defaults the same way. Standing tasks
      (no due) cannot fire notifications. Pinned reminders for
      standing tasks are out of scope v1.

---

## Phase NS-C — AlarmManager scheduling + boot re-arm

- [ ] **NS-C.1** Permission strategy:
      - `RECEIVE_BOOT_COMPLETED` — declare in manifest; required to
        re-arm on boot.
      - `POST_NOTIFICATIONS` (Android 13+ runtime).
      - Exact-alarm permission: **Decision: use
        `USE_EXACT_ALARM`** for Android 14+ where eligible, falling
        back to `SCHEDULE_EXACT_ALARM` runtime grant for Android 13
        and below the policy boundary.
        Calendar/reminder apps are explicitly listed in the
        Play Console policy as an allowed use case for `USE_EXACT_ALARM`,
        which is granted at install with no user prompt. We declare
        BOTH in the manifest with appropriate `tools:targetApi` /
        `<uses-permission ... />` and check
        `AlarmManager.canScheduleExactAlarms()` at runtime.
        On the rare device where the system denies, we fall back to
        `setAndAllowWhileIdle` (inexact) and surface a Settings
        banner offering a deep-link to the system "Alarms & reminders"
        screen via `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.

- [ ] **NS-C.2** Manifest:

      ```xml
      <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
      <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
      <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
      <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
      <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
      <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
      <uses-permission android:name="android.permission.WAKE_LOCK" />
      ```

- [ ] **NS-C.3** Alarm registry table (Room):

      ```
      table alarm_schedule
        pk: alarm_id (string, primary)
        repo_id (string)
        entity_kind ("event" | "task" | "occurrence")
        entity_id (string — UUIDv7 for events/tasks; "<rule>:<date>" for occurrences)
        lead_time_seconds (long)
        fire_at_utc_millis (long)
        channel_id (string)
        group_id (string nullable)
        importance_override (string nullable)
        title_snapshot (string)        # captured at schedule time for cheap rendering
        body_snapshot (string)
        snoozed_from (long nullable)
      ```

      The snapshot fields let the AlarmReceiver build the
      notification without hitting the file store at fire time
      (faster, robust against transient I/O failures).

- [ ] **NS-C.4** `alarm_id` formula:
      `<repo-id>:<entity-id>:<lead-seconds>`. Stable: re-scheduling
      with the same triple replaces the same `PendingIntent` (because
      we use the alarm_id's hashCode as the requestCode and the
      Intent's data Uri carries the alarm_id). Use
      `PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`.

- [ ] **NS-C.5** Scheduling API:

      ```kotlin
      alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          fireAtUtcMillis,
          buildPendingIntent(alarmId)
      )
      ```

      `setExactAndAllowWhileIdle` is the right call for
      reminder-style alarms — fires through Doze, exact, no inexact
      windowing.

- [ ] **NS-C.6** Reschedule lifecycle hooks (each triggers an
      `AlarmRescheduler` worker that diffs the alarm table against
      the resolved entity set):
      - on event create
      - on event edit (cancel-then-schedule the affected alarm_ids)
      - on event delete (cancel all alarm_ids for that entity_id)
      - on recurrence rule edit (re-resolve all instances inside the
        next 30-day rolling window — see NS-C.10)
      - on exception create/edit/delete (re-resolve the targeted
        instance)
      - on calendar group/mute changes (cancel alarms for muted
        groups; re-arm on unmute)
      - on per-event `notifications` array change

- [ ] **NS-C.7** `BootReceiver` (manifest-registered, action
      `android.intent.action.BOOT_COMPLETED`):
      - reads the `alarm_schedule` table
      - drops rows whose `fire_at_utc_millis < now`
      - re-arms every remaining row
      - **Decision:** also handle `ACTION_LOCKED_BOOT_COMPLETED`
        (Direct Boot) by registering with
        `android:directBootAware="true"`. Our DB currently lives in
        Credential Encrypted storage so we can't read it pre-unlock;
        the Direct Boot receiver simply schedules a one-shot
        `WorkManager` job that runs as soon as user-unlock happens
        and re-arms then. This avoids missing alarms for users who
        boot but don't unlock for hours.

- [ ] **NS-C.8** Time-zone correctness:
      - All `fire_at_utc_millis` are stored UTC. Computed from the
        event's local `start` + the event's `tz` (`TZID`) - lead.
      - On time-zone change (`ACTION_TIMEZONE_CHANGED` system
        broadcast) we DO NOT shift fire times — UTC instants are
        absolute. We only re-render visible UI.
      - On DST transitions, since we computed UTC at schedule time,
        the alarm fires at the correct wall-clock instant.

- [ ] **NS-C.9** Exception interaction:
      - `cancel` exception → cancel alarms for that occurrence id.
      - `override` exception → cancel + re-schedule using the
        override's start time and (if present) override's
        `notifications`.
      - `note` exception → no alarm change.

- [ ] **NS-C.10** Recurring-event horizon:
      - We materialize alarms for occurrences within a **rolling
        30-day forward window**. (Stable, safe, avoids a runaway 1000-row
        alarm table for "every 5 minutes" pathological RRULEs.)
      - A nightly `WorkManager` job (`AlarmHorizonExtender`) runs at
        02:30 local; it slides the window forward by one day, adding
        new alarms for occurrences entering the horizon and pruning
        ones that have fired.
      - **Decision:** for very-short-period RRULEs we cap at 200
        scheduled alarms per recurrence rule per horizon. Above that
        we surface a warning in the rule editor: "this rule fires too
        frequently to alarm; mute it or widen the period". The
        `AlarmManager` system limits are themselves around 500
        per-app on some OEM ROMs, so the cap protects us.

- [ ] **NS-C.11** Alarm fire path:
      - `AlarmReceiver` (BroadcastReceiver) wakes (via
        `RTC_WAKEUP`).
      - Uses `goAsync()` to extend lifetime briefly.
      - Reads the snapshot row from the alarm table.
      - Builds the `Notification` per NS-D content rules.
      - Posts via `NotificationManagerCompat.notify(notificationId, n)`.
      - `notificationId = alarm_id.hashCode()` — replaces an
        existing post for the same alarm (e.g. if user dismissed and
        we re-fire post-snooze).
      - Marks the alarm row `fired_at` for audit (or deletes if
        not snoozable).

- [ ] **NS-C.12** Mute logic at fire time: the receiver checks the
      current mute state of the resolved group / channel / per-repo
      auto-sync state and may suppress. **Decision:** mute is checked
      at fire time, not at schedule time, so toggling a group mute
      after scheduling Just Works without re-arming.

---

## Phase NS-D — notification content + actions

- [ ] **NS-D.1** Title rendering:
      - `<emoji> <title>` if event has an emoji prefix.
      - Truncate at 50 chars, ellipsize.

- [ ] **NS-D.2** Body rendering (multi-line via
      `BigTextStyle` for Android, with the inline single-line
      compact form):
      - Line 1: `HH:mm – HH:mm  •  <calendar-name>`
      - Line 2: `<location>` (if any)
      - Line 3: `by <author-display-name>` (or initials chip text)
      - All-day events: `All day  •  <calendar-name>`.
      - Author chip: notifications don't render circles cleanly, so
        we use plain text "by AS" (initials) or "by Alex 887"
        (display name) — **Decision:** always display initials in
        compact form, full name in expanded `BigTextStyle`. Maximum
        cross-OEM compatibility.
      - Multi-line body uses
        `NotificationCompat.BigTextStyle().bigText(...)`.

- [ ] **NS-D.3** Small icon: app mascot silhouette monochrome (a
      simplified bat-in-hoodie outline). Required to be opaque
      single-color. Asset: `ic_notification.xml` (vector drawable).

- [ ] **NS-D.4** Color: `setColor(themeAccentInt)` — the Material You
      seed color, looked up at notification-build time. Falls back to
      a fixed brand cyan if dynamic color unavailable.

- [ ] **NS-D.5** Actions (events):
      - `Snooze 10 min`
      - `Snooze 1 hour`
      - `Open`

      Snooze actions wrap the alarm_id in a broadcast intent that
      schedules a fresh alarm and dismisses the current one. Open
      action is a `PendingIntent.getActivity` deep-linking to event
      detail.

      **Decision:** we render only TWO snooze actions plus Open
      (action-bar real estate is tight; three actions max on most
      OEMs). For the third option ("snooze 1 day"), use a long-press
      reply or — cleaner — provide the option inside the in-app
      event detail's context menu.

- [ ] **NS-D.6** Actions (tasks):
      - `Snooze 1 hour`
      - `Mark done`
      - `Open`

      "Mark done" writes `done = true` to the task file via the
      file store and triggers a sync. **Decision:** the write happens
      on the WorkManager queue, not in the broadcast receiver, to
      stay within receiver runtime limits.

- [ ] **NS-D.7** Tap intent: deep links to
      `app://strictlykeptboy/event/<repo-id>/<event-id>` (or
      `task/<repo-id>/<task-id>`). Handled by a single
      `DeepLinkActivity` that resolves and navigates.

- [ ] **NS-D.8** Snoozed-state storage: a single
      `DataStore<Preferences>` map of `alarm_id → snoozed_until_utc`.
      The fire path consults this and (if snoozed) suppresses the
      original; the snooze action separately schedules a
      synthetic alarm with `alarm_id = "<orig>:snoozed:<n>"`.
      **Decision:** snooze state lives ON-DEVICE only, never in the
      Git repo. Snoozes are an interaction-with-this-device concept;
      sharing snoozes across devices via Git would create absurd
      conflict cases.

- [ ] **NS-D.9** Sync result notification (off by default per D.14):
      - When user enables Settings → Notifications → "Show sync results"
      - After each sync cycle, post a single notification on
        `skb.sync` with content `"Synced N repos · M changes"`.
      - `setOnlyAlertOnce(true)`, replace previous on the same
        notification id (`SYNC_RESULT_NID = 9001`).
      - Auto-dismiss after 10s via `setTimeoutAfter(10_000)`.

- [ ] **NS-D.10** Error notification:
      - Channel `skb.errors`, importance HIGH.
      - Title: "Sync failed: <repo-display-name>"
      - Body: human-readable error (e.g. "Authentication failed.
        Tap to fix.")
      - Tap → opens repo settings with the error context surfaced
        and an inline "Re-authenticate" CTA.
      - One notification per repo-error; replace by `notificationId =
        ("err:" + repoId).hashCode()` so a repeating error doesn't
        spam.

- [ ] **NS-D.11** Foreground service notification:
      - Channel `skb.service`, importance MIN.
      - Title varies:
        - while syncing: `"Syncing N repos..."`
        - idle: `"Last sync 14:32"` (HH:mm in user locale)
      - No actions (keeps it minimal).
      - Ongoing flag set; cannot be swiped away (FGS requires this).
      - Settings → Sync → "Disable background sync" toggle = stops
        the FGS, notification disappears, sync becomes manual-only
        with a banner reminding the user.

- [ ] **NS-D.12** POST_NOTIFICATIONS runtime permission:
      - **Decision:** request on first event/task creation, NOT on
        first launch. Less aggressive; the user gets the prompt at
        the moment notifications are about to start mattering.
      - If denied, show an inline banner on the schedule view:
        "notifications disabled — tap to enable". Tap opens
        `Settings.ACTION_APP_NOTIFICATION_SETTINGS` for the app.
      - On Android 12 and below this permission doesn't exist; skip
        the prompt and rely on per-channel state.

- [ ] **NS-D.13** Lockscreen-private mode: if the user has set the
      device to "hide sensitive content on lockscreen", our
      `VISIBILITY_PUBLIC` channels still get redacted bodies. We
      provide a `setPublicVersion(...)` for the events channel that
      shows only "Upcoming event" with no title, so lockscreen-shy
      users get a hint without details.

---

# Part 2 — Sharing + read-only

## Phase NS-E — granting read access

- [ ] **NS-E.1** Settings → Repos → tap a repo → "Share read access"
      button visible only when:
      - the repo is hosted on GitHub or Forgejo (URL host pattern
        match)
      - the user has a credential that (probably) has the
        `repo:admin` or equivalent scope. We don't pre-check the
        scope — we open the provider page and let the provider
        enforce.

- [ ] **NS-E.2** "Share read access" sheet — three options:
      1. **Add a collaborator (read-only)**
         - opens deep link to provider's collab page:
         - GitHub: `https://github.com/<owner>/<repo>/settings/access`
         - Forgejo: `https://<host>/<owner>/<repo>/settings/collaboration`
         - For GitHub, append `?role=read` if supported.
      2. **Add a deploy key (read-only)** — for users who want
         per-recipient access without giving them a collaborator
         seat. Opens the provider's deploy-keys page:
         - GitHub: `https://github.com/<owner>/<repo>/settings/keys/new`
         - Forgejo: `https://<host>/<owner>/<repo>/settings/keys`
         - Above the deep-link button, the sheet shows a copyable
           text field labelled "Recipient's public SSH key" — the
           user pastes whatever the recipient sent them, copies it,
           and pastes again into the provider's web form. We do NOT
           generate the recipient's key for them; that key is the
           recipient's secret.
         - "Read-only" checkbox guidance: helper text reminds the
           user to leave "Allow write access" unchecked.
      3. **Make the repo public**
         - opens repo-visibility settings; this is the lowest-friction
           path for non-sensitive calendars (e.g. team holiday
           calendar).
         - confirmation dialog warns that public = anyone on the
           internet can read.

- [ ] **NS-E.3** No in-app collaborator listing v1. Querying
      collaborators via API requires elevated scopes we don't ask for
      by default; surfacing a stale list would be worse than no list.
      **Decision:** keep it simple — deep-link to the provider and
      let the provider's UI be the source of truth.

- [ ] **NS-E.4** Per-repo "people who have read" list is OUT OF SCOPE
      v1. Document in v2 ideas: optional per-repo "trusted collaborators"
      cache for attribution unification.

---

## Phase NS-F — receiving read access (read-only repo handling)

- [ ] **NS-F.1** Add-repo flow probes write capability via a
      no-op-push-precondition: after clone we run
      `git push --dry-run origin HEAD:refs/heads/__skb_probe_<rand>`.
      If the server rejects with permission-denied, we mark the repo
      `read_only = true` in repo metadata.

- [ ] **NS-F.2** **Decision:** dry-run probe only runs ONCE on
      add-repo. Subsequent push failures (NS-F.3) re-flip the flag.
      Repeating the probe on every sync would be needless network
      load.

- [ ] **NS-F.3** Push-rejection at runtime: if a regular push fails
      with a `TransportException` whose message indicates
      permission-denied (HTTP 403, SSH "remote: ... denied", or git
      protocol error code 22), the sync engine sets `read_only =
      true` and posts an error notification (NS-D.10).

- [ ] **NS-F.4** Read-only repo banner (in the repo's main view):
      - Color: M3 secondary container (informational, not error).
      - Text: "Read-only — your local edits stay on this device."
      - CTA: "Fork to my account" (when provider supports — see
        NS-F.6).

- [ ] **NS-F.5** Local edits on a read-only repo:
      - The user CAN still create/edit events locally. Edits are
        committed locally. The sync engine recognizes `read_only`
        and skips the push step entirely. Pulls still happen.
      - Pull-on-rebase will replay the local commits on top of
        upstream. If a rebase conflicts, the standard conflict
        resolver is invoked.
      - The "ahead" counter in repo settings shows N commits ahead
        with a "your edits are not being shared" subtext.
      - **Decision:** do NOT silently drop local edits on read-only
        repos. The user might be in "preview mode" considering a
        fork, or the access status might be temporarily wrong
        (token rotation). Persisting their edits is the safer choice.

- [ ] **NS-F.6** Fork CTA:
      - GitHub: `POST /repos/<owner>/<repo>/forks` (requires `repo`
        scope). On success, swap the repo URL to the fork's URL,
        flip `read_only = false`, push the queued commits.
      - Forgejo: `POST /api/v1/repos/<owner>/<repo>/forks`.
      - On forks where the user lacks scope (e.g. read-only OAuth
        token), surface a deep link to the provider's "Fork" UI
        and instruct the user to "after forking, return here and
        update the repo URL".

---

## Phase NS-G — author attribution UI

- [ ] **NS-G.1** Attribution chip data structure
      `AuthorChip(personId, displayName, initials, avatar)` resolved
      via a small in-memory `IdentityCache` keyed `(repoId, personId)`.
      Chip is built lazily on first render.

- [ ] **NS-G.2** Avatar resolution order:
      1. `identities/<person-id>.md` frontmatter `avatar` pointing
         at an attachment SHA → resolve to file → `Coil`-load.
      2. `avatar` is an emoji string (e.g. `"🦊"`) → render as
         emoji-in-circle.
      3. No avatar → `InitialsCircle(initials, color)` where color is
         deterministic-hashed from `personId` (stable across runs).

- [ ] **NS-G.3** Chip placement:
      - Schedule view event chip: 24dp circle, top-right corner,
        overlapping the chip border by 2dp.
      - Day/Week timeline event tile: top-right inside padding.
      - Month-view event chip: too small for a circle — render only
        the initials-color as a 4dp left border accent.
      - Task row: trailing-edge 24dp circle before the row's
        overflow menu.
      - Detail sheets (event + task): leading 32dp circle in the
        header row + display name beside it.

- [ ] **NS-G.4** Tap behavior: tap chip → opens
      `AuthorProfileScreen(repoId, personId)` showing:
      - 64dp avatar + display name + email
      - Total entries authored counter (across calendars + todolists
        in this repo)
      - List of recent entries authored by this person (paginated)
      - "Filter all views by this author" button → applies the
        author filter across schedule + tasks for the current
        session (not persisted to disk).

- [ ] **NS-G.5** Author-filter UI in views:
      - Top-bar dropdown "Authors" with multi-select.
      - Default: all authors selected.
      - Persistence: filter state lives in the view-state holder
        (Compose `rememberSaveable`); intentionally NOT in the repo
        — filtering is a per-device, per-session view preference.

- [ ] **NS-G.6** Cross-repo identity:
      - **Decision (matches D.15):** v1 does NOT unify identities
        across repos. Each repo's `identities/` directory is
        independent. The UI shows the author chip *with the repo
        avatar as a small overlay* in the all-repos overlay view,
        so users can disambiguate "Alex (work-repo)" from "Alex
        (personal-repo)" even when initials match.
      - **v2 hook:** an optional cross-repo unification table keyed
        on `public_keys` matching across repos' identity files. If
        two `identities/<id>.md` files in different repos share an
        ed25519 public key, the UI may collapse them. v1 makes no
        attempt; document for future.

- [ ] **NS-G.7** Comments / replies on events: **out of scope v1**.
      Documented hook: collaborative notes go in the Markdown body
      of the event/task file. The body is free-form and renders as
      CommonMark in the detail sheet. v2 may introduce a `comments/`
      sub-folder under events, but the user direction is "events are
      not chat-shaped in v1" — we honor that.

---

# Part 3 — Import / export

## Phase NS-H — iCal (.ics) export

- [ ] **NS-H.1** Export entry-points:
      - Settings → Repos → tap repo → "Export to iCal..." → scope
        sheet:
        - **All calendars in this repo** (default)
        - **One calendar** (picker)
        - **Date range** (default = next 365 days; picker for custom)
        - **Include attachments?** toggle (default off)
      - Per-calendar settings → "Export this calendar to iCal..."
      - From any schedule view → top-bar overflow → "Export visible
        range to iCal"

- [ ] **NS-H.2** iCal renderer (pure Kotlin; no extra dep — JGit
      already pulls a sizable transitive set, we don't add another
      lib for a 600-line spec we already need to control). Output
      conforms to RFC 5545.

- [ ] **NS-H.3** File header:

      ```
      BEGIN:VCALENDAR
      VERSION:2.0
      PRODID:-//eight87//strictlykeptboy 0.1//EN
      CALSCALE:GREGORIAN
      METHOD:PUBLISH
      X-WR-CALNAME:<calendar display name>
      X-WR-CALDESC:<calendar description if any>
      X-WR-TIMEZONE:<device tz id>
      ```

- [ ] **NS-H.4** Per-event rendering for a one-off event:

      ```
      BEGIN:VEVENT
      UID:<event-id>@strictlykeptboy
      DTSTAMP:20260510T120000Z
      DTSTART;TZID=Europe/Berlin:20260512T140000
      DTEND;TZID=Europe/Berlin:20260512T150000
      SUMMARY:Dentist
      DESCRIPTION:Bring the X-rays.\n\nAuthor: alex
      LOCATION:Dental Clinic, Karlsruhe
      CATEGORIES:personal
      ORGANIZER;CN=Alex:mailto:alex@strictlykeptboy.local
      X-SKB-AUTHOR:alex
      X-SKB-EMOJI:🦷
      END:VEVENT
      ```

- [ ] **NS-H.5** All-day events: render as `DTSTART;VALUE=DATE`
      (no `TZID`). DTEND is exclusive (RFC 5545 convention).

- [ ] **NS-H.6** Recurrences:
      - Lift the rule's `RRULE` line verbatim.
      - Render the `dtstart` of the rule.
      - Emit `EXDATE` lines for each `cancel`-kind exception.
      - Emit a separate `VEVENT` with `RECURRENCE-ID` for each
        `override`-kind exception.

      Example:

      ```
      BEGIN:VEVENT
      UID:<rule-id>@strictlykeptboy
      DTSTAMP:20260510T120000Z
      DTSTART;TZID=Europe/Berlin:20260106T070000
      DTEND;TZID=Europe/Berlin:20260106T080000
      SUMMARY:Morning swim
      RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
      EXDATE;TZID=Europe/Berlin:20260311T070000
      END:VEVENT

      BEGIN:VEVENT
      UID:<rule-id>@strictlykeptboy
      RECURRENCE-ID;TZID=Europe/Berlin:20260318T070000
      DTSTAMP:20260510T120000Z
      DTSTART;TZID=Europe/Berlin:20260318T080000
      DTEND;TZID=Europe/Berlin:20260318T090000
      SUMMARY:Morning swim (later today)
      END:VEVENT
      ```

- [ ] **NS-H.7** Time-zones in output: a `VTIMEZONE` block is
      emitted at the top for every TZID referenced. We use a small
      static table for common zones; for exotic ones we shell out to
      `java.time.ZoneRules` to produce DTSTART/RRULE for STANDARD/DAYLIGHT
      transitions for the next ~5 years. **Decision:** truncated
      VTIMEZONE rendering is acceptable; importers fill in current
      rules from their own tz database.

- [ ] **NS-H.8** Line-folding: every output line wrapped at 75
      octets per RFC 5545 §3.1, with continuation `\r\n ` (CRLF +
      single space).

- [ ] **NS-H.9** Escaping: backslash-escape `,`, `;`, `\\`, and
      newline (as `\n`) in TEXT-typed fields (SUMMARY, DESCRIPTION,
      LOCATION).

- [ ] **NS-H.10** File output: SAF picker
      (`ACTION_CREATE_DOCUMENT` with mime `text/calendar` and a
      suggested filename `<repo-display-name>-<yyyy-mm-dd>.ics`).
      Stream the rendering to the SAF Uri OutputStream — never
      buffer the entire .ics in memory (large repos may be MB).

- [ ] **NS-H.11** Attachments embedding (when toggle on):
      - Emit `ATTACH;FMTTYPE=image/jpeg;ENCODING=BASE64;VALUE=BINARY:<base64>`
      - Wrap with the line-folding rules.
      - Warn the user pre-export when total attachment size > 10 MB
        ("the .ics will be N MB").

- [ ] **NS-H.12** Export of tasks as VTODO (bonus toggle):

      ```
      BEGIN:VTODO
      UID:<task-id>@strictlykeptboy
      DTSTAMP:20260510T120000Z
      DUE;VALUE=DATE:20260514
      SUMMARY:Buy milk
      STATUS:NEEDS-ACTION
      PRIORITY:5
      X-SKB-TODOLIST:shopping
      END:VTODO
      ```

      `STATUS = COMPLETED` for `done = true` tasks, plus a
      `COMPLETED:<dtstamp>` line.

---

## Phase NS-I — iCal (.ics) import

- [ ] **NS-I.1** Import entry-point: Settings → Repos → tap repo →
      "Import from iCal..." → SAF picker (`ACTION_OPEN_DOCUMENT`,
      mime `text/calendar`) → target picker:
      - target repo (pre-filled from where the user clicked in)
      - target calendar (existing or new — "Create calendar named
        from X-WR-CALNAME")
      - import mode: **First-time import** vs **Update existing**
        (auto-detected based on whether prior `imported_uid`
        matches exist; user can override).

- [ ] **NS-I.2** Parser: stream-style. We read the file once,
      tokenize at line-fold-unfolded boundaries, and dispatch on
      `BEGIN`/`END` blocks. Skip `VTIMEZONE`, `VJOURNAL`, `VFREEBUSY`,
      `VALARM` for v1 (warn, log, drop).

- [ ] **NS-I.3** Per-`VEVENT` materialization:
      - Compute target ID:
        - if incoming `UID` is shaped like a UUID
          (`[0-9a-f-]{36}`), use it as the SKB ID.
          **Decision:** we don't insist on UUIDv7-shape on import —
          most exporters won't produce one. Any UUID-shaped UID is
          accepted as the id.
        - else mint a fresh UUIDv7, stash original UID in
          `imported_uid = "<original>"`.
      - Determine event vs recurrence:
        - if `RRULE` present → write `recurrences/<id>.md`
        - else → write `events/<yyyy>/<mm>/<id>.md`
      - Map fields:
        - `SUMMARY` → frontmatter `title`
        - `DESCRIPTION` → Markdown body (un-escape iCal escapes)
        - `DTSTART` → `start` (preserve TZID, fall back to device
          tz for floating)
        - `DTEND` → `end` (compute duration if missing)
        - `LOCATION` → `location`
        - `CATEGORIES` → frontmatter `tags`
        - `RRULE` → recurrence rule (verbatim)
        - `EXDATE` → cancel-kind exceptions
        - `RECURRENCE-ID`-bearing VEVENT → override-kind exception
        - `STATUS:CANCELLED` → cancel-kind exception or skip
        - `X-SKB-*` round-trip fields → restored to original places
          (author, emoji, etc.)
        - Unknown `X-*` → preserved in `imported_extras` map under
          frontmatter for round-trip fidelity.

- [ ] **NS-I.4** Author resolution on import: `ORGANIZER` and
      `ATTENDEE` rarely map to local identities. **Decision:** all
      imported events get `author = "<active-identity>"` unless an
      `X-SKB-AUTHOR` round-trip field is present and matches an
      existing local identity id. This is the lowest-surprise rule.

- [ ] **NS-I.5** Attachments: `ATTACH` URLs are saved as a frontmatter
      `links` array. `ATTACH;ENCODING=BASE64` inline blobs are
      decoded → SHA-256 → written into `attachments/<sha-prefix>/<sha>.<ext>`
      → referenced by frontmatter `attachments` array. We don't
      attempt to fetch external `ATTACH` URLs.

- [ ] **NS-I.6** De-duplication on re-import:
      - Compute a key: `imported_uid` if present, else `UID`.
      - For each VEVENT in the new .ics:
        - if no existing entry with that key → CREATE
        - if existing entry exists and its file mtime is older than
          its imported version's mtime → UPDATE
        - if existing entry exists and the user has edited it
          (`last_edited_at > last_imported_at`, both stored in
          frontmatter) → CONFLICT → user resolves (NS-I.7)

- [ ] **NS-I.7** Conflict UI:
      - Pre-import dry-run produces a summary: "12 new, 3 updated, 2
        unchanged, 4 conflicts (you've edited these locally)".
      - Conflict resolution per-entry: `Keep mine` / `Keep imported` /
        `Show diff` (3-way).
      - Bulk action: `Apply same to all conflicts`.

- [ ] **NS-I.8** Single git commit per import: all created/updated
      files batched into one commit, message:
      `"import N entries from <filename>.ics"`.
      Aborting an import in mid-flight rolls back via
      `git reset --hard HEAD` (we keep no local-only edits during
      the import process; the user's earlier work is in HEAD).

- [ ] **NS-I.9** VTODO import: same shape, target the active todolist
      or a chosen one. `DUE` → `due`, `STATUS:COMPLETED` → `done = true`,
      `COMPLETED:<dtstamp>` → `done_at`.

---

## Phase NS-J — CSV export (tasks)

- [ ] **NS-J.1** Entry-point: Settings → Repos → tap repo → "Export
      tasks to CSV..." → scope (all todolists / one todolist) → SAF
      picker.

- [ ] **NS-J.2** Columns (header row):

      ```
      id,title,due,done,priority,todolist,author,body_excerpt,attachment_count
      ```

- [ ] **NS-J.3** Quoting rules: RFC 4180. Wrap any field containing
      `,`, `"`, or newline in `"..."`; double-up internal `"` →
      `""`. Body excerpt is the first 120 chars of the markdown body
      with newlines replaced by spaces.

- [ ] **NS-J.4** Encoding: UTF-8 with BOM (`0xEF 0xBB 0xBF`) so
      Excel opens it correctly without prompting. **Decision:** the
      BOM is ugly to non-Microsoft tools but the user-population for
      CSV-task-export skews spreadsheet-aware; we tolerate the
      compatibility wart.

- [ ] **NS-J.5** Bonus: per-todolist export = a separate CSV file
      per todolist, in a SAF-picked directory.

- [ ] **NS-J.6** No CSV import for v1. **Decision:** CSV → file-store
      mapping is fragile (no recurrence, no exception, no
      attachments). Users wanting bulk task entry should use VTODO
      import (NS-I.9) or hand-edit the repo. v2 idea: opinionated
      CSV importer with a strict template.

---

## Phase NS-K — `tools/` directory + external app interop

### `tools/` scripts

- [ ] **NS-K.1** Layout (lives in the strictlykeptboy app repo, NOT
      in user data repos):

      ```
      tools/
        README.md
        ics-export.sh
        ics-import.sh
        caldav-bridge.py     # stub — out of scope v1
      ```

- [ ] **NS-K.2** `tools/ics-export.sh`:
      - Bash script.
      - Args: `<git-repo-path-or-url> <out.ics> [--calendar <id>] [--from <yyyy-mm-dd>] [--to <yyyy-mm-dd>]`
      - Behavior: clones (or `git pull`s) the repo into a temp dir,
        runs a small Kotlin/JVM program (shipped as `tools/skb-cli.jar`)
        that performs the same render the app does.
      - Use case: cron-driven export feeding Thunderbird's
        "subscribe to remote calendar" or Outlook's
        "internet calendar".

- [ ] **NS-K.3** `tools/ics-import.sh`:
      - Bash script.
      - Args: `<git-repo-path> <in.ics> [--calendar <id>] [--target <calendar-id>]`
      - Behavior: runs `skb-cli.jar import` against the local clone,
        commits the changes locally. Does NOT push (the operator
        decides). Re-import is idempotent (UID-keyed).

- [ ] **NS-K.4** `tools/skb-cli.jar`:
      - Built from the same Kotlin sources as the app — share the
        renderer module via a Gradle subproject `:cli` that depends
        on `:core` (parser, renderer, file-store) but not the
        Android-specific bits. **Decision:** keeping the renderer
        in shared core code is cheaper than maintaining two
        implementations.

- [ ] **NS-K.5** `tools/README.md`:
      - Audience: power users and sysadmins comfortable with cron.
      - Sections: prerequisites (Java 17+, git), examples
        (Thunderbird subscribe, Outlook calendar, Apple Calendar
        subscription), security note ("don't pipe untrusted .ics
        files into ics-import.sh — we don't sandbox"), CalDAV
        future hook.

- [ ] **NS-K.6** `tools/caldav-bridge.py`:
      - Single docstring describing v2 design: a long-running
        Python service polling a Git repo and exposing it as a
        read-only CalDAV server (radicale-style). Out of scope v1.
      - File contains only the docstring + a `raise SystemExit(
        "v1: not implemented")`.

### External calendar app interop on the device

- [ ] **NS-K.7** "Open in external calendar" (event detail overflow):
      - Renders a single-event .ics into the app cache dir
        (`cacheDir/share/<event-id>.ics`).
      - Constructs a `FileProvider` Uri.
      - Builds an `ACTION_VIEW` intent with mime `text/calendar`.
      - `Intent.createChooser` so user picks among installed
        calendar/event apps.
      - The receiving app typically offers "Add to calendar" itself.

- [ ] **NS-K.8** "Add to phone calendar" (event detail action,
      OPT-IN):
      - Uses `CalendarContract.Events.CONTENT_URI` insert.
      - Required permissions:
        `WRITE_CALENDAR` + `READ_CALENDAR` (read for picking the
        target calendar). Both runtime-requested at action time, not
        at app launch.
      - User picks target device-calendar (Google, Exchange, etc.).
      - One-shot copy: the device-calendar entry is independent
        thereafter — strictlykeptboy doesn't keep them in sync.
        **Decision:** explicit one-shot is the only sane v1
        contract; full bidirectional sync is the CalDAV-bridge story
        (v2).

- [ ] **NS-K.9** "Subscribe to this repo's calendar" (instructions,
      not in-app):
      - Settings → Repos → tap repo → "Get subscription URL..."
      - Copies a `webcal://` URL pointing at a static .ics file the
        user must publish themselves (e.g. via GitHub Pages of the
        repo). We provide a one-page guide.
      - **Decision:** in-app support for serving a CalDAV/WebCal
        endpoint is out of scope v1 (would require a background
        server + port forwarding). Power users use `tools/ics-export.sh`
        cronned to a static-host instead.

- [ ] **NS-K.10** Receive .ics files: register an intent filter so
      the app appears in the share sheet for `text/calendar`:

      ```xml
      <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/calendar" />
      </intent-filter>
      ```

      Tapping a .ics in any other app + choosing strictlykeptboy →
      lands in NS-I.1's import flow with the file pre-selected.

---

# Round 2 — extensions (Phases NS-L through NS-Q)

Round 2 of `decisions.md` (D.23–D.40) pulled the v2 deferral pile into
v1. These extension phases elaborate the in-app UX and notification
surfaces for:

- **NS-L** — Bidirectional CalDAV in-app UX (D.25). Cross-link: server
  mechanics live in `sync-engine.md` SE-Q; this doc owns the user-facing
  surface. Maps to `main.md` Phase **Y**.
- **NS-M** — Comments / replies notification surface (D.29 + D.37).
  Cross-link: file layout in `data-model.md` DM-K; composer UI in
  `ui-spec.md` UI-V+. Maps to `main.md` Phase **CC**.
- **NS-N** — CSV import for tasks (D.38). Cross-link: CLI in
  `cli-tooling.md` CLI-*. Maps to `main.md` Phase **KK**.
- **NS-O** — Cross-device snooze sync (D.34, opt-in). Cross-link:
  resolver auto-merge details in `resolver.md`. Maps to `main.md`
  Phase **II**.
- **NS-P** — Multi-branch sharing semantics (D.36). Cross-link: branch
  switching mechanics in `sync-engine.md` SE-Q; UI in `ui-spec.md`.
  Maps to `main.md` Phase **JJ**.
- **NS-Q** — Surgical update of the v2 deferral list.

All five new feature phases supersede the original v2-deferred items
documented in NS-G.7, NS-E.3, NS-J.6, NS-D.8 and parts of NS-K.6.
NS-Q rewrites the deferred-items section accordingly.

---

## Phase NS-L — Bidirectional CalDAV in-app UX (D.25)

Server-side mechanics — discovery protocol, dav4jvm + ical4j wiring,
sync-loop scheduling, ETag/CTag bookkeeping, conflict surfacing back to
the resolver — live in `sync-engine.md` SE-Q. This phase owns the
user-facing UX surface and the notification touchpoints around CalDAV
mirrors, so the user understands what's happening and can intervene
when things break.

A **mirror** is a CalDAV-backed shadow of a single calendar inside a
repo. One repo can carry many mirrors; each mirror has a `mode` of
`pull` (server → repo only), `push` (repo → server only), or `bidi`
(both directions). Mirror metadata lives in the repo as
`calendars/<calendar-id>/mirror.toml` so the configuration syncs across
the user's devices automatically (the credential half stays in
`EncryptedSharedPreferences` keyed by mirror URL hash). Per-mirror
silenced-notifications state lives in app prefs.

### NS-L.1 — Add-mirror flow

- [ ] **NS-L.1.1** Entry-point: Settings → Repos → tap repo → tap a
      calendar row → "Mirrors" subsection → "+ Add CalDAV mirror"
      button. Also reachable from Settings → Repos → tap repo → "+ Add
      CalDAV mirror" which then prompts for the target calendar mid-flow.

- [ ] **NS-L.1.2** Step 1 — Server URL input:
      - Single text field `Server URL` (e.g.
        `https://caldav.example.com/`, `https://nextcloud.example.com/remote.php/dav/`).
      - Helper text under the field: "Paste the CalDAV root or a
        principal URL. We'll auto-discover from here."
      - Quick-pick buttons under the field for common providers:
        `iCloud` (prefills `https://caldav.icloud.com/`), `Fastmail`,
        `Google` (which gates with a "Google CalDAV requires App
        Passwords — open instructions" link), `Nextcloud / Generic`.
      - Credentials section: `Username` + `Password` (or app-password
        for iCloud/Google) fields. **Decision:** v1 supports
        Basic-over-HTTPS only. OAuth-for-CalDAV (Apple, Google) is
        deferred to v1.1 — `decisions.md` D.25 does not require it,
        and the app-password path is the documented escape hatch for
        both providers.
      - "Continue" button → kicks off discovery (NS-L.1.3).

- [ ] **NS-L.1.3** Step 2 — Discovery (driven by SE-Q):
      - In-line progress card: "Discovering calendars on
        `<host>`..." with a circular progress indicator.
      - On success: hand off to step 3 with the discovered list.
      - On HTTP 401 / 403: surface an inline error chip ("Wrong
        username or password — try again") and return to step 1 with
        the password field cleared but username preserved.
      - On HTTP 5xx / network: surface "Server unreachable — try
        again later" with a Retry button.
      - On schema oddities (no `current-user-principal`,
        non-conforming `PROPFIND` response): surface "This server
        doesn't speak standard CalDAV. Paste a calendar URL directly?"
        and offer a manual URL entry path.
      - Discovery timeout: 20 seconds; user can extend once via
        "Still trying — wait longer?" affordance.

- [ ] **NS-L.1.4** Step 3 — Calendar picker:
      - Header: "Found N calendars on `<host>`".
      - List rows: each calendar shows display name, color swatch
        (from CalDAV `calendar-color` property when present), and an
        approximate event count (from `getctag`-derived hint when the
        server provides one; otherwise omitted).
      - User picks ONE calendar per mirror flow. **Decision:** to
        mirror N server calendars, the user runs the wizard N times.
        Multi-select would force a follow-up "pick target for each"
        step that's worse UX than the linear flow.
      - "Back" returns to Step 1 with URL+username preserved.

- [ ] **NS-L.1.5** Step 4 — Mode picker:
      - Three radio cards:
        - **Pull only (read-only mirror)** — recommended for "work
          calendar I want to see but not edit from the app".
        - **Push only (export to server)** — recommended for "publish
          my repo calendar so partner on Thunderbird can see it".
        - **Bidirectional** — recommended for "personal calendar I
          edit from multiple apps".
      - Each card has a short description + a sample-conflict
        explanation: e.g. for `bidi`, "If both sides change the same
        event, you'll resolve the conflict the same way you do for
        Git conflicts."
      - Bidi mode shows an additional toggle "Promote conflicts to
        notifications" (default ON; maps to NS-L.4 error channel).

- [ ] **NS-L.1.6** Step 5 — Target calendar picker (push and bidi only):
      - For push/bidi: "Which repo calendar should this mirror
        sync?" — list of calendars in this repo, plus "Create a new
        calendar named X from this mirror" option (X defaults to the
        CalDAV calendar's display name).
      - For pull-only: the mirror creates its own read-only repo
        calendar named `<server-calendar-name> (mirror)`; the user
        cannot pick "merge into existing" because the read-only
        invariant must be preserved.

- [ ] **NS-L.1.7** Step 6 — Confirm:
      - Summary card:
        - Server: `<host>`
        - Calendar: `<server-calendar-name>`
        - Mode: `<pull|push|bidi>`
        - Local target: `<repo-calendar-name>` (or "new calendar")
        - Sync interval: dropdown (5m / 15m / 30m / 1h / manual),
          default 30m per D.25.
      - "Create mirror" primary button → writes `mirror.toml`, stores
        credentials, schedules first sync immediately, returns to
        the calendar's Mirrors subsection.

- [ ] **NS-L.1.8** `mirror.toml` schema (frozen here for the in-app
      surface; SE-Q owns the implementation):

      ```toml
      id = "0190f9ab-2222-7c01-9100-aaaabbbbcccc"
      server_url = "https://caldav.example.com/calendars/u/work/"
      mode = "bidi"                       # pull | push | bidi
      sync_interval = "30m"
      created_at = "2026-05-10T18:01:00+02:00"
      last_sync_at = "2026-05-10T18:31:14+02:00"
      last_sync_status = "ok"             # ok | error | conflict
      last_sync_error = ""                # human-readable, set when status=error
      promote_conflicts = true            # NS-L.5 toggle
      silenced = false                    # NS-L.3 per-mirror notification toggle
      caldav_ctag = "..."                 # SE-Q owns; UI displays as opaque
      caldav_calendar_color = "#1FB4D6"
      ```

      Note: credentials NEVER live in this file. Only the
      `server_url` and the mirror id. `EncryptedSharedPreferences`
      stores `(mirror_id) → (username, password)`.

### NS-L.2 — Mirror status surface

- [ ] **NS-L.2.1** Per-repo "Mirrors" subsection in Settings → Repos
      → tap repo. Lists every mirror across every calendar in the
      repo, grouped by calendar. Each row shows:
      - Calendar display name (with color swatch).
      - Server hostname.
      - Mode chip (`pull` / `push` / `bidi`).
      - Status chip: green check `synced 18:31`, yellow `syncing now`,
        amber `behind 14m`, red `error — tap` (links to NS-L.4).
      - Overflow menu: `Sync now`, `Silence notifications`,
        `Change mode`, `Re-authenticate`, `Remove mirror`.

- [ ] **NS-L.2.2** Per-calendar Mirrors subsection on a calendar's
      settings page (Settings → Repos → repo → calendar → "Mirrors"):
      - Same row layout as NS-L.2.1, scoped to that calendar.
      - "+ Add CalDAV mirror" button below the list.

- [ ] **NS-L.2.3** Mirror detail screen (tap any mirror row):
      - Header: server URL, mode, target calendar, created date.
      - "Diagnostics" card:
        - Last sync time + status.
        - Last error verbatim (when status=error).
        - Number of events pulled / pushed since creation.
        - CTag drift indicator if server CTag hasn't changed in over
          24h despite our polling (signals a dead mirror).
      - "Actions" card:
        - `Sync now` (force-runs SE-Q sync loop).
        - `Re-authenticate` → re-prompts for password (NS-L.5).
        - `Change mode` → opens the mode picker again; downgrading
          from `bidi` to `pull` flushes pending push commits with a
          confirmation dialog.
        - `Remove mirror` → confirm dialog; deletes `mirror.toml`,
          cancels alarms, clears credentials.

- [ ] **NS-L.2.4** Repo switcher decoration: when a repo has 1+
      mirrors, the repo switcher row shows a small "via CalDAV" dot
      under the repo name. Tap-and-hold reveals "N mirrors" tooltip.

### NS-L.3 — Mirror-sync notifications (sync channel reuse)

- [ ] **NS-L.3.1** **Decision:** CalDAV mirror successful-sync
      notifications piggyback the existing `skb.sync` channel rather
      than spawning a new channel. Rationale: from the user's mental
      model, "git sync" and "CalDAV sync" are both "background data
      reconciliation". Splitting channels would force two near-identical
      Settings rows. Channel name in system Settings remains
      "Sync results"; notification body distinguishes the source.

- [ ] **NS-L.3.2** Sync result notification (only when
      Settings → Notifications → "Show sync results" is enabled —
      same gating as NS-D.9):
      - Title: `Synced N repos · M mirrors`
      - Body: comma-separated short list, e.g.
        `personal · work-caldav · holidays-caldav`
      - On per-mirror sync success in isolation (mirror-only loop
        firing between git syncs): suppress the post entirely; only
        post at the next git-sync rollup so users don't get N
        notifications per polling interval.

- [ ] **NS-L.3.3** Per-mirror silence toggle (`silenced = true` in
      `mirror.toml`):
      - When `silenced = true`, that mirror is **excluded from the
        rollup body and counter**. It still syncs; notifications just
        don't mention it.
      - Toggle exposed in mirror detail screen (NS-L.2.3) and the
        overflow menu (NS-L.2.1).
      - **Decision:** silence is per-mirror, not per-repo, because
        users often have one noisy mirror (a busy work calendar) and
        one quiet mirror (a holidays feed) in the same repo.

- [ ] **NS-L.3.4** Silence-everywhere quick toggle:
      Settings → Notifications → "CalDAV mirror sync notifications"
      master switch (default ON). When OFF, mirror activity is
      excluded from the rollup regardless of per-mirror flags.
      Per-mirror flags re-activate on master-ON.

### NS-L.4 — Mirror-sync error notifications (errors channel)

- [ ] **NS-L.4.1** Errors route through the existing `skb.errors`
      channel (IMPORTANCE_HIGH per NS-A.5). Error notifications
      categorize:
      - **Auth error** (HTTP 401 / 403): "CalDAV: `<server>` rejected
        credentials"
      - **Network error** (timeout, DNS, TLS): "CalDAV: `<server>`
        unreachable"
      - **Protocol error** (parse failure, unexpected schema):
        "CalDAV: `<server>` returned unexpected data"
      - **Conflict** (bidi only, when SE-Q surfaces an unresolvable
        case): "CalDAV: conflict on `<event-title>`"

- [ ] **NS-L.4.2** Title format consistent with NS-D.10:
      `CalDAV sync failed: <mirror display name>`
      where mirror display name = `<server-host>/<calendar-name>`
      truncated at 40 chars.

- [ ] **NS-L.4.3** Tap behavior: opens the mirror detail screen
      (NS-L.2.3) with the diagnostic error visible at the top and
      the appropriate CTA highlighted (Re-authenticate for auth,
      Sync now for network, Resolve for conflict).

- [ ] **NS-L.4.4** De-duplication: one notification per
      `mirror_id`. Subsequent errors for the same mirror update the
      same notification rather than stacking. `notificationId =
      ("mirror-err:" + mirrorId).hashCode()`. Cleared on next
      successful sync.

- [ ] **NS-L.4.5** Conflict notifications only fire when
      `promote_conflicts = true` on that mirror (NS-L.1.5 toggle).
      Otherwise SE-Q's conflict resolver UI is the only surface,
      visible inside the app the next time the user opens the repo.

- [ ] **NS-L.4.6** Action chips on error notifications:
      - For auth errors: `Re-authenticate` (deep-links to NS-L.5).
      - For network errors: `Retry now`.
      - For conflict errors: `Resolve` (opens conflict resolver UI).
      - All errors: `Dismiss` (suppresses for 24h; the dismissal
        decays so a persistent error eventually re-surfaces).

### NS-L.5 — CalDAV credential-expired re-auth flow

- [ ] **NS-L.5.1** Trigger: SE-Q sees HTTP 401 from a mirror that
      previously authenticated. Sets `last_sync_status = "error"`,
      `last_sync_error = "Authentication failed"`, posts NS-L.4.1
      auth-error notification.

- [ ] **NS-L.5.2** Re-auth screen (reached via NS-L.4.6 deep-link or
      mirror detail's `Re-authenticate` action):
      - Single field: `Password` (username pre-filled, read-only).
      - Helper text: "Your saved credentials no longer work.
        Paste a new app-password or password."
      - "Verify and save" primary button → runs SE-Q's
        `verifyCredentials` against the mirror; on success, replaces
        `EncryptedSharedPreferences` entry, kicks off an immediate
        sync, clears the error notification.
      - "Change username too?" expansion link → reveals the
        Username field; useful for users who switched accounts on
        the server side.

- [ ] **NS-L.5.3** **Decision:** v1 does not support proactive
      pre-expiry refresh. Many CalDAV servers don't expose token
      expiry, and the auth path is Basic-only. We react to 401s; we
      don't anticipate them. If a server starts issuing 401s
      repeatedly within a short window (3 failures in 10 minutes), we
      back off the polling interval to 4h to avoid spamming the
      server with bad credentials, and surface a single notification
      until re-auth succeeds.

### NS-L.6 — iCal / CalDAV UID round-trip

- [ ] **NS-L.6.1** When SE-Q pulls a `VEVENT` from CalDAV, the
      VEVENT's `UID` is recorded on the resulting repo file as
      `imported_uid = "<UID>"` in frontmatter (same field as NS-I.3
      uses for `.ics` import — this is intentional reuse, not a new
      key).

- [ ] **NS-L.6.2** When SE-Q pushes a repo event to CalDAV, it uses
      `imported_uid` if present; otherwise it uses the event's SKB
      UUIDv7 as the `UID`. Push round-trips therefore preserve the
      identity the remote server originally minted.

- [ ] **NS-L.6.3** Round-trip drift detection: if a pulled VEVENT's
      `UID` differs from `imported_uid` on a file we last pushed
      under that id (server rewrote the UID — happens with some
      iCloud edge cases), SE-Q logs a warning, keeps both ids in
      frontmatter (`imported_uid` updated to the new one,
      `previous_imported_uid` set), and re-pushes under the new id.
      No user notification — this is a self-healing case.

- [ ] **NS-L.6.4** Cross-export consistency: a calendar that was
      pulled from CalDAV and is then exported via NS-H (iCal
      export) retains the original `UID`. This means a CalDAV-pulled
      calendar can be re-imported to Outlook with stable identities
      and Outlook will recognize updates instead of creating
      duplicates.

- [ ] **NS-L.6.5** **Decision:** `imported_uid` is the single
      authoritative round-trip field across `.ics` import (NS-I),
      CalDAV pull (NS-L), and `.ics` export (NS-H). Using one field
      means a user who imports `.ics`, syncs via CalDAV, and exports
      `.ics` again gets stable UIDs through the whole chain.

---

## Phase NS-M — Comments / replies notification surface (D.29 + D.37)

D.29 establishes the file-per-comment layout under
`events/<yyyy>/<mm>/<event-id>.comments/<comment-id>.md`. D.37 carves
out a dedicated notification channel. This phase wires the trigger
detection, channel registration, content rendering, and per-event
mute surface.

The composer UI itself, the comment thread renderer in the event detail
sheet, and the cross-link to `data-model.md` schema details live in
`ui-spec.md` UI-V+ and `data-model.md` DM-K+. NS-M owns notifications
and the per-event mute prefs key.

### NS-M.1 — `skb.comments` channel registration

- [ ] **NS-M.1.1** Add `skb.comments` to the channel-registration code
      in NS-A.7's `App.onCreate` block:
      - `id = "skb.comments"`
      - `name = "Comments"` (localized)
      - `description = "New comments on events you follow"`
      - `importance = IMPORTANCE_DEFAULT`
      - `setSound(defaultNotificationSound, audioAttributesNotification)`
      - `enableVibration(true)`, default vibration pattern (same as
        events — see NS-A.3 decision)
      - `enableLights(true)`, light color = M3 secondary
      - `lockscreenVisibility = VISIBILITY_PUBLIC`
      - `setShowBadge(true)`
      - `setBypassDnd(false)`
      - `setGroup("skb_main")` (same channel group as the five
        existing channels — keeps system settings tidy)

- [ ] **NS-M.1.2** Update NS-A's channel count comment: the app now
      ships SIX channels (events, tasks, comments, sync, errors,
      service) under the `skb_main` group.

- [ ] **NS-M.1.3** `skb.comments` is in the per-event override
      allow-list for `notification_channel` frontmatter (NS-B.6):
      valid values become `{events, tasks, comments, errors}`. The
      typical comment doesn't carry an override; this is for the rare
      event whose author wants its replies posted as `errors`-level
      urgency (raid-quality reactions).

### NS-M.2 — Comment-arrival detection

- [ ] **NS-M.2.1** Trigger: sync diff. After every successful git
      pull (and after every CalDAV mirror pull when the mirror's mode
      includes pull — see NS-L), the sync engine runs
      `git diff --name-only HEAD@{1} HEAD` (already part of D.2's
      scan model). Any added path matching
      `calendars/*/events/*/*/*.comments/*.md` is a new-comment
      candidate.

- [ ] **NS-M.2.2** Per-comment processing:
      - Parse the file's frontmatter (`id`, `event_id`, `author`,
        `created_at`, `in_reply_to?`) + body.
      - Resolve the parent event: look up
        `calendars/*/events/<yyyy>/<mm>/<event_id>.md` to fetch the
        event title and parent calendar.
      - Check per-event mute (NS-M.5). If muted, skip.
      - Check `skb.comments` channel state (NS-A.10 logical group
        mute, NS-A.13 group toggle). If suppressed, skip post but
        still mark "seen" for badge counting.
      - Otherwise build and post the notification (NS-M.3).

- [ ] **NS-M.2.3** **Decision:** comments authored by the **active
      local identity** never post a self-notification. We compare
      the comment's `author` field against the active identity for
      that repo at fire time. The comparison is by `person-id`, not
      email, so identity switches don't accidentally re-notify on
      old comments.

- [ ] **NS-M.2.4** Coalescing: if a single sync surfaces 3+ comments
      on the same event, post ONE summary notification:
      - Title: `<event-title>` — `N new comments`
      - Body: `<first-author>, <second-author>, ...` truncated.
      - Tap → event detail at the comments section.
      Threshold of 3 keeps the common "two replies during dinner"
      case showing individually; bulk imports get summarized.

- [ ] **NS-M.2.5** Re-sync replay safety: comment-arrival uses the
      `seen_comment_ids` set in `_local/seen-comments.toml` (NOT
      git-tracked; lives in `<filesDir>/seen-comments.toml`). A
      comment id is added on first post-attempt. Re-pulling old
      history (e.g. after a `git clone` of a repo that already has
      old comments) does not flood the user — the file is seeded
      with every existing comment id on first sync of the repo.

### NS-M.3 — Comment notification content

- [ ] **NS-M.3.1** Title: `<author-display-name> commented on
      '<event-title>'`. Truncate event title at 30 chars; truncate
      author display name at 20 chars. Author resolved via NS-G.1
      `IdentityCache`.

- [ ] **NS-M.3.2** Body: `<body-excerpt>` — the first 140 chars of the
      Markdown body with newlines replaced by spaces, ellipsized.
      Use `BigTextStyle` to expand to the full body up to 1500
      chars on expansion.

- [ ] **NS-M.3.3** When the comment is `in_reply_to` an existing
      comment, prepend `↳ ` to the title to signal threading without
      eating title real estate.

- [ ] **NS-M.3.4** Small icon: same `ic_notification.xml` as
      events. Color: same Material You seed as event notifications,
      NOT a fresh color — comments share the calendar's visual
      identity.

- [ ] **NS-M.3.5** `notificationId = ("comment:" +
      commentId).hashCode()`. Coalesced summary (NS-M.2.4) uses
      `("comments-summary:" + eventId + ":" + syncBatchId).hashCode()`.

- [ ] **NS-M.3.6** Author chip in notification body uses the
      initials-form per NS-D.2 decision (compact = initials, expanded
      = full display name).

### NS-M.4 — Notification actions

- [ ] **NS-M.4.1** Action 1 — `Reply`:
      - Tap opens the in-app event detail with the composer focused
        and pre-populated with `> @<original-author>: <body-excerpt>`
        as a quoted prefix (user can delete before sending).
      - Deep link:
        `app://strictlykeptboy/event/<repo-id>/<event-id>?reply_to=<comment-id>`.

- [ ] **NS-M.4.2** Action 2 — `View`:
      - Opens the event detail scrolled to the comments section.
      - Deep link:
        `app://strictlykeptboy/event/<repo-id>/<event-id>#comments`.
      - This is the default tap action when the user just taps the
        notification body (i.e. View == tap).

- [ ] **NS-M.4.3** Action 3 — `Mute event`:
      - Adds this event id to the per-event mute set (NS-M.5).
      - Toast confirmation: "Muted comments on <event-title>".
      - Future comments on this event don't notify until unmuted.

- [ ] **NS-M.4.4** Inline reply via `RemoteInput`:
      - **Decision:** v1 SHIPS inline reply. The complexity is
        moderate and the UX win on lockscreen replies is large.
      - Implementation: `NotificationCompat.Action.Builder` with a
        `RemoteInput.Builder("reply_text").setLabel("Reply...")`.
        Pressing send fires a broadcast to a `ReplyReceiver` that
        writes a new comment file via the same code path as the
        in-app composer (NS-M.6), commits, and triggers a sync push.
      - Constraints: the reply runs as a `goAsync()` broadcast with
        a strict 10-second budget; file write + commit only — push
        happens on the next sync interval rather than synchronously.
      - Failure path: if the file write fails, post a fresh error
        notification on `skb.errors` ("Reply not saved — tap to
        retry") with the draft body in the payload so the user can
        recover.
      - On Wear OS / Auto: the same `RemoteInput` works on Wear; on
        Auto, the comments channel is suppressed entirely (Auto
        notifications are NS-A's events/tasks scope only; comments
        would be too noisy while driving).

- [ ] **NS-M.4.5** Action ordering on the notification follows the
      NS-D.5 OEM-safe three-actions-max rule:
      compact form shows `Reply` (with inline input) + `View` +
      `Mute event`. The expanded form (via `BigTextStyle`) reveals
      no additional actions; Android collapses the action row
      consistently across phone/tablet/foldables.

### NS-M.5 — Per-event mute persistence

- [ ] **NS-M.5.1** Storage: `DataStore<Preferences>` with key
      `muted_event_comments`, value type `Set<String>` of fully-
      qualified `<repo-id>:<event-id>` strings.
      **Decision:** mute is per-device-local, not committed to the
      repo. Two reasons:
      - Mute is an interaction preference (like NS-D.8 snoozes), not
        data about the event.
      - Different family members sharing a repo may want different
        mute sets for the same event.

- [ ] **NS-M.5.2** Surface in the event detail screen: a small
      `🔕 Mute comments` toggle in the comments section header. State
      mirrors the prefs key. Toggling re-renders the toggle but does
      NOT immediately delete any pending notifications already posted.

- [ ] **NS-M.5.3** Bulk unmute: Settings → Notifications → "Muted
      comment threads" → list of `<event-title> (<repo>)` entries
      with `Unmute` action per row + "Unmute all" header action.

- [ ] **NS-M.5.4** Auto-unmute trigger: when an event passes its
      `end` time + 30 days, its mute entry is pruned from the prefs
      set during the nightly `AlarmHorizonExtender` job (NS-C.10).
      Past events accumulate forever otherwise.

- [ ] **NS-M.5.5** Mute does NOT suppress the comment from appearing
      in the event detail's thread view — only the notification is
      suppressed. The user can still browse the comments by opening
      the event manually.

### NS-M.6 — Reply commit conventions

- [ ] **NS-M.6.1** When a comment is created (in-app composer OR
      inline `RemoteInput` reply OR `skb comment add`), a new file is
      written at
      `calendars/<cal>/events/<yyyy>/<mm>/<event-id>.comments/<comment-id>.md`
      with frontmatter:

      ```toml
      +++
      id = "0190fa00-0000-7c01-9100-000000000001"
      event_id = "<event-id>"
      author = "<active-person-id>"
      created_at = "2026-05-10T18:42:00+02:00"
      in_reply_to = "<comment-id>"        # optional
      +++

      <body markdown>
      ```

- [ ] **NS-M.6.2** Auto-commit message format:
      `comment on event "<title>" in <calendar-name>` —
      verb is `comment` for first-level, `reply` for replies (when
      `in_reply_to` is set). Matches D.8's commit-message style.

- [ ] **NS-M.6.3** Comment edits: editing an existing comment
      rewrites the same file. Auto-commit message: `edit comment on
      event "<title>"`. **Decision:** v1 does NOT post an
      "edited" notification — too noisy. Only NEW comment files
      trigger notifications. Edits show up next time the user opens
      the event detail.

- [ ] **NS-M.6.4** Comment deletes: removing a comment removes the
      file. Auto-commit message: `delete comment on event "<title>"`.
      No notification fires; if the deleted comment had a pending
      `notificationId` already posted, we cancel that post by
      `notificationManager.cancel(("comment:" + commentId).hashCode())`
      during sync-diff processing.

---

## Phase NS-N — CSV import for tasks (D.38)

CSV import was deferred in NS-J.6 ("fragile mapping"). D.38 reverses
that decision: v1 ships CSV import for tasks because it's the
realistic on-ramp for users coming from Todoist / Things / Apple
Reminders / spreadsheets. The mitigation for "fragile mapping" is a
proper preview + column-mapping UI that surfaces problems pre-write.

CLI parity lives in `cli-tooling.md` (CLI-* phases own
`skb task import-csv`).

### NS-N.1 — Entry point

- [ ] **NS-N.1.1** Settings → Templates → row labeled
      "Import tasks from CSV" with a CSV icon. Section header
      reads "Imports" (sibling to "Templates").

- [ ] **NS-N.1.2** From any tasks view → top-bar overflow →
      "Import CSV..." (same flow, target todolist pre-selected to
      the currently-viewed list when one is in focus).

- [ ] **NS-N.1.3** Tapping the entry opens the multi-step import
      sheet (NS-N.2 through NS-N.7).

### NS-N.2 — File picker

- [ ] **NS-N.2.1** Step 1 launches SAF
      (`ACTION_OPEN_DOCUMENT`, mime `text/csv` plus
      `text/plain` and `application/vnd.ms-excel` fallback for OEMs
      that mistype CSV).

- [ ] **NS-N.2.2** Once picked, copy the file into the app cache
      dir (`cacheDir/import/<uuid>.csv`) so the user can revoke SAF
      permission without breaking the flow.

- [ ] **NS-N.2.3** Encoding detection: try UTF-8 first; on BOM
      detection (`EF BB BF`) strip it; on UTF-8 decode failure fall
      back to ISO-8859-1 then Windows-1252. Surface a chip at the
      top of the preview screen: "Encoding: UTF-8" with a dropdown
      to override.

- [ ] **NS-N.2.4** Reject files larger than 5 MB outright with a
      clear message ("File too large — please split or trim
      first."). 5 MB of CSV tasks is approx 30,000 entries, well
      beyond any realistic v1 use case.

### NS-N.3 — Column-mapping UI

- [ ] **NS-N.3.1** Step 2 parses the header row + first 5 data rows
      and renders a two-pane mapping screen:
      - Left pane: each CSV column shown as a card with `<column-name>`
        + a 5-row sample preview (truncated to 30 chars per cell).
      - Right pane: target field dropdowns per left-pane card.

- [ ] **NS-N.3.2** Target fields (the right-pane dropdown options):
      - `Title` (required — exactly one column must map here)
      - `Due date`
      - `Done`
      - `Priority`
      - `Todolist`
      - `Body / Notes`
      - `Ignore` (default for unmapped columns)
      - `Custom field → <name>` (writes to `imported_extras.<name>`
        in frontmatter, preserves round-trip)

- [ ] **NS-N.3.3** Auto-detection rules (case-insensitive
      header-name match):
      - `title | task | todo | name | subject` → `Title`
      - `due | due_date | deadline | when | date` → `Due date`
      - `done | completed | status | complete | finished` → `Done`
      - `priority | importance | p | urgency` → `Priority`
      - `list | todolist | category | project | tag` → `Todolist`
      - `body | notes | description | details | comments` → `Body`
      - Everything else → `Ignore`.

- [ ] **NS-N.3.4** Auto-detected mappings are PRESET but
      USER-EDITABLE. Each auto-detection adds a small "Auto" chip
      next to the dropdown so the user knows we guessed; the chip
      disappears when the user changes the value.

- [ ] **NS-N.3.5** Title-required validation: bar the "Continue"
      button until exactly one column is mapped to `Title`. If the
      user maps two columns to `Title`, the second mapping replaces
      the first with a toast ("Only one Title column — moved Title
      to <new>"). **Decision:** silent replace > modal error; users
      iterate on mappings and we don't want to break their flow.

- [ ] **NS-N.3.6** Date-format detection on the `Due date` column:
      sniff the first 5 cells against `yyyy-MM-dd`, `MM/dd/yyyy`,
      `dd.MM.yyyy`, `dd/MM/yyyy`, and ISO-8601 with time. Surface a
      detected-format chip next to the Due-date mapping with a
      dropdown override. Unparseable rows fall back to "no due
      date" in the preview (NS-N.5).

- [ ] **NS-N.3.7** Done-format detection: cells matching
      `true|done|yes|y|1|x|completed|complete` → `done = true`;
      everything else → `done = false`. **Decision:** be liberal
      on inputs; lossy is fine because re-running the import is
      cheap and the preview shows the result.

- [ ] **NS-N.3.8** Priority parsing: accept integer 1..1000 (passes
      through), `low | medium | high` → 250 / 500 / 750. Out-of-range
      → use the target todolist's priority instead, flag the cell
      in preview.

### NS-N.4 — Target todolist picker

- [ ] **NS-N.4.1** Step 3 — pick the target todolist:
      - Dropdown listing every todolist in the current repo.
      - "Create new todolist named ___" option at the bottom.
      - When the CSV has a `Todolist` column mapped: a top-level
        toggle "Use the CSV's `Todolist` column to route each task"
        (default ON when mapped). With ON, the target-todolist
        picker becomes the FALLBACK for rows whose Todolist cell is
        empty or doesn't match any existing list.

- [ ] **NS-N.4.2** New-todolist creation auto-fills priority=500,
      no active windows, no active hours; user can edit afterwards
      in Settings → Todolists.

- [ ] **NS-N.4.3** **Decision:** rows that route to a non-existent
      todolist via the CSV `Todolist` column WITHOUT the toggle ON
      get dropped into the fallback list with a warning chip in the
      preview row: "Todolist 'X' doesn't exist — using <fallback>".
      Users who want strict routing flip the toggle ON and either
      pre-create lists or accept the new-list-creation path.

### NS-N.5 — Preview screen

- [ ] **NS-N.5.1** Step 4 — preview the first N tasks (N=30,
      configurable via "Show more" pagination) as they will be
      written:
      - Each row: title (bold) · due date (or "—") · done-checkbox
        (read-only) · priority chip · todolist tag.
      - Body excerpt below title if mapped (60 chars).
      - Rows with validation issues get a yellow chip:
        `bad date format` / `unknown todolist` / `priority out of
        range` etc. Clicking the chip jumps back to the mapping
        screen with the relevant column highlighted.

- [ ] **NS-N.5.2** Header summary row:
      `N tasks to import · M new · K updated · J ignored
      (validation errors)`.
      Counts derive from NS-N.6 idempotency lookup against existing
      `imported_csv_hash` matches.

- [ ] **NS-N.5.3** Per-row override: tap any preview row to open a
      mini-editor for that row — change title / due / done /
      priority / todolist / body before commit. Edits are
      session-only (not written back to the CSV file). Useful for
      one-off fixes without re-mapping.

- [ ] **NS-N.5.4** Cancel button returns to mapping. "Import N
      tasks" primary button commits.

### NS-N.6 — Idempotency on re-import

- [ ] **NS-N.6.1** Compute hash per row:
      `imported_csv_hash = SHA-256(title + "|" +
      due_iso + "|" + body)` (UTF-8 bytes, base64-encoded, first
      16 chars). Stored in frontmatter of the resulting task file.

- [ ] **NS-N.6.2** On re-import:
      - For each row, look up existing tasks in the target todolist
        (and the fallback list) with matching `imported_csv_hash`.
      - If found AND the task hasn't been edited locally
        (`last_edited_at == last_imported_at`): UPDATE in place,
        bumping `last_imported_at`.
      - If found AND locally edited: CONFLICT. Surface in preview
        with a `local edits` chip and a per-row choice
        `Keep mine` / `Overwrite with CSV`. Bulk action available
        in the preview header.
      - If not found: CREATE.

- [ ] **NS-N.6.3** Deletes from the source CSV are NOT propagated
      to the repo. **Decision:** v1 CSV import is additive +
      updating only. Deleting a row from your spreadsheet to delete
      a task is too risky as the default behavior (the user
      probably just wants to focus the spreadsheet). Power users
      can `skb task delete` explicitly.

- [ ] **NS-N.6.4** Hash collision handling: if two rows in the same
      CSV produce the same hash, surface a validation warning
      ("duplicate rows: 47 and 89 are identical") and import only
      the first. Re-imports of an unchanged CSV are no-ops by
      design.

### NS-N.7 — Commit + post-import surface

- [ ] **NS-N.7.1** Single git commit per import:
      `import N tasks from <filename>.csv`.
      All new + updated files batched. If conflicts are unresolved
      in preview, those rows are skipped from the commit and a
      post-import banner offers "Resolve conflicts now" leading
      back to the preview screen.

- [ ] **NS-N.7.2** Post-import success toast: "Imported N tasks
      into <todolist>" with an `Undo` action lasting 10 seconds.
      Undo reverts the import commit
      (`git reset --hard HEAD~1`) — safe because we just created it.

- [ ] **NS-N.7.3** Sync push: kicked off automatically after a
      successful import, same path as any other commit. If the
      repo is read-only (NS-F), the commit stays local and the
      standard read-only banner reminds the user.

### NS-N.8 — CLI parity hook

- [ ] **NS-N.8.1** `skb task import-csv <file> --list <todolist>
      [--mapping <yaml>] [--dry-run]`:
      - When `--mapping` is omitted, the CLI uses the same
        auto-detection rules as NS-N.3.3.
      - `--mapping <yaml>` accepts a saved mapping file (the GUI's
        "Save mapping" affordance — see NS-N.8.2 — produces this).
      - `--dry-run` prints the preview-equivalent to stdout (JSON
        when `--json`) without writing.
      - Full spec lives in `cli-tooling.md` CLI-*; this entry just
        anchors the cross-link.

- [ ] **NS-N.8.2** "Save mapping" button in the GUI's mapping screen
      writes the current mapping config to
      `<repo>/.strictlykeptboy/csv-mappings/<name>.yaml` so re-imports
      from the same source schema skip the mapping step. The mapping
      file is git-tracked; teams can share import recipes.

---

## Phase NS-O — Cross-device snooze sync (opt-in, D.34)

NS-D.8 originally pinned snoozes to a `DataStore<Preferences>` map and
deferred cross-device sync. D.34 reverses that with an **opt-in toggle**
— default OFF preserves NS-D.8's behavior; ON elevates snoozes into the
git-tracked repo state at `_local/snoozes.toml` with a deterministic
auto-merge rule that never surfaces to the user.

### NS-O.1 — Setting toggle

- [ ] **NS-O.1.1** Settings → Notifications → new row
      "Sync snoozes across devices" with a toggle (default OFF) and
      helper text: "Snooze decisions you make on this device will be
      shared with your other devices via the calendar repo."

- [ ] **NS-O.1.2** Per-repo override: the master toggle is global,
      but Settings → Repos → tap repo → "Notifications" subsection
      exposes a per-repo override `Sync snoozes for this repo` —
      tri-state (`use global` / `force on` / `force off`).
      **Decision:** per-repo override is necessary because shared
      repos (read-only mirror of a partner's calendar) shouldn't be
      polluted with the user's snoozes. Default per-repo override is
      `use global`.

- [ ] **NS-O.1.3** Read-only repos (NS-F) have the per-repo override
      forced to `off` and disabled in the UI with helper text:
      "Snooze sync is unavailable on read-only repos."

- [ ] **NS-O.1.4** Flip-on behavior: when the user turns the toggle
      ON for the first time, the app commits the current device's
      pending snoozes (from `DataStore<Preferences>`) into
      `_local/snoozes.toml` immediately and runs a sync push. The
      `DataStore` keeps a copy as a fast-path cache — the file
      is the source of truth.

- [ ] **NS-O.1.5** Flip-off behavior: when the user turns the toggle
      OFF, snoozes already in `_local/snoozes.toml` STAY there
      (other devices still need them). Future snoozes on this device
      are recorded only in `DataStore` until re-enabled. The
      file becomes append-only from this device's perspective until
      the user flips back ON.

### NS-O.2 — `_local/snoozes.toml` schema

- [ ] **NS-O.2.1** Schema:

      ```toml
      schema_version = 1

      [[snooze]]
      alarm_id = "<event-id>:<lead-time-iso>"
      until = "2026-05-10T18:00:00+02:00"
      device_id = "<device-uuid>"
      created_at = "2026-05-10T17:55:00+02:00"
      ```

      One `[[snooze]]` entry per (alarm_id) per-device snooze action.
      Snoozing the same alarm twice on the same device replaces the
      previous entry with the same `(alarm_id, device_id)` tuple
      (latest `until` wins locally before commit).

- [ ] **NS-O.2.2** `alarm_id` matches NS-C.4's schedule key:
      `<repo-id>:<entity-id>:<lead-seconds>` — but stored without the
      repo-id prefix because the file lives inside the repo (the
      repo-id is the path context). Form:
      `<entity-id>:<lead-seconds>`. We use seconds rather than the
      grammar form (NS-B.3) because a snooze applies to a single
      scheduled alarm; the lead-time is its identifier within the
      event.

- [ ] **NS-O.2.3** `device_id` minted on first launch (per NS-O.4)
      and stored in `EncryptedSharedPreferences`. UUIDv4
      (cryptographically random — we don't need sortability for
      devices). Re-installing the app on the same physical device
      mints a new id; this is acceptable because the auto-merge rule
      (NS-O.3) handles it without surprise.

- [ ] **NS-O.2.4** `created_at` is the local wall-clock time of the
      snooze action in the device's tz, stored with offset. Used
      only for human diagnostics in NS-O.5; merge logic uses `until`
      exclusively.

- [ ] **NS-O.2.5** `until` is the absolute UTC instant the snooze
      expires, stored with offset for human-readability. Comparisons
      are by instant.

### NS-O.3 — Auto-merge rule (resolver detail)

- [ ] **NS-O.3.1** **Decision:** `_local/snoozes.toml` is special-
      cased in the conflict resolver (lives in `resolver.md`; this
      doc owns the spec). The resolver detects a 3-way conflict on
      this file by path-match and applies the snooze auto-merge
      instead of surfacing the standard conflict UI.

- [ ] **NS-O.3.2** Merge algorithm:
      - Parse all three sides (base, ours, theirs).
      - Compute the union of all `[[snooze]]` entries across all
        three sides.
      - Group entries by `alarm_id` (the device_id is NOT part of
        the grouping key — see rationale below).
      - For each group, pick the entry with the LATEST `until`.
      - Tie-breaker on identical `until`: pick the entry with the
        latest `created_at`. Further tie-breaker: lexicographic
        device_id.
      - Write the resulting set of entries back to
        `_local/snoozes.toml`, sorted by `alarm_id` then `device_id`
        for deterministic diffs.

- [ ] **NS-O.3.3** Rationale for collapsing on `alarm_id` only
      (ignoring `device_id`): a snooze is fundamentally an
      instruction "don't ring this alarm before <until>". If Device A
      says "snooze until 18:00" and Device B says "snooze until
      20:00", the user's intent is best honored by "snooze until
      20:00" everywhere. Keeping per-device entries would let an old
      device's earlier snooze incorrectly re-arm the alarm on a
      newer device.

- [ ] **NS-O.3.4** Pruning during merge: entries whose `until` is
      already in the past at merge time are dropped from the output.
      This keeps the file from accumulating forever.

- [ ] **NS-O.3.5** Idempotency: re-running the merge on the merged
      output is a no-op. The sort order + drop-by-staleness rules
      guarantee stability.

- [ ] **NS-O.3.6** Auto-commit message: `merge snoozes`. No body.
      The commit shows up in the user's git log as ordinary
      housekeeping; we don't want to mention conflict avoidance
      because there was no conflict from the user's perspective.

### NS-O.4 — Device-id minting

- [ ] **NS-O.4.1** On first app launch, mint a UUIDv4 via
      `UUID.randomUUID()`. Store in `EncryptedSharedPreferences` with
      key `skb.device_id`. **Decision:** stored encrypted because
      while the device id isn't sensitive on its own, leaking it
      across apps would let trackers correlate users; keeping it in
      the encrypted store is cheap insurance.

- [ ] **NS-O.4.2** Device id is never changed once minted, even
      across major version upgrades. App data wipe is the only
      reset.

- [ ] **NS-O.4.3** Device id is NEVER displayed to the user. NS-O.5
      diagnostics show it for support cases (with a "copy" button),
      but no everyday UI surfaces it.

### NS-O.5 — Cleanup + diagnostics

- [ ] **NS-O.5.1** Pruning trigger: the next sync after `until`
      passes for any snooze entry, the entry is dropped from
      `_local/snoozes.toml`. Implemented as a small read-rewrite-
      commit step at the end of the sync loop. Auto-commit message:
      `prune expired snoozes`.

- [ ] **NS-O.5.2** Coalesce-cleanup: if a sync would result in N
      consecutive "prune expired snoozes" commits with no other
      activity, coalesce into a single commit. The auto-commit
      message format remains identical.

- [ ] **NS-O.5.3** Diagnostics screen: Settings → Notifications →
      "Snooze sync" → "Show diagnostics" reveals:
      - This device's `device_id` (with copy button).
      - Per-repo snoozes count (active / expired).
      - Last merge commit time per repo.
      - "Clear local snooze cache" button (rebuilds `DataStore`
        cache from the repo file — recovery path if the cache drifts).

- [ ] **NS-O.5.4** Snoozes never appear in the conflict UI. The
      resolver auto-merge (NS-O.3) silently handles every case. If
      the resolver ever fails (TOML parse error, file corruption),
      it falls back to "trust this device's copy, log a warning" and
      surfaces a single low-importance notification on `skb.errors`
      with a "Reset snooze sync" CTA that rewrites the file from
      this device's `DataStore`.

### NS-O.6 — Interaction with NS-D.8 + NS-C.12

- [ ] **NS-O.6.1** NS-D.8's fast-path `DataStore` snooze map stays
      as a per-device cache. When sync is OFF, it's the only
      storage. When sync is ON, it's a write-through cache backed
      by `_local/snoozes.toml`.

- [ ] **NS-O.6.2** NS-C.12's fire-time mute check additionally
      consults the snooze map. When sync is ON, the map is always
      hydrated from the repo file on sync completion (NS-O.6.3).

- [ ] **NS-O.6.3** Sync-pull hook: every successful pull on a repo
      with sync-snoozes ON reloads `_local/snoozes.toml` into
      `DataStore`. The map is keyed by `alarm_id` (without
      `device_id`) — collapsed per the merge rule. NS-C.11's fire
      path consults this collapsed view at fire time.

- [ ] **NS-O.6.4** Snooze action from notification (NS-D.5 /
      NS-M.4):
      - When sync is OFF: write to `DataStore` only.
      - When sync is ON: write to `DataStore` + append to
        `_local/snoozes.toml` + commit + queue push. Commit message:
        `snooze "<event-title>" until <HH:mm>`.

---

## Phase NS-P — Multi-branch sharing semantics (D.36)

D.36 introduces per-repo branches as a first-class concept (work-feature
branches, partner-collab PRs, AI-agent feature branches). This phase
elaborates the sharing-related surfaces: deep-link parameters that
respect the current branch, author-chip "on branch X" indicators, and
PR-status badging when branch frontmatter declares an open PR.

Branch creation, switching, and the branch picker UI itself live in
`sync-engine.md` SE-Q + `ui-spec.md` UI-V+. NS-P owns the share-link,
attribution, and PR-status surfaces.

### NS-P.1 — Branch-aware "Share read access" deep-link

- [ ] **NS-P.1.1** NS-E.2's "Share read access" sheet adds a
      "Currently viewing branch: `<branch>`" line above the three
      options when the active branch is not `main`/`master`. Text
      includes a "Switch to main first?" link that opens the branch
      picker.

- [ ] **NS-P.1.2** The "Add a collaborator (read-only)" deep-link
      remains the standard provider collaborator screen — provider
      access is per-repo, not per-branch, so the link is unchanged.
      Helper text below the button: "Collaborators can read all
      branches by default."

- [ ] **NS-P.1.3** New affordance: "Share a link to this branch" —
      copies a `tree`-style URL to the system clipboard:
      - GitHub: `https://github.com/<owner>/<repo>/tree/<branch>`
      - Forgejo: `https://<host>/<owner>/<repo>/src/branch/<branch>`
      - Toast confirmation: "Branch link copied".
      - The link is read-only and respects the provider's existing
        visibility settings; private repos still require auth.

- [ ] **NS-P.1.4** "Share a link to this branch" is hidden when the
      active branch is `main`/`master` (deep-link to root is the
      same as the repo-share existing flow).

- [ ] **NS-P.1.5** Quick-Share intent: the standard Android share
      intent (`ACTION_SEND` from the repo overflow menu) emits a
      `text/plain` body with the branch-aware URL when the active
      branch isn't main, or the repo-root URL when it is.

### NS-P.2 — Author attribution: "on branch X" indicator

- [ ] **NS-P.2.1** When the unified-overlay view (D.9 "all repos
      overlay") is rendering an event that lives on a non-main
      branch in its source repo, the author chip (NS-G.3) gets a
      small `+ branch ribbon` decoration: a 6dp triangular cut at
      the top-left of the chip in the branch's accent color.

- [ ] **NS-P.2.2** Branch accent color resolution: per-branch color
      lives in `.strictlykeptboy/branch-state.toml` (frozen here for
      the UI surface; SE-Q owns the read-write logic):

      ```toml
      [branches.main]
      color = ""                          # default — no ribbon

      [branches."claude/plan-2026-q3"]
      color = "#9370DB"                   # purple — AI-agent default
      pr_url = "https://github.com/u/r/pull/47"
      pr_state = "open"                   # open | closed | merged | draft

      [branches."alice/timebox-redesign"]
      color = "#FF6F61"
      ```

- [ ] **NS-P.2.3** Default branch colors: when a branch has no
      explicit color in `branch-state.toml`, assign deterministically
      from a 12-color palette via
      `hashCode(branch_name) % 12`. Stable across devices because
      branch names + hash function are stable.

- [ ] **NS-P.2.4** Event detail sheet: when viewing an event from a
      non-main branch, the header row shows a chip below the title:
      `🌿 on branch <branch>` in the branch's accent color. Tap
      opens the branch overview screen (UI-V+ owns the screen).

- [ ] **NS-P.2.5** Schedule/week/month views: events from non-main
      branches render with a subtle 1.5dp dashed left border in the
      branch's accent color in addition to their regular calendar
      coloring. Distinguishes feature-branch events at a glance
      without overwhelming the day-view density.

- [ ] **NS-P.2.6** Author chip + branch ribbon combination: when
      the author chip already has a repo-avatar overlay (per
      NS-G.6's all-repos overlay decoration), the branch ribbon
      goes on the OPPOSITE corner. Order of overlays: repo avatar
      top-right, branch ribbon top-left.

- [ ] **NS-P.2.7** **Decision:** the branch indicator on chips
      does NOT carry the branch name itself — too cramped. The chip
      just signals "this is from a non-main branch"; the name is
      visible via tap or hover.

### NS-P.3 — PR-status badge

- [ ] **NS-P.3.1** Source of truth: the `pr_url` + `pr_state` fields
      in `.strictlykeptboy/branch-state.toml` per NS-P.2.2 schema.
      These fields are written by:
      - The branch creator manually (paste PR URL after creating PR
        on the provider).
      - `skb branch link-pr <url>` CLI (cli-tooling.md owns).
      - A future provider-API enrichment (deferred to v1.1).

- [ ] **NS-P.3.2** Repo switcher badge: when a non-main branch is
      active AND `pr_state == "open"`, the repo switcher shows
      "PR #N open" under the branch name (where N is parsed from
      the `pr_url`):
      - GitHub URL pattern: `.../pull/<N>`
      - Forgejo URL pattern: `.../pulls/<N>`
      - Pattern not matched: show "PR open" without the number.

- [ ] **NS-P.3.3** PR-state visual style:
      - `open`: M3 secondary container background, "PR #N open"
        label.
      - `draft`: tertiary container, "PR #N draft".
      - `merged`: surface-variant, "PR #N merged" (still useful
        info briefly after merge before user switches back to main).
      - `closed`: outline-variant with strikethrough, "PR #N
        closed" — the branch lingered; user should consider
        switching away.

- [ ] **NS-P.3.4** Tap PR badge → opens the `pr_url` in the system
      browser via `ACTION_VIEW`. No in-app PR review surface in v1.

- [ ] **NS-P.3.5** PR-state freshness: there's no automatic poll of
      the provider in v1. The badge reflects whatever
      `branch-state.toml` says. Each branch switch shows a "Last
      updated: <time>" line under the badge in the branch overview
      screen.

- [ ] **NS-P.3.6** **Decision:** v1 ships with state-update via
      manual edit or CLI. Automated provider polling would require
      either elevated OAuth scopes (D.39's deferral applies) or a
      webhook receiver (server territory, out of scope). Manual
      `skb branch link-pr` is the v1 contract; v1.1 may add a
      polling background job once scope-elevation is addressed.

- [ ] **NS-P.3.7** Sharing a PR'd branch: when the active branch
      has `pr_state = "open"`, NS-P.1.3's "Share a link to this
      branch" gains a secondary action: "Share PR link instead?" —
      copying the `pr_url` instead of the branch tree URL.

### NS-P.4 — Branch and read-only-repo interaction

- [ ] **NS-P.4.1** Read-only repos (NS-F): branch operations are
      read-only too. The branch picker lists upstream branches but
      "Create branch" is disabled with helper text "This repo is
      read-only — fork it to create branches."

- [ ] **NS-P.4.2** Read-only repo + non-main branch view: the
      read-only banner (NS-F.4) gets an addendum:
      `Read-only · viewing branch <name>`. The fork CTA (NS-F.6)
      preserves the user's currently-viewed branch when pointing
      them at the provider's fork UI; the after-fork URL update
      flow asks "switch to <branch> in your fork?" if the user's
      fork has that branch.

### NS-P.5 — Branch-aware author-filter

- [ ] **NS-P.5.1** NS-G.5's author-filter dropdown adds a secondary
      filter row "Branches" with multi-select. Default: only the
      current branch selected; user can include events from other
      local branches (those the device has fetched) in the view.

- [ ] **NS-P.5.2** **Decision:** branches-filter is OFF by default
      for everyday users — most users never use branches. When the
      repo has only `main`, the dropdown row is hidden entirely.
      When the repo has 2+ branches, the row appears with the
      current branch pre-selected.

- [ ] **NS-P.5.3** Cross-branch query is O(branches × events).
      Cache invalidation matches D.2's HEAD-SHA keying per branch.
      Performance budget: cross-branch month-view render < 400ms
      on Pixel 6a for repos with up to 5 branches × 200 events.

---

## Phase NS-Q — Updated v2 deferrals

This phase surgically updates the deferred-items section to reflect
Round 2 decisions. Phases NS-L through NS-P pull most of the v1.5/v2
pile into v1; what remains genuinely deferred or out-of-scope is
re-classified below. The "Tradeoffs resolved inline" section above is
preserved as-is — those tradeoffs were resolved in their time and
remain accurate.

### NS-Q.1 — Update tradeoffs-resolved inline references

- [ ] **NS-Q.1.1** No textual change required to the "Tradeoffs
      resolved inline" list (NS-A.14 through NS-K.9). Those rows
      describe decisions made for the original v1 scope; Round 2
      extends the scope but does not retract any prior decision.

- [ ] **NS-Q.1.2** Add an explanatory header note above the
      "Deferred to v2 with rationale" section linking to this
      phase: "*Round 2 of `decisions.md` (D.23–D.40) moved much of
      this list into v1. See NS-L through NS-P for the moved items;
      what remains here is still deferred.*"

### NS-Q.2 — Surgical updates to "Deferred to v2 with rationale"

- [ ] **NS-Q.2.1** Replace existing entries with the rewritten
      classification documented in NS-Q.3 below.

### NS-Q.3 — Reclassification reference

The deferred-items section is rewritten to three tiers:

- **Moved to v1** — items originally deferred but now in v1 scope
  via Round 2. These rows note the phase(s) implementing them.
- **Still deferred to v1.1** — items intentionally held back, with
  a v1.1 path documented.
- **Still out of scope** — items rejected as wrong-fit (not just
  postponed).

#### Moved to v1

| Original deferral | Now implemented in | Notes |
|---|---|---|
| Bidirectional CalDAV sync | NS-L (UX) + `sync-engine.md` SE-Q (mechanics) | Was scoped as `tools/`-only; now full in-app via dav4jvm + ical4j. |
| Comments / replies on events | NS-M (notifications) + `data-model.md` DM-K (schema) + `ui-spec.md` UI-V+ (composer) | User direction reversed from "events are not chat-shaped in v1" to file-per-comment in v1. |
| CSV import for tasks | NS-N + `cli-tooling.md` CLI-* | Was deferred for "fragile mapping"; mitigated by preview + column-mapping UI. |
| Cross-device snooze sync | NS-O | Was "local-by-design"; now opt-in with auto-merge in `_local/snoozes.toml`. |
| Cross-repo identity unification | D.23 signed commits + committer email | Signed commits + email-based committer make this a non-issue at the git layer; the per-repo `identities/` dirs stay as the canonical display source. |

#### Still deferred to v1.1

| Item | Why deferred | v1.1 path |
|---|---|---|
| In-app collaborator listing | Requires elevated OAuth scopes (`read:org` for GitHub) — trust-footprint expansion | Re-evaluate after v1 ships and user-trust posture is clearer (D.39). Deep-link to provider is adequate v1. |
| Per-recipient deploy-key generation in the app | Wrong trust model — recipient should hold their own private key | Guidance + paste field for the recipient's pubkey is the v1 contract (NS-E.2). No v1.1 promotion planned. |
| Provider-API PR-state polling | Either scope-elevation or webhook-receiver complexity | Manual `skb branch link-pr` is the v1 contract (NS-P.3.6). v1.1 may add polling once scope posture is resolved. |
| OAuth-for-CalDAV (Apple, Google) | Provider-specific OAuth dances; app-password path covers v1 | NS-L.1.2 documents the deferral. v1.1 may add provider-specific OAuth if app-password support degrades. |
| Branch-protection PR creation in-app | Provider-side workflow; deep-link is enough | D.40 documents; NS-P.1.3 share-branch-link is the v1 contract. |
| Notification group importance RAISE above channel ceiling | Would require dynamic channel proliferation | NS-A.14 lower-only rule stands. |
| Cross-repo identity unification (public-key match auto-merge) | D.23 covers the basics; auto-merge across repos is the v2 enhancement | NS-G.6 v2 hook stands as future work, NOT blocking. |

#### Still out of scope

| Item | Why rejected |
|---|---|
| Self-served webcal / CalDAV from the app | Server territory — Android client can't expose a sustainable endpoint without port-forward/discovery. Power users cron `tools/ics-export.sh` instead. NS-K.9. |
| Alpha-blend overlap | Colorblind + screen-reader regression. Striped overlay model in `resolver.md` stays. D.40. |
| Multi-finger calendar gestures | Undiscoverable for most users. D.40. |
| Semantic TOML auto-merge | One-file-per-entity invariant + manual conflict UI is sufficient. Adding semantic merge would obscure the explicit data model. D.40. |
| Web app / desktop app | Android-only in v1. The `skb` CLI (D.24) covers the cross-platform power-user surface. |
| End-to-end encrypted repo contents | Repo can be private at the provider level; that's the v1 data-at-rest story. D.40. |
| QR code for SSH public-key export | Marginal value over clipboard/share; zxing dep cost. D.40. |

### NS-Q.4 — Migration of existing readers

- [ ] **NS-Q.4.1** The "Deferred to v2 with rationale" section in
      the existing document is replaced wholesale by NS-Q.3's
      three-tier classification. This is the ONLY surgical write
      outside Round 2's appended content.

- [ ] **NS-Q.4.2** Anyone who linked to anchors in the old
      "Deferred to v2 with rationale" section (e.g.
      `#deferred-to-v2-with-rationale-comments-replies-on-events`)
      should update their links to point to the relevant Round 2
      phase (NS-M for comments, etc.). Anchors themselves are
      preserved as best as a markdown reflow allows; new content
      uses `## NS-Q.3 — ...` style anchors.

---

# Tradeoffs resolved inline

These were unforeseen at the brief stage; all resolved here without
punting back to the user.

- **NS-A: number of channels.** Brief specified 4 + service.
  Resolved as **5** (events, tasks, sync, errors, service).
  Foreground-service notification gets its own importance-MIN channel
  to keep it cleanly separable in system Settings. Fewer channels
  would force the FGS notification onto the `sync` channel, where
  raising the FGS notification's persistence makes the rest of the
  channel feel sticky.

- **NS-A.14: group importance can only LOWER.** Raising past channel
  ceiling would require a fresh channel — channel proliferation is
  worse than the limitation. Documented in the group editor.

- **NS-B.3: lead-time grammar.** Disallow compound `1h30m`. Trivial
  parser, lossless round-trip, trivially-explainable to users.
  Compound "90m" works for users who actually want 90 minutes.

- **NS-C.1: exact-alarm permission strategy.** Use both
  `USE_EXACT_ALARM` (Play-policy-allowed for calendar/reminder
  apps; Android 14+) and `SCHEDULE_EXACT_ALARM` (older), with a
  runtime fallback path that surfaces a Settings deep-link if the
  system denies exact-alarm.

- **NS-C.10: rolling 30-day alarm horizon.** Bounds memory/system
  alarms; nightly slider keeps it warm. Users with absurdly-frequent
  RRULEs get a warning — system alarm caps would otherwise hit
  silently.

- **NS-D.5: only two snooze actions.** Three is the OEM-safe max in
  practice. The "1 day" snooze lives in the in-app event detail
  context menu instead of cluttering the notification.

- **NS-D.8: snooze state lives ON-DEVICE only, never in Git.**
  Snoozes are device-local interactions; sharing them via Git would
  produce nonsense merge cases.

- **NS-D.12: POST_NOTIFICATIONS prompt timing.** First event create,
  not first launch. Less aggressive; the user grants when it actually
  matters.

- **NS-E.3: no in-app collaborator listing.** Querying via API
  requires elevated scopes we don't ask for; surfacing a stale list
  is worse than no list. Deep-link to provider instead.

- **NS-F.5: keep local edits on read-only repos.** Don't silently
  drop user work; the access state may be transient (token rotation),
  and the "fork to make editable" path needs the edits intact.

- **NS-G.6: no cross-repo identity unification v1.** Honors D.15
  literally; v2 hook documented (public-key match across repos).

- **NS-H.7: VTIMEZONE rendering truncated to ~5 years of transitions.**
  Importers fill in current rules from their own tz database.

- **NS-I.4: imported events get the active local identity as
  author.** Lowest-surprise default; `X-SKB-AUTHOR` round-trip
  preserves the original on re-export of our own files.

- **NS-J.4: CSV BOM included.** The CSV export user-population skews
  spreadsheet-aware; Excel compatibility wins over Unix-tool
  cleanliness.

- **NS-J.6: no CSV import v1.** Fragile mapping (no recurrence, no
  exceptions, no attachments). VTODO import is the supported path.

- **NS-K.4: shared `:cli` Gradle subproject.** Keeps renderer in one
  place; `tools/skb-cli.jar` reuses the app's parser/renderer.

- **NS-K.8: Add-to-phone-calendar is one-shot.** Bidirectional sync
  is CalDAV-bridge territory (v2 stub).

- **NS-K.9: in-app webcal/CalDAV server out of scope v1.** Power
  users cron `tools/ics-export.sh` to a static host instead.

---

# Deferred to v1.1 / out of scope

*Round 2 of `decisions.md` (D.23–D.40) moved much of the original v2
deferral pile into v1. See **NS-L** (bidi CalDAV), **NS-M** (comments),
**NS-N** (CSV import), **NS-O** (cross-device snooze sync), and **NS-P**
(multi-branch sharing) for the moved items, and **NS-Q** for the
classification rationale. What remains below is genuinely held back
for v1.1 or rejected as wrong-fit.*

## Moved to v1 (no longer deferred)

| Original deferral | Now implemented in |
|---|---|
| Bidirectional CalDAV sync | NS-L + `sync-engine.md` SE-Q |
| Comments / replies on events | NS-M + `data-model.md` DM-K + `ui-spec.md` UI-V+ |
| CSV import for tasks | NS-N + `cli-tooling.md` CLI-* |
| Cross-device snooze sync | NS-O |
| Cross-repo identity unification (basic) | D.23 signed commits + committer email |
| CalDAV server-side write-back | NS-L (bidi mode) |

## Still deferred to v1.1

- **In-app collaborator listing.** Requires elevated provider OAuth
  scopes we don't ask for. Deep-link approach is sufficient v1.
  Re-evaluate after v1 ships. D.39 + NS-E.3.

- **Per-recipient deploy-key generation in the app.** The recipient
  must hold their own private key; generating it in the granter's
  app would be the wrong trust model. Paste-recipient-pubkey is the
  v1 contract. NS-E.2.

- **OAuth-for-CalDAV (Apple, Google).** Provider-specific dances;
  app-password path covers v1. NS-L.1.2.

- **Provider-API PR-state polling.** Manual `skb branch link-pr` is
  the v1 contract. NS-P.3.6.

- **Branch-protection PR creation in-app.** Provider-side workflow;
  deep-link is enough. D.40 + NS-P.1.

- **Notification group importance RAISE above channel ceiling.**
  Would require dynamic channel proliferation. Lower-only is the
  v1 contract. NS-A.14.

- **Cross-repo identity auto-merge via public-key match.** D.23
  signed commits cover the basic identity story; auto-merge across
  repos is v1.1+. NS-G.6.

- **QR code for SSH public-key export.** Marginal value over
  clipboard/share; zxing dep cost. D.40.

## Still out of scope (rejected, not postponed)

- **Self-served webcal/CalDAV endpoint from the app.** Would require
  background server + port-forwarding/discovery; not an Android
  client's job. Power users cron `tools/ics-export.sh` to a static
  host instead. NS-K.9.

- **Alpha-blend overlap.** Colorblind + screen-reader regression.
  Striped overlay model in `resolver.md` stays. D.40.

- **Multi-finger calendar gestures.** Undiscoverable for most users.
  D.40.

- **Semantic TOML auto-merge.** One-file-per-entity invariant +
  manual conflict UI is sufficient. Adding semantic merge would
  obscure the explicit data model. D.40.

- **Web app / desktop app.** Android-only in v1. The `skb` CLI (D.24)
  covers the cross-platform power-user surface. D.40.

- **End-to-end encrypted repo contents.** Repo can be private at the
  provider level; that's the v1 data-at-rest story. D.40.

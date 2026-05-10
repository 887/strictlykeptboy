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

# Deferred to v2 with rationale

- **Cross-repo identity unification (public-key match).** Documented
  hook in NS-G.6. Not needed for v1; complicates the data model and
  the UI's repo-scoping promise.

- **Comments / replies on events.** User direction was "events are
  not chat-shaped in v1." Markdown body covers collaborative notes
  meanwhile. v2 would add `comments/<event-id>/<comment-id>.md` and a
  comment composer. NS-G.7.

- **In-app collaborator listing.** Requires elevated provider OAuth
  scopes we don't ask for. Deep-link approach is sufficient v1.
  NS-E.3.

- **Bidirectional CalDAV sync.** `tools/caldav-bridge.py` stubbed.
  Real implementation = a long-running server/process; out of scope
  for an Android client app. Power users who want CalDAV today wire
  up `tools/ics-export.sh` cron-driven. NS-K.6 + NS-K.9.

- **CSV import.** Fragile mapping and ambiguous semantics; VTODO
  import covers the realistic use case. NS-J.6.

- **Self-served webcal/CalDAV endpoint from the app.** Would require
  background server + port-forwarding/discovery; not an Android
  client's job. NS-K.9.

- **Per-recipient deploy-key generation in the app.** The recipient
  must hold their own private key; generating it in the granter's
  app would be the wrong trust model. We provide guidance + paste
  field instead. NS-E.2.

- **CalDAV server-side write-back.** Same v2 territory as
  `caldav-bridge.py`. NS-K.6.

- **Cross-device snooze sync.** Snoozes are local; if a user moves
  between devices, snoozes don't follow. Acceptable v1 trade. NS-D.8.

- **Notification group importance RAISE above channel ceiling.**
  Would require dynamic channel proliferation. Lower-only is the
  v1 contract. NS-A.14.

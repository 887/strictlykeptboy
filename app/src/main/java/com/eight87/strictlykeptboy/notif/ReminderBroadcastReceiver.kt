package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.eight87.strictlykeptboy.MainActivity
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoStore
import java.io.File

/**
 * Phase M.2 + M.6 — receives AlarmManager fire intents, posts a
 * notification, handles snooze / deviation / open actions.
 *
 * The receiver is intentionally stateless beyond `goAsync()` — heavy
 * work (deviation file writes) is delegated to [DeviationActionWriter]
 * which runs on Dispatchers.IO.
 */
class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> postNotification(context, intent)
            ACTION_SNOOZE -> handleSnooze(context, intent)
            ACTION_DEVIATION -> handleDeviation(context, intent)
        }
    }

    private fun postNotification(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val calendarId = intent.getStringExtra(EXTRA_CALENDAR_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val priv = intent.getBooleanExtra(EXTRA_PRIVATE, false)

        // Respect channel-level enable toggle.
        val prefs = NotificationPrefs.open(context)
        if (!prefs.isChannelEnabled(NotificationChannels.EVENTS)) return
        // Phase 2.1.F.2 — per-event mute short-circuit.
        if (prefs.isEventMuted(repoId, eventId)) return
        // Phase 2.1.F.3 — per-calendar enable.
        if (calendarId.isNotEmpty() && !prefs.isCalendarEnabled(repoId, calendarId)) return
        // Phase 2.1.F.4 — time-bounded group mutes (calendar + repo).
        val now = System.currentTimeMillis()
        if (calendarId.isNotEmpty() &&
            prefs.isGroupMutedAt(LogicalGroup.Calendar(repoId, calendarId), now)) return
        if (prefs.isGroupMutedAt(LogicalGroup.Repo(repoId), now)) return

        val displayTitle = if (priv) context.getString(R.string.notif_event_private_title) else title
        val publicVersion = NotificationCompat.Builder(context, NotificationChannels.EVENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_event_private_title))
            .build()

        val openPi = PendingIntent.getActivity(
            context, eventId.hashCode(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val snoozePi = { minutes: Int ->
            PendingIntent.getBroadcast(
                context, (eventId + minutes).hashCode(),
                Intent(context, ReminderBroadcastReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtra(EXTRA_REPO_ID, repoId)
                    putExtra(EXTRA_EVENT_ID, eventId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_SNOOZE_MINUTES, minutes)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val deviationPi = { kind: String ->
            PendingIntent.getBroadcast(
                context, (eventId + kind).hashCode(),
                Intent(context, ReminderBroadcastReceiver::class.java).apply {
                    action = ACTION_DEVIATION
                    putExtra(EXTRA_REPO_ID, repoId)
                    putExtra(EXTRA_EVENT_ID, eventId)
                    putExtra(EXTRA_DEVIATION_KIND, kind)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        // Phase 2.1.J.3 / DDD.11 — register-aware body. `private = true`
        // collapses to the generic role-pre line so praise/honorific can
        // never leak onto a lockscreen preview.
        //
        // Phase 2.2.E.M5 — route through bodyForPet so the pet-mode register
        // (Self-Pet / Partnered-Pet / Self-Keep) the user picked in the
        // wizard actually reaches the lockscreen. Derivation reads the
        // active repo's `mode.toml`; private flag short-circuits inside
        // `bodyForPet` before any pet-mode phrasing applies.
        // Round 2.18.F.5 — external events (`external/<accountType>/...`)
        // have no on-disk identity.toml; reroute to the active write-target
        // repo so praise/honorific copy still applies. Plain repoIds pass
        // through unchanged.
        val resolvedRepoId = effectiveRepoIdResolver(context, repoId)
        val identity = if (priv || resolvedRepoId == null) null
            else identityResolver(context, resolvedRepoId)
        val petMode = if (priv || resolvedRepoId == null) PetMode.None
            else petModeResolver(context, resolvedRepoId)
        val identityBody = IdentityNotifBody.bodyForPet(
            identity = identity,
            title = title,
            privateEvent = priv,
            petMode = petMode,
        )
        val contentText = identityBody.ifBlank { context.getString(R.string.notif_event_role_pre) }
        val baseBuilder = NotificationCompat.Builder(context, NotificationChannels.EVENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(contentText)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .apply { if (priv) setPublicVersion(publicVersion) }
            .addAction(0, context.getString(R.string.notif_action_open), openPi)
            .addAction(0, context.getString(R.string.notif_action_snooze_10), snoozePi(10))
            .addAction(0, context.getString(R.string.notif_action_snooze_30), snoozePi(30))
            .addAction(0, context.getString(R.string.notif_action_snooze_60), snoozePi(60))
            .addAction(0, context.getString(R.string.notif_action_i_didnt), deviationPi(DEVIATION_SKIPPED))
            .addAction(0, context.getString(R.string.notif_action_partial), deviationPi(DEVIATION_PARTIAL))
        // Phase 2.1.F.9 — apply InboxStyle so multi-reminder stacking
        // gets a consistent surface; single-event posts still benefit
        // from the summary line for accessibility.
        val notif = ReminderInboxStyle.applyToBuilder(
            context = context,
            builder = baseBuilder,
            lines = listOf(ReminderInboxStyle.Line(title = title, isPrivate = priv)),
        ).build()

        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.notify(eventId.hashCode(), notif)
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        val fireAt = System.currentTimeMillis() + minutes * 60_000L

        EventReminderScheduler(context).snoozeAt(repoId, eventId, title, fireAt)
        // Dismiss the current notification.
        context.getSystemService<NotificationManager>()?.cancel(eventId.hashCode())
    }

    private fun handleDeviation(context: Context, intent: Intent) {
        val repoId = intent.getStringExtra(EXTRA_REPO_ID) ?: return
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_DEVIATION_KIND) ?: DEVIATION_SKIPPED

        val pending = goAsync()
        DeviationActionWriter.writeAsync(context, repoId, eventId, kind) {
            // dismiss + finalize
            context.getSystemService<NotificationManager>()?.cancel(eventId.hashCode())
            pending.finish()
        }
    }

    companion object {
        /**
         * Phase 2.2.E.M5 — resolve PetMode for [repoId] by reading the
         * active repo's working-tree `mode.toml`. Returns [PetMode.None]
         * for unknown repos / missing file / malformed TOML — callers
         * treat that as the neutral-template fallback inside
         * [IdentityNotifBody.bodyForPet]. The seam is `internal` + a
         * mutable `var` so [NotifReminderPetCopyTest] can substitute a
         * stub without needing a live (Encrypted)SharedPreferences-backed
         * [RepoStore] under Robolectric.
         */
        @JvmStatic
        internal var petModeResolver: (Context, String) -> PetMode = { context, repoId ->
            val cfg = runCatching { RepoStore.open(context).get(repoId) }.getOrNull()
            if (cfg == null) PetMode.None
            else PetModeDerivation.deriveFor(File(cfg.rootDir).toPath())
        }

        /**
         * Phase 2.2.E.M5 — identity resolver seam. Defaults to the
         * production [IdentityNotifBody.loadFor]; tests substitute a
         * stub for the same reason as [petModeResolver].
         */
        @JvmStatic
        internal var identityResolver: (Context, String) -> com.eight87.strictlykeptboy.store.IdentityTomlData? =
            { context, repoId -> IdentityNotifBody.loadFor(context, repoId) }

        /**
         * Round 2.18.F.5 — translate a possibly-synthetic external repoId
         * to the active write-target repo for identity / pet-mode lookup.
         * Pass-through for plain repoIds. Returns `null` only when there
         * is no configured repo at all, which the receiver treats as the
         * neutral-copy fallback (no praise / honorific applied).
         */
        @JvmStatic
        internal var effectiveRepoIdResolver: (Context, String) -> String? = { context, repoId ->
            if (repoId.startsWith("external/")) {
                runCatching { RepoStore.open(context).list().firstOrNull()?.repoId }
                    .getOrNull()
            } else {
                repoId
            }
        }

        const val ACTION_FIRE = "com.eight87.strictlykeptboy.notif.FIRE"
        const val ACTION_SNOOZE = "com.eight87.strictlykeptboy.notif.SNOOZE"
        const val ACTION_DEVIATION = "com.eight87.strictlykeptboy.notif.DEVIATION"

        const val EXTRA_REPO_ID = "repoId"
        const val EXTRA_EVENT_ID = "eventId"
        const val EXTRA_CALENDAR_ID = "calendarId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_LEAD_TIME = "leadTime"
        const val EXTRA_PRIVATE = "private"
        const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
        const val EXTRA_DEVIATION_KIND = "deviationKind"

        const val DEVIATION_SKIPPED = "skipped"
        const val DEVIATION_PARTIAL = "partial"
    }
}

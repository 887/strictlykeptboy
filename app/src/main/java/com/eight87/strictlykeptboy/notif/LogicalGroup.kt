package com.eight87.strictlykeptboy.notif

/**
 * Phase 2.1.F.3 — addressable "notification group" for the mute/silence
 * surface. The user's stated intent is to be able to mute groups
 * ("notification groups to toggle etc"); per Round 2.1 design D-2.1.b
 * calendars are the primary user-facing group.
 *
 * Three variants today:
 *  - [Calendar]: a `(repoId, calendarId)` pair. Maps onto existing
 *    `cal.<repoId>.<calId>.*` SharedPreferences keys for enable/silent/
 *    lead-times.
 *  - [Category]: a template-category slug (e.g. `medical`, `flight`,
 *    `household`). Per-category default lead-times already round-trip;
 *    this lets the user say "mute medical reminders until Monday".
 *  - [Repo]: bulk handle for "everything from this repo". Derived
 *    operation per D-2.1.b — per-calendar is primary, repo-level is the
 *    convenience aggregate.
 *
 * SOLID-S: type description only; persistence is owned by
 * [NotificationPrefs].
 */
sealed interface LogicalGroup {
    /** Slug used to namespace SharedPreferences keys. */
    val storageKey: String

    data class Calendar(val repoId: String, val calendarId: String) : LogicalGroup {
        override val storageKey: String get() = "calendar.$repoId.$calendarId"
    }

    data class Category(val slug: String) : LogicalGroup {
        override val storageKey: String get() = "category.$slug"
    }

    data class Repo(val repoId: String) : LogicalGroup {
        override val storageKey: String get() = "repo.$repoId"
    }
}

/**
 * Phase 2.1.F.4 — value object for a time-bounded group mute. The
 * receiver consults [NotificationPrefs.isGroupMutedAt] alongside the
 * existing channel/group enable check; both must be quiet for the
 * notification to drop.
 *
 * Persistence is owned by [NotificationPrefs.setGroupMute]; this struct
 * exists for UI binding + test ergonomics.
 */
data class NotificationMute(
    val scope: LogicalGroup,
    /** Absolute epoch-ms when the mute expires; `null` ⇒ mute is cleared. */
    val untilEpochMs: Long?,
) {
    fun isActiveAt(nowEpochMs: Long): Boolean = (untilEpochMs ?: 0L) > nowEpochMs
}

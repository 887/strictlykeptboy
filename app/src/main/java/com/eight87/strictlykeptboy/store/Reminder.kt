package com.eight87.strictlykeptboy.store

import java.time.Duration

/**
 * Phase BBB.7 / DM-X — Event multi-reminder schema.
 *
 * On disk (per DM-X.1 / DM-X.2) an event may carry an arbitrary number
 * of `[[reminder]]` blocks:
 *
 * ```toml
 * [[reminder]]
 * offset = "-PT30M"
 * kind = "pre_event"
 *
 * [[reminder]]
 * offset = "0"
 * kind = "at_start"
 *
 * [[reminder]]
 * offset = "PT4H"
 * kind = "post_event_checkin"
 * ```
 *
 * `offset` is ISO-8601 duration; negative = before event, positive =
 * after, `"0"` = at start. `kind` is the sealed `ReminderKind` below.
 *
 * Default-cadence presets per template category (DM-X.4 / D.79) are
 * exposed via [DefaultCadences] for the wizard + materializer to
 * consume.
 *
 * SOLID-S: this file's single responsibility is the
 * reminder-array codec on event frontmatter. SOLID-O: the
 * [ReminderKind] sealed enum is closed today; adding a kind = enum
 * entry + render-mapping update (not enforced here). SOLID-D: pure
 * Kotlin, no Android / WorkManager imports — firing is the consumer's
 * concern (BBB.10).
 *
 * Round-trip is exercised by `ReminderParseTest`.
 */
data class Reminder(
    /** ISO-8601 duration string (`"0"` for at-start). */
    val offset: String,
    val kind: ReminderKind,
    /** Optional channel override (DM-X.2). `null` ⇒ derive from [kind]. */
    val channel: String? = null,
    /** Optional lockscreen visibility override (DM-X.2). `null` ⇒ derive from event's `private` flag. */
    val lockscreenVisibility: LockscreenVisibility? = null,
) {
    /** Parsed [Duration]; positive = after event, negative = before. */
    val offsetDuration: Duration get() = parseOffset(offset)

    fun toTable(): TomlTable {
        val t = TomlTable()
        t.putString("offset", offset)
        t.putString("kind", kind.tomlValue)
        channel?.let { t.putString("channel", it) }
        lockscreenVisibility?.let { t.putString("lockscreen_visibility", it.tomlValue) }
        return t
    }

    companion object {
        fun fromTable(t: TomlTable): Reminder? {
            val offset = t.getString("offset") ?: return null
            // Validate duration parsing up front; reject malformed.
            runCatching { parseOffset(offset) }.getOrNull() ?: return null
            val kind = ReminderKind.fromToml(t.getString("kind")) ?: return null
            val channel = t.getString("channel")
            val vis = LockscreenVisibility.fromToml(t.getString("lockscreen_visibility"))
            return Reminder(offset = offset, kind = kind, channel = channel, lockscreenVisibility = vis)
        }

        fun readArray(frontmatter: TomlTable): List<Reminder> {
            val rows = frontmatter.aotables["reminder"] ?: return emptyList()
            return rows.mapNotNull { fromTable(it) }
        }

        fun writeArray(frontmatter: TomlTable, reminders: List<Reminder>) {
            if (reminders.isEmpty()) return
            frontmatter.aotables["reminder"] = reminders.map { it.toTable() }.toMutableList()
        }

        /** Parse `"0"` ⇒ ZERO, otherwise standard ISO-8601 (handles negative `-PT30M`). */
        fun parseOffset(s: String): Duration {
            val trimmed = s.trim()
            if (trimmed == "0" || trimmed == "PT0S" || trimmed == "P0D") return Duration.ZERO
            return Duration.parse(trimmed)
        }

        /**
         * DM-X.5 interaction: when [allDay] is true, drop `at_start` and
         * splice in an `all_day_banner` if missing.
         */
        fun normaliseForAllDay(reminders: List<Reminder>, allDay: Boolean): List<Reminder> {
            if (!allDay) return reminders
            val withoutAtStart = reminders.filter { it.kind != ReminderKind.AtStart }
            return if (withoutAtStart.any { it.kind == ReminderKind.AllDayBanner }) {
                withoutAtStart
            } else {
                withoutAtStart + Reminder(offset = "0", kind = ReminderKind.AllDayBanner)
            }
        }
    }
}

/** DM-X.3 sealed reminder kinds. */
enum class ReminderKind(
    val tomlValue: String,
    /** Default Android notification channel id. */
    val defaultChannel: String,
) {
    HeadsUp("heads_up", "events-heads-up"),
    AllDayBanner("all_day_banner", "events-all-day"),
    TomorrowBriefing("tomorrow_briefing", "briefings-tomorrow"),
    PreEvent("pre_event", "events-pre"),
    AtStart("at_start", "events-at-start"),
    PostEventCheckin("post_event_checkin", "events-checkin");

    companion object {
        fun fromToml(s: String?): ReminderKind? =
            entries.firstOrNull { it.tomlValue == s }
    }
}

/** Per-reminder lockscreen visibility override. */
enum class LockscreenVisibility(val tomlValue: String) {
    Public("public"),
    Private("private"),
    Secret("secret");
    companion object {
        fun fromToml(s: String?): LockscreenVisibility? =
            entries.firstOrNull { it.tomlValue == s }
    }
}

/**
 * DM-X.4 / D.79 default-cadence catalogue per template category.
 *
 * The map keys are template-category slugs ("medical", "flight",
 * "travel-prep", "vacation-daily", "household", "medication", "adhd",
 * "birthdays"). Empty list ⇒ no default reminders for that category.
 *
 * Values are LOCKED per D.79; changing them is a calendar-repo-impact
 * decision (do NOT silently churn).
 */
object DefaultCadences {
    val byCategory: Map<String, List<Reminder>> = mapOf(
        // Medical: pre-event 1d + 1h + at-start (DM-X.4 / D.79)
        "medical" to listOf(
            Reminder("-P1D", ReminderKind.PreEvent),
            Reminder("-PT1H", ReminderKind.PreEvent),
            Reminder("0", ReminderKind.AtStart),
        ),
        // Flight: tomorrow-briefing + 4h pre + at-start
        "flight" to listOf(
            Reminder("-P1D", ReminderKind.TomorrowBriefing),
            Reminder("-PT4H", ReminderKind.PreEvent),
            Reminder("0", ReminderKind.AtStart),
        ),
        // Travel-prep: heads-up + at-start
        "travel-prep" to listOf(
            Reminder("-PT30M", ReminderKind.HeadsUp),
            Reminder("0", ReminderKind.AtStart),
        ),
        // Vacation-daily: at-start only (relaxed cadence)
        "vacation-daily" to listOf(
            Reminder("0", ReminderKind.AtStart),
        ),
        // Household: heads-up + checkin
        "household" to listOf(
            Reminder("-PT10M", ReminderKind.HeadsUp),
            Reminder("0", ReminderKind.AtStart),
            Reminder("PT30M", ReminderKind.PostEventCheckin),
        ),
        // Medication: at-start + 15m post-checkin (D.82 opt-in inverted habit)
        "medication" to listOf(
            Reminder("0", ReminderKind.AtStart),
            Reminder("PT15M", ReminderKind.PostEventCheckin),
        ),
        // ADHD anchors: heads-up + at-start
        "adhd" to listOf(
            Reminder("-PT5M", ReminderKind.HeadsUp),
            Reminder("0", ReminderKind.AtStart),
        ),
        // Birthdays: all-day banner + tomorrow-briefing day before
        "birthdays" to listOf(
            Reminder("-P1D", ReminderKind.TomorrowBriefing),
            Reminder("0", ReminderKind.AllDayBanner),
        ),
    )

    fun forCategory(slug: String?): List<Reminder> =
        slug?.let { byCategory[it] } ?: emptyList()
}

/**
 * Phase BBB.10 / HV-N.7 helper: collapse reminders that fire within a
 * 60-second window into a single bucket so the notification scheduler
 * can render them as one Android notification with N expansion lines.
 *
 * Pure function — caller supplies the absolute fire-instants.
 */
object ReminderCollapsing {
    /** 60 second collision window per HV-N.7. */
    val WINDOW: Duration = Duration.ofSeconds(60)

    data class Bucket(val anchor: java.time.Instant, val members: List<java.time.Instant>)

    /** Returns time-ordered buckets; never empty. */
    fun collapse(fires: List<java.time.Instant>): List<Bucket> {
        if (fires.isEmpty()) return emptyList()
        val sorted = fires.sorted()
        val out = mutableListOf<Bucket>()
        var anchor = sorted.first()
        val cur = mutableListOf(anchor)
        for (i in 1 until sorted.size) {
            val t = sorted[i]
            if (Duration.between(anchor, t) <= WINDOW) {
                cur += t
            } else {
                out += Bucket(anchor, cur.toList())
                cur.clear()
                anchor = t
                cur += t
            }
        }
        out += Bucket(anchor, cur.toList())
        return out
    }
}

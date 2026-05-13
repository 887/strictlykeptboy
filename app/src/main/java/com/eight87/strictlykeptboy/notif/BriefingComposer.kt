package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.store.IdentityTomlData
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Phase 2.1.F.6 — pure composer for the morning + evening briefing
 * notification bodies. The WorkManager worker materializes today's /
 * tomorrow's instances, hands them here, and posts the resulting
 * [Briefing] through Android's `InboxStyle`.
 *
 * Identity-driven copy: when [IdentityTomlData] is provided we use
 * `IdentityNotifBody.briefingSalutation()` for the leading line.
 * `private = true` instances render as a generic placeholder line so
 * the lockscreen preview can't leak praise term + title.
 *
 * SOLID-S / D: pure Kotlin, no Android imports — testable in plain JVM.
 */
object BriefingComposer {

    data class Briefing(
        val title: String,
        val salutation: String,
        val lines: List<String>,
        /** When at least one event is private — affects lockscreen visibility. */
        val containsPrivate: Boolean,
    )

    enum class Slot { Morning, Evening }

    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * @param slot morning briefing summarises today; evening summarises
     *   tomorrow.
     * @param instances candidate instances (caller filters to the slot's
     *   target date).
     * @param identity per-repo identity for the leading salutation.
     * @param zone timezone for "today / tomorrow" comparison + time
     *   formatting.
     * @param now anchor for "today" computation; defaults to now in [zone].
     */
    fun compose(
        slot: Slot,
        instances: List<MaterializedInstance>,
        identity: IdentityTomlData? = null,
        zone: ZoneId = ZoneId.systemDefault(),
        now: () -> java.time.ZonedDateTime = { java.time.ZonedDateTime.now(zone) },
        emptyLine: String = "Nothing scheduled.",
        privatePlaceholder: String = "private event",
    ): Briefing {
        val today: LocalDate = now().toLocalDate()
        val target = when (slot) {
            Slot.Morning -> today
            Slot.Evening -> today.plusDays(1L)
        }
        val sorted = instances
            .filter { it.effectiveStart.withZoneSameInstant(zone).toLocalDate() == target }
            .sortedBy { it.effectiveStart }
        val containsPrivate = sorted.any { it.isPrivate }
        val lines = if (sorted.isEmpty()) listOf(emptyLine)
        else sorted.map { inst ->
            val hhmm = inst.effectiveStart.withZoneSameInstant(zone).format(timeFmt)
            val displayTitle = if (inst.isPrivate) privatePlaceholder else inst.title
            "$hhmm  $displayTitle"
        }
        val salutationKind = when (slot) {
            Slot.Morning -> IdentityNotifBody.TimeOfDay.Morning
            Slot.Evening -> IdentityNotifBody.TimeOfDay.Evening
        }
        return Briefing(
            title = when (slot) {
                Slot.Morning -> "Today's schedule"
                Slot.Evening -> "Tomorrow's schedule"
            },
            salutation = IdentityNotifBody.briefingSalutation(identity, salutationKind),
            lines = lines,
            containsPrivate = containsPrivate,
        )
    }
}

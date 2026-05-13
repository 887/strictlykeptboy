package com.eight87.strictlykeptboy.ui.trip

import java.time.LocalDate

/**
 * Phase CCC / HV-F.1 — quick-trip wizard state.
 *
 * Compact-cut implementation of the HV-F TripDraft: enough fields to drive
 * the materialization of [atomic-travel-prep], [atomic-flight-day], and
 * [atomic-vacation-daily] for the most common case (a single destination,
 * single round-trip flight, 1+ travelers).
 *
 * Out-of-scope for this compact cut (tracked in main.md CCC.6 / CCC.7 /
 * CCC.9): the full supersedence picker (Screen 5), mini-month preview
 * (Screen 6), edit-in-flight (HV-F.9), cancel-trip (HV-F.10) — these
 * follow once the compact path proves itself on real trip data.
 *
 * SOLID-S: this file owns only the draft shape + the wizard's screen enum
 * + the transport-mode enum. Materialization lives in [TripScaffolder].
 *
 * @param name human-readable trip label, e.g. "Sicily 2026".
 * @param destination free-text destination (offline-only autocomplete is a
 *        follow-up; for v1 the user types it).
 * @param startDate first day of the trip (inclusive).
 * @param endDate last day of the trip (inclusive); must be >= startDate.
 * @param transport selected transport mode (controls whether the flight-day
 *        template is materialized).
 * @param travelerCount number of travelers (>=1). Recorded in the trip
 *        calendar's frontmatter; future surfaces can use it to fan-out
 *        per-person packing checklists.
 * @param packKinkKit gates HV-B.7's `pack-kink-kit` sub-beat per HV-F.5.
 * @param includeVacationDaily when true, materializes the daily anchors.
 *        Defaults true.
 */
data class TripDraft(
    val name: String = "",
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val transport: TransportMode = TransportMode.None,
    val travelerCount: Int = 1,
    val packKinkKit: Boolean = false,
    val includeVacationDaily: Boolean = true,
) {
    val hasUserChoices: Boolean
        get() = name.isNotBlank() || destination.isNotBlank() || startDate != null ||
            endDate != null || transport != TransportMode.None ||
            travelerCount != 1 || packKinkKit || !includeVacationDaily

    /** Compact-cut validation: all required fields present + dates ordered. */
    val isComplete: Boolean
        get() = name.isNotBlank() &&
            startDate != null && endDate != null &&
            !endDate.isBefore(startDate) && travelerCount >= 1

    val durationDays: Int
        get() {
            val s = startDate ?: return 0
            val e = endDate ?: return 0
            return java.time.temporal.ChronoUnit.DAYS.between(s, e).toInt() + 1
        }
}

/**
 * Phase CCC / HV-F.2 — locked transport mode set.
 *
 * `Flight` is the only mode that triggers flight-day template materialization
 * in this compact cut; the rest are recorded in the trip's `calendar.toml`
 * and the daily anchors still materialize.
 */
enum class TransportMode(val id: String, val labelEn: String) {
    Flight("flight", "Flight"),
    Train("train", "Train"),
    Car("car", "Car"),
    Boat("boat", "Boat"),
    None("none", "None / unset"),
}

/**
 * Phase CCC / HV-F.1 — wizard screens (compact 4-screen cut).
 *
 * Each screen carries a `stickerKey` mapping to the bat-mascot beats from
 * HV-H — read by the sticker resolver via per-screen activity-id lookup.
 */
enum class TripScreen(val stickerKey: String) {
    Basics("trip-suitcase-waving"),
    Transport("flight-paw-prints"),
    Anchors("beach-loungin-with-cage-still-on"),
    Confirm("confirm-tail-flick"),
}

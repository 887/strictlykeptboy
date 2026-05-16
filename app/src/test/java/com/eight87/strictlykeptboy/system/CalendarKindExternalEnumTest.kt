package com.eight87.strictlykeptboy.system

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round 2.18.J.8 — exhaustiveness audit for the [CalendarKind.External]
 * variant.
 *
 * The compiler enforces exhaustive `when` on a typed [CalendarKind]
 * expression, but it doesn't guarantee that every production-side site
 * actually *handled* the new variant rather than papering it over with
 * an `else -> /* fallthrough */`. This test:
 *
 *   1. Pins the enum entry order + ordinal of `External` so persisted
 *      `.ordinal` integers stay stable.
 *   2. Asserts that `CalendarMeta.externalAccount` honours its
 *      `kind == External` contract (Liskov: variants must satisfy the
 *      derived property's documented behaviour).
 *   3. Stand-in `when (kind)` helper covers all three variants —
 *      adding a fourth variant in the future will fail to compile
 *      here, forcing whoever adds it to teach the test about the new
 *      surface.
 */
class CalendarKindExternalEnumTest {

    @Test fun externalIsExactlyTheThirdEnumEntry() {
        val values = CalendarKind.values().toList()
        assertEquals(3, values.size)
        assertEquals(CalendarKind.Regular, values[0])
        assertEquals(CalendarKind.Timebox, values[1])
        assertEquals(CalendarKind.External, values[2])
        // Ordinal contract — load-bearing for any downstream `.ordinal`
        // serializer (Room TypeConverter, kotlinx.serialization default,
        // SharedPreferences int storage). Pinning it here so a reorder
        // never silently breaks persisted rows.
        assertEquals(2, CalendarKind.External.ordinal)
    }

    @Test fun externalAccountResolvesOnlyForExternalKindWithSyntheticRepoId() {
        val externalMeta = CalendarMeta(
            ref = CalendarRef("42"),
            repo = RepoRef("system/com.google/alex@example.com"),
            displayName = "Google — alex@example.com",
            priority = 100,
            kind = CalendarKind.External,
            externalAccessLevel = 700,
        )
        assertEquals(
            "com.google" to "alex@example.com",
            externalMeta.externalAccount,
        )

        val regularMeta = externalMeta.copy(kind = CalendarKind.Regular)
        assertNull(
            "Regular kind must never expose externalAccount even with synthetic id",
            regularMeta.externalAccount,
        )

        val timeboxMeta = externalMeta.copy(kind = CalendarKind.Timebox)
        assertNull(
            "Timebox kind must never expose externalAccount even with synthetic id",
            timeboxMeta.externalAccount,
        )

        val externalNonSynthetic = externalMeta.copy(repo = RepoRef("local-repo"))
        assertNull(
            "External kind without `system/.../...` id must yield null externalAccount",
            externalNonSynthetic.externalAccount,
        )
    }

    @Test fun externalKindHandledExhaustivelyByLocalWhenSite() {
        // Mirror of the production `when (kind)` shape used in
        // `EventDetailSheet`'s "Kind:" label + `BandOverlays`'s glyph
        // picker. If a fourth `CalendarKind` ever appears, this
        // expression will fail to compile and force the new contributor
        // to teach this test about the new variant.
        for (k in CalendarKind.values()) {
            val label: String = when (k) {
                CalendarKind.Regular -> "Regular"
                CalendarKind.Timebox -> "Timebox"
                CalendarKind.External -> "External"
            }
            assertEquals(k.name, label)
        }
    }
}

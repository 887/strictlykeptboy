package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.24.A.2 — per-event `tz_id` codec round-trip.
 *
 * Locked decision D-2.24.a: the field is **optional** and **additive**.
 * - Absent on wire ⇒ `tzId == null` (event resolves in repo-default tz
 *   at render time).
 * - Present ⇒ preserved verbatim across `toDoc` / `fromDoc`.
 * - Malformed (e.g. `"Not/A_Zone"`) ⇒ preserved verbatim — codec does
 *   NOT call `ZoneId.of(...)`; consumer (`TzResolver`, Phase B) handles
 *   fallback.
 *
 * Mirrors the Round 2.27 keeper-prompt round-trip pattern in
 * [EntityRoundTripTest] (commit `48ad3f5`).
 */
class EventTzRoundTripTest {

    private val header = EntityHeader(
        id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f024",
        createdAt = "2026-05-17T10:00:00+02:00",
        updatedAt = "2026-05-17T10:00:00+02:00",
        author = "01900000-0000-7000-8000-aaaaaaaaaaaa",
    )

    private fun baseEvent(tzId: String? = null) = Event(
        header = header,
        title = "Sync with NYC dom",
        start = "2026-05-18T09:00:00+02:00",
        end = "2026-05-18T09:30:00+02:00",
        calendarId = "0190a0aa-1c1d-7000-8a0a-000000000001",
        tzId = tzId,
    )

    @Test fun tzIdAbsentOnWireDecodesToNull() {
        val e = baseEvent(tzId = null)
        val text = FrontmatterWriter.serialize(e.toDoc())
        // Omit-on-default: must not leak the key for unpinned events.
        assert(!text.contains("tz_id")) {
            "tz_id leaked into default-valued event:\n$text"
        }
        val back = Event.fromDoc(FrontmatterReader.parse(text))
        assertEquals(null, back.tzId)
        assertEquals(e, back)
    }

    @Test fun tzIdPresentRoundTrips() {
        val e = baseEvent(tzId = "America/New_York")
        val text = FrontmatterWriter.serialize(e.toDoc())
        assert(text.contains("tz_id")) { "tz_id was dropped on write:\n$text" }
        assert(text.contains("America/New_York"))
        val back = Event.fromDoc(FrontmatterReader.parse(text))
        assertEquals("America/New_York", back.tzId)
        assertEquals(e, back)
    }

    @Test fun tzIdUnknownStringPreservedVerbatim() {
        // The codec does not validate against `ZoneId.getAvailableZoneIds()`;
        // resolver-layer code handles fallback. Malformed values must
        // still round-trip so hand-edited files don't get clobbered.
        val raw = """
            +++
            schema_version = 1
            id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f024"
            kind = "event"
            created_at = 2026-05-17T10:00:00+02:00
            updated_at = 2026-05-17T10:00:00+02:00
            author = "01900000-0000-7000-8000-aaaaaaaaaaaa"
            title = "garbled tz"
            start = 2026-05-18T09:00:00+02:00
            end = 2026-05-18T09:30:00+02:00
            calendar_id = "0190a0aa-1c1d-7000-8a0a-000000000001"
            tz_id = "Not/A_Real_Zone"
            +++
        """.trimIndent()
        val parsed = FrontmatterReader.parse(raw)
        val back = Event.fromDoc(parsed)
        assertEquals("Not/A_Real_Zone", back.tzId)
        // Re-serialise: malformed-but-preserved value survives the
        // round-trip so a `skb` save doesn't silently delete the field.
        val text2 = FrontmatterWriter.serialize(back.toDoc())
        assert(text2.contains("Not/A_Real_Zone"))
    }
}

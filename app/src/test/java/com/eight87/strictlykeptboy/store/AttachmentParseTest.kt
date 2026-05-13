package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase BBB.5 / DM-W — Attachment round-trip for all six kinds + LFS
 * threshold + ±90 / ±180 lat-lon rejection.
 */
class AttachmentParseTest {

    @Test fun link_round_trip() {
        val a = Attachment.Link(url = "https://example.test/x", label = "Booking")
        val parsed = Attachment.fromTable(a.toTable())
        assertEquals(a, parsed)
    }

    @Test fun qr_with_data_only() {
        val a = Attachment.Qr(data = "WIFI:S:home;P:secret;;")
        val parsed = Attachment.fromTable(a.toTable()) as? Attachment.Qr
        assertNotNull(parsed)
        assertEquals(a.data, parsed!!.data)
        assertNull(parsed.file)
    }

    @Test fun file_round_trip_with_description() {
        val a = Attachment.File(
            file = "ticket.pdf",
            mimeType = "application/pdf",
            sizeBytes = 4096,
            description = "Boarding pass",
        )
        val parsed = Attachment.fromTable(a.toTable())
        assertEquals(a, parsed)
    }

    @Test fun barcode_aztec_round_trip() {
        val a = Attachment.Barcode(
            file = "boarding.png",
            format = Attachment.BarcodeFormat.Aztec,
            data = "M1DOE/JOHN...",
        )
        val parsed = Attachment.fromTable(a.toTable())
        assertEquals(a, parsed)
    }

    @Test fun vcard_round_trip() {
        val a = Attachment.VCard(file = "doctor.vcf")
        val parsed = Attachment.fromTable(a.toTable())
        assertEquals(a, parsed)
    }

    @Test fun location_round_trip() {
        val a = Attachment.Location(lat = 52.520008, lon = 13.404954, label = "Brandenburger Tor")
        val parsed = Attachment.fromTable(a.toTable()) as Attachment.Location
        assertEquals(a.lat, parsed.lat, 1e-9)
        assertEquals(a.lon, parsed.lon, 1e-9)
        assertEquals(a.label, parsed.label)
    }

    @Test fun location_out_of_range_rejected() {
        val bad = TomlTable().apply {
            putString("kind", "location")
            scalars["lat"] = TomlValue.F64(91.0)
            scalars["lon"] = TomlValue.F64(0.0)
        }
        assertNull(Attachment.fromTable(bad))
    }

    @Test fun unknown_kind_returns_null() {
        val t = TomlTable().apply { putString("kind", "video_call") }
        assertNull(Attachment.fromTable(t))
    }

    @Test fun lfs_threshold_constant_matches_DM_W_3() {
        assertEquals(100L * 1024L, Attachment.LFS_THRESHOLD_BYTES)
    }

    @Test fun array_writeRead_preserves_order_and_kinds() {
        val list = listOf<Attachment>(
            Attachment.Link(url = "https://a"),
            Attachment.File(file = "x.pdf", mimeType = "application/pdf", sizeBytes = 1L),
            Attachment.Location(lat = 0.0, lon = 0.0),
        )
        val fm = TomlTable()
        Attachment.writeArray(fm, list)
        val parsed = Attachment.readArray(fm)
        assertEquals(3, parsed.size)
        assertTrue(parsed[0] is Attachment.Link)
        assertTrue(parsed[1] is Attachment.File)
        assertTrue(parsed[2] is Attachment.Location)
    }

    @Test fun writeArray_empty_is_noop() {
        val fm = TomlTable()
        Attachment.writeArray(fm, emptyList())
        assertNull(fm.aotables["attachment"])
    }
}

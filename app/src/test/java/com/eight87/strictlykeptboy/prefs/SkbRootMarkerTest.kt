package com.eight87.strictlykeptboy.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SkbRootMarkerTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun write_then_read_round_trip() {
        val parent = tmp.newFolder("p")
        SkbRootMarker.write(parent, deviceName = "Pixel 9 Pro", nowIso = "2026-05-15T12:00:00Z")
        val marker = SkbRootMarker.read(parent)
        assertNotNull(marker)
        marker!!
        assertEquals(SkbRootMarker.VERSION, marker.version)
        assertEquals(SkbRootMarker.APP_ID, marker.appId)
        assertEquals("2026-05-15T12:00:00Z", marker.createdAt)
        assertEquals("Pixel 9 Pro", marker.createdBy)
        assertTrue(SkbRootMarker.isSkbRoot(parent))
    }

    @Test fun isSkbRoot_returns_false_for_empty_dir() {
        val parent = tmp.newFolder("empty")
        assertFalse(SkbRootMarker.isSkbRoot(parent))
        assertNull(SkbRootMarker.read(parent))
    }

    @Test fun rejects_foreign_app_id() {
        val parent = tmp.newFolder("foreign")
        File(parent, SkbRootMarker.FILE_NAME).writeText(
            """
            version = 1
            app_id = "com.other.app"
            created_at = "2026-01-01T00:00:00Z"
            created_by = ""
            """.trimIndent(),
        )
        assertNull(SkbRootMarker.read(parent))
        assertFalse(SkbRootMarker.isSkbRoot(parent))
    }

    @Test fun rejects_unknown_schema_version() {
        val parent = tmp.newFolder("future")
        File(parent, SkbRootMarker.FILE_NAME).writeText(
            """
            version = 99
            app_id = "${SkbRootMarker.APP_ID}"
            created_at = "2026-01-01T00:00:00Z"
            created_by = ""
            """.trimIndent(),
        )
        assertNull(SkbRootMarker.read(parent))
    }

    @Test fun write_creates_parent_directory() {
        val parent = File(tmp.newFolder("outer"), "inner-nested/parent")
        // parent does not exist yet
        assertFalse(parent.exists())
        SkbRootMarker.write(parent, deviceName = "")
        assertTrue(parent.isDirectory)
        assertTrue(SkbRootMarker.isSkbRoot(parent))
    }
}

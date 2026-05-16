package com.eight87.strictlykeptboy.system

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.J.15 — manifest authority correctness.
 *
 * The sync-adapter descriptor at
 * `app/src/main/res/xml/sync_calendar.xml` declares
 * `android:contentAuthority`; this must equal
 * `CalendarContract.AUTHORITY` ("com.android.calendar") exactly,
 * otherwise the sync-adapter framework will refuse to bind our
 * `SkbCalendarSyncAdapter` to the system Calendar provider.
 *
 * This is a plain string-equality test against the XML resource. We
 * read the file off the test classpath rather than parsing the
 * compiled `R.xml.sync_calendar` resource so it works under bare JUnit
 * (no AndroidX test runner overhead).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SyncAdapterAuthorityTest {

    @Test fun contentAuthorityMatchesCalendarContractExactly() {
        val xml = readXmlResource("xml/sync_calendar.xml")
        // Naive but stable: look for the attribute literal. The
        // attribute is on the single root <sync-adapter> element.
        val authorityRegex = Regex("""android:contentAuthority\s*=\s*"([^"]+)"""")
        val match = authorityRegex.find(xml)
            ?: error("contentAuthority attribute not found in sync_calendar.xml")
        val declared = match.groupValues[1]

        assertEquals(
            "sync_calendar.xml#contentAuthority must equal CalendarContract.AUTHORITY",
            CalendarContract.AUTHORITY,
            declared,
        )
        // Belt-and-braces: pin the literal too. A future Android SDK
        // revising `CalendarContract.AUTHORITY` would be a load-bearing
        // breakage we want to spot, not silently follow.
        assertEquals("com.android.calendar", declared)
    }

    @Test fun accountTypeMatchesProductionConstant() {
        val xml = readXmlResource("xml/sync_calendar.xml")
        val accountTypeRegex = Regex("""android:accountType\s*=\s*"([^"]+)"""")
        val match = accountTypeRegex.find(xml)
            ?: error("accountType attribute not found in sync_calendar.xml")
        val declared = match.groupValues[1]
        assertEquals(
            "sync_calendar.xml#accountType must equal the skb authenticator's account type",
            "com.eight87.strictlykeptboy",
            declared,
        )
    }

    @Test fun isAlwaysSyncableAndUserVisible() {
        // Both flags are load-bearing for the surface to show under
        // Settings → Accounts and to permit `ContentResolver.requestSync`
        // before the first cold sync, so they're pinned here too.
        val xml = readXmlResource("xml/sync_calendar.xml")
        assertTrue(
            "sync_calendar.xml should declare isAlwaysSyncable=true",
            Regex("""android:isAlwaysSyncable\s*=\s*"true"""").containsMatchIn(xml),
        )
        assertTrue(
            "sync_calendar.xml should declare userVisible=true (Settings → Accounts surface)",
            Regex("""android:userVisible\s*=\s*"true"""").containsMatchIn(xml),
        )
    }

    private fun readXmlResource(path: String): String {
        // The Android resource compilation step copies res/xml into the
        // test-runtime classpath under `res/xml/...`. Reading it as a
        // raw string keeps the test independent of the AAPT-generated
        // R class.
        val candidates = listOf(
            "../../../../main/res/$path",
            "main/res/$path",
            "res/$path",
        )
        for (rel in candidates) {
            val f = java.io.File("app/src/main/res/$path")
            if (f.exists()) return f.readText()
            val f2 = java.io.File("src/main/res/$path")
            if (f2.exists()) return f2.readText()
        }
        // Fallback — walk from working directory until we find it.
        var cur = java.io.File("").absoluteFile
        repeat(6) {
            val probe = java.io.File(cur, "app/src/main/res/$path")
            if (probe.exists()) return probe.readText()
            cur = cur.parentFile ?: return@repeat
        }
        error("could not locate $path on the test working tree")
    }
}

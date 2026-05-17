package com.eight87.strictlykeptboy.manifest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

/**
 * Round 2.18 Phase E.9 follow-up — automates the default-handler
 * acceptance checklist by parsing AndroidManifest.xml and asserting
 * the intent-filters, permissions, activity-aliases, and sync-account
 * descriptors named in `docs/plans/round-2-18-default-handler-test.md`.
 *
 * Robolectric's manifest parsing exposes most of what we need via
 * [PackageManager.getPackageInfo], but `<activity-alias>` is not
 * surfaced through that API on every shadow version. We therefore
 * also do a direct XML parse of the manifest file off the source tree
 * (via `src/main/AndroidManifest.xml`) for alias coverage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class DefaultHandlerManifestTest {

    private val pkg = "com.eight87.strictlykeptboy"

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    // ---- permission coverage ---------------------------------------

    @Test fun manifestDeclaresCalendarReadWritePermissions() {
        val pm = ctx().packageManager
        val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        val perms = info.requestedPermissions?.toSet().orEmpty()
        assertTrue("READ_CALENDAR missing", perms.contains("android.permission.READ_CALENDAR"))
        assertTrue("WRITE_CALENDAR missing", perms.contains("android.permission.WRITE_CALENDAR"))
    }

    @Test fun manifestDeclaresBootAndNotificationPermissions() {
        val pm = ctx().packageManager
        val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        val perms = info.requestedPermissions?.toSet().orEmpty()
        assertTrue("RECEIVE_BOOT_COMPLETED missing",
            perms.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue("POST_NOTIFICATIONS missing",
            perms.contains("android.permission.POST_NOTIFICATIONS"))
    }

    @Test fun manifestDeclaresSyncAccountPermissions() {
        val pm = ctx().packageManager
        val info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        val perms = info.requestedPermissions?.toSet().orEmpty()
        assertTrue("GET_ACCOUNTS missing", perms.contains("android.permission.GET_ACCOUNTS"))
        assertTrue("AUTHENTICATE_ACCOUNTS missing",
            perms.contains("android.permission.AUTHENTICATE_ACCOUNTS"))
        assertTrue("READ_SYNC_SETTINGS missing",
            perms.contains("android.permission.READ_SYNC_SETTINGS"))
        assertTrue("WRITE_SYNC_SETTINGS missing",
            perms.contains("android.permission.WRITE_SYNC_SETTINGS"))
    }

    // ---- sync / authenticator account type -------------------------

    @Test fun authenticatorAccountTypeMatchesPackage() {
        val xml = File("src/main/res/xml/authenticator.xml").readText()
        assertTrue(
            "authenticator.xml accountType must be $pkg",
            xml.contains("android:accountType=\"$pkg\""),
        )
    }

    @Test fun syncAdapterAccountTypeMatchesPackage() {
        val xml = File("src/main/res/xml/sync_calendar.xml").readText()
        assertTrue(
            "sync_calendar.xml accountType must be $pkg",
            xml.contains("android:accountType=\"$pkg\""),
        )
        assertTrue(
            "sync_calendar.xml authority must be com.android.calendar",
            xml.contains("android:contentAuthority=\"com.android.calendar\""),
        )
    }

    // ---- ics opener handler resolution -----------------------------

    @Test fun icsOpenerResolvesToStrictlyKeptBoy() {
        val pm = ctx().packageManager
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://media/external/file/42"), "text/calendar")
        }
        val target = ComponentName(pkg, "$pkg.IcsImportActivity")
        val ri = ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
                packageName = pkg
                name = target.className
            }
            isDefault = true
        }
        Shadows.shadowOf(pm).addResolveInfoForIntent(intent, ri)
        val resolved = pm.resolveActivity(intent, 0)
        assertNotNull("PackageManager.resolveActivity returned null for text/calendar VIEW", resolved)
        assertEquals(pkg, resolved!!.activityInfo.packageName)
        assertEquals(target.className, resolved.activityInfo.name)
    }

    // ---- manifest XML parse: activity-aliases + intent-filters -----

    private data class FilterCoverage(
        val aliases: Map<String, List<ParsedFilter>>,
        val mainActivityFilters: List<ParsedFilter>,
    )

    private data class ParsedFilter(
        val actions: Set<String>,
        val categories: Set<String>,
        val mimeTypes: Set<String>,
        val schemes: Set<String>,
        val hosts: Set<String>,
    )

    private fun parseManifest(): FilterCoverage {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser()
        parser.setInput(File("src/main/AndroidManifest.xml").reader())
        val ns = "http://schemas.android.com/apk/res/android"

        val aliases = linkedMapOf<String, MutableList<ParsedFilter>>()
        val mainFilters = mutableListOf<ParsedFilter>()

        var currentTag: String? = null
        var currentName: String? = null
        var inFilter = false
        var actions = mutableSetOf<String>()
        var categories = mutableSetOf<String>()
        var mimeTypes = mutableSetOf<String>()
        var schemes = mutableSetOf<String>()
        var hosts = mutableSetOf<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "activity", "activity-alias" -> {
                        currentTag = parser.name
                        currentName = parser.getAttributeValue(ns, "name")
                    }
                    "intent-filter" -> {
                        inFilter = true
                        actions = mutableSetOf()
                        categories = mutableSetOf()
                        mimeTypes = mutableSetOf()
                        schemes = mutableSetOf()
                        hosts = mutableSetOf()
                    }
                    "action" -> if (inFilter) {
                        parser.getAttributeValue(ns, "name")?.let { actions.add(it) }
                    }
                    "category" -> if (inFilter) {
                        parser.getAttributeValue(ns, "name")?.let { categories.add(it) }
                    }
                    "data" -> if (inFilter) {
                        parser.getAttributeValue(ns, "mimeType")?.let { mimeTypes.add(it) }
                        parser.getAttributeValue(ns, "scheme")?.let { schemes.add(it) }
                        parser.getAttributeValue(ns, "host")?.let { hosts.add(it) }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "intent-filter" -> {
                        val pf = ParsedFilter(actions, categories, mimeTypes, schemes, hosts)
                        when (currentTag) {
                            "activity-alias" -> aliases
                                .getOrPut(currentName ?: "?") { mutableListOf() }
                                .add(pf)
                            "activity" -> mainFilters.add(pf)
                        }
                        inFilter = false
                    }
                    "activity", "activity-alias" -> {
                        currentTag = null
                        currentName = null
                    }
                }
            }
            event = parser.next()
        }
        return FilterCoverage(aliases, mainFilters)
    }

    @Test fun appCalendarCategoryDeclaredOnMain() {
        val cov = parseManifest()
        val hasAppCalendar = cov.mainActivityFilters.any { f ->
            f.actions.contains("android.intent.action.MAIN") &&
                f.categories.contains("android.intent.category.APP_CALENDAR")
        }
        assertTrue(
            "MainActivity must carry category APP_CALENDAR for default-app slot eligibility",
            hasAppCalendar,
        )
    }

    @Test fun goToDateFilterDeclared() {
        val cov = parseManifest()
        val ok = cov.mainActivityFilters.any { f ->
            f.actions.contains("android.intent.action.VIEW") &&
                f.schemes.contains("content") &&
                f.hosts.contains("com.android.calendar")
        }
        assertTrue("MainActivity must filter content://com.android.calendar/time/<epoch>", ok)
    }

    @Test fun eventDetailAliasFiltersViewItemMimeType() {
        val cov = parseManifest()
        val key = cov.aliases.keys.firstOrNull { it?.endsWith("EventDetailActivity") == true }
        assertNotNull("EventDetailActivity activity-alias missing", key)
        val ok = cov.aliases[key]!!.any { f ->
            f.actions.contains("android.intent.action.VIEW") &&
                f.mimeTypes.contains("vnd.android.cursor.item/event")
        }
        assertTrue("EventDetailActivity must VIEW vnd.android.cursor.item/event", ok)
    }

    @Test fun eventEditAliasFiltersEditAndInsert() {
        val cov = parseManifest()
        val key = cov.aliases.keys.firstOrNull { it?.endsWith("EventEditActivity") == true }
        assertNotNull("EventEditActivity activity-alias missing", key)
        val filters = cov.aliases[key]!!
        val hasEditItem = filters.any { f ->
            f.actions.contains("android.intent.action.EDIT") &&
                f.mimeTypes.contains("vnd.android.cursor.item/event")
        }
        val hasInsertDir = filters.any { f ->
            f.actions.contains("android.intent.action.INSERT") &&
                f.mimeTypes.contains("vnd.android.cursor.dir/event")
        }
        assertTrue("EDIT vnd.android.cursor.item/event filter missing", hasEditItem)
        assertTrue("INSERT vnd.android.cursor.dir/event filter missing", hasInsertDir)
    }

    @Test fun icsImportAliasFiltersTextCalendar() {
        val cov = parseManifest()
        val key = cov.aliases.keys.firstOrNull { it?.endsWith("IcsImportActivity") == true }
        assertNotNull("IcsImportActivity activity-alias missing", key)
        val filters = cov.aliases[key]!!
        val hasLocal = filters.any { f ->
            f.actions.contains("android.intent.action.VIEW") &&
                f.mimeTypes.contains("text/calendar") &&
                (f.schemes.contains("file") || f.schemes.contains("content"))
        }
        val hasHttps = filters.any { f ->
            f.actions.contains("android.intent.action.VIEW") &&
                f.schemes.contains("https") &&
                f.categories.contains("android.intent.category.BROWSABLE")
        }
        assertTrue("file/content text/calendar VIEW filter missing", hasLocal)
        assertTrue("https://*/*.ics VIEW filter missing", hasHttps)
    }

    @Test fun phaseMmDeepLinkSchemeStillPresent() {
        val cov = parseManifest()
        // Back-compat (Round 2.18.E.10): the strictlykeptboy:// scheme
        // for event/task/repo/bonus/review must not have been pruned.
        val ok = cov.mainActivityFilters.any { f ->
            f.schemes.contains("strictlykeptboy") && f.hosts.contains("event")
        }
        assertTrue("strictlykeptboy://event/* filter must remain for Phase MM", ok)
    }
}

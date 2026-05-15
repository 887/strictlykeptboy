package com.eight87.strictlykeptboy.system

import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CalendarKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Round 2.18.A.5 + A.10 + A.11 + A.15 — verify
 *  - synthetic repoId / `kind = External` / accessLevel piped through
 *  - color seed comes from `CALENDAR_COLOR`
 *  - prefs overlay overrides activeToggle + priority + supersedes
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SystemCalendarsRepositoryTest {

    private lateinit var ctx: Context
    private lateinit var prefs: SystemCalendarPrefsStore
    private lateinit var repo: SystemCalendarsRepository
    private lateinit var scope: CoroutineScope

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        Robolectric.buildContentProvider(FakeCalendarProvider::class.java).create(info).get()
        FakeCalendarProvider.rows.clear()
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
        val sharedPrefs = ctx.getSharedPreferences("sys_cal_repo_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        prefs = SystemCalendarPrefsStore.openForTest(sharedPrefs)
        // Round 2.18.B.2 — global toggle is off by default; flip on so
        // these A-phase tests still see the synthesized CalendarMeta.
        prefs.setShowSystemCalendars(true)
        scope = CoroutineScope(SupervisorJob())
        repo = SystemCalendarsRepository(
            bridge = CalendarContractBridge(ctx),
            prefs = prefs,
            scope = scope,
        )
    }

    @After fun tearDown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    @Test fun synthesizesMetaPerSystemCalendar() {
        FakeCalendarProvider.rows += FakeRow(
            id = 7L, accountName = "alice@gmail.com", accountType = "com.google",
            name = "Work", color = 0xFFCAFE99.toInt(), accessLevel = 700,
            ownerAccount = "alice@gmail.com", isPrimary = true,
            visible = true, syncEvents = true,
        )
        val metas = repo.readOnce()
        assertEquals(1, metas.size)
        val meta = metas.single()
        assertEquals(CalendarKind.External, meta.kind)
        assertEquals("system/com.google/alice@gmail.com", meta.repo.id)
        assertEquals("7", meta.ref.id)
        assertEquals(true, meta.activeToggle)
        assertEquals(700, meta.externalAccessLevel)
        assertNotNull(meta.colorSeed)
        assertEquals(0xFFCAFE99.toInt(), meta.colorSeed)
    }

    @Test fun primaryCalendarHasHigherDefaultPriority() {
        FakeCalendarProvider.rows += primaryRow(7L)
        FakeCalendarProvider.rows += secondaryRow(8L)
        val metas = repo.readOnce().associateBy { it.ref.id }
        val pPrim = metas.getValue("7").priority
        val pSec = metas.getValue("8").priority
        assert(pPrim > pSec) { "expected primary priority > secondary, got $pPrim vs $pSec" }
    }

    @Test fun prefsOverrideAppliesPriorityActiveToggleAndSupersedes() {
        FakeCalendarProvider.rows += primaryRow(7L)
        prefs.set(
            "com.google", "alice@gmail.com", 7L,
            SystemCalendarOverride(
                activeToggle = false,
                priority = 900,
                supersedes = listOf("8", "9"),
            ),
        )
        val meta = repo.readOnce().single()
        assertEquals(900, meta.priority)
        assertEquals(false, meta.activeToggle)
        assertEquals(listOf("8", "9"), meta.supersedes.map { it.id })
    }

    @Test fun zeroColorIntBecomesNullSeed() {
        FakeCalendarProvider.rows += FakeRow(
            id = 7L, accountName = "a", accountType = "com.google",
            name = "Plain", color = 0, accessLevel = 700,
            ownerAccount = null, isPrimary = false, visible = true, syncEvents = true,
        )
        val meta = repo.readOnce().single()
        assertNull(meta.colorSeed)
    }

    private fun primaryRow(id: Long) = FakeRow(
        id = id, accountName = "alice@gmail.com", accountType = "com.google",
        name = "Primary", color = 0xFF112233.toInt(), accessLevel = 700,
        ownerAccount = "alice@gmail.com", isPrimary = true, visible = true, syncEvents = true,
    )
    private fun secondaryRow(id: Long) = FakeRow(
        id = id, accountName = "alice@gmail.com", accountType = "com.google",
        name = "Secondary", color = 0xFF445566.toInt(), accessLevel = 700,
        ownerAccount = "alice@gmail.com", isPrimary = false, visible = true, syncEvents = true,
    )
}

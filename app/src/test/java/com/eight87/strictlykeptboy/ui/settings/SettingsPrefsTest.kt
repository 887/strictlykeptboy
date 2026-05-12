package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPrefsTest {

    private fun prefs(name: String) = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(name, Context.MODE_PRIVATE)
        .also { it.edit().clear().apply() }

    @Test fun `sync settings round-trip`() {
        val p = SyncSettingsPrefs.openForTest(prefs("sync_s"))
        assertEquals(60, p.state.value.defaultIntervalMinutes)
        p.setInterval(15)
        p.setWifiOnly(false)
        p.setPushPolicy(PushPolicy.Manual)
        p.setConflictPolicy(ConflictPolicy.ManualOnly)
        val s = p.state.value
        assertEquals(15, s.defaultIntervalMinutes)
        assertFalse(s.wifiOnly)
        assertEquals(PushPolicy.Manual, s.pushPolicy)
        assertEquals(ConflictPolicy.ManualOnly, s.conflictPolicy)
    }

    @Test fun `calendar visibility move up and down preserves identity`() {
        val p = CalendarVisibilityPrefs.openForTest(prefs("vis_s"), ListKind.Calendars)
        p.setEntries(
            listOf(
                VisibilityEntry("a", "Alpha"),
                VisibilityEntry("b", "Beta"),
                VisibilityEntry("c", "Gamma"),
            ),
        )
        p.moveDown("a")
        assertEquals(listOf("b", "a", "c"), p.state.value.ordered.map { it.id })
        p.moveUp("c")
        assertEquals(listOf("b", "c", "a"), p.state.value.ordered.map { it.id })
        p.setVisible("c", false)
        assertFalse(p.state.value.ordered.first { it.id == "c" }.visible)
    }

    @Test fun `identity prefs reset to defaults clears state`() {
        val p = IdentityPrefs.openForTest(prefs("id_s"))
        p.update { it.copy(praise = "darling", honorific = "Sir") }
        assertEquals("darling", p.state.value.praise)
        p.resetToDefaults()
        assertEquals("good boy", p.state.value.praise)
        assertEquals("", p.state.value.honorific)
    }

    @Test fun `mode prefs persona pickers and cadence`() {
        val p = ModePrefs.openForTest(prefs("mode_s"))
        assertEquals(AppMode.Free, p.state.value.mode)
        p.setMode(AppMode.StrictlyKept)
        p.setCadence(DomCadence.Weekly)
        p.setPersonaId("playful-tease")
        val s = p.state.value
        assertEquals(AppMode.StrictlyKept, s.mode)
        assertEquals(DomCadence.Weekly, s.cadence)
        assertEquals("playful-tease", s.personaId)
    }

    @Test fun `mode confirmation phrase is stable`() {
        // D.86 wording locked — any change is breaking, surface here.
        assertEquals("yes I want to leave", ModePrefs.FREE_CONFIRMATION_PHRASE)
    }

    @Test fun `mode prefs custom persona round-trips`() {
        val p = ModePrefs.openForTest(prefs("mode_s2"))
        p.addCustomPersona(DomPersona.Custom(id = "mine", label = "Mine", prompt = "hi"))
        assertTrue(p.availablePersonas().any { it.id == "mine" })
    }
}

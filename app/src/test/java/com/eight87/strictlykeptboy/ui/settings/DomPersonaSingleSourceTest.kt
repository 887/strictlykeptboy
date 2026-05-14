package com.eight87.strictlykeptboy.ui.settings

import com.eight87.strictlykeptboy.store.DomPersonaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 2.1.K.6 — `DomPersonaStore` is the single source of truth for
 * persona definitions. ModeCategory + DomPersonaPickerSheet read from
 * `DomPersonaStore.list(home)`; there is no parallel data source.
 */
class DomPersonaSingleSourceTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun list_returns_six_builtins_when_no_custom() {
        val home = tmp.newFolder("home").absolutePath
        DomPersonaStore.ensureBuiltins(home)
        val list = DomPersonaStore.list(home)
        assertEquals(6, list.size)
        // All builtins present.
        DomPersonaStore.BUILTINS.forEach { b ->
            assertNotNull(list.firstOrNull { it.id == b.id })
        }
    }

    @Test fun write_custom_appears_in_list() {
        val home = tmp.newFolder("home").absolutePath
        DomPersonaStore.ensureBuiltins(home)
        DomPersonaStore.writeCustom(home, "you are a loving partner")
        val list = DomPersonaStore.list(home)
        val custom = list.firstOrNull { it.id == DomPersonaStore.CUSTOM_ID }
        assertNotNull(custom)
        assertEquals("you are a loving partner", custom?.prompt)
        // ModePrefs only stores personaId; the resolution path is via the
        // store itself — there's no parallel DomPersona enum in ModePrefs.
        assertTrue(list.size == 7)
    }
}

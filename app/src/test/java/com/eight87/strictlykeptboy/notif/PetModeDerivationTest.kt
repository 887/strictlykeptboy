package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.store.ModeTomlData
import com.eight87.strictlykeptboy.store.RepoMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 2.1.M.5 — Pet Mode derivation from `mode.toml` state.
 *
 * Mirrors the table in [PetModeDerivation]: strictly-kept + persona
 * → SelfPet; strictly-kept + write-back-target (no persona) →
 * PartneredPet; self-keep → SelfKeep; free → None.
 */
class PetModeDerivationTest {

    @Test fun `strictly-kept with persona derives SelfPet`() {
        val data = ModeTomlData(
            mode = RepoMode.StrictlyKept,
            domPersona = "stern-but-fair",
        )
        assertEquals(PetMode.SelfPet, PetModeDerivation.derive(data))
    }

    @Test fun `strictly-kept with write-back-target and no persona derives PartneredPet`() {
        val data = ModeTomlData(
            mode = RepoMode.StrictlyKept,
            writeBackTarget = "https://example.test/share/abc",
            domPersona = null,
        )
        assertEquals(PetMode.PartneredPet, PetModeDerivation.derive(data))
    }

    @Test fun `self-keep derives SelfKeep`() {
        val data = ModeTomlData(mode = RepoMode.SelfKeep)
        assertEquals(PetMode.SelfKeep, PetModeDerivation.derive(data))
    }

    @Test fun `free derives None`() {
        val data = ModeTomlData(mode = RepoMode.Free)
        assertEquals(PetMode.None, PetModeDerivation.derive(data))
    }

    @Test fun `strictly-kept with neither persona nor target falls back to SelfPet`() {
        // Same fallback as ModeState.keptBy: when StrictlyKept is set but
        // neither field is populated yet (mid-wizard scaffold), treat as
        // AI-dom for register purposes. The wizard always lands one or
        // the other before completing.
        val data = ModeTomlData(mode = RepoMode.StrictlyKept)
        assertEquals(PetMode.SelfPet, PetModeDerivation.derive(data))
    }

    @Test fun `deriveFor with null repo root returns None`() {
        assertEquals(PetMode.None, PetModeDerivation.deriveFor(null))
    }
}

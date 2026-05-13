package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.1.M.5 — Pet Mode notification register copy.
 *
 * Each [PetMode] produces a phrasing the user picked-into via the
 * wizard's Pet Mode framing. `private = true` short-circuits the
 * Pet-Mode template before any register applies.
 */
class NotifPetCopyTest {

    private val identity = IdentityTomlData.LockedDefaults.copy(
        praiseTerm = "good boy",
        honorificForDom = "Sir",
    )

    @Test fun `SelfPet produces self-pet phrasing`() {
        val body = IdentityNotifBody.bodyForPet(
            identity = identity,
            title = "4pm",
            privateEvent = false,
            petMode = PetMode.SelfPet,
        )
        assertEquals("good boy, your 4pm walk, good boy", body)
    }

    @Test fun `PartneredPet foregrounds the honorific`() {
        val body = IdentityNotifBody.bodyForPet(
            identity = identity,
            title = "4pm",
            privateEvent = false,
            petMode = PetMode.PartneredPet,
        )
        assertEquals("good boy, your 4pm — Sir wants you ready", body)
    }

    @Test fun `SelfKeep is plain-but-encouraging`() {
        val body = IdentityNotifBody.bodyForPet(
            identity = identity,
            title = "4pm",
            privateEvent = false,
            petMode = PetMode.SelfKeep,
        )
        assertEquals("good boy, your 4pm — stay on track", body)
    }

    @Test fun `None falls through to the neutral template`() {
        val body = IdentityNotifBody.bodyForPet(
            identity = identity,
            title = "4pm",
            privateEvent = false,
            petMode = PetMode.None,
        )
        // Neutral template from bodyFor.
        assertEquals("good boy, your 4pm, Sir", body)
    }

    @Test fun `private event short-circuits regardless of pet mode`() {
        for (pm in PetMode.values()) {
            val body = IdentityNotifBody.bodyForPet(
                identity = identity,
                title = "4pm",
                privateEvent = true,
                petMode = pm,
            )
            assertEquals(
                "private flag must win for petMode=$pm",
                IdentityNotifBody.GENERIC_BODY,
                body,
            )
        }
    }

    @Test fun `null identity yields the generic body`() {
        val body = IdentityNotifBody.bodyForPet(
            identity = null,
            title = "4pm",
            privateEvent = false,
            petMode = PetMode.SelfPet,
        )
        assertTrue(body == IdentityNotifBody.GENERIC_BODY)
    }
}

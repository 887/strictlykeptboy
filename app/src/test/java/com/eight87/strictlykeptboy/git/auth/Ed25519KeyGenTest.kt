package com.eight87.strictlykeptboy.git.auth

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B.4 — ed25519 keygen round-trip. Validates:
 * - public-key line is `ssh-ed25519 <base64> <comment>` shape
 * - private-key PEM round-trips through parsePrivatePem
 * - generated key signs + verifies (i.e. it's actually valid ed25519)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class Ed25519KeyGenTest {

    @Test fun generatesProperOpenSshShape() {
        val kp = Ed25519KeyGen.generate("strictlykeptboy-test")

        // Public-key line
        val parts = kp.publicOpenSsh.split(" ")
        assertEquals(3, parts.size)
        assertEquals("ssh-ed25519", parts[0])
        assertTrue("base64 portion is non-empty", parts[1].length > 30)
        assertEquals("strictlykeptboy-test", parts[2])

        // Private-key PEM
        assertTrue(kp.privateOpenSshPem.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))
        assertTrue(kp.privateOpenSshPem.trimEnd().endsWith("-----END OPENSSH PRIVATE KEY-----"))
    }

    @Test fun privateKeyPemRoundTrips() {
        val kp = Ed25519KeyGen.generate("test-comment")
        val parsed = Ed25519KeyGen.parsePrivatePem(kp.privateOpenSshPem)
        assertNotNull("PEM round-trips", parsed)
        assertTrue(parsed is Ed25519PrivateKeyParameters)
    }

    @Test fun generatedKeySignsAndVerifies() {
        val kp = Ed25519KeyGen.generate("sign-test")
        val priv = Ed25519KeyGen.parsePrivatePem(kp.privateOpenSshPem)!!
        val pub: Ed25519PublicKeyParameters = priv.generatePublicKey()

        val message = "the bat is real ;3".toByteArray()
        val signer = Ed25519Signer().apply { init(true, priv); update(message, 0, message.size) }
        val sig = signer.generateSignature()

        val verifier = Ed25519Signer().apply { init(false, pub); update(message, 0, message.size) }
        assertTrue("signature verifies", verifier.verifySignature(sig))
    }
}

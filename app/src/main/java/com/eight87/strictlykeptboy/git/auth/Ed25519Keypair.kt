package com.eight87.strictlykeptboy.git.auth

import android.util.Base64
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import java.security.SecureRandom

/**
 * Ed25519 keypair encoded for OpenSSH consumption — what `git@github.com`,
 * Forgejo's SSH endpoint, and similar deploy keys expect.
 *
 * `publicOpenSsh` is one line: `ssh-ed25519 <base64> <comment>`.
 * `privateOpenSshPem` is the OpenSSH-PEM-wrapped private key (the
 * `-----BEGIN OPENSSH PRIVATE KEY-----` form, NOT PKCS#8).
 *
 * Per Phase B.4 / SE-C.1.
 */
data class Ed25519Keypair(
    val publicOpenSsh: String,
    val privateOpenSshPem: String,
)

object Ed25519KeyGen {

    /**
     * Generate a fresh ed25519 keypair via BouncyCastle directly (the
     * BC asymmetric API). We don't go through `KeyPairGenerator.getInstance`
     * because Android 26 doesn't expose ed25519 via JCE until API 33+, while
     * BC's classes are available everywhere we run.
     *
     * [comment] is appended to the public-key line (e.g. `strictlykeptboy-<repoId>-<remoteName>`)
     * — providers like GitHub display this label on the key-management page.
     */
    fun generate(comment: String, rng: SecureRandom = SecureRandom()): Ed25519Keypair {
        val gen = Ed25519KeyPairGenerator().apply {
            init(Ed25519KeyGenerationParameters(rng))
        }
        val pair = gen.generateKeyPair()
        val priv = pair.private as Ed25519PrivateKeyParameters
        val pub = pair.public as Ed25519PublicKeyParameters
        return Ed25519Keypair(
            publicOpenSsh = encodePublicLine(pub, comment),
            privateOpenSshPem = encodePrivatePem(priv),
        )
    }

    private fun encodePublicLine(pub: Ed25519PublicKeyParameters, comment: String): String {
        val raw = OpenSSHPublicKeyUtil.encodePublicKey(pub)
        val b64 = Base64.encodeToString(raw, Base64.NO_WRAP)
        return "ssh-ed25519 $b64 $comment"
    }

    private fun encodePrivatePem(priv: Ed25519PrivateKeyParameters): String {
        val raw = OpenSSHPrivateKeyUtil.encodePrivateKey(priv)
        val b64 = Base64.encodeToString(raw, Base64.NO_WRAP)
        // OpenSSH PEM wrapping. 70-char lines per the RFC convention.
        val wrapped = b64.chunked(70).joinToString("\n")
        return buildString {
            appendLine("-----BEGIN OPENSSH PRIVATE KEY-----")
            appendLine(wrapped)
            appendLine("-----END OPENSSH PRIVATE KEY-----")
        }
    }

    /**
     * Parse a previously-encoded OpenSSH private-key PEM back to the BC param
     * object. Used by the SshSessionFactory at transport time. Returns null on
     * malformed input rather than throwing — the caller surfaces a re-auth
     * banner via [com.eight87.strictlykeptboy.git.SyncError.Auth].
     */
    fun parsePrivatePem(pem: String): Ed25519PrivateKeyParameters? {
        val body = pem
            .lines()
            .dropWhile { !it.startsWith("-----BEGIN") }
            .drop(1)
            .takeWhile { !it.startsWith("-----END") }
            .joinToString("")
        return runCatching {
            val raw = Base64.decode(body, Base64.NO_WRAP)
            OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(raw) as Ed25519PrivateKeyParameters
        }.getOrNull()
    }

    @Suppress("unused") // kept for ASN.1 ed25519 tooling (PKCS#8 export, deferred)
    private val ED25519_OID = ASN1ObjectIdentifier("1.3.101.112")

    @Suppress("unused")
    private fun typeOf(spki: SubjectPublicKeyInfo) = spki.algorithm.algorithm
    @Suppress("unused")
    private fun typeOf(pki: PrivateKeyInfo) = pki.privateKeyAlgorithm.algorithm
}

package com.eight87.strictlykeptboy.git.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.eight87.strictlykeptboy.git.RemoteName

/**
 * Per-`(repoId, remoteName)` credential storage. Backed by
 * `EncryptedSharedPreferences` (`secrets_v1.xml`) with AES256_GCM via a
 * Keystore-anchored MasterKey.
 *
 * Key naming per Phase B.4 / SE-C "Storage" / Phase ZZ.C:
 *
 * - `ssh.priv.<repoId>.<remoteName>` — OpenSSH-PEM private key
 * - `ssh.pub.<repoId>.<remoteName>` — `ssh-ed25519 ... comment` one-liner
 * - `oauth.token.<repoId>.<remoteName>` — bearer access token
 * - `oauth.refresh.<repoId>.<remoteName>` — refresh token (if provider issues)
 * - `oauth.expiry.<repoId>.<remoteName>` — epoch millis as decimal string
 * - `pat.token.<repoId>.<remoteName>` — manual PAT
 * - `pat.username.<repoId>.<remoteName>` — username paired with the PAT
 *
 * The store does NOT cache values in memory — every read hits the
 * EncryptedSharedPreferences layer. Reads are cheap (Keystore unwrap is amortized
 * by the AndroidX wrapper); we don't want stale state if a sibling process
 * (a future Wear watch face, the `skb` CLI peer, etc.) writes.
 */
class SecretsStore internal constructor(private val prefs: SharedPreferences) {

    // ---- SSH ----------------------------------------------------------------

    fun storeSshKeypair(repoId: String, remote: RemoteName, kp: Ed25519Keypair) {
        prefs.edit().apply {
            putString(sshPriv(repoId, remote), kp.privateOpenSshPem)
            putString(sshPub(repoId, remote), kp.publicOpenSsh)
            apply()
        }
    }

    fun getSshKeypair(repoId: String, remote: RemoteName): Ed25519Keypair? {
        val priv = prefs.getString(sshPriv(repoId, remote), null) ?: return null
        val pub = prefs.getString(sshPub(repoId, remote), null) ?: return null
        return Ed25519Keypair(publicOpenSsh = pub, privateOpenSshPem = priv)
    }

    fun getSshPublic(repoId: String, remote: RemoteName): String? =
        prefs.getString(sshPub(repoId, remote), null)

    // ---- OAuth --------------------------------------------------------------

    fun storeOAuthToken(repoId: String, remote: RemoteName, token: OAuthToken) {
        prefs.edit().apply {
            putString(oauthToken(repoId, remote), token.accessToken)
            if (token.refreshToken != null) {
                putString(oauthRefresh(repoId, remote), token.refreshToken)
            } else {
                remove(oauthRefresh(repoId, remote))
            }
            if (token.expiryEpochMs != null) {
                putString(oauthExpiry(repoId, remote), token.expiryEpochMs.toString())
            } else {
                remove(oauthExpiry(repoId, remote))
            }
            apply()
        }
    }

    fun getOAuthToken(repoId: String, remote: RemoteName): OAuthToken? {
        val token = prefs.getString(oauthToken(repoId, remote), null) ?: return null
        return OAuthToken(
            accessToken = token,
            refreshToken = prefs.getString(oauthRefresh(repoId, remote), null),
            expiryEpochMs = prefs.getString(oauthExpiry(repoId, remote), null)?.toLongOrNull(),
        )
    }

    // ---- PAT ----------------------------------------------------------------

    fun storePat(repoId: String, remote: RemoteName, credential: PatCredential) {
        prefs.edit().apply {
            putString(patUsername(repoId, remote), credential.username)
            putString(patToken(repoId, remote), credential.token)
            apply()
        }
    }

    fun getPat(repoId: String, remote: RemoteName): PatCredential? {
        val username = prefs.getString(patUsername(repoId, remote), null) ?: return null
        val token = prefs.getString(patToken(repoId, remote), null) ?: return null
        return PatCredential(username, token)
    }

    // ---- bulk clears --------------------------------------------------------

    /** Wipe every credential for one repo (across all its remotes). */
    fun clearForRepo(repoId: String) {
        val suffix = ".$repoId."
        val keysToClear = prefs.all.keys.filter { it.contains(suffix) }
        prefs.edit().apply {
            keysToClear.forEach { remove(it) }
            apply()
        }
    }

    /** Wipe every credential for one `(repoId, remoteName)` pair. */
    fun clearForRemote(repoId: String, remote: RemoteName) {
        val suffix = ".$repoId.${remote.value}"
        val keysToClear = prefs.all.keys.filter { it.endsWith(suffix) }
        prefs.edit().apply {
            keysToClear.forEach { remove(it) }
            apply()
        }
    }

    // ---- key naming ---------------------------------------------------------

    private fun sshPriv(r: String, n: RemoteName) = "ssh.priv.$r.${n.value}"
    private fun sshPub(r: String, n: RemoteName) = "ssh.pub.$r.${n.value}"
    private fun oauthToken(r: String, n: RemoteName) = "oauth.token.$r.${n.value}"
    private fun oauthRefresh(r: String, n: RemoteName) = "oauth.refresh.$r.${n.value}"
    private fun oauthExpiry(r: String, n: RemoteName) = "oauth.expiry.$r.${n.value}"
    private fun patUsername(r: String, n: RemoteName) = "pat.username.$r.${n.value}"
    private fun patToken(r: String, n: RemoteName) = "pat.token.$r.${n.value}"

    companion object {
        private const val PREFS_FILE = "secrets_v1"

        fun open(context: Context): SecretsStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SecretsStore(prefs)
        }

        /** Test-only — accepts a plain SharedPreferences for Robolectric. */
        internal fun openForTest(prefs: SharedPreferences): SecretsStore = SecretsStore(prefs)
    }
}

data class OAuthToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiryEpochMs: Long? = null,
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        expiryEpochMs != null && nowEpochMs >= expiryEpochMs
}

data class PatCredential(val username: String, val token: String)

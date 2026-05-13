package com.eight87.strictlykeptboy.caldav.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Phase Y.3 — encrypted storage for CalDAV credentials. Lives in the
 * same `EncryptedSharedPreferences` file as git secrets (`secrets_v1`)
 * but uses a disjoint `caldav.*` key prefix so the two surfaces can't
 * collide. Keys are scoped by `credentialBindingId` (typically the
 * `mirrorId` from [com.eight87.strictlykeptboy.caldav.CalDavMirror]).
 *
 * Single-Responsibility: this class only marshals credentials in/out of
 * SharedPreferences. Provider-specific behaviour (OAuth refresh,
 * app-password help text) lives in the wizard / OAuth client.
 */
class CalDavSecretsStore internal constructor(private val prefs: SharedPreferences) {

    fun storeBasic(bindingId: String, cred: CalDavCredential.BasicAuth) {
        prefs.edit().apply {
            putString(kKind(bindingId), KIND_BASIC)
            putString(kUser(bindingId), cred.username)
            putString(kPass(bindingId), cred.password)
            apply()
        }
    }

    fun storeAppPassword(bindingId: String, cred: CalDavCredential.AppPassword) {
        prefs.edit().apply {
            putString(kKind(bindingId), KIND_APP_PASSWORD)
            putString(kUser(bindingId), cred.appleId)
            putString(kPass(bindingId), cred.appPassword)
            apply()
        }
    }

    fun storeBearer(bindingId: String, cred: CalDavCredential.Bearer) {
        prefs.edit().apply {
            putString(kKind(bindingId), KIND_BEARER)
            putString(kAccess(bindingId), cred.accessToken)
            if (cred.refreshToken != null) putString(kRefresh(bindingId), cred.refreshToken)
            else remove(kRefresh(bindingId))
            if (cred.expiryEpochMs != null) putString(kExpiry(bindingId), cred.expiryEpochMs.toString())
            else remove(kExpiry(bindingId))
            apply()
        }
    }

    fun get(bindingId: String): CalDavCredential? = when (prefs.getString(kKind(bindingId), null)) {
        KIND_BASIC -> {
            val u = prefs.getString(kUser(bindingId), null)
            val p = prefs.getString(kPass(bindingId), null)
            if (u != null && p != null) CalDavCredential.BasicAuth(u, p) else null
        }
        KIND_APP_PASSWORD -> {
            val u = prefs.getString(kUser(bindingId), null)
            val p = prefs.getString(kPass(bindingId), null)
            if (u != null && p != null) CalDavCredential.AppPassword(u, p) else null
        }
        KIND_BEARER -> {
            val a = prefs.getString(kAccess(bindingId), null) ?: return null
            CalDavCredential.Bearer(
                accessToken = a,
                refreshToken = prefs.getString(kRefresh(bindingId), null),
                expiryEpochMs = prefs.getString(kExpiry(bindingId), null)?.toLongOrNull(),
            )
        }
        else -> null
    }

    fun clear(bindingId: String) {
        prefs.edit().apply {
            remove(kKind(bindingId))
            remove(kUser(bindingId))
            remove(kPass(bindingId))
            remove(kAccess(bindingId))
            remove(kRefresh(bindingId))
            remove(kExpiry(bindingId))
            apply()
        }
    }

    private fun kKind(id: String) = "caldav.kind.$id"
    private fun kUser(id: String) = "caldav.user.$id"
    private fun kPass(id: String) = "caldav.pass.$id"
    private fun kAccess(id: String) = "caldav.access.$id"
    private fun kRefresh(id: String) = "caldav.refresh.$id"
    private fun kExpiry(id: String) = "caldav.expiry.$id"

    companion object {
        private const val PREFS_FILE = "secrets_v1"
        private const val KIND_BASIC = "basic"
        private const val KIND_APP_PASSWORD = "app_password"
        private const val KIND_BEARER = "bearer"

        fun open(context: Context): CalDavSecretsStore {
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
            return CalDavSecretsStore(prefs)
        }

        /** Test-only entry point — accepts a plain SharedPreferences for Robolectric. */
        fun openForTest(prefs: SharedPreferences): CalDavSecretsStore = CalDavSecretsStore(prefs)
    }
}

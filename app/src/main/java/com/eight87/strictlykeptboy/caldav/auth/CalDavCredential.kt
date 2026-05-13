package com.eight87.strictlykeptboy.caldav.auth

/**
 * Phase Y.3 — CalDAV credential kinds. Persisted in
 * `EncryptedSharedPreferences` (the same store as git credentials, with
 * a distinct `caldav.*` key prefix per `CalDavSecretsStore`).
 *
 * - [BasicAuth] — Nextcloud / Radicale / custom self-hosts.
 * - [AppPassword] — Apple iCloud (app-specific password from
 *   appleid.apple.com); structurally identical to BasicAuth but kept
 *   separate so the UI can render the correct help text and so we never
 *   prompt a user for an Apple-ID main password.
 * - [Bearer] — Google / Microsoft 365 OAuth Device Flow access token,
 *   optionally with a refresh token + expiry epoch.
 */
sealed interface CalDavCredential {
    data class BasicAuth(val username: String, val password: String) : CalDavCredential
    data class AppPassword(val appleId: String, val appPassword: String) : CalDavCredential
    data class Bearer(
        val accessToken: String,
        val refreshToken: String? = null,
        val expiryEpochMs: Long? = null,
    ) : CalDavCredential {
        fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
            expiryEpochMs != null && nowEpochMs >= expiryEpochMs
    }
}

/** Produce the `Authorization` header value for this credential. */
fun CalDavCredential.authorizationHeader(): String = when (this) {
    is CalDavCredential.BasicAuth -> basic(username, password)
    is CalDavCredential.AppPassword -> basic(appleId, appPassword)
    is CalDavCredential.Bearer -> "Bearer $accessToken"
}

private fun basic(user: String, pass: String): String {
    val raw = "$user:$pass".toByteArray(Charsets.UTF_8)
    return "Basic " + java.util.Base64.getEncoder().encodeToString(raw)
}

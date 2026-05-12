package com.eight87.strictlykeptboy.git.auth

/**
 * GitHub OAuth Device Flow configuration. Phase B.5 / SE-D.
 *
 * The `clientId` is registered as a GitHub App during Phase W release
 * engineering; for v1 it MUST be provided at build time via
 * `BuildConfig.GITHUB_OAUTH_CLIENT_ID` (this constant pulls from there
 * so debug builds can use a developer's own App and release builds use
 * the published one). It is NOT a secret — Device Flow does not use
 * client_secret. The Client ID is public.
 *
 * Scopes:
 * - `repo` — git push/pull over HTTPS to private + public repos
 * - `admin:public_key` — upload SSH key on user's behalf (Phase SE-C.7)
 *
 * If the user only configures HTTPS+OAuth and never SSH, the
 * `admin:public_key` scope is unused but doesn't cost anything to request.
 */
object GitHubAuth {

    const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    const val TOKEN_URL = "https://github.com/login/oauth/access_token"

    /** Username to pair with the access token for git HTTPS basic-auth. */
    const val GIT_USERNAME = "oauth2"

    val DEFAULT_SCOPES = listOf("repo", "admin:public_key")

    fun config(clientId: String, scopes: List<String> = DEFAULT_SCOPES): OAuthProviderConfig =
        OAuthProviderConfig(
            deviceCodeUrl = DEVICE_CODE_URL,
            tokenUrl = TOKEN_URL,
            clientId = clientId,
            defaultScopes = scopes,
        )
}

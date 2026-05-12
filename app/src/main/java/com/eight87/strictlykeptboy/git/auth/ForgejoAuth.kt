package com.eight87.strictlykeptboy.git.auth

/**
 * Forgejo (and Gitea) OAuth2 Device Flow configuration. Phase B.6 / SE-D.
 *
 * Forgejo / Gitea instances are self-hosted, so the user provides the base URL
 * at config time (e.g. `https://codeberg.org`, `https://forgejo.example.com`).
 * The Device Flow endpoints are at `/login/oauth/...` on the instance — same
 * path layout as GitHub for the most part, but each instance issues its own
 * OAuth applications.
 *
 * Scopes (Forgejo's vocabulary differs from GitHub's):
 * - `read:repository`, `write:repository` — git access
 * - `write:user` — manage SSH public keys on user's behalf
 *
 * Setup flow: the user enters the instance URL + the OAuth client_id they
 * configured on their instance's Settings → Applications → OAuth2 page. We
 * surface a copy-pasteable instructions screen in repo-add (Phase I) that
 * walks them through creating the OAuth app — Forgejo doesn't have a
 * GitHub-style central Apps registry, so first-time config is on them.
 */
object ForgejoAuth {

    /** Username to pair with the access token for git HTTPS basic-auth. */
    const val GIT_USERNAME = "oauth2"

    val DEFAULT_SCOPES = listOf("read:repository", "write:repository", "write:user")

    /**
     * Build a config for the given Forgejo instance.
     *
     * @param baseUrl the instance root, e.g. `https://codeberg.org` (no trailing slash)
     * @param clientId the OAuth2 application's client_id from the instance's
     *                 Settings → Applications page
     */
    fun config(
        baseUrl: String,
        clientId: String,
        scopes: List<String> = DEFAULT_SCOPES,
    ): OAuthProviderConfig {
        val trimmed = baseUrl.trimEnd('/')
        return OAuthProviderConfig(
            deviceCodeUrl = "$trimmed/login/oauth/device/code",
            tokenUrl = "$trimmed/login/oauth/access_token",
            clientId = clientId,
            defaultScopes = scopes,
        )
    }
}

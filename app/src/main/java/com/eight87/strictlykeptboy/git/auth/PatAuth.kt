package com.eight87.strictlykeptboy.git.auth

/**
 * Manual Personal Access Token entry path. Phase B.7 / SE-D.
 *
 * The only difference vs. the OAuth Device Flow path is the user pastes the
 * token themselves. Same `(repoId, remoteName)` keying in [SecretsStore].
 * Same `UsernamePasswordCredentialsProvider` shape at git transport time.
 *
 * Usage-pattern conventions per provider (for the UI hint text):
 *
 * | Provider | username | token |
 * |---|---|---|
 * | GitHub PAT (classic + fine-grained) | `oauth2` (or PAT username) | the PAT |
 * | GitLab PAT | the username | the PAT |
 * | Forgejo PAT | the username | the PAT |
 * | Codeberg PAT | the username | the PAT |
 * | Bitbucket app-password | the username | the app password |
 *
 * The repo-add UI (Phase I) lets the user override the username field; for
 * GitHub the default is `oauth2`, for everything else it defaults to the
 * empty string and prompts.
 */
object PatAuth {

    enum class Provider(val defaultUsername: String, val docsUrl: String) {
        GitHub(
            defaultUsername = "oauth2",
            docsUrl = "https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens",
        ),
        GitLab(
            defaultUsername = "",
            docsUrl = "https://docs.gitlab.com/user/profile/personal_access_tokens.html",
        ),
        Forgejo(
            defaultUsername = "",
            docsUrl = "https://forgejo.org/docs/latest/user/oauth2-provider/",
        ),
        Codeberg(
            defaultUsername = "",
            docsUrl = "https://docs.codeberg.org/advanced/access-token/",
        ),
        Bitbucket(
            defaultUsername = "",
            docsUrl = "https://support.atlassian.com/bitbucket-cloud/docs/app-passwords/",
        ),
        Other(defaultUsername = "", docsUrl = ""),
    }

    /**
     * Validate a freshly-entered PAT. Used by the repo-add UI to surface
     * obvious paste errors immediately. We do NOT make a network call here —
     * the real validation happens on first fetch.
     */
    fun validate(credential: PatCredential): ValidationResult = when {
        credential.token.isBlank() -> ValidationResult.Invalid("token cannot be empty")
        credential.token.any { it.isWhitespace() } -> ValidationResult.Invalid("token contains whitespace — paste error?")
        credential.token.length < 16 -> ValidationResult.Warning("token is unusually short for a PAT")
        else -> ValidationResult.Ok
    }

    sealed interface ValidationResult {
        object Ok : ValidationResult
        data class Warning(val message: String) : ValidationResult
        data class Invalid(val message: String) : ValidationResult
    }
}

package com.eight87.strictlykeptboy.system

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.content.Context
import android.os.Bundle

/**
 * Round 2.18.G.1 — `AbstractAccountAuthenticator` for skb's local-only
 * sync-adapter accounts.
 *
 * **No real auth backend.** Accounts of type [SKB_ACCOUNT_TYPE] are
 * local-only handles that exist purely to satisfy the Android sync-adapter
 * contract (a `SyncAdapter` is keyed on `(accountType, contentAuthority)`).
 * One account per skb repo; account name = `<repoId>@local`. The `@local`
 * suffix nudges AccountManager away from prompting the user for
 * credentials (Android won't try to bind a credential picker to an account
 * whose authenticator returns safe defaults).
 *
 * Every method here returns a defensible empty / safe result rather than
 * launching any UI — Phase G doesn't add credentials to anything. The
 * sync-adapter contract is satisfied by the bare existence of the
 * `Account` row in [android.accounts.AccountManager].
 */
class SkbAccountAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {

    override fun editProperties(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
    ): Bundle = Bundle().also {
        it.putInt(android.accounts.AccountManager.KEY_ERROR_CODE, 0)
        it.putString(android.accounts.AccountManager.KEY_ERROR_MESSAGE, "noop")
    }

    /**
     * Called by AccountManager.addAccount(...) when an external caller
     * tries to add an account via the Settings → Accounts UI. We never
     * surface our type to that UI (no `@xml/authenticator` UI assets
     * imply that), but if a caller does invoke it we return an empty
     * intent-less bundle so the framework completes without an Activity.
     */
    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ): Bundle = Bundle().also {
        // No interactive flow. Returning an empty bundle lets the
        // framework treat this as "the caller should provide the
        // account name itself" — the in-app toggle uses
        // `AccountManager.addAccountExplicitly` directly, so this path
        // is only ever hit by external callers (rare).
        it.putString(android.accounts.AccountManager.KEY_ACCOUNT_NAME, "")
        it.putString(android.accounts.AccountManager.KEY_ACCOUNT_TYPE, SKB_ACCOUNT_TYPE)
    }

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        options: Bundle?,
    ): Bundle = Bundle().also {
        // Local-only — nothing to confirm.
        it.putBoolean(android.accounts.AccountManager.KEY_BOOLEAN_RESULT, true)
    }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle = Bundle().also {
        // No tokens — there's no server.
        it.putString(android.accounts.AccountManager.KEY_AUTHTOKEN, "")
    }

    override fun getAuthTokenLabel(authTokenType: String?): String =
        "skb (local)"

    override fun updateCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle = Bundle().also {
        it.putString(android.accounts.AccountManager.KEY_ACCOUNT_NAME, account?.name.orEmpty())
        it.putString(android.accounts.AccountManager.KEY_ACCOUNT_TYPE, SKB_ACCOUNT_TYPE)
    }

    override fun hasFeatures(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        features: Array<out String>?,
    ): Bundle = Bundle().also {
        it.putBoolean(android.accounts.AccountManager.KEY_BOOLEAN_RESULT, false)
    }

    /**
     * Round 2.18.G.1 — the toggle-off path uses
     * `AccountManager.removeAccountExplicitly`, but external removers
     * (Settings → Accounts) go through this seam. Returning `true`
     * tells the framework "yes, this account can be removed" — the
     * actual removal is then performed by AccountManager itself.
     */
    override fun getAccountRemovalAllowed(
        response: AccountAuthenticatorResponse?,
        account: Account?,
    ): Bundle = Bundle().also {
        it.putBoolean(android.accounts.AccountManager.KEY_BOOLEAN_RESULT, true)
    }

    companion object {
        /**
         * Account type — MUST exactly match the
         * `android:accountType` attribute in `res/xml/authenticator.xml`
         * and `res/xml/sync_calendar.xml`, and the manifest application
         * id (so the OS treats this as our package's authenticator).
         */
        const val SKB_ACCOUNT_TYPE = "com.eight87.strictlykeptboy"

        /**
         * Local-only suffix on account names. Anything in the form
         * `<x>@local` is conventionally treated by Android as not
         * eligible for credential prompts.
         */
        const val LOCAL_SUFFIX = "@local"

        /**
         * Build the canonical account name for a skb repo. `<repoId>@local`.
         */
        fun accountNameFor(repoId: String): String = "$repoId$LOCAL_SUFFIX"

        /**
         * Inverse of [accountNameFor]. Strips the `@local` suffix; returns
         * `null` if the input is not in the expected shape.
         */
        fun repoIdFor(accountName: String): String? =
            if (accountName.endsWith(LOCAL_SUFFIX)) accountName.removeSuffix(LOCAL_SUFFIX)
            else null
    }
}

package com.eight87.strictlykeptboy.system

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import com.eight87.strictlykeptboy.git.RepoStore

/**
 * Round 2.18.G.6 / G.7 — `AccountManager` facade for skb's local-only
 * sync-adapter accounts.
 *
 * Responsibilities:
 *  - On toggle-on: create one Account per skb repo via
 *    [AccountManager.addAccountExplicitly], mark the
 *    `com.android.calendar` authority syncable, and request an initial
 *    sync so the adapter's first run lands events into
 *    `CalendarContract` without waiting on a periodic tick.
 *  - On toggle-off: remove every skb account and clear the cached
 *    calendar-row idempotency map so a future re-enable starts clean.
 *  - On `GitRepo.commitAll` (G.7): request a sync on the matching
 *    repo's account so the OS calendar updates within seconds of the
 *    disk write.
 *
 * No I/O off the calling thread — every method is a thin AccountManager
 * call.
 */
class SkbAccountManager(
    private val context: Context,
    private val repoStore: RepoStore,
    private val systemCalendarPrefs: SystemCalendarPrefsStore,
) {

    private val am: AccountManager get() = AccountManager.get(context)

    /**
     * G.6 — for every repo in [RepoStore.list], add an account named
     * `<repoId>@local` and enable calendar sync on it. Idempotent —
     * `addAccountExplicitly` returns `false` on duplicates, which we
     * treat as success.
     */
    fun enableForAllRepos(): Int {
        var added = 0
        for (cfg in repoStore.list()) {
            if (cfg.isDemo) continue
            val account = accountFor(cfg.repoId)
            val isNew = runCatching {
                am.addAccountExplicitly(account, null, null)
            }.getOrDefault(false)
            if (isNew) added++
            markSyncable(account)
            requestSync(account)
        }
        return added
    }

    /**
     * G.6 — remove every account we own. Uses
     * [AccountManager.removeAccountExplicitly] (API 22+; we're API 26+).
     */
    fun disableForAllRepos() {
        val ours = am.getAccountsByType(SkbAccountAuthenticator.SKB_ACCOUNT_TYPE)
        for (acc in ours) {
            runCatching { am.removeAccountExplicitly(acc) }
            val repoId = SkbAccountAuthenticator.repoIdFor(acc.name)
            if (repoId != null) systemCalendarPrefs.clearCalendarRowIdsFor(repoId)
        }
    }

    /**
     * G.7 — fire a sync for the named repo. Called from `GitRepo.commitAll`
     * via [com.eight87.strictlykeptboy.system.SkbCommitNotifier].
     */
    fun requestSyncFor(repoId: String) {
        if (!systemCalendarPrefs.globalState.value.publishToOs) return
        val account = accountFor(repoId)
        val existing = am.getAccountsByType(SkbAccountAuthenticator.SKB_ACCOUNT_TYPE)
        if (existing.none { it.name == account.name }) return
        requestSync(account)
    }

    private fun accountFor(repoId: String): Account = Account(
        SkbAccountAuthenticator.accountNameFor(repoId),
        SkbAccountAuthenticator.SKB_ACCOUNT_TYPE,
    )

    private fun markSyncable(account: Account) {
        ContentResolver.setIsSyncable(account, CALENDAR_AUTHORITY, 1)
        ContentResolver.setSyncAutomatically(account, CALENDAR_AUTHORITY, true)
    }

    private fun requestSync(account: Account) {
        val extras = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        ContentResolver.requestSync(account, CALENDAR_AUTHORITY, extras)
    }

    companion object {
        const val CALENDAR_AUTHORITY: String = "com.android.calendar"
    }
}

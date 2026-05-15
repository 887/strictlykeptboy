package com.eight87.strictlykeptboy.system

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 2.18.B.6 — first-run "new calendar accounts detected" nudge.
 *
 * Snapshots the set of currently-known accounts (`accountType +
 * accountName`) into a `SharedPreferences` keyset. When
 * `AccountManager.OnAccountsUpdateListener` fires we diff the live set
 * against the snapshot — if there's at least one new account AND the
 * user hasn't already dismissed the nudge in
 * [SystemCalendarPrefsStore.markAccountChangeNudgeShown], we raise
 * [shouldShowNudge].
 *
 * The caller (MainActivity) observes [shouldShowNudge] + the global
 * `showSystemCalendars` toggle and decides where to surface the
 * snackbar. Once shown, the caller calls
 * [SystemCalendarPrefsStore.markAccountChangeNudgeShown] so we don't
 * pester the user again on every account change.
 *
 * Account types we care about (anything that registers calendars):
 *  - `com.google` (Google Calendar)
 *  - `com.android.exchange` / `com.microsoft.*` (Exchange / Outlook)
 *  - `at.bitfire.davdroid` (DAVx5)
 *  - any other type that has installed a CalendarContract provider —
 *    we trust the AccountManager listener to fire for those too.
 *
 * Why no listener inside the bridge? CalendarContract's content URI
 * already fires on calendar-row changes (A.7). The nudge here is
 * deliberately about ACCOUNTS being added at the OS level, which is a
 * coarser signal — it fires before the user has a chance to pick which
 * calendars sync, so we point them at Settings rather than just folding
 * the new calendars into skb silently.
 */
class AccountChangeNudge internal constructor(
    private val accountManager: AccountManager,
    private val prefs: SharedPreferences,
) {

    private val _shouldShowNudge = MutableStateFlow(false)
    val shouldShowNudge: StateFlow<Boolean> = _shouldShowNudge.asStateFlow()

    private val listener = OnAccountsUpdateListener { accounts ->
        recompute(accounts)
    }

    /** Wire up the AccountManager listener. Idempotent — safe to call twice. */
    fun start() {
        // `updateImmediately = true` invokes the listener with the
        // current account snapshot on registration, which seeds
        // [_shouldShowNudge] without a redundant readAccounts() call.
        accountManager.addOnAccountsUpdatedListener(listener, Handler(Looper.getMainLooper()), true)
    }

    fun stop() {
        runCatching { accountManager.removeOnAccountsUpdatedListener(listener) }
    }

    /** Called by the UI once the snackbar has been displayed. */
    fun markShown(latest: Array<Account> = accountManager.accounts) {
        prefs.edit().putStringSet(KEY_SEEN, latest.toKey()).apply()
        _shouldShowNudge.value = false
    }

    private fun recompute(accounts: Array<Account>) {
        val seen = prefs.getStringSet(KEY_SEEN, null)
        val live = accounts.toKey()
        if (seen == null) {
            // First-ever run: seed the snapshot WITHOUT raising the
            // nudge — pre-existing accounts at install time aren't
            // "newly added", they were there before we cared.
            prefs.edit().putStringSet(KEY_SEEN, live).apply()
            _shouldShowNudge.value = false
            return
        }
        val newOnes = live - seen
        _shouldShowNudge.value = newOnes.isNotEmpty()
    }

    private fun Array<Account>.toKey(): Set<String> =
        map { "${it.type}|${it.name}" }.toSet()

    companion object {
        private const val PREFS_FILE = "account_change_nudge_v1"
        private const val KEY_SEEN = "seen.accounts"

        fun open(context: Context): AccountChangeNudge = AccountChangeNudge(
            accountManager = AccountManager.get(context),
            prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
        )

        internal fun openForTest(am: AccountManager, prefs: SharedPreferences): AccountChangeNudge =
            AccountChangeNudge(am, prefs)
    }
}

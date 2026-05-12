package com.eight87.strictlykeptboy.ui.wizard

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Phase K.14 — one-time first-launch age gate persistence.
 *
 * Stored in EncryptedSharedPreferences so the timestamp survives uninstall
 * only via Android's auto-backup if enabled; otherwise it lives for the life
 * of the install. Key is the epoch-millis moment the user confirmed 17+.
 */
class AgeGatePrefs internal constructor(private val prefs: SharedPreferences) {

    fun isConfirmed(): Boolean = prefs.contains(KEY_CONFIRMED_AT)

    fun confirmedAtMs(): Long? =
        if (prefs.contains(KEY_CONFIRMED_AT)) prefs.getLong(KEY_CONFIRMED_AT, 0L).takeIf { it > 0 }
        else null

    fun confirm(nowMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_CONFIRMED_AT, nowMs).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CONFIRMED_AT).apply()
    }

    companion object {
        const val KEY_CONFIRMED_AT = "age_confirmed_at"
        private const val PREFS_FILE = "age_gate_v1"

        fun open(context: Context): AgeGatePrefs {
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
            return AgeGatePrefs(prefs)
        }

        /** Test constructor — plain prefs for Robolectric. */
        internal fun openForTest(prefs: SharedPreferences) = AgeGatePrefs(prefs)
    }
}

/**
 * Phase K.14 — neutral-mode toggle: when on, kink-tagged content is hidden
 * app-wide (composes with the wizard's `unaligned-private` alignment).
 * Plain SharedPreferences — display preference, not a secret.
 */
class NeutralModePrefs internal constructor(private val prefs: SharedPreferences) {
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        const val KEY_ENABLED = "neutral_mode_enabled"
        private const val PREFS_FILE = "neutral_mode_v1"

        fun open(context: Context) = NeutralModePrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
        )

        internal fun openForTest(prefs: SharedPreferences) = NeutralModePrefs(prefs)
    }
}

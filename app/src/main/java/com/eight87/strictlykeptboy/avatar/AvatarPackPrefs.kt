package com.eight87.strictlykeptboy.avatar

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase WW.5 — per-device sticker prefs (D.67).
 *
 * Two storage tracks, kept here so the rest of the app sees a single
 * narrow surface:
 *
 *  - **Active pack per species** — which pack id the resolver should
 *    consult when rendering the user's current species. Multi-pack-
 *    per-species support is part of D.69: when the user clones a new
 *    pack, it becomes active for its species; the picker can switch
 *    back. Stored in plain `SharedPreferences` (display preference).
 *
 *  - **Per-activity sticker overrides** — `activity_id → "<pack-id>:<sticker-activity-id>"`.
 *    Stored in `EncryptedSharedPreferences` per D.67: device aesthetic,
 *    NOT committed to the user data repo, so we treat it like other
 *    on-device secrets even though it isn't a credential.
 */
class AvatarPackPrefs internal constructor(
    private val plain: SharedPreferences,
    private val encrypted: SharedPreferences,
) {

    // -- Active pack per species ---------------------------------------

    private val activeState = MutableStateFlow(loadActive())
    val activePerSpecies: StateFlow<Map<String, String>> = activeState.asStateFlow()

    /**
     * Pack id currently active for [species]. Defaults to the bundled
     * default pack for the species (`default-<species>`).
     */
    fun activePackFor(species: String): String {
        val key = activeKey(species)
        return plain.getString(key, null) ?: PackId.forBundledSpecies(species)
    }

    fun setActivePackFor(species: String, packId: String) {
        plain.edit().putString(activeKey(species), packId).apply()
        activeState.value = loadActive()
    }

    private fun loadActive(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in plain.all) {
            if (k.startsWith(ACTIVE_PREFIX) && v is String) {
                out[k.removePrefix(ACTIVE_PREFIX)] = v
            }
        }
        return out
    }

    // -- Per-activity sticker overrides --------------------------------

    /**
     * Returns the device-level override for [activityId], or `null` if
     * the user hasn't pinned a specific sticker. Format on disk per
     * D.67: `"<pack-id>:<sticker-activity-id>"`.
     */
    fun overrideFor(activityId: String): StickerOverride? {
        val raw = encrypted.getString(overrideKey(activityId), null) ?: return null
        val colon = raw.indexOf(':')
        if (colon <= 0 || colon == raw.length - 1) return null
        return StickerOverride(
            packId = raw.substring(0, colon),
            stickerActivityId = raw.substring(colon + 1),
        )
    }

    fun setOverride(activityId: String, override: StickerOverride?) {
        val e = encrypted.edit()
        if (override == null) {
            e.remove(overrideKey(activityId))
        } else {
            e.putString(overrideKey(activityId), "${override.packId}:${override.stickerActivityId}")
        }
        e.apply()
    }

    /** Snapshot of every override the user has set (for export per WW.5). */
    fun allOverrides(): Map<String, StickerOverride> {
        val out = LinkedHashMap<String, StickerOverride>()
        for ((k, v) in encrypted.all) {
            if (k.startsWith(OVERRIDE_PREFIX) && v is String) {
                val colon = v.indexOf(':')
                if (colon > 0 && colon < v.length - 1) {
                    out[k.removePrefix(OVERRIDE_PREFIX)] = StickerOverride(
                        packId = v.substring(0, colon),
                        stickerActivityId = v.substring(colon + 1),
                    )
                }
            }
        }
        return out
    }

    private fun activeKey(species: String) = ACTIVE_PREFIX + species.lowercase()
    private fun overrideKey(activityId: String) = OVERRIDE_PREFIX + activityId

    data class StickerOverride(val packId: String, val stickerActivityId: String)

    companion object {
        private const val PLAIN_FILE = "avatar_pack_v1"
        private const val ENC_FILE = "avatar_overrides_v1"
        private const val ACTIVE_PREFIX = "active."
        private const val OVERRIDE_PREFIX = "avatar.overrides."

        fun open(context: Context): AvatarPackPrefs {
            val plain = context.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val enc = EncryptedSharedPreferences.create(
                context,
                ENC_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return AvatarPackPrefs(plain, enc)
        }

        /** Test constructor — plain prefs for both tracks. */
        internal fun openForTest(plain: SharedPreferences, encrypted: SharedPreferences) =
            AvatarPackPrefs(plain, encrypted)
    }
}

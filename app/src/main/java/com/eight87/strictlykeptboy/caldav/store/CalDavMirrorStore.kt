package com.eight87.strictlykeptboy.caldav.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.eight87.strictlykeptboy.caldav.CalDavMirror
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Phase Y.4 — persistent registry of `CalDavMirror`s. EncryptedShared-
 * Preferences-backed (mirrors live alongside git remotes in the trust
 * boundary). One JSON-serialised mirror per key `caldav.mirror.<id>`.
 *
 * SOLID.S — only owns mirror persistence; sync orchestration lives in
 * `CalDavMirrorWorker`. SOLID.D — UI/sync layers depend on this surface,
 * not on raw `SharedPreferences`.
 */
class CalDavMirrorStore internal constructor(
    private val prefs: SharedPreferences,
    private val json: Json = DefaultJson,
) {
    private val _state = MutableStateFlow<List<CalDavMirror>>(emptyList())
    val state: StateFlow<List<CalDavMirror>> = _state.asStateFlow()

    init { reload() }

    fun list(): List<CalDavMirror> = _state.value
    fun listForRepo(repoId: String): List<CalDavMirror> = _state.value.filter { it.repoId == repoId }
    fun get(mirrorId: String): CalDavMirror? = _state.value.firstOrNull { it.mirrorId == mirrorId }

    fun upsert(mirror: CalDavMirror) {
        prefs.edit().putString(key(mirror.mirrorId), json.encodeToString(mirror)).apply()
        reload()
    }

    fun remove(mirrorId: String) {
        prefs.edit().remove(key(mirrorId)).apply()
        reload()
    }

    fun removeForRepo(repoId: String) {
        val edits = prefs.edit()
        listForRepo(repoId).forEach { edits.remove(key(it.mirrorId)) }
        edits.apply()
        reload()
    }

    private fun reload() {
        val all = prefs.all.entries
            .filter { it.key.startsWith(PREFIX) }
            .mapNotNull { entry ->
                runCatching { json.decodeFromString<CalDavMirror>(entry.value as String) }.getOrNull()
            }
            .sortedBy { it.displayName }
        _state.value = all
    }

    private fun key(mirrorId: String) = "$PREFIX$mirrorId"

    companion object {
        private const val PREFS_FILE = "caldav_mirrors_v1"
        private const val PREFIX = "caldav.mirror."
        private val DefaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun open(context: Context): CalDavMirrorStore {
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
            return CalDavMirrorStore(prefs)
        }

        fun openForTest(prefs: SharedPreferences): CalDavMirrorStore = CalDavMirrorStore(prefs)
    }
}

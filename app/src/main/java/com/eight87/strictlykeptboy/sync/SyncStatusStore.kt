package com.eight87.strictlykeptboy.sync

import android.content.Context
import android.content.SharedPreferences
import com.eight87.strictlykeptboy.git.RemoteName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phase J.4 — per-repo + per-remote sync status persistence. Plain
 * SharedPreferences (`sync_status_v1.xml`) — display state only,
 * NOT encrypted secrets.
 *
 * One pref key per repo: `status.<repoId>` → JSON [SyncStatusSnapshot].
 * One index key: `index` → JSON `List<String>`.
 */
class SyncStatusStore internal constructor(
    private val prefs: SharedPreferences,
    private val json: Json = DefaultJson,
) {
    private val _state = MutableStateFlow(loadAll())
    val state: StateFlow<Map<String, SyncStatusSnapshot>> = _state.asStateFlow()

    fun snapshotFor(repoId: String): SyncStatusSnapshot =
        _state.value[repoId] ?: SyncStatusSnapshot(repoId)

    /** Update a single remote's status, persist, emit. */
    fun updateRemote(
        repoId: String,
        remote: RemoteName,
        transform: (RemoteSyncStatus) -> RemoteSyncStatus,
    ) {
        val current = snapshotFor(repoId)
        val key = remote.value
        val existing = current.perRemote[key] ?: RemoteSyncStatus()
        val updated = current.copy(perRemote = current.perRemote + (key to transform(existing)))
        write(updated)
    }

    /** Update repo-level fields (lastSyncedAt, lastError). */
    fun updateRepo(repoId: String, transform: (SyncStatusSnapshot) -> SyncStatusSnapshot) {
        write(transform(snapshotFor(repoId)))
    }

    fun clearRepo(repoId: String) {
        val map = _state.value - repoId
        prefs.edit().run {
            remove(keyFor(repoId))
            putString(INDEX_KEY, json.encodeToString(map.keys.toList()))
            apply()
        }
        _state.value = map
    }

    private fun write(snapshot: SyncStatusSnapshot) {
        val map = _state.value + (snapshot.repoId to snapshot)
        prefs.edit().run {
            putString(keyFor(snapshot.repoId), json.encodeToString(snapshot))
            putString(INDEX_KEY, json.encodeToString(map.keys.toList()))
            apply()
        }
        _state.value = map
    }

    private fun loadAll(): Map<String, SyncStatusSnapshot> {
        val indexJson = prefs.getString(INDEX_KEY, null) ?: return emptyMap()
        val ids: List<String> = runCatching { json.decodeFromString<List<String>>(indexJson) }
            .getOrDefault(emptyList())
        return ids.mapNotNull { id ->
            val s = prefs.getString(keyFor(id), null) ?: return@mapNotNull null
            runCatching { json.decodeFromString<SyncStatusSnapshot>(s) }.getOrNull()?.let { id to it }
        }.toMap()
    }

    private fun keyFor(repoId: String) = "status.$repoId"

    companion object {
        private const val INDEX_KEY = "index"
        private const val PREFS_FILE = "sync_status_v1"
        private val DefaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun open(context: Context): SyncStatusStore {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return SyncStatusStore(prefs)
        }

        /** Test-only constructor — accepts a plain SharedPreferences for Robolectric. */
        internal fun openForTest(prefs: SharedPreferences): SyncStatusStore = SyncStatusStore(prefs)
    }
}

/**
 * Per-repo snapshot. [perRemote] is keyed by [RemoteName.value] (string)
 * for kotlinx.serialization compatibility — value classes don't serialize
 * as map keys without a custom serializer.
 */
@Serializable
data class SyncStatusSnapshot(
    val repoId: String,
    val lastSyncedAt: Long? = null,
    val lastErrorMessage: String? = null,
    val commitsAhead: Int = 0,
    val commitsBehind: Int = 0,
    val perRemote: Map<String, RemoteSyncStatus> = emptyMap(),
)

@Serializable
data class RemoteSyncStatus(
    val lastSyncedAt: Long? = null,
    val lastErrorMessage: String? = null,
    val readOnlyDetected: Boolean = false,
)

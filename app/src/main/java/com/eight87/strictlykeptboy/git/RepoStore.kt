package com.eight87.strictlykeptboy.git

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent list of configured repos. Backed by `repos_v1.xml`
 * (EncryptedSharedPreferences with AES256_SIV / AES256_GCM via MasterKey).
 * Per Phase B.3 / SE-F.2.
 *
 * Storage layout:
 * - One pref key per repo: `repo.<repoId>` → JSON `RepoConfig`.
 * - One index key: `index` → JSON `List<String>` (ordered repo IDs).
 *
 * Keeping repos in separate keys (vs. one big list blob) means writes
 * touch only the affected entry — important if we later grow to N>10 repos
 * with frequent state updates (lastSyncedAt / commitsAhead).
 *
 * Concurrency: a single [Mutex] serializes all writes. Reads of the
 * [StateFlow] are lock-free.
 */
class RepoStore internal constructor(
    private val prefs: SharedPreferences,
    private val json: Json,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(loadAll())
    val state: StateFlow<List<RepoConfig>> = _state.asStateFlow()

    fun list(): List<RepoConfig> = _state.value
    fun get(repoId: String): RepoConfig? = _state.value.firstOrNull { it.repoId == repoId }

    suspend fun add(config: RepoConfig) = mutex.withLock {
        val current = _state.value
        require(current.none { it.repoId == config.repoId }) {
            "repo ${config.repoId} already in store"
        }
        prefs.edit().run {
            putString(keyFor(config.repoId), json.encodeToString(config))
            val newIndex: List<String> = current.map { it.repoId } + config.repoId
            putString(INDEX_KEY, json.encodeToString(newIndex))
            apply()
        }
        _state.value = current + config
    }

    suspend fun update(config: RepoConfig) = mutex.withLock {
        val current = _state.value
        require(current.any { it.repoId == config.repoId }) {
            "repo ${config.repoId} not in store"
        }
        prefs.edit().run {
            putString(keyFor(config.repoId), json.encodeToString(config))
            apply()
        }
        _state.value = current.map { if (it.repoId == config.repoId) config else it }
    }

    suspend fun remove(repoId: String) = mutex.withLock {
        val current = _state.value
        prefs.edit().run {
            remove(keyFor(repoId))
            val newIndex: List<String> = current.map { it.repoId } - repoId
            putString(INDEX_KEY, json.encodeToString(newIndex))
            apply()
        }
        _state.value = current.filterNot { it.repoId == repoId }
    }

    suspend fun addRemote(repoId: String, binding: RemoteBinding) {
        val existing = get(repoId) ?: error("repo $repoId not in store")
        require(existing.remotes.none { it.name == binding.name }) {
            "remote ${binding.name} already exists on repo $repoId"
        }
        val updatedRemotes = existing.remotes + binding
        val updatedPrimary = existing.primaryRemote ?: binding.name
        update(existing.copy(remotes = updatedRemotes, primaryRemote = updatedPrimary))
    }

    suspend fun removeRemote(repoId: String, name: RemoteName) {
        val existing = get(repoId) ?: error("repo $repoId not in store")
        val updatedRemotes = existing.remotes.filterNot { it.name == name }
        val updatedPrimary = when {
            updatedRemotes.isEmpty() -> null
            existing.primaryRemote == name -> updatedRemotes.first().name
            else -> existing.primaryRemote
        }
        update(existing.copy(remotes = updatedRemotes, primaryRemote = updatedPrimary))
    }

    suspend fun renameRemote(repoId: String, from: RemoteName, to: RemoteName) {
        val existing = get(repoId) ?: error("repo $repoId not in store")
        require(to.value != "HEAD") { "'HEAD' is a reserved ref name" }
        require(existing.remotes.any { it.name == from }) {
            "remote $from is not configured on repo $repoId"
        }
        require(existing.remotes.none { it.name == to }) {
            "remote $to already exists on repo $repoId"
        }
        val updatedRemotes = existing.remotes.map { if (it.name == from) it.copy(name = to) else it }
        val updatedPrimary = if (existing.primaryRemote == from) to else existing.primaryRemote
        update(existing.copy(remotes = updatedRemotes, primaryRemote = updatedPrimary))
    }

    suspend fun setPushPolicy(repoId: String, name: RemoteName, policy: PushPolicy) {
        val existing = get(repoId) ?: error("repo $repoId not in store")
        require(existing.remotes.any { it.name == name }) {
            "remote $name is not configured on repo $repoId"
        }
        val updatedRemotes = existing.remotes.map {
            if (it.name == name) it.copy(pushPolicy = policy) else it
        }
        update(existing.copy(remotes = updatedRemotes))
    }

    suspend fun setPrimary(repoId: String, name: RemoteName) {
        val existing = get(repoId) ?: error("repo $repoId not in store")
        require(existing.remotes.any { it.name == name }) {
            "remote $name is not configured on repo $repoId"
        }
        update(existing.copy(primaryRemote = name))
    }

    private fun loadAll(): List<RepoConfig> {
        val indexJson = prefs.getString(INDEX_KEY, null) ?: return emptyList()
        val ids: List<String> = json.decodeFromString(indexJson)
        return ids.mapNotNull { id ->
            val s = prefs.getString(keyFor(id), null) ?: return@mapNotNull null
            runCatching { json.decodeFromString<RepoConfig>(s) }.getOrNull()
        }
    }

    private fun keyFor(repoId: String) = "repo.$repoId"

    companion object {
        private const val INDEX_KEY = "index"
        private const val PREFS_FILE = "repos_v1"

        private val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun open(context: Context): RepoStore {
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
            return RepoStore(prefs, DefaultJson)
        }

        /** Test-only constructor — accepts a plain SharedPreferences for Robolectric. */
        internal fun openForTest(prefs: SharedPreferences): RepoStore =
            RepoStore(prefs, DefaultJson)
    }
}

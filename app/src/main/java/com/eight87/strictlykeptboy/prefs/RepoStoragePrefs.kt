package com.eight87.strictlykeptboy.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Round 2.17.A.2 — app-wide *parent folder* prefs.
 *
 * D-2.17.a: there is exactly one parent folder for the whole app; every
 * repo is a direct subfolder of it. The parent is either internal (a
 * fixed path under `filesDir/strictlykeptboy/`) or external (a SAF
 * tree URI the user picked outside the sandbox + the cached real path
 * that we hand to JGit).
 *
 * Schema bumped to `repo_storage_v2.xml`. On first construction we read
 * v1 once (the legacy `MirrorLocation` keys) and upgrade an `external`
 * mirror into an `External` parent shell — see [ParentLocationMigrator]
 * for the full move-on-disk behaviour. v2 also remembers the legacy
 * "skipped during wizard" boolean so the post-wizard reminder banner
 * keeps working for the upgrade-from-2.7 cohort until Phase D ships
 * its own gate.
 *
 * Storage: EncryptedSharedPreferences. The picked folder path can carry
 * filesystem-fingerprinting info we keep at the same trust level as
 * `RepoStore`.
 *
 * Keys (v2):
 *  - `parent.kind`              `"internal"` | `"external"`
 *  - `parent.abs_path`          String (internal only — absolute path)
 *  - `parent.tree_uri`          String (external only)
 *  - `parent.label`             String (external only)
 *  - `parent.cached_real_path`  String? (external only — resolved File path)
 *  - `parent.skipped_in_wizard` Boolean (upgrade tail from 2.7)
 *  - `migrated_from_d_2_7_b`    Boolean (set once by ParentLocationMigrator)
 */
class RepoStoragePrefs internal constructor(private val prefs: SharedPreferences) {

    init {
        // Round 2.17.A.2 — one-time upgrade from the v1 `mirror.*` keyspace.
        // Idempotent: marked by [KEY_V1_UPGRADED]. We don't move repos on
        // disk here — that's [ParentLocationMigrator]'s job; this only
        // promotes a v1 External mirror into a v2 External parent shell so
        // the v1 cohort sees their previously-picked folder pre-filled.
        if (!prefs.getBoolean(KEY_V1_UPGRADED, false)) {
            upgradeFromV1IfPresent()
        }
    }

    private val _state = MutableStateFlow(load())

    /** Currently-configured parent location. */
    val state: StateFlow<ParentLocation?> = _state.asStateFlow()

    /**
     * Current value, lock-free. `null` means the user has not yet
     * confirmed any parent — wizard / add-repo flow must surface the
     * "where to store?" question before scaffolding.
     */
    val location: ParentLocation? get() = _state.value

    /**
     * Upgrade tail: true iff the user picked "Skip — decide later" on the
     * 2.7 mirror screen. Used by the Repos pane reminder banner until
     * Phase D ships its own gate. Cleared automatically when the user
     * confirms a real parent.
     */
    var skippedDuringWizard: Boolean
        get() = prefs.getBoolean(KEY_SKIPPED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SKIPPED, value).apply()
        }

    /** True once [ParentLocationMigrator] has run successfully. */
    var migratedFromD27b: Boolean
        get() = prefs.getBoolean(KEY_MIGRATED_D27B, false)
        set(value) {
            prefs.edit().putBoolean(KEY_MIGRATED_D27B, value).apply()
        }

    /** Persist a new parent location. Idempotent. */
    fun set(location: ParentLocation) {
        val editor = prefs.edit()
        when (location) {
            is ParentLocation.Internal -> {
                editor.putString(KEY_KIND, KIND_INTERNAL)
                editor.putString(KEY_ABS_PATH, location.absPath)
                editor.remove(KEY_TREE_URI)
                editor.remove(KEY_LABEL)
                editor.remove(KEY_CACHED_REAL_PATH)
            }
            is ParentLocation.External -> {
                editor.putString(KEY_KIND, KIND_EXTERNAL)
                editor.putString(KEY_TREE_URI, location.treeUri)
                editor.putString(KEY_LABEL, location.label)
                if (location.cachedRealPath != null) {
                    editor.putString(KEY_CACHED_REAL_PATH, location.cachedRealPath)
                } else {
                    editor.remove(KEY_CACHED_REAL_PATH)
                }
                editor.remove(KEY_ABS_PATH)
            }
        }
        // Confirming a parent clears the legacy wizard-skip reminder.
        editor.putBoolean(KEY_SKIPPED, false)
        editor.apply()
        _state.value = location
    }

    private fun load(): ParentLocation? {
        val kind = prefs.getString(KEY_KIND, null) ?: return null
        return when (kind) {
            KIND_INTERNAL -> {
                val abs = prefs.getString(KEY_ABS_PATH, null) ?: return null
                ParentLocation.Internal(absPath = abs)
            }
            KIND_EXTERNAL -> {
                val uri = prefs.getString(KEY_TREE_URI, null)
                val label = prefs.getString(KEY_LABEL, null)
                if (uri != null && label != null) {
                    ParentLocation.External(
                        treeUri = uri,
                        label = label,
                        cachedRealPath = prefs.getString(KEY_CACHED_REAL_PATH, null),
                    )
                } else null
            }
            else -> null
        }
    }

    private fun upgradeFromV1IfPresent() {
        // v1 used `mirror.kind` = `"none"` | `"external"`. If the user had
        // picked an external mirror, promote it to a v2 External parent
        // shell (no cached real path yet; the Phase B picker rewrite or
        // ParentLocationMigrator will populate it). Otherwise leave v2
        // unset so the gate surfaces the question.
        val v1Kind = prefs.getString(V1_KEY_KIND, null)
        val editor = prefs.edit()
        if (v1Kind == V1_KIND_EXTERNAL) {
            val uri = prefs.getString(V1_KEY_TREE_URI, null)
            val label = prefs.getString(V1_KEY_LABEL, null)
            if (uri != null && label != null && prefs.getString(KEY_KIND, null) == null) {
                editor.putString(KEY_KIND, KIND_EXTERNAL)
                editor.putString(KEY_TREE_URI, uri)
                editor.putString(KEY_LABEL, label)
            }
        }
        // Carry the v1 wizard-skip flag forward. v1 stored it at
        // `mirror.skipped_in_wizard`; v2 reads from `parent.skipped_in_wizard`.
        if (prefs.contains(V1_KEY_SKIPPED) && !prefs.contains(KEY_SKIPPED)) {
            editor.putBoolean(KEY_SKIPPED, prefs.getBoolean(V1_KEY_SKIPPED, false))
        }
        editor.putBoolean(KEY_V1_UPGRADED, true)
        editor.apply()
    }

    companion object {
        // Round 2.17.A.2 — bumped to v2 to mark the ParentLocation rework.
        // EncryptedSharedPreferences is keyed by file name, so v1's bytes
        // are not lost; v2 reads them via [upgradeFromV1IfPresent] on
        // first construction.
        const val PREFS_FILE: String = "repo_storage_v2"

        private const val KEY_KIND = "parent.kind"
        private const val KEY_ABS_PATH = "parent.abs_path"
        private const val KEY_TREE_URI = "parent.tree_uri"
        private const val KEY_LABEL = "parent.label"
        private const val KEY_CACHED_REAL_PATH = "parent.cached_real_path"
        private const val KEY_SKIPPED = "parent.skipped_in_wizard"
        private const val KEY_V1_UPGRADED = "v1_upgraded"
        private const val KEY_MIGRATED_D27B = "migrated_from_d_2_7_b"

        // Legacy v1 keys retained for one-shot read.
        private const val V1_KEY_KIND = "mirror.kind"
        private const val V1_KEY_TREE_URI = "mirror.tree_uri"
        private const val V1_KEY_LABEL = "mirror.label"
        private const val V1_KEY_SKIPPED = "mirror.skipped_in_wizard"
        private const val V1_KIND_EXTERNAL = "external"

        private const val KIND_INTERNAL = "internal"
        private const val KIND_EXTERNAL = "external"

        /**
         * Default absolute path for an Internal parent — `filesDir/strictlykeptboy`.
         */
        fun defaultInternalDir(context: Context): File =
            File(context.filesDir, "strictlykeptboy")

        fun open(context: Context): RepoStoragePrefs {
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
            return RepoStoragePrefs(prefs)
        }

        /** Test-only constructor — accepts a plain SharedPreferences for Robolectric. */
        internal fun openForTest(prefs: SharedPreferences): RepoStoragePrefs =
            RepoStoragePrefs(prefs)
    }
}

/**
 * Round 2.17 — D-2.17.a. Exactly one parent folder for the whole app;
 * every repo lives directly underneath as a subfolder.
 */
sealed interface ParentLocation {
    /**
     * Concrete on-disk directory the parent resolves to. For Internal this
     * is the configured absolute path under `filesDir`; for External, the
     * `cachedRealPath` derived by `SafTreeUriResolver.resolveRealPath`
     * when the user picked. May be `null` for External when the cache
     * was never populated (legacy v1 upgrade, or SAF on a non-primary
     * volume) — callers must guard.
     */
    fun workingDir(): File?

    /**
     * Round 2.17.C.6 — non-null overload for callers that have a
     * [filesDir] handy and want a deterministic fallback. Internal
     * returns its configured path (typically `filesDir/strictlykeptboy`);
     * External returns the cached real path if present, otherwise falls
     * back to `filesDir/strictlykeptboy` so the scaffolder never lands
     * on a null parent. Use this from wizard / Add-Repo call sites; use
     * the nullable [workingDir] when you actually want to react to the
     * cache being unpopulated (e.g. surfacing a re-pick prompt).
     */
    fun workingDir(filesDir: File): File = when (this) {
        is Internal -> File(absPath)
        is External -> cachedRealPath?.let { File(it) }
            ?: File(filesDir, "strictlykeptboy")
    }

    /**
     * App-private parent. Default route for users who pick "Keep inside
     * the app". Stickers, recordings, and other media are easier to keep
     * here per the user's brief; the trade-off is uninstall-deletes-data.
     *
     * @param absPath absolute path on disk, typically `filesDir/strictlykeptboy`.
     */
    data class Internal(val absPath: String) : ParentLocation {
        override fun workingDir(): File = File(absPath)
    }

    /**
     * User-picked SAF tree URI + resolved real path. JGit reads/writes
     * via `cachedRealPath`; the URI is held so we can re-verify
     * `contentResolver.persistedUriPermissions` after process restart
     * (D-2.17.k).
     *
     * @param treeUri stringified `content://com.android.externalstorage…`
     * @param label display name for the folder (e.g. `"Documents"`)
     * @param cachedRealPath absolute `File` path the SAF URI resolves to
     *   on the primary internal volume, or `null` if unresolved.
     */
    data class External(
        val treeUri: String,
        val label: String,
        val cachedRealPath: String?,
    ) : ParentLocation {
        override fun workingDir(): File? = cachedRealPath?.let { File(it) }
    }
}

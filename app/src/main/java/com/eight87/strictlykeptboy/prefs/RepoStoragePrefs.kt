package com.eight87.strictlykeptboy.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Round 2.7.B.1 — app-wide repo storage prefs.
 *
 * Per locked decision D-2.7.b: repos always live in `filesDir/repos/<repoId>/`.
 * This prefs surface stores the user-picked *backup mirror location*: a SAF
 * tree URI (e.g. `/storage/emulated/0/Documents/strictlykeptboy/`). On every
 * sync the [com.eight87.strictlykeptboy.sync.MirrorReconciler] ensures each
 * repo has a `file://`-transport remote named `"mirror"` pointing at
 * `<resolvedPath>/<repoId>.git`, and the sync runtime pushes there
 * alongside the user's primary remote.
 *
 * Storage: EncryptedSharedPreferences (`repo_storage_v1.xml`). We encrypt
 * because the picked folder path can carry useful filesystem fingerprinting
 * info we'd rather keep at the same trust level as `RepoStore`.
 *
 * Keys:
 *  - `mirror.kind`              `"none"` | `"external"`
 *  - `mirror.tree_uri`          String (external only)
 *  - `mirror.label`             String (external only — DocumentFile.name)
 *  - `mirror.skipped_in_wizard` Boolean (D-2.7.c — wizard "Skip — decide later")
 */
class RepoStoragePrefs internal constructor(private val prefs: SharedPreferences) {

    private val _state = MutableStateFlow(load())

    /** Currently-configured mirror location. */
    val state: StateFlow<MirrorLocation> = _state.asStateFlow()

    /** Current value, lock-free. */
    val location: MirrorLocation get() = _state.value

    /**
     * True iff the user picked "Skip — decide later" on the wizard mirror
     * screen. Drives the post-wizard reminder banner on the Repos pane
     * (2.7.D.2). Cleared automatically when the user later sets a real
     * mirror location.
     */
    var skippedDuringWizard: Boolean
        get() = prefs.getBoolean(KEY_SKIPPED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SKIPPED, value).apply()
        }

    /** Persist a new mirror location. Idempotent. */
    fun set(location: MirrorLocation) {
        val editor = prefs.edit()
        when (location) {
            is MirrorLocation.None -> {
                editor.putString(KEY_KIND, KIND_NONE)
                editor.remove(KEY_TREE_URI)
                editor.remove(KEY_LABEL)
            }
            is MirrorLocation.External -> {
                editor.putString(KEY_KIND, KIND_EXTERNAL)
                editor.putString(KEY_TREE_URI, location.treeUri)
                editor.putString(KEY_LABEL, location.label)
                // 2.7.D — once the user picks a real folder the
                // wizard-skip reminder no longer applies.
                editor.putBoolean(KEY_SKIPPED, false)
            }
        }
        editor.apply()
        _state.value = location
    }

    private fun load(): MirrorLocation {
        val kind = prefs.getString(KEY_KIND, null) ?: return MirrorLocation.None
        return when (kind) {
            KIND_EXTERNAL -> {
                val uri = prefs.getString(KEY_TREE_URI, null)
                val label = prefs.getString(KEY_LABEL, null)
                if (uri != null && label != null) {
                    MirrorLocation.External(treeUri = uri, label = label)
                } else MirrorLocation.None
            }
            else -> MirrorLocation.None
        }
    }

    companion object {
        private const val PREFS_FILE = "repo_storage_v1"
        private const val KEY_KIND = "mirror.kind"
        private const val KEY_TREE_URI = "mirror.tree_uri"
        private const val KEY_LABEL = "mirror.label"
        private const val KEY_SKIPPED = "mirror.skipped_in_wizard"

        private const val KIND_NONE = "none"
        private const val KIND_EXTERNAL = "external"

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
 * Per D-2.7.b. Two states only — repos themselves never move, so there's no
 * `Internal(...)` discriminating between sub-paths of `filesDir`.
 */
sealed interface MirrorLocation {
    /** No external mirror; repos still live in `filesDir/repos/`. */
    data object None : MirrorLocation

    /**
     * SAF tree URI the user granted via `OPEN_DOCUMENT_TREE`. The persistable
     * URI permission must already have been taken before this is stored, or
     * the URI is unusable after process restart.
     *
     * @param treeUri stringified `content://com.android.externalstorage…` URI
     * @param label display name for the folder (e.g. `"strictlykeptboy"`)
     */
    data class External(val treeUri: String, val label: String) : MirrorLocation
}

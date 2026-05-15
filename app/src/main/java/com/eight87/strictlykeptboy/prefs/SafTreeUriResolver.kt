package com.eight87.strictlykeptboy.prefs

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

/**
 * Round 2.7.B.3 — resolve a SAF tree URI to a real `java.io.File` path that
 * JGit can write to.
 *
 * Background: the Storage Access Framework only hands out `content://…` URIs,
 * but JGit's `file://` transport needs a real on-disk path. The volume +
 * sub-path embedded in the DocumentsContract tree-document-id (e.g.
 * `"primary:Documents/strictlykeptboy"`) can be mapped back to
 * `/storage/emulated/0/Documents/strictlykeptboy` for the **primary** internal
 * volume. SD cards and cloud providers (Drive, Dropbox, etc.) don't expose a
 * real path — those return `null` and the caller surfaces a warning + skips
 * the mirror remote.
 *
 * v1 constraint: only the primary internal volume is supported. The picker
 * copy ("Pick a folder on your phone's internal storage") encodes this.
 */
object SafTreeUriResolver {

    /**
     * @return absolute path under `/storage/emulated/0/…` when [uri] points at
     *   a directory on the primary internal volume; `null` otherwise.
     */
    fun resolveRealPath(uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        // Document IDs look like `"<volume>:<sub-path>"`. Sub-path may be empty
        // (volume root).
        val colon = docId.indexOf(':')
        if (colon < 0) return null
        val volume = docId.substring(0, colon)
        val subPath = docId.substring(colon + 1)
        if (volume != PRIMARY_VOLUME) return null
        val base = Environment.getExternalStorageDirectory().absolutePath
        return if (subPath.isEmpty()) base else "$base/$subPath"
    }

    /**
     * Round 2.17.B.3 — derive a human-readable label from a SAF tree URI
     * without pulling in `androidx.documentfile`. Doc-ids look like
     * `"primary:Documents/foo"`; we take the leaf segment of the sub-path.
     * Falls back to `"selected folder"` for volume-root, non-primary
     * picks, or when the doc-id can't be parsed.
     */
    fun deriveLabel(uri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val sub = docId.substringAfter(':', "")
            sub.substringAfterLast('/', sub).ifBlank { null }
        }.getOrNull() ?: "selected folder"
    }

    private const val PRIMARY_VOLUME = "primary"
}

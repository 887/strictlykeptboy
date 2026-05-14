package com.eight87.strictlykeptboy.prefs

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.7.B.5 — primary-volume tree URIs resolve to a real
 * `/storage/emulated/0/…` path; non-primary volumes (SD card, cloud
 * providers) return null.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SafTreeUriResolverTest {

    private fun treeUriFor(docId: String): Uri =
        DocumentsContract.buildTreeDocumentUri(EXTERNALSTORAGE_AUTHORITY, docId)

    @Test fun primaryVolumeSubPathResolves() {
        val uri = treeUriFor("primary:Documents/strictlykeptboy")
        val expected = Environment.getExternalStorageDirectory().absolutePath +
            "/Documents/strictlykeptboy"
        assertEquals(expected, SafTreeUriResolver.resolveRealPath(uri))
    }

    @Test fun primaryVolumeRootResolves() {
        val uri = treeUriFor("primary:")
        val expected = Environment.getExternalStorageDirectory().absolutePath
        assertEquals(expected, SafTreeUriResolver.resolveRealPath(uri))
    }

    @Test fun nonPrimaryVolumeReturnsNull() {
        val uri = treeUriFor("ABCD-1234:DCIM")
        assertNull(SafTreeUriResolver.resolveRealPath(uri))
    }

    @Test fun homeVolumeReturnsNull() {
        val uri = treeUriFor("home:Documents")
        assertNull(SafTreeUriResolver.resolveRealPath(uri))
    }

    companion object {
        private const val EXTERNALSTORAGE_AUTHORITY =
            "com.android.externalstorage.documents"
    }
}

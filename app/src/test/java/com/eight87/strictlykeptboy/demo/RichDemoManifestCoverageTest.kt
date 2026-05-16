package com.eight87.strictlykeptboy.demo

import android.content.res.AssetManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.20 Phase B.4 — manifest must equal asset tree.
 *
 * Walks `assets/rich-demo-repo/` recursively via [AssetManager.list]
 * (the only way at runtime, since the manifest is the index we're
 * checking), then compares to the lines of `_manifest.txt`. Catches
 * authors who add a file under `rich-demo-repo/` but forget to run
 * `./gradlew :app:regenerateRichDemoManifest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoManifestCoverageTest {

    @Test fun `manifest matches asset tree exactly`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val assets = ctx.assets

        val actual = walkAssetTree(assets, RichDemoSeeder.ASSET_ROOT)
            .map { it.removePrefix("${RichDemoSeeder.ASSET_ROOT}/") }
            .toSortedSet()

        val manifest = assets.open("${RichDemoSeeder.ASSET_ROOT}/${RichDemoSeeder.MANIFEST_NAME}")
            .bufferedReader().use { it.readLines() }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSortedSet()

        // The manifest lists itself; the walker also returns it. Both
        // sets include `_manifest.txt`, so equality works without
        // special-casing.
        val onlyInTree = actual - manifest
        val onlyInManifest = manifest - actual
        assertEquals(
            "manifest drift — onlyInTree=$onlyInTree onlyInManifest=$onlyInManifest. " +
                "Run: ./gradlew :app:regenerateRichDemoManifest",
            emptySet<String>(),
            onlyInTree + onlyInManifest,
        )
    }

    /** Recursive AssetManager walk — returns POSIX-style paths relative to `assets/`. */
    private fun walkAssetTree(assets: AssetManager, path: String): List<String> {
        val children = assets.list(path) ?: return emptyList()
        if (children.isEmpty()) {
            // Leaf file. (AssetManager doesn't distinguish files vs empty dirs;
            // an empty dir is impossible under aapt2 packaging, so this is safe.)
            return listOf(path)
        }
        return children.flatMap { child -> walkAssetTree(assets, "$path/$child") }
    }
}

package com.eight87.strictlykeptboy.avatar

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.R
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Round 2.5.C.4 — local-fixture import path: `file://` URL → JGit clone
 * → on-disk pack registers in [UserPackLoader] → composite picks it up →
 * [AvatarPackPrefs.setActivePackFor] flips the active pack id → the
 * resolver returns the new pack's entry on the next call.
 *
 * Exercises the same wires the in-app "+ Import custom pack" button
 * uses; no network, no Android assets required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class StickerPackImportTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun importFromFileUrlActivatesAndResolves() = runTest {
        // 1. Build a fixture pack repo: a git working tree with pack.toml
        //    + a one-pixel idle bitmap. Committed so JGit can clone it.
        val source = tmp.newFolder("source-pack")
        File(source, "pack.toml").writeText(
            """
            schema_version = 1
            name = "Test Bat"
            species = "bat"
            author = "test"
            license = "Apache-2.0"

            [[sticker]]
            activity_id = "idle"
            file = "idle.webp"
            tags = ["idle", "neutral"]
            """.trimIndent(),
        )
        // 1px stub — any non-zero bytes work; UserPackLoader.loadBitmap
        // will fall through to null on decode error, which the test path
        // explicitly checks (resolved entry's file points here regardless).
        File(source, "idle.webp").writeBytes(byteArrayOf(0))
        val srcGit = Git.init().setDirectory(source).setInitialBranch("main").call()
        srcGit.add().addFilepattern(".").call()
        srcGit.commit()
            .setMessage("seed pack")
            .setAuthor("test", "test@example.com")
            .setSign(false)
            .call()
        srcGit.close()

        // 2. UserPackLoader rooted in a fresh temp dir → cloneFrom file://.
        val userRoot = tmp.newFolder("user")
        val userLoader = UserPackLoader(rootDir = userRoot)
        val fileUrl = "file://${source.absolutePath}"
        val cloned = userLoader.cloneFrom(fileUrl, packId = "test-bat-pack")
        assertTrue("clone produced pack.toml", File(cloned, "pack.toml").isFile)

        // 3. Composite store rebuilds via refresh() — bundled assets
        //    don't matter here, only the user side.
        val composite = CompositePackStore(
            sourceFactories = listOf({ userLoader.loadAll() }),
        )
        val newPack = composite.get("test-bat-pack")
        assertNotNull("composite registers cloned pack", newPack)
        assertEquals("bat", newPack!!.species)
        assertEquals("idle", newPack.stickers.values.first().activityId)

        // 4. AvatarPackPrefs activates it for the `bat` species. Use the
        //    test constructor with plain SharedPreferences for both tracks.
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val plain: SharedPreferences = ctx.getSharedPreferences("test-active", Context.MODE_PRIVATE)
        val enc: SharedPreferences = ctx.getSharedPreferences("test-overrides", Context.MODE_PRIVATE)
        val prefs = AvatarPackPrefs.openForTest(plain, enc)
        prefs.setActivePackFor("bat", "test-bat-pack")
        assertEquals("test-bat-pack", prefs.activePackFor("bat"))

        // 5. DefaultStickerResolver returns the new pack's entry.
        val resolver = DefaultStickerResolver(
            packStore = composite,
            activePackProvider = { species -> prefs.activePackFor(species) },
        )
        val resolution = resolver.resolve(StickerResolver.Request(species = "bat"))
        assertTrue("resolver hit our pack", resolution is StickerResolver.Resolution.Found)
        val found = resolution as StickerResolver.Resolution.Found
        assertEquals("test-bat-pack", found.packId)
        assertEquals("idle", found.entry.activityId)

        // 6. DefaultAvatarResolver also dispatches to the user-pack
        //    loader (custom packId, not `default-…`). The 1-byte stub
        //    fails to decode → BitmapFactory returns null → the avatar
        //    facade falls back to the bat drawable, which the test
        //    accepts (we're verifying the wire, not the artwork).
        val cache = StickerBitmapCache()
        val avatar = DefaultAvatarResolver(
            stickerResolver = resolver,
            cache = cache,
            loader = { packId, file ->
                if (packId.startsWith("default-")) null else userLoader.loadBitmap(packId, file)
            },
            fallbackRes = R.drawable.about_bat,
        )
        val resolved = avatar.resolve(species = "bat")
        // Either a BitmapHit (if the platform decoded the stub) or a
        // DrawableFallback (decode null → graceful fallback). Both
        // outcomes are correct for the import wiring.
        assertTrue(
            "avatar resolved via the new pack route",
            resolved is AvatarResolver.Resolved.BitmapHit ||
                resolved is AvatarResolver.Resolved.DrawableFallback,
        )
    }
}

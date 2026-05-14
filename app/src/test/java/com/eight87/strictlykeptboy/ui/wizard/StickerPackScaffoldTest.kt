package com.eight87.strictlykeptboy.ui.wizard

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.git.AuthorIdentity
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/**
 * Phase 2.7.A.4 — verify the wizard scaffold bakes the bundled sticker
 * pack into `stickers/<species>/` and that those files land in the
 * initial git commit.
 *
 * Today (early-Phase-WW state) the bundled packs ship `pack.toml` only —
 * artwork lands in a follow-up. The test therefore asserts on whatever
 * bundled files do exist (currently `pack.toml`) and on byte-for-byte
 * parity with the asset stream. When PNGs land later, the test still
 * passes (it counts all files, not just one extension).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class StickerPackScaffoldTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    private fun listAssetFiles(species: String): List<String> {
        val assets = ctx().assets
        val root = "avatar-packs/$species"
        val children = assets.list(root) ?: emptyArray()
        // Bundled packs are flat (pack.toml + leaf images); flat walk is fine.
        return children.toList()
    }

    private fun bytesFromAsset(species: String, name: String): ByteArray {
        return ctx().assets.open("avatar-packs/$species/$name").use { it.readBytes() }
    }

    @Test fun foxScaffoldShipsReadmeOnly_packCopyDeferredToToggle() = runTest {
        // Round 2.8 — by default, scaffolding writes only stickers/README.md.
        // The bundled pack is NOT copied into the repo unless the user
        // explicitly toggles `RepoConfig.importStickersToRepo` ON later in
        // per-repo Sticker pack settings. This keeps initial repo size small.
        val loader = AssetPackLoader(ctx())
        val draft = WizardDraft(
            alignment = Alignment.UnalignedPrivate,
            roles = setOf(RoleId.SelfCare),
            species = SpeciesChoice.Fox,
            displayName = "fox repo",
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent-fox"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
            assetPackLoader = loader,
        )
        val root = outcome.rootDir.toPath()

        // README is now ALWAYS written (regardless of species — Round 2.8).
        val readme = root.resolve("stickers/README.md")
        assertTrue("stickers/README.md exists for all species", Files.exists(readme))

        // No pack image files in the repo — those are opt-in via the toggle.
        val stickerDir = root.resolve("stickers/fox")
        assertFalse(
            "stickers/fox/ should NOT exist until import-toggle is flipped",
            Files.isDirectory(stickerDir),
        )

        // Initial commit contains the README but not pack files.
        Git.open(outcome.rootDir).use { git ->
            val headId = git.repository.resolve(Constants.HEAD)
            RevWalk(git.repository).use { walk ->
                val commit = walk.parseCommit(headId)
                TreeWalk(git.repository).use { tw ->
                    tw.addTree(commit.tree)
                    tw.isRecursive = true
                    val paths = mutableListOf<String>()
                    while (tw.next()) paths += tw.pathString
                    assertTrue(
                        "initial commit contains stickers/README.md",
                        paths.contains("stickers/README.md"),
                    )
                    assertFalse(
                        "initial commit does not contain stickers/fox/pack.toml (deferred to toggle)",
                        paths.contains("stickers/fox/pack.toml"),
                    )
                }
            }
        }
    }

    @Test fun customScaffoldShipsBatStarter() = runTest {
        val loader = AssetPackLoader(ctx())
        val draft = WizardDraft(
            alignment = Alignment.UnalignedPrivate,
            roles = setOf(RoleId.SelfCare),
            species = SpeciesChoice.ChooseYourOwn,
            displayName = "custom repo",
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent-custom"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
            assetPackLoader = loader,
        )
        val root = outcome.rootDir.toPath()
        // Round 2.8 — README is ALWAYS written (no species-conditional anymore).
        val readme = root.resolve("stickers/README.md")
        assertTrue("stickers/README.md exists for ChooseYourOwn", Files.exists(readme))
        val readmeText = String(Files.readAllBytes(readme), Charsets.UTF_8)
        assertTrue(
            "README mentions Import stickers into repo toggle",
            readmeText.contains("Import stickers into repo"),
        )

        // Pack files are NOT copied — toggle is the entry point.
        assertFalse("no stickers/bat/ until toggle", Files.isDirectory(root.resolve("stickers/bat")))
        assertFalse("no stickers/fox/", Files.isDirectory(root.resolve("stickers/fox")))

        // README lands in the initial commit.
        Git.open(outcome.rootDir).use { git ->
            val headId = git.repository.resolve(Constants.HEAD)
            RevWalk(git.repository).use { walk ->
                val commit = walk.parseCommit(headId)
                TreeWalk(git.repository).use { tw ->
                    tw.addTree(commit.tree)
                    tw.isRecursive = true
                    val paths = mutableListOf<String>()
                    while (tw.next()) paths += tw.pathString
                    assertTrue(
                        "initial commit contains stickers/README.md",
                        paths.contains("stickers/README.md"),
                    )
                }
            }
        }
    }

    private fun assertArrayEqualsMsg(msg: String, expected: ByteArray, actual: ByteArray) {
        assertEquals("$msg — sizes differ", expected.size, actual.size)
        for (i in expected.indices) {
            if (expected[i] != actual[i]) {
                throw AssertionError("$msg — first diff at byte $i")
            }
        }
    }
}

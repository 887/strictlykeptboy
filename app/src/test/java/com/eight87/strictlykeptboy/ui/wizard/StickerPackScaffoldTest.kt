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

    @Test fun foxScaffoldShipsBundledPack() = runTest {
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
        val stickerDir = root.resolve("stickers/fox")
        assertTrue("stickers/fox/ exists", Files.isDirectory(stickerDir))

        // Every bundled file shows up + matches byte-for-byte.
        val bundled = listAssetFiles("fox")
        assertTrue("bundled fox pack non-empty (at minimum pack.toml)", bundled.isNotEmpty())
        for (name in bundled) {
            val onDisk = stickerDir.resolve(name)
            assertTrue("stickers/fox/$name copied", Files.exists(onDisk))
            val diskBytes = Files.readAllBytes(onDisk)
            val assetBytes = bytesFromAsset("fox", name)
            assertArrayEqualsMsg("byte parity for $name", assetBytes, diskBytes)
        }

        // For non-custom species, no stickers/README.md is written.
        assertFalse(
            "stickers/README.md is only emitted for ChooseYourOwn",
            Files.exists(root.resolve("stickers/README.md")),
        )

        // The initial commit includes the sticker files.
        Git.open(outcome.rootDir).use { git ->
            val headId = git.repository.resolve(Constants.HEAD)
            RevWalk(git.repository).use { walk ->
                val commit = walk.parseCommit(headId)
                val tree = commit.tree
                TreeWalk(git.repository).use { tw ->
                    tw.addTree(tree)
                    tw.isRecursive = true
                    val paths = mutableListOf<String>()
                    while (tw.next()) paths += tw.pathString
                    for (name in bundled) {
                        assertTrue(
                            "initial commit contains stickers/fox/$name (have: $paths)",
                            paths.contains("stickers/fox/$name"),
                        )
                    }
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
        // ChooseYourOwn → Bat starter at stickers/bat/.
        val stickerDir = root.resolve("stickers/bat")
        assertTrue("stickers/bat/ exists for ChooseYourOwn", Files.isDirectory(stickerDir))
        val bundled = listAssetFiles("bat")
        assertTrue("bundled bat pack non-empty", bundled.isNotEmpty())
        for (name in bundled) {
            assertTrue("stickers/bat/$name exists", Files.exists(stickerDir.resolve(name)))
        }
        // README is emitted for ChooseYourOwn.
        val readme = root.resolve("stickers/README.md")
        assertTrue("stickers/README.md exists for ChooseYourOwn", Files.exists(readme))
        val readmeText = String(Files.readAllBytes(readme), Charsets.UTF_8)
        assertTrue("README mentions editing", readmeText.contains("Edit"))

        // README lands in the initial commit too.
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
                    assertTrue(
                        "initial commit contains stickers/bat/pack.toml",
                        paths.contains("stickers/bat/pack.toml"),
                    )
                }
            }
        }

        // The fox dir is NOT created when ChooseYourOwn is picked.
        assertFalse("no stickers/fox/", Files.isDirectory(root.resolve("stickers/fox")))
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

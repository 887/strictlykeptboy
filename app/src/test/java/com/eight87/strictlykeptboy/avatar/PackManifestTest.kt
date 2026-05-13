package com.eight87.strictlykeptboy.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackManifestTest {

    @Test
    fun `parses bundled bat manifest`() {
        val toml = """
            schema_version = 1
            name = "Default Bat"
            species = "bat"
            author = "eight87"
            license = "Apache-2.0"
            style = "flat"

            [[sticker]]
            activity_id = "idle"
            file = "idle.webp"
            tags = ["idle", "neutral"]
            animated = false

            [[sticker]]
            activity_id = "workout"
            file = "workout.webp"
            tags = ["workout", "neutral"]
            animated = false
        """.trimIndent()
        val pack = PackManifest.parse(toml, "default-bat")
        assertEquals("default-bat", pack.packId)
        assertEquals("bat", pack.species)
        assertEquals("Default Bat", pack.name)
        assertEquals(2, pack.stickers.size)
        val idle = pack.stickers.getValue("idle")
        assertTrue(idle.tags.contains(StickerTag.Idle))
        assertTrue(idle.tags.contains(StickerTag.Neutral))
        assertEquals("idle.webp", idle.file)
    }

    @Test(expected = PackManifestException::class)
    fun `rejects manifest missing species`() {
        val toml = """
            [[sticker]]
            activity_id = "idle"
            file = "idle.webp"
        """.trimIndent()
        PackManifest.parse(toml, "x")
    }

    @Test(expected = PackManifestException::class)
    fun `rejects manifest with no stickers`() {
        val toml = """
            species = "bat"
            name = "Empty"
        """.trimIndent()
        PackManifest.parse(toml, "x")
    }

    @Test
    fun `pack id derivation is deterministic`() {
        val a = PackId.fromCloneUrl("https://github.com/foo/bar")
        val b = PackId.fromCloneUrl("https://github.com/foo/bar.git/")
        val c = PackId.fromCloneUrl("https://github.com/foo/bar.git")
        // All three normalize to the same value.
        assertEquals(a, b)
        assertEquals(b, c)
        assertEquals(16, a.length)
    }
}

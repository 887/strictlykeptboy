package com.eight87.strictlykeptboy.ui.components

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.ui.theming.RepoIconKind
import com.eight87.strictlykeptboy.ui.theming.initialsFromName
import com.eight87.strictlykeptboy.ui.theming.seedColorFromName
import com.eight87.strictlykeptboy.ui.theming.toIconKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.22 Phase A / F48 close-out — pin the fallback chain locked
 * in decision D-2.22.a. `RepoConfig.toIconKind()` is the single
 * chokepoint that both the top-bar `IdentityAvatar` and the per-repo
 * row `RepoCircle` delegate to; if the chain breaks here, every
 * avatar surface across the app breaks consistently.
 *
 * Chain per D-2.22.a:
 *   1. iconSpecies != null → Sticker(species)
 *      a. Sticker resolver renders bitmap (BitmapHit) — runtime path
 *      b. species == "bat" + DrawableFallback → about_bat — runtime path
 *      c. species != "bat" + DrawableFallback → species[0] monogram
 *         on species-derived seed colour — `StickerBadge` renderer path
 *   2. iconEmoji != null → Emoji(glyph)
 *   3. iconPhotoUri (not yet on RepoConfig; reserved) → Photo(uri)
 *   4. else → AutoInitials(name[0..1], seedColorFromName(displayName))
 *
 * These tests cover the (1)/(2)/(4) branches that live in pure data
 * code; the (1a)/(1b)/(1c) StickerBadge branches require a Composable
 * runtime + `LocalAvatarResolver` injection and are exercised by
 * `RepoIconKindTest` (existing) + AVD smoke.
 */
class IdentityAvatarFallbackTest {

    private fun baseRepo(
        displayName: String = "Personal",
        iconSpecies: String? = null,
        iconEmoji: String? = null,
    ): RepoConfig = RepoConfig(
        repoId = "test-repo",
        displayName = displayName,
        rootDir = "/tmp/test",
        authorIdentity = AuthorIdentity("Tester", "test@example.com"),
        iconSpecies = iconSpecies,
        iconEmoji = iconEmoji,
    )

    @Test fun `species bat resolves to Sticker bat`() {
        val kind = baseRepo(iconSpecies = "bat").toIconKind()
        assertTrue("Expected Sticker, got $kind", kind is RepoIconKind.Sticker)
        assertEquals("bat", (kind as RepoIconKind.Sticker).species)
    }

    @Test fun `species fox resolves to Sticker fox lowercase`() {
        val kind = baseRepo(iconSpecies = "Fox").toIconKind()
        assertTrue(kind is RepoIconKind.Sticker)
        assertEquals("fox", (kind as RepoIconKind.Sticker).species)
    }

    @Test fun `emoji-only repo resolves to Emoji variant`() {
        val kind = baseRepo(iconEmoji = "🦊").toIconKind()
        assertTrue(kind is RepoIconKind.Emoji)
        assertEquals("🦊", (kind as RepoIconKind.Emoji).glyph)
    }

    @Test fun `species wins over emoji when both set`() {
        val kind = baseRepo(iconSpecies = "wolf", iconEmoji = "🐺").toIconKind()
        assertTrue(kind is RepoIconKind.Sticker)
        assertEquals("wolf", (kind as RepoIconKind.Sticker).species)
    }

    @Test fun `no species no emoji falls back to AutoInitials from displayName`() {
        val kind = baseRepo(displayName = "Personal Calendar").toIconKind()
        assertTrue(kind is RepoIconKind.AutoInitials)
        val ai = kind as RepoIconKind.AutoInitials
        assertEquals(initialsFromName("Personal Calendar"), ai.initials)
        assertEquals(seedColorFromName("Personal Calendar"), ai.seedColor)
    }

    @Test fun `AutoInitials seed colour is stable across calls`() {
        val a = baseRepo(displayName = "Personal").toIconKind() as RepoIconKind.AutoInitials
        val b = baseRepo(displayName = "Personal").toIconKind() as RepoIconKind.AutoInitials
        assertEquals(a.seedColor, b.seedColor)
        assertEquals(a.initials, b.initials)
    }

    @Test fun `empty displayName still yields a non-null AutoInitials`() {
        val kind = baseRepo(displayName = "").toIconKind()
        assertTrue(kind is RepoIconKind.AutoInitials)
        val ai = kind as RepoIconKind.AutoInitials
        assertNotNull(ai.initials)
        assertNotNull(ai.seedColor)
    }
}

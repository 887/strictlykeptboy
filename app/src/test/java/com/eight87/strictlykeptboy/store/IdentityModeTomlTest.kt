package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase DDD.16 / DM-Y / DM-Z — round-trip tests for the new toml codecs +
 * review-feed writer + dom-persona store + AGENTS.md byte-identicality
 * across praise-term changes (DDD.10 / HV-O.14).
 */
class IdentityModeTomlTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun identityToml_roundTrips_lockedDefaults() {
        val emitted = IdentityTomlCodec.toToml(IdentityTomlData.LockedDefaults)
        val back = IdentityTomlCodec.fromToml(emitted)
        assertEquals(IdentityTomlData.LockedDefaults, back)
        assertEquals("good boy", back.praiseTerm)
        assertEquals(IdentityPronouns.HeHim, back.pronouns)
        assertEquals("Sir", back.honorificForDom)
        assertEquals("soft-kinky", back.toneRegister)
        assertEquals("medium", back.emojiDensity)
    }

    @Test fun identityToml_roundTrips_withAltTermsAndExtraSets() {
        val data = IdentityTomlData(
            praiseTerm = "pup",
            altTerms = listOf("good pup", "puppy"),
            pronouns = IdentityPronouns.TheyThem,
            pronounsExtra = listOf(IdentityPronouns.HeHim),
            honorificForDom = "Daddy",
            toneRegister = "playful",
            emojiDensity = "lush",
        )
        val back = IdentityTomlCodec.fromToml(IdentityTomlCodec.toToml(data))
        assertEquals(data, back)
    }

    @Test(expected = IllegalArgumentException::class)
    fun identityToml_rejectsBlankPraiseTerm() {
        IdentityTomlData(praiseTerm = "  ")
    }

    @Test fun modeToml_defaultIsFree() {
        val emitted = ModeTomlCodec.toToml(ModeTomlData.Default)
        val back = ModeTomlCodec.fromToml(emitted)
        assertEquals(RepoMode.Free, back.mode)
    }

    @Test fun modeToml_roundTripsKeptWithFullFields() {
        val data = ModeTomlData(
            mode = RepoMode.StrictlyKept,
            writeBackTarget = "https://forgejo/example/repo.git",
            domPersona = "stern-but-fair",
            domCadence = DomCadenceWire.Realtime,
            keptSince = "2026-05-13T12:00:00+02:00",
            calendarOverrides = mapOf("cal-abc" to RepoMode.Free),
        )
        val back = ModeTomlCodec.fromToml(ModeTomlCodec.toToml(data))
        assertEquals(data.mode, back.mode)
        assertEquals(data.writeBackTarget, back.writeBackTarget)
        assertEquals(data.domPersona, back.domPersona)
        assertEquals(data.domCadence, back.domCadence)
        assertEquals(data.keptSince, back.keptSince)
        assertEquals(RepoMode.Free, back.resolveFor("cal-abc"))
        assertEquals(RepoMode.StrictlyKept, back.resolveFor("cal-other"))
    }

    @Test fun modeToml_writeAndReadOnDisk() {
        val root = tmp.newFolder().toPath()
        ModeTomlCodec.write(root, ModeTomlData(mode = RepoMode.StrictlyKept))
        val read = ModeTomlCodec.readOrDefault(root)
        assertEquals(RepoMode.StrictlyKept, read.mode)
    }

    @Test fun reviewFeedWriter_writesEntryAndCreatesResponsesDir() {
        val root = tmp.newFolder().toPath()
        IdentityTomlCodec.write(root, IdentityTomlData.LockedDefaults)
        val entry = ReviewFeedWriter.Entry(
            commitSha = "abcdef1234567890abcdef1234567890abcdef12",
            author = "good boy",
            timestamp = "2026-05-13T12:00:00+02:00",
            changedPaths = listOf(
                ReviewFeedWriter.ChangedPath("calendars/x/events/2026/05/y.md", ReviewFeedWriter.PathFamily.EventEdit),
            ),
            diffHunks = "+ new line\n",
        )
        val target = ReviewFeedWriter.writeReviewableChange(root, entry, IdentityTomlData.LockedDefaults)
        assertTrue(java.nio.file.Files.isRegularFile(target))
        val responsesDir = root.resolve("reviews/${entry.commitSha}/responses")
        assertTrue(java.nio.file.Files.isDirectory(responsesDir))
    }

    @Test fun autoSummary_usesPraiseTerm_DM_Z_3() {
        val identity = IdentityTomlData.LockedDefaults.copy(praiseTerm = "pup")
        val entry = ReviewFeedWriter.Entry(
            commitSha = "x",
            author = "pup",
            timestamp = "2026-05-13T00:00:00Z",
            changedPaths = listOf(
                ReviewFeedWriter.ChangedPath("calendars/c/events/2026/05/a.md", ReviewFeedWriter.PathFamily.EventEdit),
                ReviewFeedWriter.ChangedPath("mode.toml", ReviewFeedWriter.PathFamily.ModeFlip),
            ),
            diffHunks = "",
        )
        val summary = ReviewFeedWriter.autoSummary(entry, identity)
        assertTrue(summary.startsWith("pup"))
        assertTrue(summary.contains("flipped the mode"))
    }

    @Test fun classifyPath_total() {
        assertEquals(ReviewFeedWriter.PathFamily.ModeFlip, ReviewFeedWriter.classifyPath("mode.toml"))
        assertEquals(ReviewFeedWriter.PathFamily.IdentityEdit, ReviewFeedWriter.classifyPath("identity.toml"))
        assertEquals(ReviewFeedWriter.PathFamily.EventEdit, ReviewFeedWriter.classifyPath("calendars/c/events/2026/05/x.md"))
        assertEquals(ReviewFeedWriter.PathFamily.Recurrence, ReviewFeedWriter.classifyPath("calendars/c/recurrences/r.md"))
        assertEquals(ReviewFeedWriter.PathFamily.Deviation, ReviewFeedWriter.classifyPath("todolists/t/deviations/d/2026-05-12.md"))
        assertEquals(ReviewFeedWriter.PathFamily.Attachment, ReviewFeedWriter.classifyPath("attachments/foo.png"))
    }

    @Test fun reviewResponseWriter_emitsResponseFile() {
        val root = tmp.newFolder().toPath()
        val response = ReviewResponseWriter.Response(
            commitSha = "abc",
            reactions = listOf("good-boy"),
            responderFingerprint = "dom-fp-1",
            responderLabel = "Sir",
            created = "2026-05-13T12:00:00+02:00",
            bodyMarkdown = "",
        )
        assertTrue(response.isCuteCodedLgtm)
        val target = ReviewResponseWriter.write(root, response)
        assertTrue(java.nio.file.Files.isRegularFile(target))
    }

    @Test(expected = IllegalArgumentException::class)
    fun reviewResponseWriter_rejectsUnknownReaction() {
        ReviewResponseWriter.Response(
            commitSha = "abc",
            reactions = listOf("nope"),
            responderFingerprint = "fp",
            responderLabel = "Sir",
        )
    }

    @Test fun domPersonaStore_listsBuiltins() {
        val home = tmp.newFolder().toPath().toString()
        DomPersonaStore.ensureBuiltins(home)
        val list = DomPersonaStore.list(home)
        assertEquals(6, list.size)
        assertNotNull(list.firstOrNull { it.id == "stern-but-fair" })
    }

    @Test fun domPersonaStore_customSlotAppears() {
        val home = tmp.newFolder().toPath().toString()
        DomPersonaStore.writeCustom(home, "you are a loving partner")
        val list = DomPersonaStore.list(home)
        assertEquals(7, list.size)
        assertNotNull(list.firstOrNull { it.id == DomPersonaStore.CUSTOM_ID })
    }
}

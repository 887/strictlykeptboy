package com.eight87.strictlykeptboy.ui.trip

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.time.LocalDate

/**
 * Phase CCC.12 / HV-I.4 / HV-I.5 — trip materializer unit tests.
 *
 * Uses the production template assets from `app/src/main/assets/templates/`
 * via a filesystem-backed [TripScaffolder.AssetReader] so the parser walks
 * the real HV-B / HV-C / HV-D shapes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class TripScaffolderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val assetsRoot: File by lazy {
        File("app/src/main/assets")
            .takeIf { it.exists() }
            ?: File("src/main/assets")
    }

    private val assets = TripScaffolder.AssetReader { path ->
        val f = File(assetsRoot, path)
        if (!f.exists()) error("missing template asset: ${f.absolutePath}")
        Files.newInputStream(f.toPath())
    }

    private suspend fun seedEmptyRepo(): Pair<File, String> {
        val rootDir = tmp.newFolder("repo")
        val repoId = "test-repo"
        val author = AuthorIdentity("tester", "tester@example.com")
        val repo = GitRepo.initLocalOnly(rootDir, repoId, author)
        // Need at least one commit on main so subsequent commits don't trip.
        File(rootDir, "README.md").writeText("seed")
        repo.commitAll("seed")
        repo.close()
        return rootDir to repoId
    }

    @Test fun `materialize Sicily flight trip writes prep flight daily and commits`() = runTest {
        val (rootDir, repoId) = seedEmptyRepo()
        val draft = TripDraft(
            name = "Sicily 2026",
            destination = "Catania",
            startDate = LocalDate.of(2026, 6, 12),
            endDate = LocalDate.of(2026, 6, 19),
            transport = TransportMode.Flight,
            travelerCount = 2,
            packKinkKit = false,
            includeVacationDaily = true,
        )
        val outcome = TripScaffolder.materialize(
            repoRoot = rootDir,
            draft = draft,
            assets = assets,
            author = AuthorIdentity("me", "me@example.com"),
            repoId = repoId,
            tzId = "Europe/Berlin",
        )

        assertTrue("prep events written", outcome.prepEventCount > 0)
        assertTrue("flight events written", outcome.flightEventCount > 0)
        assertNotNull("daily rule materialized", outcome.vacationDailyRuleId)

        val tripDir = rootDir.toPath().resolve("calendars/${outcome.tripCalendarId}")
        assertTrue(Files.exists(tripDir.resolve("calendar.toml")))
        val calToml = String(Files.readAllBytes(tripDir.resolve("calendar.toml")), Charsets.UTF_8)
        assertTrue(calToml.contains("Sicily 2026"))
        assertTrue(calToml.contains("Catania"))
        assertTrue(calToml.contains("flight"))
        assertTrue(calToml.contains("trip-overlay"))

        // HV-I.4 — back-fill math: passport-validity is at T-42.
        // Trip start 2026-06-12 minus 42 days = 2026-05-01. Verify an event
        // exists in calendars/<trip>/events/2026/05/.
        val eventsDir = tripDir.resolve("events")
        assertTrue(Files.isDirectory(eventsDir))
        val mayFiles = Files.walk(eventsDir).filter { p ->
            p.toString().contains("/2026/05/")
        }.use { it.count() }
        assertTrue("at least one event back-filled into May", mayFiles > 0L)

        // A recurring rule (vacation-daily) exists with the trip window bound.
        val recurDir = tripDir.resolve("recurrences")
        assertTrue(Files.isDirectory(recurDir))
        val rules = Files.list(recurDir).use { it.toList() }
        assertTrue("at least one vacation-daily rule", rules.isNotEmpty())
        val firstRuleText = String(Files.readAllBytes(rules.first()), Charsets.UTF_8)
        assertTrue("RRULE present", firstRuleText.contains("FREQ=DAILY"))
        assertTrue("UNTIL bounded", firstRuleText.contains("UNTIL=20260619"))
    }

    @Test fun `materialize car trip skips flight events`() = runTest {
        val (rootDir, repoId) = seedEmptyRepo()
        val draft = TripDraft(
            name = "Black Forest weekend",
            destination = "Freiburg",
            startDate = LocalDate.of(2026, 8, 1),
            endDate = LocalDate.of(2026, 8, 3),
            transport = TransportMode.Car,
            travelerCount = 2,
        )
        val outcome = TripScaffolder.materialize(
            repoRoot = rootDir,
            draft = draft,
            assets = assets,
            author = AuthorIdentity("me", "me@example.com"),
            repoId = repoId,
        )
        assertEquals(0, outcome.flightEventCount)
        assertTrue("prep still materialized for non-flight", outcome.prepEventCount > 0)
    }

    @Test fun `incomplete draft rejected`() = runTest {
        val (rootDir, repoId) = seedEmptyRepo()
        val draft = TripDraft(name = "", startDate = null, endDate = null)
        var threw = false
        try {
            TripScaffolder.materialize(
                repoRoot = rootDir,
                draft = draft,
                assets = assets,
                author = AuthorIdentity("me", "me@example.com"),
                repoId = repoId,
            )
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("incomplete draft threw", threw)
    }

    @Test fun `includeVacationDaily false skips recurring rule`() = runTest {
        val (rootDir, repoId) = seedEmptyRepo()
        val draft = TripDraft(
            name = "Quick trip",
            destination = "Hamburg",
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 3),
            transport = TransportMode.Train,
            includeVacationDaily = false,
        )
        val outcome = TripScaffolder.materialize(
            repoRoot = rootDir,
            draft = draft,
            assets = assets,
            author = AuthorIdentity("me", "me@example.com"),
            repoId = repoId,
        )
        assertEquals(null, outcome.vacationDailyRuleId)
        val tripDir = rootDir.toPath().resolve("calendars/${outcome.tripCalendarId}")
        val recurDir = tripDir.resolve("recurrences")
        // recurDir may exist or not depending on whether anything wrote to it;
        // if it exists, it must be empty.
        if (Files.isDirectory(recurDir)) {
            val rules = Files.list(recurDir).use { it.toList() }
            assertEquals(0, rules.size)
        }
    }
}

class TripStickerBeatsTest {
    @org.junit.Test fun `every screen has a beat`() {
        val screens = TripScreen.entries
        for (s in screens) {
            val beat = TripStickerBeats.beatFor(s)
            org.junit.Assert.assertEquals(s, beat.screen)
            org.junit.Assert.assertTrue(beat.kinkKey.isNotBlank())
        }
    }

    @org.junit.Test fun `anchors and confirm have neutral variants`() {
        org.junit.Assert.assertEquals(
            "beach-loungin",
            TripStickerBeats.beatFor(TripScreen.Anchors).neutralKey,
        )
        org.junit.Assert.assertEquals(
            "staying-on-track",
            TripStickerBeats.beatFor(TripScreen.Confirm).neutralKey,
        )
    }
}

package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Phase AAA.9 / HV-I + HV-O — content + envelope tests for the 8 new
 * lifestyle templates (HV-A household, HV-B travel-prep, HV-C flight-
 * day, HV-D vacation-daily, HV-K ADHD anchors, HV-L.A medication,
 * HV-L.B menstrual-cycle, HV-P leisure).
 *
 * Goal: each shipped TOML parses round-trip via [AtomicTemplateLoader];
 * every entry's sub-beat sum stays within its envelope (AT-D.4); the
 * neutral-mode rendering produces a non-empty string; kink variants
 * cross-reference back to a real base entry (HV-A.17); the
 * `nonSuperseable` / `privacy_flag` flags surface correctly.
 *
 * The asset path is the working-directory-relative `app/src/main/assets`
 * tree (where the file actually lives) — the unit test classpath does
 * not include arbitrary asset files.
 */
class LifestyleTemplatesParseTest {

    private fun loadAsset(name: String): AtomicTemplate {
        val candidates = listOf(
            File("app/src/main/assets/templates/$name"),
            File("src/main/assets/templates/$name"),
            File("../app/src/main/assets/templates/$name"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("template asset $name not found; tried: $candidates")
        return AtomicTemplateLoader.parse(String(Files.readAllBytes(file.toPath()), Charsets.UTF_8))
    }

    @Test fun householdParsesAndCarriesAllCategories() {
        val t = loadAsset("atomic-household.toml")
        assertEquals("atomic-household", t.templateId)
        assertTrue(t.neutralSafe)
        assertFalse(t.parameterized)
        // HV-A: ~88 base entries. We assert a lower-bound to stay
        // resilient to additive edits (and the spec calls out "+ 4 kink
        // variants" separately on the variants array).
        assertTrue("expected ≥ 80 entries, got ${t.entries.size}", t.entries.size >= 80)
        assertTrue("expected ≥ 4 kink variants, got ${t.variants.size}", t.variants.size >= 4)
        // Every entry has a neutral_title (HV-A.1 LOCKED).
        for (e in t.entries) {
            assertNotNull("entry ${e.id} missing neutral_title", e.neutralTitle)
            assertTrue("entry ${e.id} sub-beat envelope", e.subbeatTotalFitsEnvelope())
        }
        // Pet entries are nonSuperseable (D.76).
        val pet = t.entry("pet-feed")!!
        assertTrue("pet-feed nonSuperseable", pet.nonSuperseable)
        // Kink variants point at real base entries.
        for (v in t.variants) {
            assertNotNull(
                "variant ${v.kinkVariantOf} not found in entries",
                t.entry(v.kinkVariantOf),
            )
        }
    }

    @Test fun travelPrepParsesAsParameterizedWithLeadOffsets() {
        val t = loadAsset("atomic-travel-prep.toml")
        assertEquals("atomic-travel-prep", t.templateId)
        assertTrue(t.parameterized)
        assertTrue("travel-prep entries >= 50", t.entries.size >= 50)
        // Every travel-prep entry must carry a lead_offset_days.
        for (e in t.entries) {
            assertNotNull("entry ${e.id} missing lead_offset_days", e.leadOffsetDays)
        }
        // pack-medication is nonSuperseable per HV-B.7.
        assertTrue(
            "pack-medication nonSuperseable",
            t.entry("pack-medication")!!.nonSuperseable,
        )
        // pack-kink-kit carries privacy_flag.
        assertTrue("pack-kink-kit privacy_flag", t.entry("pack-kink-kit")!!.privacyFlag)
    }

    @Test fun flightDayParsesWithOffsetAxes() {
        val t = loadAsset("atomic-flight-day.toml")
        assertTrue(t.parameterized)
        assertEquals(19, t.entries.size)
        // At least one entry must reference each offset axis.
        assertTrue(t.entries.any { it.offsetMinutesFromDeparture != null })
        assertTrue(t.entries.any { it.offsetMinutesFromArrival != null })
    }

    @Test fun vacationDailyHas15Anchors() {
        val t = loadAsset("atomic-vacation-daily.toml")
        assertEquals("atomic-vacation-daily", t.templateId)
        assertEquals(15, t.entries.size)
        // Four kink anchors carry privacy_flag.
        val kinkAnchors = t.entries.filter { it.privacyFlag }
        assertEquals(4, kinkAnchors.size)
    }

    @Test fun adhdAnchorsParsesAllSixCategories() {
        val t = loadAsset("atomic-adhd-anchors.toml")
        assertEquals("atomic-adhd-anchors", t.templateId)
        assertTrue("adhd entries >= 30", t.entries.size >= 30)
        // hyperfocus-recovery present (HV-K.8 LOCKED).
        assertNotNull(t.entry("hyperfocus-recovery"))
        // Wind-down sub-beats sum to <= envelope.
        val windDown = t.entry("wind-down-routine-start")!!
        assertTrue("wind-down envelope", windDown.subbeatTotalFitsEnvelope())
    }

    @Test fun medicationAllPrivacyFlaggedAndNonSuperseable() {
        val t = loadAsset("atomic-medication.toml")
        assertEquals("atomic-medication", t.templateId)
        assertTrue("medication entries >= 20", t.entries.size >= 20)
        // Per HV-L.A.1 + D.76: every entry privacy + nonSuperseable.
        for (e in t.entries) {
            assertTrue("medication entry ${e.id} privacy_flag", e.privacyFlag)
            assertTrue("medication entry ${e.id} nonSuperseable", e.nonSuperseable)
        }
    }

    @Test fun menstrualCycleSurfacesPrivateAnchors() {
        val t = loadAsset("atomic-menstrual-cycle.toml")
        assertEquals("atomic-menstrual-cycle", t.templateId)
        assertEquals(12, t.entries.size)
        // period-day-1 cycle anchor is private.
        assertTrue(t.entry("period-day-1")!!.privacyFlag)
        // supplies-restock is the ONE non-private entry (HV-L.B.4).
        assertFalse(t.entry("supplies-restock")!!.privacyFlag)
    }

    @Test fun leisureScaffoldsAllEightCategories() {
        val t = loadAsset("atomic-leisure.toml")
        assertEquals("atomic-leisure", t.templateId)
        assertTrue("leisure entries >= 40", t.entries.size >= 40)
        // dog-walk has 7 sub-beats (HV-P.10 LOCKED).
        val dogWalk = t.entry("dog-walk")!!
        assertEquals(7, dogWalk.subbeats.size)
        assertTrue(dogWalk.subbeatTotalFitsEnvelope())
        // caged-play-affordances entries carry the kink tag.
        val cageCheck = t.entry("cage-comfort-check")!!
        assertTrue("cage-comfort-check kink-tagged", "kink" in cageCheck.tags)
    }

    @Test fun neutralModeRendersNeutralTitle() {
        val t = loadAsset("atomic-household.toml")
        val e = t.entry("bed-make")!!
        // Neutral mode prefers neutral_title.
        assertEquals("Make the bed", e.renderTitle(neutralMode = true))
        // Kink mode uses base title.
        assertEquals("good boy makes the bed", e.renderTitle(neutralMode = false))
        // Kink mode + variant uses variant title.
        val variant = t.variantFor("bed-make")
        assertNotNull(variant)
        assertEquals(
            "good boy makes Sir's bed",
            e.renderTitle(neutralMode = false, variant = variant),
        )
    }

    @Test fun allShippedTemplatesParse() {
        for (name in TemplateCatalog.ALL.map { "${it.first}.toml" }) {
            val t = loadAsset(name)
            assertNotNull("template $name", t)
            for (e in t.entries) {
                assertTrue("entry ${e.id} in $name fits envelope", e.subbeatTotalFitsEnvelope())
            }
        }
    }
}

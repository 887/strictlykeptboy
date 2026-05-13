package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase FFF / EC-G.2 — picker index merge + neutral-mode filter.
 *
 * Pure JVM. Exercises [TemplateIndexLoader.parse], [TemplateMerger.merge],
 * [TemplateMerger.filter], [TemplateMerger.group].
 */
class TemplateIndexMergeTest {

    @Test fun parse_index_returns_entries() {
        val toml = """
            schema_version = 1

            [[template]]
            template_id = "a"
            display_name = "Alpha"
            asset_file = "a.toml"
            category = "self-care"
            neutral_safe = true
            tags = ["foo", "bar"]
            aliases = ["alfa"]

            [[template]]
            template_id = "b"
            display_name = "Beta"
            asset_file = "b.toml"
            category = "kink"
            neutral_safe = false
            tags = ["kink"]
        """.trimIndent()
        val entries = TemplateIndexLoader.parse(toml)
        assertEquals(2, entries.size)
        assertEquals("a", entries[0].templateId)
        assertEquals(TemplateSource.Shipped, entries[0].source)
    }

    @Test fun merge_user_overrides_shipped_by_id() {
        val shipped = listOf(
            TemplateEntry("a", "Shipped A", "self-care", emptyList(), emptyList(), true, TemplateSource.Shipped),
        )
        val user = listOf(
            TemplateEntry("a", "User A", "self-care", emptyList(), emptyList(), true, TemplateSource.User),
        )
        val merged = TemplateMerger.merge(shipped, user, emptyList())
        assertEquals(1, merged.size)
        assertEquals("User A", merged[0].displayName)
        assertEquals(TemplateSource.User, merged[0].source)
    }

    @Test fun neutral_mode_filters_kink_tag() {
        val entries = listOf(
            TemplateEntry("a", "Brush", "self-care", listOf("self-care"), emptyList(), true, TemplateSource.Shipped),
            TemplateEntry("b", "Play", "kink", listOf("kink"), emptyList(), false, TemplateSource.Shipped),
        )
        val filteredNeutral = TemplateMerger.filter(entries, "", neutralMode = true)
        assertEquals(1, filteredNeutral.size)
        assertEquals("a", filteredNeutral[0].templateId)
        val filteredKink = TemplateMerger.filter(entries, "", neutralMode = false)
        assertEquals(2, filteredKink.size)
    }

    @Test fun search_matches_displayName_tags_aliases() {
        val entries = listOf(
            TemplateEntry("a", "Brush", "self-care", listOf("hygiene"), listOf("morning"), true, TemplateSource.Shipped),
        )
        assertTrue(TemplateMerger.filter(entries, "brush", false).isNotEmpty())
        assertTrue(TemplateMerger.filter(entries, "hygiene", false).isNotEmpty())
        assertTrue(TemplateMerger.filter(entries, "morn", false).isNotEmpty())
        assertFalse(TemplateMerger.filter(entries, "zzz", false).isNotEmpty())
    }

    @Test fun group_sorts_sections_stably() {
        val entries = listOf(
            TemplateEntry("a", "Workout", "workout", emptyList(), emptyList(), true, TemplateSource.Shipped),
            TemplateEntry("b", "Brush", "self-care", emptyList(), emptyList(), true, TemplateSource.Shipped),
            TemplateEntry("c", "MyOwn", "x", emptyList(), emptyList(), true, TemplateSource.User),
        )
        val sections = TemplateMerger.group(entries)
        // Expect Self-care first, then Workout, then User
        assertEquals(SectionKind.SelfCare, sections[0].kind)
        assertEquals(SectionKind.Workout, sections[1].kind)
        assertEquals(SectionKind.User, sections[2].kind)
    }

    @Test fun pack_section_grouped_by_packId() {
        val entries = listOf(
            TemplateEntry("a", "P-A", "x", emptyList(), emptyList(), true, TemplateSource.Pack("packA")),
            TemplateEntry("b", "P-B", "x", emptyList(), emptyList(), true, TemplateSource.Pack("packA")),
            TemplateEntry("c", "Q-A", "x", emptyList(), emptyList(), true, TemplateSource.Pack("packB")),
        )
        val sections = TemplateMerger.group(entries)
        val packSections = sections.filter { it.kind == SectionKind.Pack }
        assertEquals(2, packSections.size)
        val byPack = packSections.associateBy { it.packId }
        assertEquals(2, byPack["packA"]?.entries?.size)
        assertEquals(1, byPack["packB"]?.entries?.size)
        assertNotNull(byPack["packA"])
    }
}

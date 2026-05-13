package com.eight87.strictlykeptboy.store

/**
 * Phase AAA — `template_origin` + `template_slot` frontmatter conventions.
 *
 * Every recurrence rule / event materialized from a lifestyle template
 * carries two tags in its frontmatter `tags` array:
 *
 *   tags = [
 *     "template_origin:<materializer>",     // who put this on the calendar
 *     "template_slot:<template-id>/<entry-id>",  // which template + entry
 *     ...
 *   ]
 *
 * Why two fields rather than one:
 *  - `template_origin` answers "did the wizard scaffold this, or did the
 *    quick-trip wizard, or did the user via the in-app picker, or the
 *    CLI?". Drives the LW-L re-run-from-Settings idempotency check
 *    (we only refresh entries whose origin matches the re-run surface;
 *    user-edits via the picker survive untouched).
 *  - `template_slot` answers "which atomic from which template is this".
 *    The pair `<template-id>/<entry-id>` is unique enough to dedupe on
 *    re-runs and to power `skb template reset <template-id>` (CLI-U).
 *
 * Both values are LOWERCASE kebab-case. Spaces become `-`. The
 * `template-id` half is the `template_id` field from the asset TOML
 * (e.g. `atomic-household`); the `entry-id` half is the `[[entry]].id`.
 *
 * The wizard, the quick-trip wizard, and the CLI all funnel through
 * [TemplateOrigin.tagsFor] so the format stays single-sourced.
 */
object TemplateOrigin {

    /** Wizard-scaffolded recurrences (Phase K / LW-I). */
    const val WIZARD: String = "wizard"

    /** Re-run-from-Settings refresh (LW-L). Same template, different origin
     *  so existing user-edited copies are preserved across re-runs. */
    const val WIZARD_RERUN: String = "wizard-rerun"

    /** Quick-trip wizard (Phase CCC / HV-F) — vacation overlay calendars. */
    const val TRIP_WIZARD: String = "trip-wizard"

    /** In-app template picker (Phase FFF) — user opt-in materialization. */
    const val USER_PICKER: String = "user-picker"

    /** CLI `skb template apply` (Phase AAA / CLI-U). */
    const val CLI: String = "cli"

    /**
     * Build the conventional `["template_origin:…", "template_slot:…/…"]`
     * pair plus any extra caller-supplied tags.
     */
    fun tagsFor(
        origin: String,
        templateId: String,
        entryId: String,
        extra: List<String> = emptyList(),
    ): List<String> = buildList {
        add("template_origin:$origin")
        add("template_slot:$templateId/$entryId")
        addAll(extra)
    }

    /** Parse `template_slot:atomic-household/bed-make` → (`atomic-household`, `bed-make`). */
    fun parseSlot(tag: String): Pair<String, String>? {
        if (!tag.startsWith("template_slot:")) return null
        val payload = tag.removePrefix("template_slot:")
        val slash = payload.indexOf('/')
        if (slash <= 0 || slash == payload.length - 1) return null
        return payload.substring(0, slash) to payload.substring(slash + 1)
    }

    /** Parse `template_origin:wizard` → `"wizard"`. */
    fun parseOrigin(tag: String): String? =
        if (tag.startsWith("template_origin:")) tag.removePrefix("template_origin:") else null

    /**
     * LW-L re-run idempotency predicate: a given recurrence's `tags` list
     * "matches" a slot iff both the `template_origin` and `template_slot`
     * tags resolve. Used to skip re-scaffolding entries the user has
     * customised under a different origin (e.g. `user-picker`).
     */
    fun matchesSlot(tags: List<String>, templateId: String, entryId: String): Boolean {
        val slot = "template_slot:$templateId/$entryId"
        return tags.any { it == slot }
    }
}

/**
 * Catalog of asset paths for every lifestyle template shipped under
 * `app/src/main/assets/templates/`. Phase AAA enumeration; the CLI's
 * `skb template list` reads this list, the wizard's [TemplateRegistry]
 * dispatches against `templateId` here.
 */
object TemplateCatalog {

    /** Pair of (template_id, asset path). Order is the catalog display order. */
    val ALL: List<Pair<String, String>> = listOf(
        "atomic-self-care" to "templates/atomic-self-care.toml",
        "atomic-kink-self-care" to "templates/atomic-kink-self-care.toml",
        "atomic-workout" to "templates/atomic-workout.toml",
        "atomic-household" to "templates/atomic-household.toml",
        "atomic-travel-prep" to "templates/atomic-travel-prep.toml",
        "atomic-flight-day" to "templates/atomic-flight-day.toml",
        "atomic-vacation-daily" to "templates/atomic-vacation-daily.toml",
        "atomic-adhd-anchors" to "templates/atomic-adhd-anchors.toml",
        "atomic-medication" to "templates/atomic-medication.toml",
        "atomic-menstrual-cycle" to "templates/atomic-menstrual-cycle.toml",
        "atomic-leisure" to "templates/atomic-leisure.toml",
    )

    /** Phase AAA additions only (the 8 templates introduced here). */
    val PHASE_AAA: List<Pair<String, String>> = ALL.drop(3)

    /** Templates always scaffolded per D.87 (leisure first-class). */
    val ALWAYS_SCAFFOLDED: Set<String> = setOf("atomic-leisure")

    /** Parameterized templates per HV-B / HV-C — wizard must resolve params. */
    val PARAMETERIZED: Set<String> = setOf("atomic-travel-prep", "atomic-flight-day")

    fun assetPath(templateId: String): String? =
        ALL.firstOrNull { it.first == templateId }?.second
}

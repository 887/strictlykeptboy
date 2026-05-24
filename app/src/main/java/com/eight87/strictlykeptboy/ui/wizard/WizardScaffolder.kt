package com.eight87.strictlykeptboy.ui.wizard

import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.store.DomCadenceWire
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.IdentityTomlCodec
import com.eight87.strictlykeptboy.store.IdentityTomlData
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.RepoBootstrap
import com.eight87.strictlykeptboy.store.ModeTomlCodec
import com.eight87.strictlykeptboy.store.ModeTomlData
import com.eight87.strictlykeptboy.store.RepoMode
import com.eight87.strictlykeptboy.store.StandingTask
import com.eight87.strictlykeptboy.store.TemplateOrigin
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Phase K.9 / LW-I — single-shot disk materialization driven by the wizard's
 * accumulated [WizardDraft]. All disk writes for the wizard live here.
 *
 * Order of operations:
 *   1. Bootstrap the repo skeleton (RepoBootstrap.scaffold) — AGENTS.md /
 *      CLAUDE.md / identity.toml / .strictlykeptboy / per-role calendars
 *      and the onboarding todolist.
 *   2. Per role: materialize a calendar.toml refresh with the locked
 *      priority/emoji from the registry (LW-I.4).
 *   3. Per enabled atomic template: emit one [RecurrenceRule] with a daily
 *      RRULE (LW-I.5). v1 uses a generic 09:00 dtstart + 15-minute duration
 *      for every atom — fine-tuning per-atom recurrence specs is a follow-up.
 *   4. Seed five onboarding standing tasks (LW-I.6).
 *   5. Initialize git (phone-only via [GitRepo.initLocalOnly]; remote paths
 *      stubbed for v1 — the wizard advertises "OAuth deferred" copy and
 *      falls back to phone-only on Screen 7 OAuth pseudo-success).
 *   6. Single initial commit.
 */
object WizardScaffolder {

    data class Outcome(
        val repoId: String,
        val rootDir: File,
        val calendarIds: Map<RoleId, String>,
        val todolistId: String,
        val recurrencesWritten: Int,
        val standingTasksWritten: Int,
        val authorIdentity: AuthorIdentity,
        /**
         * Walkthrough-2 enrichment counters. See [WizardBaseLayersScaffolder].
         * `null` only in the (defensive) case that the enrichment step was
         * skipped — today it always runs after Step 4.
         */
        val baseLayers: WizardBaseLayersScaffolder.Outcome? = null,
    )

    /**
     * @param parentDir the **strictlykeptboy parent folder** under which
     *   the new repo dir gets created. Round 2.17.C.1: callers now pass
     *   the canonical parent — `graph.repoStoragePrefs.location.workingDir(filesDir)`
     *   for production, `filesDir/demo-repos/...` for the demo seeder
     *   (demos are app-private by design and intentionally bypass the
     *   user-facing parent). The scaffolder writes `<parent>/<repoId>/`
     *   inside whatever directory it is handed; it does NOT itself
     *   resolve `filesDir/strictlykeptboy` or the SAF cache path.
     * @param draft normalized [WizardDraft]
     * @param author committer identity (Name <email>)
     * @param tzId default timezone for new calendars
     * @param now optional clock for tests (epoch-millis source)
     * @param assetPackLoader Phase 2.7.A — when supplied, bundled sticker
     *   pack for `draft.species` is copied into `stickers/<species>/`
     *   before the initial commit so the pack lands in git history.
     *   Passed as a parameter (not a constructor dep) because
     *   WizardScaffolder is an `object`; injecting per-call keeps the
     *   existing test surface (Robolectric runs that don't need stickers
     *   can pass null) while letting MainActivity wire `graph.assetPackLoader`.
     */
    suspend fun materialize(
        parentDir: File,
        draft: WizardDraft,
        author: AuthorIdentity = AuthorIdentity("me", "me@example.com"),
        tzId: String = ZoneId.systemDefault().id,
        now: () -> OffsetDateTime = { OffsetDateTime.now().withNano(0) },
        assetPackLoader: AssetPackLoader? = null,
    ): Outcome = withContext(Dispatchers.IO) {
        val safeName = draft.displayName.ifBlank { "my-calendar" }
            .lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-')
            .ifBlank { "my-calendar" }
        val rootDir = File(parentDir, "$safeName-${UUID.randomUUID().toString().take(8)}")
        rootDir.mkdirs()

        val normalized = draft.normalize()

        // Step 1 — scaffold repo skeleton with one calendar per selected role.
        // Role order is stable per RoleId.entries iteration.
        val orderedRoles: List<RoleId> = RoleId.entries.filter { it in normalized.roles }
        val seedCalendarNames = orderedRoles.map { "cal-${it.id}" }
        val onboardingTodolist = "todo-onboarding"
        val praiseTerm = normalized.praiseTerms.firstOrNull() ?: "good boy"
        val pronounSetForBootstrap = RepoBootstrap.PronounSet(
            subject = normalized.pronouns.subject,
            obj = normalized.pronouns.obj,
            possessive = normalized.pronouns.possessive,
            reflexive = normalized.pronouns.reflexive,
        )
        val scaffold = RepoBootstrap.scaffold(
            rootDir = rootDir,
            spec = RepoBootstrap.Spec(
                repoName = draft.displayName.ifBlank { "my calendar" },
                tzId = tzId,
                identity = author,
                seedCalendars = seedCalendarNames,
                seedTodolists = listOf(onboardingTodolist),
                praiseTerm = praiseTerm,
                pronouns = pronounSetForBootstrap,
            ),
        )

        // Map calendar IDs by role.
        val calendarsByRole: Map<RoleId, String> = orderedRoles
            .associateWith { role -> scaffold.calendarIds["cal-${role.id}"]!! }

        // Step 2 — overwrite calendar.toml with the canonical wizard fields
        // (priority + emoji + tags). Bootstrap wrote minimal scaffolding;
        // here we layer wizard intent.
        for ((role, calId) in calendarsByRole) {
            val table = TomlTable().apply {
                putInt("schema_version", 1)
                putString("id", calId)
                putString("kind", "calendar")
                putString("name", role.label)
                putString("role", role.id)
                putInt("priority", role.priority)
                putString("emoji", role.emoji)
                // Round 2.21.B.5 — every wizard-seeded calendar lands with
                // a color_seed from the locked role→swatch map so the
                // overlay-picker dot, chip, and per-band tint all render
                // from first paint. Users override via the identity editor.
                putInt("color_seed", role.colorSeed)
                putString("tz_id", tzId)
                if (role == RoleId.Kink) putStringArray("tags", listOf("kink"))
            }
            val calPath = rootDir.toPath().resolve("calendars/$calId/calendar.toml")
            Files.write(calPath, TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8))
        }

        // Step 3 — recurrences (one per enabled atom).
        val ts = now().toString()
        var recurCount = 0
        for ((role, calId) in calendarsByRole) {
            val enabledAtomIds = normalized.enabledTemplates[role] ?: emptySet()
            // Default: all visible templates pre-toggled on if no explicit set yet
            // (smart-defaults per LW-G.5).
            val atomsToWrite: List<String> = if (enabledAtomIds.isEmpty()) {
                TemplateRegistry.visibleTemplatesFor(role, neutralMode = normalized.kinkOff)
                    .map { it.atomId }
            } else {
                enabledAtomIds.toList()
            }
            for (atomId in atomsToWrite) {
                val rid = Uuid7.generate().toString()
                // Phase 2.1.I.5 — spread atoms across morning/midday/evening
                // buckets so the wizard's emitted schedule isn't N
                // overlapping 09:00 blocks. Fallback bucket is 09:00.
                val hm = TemplateRegistry.dtstartHmFor(atomId)
                val rule = RecurrenceRule(
                    header = EntityHeader(
                        id = rid,
                        createdAt = ts,
                        updatedAt = ts,
                        author = scaffold.identityId,
                    ),
                    title = TemplateRegistry.templatesFor(role)
                        .firstOrNull { it.atomId == atomId }?.label ?: atomId,
                    dtstart = "2025-01-01T$hm:00",
                    duration = "PT15M",
                    tzId = tzId,
                    rrule = "FREQ=DAILY",
                    calendarId = calId,
                    tags = buildList {
                        add("template")
                        // Phase AAA: route through TemplateOrigin so the
                        // re-run-from-Settings (LW-L) idempotency check
                        // and the CLI `skb template reset` share format.
                        addAll(
                            TemplateOrigin.tagsFor(
                                origin = TemplateOrigin.WIZARD,
                                templateId = role.id,
                                entryId = atomId,
                            )
                        )
                        if (role == RoleId.Kink) add("kink")
                    },
                    emoji = role.emoji,
                    // Phase 2.1.I.6 — flag passive habit atoms.
                    passive = TemplateRegistry.isPassiveAtom(atomId),
                )
                EntityWriter.write(rootDir, rule)
                recurCount += 1
            }
        }

        // Step 4 — onboarding standing tasks (5, locked roster per LW-I.6).
        val todoId = scaffold.todolistIds[onboardingTodolist]!!
        val onboarding: List<Pair<String, Int>> = listOf(
            "Open the app every day for 3 days" to 700,
            "Customize a sticker" to 500,
            "Add your own atomic activity" to 500,
            "Invite a partner (or skip)" to 400,
            "Share a calendar with a partner (or skip)" to 400,
        )
        for ((title, priority) in onboarding) {
            val tid = Uuid7.generate().toString()
            val task = StandingTask(
                header = EntityHeader(
                    id = tid,
                    createdAt = ts,
                    updatedAt = ts,
                    author = scaffold.identityId,
                ),
                title = title,
                todolistId = todoId,
                priority = priority,
            )
            EntityWriter.write(rootDir, task)
        }

        // Walkthrough-2 enrichment — base / public-holidays / vacation
        // calendars + a handful of sample tasks + one upcoming event so
        // the user's first-paint schedule has demo-comparable density.
        // See WizardBaseLayersScaffolder kdoc for the full rationale.
        val baseLayersOutcome = WizardBaseLayersScaffolder.materialize(
            rootDir = rootDir,
            identityId = scaffold.identityId,
            tzId = tzId,
            roleCalendarIds = calendarsByRole.values,
            onboardingTodolistId = scaffold.todolistIds[onboardingTodolist]!!,
            now = now,
        )

        // Identity.toml — Phase 2.1.J.2: replace the legacy text-concat
        // appendix with a single codec-driven write so the on-disk format
        // matches IdentityTomlCodec exactly (and so Settings edits can
        // round-trip via the same codec). RepoBootstrap wrote primary
        // praise term + pronouns; we now overwrite with the full draft.
        val firstAlt = normalized.praiseTerms.firstOrNull() ?: "good boy"
        val alts = if (normalized.praiseTerms.size > 1) {
            normalized.praiseTerms.drop(1)
        } else emptyList()
        val honorificTerm = normalized.honorific.label
        val identityData = IdentityTomlData(
            praiseTerm = firstAlt,
            altTerms = alts,
            pronouns = IdentityPronouns(
                subject = normalized.pronouns.subject,
                obj = normalized.pronouns.obj,
                possessive = normalized.pronouns.possessive,
                reflexive = normalized.pronouns.reflexive,
            ),
            honorificForDom = honorificTerm.ifBlank { "Sir" },
            toneRegister = normalized.tone.id,
            emojiDensity = normalized.emojiDensity.id,
            alignment = normalized.alignment.id,
            lifestyle = normalized.lifestyle.id,
        )
        IdentityTomlCodec.write(rootDir.toPath(), identityData)

        // Phase 2.1.I.3 — overwrite mode.toml with the wizard's mode pick.
        // RepoBootstrap.scaffold wrote ModeTomlData.Default (free, no dom);
        // here we layer the user's actual pick on top. KeptByAi seeds a
        // builtin dom-persona; KeptByHuman leaves persona null (the share
        // link the user generates next populates write_back_target).
        val modeData = when (normalized.effectiveModePick) {
            WizardModePick.Free -> ModeTomlData(mode = RepoMode.Free)
            WizardModePick.SelfKeep -> ModeTomlData(mode = RepoMode.SelfKeep)
            WizardModePick.KeptByAi -> ModeTomlData(
                mode = RepoMode.StrictlyKept,
                domPersona = "stern-but-fair",
                domCadence = DomCadenceWire.EndOfDay,
            )
            WizardModePick.KeptByHuman -> ModeTomlData(
                mode = RepoMode.StrictlyKept,
                domPersona = null,
                domCadence = DomCadenceWire.EndOfDay,
            )
        }
        ModeTomlCodec.write(rootDir.toPath(), modeData)

        // Phase 2.1.F.7 — seed `cal-briefings/` so the WorkManager
        // briefing worker has a canonical system calendar to walk.
        // Idempotent: re-running the wizard for an existing repo skips
        // when calendar.toml already exists.
        com.eight87.strictlykeptboy.store.CalBriefingsSeed.seed(
            rootDir = rootDir,
            author = scaffold.identityId,
            tzId = tzId,
        )

        // Phase 2.8 — write the customization README always, regardless of
        // species. We do NOT copy bundled-pack image files into the repo by
        // default (they'd inflate repo size for users who never customize).
        // The opt-in "Import stickers into repo" toggle in per-repo Sticker
        // Pack settings (RepoConfig.importStickersToRepo) is what triggers
        // the copy — see StickerPackSelectorScreen.
        run {
            val readme = rootDir.toPath().resolve("stickers/README.md")
            Files.createDirectories(readme.parent)
            if (!Files.exists(readme)) {
                Files.write(
                    readme,
                    ("# Sticker packs\n\n" +
                        "Built-in sticker packs render straight from the app's bundled\n" +
                        "assets — they are NOT copied into this repo by default (image\n" +
                        "bytes would inflate every clone for users who don't customize).\n\n" +
                        "To customize your stickers (e.g. hand them to an AI image\n" +
                        "generator), open the app's **Repo Settings → Sticker pack**\n" +
                        "section and turn on **Import stickers into repo**. The active\n" +
                        "pack will be copied to `stickers/<pack>/` (one directory per\n" +
                        "pack, so multiple packs can coexist) and committed. Edit the\n" +
                        "files in that directory, commit your changes, and the app\n" +
                        "re-reads them on next launch.\n").toByteArray(StandardCharsets.UTF_8),
                )
            }
        }

        // Step 5 — git init (phone-only for v1; remote paths land in a follow-up
        // once OAuth client IDs are registered).
        val repoId = scaffold.repoId
        val gitRepo = GitRepo.initLocalOnly(
            rootDir = rootDir,
            repoId = repoId,
            authorIdentity = author,
        )

        // Step 6 — single initial commit.
        val commitMsg = buildString {
            append("wizard: scaffold lifestyle (")
            append(normalized.alignment.id).append("/").append(normalized.lifestyle.id)
            append(", ${calendarsByRole.size} roles, ")
            append("${recurCount + baseLayersOutcome.recurrencesWritten} recurrences)")
        }
        gitRepo.commitAll(commitMsg)
        gitRepo.close()

        Outcome(
            repoId = repoId,
            rootDir = rootDir,
            calendarIds = calendarsByRole,
            todolistId = todoId,
            recurrencesWritten = recurCount + baseLayersOutcome.recurrencesWritten,
            standingTasksWritten = onboarding.size + baseLayersOutcome.sampleTasksWritten,
            authorIdentity = author,
            baseLayers = baseLayersOutcome,
        )
    }
}

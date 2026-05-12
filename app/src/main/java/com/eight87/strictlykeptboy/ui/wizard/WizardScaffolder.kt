package com.eight87.strictlykeptboy.ui.wizard

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.RepoBootstrap
import com.eight87.strictlykeptboy.store.StandingTask
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
    )

    /**
     * @param parentDir parent directory under which the new repo dir gets created
     * @param draft normalized [WizardDraft]
     * @param author committer identity (Name <email>)
     * @param tzId default timezone for new calendars
     * @param now optional clock for tests (epoch-millis source)
     */
    suspend fun materialize(
        parentDir: File,
        draft: WizardDraft,
        author: AuthorIdentity = AuthorIdentity("me", "me@example.com"),
        tzId: String = ZoneId.systemDefault().id,
        now: () -> OffsetDateTime = { OffsetDateTime.now().withNano(0) },
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
                val rule = RecurrenceRule(
                    header = EntityHeader(
                        id = rid,
                        createdAt = ts,
                        updatedAt = ts,
                        author = scaffold.identityId,
                    ),
                    title = TemplateRegistry.templatesFor(role)
                        .firstOrNull { it.atomId == atomId }?.label ?: atomId,
                    dtstart = "2025-01-01T09:00:00",
                    duration = "PT15M",
                    tzId = tzId,
                    rrule = "FREQ=DAILY",
                    calendarId = calId,
                    tags = buildList {
                        add("template")
                        add("template_origin:wizard")
                        add("template_slot:${role.id}/$atomId")
                        if (role == RoleId.Kink) add("kink")
                    },
                    emoji = role.emoji,
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

        // Identity.toml — extend with honorific / tone / emoji density that
        // RepoBootstrap doesn't yet know about (D.83 surface).
        val idTomlPath = rootDir.toPath().resolve("identity.toml")
        val extra = buildString {
            append("\n[honorific]\nterm = \"${normalized.honorific.label}\"\n")
            append("\n[tone]\nregister = \"${normalized.tone.id}\"\n")
            append("\n[emoji]\ndensity = \"${normalized.emojiDensity.id}\"\n")
            if (normalized.praiseTerms.size > 1) {
                val csv = normalized.praiseTerms.joinToString(", ") { "\"$it\"" }
                append("\n[praise.alternates]\nterms = [$csv]\n")
            }
            append("\n[alignment]\nvalue = \"${normalized.alignment.id}\"\n")
            append("\n[lifestyle]\nvalue = \"${normalized.lifestyle.id}\"\n")
        }
        Files.write(
            idTomlPath,
            (String(Files.readAllBytes(idTomlPath), Charsets.UTF_8) + extra).toByteArray(StandardCharsets.UTF_8),
        )

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
            append(", ${calendarsByRole.size} roles, $recurCount recurrences)")
        }
        gitRepo.commitAll(commitMsg)
        gitRepo.close()

        Outcome(
            repoId = repoId,
            rootDir = rootDir,
            calendarIds = calendarsByRole,
            todolistId = todoId,
            recurrencesWritten = recurCount,
            standingTasksWritten = onboarding.size,
            authorIdentity = author,
        )
    }
}

package com.eight87.skb.cli.routine

import com.eight87.skb.cli.commands.emitHuman
import com.eight87.skb.cli.commands.emitJson
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.RepoStore
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * Phase XX.10 / AT-J.6 — `skb streak <event-id-or-rule-id>`.
 *
 * Prints the integer streak (count of consecutive scheduled days with
 * no `skipped` deviation, walking back from today) and the last-skip
 * date. `partial` / `completed-early` / `completed-late` deviations do
 * NOT break the streak — only `kind = "skipped"` does (AT-J.1 LOCKED).
 *
 * Stateless: re-walks the `deviations/` directory from scratch.
 */
class StreakCommand(private val ctxOf: () -> CliContext) : CliktCommand(name = "streak") {
    val targetArg by argument("event-id-or-rule-id")

    override fun run() {
        val ctx = ctxOf()
        val root = ctx.repoRoot()
        val store = RepoStore(root)
        val skipped = mutableListOf<LocalDate>()
        for (cal in store.listCalendars()) {
            val dir = root.resolve("calendars/${cal.id}/deviations/$targetArg")
            if (!Files.isDirectory(dir)) continue
            scanDeviations(dir).forEach { (date, kind) ->
                if (kind == "skipped") skipped += date
            }
        }
        // Scheduled-dates: with no resolver linked, assume the entity
        // was scheduled every day since its earliest deviation
        // (over-approximation, fine for `skb streak`; the Room cache in
        // `:app` does the precise version).
        val today = LocalDate.now()
        val streak = computeStreak(today, skipped.toSet())
        val lastSkip = skipped.maxOrNull()

        emitHuman(
            buildString {
                appendLine("streak: $streak")
                appendLine("  target:    $targetArg")
                appendLine("  last_skip: ${lastSkip?.toString() ?: "(none)"}")
            }.trimEnd(), ctx,
        )
        emitJson(
            JsonEnvelope.success(
                "streak",
                buildJsonObject {
                    put("target_id", JsonPrimitive(targetArg))
                    put("streak", JsonPrimitive(streak))
                    put("last_skip", JsonPrimitive(lastSkip?.toString()))
                },
            ),
            ctx,
        )
    }
}

private fun scanDeviations(dir: Path): List<Pair<LocalDate, String>> {
    val out = mutableListOf<Pair<LocalDate, String>>()
    Files.walk(dir).use { stream ->
        stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.forEach { f ->
            val name = f.fileName.toString().removeSuffix(".md")
            val day = try { LocalDate.parse(name) } catch (_: Exception) { return@forEach }
            val (table, _) = Frontmatter.parse(Files.readString(f))
            val kind = table.getString("deviation_kind") ?: table.getString("kind") ?: return@forEach
            out.add(day to kind)
        }
    }
    return out
}

/**
 * Streak rule AT-J.1: walk back from [today] until a `skipped` day
 * appears. Days with no deviation count as "scheduled, no skip" since
 * the inversion treats them as `completed-by-schedule` (the CLI lacks
 * a resolver, so this matches the user's mental model of "I haven't
 * skipped since X").
 */
internal fun computeStreak(today: LocalDate, skipped: Set<LocalDate>): Int {
    if (today in skipped) return 0
    var count = 0
    var cursor = today
    while (true) {
        if (cursor in skipped) return count
        count += 1
        cursor = cursor.minusDays(1)
        // Cap to one year (AT-J.1 spirit, matches `StreakCounter.MAX_LOOKBACK_DAYS`).
        if (today.toEpochDay() - cursor.toEpochDay() > 366) return count
    }
}

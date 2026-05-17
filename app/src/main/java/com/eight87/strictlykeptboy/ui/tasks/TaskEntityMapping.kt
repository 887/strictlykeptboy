package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.cache.entities.StandingTaskRow
import com.eight87.strictlykeptboy.cache.entities.TaskRow
import com.eight87.strictlykeptboy.resolver.TodolistMeta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Round 2.26.F.1 — Room TaskRow / StandingTaskRow → UI TaskItem mapper.
 *
 * Lives in `ui/tasks/` so it can construct [TaskItem] / [TodolistInfo]
 * without UI ↔ cache cycles. Pure, no I/O — caller pre-resolves the
 * matching [TodolistMeta] (or returns the fallback shape when the
 * snapshot publisher only has an id-only placeholder).
 *
 * Tag decoding is intentionally cheap: rows carry `tagsJson` as a JSON
 * array of strings; the empty `"[]"` case is shortcut-decoded as
 * `emptyList()`. Anything else round-trips through a permissive
 * string-list parse that tolerates spacing + trailing commas (the
 * indexer's writer is canonical JSON, but we accept a wider grammar
 * for robustness against hand edits to the cache DB during dev).
 */

/** Build a [TodolistInfo] from the (possibly null) resolver-supplied meta. */
fun buildTodolistInfo(
    todolistId: String,
    repoId: String,
    meta: TodolistMeta?,
    fallbackDisplayName: String? = null,
    fallbackEmoji: String? = null,
    fallbackColorSeed: String? = null,
    fallbackPriority: Int = 0,
    fallbackMode: TodolistMode = TodolistMode.Standard,
): TodolistInfo {
    val displayName = meta?.displayName?.takeIf { it.isNotBlank() && it != todolistId }
        ?: fallbackDisplayName?.takeIf { it.isNotBlank() }
        ?: todolistId
    return TodolistInfo(
        id = todolistId,
        repoId = repoId,
        name = displayName,
        emoji = fallbackEmoji,
        colorSeed = fallbackColorSeed ?: todolistId,
        mode = fallbackMode,
        priority = fallbackPriority,
        active = meta?.activeToggle ?: true,
        activeWindows = meta?.activeWindows ?: emptyList(),
        activeHours = meta?.activeHours ?: emptyList(),
        tzId = meta?.tzId ?: ZoneId.systemDefault(),
    )
}

fun TaskRow.toTaskItem(
    todolistInfo: TodolistInfo,
    zone: ZoneId = ZoneId.systemDefault(),
): TaskItem = TaskItem(
    id = id,
    title = title,
    todolist = todolistInfo,
    due = dueEpochMs?.let { epochMsToLocalDate(it, zone) },
    done = done,
    doneAt = doneAtEpochMs?.let { epochMsToLocalDate(it, zone) },
    priority = priority ?: 0,
    tags = decodeTags(tagsJson),
    standing = false,
    pinnedForToday = false,
    body = body,
    source = TaskSource.Other,
)

fun StandingTaskRow.toTaskItem(todolistInfo: TodolistInfo): TaskItem = TaskItem(
    id = id,
    title = title,
    todolist = todolistInfo,
    due = null,
    done = done,
    priority = priority ?: 0,
    tags = decodeTags(tagsJson),
    standing = true,
    pinnedForToday = pinned,
    body = body,
    source = if (pinned) TaskSource.Pinned else TaskSource.Other,
)

private fun epochMsToLocalDate(ms: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

private fun decodeTags(json: String): List<String> {
    val trimmed = json.trim()
    if (trimmed.isEmpty() || trimmed == "[]") return emptyList()
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
    val inner = trimmed.substring(1, trimmed.length - 1).trim()
    if (inner.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    var i = 0
    val n = inner.length
    while (i < n) {
        while (i < n && (inner[i] == ' ' || inner[i] == ',' || inner[i] == '\t')) i++
        if (i >= n) break
        if (inner[i] == '"') {
            val sb = StringBuilder()
            i++
            while (i < n && inner[i] != '"') {
                if (inner[i] == '\\' && i + 1 < n) { sb.append(inner[i + 1]); i += 2 }
                else { sb.append(inner[i]); i++ }
            }
            if (i < n) i++ // consume closing quote
            out += sb.toString()
        } else {
            val start = i
            while (i < n && inner[i] != ',') i++
            out += inner.substring(start, i).trim()
        }
    }
    return out
}

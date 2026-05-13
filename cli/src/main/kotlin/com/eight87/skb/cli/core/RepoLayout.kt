package com.eight87.skb.cli.core

import java.nio.file.Path
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * On-disk paths per D.3 / DM-C. Parallels `:app`'s store/EntityPath.kt
 * but trimmed to what Phase X writes.
 */
object RepoLayout {
  fun calendarDir(root: Path, calendarId: String): Path =
    root.resolve("calendars/$calendarId")

  fun calendarToml(root: Path, calendarId: String): Path =
    calendarDir(root, calendarId).resolve("calendar.toml")

  fun event(root: Path, calendarId: String, eventId: String, start: String): Path {
    val (yyyy, mm) = bucketYearMonth(start)
    return calendarDir(root, calendarId).resolve("events/$yyyy/$mm/$eventId.md")
  }

  fun todolistDir(root: Path, todolistId: String): Path =
    root.resolve("todolists/$todolistId")

  fun todolistToml(root: Path, todolistId: String): Path =
    todolistDir(root, todolistId).resolve("todolist.toml")

  fun datedTask(root: Path, todolistId: String, taskId: String, due: String): Path {
    val (yyyy, mm) = bucketYearMonth(due)
    return todolistDir(root, todolistId).resolve("tasks/$yyyy/$mm/$taskId.md")
  }

  fun standingTask(root: Path, todolistId: String, taskId: String): Path =
    todolistDir(root, todolistId).resolve("standing/$taskId.md")

  /** Buckets datetimes by their YYYY/MM in the supplied string's offset (per DM-C.3). */
  internal fun bucketYearMonth(isoOrDate: String): Pair<String, String> {
    return try {
      val odt = OffsetDateTime.parse(isoOrDate)
      "%04d".format(odt.year) to "%02d".format(odt.monthValue)
    } catch (_: Exception) {
      val d = LocalDate.parse(isoOrDate, DateTimeFormatter.ISO_LOCAL_DATE)
      "%04d".format(d.year) to "%02d".format(d.monthValue)
    }
  }
}

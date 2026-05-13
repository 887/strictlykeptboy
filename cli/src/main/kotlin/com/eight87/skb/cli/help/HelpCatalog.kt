package com.eight87.skb.cli.help

import com.eight87.skb.cli.core.ExitCode

/**
 * Single-source-of-truth catalog driving both `skb help <command>` and
 * `skb help --json`. CLI-C.4 mandates one source for human + JSON help.
 *
 * SOLID.O: adding a new command = adding a new [CommandHelp]; no
 * `when`-chain to update. Help rendering iterates the catalog.
 */
data class FlagHelp(
  val name: String,
  val type: String,
  val required: Boolean = false,
  val default: String? = null,
  val description: String,
)

data class CommandHelp(
  val name: String,
  val synopsis: String,
  val description: String,
  val flags: List<FlagHelp> = emptyList(),
  val exitCodes: List<ExitCode> = listOf(ExitCode.OK, ExitCode.USAGE, ExitCode.NOT_FOUND),
  val example: String? = null,
)

object HelpCatalog {

  private val commonWriteFlags = listOf(
    FlagHelp("--json", "bool", description = "machine-readable JSON output"),
    FlagHelp("--dry-run", "bool", description = "print proposed change, do not write or commit"),
    FlagHelp("--repo", "path", description = "explicit repo root (overrides discovery + SKB_REPO)"),
    FlagHelp("--quiet", "bool", description = "suppress human stdout (still emits --json)"),
    FlagHelp("--verbose", "bool", description = "verbose stderr"),
    FlagHelp("--no-color", "bool", description = "disable ANSI color"),
  )

  val commands: List<CommandHelp> = listOf(
    CommandHelp(
      name = "event.add",
      synopsis = "skb event add --title <s> --start <iso> (--end <iso> | --duration <iso>) [--calendar <name|id>] [flags]",
      description = "Create a one-off event in the named calendar. Writes atomically and auto-commits.",
      flags = listOf(
        FlagHelp("--title", "string", required = true, description = "event title"),
        FlagHelp("--start", "offset-datetime", required = true, description = "RFC 3339, e.g. 2026-05-12T14:00:00+02:00"),
        FlagHelp("--end", "offset-datetime", description = "mutually exclusive with --duration"),
        FlagHelp("--duration", "iso8601-duration", description = "e.g. PT45M; mutually exclusive with --end"),
        FlagHelp("--calendar", "string", description = "calendar name or id; defaults to the repo's only calendar"),
        FlagHelp("--location", "string", description = "free text"),
        FlagHelp("--tag", "string (repeatable)", description = "tag, repeat for multiple"),
        FlagHelp("--body", "string", description = "inline body content"),
        FlagHelp("--id", "uuidv7", description = "explicit id (idempotency key)"),
      ) + commonWriteFlags,
      exitCodes = listOf(ExitCode.OK, ExitCode.USAGE, ExitCode.NOT_FOUND, ExitCode.CONFLICT, ExitCode.CORRUPT),
      example = "skb event add --title \"Dentist\" --start 2026-05-12T14:00:00+02:00 --duration PT45M --calendar Personal",
    ),
    CommandHelp(
      name = "event.list",
      synopsis = "skb event list [--calendar <name|id>] [--from <date>] [--to <date>] [--limit N]",
      description = "List events matching filters. Default range is the next 90 days.",
      flags = listOf(
        FlagHelp("--calendar", "string", description = "filter to one calendar"),
        FlagHelp("--from", "date", description = "inclusive lower bound (YYYY-MM-DD)"),
        FlagHelp("--to", "date", description = "inclusive upper bound (YYYY-MM-DD)"),
        FlagHelp("--limit", "int", default = "100", description = "max events"),
        FlagHelp("--json", "bool", description = "JSON output"),
        FlagHelp("--repo", "path", description = "explicit repo root"),
      ),
      exitCodes = listOf(ExitCode.OK, ExitCode.NOT_FOUND),
      example = "skb event list --from 2026-05-10 --to 2026-05-14",
    ),
    CommandHelp(
      name = "event.show",
      synopsis = "skb event show <event-id>",
      description = "Show one event by id (prefix-matching: a unique UUIDv7 prefix is enough).",
      flags = listOf(
        FlagHelp("--json", "bool", description = "JSON output"),
        FlagHelp("--repo", "path", description = "explicit repo root"),
      ),
      exitCodes = listOf(ExitCode.OK, ExitCode.NOT_FOUND, ExitCode.CONFLICT),
      example = "skb event show 0190d4a0",
    ),
    CommandHelp(
      name = "task.add",
      synopsis = "skb task add --title <s> [--list <name|id>] [--due <date>] [flags]",
      description = "Create a task. If --due is omitted, the task is a standing task.",
      flags = listOf(
        FlagHelp("--title", "string", required = true, description = "task title"),
        FlagHelp("--list", "string", description = "todolist name or id"),
        FlagHelp("--due", "date-or-datetime", description = "YYYY-MM-DD or RFC 3339"),
        FlagHelp("--priority", "int", description = "1..1000"),
        FlagHelp("--tag", "string (repeatable)", description = "tag"),
        FlagHelp("--body", "string", description = "inline body"),
        FlagHelp("--id", "uuidv7", description = "explicit id"),
      ) + commonWriteFlags,
      exitCodes = listOf(ExitCode.OK, ExitCode.USAGE, ExitCode.NOT_FOUND, ExitCode.CONFLICT, ExitCode.CORRUPT),
      example = "skb task add --title \"Reply to Sam\" --list Chores --due 2026-05-13",
    ),
    CommandHelp(
      name = "task.list",
      synopsis = "skb task list [--list <name|id>] [--standing-only]",
      description = "List tasks across one or all todolists.",
      flags = listOf(
        FlagHelp("--list", "string", description = "filter to one todolist"),
        FlagHelp("--standing-only", "bool", description = "exclude dated tasks"),
        FlagHelp("--json", "bool", description = "JSON output"),
        FlagHelp("--repo", "path", description = "explicit repo root"),
      ),
      exitCodes = listOf(ExitCode.OK, ExitCode.NOT_FOUND),
      example = "skb task list --list Chores",
    ),
    CommandHelp(
      name = "task.show",
      synopsis = "skb task show <task-id>",
      description = "Show one task by id (prefix-matching).",
      flags = listOf(
        FlagHelp("--json", "bool", description = "JSON output"),
        FlagHelp("--repo", "path", description = "explicit repo root"),
      ),
      exitCodes = listOf(ExitCode.OK, ExitCode.NOT_FOUND, ExitCode.CONFLICT),
      example = "skb task show 0190d4cf",
    ),
    CommandHelp(
      name = "cal.add",
      synopsis = "skb cal add --name <s> [--tz <iana>] [--color <#rrggbb>]",
      description = "Create a new calendar.",
      flags = listOf(
        FlagHelp("--name", "string", required = true, description = "display name"),
        FlagHelp("--tz", "iana-tz", default = "UTC", description = "IANA timezone id"),
        FlagHelp("--color", "string", description = "#rrggbb"),
        FlagHelp("--id", "uuidv7", description = "explicit id"),
      ) + commonWriteFlags,
      exitCodes = listOf(ExitCode.OK, ExitCode.USAGE, ExitCode.CONFLICT),
      example = "skb cal add --name Personal --tz Europe/Berlin",
    ),
    CommandHelp(
      name = "cal.list",
      synopsis = "skb cal list",
      description = "List all calendars.",
      flags = listOf(
        FlagHelp("--json", "bool", description = "JSON output"),
        FlagHelp("--repo", "path", description = "explicit repo root"),
      ),
      exitCodes = listOf(ExitCode.OK),
    ),
    CommandHelp(
      name = "repo.init",
      synopsis = "skb repo init <path> [--name <s>] [--default-calendar <s>] [--default-list <s>] [--default-tz <iana>]",
      description = "Initialize a new strictlykeptboy repo at <path>: git init + scaffold .strictlykeptboy/, identity, optional seed calendar + todolist.",
      flags = listOf(
        FlagHelp("--name", "string", description = "repo name (defaults to basename of path)"),
        FlagHelp("--default-calendar", "string", description = "seed a calendar of this name"),
        FlagHelp("--default-list", "string", description = "seed a todolist of this name"),
        FlagHelp("--default-tz", "iana-tz", default = "UTC", description = "default timezone for seeded entities"),
        FlagHelp("--author-name", "string", default = "skb-cli", description = "initial identity display name"),
        FlagHelp("--author-email", "string", description = "initial identity email"),
        FlagHelp("--json", "bool", description = "JSON output"),
      ),
      exitCodes = listOf(ExitCode.OK, ExitCode.USAGE, ExitCode.CONFLICT),
      example = "skb repo init ~/my-cal --name Personal --default-calendar Personal --default-tz Europe/Berlin",
    ),
    CommandHelp(
      name = "repo.list",
      synopsis = "skb repo list",
      description = "Lists configured repos. In Phase X, this prints the active discovered repo only (config file lands in CLI-E.3).",
      flags = listOf(FlagHelp("--json", "bool", description = "JSON output")),
      exitCodes = listOf(ExitCode.OK, ExitCode.NOT_FOUND),
    ),
    CommandHelp(
      name = "help",
      synopsis = "skb help [<command>]",
      description = "Print help. Without arguments, lists top-level commands; with a command, prints detailed help. --json emits the full machine-readable command catalog (AI-agent discovery surface).",
      flags = listOf(FlagHelp("--json", "bool", description = "JSON catalog")),
      exitCodes = listOf(ExitCode.OK, ExitCode.NOT_FOUND),
      example = "skb help event.add",
    ),
  )

  fun findByName(name: String): CommandHelp? =
    commands.firstOrNull { it.name == name || it.name == name.replace(" ", ".") }
}

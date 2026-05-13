package com.eight87.skb.cli

import com.eight87.skb.cli.commands.CalGroup
import com.eight87.skb.cli.commands.DomGroup
import com.eight87.skb.cli.commands.EventGroup
import com.eight87.skb.cli.commands.HelpCommand
import com.eight87.skb.cli.commands.IdentityGroup
import com.eight87.skb.cli.commands.ModeGroup
import com.eight87.skb.cli.commands.RepoGroup
import com.eight87.skb.cli.commands.ReviewGroup
import com.eight87.skb.cli.commands.TaskGroup
import com.eight87.skb.cli.feedback.CommentGroup
import com.eight87.skb.cli.feedback.ReactGroup
import com.eight87.skb.cli.feedback.RefSetWriteBackCommand
import com.eight87.skb.cli.feedback.RepoFingerprintCommand
import com.eight87.skb.cli.feedback.RepoRegistryGroup
import com.eight87.skb.cli.repo.RemoteGroup
import com.eight87.skb.cli.routine.RoutineGroup
import com.eight87.skb.cli.routine.StreakCommand
import com.eight87.skb.cli.template.TemplateGroup
import com.eight87.skb.cli.override.OverrideGroup
import com.eight87.skb.cli.attachment.AttachGroup
import com.eight87.skb.cli.reminder.ReminderGroup
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.JsonEnvelope
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.system.exitProcess

/**
 * Root command. Per cli-tooling.md CLI-A/CLI-D: every subcommand inherits
 * --json / --repo / --dry-run / --verbose / --quiet / --no-color from
 * here. The flags are parsed up-front into a [CliContext], which child
 * commands access via the closure passed at construction time.
 *
 * Subcommands wired here: event, task, cal, repo, help. Each is its own
 * group with `add/list/show/...` subcommands. Per X.1 the surfaces are
 * minimal Phase-X-locked-in: detailed flag matrices live in CLI-A.*
 * follow-ups.
 */
class Skb : CliktCommand(name = "skb") {
  override val invokeWithoutSubcommand = true

  val jsonFlag by option("--json", help = "machine-readable output").flag()
  val versionFlag by option("--version", help = "print version and exit").flag()
  val dryRun by option("--dry-run", help = "do not write/commit (per-subcommand previews)").flag()
  val quiet by option("--quiet", help = "suppress human stdout").flag()
  val verbose by option("--verbose", help = "verbose stderr").flag()
  val noColor by option("--no-color", help = "disable ANSI color").flag()
  val repoFlag by option("--repo", help = "repo root path (overrides discovery + SKB_REPO)")

  // The single CliContext built once per process and shared with every
  // subcommand via [ctxOf()]. Late-init because the flags above are only
  // populated after Clikt parses argv.
  lateinit var ctx: CliContext
    private set

  override fun run() {
    val env = System.getenv()
    ctx = CliContext(
      json = jsonFlag || CliContext.envJson(env),
      dryRun = dryRun,
      quiet = quiet,
      verbose = verbose,
      noColor = noColor || (env["SKB_NO_COLOR"]?.isNotBlank() == true),
      explicitRepo = repoFlag,
      env = env,
    )
    if (versionFlag) {
      if (ctx.json) {
        val payload: JsonObject = buildJsonObject {
          put("name", "skb")
          put("version", BuildInfo.VERSION)
          put("git_sha", BuildInfo.GIT_SHA)
          put("build_date", BuildInfo.BUILD_DATE)
        }
        echo(JsonEnvelope.success("version", payload))
      } else {
        echo("skb ${BuildInfo.VERSION}-${BuildInfo.GIT_SHA} (${BuildInfo.BUILD_DATE})")
      }
      return
    }
    if (currentContext.invokedSubcommand == null) {
      echo(getFormattedHelp())
    }
  }
}

/** Main entrypoint with full X.7 exit-code taxonomy. */
fun main(args: Array<String>) {
  val skb = Skb()
  val ctxOf: () -> CliContext = { skb.ctx }
  skb.subcommands(
    EventGroup(ctxOf),
    TaskGroup(ctxOf),
    CalGroup(ctxOf),
    RepoGroup(ctxOf),
    RemoteGroup(ctxOf),
    RoutineGroup(ctxOf),
    StreakCommand(ctxOf),
    TemplateGroup(ctxOf),
    OverrideGroup(ctxOf),
    AttachGroup(ctxOf),
    ReminderGroup(ctxOf),
    ReactGroup(ctxOf),
    CommentGroup(ctxOf),
    RepoFingerprintCommand(ctxOf),
    RepoRegistryGroup(ctxOf),
    RefSetWriteBackCommand(ctxOf),
    ModeGroup(ctxOf),
    IdentityGroup(ctxOf),
    DomGroup(ctxOf),
    ReviewGroup(ctxOf),
    HelpCommand(ctxOf),
  )

  try {
    skb.parse(args)
    exitProcess(ExitCode.OK.code)
  } catch (e: PrintHelpMessage) {
    // `--help` was requested; Clikt has already produced the message.
    e.context?.command?.getFormattedHelp()?.let { System.out.println(it) }
    exitProcess(ExitCode.OK.code)
  } catch (e: UsageError) {
    val msg = e.message ?: "usage error"
    reportError(jsonSafe(skb), "skb", ExitCode.USAGE, msg, null, emptyMap())
    exitProcess(ExitCode.USAGE.code)
  } catch (e: CliError) {
    reportError(jsonSafe(skb), "skb", e.code, e.message ?: "error", e.hint, e.details)
    exitProcess(e.code.code)
  } catch (e: CliktError) {
    val msg = e.message ?: "command failed"
    reportError(jsonSafe(skb), "skb", ExitCode.USAGE, msg, null, emptyMap())
    exitProcess(ExitCode.USAGE.code)
  } catch (e: Throwable) {
    val msg = e.message ?: e::class.java.simpleName
    reportError(jsonSafe(skb), "skb", ExitCode.INTERNAL, msg, null, emptyMap())
    if (verboseSafe(skb)) e.printStackTrace(System.err)
    exitProcess(ExitCode.INTERNAL.code)
  }
}

private fun jsonSafe(skb: Skb): Boolean = try { skb.ctx.json } catch (_: UninitializedPropertyAccessException) { false }
private fun verboseSafe(skb: Skb): Boolean = try { skb.ctx.verbose } catch (_: UninitializedPropertyAccessException) { false }

private fun reportError(
  json: Boolean,
  command: String,
  code: ExitCode,
  message: String,
  hint: String?,
  details: Map<String, Any?>,
) {
  if (json) {
    System.err.println(JsonEnvelope.error(command, code, message, details))
  } else {
    System.err.println("error: $message")
    hint?.let { System.err.println("hint:  $it") }
  }
}

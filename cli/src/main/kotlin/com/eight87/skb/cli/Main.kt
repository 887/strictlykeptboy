package com.eight87.skb.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Root command. Per cli-tooling.md CLI-A/CLI-D: every subcommand inherits
// --json / --repo / --dry-run / --verbose / --quiet / --no-color from
// here. Phase X scaffold ships only --version + the dispatch tree; real
// surfaces (event/task/cal/list/...) are stubs that exit 1 with a
// usage error until their CLI-A.* phase lands.
class Skb : CliktCommand(name = "skb") {
  override val invokeWithoutSubcommand = true

  val json by option("--json", help = "machine-readable output").flag()
  val version by option("--version", help = "print version and exit").flag()

  override fun run() {
    if (version) {
      if (json) {
        val payload: JsonObject = buildJsonObject {
          put("name", "skb")
          put("version", BuildInfo.VERSION)
          put("git_sha", BuildInfo.GIT_SHA)
          put("build_date", BuildInfo.BUILD_DATE)
        }
        echo(Json.encodeToString(JsonObject.serializer(), payload))
      } else {
        echo("skb ${BuildInfo.VERSION}-${BuildInfo.GIT_SHA} (${BuildInfo.BUILD_DATE})")
      }
      return
    }
    // Subcommand will run next — let it. Only print help when nothing
    // else is going to happen.
    if (currentContext.invokedSubcommand == null) {
      echo(getFormattedHelp())
    }
  }
}

// Stub group commands — placeholders that print "not yet implemented".
// Each gets its real surface in the corresponding CLI-A.* phase.
private class Event : CliktCommand(name = "event") {
  override fun run() = echo("skb event: not yet implemented (CLI-A.1)", err = true)
}

private class Task : CliktCommand(name = "task") {
  override fun run() = echo("skb task: not yet implemented (CLI-A.2)", err = true)
}

private class Cal : CliktCommand(name = "cal") {
  override fun run() = echo("skb cal: not yet implemented (CLI-A.4)", err = true)
}

private class Repo : CliktCommand(name = "repo") {
  override fun run() = echo("skb repo: not yet implemented (CLI-A.19)", err = true)
}

fun main(args: Array<String>) {
  Skb().subcommands(Event(), Task(), Cal(), Repo()).main(args)
}

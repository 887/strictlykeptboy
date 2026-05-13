package com.eight87.skb.cli.commands

import com.eight87.skb.cli.BuildInfo
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.help.HelpCatalog
import com.eight87.skb.cli.help.HelpRender
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional

class HelpCommand(val ctxOf: () -> CliContext) : CliktCommand(name = "help") {
  val command by argument("command").optional()

  override fun run() {
    val ctx = ctxOf()
    if (ctx.json) {
      echo(HelpRender.renderCatalogJson(BuildInfo.VERSION, BuildInfo.GIT_SHA))
      return
    }
    if (command == null) {
      echo(HelpRender.renderTopHuman())
      return
    }
    val c = HelpCatalog.findByName(command!!) ?: throw CliError(
      ExitCode.NOT_FOUND,
      "no such command: $command",
      hint = "list all with: skb help",
    )
    echo(HelpRender.renderCommandHuman(c))
  }
}

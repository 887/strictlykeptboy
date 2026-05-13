package com.eight87.skb.cli.help

import com.eight87.skb.cli.core.JsonEnvelope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object HelpRender {

  fun renderTopHuman(): String = buildString {
    appendLine("skb — strictlykeptboy CLI")
    appendLine()
    appendLine("usage:")
    appendLine("  skb [--repo PATH] [--json] [--dry-run] <command> [args]")
    appendLine()
    appendLine("commands:")
    val grouped = HelpCatalog.commands.groupBy { it.name.substringBefore('.') }
    for ((group, items) in grouped) {
      appendLine("  $group")
      for (c in items) {
        val tail = c.name.substringAfter('.', missingDelimiterValue = "")
        appendLine("    skb $group ${tail.ifEmpty { "" }}".trimEnd() + "    — " + c.description.lineSequence().first())
      }
    }
    appendLine()
    appendLine("see 'skb help <command>' for full help, or 'skb help --json' for the AI catalog.")
  }

  fun renderCommandHuman(c: CommandHelp): String = buildString {
    appendLine(c.name)
    appendLine("  ${c.synopsis}")
    appendLine()
    appendLine(c.description)
    if (c.flags.isNotEmpty()) {
      appendLine()
      appendLine("flags:")
      val w = c.flags.maxOf { it.name.length }
      for (f in c.flags) {
        val req = if (f.required) " (required)" else ""
        val def = f.default?.let { " [default: $it]" } ?: ""
        appendLine("  ${f.name.padEnd(w)}  ${f.type}$req$def  — ${f.description}")
      }
    }
    appendLine()
    appendLine("exit codes: " + c.exitCodes.joinToString(", ") { "${it.code} ${it.wireName}" })
    c.example?.let {
      appendLine()
      appendLine("example:")
      appendLine("  $it")
    }
  }

  fun renderCatalogJson(version: String, gitSha: String): String {
    val cmds = JsonArray(
      HelpCatalog.commands.map { c ->
        buildJsonObject {
          put("name", JsonPrimitive(c.name))
          put("synopsis", JsonPrimitive(c.synopsis))
          put("description", JsonPrimitive(c.description))
          put(
            "flags",
            JsonArray(
              c.flags.map { f ->
                buildJsonObject {
                  put("name", JsonPrimitive(f.name))
                  put("type", JsonPrimitive(f.type))
                  put("required", JsonPrimitive(f.required))
                  f.default?.let { put("default", JsonPrimitive(it)) }
                  put("description", JsonPrimitive(f.description))
                }
              },
            ),
          )
          put("exit_codes", JsonArray(c.exitCodes.map { JsonPrimitive(it.code) }))
          c.example?.let { put("example", JsonPrimitive(it)) }
        }
      },
    )
    val root: JsonObject = buildJsonObject {
      put("skb_version", JsonPrimitive("$version-$gitSha"))
      put("schema_version", JsonPrimitive(1))
      put("commands", cmds)
    }
    return JsonEnvelope.success("help", root)
  }
}

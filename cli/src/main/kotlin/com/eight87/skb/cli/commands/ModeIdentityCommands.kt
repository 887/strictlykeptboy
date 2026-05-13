package com.eight87.skb.cli.commands

import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.CliIdentityToml
import com.eight87.skb.cli.core.CliModeToml
import com.eight87.skb.cli.core.CliRepoMode
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.JsonEnvelope
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Files
import java.time.OffsetDateTime

/**
 * Phase DDD.15 / CLI-U — `skb mode`, `skb identity`, `skb dom`, `skb review`.
 *
 * Provides:
 *  - `skb mode get|set` — read / change mode.toml (per D.84).
 *  - `skb identity show|edit|preview` — read / write identity.toml fields.
 *  - `skb dom set-persona|cadence|respond` — manage dom-persona pointer + send response.
 *  - `skb review list` — enumerate `reviews/<sha>/` entries.
 */

class ModeGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "mode") {
  init { subcommands(ModeGet(ctxOf), ModeSet(ctxOf)) }
  override fun run() = Unit
}

private class ModeGet(val ctxOf: () -> CliContext) : CliktCommand(name = "get") {
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val m = CliModeToml.read(root)
    if (ctx.json) {
      echo(JsonEnvelope.success("mode.get", buildJsonObject {
        put("mode", JsonPrimitive(m.mode.wire))
        m.writeBackTarget?.let { put("write_back_target", JsonPrimitive(it)) }
        m.domPersona?.let { put("dom_persona", JsonPrimitive(it)) }
        m.domCadence?.let { put("dom_cadence", JsonPrimitive(it)) }
        m.keptSince?.let { put("kept_since", JsonPrimitive(it)) }
      }))
    } else {
      echo("mode: ${m.mode.wire}")
      m.writeBackTarget?.let { echo("write_back_target: $it") }
      m.domPersona?.let { echo("dom_persona: $it") }
      m.domCadence?.let { echo("dom_cadence: $it") }
      m.keptSince?.let { echo("kept_since: $it") }
    }
  }
}

private class ModeSet(val ctxOf: () -> CliContext) : CliktCommand(name = "set") {
  val modeArg by argument("mode", help = "free | strictly-kept")
  val writeBackTarget by option("--write-back-target")
  val domPersona by option("--dom-persona")
  val domCadence by option("--dom-cadence")
  override fun run() {
    val ctx = ctxOf()
    val newMode = try { CliRepoMode.fromWire(modeArg) }
      catch (e: IllegalArgumentException) {
        throw CliError(ExitCode.USAGE, e.message ?: "bad mode")
      }
    val root = ctx.repoRoot()
    val curr = CliModeToml.read(root)
    val now = OffsetDateTime.now().withNano(0).toString()
    val updated = curr.copy(
      mode = newMode,
      writeBackTarget = writeBackTarget ?: curr.writeBackTarget,
      domPersona = domPersona ?: curr.domPersona,
      domCadence = domCadence ?: curr.domCadence,
      keptSince = if (newMode == CliRepoMode.StrictlyKept && curr.keptSince == null) now else curr.keptSince,
    )
    if (ctx.dryRun) {
      if (ctx.json) echo(JsonEnvelope.success("mode.set", buildJsonObject {
        put("dry_run", JsonPrimitive(true))
        put("mode", JsonPrimitive(updated.mode.wire))
      })) else echo("[dry-run] would set mode = ${updated.mode.wire}")
      return
    }
    CliModeToml.write(root, updated)
    if (ctx.json) echo(JsonEnvelope.success("mode.set", buildJsonObject {
      put("mode", JsonPrimitive(updated.mode.wire))
    })) else echo("mode set: ${updated.mode.wire}")
  }
}

class IdentityGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "identity") {
  init { subcommands(IdentityShow(ctxOf), IdentityEdit(ctxOf), IdentityPreview(ctxOf)) }
  override fun run() = Unit
}

private class IdentityShow(val ctxOf: () -> CliContext) : CliktCommand(name = "show") {
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val id = CliIdentityToml.read(root)
    if (ctx.json) {
      echo(JsonEnvelope.success("identity.show", buildJsonObject {
        put("praise_term", JsonPrimitive(id.praiseTerm))
        put("pronouns", JsonPrimitive("${id.pronounsSubject}/${id.pronounsObject}"))
        put("honorific_for_dom", JsonPrimitive(id.honorificForDom))
        put("tone_register", JsonPrimitive(id.toneRegister))
        put("emoji_density", JsonPrimitive(id.emojiDensity))
      }))
    } else {
      echo("praise.term = ${id.praiseTerm}")
      echo("pronouns = ${id.pronounsSubject}/${id.pronounsObject}/${id.pronounsPossessive}/${id.pronounsReflexive}")
      echo("honorific_for_dom = ${id.honorificForDom}")
      echo("tone.register = ${id.toneRegister}")
      echo("tone.emoji_density = ${id.emojiDensity}")
    }
  }
}

private class IdentityEdit(val ctxOf: () -> CliContext) : CliktCommand(name = "edit") {
  val praise by option("--praise")
  val honorific by option("--honorific")
  val toneRegister by option("--tone")
  val emojiDensity by option("--emoji")
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val curr = CliIdentityToml.read(root)
    val updated = curr.copy(
      praiseTerm = praise?.takeIf { it.isNotBlank() } ?: curr.praiseTerm,
      honorificForDom = honorific ?: curr.honorificForDom,
      toneRegister = toneRegister ?: curr.toneRegister,
      emojiDensity = emojiDensity ?: curr.emojiDensity,
    )
    if (updated.praiseTerm.isBlank()) {
      throw CliError(ExitCode.USAGE, "praise.term must not be blank (DM-Y.5)")
    }
    if (ctx.dryRun) {
      if (ctx.json) echo(JsonEnvelope.success("identity.edit", buildJsonObject {
        put("dry_run", JsonPrimitive(true)); put("praise_term", JsonPrimitive(updated.praiseTerm))
      })) else echo("[dry-run] would set praise.term = ${updated.praiseTerm}")
      return
    }
    CliIdentityToml.write(root, updated)
    if (ctx.json) echo(JsonEnvelope.success("identity.edit", buildJsonObject {
      put("praise_term", JsonPrimitive(updated.praiseTerm))
    })) else echo("identity updated.")
  }
}

private class IdentityPreview(val ctxOf: () -> CliContext) : CliktCommand(name = "preview") {
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val id = CliIdentityToml.read(root)
    val emoji = when (id.emojiDensity) {
      "none" -> ""; "sparse" -> " ✨"; "lush" -> " ✨✨✨"; else -> " ✨"
    }
    val sample = listOf(
      "now-card: \"time to plan with ${id.praiseTerm}\"$emoji",
      "template-title: \"${id.praiseTerm}'s tasks for today\"",
      "briefing: \"good morning, ${id.praiseTerm}\"$emoji",
      "dom-response: \"keep going, ${id.praiseTerm}. — ${id.honorificForDom}\"",
    )
    if (ctx.json) {
      echo(JsonEnvelope.success("identity.preview", buildJsonObject {
        sample.forEachIndexed { i, s -> put("line_$i", JsonPrimitive(s)) }
      }))
    } else {
      sample.forEach { echo(it) }
    }
  }
}

class DomGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "dom") {
  init { subcommands(DomSetPersona(ctxOf), DomCadenceCmd(ctxOf), DomRespond(ctxOf)) }
  override fun run() = Unit
}

private class DomSetPersona(val ctxOf: () -> CliContext) : CliktCommand(name = "set-persona") {
  val personaId by argument("persona-id")
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val curr = CliModeToml.read(root)
    val updated = curr.copy(domPersona = personaId)
    if (ctx.dryRun) {
      echo("[dry-run] would set dom_persona = $personaId"); return
    }
    CliModeToml.write(root, updated)
    if (ctx.json) echo(JsonEnvelope.success("dom.set-persona", buildJsonObject {
      put("dom_persona", JsonPrimitive(personaId))
    })) else echo("dom_persona set: $personaId")
  }
}

private class DomCadenceCmd(val ctxOf: () -> CliContext) : CliktCommand(name = "cadence") {
  val cadenceArg by argument("cadence", help = "realtime | end-of-day | weekly")
  override fun run() {
    val ctx = ctxOf()
    if (cadenceArg !in setOf("realtime", "end-of-day", "weekly")) {
      throw CliError(ExitCode.USAGE, "cadence must be realtime|end-of-day|weekly")
    }
    val root = ctx.repoRoot()
    val curr = CliModeToml.read(root)
    val updated = curr.copy(domCadence = cadenceArg)
    if (ctx.dryRun) {
      echo("[dry-run] would set dom_cadence = $cadenceArg"); return
    }
    CliModeToml.write(root, updated)
    if (ctx.json) echo(JsonEnvelope.success("dom.cadence", buildJsonObject {
      put("dom_cadence", JsonPrimitive(cadenceArg))
    })) else echo("dom_cadence set: $cadenceArg")
  }
}

/** `skb dom respond <commit-sha> --reactions <list> [--body <md>]` — writes review response file. */
private class DomRespond(val ctxOf: () -> CliContext) : CliktCommand(name = "respond") {
  val commitSha by argument("commit-sha")
  val reactions by option("--reactions", help = "comma-separated reaction tokens")
  val body by option("--body", help = "free-text markdown body")
  val responder by option("--responder", help = "responder label")
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val rxList = reactions?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val allowed = listOf("locked", "collar", "good-boy", "paw", "heart", "fire", "thumbsup", "🦇", "smirk")
    rxList.forEach {
      if (it !in allowed) throw CliError(ExitCode.USAGE, "unknown reaction: $it (DM-Z.2)")
    }
    val now = OffsetDateTime.now().withNano(0).toString()
    val tsSafe = now.replace(":", "").replace("+", "p")
    val identity = CliIdentityToml.read(root)
    val fingerprint = "skb-cli-${identity.praiseTerm.hashCode().toUInt().toString(16)}"
    val label = responder ?: identity.honorificForDom
    val dir = root.resolve("reviews/$commitSha/responses")
    val target = dir.resolve("$fingerprint-$tsSafe.md")
    if (ctx.dryRun) {
      echo("[dry-run] would write $target"); return
    }
    Files.createDirectories(dir)
    val cuteLgtm = body.isNullOrBlank() && rxList == listOf("good-boy")
    val fm = buildString {
      append("+++\n")
      append("kind = \"review_response\"\n")
      append("commit_sha = \"$commitSha\"\n")
      if (rxList.isNotEmpty()) {
        append("reactions = ")
        append(rxList.joinToString(prefix = "[", postfix = "]", separator = ", ") { "\"$it\"" })
        append('\n')
      }
      append("responder_fingerprint = \"$fingerprint\"\n")
      append("responder_label = \"$label\"\n")
      append("created = $now\n")
      if (cuteLgtm) append("cute_coded_lgtm = true\n")
      append("+++\n\n")
      append(body ?: "")
      append('\n')
    }
    Files.write(target, fm.toByteArray(Charsets.UTF_8))
    if (ctx.json) echo(JsonEnvelope.success("dom.respond", buildJsonObject {
      put("path", JsonPrimitive(root.relativize(target).toString()))
      if (cuteLgtm) put("cute_coded_lgtm", JsonPrimitive(true))
    })) else echo("wrote ${root.relativize(target)}")
  }
}

class ReviewGroup(private val ctxOf: () -> CliContext) : CliktCommand(name = "review") {
  init { subcommands(ReviewList(ctxOf)) }
  override fun run() = Unit
}

private class ReviewList(val ctxOf: () -> CliContext) : CliktCommand(name = "list") {
  val unread by option("--unread").flag()
  override fun run() {
    val ctx = ctxOf()
    val root = ctx.repoRoot()
    val reviewsDir = root.resolve("reviews")
    if (!Files.isDirectory(reviewsDir)) {
      if (ctx.json) echo(JsonEnvelope.success("review.list", buildJsonObject {
        put("count", JsonPrimitive(0))
      })) else echo("no reviews/ directory")
      return
    }
    val entries = Files.list(reviewsDir).use { it.toList() }
      .filter { Files.isDirectory(it) }
      .filter { Files.isRegularFile(it.resolve("reviewable_change.md")) }
      .sortedByDescending { it.fileName.toString() }
    val filtered = if (unread) {
      entries.filter { p ->
        val resp = p.resolve("responses")
        !Files.isDirectory(resp) || Files.list(resp).use { it.toList() }.isEmpty()
      }
    } else entries
    if (ctx.json) {
      echo(JsonEnvelope.success("review.list", buildJsonObject {
        put("count", JsonPrimitive(filtered.size))
        filtered.forEachIndexed { i, p ->
          put("sha_$i", JsonPrimitive(p.fileName.toString()))
        }
      }))
    } else {
      if (filtered.isEmpty()) echo("(no reviews)") else
        filtered.forEach { echo(it.fileName.toString()) }
    }
  }
}

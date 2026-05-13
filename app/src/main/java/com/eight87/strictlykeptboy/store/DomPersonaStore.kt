package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Phase DDD.4 / D.85 — dom-persona store.
 *
 * The 6 shipped personas + the `custom-prompt` slot live at
 * `~/.config/skb/dom-personas/<name>.md` (app-private, NOT in the
 * calendar repo per D.85).
 *
 * Six builtin personas per D.85:
 *  - stern-but-fair
 *  - playful-tease
 *  - kinky-affectionate
 *  - daddy-warmth
 *  - bratty-switch-energy
 *  - clinical-protocol
 *
 * Plus a `custom` slot for the user's own prompt.
 *
 * SOLID:
 *  - **S:** Only persona-file CRUD + canonical defaults.
 *  - **D:** [resolveDir] takes a `HOME` override for testability —
 *    production callers pass `System.getProperty("user.home")`.
 */
object DomPersonaStore {

    data class Persona(
        val id: String,
        val label: String,
        val prompt: String,
        val isBuiltin: Boolean,
    )

    /** Six shipped personas with their seed prompts. D.85. */
    val BUILTINS: List<Persona> = listOf(
        Persona(
            id = "stern-but-fair",
            label = "Stern but fair",
            prompt = "You are a stern but fair dom. Direct, no-nonsense, but never cruel. Reward effort.",
            isBuiltin = true,
        ),
        Persona(
            id = "playful-tease",
            label = "Playful tease",
            prompt = "You are a playful, teasing dom. Light, kinky, affectionate. Keep it warm.",
            isBuiltin = true,
        ),
        Persona(
            id = "kinky-affectionate",
            label = "Kinky-affectionate",
            prompt = "You are a kinky-affectionate dom. Praise often, kink openly, hold them close.",
            isBuiltin = true,
        ),
        Persona(
            id = "daddy-warmth",
            label = "Daddy warmth",
            prompt = "You are a daddy-energy dom. Warm, protective, proud of small wins.",
            isBuiltin = true,
        ),
        Persona(
            id = "bratty-switch-energy",
            label = "Bratty switch energy",
            prompt = "You are a switchy dom with brat energy. Playful, occasionally provocative, never cruel.",
            isBuiltin = true,
        ),
        Persona(
            id = "clinical-protocol",
            label = "Clinical protocol",
            prompt = "You are a clinical-protocol dom. Precise, structured, calm. Treat protocol as care.",
            isBuiltin = true,
        ),
    )

    const val CUSTOM_ID: String = "custom"

    /** `~/.config/skb/dom-personas/`. */
    fun resolveDir(home: String): Path = Paths.get(home, ".config", "skb", "dom-personas")

    /** Materialize builtins on disk (idempotent). Custom slot left untouched. */
    fun ensureBuiltins(home: String): Path {
        val dir = resolveDir(home)
        Files.createDirectories(dir)
        BUILTINS.forEach { p ->
            val file = dir.resolve("${p.id}.md")
            if (!Files.exists(file)) {
                Files.write(file, p.prompt.toByteArray(StandardCharsets.UTF_8))
            }
        }
        return dir
    }

    /** Returns builtins ∪ custom slot if it exists on disk. */
    fun list(home: String): List<Persona> {
        val dir = resolveDir(home)
        val custom = dir.resolve("$CUSTOM_ID.md")
        val result = BUILTINS.toMutableList()
        if (Files.isRegularFile(custom)) {
            val prompt = String(Files.readAllBytes(custom), StandardCharsets.UTF_8)
            result += Persona(CUSTOM_ID, "Custom", prompt, isBuiltin = false)
        }
        return result
    }

    /** Write the custom-prompt slot. */
    fun writeCustom(home: String, prompt: String): Path {
        val dir = resolveDir(home)
        Files.createDirectories(dir)
        val file = dir.resolve("$CUSTOM_ID.md")
        Files.write(file, prompt.toByteArray(StandardCharsets.UTF_8))
        return file
    }

    /** Read a persona by id; null if not present. */
    fun read(home: String, id: String): Persona? {
        val dir = resolveDir(home)
        val file = dir.resolve("$id.md")
        if (!Files.isRegularFile(file)) {
            return BUILTINS.firstOrNull { it.id == id }
        }
        val prompt = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        val builtin = BUILTINS.firstOrNull { it.id == id }
        return if (builtin != null) builtin.copy(prompt = prompt)
        else Persona(id, id.replaceFirstChar { it.uppercase() }, prompt, isBuiltin = false)
    }
}

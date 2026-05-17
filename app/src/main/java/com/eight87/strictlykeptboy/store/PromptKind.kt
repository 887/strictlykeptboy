package com.eight87.strictlykeptboy.store

/**
 * Round 2.27 / D-2.27.a — Keeper-prompt schema enums.
 *
 * Events / recurrence rules with `requires_response = true` carry a
 * [PromptKind] (what response shape the keeper wants — a photo, a
 * text reply, or a plain check-in tap) and a [PromptTarget] (who the
 * prompt is from — keeper-authored prompts that demand the boy's
 * response, or self-directed pings the boy authored for themselves).
 *
 * Both enums round-trip through TOML via [tomlValue] / [fromToml].
 * Mirrors the [ReminderKind] / [LockscreenVisibility] pattern in
 * [Reminder.kt].
 *
 * Unknown wire values fall back to the most permissive default —
 * [PromptKind.CheckIn] / [PromptTarget.Keeper] — so hand-edited typos
 * never lose data; the schema validator (DM-H) will warn separately.
 */
enum class PromptKind(val tomlValue: String) {
    Photo("photo"),
    Text("text"),
    CheckIn("check-in");

    /** Convenience alias used by writers — matches `ReminderKind.label` convention. */
    val label: String get() = tomlValue

    companion object {
        fun fromToml(s: String?): PromptKind? =
            if (s == null) null else entries.firstOrNull { it.tomlValue == s } ?: CheckIn
    }
}

enum class PromptTarget(val tomlValue: String) {
    Keeper("keeper"),
    Self("self");

    val label: String get() = tomlValue

    companion object {
        fun fromToml(s: String?): PromptTarget? =
            if (s == null) null else entries.firstOrNull { it.tomlValue == s } ?: Keeper
    }
}

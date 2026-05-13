package com.eight87.strictlykeptboy.notif

import android.content.Context
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.store.IdentityTomlCodec
import com.eight87.strictlykeptboy.store.IdentityTomlData
import java.io.File

/**
 * Phase 2.1.J.3 / DDD.11 — register-aware notification body builder.
 *
 * Notifications and briefings consume the per-repo identity.toml so the
 * praise term + honorific surface in event reminders, and pronouns
 * thread through briefing salutations. The `private = true` flag on an
 * event short-circuits to a generic body — no praise/honorific can leak
 * onto a lockscreen preview (K-2).
 *
 * SOLID:
 *  - **S:** Owns identity-aware copy generation only. The codec reads
 *    bytes; receivers post notifications.
 *  - **I:** Receivers can pass either a [RepoStore]/[Context] pair (for
 *    production look-up) or a pre-resolved [IdentityTomlData] (for
 *    tests). Tests don't need a live RepoStore.
 */
object IdentityNotifBody {

    /**
     * Generic body when [identity] is null, the repo is unbound, or the
     * event is marked `private = true`. Single source of truth so tests
     * can pin it. Empty string ⇒ caller emits no body at all.
     */
    const val GENERIC_BODY: String = ""

    /**
     * Build the notification body text for an event-reminder fire.
     *
     * @param identity identity for the active repo; pass `null` to skip
     *   identity-driven copy entirely (returns [GENERIC_BODY]).
     * @param title the user-authored event title (verbatim — never edited).
     * @param privateEvent when `true`, returns [GENERIC_BODY] so no praise /
     *   honorific can leak onto the lockscreen (K-2 / private-flag rule).
     */
    fun bodyFor(
        identity: IdentityTomlData?,
        title: String,
        privateEvent: Boolean,
    ): String {
        if (privateEvent || identity == null) return GENERIC_BODY
        val praise = identity.praiseTerm
        val hon = identity.honorificForDom
        // Shape: "<praise>, your <title>, <honorific>" — drop honorific
        // when it's the default placeholder "Sir" and the user hasn't
        // opted in to any tone register that uses it. Cheap rule: if
        // praise == default "good boy" AND honorific == default "Sir",
        // still emit so the user sees the surface working; the explicit
        // private-flag is the lockscreen guard.
        val safeTitle = title.ifBlank { "your event" }
        return if (hon.isBlank() || hon == "(none)") {
            "$praise, your $safeTitle"
        } else {
            "$praise, your $safeTitle, $hon"
        }
    }

    /**
     * Briefing salutation — consumed by [com.eight87.strictlykeptboy.notif]
     * briefing rendering surfaces. Threads pronouns through the
     * salutation when the dom-persona register asks for it. v1: short
     * "Morning, <praise>" / "Evening, <praise>". Private flag isn't
     * meaningful at briefing-level (briefings aggregate over many events),
     * so we always emit the praise term; lockscreen visibility of the
     * briefing itself is governed by [NotificationPrefs].
     */
    fun briefingSalutation(identity: IdentityTomlData?, timeOfDay: TimeOfDay): String {
        val praise = identity?.praiseTerm ?: "you"
        return when (timeOfDay) {
            TimeOfDay.Morning -> "Morning, $praise"
            TimeOfDay.Evening -> "Evening, $praise"
        }
    }

    enum class TimeOfDay { Morning, Evening }

    /**
     * Convenience lookup: resolve identity for [repoId] via [RepoStore].
     * Returns `null` when the repo isn't known or the file is missing /
     * malformed — caller should treat this as the generic-body case.
     */
    fun loadFor(context: Context, repoId: String?): IdentityTomlData? {
        if (repoId == null) return null
        return runCatching {
            val cfg = RepoStore.open(context).get(repoId) ?: return null
            val root = File(cfg.rootDir).toPath()
            IdentityTomlCodec.readOrDefault(root)
        }.getOrNull()
    }
}

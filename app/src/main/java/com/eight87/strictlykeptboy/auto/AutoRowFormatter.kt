package com.eight87.strictlykeptboy.auto

import com.eight87.strictlykeptboy.store.IdentityTomlData
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Phase 2.1.G — pure helpers that turn a row + identity snapshot into
 * driver-readable Auto-row text.
 *
 * Lives outside [TodayScreen] / [NextUpScreen] so:
 *  - **S:** Each Screen file owns its template assembly; the copy
 *    rules (truncation, register branching, off-schedule prefix) live
 *    in one place we can unit-test directly without a `TestCarContext`.
 *  - **D:** Screens depend on this object (pure), not the other way
 *    round; the formatter does not import `androidx.car.app.*`.
 *  - **I:** Callers pass only the fields the formatter actually reads;
 *    no `MaterializedInstance` / `CarContext` god-handle threaded
 *    through.
 *
 * String resources are NOT routed through here on purpose — the
 * identity-driven copy is dynamic (praise term + honorific come from
 * the user's wizard choices, not `strings.xml`), and the fallback
 * plain template format is owned by the calling Screen via
 * `carContext.getString(...)`. Truncation + off-schedule are
 * format-level concerns that should not be locale-overridden.
 */
object AutoRowFormatter {

    /**
     * Phase 2.1.G.1 — Auto host title cap. The Android Auto host
     * truncates row titles to roughly 36 chars on phone, less on
     * smaller surfaces. 24 chars is the safe heuristic per the
     * `androidx.car.app` HFP recommendations + the audit
     * `audit-2-1-notif-auto-tablet.md §2.1.G.1`.
     */
    const val MAX_TITLE_LEN: Int = 24

    /** Leading marker that replaces the dropped suffix when a title
     *  exceeds [MAX_TITLE_LEN]. Single char (Unicode U+2026) so the
     *  visible budget stays close to [MAX_TITLE_LEN]. */
    const val ELLIPSIS: String = "…"

    /** Phase 2.1.G.3 — prefix appended (with a trailing space) when an
     *  instance is flagged off-schedule (RV-Q / D.80). */
    const val OFF_SCHEDULE_PREFIX: String = "⚠ "

    private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Phase 2.1.G.1 — clip [title] to [MAX_TITLE_LEN] visible chars,
     * appending [ELLIPSIS] when truncation actually happens. Leaves
     * already-short titles alone (no spurious ellipsis on a 12-char
     * "Standup" row).
     *
     * Surrogate pairs + combining marks: v1 treats the title as a
     * `String` and counts code units. Most user titles are ASCII /
     * BMP; if a future test surfaces a surrogate-pair regression we
     * can switch to `codePointCount` here.
     */
    fun truncateTitle(title: String): String {
        if (title.length <= MAX_TITLE_LEN) return title
        return title.take(MAX_TITLE_LEN - ELLIPSIS.length) + ELLIPSIS
    }

    /**
     * Phase 2.1.G.2 — assemble the row title.
     *
     * Branches:
     *  - identity == null → plain `HH:mm  <title>` (the existing
     *    `auto_row_title` template shape). Caller is expected to
     *    format via `strings.xml`; this branch is here for tests
     *    + the fallback path inside the Screens.
     *  - identity != null, plain template path → `<HH:mm> — <title>`
     *    register-neutral; reserved for a future "neutral mode"
     *    toggle. Today the same shape as the kink-positive register
     *    because v1 only emits one shape.
     *  - identity != null, sub register (`toneRegister` contains
     *    "kinky" / "strict" / "submissive") → "your HH:mm, <hon>" or
     *    "your HH:mm <title>, <hon>". Mirrors
     *    [com.eight87.strictlykeptboy.notif.IdentityNotifBody.bodyFor]
     *    so the cross-surface voice is consistent.
     *
     * `offSchedule = true` prefixes the result with [OFF_SCHEDULE_PREFIX].
     * Title is clipped via [truncateTitle] *before* the praise/honorific
     * decoration so the visible budget is spent on the user-authored
     * substring, not the chrome.
     */
    fun rowTitle(
        start: ZonedDateTime,
        title: String,
        identity: IdentityTomlData?,
        offSchedule: Boolean = false,
    ): String {
        val time = TIME_FMT.format(start)
        val clipped = truncateTitle(title)
        val core = if (identity != null && isSubRegister(identity)) {
            val hon = identity.honorificForDom.takeIf { it.isNotBlank() && it != "(none)" }
            // "your 4pm — Standup, Sir" / "your 4pm — Standup" if honorific
            // suppressed.
            val honSuffix = hon?.let { ", $it" }.orEmpty()
            "your $time — $clipped$honSuffix"
        } else {
            // Plain template parity with strings.xml `auto_row_title`:
            // two-space gap so the existing Robolectric assertions that
            // search for "Standup" inside the title text still pass.
            "$time  $clipped"
        }
        return if (offSchedule) "$OFF_SCHEDULE_PREFIX$core" else core
    }

    /**
     * Phase 2.1.G.4 — empty-state copy.
     *
     * Branches on identity + register:
     *  - identity == null → "Nothing scheduled today." (matches
     *    `auto_today_empty` exactly for back-compat with the existing
     *    Robolectric `today_with_no_events_renders_no_items_message`
     *    test before identity wiring lands).
     *  - identity != null, sub register → "all clear, <praise>" using
     *    the user's praise term (default `good boy`).
     *  - identity != null, plain register → "nothing scheduled, <honorific>"
     *    when an honorific is set, else "nothing scheduled".
     */
    fun emptyStateCopy(identity: IdentityTomlData?): String {
        if (identity == null) return "Nothing scheduled today."
        return if (isSubRegister(identity)) {
            "all clear, ${identity.praiseTerm}"
        } else {
            val hon = identity.honorificForDom.takeIf { it.isNotBlank() && it != "(none)" }
            if (hon != null) "nothing scheduled, $hon" else "nothing scheduled"
        }
    }

    /**
     * Phase 2.1.G.2 / 2.1.G.4 — does the user's register want
     * praise-term / honorific copy?
     *
     * v1 heuristic: any register token containing "kinky", "strict",
     * "submissive", or "sub" lights up the sub-voice surfaces. The
     * default `soft-kinky` register hits this branch, which is the
     * point — the wizard default opts into the surface so the user
     * hears their identity choices back. Neutral-mode toggle is
     * orthogonal and handled by the caller deciding whether to pass
     * `identity = null`.
     */
    private fun isSubRegister(identity: IdentityTomlData): Boolean {
        val r = identity.toneRegister.lowercase()
        return "kinky" in r || "strict" in r || "submissive" in r || r == "sub"
    }
}

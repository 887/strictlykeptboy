package com.eight87.strictlykeptboy.share

/**
 * Phase O.1 — `strictlykeptboy://share` deep-link model.
 *
 * Scheme:
 *   strictlykeptboy://share
 *     ?url=<repo-url>            (1..N copies — multi-origin per D.74 / ZZ.B.5)
 *     &calendar=<id>             (optional, scopes the share to one calendar)
 *     &mode=read-only|read-write
 *     &expiry=<iso-instant>      (optional, no expiry if absent)
 *     &name=<source-repo-label>  (optional, surfaced as foreign-event chip)
 *     &back=<deep-link-url>      (optional, "Open in source app" CTA)
 *
 * The encoder always uses the parameter spelling `url`; legacy `repo`
 * is also accepted on decode (since the task spec describes that shape
 * in the user-facing description).
 *
 * Pure data — no Android imports. Lives in the neutral top-level
 * `share/` package (not `ui/share/`) because `store/AccessAggregator`
 * needs this type, and `store/` → `ui/` imports are forbidden by
 * R.X.6. UI composables (`ShareSheet`, `ShareLinkGenerator`, etc.) and
 * the codec / expiry helpers stay in `ui/share/`.
 */
data class ShareLink(
    val urls: List<String>,
    val mode: ShareMode,
    val calendarId: String? = null,
    val expiryIso: String? = null,
    val sourceLabel: String? = null,
    val backLink: String? = null,
    /**
     * Phase RR.1 / RR.4 — when true, the recipient should write
     * `write_back_target = "<repo-fingerprint>"` into its `references.toml`
     * entry for this share. Signals "sender invites feedback writes back
     * to its entries" (per YY.8 / FB-H). Pure data; the recipient-side
     * accept flow honours it.
     */
    val allowWriteBack: Boolean = false,
    /**
     * Phase RR.5 — sender-asserted one-shot token marker. The fragment
     * carries the actual credential; this top-level flag is the
     * agent-readable "this link was generated as single-use, the sender
     * has noted it as consumed" signal. Encoded as `single_use=true`.
     */
    val singleUseToken: Boolean = false,
) {
    init {
        require(urls.isNotEmpty()) { "ShareLink must carry at least one url" }
    }
}

enum class ShareMode { ReadOnly, ReadWrite;
    fun wire(): String = when (this) { ReadOnly -> "read-only"; ReadWrite -> "read-write" }
    companion object {
        fun parse(s: String?): ShareMode? = when (s) {
            "read-only" -> ReadOnly
            "read-write" -> ReadWrite
            else -> null
        }
    }
}

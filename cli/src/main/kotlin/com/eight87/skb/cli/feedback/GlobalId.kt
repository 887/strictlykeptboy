package com.eight87.skb.cli.feedback

/**
 * Phase YY.1 — global-ID parsing per the FB-A LOCK:
 * `<repo-fingerprint>:<entity-uuid>` where fingerprint is 16 lowercase
 * hex chars and entity is a 36-char UUIDv7.
 *
 * SOLID.S: this file does one thing — parse + format global IDs.
 * Validation refusal is the writer's job (see [FeedbackWriter]).
 */
data class GlobalId(val repoFingerprint: String, val entityUuid: String) {
  override fun toString(): String = "$repoFingerprint:$entityUuid"

  companion object {
    private val FINGERPRINT_RE = Regex("^[0-9a-f]{16}$")
    private val UUID7_RE = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    private val COMBINED_RE = Regex("^([0-9a-f]{16}):([0-9a-f-]{36})$")

    fun parse(raw: String): GlobalId? {
      val m = COMBINED_RE.matchEntire(raw) ?: return null
      val uuid = m.groupValues[2]
      if (!UUID7_RE.matches(uuid)) return null
      return GlobalId(m.groupValues[1], uuid)
    }

    fun isValidFingerprint(s: String): Boolean = FINGERPRINT_RE.matches(s)
    fun isValidEntityUuid(s: String): Boolean = UUID7_RE.matches(s)
  }
}

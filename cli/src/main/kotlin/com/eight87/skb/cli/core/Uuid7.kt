package com.eight87.skb.cli.core

import java.security.SecureRandom

/**
 * UUIDv7 generator (D.3 — IDs are UUIDv7, time-sortable).
 *
 * Layout (128 bits):
 *   - 48 bits unix-time-ms big-endian
 *   - 4 bits version (0b0111)
 *   - 12 bits random
 *   - 2 bits variant (0b10)
 *   - 62 bits random
 *
 * Pure-JVM; no Android dep. Duplicated from `:app`'s `git/Uuid7.kt` per
 * the extraction-vs-duplicate decision in `cli` module README (small,
 * self-contained, stable spec).
 */
object Uuid7 {
  private val rng = SecureRandom()

  fun generate(nowMs: Long = System.currentTimeMillis()): String {
    val bytes = ByteArray(16)
    // 48 bits of timestamp
    bytes[0] = ((nowMs ushr 40) and 0xFF).toByte()
    bytes[1] = ((nowMs ushr 32) and 0xFF).toByte()
    bytes[2] = ((nowMs ushr 24) and 0xFF).toByte()
    bytes[3] = ((nowMs ushr 16) and 0xFF).toByte()
    bytes[4] = ((nowMs ushr 8) and 0xFF).toByte()
    bytes[5] = (nowMs and 0xFF).toByte()
    val rand = ByteArray(10)
    rng.nextBytes(rand)
    System.arraycopy(rand, 0, bytes, 6, 10)
    // Set version (4 high bits of byte 6) = 0111
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
    // Set variant (2 high bits of byte 8) = 10
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    return format(bytes)
  }

  private fun format(b: ByteArray): String {
    val sb = StringBuilder(36)
    for (i in 0 until 16) {
      sb.append(HEX[(b[i].toInt() ushr 4) and 0x0F])
      sb.append(HEX[b[i].toInt() and 0x0F])
      if (i == 3 || i == 5 || i == 7 || i == 9) sb.append('-')
    }
    return sb.toString()
  }

  private val HEX = "0123456789abcdef".toCharArray()
}

package com.eight87.strictlykeptboy.git

import java.security.SecureRandom
import java.util.UUID

/**
 * UUIDv7 generator — time-sortable 128-bit IDs per draft-ietf-uuidrev-rfc4122bis.
 *
 * Layout (big-endian):
 *   bits  0..47  unix timestamp in ms (48 bits)
 *   bits 48..51  version = 0b0111 (4 bits)
 *   bits 52..63  random_a (12 bits)
 *   bits 64..65  variant = 0b10 (2 bits)
 *   bits 66..127 random_b (62 bits)
 *
 * Used by repo-id (Phase ZZ.A.4 / D.74) and entity IDs (D.6).
 */
object Uuid7 {
    private val rng = SecureRandom()

    fun generate(): UUID {
        val ms = System.currentTimeMillis()
        val randA = (rng.nextInt() and 0x0FFF).toLong()
        val randB = rng.nextLong()

        val msb = (ms shl 16) or (0x7L shl 12) or randA
        val lsb = (randB and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(msb, lsb)
    }
}

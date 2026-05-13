package com.eight87.skb.cli

import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.JsonEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitCodesTest {
  @Test fun taxonomyMatchesD24() {
    assertEquals(0, ExitCode.OK.code)
    assertEquals(1, ExitCode.USAGE.code)
    assertEquals(2, ExitCode.NOT_FOUND.code)
    assertEquals(3, ExitCode.CONFLICT.code)
    assertEquals(4, ExitCode.AUTH.code)
    assertEquals(5, ExitCode.CORRUPT.code)
    assertEquals(6, ExitCode.SCHEMA_MISMATCH.code)
    assertEquals(7, ExitCode.NETWORK.code)
    assertEquals(8, ExitCode.INTERNAL.code)
  }

  @Test fun wireNamesAreLowercaseUnderscore() {
    for (c in ExitCode.values()) {
      assertTrue("name should be lower_snake: ${c.wireName}", c.wireName.matches(Regex("^[a-z_]+$")))
    }
  }

  @Test fun errorEnvelopeShape() {
    val raw = JsonEnvelope.error("event.add", ExitCode.NOT_FOUND, "calendar X not found", mapOf("kind" to "calendar"))
    assertTrue(raw.contains("\"error\""))
    assertTrue(raw.contains("\"code\":\"not_found\""))
    assertTrue(raw.contains("\"message\":\"calendar X not found\""))
  }
}

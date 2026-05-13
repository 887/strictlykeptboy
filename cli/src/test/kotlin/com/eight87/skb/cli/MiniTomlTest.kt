package com.eight87.skb.cli

import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.MiniToml
import com.eight87.skb.cli.core.TomlTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniTomlTest {
  @Test fun roundTripsScalars() {
    val t = TomlTable().apply {
      putString("title", "Hello \"world\"")
      putInt("count", 3)
      putBool("done", true)
      putDateTime("at", "2026-05-12T14:00:00+02:00")
      putStringArray("tags", listOf("a", "b,c"))
    }
    val text = t.emit()
    val parsed = MiniToml.parse(text)
    assertEquals("Hello \"world\"", parsed.getString("title"))
    assertEquals(3L, parsed.getInt("count"))
    assertEquals(true, parsed.getBool("done"))
    assertEquals("2026-05-12T14:00:00+02:00", parsed.getDateTime("at"))
    assertEquals(listOf("a", "b,c"), parsed.getStringArray("tags"))
  }

  @Test fun frontmatterRoundTrip() {
    val t = TomlTable().apply {
      putString("kind", "event")
      putString("title", "Standup")
    }
    val text = Frontmatter.serialize(t, "body text\n")
    assertTrue(text.startsWith("+++\n"))
    val (parsed, body) = Frontmatter.parse(text)
    assertEquals("event", parsed.getString("kind"))
    assertEquals("Standup", parsed.getString("title"))
    assertEquals("body text\n", body)
  }

  @Test fun frontmatterEmptyBody() {
    val t = TomlTable().apply { putString("k", "v") }
    val (parsed, body) = Frontmatter.parse(Frontmatter.serialize(t))
    assertEquals("v", parsed.getString("k"))
    assertEquals("", body)
  }

  @Test fun parsesEmptyArray() {
    val parsed = MiniToml.parse("tags = []\n")
    assertEquals(emptyList<String>(), parsed.getStringArray("tags"))
  }

  @Test fun missingKeyReturnsNull() {
    val parsed = MiniToml.parse("a = 1\n")
    assertNull(parsed.getString("b"))
  }
}

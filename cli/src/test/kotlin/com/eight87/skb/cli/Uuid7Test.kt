package com.eight87.skb.cli

import com.eight87.skb.cli.core.Uuid7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Uuid7Test {
  private val pattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

  @Test fun isUuidV7Shape() {
    val id = Uuid7.generate()
    assertTrue("expected v7 shape, got: $id", pattern.matches(id))
  }

  @Test fun timeSortableAcrossInstants() {
    val a = Uuid7.generate(nowMs = 1_700_000_000_000L)
    val b = Uuid7.generate(nowMs = 1_700_000_000_500L)
    assertTrue("a should sort before b: a=$a b=$b", a < b)
  }

  @Test fun distinctConsecutive() {
    val a = Uuid7.generate(); val b = Uuid7.generate()
    assertNotEquals(a, b)
  }

  @Test fun version4thNibbleIs7() {
    val id = Uuid7.generate()
    // chars 14 (after two `-`) → version digit
    assertEquals('7', id[14])
  }
}

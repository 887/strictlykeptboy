package com.eight87.skb.cli

import com.eight87.skb.cli.core.CliIdentity
import com.eight87.skb.cli.core.CliIdentityToml
import com.eight87.skb.cli.core.CliMode
import com.eight87.skb.cli.core.CliModeToml
import com.eight87.skb.cli.core.CliRepoMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase DDD.15 / DDD.16 — CLI-side identity.toml + mode.toml codec
 * round-trips for the narrow shape `skb mode` / `skb identity` produces.
 */
class IdentityModeTomlTest {

  @Test fun identity_roundTrip_defaults() {
    val d = CliIdentity()
    val back = CliIdentityToml.parse(CliIdentityToml.emit(d))
    assertEquals(d, back)
  }

  @Test fun identity_roundTrip_customFields() {
    val d = CliIdentity(
      praiseTerm = "pup",
      altTerms = listOf("good pup", "puppy"),
      pronounsSubject = "they",
      pronounsObject = "them",
      pronounsPossessive = "theirs",
      pronounsReflexive = "themself",
      honorificForDom = "Daddy",
      toneRegister = "playful",
      emojiDensity = "lush",
    )
    val back = CliIdentityToml.parse(CliIdentityToml.emit(d))
    assertEquals(d, back)
  }

  @Test fun mode_roundTrip_kept() {
    val d = CliMode(
      mode = CliRepoMode.StrictlyKept,
      writeBackTarget = "https://forgejo/example/repo.git",
      domPersona = "stern-but-fair",
      domCadence = "realtime",
      keptSince = "2026-05-13T12:00:00+02:00",
    )
    val back = CliModeToml.parse(CliModeToml.emit(d))
    assertEquals(d, back)
  }

  @Test fun mode_fromWire_isLenient() {
    assertEquals(CliRepoMode.Free, CliRepoMode.fromWire(null))
    assertEquals(CliRepoMode.Free, CliRepoMode.fromWire("free"))
    assertEquals(CliRepoMode.StrictlyKept, CliRepoMode.fromWire("strictly-kept"))
  }
}

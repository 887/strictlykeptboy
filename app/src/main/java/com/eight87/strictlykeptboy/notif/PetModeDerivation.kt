package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.store.ModeTomlCodec
import com.eight87.strictlykeptboy.store.ModeTomlData
import com.eight87.strictlykeptboy.store.RepoMode
import java.nio.file.Path

/**
 * Phase 2.1.M.5 — derived Pet Mode classification.
 *
 * The wizard's "Pet Mode" framing (M.1) maps onto `mode.toml` state
 * (K.3 / `KeptBy`). This enum is the *notification-time* projection
 * so the praise/notification register can branch on a single value
 * without re-reading TOML internals on every fire.
 *
 * Mapping table (matches docs/plans/round-2-1.md §2.1.M):
 *
 * | PetMode        | mode.toml fields                                              |
 * |----------------|---------------------------------------------------------------|
 * | [SelfPet]      | `mode = strictly-kept` + `dom_persona` set (AI dom path)      |
 * | [PartneredPet] | `mode = strictly-kept` + `write_back_target` set, no persona  |
 * | [SelfKeep]     | `mode = self-keep`                                            |
 * | [None]         | `mode = free` (or any unrecognised combination)               |
 *
 * SOLID:
 *  - **S:** derivation only — no I/O wiring, no notification posting.
 *  - **O:** new variants get added here as Pet Mode framing grows;
 *    `bodyForPet` exhausts via a sealed `when` so the compiler enforces.
 *  - **D:** consumers depend on [PetMode] (a value), not on the TOML
 *    file layout.
 */
enum class PetMode {
    /** AI keeps you on track — `mode = strictly-kept` + `dom_persona`. */
    SelfPet,
    /** Partner keeps you — `mode = strictly-kept` + `write_back_target`. */
    PartneredPet,
    /** No AI, no partner — `mode = self-keep`. */
    SelfKeep,
    /** Plain calendar — `mode = free` or no kept-mode at all. */
    None,
}

/**
 * Phase 2.1.M.5 — pure derivation from a [ModeTomlData] value.
 * Visible-for-testing so [PetModeDerivationTest] doesn't need disk I/O.
 */
object PetModeDerivation {

    /**
     * Pure projection. `strictly-kept` with a persona is [PetMode.SelfPet]
     * (AI-dom); with a `write_back_target` and no persona it's
     * [PetMode.PartneredPet]; otherwise we fall back to [PetMode.SelfPet]
     * (the K.3 `keptBy` derivation defaults the same way when neither is
     * set yet). `self-keep` → [PetMode.SelfKeep]; `free` → [PetMode.None].
     */
    fun derive(data: ModeTomlData): PetMode = when (data.mode) {
        RepoMode.Free -> PetMode.None
        RepoMode.SelfKeep -> PetMode.SelfKeep
        RepoMode.StrictlyKept -> {
            val isPartnered = data.writeBackTarget != null && data.domPersona == null
            if (isPartnered) PetMode.PartneredPet else PetMode.SelfPet
        }
    }

    /**
     * Disk-reading convenience. Returns [PetMode.None] when the repo root
     * is null or the `mode.toml` is missing / malformed — callers treat
     * that the same as the no-kept-mode case.
     */
    fun deriveFor(repoRoot: Path?): PetMode {
        if (repoRoot == null) return PetMode.None
        return runCatching { derive(ModeTomlCodec.readOrDefault(repoRoot)) }
            .getOrDefault(PetMode.None)
    }
}

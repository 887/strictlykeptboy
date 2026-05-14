package com.eight87.strictlykeptboy.ui.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.1.B.5 — predicate covers the multirepo authorship case
 * regardless of `readOnlyViaShare` flag.
 */
class IsForeignBandTest {

    @Test fun band_from_other_repo_is_foreign() {
        assertTrue(isForeignBand(bandRepoId = "dom-overlay", defaultWriteRepoId = "my-cal"))
    }

    @Test fun band_from_same_repo_is_not_foreign() {
        assertFalse(isForeignBand(bandRepoId = "my-cal", defaultWriteRepoId = "my-cal"))
    }

    @Test fun blank_write_target_suppresses_chip() {
        assertFalse(isForeignBand(bandRepoId = "any", defaultWriteRepoId = ""))
    }

    @Test fun blank_band_repo_suppresses_chip() {
        assertFalse(isForeignBand(bandRepoId = "", defaultWriteRepoId = "my-cal"))
    }
}

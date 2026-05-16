package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.21 Phase D.3 — `hourHeightForZoom` maps the 4-stop zoom scale
 * to dp/h per D-2.21.h. Out-of-range inputs fall through to the default
 * (2 = 80dp/h).
 */
class HourHeightForZoomTest {
    @Test fun zoom_1_is_40dp_h() = assertEquals(40.dp, hourHeightForZoom(1))
    @Test fun zoom_2_is_80dp_h() = assertEquals(80.dp, hourHeightForZoom(2))
    @Test fun zoom_3_is_160dp_h() = assertEquals(160.dp, hourHeightForZoom(3))
    @Test fun zoom_4_is_320dp_h() = assertEquals(320.dp, hourHeightForZoom(4))
    @Test fun out_of_range_falls_to_default() {
        assertEquals(80.dp, hourHeightForZoom(0))
        assertEquals(80.dp, hourHeightForZoom(5))
        assertEquals(80.dp, hourHeightForZoom(-1))
    }
}

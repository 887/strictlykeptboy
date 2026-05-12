package com.eight87.strictlykeptboy.perf

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PerfTraceRecorderTest {

    @Test fun traceBlock_returnsValue_andTagsAreUnique() {
        val seen = PerfTraceRecorder.Section.entries.map { it.tag }.toSet()
        assertEquals(PerfTraceRecorder.Section.entries.size, seen.size)

        val result = PerfTraceRecorder.trace(PerfTraceRecorder.Section.AppOnCreate) { 42 }
        assertEquals(42, result)

        // Nested sections must end cleanly (no exception bubbles up).
        PerfTraceRecorder.trace(PerfTraceRecorder.Section.AppGraphInit) {
            PerfTraceRecorder.trace(PerfTraceRecorder.Section.MainActivityOnCreate) {
                Unit
            }
        }
    }

    @Test fun beginAndEnd_pair_neverThrowsOnDoubleEnd() {
        // The wrap swallows Trace errors; ensure callers can't crash the
        // app via mismatched end (defensive: not a normal call path).
        PerfTraceRecorder.begin(PerfTraceRecorder.Section.SchedulePaneFirstRender)
        PerfTraceRecorder.end()
        PerfTraceRecorder.end() // tolerated
    }
}

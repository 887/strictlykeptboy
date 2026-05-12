package com.eight87.strictlykeptboy.perf

import android.os.Trace

/**
 * Phase V.1 — narrow tracer port for cold-start phase tagging.
 *
 * Wraps `android.os.Trace.beginSection / endSection` (Systrace / Perfetto
 * pickup) so that `MainActivity.onCreate`, `AppGraph` construction and the
 * first `SchedulePane` render are visible in a `perfetto` capture without
 * the call sites importing `android.os.Trace` directly (R.X.1).
 *
 * Sections are no-op-safe on the JVM path so that Robolectric tests that
 * exercise the same wiring don't crash; the real Android implementation
 * delegates to `android.os.Trace`.
 *
 * R.X.7 (ISP): callers depend on the [Section] enum + the two `begin` /
 * `end` methods only — no god-state, no thread-local plumbing.
 */
object PerfTraceRecorder {

    enum class Section(val tag: String) {
        AppOnCreate("skb:app_oncreate"),
        AppGraphInit("skb:appgraph_init"),
        MainActivityOnCreate("skb:mainactivity_oncreate"),
        SchedulePaneFirstRender("skb:schedulepane_first_render"),
    }

    /** Begin a perfetto trace section. Idempotent if Trace API throws. */
    fun begin(section: Section) {
        runCatching { Trace.beginSection(section.tag) }
    }

    /** End the most-recently-begun section. Pairs with [begin]. */
    fun end() {
        runCatching { Trace.endSection() }
    }

    /** Convenience: time a block and report nanos. Trace bounds applied. */
    inline fun <T> trace(section: Section, block: () -> T): T {
        begin(section)
        try {
            return block()
        } finally {
            end()
        }
    }
}

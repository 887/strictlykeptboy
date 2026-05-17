package com.eight87.strictlykeptboy.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.BandRef
import com.eight87.strictlykeptboy.resolver.NowNextSnapshot
import com.eight87.strictlykeptboy.widget.common.WidgetRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.25 Phase D.3 — asserts the shared widget binder writes the
 * expected text strings into the title + relative views, and that an
 * empty snapshot clears them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WidgetNowNextRenderTest {

    private val anchor: Instant = Instant.parse("2026-05-17T10:00:00Z")
    private val tz: ZoneId = ZoneId.of("UTC")

    private fun band(title: String, atOffsetMin: Long, emoji: String? = null): BandRef {
        val start = ZonedDateTime.ofInstant(anchor.plusSeconds(atOffsetMin * 60), tz)
        return BandRef(
            title = title,
            emoji = emoji,
            calendarDisplay = "demo",
            start = start,
            end = start.plusMinutes(30),
        )
    }

    @Test fun nextLine_formats_title_and_relative_pair() {
        val snap = NowNextSnapshot(
            now = null,
            next = band("brush teeth", atOffsetMin = 135, emoji = "🪥"),
            now_at = anchor,
        )
        val line = WidgetRenderer.nextLine(snap)!!
        assertEquals("Next: 🪥 brush teeth", line.title)
        assertEquals("in 2h 15m", line.relative)
    }

    @Test fun nextLine_returns_null_when_snapshot_has_no_next() {
        assertNull(WidgetRenderer.nextLine(NowNextSnapshot.Empty))
    }

    @Test fun bindNowNext_writes_into_remote_views() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val views = RemoteViews(ctx.packageName, R.layout.widget_now_4x2)
        val snap = NowNextSnapshot(
            now = null,
            next = band("brush teeth", atOffsetMin = 135, emoji = "🪥"),
            now_at = anchor,
        )
        val rendered = WidgetRenderer.bindNowNext(
            views = views,
            snapshot = snap,
            titleViewId = R.id.widget_subbeat,
            relativeViewId = R.id.widget_remaining,
        )
        assertTrue(rendered)
        // Inflate the RemoteViews against a host parent and read the
        // text values back from the realised TextViews.
        val applied = views.apply(ctx, android.widget.FrameLayout(ctx))
        val subbeat = applied.findViewById<android.widget.TextView>(R.id.widget_subbeat)
        val remaining = applied.findViewById<android.widget.TextView>(R.id.widget_remaining)
        assertEquals("Next: 🪥 brush teeth", subbeat?.text?.toString())
        assertEquals("in 2h 15m", remaining?.text?.toString())
    }

    @Test fun bindNowNext_clears_when_snapshot_empty() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val views = RemoteViews(ctx.packageName, R.layout.widget_now_4x2)
        val rendered = WidgetRenderer.bindNowNext(views, NowNextSnapshot.Empty)
        assertFalse(rendered)
    }
}

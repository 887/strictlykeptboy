package com.eight87.strictlykeptboy.auto

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Phase Q.3 — Next-up pane template (UI-S.2).
 *
 * Single [PaneTemplate] focused on one [MaterializedInstance]:
 *  - title row: HH:mm + event title
 *  - duration row: "1h 30m"
 *  - time-until row: "in 12 minutes" (or "now" / "starts in N hours")
 *  - up to 3 follow-up rows: the next instances after this one
 *
 * Includes an "Open in app" pane-action that fires an [Intent] back to
 * `MainActivity` on the phone (UI-S.4 still defers voice). Read-only by
 * contract; no edit affordances anywhere on the screen (Phase Q / UI-S.4).
 */
class NextUpScreen(
    carContext: CarContext,
    private val focus: MaterializedInstance,
    private val allToday: List<MaterializedInstance>,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val now = ZonedDateTime.now(focus.effectiveStart.zone)
        val titleText = carContext.getString(
            R.string.auto_row_title,
            TIME_FMT.format(focus.effectiveStart),
            focus.title,
        )

        val duration = Duration.between(focus.effectiveStart, focus.effectiveEnd)
        val durationText = carContext.getString(R.string.auto_pane_duration, formatDuration(duration))
        val timeUntilText = carContext.getString(R.string.auto_pane_time_until, formatTimeUntil(focus.effectiveStart, now))

        val paneBuilder = Pane.Builder()
            .addRow(Row.Builder().setTitle(titleText).build())
            .addRow(Row.Builder().setTitle(durationText).build())
            .addRow(Row.Builder().setTitle(timeUntilText).build())

        // Up to 3 follow-up rows for context (UI-S.2 — "small list of next 3").
        val followUps = allToday
            .filter { it.effectiveStart.isAfter(focus.effectiveStart) }
            .take(3)
        followUps.forEach { f ->
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(
                        carContext.getString(
                            R.string.auto_row_title,
                            TIME_FMT.format(f.effectiveStart),
                            f.title,
                        ),
                    )
                    .build(),
            )
        }

        paneBuilder.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.auto_open_in_app))
                .setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setClassName(
                            carContext.packageName,
                            "com.eight87.strictlykeptboy.MainActivity",
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    carContext.startActivity(intent)
                }
                .build(),
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(carContext.getString(R.string.auto_next_up_title))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun formatDuration(d: Duration): String {
        val mins = d.toMinutes().coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun formatTimeUntil(start: ZonedDateTime, now: ZonedDateTime): String {
        val mins = Duration.between(now, start).toMinutes()
        return when {
            mins <= 0L -> carContext.getString(R.string.auto_time_until_now)
            mins < 60L -> carContext.getString(R.string.auto_time_until_minutes, mins)
            else -> {
                val hours = mins / 60
                carContext.getString(R.string.auto_time_until_hours, hours)
            }
        }
    }

    companion object {
        private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

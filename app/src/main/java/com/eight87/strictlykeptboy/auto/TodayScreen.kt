package com.eight87.strictlykeptboy.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import java.time.format.DateTimeFormatter

/**
 * Phase Q.2 — Today list template (UI-S.1).
 *
 * Renders today's materialized event instances as a [ListTemplate]. One
 * [Row] per instance:
 *   title = HH:mm  + event title
 *   text  = repo / calendar emoji where available
 *   tap   = push [NextUpScreen] focused on the tapped instance.
 *
 * All user-facing strings come from `strings.xml` via
 * `carContext.getString(...)` (the CarContext equivalent of Compose's
 * `stringResource`).
 */
class TodayScreen(
    carContext: CarContext,
    private val source: TodayEventSource,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val events = source.eventsForToday()
            .sortedBy { it.effectiveStart.toInstant() }

        val builder = ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.auto_today_title))
            .setHeaderAction(Action.APP_ICON)

        if (events.isEmpty()) {
            builder.setLoading(false)
            builder.setSingleList(
                ItemList.Builder()
                    .setNoItemsMessage(carContext.getString(R.string.auto_today_empty))
                    .build(),
            )
        } else {
            val list = ItemList.Builder()
            events.forEach { evt -> list.addItem(rowFor(evt, events)) }
            builder.setSingleList(list.build())
        }
        return builder.build()
    }

    private fun rowFor(evt: MaterializedInstance, allToday: List<MaterializedInstance>): Row {
        val time = TIME_FMT.format(evt.effectiveStart)
        val titleText = carContext.getString(R.string.auto_row_title, time, evt.title)
        val secondary = evt.emoji ?: evt.calendar.id
        return Row.Builder()
            .setTitle(titleText)
            .addText(secondary)
            .setOnClickListener {
                screenManager.push(NextUpScreen(carContext, evt, allToday))
            }
            .build()
    }

    companion object {
        private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

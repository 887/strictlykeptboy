package com.eight87.strictlykeptboy.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.store.IdentityTomlData

/**
 * Phase Q.2 / 2.1.G — Today list template (UI-S.1).
 *
 * Renders today's materialized event instances as a [ListTemplate].
 * One [Row] per [AutoEvent]:
 *   title = identity-driven shape via [AutoRowFormatter.rowTitle];
 *           "⚠ " prefix when the resolver flagged off-schedule
 *   text  = repo / calendar emoji where available
 *   tap   = push [NextUpScreen] focused on the tapped instance.
 *
 * String resources back the title + empty-state for the **plain**
 * fallback path (no identity bound). The identity-driven copy is
 * dynamic (praise term + honorific live in `identity.toml`) and is
 * not routed through `strings.xml` — see [AutoRowFormatter] for the
 * branching rationale.
 */
class TodayScreen(
    carContext: CarContext,
    private val source: TodayEventSource,
    private val identityProvider: () -> IdentityTomlData? = { null },
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val identity = identityProvider()
        val events = source.eventsForToday()
            .sortedBy { it.instance.effectiveStart.toInstant() }

        val builder = ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.auto_today_title))
            .setHeaderAction(Action.APP_ICON)

        if (events.isEmpty()) {
            builder.setLoading(false)
            builder.setSingleList(
                ItemList.Builder()
                    .setNoItemsMessage(AutoRowFormatter.emptyStateCopy(identity))
                    .build(),
            )
        } else {
            val list = ItemList.Builder()
            events.forEach { evt -> list.addItem(rowFor(evt, events, identity)) }
            builder.setSingleList(list.build())
        }
        return builder.build()
    }

    private fun rowFor(
        evt: AutoEvent,
        allToday: List<AutoEvent>,
        identity: IdentityTomlData?,
    ): Row {
        val titleText = AutoRowFormatter.rowTitle(
            start = evt.instance.effectiveStart,
            title = evt.instance.title,
            identity = identity,
            offSchedule = evt.offSchedule,
        )
        val secondary = evt.instance.emoji ?: evt.instance.calendar.id
        return Row.Builder()
            .setTitle(titleText)
            .addText(secondary)
            .setOnClickListener {
                screenManager.push(NextUpScreen(carContext, evt, allToday, identityProvider))
            }
            .build()
    }
}

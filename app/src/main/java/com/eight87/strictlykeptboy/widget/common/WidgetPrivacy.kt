package com.eight87.strictlykeptboy.widget.common

import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.MaterializedInstance

/**
 * Phase VV.5 / EEE.8 (K-2 / D.57) — widget privacy contract.
 *
 * Pure decision object: given an instance + the surface it will render
 * on, decide whether to fall back to "scheduled event" + bat sticker.
 *
 * Lockscreen rule is stricter (EEE.8): any event whose calendar has
 * `default_private = true` is redacted regardless of the per-event flag.
 */
object WidgetPrivacy {
    enum class Surface { Home, Lockscreen }

    fun shouldRedact(
        instance: MaterializedInstance,
        surface: Surface,
        calendarDefaultPrivate: (CalendarRef) -> Boolean,
    ): Boolean {
        if (instance.isPrivate) return true
        if (surface == Surface.Lockscreen && calendarDefaultPrivate(instance.calendar)) return true
        return false
    }
}

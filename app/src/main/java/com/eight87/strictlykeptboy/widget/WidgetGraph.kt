package com.eight87.strictlykeptboy.widget

import android.content.Context
import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.avatar.DefaultStickerResolver
import com.eight87.strictlykeptboy.avatar.PackId
import com.eight87.strictlykeptboy.avatar.PackStore
import com.eight87.strictlykeptboy.avatar.StickerBitmapCache
import com.eight87.strictlykeptboy.avatar.StickerResolver
import com.eight87.strictlykeptboy.widget.common.ActiveEvent
import com.eight87.strictlykeptboy.widget.common.ActiveEventSource
import com.eight87.strictlykeptboy.widget.common.PinnedEvent
import com.eight87.strictlykeptboy.widget.common.PinnedEventSource
import com.eight87.strictlykeptboy.widget.common.WidgetStickerRenderer
import java.time.Instant

/**
 * Phase VV / EEE — composition root for widget collaborators.
 *
 * `AppWidgetProvider` is built by the platform with a no-arg constructor;
 * this object hands the providers their dependencies. Defaults are
 * "empty" so widgets render their empty state until a real source gets
 * installed from the AppGraph (post-MM).
 */
class WidgetGraph private constructor(
    val pinnedEventSource: PinnedEventSource,
    val activeEventSource: ActiveEventSource,
    val stickerRenderer: WidgetStickerRenderer,
    val activeSpecies: () -> String,
    val neutralMode: () -> Boolean,
) {

    companion object {
        @Volatile private var instance: WidgetGraph? = null

        fun get(context: Context): WidgetGraph {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: createDefault(context.applicationContext).also { instance = it }
            }
        }

        fun installForTest(graph: WidgetGraph) {
            synchronized(this) { instance = graph }
        }

        fun resetForTest() {
            synchronized(this) { instance = null }
        }

        fun build(
            pinnedEventSource: PinnedEventSource,
            activeEventSource: ActiveEventSource,
            stickerRenderer: WidgetStickerRenderer,
            activeSpecies: () -> String = { "bat" },
            neutralMode: () -> Boolean = { false },
        ): WidgetGraph = WidgetGraph(
            pinnedEventSource, activeEventSource, stickerRenderer, activeSpecies, neutralMode,
        )

        private fun createDefault(appContext: Context): WidgetGraph {
            val loader = AssetPackLoader(appContext)
            val store: PackStore = loader.loadAll()
            val resolver: StickerResolver = DefaultStickerResolver(
                packStore = store,
                activePackProvider = { species -> PackId.forBundledSpecies(species) },
            )
            val cache = StickerBitmapCache()
            val renderer = WidgetStickerRenderer(appContext, resolver, loader, cache)
            return WidgetGraph(
                pinnedEventSource = PinnedEventSource { _, _ -> PinnedEvent.Missing },
                activeEventSource = ActiveEventSource { _: Instant -> ActiveEvent.Absent },
                stickerRenderer = renderer,
                activeSpecies = { "bat" },
                neutralMode = { false },
            )
        }
    }
}

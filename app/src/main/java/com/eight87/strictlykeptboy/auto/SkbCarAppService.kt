package com.eight87.strictlykeptboy.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

/**
 * Phase Q.1 — Android Auto entry-point.
 *
 * Pattern mirrors [com.eight87.strictlykeptboy.sync.SyncService]:
 * - Manifest registration declares the intent-filter + category.
 * - The service is decoupled from the rest of the app via a process-wide
 *   runtime handle ([CarAppRuntime]) populated by the composition root
 *   ([com.eight87.strictlykeptboy.composition.AppGraph.parkRuntimes]).
 * - All concrete-class wiring lives in `AppGraph`; this file only reads
 *   the narrow [TodayEventSource] interface.
 *
 * Host validation: v1 allows ALL_HOSTS for headless smoke + Auto Simulator.
 * Production would tighten to the AOSP + Android Auto host package
 * signature list, but the host-validator default already enforces signed
 * packages from the projection host whitelist.
 */
class SkbCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(sessionInfo: SessionInfo): Session = SkbSession()
}

/**
 * Phase Q.1 — the Auto session.
 *
 * Owns the [Screen] stack. Initial screen is [TodayScreen]; the user can
 * push [NextUpScreen] by tapping a row.
 *
 * The session reads from [CarAppRuntime.todayEventSource]; if `null`
 * (phone app never reached `MainActivity.onCreate` in this process —
 * Auto cold-started us), it falls back to an empty source so the
 * screen renders the empty-state ListTemplate.
 */
class SkbSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val source = CarAppRuntime.todayEventSource ?: TodayEventSource { emptyList() }
        return TodayScreen(carContext, source)
    }
}

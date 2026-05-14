package com.eight87.strictlykeptboy.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.eight87.strictlykeptboy.BuildConfig

/**
 * Phase Q.1 / 2.1.G.5 — Android Auto entry-point.
 *
 * Pattern mirrors [com.eight87.strictlykeptboy.sync.SyncService]:
 * - Manifest registration declares the intent-filter + category.
 * - The service is decoupled from the rest of the app via a process-wide
 *   runtime handle ([CarAppRuntime]) populated by the composition root
 *   ([com.eight87.strictlykeptboy.composition.AppGraph.parkRuntimes]).
 * - All concrete-class wiring lives in `AppGraph`; this file only reads
 *   the narrow [TodayEventSource] interface.
 *
 * Host validation:
 *  - **Debug builds** allow ALL_HOSTS so the headless emulator + Auto
 *    Simulator (which is not signed by the production AOSP key) can
 *    drive the surface during development. Gated on
 *    [BuildConfig.DEBUG] so this never compiles into a release APK.
 *  - **Release builds** delegate to the framework-provided allow-list
 *    declared in `R.array.hosts_allowlist_sample` (AOSP + Android Auto
 *    projection hosts, the canonical car-app-library template). The
 *    list is the AOSP `androidx.car.app:app` reference set — only
 *    Google-signed Auto / Automotive OS hosts pass.
 */
class SkbCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = SkbSession()
}

/**
 * Phase Q.1 / 2.1.G — the Auto session.
 *
 * Owns the [Screen] stack. Initial screen is [TodayScreen]; the user
 * can push [NextUpScreen] by tapping a row.
 *
 * The session reads from [CarAppRuntime.todayEventSource]; if `null`
 * (phone app never reached `MainActivity.onCreate` in this process —
 * Auto cold-started us), it falls back to an empty source so the
 * screen renders the empty-state ListTemplate.
 *
 * Identity provider (2.1.G.2 / 2.1.G.4) is similarly looked up
 * lazily; a `null` provider means "no identity bound" and the screens
 * use the plain `strings.xml` template fallback path.
 */
class SkbSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val source = CarAppRuntime.todayEventSource ?: TodayEventSource { emptyList() }
        val identityProvider = CarAppRuntime.identityProvider ?: { null }
        return TodayScreen(carContext, source, identityProvider)
    }
}

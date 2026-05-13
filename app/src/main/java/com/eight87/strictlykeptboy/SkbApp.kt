package com.eight87.strictlykeptboy

import android.app.Application
import com.eight87.strictlykeptboy.notif.NotificationChannels
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * IMPORTANT — JGit caches `java.io.tmpdir` at class-load time. We MUST set it
 * to the app's cacheDir BEFORE any JGit class loads. Do not move this logic to
 * lazy init: a static reference to `org.eclipse.jgit.*` anywhere in the call
 * graph above this point would freeze the wrong path (the OS default tmpdir,
 * which on Android is `/data/local/tmp` and is process-inaccessible).
 *
 * BouncyCastle provider must be inserted at slot 1 so its ed25519 implementation
 * wins over the platform's (which exists only on API 33+ and behaves differently
 * for OpenSSH-format encoding).
 */
class SkbApp : Application() {
    override fun onCreate() {
        com.eight87.strictlykeptboy.perf.PerfTraceRecorder.begin(
            com.eight87.strictlykeptboy.perf.PerfTraceRecorder.Section.AppOnCreate,
        )
        // Order matters — see KDoc above.
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)

        // Remove any pre-installed BC (e.g. in test harness) before re-inserting
        // at the top of the provider list.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        super.onCreate()

        // Phase M.1 — register all six notification channels at app start.
        // Idempotent: the OS dedupes by id, so we re-run on every cold start.
        NotificationChannels.registerAll(this)
        // Phase 2.1.F.6 — schedule the morning + evening briefing workers.
        // Idempotent (KEEP policy). The master toggle short-circuits at
        // doWork() time so flipping it off in Settings mutes briefings
        // without needing to cancel work. Wrapped in try/catch because
        // Robolectric unit tests boot the Application without a
        // WorkManager initialiser (custom androidx.work init is disabled
        // in this app's manifest), and WorkManager throws ISE on first
        // touch in that environment.
        try {
            com.eight87.strictlykeptboy.notif.BriefingWorker.scheduleAll(this)
        } catch (_: IllegalStateException) {
            // No-op — production wiring happens at the next call site that
            // explicitly initialises WorkManager.
        }
        com.eight87.strictlykeptboy.perf.PerfTraceRecorder.end()
    }
}

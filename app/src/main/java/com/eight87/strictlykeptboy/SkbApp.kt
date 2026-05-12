package com.eight87.strictlykeptboy

import android.app.Application
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
        // Order matters — see KDoc above.
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)

        // Remove any pre-installed BC (e.g. in test harness) before re-inserting
        // at the top of the provider list.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        super.onCreate()
    }
}

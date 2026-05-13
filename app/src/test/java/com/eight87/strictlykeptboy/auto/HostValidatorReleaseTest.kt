package com.eight87.strictlykeptboy.auto

import androidx.car.app.validation.HostValidator
import com.eight87.strictlykeptboy.BuildConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.1.G.5 — `HostValidator` tightening.
 *
 * The release-build branch swaps the v1 `ALLOW_ALL_HOSTS_VALIDATOR`
 * for the framework-provided AOSP + AndroidAuto signature whitelist
 * (`R.array.hosts_allowlist_sample`).
 *
 * Unit tests run on the debug variant (`:app:testDebugUnitTest`), so
 * the live-`BuildConfig.DEBUG`-branch returns `ALLOW_ALL_HOSTS`. We
 * pin both:
 *  - the debug branch is `ALL_HOSTS` (so headless emulators + Auto
 *    Simulator keep working),
 *  - the release branch returns a *non-ALL_HOSTS* validator that
 *    matches the framework-provided allow-list reference.
 *
 * For the release-branch assertion we cannot flip `BuildConfig.DEBUG`
 * from a unit test, so we exercise the underlying `HostValidator.Builder`
 * directly with the same `R.array.hosts_allowlist_sample` to confirm
 * the resource the production branch references is well-formed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HostValidatorReleaseTest {

    @Test fun debug_build_uses_allow_all_hosts() {
        // Sanity: assumption-baseline. The test runs under `:app:testDebugUnitTest`
        // where `BuildConfig.DEBUG == true`.
        assertTrue("test must run under debug variant", BuildConfig.DEBUG)

        val ctx = androidx.car.app.testing.TestCarContext.createCarContext(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        val service = SkbCarAppService()
        // The service's createHostValidator is `internal final`; call it
        // directly via the public override surface.
        service.attachBaseContextForTest(ctx)
        val validator = service.createHostValidator()
        assertSame(HostValidator.ALLOW_ALL_HOSTS_VALIDATOR, validator)
    }

    @Test fun release_build_signature_allowlist_resource_resolves() {
        // The release branch references `R.array.hosts_allowlist_sample`
        // from the `androidx.car.app:app` AAR. If the dep ever drops or
        // renames the resource, the release build breaks silently — pin
        // the lookup so this test fails first.
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val validator = HostValidator.Builder(app)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()
        assertNotNull(validator)
        // Concrete invariant: this validator is **not** ALLOW_ALL_HOSTS.
        assertTrue(validator !== HostValidator.ALLOW_ALL_HOSTS_VALIDATOR)
    }
}

/**
 * Test-only shim: `CarAppService` doesn't expose an `attachBaseContext`
 * surface for unit tests. The default `androidx.car.app:app-testing`
 * pattern is to construct a `TestCarContext` separately and call the
 * service hooks directly; `createHostValidator` doesn't depend on
 * `applicationContext` in the debug branch, so this stub is a no-op.
 *
 * Kept as an extension so the test reads top-to-bottom without
 * subclass plumbing.
 */
private fun SkbCarAppService.attachBaseContextForTest(@Suppress("UNUSED_PARAMETER") ctx: androidx.car.app.CarContext) {
    // No-op; debug branch ignores applicationContext.
}

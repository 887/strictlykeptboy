package com.eight87.strictlykeptboy.release

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase W.1 — assert the release build is wired through an env-var-driven
 * signing config that falls back to the debug keystore when the
 * `STRICTLYKEPTBOY_RELEASE_*` triple isn't set.
 *
 * Static-text scan of `app/build.gradle.kts`. Catches the load-bearing
 * regressions (someone removes `signingConfigs { create("release") }`,
 * someone forgets the fallback, someone hardcodes a path) without
 * needing to actually exercise Gradle.
 */
class ReleaseSigningConfigTest {

    private val buildGradle: String = run {
        // Test runs in `app/` working dir under Gradle.
        val candidates = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts"),
            File("../app/build.gradle.kts"),
        )
        val resolved = candidates.firstOrNull { it.exists() }
            ?: error("could not locate app/build.gradle.kts from ${File(".").absolutePath}")
        resolved.readText()
    }

    @Test
    fun declares_release_signing_config_block() {
        assertTrue(
            "signingConfigs block missing",
            buildGradle.contains("signingConfigs"),
        )
        assertTrue(
            "release signing config not created",
            buildGradle.contains("create(\"release\")"),
        )
    }

    @Test
    fun reads_keystore_from_env_vars() {
        assertTrue(
            "STRICTLYKEPTBOY_RELEASE_KEYSTORE env var not consumed",
            buildGradle.contains("STRICTLYKEPTBOY_RELEASE_KEYSTORE"),
        )
        assertTrue(
            "STRICTLYKEPTBOY_RELEASE_KEY_ALIAS env var not consumed",
            buildGradle.contains("STRICTLYKEPTBOY_RELEASE_KEY_ALIAS"),
        )
        assertTrue(
            "STRICTLYKEPTBOY_RELEASE_KEY_PASSWORD env var not consumed",
            buildGradle.contains("STRICTLYKEPTBOY_RELEASE_KEY_PASSWORD"),
        )
    }

    @Test
    fun release_buildtype_falls_back_to_debug_signing() {
        // Either the explicit fallback expression OR an explicit
        // `signingConfig = signingConfigs.findByName("release") ?:
        // signingConfigs.getByName("debug")` pattern.
        assertTrue(
            "release buildType not wired to a signingConfig fallback",
            buildGradle.contains("signingConfigs.findByName(\"release\")") &&
                buildGradle.contains("signingConfigs.getByName(\"debug\")"),
        )
    }

    @Test
    fun release_buildtype_has_minify_enabled() {
        // Phase W.6 — R8 minify locked on.
        assertTrue(
            "isMinifyEnabled must be true for release builds",
            buildGradle.contains("isMinifyEnabled = true"),
        )
    }
}

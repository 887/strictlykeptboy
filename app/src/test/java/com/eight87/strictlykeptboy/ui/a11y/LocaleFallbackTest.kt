package com.eight87.strictlykeptboy.ui.a11y

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Phase U.5 — locale fallback.
 *
 * `values-en-rGB/strings.xml` is intentionally partial: it overrides
 * `locale_demo_color_seed` to "Colour seed" (and a couple of seed-related
 * UI strings) while every other key falls back to `values/strings.xml`.
 * This proves the Android resource resolver's partial-override semantics
 * are correctly leveraged — translators do NOT need to mirror every
 * string from the canonical English file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocaleFallbackTest {

    private fun ctxIn(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }

    @Test fun default_english_says_color() {
        val s = ctxIn(Locale.US).getString(R.string.locale_demo_color_seed)
        check(s == "Color seed") { "expected 'Color seed', got '$s'" }
    }

    @Test fun en_gb_says_colour() {
        val s = ctxIn(Locale.UK).getString(R.string.locale_demo_color_seed)
        check(s == "Colour seed") { "expected 'Colour seed', got '$s'" }
    }

    @Test fun en_gb_falls_back_to_canonical_for_non_overridden_keys() {
        // The app_name lives only in values/strings.xml; en-GB must fall
        // back through to the canonical English copy.
        val gb = ctxIn(Locale.UK).getString(R.string.app_name)
        val us = ctxIn(Locale.US).getString(R.string.app_name)
        check(gb == us) { "expected fallback to canonical app_name; gb=$gb us=$us" }
    }

    @Test fun en_gb_overrides_repo_settings_color_seed_header() {
        val s = ctxIn(Locale.UK).getString(R.string.repo_settings_color_seed_header)
        check(s.startsWith("Colour")) { "expected 'Colour ...', got '$s'" }
    }
}

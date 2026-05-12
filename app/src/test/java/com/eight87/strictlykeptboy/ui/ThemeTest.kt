package com.eight87.strictlykeptboy.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.theme.AppearancePrefs
import com.eight87.strictlykeptboy.theme.DensityScale
import com.eight87.strictlykeptboy.theme.LocalDensityScale
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.theme.ThemeMode
import com.eight87.strictlykeptboy.theme.scaled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun density_multiplier_flows_through_to_scaled_dp() {
        // Compact: 0.75 × 16 = 12; Comfortable: 1.0 × 16 = 16; Spacious: 1.25 × 16 = 20.
        assertEquals(12.dp.value, 16.dp.scaled(DensityScale.Compact).value, 0.001f)
        assertEquals(16.dp.value, 16.dp.scaled(DensityScale.Comfortable).value, 0.001f)
        assertEquals(20.dp.value, 16.dp.scaled(DensityScale.Spacious).value, 0.001f)
    }

    @Test fun density_composition_local_is_provided_by_theme() {
        var capturedScale: DensityScale? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme(densityScale = DensityScale.Spacious, dynamicColor = false) {
                val s = LocalDensityScale.current
                SideEffect { capturedScale = s }
            }
        }
        composeRule.runOnIdle {
            assertEquals(DensityScale.Spacious, capturedScale)
        }
    }

    @Test fun theme_mode_light_vs_dark_yields_different_schemes() {
        var lightBg: androidx.compose.ui.graphics.Color? = null
        var darkBg: androidx.compose.ui.graphics.Color? = null
        composeRule.setContent {
            // Render both side-by-side in the same composition so we can read
            // both color schemes without re-entering setContent.
            StrictlyKeptBoyTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                val c = MaterialTheme.colorScheme.background
                SideEffect { lightBg = c }
            }
            StrictlyKeptBoyTheme(themeMode = ThemeMode.Dark, dynamicColor = false) {
                val c = MaterialTheme.colorScheme.background
                SideEffect { darkBg = c }
            }
        }
        composeRule.runOnIdle {
            assertNotEquals(lightBg, darkBg)
        }
    }

    @Test fun appearance_prefs_round_trip() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val raw = ctx.getSharedPreferences("appearance_test_${System.nanoTime()}", Context.MODE_PRIVATE)
        raw.edit().clear().apply()
        val prefs = AppearancePrefs.openForTest(raw)
        assertEquals(ThemeMode.Auto, prefs.state.value.themeMode)
        prefs.setThemeMode(ThemeMode.Dark)
        prefs.setDensity(DensityScale.Compact)
        prefs.setDynamicColor(false)
        // Reopen — values must persist.
        val reopened = AppearancePrefs.openForTest(raw)
        assertEquals(ThemeMode.Dark, reopened.state.value.themeMode)
        assertEquals(DensityScale.Compact, reopened.state.value.densityScale)
        assertEquals(false, reopened.state.value.dynamicColor)
    }
}

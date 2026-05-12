package com.eight87.strictlykeptboy.ui.theming

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarColorPickerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `clicking a swatch surfaces seed update`() {
        var captured: Int? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                val state = remember { mutableStateOf<Int?>(null) }
                Box(Modifier.size(800.dp)) {
                    CalendarColorPicker(
                        currentArgb = state.value,
                        onSeedChange = {
                            state.value = it
                            captured = it
                        },
                    )
                }
            }
        }
        // The orange swatch (0xFFFFB74D) tags by RGB-only int.
        val rgb = 0xFFFFB74D.toInt() and 0x00FFFFFF
        composeRule.onNodeWithTag("$TestTagCalColorSwatch-$rgb").performClick()
        assertNotNull(captured)
        assertEquals(0xFFFFB74D.toInt(), captured)
    }

    @Test fun `hex parse rejects invalid`() {
        assertNull(parseHexToArgb("zz"))
        assertNull(parseHexToArgb("#GGGGGG"))
        assertNull(parseHexToArgb("#1234"))
    }

    @Test fun `hex parse accepts six-digit`() {
        val argb = parseHexToArgb("#FF8800")
        assertNotNull(argb)
        // Always opaque alpha:
        assertEquals(0xFF, (argb!! ushr 24) and 0xFF)
        assertEquals(0xFF, (argb shr 16) and 0xFF)
        assertEquals(0x88, (argb shr 8) and 0xFF)
        assertEquals(0x00, argb and 0xFF)
    }

    @Test fun `calendar theme prefs round-trip seed`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("cal_theme_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        val themePrefs = CalendarThemePrefs.openForTest(prefs)
        themePrefs.setSeed("repo1/cal-a", 0xFF4DD0E1.toInt())
        assertEquals(0xFF4DD0E1.toInt(), themePrefs.entry("repo1/cal-a")?.colorSeedArgb)
        themePrefs.setIcon("repo1/cal-a", "🦊")
        assertEquals("🦊", themePrefs.entry("repo1/cal-a")?.iconEmoji)
    }
}

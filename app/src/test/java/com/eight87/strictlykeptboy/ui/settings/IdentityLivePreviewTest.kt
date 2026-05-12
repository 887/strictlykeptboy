package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.settings.categories.IdentityCategory
import com.eight87.strictlykeptboy.ui.settings.categories.TestTagCatIdentity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityLivePreviewTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun changing_praise_updates_preview_state() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("id_preview_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        val ident = IdentityPrefs.openForTest(prefs)

        composeRule.setContent {
            StrictlyKeptBoyTheme {
                Box(Modifier.size(width = 600.dp, height = 1200.dp)) {
                    IdentityCategory(prefs = ident)
                }
            }
        }
        composeRule.onNodeWithTag("$TestTagCatIdentity-Praise").performScrollTo()
        composeRule.onNodeWithTag("$TestTagCatIdentity-Praise").performTextClearance()
        composeRule.onNodeWithTag("$TestTagCatIdentity-Praise").performTextInput("darling")
        // State-level assertion: prefs reflect the typed praise term — the
        // preview Composable consumes that state via collectAsState.
        assertEquals("darling", ident.state.value.praise)
    }
}

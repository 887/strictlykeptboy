package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.19 — every inline switch on RepoCard fires its respective
 * callback with the flipped value when tapped. Each switch starts
 * unchecked here (RepoFixtures.localOnly defaults), so a tap delivers
 * `true` upstream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoCardSwitchWiringTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun each_switch_invokes_its_callback() {
        val repo = RepoFixtures.localOnly("gamma")
        // Defaults: showOnSchedule=true, drawTasksFrom=false,
        // autoSyncEnabled=true(?), wifiOnly=false, importStickers=false.
        // We just confirm the callback receives the *flipped* value
        // regardless of the start state.
        var lastShow: Boolean? = null
        var lastTasks: Boolean? = null
        var lastAuto: Boolean? = null
        var lastWifi: Boolean? = null
        var lastImport: Boolean? = null
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoCard(
                    repo = repo,
                    isWriteTarget = true, // expand by default
                    onSelectWriteTarget = {},
                    onToggleShowOnSchedule = { lastShow = it },
                    onToggleDrawTasksFrom = { lastTasks = it },
                    onToggleAutoSync = { lastAuto = it },
                    onToggleWifiOnly = { lastWifi = it },
                    onToggleImportStickers = { lastImport = it },
                    onOpenMoreSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("$TestTagRepoCardSwitchShowOnSchedule-gamma").performClick()
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchDrawTasksFrom-gamma").performClick()
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchAutoSync-gamma").performClick()
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchWifiOnly-gamma").performClick()
        composeRule.onNodeWithTag("$TestTagRepoCardSwitchImportStickers-gamma").performClick()

        // Each callback received the flipped value (start = field's default,
        // tap = !default). We just assert *something* came through to each.
        assertEquals(!repo.showOnSchedule, lastShow)
        assertEquals(!repo.drawTasksFrom, lastTasks)
        assertEquals(!repo.autoSyncEnabled, lastAuto)
        assertEquals(!repo.wifiOnly, lastWifi)
        assertEquals(!repo.importStickersToRepo, lastImport)
    }

    @Test fun more_settings_button_opens_detail_screen_via_callback() {
        val repo = RepoFixtures.localOnly("delta")
        var opened = false
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RepoCard(
                    repo = repo,
                    isWriteTarget = true,
                    onSelectWriteTarget = {},
                    onToggleShowOnSchedule = {},
                    onToggleDrawTasksFrom = {},
                    onToggleAutoSync = {},
                    onToggleWifiOnly = {},
                    onToggleImportStickers = {},
                    onOpenMoreSettings = { opened = true },
                )
            }
        }
        composeRule.onNodeWithTag("$TestTagRepoCardMoreSettings-delta").performClick()
        assertEquals(true, opened)
    }
}

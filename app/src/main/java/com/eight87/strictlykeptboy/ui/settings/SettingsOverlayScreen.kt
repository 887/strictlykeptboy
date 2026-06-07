package com.eight87.strictlykeptboy.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import com.eight87.strictlykeptboy.ui.import_export.ImportExportViewState

const val TestTagSettingsOverlay = "SettingsOverlay"
const val TestTagSettingsOverlayBack = "SettingsOverlayBack"

/**
 * Full-shell-cover Settings overlay — tonearmboy / whisperboy parity.
 *
 * Settings is no longer a swappable destination pane; it opens as an
 * overlay above the active destination, with its own back arrow that
 * dismisses the overlay (returning the user to whatever pane they
 * were on when they tapped the cog).
 *
 * Header chrome rule (post-`ui(settings)` cleanup): the TopAppBar here
 * is the ONLY header in the Settings stack. When the user has drilled
 * into a subpage on a compact-width screen, the bar swaps to show the
 * subpage label and the back arrow pops back to the category list;
 * one more tap (now on the root list) dismisses the overlay entirely.
 * No subpage renders its own header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsOverlayScreen(
    importExportState: ImportExportViewState?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPickImportFile: (RepoConfig) -> Unit = {},
    onPickExportFile: (RepoConfig) -> Unit = {},
    access: SettingsAccess = SettingsAccess(),
) {
    val state = rememberSettingsPaneState()
    val widthClass = LocalWindowWidthSizeClass.current
    val twoPane = widthClass.isTwoPane()
    val inSubpage = state.compactPushed && !twoPane

    // System / gesture back must mirror the toolbar back-arrow's two-step
    // logic: from a drilled-in subpage it pops to the category list (one
    // step), NOT straight out to the host pane. Without this, gesture-back
    // over-popped the whole overlay and skipped the category list (QA B.6).
    // On the root list (inSubpage == false) we leave back unhandled so it
    // falls through to dismiss the overlay as before.
    BackHandler(enabled = inSubpage) { state.popToCategoryList() }

    val title: String = if (inSubpage) {
        stringResource(state.activeCategory.labelRes)
    } else {
        stringResource(R.string.dest_settings)
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag(TestTagSettingsOverlay),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (inSubpage) {
                                state.popToCategoryList()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier
                            .testTag(if (inSubpage) TestTagSettingsBack else TestTagSettingsOverlayBack),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(
                                if (inSubpage) R.string.settings_back_to_categories else android.R.string.cancel,
                            ),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SettingsPane(
                importExportState = importExportState,
                onPickImportFile = onPickImportFile,
                onPickExportFile = onPickExportFile,
                access = access,
                state = state,
            )
        }
    }
}

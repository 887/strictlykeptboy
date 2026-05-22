package com.eight87.strictlykeptboy.ui.settings

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
 * The body is the existing [SettingsPane] verbatim — only the outer
 * chrome (TopAppBar + back arrow + dismiss wiring) is new.
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
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(TestTagSettingsOverlay),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dest_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TestTagSettingsOverlayBack),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
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
            )
        }
    }
}

package com.eight87.strictlykeptboy.ui.repos

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.ui.settings.SettingsAccess

const val TestTagReposOverlay = "ReposOverlay"
const val TestTagReposOverlayBack = "ReposOverlayBack"

/**
 * Full-shell-cover Repos overlay — Settings parity.
 *
 * Repos is no longer reachable via the top-bar destination tabs; it
 * opens as a full-shell overlay above the active destination (driven
 * by the bat-avatar tap + the top-bar repo-switcher chip in
 * `SkbAppShell.kt`). Back arrow dismisses, returning the user to the
 * pane they were on.
 *
 * The body is the existing [ReposPane] verbatim — only the outer
 * chrome (TopAppBar + back arrow + dismiss wiring) is new.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReposOverlayScreen(
    state: ReposViewState,
    secretsStore: SecretsStore?,
    onBack: () -> Unit,
    onOpenTogether: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPickInternalStorage: () -> Unit,
    settingsAccess: SettingsAccess,
    modifier: Modifier = Modifier,
) {
    val safRevoked = settingsAccess.safPermissionRevokedFlow
        ?.collectAsState()?.value == true
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(TestTagReposOverlay),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dest_repos)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TestTagReposOverlayBack),
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
            ReposPane(
                state = state,
                secretsStore = secretsStore,
                onOpenTogether = onOpenTogether,
                onOpenWizard = onOpenWizard,
                onOpenAppSettings = onOpenAppSettings,
                repoStoragePrefs = settingsAccess.repoStoragePrefs,
                notificationPrefs = settingsAccess.notificationPrefs,
                onPickBackupFolder = settingsAccess.onPickBackupFolder,
                demoModePrefs = settingsAccess.demoModePrefs,
                onPickInternalStorage = onPickInternalStorage,
                safPermissionRevoked = safRevoked,
            )
        }
    }
}

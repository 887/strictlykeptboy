package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.caldav.CalDavMode
import com.eight87.strictlykeptboy.caldav.auth.CalDavSecretsStore
import com.eight87.strictlykeptboy.caldav.store.CalDavMirrorStore
import com.eight87.strictlykeptboy.caldav.ui.AddMirrorScreen
import com.eight87.strictlykeptboy.caldav.ui.AddMirrorViewModel
import com.eight87.strictlykeptboy.git.RepoConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val TestTagCatCalDav = "Cat-CalDav"

/**
 * Settings → CalDAV (Behaviour). QA iter-4 C — wired to the real
 * `caldav/` mirror flow.
 *
 * The CalDAV mirror engine (PROPFIND discovery, ETag-journaled pull,
 * If-Match push, conflict surfacing — `caldav/sync/`) was implemented +
 * unit-tested under Phase Y but its setup UI was never reached from the
 * shell ("coming soon" stub). This category now hosts the real
 * [AddMirrorScreen] wizard and lists the configured mirrors.
 *
 * Scope of this wiring: mirror configuration is reachable + persisted
 * for password / app-password servers (Nextcloud, Apple iCloud, custom).
 * Background scheduling (`CalDavScheduler` boot-wiring) and OAuth device
 * flow for Google / Microsoft 365 are tracked in
 * `docs/plans/caldav-wiring.md`.
 *
 * SOLID-S: one responsibility — present + create CalDAV mirrors. Stores
 * are opened from [LocalContext] (the encrypted-prefs trust boundary the
 * mirror engine already keys against); the screen forwards user actions
 * to the unit-tested [AddMirrorViewModel].
 */
@Composable
fun CalDavCategory(
    reposFlow: StateFlow<List<RepoConfig>>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Encrypted-prefs stores need the platform keystore — absent under
    // Robolectric and on the rare device without a secure keystore. Open
    // defensively so the category renders (and the rest of Settings stays
    // navigable) instead of crashing; the add-flow is disabled when null.
    val mirrorStore = remember { runCatching { CalDavMirrorStore.open(context) }.getOrNull() }
    val secrets = remember { runCatching { CalDavSecretsStore.open(context) }.getOrNull() }
    val mirrors by (mirrorStore?.state ?: remember { MutableStateFlow(emptyList()) })
        .collectAsState()
    val repos by (reposFlow ?: remember { MutableStateFlow(emptyList<RepoConfig>()) })
        .collectAsState()
    var addingForRepo by remember { mutableStateOf<String?>(null) }

    CategorySurface(
        testTag = TestTagCatCalDav,
        title = stringResource(R.string.settings_category_caldav),
        modifier = modifier,
    ) {
        if (mirrorStore == null || secrets == null) {
            Text(
                stringResource(R.string.settings_caldav_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_caldav_unavailable),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@CategorySurface
        }
        val activeAdd = addingForRepo
        if (activeAdd != null) {
            val vm = remember(activeAdd) { AddMirrorViewModel(activeAdd, mirrorStore, secrets) }
            AddMirrorScreen(vm = vm, onDone = { addingForRepo = null })
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { addingForRepo = null },
                modifier = Modifier.testTag("$TestTagCatCalDav-Cancel"),
            ) {
                Text(stringResource(R.string.settings_caldav_cancel))
            }
            return@CategorySurface
        }

        Text(
            stringResource(R.string.settings_caldav_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        if (mirrors.isEmpty()) {
            Text(
                stringResource(R.string.settings_caldav_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            mirrors.forEach { m ->
                Text(
                    "${m.displayName} · ${m.provider.displayName} · ${modeLabel(m.mode)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("$TestTagCatCalDav-Mirror-${m.mirrorId}"),
                )
                m.lastError?.let { err ->
                    Text(
                        "⚠ $err",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        val target = repos.firstOrNull()
        Button(
            onClick = { target?.let { addingForRepo = it.repoId } },
            enabled = target != null,
            modifier = Modifier.testTag("$TestTagCatCalDav-Add"),
        ) {
            Text(
                target?.let { stringResource(R.string.settings_caldav_add_to, it.displayName) }
                    ?: stringResource(R.string.settings_caldav_need_repo),
            )
        }
    }
}

@Composable
private fun modeLabel(mode: CalDavMode): String = stringResource(
    when (mode) {
        CalDavMode.PULL_ONLY -> R.string.settings_caldav_mode_pull
        CalDavMode.PUSH_ONLY -> R.string.settings_caldav_mode_push
        CalDavMode.BIDI -> R.string.settings_caldav_mode_bidi
    },
)

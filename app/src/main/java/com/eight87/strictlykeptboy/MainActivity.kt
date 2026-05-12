package com.eight87.strictlykeptboy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.theme.AppearancePrefs
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.repos.ReposViewState
import com.eight87.strictlykeptboy.ui.scaffold.AppScaffold
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewModePrefs
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appearancePrefs = AppearancePrefs.open(this)
        val viewModePrefs = ScheduleViewModePrefs.open(this)

        // Phase I — RepoStore + SecretsStore live in EncryptedSharedPreferences;
        // open here so the Repos rail destination can write through them.
        val repoStore = RepoStore.open(this)
        val secretsStore = SecretsStore.open(this)
        val reposState = ReposViewState.open(this, repoStore)

        // Phase F stub: RepoStore + DAO wiring lands in Phase F→G integration.
        // For now we feed an empty snapshot + empty sources so SchedulePane
        // renders the EmptyScheduleState (F.5).
        val activeRepoName = MutableStateFlow("demo-repo")
        val snapshot = MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
        val sources = MutableStateFlow(
            Renderer.Sources(
                events = emptyList(),
                rules = emptyList(),
                exceptionsByRule = emptyMap(),
                deviations = emptyList(),
                overrides = emptyList(),
            ),
        )

        setContent {
            val appearance by appearancePrefs.state.collectAsState()
            StrictlyKeptBoyTheme(
                themeMode = appearance.themeMode,
                densityScale = appearance.densityScale,
                dynamicColor = appearance.dynamicColor,
            ) {
                val scope = rememberCoroutineScope()
                val scheduleState = remember {
                    ScheduleViewState(
                        scope = scope,
                        snapshotFlow = snapshot,
                        sourcesFlow = sources,
                        initialTab = viewModePrefs.selected.value,
                    )
                }
                AppScaffold(
                    activeRepoNameFlow = activeRepoName,
                    scheduleState = scheduleState,
                    onPersistTab = viewModePrefs::set,
                    reposState = reposState,
                    secretsStore = secretsStore,
                )
            }
        }
    }
}

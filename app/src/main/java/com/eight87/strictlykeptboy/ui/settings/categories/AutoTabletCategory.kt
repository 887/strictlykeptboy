package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.ui.settings.AutoTabletPrefs
import com.eight87.strictlykeptboy.ui.settings.TabletMasterDetailMode
import kotlinx.coroutines.flow.StateFlow

const val TestTagCatAutoTablet = "Cat-AutoTablet"
const val TestTagAutoTabletMaxSlider = "Cat-AutoTablet-MaxSlider"
const val TestTagAutoTabletModeAuto = "Cat-AutoTablet-Mode-Auto"
const val TestTagAutoTabletModeOn = "Cat-AutoTablet-Mode-On"
const val TestTagAutoTabletModeOff = "Cat-AutoTablet-Mode-Off"
const val TestTagAutoTabletOpenDetail = "Cat-AutoTablet-OpenDetail"
const val TestTagAutoTabletRepoShowPrefix = "Cat-AutoTablet-RepoShow-"

/**
 * Round 2.2.D.7 — Android Auto + tablet master-detail settings surface.
 */
@Composable
fun AutoTabletCategory(
    prefs: AutoTabletPrefs,
    reposFlow: StateFlow<List<RepoConfig>>?,
    modifier: Modifier = Modifier,
) {
    val state by prefs.state.collectAsState()
    CategorySurface(
        testTag = TestTagCatAutoTablet,
        title = stringResource(R.string.settings_category_autotablet),
        modifier = modifier,
    ) {
        // Per-repo "show on Auto" toggles
        SectionLabel(stringResource(R.string.settings_autotablet_show_on_auto))
        val repos = reposFlow?.collectAsState()?.value ?: emptyList()
        if (repos.isEmpty()) {
            Text(
                stringResource(R.string.settings_autotablet_no_repos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            repos.forEach { cfg ->
                var on by remember(cfg.repoId, state) {
                    mutableStateOf(prefs.isShowOnAutoForRepo(cfg.repoId))
                }
                ToggleRow(
                    label = cfg.displayName.ifBlank { cfg.repoId },
                    checked = on,
                    onCheckedChange = {
                        on = it
                        prefs.setShowOnAutoForRepo(cfg.repoId, it)
                    },
                    testTag = "$TestTagAutoTabletRepoShowPrefix${cfg.repoId}",
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        SectionLabel(
            stringResource(R.string.settings_autotablet_max_events, state.maxAutoEventsToday),
        )
        var maxFloat by remember(state.maxAutoEventsToday) {
            mutableFloatStateOf(state.maxAutoEventsToday.toFloat())
        }
        Slider(
            value = maxFloat,
            onValueChange = { maxFloat = it },
            onValueChangeFinished = { prefs.setMaxAutoEventsToday(maxFloat.toInt()) },
            valueRange = AutoTabletPrefs.MIN_EVENTS.toFloat()..AutoTabletPrefs.MAX_EVENTS.toFloat(),
            steps = AutoTabletPrefs.MAX_EVENTS - AutoTabletPrefs.MIN_EVENTS - 1,
            modifier = Modifier.testTag(TestTagAutoTabletMaxSlider),
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        SectionLabel(stringResource(R.string.settings_autotablet_master_detail))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeChip(
                label = stringResource(R.string.settings_autotablet_master_detail_auto),
                selected = state.tabletMasterDetailMode == TabletMasterDetailMode.Auto,
                onClick = { prefs.setTabletMasterDetailMode(TabletMasterDetailMode.Auto) },
                testTag = TestTagAutoTabletModeAuto,
            )
            ModeChip(
                label = stringResource(R.string.settings_autotablet_master_detail_on),
                selected = state.tabletMasterDetailMode == TabletMasterDetailMode.On,
                onClick = { prefs.setTabletMasterDetailMode(TabletMasterDetailMode.On) },
                testTag = TestTagAutoTabletModeOn,
            )
            ModeChip(
                label = stringResource(R.string.settings_autotablet_master_detail_off),
                selected = state.tabletMasterDetailMode == TabletMasterDetailMode.Off,
                onClick = { prefs.setTabletMasterDetailMode(TabletMasterDetailMode.Off) },
                testTag = TestTagAutoTabletModeOff,
            )
        }

        Spacer(Modifier.height(12.dp))
        ToggleRow(
            label = stringResource(R.string.settings_autotablet_open_detail_default),
            checked = state.tabletOpenDetailByDefault,
            onCheckedChange = { prefs.setTabletOpenDetailByDefault(it) },
            testTag = TestTagAutoTabletOpenDetail,
        )
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag(testTag),
    )
}

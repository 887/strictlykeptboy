package com.eight87.strictlykeptboy.ui.scaffold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.components.IdentityAvatar
import com.eight87.strictlykeptboy.ui.components.RepoSwitcherChip
import com.eight87.strictlykeptboy.ui.components.SyncButton

const val TestTagTopBar = "SkbTopBar"
const val TestTagViewTab = "ViewTab"

/**
 * UI-B — Day / Week / Month / Agenda / Year. Only Day is functional in Phase F.
 *
 * Phase U.4 / F11 note: [label] is **wire-format** — used by
 * [com.eight87.strictlykeptboy.ui.schedule.ScheduleViewModePrefs] as the
 * persisted SharedPreferences key form (alongside `name`) and as a stable
 * toString fallback. Translatable UI display routes through
 * `ScheduleViewTab.labelString()` in `ui/a11y/EnumLabels.kt`.
 */
enum class ScheduleViewTab(val label: String) {
    Day("Day"), Week("Week"), Month("Month"), Agenda("Agenda"), Year("Year");
}

@Composable
fun SkbTopBar(
    activeRepoName: String,
    selectedViewTab: ScheduleViewTab,
    onSelectViewTab: (ScheduleViewTab) -> Unit,
    onRepoSwitcherClick: () -> Unit,
    onSyncClick: () -> Unit,
    onIdentityClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().testTag(TestTagTopBar),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RepoSwitcherChip(activeRepoName = activeRepoName, onClick = onRepoSwitcherClick)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SyncButton(onClick = onSyncClick)
                    IdentityAvatar(onClick = onIdentityClick)
                }
            }
            ViewTabStrip(selected = selectedViewTab, onSelect = onSelectViewTab)
        }
    }
}

@Composable
private fun ViewTabStrip(
    selected: ScheduleViewTab,
    onSelect: (ScheduleViewTab) -> Unit,
) {
    SecondaryTabRow(selectedTabIndex = selected.ordinal) {
        ScheduleViewTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.testTag("$TestTagViewTab-${tab.name}"),
                text = { Text(tab.labelString()) },
            )
        }
    }
}

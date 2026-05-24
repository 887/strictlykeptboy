package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import com.eight87.strictlykeptboy.ui.settings.ListKind
import kotlinx.coroutines.flow.StateFlow

const val TestTagCatCalendars = "Cat-Calendars"
const val TestTagCatTodolists = "Cat-Todolists"

/** Phase S.5 — Calendars category. */
@Composable
fun CalendarsCategory(prefs: CalendarVisibilityPrefs, modifier: Modifier = Modifier) {
    ListCategoryBody(
        testTag = TestTagCatCalendars,
        title = stringResource(R.string.settings_category_calendars),
        emptyWord = stringResource(R.string.settings_lists_calendars_word),
        prefs = prefs,
        modifier = modifier,
    )
}

/**
 * Round 2.1.B.11 / 2.1.E.1 — calendars master list across all repos.
 *
 * Cross-repo `CalendarMeta` list with: (a) show-on-schedule visibility
 * switch, (b) reorder priority buttons, (c) row-tap to open
 * [com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsSheet] via
 * [onEditCalendar]. Per-repo `RepoSettingsScreen` keeps repo-level
 * concerns only; calendar selection lives here.
 */
@Composable
fun CalendarsCategoryMaster(
    prefs: CalendarVisibilityPrefs,
    calendarsFlow: StateFlow<List<CalendarMeta>>,
    onEditCalendar: (CalendarMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendars by calendarsFlow.collectAsState()
    val visibility by prefs.state.collectAsState()
    val visMap = visibility.ordered.associateBy { it.repoId to it.id }
    CategorySurface(
        testTag = TestTagCatCalendars,
        title = stringResource(R.string.settings_category_calendars),
        modifier = modifier,
    ) {
        if (calendars.isEmpty()) {
            Text(
                stringResource(R.string.settings_lists_empty, stringResource(R.string.settings_lists_calendars_word)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CategorySurface
        }
        SectionLabel(stringResource(R.string.settings_lists_visibility_title))
        calendars.forEach { cal ->
            val key = cal.repo.id to cal.ref.id
            val visible = visMap[key]?.visible ?: true
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEditCalendar(cal) }
                    .padding(vertical = 6.dp)
                    .testTag("$TestTagCatCalendars-Row-${cal.repo.id}-${cal.ref.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(cal.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "priority ${cal.priority}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = visible,
                    onCheckedChange = { v ->
                        prefs.setVisible(id = cal.ref.id, visible = v, repoId = cal.repo.id)
                    },
                    modifier = Modifier.testTag("$TestTagCatCalendars-Vis-${cal.repo.id}-${cal.ref.id}"),
                )
                IconButton(
                    onClick = { prefs.moveUp(cal.ref.id, cal.repo.id) },
                    modifier = Modifier.testTag("$TestTagCatCalendars-Up-${cal.repo.id}-${cal.ref.id}"),
                ) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.settings_lists_move_up),
                    )
                }
                IconButton(
                    onClick = { prefs.moveDown(cal.ref.id, cal.repo.id) },
                    modifier = Modifier.testTag("$TestTagCatCalendars-Down-${cal.repo.id}-${cal.ref.id}"),
                ) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(R.string.settings_lists_move_down),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Phase S.6 — Todolists category. Same shape as Calendars. */
@Composable
fun TodolistsCategory(prefs: CalendarVisibilityPrefs, modifier: Modifier = Modifier) {
    ListCategoryBody(
        testTag = TestTagCatTodolists,
        title = stringResource(R.string.settings_category_todolists),
        emptyWord = stringResource(R.string.settings_lists_todolists_word),
        prefs = prefs,
        modifier = modifier,
    )
}

@Composable
private fun ListCategoryBody(
    testTag: String,
    title: String,
    emptyWord: String,
    prefs: CalendarVisibilityPrefs,
    modifier: Modifier = Modifier,
) {
    val state by prefs.state.collectAsState()
    CategorySurface(testTag = testTag, title = title, modifier = modifier) {
        if (state.ordered.isEmpty()) {
            Text(
                stringResource(R.string.settings_lists_empty, emptyWord),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SectionLabel(stringResource(R.string.settings_lists_visibility_title))
            state.ordered.forEach { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.label, modifier = Modifier.weight(1f))
                    Switch(
                        checked = entry.visible,
                        onCheckedChange = { prefs.setVisible(entry.id, it) },
                        modifier = Modifier.testTag("$testTag-Vis-${entry.id}"),
                    )
                }
            }
            HorizontalDivider()
            SectionLabel(stringResource(R.string.settings_lists_priority_title))
            state.ordered.forEachIndexed { idx, entry ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${idx + 1}. ${entry.label}",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { prefs.moveUp(entry.id) },
                        modifier = Modifier.testTag("$testTag-Up-${entry.id}"),
                    ) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(R.string.settings_lists_move_up),
                        )
                    }
                    IconButton(
                        onClick = { prefs.moveDown(entry.id) },
                        modifier = Modifier.testTag("$testTag-Down-${entry.id}"),
                    ) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = stringResource(R.string.settings_lists_move_down),
                        )
                    }
                }
            }
            // Round 2.1.B.3 — active-windows display moved to
            // calendar.toml (see CalendarActivityConfig). The new
            // editor surface is CalendarSettingsSheet (2.1.B.4),
            // reached from the calendars master list (2.1.B.11).
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Suppress("unused") // kept for parity with the ListKind enum.
private val _ensureListKindLinked = ListKind.Calendars

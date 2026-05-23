package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.eight87.strictlykeptboy.notif.NotificationPrefs
import com.eight87.strictlykeptboy.resolver.DayBand

const val TestTagEventDetailScreen = "EventDetailScreen"
const val TestTagEventDetailBack = "EventDetailBack"

/**
 * Round 2.23 Phase D (D-2.23.d) — full-screen event detail destination.
 *
 * Replaces the legacy `EventDetailSheet` (ModalBottomSheet) for the
 * compact path. Mounted as a Surface overlay above the chrome Column
 * (same pattern Round 2.22 used for [com.eight87.strictlykeptboy.ui.calendars.OverlayPickerScreen])
 * so the TopAppBar covers the schedule tabs and the back arrow is
 * unambiguous.
 *
 * Reuses [EventDetailContent] verbatim — only the chrome differs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    band: DayBand,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    attachments: List<AttachmentRef> = emptyList(),
    calendarName: String? = null,
    repoName: String? = null,
    calendarPriority: Int? = null,
    linkedTaskTitles: List<String> = emptyList(),
    supersededByName: String? = null,
    notificationPrefs: NotificationPrefs? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize().testTag(TestTagEventDetailScreen),
        color = MaterialTheme.colorScheme.background,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = (band.instance.emoji?.let { "$it  " } ?: "") +
                                band.instance.title,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag(TestTagEventDetailBack),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                EventDetailContent(
                    band = band,
                    onEdit = onEdit,
                    attachments = attachments,
                    calendarName = calendarName,
                    repoName = repoName,
                    calendarPriority = calendarPriority,
                    linkedTaskTitles = linkedTaskTitles,
                    supersededByName = supersededByName,
                    notificationPrefs = notificationPrefs,
                )
            }
        }
    }
}

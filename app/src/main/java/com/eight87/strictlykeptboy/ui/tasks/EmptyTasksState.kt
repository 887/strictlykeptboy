package com.eight87.strictlykeptboy.ui.tasks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagEmptyTasksBat = "EmptyTasksBat"
const val TestTagEmptyTasksMessage = "EmptyTasksMessage"

/**
 * Phase H — empty state shared by all task views. Mirrors
 * [com.eight87.strictlykeptboy.ui.schedule.EmptyScheduleState]: bat
 * mascot, kink-positive copy, and a neutral fallback. Phase K wires the
 * real `identity.toml` read; for now the praise term + tone register are
 * hardcoded.
 */
@Composable
fun EmptyTasksState(
    primaryMessage: String,
    neutralMessage: String,
    modifier: Modifier = Modifier,
    neutralOnly: Boolean = false,
) {
    val message = if (neutralOnly) neutralMessage else primaryMessage

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.about_bat),
            contentDescription = stringResource(R.string.cd_bat_mascot),
            modifier = Modifier.size(160.dp).testTag(TestTagEmptyTasksBat),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp).testTag(TestTagEmptyTasksMessage),
        )
    }
}

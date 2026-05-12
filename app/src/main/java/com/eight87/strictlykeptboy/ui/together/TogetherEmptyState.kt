package com.eight87.strictlykeptboy.ui.together

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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

const val TestTagTogetherEmptyBat = "TogetherEmptyBat"
const val TestTagTogetherEmptyMessage = "TogetherEmptyMessage"

/**
 * Phase N.2 — empty state when the finder returns no slots.
 * Bat-mascot + cute "try widening the window" message.
 */
@Composable
fun TogetherEmptyState(
    modifier: Modifier = Modifier,
    neutralOnly: Boolean = false,
) {
    val msg = if (neutralOnly) {
        stringResource(R.string.together_empty_neutral)
    } else {
        stringResource(R.string.together_empty_no_overlap)
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.about_bat),
            contentDescription = stringResource(R.string.cd_bat_mascot),
            modifier = Modifier.size(140.dp).testTag(TestTagTogetherEmptyBat),
        )
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp).testTag(TestTagTogetherEmptyMessage),
        )
    }
}

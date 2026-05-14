package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

const val TestTagCatCalDav = "Cat-CalDav"

/**
 * 2.1.E.12 — CalDAV stub category.
 *
 * Promised in the genesis prompt; implementation closes out with
 * Phase Y. Intro text + disabled "coming with Phase Y" button so the
 * surface exists today even though wiring lands later. Mirrors how
 * Identities was kept visible as a stub before Phase GG.
 */
@Composable
fun CalDavCategory(modifier: Modifier = Modifier) {
    CategorySurface(
        testTag = TestTagCatCalDav,
        title = stringResource(R.string.settings_category_caldav),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.settings_caldav_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.testTag("$TestTagCatCalDav-Coming"),
        ) {
            Text(stringResource(R.string.settings_caldav_coming))
        }
    }
}

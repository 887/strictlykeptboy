package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.BuildConfig
import com.eight87.strictlykeptboy.R

const val TestTagCatAbout = "Cat-About"

/**
 * Phase S.10 — About category.
 *
 * Build info (GIT_SHA + BUILD_DATE via BuildConfig), licenses placeholder
 * until Phase W Licensee, mascot easter egg (tap version 5x), repo link.
 */
@Composable
fun AboutCategory(
    onOpenLicenses: () -> Unit = {},
    onOpenRepo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CategorySurface(
        testTag = TestTagCatAbout,
        title = stringResource(R.string.settings_category_about),
        modifier = modifier,
    ) {
        Text(stringResource(R.string.settings_about_app_name), style = MaterialTheme.typography.titleLarge)
        var taps by remember { mutableIntStateOf(0) }
        var showEgg by remember { mutableStateOf(false) }
        Text(
            stringResource(R.string.settings_about_version_tap, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .testTag("$TestTagCatAbout-Version")
                .clickable {
                    taps += 1
                    if (taps >= 5) showEgg = true
                },
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_about_build_sha, BuildConfig.GIT_SHA))
        Text(stringResource(R.string.settings_about_build_date, BuildConfig.BUILD_DATE))
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = onOpenLicenses,
            modifier = Modifier.testTag("$TestTagCatAbout-Licenses"),
        ) {
            Text(stringResource(R.string.settings_about_licenses))
        }
        TextButton(
            onClick = onOpenRepo,
            modifier = Modifier.testTag("$TestTagCatAbout-Repo"),
        ) {
            Text(stringResource(R.string.settings_about_repo_link))
        }
        if (showEgg) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showEgg = false; taps = 0 }
                    .testTag("$TestTagCatAbout-EasterEgg"),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.about_bat),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

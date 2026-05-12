package com.eight87.strictlykeptboy.ui.settings.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay

const val TestTagCatAbout = "Cat-About"
const val TestTagCatAboutEasterEgg = "Cat-About-EasterEgg"

/** Number of taps in rapid succession that unlocks the easter egg. */
const val ABOUT_EASTER_EGG_TAP_COUNT = 7
/** Reset window for the rapid-tap counter, in milliseconds. */
const val ABOUT_EASTER_EGG_RESET_MS = 2_000L

/**
 * Phase S.10 + T.7 — About category with rapid-tap easter egg.
 *
 * Build info (GIT_SHA + BUILD_DATE via BuildConfig), licenses
 * placeholder until Phase W Licensee, repo link, and a hidden mascot
 * reveal: tap the version row [ABOUT_EASTER_EGG_TAP_COUNT] times within
 * a [ABOUT_EASTER_EGG_RESET_MS]-millisecond window to surface a
 * full-screen `about_bat.webp` plus the project tagline.
 *
 * Counter is per-Compose-scope state (not persisted) and resets after
 * the gap window; pressing back or tapping the overlay dismisses.
 */
@Composable
fun AboutCategory(
    onOpenLicenses: () -> Unit = {},
    onOpenRepo: () -> Unit = {},
    nowMs: () -> Long = { System.currentTimeMillis() },
    modifier: Modifier = Modifier,
) {
    CategorySurface(
        testTag = TestTagCatAbout,
        title = stringResource(R.string.settings_category_about),
        modifier = modifier,
    ) {
        Text(stringResource(R.string.settings_about_app_name), style = MaterialTheme.typography.titleLarge)
        var taps by remember { mutableIntStateOf(0) }
        var lastTapMs by remember { mutableLongStateOf(0L) }
        var showEgg by remember { mutableStateOf(false) }
        // Schedule a coroutine to clear the counter 2s after the last tap.
        LaunchedEffect(lastTapMs) {
            if (lastTapMs != 0L && !showEgg) {
                delay(ABOUT_EASTER_EGG_RESET_MS)
                if (nowMs() - lastTapMs >= ABOUT_EASTER_EGG_RESET_MS && !showEgg) {
                    taps = 0
                }
            }
        }
        Text(
            stringResource(R.string.settings_about_version_tap, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .testTag("$TestTagCatAbout-Version")
                .clickable {
                    val now = nowMs()
                    taps = if (now - lastTapMs < ABOUT_EASTER_EGG_RESET_MS) taps + 1 else 1
                    lastTapMs = now
                    if (taps >= ABOUT_EASTER_EGG_TAP_COUNT) showEgg = true
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
            BackHandler { showEgg = false; taps = 0 }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim)
                    .clickable { showEgg = false; taps = 0 }
                    .testTag(TestTagCatAboutEasterEgg),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.about_bat),
                        contentDescription = stringResource(R.string.about_easter_egg_cd),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.about_easter_egg_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

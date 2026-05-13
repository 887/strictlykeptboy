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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Launch
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.BuildConfig
import com.eight87.strictlykeptboy.R
import kotlinx.coroutines.launch

const val TestTagCatAbout = "Cat-About"
const val TestTagCatAboutEasterEgg = "Cat-About-EasterEgg"

/**
 * About category — tonearmboy-parity grouped-card layout.
 *
 * Two cards: **Build** (app, version with easter-egg tap target, build
 * date) and **Source** (GitHub, licenses, privacy). The Version row is
 * wired to [EasterEggController]: first two taps surface snackbar
 * prompts, the third reveals a fullscreen bat scene. Tapping the scene
 * or pressing back dismisses; the counter resets so the reveal is
 * repeatable.
 */
@Composable
fun AboutCategory(
    onOpenLicenses: () -> Unit = {},
    onOpenRepo: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    nowMs: () -> Long = { System.currentTimeMillis() },
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val easterEgg = remember { EasterEggController() }
    var showEgg by remember { mutableStateOf(false) }

    val firstPrompt = stringResource(R.string.settings_about_easter_egg_first)
    val secondPrompt = stringResource(R.string.settings_about_easter_egg_second)

    Box(modifier = modifier.fillMaxSize().testTag(TestTagCatAbout)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AboutSectionHeader(stringResource(R.string.settings_about_card_build))
            AboutCard {
                AboutRow(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.settings_about_app_label),
                    subtitle = stringResource(R.string.settings_about_app_subtitle),
                    tint = MaterialTheme.colorScheme.primary,
                )
                AboutDivider()
                AboutRow(
                    icon = Icons.Outlined.Numbers,
                    label = stringResource(R.string.settings_about_version_label),
                    subtitle = stringResource(
                        R.string.settings_about_version_subtitle,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.GIT_SHA,
                    ),
                    tint = MaterialTheme.colorScheme.secondary,
                    testTag = "$TestTagCatAbout-Version",
                    onClick = {
                        when (easterEgg.tap(nowMs())) {
                            EasterEggController.Outcome.FirstPromptSnackbar -> scope.launch {
                                snackbarHostState.showSnackbar(firstPrompt)
                            }
                            EasterEggController.Outcome.SecondPromptSnackbar -> scope.launch {
                                snackbarHostState.showSnackbar(secondPrompt)
                            }
                            EasterEggController.Outcome.Reveal -> showEgg = true
                        }
                    },
                )
                AboutDivider()
                AboutRow(
                    icon = Icons.Outlined.Schedule,
                    label = stringResource(R.string.settings_about_build_date_label),
                    subtitle = BuildConfig.BUILD_DATE,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }

            AboutSectionHeader(stringResource(R.string.settings_about_card_source))
            AboutCard {
                AboutRow(
                    icon = Icons.Outlined.Launch,
                    label = stringResource(R.string.settings_about_github_label),
                    subtitle = stringResource(R.string.settings_about_github_subtitle),
                    tint = MaterialTheme.colorScheme.primary,
                    testTag = "$TestTagCatAbout-Repo",
                    onClick = onOpenRepo,
                )
                AboutDivider()
                AboutRow(
                    icon = Icons.Outlined.Article,
                    label = stringResource(R.string.settings_about_licenses_label),
                    subtitle = stringResource(R.string.settings_about_licenses_subtitle),
                    tint = MaterialTheme.colorScheme.secondary,
                    testTag = "$TestTagCatAbout-Licenses",
                    onClick = onOpenLicenses,
                )
                AboutDivider()
                AboutRow(
                    icon = Icons.Outlined.Lock,
                    label = stringResource(R.string.settings_about_privacy_label),
                    subtitle = stringResource(R.string.settings_about_privacy_subtitle),
                    tint = MaterialTheme.colorScheme.tertiary,
                    testTag = "$TestTagCatAbout-Privacy",
                    onClick = onOpenPrivacyPolicy,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
        if (showEgg) {
            BackHandler { showEgg = false }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { showEgg = false }
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
                        contentDescription = stringResource(R.string.settings_about_easter_egg_bat_cd),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.about_easter_egg_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) { content() }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    tint: Color,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowMod = (if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = tint.copy(alpha = 0.18f), shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = rowMod,
    )
}

@Composable
private fun AboutDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

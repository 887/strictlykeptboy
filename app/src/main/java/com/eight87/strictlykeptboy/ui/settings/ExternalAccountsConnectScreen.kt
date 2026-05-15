package com.eight87.strictlykeptboy.ui.settings

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R

/**
 * Round 2.18.H — onboarding screen that points users at DAVx5 (CalDAV)
 * and the system Exchange account adder. No bundled libraries, no
 * sync code — purely outbound intents.
 *
 * Three cards:
 *  - CalDAV (Google / iCloud / Nextcloud / Fastmail): install DAVx5 if
 *    absent (F-Droid preferred when an F-Droid-class client is present
 *    on the device), or open it if already installed.
 *  - Microsoft 365 / Exchange: launch [Settings.ACTION_ADD_ACCOUNT]
 *    pre-filtered to `com.android.exchange`. If at least one Exchange
 *    account already exists, the button swaps to "Manage Exchange
 *    accounts" + opens system sync settings.
 *  - Already configured: open [Settings.ACTION_SYNC_SETTINGS].
 */

const val TestTagConnectAccountsScreen = "ConnectAccountsScreen"
const val TestTagConnectAccountsCalDavCard = "ConnectAccounts-CalDavCard"
const val TestTagConnectAccountsCalDavButton = "ConnectAccounts-CalDavButton"
const val TestTagConnectAccountsExchangeCard = "ConnectAccounts-ExchangeCard"
const val TestTagConnectAccountsExchangeButton = "ConnectAccounts-ExchangeButton"
const val TestTagConnectAccountsConfiguredCard = "ConnectAccounts-ConfiguredCard"
const val TestTagConnectAccountsConfiguredButton = "ConnectAccounts-ConfiguredButton"
/** Round 2.18.H — entry row on ExternalCalendarsScreen. */
const val TestTagExternalCalendarsConnectAccountsRow = "ExternalCalendars-ConnectAccountsRow"

/**
 * Probes for testability — passed in so Robolectric tests can stub
 * PackageManager / AccountManager state without driving a real device.
 *
 * Defaults call the live [PackageManager] / [AccountManager] APIs.
 */
data class ExternalAccountsProbes(
    val isDavx5Installed: (Context) -> Boolean = { ctx ->
        ExternalAccountsIntentFactory.isPackageInstalled(
            ctx.packageManager, ExternalAccountsIntentFactory.PKG_DAVX5,
        )
    },
    val isFDroidClientInstalled: (Context) -> Boolean = { ctx ->
        ExternalAccountsIntentFactory.isPackageInstalled(
            ctx.packageManager, ExternalAccountsIntentFactory.PKG_FDROID,
        ) || ExternalAccountsIntentFactory.isPackageInstalled(
            ctx.packageManager, ExternalAccountsIntentFactory.PKG_AURORA_FDROID,
        )
    },
    val hasExchangeAccount: (Context) -> Boolean = { ctx ->
        runCatching {
            AccountManager.get(ctx).getAccountsByType(
                ExternalAccountsIntentFactory.ACCOUNT_TYPE_EXCHANGE,
            ).isNotEmpty()
        }.getOrDefault(false)
    },
)

/**
 * Pure intent constructors — no Compose, no Context dependence beyond
 * [PackageManager] for launch-intent retrieval. Kept top-level so unit
 * tests can call them directly without driving the UI.
 */
object ExternalAccountsIntentFactory {

    const val PKG_DAVX5 = "at.bitfire.davdroid"
    const val PKG_FDROID = "org.fdroid.fdroid"
    const val PKG_AURORA_FDROID = "com.aurora.adroid"
    const val ACCOUNT_TYPE_EXCHANGE = "com.android.exchange"

    const val URL_FDROID_DAVX5 = "https://f-droid.org/packages/at.bitfire.davdroid/"
    const val URL_PLAY_DAVX5 =
        "https://play.google.com/store/apps/details?id=at.bitfire.davdroid"
    const val URL_DAVX5_MANUAL = "https://manual.davx5.com/"

    /**
     * @return true when [pkg] resolves via [PackageManager.getApplicationInfo].
     */
    fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean = try {
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /**
     * Build the install-DAVx5 outbound intent.
     *
     * Preference order:
     *  1. F-Droid client installed → F-Droid web URL (the F-Droid app
     *     intercepts the URI via its own intent filter and opens the
     *     listing in-app).
     *  2. Otherwise → Play Store URL.
     *
     * Fallback is identical to (1) when neither client is detected —
     * the F-Droid web page works in any browser.
     */
    fun installDavx5Intent(fdroidClientPresent: Boolean): Intent {
        val url = if (fdroidClientPresent) URL_FDROID_DAVX5 else URL_PLAY_DAVX5
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Launch DAVx5's main activity. Returns null if package not installed. */
    fun openDavx5Intent(pm: PackageManager): Intent? =
        pm.getLaunchIntentForPackage(PKG_DAVX5)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * `Settings.ACTION_ADD_ACCOUNT` filtered to the Exchange account
     * type. Phones that don't ship Exchange (most non-Samsung devices)
     * will show the system "no matching apps" dialog; that's the
     * expected behaviour — there's no skb side to it.
     */
    fun addExchangeAccountIntent(): Intent =
        Intent(Settings.ACTION_ADD_ACCOUNT).apply {
            putExtra(
                Settings.EXTRA_ACCOUNT_TYPES,
                arrayOf(ACCOUNT_TYPE_EXCHANGE),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Generic per-account-type sync settings entrypoint. */
    fun openSyncSettingsIntent(): Intent =
        Intent(Settings.ACTION_SYNC_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
fun ExternalAccountsConnectScreen(
    modifier: Modifier = Modifier,
    probes: ExternalAccountsProbes = ExternalAccountsProbes(),
    onBack: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val davx5Installed = remember(ctx) { probes.isDavx5Installed(ctx) }
    val fdroidClientPresent = remember(ctx) { probes.isFDroidClientInstalled(ctx) }
    val exchangeAccountPresent = remember(ctx) { probes.hasExchangeAccount(ctx) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(TestTagConnectAccountsScreen),
    ) {
        Text(
            stringResource(R.string.settings_connect_accounts_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_connect_accounts_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onBack != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.settings_connect_accounts_back))
            }
        }
        Spacer(Modifier.height(16.dp))

        // CalDAV / DAVx5 card.
        ConnectCard(
            title = stringResource(R.string.settings_connect_accounts_caldav_title),
            body = stringResource(R.string.settings_connect_accounts_caldav_body),
            buttonLabel = stringResource(
                if (davx5Installed) R.string.settings_connect_accounts_caldav_open
                else R.string.settings_connect_accounts_caldav_install,
            ),
            cardTestTag = TestTagConnectAccountsCalDavCard,
            buttonTestTag = TestTagConnectAccountsCalDavButton,
            onClick = {
                val intent = if (davx5Installed) {
                    ExternalAccountsIntentFactory.openDavx5Intent(ctx.packageManager)
                        ?: ExternalAccountsIntentFactory.installDavx5Intent(
                            fdroidClientPresent,
                        )
                } else {
                    ExternalAccountsIntentFactory.installDavx5Intent(fdroidClientPresent)
                }
                runCatching { ctx.startActivity(intent) }
            },
        )
        Spacer(Modifier.height(12.dp))

        // Exchange / MS 365 card.
        ConnectCard(
            title = stringResource(R.string.settings_connect_accounts_exchange_title),
            body = stringResource(R.string.settings_connect_accounts_exchange_body),
            buttonLabel = stringResource(
                if (exchangeAccountPresent)
                    R.string.settings_connect_accounts_exchange_manage
                else R.string.settings_connect_accounts_exchange_add,
            ),
            cardTestTag = TestTagConnectAccountsExchangeCard,
            buttonTestTag = TestTagConnectAccountsExchangeButton,
            onClick = {
                val intent = if (exchangeAccountPresent) {
                    ExternalAccountsIntentFactory.openSyncSettingsIntent()
                } else {
                    ExternalAccountsIntentFactory.addExchangeAccountIntent()
                }
                runCatching { ctx.startActivity(intent) }
            },
        )
        Spacer(Modifier.height(12.dp))

        // Already-configured card.
        ConnectCard(
            title = stringResource(R.string.settings_connect_accounts_configured_title),
            body = stringResource(R.string.settings_connect_accounts_configured_body),
            buttonLabel = stringResource(R.string.settings_connect_accounts_configured_action),
            cardTestTag = TestTagConnectAccountsConfiguredCard,
            buttonTestTag = TestTagConnectAccountsConfiguredButton,
            onClick = {
                runCatching {
                    ctx.startActivity(
                        ExternalAccountsIntentFactory.openSyncSettingsIntent(),
                    )
                }
            },
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.settings_connect_accounts_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(64.dp))
    }
}

@Composable
private fun ConnectCard(
    title: String,
    body: String,
    buttonLabel: String,
    cardTestTag: String,
    buttonTestTag: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTestTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.testTag(buttonTestTag),
                ) {
                    Text(buttonLabel)
                }
            }
        }
    }
}

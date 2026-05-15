package com.eight87.strictlykeptboy.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.H — Connect Accounts screen renders all three cards and
 * swaps the DAVx5 button label based on the install probe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExternalAccountsConnectScreenTest {

    @get:Rule val composeRule = createComposeRule()

    @Test fun rendersAllThreeCards_davx5NotInstalled_showsInstallLabel() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalAccountsConnectScreen(
                    probes = ExternalAccountsProbes(
                        isDavx5Installed = { false },
                        isFDroidClientInstalled = { false },
                        hasExchangeAccount = { false },
                    ),
                )
            }
        }
        composeRule.onNodeWithTag(TestTagConnectAccountsScreen).assertExists()
        composeRule.onNodeWithTag(TestTagConnectAccountsCalDavCard).assertExists()
        composeRule.onNodeWithTag(TestTagConnectAccountsExchangeCard).assertExists()
        composeRule.onNodeWithTag(TestTagConnectAccountsConfiguredCard).assertExists()
        // Install label appears.
        composeRule.onNodeWithText("Install DAVx5").assertIsDisplayed()
    }

    @Test fun davx5Installed_showsOpenLabel() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalAccountsConnectScreen(
                    probes = ExternalAccountsProbes(
                        isDavx5Installed = { true },
                        isFDroidClientInstalled = { false },
                        hasExchangeAccount = { false },
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Open DAVx5").assertIsDisplayed()
    }

    @Test fun exchangeAccountPresent_showsManageLabel() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ExternalAccountsConnectScreen(
                    probes = ExternalAccountsProbes(
                        isDavx5Installed = { false },
                        isFDroidClientInstalled = { false },
                        hasExchangeAccount = { true },
                    ),
                )
            }
        }
        composeRule.onNodeWithText("Manage Exchange accounts").assertIsDisplayed()
    }
}

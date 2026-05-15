package com.eight87.strictlykeptboy.system

import android.accounts.Account
import android.accounts.AccountManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.G.1 — sanity tests for the local-only authenticator.
 *
 * AbstractAccountAuthenticator's interesting methods aren't directly
 * callable (they're guarded by `final` wrappers that hand the result
 * back via an AIDL response), so we test the public Bundle-returning
 * overloads + the static name <-> repoId helpers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SkbAccountAuthenticatorTest {

    @Test fun accountNameAndInverse() {
        assertEquals("repo-7@local", SkbAccountAuthenticator.accountNameFor("repo-7"))
        assertEquals("repo-7", SkbAccountAuthenticator.repoIdFor("repo-7@local"))
        assertNull(SkbAccountAuthenticator.repoIdFor("not-skb-format"))
    }

    @Test fun addAccountReturnsBundleWithType() {
        val authn = SkbAccountAuthenticator(ApplicationProvider.getApplicationContext())
        val out = authn.addAccount(
            response = null,
            accountType = SkbAccountAuthenticator.SKB_ACCOUNT_TYPE,
            authTokenType = null,
            requiredFeatures = null,
            options = null,
        )
        assertEquals(
            SkbAccountAuthenticator.SKB_ACCOUNT_TYPE,
            out.getString(AccountManager.KEY_ACCOUNT_TYPE),
        )
    }

    @Test fun accountRemovalAllowed() {
        val authn = SkbAccountAuthenticator(ApplicationProvider.getApplicationContext())
        val acc = Account("repo-1@local", SkbAccountAuthenticator.SKB_ACCOUNT_TYPE)
        val out = authn.getAccountRemovalAllowed(response = null, account = acc)
        assertTrue(out.getBoolean(AccountManager.KEY_BOOLEAN_RESULT))
    }
}

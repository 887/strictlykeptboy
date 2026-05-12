package com.eight87.strictlykeptboy.git.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.CredentialBinding
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.Transport
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B.4 (HTTPS half) — credential bindings install the right JGit
 * CredentialsProvider on a TransportCommand. SSH binding throws NotImplementedError
 * until Phase I lands the JGit-SSH transport wiring.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CredentialBindingsTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var secrets: SecretsStore
    private lateinit var resolver: ProductionCredentialResolver

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("secrets_bindings_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        secrets = SecretsStore.openForTest(prefs)
        resolver = ProductionCredentialResolver(secrets)
    }

    private fun binding(authMethod: AuthMethod) = RemoteBinding(
        name = RemoteName.ORIGIN,
        url = "https://example.com/r.git",
        transport = Transport.HttpsOAuth,
        authMethod = authMethod,
    )

    private fun freshFetchCommand() = Git.init().setDirectory(tmp.newFolder()).call().fetch()

    @Test fun oauthBindingConfiguresWithoutThrowing() {
        secrets.storeOAuthToken("repo-a", RemoteName.ORIGIN, OAuthToken("gho_xyz"))
        val cb = resolver.resolve("repo-a", binding(AuthMethod.OAuthGitHub))
        cb.configure(freshFetchCommand())   // doesn't throw == installed
    }

    @Test fun oauthBindingThrowsWhenTokenMissing() {
        val cb = resolver.resolve("repo-a", binding(AuthMethod.OAuthGitHub))
        val cmd = freshFetchCommand()
        assertThrows(IllegalStateException::class.java) { cb.configure(cmd) }
    }

    @Test fun patBindingInstallsCredentialsProvider() {
        secrets.storePat("repo-a", RemoteName.ORIGIN, PatCredential("the-bat", "pat-1234567890abcdef"))
        val cb = resolver.resolve("repo-a", binding(AuthMethod.ManualPat))
        val cmd = freshFetchCommand()
        cb.configure(cmd)   // does not throw
    }

    @Test fun patBindingThrowsWhenMissing() {
        val cb = resolver.resolve("repo-a", binding(AuthMethod.ManualPat))
        val cmd = freshFetchCommand()
        assertThrows(IllegalStateException::class.java) { cb.configure(cmd) }
    }

    @Test fun sshBindingThrowsNotImplementedForNow() {
        val cb = resolver.resolve("repo-a", binding(AuthMethod.Ssh))
        val cmd = freshFetchCommand()
        assertThrows(NotImplementedError::class.java) { cb.configure(cmd) }
    }

    @Test fun noneBindingIsNoOp() {
        val cb = resolver.resolve("repo-a", binding(AuthMethod.None))
        assertNotNull(cb)
        // Reuses CredentialBinding.None; configure() does nothing.
        cb.configure(freshFetchCommand())
    }
}

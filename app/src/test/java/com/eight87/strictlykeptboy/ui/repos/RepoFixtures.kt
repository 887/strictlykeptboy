package com.eight87.strictlykeptboy.ui.repos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.git.auth.SecretsStore

internal object RepoFixtures {
    fun store(name: String = "repos_test"): RepoStore {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        return RepoStore.openForTest(prefs)
    }

    fun secretsStore(name: String = "secrets_test"): SecretsStore {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        return SecretsStore.openForTest(prefs)
    }

    fun localOnly(id: String, displayName: String = "Local $id") = RepoConfig(
        repoId = id,
        displayName = displayName,
        rootDir = "/tmp/$id",
        remotes = emptyList(),
        primaryRemote = null,
        authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
    )

    fun withRemote(id: String, displayName: String = "Remote $id"): RepoConfig {
        val r = RemoteBinding(
            name = RemoteName.ORIGIN,
            url = "https://example.com/$id.git",
            transport = Transport.HttpsOAuth,
            authMethod = AuthMethod.OAuthGitHub,
        )
        return RepoConfig(
            repoId = id,
            displayName = displayName,
            rootDir = "/tmp/$id",
            remotes = listOf(r),
            primaryRemote = r.name,
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )
    }
}

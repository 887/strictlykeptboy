package com.eight87.strictlykeptboy.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.Transport
import org.eclipse.jgit.api.Git
import java.io.File

/** Shared fixtures for Phase J sync tests. */
object SyncTestFixtures {
    fun ctx(): Context = ApplicationProvider.getApplicationContext()

    fun newRepoStore(): RepoStore {
        val prefs = ctx().getSharedPreferences("test-repos-${System.nanoTime()}", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        return RepoStore.openForTest(prefs)
    }

    fun newStatusStore(): SyncStatusStore {
        val prefs = ctx().getSharedPreferences("test-status-${System.nanoTime()}", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        return SyncStatusStore.openForTest(prefs)
    }

    fun initBare(parent: File, name: String = "bare-${System.nanoTime()}.git"): File {
        val bare = File(parent, name)
        Git.init().setBare(true).setDirectory(bare).setInitialBranch("main").call().close()
        return bare
    }

    fun bareBinding(bare: File, name: RemoteName = RemoteName.ORIGIN): RemoteBinding =
        RemoteBinding(
            name = name,
            url = bare.toURI().toString(),
            transport = Transport.File,
            authMethod = AuthMethod.None,
        )

    suspend fun cloneFrom(bare: File, workDir: File, repoId: String, additional: List<RemoteBinding> = emptyList()): GitRepo {
        return GitRepo.clone(
            rootDir = workDir,
            repoId = repoId,
            primaryBinding = bareBinding(bare),
            additionalRemotes = additional,
            authorIdentity = AuthorIdentity("Bat", "$repoId@example.com"),
        )
    }

    fun cfgFor(repo: GitRepo, autoSync: Boolean = true, intervalMin: Int = 15): RepoConfig =
        RepoConfig(
            repoId = repo.repoId,
            displayName = repo.repoId,
            rootDir = repo.rootDir.absolutePath,
            remotes = repo.remotes,
            primaryRemote = repo.primaryRemote,
            authorIdentity = AuthorIdentity("Bat", "${repo.repoId}@example.com"),
            autoSyncEnabled = autoSync,
            syncIntervalMinutes = intervalMin,
        )
}

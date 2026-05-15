package com.eight87.strictlykeptboy.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseCommand
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.File

/**
 * Async wrapper around JGit's `Git` for one repository on disk.
 *
 * Threading: every public function suspends on `Dispatchers.IO` and serializes
 * through a per-repo [Mutex]. JGit's Repository is read-safe but writes
 * (commit, rebase, checkout) MUST be serialized — the mutex covers everything.
 *
 * Multi-origin & no-origin handling per Phase ZZ.A:
 * - `remotes` may be empty. [fetch] / [pullRebase] / [push] return the
 *   `NoRemotes` variant rather than throwing.
 * - Network methods accept an explicit `remote` parameter that defaults to
 *   [primaryRemote]; passing a non-empty remote that is not in [remotes]
 *   raises [IllegalArgumentException].
 */
class GitRepo internal constructor(
    val rootDir: File,
    val repoId: String,
    remotes: List<RemoteBinding>,
    primaryRemote: RemoteName?,
    val authorIdentity: AuthorIdentity,
    val defaultBranch: String,
    private val credentialResolver: CredentialResolver,
    private val git: Git,
) {
    private val mutex = Mutex()
    private val repository: Repository = git.repository

    @Volatile
    private var _remotes: List<RemoteBinding> = remotes
    @Volatile
    private var _primaryRemote: RemoteName? = primaryRemote

    /** Snapshot of currently-configured remotes. Mutated by [addRemote] / [removeRemote] / [renameRemote] / [setPrimaryRemote]. */
    val remotes: List<RemoteBinding> get() = _remotes
    val primaryRemote: RemoteName? get() = _primaryRemote

    init {
        require(remotes.isEmpty() == (primaryRemote == null)) {
            "primaryRemote must be null iff remotes is empty"
        }
        if (primaryRemote != null) {
            require(remotes.any { it.name == primaryRemote }) {
                "primaryRemote $primaryRemote is not in remotes list"
            }
        }
    }

    /**
     * Phase ZZ.B — return a snapshot of remotes. JGit config is the on-disk
     * source of truth; this returns the in-memory cache populated by add /
     * remove / rename. Use only after construction.
     */
    fun listRemotes(): List<RemoteBinding> = _remotes

    /**
     * Phase ZZ.B — add a remote to the on-disk JGit config + update the
     * in-memory list. If this is the first remote added to a previously
     * no-origin repo, it becomes [primaryRemote].
     *
     * Throws [IllegalArgumentException] if [binding.name] is reserved
     * (`HEAD`) or already present.
     */
    suspend fun addRemote(binding: RemoteBinding) = withLock {
        require(binding.name.value != "HEAD") { "'HEAD' is reserved" }
        require(_remotes.none { it.name == binding.name }) {
            "remote ${binding.name} already exists"
        }
        git.remoteAdd()
            .setName(binding.name.value)
            .setUri(org.eclipse.jgit.transport.URIish(binding.url))
            .call()
        _remotes = _remotes + binding
        if (_primaryRemote == null) _primaryRemote = binding.name
    }

    /**
     * Phase ZZ.B — remove a remote from the on-disk JGit config + the in-memory list.
     * If the removed remote was primary, the next remote (by add-order) becomes
     * primary; if no remotes remain, [primaryRemote] becomes `null`.
     */
    suspend fun removeRemote(name: RemoteName) = withLock {
        require(_remotes.any { it.name == name }) {
            "remote $name is not configured on this repo"
        }
        git.remoteRemove().setRemoteName(name.value).call()
        val updated = _remotes.filterNot { it.name == name }
        _remotes = updated
        _primaryRemote = when {
            updated.isEmpty() -> null
            _primaryRemote == name -> updated.first().name
            else -> _primaryRemote
        }
    }

    /**
     * Phase ZZ.B — rename a remote in both on-disk JGit config + in-memory list.
     * `HEAD` is reserved. If the renamed remote was primary, the primary
     * pointer is updated to track the new name.
     */
    suspend fun renameRemote(from: RemoteName, to: RemoteName) = withLock {
        require(to.value != "HEAD") { "'HEAD' is reserved" }
        require(_remotes.any { it.name == from }) {
            "remote $from is not configured on this repo"
        }
        require(_remotes.none { it.name == to }) {
            "remote $to already exists"
        }
        // JGit's high-level API has no remoteRename; mutate the underlying
        // repository config directly. We copy every `remote.<from>.*` key
        // to `remote.<to>.*`, then unset the old section. JGit's config
        // honours this idiomatically; subsequent fetch/push find the new name.
        val cfg = repository.config
        val sub = from.value
        for (key in cfg.getNames("remote", sub).toList()) {
            val values = cfg.getStringList("remote", sub, key)
            cfg.setStringList("remote", to.value, key, values.toList())
        }
        cfg.unsetSection("remote", sub)
        cfg.save()
        _remotes = _remotes.map { if (it.name == from) it.copy(name = to) else it }
        if (_primaryRemote == from) _primaryRemote = to
    }

    /**
     * Phase ZZ.E — adjust per-remote push policy. The policy is in-memory
     * here; the caller is responsible for persisting via `RepoStore.update`.
     */
    suspend fun setPushPolicy(name: RemoteName, policy: PushPolicy) = withLock {
        require(_remotes.any { it.name == name }) {
            "remote $name is not configured on this repo"
        }
        _remotes = _remotes.map { if (it.name == name) it.copy(pushPolicy = policy) else it }
    }

    /**
     * Phase ZZ.A — pick a different remote as primary. Must already be in
     * [remotes]. Persistence is the caller's responsibility.
     */
    suspend fun setPrimaryRemote(name: RemoteName) = withLock {
        require(_remotes.any { it.name == name }) {
            "remote $name is not configured on this repo"
        }
        _primaryRemote = name
    }

    suspend fun headSha(): ObjectId? = withLock {
        repository.resolve("HEAD")
    }

    suspend fun status(): GitStatus = withLock {
        val s = git.status().call()
        val head = repository.resolve("HEAD")
        GitStatus(
            untracked = s.untracked.toSet(),
            modified = s.modified.toSet(),
            added = s.added.toSet(),
            removed = s.removed.toSet(),
            missing = s.missing.toSet(),
            conflicting = s.conflicting.toSet(),
            headSha = head,
            localOnlyCommits = countLocalOnlyCommits(head),
        )
    }

    suspend fun log(maxCount: Int = 100): List<LogEntry> = withLock {
        val head = repository.resolve("HEAD") ?: return@withLock emptyList()
        git.log().add(head).setMaxCount(maxCount).call().map { commit ->
            LogEntry(
                sha = commit.id,
                authorName = commit.authorIdent.name,
                authorEmail = commit.authorIdent.emailAddress,
                whenEpochSec = commit.commitTime.toLong(),
                message = commit.fullMessage,
            )
        }
    }

    suspend fun commitAll(message: String): CommitResult = withLock {
        val s = git.status().call()
        if (s.isClean) return@withLock CommitResult.NothingToCommit

        // `add .` stages new + modified files but NOT deletions. Pick up
        // missing/removed entries with `rm` so deletions actually land in
        // the commit.
        git.add().addFilepattern(".").call()
        for (path in s.missing + s.removed) {
            try {
                git.rm().setCached(true).addFilepattern(path).call()
            } catch (_: Exception) {
                // already staged by add; ignore.
            }
        }
        try {
            val commit = git.commit()
                .setAuthor(authorIdentity.name, authorIdentity.email)
                .setCommitter(authorIdentity.name, authorIdentity.email)
                .setMessage(message)
                .call()
            // Round 2.18.G.7 — best-effort hook for the sync adapter,
            // wired by AppGraph.parkRuntimes; null in tests.
            com.eight87.strictlykeptboy.system.SkbCommitNotifier.notifyCommit(repoId)
            CommitResult.Success(commit.id, message)
        } catch (t: Throwable) {
            CommitResult.Failed(SyncError.Unknown(t))
        }
    }

    suspend fun commitPaths(paths: List<String>, message: String): CommitResult = withLock {
        if (paths.isEmpty()) return@withLock CommitResult.NothingToCommit
        val add = git.add()
        paths.forEach { add.addFilepattern(it) }
        add.call()
        try {
            val commit = git.commit()
                .setOnly(paths.first()).also { c -> paths.drop(1).forEach { c.setOnly(it) } }
                .setAuthor(authorIdentity.name, authorIdentity.email)
                .setCommitter(authorIdentity.name, authorIdentity.email)
                .setMessage(message)
                .call()
            CommitResult.Success(commit.id, message)
        } catch (t: Throwable) {
            CommitResult.Failed(SyncError.Unknown(t))
        }
    }

    suspend fun fetch(remote: RemoteName? = primaryRemote): FetchResult = withLock {
        if (remotes.isEmpty()) return@withLock FetchResult.NoRemotes
        val binding = remoteOrThrow(remote)
        val fetch = git.fetch().setRemote(binding.name.value)
        credentialResolver.resolve(repoId, binding).configure(fetch)
        try {
            val result = fetch.call()
            val updated = result.trackingRefUpdates.map { it.localName }.toSet()
            FetchResult.Success(binding.name, updated)
        } catch (t: Throwable) {
            FetchResult.Failed(binding.name, classify(t, binding))
        }
    }

    suspend fun pullRebase(remote: RemoteName? = primaryRemote): PullResult = withLock {
        if (remotes.isEmpty()) return@withLock PullResult.NoRemotes
        val binding = remoteOrThrow(remote)
        // Fetch first.
        val fetchCmd = git.fetch().setRemote(binding.name.value)
        credentialResolver.resolve(repoId, binding).configure(fetchCmd)
        try {
            fetchCmd.call()
        } catch (t: Throwable) {
            return@withLock PullResult.Failed(classify(t, binding))
        }

        val before = repository.resolve("HEAD")
        val upstreamRef = "${binding.name.value}/$defaultBranch"
        val upstreamSha = repository.resolve(upstreamRef)
            ?: return@withLock PullResult.UpToDate
        if (before != null && before == upstreamSha) return@withLock PullResult.UpToDate

        // Rebase onto upstream.
        val rebase = git.rebase().setUpstream(upstreamRef).call()
        when (rebase.status) {
            RebaseResult.Status.UP_TO_DATE -> PullResult.UpToDate
            RebaseResult.Status.FAST_FORWARD -> PullResult.FastForwarded(
                fromSha = before ?: ObjectId.zeroId(),
                toSha = repository.resolve("HEAD"),
                changedPaths = diffPaths(before, repository.resolve("HEAD")),
            )
            RebaseResult.Status.OK -> PullResult.Rebased(
                fromSha = before ?: ObjectId.zeroId(),
                toSha = repository.resolve("HEAD"),
                changedPaths = diffPaths(before, repository.resolve("HEAD")),
            )
            RebaseResult.Status.STOPPED, RebaseResult.Status.CONFLICTS -> PullResult.Conflicted(
                conflictedPaths = (rebase.conflicts ?: emptyList()).ifEmpty { unmergedPathsFromDirCache() },
                handle = RebaseHandle(this),
            )
            else -> PullResult.Failed(SyncError.Unknown())
        }
    }

    private fun unmergedPathsFromDirCache(): List<String> {
        val cache = repository.readDirCache()
        val out = LinkedHashSet<String>()
        for (i in 0 until cache.entryCount) {
            val e = cache.getEntry(i)
            if (e.stage != 0) out += e.pathString
        }
        return out.toList()
    }

    /**
     * Stage a single path during conflict resolution. Used by the conflict-
     * resolution UI after writing the resolved content to disk. Does NOT
     * commit — the caller drives [continueRebase] once every path is staged.
     */
    suspend fun stageForResolve(path: String) = withLock {
        // Mark conflict resolved: hash the working-tree content into a single
        // stage-0 DirCache entry, replacing JGit's stage-1/2/3 conflict trio.
        val workFile = java.io.File(rootDir, path)
        val ins = repository.newObjectInserter()
        val blobId = try {
            val id = java.io.FileInputStream(workFile).use { stream ->
                ins.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, workFile.length(), stream)
            }
            ins.flush()
            id
        } finally {
            ins.close()
        }

        val dirCache = repository.lockDirCache()
        var committed = false
        try {
            val builder = dirCache.builder()
            for (i in 0 until dirCache.entryCount) {
                val existing = dirCache.getEntry(i)
                if (existing.pathString == path) continue
                builder.add(existing)
            }
            val resolved = org.eclipse.jgit.dircache.DirCacheEntry(path)
            resolved.fileMode = org.eclipse.jgit.lib.FileMode.REGULAR_FILE
            resolved.setObjectId(blobId)
            resolved.setLength(workFile.length())
            builder.add(resolved)
            builder.commit()
            committed = true
        } finally {
            if (!committed) dirCache.unlock()
        }
    }

    suspend fun continueRebase(handle: RebaseHandle): PullResult = withLock {
        require(handle.repo === this) { "RebaseHandle does not belong to this GitRepo" }
        val before = repository.resolve("ORIG_HEAD") ?: repository.resolve("HEAD")
        val result = git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call()
        rebaseResultToPullResult(result, before)
    }

    suspend fun abortRebase(handle: RebaseHandle): PullResult = withLock {
        require(handle.repo === this) { "RebaseHandle does not belong to this GitRepo" }
        git.rebase().setOperation(RebaseCommand.Operation.ABORT).call()
        PullResult.UpToDate
    }

    /**
     * Push to a specific remote, or fan-out across all push-enabled remotes
     * when [remote] is null and [remotes] has more than one entry. Per ZZ.E:
     * if primary push succeeds but a mirror push fails, the call returns
     * [PushResult.PartiallySuccess] — primary is considered shipped.
     */
    suspend fun push(remote: RemoteName? = primaryRemote): PushResult = withLock {
        if (remotes.isEmpty()) return@withLock PushResult.NoRemotes
        val targets = if (remote != null) {
            listOf(remoteOrThrow(remote))
        } else {
            remotes.filter { it.pushPolicy == PushPolicy.Push }
        }
        if (targets.isEmpty()) return@withLock PushResult.NothingToPush

        // Push primary first if it's in the target set.
        val ordered = targets.sortedBy { if (it.name == primaryRemote) 0 else 1 }
        val pushed = mutableSetOf<RemoteName>()
        val failures = mutableMapOf<RemoteName, SyncError>()
        var primaryFailed = false

        for (binding in ordered) {
            if (primaryFailed && binding.name != primaryRemote) {
                // Don't push mirrors when primary failed — risk of mirror running
                // ahead of canonical source. Per ZZ.E.4.
                break
            }
            val cmd = git.push().setRemote(binding.name.value)
            credentialResolver.resolve(repoId, binding).configure(cmd)
            try {
                val results = cmd.call()
                val rejection = results.flatMap { it.remoteUpdates }.firstOrNull {
                    it.status != RemoteRefUpdate.Status.OK &&
                        it.status != RemoteRefUpdate.Status.UP_TO_DATE
                }
                if (rejection != null) {
                    val reason = when (rejection.status) {
                        RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> PushRejection.NonFastForward
                        RemoteRefUpdate.Status.REJECTED_NODELETE -> PushRejection.BranchProtected
                        RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED -> PushRejection.NonFastForward
                        RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> PushRejection.Unknown
                        else -> PushRejection.Unknown
                    }
                    failures[binding.name] = SyncError.RemoteRejected(reason)
                    if (binding.name == primaryRemote) primaryFailed = true
                } else {
                    pushed += binding.name
                }
            } catch (t: Throwable) {
                failures[binding.name] = classify(t, binding)
                if (binding.name == primaryRemote) primaryFailed = true
            }
        }

        when {
            pushed.isEmpty() && failures.size == 1 -> {
                val (remoteName, err) = failures.entries.first()
                when (err) {
                    is SyncError.RemoteRejected -> PushResult.Rejected(remoteName, err.reason)
                    else -> PushResult.Failed(remoteName, err)
                }
            }
            pushed.isEmpty() -> PushResult.Failed(null, SyncError.Unknown())
            failures.isEmpty() && pushed.size == ordered.size -> PushResult.Success
            failures.isEmpty() -> PushResult.Success
            else -> PushResult.PartiallySuccess(pushed, failures)
        }
    }

    /**
     * Path-level diff between two commits. Used by the indexer (Phase D) to
     * decide which entity files to re-parse after a pull / commit.
     */
    suspend fun diffSinceLastIndexed(lastIndexedHead: ObjectId?): Set<ChangedPath> = withLock {
        val head = repository.resolve("HEAD") ?: return@withLock emptySet()
        if (lastIndexedHead == head) return@withLock emptySet()
        diffPaths(lastIndexedHead, head)
    }

    suspend fun close() = withLock {
        git.close()
        repository.close()
    }

    // ---- internals ----------------------------------------------------------

    private fun remoteOrThrow(name: RemoteName?): RemoteBinding {
        if (name == null) {
            throw IllegalStateException(
                "no remote specified and no primaryRemote configured (repo is no-origin?)",
            )
        }
        return remotes.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("remote $name is not configured on this repo")
    }

    private fun diffPaths(from: ObjectId?, to: ObjectId?): Set<ChangedPath> {
        if (to == null) return emptySet()
        val reader = repository.newObjectReader()
        val newTree: AbstractTreeIterator = CanonicalTreeParser().apply {
            reset(reader, RevWalk(repository).use { it.parseCommit(to).tree })
        }
        val oldTree: AbstractTreeIterator = if (from == null) {
            EmptyTreeIterator()
        } else {
            CanonicalTreeParser().apply {
                reset(reader, RevWalk(repository).use { it.parseCommit(from).tree })
            }
        }
        return DiffFormatter(DisabledOutputStream.INSTANCE).use { fmt ->
            fmt.setRepository(repository)
            fmt.scan(oldTree, newTree).map { entry ->
                ChangedPath(
                    path = entry.newPath.takeIf { it != DiffEntry.DEV_NULL } ?: entry.oldPath,
                    oldPath = entry.oldPath.takeIf { it != DiffEntry.DEV_NULL },
                    kind = when (entry.changeType) {
                        DiffEntry.ChangeType.ADD -> ChangeKind.Added
                        DiffEntry.ChangeType.MODIFY -> ChangeKind.Modified
                        DiffEntry.ChangeType.DELETE -> ChangeKind.Deleted
                        DiffEntry.ChangeType.RENAME -> ChangeKind.Renamed
                        DiffEntry.ChangeType.COPY -> ChangeKind.Copied
                    },
                )
            }.toSet()
        }
    }

    private fun countLocalOnlyCommits(head: ObjectId?): Int {
        if (head == null) return 0
        if (remotes.isEmpty()) {
            // All commits are local-only for no-origin repos. Walk + count.
            return RevWalk(repository).use { walk ->
                walk.markStart(walk.parseCommit(head))
                walk.count()
            }
        }
        // For repos with remotes, count commits reachable from HEAD but not
        // from ANY remote tracking branch. This is the "not yet pushed to all"
        // count per ZZ.A.6.
        return RevWalk(repository).use { walk ->
            walk.markStart(walk.parseCommit(head))
            for (r in remotes) {
                val ref = repository.resolve("${r.name.value}/$defaultBranch")
                if (ref != null) walk.markUninteresting(walk.parseCommit(ref))
            }
            walk.count()
        }
    }

    private fun rebaseResultToPullResult(result: RebaseResult, before: ObjectId?): PullResult =
        when (result.status) {
            RebaseResult.Status.UP_TO_DATE -> PullResult.UpToDate
            RebaseResult.Status.FAST_FORWARD -> PullResult.FastForwarded(
                fromSha = before ?: ObjectId.zeroId(),
                toSha = repository.resolve("HEAD"),
                changedPaths = diffPaths(before, repository.resolve("HEAD")),
            )
            RebaseResult.Status.OK -> PullResult.Rebased(
                fromSha = before ?: ObjectId.zeroId(),
                toSha = repository.resolve("HEAD"),
                changedPaths = diffPaths(before, repository.resolve("HEAD")),
            )
            RebaseResult.Status.STOPPED, RebaseResult.Status.CONFLICTS ->
                PullResult.Conflicted(
                    (result.conflicts ?: emptyList()).ifEmpty { unmergedPathsFromDirCache() },
                    RebaseHandle(this),
                )
            else -> PullResult.Failed(SyncError.Unknown())
        }

    private fun classify(t: Throwable, binding: RemoteBinding): SyncError {
        val msg = t.message?.lowercase().orEmpty()
        return when {
            "host key" in msg || "hostkey" in msg -> SyncError.HostKey(extractHost(binding.url), t)
            "auth" in msg || "credentials" in msg || "401" in msg || "403" in msg ->
                SyncError.Auth(binding.authMethod, binding.name, t)
            "connect" in msg || "unreachable" in msg || "timeout" in msg ->
                SyncError.Network(t)
            else -> SyncError.Unknown(t)
        }
    }

    private fun extractHost(url: String): String = url
        .substringAfter("://", url.substringAfter("@", url))
        .substringBefore("/")
        .substringBefore(":")

    private suspend inline fun <T> withLock(crossinline block: () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    companion object {
        suspend fun open(
            rootDir: File,
            repoId: String,
            remotes: List<RemoteBinding>,
            primaryRemote: RemoteName?,
            authorIdentity: AuthorIdentity,
            credentialResolver: CredentialResolver = CredentialResolver.NoopResolver,
            defaultBranch: String = "main",
        ): GitRepo = withContext(Dispatchers.IO) {
            val git = try {
                Git.open(rootDir)
            } catch (e: RepositoryNotFoundException) {
                throw IllegalArgumentException("not a git repo: ${rootDir.absolutePath}", e)
            }
            GitRepo(rootDir, repoId, remotes, primaryRemote, authorIdentity, defaultBranch, credentialResolver, git)
        }

        /**
         * Initialise an empty git repo at [rootDir] with one configured remote.
         * For no-origin repos use [initLocalOnly].
         */
        suspend fun init(
            rootDir: File,
            repoId: String,
            remotes: List<RemoteBinding>,
            primaryRemote: RemoteName,
            authorIdentity: AuthorIdentity,
            credentialResolver: CredentialResolver = CredentialResolver.NoopResolver,
            defaultBranch: String = "main",
        ): GitRepo = withContext(Dispatchers.IO) {
            require(remotes.isNotEmpty()) { "use initLocalOnly for repos with no remotes" }
            rootDir.mkdirs()
            val git = Git.init()
                .setDirectory(rootDir)
                .setInitialBranch(defaultBranch)
                .call()
            applyRemotes(git, remotes)
            writeRepoIdFile(rootDir, repoId)
            GitRepo(rootDir, repoId, remotes, primaryRemote, authorIdentity, defaultBranch, credentialResolver, git)
        }

        /**
         * Initialise a phone-only git repo at [rootDir] — git-backed locally,
         * zero remotes. Per Phase ZZ.A.2 / D.74.
         */
        suspend fun initLocalOnly(
            rootDir: File,
            repoId: String,
            authorIdentity: AuthorIdentity,
            defaultBranch: String = "main",
        ): GitRepo = withContext(Dispatchers.IO) {
            rootDir.mkdirs()
            val git = Git.init()
                .setDirectory(rootDir)
                .setInitialBranch(defaultBranch)
                .call()
            writeRepoIdFile(rootDir, repoId)
            GitRepo(rootDir, repoId, emptyList(), null, authorIdentity, defaultBranch, CredentialResolver.NoopResolver, git)
        }

        suspend fun clone(
            rootDir: File,
            repoId: String,
            primaryBinding: RemoteBinding,
            additionalRemotes: List<RemoteBinding> = emptyList(),
            authorIdentity: AuthorIdentity,
            credentialResolver: CredentialResolver = CredentialResolver.NoopResolver,
            defaultBranch: String = "main",
        ): GitRepo = withContext(Dispatchers.IO) {
            val cmd = Git.cloneRepository()
                .setURI(primaryBinding.url)
                .setDirectory(rootDir)
                .setRemote(primaryBinding.name.value)
                .setBranch(defaultBranch)
            credentialResolver.resolve(repoId, primaryBinding).configure(cmd)
            val git = cmd.call()
            applyRemotes(git, additionalRemotes)
            writeRepoIdFile(rootDir, repoId)
            val remotes = listOf(primaryBinding) + additionalRemotes
            GitRepo(rootDir, repoId, remotes, primaryBinding.name, authorIdentity, defaultBranch, credentialResolver, git)
        }

        /**
         * Phase ZZ.A.4 — repo-id is committed at `.strictlykeptboy/repo-id`
         * so every device that has this repo sees the same value. Idempotent;
         * if the file already exists with a non-blank value, leave it.
         */
        private fun writeRepoIdFile(rootDir: File, repoId: String) {
            val dir = File(rootDir, ".strictlykeptboy")
            dir.mkdirs()
            val file = File(dir, "repo-id")
            if (!file.exists() || file.readText().trim().isBlank()) {
                file.writeText(repoId + "\n")
            }
        }

        /**
         * Phase ZZ.A.4 — read the repo-id from disk if present. Returns null
         * for repos predating this convention; callers can fall back to the
         * per-device RepoConfig.repoId.
         */
        fun readRepoIdFile(rootDir: File): String? {
            val file = File(rootDir, ".strictlykeptboy/repo-id")
            if (!file.isFile) return null
            return file.readText().trim().ifBlank { null }
        }

        private fun applyRemotes(git: Git, remotes: List<RemoteBinding>) {
            for (r in remotes) {
                git.remoteAdd()
                    .setName(r.name.value)
                    .setUri(org.eclipse.jgit.transport.URIish(r.url))
                    .call()
            }
        }
    }
}

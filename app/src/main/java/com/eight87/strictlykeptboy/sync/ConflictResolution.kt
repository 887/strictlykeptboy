package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.PullResult
import com.eight87.strictlykeptboy.git.RebaseHandle
import com.eight87.strictlykeptboy.git.RemoteName
import java.io.File

/**
 * Phase J.5 — three-way file model + resolution choices. Per SE-J:
 * "ours" is the user's local change, "theirs" is the remote (we relabel
 * from JGit's rebase-internal terminology so the UI is user-perspective).
 */
data class ConflictedFile(
    val path: String,
    val oursContent: String,
    val theirsContent: String,
    val baseContent: String?,
)

enum class ConflictChoice { KeepMine, KeepTheirs }

/**
 * Read a conflicted file from disk. JGit writes the working-tree file with
 * standard `<<<<<<<` / `=======` / `>>>>>>>` markers when the rebase stops;
 * we parse those into [ConflictedFile.oursContent] / [theirsContent].
 *
 * For files where JGit could not write conflict markers (binary; unmerged
 * add/add), we fall back to the raw working-tree text as `oursContent` and
 * leave `theirsContent` empty.
 */
fun readConflictedFile(repoRoot: File, path: String): ConflictedFile {
    val file = File(repoRoot, path)
    val raw = if (file.exists()) file.readText() else ""
    // JGit writes conflict markers from the rebase perspective: the
    // `<<<<<<<` block is the upstream (remote, what we're rebasing onto)
    // and the `>>>>>>>` block is the local commit being replayed. We relabel
    // from the user's viewpoint per SE-J: "ours" = user's local change,
    // "theirs" = remote change.
    val ours = StringBuilder()
    val theirs = StringBuilder()
    val base = StringBuilder()
    var section = Section.None
    var sawMarker = false
    for (line in raw.lineSequence()) {
        when {
            line.startsWith("<<<<<<<") -> { sawMarker = true; section = Section.Theirs }
            line.startsWith("|||||||") -> section = Section.Base
            line.startsWith("=======") -> section = Section.Ours
            line.startsWith(">>>>>>>") -> section = Section.None
            else -> when (section) {
                Section.None -> {
                    ours.appendLine(line); theirs.appendLine(line); base.appendLine(line)
                }
                Section.Ours -> ours.appendLine(line)
                Section.Theirs -> theirs.appendLine(line)
                Section.Base -> base.appendLine(line)
            }
        }
    }
    return if (sawMarker) {
        ConflictedFile(path, ours.toString(), theirs.toString(), base.toString().takeIf { it.isNotEmpty() })
    } else {
        ConflictedFile(path, raw, "", null)
    }
}

private enum class Section { None, Ours, Theirs, Base }

/**
 * Apply a [choice] (or arbitrary [overrideContent]) to a single file, then
 * stage it. Caller drives [GitRepo.continueRebase] after every file is resolved.
 */
suspend fun resolveAs(
    repo: GitRepo,
    file: ConflictedFile,
    choice: ConflictChoice,
    overrideContent: String? = null,
) {
    val finalContent = overrideContent ?: when (choice) {
        ConflictChoice.KeepMine -> file.oursContent
        ConflictChoice.KeepTheirs -> file.theirsContent
    }
    val target = File(repo.rootDir, file.path)
    target.parentFile?.mkdirs()
    target.writeText(finalContent)
    // Use the git add path-pattern through the registry path; commitPaths
    // is not what we want (we're mid-rebase). Direct JGit add via repo:
    repo.stageForResolve(file.path)
}

/**
 * Convenience: resolve every conflicted file with the same choice and
 * continue the rebase. UI's "Keep mine (all)" / "Keep theirs (all)" buttons.
 */
suspend fun resolveAllAndContinue(
    repo: GitRepo,
    handle: RebaseHandle,
    paths: List<String>,
    choice: ConflictChoice,
): PullResult {
    for (path in paths) {
        val file = readConflictedFile(repo.rootDir, path)
        resolveAs(repo, file, choice)
    }
    return repo.continueRebase(handle)
}

@Suppress("unused")
internal fun bindRemoteForLabel(remote: RemoteName): String = "Remote (${remote.value})"

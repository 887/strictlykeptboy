package com.eight87.strictlykeptboy.ui.share

import android.app.Activity
import android.widget.Toast
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.Uuid7
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Round 2.28 / SOLID #8b — extracted from MainActivity.
 *
 * Pure dispatch of a [ShareLinkReceiver.Action] into concrete side
 * effects against the host [Activity]. Toasts + RepoStore writes.
 *
 * Composition root only place that wires concrete classes (R.X.3) —
 * the receiver is still MainActivity, this is just the dispatch body.
 */
object ShareActionHandler {

    /**
     * Round 2.18.E.10 — true only when [data] is a Phase O share link
     * or Phase MM custom-scheme deep link. Calendar contract intents
     * (`content://com.android.calendar/...`, `file://...ics`,
     * `https://example.com/foo.ics`) MUST fall through to the
     * Calendar router without being misclassified by the share
     * receiver.
     */
    fun isShareLinkScheme(data: String): Boolean {
        return data.startsWith("strictlykeptboy://") ||
            data.startsWith("https://strictlykeptboy.app/link/") ||
            data.startsWith("http://strictlykeptboy.app/link/")
    }

    /**
     * Phase O.2 — dispatch a [ShareLinkReceiver.Action] into concrete
     * side effects.
     */
    fun handle(
        activity: Activity,
        action: ShareLinkReceiver.Action,
        repoStore: RepoStore,
    ) {
        when (action) {
            is ShareLinkReceiver.Action.Invalid -> {
                Toast.makeText(activity, activity.getString(R.string.share_invalid), Toast.LENGTH_LONG).show()
            }
            is ShareLinkReceiver.Action.Expired -> {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.share_expired, action.link.expiryIso ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            }
            is ShareLinkReceiver.Action.CloneReadOnly -> {
                val link = action.link
                val repoId = Uuid7.generate().toString()
                val rootDir = activity.filesDir.resolve("shared-readonly/$repoId").apply { mkdirs() }
                val label = link.sourceLabel?.takeIf { it.isNotBlank() }
                    ?: link.urls.first().substringAfterLast('/').removeSuffix(".git")
                GlobalScope.launch {
                    runCatching {
                        repoStore.add(
                            RepoConfig(
                                repoId = repoId,
                                displayName = label,
                                rootDir = rootDir.absolutePath,
                                remotes = emptyList(),
                                primaryRemote = null,
                                authorIdentity = AuthorIdentity("me", "me@example.com"),
                                readOnlyViaShare = true,
                                sourceRepoLabel = label,
                                sourceRepoBackLink = link.backLink,
                            ),
                        )
                    }
                }
                Toast.makeText(
                    activity,
                    activity.getString(R.string.share_received_read_only_badge),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            is ShareLinkReceiver.Action.LaunchAddRepo -> {
                Toast.makeText(
                    activity,
                    action.link.urls.first(),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}

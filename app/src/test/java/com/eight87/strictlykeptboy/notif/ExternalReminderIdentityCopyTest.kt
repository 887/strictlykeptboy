package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Round 2.18.F.5 — for external events (synthetic repoId starting with
 * `external/`), the notification body composer reaches into the **active
 * write-target repo's** identity (via the `IdentityPrefs` /
 * [IdentityNotifBody.loadFor] seam) — never the synthetic external
 * "repo" (which has no identity.toml). Mocked through the test seam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ExternalReminderIdentityCopyTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @After fun reset() {
        ReminderBroadcastReceiver.identityResolver = { c, r -> IdentityNotifBody.loadFor(c, r) }
        ReminderBroadcastReceiver.petModeResolver = { _, _ -> PetMode.None }
        ReminderBroadcastReceiver.effectiveRepoIdResolver = { _, r ->
            if (r.startsWith("external/")) null else r
        }
    }

    @Test fun externalRepoIdReroutesToActiveRepoForIdentityCopy() {
        var observedRepoIdForIdentity: String? = null
        ReminderBroadcastReceiver.effectiveRepoIdResolver = { _, repoId ->
            if (repoId.startsWith("external/")) "active-repo-id" else repoId
        }
        ReminderBroadcastReceiver.identityResolver = { _, repoId ->
            observedRepoIdForIdentity = repoId
            IdentityTomlData(
                praiseTerm = "love",
                honorificForDom = "Sir",
                pronouns = IdentityPronouns.HeHim,
            )
        }
        ReminderBroadcastReceiver.petModeResolver = { _, _ -> PetMode.None }

        val receiver = ReminderBroadcastReceiver()
        val intent = Intent(ReminderBroadcastReceiver.ACTION_FIRE).apply {
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID,
                "external/com.google/alice@example.com")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev-42")
            putExtra(ReminderBroadcastReceiver.EXTRA_CALENDAR_ID, "100")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "Standup")
            putExtra(ReminderBroadcastReceiver.EXTRA_LEAD_TIME, "15m")
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, false)
        }
        receiver.onReceive(ctx(), intent)

        // The receiver looked up identity via the rerouted active repoId
        // (NOT the synthetic external one).
        assertNotNull(observedRepoIdForIdentity)
        assertTrue(
            "expected rerouted identity lookup, got $observedRepoIdForIdentity",
            observedRepoIdForIdentity == "active-repo-id",
        )

        // Notification posted with the praise-driven body.
        val nm = ctx().getSystemService(NotificationManager::class.java)
        val posted = shadowOf(nm).activeNotifications
        assertTrue(posted.isNotEmpty())
        val text = posted.first().notification.extras
            .getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue("expected praise term in body, got: $text", text.contains("love"))
    }

    @Test fun externalRepoIdWithNoActiveRepoFallsBackToNeutral() {
        ReminderBroadcastReceiver.effectiveRepoIdResolver = { _, _ -> null }
        ReminderBroadcastReceiver.identityResolver = { _, _ -> null }
        ReminderBroadcastReceiver.petModeResolver = { _, _ -> PetMode.None }

        val receiver = ReminderBroadcastReceiver()
        val intent = Intent(ReminderBroadcastReceiver.ACTION_FIRE).apply {
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID,
                "external/com.google/alice@example.com")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev-99")
            putExtra(ReminderBroadcastReceiver.EXTRA_CALENDAR_ID, "100")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "Standup")
            putExtra(ReminderBroadcastReceiver.EXTRA_LEAD_TIME, "15m")
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, false)
        }
        receiver.onReceive(ctx(), intent)

        val nm = ctx().getSystemService(NotificationManager::class.java)
        // Still posts (neutral copy bank), doesn't crash on missing identity.
        val posted = shadowOf(nm).activeNotifications
        assertTrue(posted.isNotEmpty())
    }
}

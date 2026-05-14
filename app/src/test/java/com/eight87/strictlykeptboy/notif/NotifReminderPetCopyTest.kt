package com.eight87.strictlykeptboy.notif

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Phase 2.2.E.M5 — ReminderBroadcastReceiver routes through
 * [IdentityNotifBody.bodyForPet] with the derived [PetMode] so the
 * pet-mode register the user picked in the wizard reaches the lockscreen.
 *
 * The test substitutes the [ReminderBroadcastReceiver.petModeResolver]
 * + [ReminderBroadcastReceiver.identityResolver] seams to avoid needing
 * a live RepoStore (which depends on EncryptedSharedPreferences,
 * unsupported under Robolectric without extensive shadowing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class NotifReminderPetCopyTest {

    private val identity = IdentityTomlData.LockedDefaults.copy(
        praiseTerm = "good boy",
        honorificForDom = "Sir",
    )

    @Before fun installResolvers() {
        ReminderBroadcastReceiver.petModeResolver = { _, _ -> PetMode.SelfPet }
        ReminderBroadcastReceiver.identityResolver = { _, _ -> identity }
    }

    @After fun resetResolvers() {
        // Restore production behaviour for sibling tests in the same JVM.
        ReminderBroadcastReceiver.petModeResolver = { context, repoId ->
            val cfg = runCatching {
                com.eight87.strictlykeptboy.git.RepoStore.open(context).get(repoId)
            }.getOrNull()
            if (cfg == null) PetMode.None
            else PetModeDerivation.deriveFor(java.io.File(cfg.rootDir).toPath())
        }
        ReminderBroadcastReceiver.identityResolver = { context, repoId ->
            IdentityNotifBody.loadFor(context, repoId)
        }
    }

    @Test fun selfPetRegisterReachesNotificationBody() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)

        val intent = Intent(ctx, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, "r1")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev-pet-1")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "4pm")
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, false)
        }
        ReminderBroadcastReceiver().onReceive(ctx, intent)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(nm).activeNotifications
        assertEquals(1, posted.size)
        val text = posted[0].notification.extras.getCharSequence("android.text")?.toString()
        assertNotNull(text)
        // SelfPet phrasing: "<praise>, your <title> walk, good boy"
        assertEquals("good boy, your 4pm walk, good boy", text)
    }

    @Test fun partneredPetRegisterReachesNotificationBody() {
        ReminderBroadcastReceiver.petModeResolver = { _, _ -> PetMode.PartneredPet }
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)

        val intent = Intent(ctx, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, "r1")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev-pet-2")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "4pm")
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, false)
        }
        ReminderBroadcastReceiver().onReceive(ctx, intent)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = shadowOf(nm).activeNotifications[0].notification.extras
            .getCharSequence("android.text")?.toString()
        assertEquals("good boy, your 4pm — Sir wants you ready", text)
    }

    @Test fun selfKeepRegisterReachesNotificationBody() {
        ReminderBroadcastReceiver.petModeResolver = { _, _ -> PetMode.SelfKeep }
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)

        val intent = Intent(ctx, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, "r1")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev-pet-3")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "4pm")
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, false)
        }
        ReminderBroadcastReceiver().onReceive(ctx, intent)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = shadowOf(nm).activeNotifications[0].notification.extras
            .getCharSequence("android.text")?.toString()
        assertEquals("good boy, your 4pm — stay on track", text)
    }

    @Test fun privateEventShortCircuitsBeforePetMode() {
        // Even with SelfPet derived, the private flag must collapse to the
        // generic role-pre body so praise + honorific cannot leak onto the
        // lockscreen preview.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.registerAll(ctx)

        val intent = Intent(ctx, ReminderBroadcastReceiver::class.java).apply {
            action = ReminderBroadcastReceiver.ACTION_FIRE
            putExtra(ReminderBroadcastReceiver.EXTRA_REPO_ID, "r1")
            putExtra(ReminderBroadcastReceiver.EXTRA_EVENT_ID, "ev-pet-priv")
            putExtra(ReminderBroadcastReceiver.EXTRA_TITLE, "Therapist")
            putExtra(ReminderBroadcastReceiver.EXTRA_PRIVATE, true)
        }
        ReminderBroadcastReceiver().onReceive(ctx, intent)

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val text = shadowOf(nm).activeNotifications[0].notification.extras
            .getCharSequence("android.text")?.toString() ?: ""
        assertTrue("must not contain praise term", !text.contains("good boy"))
        assertTrue("must not contain honorific", !text.contains("Sir"))
        assertTrue("must not contain title", !text.contains("Therapist"))
    }
}

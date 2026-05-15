package com.eight87.strictlykeptboy.system

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Round 2.18.G.2 — bound service that exposes [SkbAccountAuthenticator]
 * to the system's `AccountManager`.
 *
 * Registered in the manifest with the
 * `android.accounts.AccountAuthenticator` intent-filter + a `meta-data`
 * pointer at `res/xml/authenticator.xml`. AccountManager discovers it
 * by enumerating that filter at boot.
 */
class SkbAuthenticatorService : Service() {

    private val authenticator: SkbAccountAuthenticator by lazy {
        SkbAccountAuthenticator(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder = authenticator.iBinder
}

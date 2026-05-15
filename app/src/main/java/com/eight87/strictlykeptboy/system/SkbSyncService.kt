package com.eight87.strictlykeptboy.system

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Round 2.18.G.4 — bound service exposing [SkbCalendarSyncAdapter] to
 * Android's sync framework.
 *
 * Manifest entry pairs this with the
 * `android.content.SyncAdapter` intent-filter + a `meta-data` pointer
 * at `res/xml/sync_calendar.xml` (which declares
 * `contentAuthority="com.android.calendar"` +
 * `accountType="com.eight87.strictlykeptboy"`).
 *
 * The adapter is a process-wide singleton (one [SkbCalendarSyncAdapter]
 * per [Service] instance, which Android keeps alive across syncs for
 * the same authority).
 */
class SkbSyncService : Service() {

    private val lock = Any()
    private var syncAdapter: SkbCalendarSyncAdapter? = null

    override fun onCreate() {
        super.onCreate()
        synchronized(lock) {
            if (syncAdapter == null) {
                syncAdapter = SkbCalendarSyncAdapter(
                    context = applicationContext,
                    autoInitialize = true,
                    allowParallelSyncs = false,
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = syncAdapter!!.syncAdapterBinder
}

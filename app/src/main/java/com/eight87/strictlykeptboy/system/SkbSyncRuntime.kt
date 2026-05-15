package com.eight87.strictlykeptboy.system

import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.git.RepoStore

/**
 * Round 2.18.G.4 — process-wide handles parked here so
 * [SkbSyncService.getSyncAdapterBinder] can locate everything it needs
 * without spinning up a separate DI graph inside the service process.
 *
 * Mirrors the
 * [com.eight87.strictlykeptboy.sync.SyncRuntime] /
 * [com.eight87.strictlykeptboy.auto.CarAppRuntime] pattern. `AppGraph`
 * sets the fields during `parkRuntimes`; the sync service reads them
 * lazily on first `onPerformSync`. Null entries → the adapter degrades
 * to a no-op, the way it does on cold-boot before AppGraph wires up.
 *
 * Process boundaries: the SyncAdapter runs in the same process as the
 * rest of the app (no `android:process="..."` on the service), so this
 * static lookup is safe — Android won't tear down `AppGraph`'s memory
 * underneath us while the sync is running.
 */
object SkbSyncRuntime {

    @Volatile var repoStore: RepoStore? = null

    @Volatile var cacheDatabase: CacheDatabase? = null

    @Volatile var systemCalendarPrefs: SystemCalendarPrefsStore? = null
}

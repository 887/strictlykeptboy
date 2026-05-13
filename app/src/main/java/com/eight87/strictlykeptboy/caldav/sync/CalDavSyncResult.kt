package com.eight87.strictlykeptboy.caldav.sync

/**
 * Phase Y — outcome of one CalDAV sync pass. Sealed so the scheduler
 * + UI can switch exhaustively (SOLID.O).
 */
sealed interface CalDavSyncResult {
    data class Pulled(val added: Int, val updated: Int, val deleted: Int) : CalDavSyncResult
    data class Pushed(val added: Int, val updated: Int, val deleted: Int) : CalDavSyncResult
    data class BiDirectional(val pulled: Pulled, val pushed: Pushed) : CalDavSyncResult
    data class Conflicted(val hrefs: List<String>) : CalDavSyncResult
    data class Failed(val reason: String) : CalDavSyncResult
    data object UpToDate : CalDavSyncResult
    data object RateLimited : CalDavSyncResult
}

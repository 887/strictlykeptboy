package com.eight87.strictlykeptboy.git

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope

/**
 * Network connectivity signal for the sync orchestrator. Phase B.8.
 *
 * Emits the current [NetworkState] and updates when the system signals a
 * capability change. The sync queue (Phase J) consumes this to decide whether
 * to attempt push/pull right now (online) vs. queue for later (offline) vs.
 * defer non-essential repos (metered + wifiOnly preference).
 *
 * NOT a fitness function — the actual outcome of a fetch / push is
 * authoritative. This is a fast-path hint to avoid the round-trip when the
 * radio is off.
 */
sealed interface NetworkState {
    object Offline : NetworkState
    data class Online(val metered: Boolean, val wifi: Boolean) : NetworkState
}

class NetworkMonitor(private val context: Context) {

    /**
     * Cold flow that subscribes to the system's `ConnectivityManager` callbacks
     * on collection. For a hot, lifecycle-scoped state, lift this into a
     * [stateIn] inside a CoroutineScope (see [stateIn] convenience below).
     */
    val networkState: Flow<NetworkState> = callbackFlow {
        val cm = context.getSystemService<ConnectivityManager>()
        if (cm == null) {
            trySend(NetworkState.Offline)
            close()
            return@callbackFlow
        }

        // Seed with current state before registering callbacks.
        trySend(currentState(cm))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(currentState(cm)) }
            override fun onLost(network: Network) { trySend(currentState(cm)) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(stateOf(caps))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /**
     * Convenience: hoist [networkState] into a lifecycle-scoped StateFlow.
     * Caller passes its own scope (typically `viewModelScope` or the sync
     * service's scope) so cancellation is correct.
     */
    fun stateIn(scope: CoroutineScope) = networkState.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = currentStateOrOffline(),
    )

    private fun currentStateOrOffline(): NetworkState {
        val cm = context.getSystemService<ConnectivityManager>() ?: return NetworkState.Offline
        return currentState(cm)
    }

    private fun currentState(cm: ConnectivityManager): NetworkState {
        val active = cm.activeNetwork ?: return NetworkState.Offline
        val caps = cm.getNetworkCapabilities(active) ?: return NetworkState.Offline
        return stateOf(caps)
    }

    private fun stateOf(caps: NetworkCapabilities): NetworkState {
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!hasInternet) return NetworkState.Offline
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return NetworkState.Online(metered = metered, wifi = wifi)
    }
}

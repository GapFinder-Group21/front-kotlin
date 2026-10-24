package co.gapfinder.mobile.foundation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "NetworkMonitor"


class NetworkMonitor(context: Context) : NetworkStatus {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val validated = ConcurrentHashMap.newKeySet<Network>()

    private val state = MutableStateFlow(currentlyOnline())
    override val online: StateFlow<Boolean> = state.asStateFlow()

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasInternet()) validated += network else validated -= network
            publish()
        }

        override fun onLost(network: Network) {
            validated -= network
            publish()
        }
    }

    /** Empieza a escuchar los cambios de red. Se llama una vez al abrir la app. */
    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        // Al registrarse, Android avisa de inmediato el estado de cada red que ya está conectada
        manager.registerNetworkCallback(request, callback)
        registered = true
    }

    fun stop() {
        if (!registered) return
        manager.unregisterNetworkCallback(callback)
        registered = false
        validated.clear()
    }

    private fun publish() {
        val now = validated.isNotEmpty()
        if (state.value != now) Log.d(TAG, if (now) "Online" else "Offline")
        state.value = now
    }

    // Estado inicial, antes de que lleguen los callbacks
    private fun currentlyOnline(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasInternet() == true

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

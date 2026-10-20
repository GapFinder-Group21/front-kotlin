package co.gapfinder.mobile.foundation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lo mínimo que mis pantallas necesitan saber de la red. El monitor completo
 * (ConnectivityManager.NetworkCallback + banner offline) es de Daniela: cuando esté en main
 * se conecta con [Connectivity.install] y [DeviceNetworkStatus] se puede borrar.
 */
interface NetworkStatus {
    /** true si hay una red con internet validada. */
    val online: StateFlow<Boolean>
}

/** Punto único donde las pantallas leen el estado de la red. */
object Connectivity {
    private var source: NetworkStatus? = null

    val online: StateFlow<Boolean>
        get() = source?.online ?: alwaysOnline

    // Antes de attach se asume que hay red, igual que se comportaba la app antes
    private val alwaysOnline = MutableStateFlow(true).asStateFlow()

    fun attach(context: Context) {
        if (source == null) source = DeviceNetworkStatus(context)
    }

    /** Reemplaza la fuente del estado de la red (por ejemplo, por el monitor de Daniela). */
    fun install(status: NetworkStatus) {
        source = status
    }

    fun isOnline(): Boolean = online.value
}

/** Estado de la red como State de Compose: la pantalla se recompone cuando cambia. */
@Composable
fun rememberOnline(): State<Boolean> = Connectivity.online.collectAsState()

/**
 * Implementación provisional con registerDefaultNetworkCallback, solo para que mis pantallas
 * funcionen mientras llega el monitor de Daniela.
 */
private class DeviceNetworkStatus(context: Context) : NetworkStatus {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val state = MutableStateFlow(currentlyOnline())
    override val online: StateFlow<Boolean> = state.asStateFlow()

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.hasInternet()
            }

            override fun onLost(network: Network) {
                state.value = false
            }
        })
    }

    private fun currentlyOnline(): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasInternet() == true

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

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
 * Lo mínimo que mis pantallas necesitan saber de la red. 
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
        if (source != null) return
        source = NetworkMonitor(context).also { it.start() }
    }
    
    fun install(status: NetworkStatus) {
        (source as? NetworkMonitor)?.stop()
        source = status
    }

    fun isOnline(): Boolean = online.value
}

/** Estado de la red como State de Compose: la pantalla se recompone cuando cambia. */
@Composable
fun rememberOnline(): State<Boolean> = Connectivity.online.collectAsState()


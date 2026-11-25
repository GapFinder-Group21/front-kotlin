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
    val online: StateFlow<Boolean>

    /**
     * Cuándo volvió la red por última vez (reloj del teléfono, epoch ms), o null si ahora no hay red o no se sabe.
     * Lo usa la BQ 17 para separar el tiempo que el estudiante estuvo sin red del tiempo que tardó la cola.
     */
    val onlineSince: Long? get() = null
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
    /** Cuándo volvió la red por última vez (epoch ms), o null si no se sabe (BQ 17). */
    fun onlineSince(): Long? = source?.onlineSince
}

/** Estado de la red como State de Compose: la pantalla se recompone cuando cambia. */
@Composable
fun rememberOnline(): State<Boolean> = Connectivity.online.collectAsState()


package co.gapfinder.mobile.foundation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AmbientLight"

/** Modo que sugiere la luz ambiental. SYSTEM = no hay lectura útil: se sigue el tema del sistema. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Lee el sensor de luz (Sensor.TYPE_LIGHT) y lo convierte en un [ThemeMode] (patrón Observer:
 * el sensor publica lecturas, este monitor las traduce y la interfaz se suscribe a [mode]).
 *
 * Mismas reglas que la versión Flutter (theme_controller.dart):
 * - Histéresis: oscuro por debajo de [DARK_BELOW_LUX] y claro por encima de [LIGHT_ABOVE_LUX].
 *   Entre los dos se queda en el modo actual, para que no parpadee.
 * - Sin sensor o con error: SYSTEM, y la app funciona normal.
 * - Lecturas negativas (o NaN) se ignoran.
 * - Solo publica cuando el modo cambia de verdad, no en cada lectura.
 *
 * Se escucha solo con la app en primer plano: HostActivity llama [start] en onResume y [stop] en onPause.
 */
object AmbientLightMonitor : SensorEventListener {
    const val DARK_BELOW_LUX = 10f
    const val LIGHT_ABOVE_LUX = 30f

    private var manager: SensorManager? = null
    private var sensor: Sensor? = null
    private var listening = false

    private val state = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = state.asStateFlow()

    /** true si el celular tiene sensor de luz. */
    val available: Boolean get() = sensor != null

    fun attach(context: Context) {
        if (manager != null) return
        manager = context.applicationContext.getSystemService(SensorManager::class.java)
        sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) Log.d(TAG, "No light sensor: following the system theme")
    }

    fun start() {
        val s = sensor ?: return
        if (listening) return
        listening = try {
            manager?.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL) == true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register the light sensor: $e")
            false
        }
        if (!listening) state.value = ThemeMode.SYSTEM
    }

    fun stop() {
        if (!listening) return
        manager?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val lux = event.values.firstOrNull() ?: return
        if (lux.isNaN() || lux < 0f) return // lectura inválida
        val next = decide(state.value, lux)
        if (next != state.value) {
            Log.d(TAG, "💡 $lux lux → $next")
            state.value = next
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Histéresis: entre los dos umbrales se mantiene el modo actual. */
    fun decide(current: ThemeMode, lux: Float): ThemeMode = when {
        lux < DARK_BELOW_LUX -> ThemeMode.DARK
        lux > LIGHT_ABOVE_LUX -> ThemeMode.LIGHT
        else -> current
    }
}

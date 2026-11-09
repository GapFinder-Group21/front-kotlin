package co.gapfinder.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import co.gapfinder.mobile.domain.MobilityPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferencia de movilidad guardada en el teléfono, una por usuario. Va en SharedPreferences
 * privadas de la app (es un par de valores, no hace falta una tabla de Room) y se borra al cerrar sesión.
 *
 * Es la fuente para filtrar: las pantallas leen de aquí, así que la preferencia funciona sin red.
 * [isPending] indica que hay un cambio que todavía no llegó al backend.
 */
object MobilityStore {
    private const val FILE = "mobility_preference"
    private const val NO_LIMIT = -1

    private lateinit var prefs: SharedPreferences

    // Sube cada vez que cambia la preferencia, para que las pantallas vuelvan a filtrar
    private val version = MutableStateFlow(0)
    val changes: StateFlow<Int> = version.asStateFlow()

    fun attach(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    fun read(memberId: Int): MobilityPreference {
        val meters = prefs.getInt(key(memberId, "maxWalkMeters"), NO_LIMIT)
        return MobilityPreference(
            maxWalkMeters = meters.takeIf { it != NO_LIMIT },
            groundFloorOnly = prefs.getBoolean(key(memberId, "groundFloorOnly"), false),
        )
    }

    /** Guarda la preferencia. [pending] = true si todavía falta mandarla al backend. */
    fun write(memberId: Int, preference: MobilityPreference, pending: Boolean) {
        prefs.edit()
            .putInt(key(memberId, "maxWalkMeters"), preference.maxWalkMeters ?: NO_LIMIT)
            .putBoolean(key(memberId, "groundFloorOnly"), preference.groundFloorOnly)
            .putBoolean(key(memberId, "pending"), pending)
            .apply()
        version.value++
    }

    fun isPending(memberId: Int): Boolean = prefs.getBoolean(key(memberId, "pending"), false)

    fun markSynced(memberId: Int) {
        prefs.edit().putBoolean(key(memberId, "pending"), false).apply()
    }

    /** Al cerrar sesión: la preferencia es privada y no debe quedar en el teléfono. */
    fun wipe() {
        prefs.edit().clear().apply()
        version.value++
    }

    private fun key(memberId: Int, field: String) = "$memberId.$field"
}

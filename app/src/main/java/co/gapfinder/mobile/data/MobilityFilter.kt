package co.gapfinder.mobile.data

import android.location.Location
import android.util.Log
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.domain.CampusSpot
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.domain.MobilityPreference
import co.gapfinder.mobile.foundation.Connectivity
import kotlinx.coroutines.CancellationException

private const val TAG = "MobilityFilter"
private const val FIX_TIMEOUT_MS = 5_000L

/** Primer piso: con el piso desconocido no se descarta la mesa (el backend también filtra). */
private const val GROUND_FLOOR = 1

/**
 * Lo necesario para aplicar la preferencia a una lista: la preferencia, dónde está el
 * estudiante y las coordenadas de los edificios (las mesas guardadas en Room no las traen).
 */
class MobilityScope(
    val preference: MobilityPreference,
    private val here: Location?,
    private val spots: Map<Int, CampusSpot>,
) {
    /** true si la distancia no se puede medir (sin GPS), para avisarlo en la pantalla. */
    val distanceUnknown: Boolean get() = preference.maxWalkMeters != null && here == null

    /** Metros en línea recta hasta el edificio de la mesa, o null si no se puede calcular. */
    fun distanceTo(hangout: Hangout): Int? {
        val origin = here ?: return null
        val spot = hangout.spot?.takeIf { it.hasCoordinates() } ?: hangout.spot?.id?.let { spots[it] } ?: return null
        if (!spot.hasCoordinates()) return null
        val result = FloatArray(1)
        Location.distanceBetween(origin.latitude, origin.longitude, spot.latitude, spot.longitude, result)
        return result[0].toInt()
    }

    fun allows(hangout: Hangout): Boolean {
        if (preference.groundFloorOnly && (hangout.floor ?: GROUND_FLOOR) > GROUND_FLOOR) return false
        val max = preference.maxWalkMeters ?: return true
        // Sin ubicación o sin coordenadas no se puede medir: no se esconde
        val meters = distanceTo(hangout) ?: return true
        return meters <= max
    }

    fun keep(hangouts: List<Hangout>): List<Hangout> = if (preference.isActive) hangouts.filter(::allows) else hangouts

    private fun CampusSpot.hasCoordinates() = latitude != 0.0 || longitude != 0.0

    companion object {
        val Open = MobilityScope(MobilityPreference.None, null, emptyMap())
    }
}

/** Arma el [MobilityScope] del usuario. Funciona sin red: preferencia del teléfono y edificios del caché o de Room. */
object MobilityFilter {
    suspend fun prepare(memberId: Int): MobilityScope {
        val preference = MobilityRepository.current(memberId)
        // Para "solo primer piso" no hace falta ubicación
        if (preference.maxWalkMeters == null) return MobilityScope(preference, null, emptyMap())
        return MobilityScope(preference, locate(), spotIndex())
    }

    private suspend fun locate(): Location? = try {
        if (GeoProbe.isReady()) GeoProbe.currentFix(FIX_TIMEOUT_MS) else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d(TAG, "No location for the distance filter: $e")
        null
    }

    /** Edificios por id: caché en memoria → red → copia en Room. */
    private suspend fun spotIndex(): Map<Int, CampusSpot> {
        val list = SpotRepository.cached() ?: fetchSpots() ?: LocalStore.spots()?.items ?: emptyList()
        return list.associateBy { it.id }
    }

    private suspend fun fetchSpots(): List<CampusSpot>? {
        if (!Connectivity.isOnline()) return null
        return try {
            SpotRepository.all()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}

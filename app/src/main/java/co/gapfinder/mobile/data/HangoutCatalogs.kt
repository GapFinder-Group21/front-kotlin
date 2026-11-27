package co.gapfinder.mobile.data

import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.domain.CampusSpot
import co.gapfinder.mobile.domain.Pastime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Estrategia: local storage + eventual connectivity. Actividades y edificios del wizard de Open Tables.
 * Se bajan en paralelo y se guardan en Room, así el wizard se puede llenar (y la mesa encolar) sin red,
 * aunque el estudiante nunca lo haya abierto con conexión.
 */
object HangoutCatalogs {
    suspend fun refresh(): Pair<List<Pastime>, List<CampusSpot>> = coroutineScope {
        val pastimes = async { PastimeRepository.catalog() }
        val spots = async { SpotRepository.all() }
        (pastimes.await() to spots.await()).also { (fresh, places) ->
            LocalStore.savePastimes(fresh)
            LocalStore.saveSpots(places)
        }
    }
}

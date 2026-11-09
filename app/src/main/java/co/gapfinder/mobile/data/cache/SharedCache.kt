package co.gapfinder.mobile.data.cache

import co.gapfinder.mobile.domain.CampusSpot
import co.gapfinder.mobile.domain.Hobby
import co.gapfinder.mobile.domain.Recommendation
import java.time.Duration

/**
 * Cachés en memoria de lo compartido y temporal: edificios, intereses y recomendaciones.
 * Lo del usuario (horario, gaps, mis open tables) va en Room; esto no se repite allá.
 */
object SharedCache {
    /** Clave única para los catálogos que se piden completos. */
    const val ALL = "all"

    /** Edificios del campus: casi no cambian. */
    val spots = TtlCache<String, List<CampusSpot>>("spots", maxEntries = 1, ttl = Duration.ofHours(12))

    /** Lista de intereses. */
    val hobbies = TtlCache<String, List<Hobby>>("hobbies", maxEntries = 1, ttl = Duration.ofHours(6))

    /** Recomendaciones de open tables por usuario: cambian rápido (se crean y se llenan mesas). */
    val recommendations = TtlCache<Int, Recommendation>("recommendations", maxEntries = 8, ttl = Duration.ofMinutes(3))

    /** Al cerrar sesión no debe quedar nada del usuario anterior. */
    fun clear() {
        spots.clear()
        hobbies.clear()
        recommendations.clear()
    }
}

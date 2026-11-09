package co.gapfinder.mobile.domain

import co.gapfinder.mobile.foundation.flagOr
import co.gapfinder.mobile.foundation.intOrNull
import co.gapfinder.mobile.foundation.jsonOf
import org.json.JSONObject

/**
 * Preferencia de movilidad del estudiante (BQ 14). Es privada y opcional: solo se usa para
 * filtrar los lugares que se le sugieren, y sin preferencia no se filtra nada.
 */
data class MobilityPreference(
    /** Distancia máxima que quiere caminar, en metros. null = sin límite. */
    val maxWalkMeters: Int? = null,
    /** Solo lugares en primer piso. */
    val groundFloorOnly: Boolean = false,
) {
    /** true si filtra algo. */
    val isActive: Boolean get() = maxWalkMeters != null || groundFloorOnly

    fun encode(): JSONObject = jsonOf(
        "maxWalkMeters" to maxWalkMeters,
        "groundFloorOnly" to groundFloorOnly,
    )

    companion object {
        val None = MobilityPreference()

        fun decode(j: JSONObject) = MobilityPreference(
            maxWalkMeters = j.intOrNull("maxWalkMeters"),
            groundFloorOnly = j.flagOr("groundFloorOnly"),
        )
    }
}

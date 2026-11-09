package co.gapfinder.mobile.data

import android.util.Log
import co.gapfinder.mobile.data.outbox.Outbox
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.jsonOf
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SuggestionLog"

/**
 * Registro de la BQ 14: cuándo se le sugiere un lugar (open table) al estudiante y si lo acepta
 * (se une a la mesa). Cada evento va por la cola de acciones: con red sale enseguida y sin red
 * cuando vuelve la conexión. occurredAt guarda cuándo pasó, no cuándo se envió.
 */
object SuggestionLog {
    /** Endpoint del backend. Si allá queda con otra ruta, se cambia solo aquí. */
    const val PATH = "/mobility-suggestions"

    const val SUGGESTED = "SUGGESTED"
    const val ACCEPTED = "ACCEPTED"

    // Mesas ya sugeridas en esta sesión (id → usuario), para no registrar la misma dos veces
    // y para saber si una mesa a la que se une el estudiante venía de una sugerencia
    private val suggested = ConcurrentHashMap<Int, Int>()
    private val accepted = ConcurrentHashMap.newKeySet<Int>()

    /** Las mesas que se le están mostrando como recomendadas (ya filtradas por la preferencia). */
    suspend fun suggested(memberId: Int, hangouts: List<Hangout>, mobility: MobilityScope) {
        for (table in hangouts) {
            if (suggested.putIfAbsent(table.id, memberId) != null) continue
            record(memberId, table, SUGGESTED, mobility)
        }
    }

    /** El estudiante se unió a la mesa. Solo cuenta si se la habíamos sugerido. */
    suspend fun accepted(memberId: Int, table: Hangout, mobility: MobilityScope) {
        if (suggested[table.id] != memberId || !accepted.add(table.id)) return
        record(memberId, table, ACCEPTED, mobility)
    }

    /** true si la mesa se le sugirió en esta sesión. */
    fun wasSuggested(tableId: Int): Boolean = suggested.containsKey(tableId)

    /** Al cerrar sesión. */
    fun reset() {
        suggested.clear()
        accepted.clear()
    }

    private suspend fun record(memberId: Int, table: Hangout, event: String, mobility: MobilityScope) {
        val preference = mobility.preference
        Outbox.enqueueRequest(
            "POST",
            PATH,
            jsonOf(
                "userId" to memberId,
                "openTableId" to table.id,
                "buildingId" to table.spot?.id,
                "event" to event,
                "distanceMeters" to mobility.distanceTo(table),
                "maxWalkMeters" to preference.maxWalkMeters,
                "groundFloorOnly" to preference.groundFloorOnly,
                "occurredAt" to Chrono.writeMoment(LocalDateTime.now()),
            ),
        )
        Log.d(TAG, "📍 $event table=${table.id}")
    }
}

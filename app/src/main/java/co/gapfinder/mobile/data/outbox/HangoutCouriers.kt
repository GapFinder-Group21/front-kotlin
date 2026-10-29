package co.gapfinder.mobile.data.outbox

import co.gapfinder.mobile.data.AttendeeRepository
import co.gapfinder.mobile.data.DropOffRepository
import co.gapfinder.mobile.data.HangoutRepository
import co.gapfinder.mobile.domain.HangoutDropOff
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.jsonOf
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime

/** Acciones de open tables que pueden quedar en la cola: crear, unirse y reportar el abandono del wizard. */
object HangoutCouriers {
    const val CREATE = "hangout.create"
    const val JOIN = "hangout.join"
    const val DROP_OFF = "hangout.dropoff"

    internal fun registerAll() {
        Outbox.register(CREATE, CreateCourier)
        Outbox.register(JOIN, JoinCourier)
        Outbox.register(DROP_OFF) { payload -> HttpGateway.send(DropOffRepository.PATH, payload).toDelivery() }
    }

    // ============================================================ Encolar

    /**
     * Encola la creación de una mesa hecha sin red. La foto pasa a ser de la cola: se borra
     * cuando la mesa se publica o se descarta. La hora de inicio se pone al enviarla.
     */
    suspend fun enqueueCreate(hostId: Int, spotId: Int, body: JSONObject, durationMinutes: Int, photo: File) {
        Outbox.enqueue(
            CREATE,
            jsonOf(
                "hostId" to hostId,
                "spotId" to spotId,
                "body" to body,
                "durationMinutes" to durationMinutes,
                "photoPath" to photo.absolutePath,
                "queuedAt" to Chrono.writeMoment(LocalDateTime.now()),
            ),
            memberId = hostId,
        )
    }

    suspend fun enqueueJoin(hangoutId: Int, memberId: Int) {
        Outbox.enqueue(JOIN, jsonOf("hangoutId" to hangoutId, "memberId" to memberId), memberId = memberId)
    }

    suspend fun enqueueDropOff(record: HangoutDropOff) {
        Outbox.enqueue(DROP_OFF, record.encode(), memberId = record.memberId)
    }

    // ============================================================ Enviar

    private object CreateCourier : Courier {
        override suspend fun deliver(payload: JSONObject): Delivery {
            val minutes = payload.getInt("durationMinutes").toLong()
            val queuedAt = Chrono.readMoment(payload.getString("queuedAt"))
            val now = LocalDateTime.now()

            // La mesa era para "ahora": si su duración ya pasó mientras no había red, ya no tiene sentido publicarla
            if (queuedAt.plusMinutes(minutes).isBefore(now)) return Delivery.Rejected("The table expired while offline")

            val photo = File(payload.getString("photoPath"))
            if (!photo.exists()) return Delivery.Rejected("Photo not found")

            // Empieza cuando se publica, para que el backend no la rechace por tener el fin en el pasado
            val body = payload.getJSONObject("body")
                .put("startTime", Chrono.writeMoment(now))
                .put("endTime", Chrono.writeMoment(now.plusMinutes(minutes)))
            val reply = HttpGateway.sendForm("/open-tables", HangoutRepository.hostForm(body, photo))
            val delivery = reply.toDelivery()

            if (delivery == Delivery.Sent) {
                photo.delete()
                // El creador también queda como participante; va por la cola para no perderlo si la red se cae justo aquí
                enqueueJoin(reply.json.getInt("id"), payload.getInt("hostId"))
                tell("Your Open Table was published")
            }
            return delivery
        }

        override suspend fun discarded(payload: JSONObject) {
            File(payload.getString("photoPath")).delete()
            tell("Your offline Open Table could not be published", failed = true)
        }
    }

    private object JoinCourier : Courier {
        override suspend fun deliver(payload: JSONObject): Delivery =
            HttpGateway.send(AttendeeRepository.joinPath(payload.getInt("hangoutId"), payload.getInt("memberId")))
                .toDelivery()

        override suspend fun discarded(payload: JSONObject) {
            tell("Could not join the table you chose while offline", failed = true)
        }
    }

    /** Aviso en la app (si está abierta) cuando una acción de la cola termina. */
    private suspend fun tell(text: String, failed: Boolean = false) = withContext(Dispatchers.Main) {
        if (failed) Toaster.show(text) else Toaster.show(text, background = Palette.Teal)
    }
}

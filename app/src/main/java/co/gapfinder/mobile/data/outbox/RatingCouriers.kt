package co.gapfinder.mobile.data.outbox

import co.gapfinder.mobile.data.Caches
import co.gapfinder.mobile.data.MatchHistoryRepository
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.jsonOf
import co.gapfinder.mobile.foundation.textOrNull
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Calificaciones de matches hechas sin red que esperan en la cola. */
object RatingCouriers {
    const val RATE = "match.rate"

    internal fun registerAll() {
        Outbox.register(RATE, RateCourier)
    }

    /** Encola la calificación. La pantalla ya la mostró como "pendiente" con [LocalStore.markRatingPending]. */
    suspend fun enqueueRate(matchId: Int, memberId: Int, stars: Int, comment: String?) {
        Outbox.enqueue(
            RATE,
            jsonOf("matchId" to matchId, "memberId" to memberId, "stars" to stars, "comment" to comment),
            memberId = memberId,
        )
    }

    private object RateCourier : Courier {
        override suspend fun deliver(payload: JSONObject): Delivery {
            val matchId = payload.getInt("matchId")
            val reply = HttpGateway.send(
                MatchHistoryRepository.ratingPath(matchId),
                MatchHistoryRepository.ratingBody(payload.getInt("stars"), payload.textOrNull("comment")),
            )
            // 409: el backend ya tenía mi calificación de este match, o sea que ya está hecho
            val delivery = if (reply.code == 409) Delivery.Sent else reply.toDelivery()
            if (delivery == Delivery.Sent) {
                LocalStore.settleRating(payload.getInt("memberId"), matchId, accepted = true)
                Caches.matchHistory.clear()
                tell("Your match rating was sent")
            }
            return delivery
        }

        // Rechazada (match no terminado, no participante, datos inválidos) o sin más intentos: vuelve a "por calificar"
        override suspend fun discarded(payload: JSONObject) {
            LocalStore.settleRating(payload.getInt("memberId"), payload.getInt("matchId"), accepted = false)
            Caches.matchHistory.clear()
            tell("Your offline rating could not be sent. You can rate the match again", failed = true)
        }
    }

    /** Aviso en la app (si está abierta) cuando una acción de la cola termina. */
    private suspend fun tell(text: String, failed: Boolean = false) = withContext(Dispatchers.Main) {
        if (failed) Toaster.show(text) else Toaster.show(text, background = Palette.Teal)
    }
}

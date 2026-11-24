package co.gapfinder.mobile.data.outbox

import co.gapfinder.mobile.data.Caches
import co.gapfinder.mobile.data.ChatRepository
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.domain.ChatMessage
import co.gapfinder.mobile.foundation.jsonOf
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

/**
 * Estrategia: eventual connectivity. Mensajes del chat que esperan en la cola (outbox) porque se escribieron
 * sin red o porque la red falló a mitad del envío. OutboxWorker los manda en orden cuando vuelve la conexión.
 * Siempre van con el mismo clientId, así que reenviar un mensaje nunca lo duplica.
 */
object ChatCouriers {
    const val SEND = "chat.send"

    internal fun registerAll() {
        Outbox.register(SEND, SendCourier)
    }

    /** Encola el mensaje. Ya está guardado en Room como PENDING, así que en el chat se ve con el reloj. */
    suspend fun enqueueSend(memberId: Int, message: ChatMessage) {
        Outbox.enqueue(
            SEND,
            jsonOf("clientId" to message.clientId, "receiverId" to message.receiverId, "content" to message.content),
            memberId = memberId,
        )
    }

    /**
     * Manda el mensaje y traduce la respuesta a [Delivery]. No toca Room: lo usan la pantalla (con red)
     * y el courier de la cola, y cada uno guarda el resultado.
     * - 2xx → Sent. 409 → también Sent: el backend ya tenía ese clientId guardado.
     * - Otros 4xx (400 mensaje inválido, 422 ya no son amigos) → Rejected con el motivo para mostrar.
     * - Sin red (IOException), 5xx, 401/403/408/429 → Retry (lo decide toDelivery).
     */
    suspend fun post(receiverId: Int, content: String, clientId: String): Delivery {
        val reply = try {
            ChatRepository.send(receiverId, content, clientId)
        } catch (e: IOException) {
            return Delivery.Retry(e.toString())
        }
        if (reply.code == 409) return Delivery.Sent
        return when (val delivery = reply.toDelivery()) {
            is Delivery.Rejected -> Delivery.Rejected(refusalText(reply.code))
            else -> delivery
        }
    }

    private object SendCourier : Courier {
        override suspend fun deliver(payload: JSONObject): Delivery {
            val clientId = payload.getString("clientId")
            val friendId = payload.getInt("receiverId")
            val delivery = post(friendId, payload.getString("content"), clientId)
            when (delivery) {
                Delivery.Sent -> {
                    LocalStore.markChatSent(clientId)
                    tell("Your message was sent")
                }
                // Queda FAILED con su motivo; discarded solo confirma que salió de la cola
                is Delivery.Rejected -> LocalStore.markChatFailed(clientId, delivery.reason)
                is Delivery.Retry -> Unit
            }
            // El chat en memoria ya no está al día: al volver a abrirlo se lee de Room
            if (delivery !is Delivery.Retry) Caches.chats.remove(friendId)
            return delivery
        }

        // Rechazado o sin más intentos: el mensaje queda FAILED y en el chat aparece "Retry"
        override suspend fun discarded(payload: JSONObject) {
            LocalStore.markChatFailed(payload.getString("clientId"), reason = null)
            Caches.chats.remove(payload.getInt("receiverId"))
            tell("A message couldn't be sent. Open the chat and tap Retry", failed = true)
        }
    }

    /** Motivo que se muestra debajo del mensaje fallido (el backend lo explica en español). */
    private fun refusalText(code: Int): String = when (code) {
        422 -> "You can only message friends"
        400 -> "This message can't be sent (empty or longer than ${ChatMessage.MAX_LENGTH} characters)"
        else -> "Couldn't send this message"
    }

    /** Aviso en la app (si está abierta) cuando un mensaje de la cola termina. Toaster es estado de Compose: va en Main. */
    private suspend fun tell(text: String, failed: Boolean = false) = withContext(Dispatchers.Main) {
        if (failed) Toaster.show(text) else Toaster.show(text, background = Palette.Teal)
    }
}

package co.gapfinder.mobile.data

import co.gapfinder.mobile.domain.ChatMessage
import co.gapfinder.mobile.foundation.isBlankPayload
import co.gapfinder.mobile.foundation.mapNodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Resultado de procesar un GET del chat. */
data class ChatPage(
    /** Toda la conversación ya unida, sin repetidos y en orden: es lo que se muestra. */
    val messages: List<ChatMessage>,
    /** Lo que llegó del backend en este GET: es lo que se guarda en Room. */
    val arrived: List<ChatMessage>,
)

// Por fecha; si empatan, primero los que ya tienen id del backend y luego por clientId, para que el orden no salte
private val CHAT_ORDER = compareBy<ChatMessage>({ it.createdAt }, { it.serverId ?: Int.MAX_VALUE }, { it.clientId })

/**
 * Une dos listas de mensajes, deduplica por clientId y ordena por createdAt.
 * Si un clientId está en las dos, gana el de [newer] (lo que llegó del backend, o el mensaje recién cambiado):
 * así un PENDING que el backend ya tiene pasa a SENT sin quedar repetido.
 * Es una función pura: no toca la red, Room ni la pantalla, así se puede probar sola (ChatProcessingTest).
 */
fun mergeChat(local: List<ChatMessage>, newer: List<ChatMessage>): List<ChatMessage> {
    val byClientId = LinkedHashMap<String, ChatMessage>()
    local.forEach { byClientId[it.clientId] = it }
    newer.forEach { byClientId[it.clientId] = it }
    return byClientId.values.sortedWith(CHAT_ORDER)
}

/**
 * El afterId del siguiente GET: el id más alto que ya llegó por GET.
 * El POST no le pone serverId al mensaje a propósito: si el amigo escribió justo antes que yo, su mensaje
 * tiene un id menor que el mío y, con el id del POST como afterId, ese mensaje no llegaría nunca.
 */
fun lastServerId(messages: List<ChatMessage>): Int = messages.maxOfOrNull { it.serverId ?: 0 } ?: 0

/** Convierte el JSON del GET (una lista de ChatMessageDTO) en mensajes. */
fun parseChatPage(json: String): List<ChatMessage> =
    if (json.isBlankPayload()) emptyList() else JSONArray(json).mapNodes(ChatMessage::decode)

/**
 * Estrategia: multithreading. Parsear el JSON, unirlo con lo guardado, deduplicar y ordenar es trabajo de CPU,
 * así que va en Dispatchers.Default y el hilo principal queda libre para la pantalla.
 * (La red ya va en Dispatchers.IO dentro de HttpGateway.)
 */
suspend fun processChatPage(saved: List<ChatMessage>, json: String): ChatPage =
    withContext(Dispatchers.Default) {
        val arrived = parseChatPage(json)
        ChatPage(messages = mergeChat(saved, arrived), arrived = arrived)
    }

/** Estrategia: multithreading. Un cambio suelto (mensaje nuevo, enviado o fallido) también se une fuera del hilo principal. */
suspend fun mergeChatOffMain(current: List<ChatMessage>, changed: ChatMessage): List<ChatMessage> =
    withContext(Dispatchers.Default) { mergeChat(current, listOf(changed)) }

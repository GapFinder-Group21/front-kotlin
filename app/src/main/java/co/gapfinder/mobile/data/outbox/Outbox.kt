package co.gapfinder.mobile.data.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.data.local.PendingActionEntity
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.Reply
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.jsonOf
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Resultado de intentar mandar una acción pendiente. */
sealed interface Delivery {
    data object Sent : Delivery

    /** Vale la pena reintentar: sin red, servidor caído, sesión por refrescar o 408/429/5xx. */
    data class Retry(val reason: String) : Delivery

    /** El backend la rechazó (resto de 4xx): reenviarla no cambia nada, así que se descarta. */
    data class Rejected(val reason: String) : Delivery
}

/** Quien sabe mandar al backend un tipo de acción pendiente. */
fun interface Courier {
    suspend fun deliver(payload: JSONObject): Delivery

    /** Se llama cuando la acción sale de la cola sin enviarse (rechazada o sin más intentos), para limpiar archivos. */
    suspend fun discarded(payload: JSONObject) {}
}

/** Traduce el código HTTP de la respuesta a un [Delivery]. */
fun Reply.toDelivery(): Delivery = when {
    code in 200..299 -> Delivery.Sent
    code == 401 || code == 403 || code == 408 || code == 429 || code >= 500 -> Delivery.Retry("HTTP $code")
    else -> Delivery.Rejected("HTTP $code ${text.take(200)}")
}

/**
 * Cola de acciones pendientes (outbox). Lo que el usuario hace sin red se guarda en la tabla
 * pending_actions de Room y [OutboxWorker] lo manda en orden cuando vuelve la conexión.
 *
 * Para encolar algo propio hay dos formas:
 * - Una petición HTTP cualquiera: `Outbox.enqueueRequest("POST", "/ruta", json)`.
 * - Un tipo con lógica propia: `Outbox.register("mi.tipo") { payload -> ...; reply.toDelivery() }`
 *   una vez al iniciar la app, y luego `Outbox.enqueue("mi.tipo", payload)`.
 */
object Outbox {
    private const val WORK_NAME = "outbox"
    private const val BACKOFF_SECONDS = 30L

    /** Petición HTTP genérica con el token del usuario (method, path y body opcional). */
    const val TYPE_REQUEST = "http.request"

    private val couriers = ConcurrentHashMap<String, Courier>()
    private lateinit var appContext: Context

    fun attach(context: Context) {
        appContext = context.applicationContext
        register(TYPE_REQUEST, Courier(::deliverRequest))
        HangoutCouriers.registerAll()
        // Por si quedaron acciones de antes de cerrar la app
        flush()
    }

    fun register(type: String, courier: Courier) {
        couriers[type] = courier
    }

    internal fun courierFor(type: String): Courier? = couriers[type]

    /** Encola la acción a nombre del usuario con sesión activa. */
    suspend fun enqueue(type: String, payload: JSONObject): Long = enqueue(type, payload, SessionVault.memberId())

    /** Guarda la acción y pide al worker que la mande apenas haya red. Devuelve el id de la fila. */
    suspend fun enqueue(type: String, payload: JSONObject, memberId: Int?): Long {
        val id = LocalStore.pendingActions().add(
            PendingActionEntity(
                memberId = memberId,
                type = type,
                payload = payload.toString(),
                createdAt = LocalDateTime.now(),
            )
        )
        flush()
        return id
    }

    /** Encola una petición HTTP autenticada (POST, PUT, PATCH o DELETE). */
    suspend fun enqueueRequest(method: String, path: String, body: JSONObject? = null): Long =
        enqueue(TYPE_REQUEST, jsonOf("method" to method.uppercase(), "path" to path, "body" to body))

    /** Cantidad de acciones esperando en la cola (para mostrarlo en la interfaz). */
    suspend fun pendingCount(): Int = LocalStore.pendingActions().count()

    /**
     * Programa [OutboxWorker] con la restricción de red. APPEND_OR_REPLACE encadena un worker
     * nuevo detrás del que esté corriendo, así una acción que llega a mitad de un envío no se queda esperando.
     */
    fun flush() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private suspend fun deliverRequest(payload: JSONObject): Delivery {
        val path = payload.getString("path")
        val body = payload.optJSONObject("body")
        val reply = when (payload.getString("method")) {
            "POST" -> HttpGateway.send(path, body)
            "PUT" -> HttpGateway.overwrite(path, body)
            "PATCH" -> HttpGateway.tweak(path, body)
            "DELETE" -> HttpGateway.erase(path)
            else -> return Delivery.Rejected("Unsupported method ${payload.getString("method")}")
        }
        return reply.toDelivery()
    }
}

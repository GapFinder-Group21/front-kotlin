package co.gapfinder.mobile.data

import android.util.Log
import co.gapfinder.mobile.data.outbox.Delivery
import co.gapfinder.mobile.data.outbox.Outbox
import co.gapfinder.mobile.data.outbox.toDelivery
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.jsonOf
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val TAG = "ChatDeliveryReport"

/**
 * Cómo llegó un mensaje del chat al servidor (BQ 17, Type 1).
 * [wire] es el valor que se manda al backend; tiene que coincidir con ChatDeliveryRouteEnum.
 */
enum class DeliveryRoute(val wire: String) {
    /** Salió apenas se tocó Send y el primer intento funcionó. */
    Direct("DIRECT"),

    /** Esperó en la cola (sin red, o el primer intento falló) y la cola lo mandó después. */
    Queued("QUEUED"),
}

/**
 * Lo que se mide de un mensaje. [elapsedMs]: desde el tap en Send hasta que el servidor respondió que lo guardó.
 * [afterReconnectMs]: solo para Queued, la parte de esa espera desde que volvió la red (null si no se sabe).
 */
data class DeliveryMeasure(val route: DeliveryRoute, val elapsedMs: Long, val afterReconnectMs: Long?)

/**
 * BQ 17 (Type 1): ¿cuánto tarda un mensaje del chat desde que se toca Send hasta que el backend lo guarda, cuando sale
 * de una vez y cuando espera en la cola sin conexión?
 *
 * La app mide con el reloj del teléfono: el tap y la respuesta del servidor los ve el mismo reloj, así que no importa
 * si el reloj del teléfono y el del servidor no coinciden. La medida se manda después de que el mensaje quedó guardado,
 * con el mismo patrón de CalendarImportReport: directo si hay red y, si falla, a la cola (Outbox).
 */
object ChatDeliveryReport {
    /** Igual que el backend: más de 7 días no es creíble (la cola se rinde mucho antes, a los 8 intentos). */
    const val MAX_ELAPSED_MS = 7L * 24 * 60 * 60 * 1000

    /** Endpoint del backend para la BQ 17. Si en el backend queda con otra ruta, se cambia solo aquí. */
    fun path(memberId: Int) = "/chat-delivery-reports/user/$memberId"

    /**
     * Calcula la medida, o null si no sirve: negativa (el reloj del teléfono cambió en medio) o de más de 7 días.
     * Para Queued, el tiempo después de volver la red cuenta desde lo último entre el tap y [onlineSince]: si el
     * mensaje se escribió con red y lo que falló fue el servidor, es toda la espera. Para Direct no aplica (null).
     */
    fun measure(route: DeliveryRoute, tappedAt: Long, savedAt: Long, onlineSince: Long?): DeliveryMeasure? {
        val elapsed = savedAt - tappedAt
        if (elapsed < 0 || elapsed > MAX_ELAPSED_MS) return null
        val afterReconnect = when {
            route == DeliveryRoute.Direct -> null
            onlineSince == null -> null
            else -> (savedAt - maxOf(tappedAt, onlineSince)).coerceIn(0L, elapsed)
        }
        return DeliveryMeasure(route, elapsed, afterReconnect)
    }

    /**
     * Manda la medida de un mensaje que el servidor ya guardó ([savedAt] = cuándo respondió). Nunca lanza: si algo
     * falla, el chat sigue igual y solo se pierde la medida.
     */
    suspend fun report(
        memberId: Int,
        clientId: String,
        route: DeliveryRoute,
        tappedAt: Long,
        savedAt: Long,
    ) {
        val measure = measure(route, tappedAt, savedAt, Connectivity.onlineSince())
        if (measure == null) {
            Log.w(TAG, "Measure ignored: ${savedAt - tappedAt} ms (did the phone clock change?)")
            return
        }
        val tapMoment = LocalDateTime.ofInstant(Instant.ofEpochMilli(tappedAt), ZoneId.systemDefault())
        val body = jsonOf(
            "clientId" to clientId,
            "route" to measure.route.wire,
            "elapsedMs" to measure.elapsedMs,
            "afterReconnectMs" to measure.afterReconnectMs,
            "tappedAt" to Chrono.writeMoment(tapMoment),
        )
        Log.d(TAG, "📊 ${measure.route.wire} in ${measure.elapsedMs} ms (after reconnect: ${measure.afterReconnectMs ?: "-"})")

        try {
            val delivery = if (!Connectivity.isOnline()) {
                Delivery.Retry("offline")
            } else {
                try {
                    HttpGateway.send(path(memberId), body).toDelivery()
                } catch (e: IOException) {
                    Delivery.Retry(e.toString())
                }
            }
            when (delivery) {
                Delivery.Sent -> Log.d(TAG, "✅ Report sent")
                // Estrategia: eventual connectivity. La medida no se pierde: sale con la cola cuando vuelva la red
                is Delivery.Retry -> {
                    Outbox.enqueueRequest("POST", path(memberId), body)
                    Log.d(TAG, "📥 Report queued (${delivery.reason})")
                }
                // 409: ya estaba reportado. 422: el backend no tiene ese mensaje. Reenviarlo no cambia nada
                is Delivery.Rejected -> Log.w(TAG, "Report rejected: ${delivery.reason}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Report failed: $e")
        }
    }
}

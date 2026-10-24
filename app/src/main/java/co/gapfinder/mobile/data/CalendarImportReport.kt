package co.gapfinder.mobile.data

import android.util.Log
import co.gapfinder.mobile.data.outbox.Delivery
import co.gapfinder.mobile.data.outbox.Outbox
import co.gapfinder.mobile.data.outbox.toDelivery
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.jsonOf
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDateTime

private const val TAG = "CalendarImportReport"

/**
 * Tipo de error de la importación de Google Calendar (BQ 1, Type 1).
 * [wire] es el valor que se manda al backend; tiene que coincidir con el enum del endpoint.
 */
enum class ImportFailureKind(val wire: String) {
    /** Sin red o el servidor no se alcanzó. */
    Network("NETWORK"),

    /** El servidor tardó más que el timeout de HttpGateway. */
    Timeout("TIMEOUT"),

    /** 401 / 403: la sesión o el permiso de Google ya no sirve. */
    Auth("AUTH"),

    /** Otro 4xx: el backend o Google rechazaron la solicitud. */
    Client("CLIENT_ERROR"),

    /** 5xx: falló el backend o Google. */
    Server("SERVER_ERROR"),

    /** Cualquier otra cosa (respuesta rara, error al leerla, ...). */
    Unknown("UNKNOWN"),
}

/** Reporta al backend por qué falló la importación de Google, o lo encola si no hay red. */
object CalendarImportReport {
    /** Endpoint del backend para la BQ 1. Si en el backend queda con otra ruta, se cambia solo aquí. */
    const val PATH = "/calendar-import-failures"

    /** Tipo de error y código HTTP (si hubo respuesta). */
    fun classify(error: Throwable): Pair<ImportFailureKind, Int?> = when (error) {
        is CalendarImportFault -> when (error.status) {
            401, 403 -> ImportFailureKind.Auth
            in 400..499 -> ImportFailureKind.Client
            in 500..599 -> ImportFailureKind.Server
            else -> ImportFailureKind.Unknown
        } to error.status
        // SocketTimeoutException es un IOException: va primero para no contarlo como falta de red
        is SocketTimeoutException -> ImportFailureKind.Timeout to null
        is IOException -> ImportFailureKind.Network to null
        else -> ImportFailureKind.Unknown to null
    }

    /**
     * Manda el reporte. Con red se intenta directo; sin red, o si el envío falla por la red o el
     * servidor, va a la cola de acciones (Outbox) y sale cuando vuelva la conexión. occurredAt
     * guarda cuándo pasó, no cuándo se envió.
     */
    suspend fun report(error: Throwable) {
        val me = SessionVault.memberId() ?: return
        val (kind, status) = classify(error)
        val body = jsonOf(
            "userId" to me,
            "errorType" to kind.wire,
            "httpStatus" to status,
            "detail" to ((error as? CalendarImportFault)?.detail ?: error.message ?: error.javaClass.simpleName).take(300),
            "occurredAt" to Chrono.writeMoment(LocalDateTime.now()),
        )
        Log.d(TAG, "📊 Import failure: ${kind.wire} (HTTP ${status ?: "-"})")

        if (!Connectivity.isOnline()) {
            enqueue(body, "offline")
            return
        }
        val delivery = try {
            HttpGateway.send(PATH, body).toDelivery()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Delivery.Retry(e.toString())
        }
        when (delivery) {
            Delivery.Sent -> Log.d(TAG, "✅ Report sent")
            is Delivery.Retry -> enqueue(body, delivery.reason)
            is Delivery.Rejected -> Log.w(TAG, "Report rejected: ${delivery.reason}")
        }
    }

    private suspend fun enqueue(body: JSONObject, why: String) {
        Outbox.enqueueRequest("POST", PATH, body)
        Log.d(TAG, "📥 Report queued ($why)")
    }
}

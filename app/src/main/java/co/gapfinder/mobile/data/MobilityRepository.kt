package co.gapfinder.mobile.data

import android.util.Log
import co.gapfinder.mobile.data.local.MobilityStore
import co.gapfinder.mobile.data.outbox.Delivery
import co.gapfinder.mobile.data.outbox.Outbox
import co.gapfinder.mobile.data.outbox.toDelivery
import co.gapfinder.mobile.domain.MobilityPreference
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.jsonOf
import kotlinx.coroutines.CancellationException
import java.io.IOException

private const val TAG = "MobilityRepository"

/**
 * Preferencia de movilidad (BQ 14). El teléfono es la fuente: se guarda primero en
 * [MobilityStore] y después se manda al backend, que la usa para filtrar sus consultas.
 */
object MobilityRepository {
    /** Tipo de acción en la cola: sincronizar la preferencia del usuario. */
    const val COURIER = "mobility.sync"

    /**
     * Se registra una vez al abrir la app. La acción solo lleva el usuario: al enviarla se manda
     * lo que haya en el teléfono en ese momento, así que varios cambios sin red terminan en un solo PUT
     * con el último valor.
     */
    fun registerCourier() {
        Outbox.register(COURIER) { payload -> push(payload.getInt("memberId")) }
    }

    /** GET / PUT /users/{userId}/mobility-preference */
    fun path(memberId: Int) = "/users/$memberId/mobility-preference"

    /** La preferencia guardada en el teléfono (None si nunca eligió). */
    fun current(memberId: Int): MobilityPreference = MobilityStore.read(memberId)

    /**
     * Trae la del backend (por ejemplo, si la eligió en otro teléfono). No pisa un cambio local
     * que todavía no se mandó. 404 = nunca eligió.
     */
    suspend fun refresh(memberId: Int): MobilityPreference {
        if (MobilityStore.isPending(memberId) || !Connectivity.isOnline()) return current(memberId)
        try {
            val reply = HttpGateway.fetch(path(memberId))
            when (reply.code) {
                200 -> MobilityStore.write(memberId, MobilityPreference.decode(reply.json), pending = false)
                404 -> MobilityStore.write(memberId, MobilityPreference.None, pending = false)
                else -> Log.d(TAG, "GET preference: HTTP ${reply.code}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Could not refresh preference: $e")
        }
        return current(memberId)
    }

    /**
     * Guarda la preferencia en el teléfono y la intenta mandar. Devuelve true si ya llegó al
     * backend; si no hay red (o el servidor no responde) queda en la cola de acciones y se
     * sincroniza sola cuando vuelva la conexión. Las recomendaciones en caché se invalidan
     * porque dependen del filtro.
     */
    suspend fun save(memberId: Int, preference: MobilityPreference): Boolean {
        MobilityStore.write(memberId, preference, pending = true)
        RecommendationRepository.invalidate(memberId)
        val delivery = push(memberId)
        if (delivery is Delivery.Retry) {
            Outbox.enqueue(COURIER, jsonOf("memberId" to memberId), memberId = memberId)
            Log.d(TAG, "📥 Preference queued (${delivery.reason})")
        }
        return delivery == Delivery.Sent
    }

    /** Manda al backend lo que haya en el teléfono (siempre la última versión). */
    suspend fun push(memberId: Int): Delivery {
        if (!Connectivity.isOnline()) return Delivery.Retry("offline")
        val delivery = try {
            HttpGateway.overwrite(path(memberId), current(memberId).encode()).toDelivery()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Delivery.Retry(e.toString())
        }
        // Rechazada (4xx) tampoco se reintenta: queda la del teléfono y no se bloquea el refresh
        if (delivery !is Delivery.Retry) MobilityStore.markSynced(memberId)
        Log.d(TAG, "PUT preference → $delivery")
        return delivery
    }
}

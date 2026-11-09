package co.gapfinder.mobile.data

import android.util.Log
import co.gapfinder.mobile.data.local.MobilityStore
import co.gapfinder.mobile.data.outbox.Delivery
import co.gapfinder.mobile.data.outbox.toDelivery
import co.gapfinder.mobile.domain.MobilityPreference
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.HttpGateway
import kotlinx.coroutines.CancellationException
import java.io.IOException

private const val TAG = "MobilityRepository"

/**
 * Preferencia de movilidad (BQ 14). El teléfono es la fuente: se guarda primero en
 * [MobilityStore] y después se manda al backend, que la usa para filtrar sus consultas.
 */
object MobilityRepository {
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
     * backend. Las recomendaciones en caché se invalidan porque dependen del filtro.
     */
    suspend fun save(memberId: Int, preference: MobilityPreference): Boolean {
        MobilityStore.write(memberId, preference, pending = true)
        RecommendationRepository.invalidate(memberId)
        return push(memberId) == Delivery.Sent
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

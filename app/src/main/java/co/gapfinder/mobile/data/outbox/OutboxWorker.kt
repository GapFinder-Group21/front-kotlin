package co.gapfinder.mobile.data.outbox

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.foundation.SessionVault
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.IOException

private const val TAG = "OutboxWorker"

// Después de tantos intentos fallidos la acción se descarta para no reintentar para siempre
private const val MAX_ATTEMPTS = 8

/**
 * Manda las acciones pendientes en el orden en que se hicieron. Solo corre con red
 * (restricción NetworkType.CONNECTED). Se detiene en la primera que hay que reintentar,
 * para no mandar, por ejemplo, "unirse" antes de "crear", y le pide a WorkManager
 * que lo vuelva a intentar con backoff exponencial.
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = LocalStore.pendingActions()
        val me = SessionVault.memberId()

        for (action in dao.queueFor(me)) {
            val payload = try {
                JSONObject(action.payload)
            } catch (e: Exception) {
                Log.w(TAG, "Descartada acción ${action.id} con payload inválido: $e")
                dao.remove(action.id)
                continue
            }
            val courier = Outbox.courierFor(action.type)
            val delivery = when {
                courier == null -> Delivery.Rejected("No courier registered for ${action.type}")
                else -> try {
                    courier.deliver(payload)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    // Sin red o servidor inalcanzable aunque el sistema diga que hay conexión
                    Delivery.Retry(e.toString())
                } catch (e: Exception) {
                    Delivery.Rejected(e.toString())
                }
            }

            when (delivery) {
                Delivery.Sent -> {
                    Log.d(TAG, "Enviada acción ${action.id} (${action.type})")
                    dao.remove(action.id)
                }

                is Delivery.Rejected -> {
                    Log.w(TAG, "Rechazada acción ${action.id} (${action.type}): ${delivery.reason}")
                    dao.remove(action.id)
                    courier?.discarded(payload)
                }

                is Delivery.Retry -> {
                    if (action.attempts + 1 >= MAX_ATTEMPTS) {
                        Log.w(TAG, "Sin más intentos para la acción ${action.id} (${action.type}): ${delivery.reason}")
                        dao.remove(action.id)
                        courier?.discarded(payload)
                    } else {
                        dao.markFailed(action.id, delivery.reason)
                        return Result.retry()
                    }
                }
            }
        }
        return Result.success()
    }
}

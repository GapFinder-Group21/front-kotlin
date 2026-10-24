package co.gapfinder.mobile.screens.home

import android.os.SystemClock
import android.util.Log
import co.gapfinder.mobile.data.AlertRepository
import co.gapfinder.mobile.data.LectureRepository
import co.gapfinder.mobile.data.RecommendationRepository
import co.gapfinder.mobile.data.WindowRepository
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.data.local.SavedTimetable
import co.gapfinder.mobile.domain.Alert
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Lecture
import co.gapfinder.mobile.domain.Recommendation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val TAG = "HomeWarmup"

/** Corre [block] y devuelve el valor o el error, sin cancelar a las demás cargas si falla. */
internal inline fun <T> attempt(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * Carga inicial del home en paralelo. Al entrar al home se lanzan a la vez horario, gaps,
 * notificaciones y recomendaciones (async / awaitAll), en vez de que cada pestaña haga su
 * petición una tras otra. Cada pestaña toma su resultado una sola vez con take*; las recargas
 * siguientes (volver a la pestaña, deslizar para refrescar) ya van directo a la red.
 */
internal object HomeWarmup {
    private var memberId: Int? = null
    private var timetable: Deferred<Result<SavedTimetable>>? = null
    private var alerts: Deferred<Result<List<Alert>>>? = null
    private var recommendation: Deferred<Result<Recommendation>>? = null

    /** Lanza las cargas en [scope] (el del HomeShell, que las cancela si se sale del home). */
    fun start(scope: CoroutineScope, me: Int) {
        val began = SystemClock.elapsedRealtime()
        memberId = me

        val lectures = scope.async { attempt { LectureRepository.timetableOf(me) } }
        val windows = scope.async { attempt { syncedWeek(me) } }
        val inbox = scope.async { attempt { AlertRepository.inboxOf(me) } }
        val suggested = scope.async { attempt { RecommendationRepository.hangoutsFor(me) } }

        // El horario se arma con clases + gaps, pero las cuatro peticiones ya van corriendo a la vez
        timetable = scope.async { combineTimetable(me, lectures.await(), windows.await()) }
        alerts = inbox
        recommendation = suggested

        scope.launch {
            awaitAll(lectures, windows, inbox, suggested)
            Log.d(TAG, "⚡ Home cargado en paralelo en ${SystemClock.elapsedRealtime() - began} ms")
        }
    }

    /** Suelta lo que nadie tomó (al salir del home o cerrar sesión). */
    fun clear() {
        memberId = null
        timetable = null
        alerts = null
        recommendation = null
    }

    suspend fun takeTimetable(me: Int): Result<SavedTimetable>? {
        val job = timetable?.takeIf { memberId == me && !it.isCancelled } ?: return null
        timetable = null
        return job.await()
    }

    suspend fun takeAlerts(me: Int): Result<List<Alert>>? {
        val job = alerts?.takeIf { memberId == me && !it.isCancelled } ?: return null
        alerts = null
        return job.await()
    }

    suspend fun takeRecommendation(me: Int): Result<Recommendation>? {
        val job = recommendation?.takeIf { memberId == me && !it.isCancelled } ?: return null
        recommendation = null
        return job.await()
    }

    /** Horario completo: clases y gaps en paralelo, y se guarda en Room. Lo usa también TimetableTab al recargar. */
    suspend fun loadTimetable(me: Int): SavedTimetable = coroutineScope {
        val lectures = async { LectureRepository.timetableOf(me) }
        val windows = async { syncedWeek(me) }
        saved(me, SavedTimetable(lectures.await(), windows.await()))
    }

    /** Recalcula los gaps de la semana en el backend (sin fallar si no se puede) y los trae. */
    private suspend fun syncedWeek(me: Int): List<FreeWindow> {
        try {
            WindowRepository.generateWeek(me)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Advertencia: No se pudieron sincronizar los GAPs con el servidor: $e")
        }
        return WindowRepository.weekOf(me)
    }

    private suspend fun combineTimetable(
        me: Int,
        lectures: Result<List<Lecture>>,
        windows: Result<List<FreeWindow>>,
    ): Result<SavedTimetable> = attempt {
        saved(me, SavedTimetable(lectures.getOrThrow(), windows.getOrThrow()))
    }

    private suspend fun saved(me: Int, table: SavedTimetable): SavedTimetable {
        LocalStore.saveTimetable(me, table.lectures, table.windows)
        return table
    }
}

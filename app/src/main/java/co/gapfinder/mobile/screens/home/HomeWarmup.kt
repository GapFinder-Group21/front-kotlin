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

/** Runs [block] and returns the value or the error, without cancelling other loads if it fails. */
internal inline fun <T> attempt(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * Initial parallel home load. On entering home, schedule, gaps, notifications and recommendations
 * are launched at the same time (async / awaitAll) instead of each tab making its request
 * one after another. Each tab takes its result once with take*; subsequent reloads
 * (returning to the tab, pull-to-refresh) go directly to the network.
 */
internal object HomeWarmup {
    private var memberId: Int? = null
    private var timetable: Deferred<Result<SavedTimetable>>? = null
    private var alerts: Deferred<Result<List<Alert>>>? = null
    private var recommendation: Deferred<Result<Recommendation>>? = null

    /** Launches the loads in [scope] (the HomeShell scope, which cancels them if the home is exited). */
    fun start(scope: CoroutineScope, me: Int) {
        val began = SystemClock.elapsedRealtime()
        memberId = me

        val lectures = scope.async { attempt { LectureRepository.timetableOf(me) } }
        val windows = scope.async { attempt { syncedWeek(me) } }
        val inbox = scope.async { attempt { AlertRepository.inboxOf(me) } }
        val suggested = scope.async { attempt { RecommendationRepository.hangoutsFor(me) } }

        // The timetable is assembled from lectures + gaps, but all four requests are already running in parallel
        timetable = scope.async { combineTimetable(me, lectures.await(), windows.await()) }
        alerts = inbox
        recommendation = suggested

        scope.launch {
            awaitAll(lectures, windows, inbox, suggested)
            Log.d(TAG, "Home loaded in parallel in ${SystemClock.elapsedRealtime() - began} ms")
        }
    }

    /** Releases anything nobody took (on home exit or sign-out). */
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

    /** Full timetable: lectures and gaps in parallel, saved to Room. Also used by TimetableTab on reload. */
    suspend fun loadTimetable(me: Int): SavedTimetable = coroutineScope {
        val lectures = async { LectureRepository.timetableOf(me) }
        val windows = async { syncedWeek(me) }
        saved(me, SavedTimetable(lectures.await(), windows.await()))
    }

    /** Recalculates the week's gaps on the backend (without failing if it can't) and fetches them. */
    private suspend fun syncedWeek(me: Int): List<FreeWindow> {
        try {
            WindowRepository.generateWeek(me)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Warning: Could not sync GAPs with the server: $e")
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

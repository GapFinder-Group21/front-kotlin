package co.gapfinder.mobile.ui.nav

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicLong

/**
 * Navegador de pila propio: cada entrada queda viva mientras esté en la pila
 * (igual que las rutas de Flutter), así que conservan estado y timers.
 */
object StackNavigator {
    class Entry internal constructor(val key: Long, val destination: Destination) {
        internal val closed = CompletableDeferred<Unit>()
    }

    private val counter = AtomicLong(0)
    val entries = mutableStateListOf<Entry>()

    val top: Destination? get() = entries.lastOrNull()?.destination
    val canGoBack: Boolean get() = entries.size > 1

    fun start(first: Destination) {
        if (entries.isEmpty()) entries.add(entry(first))
    }

    /** pushNamed */
    fun go(destination: Destination) {
        if (ignored(destination)) return
        entries.add(entry(destination))
    }

    /** pushNamed(...).then(...) — suspende hasta que esa pantalla se cierre. */
    suspend fun goAndWait(destination: Destination) {
        if (ignored(destination)) return
        val e = entry(destination)
        entries.add(e)
        e.closed.await()
    }

    /** pushReplacementNamed */
    fun swap(destination: Destination) {
        if (ignored(destination)) return
        val old = entries.removeLastOrNull()
        entries.add(entry(destination))
        old?.closed?.complete(Unit)
    }

    /** pushNamedAndRemoveUntil(..., (route) => false) */
    fun resetTo(destination: Destination) {
        val old = entries.toList()
        entries.clear()
        entries.add(entry(destination))
        old.forEach { it.closed.complete(Unit) }
    }

    /** Navigator.pop */
    fun back(): Boolean {
        if (entries.size <= 1) return false
        entries.removeAt(entries.lastIndex).closed.complete(Unit)
        return true
    }

    private fun entry(d: Destination) = Entry(counter.incrementAndGet(), d)

    private fun ignored(d: Destination): Boolean {
        if (d is Destination.Unmapped) {
            Log.w("StackNavigator", "Route not registered: ${d.path}")
            return true
        }
        return false
    }
}

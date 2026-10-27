package co.gapfinder.mobile.foundation

import android.os.SystemClock
import android.util.LruCache
import java.time.LocalDateTime

/**
 * Caché en memoria con tamaño máximo (LruCache) y tiempo de vida por entrada.
 * Guarda datos compartidos y temporales (edificios, intereses, recomendaciones); lo del usuario va en Room.
 * Una entrada vencida se trata como si no existiera.
 */
class TtlCache<K : Any, V : Any>(
    maxEntries: Int,
    private val ttlMillis: Long,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Entry<V>(val value: V, val storedAtClock: Long, val storedAt: LocalDateTime)

    private val entries = object : LruCache<K, Entry<V>>(maxEntries) {}

    /** El valor guardado, o null si no hay o ya venció. */
    @Synchronized
    fun fresh(key: K): V? {
        val entry = entries.get(key) ?: return null
        if (clock() - entry.storedAtClock >= ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V) {
        entries.put(key, Entry(value, clock(), LocalDateTime.now()))
    }

    /** Cuándo se guardó la entrada (para "Last updated"), o null si no hay. */
    @Synchronized
    fun storedAt(key: K): LocalDateTime? = entries.get(key)?.storedAt

    @Synchronized
    fun clear() {
        entries.evictAll()
    }

    /** Primero el caché; si no hay nada vigente, [load] va a la red y el resultado se guarda. */
    suspend fun getOrLoad(key: K, load: suspend () -> V): V {
        fresh(key)?.let { return it }
        return load().also { put(key, it) }
    }
}

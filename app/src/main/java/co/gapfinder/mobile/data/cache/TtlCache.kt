package co.gapfinder.mobile.data.cache

import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import java.time.Duration
import java.time.LocalDateTime

private const val TAG = "TtlCache"

/** Valor del caché, cuándo se guardó y si todavía está dentro del TTL. */
data class Cached<T>(val value: T, val savedAt: LocalDateTime, val fresh: Boolean)

/**
 * Caché en memoria con [LruCache] y vencimiento (TTL). LruCache ya limita la cantidad de
 * entradas (saca la menos usada) y es seguro entre hilos. El TTL se mide con
 * [SystemClock.elapsedRealtime], que no cambia si el usuario mueve la hora del teléfono.
 *
 * Lo vencido no se borra al leerlo: [fresh] lo ignora, pero [peek] lo sigue devolviendo,
 * porque sin red es mejor mostrar algo viejo con su fecha que nada.
 */
class TtlCache<K : Any, V : Any>(
    private val name: String,
    maxEntries: Int,
    private val ttl: Duration,
) {
    private class Entry<V>(val value: V, val storedAtMs: Long, val savedAt: LocalDateTime)

    private val entries = LruCache<K, Entry<V>>(maxEntries)

    /** Valor dentro del TTL, o null si no hay o ya venció. */
    fun fresh(key: K): V? = entries.get(key)?.takeIf { it.isFresh() }?.value

    /** Lo que haya guardado, vigente o no, con su fecha. */
    fun peek(key: K): Cached<V>? = entries.get(key)?.let { Cached(it.value, it.savedAt, it.isFresh()) }

    fun put(key: K, value: V) {
        entries.put(key, Entry(value, SystemClock.elapsedRealtime(), LocalDateTime.now()))
    }

    fun invalidate(key: K) {
        entries.remove(key)
    }

    fun clear() {
        entries.evictAll()
    }

    /**
     * Primero el caché; si no hay nada vigente (o [force] es true) llama a [load], que va a la red,
     * y guarda el resultado. Si [load] falla, el error sube igual que antes: quien llama decide si
     * muestra lo vencido con [peek].
     */
    suspend fun getOrLoad(key: K, force: Boolean = false, load: suspend () -> V): V {
        if (!force) {
            fresh(key)?.let {
                Log.d(TAG, "✅ HIT $name[$key]")
                return it
            }
        }
        Log.d(TAG, "⬇️ MISS $name[$key]${if (force) " (forced)" else ""}")
        return load().also { put(key, it) }
    }

    private fun Entry<V>.isFresh(): Boolean = SystemClock.elapsedRealtime() - storedAtMs < ttl.toMillis()
}

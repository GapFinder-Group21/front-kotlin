package co.gapfinder.mobile.data

import co.gapfinder.mobile.domain.MatchHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Filtro de "My matches". */
enum class MatchFilter { ToRate, Rated, All }

/** El historial ya ordenado, repartido por filtro y con el resumen de arriba. */
data class MatchHistoryView(
    /** Todos, del más nuevo al más viejo. */
    val all: List<MatchHistoryItem>,
    val toRate: List<MatchHistoryItem>,
    val rated: List<MatchHistoryItem>,
    /** Promedio de estrellas que me dieron, o null si nadie me ha calificado (o aún no califico). */
    val averageReceived: Double?,
    val receivedCount: Int,
) {
    val toRateCount: Int get() = toRate.size

    fun of(filter: MatchFilter): List<MatchHistoryItem> = when (filter) {
        MatchFilter.ToRate -> toRate
        MatchFilter.Rated -> rated
        MatchFilter.All -> all
    }
}

/**
 * Ordena, reparte y promedia el historial. Es una función pura: no toca la red, Room ni la pantalla.
 * Va aparte para poder correrla fuera del hilo principal ([processMatchHistory]) y probarla sola.
 */
fun buildMatchHistoryView(items: List<MatchHistoryItem>): MatchHistoryView {
    val sorted = items.sortedByDescending { it.startsAt }
    val received = sorted.mapNotNull { it.otherRating?.stars }
    return MatchHistoryView(
        all = sorted,
        toRate = sorted.filter { it.canRate },
        // Con la calificación en la cola también cuenta como calificado: ya no se puede volver a calificar
        rated = sorted.filter { it.myRating != null },
        averageReceived = if (received.isEmpty()) null else received.average(),
        receivedCount = received.size,
    )
}

/** Estrategia: multithreading. Ordenar y promediar es trabajo de CPU, así que va en Dispatchers.Default. */
suspend fun processMatchHistory(items: List<MatchHistoryItem>): MatchHistoryView =
    withContext(Dispatchers.Default) { buildMatchHistoryView(items) }

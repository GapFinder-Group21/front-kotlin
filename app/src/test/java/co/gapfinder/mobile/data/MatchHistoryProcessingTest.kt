package co.gapfinder.mobile.data

import co.gapfinder.mobile.domain.MatchHistoryItem
import co.gapfinder.mobile.domain.MatchRating
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.PairingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MatchHistoryProcessingTest {
    private val base = LocalDateTime.of(2026, 11, 17, 10, 0)

    private fun match(
        id: Int,
        daysAgo: Long,
        state: PairingState = PairingState.Finished,
        mine: Int? = null,
        theirs: Int? = null,
    ) = MatchHistoryItem(
        matchId = id,
        state = state,
        startsAt = base.minusDays(daysAgo),
        endsAt = base.minusDays(daysAgo).plusHours(1),
        other = Member(id = 100 + id, name = "Other $id", email = "", career = "Systems"),
        myRating = mine?.let { MatchRating(it) },
        otherRating = theirs?.let { MatchRating(it) },
        canRate = state == PairingState.Finished && mine == null,
    )

    @Test
    fun sortsFromNewestToOldest() {
        val view = buildMatchHistoryView(listOf(match(1, daysAgo = 5), match(2, daysAgo = 1), match(3, daysAgo = 3)))
        assertEquals(listOf(2, 3, 1), view.all.map { it.matchId })
    }

    @Test
    fun splitsToRateFromRated() {
        val view = buildMatchHistoryView(
            listOf(
                match(1, 1),
                match(2, 2, mine = 4),
                match(3, 3, state = PairingState.Live),
            ),
        )
        assertEquals(listOf(1), view.toRate.map { it.matchId })
        assertEquals(listOf(2), view.rated.map { it.matchId })
        assertEquals(3, view.all.size)
        assertEquals(1, view.toRateCount)
    }

    @Test
    fun averagesOnlyTheRatingsReceived() {
        val view = buildMatchHistoryView(
            listOf(match(1, 1, mine = 5, theirs = 4), match(2, 2, mine = 3, theirs = 5), match(3, 3, mine = 2)),
        )
        assertEquals(4.5, view.averageReceived!!, 0.0001)
        assertEquals(2, view.receivedCount)
    }

    @Test
    fun noAverageWhenNobodyRatedMe() {
        val view = buildMatchHistoryView(listOf(match(1, 1), match(2, 2, mine = 3)))
        assertNull(view.averageReceived)
        assertEquals(0, view.receivedCount)
    }

    @Test
    fun aRatingWaitingInTheQueueCountsAsRated() {
        val queued = match(1, 1, mine = 5).copy(ratingPending = true, canRate = false)
        val view = buildMatchHistoryView(listOf(queued))
        assertEquals(emptyList<MatchHistoryItem>(), view.toRate)
        assertEquals(listOf(1), view.rated.map { it.matchId })
    }

    @Test
    fun emptyHistory() {
        val view = buildMatchHistoryView(emptyList())
        assertEquals(0, view.all.size)
        assertNull(view.averageReceived)
    }
}

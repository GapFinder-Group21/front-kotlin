package co.gapfinder.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** BQ 17: la medida del tiempo de entrega de un mensaje del chat. */
class ChatDeliveryReportTest {

    private val tap = 1_000_000L

    @Test
    fun aDirectMessageMeasuresFromTheTapAndHasNoTimeAfterReconnecting() {
        val measure = ChatDeliveryReport.measure(DeliveryRoute.Direct, tap, tap + 420, onlineSince = tap - 60_000)!!

        assertEquals(DeliveryRoute.Direct, measure.route)
        assertEquals(420L, measure.elapsedMs)
        assertNull(measure.afterReconnectMs)
    }

    @Test
    fun aMessageWrittenOfflineCountsTheQueueFromWhenTheConnectionCameBack() {
        // Escrito sin red; la red volvió 90 s después; la cola lo mandó 4 s más tarde
        val back = tap + 90_000
        val measure = ChatDeliveryReport.measure(DeliveryRoute.Queued, tap, back + 4_000, onlineSince = back)!!

        assertEquals(94_000L, measure.elapsedMs)
        assertEquals(4_000L, measure.afterReconnectMs)
    }

    @Test
    fun aMessageWrittenOnlineThatFailedCountsTheWholeWait() {
        // Había red al escribirlo (el servidor falló): la red "volvió" antes del tap, así que cuenta todo
        val measure = ChatDeliveryReport.measure(DeliveryRoute.Queued, tap, tap + 35_000, onlineSince = tap - 600_000)!!

        assertEquals(35_000L, measure.elapsedMs)
        assertEquals(35_000L, measure.afterReconnectMs)
    }

    @Test
    fun whenTheAppDoesNotKnowWhenTheConnectionCameBackItSendsOnlyTheWholeWait() {
        val measure = ChatDeliveryReport.measure(DeliveryRoute.Queued, tap, tap + 35_000, onlineSince = null)!!

        assertEquals(35_000L, measure.elapsedMs)
        assertNull(measure.afterReconnectMs)
    }

    @Test
    fun aNegativeTimeIsIgnoredBecauseThePhoneClockChanged() {
        assertNull(ChatDeliveryReport.measure(DeliveryRoute.Direct, tap, tap - 1, onlineSince = null))
    }

    @Test
    fun moreThanSevenDaysIsIgnored() {
        val eightDays = 8L * 24 * 60 * 60 * 1000

        assertNull(ChatDeliveryReport.measure(DeliveryRoute.Queued, tap, tap + eightDays, onlineSince = tap))
    }

    @Test
    fun theRouteNamesMatchTheBackendEnum() {
        assertEquals(listOf("DIRECT", "QUEUED"), DeliveryRoute.entries.map { it.wire })
    }
}

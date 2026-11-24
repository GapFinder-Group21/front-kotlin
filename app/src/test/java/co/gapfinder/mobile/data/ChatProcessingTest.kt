package co.gapfinder.mobile.data

import co.gapfinder.mobile.domain.ChatMessage
import co.gapfinder.mobile.domain.ChatStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ChatProcessingTest {
    private val base = LocalDateTime.of(2026, 11, 20, 10, 0)
    private val me = 1
    private val friend = 2

    private fun message(
        clientId: String,
        minute: Long,
        serverId: Int? = null,
        status: ChatStatus = ChatStatus.SENT,
        fromMe: Boolean = true,
    ) = ChatMessage(
        clientId = clientId,
        serverId = serverId,
        senderId = if (fromMe) me else friend,
        receiverId = if (fromMe) friend else me,
        content = "Message $clientId",
        createdAt = base.plusMinutes(minute),
        status = status,
    )

    @Test
    fun sortsByCreatedAt() {
        val merged = mergeChat(
            local = listOf(message("c", minute = 3), message("a", minute = 1)),
            newer = listOf(message("b", minute = 2, fromMe = false)),
        )
        assertEquals(listOf("a", "b", "c"), merged.map { it.clientId })
    }

    @Test
    fun deduplicatesByClientIdAndTheServerCopyWins() {
        // Lo escribí sin red (PENDING) y después llegó del backend: queda una sola vez y como SENT
        val merged = mergeChat(
            local = listOf(message("a", minute = 1, status = ChatStatus.PENDING)),
            newer = listOf(message("a", minute = 1, serverId = 10)),
        )
        assertEquals(1, merged.size)
        assertEquals(ChatStatus.SENT, merged.single().status)
        assertEquals(10, merged.single().serverId)
    }

    @Test
    fun keepsLocalMessagesThatTheServerDoesNotHave() {
        val merged = mergeChat(
            local = listOf(
                message("sent", minute = 1, serverId = 5),
                message("queued", minute = 4, status = ChatStatus.PENDING),
                message("refused", minute = 3, status = ChatStatus.FAILED),
            ),
            newer = listOf(message("new", minute = 2, serverId = 6, fromMe = false)),
        )
        assertEquals(listOf("sent", "new", "refused", "queued"), merged.map { it.clientId })
        assertEquals(ChatStatus.PENDING, merged.last().status)
    }

    @Test
    fun sameTimeIsOrderedByServerIdAndPendingGoesLast() {
        val merged = mergeChat(
            local = listOf(message("pending", minute = 1, status = ChatStatus.PENDING)),
            newer = listOf(message("second", minute = 1, serverId = 8), message("first", minute = 1, serverId = 7)),
        )
        assertEquals(listOf("first", "second", "pending"), merged.map { it.clientId })
    }

    @Test
    fun emptyListsGiveAnEmptyChat() {
        assertTrue(mergeChat(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun afterIdIsTheHighestServerId() {
        val chat = listOf(
            message("a", minute = 1, serverId = 4),
            message("b", minute = 2, serverId = 9),
            message("c", minute = 3), // enviado por POST, todavía sin id del GET
        )
        assertEquals(9, lastServerId(chat))
        assertEquals(0, lastServerId(emptyList()))
    }

    @Test
    fun aSingleChangeReplacesTheMessage() {
        val failed = message("a", minute = 1, status = ChatStatus.FAILED).copy(failReason = "You can only message friends")
        val merged = mergeChat(listOf(message("a", minute = 1, status = ChatStatus.PENDING)), listOf(failed))
        assertEquals(ChatStatus.FAILED, merged.single().status)
        assertEquals("You can only message friends", merged.single().failReason)
        assertNull(merged.single().serverId)
    }
}

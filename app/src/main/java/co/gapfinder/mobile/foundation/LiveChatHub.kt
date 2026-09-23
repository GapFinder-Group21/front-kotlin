package co.gapfinder.mobile.foundation

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import co.gapfinder.mobile.data.PairingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/** Chat en curso (match u open table) con su ventana de tiempo. */
data class ChatWindow(
    val pairingId: Int? = null,
    val hangoutId: Int? = null,
    val peerName: String,
    val peerAvatar: String? = null,
    val opensAt: LocalDateTime,
    val closesAt: LocalDateTime,
) {
    val isOpen: Boolean get() = LocalDateTime.now().isBefore(closesAt)
    val remaining: Duration get() = Duration.between(LocalDateTime.now(), closesAt)
    val span: Duration get() = Duration.between(opensAt, closesAt)
}

/** Mantiene el chat activo global que muestra la burbuja flotante (antes ChatManager). */
object LiveChatHub {
    private const val TAG = "LiveChatHub"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var expiry: Job? = null

    var window by mutableStateOf<ChatWindow?>(null)
        private set

    val isLive: Boolean get() = window?.isOpen == true

    fun open(
        pairingId: Int? = null,
        hangoutId: Int? = null,
        peerName: String,
        peerAvatar: String? = null,
        opensAt: LocalDateTime,
        closesAt: LocalDateTime,
    ) {
        // Si ya hay sesión para este mismo match/mesa, no se hace nada
        val existing = window
        if (existing != null && existing.pairingId == pairingId && existing.hangoutId == hangoutId) return

        expiry?.cancel()
        val left = Duration.between(LocalDateTime.now(), closesAt)
        if (left.isNegative) {
            window = null
            return
        }
        window = ChatWindow(pairingId, hangoutId, peerName, peerAvatar, opensAt, closesAt)
        expiry = scope.launch {
            delay(left.toMillis())
            window = null
        }
    }

    /** Consulta el servidor y abre/cierra la sesión local según haya un match activo. */
    suspend fun reconcile(memberId: Int) {
        try {
            Log.d(TAG, "🎯 CHAT SYNC: Checking for active match for user $memberId...")
            val ongoing = PairingRepository.ongoingFor(memberId)
            if (ongoing == null) {
                Log.d(TAG, "🎯 CHAT SYNC: No active match found on server.")
                if (window != null) close()
                return
            }
            val full = PairingRepository.byId(ongoing.id)
            val peer = if (full.requester?.id == memberId) full.receiver else full.requester
            if (peer == null) {
                Log.d(TAG, "⚠️ CHAT SYNC: Peer info missing in match ${full.id}")
                return
            }
            Log.d(TAG, "🎯 CHAT SYNC: Session established with ${peer.name}")
            open(
                pairingId = full.id,
                peerName = peer.name,
                peerAvatar = peer.avatarUrl,
                opensAt = full.overlapStart,
                closesAt = full.overlapEnd,
            )
        } catch (e: Exception) {
            Log.d(TAG, "❌ CHAT SYNC ERROR: $e")
        }
    }

    fun close() {
        window = null
        expiry?.cancel()
    }
}

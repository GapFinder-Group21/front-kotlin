package co.gapfinder.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import co.gapfinder.mobile.domain.ChatStatus
import java.time.LocalDateTime

/**
 * Estrategia: local storage. Mensaje del chat guardado en el teléfono (copia plana de
 * [co.gapfinder.mobile.domain.ChatMessage]). Con esto el chat se ve sin red y los mensajes
 * escritos sin red no se pierden si se cierra la app.
 *
 * La clave es [clientId] (el UUID que genera la app), así el mismo mensaje nunca queda dos veces:
 * cuando llega del backend reemplaza a la copia local. [memberId] es el usuario con sesión y
 * [friendId] el amigo del chat; al cerrar sesión LocalStore.wipe() borra la tabla.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val clientId: String,
    /** Id del backend; null mientras el mensaje no haya llegado por el GET de la conversación. */
    val serverId: Int?,
    val memberId: Int,
    val friendId: Int,
    val senderId: Int,
    val content: String,
    val createdAt: LocalDateTime,
    /** SENT, PENDING o FAILED (Room guarda el nombre del enum como texto). */
    val status: ChatStatus,
    /** Por qué el backend lo rechazó; solo en FAILED. */
    val failReason: String?,
)

@Dao
abstract class ChatMessageDao {
    /** Toda la conversación guardada con ese amigo, de la más vieja a la más nueva. */
    @Query("SELECT * FROM chat_messages WHERE memberId = :memberId AND friendId = :friendId ORDER BY createdAt, serverId")
    abstract suspend fun conversation(memberId: Int, friendId: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE clientId = :clientId")
    abstract suspend fun byClientId(clientId: String): ChatMessageEntity?

    /** Guarda o reemplaza un mensaje (por clientId). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(row: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putAll(rows: List<ChatMessageEntity>)

    /**
     * Guarda el lote que acaba de llegar del backend en una sola transacción: entra completo o no entra.
     * Solo se escriben los mensajes del backend (que mandan sobre la copia local con el mismo clientId);
     * los PENDING y FAILED que no están en el lote no se tocan.
     */
    @Transaction
    open suspend fun saveFromServer(rows: List<ChatMessageEntity>) {
        putAll(rows)
    }
}

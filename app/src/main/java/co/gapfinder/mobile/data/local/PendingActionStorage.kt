package co.gapfinder.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.LocalDateTime

/**
 * Acción que el usuario hizo sin red y que falta mandar al backend (cola "outbox").
 * El payload es el JSON que necesita quien la envía; lo interpreta el remitente registrado para [type].
 */
@Entity(tableName = "pending_actions", indices = [Index("memberId")])
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Usuario que hizo la acción (null si no depende de la sesión). Solo se envía con esa sesión activa. */
    val memberId: Int?,
    val type: String,
    val payload: String,
    /** Envíos que fallaron y se van a reintentar. */
    val attempts: Int = 0,
    val createdAt: LocalDateTime,
    val lastError: String? = null,
)

@Dao
interface PendingActionDao {
    @Insert
    suspend fun add(action: PendingActionEntity): Long

    /** Acciones que se pueden mandar con la sesión actual, en el orden en que se hicieron. */
    @Query("SELECT * FROM pending_actions WHERE memberId IS NULL OR memberId = :memberId ORDER BY createdAt, id")
    suspend fun queueFor(memberId: Int?): List<PendingActionEntity>

    @Query("UPDATE pending_actions SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun count(): Int
}

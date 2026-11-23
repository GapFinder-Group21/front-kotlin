package co.gapfinder.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDateTime

/**
 * Match del historial guardado en el teléfono (copia plana de [co.gapfinder.mobile.domain.MatchHistoryItem]).
 * La clave incluye memberId porque dos cuentas pueden usar el mismo teléfono.
 * De la otra persona se guarda solo lo que muestra la tarjeta.
 */
@Entity(tableName = "match_history", primaryKeys = ["memberId", "matchId"])
data class MatchHistoryEntity(
    val memberId: Int,
    val matchId: Int,
    /** Estado con el valor del backend (PENDING, ACCEPTED, COMPLETED, ...). */
    val status: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val score: Double?,
    val otherId: Int,
    val otherName: String,
    val otherCareer: String,
    val otherSemester: Int,
    val otherAvatarUrl: String?,
    val myStars: Int?,
    val myComment: String?,
    val myRatedAt: LocalDateTime?,
    val theirStars: Int?,
    val theirComment: String?,
    val theirRatedAt: LocalDateTime?,
    val canRate: Boolean,
    /** La calificación se hizo sin red y sigue en la cola: myStars es la que el usuario eligió. */
    val ratingPending: Boolean = false,
    val savedAt: LocalDateTime,
)

@Dao
abstract class MatchHistoryDao {
    @Query("SELECT * FROM match_history WHERE memberId = :memberId ORDER BY startsAt DESC")
    abstract suspend fun listOf(memberId: Int): List<MatchHistoryEntity>

    /** Momento de la última sincronización (null si nunca se guardó). */
    @Query("SELECT MAX(savedAt) FROM match_history WHERE memberId = :memberId")
    abstract suspend fun lastSavedAt(memberId: Int): LocalDateTime?

    @Query("SELECT * FROM match_history WHERE memberId = :memberId AND ratingPending = 1")
    abstract suspend fun pendingOf(memberId: Int): List<MatchHistoryEntity>

    @Query("SELECT * FROM match_history WHERE memberId = :memberId AND matchId = :matchId")
    abstract suspend fun byId(memberId: Int, matchId: Int): MatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun put(row: MatchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putAll(rows: List<MatchHistoryEntity>)

    @Query("DELETE FROM match_history WHERE memberId = :memberId")
    protected abstract suspend fun clear(memberId: Int)

    /** Cambia el historial por lo que acaba de llegar del backend, en una sola transacción. */
    @Transaction
    open suspend fun replace(memberId: Int, rows: List<MatchHistoryEntity>) {
        clear(memberId)
        putAll(rows)
    }
}

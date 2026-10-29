package co.gapfinder.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDateTime

/** Relación del usuario con una de sus open tables. */
enum class HangoutRole { HOSTED, ATTENDED }

/**
 * Open table propia guardada en el teléfono (copia plana de [co.gapfinder.mobile.domain.Hangout]).
 * La clave incluye memberId porque la misma mesa puede estar en la lista de dos cuentas.
 * Del creador, la actividad y el edificio se guarda solo lo que muestra la pantalla.
 */
@Entity(tableName = "my_hangouts", primaryKeys = ["memberId", "id"])
data class MyHangoutEntity(
    val memberId: Int,
    val id: Int,
    val role: HangoutRole,
    val title: String,
    val description: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val maxParticipants: Int,
    /** Estado con el valor del backend (OPEN, FULL, ...). */
    val state: String,
    val pastimeId: Int?,
    val pastimeName: String?,
    val hostId: Int?,
    val hostName: String?,
    val hostAvatarUrl: String?,
    val spotId: Int?,
    val spotName: String?,
    val savedAt: LocalDateTime,
)

/** Mesas que el usuario creó o en las que participa. */
@Dao
abstract class MyHangoutDao {
    @Query("SELECT * FROM my_hangouts WHERE memberId = :memberId ORDER BY startsAt DESC")
    abstract suspend fun mineOf(memberId: Int): List<MyHangoutEntity>

    /** Momento de la última sincronización con el backend (null si nunca se guardó nada). */
    @Query("SELECT MAX(savedAt) FROM my_hangouts WHERE memberId = :memberId")
    abstract suspend fun lastSavedAt(memberId: Int): LocalDateTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun put(rows: List<MyHangoutEntity>)

    @Query("DELETE FROM my_hangouts WHERE memberId = :memberId")
    protected abstract suspend fun clear(memberId: Int)

    /** Cambia la lista guardada por la que acaba de llegar del backend, en una sola transacción. */
    @Transaction
    open suspend fun replace(memberId: Int, rows: List<MyHangoutEntity>) {
        clear(memberId)
        put(rows)
    }
}

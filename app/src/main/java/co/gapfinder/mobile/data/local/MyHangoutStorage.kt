package co.gapfinder.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDateTime

/** Relación del usuario con una mesa guardada: la creó, participa o solo la vio en el mapa. */
enum class HangoutRole { HOSTED, ATTENDED, BOARD }

/**
 * Open table guardada en el teléfono (copia plana de [co.gapfinder.mobile.domain.Hangout]):
 * las mías (HOSTED / ATTENDED) y las abiertas del mapa (BOARD).
 * La clave incluye memberId y role porque la misma mesa puede estar en la lista de dos cuentas
 * y, a la vez, en mis mesas y en el mapa.
 * Del creador, la actividad y el edificio se guarda solo lo que muestran las pantallas.
 */
@Entity(tableName = "my_hangouts", primaryKeys = ["memberId", "role", "id"])
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
    /** Solo en el mapa: si el usuario ya participa en la mesa. */
    val joined: Boolean = false,
    val photoUrl: String? = null,
    val savedAt: LocalDateTime,
)

/** Mesas guardadas por lista (mías o del mapa). */
@Dao
abstract class MyHangoutDao {
    @Query("SELECT * FROM my_hangouts WHERE memberId = :memberId AND role IN (:roles) ORDER BY startsAt DESC")
    abstract suspend fun listOf(memberId: Int, roles: List<HangoutRole>): List<MyHangoutEntity>

    /** Momento de la última sincronización de esas listas (null si nunca se guardaron). */
    @Query("SELECT MAX(savedAt) FROM my_hangouts WHERE memberId = :memberId AND role IN (:roles)")
    abstract suspend fun lastSavedAt(memberId: Int, roles: List<HangoutRole>): LocalDateTime?

    /** Una mesa guardada en cualquiera de las listas (la más reciente), para el detalle sin red. */
    @Query("SELECT * FROM my_hangouts WHERE memberId = :memberId AND id = :id ORDER BY savedAt DESC LIMIT 1")
    abstract suspend fun byId(memberId: Int, id: Int): MyHangoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun put(rows: List<MyHangoutEntity>)

    @Query("DELETE FROM my_hangouts WHERE memberId = :memberId AND role IN (:roles)")
    protected abstract suspend fun clear(memberId: Int, roles: List<HangoutRole>)

    /** Cambia esas listas por lo que acaba de llegar del backend, en una sola transacción. */
    @Transaction
    open suspend fun replace(memberId: Int, roles: List<HangoutRole>, rows: List<MyHangoutEntity>) {
        clear(memberId, roles)
        put(rows)
    }
}

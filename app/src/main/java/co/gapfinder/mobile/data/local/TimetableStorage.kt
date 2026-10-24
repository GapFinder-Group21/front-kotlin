package co.gapfinder.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDateTime
import java.time.LocalTime

/** Bloque de clase guardado en el teléfono (copia de [co.gapfinder.mobile.domain.Lecture]). */
@Entity(tableName = "lectures", indices = [Index("memberId")])
data class LectureEntity(
    @PrimaryKey val id: Int,
    /** Dueño del horario, para no mezclar cuentas si varias inician sesión en el mismo teléfono. */
    val memberId: Int,
    val subject: String,
    val location: String,
    /** Día con el valor del backend (MON, TUE, ...). */
    val day: String,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val savedAt: LocalDateTime,
)

/** GAP de la semana guardado en el teléfono (copia de [co.gapfinder.mobile.domain.FreeWindow]). */
@Entity(tableName = "free_windows", indices = [Index("memberId")])
data class FreeWindowEntity(
    @PrimaryKey val id: Int,
    val memberId: Int,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val savedAt: LocalDateTime,
)

/** Horario del estudiante: sus clases y los GAPs que el backend calculó para la semana. */
@Dao
abstract class TimetableDao {
    @Query("SELECT * FROM lectures WHERE memberId = :memberId ORDER BY startsAt")
    abstract suspend fun lecturesOf(memberId: Int): List<LectureEntity>

    @Query("SELECT * FROM free_windows WHERE memberId = :memberId ORDER BY startsAt")
    abstract suspend fun windowsOf(memberId: Int): List<FreeWindowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putLectures(rows: List<LectureEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putWindows(rows: List<FreeWindowEntity>)

    @Query("DELETE FROM lectures WHERE memberId = :memberId")
    protected abstract suspend fun clearLectures(memberId: Int)

    @Query("DELETE FROM free_windows WHERE memberId = :memberId")
    protected abstract suspend fun clearWindows(memberId: Int)

    /**
     * Cambia el horario guardado por el que acaba de llegar del backend. Va en una transacción
     * para que quien lea nunca vea el horario a medias (sin clases pero con GAPs, por ejemplo).
     */
    @Transaction
    open suspend fun replace(memberId: Int, lectures: List<LectureEntity>, windows: List<FreeWindowEntity>) {
        clearLectures(memberId)
        clearWindows(memberId)
        putLectures(lectures)
        putWindows(windows)
    }
}

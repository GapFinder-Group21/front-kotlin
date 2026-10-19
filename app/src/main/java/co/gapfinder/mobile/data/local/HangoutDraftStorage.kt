package co.gapfinder.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDateTime

/**
 * Borrador del wizard de creación de open table (HangoutComposerPage). Hay uno por usuario:
 * se sobrescribe con cada cambio y se borra al publicar o al cancelar.
 * Actividad y edificio se guardan por id y se vuelven a buscar en el catálogo al restaurar.
 */
@Entity(tableName = "hangout_drafts")
data class HangoutDraftEntity(
    @PrimaryKey val memberId: Int,
    val pastimeId: Int?,
    val minutes: Int,
    val blurb: String,
    val seats: Int,
    val spotId: Int?,
    /** Ruta absoluta de la foto en filesDir (null si todavía no hay foto). */
    val photoPath: String?,
    /** Último paso que tocó el usuario, con el valor del backend (ACTIVITY, DESCRIPTION, ...). */
    val stage: String,
    val updatedAt: LocalDateTime,
)

@Dao
interface HangoutDraftDao {
    @Query("SELECT * FROM hangout_drafts WHERE memberId = :memberId")
    suspend fun of(memberId: Int): HangoutDraftEntity?

    @Upsert
    suspend fun save(draft: HangoutDraftEntity)

    @Query("DELETE FROM hangout_drafts WHERE memberId = :memberId")
    suspend fun discard(memberId: Int)

    /** Fotos de todos los borradores, para borrarlas del disco al cerrar sesión. */
    @Query("SELECT photoPath FROM hangout_drafts WHERE photoPath IS NOT NULL")
    suspend fun photoPaths(): List<String>
}

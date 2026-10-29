package co.gapfinder.mobile.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDateTime

/**
 * Catálogo del backend guardado tal cual (arreglo JSON): intereses, actividades y edificios.
 * Son listas pequeñas que solo se leen completas, así que no vale la pena una tabla por cada una.
 */
@Entity(tableName = "catalogs")
data class CatalogEntity(
    /** Nombre del catálogo (hobbies, pastimes, spots). */
    @PrimaryKey val name: String,
    val json: String,
    val savedAt: LocalDateTime,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalogs WHERE name = :name")
    suspend fun of(name: String): CatalogEntity?

    @Upsert
    suspend fun save(catalog: CatalogEntity)
}

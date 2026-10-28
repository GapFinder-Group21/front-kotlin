package co.gapfinder.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local (Room). Guarda lo que el usuario necesita ver sin red.
 * Se abre una sola vez desde [co.gapfinder.mobile.GapFinderApplication].
 */
@Database(
    entities = [
        LectureEntity::class,
        FreeWindowEntity::class,
        MyHangoutEntity::class,
        HangoutDraftEntity::class,
        PendingActionEntity::class,
        CatalogEntity::class,
    ],
    // 2: tabla pending_actions (cola de acciones sin red)
    // 3: tabla catalogs y mesas del mapa (rol BOARD) en my_hangouts
    version = 4,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class GapFinderDatabase : RoomDatabase() {
    abstract fun timetable(): TimetableDao

    abstract fun myHangouts(): MyHangoutDao

    abstract fun hangoutDrafts(): HangoutDraftDao

    abstract fun pendingActions(): PendingActionDao

    abstract fun catalogs(): CatalogDao

    companion object {
        private const val FILE = "gapfinder.db"

        /** Versión 4: la mesa guardada lleva la URL de su foto. Se migra para no perder la cola de pendientes. */
        private val ADD_PHOTO_URL = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE my_hangouts ADD COLUMN photoUrl TEXT")
            }
        }

        @Volatile
        private var instance: GapFinderDatabase? = null

        fun get(context: Context): GapFinderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GapFinderDatabase::class.java, FILE)
                    .addMigrations(ADD_PHOTO_URL)
                    // Casi todo es una copia del backend que se vuelve a bajar; lo único que se pierde
                    // si cambia el esquema es un borrador del wizard sin publicar
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}

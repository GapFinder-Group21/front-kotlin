package co.gapfinder.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos local (Room). Guarda lo que el usuario necesita ver sin red.
 * Se abre una sola vez desde [co.gapfinder.mobile.GapFinderApplication].
 */
@Database(
    entities = [
        LectureEntity::class,
        FreeWindowEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class GapFinderDatabase : RoomDatabase() {
    abstract fun timetable(): TimetableDao

    companion object {
        private const val FILE = "gapfinder.db"

        @Volatile
        private var instance: GapFinderDatabase? = null

        fun get(context: Context): GapFinderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GapFinderDatabase::class.java, FILE)
                    // Lo guardado es una copia de lo que hay en el backend: si cambia el esquema se vuelve a bajar
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}

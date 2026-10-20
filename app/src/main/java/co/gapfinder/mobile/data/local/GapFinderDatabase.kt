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
        MyHangoutEntity::class,
        HangoutDraftEntity::class,
        PendingActionEntity::class,
    ],
    // 2: tabla pending_actions (cola de acciones sin red)
    version = 2,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class GapFinderDatabase : RoomDatabase() {
    abstract fun timetable(): TimetableDao

    abstract fun myHangouts(): MyHangoutDao

    abstract fun hangoutDrafts(): HangoutDraftDao

    abstract fun pendingActions(): PendingActionDao

    companion object {
        private const val FILE = "gapfinder.db"

        @Volatile
        private var instance: GapFinderDatabase? = null

        fun get(context: Context): GapFinderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GapFinderDatabase::class.java, FILE)
                    // Casi todo es una copia del backend que se vuelve a bajar; lo único que se pierde
                    // si cambia el esquema es un borrador del wizard sin publicar
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}

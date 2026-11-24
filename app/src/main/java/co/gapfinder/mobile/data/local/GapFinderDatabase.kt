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
        MatchHistoryEntity::class,
        ChatMessageEntity::class,
    ],
    // 2: tabla pending_actions (cola de acciones sin red)
    // 3: tabla catalogs y mesas del mapa (rol BOARD) en my_hangouts
    // 4: photoUrl en my_hangouts
    // 5: tabla match_history (Mis matches)
    // 6: tabla chat_messages (chat con amigos)
    version = 6,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class GapFinderDatabase : RoomDatabase() {
    abstract fun timetable(): TimetableDao

    abstract fun myHangouts(): MyHangoutDao

    abstract fun hangoutDrafts(): HangoutDraftDao

    abstract fun pendingActions(): PendingActionDao

    abstract fun catalogs(): CatalogDao

    abstract fun matchHistory(): MatchHistoryDao
    abstract fun chatMessages(): ChatMessageDao

    companion object {
        private const val FILE = "gapfinder.db"

        /** Versión 4: la mesa guardada lleva la URL de su foto. Se migra para no perder la cola de pendientes. */
        private val ADD_PHOTO_URL = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE my_hangouts ADD COLUMN photoUrl TEXT")
            }
        }

        /** Versión 5: tabla del historial de matches. Solo se agrega, así la cola de pendientes se conserva. */
        private val ADD_MATCH_HISTORY = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `match_history` (`memberId` INTEGER NOT NULL, `matchId` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `startsAt` TEXT NOT NULL, `endsAt` TEXT NOT NULL, `score` REAL, " +
                        "`otherId` INTEGER NOT NULL, `otherName` TEXT NOT NULL, `otherCareer` TEXT NOT NULL, " +
                        "`otherSemester` INTEGER NOT NULL, `otherAvatarUrl` TEXT, `myStars` INTEGER, `myComment` TEXT, " +
                        "`myRatedAt` TEXT, `theirStars` INTEGER, `theirComment` TEXT, `theirRatedAt` TEXT, " +
                        "`canRate` INTEGER NOT NULL, `ratingPending` INTEGER NOT NULL, `savedAt` TEXT NOT NULL, " +
                        "PRIMARY KEY(`memberId`, `matchId`))"
                )
            }
        }
                /**
         * Versión 6: tabla del chat con amigos. Solo se agrega, así no cae en fallbackToDestructiveMigration,
         * que borraría la cola de pendientes. El CREATE TABLE es el mismo que genera Room
         * (build/generated/ksp/debug/.../GapFinderDatabase_Impl.kt).
         */
        private val ADD_CHAT_MESSAGES = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chat_messages` (`clientId` TEXT NOT NULL, `serverId` INTEGER, " +
                        "`memberId` INTEGER NOT NULL, `friendId` INTEGER NOT NULL, `senderId` INTEGER NOT NULL, " +
                        "`content` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `status` TEXT NOT NULL, `failReason` TEXT, " +
                        "PRIMARY KEY(`clientId`))"
                )
            }
        }

        @Volatile
        private var instance: GapFinderDatabase? = null

        fun get(context: Context): GapFinderDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GapFinderDatabase::class.java, FILE)
                    .addMigrations(ADD_PHOTO_URL, ADD_MATCH_HISTORY, ADD_CHAT_MESSAGES)                    // Casi todo es una copia del backend que se vuelve a bajar; lo único que se pierde
                    // si cambia el esquema es un borrador del wizard sin publicar
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}

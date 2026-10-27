package co.gapfinder.mobile.data.local

import android.content.Context
import co.gapfinder.mobile.domain.CampusSpot
import co.gapfinder.mobile.domain.EnergyLevel
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.domain.HangoutState
import co.gapfinder.mobile.domain.Hobby
import co.gapfinder.mobile.domain.Lecture
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.Pastime
import co.gapfinder.mobile.domain.Weekday
import co.gapfinder.mobile.foundation.mapNodes
import co.gapfinder.mobile.foundation.toJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Duration
import java.time.LocalDateTime

/** Horario guardado en el teléfono. */
data class SavedTimetable(val lectures: List<Lecture>, val windows: List<FreeWindow>)

/** Mis open tables guardadas en el teléfono y el momento en que se bajaron del backend. */
data class SavedHangouts(val hosted: List<Hangout>, val attended: List<Hangout>, val savedAt: LocalDateTime)

/** Mesas abiertas del mapa guardadas en el teléfono, en cuáles participa el usuario y cuándo se bajaron. */
data class SavedBoard(val listings: List<Hangout>, val joinedIds: Set<Int>, val savedAt: LocalDateTime)

/** Una mesa guardada, si el usuario ya está en ella y cuándo se bajó del backend. */
data class SavedHangout(val hangout: Hangout, val joined: Boolean, val savedAt: LocalDateTime)

/** Catálogo guardado y el momento en que se bajó del backend. */
data class SavedCatalog<T>(val items: List<T>, val savedAt: LocalDateTime)

/**
 * Puerta de entrada al almacenamiento local. Las pantallas guardan y leen modelos del dominio
 * (Lecture, FreeWindow, Hangout) y aquí se traducen a las entidades de Room, igual que
 * los repositorios REST traducen el JSON del backend.
 */
object LocalStore {
    private lateinit var db: GapFinderDatabase

    fun attach(context: Context) {
        if (::db.isInitialized) return
        db = GapFinderDatabase.get(context)
    }

    // ============================================================ Horario

    /** Horario guardado del usuario, o null si nunca se guardó. */
    suspend fun timetableOf(memberId: Int): SavedTimetable? {
        val dao = db.timetable()
        val lectures = dao.lecturesOf(memberId).map { it.toLecture() }
        val windows = dao.windowsOf(memberId).map { it.toWindow() }
        if (lectures.isEmpty() && windows.isEmpty()) return null
        return SavedTimetable(lectures, windows)
    }

    suspend fun saveTimetable(memberId: Int, lectures: List<Lecture>, windows: List<FreeWindow>) {
        val now = LocalDateTime.now()
        db.timetable().replace(
            memberId,
            lectures.map { it.toEntity(memberId, now) },
            windows.map { it.toEntity(memberId, now) },
        )
    }

    // ============================================================ Mis open tables

    private val MINE = listOf(HangoutRole.HOSTED, HangoutRole.ATTENDED)
    private val BOARD = listOf(HangoutRole.BOARD)

    /** Mesas guardadas del usuario, o null si nunca se guardaron. */
    suspend fun myHangoutsOf(memberId: Int): SavedHangouts? {
        val dao = db.myHangouts()
        val savedAt = dao.lastSavedAt(memberId, MINE) ?: return null
        val (hosted, attended) = dao.listOf(memberId, MINE).partition { it.role == HangoutRole.HOSTED }
        return SavedHangouts(hosted.map { it.toHangout() }, attended.map { it.toHangout() }, savedAt)
    }

    suspend fun saveMyHangouts(memberId: Int, hosted: List<Hangout>, attended: List<Hangout>) {
        val now = LocalDateTime.now()
        db.myHangouts().replace(
            memberId,
            MINE,
            hosted.map { it.toEntity(memberId, HangoutRole.HOSTED, now) } +
                attended.map { it.toEntity(memberId, HangoutRole.ATTENDED, now) },
        )
    }

    // ============================================================ Mesas del mapa

    /** Mesas abiertas guardadas del mapa, o null si nunca se guardaron. */
    suspend fun boardOf(memberId: Int): SavedBoard? {
        val dao = db.myHangouts()
        val savedAt = dao.lastSavedAt(memberId, BOARD) ?: return null
        val rows = dao.listOf(memberId, BOARD)
        return SavedBoard(rows.map { it.toHangout() }, rows.filter { it.joined }.map { it.id }.toSet(), savedAt)
    }

    suspend fun saveBoard(memberId: Int, listings: List<Hangout>, joinedIds: Set<Int>) {
        val now = LocalDateTime.now()
        db.myHangouts().replace(
            memberId,
            BOARD,
            listings.map { it.toEntity(memberId, HangoutRole.BOARD, now).copy(joined = it.id in joinedIds) },
        )
    }

    /** Una mesa guardada en el mapa o en mis mesas, para el detalle sin red. */
    suspend fun hangoutById(memberId: Int, id: Int): SavedHangout? =
        db.myHangouts().byId(memberId, id)?.let {
            SavedHangout(it.toHangout(), joined = it.role != HangoutRole.BOARD || it.joined, savedAt = it.savedAt)
        }

    // ============================================================ Catálogos

    suspend fun saveHobbies(items: List<Hobby>) = saveCatalog(HOBBIES, items.toJsonArray { it.encode() })

    suspend fun hobbies(): SavedCatalog<Hobby>? = catalog(HOBBIES, Hobby::decode)

    suspend fun savePastimes(items: List<Pastime>) = saveCatalog(PASTIMES, items.toJsonArray { it.encode() })

    suspend fun pastimes(): SavedCatalog<Pastime>? = catalog(PASTIMES, Pastime::decode)

    suspend fun saveSpots(items: List<CampusSpot>) = saveCatalog(SPOTS, items.toJsonArray { it.encode() })

    suspend fun spots(): SavedCatalog<CampusSpot>? = catalog(SPOTS, CampusSpot::decode)

    private const val HOBBIES = "hobbies"
    private const val PASTIMES = "pastimes"
    private const val SPOTS = "spots"

    private suspend fun saveCatalog(name: String, json: JSONArray) =
        db.catalogs().save(CatalogEntity(name, json.toString(), LocalDateTime.now()))

    private suspend fun <T> catalog(name: String, decode: (JSONObject) -> T): SavedCatalog<T>? {
        val row = db.catalogs().of(name) ?: return null
        return SavedCatalog(JSONArray(row.json).mapNodes(decode), row.savedAt)
    }

    // ============================================================ Borrador del wizard

    suspend fun draftOf(memberId: Int): HangoutDraftEntity? = db.hangoutDrafts().of(memberId)

    suspend fun saveDraft(draft: HangoutDraftEntity) = db.hangoutDrafts().save(draft)

    suspend fun discardDraft(memberId: Int) = db.hangoutDrafts().discard(memberId)

    // ============================================================ Acciones pendientes

    /** DAO de la cola de acciones; solo lo usa [co.gapfinder.mobile.data.outbox.Outbox]. */
    internal fun pendingActions(): PendingActionDao = db.pendingActions()

    // ============================================================ Sesión

    /** Borra todo lo guardado (logout), incluidas las fotos de borradores sin publicar. */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        db.hangoutDrafts().photoPaths().forEach { File(it).delete() }
        db.clearAllTables()
    }
}

// ============================================================ Traducción dominio ↔ Room

private fun Lecture.toEntity(memberId: Int, savedAt: LocalDateTime) = LectureEntity(
    id = id,
    memberId = memberId,
    subject = subject,
    location = location,
    day = day.wire,
    startsAt = startsAt,
    endsAt = endsAt,
    savedAt = savedAt,
)

private fun LectureEntity.toLecture() = Lecture(
    id = id,
    subject = subject,
    location = location,
    day = Weekday.parse(day),
    startsAt = startsAt,
    endsAt = endsAt,
)

private fun FreeWindow.toEntity(memberId: Int, savedAt: LocalDateTime) = FreeWindowEntity(
    id = id,
    memberId = memberId,
    startsAt = startsAt,
    endsAt = endsAt,
    savedAt = savedAt,
)

private fun FreeWindowEntity.toWindow() = FreeWindow(
    id = id,
    startsAt = startsAt,
    endsAt = endsAt,
    ownerId = memberId,
)

private fun Hangout.toEntity(memberId: Int, role: HangoutRole, savedAt: LocalDateTime) = MyHangoutEntity(
    memberId = memberId,
    id = id,
    role = role,
    title = title,
    description = description,
    startsAt = startsAt,
    endsAt = endsAt,
    maxParticipants = maxParticipants,
    state = state.wire,
    pastimeId = pastime?.id,
    pastimeName = pastime?.name,
    hostId = host?.id,
    hostName = host?.name,
    hostAvatarUrl = host?.avatarUrl,
    spotId = spot?.id,
    spotName = spot?.name,
    photoUrl = photoUrl,
    savedAt = savedAt,
)

/** Arma la mesa con lo guardado; los datos del creador y del edificio que no se guardan quedan vacíos. */
private fun MyHangoutEntity.toHangout() = Hangout(
    id = id,
    title = title,
    description = description,
    startsAt = startsAt,
    endsAt = endsAt,
    maxParticipants = maxParticipants,
    state = HangoutState.parse(state),
    pastime = pastimeId?.let {
        Pastime(
            id = it,
            name = pastimeName ?: title,
            durationMinutes = Duration.between(startsAt, endsAt).toMinutes().toInt(),
            energy = EnergyLevel.parse(null),
        )
    },
    host = hostId?.let { Member(id = it, name = hostName ?: "", email = "", career = "", avatarUrl = hostAvatarUrl) },
    spot = spotId?.let { CampusSpot(id = it, name = spotName ?: "") },
    photoUrl = photoUrl,
)

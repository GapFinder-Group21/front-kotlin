package co.gapfinder.mobile.domain

import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.decimal
import co.gapfinder.mobile.foundation.decimalOrNull
import co.gapfinder.mobile.foundation.flagOr
import co.gapfinder.mobile.foundation.intOr
import co.gapfinder.mobile.foundation.intOrNull
import co.gapfinder.mobile.foundation.jsonOf
import co.gapfinder.mobile.foundation.nodeList
import co.gapfinder.mobile.foundation.nodeOrNull
import co.gapfinder.mobile.foundation.textOr
import co.gapfinder.mobile.foundation.textOrNull
import co.gapfinder.mobile.foundation.toJsonArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

// Cada modelo expone `decode(JSONObject)` y `encode()`; las claves JSON son las del backend.
// El backend responde con dos formas de cada entidad: el DTO básico (solo campos propios)
// y el completo (con relaciones). Los campos de relaciones son opcionales por eso.

/** Usuario (antes User). */
data class Member(
    val id: Int,
    val name: String,
    val email: String,
    val career: String,
    val semester: Int = 0,
    val phoneNumber: String? = null,
    val avatarUrl: String? = null,
    val verified: Boolean = false,
    val energy: EnergyLevel? = null,
    val createdAt: LocalDateTime? = null,
    val locationUpdatedAt: LocalDateTime? = null,
    // Solo llegan con UserCompleteDTO
    val hobbies: List<Hobby> = emptyList(),
    val currentSpot: CampusSpot? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "name" to name,
        "email" to email,
        "phoneNumber" to phoneNumber,
        "career" to career,
        "semester" to semester,
        "avatarUrl" to avatarUrl,
        "verified" to verified,
        "preferredEffort" to energy?.wire,
        "locationUpdatedAt" to locationUpdatedAt?.let(Chrono::writeMoment),
    )

    companion object {
        fun decode(j: JSONObject): Member = Member(
            id = j.intOr("id"),
            name = j.textOr("name"),
            email = j.textOr("email"),
            career = j.textOr("career"),
            semester = j.intOr("semester"),
            phoneNumber = j.textOrNull("phoneNumber"),
            avatarUrl = j.textOrNull("avatarUrl"),
            verified = j.flagOr("verified"),
            energy = j.textOrNull("preferredEffort")?.let(EnergyLevel::parse),
            createdAt = j.textOrNull("createdAt")?.let(Chrono::readMoment),
            locationUpdatedAt = j.textOrNull("locationUpdatedAt")?.let(Chrono::readMoment),
            hobbies = j.nodeList("interests", Hobby::decode),
            currentSpot = j.nodeOrNull("currentBuilding")?.let(CampusSpot::decode),
        )
    }
}

/** Interés (antes Interest). */
data class Hobby(
    val id: Int,
    val name: String,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "name" to name,
    )

    companion object {
        fun decode(j: JSONObject) = Hobby(
            id = j.intOr("id"),
            name = j.textOr("name"),
        )
    }
}

/** Actividad del catálogo (antes Activity). */
data class Pastime(
    val id: Int,
    val name: String,
    val durationMinutes: Int,
    val energy: EnergyLevel,
    // Solo llega con ActivityCompleteDTO
    val hobby: Hobby? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "name" to name,
        "durationMinutes" to durationMinutes,
        "effortType" to energy.wire,
    )

    companion object {
        fun decode(j: JSONObject) = Pastime(
            id = j.intOr("id"),
            name = j.textOr("name"),
            durationMinutes = j.intOr("durationMinutes"),
            energy = EnergyLevel.parse(j.textOrNull("effortType")),
            hobby = j.nodeOrNull("interest")?.let(Hobby::decode),
        )
    }
}

/** Edificio del campus (antes Building). */
data class CampusSpot(
    val id: Int,
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 0.0,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "name" to name,
        "latitude" to latitude,
        "longitude" to longitude,
        "radiusMeters" to radiusMeters,
    )

    companion object {
        fun decode(j: JSONObject) = CampusSpot(
            id = j.intOr("id"),
            name = j.textOr("name"),
            latitude = j.decimal("latitude"),
            longitude = j.decimal("longitude"),
            radiusMeters = j.decimal("radiusMeters"),
        )
    }
}

/** Minutos pasados en un edificio (antes BuildingTime). */
data class SpotDwell(val spot: CampusSpot, val minutes: Int) {
    companion object {
        fun decode(j: JSONObject) = SpotDwell(
            spot = CampusSpot.decode(j.getJSONObject("building")),
            minutes = j.intOr("minutes"),
        )
    }
}

/** Bloque de clase del horario (antes ClassBlock). */
data class Lecture(
    val id: Int,
    val subject: String,
    val location: String,
    val day: Weekday,
    val startsAt: LocalTime,
    val endsAt: LocalTime,
    val owner: Member? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "subject" to subject,
        "location" to location,
        "dayOfWeek" to day.wire,
        "startTime" to Chrono.writeClock(startsAt),
        "endTime" to Chrono.writeClock(endsAt),
        "user" to owner?.encode(),
    )

    companion object {
        fun decode(j: JSONObject) = Lecture(
            id = j.intOr("id"),
            subject = j.textOr("subject"),
            location = j.textOr("location"),
            day = Weekday.parse(j.textOrNull("dayOfWeek")),
            startsAt = Chrono.readClock(j.textOr("startTime", "00:00")),
            endsAt = Chrono.readClock(j.textOr("endTime", "00:00")),
            owner = j.nodeOrNull("user")?.let(Member::decode),
        )
    }
}

/** Hueco libre entre clases (antes Gap). */
data class FreeWindow(
    val id: Int,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val ownerId: Int? = null,
    // Solo llega con GapCompleteDTO (candidatos y pendientes de match)
    val owner: Member? = null,
) {
    val durationMinutes: Int get() = Duration.between(startsAt, endsAt).toMinutes().toInt()

    /** true si el hueco está corriendo en ese instante (inicio inclusive, fin exclusive). */
    fun isLiveAt(moment: LocalDateTime): Boolean = !moment.isBefore(startsAt) && moment.isBefore(endsAt)

    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "startTime" to Chrono.writeMoment(startsAt),
        "endTime" to Chrono.writeMoment(endsAt),
        "userId" to ownerId,
    )

    companion object {
        fun decode(j: JSONObject) = FreeWindow(
            id = j.intOr("id"),
            startsAt = Chrono.readMoment(j.getString("startTime")),
            endsAt = Chrono.readMoment(j.getString("endTime")),
            ownerId = j.intOrNull("userId"),
            owner = j.nodeOrNull("user")?.let(Member::decode),
        )
    }
}

/** Amistad / solicitud de amistad (antes Friendship). */
data class Bond(
    val id: Int,
    val state: BondState,
    val createdAt: LocalDateTime,
    val requesterId: Int? = null,
    val receiverId: Int? = null,
    // Solo llegan con FriendshipCompleteDTO
    val requester: Member? = null,
    val receiver: Member? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "status" to state.wire,
        "createdAt" to Chrono.writeMoment(createdAt),
        "requesterId" to requesterId,
        "receiverId" to receiverId,
    )

    companion object {
        fun decode(j: JSONObject) = Bond(
            id = j.intOr("id"),
            state = BondState.parse(j.textOrNull("status")),
            createdAt = Chrono.readMoment(j.getString("createdAt")),
            requesterId = j.intOrNull("requesterId"),
            receiverId = j.intOrNull("receiverId"),
            requester = j.nodeOrNull("requester")?.let(Member::decode),
            receiver = j.nodeOrNull("receiver")?.let(Member::decode),
        )
    }
}

/** Grupo de amigos (antes Group). */
data class Crew(
    val id: Int,
    val name: String,
    val createdAt: LocalDateTime,
    val founder: Member? = null,
    val members: List<Member> = emptyList(),
    val hangouts: List<Hangout> = emptyList(),
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "name" to name,
        "createdAt" to Chrono.writeMoment(createdAt),
        "creator" to founder?.encode(),
        "members" to members.toJsonArray { it.encode() },
        "openTables" to hangouts.toJsonArray { it.encode() },
    )

    companion object {
        fun decode(j: JSONObject) = Crew(
            id = j.intOr("id"),
            name = j.textOr("name"),
            createdAt = Chrono.readMoment(j.getString("createdAt")),
            founder = j.nodeOrNull("creator")?.let(Member::decode),
            members = j.nodeList("members", Member::decode),
            hangouts = j.nodeList("openTables", Hangout::decode),
        )
    }
}

/** Match entre dos estudiantes (antes Match). Se arma sobre el hueco de cada uno. */
data class Pairing(
    val id: Int,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val state: PairingState,
    val score: Double? = null,
    // Solo llegan con MatchCompleteDTO; cada hueco trae a su dueño
    val proposerWindow: FreeWindow? = null,
    val acceptorWindow: FreeWindow? = null,
) {
    val durationMinutes: Int get() = Duration.between(startsAt, endsAt).toMinutes().toInt()

    /** Quien envió la solicitud. */
    val proposer: Member? get() = proposerWindow?.owner

    /** Quien la recibió. */
    val acceptor: Member? get() = acceptorWindow?.owner

    fun involves(memberId: Int): Boolean = proposer?.id == memberId || acceptor?.id == memberId

    /** La otra persona del match, vista desde [memberId]. */
    fun peerOf(memberId: Int): Member? = if (proposer?.id == memberId) acceptor else proposer

    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "startTime" to Chrono.writeMoment(startsAt),
        "endTime" to Chrono.writeMoment(endsAt),
        "score" to score,
        "status" to state.wire,
    )

    companion object {
        fun decode(j: JSONObject): Pairing = Pairing(
            id = j.intOr("id"),
            startsAt = Chrono.readMoment(j.getString("startTime")),
            endsAt = Chrono.readMoment(j.getString("endTime")),
            state = PairingState.parse(j.textOrNull("status")),
            score = j.decimalOrNull("score"),
            proposerWindow = j.nodeOrNull("proposerGap")?.let(FreeWindow::decode),
            acceptorWindow = j.nodeOrNull("acceptorGap")?.let(FreeWindow::decode),
        )
    }
}

/** Candidato devuelto por /matches/gap/{gapId}/candidates (antes MatchCandidate). */
data class PairingProspect(
    /** Hueco de quien busca. */
    val proposerWindow: FreeWindow,
    /** Hueco del candidato, con su usuario. */
    val acceptorWindow: FreeWindow,
    /** Puntaje calculado por el backend; hay que devolverlo igual al enviar la solicitud. */
    val score: Double,
) {
    companion object {
        fun decode(j: JSONObject) = PairingProspect(
            proposerWindow = FreeWindow.decode(j.getJSONObject("proposerGap")),
            acceptorWindow = FreeWindow.decode(j.getJSONObject("acceptorGap")),
            score = j.getDouble("score"),
        )
    }
}

/** Mensaje de chat (antes Message). */
data class ChatLine(
    val id: Int,
    val content: String,
    val sentAt: LocalDateTime,
    val senderName: String,
    val sender: Member? = null,
    val pairing: Pairing? = null,
    val hangout: Hangout? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "content" to content,
        "sentAt" to Chrono.writeMoment(sentAt),
        "senderName" to senderName,
        "sender" to sender?.encode(),
        "match" to pairing?.encode(),
        "openTable" to hangout?.encode(),
    )

    companion object {
        fun decode(j: JSONObject): ChatLine = ChatLine(
            id = j.intOr("id"),
            content = j.textOr("content"),
            sentAt = Chrono.readMoment(j.getString("sentAt")),
            senderName = j.textOr("senderName"),
            sender = j.nodeOrNull("sender")?.let(Member::decode),
            pairing = j.nodeOrNull("match")?.let(Pairing::decode),
            hangout = j.nodeOrNull("openTable")?.let(Hangout::decode),
        )
    }
}

/** Mesa abierta (antes OpenTable). */
data class Hangout(
    val id: Int,
    val title: String,
    val description: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val maxParticipants: Int,
    val state: HangoutState,
    val createdAt: LocalDateTime? = null,
    val pastime: Pastime? = null,
    // Solo llegan con OpenTableCompleteDTO
    val host: Member? = null,
    val spot: CampusSpot? = null,
) {
    /** createdAt no se manda: en el backend la columna no es actualizable. */
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "title" to title,
        "description" to description,
        "startTime" to Chrono.writeMoment(startsAt),
        "endTime" to Chrono.writeMoment(endsAt),
        "maxParticipants" to maxParticipants,
        "status" to state.wire,
    ).apply {
        pastime?.let { put("activity", jsonOf("id" to it.id)) }
    }

    companion object {
        fun decode(j: JSONObject): Hangout = Hangout(
            id = j.intOr("id"),
            title = j.textOr("title"),
            description = j.textOr("description"),
            startsAt = Chrono.readMoment(j.getString("startTime")),
            endsAt = Chrono.readMoment(j.getString("endTime")),
            maxParticipants = j.intOr("maxParticipants"),
            state = HangoutState.parse(j.textOrNull("status")),
            createdAt = j.textOrNull("createdAt")?.let(Chrono::readMoment),
            pastime = j.nodeOrNull("activity")?.let(Pastime::decode),
            host = j.nodeOrNull("creator")?.let(Member::decode),
            spot = j.nodeOrNull("building")?.let(CampusSpot::decode),
        )
    }
}

/** Participante de una mesa abierta (antes OpenTableParticipant). */
data class HangoutAttendee(
    val id: Int,
    val joinedAt: LocalDateTime,
    // Solo llegan con OpenTableParticipantCompleteDTO
    val hangout: Hangout? = null,
    val member: Member? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "joinedAt" to Chrono.writeMoment(joinedAt),
    )

    companion object {
        fun decode(j: JSONObject): HangoutAttendee = HangoutAttendee(
            id = j.intOr("id"),
            joinedAt = Chrono.readMoment(j.getString("joinedAt")),
            hangout = j.nodeOrNull("openTable")?.let(Hangout::decode),
            member = j.nodeOrNull("user")?.let(Member::decode),
        )
    }
}

/** Notificación (antes AppNotification). */
data class Alert(
    val id: Int,
    val kind: AlertKind,
    val message: String,
    /** Id de la entidad a la que se refiere (por ejemplo, el match propuesto). */
    val relatedId: Int? = null,
    val seen: Boolean,
    val createdAt: LocalDateTime,
    // Solo llega con NotificationCompleteDTO
    val recipient: Member? = null,
) {
    companion object {
        fun decode(j: JSONObject) = Alert(
            id = j.intOr("id"),
            kind = AlertKind.parse(j.textOrNull("type")),
            message = j.textOr("message"),
            relatedId = j.intOrNull("relatedEntityId"),
            seen = j.flagOr("read"),
            createdAt = Chrono.readMoment(j.getString("createdAt")),
            recipient = j.nodeOrNull("recipient")?.let(Member::decode),
        )
    }
}

/** Punto de ubicación registrado durante un hueco (antes UserLocationLog). */
data class LocationPing(
    val id: Int,
    val recordedAt: LocalDateTime,
    // Solo llegan con UserLocationLogCompleteDTO
    val member: Member? = null,
    val spot: CampusSpot? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "timestamp" to Chrono.writeMoment(recordedAt),
    )

    companion object {
        fun decode(j: JSONObject): LocationPing = LocationPing(
            id = j.intOr("id"),
            recordedAt = Chrono.readMoment(j.getString("timestamp")),
            member = j.nodeOrNull("user")?.let(Member::decode),
            spot = j.nodeOrNull("building")?.let(CampusSpot::decode),
        )
    }
}

/** Respuesta de login / registro (antes AuthResponse). */
data class LoginTicket(
    val id: Int,
    val accessToken: String,
    val refreshToken: String,
    val email: String,
    val name: String,
) {
    companion object {
        // El backend ha cambiado el nombre de estas claves entre versiones; se aceptan todas
        fun decode(j: JSONObject) = LoginTicket(
            id = j.intOrNull("id") ?: j.intOr("userId"),
            accessToken = j.textOrNull("accessToken") ?: j.textOrNull("token") ?: j.textOr("jwt"),
            refreshToken = j.textOrNull("refreshToken") ?: j.textOr("refresh_token"),
            email = j.textOr("email"),
            name = j.textOr("name"),
        )
    }
}

/** Registro de abandono del formulario de creación de mesa. */
data class HangoutDropOff(
    val memberId: Int,
    val stage: DraftStage,
    val pastimeId: Int? = null,
    val durationMinutes: Int? = null,
    val maxParticipants: Int? = null,
    val spotId: Int? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "userId" to memberId,
        "step" to stage.wire,
        "activityId" to pastimeId,
        "durationMinutes" to durationMinutes,
        "maxParticipants" to maxParticipants,
        "buildingId" to spotId,
    )
}

/** Estadística de abandono por paso. */
data class DropOffStat(
    val stage: DraftStage,
    val abandonments: Int,
    val reached: Int,
    /** Entre 0 y 1. */
    val rate: Double,
) {
    companion object {
        fun decode(j: JSONObject) = DropOffStat(
            stage = DraftStage.parse(j.getString("step")),
            abandonments = j.intOr("abandonments"),
            reached = j.intOr("reached"),
            rate = j.decimal("abandonmentRate"),
        )
    }
}

/** Mesas recomendadas en el edificio que más frecuenta el usuario (antes RecommendationResponse). */
data class Recommendation(
    val favoriteSpotId: Int? = null,
    val favoriteSpotName: String? = null,
    /** Minutos acumulados en ese edificio. */
    val totalMinutes: Double = 0.0,
    val hangouts: List<Hangout> = emptyList(),
) {
    companion object {
        fun decode(j: JSONObject) = Recommendation(
            favoriteSpotId = j.intOrNull("favoriteBuildingId"),
            favoriteSpotName = j.textOrNull("favoriteBuildingName"),
            totalMinutes = j.decimal("totalMinutes"),
            hangouts = j.nodeList("openTables", Hangout::decode),
        )
    }
}

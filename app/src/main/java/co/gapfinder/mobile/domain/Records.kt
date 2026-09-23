package co.gapfinder.mobile.domain

import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.decimal
import co.gapfinder.mobile.foundation.flagOr
import co.gapfinder.mobile.foundation.flagOrNull
import co.gapfinder.mobile.foundation.intOr
import co.gapfinder.mobile.foundation.intOrNull
import co.gapfinder.mobile.foundation.jsonOf
import co.gapfinder.mobile.foundation.nodeList
import co.gapfinder.mobile.foundation.nodeOrNull
import co.gapfinder.mobile.foundation.textOr
import co.gapfinder.mobile.foundation.textOrNull
import co.gapfinder.mobile.foundation.toJsonArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.LocalTime

// Cada modelo expone `decode(JSONObject)` y `encode()`; las claves JSON son las del backend.

/** Usuario (antes User). */
data class Member(
    val id: Int,
    val name: String,
    val email: String,
    val program: String,
    val semester: String,
    val avatarUrl: String? = null,
    val verified: Boolean = false,
    val energy: EnergyLevel = EnergyLevel.Regular,
    val locationUpdatedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val currentSpot: CampusSpot? = null,
    val hobbies: List<Hobby> = emptyList(),
    val lectures: List<Lecture> = emptyList(),
    val windows: List<FreeWindow> = emptyList(),
    val ownedCrews: List<Crew> = emptyList(),
    val crews: List<Crew> = emptyList(),
    val sentBonds: List<Bond> = emptyList(),
    val receivedBonds: List<Bond> = emptyList(),
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "name" to name,
        "email" to email,
        "program" to program,
        "semester" to semester,
        "avatarUrl" to avatarUrl,
        "verified" to verified,
        "locationUpdatedAt" to locationUpdatedAt?.let(Chrono::writeMoment),
        "createdAt" to createdAt?.let(Chrono::writeMoment),
        "currentBuilding" to currentSpot?.encode(),
        "interests" to hobbies.toJsonArray { it.encode() },
        "classBlocks" to lectures.toJsonArray { it.encode() },
        "gaps" to windows.toJsonArray { it.encode() },
        "createdGroups" to ownedCrews.toJsonArray { it.encode() },
        "groups" to crews.toJsonArray { it.encode() },
        "sentFriendRequests" to sentBonds.toJsonArray { it.encode() },
        "receivedFriendRequests" to receivedBonds.toJsonArray { it.encode() },
    )

    companion object {
        fun decode(j: JSONObject): Member = Member(
            id = j.intOr("id"),
            name = j.textOr("name"),
            email = j.textOr("email"),
            program = j.textOr("program"),
            semester = j.textOr("semester"),
            avatarUrl = j.textOrNull("avatarUrl"),
            verified = j.flagOr("verified"),
            energy = EnergyLevel.parse(j.textOrNull("activityEffortPreference")),
            locationUpdatedAt = j.textOrNull("locationUpdatedAt")?.let(Chrono::readMoment),
            createdAt = j.textOrNull("createdAt")?.let(Chrono::readMoment),
            currentSpot = j.nodeOrNull("currentBuilding")?.let(CampusSpot::decode),
            hobbies = j.nodeList("interests", Hobby::decode),
            lectures = j.nodeList("classBlocks", Lecture::decode),
            windows = j.nodeList("gaps", FreeWindow::decode),
            ownedCrews = j.nodeList("createdGroups", Crew::decode),
            crews = j.nodeList("groups", Crew::decode),
            sentBonds = j.nodeList("sentFriendRequests", Bond::decode),
            receivedBonds = j.nodeList("receivedFriendRequests", Bond::decode),
        )

        /** Usuario "vacío" con solo el id, como el que se arma cuando el backend manda userId. */
        fun stub(id: Int) = Member(id = id, name = "", email = "", program = "", semester = "")
    }
}

/** Interés (antes Interest). */
data class Hobby(
    val id: Int,
    val name: String,
    val members: List<Member> = emptyList(),
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "name" to name,
        "users" to members.toJsonArray { it.encode() },
    )

    companion object {
        fun decode(j: JSONObject) = Hobby(
            id = j.intOr("id"),
            name = j.textOr("name"),
            members = j.nodeList("users", Member::decode),
        )
    }
}

/** Actividad del catálogo (antes Activity). */
data class Pastime(
    val id: Int,
    val title: String,
    val description: String,
    val durationMinutes: Int,
    val energy: EnergyLevel,
    val hobby: Hobby? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "title" to title,
        "description" to description,
        "durationMinutes" to durationMinutes,
        "activityEffortLevel" to energy.wire,
        "interest" to hobby?.encode(),
    )

    companion object {
        fun decode(j: JSONObject) = Pastime(
            id = j.intOr("id"),
            title = j.textOr("title"),
            description = j.textOr("description"),
            durationMinutes = j.intOr("durationMinutes"),
            energy = EnergyLevel.parse(j.textOrNull("activityEffortLevel")),
            hobby = j.nodeOrNull("interest")?.let(Hobby::decode),
        )
    }
}

/** Edificio del campus (antes Building). */
data class CampusSpot(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val presentMembers: List<Member> = emptyList(),
    val hangouts: List<Hangout> = emptyList(),
    val pings: List<LocationPing> = emptyList(),
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "name" to name,
        "latitude" to latitude,
        "longitude" to longitude,
        "radiusMeters" to radiusMeters,
        "currentUsers" to presentMembers.toJsonArray { it.encode() },
        "openTables" to hangouts.toJsonArray { it.encode() },
        "locationLogs" to pings.toJsonArray { it.encode() },
    )

    companion object {
        fun decode(j: JSONObject) = CampusSpot(
            id = j.intOr("id"),
            name = j.textOr("name"),
            latitude = j.decimal("latitude"),
            longitude = j.decimal("longitude"),
            radiusMeters = j.decimal("radiusMeters"),
            presentMembers = j.nodeList("currentUsers", Member::decode),
            hangouts = j.nodeList("openTables", Hangout::decode),
            pings = j.nodeList("locationLogs", LocationPing::decode),
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
    val durationMinutes: Int,
    val owner: Member? = null,
    val pings: List<LocationPing> = emptyList(),
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "startTime" to Chrono.writeMoment(startsAt),
        "endTime" to Chrono.writeMoment(endsAt),
        "durationMinutes" to durationMinutes,
        "user" to owner?.encode(),
        "locationLogs" to pings.toJsonArray { it.encode() },
    )

    companion object {
        fun decode(j: JSONObject) = FreeWindow(
            id = j.intOr("id"),
            startsAt = Chrono.readMoment(j.getString("startTime")),
            endsAt = Chrono.readMoment(j.getString("endTime")),
            durationMinutes = j.intOr("durationMinutes"),
            owner = j.nodeOrNull("user")?.let(Member::decode),
            pings = j.nodeList("locationLogs", LocationPing::decode),
        )
    }
}

/** Amistad / solicitud de amistad (antes Friendship). */
data class Bond(
    val id: Int,
    val state: BondState,
    val createdAt: LocalDateTime,
    val requester: Member? = null,
    val addressee: Member? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "status" to state.wire,
        "createdAt" to Chrono.writeMoment(createdAt),
        "requester" to requester?.encode(),
        "addressee" to addressee?.encode(),
    )

    companion object {
        fun decode(j: JSONObject) = Bond(
            id = j.intOr("id"),
            state = BondState.parse(j.textOrNull("status")),
            createdAt = Chrono.readMoment(j.getString("createdAt")),
            requester = j.nodeOrNull("requester")?.let(Member::decode),
            addressee = j.nodeOrNull("addressee")?.let(Member::decode),
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

/** Match entre dos estudiantes (antes Match). */
data class Pairing(
    val id: Int,
    val state: PairingState,
    val overlapStart: LocalDateTime,
    val overlapEnd: LocalDateTime,
    val createdAt: LocalDateTime,
    val chosenPastime: Pastime? = null,
    val suggestedPastime: Pastime? = null,
    val requester: Member? = null,
    val receiver: Member? = null,
    val requesterWindow: FreeWindow? = null,
    val receiverWindow: FreeWindow? = null,
    val lines: List<ChatLine> = emptyList(),
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "status" to state.wire,
        "overlapStart" to Chrono.writeMoment(overlapStart),
        "overlapEnd" to Chrono.writeMoment(overlapEnd),
        "createdAt" to Chrono.writeMoment(createdAt),
        "chosenActivity" to chosenPastime?.encode(),
        "suggestedActivity" to suggestedPastime?.encode(),
        "requester" to requester?.encode(),
        "receiver" to receiver?.encode(),
        "requesterGap" to requesterWindow?.encode(),
        "receiverGap" to receiverWindow?.encode(),
        "messages" to lines.toJsonArray { it.encode() },
    )

    companion object {
        fun decode(j: JSONObject): Pairing = Pairing(
            id = j.intOr("id"),
            state = PairingState.parse(j.textOrNull("status")),
            overlapStart = Chrono.readMoment(j.getString("overlapStart")),
            overlapEnd = Chrono.readMoment(j.getString("overlapEnd")),
            createdAt = Chrono.readMoment(j.getString("createdAt")),
            chosenPastime = j.nodeOrNull("chosenActivity")?.let(Pastime::decode),
            suggestedPastime = j.nodeOrNull("suggestedActivity")?.let(Pastime::decode),
            requester = j.nodeOrNull("requester")?.let(Member::decode),
            receiver = j.nodeOrNull("receiver")?.let(Member::decode),
            requesterWindow = j.nodeOrNull("requesterGap")?.let(FreeWindow::decode),
            receiverWindow = j.nodeOrNull("receiverGap")?.let(FreeWindow::decode),
            lines = j.nodeList("messages", ChatLine::decode),
        )
    }
}

/** Candidato devuelto por /matches/candidates. */
data class PairingProspect(
    val member: Member,
    val activeWindow: FreeWindow?,
    /** Puntaje único: intereses (base) + bono del modo elegido. */
    val affinity: Double,
    val sharedHobbies: List<Hobby>,
) {
    companion object {
        fun decode(j: JSONObject) = PairingProspect(
            member = Member.decode(j.getJSONObject("user")),
            activeWindow = j.nodeOrNull("activeGap")?.let(FreeWindow::decode),
            affinity = j.decimal("compatibility"),
            sharedHobbies = j.nodeList("commonInterests", Hobby::decode),
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
    val description: String,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val state: HangoutState,
    val createdAt: LocalDateTime,
    val pastime: Pastime? = null,
    val host: Member? = null,
    val spot: CampusSpot? = null,
    val lines: List<ChatLine> = emptyList(),
    val attendees: List<HangoutAttendee> = emptyList(),
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "description" to description,
        "startTime" to Chrono.writeMoment(startsAt),
        "endTime" to Chrono.writeMoment(endsAt),
        "status" to state.wire,
        "createdAt" to Chrono.writeMoment(createdAt),
        "activity" to pastime?.encode(),
        "creator" to host?.encode(),
        "building" to spot?.encode(),
        "messages" to lines.toJsonArray { it.encode() },
        "participants" to attendees.toJsonArray { it.encode() },
    )

    companion object {
        fun decode(j: JSONObject): Hangout = Hangout(
            id = j.intOr("id"),
            description = j.textOr("description"),
            startsAt = Chrono.readMoment(j.getString("startTime")),
            endsAt = Chrono.readMoment(j.getString("endTime")),
            state = HangoutState.parse(j.textOrNull("status")),
            createdAt = Chrono.readMoment(j.getString("createdAt")),
            pastime = j.nodeOrNull("activity")?.let(Pastime::decode),
            host = j.nodeOrNull("creator")?.let(Member::decode),
            spot = j.nodeOrNull("building")?.let(CampusSpot::decode),
            lines = j.nodeList("messages", ChatLine::decode),
            attendees = j.nodeList("participants", HangoutAttendee::decode),
        )
    }
}

/** Participante de una mesa abierta con su RSVP (antes OpenTableParticipant). */
data class HangoutAttendee(
    val id: Int,
    val rsvp: Rsvp,
    val respondedAt: LocalDateTime? = null,
    val enjoyed: Boolean? = null,
    val hangout: Hangout? = null,
    val member: Member? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "rsvp" to rsvp.wire,
        "respondedAt" to respondedAt?.let(Chrono::writeMoment),
        "enjoyed" to enjoyed,
        "openTable" to hangout?.encode(),
        "user" to member?.encode(),
    )

    companion object {
        fun decode(j: JSONObject): HangoutAttendee = HangoutAttendee(
            id = j.intOr("id"),
            rsvp = Rsvp.parse(j.textOrNull("rsvp")),
            respondedAt = j.textOrNull("respondedAt")?.let(Chrono::readMoment),
            enjoyed = j.flagOrNull("enjoyed"),
            hangout = j.nodeOrNull("openTable")?.let(Hangout::decode),
            member = j.nodeOrNull("user")?.let(Member::decode)
                ?: j.intOrNull("userId")?.let(Member::stub),
        )
    }
}

/** Notificación (antes AppNotification). */
data class Alert(
    val id: Int,
    val kind: AlertKind,
    val referenceId: Int,
    val message: String,
    val seen: Boolean,
    val createdAt: LocalDateTime,
    val member: Member? = null,
) {
    companion object {
        fun decode(j: JSONObject) = Alert(
            id = j.intOr("id"),
            kind = AlertKind.parse(j.textOrNull("type")),
            referenceId = j.intOr("referenceId"),
            message = j.textOr("message"),
            seen = j.flagOr("read"),
            createdAt = Chrono.readMoment(j.getString("createdAt")),
            member = j.nodeOrNull("user")?.let(Member::decode),
        )
    }
}

/** Punto de ubicación registrado durante un hueco (antes UserLocationLog). */
data class LocationPing(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: LocalDateTime,
    val member: Member? = null,
    val window: FreeWindow? = null,
    val spot: CampusSpot? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "id" to id,
        "latitude" to latitude,
        "longitude" to longitude,
        "recordedAt" to Chrono.writeMoment(recordedAt),
        "user" to member?.encode(),
        "gap" to window?.encode(),
        "building" to spot?.encode(),
    )

    companion object {
        fun decode(j: JSONObject): LocationPing = LocationPing(
            id = j.intOr("id"),
            latitude = j.decimal("latitude"),
            longitude = j.decimal("longitude"),
            recordedAt = Chrono.readMoment(j.getString("recordedAt")),
            member = j.nodeOrNull("user")?.let(Member::decode),
            window = j.nodeOrNull("gap")?.let(FreeWindow::decode),
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
        fun decode(j: JSONObject) = LoginTicket(
            id = j.intOr("id"),
            accessToken = j.textOr("accessToken"),
            refreshToken = j.textOr("refreshToken"),
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
    val spotId: Int? = null,
) {
    fun encode(): JSONObject = jsonOf(
        "userId" to memberId,
        "step" to stage.wire,
        "activityId" to pastimeId,
        "durationMinutes" to durationMinutes,
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

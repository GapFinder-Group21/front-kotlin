package co.gapfinder.mobile.data

import android.util.Log
import co.gapfinder.mobile.domain.Alert
import co.gapfinder.mobile.domain.Bond
import co.gapfinder.mobile.domain.BondState
import co.gapfinder.mobile.domain.CampusSpot
import co.gapfinder.mobile.domain.ChatLine
import co.gapfinder.mobile.domain.Crew
import co.gapfinder.mobile.domain.DropOffStat
import co.gapfinder.mobile.domain.EnergyLevel
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.domain.HangoutAttendee
import co.gapfinder.mobile.domain.HangoutDropOff
import co.gapfinder.mobile.domain.Hobby
import co.gapfinder.mobile.domain.Lecture
import co.gapfinder.mobile.domain.LocationPing
import co.gapfinder.mobile.domain.LoginTicket
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.Pairing
import co.gapfinder.mobile.domain.PairingFocus
import co.gapfinder.mobile.domain.PairingProspect
import co.gapfinder.mobile.domain.PairingState
import co.gapfinder.mobile.domain.Pastime
import co.gapfinder.mobile.domain.Recommendation
import co.gapfinder.mobile.domain.SpotDwell
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.Reply
import co.gapfinder.mobile.foundation.ServerFault
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.expect
import co.gapfinder.mobile.foundation.isBlankPayload
import co.gapfinder.mobile.foundation.jsonOf
import co.gapfinder.mobile.foundation.mapNodes
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "Repo"

private fun encodeParam(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

private inline fun <T> Reply.many(mapper: (JSONObject) -> T): List<T> = jsonList.mapNodes(mapper)

private inline fun <T> Reply.oneOrNull(mapper: (JSONObject) -> T): T? =
    if (text.isBlankPayload()) null else mapper(json)

/**
 * Como [expect], pero el error lleva el mensaje del backend cuando lo manda:
 * texto plano (400/404/409) o JSON {"error": "..."}.
 */
private fun Reply.expectOrExplain(code: Int, fallback: String): Reply {
    if (this.code == code) return this
    val body = text.trim()
    if (body.isEmpty()) throw ServerFault("$fallback: ${this.code}")
    val explained = try {
        JSONObject(body).takeIf { it.has("error") && !it.isNull("error") }?.get("error")?.toString()
    } catch (ignored: Exception) {
        null // no es JSON, es texto plano
    }
    throw ServerFault(explained ?: body)
}

// ============================================================ Cuenta / autenticación

object AccountRepository {
    /** POST /auth/register — crea la cuenta y guarda la sesión. */
    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        career: String,
        semester: Int,
        energy: EnergyLevel,
        phoneNumber: String,
    ): LoginTicket {
        val reply = HttpGateway.sendAnonymous(
            "/auth/register",
            jsonOf(
                "name" to name,
                "email" to email.lowercase(),
                "password" to password,
                "career" to career,
                "semester" to semester,
                "preferredEffort" to energy.wire,
                "phoneNumber" to phoneNumber,
            ),
        )
        if (reply.code != 201 && reply.code != 200) throw ServerFault("Error al registrarse: ${reply.code}")

        // Si el registro ya trae tokens se guarda la sesión; si no, se inicia sesión
        val ticket = try {
            reply.json.takeIf { it.has("accessToken") && !it.isNull("accessToken") }?.let(LoginTicket::decode)
        } catch (ignored: Exception) {
            null
        }
        return if (ticket != null) persist(ticket) else signIn(email, password)
    }

    /** POST /auth/login */
    suspend fun signIn(email: String, password: String): LoginTicket {
        val reply = HttpGateway.sendAnonymous("/auth/login", jsonOf("email" to email, "password" to password))
            .expect(200, "Error al iniciar sesión")
        return persist(LoginTicket.decode(reply.json))
    }

    /** POST /auth/refresh */
    suspend fun renewSession(): LoginTicket {
        val reply = HttpGateway.sendAnonymous("/auth/refresh", jsonOf("refreshToken" to SessionVault.refreshToken()))
            .expect(200, "Error al refrescar la sesión")
        return persist(LoginTicket.decode(reply.json))
    }

    /** POST /auth/logout y limpia la sesión local. */
    suspend fun signOut() {
        HttpGateway.send("/auth/logout")
        SessionVault.wipe()
    }

    private suspend fun persist(ticket: LoginTicket): LoginTicket {
        SessionVault.store(ticket.accessToken, ticket.refreshToken, ticket.id)
        return ticket
    }
}

// ============================================================ Usuarios

object MemberRepository {
    suspend fun all(): List<Member> =
        HttpGateway.fetch("/users").expect(200, "Error getting users").many(Member::decode)

    suspend fun byId(id: Int): Member =
        Member.decode(HttpGateway.fetch("/users/$id").expect(200, "Error getting user").json)

    suspend fun add(member: Member): Member =
        Member.decode(HttpGateway.send("/users", member.encode()).expect(201, "Error creating user").json)

    suspend fun edit(id: Int, member: Member): Member =
        Member.decode(HttpGateway.overwrite("/users/$id", member.encode()).expect(200, "Error updating user").json)

    suspend fun drop(id: Int) {
        HttpGateway.erase("/users/$id").expect(204, "Error deleting user")
    }

    /** POST /users/{userId}/interests/{interestId} */
    suspend fun attachHobby(memberId: Int, hobbyId: Int) {
        HttpGateway.send("/users/$memberId/interests/$hobbyId").expect(200, "Error adding interest")
    }

    /** PATCH /users/{userId}/location/gps — el backend resuelve el edificio a partir de las coordenadas. */
    suspend fun relocate(memberId: Int, latitude: Double, longitude: Double) {
        HttpGateway.tweak("/users/$memberId/location/gps?latitude=$latitude&longitude=$longitude")
            .expect(200, "Error updating location")
    }

    /** GET /users/search?name=... */
    suspend fun searchByName(name: String): List<Member> =
        HttpGateway.fetch("/users/search?name=${encodeParam(name)}")
            .expect(200, "Error searching users").many(Member::decode)
}

// ============================================================ Intereses

object HobbyRepository {
    suspend fun all(): List<Hobby> =
        HttpGateway.fetch("/interests").expectOrExplain(200, "Error getting interests").many(Hobby::decode)

    suspend fun byId(id: Int): Hobby =
        Hobby.decode(HttpGateway.fetch("/interests/$id").expectOrExplain(200, "Error getting interest").json)

    /** El nombre es obligatorio y único. */
    suspend fun add(hobby: Hobby): Hobby =
        Hobby.decode(HttpGateway.send("/interests", hobby.encode()).expectOrExplain(201, "Error creating interest").json)

    suspend fun edit(id: Int, hobby: Hobby): Hobby =
        Hobby.decode(HttpGateway.overwrite("/interests/$id", hobby.encode()).expectOrExplain(200, "Error updating interest").json)

    suspend fun drop(id: Int) {
        HttpGateway.erase("/interests/$id").expectOrExplain(204, "Error deleting interest")
    }
}

// ============================================================ Actividades

object PastimeRepository {
    /** GET /activities */
    suspend fun catalog(): List<Pastime> {
        val reply = HttpGateway.fetch("/activities")
        Log.d(TAG, "API RESPONSE [/activities]: ${reply.code} - ${reply.text}")
        return reply.expect(200, "Error getting activities").many(Pastime::decode)
    }

    suspend fun byId(id: Int): Pastime =
        Pastime.decode(HttpGateway.fetch("/activities/$id").expect(200, "Error getting activity").json)

    suspend fun publish(pastime: Pastime): Pastime =
        Pastime.decode(HttpGateway.send("/activities", pastime.encode()).expect(201, "Error creating activity").json)

    suspend fun edit(id: Int, pastime: Pastime): Pastime =
        Pastime.decode(HttpGateway.overwrite("/activities/$id", pastime.encode()).expect(200, "Error updating activity").json)

    suspend fun drop(id: Int) {
        HttpGateway.erase("/activities/$id").expect(204, "Error deleting activity")
    }
}

// ============================================================ Edificios

object SpotRepository {
    suspend fun all(): List<CampusSpot> =
        HttpGateway.fetch("/buildings").expectOrExplain(200, "Error getting buildings").many(CampusSpot::decode)

    suspend fun byId(id: Int): CampusSpot =
        CampusSpot.decode(HttpGateway.fetch("/buildings/$id").expectOrExplain(200, "Error getting building").json)

    /** GET /buildings/locate — edificio que contiene esas coordenadas; null si el punto queda fuera de todos (404). */
    suspend fun locate(latitude: Double, longitude: Double): CampusSpot? {
        val reply = HttpGateway.fetch("/buildings/locate?latitude=$latitude&longitude=$longitude")
        if (reply.code == 404) return null
        return CampusSpot.decode(reply.expectOrExplain(200, "Error locating building").json)
    }

    suspend fun add(spot: CampusSpot): CampusSpot =
        CampusSpot.decode(HttpGateway.send("/buildings", spot.encode()).expectOrExplain(201, "Error creating building").json)

    suspend fun edit(id: Int, spot: CampusSpot): CampusSpot =
        CampusSpot.decode(HttpGateway.overwrite("/buildings/$id", spot.encode()).expectOrExplain(200, "Error updating building").json)

    suspend fun drop(id: Int) {
        HttpGateway.erase("/buildings/$id").expectOrExplain(204, "Error deleting building")
    }
}

// ============================================================ Horario (bloques de clase)

object LectureRepository {
    suspend fun byId(id: Int): Lecture =
        Lecture.decode(HttpGateway.fetch("/class-blocks/$id").expect(200, "Error getting class block").json)

    /** GET /class-blocks/user/{userId} */
    suspend fun timetableOf(memberId: Int): List<Lecture> =
        HttpGateway.fetch("/class-blocks/user/$memberId").expect(200, "Error getting schedule").many(Lecture::decode)

    suspend fun addTo(memberId: Int, lecture: Lecture): Lecture =
        Lecture.decode(HttpGateway.send("/class-blocks/user/$memberId", lecture.encode())
            .expect(201, "Error creating class block").json)

    suspend fun edit(id: Int, lecture: Lecture): Lecture =
        Lecture.decode(HttpGateway.overwrite("/class-blocks/$id", lecture.encode())
            .expect(200, "Error updating class block").json)

    suspend fun drop(id: Int) {
        HttpGateway.erase("/class-blocks/$id").expect(204, "Error deleting class block")
    }
}

// ============================================================ Huecos (gaps)

object WindowRepository {
    /** GET /gaps — los de todos los usuarios. */
    suspend fun all(): List<FreeWindow> =
        HttpGateway.fetch("/gaps").expectOrExplain(200, "Error getting gaps").many(FreeWindow::decode)

    suspend fun byId(id: Int): FreeWindow =
        FreeWindow.decode(HttpGateway.fetch("/gaps/$id").expectOrExplain(200, "Error getting gap").json)

    /** GET /gaps/user/{userId} — huecos de la semana del usuario (la actual si no se pasa weekStart). */
    suspend fun weekOf(memberId: Int, weekStart: LocalDate? = null): List<FreeWindow> {
        val query = weekStart?.let { "?weekStart=${Chrono.isoDay(it)}" } ?: ""
        return HttpGateway.fetch("/gaps/user/$memberId$query").expect(200, "Error getting user gaps").many(FreeWindow::decode)
    }

    /**
     * POST /gaps/user/{userId}/generate-week — calcula y guarda los huecos de la semana a partir del horario.
     * weekStart debe ser lunes; si se omite, el backend usa la semana actual (o la siguiente si hoy es domingo).
     */
    suspend fun generateWeek(memberId: Int, weekStart: LocalDate? = null): List<FreeWindow> {
        val query = weekStart?.let { "?weekStart=${Chrono.isoDay(it)}" } ?: ""
        return HttpGateway.send("/gaps/user/$memberId/generate-week$query")
            .expectOrExplain(201, "Error generating week gaps").many(FreeWindow::decode)
    }

    suspend fun add(memberId: Int, window: FreeWindow): FreeWindow =
        FreeWindow.decode(HttpGateway.send("/gaps", window.ownedBy(memberId)).expectOrExplain(201, "Error creating gap").json)

    suspend fun edit(id: Int, memberId: Int, window: FreeWindow): FreeWindow =
        FreeWindow.decode(HttpGateway.overwrite("/gaps/$id", window.ownedBy(memberId)).expectOrExplain(200, "Error updating gap").json)

    suspend fun drop(id: Int) {
        HttpGateway.erase("/gaps/$id").expectOrExplain(204, "Error deleting gap")
    }

    /** El dueño va plano (userId), como lo lee GapBasicDTO. */
    private fun FreeWindow.ownedBy(memberId: Int): JSONObject = encode().put("userId", memberId)
}

// ============================================================ Google Calendar

object CalendarBridge {
    /** GET /google/auth-url */
    suspend fun authorizationLink(): String =
        HttpGateway.fetch("/google/auth-url").expect(200, "Error al obtener la URL de Google").json.getString("url")

    /** POST /google/import-schedule */
    suspend fun pullTimetable(): Boolean {
        HttpGateway.send("/google/import-schedule").expect(200, "Error al importar el horario")
        return true
    }
}

// ============================================================ Amistades

object BondRepository {
    /** GET /friendships — las de todos los usuarios. */
    suspend fun all(): List<Bond> =
        HttpGateway.fetch("/friendships").expectOrExplain(200, "Error getting friendships").many(Bond::decode)

    suspend fun byId(id: Int): Bond =
        Bond.decode(HttpGateway.fetch("/friendships/$id").expectOrExplain(200, "Error getting friendship").json)

    /** GET /friendships/user/{userId}/friends — amigos ya aceptados. */
    suspend fun friendsOf(memberId: Int): List<Member> =
        HttpGateway.fetch("/friendships/user/$memberId/friends")
            .expectOrExplain(200, "Error getting friends").many(Member::decode)

    /** Solicitudes pendientes que recibió el usuario. Se filtran aquí porque GET /friendships trae las de todos. */
    suspend fun incomingFor(memberId: Int): List<Bond> =
        all().filter { it.receiverId == memberId && it.state == BondState.Waiting }

    /** POST /friendships — el backend la crea en PENDING. */
    suspend fun request(requesterId: Int, receiverId: Int): Bond =
        Bond.decode(
            HttpGateway.send("/friendships", jsonOf("requesterId" to requesterId, "receiverId" to receiverId))
                .expectOrExplain(201, "Error sending friend request").json
        )

    /** PATCH /friendships/{id}/accept?userId= */
    suspend fun approve(id: Int, memberId: Int): Bond =
        Bond.decode(HttpGateway.tweak("/friendships/$id/accept?userId=$memberId")
            .expectOrExplain(200, "Error accepting friend request").json)

    /** PATCH /friendships/{id}/reject?userId= */
    suspend fun decline(id: Int, memberId: Int): Bond =
        Bond.decode(HttpGateway.tweak("/friendships/$id/reject?userId=$memberId")
            .expectOrExplain(200, "Error rejecting friend request").json)

    suspend fun dissolve(id: Int) {
        HttpGateway.erase("/friendships/$id").expectOrExplain(204, "Error deleting friendship")
    }
}

// ============================================================ Amigos cercanos

object NearbyRepository {
    /** GET /nearby-friends/user/{userId} — amigos que están ahora en el mismo edificio. */
    suspend fun friendsNear(memberId: Int): List<Member> =
        HttpGateway.fetch("/nearby-friends/user/$memberId").expect(200, "Error getting nearby friends").many(Member::decode)

    /** PUT /nearby-friends/user/{userId}/location — actualiza la ubicación por GPS y devuelve los amigos cercanos. */
    suspend fun moveTo(memberId: Int, latitude: Double, longitude: Double): List<Member> =
        HttpGateway.overwrite("/nearby-friends/user/$memberId/location?latitude=$latitude&longitude=$longitude")
            .expect(200, "Error updating location by coordinates").many(Member::decode)

    /** PUT /nearby-friends/user/{userId}/building/{buildingId} — fija el edificio directamente. */
    suspend fun moveToSpot(memberId: Int, spotId: Int): List<Member> =
        HttpGateway.overwrite("/nearby-friends/user/$memberId/building/$spotId")
            .expect(200, "Error updating location by building").many(Member::decode)
}

// ============================================================ Grupos

object CrewRepository {
    suspend fun all(): List<Crew> =
        HttpGateway.fetch("/groups").expect(200, "Error getting groups").many(Crew::decode)

    suspend fun of(memberId: Int): List<Crew> =
        HttpGateway.fetch("/groups/user?userId=$memberId").expect(200, "Error getting user groups").many(Crew::decode)

    suspend fun byId(id: Int): Crew =
        Crew.decode(HttpGateway.fetch("/groups/$id").expect(200, "Error getting group").json)

    suspend fun found(founderId: Int, crew: Crew): Crew =
        Crew.decode(HttpGateway.send("/groups?creatorId=$founderId", crew.encode()).expect(201, "Error creating group").json)

    suspend fun edit(id: Int, crew: Crew): Crew =
        Crew.decode(HttpGateway.overwrite("/groups/$id", crew.encode()).expect(200, "Error updating group").json)

    suspend fun disband(id: Int) {
        HttpGateway.erase("/groups/$id").expect(204, "Error deleting group")
    }

    /** POST /groups/{id}/members — agrega a un amigo como miembro. */
    suspend fun enlist(id: Int, requesterId: Int, newMemberId: Int): Crew =
        Crew.decode(HttpGateway.send("/groups/$id/members?requesterId=$requesterId&newMemberId=$newMemberId")
            .expect(200, "Error adding member").json)
}

// ============================================================ Matches

object PairingRepository {
    /** GET /matches/gap/{gapId}/candidates — candidatos para un hueco, priorizados según el modo. */
    suspend fun prospects(windowId: Int, focus: PairingFocus): List<PairingProspect> =
        HttpGateway.fetch(
            "/matches/gap/$windowId/candidates" +
                "?useSameCareer=${focus == PairingFocus.Career}" +
                "&useSharedInterests=${focus == PairingFocus.Interests}" +
                "&useEffort=${focus == PairingFocus.Energy}"
        ).expectOrExplain(200, "Error getting candidates").many(PairingProspect::decode)

    /** POST /matches/request — el score debe ser el que devolvió [prospects]. */
    suspend fun propose(proposerWindowId: Int, acceptorWindowId: Int, score: Double): Pairing =
        Pairing.decode(
            HttpGateway.send("/matches/request?proposerGapId=$proposerWindowId&acceptorGapId=$acceptorWindowId&score=$score")
                .expectOrExplain(201, "Error sending match request").json
        )

    suspend fun all(): List<Pairing> =
        HttpGateway.fetch("/matches").expectOrExplain(200, "Error getting matches").many(Pairing::decode)

    suspend fun byId(id: Int): Pairing =
        Pairing.decode(HttpGateway.fetch("/matches/$id").expectOrExplain(200, "Error getting match").json)

    /** GET /matches/user/{userId}/pending — solicitudes que recibió el usuario. */
    suspend fun incomingFor(memberId: Int): List<Pairing> =
        HttpGateway.fetch("/matches/user/$memberId/pending")
            .expectOrExplain(200, "Error getting pending matches").many(Pairing::decode)

    /** PATCH /matches/{id}/accept?userId= */
    suspend fun approve(id: Int, memberId: Int): Pairing =
        Pairing.decode(HttpGateway.tweak("/matches/$id/accept?userId=$memberId")
            .expectOrExplain(200, "Error accepting match").json)

    /** PATCH /matches/{id}/reject?userId= */
    suspend fun decline(id: Int, memberId: Int): Pairing =
        Pairing.decode(HttpGateway.tweak("/matches/$id/reject?userId=$memberId")
            .expectOrExplain(200, "Error rejecting match").json)

    /** PATCH /matches/{id}/complete — marca como terminado un match aceptado. */
    suspend fun complete(id: Int): Pairing =
        Pairing.decode(HttpGateway.tweak("/matches/$id/complete").expectOrExplain(200, "Error completing match").json)

    /** Match aceptado (ACCEPTED) en el que participa el usuario, si existe. */
    suspend fun ongoingFor(memberId: Int): Pairing? =
        all().firstOrNull { it.involves(memberId) && it.state == PairingState.Live }
}

// ============================================================ Mensajes

object ChatLineRepository {
    /** POST /messages?senderId=...&matchId=...&openTableId=...&content=... */
    suspend fun post(senderId: Int, pairingId: Int? = null, hangoutId: Int? = null, content: String): ChatLine {
        val query = buildList {
            add("senderId=$senderId")
            pairingId?.let { add("matchId=$it") }
            hangoutId?.let { add("openTableId=$it") }
            add("content=${encodeParam(content)}")
        }.joinToString("&")
        return ChatLine.decode(HttpGateway.send("/messages?$query").expect(201, "Error sending message").json)
    }

    suspend fun forPairing(pairingId: Int): List<ChatLine> =
        HttpGateway.fetch("/messages/match/$pairingId").expect(200, "Error getting match messages").many(ChatLine::decode)

    suspend fun forHangout(hangoutId: Int): List<ChatLine> =
        HttpGateway.fetch("/messages/opentable/$hangoutId").expect(200, "Error getting open table messages").many(ChatLine::decode)

    suspend fun byId(id: Int): ChatLine =
        ChatLine.decode(HttpGateway.fetch("/messages/$id").expect(200, "Error getting message").json)
}

// ============================================================ Notificaciones

object AlertRepository {
    /** GET /api/notifications/user/{userId} */
    suspend fun inboxOf(memberId: Int): List<Alert> =
        HttpGateway.fetch("/api/notifications/user/$memberId").expect(200, "Error getting notifications").many(Alert::decode)

    /** GET /api/notifications/user/{userId}/unread */
    suspend fun unseenOf(memberId: Int): List<Alert> =
        HttpGateway.fetch("/api/notifications/user/$memberId/unread")
            .expect(200, "Error getting unread notifications").many(Alert::decode)

    /** PUT /api/notifications/{notificationId}/read?userId= */
    suspend fun markSeen(id: Int, memberId: Int): Alert =
        Alert.decode(HttpGateway.overwrite("/api/notifications/$id/read?userId=$memberId")
            .expect(200, "Error marking notification as read").json)

    /** PUT /api/notifications/user/{userId}/read-all */
    suspend fun markAllSeen(memberId: Int) {
        val reply = HttpGateway.overwrite("/api/notifications/user/$memberId/read-all")
        if (reply.code != 204 && reply.code != 200) throw ServerFault("Error marking all as read: ${reply.code}")
    }
}

// ============================================================ Mesas abiertas

object HangoutRepository {
    suspend fun byId(id: Int): Hangout =
        Hangout.decode(HttpGateway.fetch("/open-tables/$id").expectOrExplain(200, "Error getting open table").json)

    /** GET /open-tables */
    suspend fun all(): List<Hangout> =
        HttpGateway.fetch("/open-tables").expectOrExplain(200, "Error getting open tables").many(Hangout::decode)

    /** GET /open-tables/count?since=yyyy-MM-ddTHH:mm:ss — cuántas mesas se crearon desde esa fecha. */
    suspend fun countSince(since: LocalDateTime): Int =
        HttpGateway.fetch("/open-tables/count?since=${since.format(PLAIN_MOMENT)}")
            .expectOrExplain(200, "Error counting open tables").text.trim().toInt()

    /**
     * POST /open-tables — el backend exige creador, edificio, título, inicio antes del fin,
     * cupo mayor a 0 y estado.
     */
    suspend fun host(hostId: Int, spotId: Int, draft: Hangout): Hangout =
        Hangout.decode(
            HttpGateway.send("/open-tables", draft.withRelations(hostId, spotId))
                .expectOrExplain(201, "Error creating open table").json
        )

    suspend fun edit(id: Int, hostId: Int, spotId: Int, hangout: Hangout): Hangout =
        Hangout.decode(
            HttpGateway.overwrite("/open-tables/$id", hangout.withRelations(hostId, spotId))
                .expectOrExplain(200, "Error updating open table").json
        )

    suspend fun drop(id: Int) {
        HttpGateway.erase("/open-tables/$id").expectOrExplain(204, "Error deleting open table")
    }

    /** Formato de OpenTableCompleteDTO: creador y edificio anidados con su id. */
    private fun Hangout.withRelations(hostId: Int, spotId: Int): JSONObject =
        encode()
            .put("creator", jsonOf("id" to hostId))
            .put("building", jsonOf("id" to spotId))

    // Sin zona ni fracciones de segundo, que es lo que acepta "since"
    private val PLAIN_MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
}

// ============================================================ Participantes de mesa

object AttendeeRepository {
    suspend fun all(): List<HangoutAttendee> =
        HttpGateway.fetch("/open-table-participants").expect(200, "Error getting participants").many(HangoutAttendee::decode)

    suspend fun byId(id: Int): HangoutAttendee =
        HangoutAttendee.decode(HttpGateway.fetch("/open-table-participants/$id").expect(200, "Error getting participant").json)

    /** GET /open-table-participants/table/{tableId} */
    suspend fun of(hangoutId: Int): List<HangoutAttendee> =
        HttpGateway.fetch("/open-table-participants/table/$hangoutId")
            .expectOrExplain(200, "Error getting participants of the table").many(HangoutAttendee::decode)

    /** POST /open-table-participants/table/{tableId}/join?userId= */
    suspend fun join(hangoutId: Int, memberId: Int): HangoutAttendee =
        HangoutAttendee.decode(
            HttpGateway.send("/open-table-participants/table/$hangoutId/join?userId=$memberId")
                .expectOrExplain(201, "Error joining the table").json
        )

    /** DELETE /open-table-participants/table/{tableId}/leave?userId= */
    suspend fun leave(hangoutId: Int, memberId: Int) {
        HttpGateway.erase("/open-table-participants/table/$hangoutId/leave?userId=$memberId")
            .expectOrExplain(204, "Error leaving the table")
    }

    suspend fun drop(id: Int) {
        HttpGateway.erase("/open-table-participants/$id").expect(204, "Error deleting participant")
    }
}

// ============================================================ Abandonos del formulario de mesa

object DropOffRepository {
    suspend fun log(dropOff: HangoutDropOff) {
        HttpGateway.send("/open-table-abandonments", dropOff.encode()).expect(201, "Error registering abandonment")
    }

    /** GET /open-table-abandonments/stats?since= — abandonos por paso desde esa fecha. */
    suspend fun stats(since: LocalDateTime): List<DropOffStat> =
        HttpGateway.fetch("/open-table-abandonments/stats?since=${encodeParam(Chrono.writeMoment(since))}")
            .expect(200, "Error getting abandonment stats").many(DropOffStat::decode)
}

// ============================================================ Recomendaciones

object RecommendationRepository {
    /** GET /recommendations/user/{userId}/open-tables — mesas en el edificio que más frecuenta el usuario. */
    suspend fun hangoutsFor(memberId: Int): Recommendation {
        val reply = HttpGateway.fetch("/recommendations/user/$memberId/open-tables")
        Log.d(TAG, "REC userId=$memberId status=${reply.code} body=${reply.text}")
        return Recommendation.decode(reply.expect(200, "Error getting recommendations").json)
    }
}

// ============================================================ Registros de ubicación

object PingRepository {
    suspend fun record(memberId: Int, windowId: Int, latitude: Double, longitude: Double): LocationPing =
        LocationPing.decode(
            HttpGateway.send("/user-location-logs?userId=$memberId&gapId=$windowId&latitude=$latitude&longitude=$longitude")
                .expect(201, "Error creating checkpoint").json
        )

    suspend fun trail(windowId: Int): List<LocationPing> =
        HttpGateway.fetch("/user-location-logs/gap/$windowId").expect(200, "Error getting checkpoints").many(LocationPing::decode)

    suspend fun dwellPerSpot(windowId: Int): List<SpotDwell> =
        HttpGateway.fetch("/user-location-logs/gap/$windowId/time-per-building")
            .expect(200, "Error getting time per building").many(SpotDwell::decode)

    suspend fun favoriteSpot(memberId: Int): CampusSpot? =
        HttpGateway.fetch("/user-location-logs/user/$memberId/favorite-building")
            .expect(200, "Error getting favorite building").oneOrNull(CampusSpot::decode)

    suspend fun topSpot(windowId: Int): CampusSpot? =
        HttpGateway.fetch("/user-location-logs/gap/$windowId/top-building")
            .expect(200, "Error getting top building").oneOrNull(CampusSpot::decode)
}

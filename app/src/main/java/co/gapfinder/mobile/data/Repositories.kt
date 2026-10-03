package co.gapfinder.mobile.data

import android.util.Log
import co.gapfinder.mobile.domain.CampusSpot
import co.gapfinder.mobile.domain.EnergyLevel
import co.gapfinder.mobile.domain.Hobby
import co.gapfinder.mobile.domain.LoginTicket
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.Pastime
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.Reply
import co.gapfinder.mobile.foundation.ServerFault
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.expect
import co.gapfinder.mobile.foundation.jsonOf
import co.gapfinder.mobile.foundation.mapNodes
import org.json.JSONObject
import java.net.URLEncoder

private const val TAG = "Repo"

private fun encodeParam(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

private inline fun <T> Reply.many(mapper: (JSONObject) -> T): List<T> = jsonList.mapNodes(mapper)

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

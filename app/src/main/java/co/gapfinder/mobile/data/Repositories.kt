package co.gapfinder.mobile.data

import co.gapfinder.mobile.domain.EnergyLevel
import co.gapfinder.mobile.domain.LoginTicket
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.foundation.HttpGateway
import co.gapfinder.mobile.foundation.Reply
import co.gapfinder.mobile.foundation.ServerFault
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.expect
import co.gapfinder.mobile.foundation.jsonOf
import co.gapfinder.mobile.foundation.mapNodes
import org.json.JSONObject
import java.net.URLEncoder

private fun encodeParam(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

private inline fun <T> Reply.many(mapper: (JSONObject) -> T): List<T> = jsonList.mapNodes(mapper)

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

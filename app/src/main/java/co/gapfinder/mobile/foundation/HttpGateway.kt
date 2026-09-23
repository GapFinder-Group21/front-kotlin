package co.gapfinder.mobile.foundation

import android.util.Log
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Respuesta cruda del servidor: código HTTP + cuerpo ya decodificado en UTF-8. */
data class Reply(val code: Int, val text: String) {
    val json: JSONObject get() = JSONObject(text)
    val jsonList get() = org.json.JSONArray(text)
}

class ServerFault(message: String) : Exception(message)

/**
 * Cliente HTTP autenticado. Añade el Bearer token, refresca una vez si llega 401
 * y fuerza el cierre de sesión cuando la sesión ya no es válida (401/403).
 */
object HttpGateway {
    private val JSON_TYPE = "application/json".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(path: String): Reply = withToken { Request.Builder().url(url(path)).get() }

    suspend fun send(path: String, body: Any? = null): Reply =
        withToken { Request.Builder().url(url(path)).post(payload(body)) }

    suspend fun overwrite(path: String, body: Any? = null): Reply =
        withToken { Request.Builder().url(url(path)).put(payload(body)) }

    suspend fun tweak(path: String, body: Any? = null): Reply =
        withToken { Request.Builder().url(url(path)).patch(payload(body)) }

    suspend fun erase(path: String): Reply = withToken { Request.Builder().url(url(path)).delete() }

    /** POST sin token (login / registro / refresh). */
    suspend fun sendAnonymous(path: String, body: Any?): Reply = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url(path)).post(payload(body)).build())
    }

    private fun url(path: String) = "${ServerEnv.BASE_URL}$path"

    private fun payload(body: Any?): RequestBody =
        (body?.toString() ?: "").toRequestBody(JSON_TYPE)

    private fun execute(request: Request): Reply =
        client.newCall(request).execute().use { res ->
            Reply(res.code, res.body?.bytes()?.toString(Charsets.UTF_8) ?: "")
        }

    private suspend fun withToken(build: () -> Request.Builder): Reply = withContext(Dispatchers.IO) {
        val token = SessionVault.accessToken()
        var reply = execute(build().header("Authorization", "Bearer $token").build())

        if (reply.code == 401) {
            val fresh = renewAccess()
            if (fresh != null) {
                reply = execute(build().header("Authorization", "Bearer $fresh").build())
            }
        }

        if (reply.code == 401 || reply.code == 403) kickOut()
        reply
    }

    private suspend fun kickOut() {
        Log.w("HttpGateway", "🚨 SESSION EXPIRED: Forced logout triggered (401/403)")
        SessionVault.wipe()
        withContext(Dispatchers.Main) { StackNavigator.resetTo(Destination.Landing) }
    }

    /** Pide un access token nuevo con el refresh token guardado. */
    private suspend fun renewAccess(): String? {
        val refresh = SessionVault.refreshToken() ?: return null
        val reply = execute(
            Request.Builder()
                .url(url("/auth/refresh"))
                .post(jsonOf("refreshToken" to refresh).toString().toRequestBody(JSON_TYPE))
                .build()
        )
        if (reply.code != 200) {
            SessionVault.wipe()
            return null
        }
        val access = reply.json.optString("accessToken")
        SessionVault.rotateAccess(access)
        return access
    }
}

/** Lanza [ServerFault] si el código no es el esperado. */
fun Reply.expect(code: Int, what: String): Reply {
    if (this.code != code) throw ServerFault("$what: ${this.code}")
    return this
}

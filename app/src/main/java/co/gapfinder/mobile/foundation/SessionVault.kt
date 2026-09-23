package co.gapfinder.mobile.foundation

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Guarda tokens y el id del usuario de forma cifrada (equivalente a flutter_secure_storage). */
object SessionVault {
    private const val FILE = "gapfinder_vault"
    private const val KEY_ACCESS = "accessToken"
    private const val KEY_REFRESH = "refreshToken"
    private const val KEY_MEMBER = "userId"

    private lateinit var prefs: SharedPreferences

    fun attach(context: Context) {
        if (::prefs.isInitialized) return
        prefs = try {
            val master = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE,
                master,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (ignored: Exception) {
            context.getSharedPreferences("${FILE}_plain", Context.MODE_PRIVATE)
        }
    }

    /** Guarda tokens + id tras login / registro / refresh. */
    suspend fun store(access: String, refresh: String, memberId: Int) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_ACCESS, access)
            .putString(KEY_REFRESH, refresh)
            .putString(KEY_MEMBER, memberId.toString())
            .commit()
    }

    /** Solo reemplaza el access token (el id no cambia tras un refresh). */
    suspend fun rotateAccess(access: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_ACCESS, access).commit()
    }

    suspend fun accessToken(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY_ACCESS, null) }

    suspend fun refreshToken(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY_REFRESH, null) }

    suspend fun memberId(): Int? = withContext(Dispatchers.IO) { prefs.getString(KEY_MEMBER, null)?.toIntOrNull() }

    suspend fun isSignedIn(): Boolean = refreshToken() != null

    /** Borra todo (logout). */
    suspend fun wipe() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_MEMBER).commit()
    }
}

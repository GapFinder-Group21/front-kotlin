package co.gapfinder.mobile.foundation

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cómo carga Coil las imágenes (fotos de open tables, avatares). Lo usa GapFinderApplication
 * como ImageLoader de toda la app, así que cualquier AsyncImage pasa por aquí.
 *
 * - Caché en memoria: 20 % de la memoria de la app, para no volver a decodificar al hacer scroll.
 * - Caché en disco: hasta 100 MB en cacheDir/image_cache. Una foto que ya se vio no se vuelve a
 *   bajar, y se puede ver sin red. Si Android necesita espacio puede borrar cacheDir; Coil la vuelve a bajar.
 */
object ImageCaching {
    private const val DISK_FOLDER = "image_cache"
    private const val DISK_BYTES = 100L * 1024 * 1024

    fun loader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(DISK_FOLDER))
                    .maxSizeBytes(DISK_BYTES)
                    .build()
            }
            // Mismo cliente que HttpGateway (timeouts) y el token cuando la foto la sirve nuestro backend
            .okHttpClient {
                HttpGateway.client.newBuilder()
                    .addInterceptor(ServerAuth)
                    .build()
            }
            // Si el backend no manda Cache-Control, Coil igual guarda y reusa la foto del disco
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()

    /** Agrega el Bearer token solo a las peticiones de nuestro backend (no a URLs externas). */
    private object ServerAuth : Interceptor {
        private val host = ServerEnv.BASE_URL.toHttpUrl().host

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.url.host != host || request.header("Authorization") != null) return chain.proceed(request)
            // Corre en un hilo de OkHttp, no en el principal, así que se puede esperar el token
            val token = runBlocking { SessionVault.accessToken() } ?: return chain.proceed(request)
            return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
        }
    }
}

package co.gapfinder.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.data.outbox.Outbox
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.SessionVault

class GapFinderApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        SessionVault.attach(this)
        Connectivity.attach(this)
        LocalStore.attach(this)
        Outbox.attach(this)
    }

    /** Coil guarda las fotos en memoria (25 % de la RAM disponible) y en disco (50 MB), para verlas sin red. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .build()
}

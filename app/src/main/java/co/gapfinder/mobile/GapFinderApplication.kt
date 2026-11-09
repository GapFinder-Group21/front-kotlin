package co.gapfinder.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.data.outbox.Outbox
import co.gapfinder.mobile.foundation.AmbientLightMonitor
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.ImageCaching
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.theme.ThemeSettings
import co.gapfinder.mobile.data.local.MobilityStore
import co.gapfinder.mobile.data.MobilityRepository

class GapFinderApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        SessionVault.attach(this)
        Connectivity.attach(this)
        LocalStore.attach(this)
        MobilityStore.attach(this)
        MobilityRepository.registerCourier()
        Outbox.attach(this)
        AmbientLightMonitor.attach(this)
        ThemeSettings.attach(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageCaching.loader(this)
}
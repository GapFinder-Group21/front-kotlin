package co.gapfinder.mobile

import android.app.Application
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.data.outbox.Outbox
import co.gapfinder.mobile.foundation.SessionVault

class GapFinderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionVault.attach(this)
        LocalStore.attach(this)
        Outbox.attach(this)
    }
}

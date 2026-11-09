package co.gapfinder.mobile

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.gapfinder.mobile.data.GeoProbe
import co.gapfinder.mobile.foundation.AmbientLightMonitor
import co.gapfinder.mobile.ui.nav.AppStage
import co.gapfinder.mobile.ui.theme.GapFinderTheme
import co.gapfinder.mobile.ui.theme.rememberDarkTheme

/** Única Activity: aloja toda la UI en Compose. */
class HostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        GeoProbe.bind(this)
        setContent {
            GapFinderTheme(darkTheme = rememberDarkTheme()) {
                AppStage()
            }
        }
    }

    // El sensor solo se escucha con la app en primer plano, para no gastar batería
    override fun onResume() {
        super.onResume()
        AmbientLightMonitor.start()
    }

    override fun onPause() {
        AmbientLightMonitor.stop()
        super.onPause()
    }

    override fun onDestroy() {
        GeoProbe.unbind(this)
        super.onDestroy()
    }
}

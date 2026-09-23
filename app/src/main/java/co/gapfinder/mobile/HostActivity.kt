package co.gapfinder.mobile

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import co.gapfinder.mobile.data.GeoProbe
import co.gapfinder.mobile.ui.nav.AppStage
import co.gapfinder.mobile.ui.theme.GapFinderTheme

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
            GapFinderTheme {
                AppStage()
            }
        }
    }

    override fun onDestroy() {
        GeoProbe.unbind(this)
        super.onDestroy()
    }
}

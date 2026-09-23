package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import co.gapfinder.mobile.R
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** Pestañas de la barra inferior. */
enum class DockTab(val key: String) { Schedule("schedule"), Friends("friends"), Match("match"), Map("map"), Profile("profile") }

/** Barra de navegación inferior con el botón central de Match (antes BottomNav). */
@Composable
fun DockBar(active: DockTab, onSelect: (DockTab) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .background(Palette.Ink)
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            DockItem(DockTab.Schedule, "Schedule", Glyphs.calendarTodayOutlined, active, onSelect)
            DockItem(DockTab.Friends, "Friends", Glyphs.peopleOutlineRounded, active, onSelect)
            CenterMatchItem(onSelect)
            DockItem(DockTab.Map, "Open Tables", Glyphs.locationOnOutlined, active, onSelect)
            DockItem(DockTab.Profile, "Profile", Glyphs.personOutlineRounded, active, onSelect)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.DockItem(
    tab: DockTab,
    label: String,
    icon: ImageVector,
    active: DockTab,
    onSelect: (DockTab) -> Unit,
) {
    val isActive = active == tab
    val tint = if (isActive) Palette.Fog else Palette.Fog.fade(0.5f)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .inkTap { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            if (isActive) {
                // Punto indicador debajo del ícono (bottom: -6)
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 6.dp)
                        .size(4.dp)
                        .background(Palette.Fog, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = Typo.paragraph(if (isActive) FontWeight.Bold else FontWeight.Normal).copy(
                fontSize = 10.sp,
                color = tint,
                letterSpacing = 0.2.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CenterMatchItem(onSelect: (DockTab) -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .plainTap { onSelect(DockTab.Match) },
    ) {
        // Círculo con borde tricolor que sobresale 24dp por encima de la barra
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(62.dp)
                .background(
                    Brush.sweepGradient(
                        0.0f to Palette.Sky, 0.33f to Palette.Sky,
                        0.33f to Palette.Teal, 0.66f to Palette.Teal,
                        0.66f to Palette.Coral, 1.0f to Palette.Coral,
                    ),
                    CircleShape,
                )
                .padding(3.dp)
        ) {
            Box(
                Modifier.fillMaxSize().background(Palette.Ink, CircleShape).padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.brand_mark),
                    contentDescription = "Match",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = "Match",
            style = Typo.heading(FontWeight.ExtraBold).copy(
                fontSize = 10.sp,
                color = Palette.White,
                letterSpacing = 0.5.sp,
            ),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

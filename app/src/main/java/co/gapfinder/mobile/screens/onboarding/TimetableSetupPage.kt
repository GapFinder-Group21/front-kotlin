package co.gapfinder.mobile.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.ui.kit.ChoiceTile
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** Elección de cómo cargar el horario (Google o manual). */
@Composable
fun TimetableSetupPage(onNavigate: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
    ) {
        // maybePop: solo vuelve si hay algo debajo
        InkHeader(title = "GAP FINDER", onBack = { StackNavigator.back() })

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = "Set up your schedule",
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 22.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "How would you like to add your classes?",
                style = Typo.label().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)),
            )
            Spacer(Modifier.height(32.dp))

            // "Connect Google Calendar" (CalendarLinkPage) queda oculto: el
            // backend no tiene los endpoints /google/*.

            ChoiceTile(
                title = "Add Schedule Manually",
                subtitle = "Enter your classes one by one",
                icon = { OptionBadge(Palette.Teal, Glyphs.editCalendarRounded) },
                onTap = { onNavigate("manualSchedule") },
            )
        }
    }
}

@Composable
private fun OptionBadge(tone: Color, glyph: ImageVector) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(tone, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = Palette.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

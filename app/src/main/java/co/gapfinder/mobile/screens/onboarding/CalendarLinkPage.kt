package co.gapfinder.mobile.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.CalendarBridge
import co.gapfinder.mobile.foundation.LinkOpener
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.launch

/** Texto de error al estilo de Dart (`Exception: ...`). */
private fun Throwable.linkDartText(): String = "Exception: ${message ?: ""}"

/** Abre el login de Google en el navegador y luego pasa a importar. */
@Composable
fun CalendarLinkPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(false) }
    var browserShown by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun launchGoogleSignIn() {
        waiting = true
        problem = null
        scope.launch {
            try {
                val link = CalendarBridge.authorizationLink()
                val ok = LinkOpener.openExternally(context, link)
                if (!ok) throw Exception("No se pudo abrir el navegador")
                browserShown = true
                waiting = false
            } catch (e: Exception) {
                problem = e.linkDartText()
                waiting = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
    ) {
        InkHeader(title = "Google Calendar", showBack = true)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Palette.Sky, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Glyphs.calendarMonthRounded,
                    contentDescription = null,
                    tint = Palette.White,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Connect Google Calendar",
                textAlign = TextAlign.Center,
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 20.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (browserShown) {
                    "Finish signing in with your Google account in the browser, then come back and tap Continue."
                } else {
                    "You'll be taken to Google to sign in and authorize access to your calendar."
                },
                textAlign = TextAlign.Center,
                style = Typo.paragraph(FontWeight.Normal).copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)),
            )
            problem?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = msg,
                    textAlign = TextAlign.Center,
                    style = Typo.paragraph(FontWeight.Normal).copy(fontSize = 13.sp, color = Palette.Coral),
                )
            }
            Spacer(Modifier.height(40.dp))

            when {
                waiting -> CircularProgressIndicator(Modifier.size(36.dp))
                !browserShown -> PillButton(
                    label = "Continue with Google",
                    color = Palette.Sky,
                    onClick = { launchGoogleSignIn() },
                )
                else -> {
                    PillButton(
                        label = "Continue",
                        color = Palette.Sky,
                        onClick = { StackNavigator.swap(Destination.CalendarImport) },
                    )
                    Spacer(Modifier.height(12.dp))
                    RetryLink(onTap = { launchGoogleSignIn() })
                }
            }
        }
    }
}

/** TextButton por defecto de M3: 48 de área táctil, mínimo 64x40, padding 12/8. */
@Composable
private fun RetryLink(onTap: () -> Unit) {
    Box(
        modifier = Modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 64.dp, minHeight = 40.dp)
                .inkTap(RoundedCornerShape(50), onTap = onTap)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Open Google sign-in again",
                style = Typo.paragraph(FontWeight.Normal).copy(fontSize = 13.sp, color = Palette.Ink.fade(0.5f)),
            )
        }
    }
}

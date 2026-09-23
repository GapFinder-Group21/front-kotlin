package co.gapfinder.mobile.ui.kit

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.foundation.LiveChatHub
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.delay

/** Burbuja flotante del chat activo con cuenta regresiva (antes FloatingChatButton). */
@Composable
fun BoxScope.ChatFab() {
    // Re-dibuja cada segundo para refrescar el contador
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            tick++
        }
    }
    val session = LiveChatHub.window
    if (session == null || !session.isOpen) return

    val left = session.remaining.also { tick } // leer tick fuerza el refresco cada segundo
    val minutes = left.toMinutes()
    val seconds = left.seconds % 60
    val urgent = minutes < 10

    val loop = rememberInfiniteTransition(label = "fabPulse")
    val scale by loop.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 100.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(64.dp)
            .shadow(12.dp, CircleShape, ambientColor = Palette.Teal.fade(0.4f), spotColor = Palette.Teal.fade(0.4f))
            .background(Palette.Teal, CircleShape)
            .plainTap {
                StackNavigator.go(Destination.Conversation(session.pairingId, session.hangoutId))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Glyphs.chatBubbleRounded, contentDescription = "Chat", tint = Palette.White, modifier = Modifier.size(28.dp))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .background(if (urgent) Palette.Coral else Palette.Ink, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$minutes:${seconds.toString().padStart(2, '0')}",
                style = Typo.paragraph(FontWeight.ExtraBold).copy(fontSize = 9.sp, color = Palette.White),
            )
        }
    }
}

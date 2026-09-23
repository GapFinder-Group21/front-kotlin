package co.gapfinder.mobile.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Mensaje flotante inferior (equivalente a ScaffoldMessenger.showSnackBar). */
object Toaster {
    data class Toast(
        val id: Long,
        val text: String,
        val background: Color,
        val style: TextStyle?,
        val durationMs: Long,
    )

    private var seq = 0L
    var current by mutableStateOf<Toast?>(null)
        private set

    val DefaultBackground = Color(0xFF322F35)

    fun show(
        text: String,
        background: Color = DefaultBackground,
        style: TextStyle? = null,
        durationMs: Long = 4_000,
    ) {
        current = Toast(++seq, text, background, style, durationMs)
    }

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

@Composable
fun BoxScope.ToastHost() {
    val toast = Toaster.current
    LaunchedEffect(toast?.id) {
        val t = toast ?: return@LaunchedEffect
        delay(t.durationMs)
        Toaster.dismiss(t.id)
    }
    AnimatedVisibility(
        visible = toast != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val t = toast ?: return@AnimatedVisibility
        Box(
            Modifier
                .fillMaxWidth()
                .background(t.background)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = t.text,
                style = t.style ?: TextStyle(fontSize = 14.sp, color = Color(0xFFF5EFF7)),
            )
        }
    }
}

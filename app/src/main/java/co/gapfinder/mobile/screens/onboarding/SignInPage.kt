package co.gapfinder.mobile.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.AccountRepository
import co.gapfinder.mobile.ui.kit.FormField
import co.gapfinder.mobile.ui.kit.NightHeader
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.launch

/** Inicio de sesión con email y contraseña. */
@Composable
fun SignInPage() {
    val scope = rememberCoroutineScope()
    var mailText by remember { mutableStateOf("") }
    var keyText by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    fun attemptEntry() {
        val address = mailText.trim()
        val key = keyText
        if (address.isEmpty() || key.isEmpty()) {
            Toaster.show("Please fill in all fields")
            return
        }
        working = true
        scope.launch {
            try {
                // Guarda los tokens en SessionVault
                AccountRepository.signIn(address, key)
                // Limpia la pila y va al inicio
                StackNavigator.resetTo(Destination.Home)
            } catch (e: Exception) {
                Toaster.show(e.message ?: e.toString(), background = Color(0xFFFF5252))
            } finally {
                working = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
    ) {
        NightHeader(title = "GAP FINDER", subtitle = "Log In")

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = "Welcome back",
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 22.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sign in to your account",
                style = Typo.label(FontWeight.Normal).copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)),
            )
            Spacer(Modifier.height(24.dp))

            FormField(placeholder = "Email", value = mailText, onValueChange = { mailText = it })
            Spacer(Modifier.height(14.dp))
            FormField(
                placeholder = "Password",
                value = keyText,
                onValueChange = { keyText = it },
                secret = true,
            )

            Spacer(Modifier.height(28.dp))

            if (working) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp))
                }
            } else {
                PillButton(label = "Log In", onClick = { attemptEntry() })
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .inkTap(RoundedCornerShape(50)) { StackNavigator.go(Destination.SignUp) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Don't have an account? Register",
                    style = Typo.paragraph(FontWeight.Normal).copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)),
                )
            }
        }
    }
}

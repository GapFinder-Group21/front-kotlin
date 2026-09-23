package co.gapfinder.mobile.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.AccountRepository
import co.gapfinder.mobile.domain.EnergyLevel
import co.gapfinder.mobile.foundation.ACADEMIC_PROGRAMS
import co.gapfinder.mobile.ui.kit.FormField
import co.gapfinder.mobile.ui.kit.Glyphs
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

// Colores por defecto de M3 (semilla deepPurple) usados por DropdownButtonFormField
private val OutlineTone = Color(0xFF79747E)
private val ArrowTone = Color(0xFF616161)

/** Registro de cuenta nueva. */
@Composable
fun SignUpPage() {
    val scope = rememberCoroutineScope()

    var fullName by remember { mutableStateOf("") }
    var mail by remember { mutableStateOf("") }
    var secretWord by remember { mutableStateOf("") }
    var secretRepeat by remember { mutableStateOf("") }
    var term by remember { mutableStateOf("") }
    var program by remember { mutableStateOf<String?>(ACADEMIC_PROGRAMS.first()) }
    var energy by remember { mutableStateOf(EnergyLevel.Regular) }
    var busy by remember { mutableStateOf(false) }

    fun submitAccount() {
        if (secretWord != secretRepeat) {
            Toaster.show("Las contraseñas no coinciden")
            return
        }
        val chosenProgram = program
        if (chosenProgram == null) {
            Toaster.show("Por favor selecciona una carrera")
            return
        }
        busy = true
        scope.launch {
            try {
                AccountRepository.signUp(
                    name = fullName.trim(),
                    email = mail.trim(),
                    password = secretWord,
                    program = chosenProgram,
                    semester = term.trim(),
                    energy = energy,
                )
                StackNavigator.swap(Destination.LocationGate(fromOnboarding = true))
            } catch (e: Exception) {
                Toaster.show(e.message ?: e.toString())
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
    ) {
        NightHeader(title = "GAP FINDER", subtitle = "Create Account")

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(
                text = "Create Account",
                style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 22.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Join the campus network",
                style = Typo.label(FontWeight.Normal).copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)),
            )
            Spacer(Modifier.height(24.dp))

            FormField(placeholder = "Full Name", value = fullName, onValueChange = { fullName = it })
            Spacer(Modifier.height(14.dp))

            FormField(
                placeholder = "Email",
                value = mail,
                onValueChange = { mail = it },
                keyboardType = KeyboardType.Email,
            )
            Spacer(Modifier.height(14.dp))

            FieldCaption("Academic Program")
            Spacer(Modifier.height(6.dp))
            OptionPicker(
                current = program,
                options = ACADEMIC_PROGRAMS,
                caption = { it },
                onPick = { program = it },
            )
            Spacer(Modifier.height(14.dp))

            FormField(
                placeholder = "Semester",
                value = term,
                onValueChange = { term = it },
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(14.dp))

            FieldCaption("Activity Effort Preference")
            Spacer(Modifier.height(6.dp))
            OptionPicker(
                current = energy,
                options = EnergyLevel.entries,
                caption = { it.wire },
                onPick = { energy = it },
            )
            Spacer(Modifier.height(14.dp))

            FormField(
                placeholder = "Password",
                value = secretWord,
                onValueChange = { secretWord = it },
                secret = true,
            )
            Spacer(Modifier.height(14.dp))

            FormField(
                placeholder = "Confirm Password",
                value = secretRepeat,
                onValueChange = { secretRepeat = it },
                secret = true,
            )

            Spacer(Modifier.height(28.dp))

            if (busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp))
                }
            } else {
                PillButton(label = "Create Account", onClick = { submitAccount() })
            }

            Spacer(Modifier.height(14.dp))

            // Enlace a login (TextButton de ancho completo, sin padding)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .inkTap(RoundedCornerShape(50)) { StackNavigator.go(Destination.SignIn) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Already have an account? Log In",
                    style = Typo.label(FontWeight.Normal).copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)),
                )
            }
        }
    }
}

@Composable
private fun FieldCaption(text: String) {
    Text(
        text = text,
        style = Typo.label(FontWeight.SemiBold).copy(fontSize = 14.sp, color = Palette.Ink),
    )
}

/** Selector desplegable con borde redondeado (equivale a DropdownButtonFormField). */
@Composable
private fun <T> OptionPicker(
    current: T?,
    options: List<T>,
    caption: (T) -> String,
    onPick: (T) -> Unit,
) {
    var unfolded by remember { mutableStateOf(false) }
    var boxWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val shape = RoundedCornerShape(8.dp)
    val entryStyle = Typo.label(FontWeight.Normal).copy(fontSize = 14.sp, color = Palette.Ink)

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { boxWidthPx = it.width }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, OutlineTone, shape)
                .inkTap(shape) { unfolded = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = current?.let(caption) ?: "",
                    style = entryStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Glyphs.arrowDropDown,
                contentDescription = null,
                tint = ArrowTone,
                modifier = Modifier.size(24.dp),
            )
        }

        DropdownMenu(
            expanded = unfolded,
            onDismissRequest = { unfolded = false },
            modifier = Modifier.width(with(density) { boxWidthPx.toDp() }),
            shape = RoundedCornerShape(2.dp),
            containerColor = Palette.Fog,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = caption(option),
                            style = entryStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onPick(option)
                        unfolded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                )
            }
        }
    }
}

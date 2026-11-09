package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.ThemeChoice
import co.gapfinder.mobile.ui.theme.ThemeSettings
import co.gapfinder.mobile.ui.theme.Typo

private fun ThemeChoice.glyph(): ImageVector = when (this) {
    ThemeChoice.AUTO -> Glyphs.brightnessAutoRounded
    ThemeChoice.LIGHT -> Glyphs.lightModeRounded
    ThemeChoice.DARK -> Glyphs.darkModeRounded
}

private fun ThemeChoice.label(): String = when (this) {
    ThemeChoice.AUTO -> "Automatic (light sensor)"
    ThemeChoice.LIGHT -> "Light"
    ThemeChoice.DARK -> "Dark"
}

/**
 * Selector de tema para la barra superior: un ícono con el modo actual que abre un menú con
 * Automático, Claro y Oscuro. La elección se guarda en [ThemeSettings].
 */
@Composable
fun ThemeToggle(tint: Color, modifier: Modifier = Modifier) {
    val choice by ThemeSettings.choice.collectAsState()
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        Box(
            Modifier
                .size(40.dp)
                .inkTap(CircleShape) { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(choice.glyph(), contentDescription = "Theme: ${choice.label()}", tint = tint, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThemeChoice.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label(),
                            style = Typo.paragraph(if (option == choice) FontWeight.Bold else FontWeight.Normal),
                        )
                    },
                    leadingIcon = {
                        Icon(option.glyph(), contentDescription = null, tint = if (option == choice) Palette.Teal else Palette.Ink)
                    },
                    onClick = {
                        ThemeSettings.choose(option)
                        open = false
                    },
                )
            }
        }
    }
}

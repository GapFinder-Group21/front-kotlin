package co.gapfinder.mobile.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade

/** Campo de texto con borde redondeado (antes CustomInput). */
@Composable
fun FormField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val textStyle = Typo.paragraph().copy(fontSize = 15.sp, color = Palette.Ink)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(Palette.Coral),
        interactionSource = source,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (secret) PasswordVisualTransformation('•') else VisualTransformation.None,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.White, shape)
                    .border(1.5.dp, if (focused) Palette.Coral else Palette.Ink.fade(0.15f), shape)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = Palette.Ink.fade(0.4f)))
                }
                inner()
            }
        },
    )
}

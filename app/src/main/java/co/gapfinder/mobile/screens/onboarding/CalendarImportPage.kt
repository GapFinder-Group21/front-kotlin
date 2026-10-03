package co.gapfinder.mobile.screens.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.CalendarBridge
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.launch

private const val IMPORT_TAG = "CalendarImport"

/** Etapas de la importación. */
private enum class ImportStage { Linking, Done, Failed }

/** Texto de error al estilo de Dart (`Exception: ...`). */
private fun Throwable.importDartText(): String = "Exception: ${message ?: ""}"

/** Importa el horario desde Google Calendar. */
@Composable
fun CalendarImportPage() {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(ImportStage.Linking) }
    var failure by remember { mutableStateOf<String?>(null) }

    suspend fun runImport() {
        stage = ImportStage.Linking
        failure = null
        try {
            Log.d(IMPORT_TAG, "🚀 IMPORT: Starting Google Calendar import...")
            CalendarBridge.pullTimetable()
            Log.d(IMPORT_TAG, "✅ IMPORT: Success (Standard)")
            stage = ImportStage.Done
        } catch (e: Exception) {
            val text = e.importDartText()
            Log.d(IMPORT_TAG, "⚠️ IMPORT ERROR/CONFLICT: $text")
            // Un 409 significa conflicto, pero los datos ya existen
            if (text.contains("409")) {
                Log.d(IMPORT_TAG, "ℹ️ IMPORT: Conflict 409 detected, but data persists. Marking as success.")
                stage = ImportStage.Done
                return
            }
            stage = ImportStage.Failed
            failure = text
        }
    }

    LaunchedEffect(Unit) { runImport() }

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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            when (stage) {
                ImportStage.Linking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = Palette.Sky,
                        strokeWidth = 3.5.dp,
                    )
                    Spacer(Modifier.height(36.dp))
                    StageTitle("Connecting to Google...")
                    Spacer(Modifier.height(10.dp))
                    StageNote("Importing your calendar events", alpha = 0.5f)
                }

                ImportStage.Done -> {
                    StageBadge(Palette.Sky, Glyphs.checkRounded)
                    Spacer(Modifier.height(28.dp))
                    StageTitle("Calendar connected!")
                    Spacer(Modifier.height(10.dp))
                    StageNote("Your Google Calendar events have been imported successfully.", alpha = 0.55f)
                }

                ImportStage.Failed -> {
                    StageBadge(Palette.Coral, Glyphs.closeRounded)
                    Spacer(Modifier.height(28.dp))
                    StageTitle("Something went wrong")
                    Spacer(Modifier.height(10.dp))
                    StageNote(failure ?: "We couldn't import your calendar. Try again.", alpha = 0.55f)
                }
            }
            Spacer(Modifier.weight(1f))

            if (stage == ImportStage.Done) {
                PillButton(
                    label = "Continue",
                    color = Palette.Sky,
                    onClick = { StackNavigator.resetTo(Destination.Home) },
                )
            }
            if (stage == ImportStage.Failed) {
                PillButton(
                    label = "Try Again",
                    color = Palette.Coral,
                    onClick = { scope.launch { runImport() } },
                )
            }
        }
    }
}

@Composable
private fun StageBadge(tone: Color, glyph: ImageVector) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(tone, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = Palette.White,
            modifier = Modifier.size(38.dp),
        )
    }
}

@Composable
private fun StageTitle(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 20.sp, color = Palette.Ink),
    )
}

@Composable
private fun StageNote(text: String, alpha: Float) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = Typo.paragraph(FontWeight.Normal).copy(fontSize = 14.sp, color = Palette.Ink.fade(alpha)),
    )
}

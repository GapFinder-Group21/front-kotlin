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
import co.gapfinder.mobile.data.CalendarImportFault
import co.gapfinder.mobile.data.CalendarImportReport
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.rememberOnline
import co.gapfinder.mobile.foundation.userText
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.OfflineNote
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val IMPORT_TAG = "CalendarImport"

/** Etapas de la importación. Offline: esperando la red para empezar. */
private enum class ImportStage { Linking, Done, Failed, Offline }

/** Importa el horario desde Google Calendar. */
@Composable
fun CalendarImportPage() {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(ImportStage.Linking) }
    var failure by remember { mutableStateOf<String?>(null) }
    val online by rememberOnline()

    suspend fun runImport() {
        // La importación lee el calendario en Google a través del backend: sin red no se intenta
        if (!Connectivity.isOnline()) {
            stage = ImportStage.Offline
            return
        }
        stage = ImportStage.Linking
        failure = null
        try {
            Log.d(IMPORT_TAG, "🚀 IMPORT: Starting Google Calendar import...")
            CalendarBridge.pullTimetable()
            Log.d(IMPORT_TAG, "✅ IMPORT: Success (Standard)")
            stage = ImportStage.Done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val text = e.userText()
            Log.d(IMPORT_TAG, "⚠️ IMPORT ERROR/CONFLICT: $e")
            // Un 409 significa conflicto, pero los datos ya existen
            if ((e as? CalendarImportFault)?.status == 409 || e.message?.contains("409") == true) {
                Log.d(IMPORT_TAG, "ℹ️ IMPORT: Conflict 409 detected, but data persists. Marking as success.")
                stage = ImportStage.Done
                return
            }
            stage = ImportStage.Failed
            failure = text
            // BQ 1: se reporta el tipo de error al backend (o se encola si no hay red) sin frenar la pantalla
            scope.launch { CalendarImportReport.report(e) }
        }
    }

    LaunchedEffect(Unit) { runImport() }
    // Si estaba esperando la red, importa apenas vuelve
    LaunchedEffect(online) {
        if (online && stage == ImportStage.Offline) runImport()
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

                ImportStage.Offline -> {
                    StageBadge(Palette.Ink.fade(0.3f), Glyphs.cloudOff)
                    Spacer(Modifier.height(28.dp))
                    StageTitle("Waiting for connection")
                    Spacer(Modifier.height(10.dp))
                    StageNote("Your calendar is read from Google, so the import needs internet.", alpha = 0.55f)
                    Spacer(Modifier.height(16.dp))
                    OfflineNote("The import starts by itself when you're back online.")
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
                    onClick = if (online) ({ scope.launch { runImport() } }) else null,
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

package co.gapfinder.mobile.screens.hangouts

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.DropOffRepository
import co.gapfinder.mobile.data.HangoutRepository
import co.gapfinder.mobile.data.MemberRepository
import co.gapfinder.mobile.data.PastimeRepository
import co.gapfinder.mobile.data.SpotRepository
import co.gapfinder.mobile.domain.CampusSpot
import co.gapfinder.mobile.domain.DraftStage
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.domain.HangoutDropOff
import co.gapfinder.mobile.domain.HangoutState
import co.gapfinder.mobile.domain.Pastime
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.kit.plainTap
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private const val TAG = "HangoutComposer"
private const val BLURB_LIMIT = 160

/** Hoja inferior abierta en el formulario. */
private enum class OpenSheet { None, Pastimes, Spots }

/** Registro mutable de en qué paso quedó el usuario (no provoca recomposición, como en Dart). */
private class DraftTracker {
    var stage: DraftStage = DraftStage.PickPastime
    var reported: Boolean = false
}

/** Formulario para publicar una mesa abierta (antes CreateOpenTableScreen). */
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun HangoutComposerPage() {
    var pastimes by remember { mutableStateOf<List<Pastime>>(emptyList()) }
    var spots by remember { mutableStateOf<List<CampusSpot>>(emptyList()) }
    var chosenPastime by remember { mutableStateOf<Pastime?>(null) }
    var chosenSpot by remember { mutableStateOf<CampusSpot?>(null) }
    var minutes by remember { mutableIntStateOf(0) }
    var blurb by remember { mutableStateOf("") }
    val tracker = remember { DraftTracker() }

    var warming by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(OpenSheet.None) }
    val scope = rememberCoroutineScope()

    suspend fun fetchCatalogs() {
        warming = true
        failure = null
        try {
            // Catálogos en paralelo
            val (fetchedPastimes, fetchedSpots) = coroutineScope {
                val a = async { PastimeRepository.catalog() }
                val b = async { SpotRepository.all() }
                a.await() to b.await()
            }
            pastimes = fetchedPastimes
            spots = fetchedSpots

            // Edificio actual por separado
            try {
                val me = SessionVault.memberId()
                chosenSpot = if (me == null) null else MemberRepository.byId(me).currentSpot
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Note: Current building not auto-detected: $e")
            }

            if (fetchedPastimes.isNotEmpty()) {
                chosenPastime = fetchedPastimes.first()
                minutes = fetchedPastimes.first().durationMinutes
            }
            warming = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "CRITICAL ERROR in Create Open Table: $e")
            failure = e.plainText()
            warming = false
        }
    }

    LaunchedEffect(Unit) { fetchCatalogs() }

    fun submit() {
        scope.launch {
            val pastime = chosenPastime
            val spot = chosenSpot
            if (pastime == null || spot == null) {
                Toaster.show("Please select an activity and location")
                return@launch
            }
            val me = SessionVault.memberId() ?: return@launch
            sending = true
            try {
                val now = LocalDateTime.now()
                val draft = Hangout(
                    id = 0,
                    description = blurb.trim(),
                    startsAt = now,
                    endsAt = now.plusMinutes(minutes.toLong()),
                    state = HangoutState.Running,
                    createdAt = now,
                    pastime = pastime,
                )
                HangoutRepository.host(hostId = me, spotId = spot.id, durationMinutes = minutes, draft = draft)
                StackNavigator.back()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toaster.show("Error publishing: ${e.asDartString()}")
            } finally {
                sending = false
            }
        }
    }

    // Registra en qué campo quedó el usuario y cierra; el registro no bloquea la salida.
    fun giveUp() {
        if (tracker.reported) return
        tracker.reported = true
        scope.launch {
            val me = SessionVault.memberId()
            if (me != null) {
                val stage = tracker.stage
                val record = HangoutDropOff(
                    memberId = me,
                    stage = stage,
                    pastimeId = chosenPastime?.id,
                    durationMinutes = if (chosenPastime != null && minutes > 0) minutes else null,
                    // El backend solo acepta edificio si el abandono fue en LOCATION
                    spotId = if (stage == DraftStage.PickSpot) chosenSpot?.id else null,
                )
                GlobalScope.launch {
                    try {
                        DropOffRepository.log(record)
                    } catch (e: Exception) {
                        Log.d(TAG, "Abandonment not registered: $e")
                    }
                }
            }
            StackNavigator.back()
        }
    }

    when {
        warming -> {
            Box(Modifier.fillMaxSize().background(BareScaffold), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(36.dp))
            }
        }

        failure != null -> {
            Column(Modifier.fillMaxSize().background(BareScaffold)) {
                InkHeader(title = "Create Open Table", showBack = true)
                Box(
                    Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Glyphs.errorOutlineRounded,
                            contentDescription = null,
                            tint = Palette.Coral,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Connection Error",
                            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 18.sp, color = SurfaceText),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = failure ?: "",
                            textAlign = TextAlign.Center,
                            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.5f)),
                        )
                        Spacer(Modifier.height(24.dp))
                        PillButton(label = "Retry", onClick = { scope.launch { fetchCatalogs() } })
                    }
                }
            }
        }

        else -> {
            Column(Modifier.fillMaxSize().background(Palette.Fog)) {
                InkHeader(title = "Create Open Table", showBack = true)
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Actividad
                    PickerField(
                        heading = "Activity",
                        shown = chosenPastime?.title ?: "Select an activity",
                        hint = chosenPastime?.let { "${it.durationMinutes} min" },
                        onOpen = {
                            tracker.stage = DraftStage.PickPastime
                            sheet = OpenSheet.Pastimes
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    // Descripción
                    CardSection(heading = "What do you propose?") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            BlurbInput(
                                text = blurb,
                                onText = { blurb = it.take(BLURB_LIMIT) },
                                onPressed = { tracker.stage = DraftStage.WriteBlurb },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${blurb.length}/$BLURB_LIMIT",
                                style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Ink.fade(0.3f)),
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Lugar
                    PickerField(
                        heading = "Location",
                        shown = chosenSpot?.name ?: "Select a building",
                        hint = null,
                        onOpen = {
                            tracker.stage = DraftStage.PickSpot
                            sheet = OpenSheet.Spots
                        },
                    )

                    Spacer(Modifier.height(32.dp))

                    if (sending) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(36.dp))
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            PillButton(label = "Publish Open Table", onClick = { submit() }, color = Palette.Sky)
                            Spacer(Modifier.height(12.dp))
                            FlatTextAction(label = "Cancel", onTap = { giveUp() })
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        OpenSheet.Pastimes -> ChoiceSheet(
            heading = "SELECT ACTIVITY",
            emptyText = "No activities found in catalog",
            rows = pastimes,
            isChosen = { it.id == chosenPastime?.id },
            accent = Palette.Sky,
            titleOf = { it.title },
            detailOf = { "${it.durationMinutes} minutes · ${it.energy.wire.lowercase()}" },
            onPick = {
                chosenPastime = it
                minutes = it.durationMinutes
            },
            onClose = { sheet = OpenSheet.None },
        )

        OpenSheet.Spots -> ChoiceSheet(
            heading = "SELECT LOCATION",
            emptyText = "No buildings found in system",
            rows = spots,
            isChosen = { it.id == chosenSpot?.id },
            accent = Palette.Teal,
            titleOf = { it.name },
            detailOf = null,
            onPick = { chosenSpot = it },
            onClose = { sheet = OpenSheet.None },
        )

        OpenSheet.None -> Unit
    }
}

/** Tarjeta blanca con título en mayúsculas. */
@Composable
private fun CardSection(heading: String, body: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, shape)
            .border(1.5.dp, Palette.Ink.fade(0.08f), shape)
            .padding(16.dp)
    ) {
        Text(
            text = heading.uppercase(),
            style = Typo.label(FontWeight.Bold).copy(
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = Palette.Ink.fade(0.45f),
            ),
        )
        Spacer(Modifier.height(12.dp))
        body()
    }
}

/** Campo que abre un selector (actividad o edificio). */
@Composable
private fun PickerField(heading: String, shown: String, hint: String?, onOpen: () -> Unit) {
    CardSection(heading) {
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .plainTap { onOpen() }
                .background(Palette.Fog, shape)
                .border(1.5.dp, Palette.Ink.fade(0.12f), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    text = shown,
                    style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 14.sp, color = Palette.Ink),
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        style = Typo.paragraph(FontWeight.SemiBold).copy(fontSize = 12.sp, color = Palette.Sky),
                    )
                }
            }
            Icon(
                Glyphs.unfoldMoreRounded,
                contentDescription = null,
                tint = Palette.Ink.fade(0.4f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Área de texto de 4 líneas con borde (TextField con OutlineInputBorder). */
@Composable
private fun BlurbInput(text: String, onText: (String) -> Unit, onPressed: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    // Cada toque en el campo marca el paso DESCRIPTION (onTap de Flutter)
    LaunchedEffect(source) {
        source.interactions.collect { if (it is PressInteraction.Release || it is FocusInteraction.Focus) onPressed() }
    }
    val shape = RoundedCornerShape(10.dp)
    val inputStyle = Typo.paragraph().copy(fontSize = 13.sp, color = Palette.Ink)

    BasicTextField(
        value = text,
        onValueChange = onText,
        modifier = Modifier.fillMaxWidth(),
        textStyle = inputStyle,
        minLines = 4,
        maxLines = 4,
        cursorBrush = SolidColor(ThemePrimary),
        interactionSource = source,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Fog, shape)
                    .border(
                        if (focused) 2.dp else 1.5.dp,
                        if (focused) ThemePrimary else Palette.Ink.fade(0.12f),
                        shape,
                    )
                    .padding(12.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Describe what you want to do, where exactly you'll be...",
                        style = inputStyle.copy(color = Palette.Ink.fade(0.3f)),
                    )
                }
                inner()
            }
        },
    )
}

/** Equivalente a un TextButton de ancho completo. */
@Composable
private fun FlatTextAction(label: String, onTap: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .inkTap(shape) { onTap() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
            )
        }
    }
}

/** Hoja inferior con la lista de opciones (ListTile de Flutter). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    heading: String,
    emptyText: String,
    rows: List<T>,
    isChosen: (T) -> Boolean,
    accent: Color,
    titleOf: (T) -> String,
    detailOf: ((T) -> String)?,
    onPick: (T) -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = Color.White,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = heading,
                style = Typo.label(FontWeight.ExtraBold).copy(
                    fontSize = 14.sp,
                    letterSpacing = 1.2.sp,
                    color = Palette.Ink.fade(0.4f),
                ),
            )
            Spacer(Modifier.height(16.dp))
            if (rows.isEmpty()) {
                Text(
                    text = emptyText,
                    style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.5f)),
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    items(rows) { row ->
                        val on = isChosen(row)
                        SheetRow(
                            leading = if (on) Glyphs.checkCircleRounded else Glyphs.circleOutlined,
                            leadingTint = if (on) accent else Palette.Ink.fade(0.2f),
                            title = titleOf(row),
                            titleBold = on,
                            titleTint = if (on) accent else Palette.Ink,
                            detail = detailOf?.invoke(row),
                            onTap = {
                                onPick(row)
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Fila estilo ListTile de Material 3. */
@Composable
private fun SheetRow(
    leading: ImageVector,
    leadingTint: Color,
    title: String,
    titleBold: Boolean,
    titleTint: Color,
    detail: String?,
    onTap: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (detail != null) 72.dp else 56.dp)
            .inkTap { onTap() }
            .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(leading, contentDescription = null, tint = leadingTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typo.paragraph(if (titleBold) FontWeight.Bold else FontWeight.Normal).copy(
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp,
                    color = titleTint,
                ),
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = Typo.paragraph().copy(
                        fontSize = 12.sp,
                        letterSpacing = 0.25.sp,
                        color = Palette.Ink.fade(0.45f),
                    ),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Icon(
            Glyphs.chevronRightRounded,
            contentDescription = null,
            tint = Color(0xFF49454F),
            modifier = Modifier.size(18.dp),
        )
    }
}

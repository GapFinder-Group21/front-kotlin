package co.gapfinder.mobile.screens.hangouts

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.AttendeeRepository
import co.gapfinder.mobile.data.HangoutPhotos
import co.gapfinder.mobile.data.HangoutRepository
import co.gapfinder.mobile.data.PastimeRepository
import co.gapfinder.mobile.data.SpotRepository
import co.gapfinder.mobile.data.local.HangoutDraftEntity
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.data.outbox.HangoutCouriers
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
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.LocalDateTime

private const val TAG = "HangoutComposer"
private const val BLURB_LIMIT = 160

// Cupo de la mesa: valor inicial y límites del selector
private const val DEFAULT_SEATS = 4
private const val MIN_SEATS = 2
private const val MAX_SEATS = 20

// Espera desde el último cambio antes de guardar el borrador (no se escribe en Room por cada tecla)
private const val DRAFT_SAVE_DELAY_MS = 400L

/** Hoja inferior abierta en el formulario. */
private enum class OpenSheet { None, Pastimes, Spots }

/** Registro mutable de en qué paso quedó el usuario (no provoca recomposición, como en Dart). */
private class DraftTracker {
    var stage: DraftStage = DraftStage.PickPastime
    var reported: Boolean = false
    // true al publicar o cancelar: desde ahí el borrador ya no se vuelve a guardar
    var closed: Boolean = false
}

/** Formulario para publicar una mesa abierta (antes CreateOpenTableScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangoutComposerPage() {
    var pastimes by remember { mutableStateOf<List<Pastime>>(emptyList()) }
    var spots by remember { mutableStateOf<List<CampusSpot>>(emptyList()) }
    var chosenPastime by remember { mutableStateOf<Pastime?>(null) }
    var chosenSpot by remember { mutableStateOf<CampusSpot?>(null) }
    var minutes by remember { mutableIntStateOf(0) }
    var seats by remember { mutableIntStateOf(DEFAULT_SEATS) }
    var blurb by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<File?>(null) }
    val tracker = remember { DraftTracker() }

    // false hasta que se carga el catálogo y se restaura el borrador; antes no se guarda nada
    var restored by remember { mutableStateOf(false) }
    var warming by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(OpenSheet.None) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Archivo donde la cámara está escribiendo la foto (null si no hay captura en curso).
    // rememberSaveable porque Android puede matar la app mientras la cámara está abierta.
    var capturing by rememberSaveable { mutableStateOf<String?>(null) }

    // Normaliza la foto (JPEG reducido y derecho) y reemplaza la anterior
    fun adoptPhoto(source: Uri, leftover: File? = null) {
        scope.launch {
            try {
                val fresh = HangoutPhotos.normalize(context, source)
                HangoutPhotos.discard(photo)
                photo = fresh
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Photo not readable: $e")
                Toaster.show("Could not read the photo")
            } finally {
                HangoutPhotos.discard(leftover)
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = capturing?.let(::File)
        capturing = null
        if (saved && target != null) adoptPhoto(Uri.fromFile(target), leftover = target) else HangoutPhotos.discard(target)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) adoptPhoto(picked)
    }

    fun openCamera() {
        val (file, uri) = HangoutPhotos.cameraTarget(context)
        capturing = file.absolutePath
        camera.launch(uri)
    }

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

            val saved = SessionVault.memberId()?.let { LocalStore.draftOf(it) }
            if (saved != null) {
                // Actividad y edificio se buscan por id; si ya no están en el catálogo quedan sin elegir
                val pastime = fetchedPastimes.firstOrNull { it.id == saved.pastimeId } ?: fetchedPastimes.firstOrNull()
                chosenPastime = pastime
                minutes = if (pastime != null && pastime.id == saved.pastimeId) saved.minutes else pastime?.durationMinutes ?: 0
                blurb = saved.blurb
                seats = saved.seats.coerceIn(MIN_SEATS, MAX_SEATS)
                chosenSpot = fetchedSpots.firstOrNull { it.id == saved.spotId }
                photo = saved.photoPath?.let(::File)?.takeIf { it.exists() }
                tracker.stage = DraftStage.entries.firstOrNull { it.wire == saved.stage } ?: DraftStage.PickPastime
                Toaster.show("Draft restored")
            } else if (fetchedPastimes.isNotEmpty()) {
                chosenPastime = fetchedPastimes.first()
                minutes = fetchedPastimes.first().durationMinutes
            }
            restored = true
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

    // Guarda el borrador en Room con cada cambio. El efecto se reinicia con cada cambio, así que
    // el delay hace de debounce: solo se escribe cuando el usuario se detiene un momento.
    LaunchedEffect(restored, chosenPastime, chosenSpot, minutes, seats, blurb, photo) {
        if (!restored || tracker.closed) return@LaunchedEffect
        delay(DRAFT_SAVE_DELAY_MS)
        val me = SessionVault.memberId() ?: return@LaunchedEffect
        val untouched = chosenPastime?.id == pastimes.firstOrNull()?.id && chosenSpot == null &&
            blurb.isBlank() && seats == DEFAULT_SEATS && photo == null
        if (untouched) {
            LocalStore.discardDraft(me)
        } else {
            LocalStore.saveDraft(
                HangoutDraftEntity(
                    memberId = me,
                    pastimeId = chosenPastime?.id,
                    minutes = minutes,
                    blurb = blurb,
                    seats = seats,
                    spotId = chosenSpot?.id,
                    photoPath = photo?.absolutePath,
                    stage = tracker.stage.wire,
                    updatedAt = LocalDateTime.now(),
                )
            )
        }
    }

    fun submit() {
        scope.launch {
            val pastime = chosenPastime
            val spot = chosenSpot
            val picture = photo
            if (pastime == null || spot == null) {
                Toaster.show("Please select an activity and location")
                return@launch
            }
            // El backend no acepta mesas sin foto
            if (picture == null) {
                Toaster.show("Please add a photo of the table")
                return@launch
            }
            val me = SessionVault.memberId() ?: return@launch
            sending = true
            try {
                val now = LocalDateTime.now()
                val draft = Hangout(
                    id = 0,
                    title = pastime.name,
                    description = blurb.trim(),
                    startsAt = now,
                    endsAt = now.plusMinutes(minutes.toLong()),
                    maxParticipants = seats,
                    state = HangoutState.Open,
                    pastime = pastime,
                )
                val created = try {
                    HangoutRepository.host(hostId = me, spotId = spot.id, draft = draft, photo = picture)
                } catch (e: IOException) {
                    // Sin red: la mesa queda en la cola (con su foto) y se publica sola cuando vuelva la conexión
                    HangoutCouriers.enqueueCreate(
                        hostId = me,
                        spotId = spot.id,
                        body = HangoutRepository.newTableBody(me, spot.id, draft),
                        durationMinutes = minutes,
                        photo = picture,
                    )
                    null
                }
                // Publicada o en la cola, el borrador ya sobra
                tracker.closed = true
                LocalStore.discardDraft(me)
                photo = null
                if (created == null) {
                    Toaster.show("No connection: your Open Table will be published when you're back online")
                } else {
                    HangoutPhotos.discard(picture)
                    // El creador también queda registrado como participante
                    try {
                        AttendeeRepository.join(created.id, me)
                    } catch (e: IOException) {
                        HangoutCouriers.enqueueJoin(created.id, me)
                    } catch (e: Exception) {
                        Log.d(TAG, "Creator not registered as participant: $e")
                    }
                }
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
        // Cancelar descarta el borrador; salir con la flecha de atrás lo conserva
        tracker.closed = true
        HangoutPhotos.discard(photo)
        photo = null
        scope.launch {
            val me = SessionVault.memberId()
            if (me != null) {
                LocalStore.discardDraft(me)
                val stage = tracker.stage
                val record = HangoutDropOff(
                    memberId = me,
                    stage = stage,
                    pastimeId = chosenPastime?.id,
                    durationMinutes = if (chosenPastime != null && minutes > 0) minutes else null,
                    // Cupo y edificio solo se mandan si el abandono fue justo en ese paso
                    maxParticipants = if (stage == DraftStage.PickHeadcount) seats else null,
                    spotId = if (stage == DraftStage.PickSpot) chosenSpot?.id else null,
                )
                // Va por la cola: se manda enseguida si hay red, o cuando vuelva si no
                HangoutCouriers.enqueueDropOff(record)
            }
            StackNavigator.back()
        }
    }

    fun changeSeats(delta: Int) {
        tracker.stage = DraftStage.PickHeadcount
        seats = (seats + delta).coerceIn(MIN_SEATS, MAX_SEATS)
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
                        shown = chosenPastime?.name ?: "Select an activity",
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

                    // Cupo máximo
                    CardSection(heading = "Max participants") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StepButton(
                                glyph = Glyphs.removeCircleOutline,
                                enabled = seats > MIN_SEATS,
                                onTap = { changeSeats(-1) },
                            )
                            Text(
                                text = "$seats",
                                style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 16.sp, color = Palette.Ink),
                            )
                            StepButton(
                                glyph = Glyphs.addCircleOutline,
                                enabled = seats < MAX_SEATS,
                                onTap = { changeSeats(+1) },
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

                    Spacer(Modifier.height(12.dp))

                    // Foto de la mesa (obligatoria en el backend)
                    CardSection(heading = "Photo") {
                        PhotoField(
                            photo = photo,
                            onCamera = { openCamera() },
                            onGallery = {
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                    }

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
            titleOf = { it.name },
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

/** Vista previa de la foto con los botones de cámara y galería. */
@Composable
private fun PhotoField(photo: File?, onCamera: () -> Unit, onGallery: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth()) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = "Open table photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).background(Palette.Fog, shape).clip(shape),
            )
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhotoSourceButton(Glyphs.photoCameraRounded, if (photo == null) "Take photo" else "Retake", onCamera, Modifier.weight(1f))
            PhotoSourceButton(Glyphs.photoLibraryRounded, "Gallery", onGallery, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhotoSourceButton(glyph: ImageVector, label: String, onTap: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .background(Palette.Fog, shape)
            .border(1.5.dp, Palette.Ink.fade(0.12f), shape)
            .inkTap(shape) { onTap() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(glyph, contentDescription = null, tint = Palette.Sky, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 13.sp, color = Palette.Ink))
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

/** Botón − / + del cupo (IconButton de M3: ícono de 24 en un área táctil de 48). */
@Composable
private fun StepButton(glyph: ImageVector, enabled: Boolean, onTap: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .inkTap(CircleShape, enabled = enabled) { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            glyph,
            contentDescription = null,
            // onSurfaceVariant, o onSurface al 38% cuando está deshabilitado
            tint = if (enabled) Color(0xFF49454F) else SurfaceText.fade(0.38f),
            modifier = Modifier.size(24.dp),
        )
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

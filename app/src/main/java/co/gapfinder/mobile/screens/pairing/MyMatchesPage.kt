package co.gapfinder.mobile.screens.pairing

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.MatchFilter
import co.gapfinder.mobile.data.MatchHistoryRepository
import co.gapfinder.mobile.data.MatchHistoryView
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.data.outbox.RatingCouriers
import co.gapfinder.mobile.data.processMatchHistory
import co.gapfinder.mobile.domain.MatchHistoryItem
import co.gapfinder.mobile.domain.MatchRating
import co.gapfinder.mobile.domain.PairingState
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.rememberOnline
import co.gapfinder.mobile.foundation.userText
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.Portrait
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "MyMatchesPage"

private val WHEN_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM · H:mm", Locale.ENGLISH)
private val END_FORMAT = DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH)

/** "My matches": historial de todos mis matches y calificación (1 a 5 estrellas) de los que ya terminaron. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMatchesPage() {
    var history by remember { mutableStateOf<MatchHistoryView?>(null) }
    var filter by remember { mutableStateOf(MatchFilter.ToRate) }
    var pending by remember { mutableStateOf(true) }
    var fault by remember { mutableStateOf<String?>(null) }
    // Fecha de lo guardado en Room cuando eso es lo que se está mostrando (null si viene de la red o del caché)
    var savedAt by remember { mutableStateOf<LocalDateTime?>(null) }
    var rating by remember { mutableStateOf<MatchHistoryItem?>(null) }
    var sending by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val online by rememberOnline()
    val scope = rememberCoroutineScope()

    // Estrategia: caching. Se busca primero en memoria (TtlCache), luego en Room y al final en la red.
    suspend fun pullHistory(force: Boolean = false) {
        fault = null
        val me = SessionVault.memberId() ?: return

        if (!force) {
            val inMemory = MatchHistoryRepository.cached(me)
            if (inMemory != null) {
                history = processMatchHistory(inMemory)
                savedAt = null
                pending = false
                return
            }
        }

        // Estrategia: local storage. Lo guardado en Room se muestra de inmediato; el spinner solo sale si no hay nada
        val saved = LocalStore.matchHistoryOf(me)
        if (saved != null) {
            history = processMatchHistory(saved.items)
            savedAt = saved.savedAt
            pending = false
        } else if (history == null) {
            pending = true
        }

        // Estrategia: eventual connectivity. Sin red no se intenta; se queda lo guardado
        if (!Connectivity.isOnline()) {
            if (saved == null) fault = "No connection. Your matches show up when you're back online."
            pending = false
            return
        }

        try {
            // Estrategia: multithreading. El historial y las calificaciones que siguen en la cola se piden en paralelo
            val (fromServer, queued) = coroutineScope {
                val history = async { MatchHistoryRepository.fetch(me) }
                val queued = async { LocalStore.pendingRatingsOf(me) }
                history.await() to queued.await()
            }
            // El backend aún no tiene las calificaciones de la cola: se siguen mostrando como "pendientes"
            val merged = fromServer.map { item ->
                val mine = queued[item.matchId]
                if (mine != null && item.canRate) item.copy(myRating = mine, canRate = false, ratingPending = true) else item
            }
            LocalStore.saveMatchHistory(me, merged)
            MatchHistoryRepository.remember(me, merged)
            history = processMatchHistory(merged)
            savedAt = null
            pending = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Error loading my matches: $e")
            // Con algo guardado se sigue mostrando eso; el error solo se ve si no hay nada
            if (saved == null) fault = e.userText()
            pending = false
        }
    }

    // Al abrir y cada vez que cambia la red o se pide recargar. Con calificaciones en la cola y red, se revisa
    // cada tanto si ya salieron, porque el envío lo hace WorkManager en segundo plano
    LaunchedEffect(online, reload) {
        pullHistory(force = reload > 0)
        if (online) {
            repeat(6) {
                if (history?.all?.any { it.ratingPending } != true) return@LaunchedEffect
                delay(5_000)
                pullHistory(force = true)
            }
        }
    }

    // Califica: con red se manda; sin red (o si la red falla a mitad) queda en la cola y se ve como "pendiente"
    fun submitRating(item: MatchHistoryItem, stars: Int, rawComment: String) {
        val comment = rawComment.trim().ifEmpty { null }
        scope.launch {
            val me = SessionVault.memberId() ?: return@launch
            suspend fun queue() {
                LocalStore.markRatingPending(me, item.matchId, stars, comment)
                RatingCouriers.enqueueRate(item.matchId, me, stars, comment)
                MatchHistoryRepository.forget()
                Toaster.show("No connection: your rating will be sent when you're back online", background = Palette.Teal, style = toastStyle())
            }
            sending = true
            try {
                if (!Connectivity.isOnline()) {
                    queue()
                } else {
                    try {
                        MatchHistoryRepository.rate(item.matchId, stars, comment)
                        MatchHistoryRepository.forget()
                        Toaster.show("Thanks! Your rating was sent", background = Palette.Teal, style = toastStyle())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        queue()
                    }
                }
                rating = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // El backend explica el motivo (el match no terminó, ya lo calificaste, etc.)
                Toaster.show(e.userText(), background = Palette.Coral, style = toastStyle())
                rating = null
            } finally {
                sending = false
            }
            // 409 u otro rechazo: se vuelve a pedir lo del servidor para que la pantalla quede al día
            reload++
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Fog)) {
        InkHeader(title = "My matches", showBack = true)

        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
            val current = history
            when {
                pending -> CircularProgressIndicator(Modifier.size(36.dp).align(Alignment.Center))
                fault != null -> FaultState(fault.orEmpty(), offline = !online) { scope.launch { pullHistory(force = true) } }
                current == null -> Unit
                else -> Column(Modifier.fillMaxSize()) {
                    savedAt?.let { moment ->
                        SavedBanner(
                            "${if (online) "Couldn't refresh" else "No connection"} · showing your saved matches · ${savedAtText(moment)}",
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                    }
                    PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = { scope.launch { pullHistory(force = true) } },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        val shown = current.of(filter)
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                            if (current.all.isEmpty()) {
                                item { NoMatchesYet(Modifier.fillParentMaxHeight(0.7f)) }
                            } else {
                                item { SummaryCard(current) }
                                item { Spacer(Modifier.height(16.dp)) }
                                item { FilterRow(filter) { filter = it } }
                                item { Spacer(Modifier.height(16.dp)) }
                                if (shown.isEmpty()) {
                                    item { NothingHere(filter) }
                                } else {
                                    items(shown, key = { it.matchId }) { MatchCard(it) { target -> rating = target } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    rating?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!sending) rating = null },
            sheetState = sheetState,
            containerColor = Palette.White,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            dragHandle = {
                Box(Modifier.padding(top = 12.dp).size(width = 40.dp, height = 4.dp).background(Palette.Ink.fade(0.15f), RoundedCornerShape(2.dp)))
            },
        ) {
            RatingSheet(target, sending, online) { stars, comment -> submitRating(target, stars, comment) }
        }
    }
}

// ============================================================ Banner, resumen y filtros

@Composable
private fun SavedBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Palette.Ink.fade(0.06f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Glyphs.cloudOffRounded, contentDescription = null, tint = Palette.Ink.fade(0.75f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Typo.paragraph(FontWeight.SemiBold).copy(fontSize = 13.sp, color = Palette.Ink.fade(0.75f)))
    }
}

/** "last updated at 14:05" (hoy) o "last updated on Oct 18, 14:05" (otro día). */
private fun savedAtText(moment: LocalDateTime): String =
    if (moment.toLocalDate() == LocalDate.now()) "last updated at ${moment.format(END_FORMAT)}"
    else "last updated on ${moment.format(DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH))}"

/** Base de las tarjetas de la pantalla: resumen y cada match. */
private fun Modifier.cardStyle(shape: RoundedCornerShape): Modifier = this
    .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.04f), spotColor = Palette.Ink.fade(0.04f))
    .background(Palette.White, shape)
    .border(1.5.dp, Palette.Teal.fade(0.3f), shape)

@Composable
private fun SummaryCard(view: MatchHistoryView) {
    Row(
        Modifier.fillMaxWidth().cardStyle(CARD_SHAPE).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SummaryCaption("AVERAGE RECEIVED")
            Spacer(Modifier.height(4.dp))
            val average = view.averageReceived
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    average?.let { String.format(Locale.ENGLISH, "%.1f", it) } ?: "—",
                    style = Typo.heading(FontWeight.Black).copy(fontSize = 24.sp, color = Palette.Teal),
                )
                if (average != null) {
                    Icon(Glyphs.starRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(22.dp))
                }
            }
        }
        Box(Modifier.width(1.dp).height(44.dp).background(Palette.Ink.fade(0.08f)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            SummaryCaption("TO RATE")
            Spacer(Modifier.height(4.dp))
            Text(
                view.toRateCount.toString(),
                style = Typo.heading(FontWeight.Black).copy(
                    fontSize = 24.sp,
                    color = if (view.toRateCount > 0) Palette.Coral else Palette.Ink,
                ),
            )
        }
    }
}

@Composable
private fun SummaryCaption(text: String) {
    Text(text, style = Typo.label(FontWeight.ExtraBold).copy(fontSize = 11.sp, letterSpacing = 1.sp, color = Palette.Ink.fade(0.4f)))
}

@Composable
private fun FilterRow(selected: MatchFilter, onChange: (MatchFilter) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip("To rate", selected == MatchFilter.ToRate, Modifier.weight(1f)) { onChange(MatchFilter.ToRate) }
        FilterChip("Rated", selected == MatchFilter.Rated, Modifier.weight(1f)) { onChange(MatchFilter.Rated) }
        FilterChip("All", selected == MatchFilter.All, Modifier.weight(1f)) { onChange(MatchFilter.All) }
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val fill by animateColorAsState(if (on) Palette.Coral else Palette.White, tween(180), label = "chip-fill")
    val stroke by animateColorAsState(if (on) Palette.Coral else Palette.Ink.fade(0.1f), tween(180), label = "chip-stroke")
    val ink by animateColorAsState(if (on) Palette.White else Palette.Ink, tween(180), label = "chip-ink")
    Text(
        text = label,
        textAlign = TextAlign.Center,
        style = Typo.paragraph(if (on) FontWeight.Black else FontWeight.SemiBold).copy(fontSize = 13.sp, color = ink),
        modifier = modifier
            .background(fill, shape)
            .border(1.5.dp, stroke, shape)
            .inkTap(shape) { onTap() }
            .padding(vertical = 10.dp),
    )
}

// ============================================================ Tarjeta de match

private val CARD_SHAPE = RoundedCornerShape(16.dp)

@Composable
private fun MatchCard(item: MatchHistoryItem, onRate: (MatchHistoryItem) -> Unit) {
    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .cardStyle(CARD_SHAPE)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Portrait(url = item.other.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.other.name.ifBlank { "User" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink),
                )
                if (item.other.career.isNotBlank()) {
                    Text(
                        item.other.career,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.5f)),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyphs.accessTimeRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${item.startsAt.format(WHEN_FORMAT)} – ${item.endsAt.format(END_FORMAT)}",
                        style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.5f)),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StateBadge(item.state)
        }

        // Solo los matches que ya terminaron se pueden calificar o muestran calificaciones
        if (item.state == PairingState.Finished) {
            Spacer(Modifier.height(14.dp))
            if (item.canRate) {
                PillButton(label = "Rate", onClick = { onRate(item) }, compact = true)
            } else {
                RatingsBox(item)
            }
        }
    }
}

@Composable
private fun RatingsBox(item: MatchHistoryItem) {
    val mine = item.myRating
    val theirs = item.otherRating
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Fog, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (mine != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingCaption("YOUR RATING", Modifier.weight(1f))
                Stars(mine.stars, Palette.Coral)
            }
            if (item.ratingPending) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyphs.scheduleRounded, contentDescription = null, tint = Palette.Coral, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pending to send", style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 12.sp, color = Palette.Coral))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (theirs != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingCaption("THEIR RATING", Modifier.weight(1f))
                Stars(theirs.stars, Palette.Teal)
            }
            theirs.comment?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "\"$it\"",
                    style = Typo.paragraph().copy(fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Palette.Ink.fade(0.65f)),
                )
            }
        } else {
            Text("They haven't rated you yet", style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)))
        }
    }
}

@Composable
private fun RatingCaption(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, style = Typo.label(FontWeight.ExtraBold).copy(fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Ink.fade(0.4f)))
}

@Composable
private fun Stars(count: Int, tone: Color, size: Dp = 18.dp) {
    Row {
        for (n in MatchRating.MIN_STARS..MatchRating.MAX_STARS) {
            Icon(
                if (n <= count) Glyphs.starRounded else Glyphs.starOutlineRounded,
                contentDescription = null,
                tint = if (n <= count) tone else Palette.Ink.fade(0.2f),
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
private fun StateBadge(state: PairingState) {
    val (text, tone) = when (state) {
        PairingState.Finished -> "COMPLETED" to Palette.Teal
        PairingState.Live -> "ACCEPTED" to Palette.Sky
        PairingState.AwaitingReply -> "PENDING" to Color.Gray
        PairingState.Turned -> "REJECTED" to Color.Gray
    }
    Text(
        text = text,
        style = Typo.label(FontWeight.ExtraBold).copy(fontSize = 9.sp, color = tone),
        modifier = Modifier.background(tone.fade(0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ============================================================ Hoja para calificar

@Composable
private fun RatingSheet(item: MatchHistoryItem, sending: Boolean, online: Boolean, onSend: (Int, String) -> Unit) {
    var stars by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Rate your match with ${item.other.name.ifBlank { "this person" }}",
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 18.sp, color = Palette.Ink),
        )
        Spacer(Modifier.height(4.dp))
        Text("How was this GAP?", style = Typo.paragraph().copy(fontSize = 13.sp, color = Palette.Ink.fade(0.5f)))
        Spacer(Modifier.height(16.dp))

        // Las estrellas son obligatorias: sin elegir una no se puede enviar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            for (n in MatchRating.MIN_STARS..MatchRating.MAX_STARS) {
                Icon(
                    if (n <= stars) Glyphs.starRounded else Glyphs.starOutlineRounded,
                    contentDescription = "$n stars",
                    tint = if (n <= stars) Palette.Coral else Palette.Ink.fade(0.25f),
                    modifier = Modifier.size(40.dp).inkTap(RoundedCornerShape(20.dp)) { if (!sending) stars = n },
                )
            }
        }
        if (stars == 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Choose 1 to 5 stars",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)),
            )
        }
        Spacer(Modifier.height(16.dp))

        CommentField(comment) { comment = it.take(MatchRating.MAX_COMMENT) }
        if (!online) {
            Spacer(Modifier.height(6.dp))
            Text(
                "No connection: your rating will be sent when you're back online",
                style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 12.sp, color = Palette.Coral),
            )
        }
        Spacer(Modifier.height(14.dp))

        PillButton(
            label = if (sending) "Sending…" else "Send rating",
            onClick = if (stars == 0 || sending) null else ({ onSend(stars, comment) }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CommentField(value: String, onValueChange: (String) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val textStyle = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 3,
        textStyle = textStyle,
        cursorBrush = SolidColor(Palette.Teal),
        interactionSource = source,
        decorationBox = { inner ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Fog, shape)
                    .border(1.5.dp, if (focused) Palette.Teal else Palette.Ink.fade(0.1f), shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Box {
                    if (value.isEmpty()) Text("Comment (optional)", style = textStyle.copy(color = Palette.Ink.fade(0.4f)))
                    inner()
                }
                Text(
                    "${value.length}/${MatchRating.MAX_COMMENT}",
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textAlign = TextAlign.End,
                    style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Ink.fade(0.45f)),
                )
            }
        },
    )
}

// ============================================================ Estados vacío y error

/** Sin ningún match en el historial. */
@Composable
private fun NoMatchesYet(modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Glyphs.historyRounded, contentDescription = null, tint = Palette.Ink.fade(0.1f), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "You don't have matches yet. Look for one in your next GAP!",
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

/** Hay matches, pero ninguno cae en el filtro elegido. */
@Composable
private fun NothingHere(filter: MatchFilter) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Glyphs.starOutlineRounded, contentDescription = null, tint = Palette.Ink.fade(0.1f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (filter) {
                MatchFilter.ToRate -> "You have no matches to rate."
                MatchFilter.Rated -> "You haven't rated any match yet."
                MatchFilter.All -> "You don't have matches yet."
            },
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
        )
    }
}

@Composable
private fun FaultState(message: String, offline: Boolean, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (offline) Glyphs.wifiOffRounded else Glyphs.errorOutlineRounded,
                contentDescription = null,
                tint = Palette.Coral,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (offline) "No connection" else "We couldn't load your matches",
                style = Typo.heading(FontWeight.Bold).copy(fontSize = 16.sp, color = Palette.Ink),
            )
            Spacer(Modifier.height(8.dp))
            Text(message, textAlign = TextAlign.Center, style = Typo.paragraph().copy(fontSize = 14.sp, color = Color.Gray))
            Spacer(Modifier.height(24.dp))
            PillButton(label = "Retry", onClick = onRetry)
        }
    }
}

/** Texto de los toasts de esta pantalla: Cambay 13 sp blanco. */
private fun toastStyle() = Typo.paragraph().copy(fontSize = 13.sp, color = Color.White)

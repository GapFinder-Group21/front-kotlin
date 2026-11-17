package co.gapfinder.mobile.screens.pairing

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import co.gapfinder.mobile.ui.kit.FormField
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.OfflineNote
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.Portrait
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.kit.lastUpdatedText
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "MyMatchesPage"

private val WHEN_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d · HH:mm", Locale.ENGLISH)
private val END_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

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
                Toaster.show("No connection: your rating will be sent when you're back online")
            }
            sending = true
            try {
                if (!Connectivity.isOnline()) {
                    queue()
                } else {
                    try {
                        MatchHistoryRepository.rate(item.matchId, stars, comment)
                        MatchHistoryRepository.forget()
                        Toaster.show("Thanks! Your rating was sent", background = Palette.Teal)
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
                Toaster.show(e.userText())
                rating = null
            } finally {
                sending = false
            }
            // 409 u otro rechazo: se vuelve a pedir lo del servidor para que la pantalla quede al día
            reload++
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Fog)) {
        InkHeader(title = "My Matches", showBack = true)

        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
            val current = history
            when {
                pending -> CircularProgressIndicator(Modifier.size(36.dp).align(Alignment.Center))
                fault != null -> FaultState(fault.orEmpty()) { scope.launch { pullHistory(force = true) } }
                current == null -> Unit
                else -> PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { scope.launch { pullHistory(force = true) } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val shown = current.of(filter)
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        savedAt?.let { moment ->
                            item {
                                OfflineNote(
                                    "${if (online) "Couldn't refresh" else "No connection"} · ${lastUpdatedText(moment)}",
                                    Modifier.padding(bottom = 16.dp),
                                )
                            }
                        }
                        item { SummaryRow(current) }
                        item { FilterRow(filter, current) { filter = it } }
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

    rating?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!sending) rating = null },
            sheetState = sheetState,
            containerColor = Palette.White,
        ) {
            RatingSheet(target, sending) { stars, comment -> submitRating(target, stars, comment) }
        }
    }
}

// ============================================================ Resumen y filtros

@Composable
private fun SummaryRow(view: MatchHistoryView) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryBox(
            caption = "AVG. RECEIVED",
            value = view.averageReceived?.let { String.format(Locale.ENGLISH, "%.1f ★", it) } ?: "—",
            note = if (view.receivedCount == 0) "No ratings yet" else "${view.receivedCount} rating${if (view.receivedCount == 1) "" else "s"}",
            tone = Palette.Sky,
            modifier = Modifier.weight(1f),
        )
        SummaryBox(
            caption = "TO RATE",
            value = view.toRateCount.toString(),
            note = if (view.toRateCount == 0) "You're up to date" else "finished matches",
            tone = Palette.Coral,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryBox(caption: String, value: String, note: String, tone: Color, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .background(Palette.White, shape)
            .border(1.5.dp, Palette.Ink.fade(0.07f), shape)
            .padding(14.dp),
    ) {
        Text(caption, style = Typo.label(FontWeight.ExtraBold).copy(fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Ink.fade(0.45f)))
        Spacer(Modifier.height(4.dp))
        Text(value, style = Typo.heading(FontWeight.Black).copy(fontSize = 24.sp, color = tone))
        Text(note, style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Ink.fade(0.5f)))
    }
}

@Composable
private fun FilterRow(selected: MatchFilter, view: MatchHistoryView, onChange: (MatchFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip("To rate (${view.toRate.size})", selected == MatchFilter.ToRate) { onChange(MatchFilter.ToRate) }
        FilterChip("Rated (${view.rated.size})", selected == MatchFilter.Rated) { onChange(MatchFilter.Rated) }
        FilterChip("All (${view.all.size})", selected == MatchFilter.All) { onChange(MatchFilter.All) }
    }
}

@Composable
private fun FilterChip(label: String, on: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        style = Typo.paragraph(FontWeight.ExtraBold).copy(fontSize = 12.sp, color = if (on) Palette.White else Palette.Ink.fade(0.7f)),
        modifier = Modifier
            .background(if (on) Palette.Ink else Palette.White, shape)
            .border(1.dp, if (on) Palette.Ink else Palette.Ink.fade(0.15f), shape)
            .inkTap(shape) { onTap() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// ============================================================ Tarjeta de match

@Composable
private fun MatchCard(item: MatchHistoryItem, onRate: (MatchHistoryItem) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.04f), spotColor = Palette.Ink.fade(0.04f))
            .background(Palette.White, shape)
            .border(1.5.dp, Palette.Ink.fade(0.07f), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Portrait(url = item.other.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.other.name.ifBlank { "User" }, style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink))
                val detail = listOfNotNull(
                    item.other.career.takeIf { it.isNotBlank() },
                    item.other.semester.takeIf { it > 0 }?.let { "Semester $it" },
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.55f)))
                }
            }
            StateBadge(item.state)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Glyphs.accessTimeRounded, contentDescription = null, tint = Palette.Ink.fade(0.4f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${item.startsAt.format(WHEN_FORMAT)} – ${item.endsAt.format(END_FORMAT)}",
                style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.6f)),
            )
        }

        when {
            // Todavía no terminó: solo se explica en qué va
            item.state != PairingState.Finished -> {
                Spacer(Modifier.height(10.dp))
                Text(stateNote(item.state), style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.5f)))
            }

            item.canRate -> {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "How was it?",
                        style = Typo.paragraph(FontWeight.SemiBold).copy(fontSize = 13.sp, color = Palette.Ink.fade(0.7f)),
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(label = "Rate", onClick = { onRate(item) }, compact = true)
                }
            }

            else -> {
                val mine = item.myRating
                if (mine != null) {
                    Spacer(Modifier.height(14.dp))
                    RatingBlock("You rated", mine, pendingSend = item.ratingPending)
                }
                Spacer(Modifier.height(10.dp))
                val theirs = item.otherRating
                if (theirs != null) {
                    RatingBlock("Their rating", theirs)
                } else {
                    Text(
                        "They haven't rated you yet",
                        style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingBlock(caption: String, rating: MatchRating, pendingSend: Boolean = false) {
    Column(Modifier.fillMaxWidth().background(Palette.Fog, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(caption.uppercase(), style = Typo.label(FontWeight.ExtraBold).copy(fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Ink.fade(0.45f)))
            Spacer(Modifier.width(10.dp))
            Stars(rating.stars, size = 16.dp)
            if (pendingSend) {
                Spacer(Modifier.weight(1f))
                Text(
                    "PENDING TO SEND",
                    style = Typo.label(FontWeight.ExtraBold).copy(fontSize = 9.sp, color = Palette.Sky),
                    modifier = Modifier
                        .background(Palette.Sky.fade(0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        rating.comment?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = Typo.paragraph().copy(fontSize = 13.sp, color = Palette.Ink.fade(0.8f)))
        }
    }
}

@Composable
private fun Stars(count: Int, size: Dp) {
    Row {
        for (n in MatchRating.MIN_STARS..MatchRating.MAX_STARS) {
            Icon(
                if (n <= count) Glyphs.starRounded else Glyphs.starOutlineRounded,
                contentDescription = null,
                tint = if (n <= count) Color(0xFFFFB400) else Palette.Ink.fade(0.25f),
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
private fun StateBadge(state: PairingState) {
    val (text, tone) = when (state) {
        PairingState.Finished -> "COMPLETED" to Palette.Teal
        PairingState.Live -> "IN PROGRESS" to Palette.Sky
        PairingState.AwaitingReply -> "PENDING" to Palette.Ink.fade(0.5f)
        PairingState.Turned -> "DECLINED" to Palette.Coral
    }
    Text(
        text = text,
        style = Typo.label(FontWeight.ExtraBold).copy(fontSize = 9.sp, color = tone),
        modifier = Modifier.background(tone.fade(0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private fun stateNote(state: PairingState): String = when (state) {
    PairingState.AwaitingReply -> "Waiting for their reply. You can rate it once it's over."
    PairingState.Live -> "This match is in progress. You can rate it once it's over."
    PairingState.Turned -> "This match was declined."
    PairingState.Finished -> ""
}

// ============================================================ Hoja para calificar

@Composable
private fun RatingSheet(item: MatchHistoryItem, sending: Boolean, onSend: (Int, String) -> Unit) {
    var stars by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Portrait(url = item.other.avatarUrl, size = 56.dp)
        Spacer(Modifier.height(10.dp))
        Text("Rate your match with ${item.other.name.ifBlank { "this person" }}", textAlign = TextAlign.Center, style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 17.sp, color = Palette.Ink))
        Spacer(Modifier.height(4.dp))
        Text(
            "They'll only see your rating after they rate you too.",
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.55f)),
        )
        Spacer(Modifier.height(18.dp))

        // Las cinco estrellas son obligatorias: sin elegir una no se puede enviar
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (n in MatchRating.MIN_STARS..MatchRating.MAX_STARS) {
                Icon(
                    if (n <= stars) Glyphs.starRounded else Glyphs.starOutlineRounded,
                    contentDescription = "$n stars",
                    tint = if (n <= stars) Color(0xFFFFB400) else Palette.Ink.fade(0.25f),
                    modifier = Modifier.size(40.dp).inkTap(RoundedCornerShape(20.dp)) { if (!sending) stars = n },
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        FormField(
            placeholder = "Add a comment (optional)",
            value = comment,
            onValueChange = { comment = it.take(MatchRating.MAX_COMMENT) },
        )
        Text(
            "${comment.length}/${MatchRating.MAX_COMMENT}",
            style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Ink.fade(0.45f)),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(16.dp))

        PillButton(
            label = if (sending) "Sending…" else "Send rating",
            onClick = if (stars == 0 || sending) null else ({ onSend(stars, comment) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ============================================================ Estados vacío y error

@Composable
private fun NothingHere(filter: MatchFilter) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Glyphs.historyRounded, contentDescription = null, tint = Palette.Ink.fade(0.1f), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            text = when (filter) {
                MatchFilter.ToRate -> "Nothing to rate right now."
                MatchFilter.Rated -> "You haven't rated any match yet."
                MatchFilter.All -> "No matches yet."
            },
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
        )
    }
}

@Composable
private fun FaultState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Glyphs.errorOutlineRounded, contentDescription = null, tint = Palette.Coral, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Error loading matches", style = Typo.heading(FontWeight.Bold).copy(fontSize = 16.sp, color = Palette.Ink))
            Spacer(Modifier.height(8.dp))
            Text(message, textAlign = TextAlign.Center, style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)))
            Spacer(Modifier.height(24.dp))
            PillButton(label = "Retry", onClick = onRetry)
        }
    }
}

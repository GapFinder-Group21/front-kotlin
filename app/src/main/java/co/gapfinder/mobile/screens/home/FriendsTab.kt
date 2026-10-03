package co.gapfinder.mobile.screens.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.BondRepository
import co.gapfinder.mobile.data.MemberRepository
import co.gapfinder.mobile.data.WindowRepository
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private const val LOG_TAG = "FriendsTab"

/** Primario del tema M3: borde del buscador cuando tiene el foco. */
private val FocusedEdge = Color(0xFF65558F)

private data class FreeBuddy(val member: Member, val window: FreeWindow?)

/** Solicitud de amistad recibida, con quien la envió. */
private data class BondAsk(val bondId: Int, val sender: Member)

private data class BuddyRoster(
    val available: List<FreeBuddy>,
    val attending: List<Member>,
    val asks: List<BondAsk>,
) {
    val isEmpty: Boolean get() = available.isEmpty() && attending.isEmpty() && asks.isEmpty()
}

/** Búsqueda en curso (no es estado de UI: cambiarla no debe recomponer). */
private class LookupHandle {
    var job: Job? = null
}

/**
 * Pestaña de amigos: buscador de usuarios, solicitudes recibidas, quién tiene GAP ahora
 * y quién está en clase (antes FriendsScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsTab(isActive: Boolean) {
    var cycle by remember { mutableIntStateOf(0) }
    // Como FutureBuilder: mientras recarga conserva el último dato (lo usa la cabecera)
    var loading by remember { mutableStateOf(true) }
    var roster by remember { mutableStateOf<BuddyRoster?>(null) }
    var failure by remember { mutableStateOf<Throwable?>(null) }

    // Búsqueda de usuarios por nombre
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<Member>>(emptyList()) }
    val lookup = remember { LookupHandle() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cycle) {
        loading = true
        try {
            roster = collectRoster()
            failure = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            roster = null
            failure = e
        }
        loading = false
    }

    val reload: () -> Unit = {
        loading = true
        cycle++
    }

    OnTabWake(isActive) { reload() }

    // Busca con cada cambio del texto; la consulta anterior se cancela para que no pise a la nueva
    fun search(text: String) {
        query = text
        lookup.job?.cancel()
        if (text.isBlank()) {
            matches = emptyList()
            searching = false
            return
        }
        searching = true
        lookup.job = scope.launch {
            try {
                val me = SessionVault.memberId()
                matches = MemberRepository.searchByName(text.trim()).filter { it.id != me }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error searching users: $e")
            }
        }
    }

    fun sendAsk(targetId: Int) {
        scope.launch {
            val me = SessionVault.memberId() ?: return@launch
            try {
                BondRepository.request(me, targetId)
                Toaster.show("Friend request sent!")
                search("")
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // El backend explica el motivo (ya son amigos, solicitud repetida, etc.)
                Toaster.show(e.message ?: e.toString())
            }
        }
    }

    fun answerAsk(bondId: Int, accept: Boolean) {
        scope.launch {
            val me = SessionVault.memberId() ?: return@launch
            try {
                if (accept) BondRepository.approve(bondId, me) else BondRepository.decline(bondId, me)
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val verb = if (accept) "accepting" else "rejecting"
                Toaster.show("Error $verb request: ${e.dartStyleText()}")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
            .statusBarsPadding()
    ) {
        RosterTopBar(roster)
        UserSearchBar(
            text = query,
            showClear = searching || query.isNotEmpty(),
            onText = { search(it) },
            onClear = { search("") },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val current = roster
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp))
                failure != null || current == null -> TroubleNotice(reload, Modifier.align(Alignment.Center))
                searching -> SearchResults(matches, onAdd = { sendAsk(it.id) })
                current.isEmpty -> LonelyNotice(Modifier.align(Alignment.Center))
                else -> PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = reload,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        if (current.asks.isNotEmpty()) {
                            item {
                                GroupCaption(
                                    text = "Friend Requests",
                                    total = current.asks.size,
                                    bullet = Palette.Sky,
                                )
                            }
                            item { Spacer(Modifier.height(14.dp)) }
                            items(current.asks) { ask ->
                                BondAskCard(
                                    ask = ask,
                                    onAccept = { answerAsk(ask.bondId, accept = true) },
                                    onReject = { answerAsk(ask.bondId, accept = false) },
                                )
                            }
                            item { Spacer(Modifier.height(20.dp)) }
                        }
                        if (current.available.isNotEmpty()) {
                            item {
                                GroupCaption(
                                    text = "With GAP now",
                                    total = current.available.size,
                                    bullet = Palette.Coral,
                                )
                            }
                            item { Spacer(Modifier.height(14.dp)) }
                            items(current.available) { buddy -> FreeBuddyCard(buddy) }
                        }
                        if (current.attending.isNotEmpty()) {
                            item { Spacer(Modifier.height(4.dp)) }
                            item {
                                GroupCaption(
                                    text = "In class",
                                    total = current.attending.size,
                                    bullet = Palette.Ink.fade(0.18f),
                                    tint = Palette.Ink.fade(0.3f),
                                    muted = true,
                                )
                            }
                            item { Spacer(Modifier.height(12.dp)) }
                            item { AttendingGrid(current.attending) }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

/** Trae amigos y solicitudes pendientes, y separa a los amigos según tengan un GAP corriendo ahora. */
private suspend fun collectRoster(): BuddyRoster {
    val me = SessionVault.memberId() ?: return BuddyRoster(emptyList(), emptyList(), emptyList())

    val everyone = BondRepository.friendsOf(me)
    val incoming = BondRepository.incomingFor(me)

    // La solicitud puede traer al usuario completo o solo su id; en ese caso se consulta
    val asks = incoming.mapNotNull { bond ->
        val sender = bond.requester ?: bond.requesterId?.let { senderId ->
            try {
                MemberRepository.byId(senderId)
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                null
            }
        }
        sender?.let { BondAsk(bond.id, it) }
    }

    val now = Chrono.now()
    val available = mutableListOf<FreeBuddy>()
    val attending = mutableListOf<Member>()
    for (friend in everyone) {
        val live = WindowRepository.weekOf(friend.id).firstOrNull { it.isLiveAt(now) }
        if (live != null) available += FreeBuddy(friend, live) else attending += friend
    }
    return BuddyRoster(available, attending, asks)
}

private fun clockOf(moment: LocalDateTime): String = twelveHourClock(moment.hour, moment.minute)

private fun spanText(window: FreeWindow?): String? =
    window?.let { "${clockOf(it.startsAt)} – ${clockOf(it.endsAt)}" }

/** "Systems Engineering · Sem 5" */
private fun Member.studyLine(): String = "$career · Sem $semester"

@Composable
private fun RosterTopBar(roster: BuddyRoster?) {
    val everybody = roster?.let { r -> r.available.map { it.member } + r.attending } ?: emptyList()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Friends",
            style = Typo.heading(FontWeight.ExtraBold).copy(
                fontSize = 20.sp,
                color = Palette.White,
                letterSpacing = 0.5.sp,
            ),
        )
        Spacer(Modifier.width(10.dp))
        if (everybody.isNotEmpty()) {
            Box(
                Modifier
                    .size(24.dp)
                    .background(Palette.Teal, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${everybody.size}",
                    style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 12.sp, color = Palette.White),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (everybody.isNotEmpty()) OverlappingFaces(everybody.take(5))
    }
}

@Composable
private fun OverlappingFaces(people: List<Member>) {
    Box(
        Modifier
            .height(32.dp)
            .width(32.dp + 22.dp * (people.size - 1))
    ) {
        people.forEachIndexed { index, person ->
            FaceBubble(
                url = person.avatarUrl,
                diameter = 32.dp,
                ring = Palette.Ink,
                ringWidth = 2.dp,
                modifier = Modifier.offset(x = 22.dp * index),
            )
        }
    }
}

/** Buscador de usuarios (TextField con borde, lupa a la izquierda y botón de limpiar). */
@Composable
private fun UserSearchBar(text: String, showClear: Boolean, onText: (String) -> Unit, onClear: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    val typed = Typo.paragraph().copy(fontSize = 14.sp, color = InheritedInk)

    Box(
        Modifier
            .fillMaxWidth()
            .background(Palette.White)
            .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 12.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = typed,
            cursorBrush = SolidColor(FocusedEdge),
            interactionSource = source,
            decorationBox = { field ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(Palette.Fog, shape)
                        .border(
                            width = if (focused) 2.dp else 1.5.dp,
                            color = if (focused) FocusedEdge else Palette.Ink.fade(0.1f),
                            shape = shape,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(Glyphs.searchRounded, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(24.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Search users by name...",
                                style = typed.copy(color = Palette.Ink.fade(0.4f)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        field()
                    }
                    if (showClear) {
                        TapIcon(Glyphs.clearRounded, tint = Color(0xFF49454F), iconSize = 24.dp, onTap = onClear)
                    } else {
                        Spacer(Modifier.width(16.dp))
                    }
                }
            },
        )
    }
}

/** IconButton de M3: ícono dentro de un área táctil de 48. */
@Composable
private fun TapIcon(glyph: ImageVector, tint: Color, iconSize: Dp, onTap: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .inkTap(CircleShape, onTap = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun SearchResults(people: List<Member>, onAdd: (Member) -> Unit) {
    if (people.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No users found",
                style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.5f)),
            )
        }
        return
    }

    val shape = RoundedCornerShape(16.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(people) { person ->
            Row(
                Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .background(Palette.White, shape)
                    .border(1.dp, Palette.Ink.fade(0.08f), shape)
                    .padding(15.dp), // padding de Flutter + ancho del borde
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaceBubble(url = person.avatarUrl, diameter = 44.dp, ring = Palette.Ink.fade(0.1f), ringWidth = 1.5.dp)
                Spacer(Modifier.width(14.dp))
                PersonLines(person, Modifier.weight(1f))
                AddPill { onAdd(person) }
            }
        }
    }
}

/** Nombre + "carrera · semestre". */
@Composable
private fun PersonLines(person: Member, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = person.name,
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = person.studyLine(),
            style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)),
        )
    }
}

/** Botón "Add" (ElevatedButton 70x32 con área táctil de 48). */
@Composable
private fun AddPill(onPress: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .defaultMinSize(minWidth = 70.dp, minHeight = 32.dp)
                .background(Palette.Teal, shape)
                .inkTap(shape, onTap = onPress)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Add",
                style = Typo.heading(FontWeight.Black).copy(fontSize = 11.sp, color = Palette.White),
            )
        }
    }
}

@Composable
private fun BondAskCard(ask: BondAsk, onAccept: () -> Unit, onReject: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.05f), spotColor = Palette.Ink.fade(0.05f))
            .background(Palette.White, shape)
            .border(1.5.dp, Palette.Sky.fade(0.3f), shape)
            .padding(17.5.dp), // padding de Flutter + ancho del borde
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaceBubble(url = ask.sender.avatarUrl, diameter = 48.dp, ring = Palette.Sky, ringWidth = 2.dp)
        Spacer(Modifier.width(14.dp))
        PersonLines(ask.sender, Modifier.weight(1f))
        TapIcon(Glyphs.checkCircleRounded, tint = Palette.Teal, iconSize = 32.dp, onTap = onAccept)
        TapIcon(Glyphs.cancelRounded, tint = Palette.Coral, iconSize = 32.dp, onTap = onReject)
    }
}

@Composable
private fun GroupCaption(
    text: String,
    total: Int,
    bullet: Color,
    tint: Color? = null,
    muted: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(bullet, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = Typo.heading(FontWeight.ExtraBold).copy(
                fontSize = if (muted) 14.sp else 16.sp,
                color = tint ?: Palette.Ink,
            ),
        )
        Spacer(Modifier.width(8.dp))
        if (!muted) {
            Box(
                Modifier
                    .background(Palette.Coral, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$total",
                    style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 12.sp, color = Palette.White),
                )
            }
        } else {
            Text(
                text = "$total",
                style = Typo.heading(FontWeight.ExtraBold).copy(
                    fontSize = 14.sp,
                    color = Palette.Ink.fade(0.2f),
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeBuddyCard(buddy: FreeBuddy) {
    val person = buddy.member
    val span = spanText(buddy.window)
    val shape = RoundedCornerShape(18.dp)

    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.05f), spotColor = Palette.Ink.fade(0.05f))
            .background(Palette.White, shape)
            .border(1.5.dp, Palette.Teal.fade(0.3f), shape)
            .padding(1.5.dp) // Container suma el ancho del borde al padding
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FaceBubble(url = person.avatarUrl, diameter = 48.dp, ring = Palette.Teal, ringWidth = 2.dp)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .background(Palette.Teal, CircleShape)
                        .border(2.dp, Palette.White, CircleShape)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${person.career} · ${person.semester}",
                    style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)),
                )
            }
        }

        if (span != null) {
            Row(
                Modifier
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp)
                    .background(Palette.Teal.fade(0.08f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Glyphs.accessTime, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = span,
                    style = Typo.heading(FontWeight.Bold).copy(fontSize = 12.sp, color = Palette.Teal),
                )
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            person.hobbies.take(3).forEach { hobby ->
                Text(
                    text = hobby.name,
                    style = Typo.paragraph(FontWeight.Bold).copy(
                        fontSize = 10.sp,
                        color = Palette.Ink.fade(0.5f),
                    ),
                    modifier = Modifier
                        .background(Palette.Ink.fade(0.05f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttendingGrid(people: List<Member>) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 3 columnas: (ancho - 2 espacios de 12) / 3, calculado en px para que quepan exactas
        val gapPx = with(density) { 12.dp.roundToPx() }
        val cellWidth = with(density) { ((constraints.maxWidth - 2 * gapPx) / 3).toDp() }
        val cardShape = RoundedCornerShape(14.dp)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            people.forEach { person ->
                Column(
                    Modifier
                        .width(cellWidth)
                        .alpha(0.7f)
                        .background(Palette.White, cardShape)
                        .border(1.dp, Palette.Ink.fade(0.08f), cardShape)
                        .padding(vertical = 13.dp, horizontal = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FaceBubble(
                        url = person.avatarUrl,
                        diameter = 36.dp,
                        ring = Palette.Ink.fade(0.1f),
                        ringWidth = 1.5.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = person.name.split(" ").first(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 11.sp, color = Palette.Ink),
                    )
                    Text(
                        text = "In class",
                        style = Typo.paragraph().copy(fontSize = 10.sp, color = Palette.Ink.fade(0.4f)),
                    )
                }
            }
        }
    }
}

/**
 * Avatar circular con borde. Igual que el Container de Flutter: el borde se pinta
 * debajo de la imagen y la imagen queda inset por el ancho del borde.
 */
@Composable
private fun FaceBubble(
    url: String?,
    diameter: Dp,
    ring: Color,
    ringWidth: Dp,
    modifier: Modifier = Modifier,
) {
    var imageFailed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(Palette.Ink.fade(0.06f))
            .drawBehind {
                val stroke = ringWidth.toPx()
                drawCircle(
                    color = ring,
                    radius = size.minDimension / 2f - stroke / 2f,
                    style = Stroke(width = stroke),
                )
            }
            .padding(ringWidth),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrEmpty() && !imageFailed) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { imageFailed = true },
            )
        } else {
            Icon(
                imageVector = Glyphs.person,
                contentDescription = null,
                tint = Palette.Ink.fade(0.3f),
                modifier = Modifier.size(diameter * 0.5f),
            )
        }
    }
}

@Composable
private fun LonelyNotice(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Glyphs.peopleOutline,
            contentDescription = null,
            tint = Palette.Ink.fade(0.15f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No friends added yet",
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 16.sp, color = InheritedInk),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add friends to see when they have free GAPs.",
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.5f)),
        )
    }
}

@Composable
private fun TroubleNotice(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Glyphs.cloudOff,
            contentDescription = null,
            tint = Palette.Ink.fade(0.2f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Something went wrong",
            style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 14.sp, color = InheritedInk),
        )
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

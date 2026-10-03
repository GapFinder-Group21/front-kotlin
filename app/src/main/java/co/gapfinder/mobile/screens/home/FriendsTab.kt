package co.gapfinder.mobile.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.BondRepository
import co.gapfinder.mobile.data.WindowRepository
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime

private data class FreeBuddy(val member: Member, val window: FreeWindow?)

private data class BuddyRoster(val available: List<FreeBuddy>, val attending: List<Member>)

/** Pestaña de amigos: quién tiene GAP ahora y quién está en clase (antes FriendsScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsTab(isActive: Boolean) {
    var cycle by remember { mutableIntStateOf(0) }
    // Como FutureBuilder: mientras recarga conserva el último dato (lo usa la cabecera)
    var loading by remember { mutableStateOf(true) }
    var roster by remember { mutableStateOf<BuddyRoster?>(null) }
    var failure by remember { mutableStateOf<Throwable?>(null) }

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

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
            .statusBarsPadding()
    ) {
        RosterTopBar(roster)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val current = roster
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp))
                failure != null || current == null -> TroubleNotice(reload, Modifier.align(Alignment.Center))
                current.available.isEmpty() && current.attending.isEmpty() ->
                    LonelyNotice(Modifier.align(Alignment.Center))
                else -> PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = reload,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
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

/** Trae los amigos y los separa según tengan un GAP corriendo ahora. */
private suspend fun collectRoster(): BuddyRoster {
    val me = SessionVault.memberId() ?: return BuddyRoster(emptyList(), emptyList())

    val everyone = BondRepository.friendsOf(me)

    val now = Chrono.now()
    val available = mutableListOf<FreeBuddy>()
    val attending = mutableListOf<Member>()
    for (friend in everyone) {
        val live = WindowRepository.weekOf(friend.id).firstOrNull { it.isLiveAt(now) }
        if (live != null) available += FreeBuddy(friend, live) else attending += friend
    }
    return BuddyRoster(available, attending)
}

private fun clockOf(moment: LocalDateTime): String = twelveHourClock(moment.hour, moment.minute)

private fun spanText(window: FreeWindow?): String? =
    window?.let { "${clockOf(it.startsAt)} – ${clockOf(it.endsAt)}" }

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

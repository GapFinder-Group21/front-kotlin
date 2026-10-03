package co.gapfinder.mobile.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.RecommendationRepository
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.domain.Recommendation
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.Portrait
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDateTime

/**
 * Pestaña "Suggest": edificio que más frecuenta el usuario y las mesas abiertas que hay ahí
 * (antes RecommendationsScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsTab(isActive: Boolean) {
    var cycle by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<Fetch<Recommendation>>(Fetch.Waiting) }

    LaunchedEffect(cycle) {
        outcome = Fetch.Waiting
        outcome = try {
            val me = SessionVault.memberId() ?: throw Exception("No user session found")
            Fetch.Done(RecommendationRepository.hangoutsFor(me))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Fetch.Broken(e)
        }
    }

    val reload: () -> Unit = {
        outcome = Fetch.Waiting
        cycle++
    }

    OnTabWake(isActive) { reload() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
            .statusBarsPadding()
    ) {
        SuggestHeader()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val state = outcome) {
                Fetch.Waiting -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp))

                is Fetch.Broken -> LoadFailure(
                    detail = state.cause.message ?: state.cause.toString(),
                    onRetry = reload,
                    modifier = Modifier.align(Alignment.Center),
                )

                is Fetch.Done -> PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = reload,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val data = state.value
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                        item { FavoriteSpotCard(data) }
                        item { Spacer(Modifier.height(28.dp)) }
                        item { SuggestedCaption(total = data.hangouts.size) }
                        item { Spacer(Modifier.height(14.dp)) }
                        if (data.hangouts.isEmpty()) {
                            item { NoSuggestions() }
                        } else {
                            items(data.hangouts) { table -> SuggestedCard(table) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Ink)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .background(Palette.Teal.fade(0.15f), CircleShape)
                .padding(8.dp)
        ) {
            Icon(Glyphs.lightbulbRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Recommendation",
            style = Typo.heading(FontWeight.Black).copy(
                fontSize = 20.sp,
                color = Palette.White,
                letterSpacing = 0.5.sp,
            ),
        )
    }
}

@Composable
private fun LoadFailure(detail: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Glyphs.errorOutlineRounded,
            contentDescription = null,
            tint = Palette.Coral.fade(0.7f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Could not load recommendations",
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 16.sp, color = InheritedInk),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = detail,
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.5f)),
        )
        Spacer(Modifier.height(16.dp))
        TealPill(
            label = "Retry",
            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
            horizontal = 24.dp,
            vertical = 10.dp,
            onTap = onRetry,
        )
    }
}

/** Tarjeta destacada con el edificio favorito y los minutos acumulados ahí. */
@Composable
private fun FavoriteSpotCard(data: Recommendation) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = Palette.Teal.fade(0.12f), spotColor = Palette.Teal.fade(0.12f))
            .background(Palette.White, shape)
            .border(2.dp, Palette.Teal, shape)
            .padding(24.dp) // padding de Flutter + ancho del borde
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(Palette.Teal.fade(0.15f), CircleShape)
                    .padding(10.dp)
            ) {
                Icon(Glyphs.starRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "TOP FREQUENTED",
                    style = Typo.label(FontWeight.ExtraBold).copy(
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = Palette.Teal,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Favorite Building",
                    style = Typo.heading(FontWeight.Black).copy(fontSize = 18.sp, color = Palette.Ink),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val innerShape = RoundedCornerShape(14.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Fog, innerShape)
                .border(1.dp, Palette.Ink.fade(0.08f), innerShape)
                .padding(horizontal = 17.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyphs.businessRounded, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = data.favoriteSpotName ?: "No frequent building yet",
                style = Typo.heading(FontWeight.Black).copy(fontSize = 16.sp, color = Palette.Ink),
                modifier = Modifier.weight(1f),
            )
            if (data.totalMinutes > 0) {
                Text(
                    text = "${data.totalMinutes.toInt()} mins",
                    style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 12.sp, color = Palette.Teal),
                    modifier = Modifier
                        .background(Palette.Teal.fade(0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Glyphs.infoOutlineRounded,
                contentDescription = null,
                tint = Palette.Ink.fade(0.4f),
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Recalculated daily based on your location activity",
                style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Ink.fade(0.45f)),
            )
        }
    }
}

@Composable
private fun SuggestedCaption(total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(Palette.Teal, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Recommended Open Tables",
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 16.sp, color = Palette.Ink),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "$total available",
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 11.sp, color = Palette.Teal),
            modifier = Modifier
                .background(Palette.Teal.fade(0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun NoSuggestions() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.White, shape)
            .border(1.dp, Palette.Ink.fade(0.08f), shape)
            .padding(41.dp), // padding de Flutter + ancho del borde
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Glyphs.tableRestaurantOutlined,
            contentDescription = null,
            tint = Palette.Ink.fade(0.2f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No open tables found",
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = InheritedInk),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "There are no active open tables in your favorite building right now.",
            textAlign = TextAlign.Center,
            style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.5f)),
        )
    }
}

@Composable
private fun SuggestedCard(table: Hangout) {
    val minutesLeft = Duration.between(LocalDateTime.now(), table.endsAt).toMinutes()
    val shape = RoundedCornerShape(18.dp)

    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.04f), spotColor = Palette.Ink.fade(0.04f))
            .background(Palette.White, shape)
            .border(1.5.dp, Palette.Teal.fade(0.3f), shape)
            .padding(1.5.dp) // Container suma el ancho del borde al padding
    ) {
        // Cabecera: anfitrión, lugar y tiempo restante
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Portrait(url = table.host?.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = table.host?.name ?: "User",
                    style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink),
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Glyphs.locationOnRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = table.spot?.name ?: "Unknown location",
                        style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 12.sp, color = Palette.Ink.fade(0.5f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Glyphs.accessTimeRounded,
                        contentDescription = null,
                        tint = Palette.Ink.fade(0.35f),
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${minutesLeft}m left",
                        style = Typo.paragraph().copy(fontSize = 12.sp, color = Palette.Ink.fade(0.45f)),
                    )
                }
            }
        }

        // Título de la mesa
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .background(Palette.Teal.fade(0.08f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyphs.tableBarRounded, contentDescription = null, tint = Palette.Teal, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = table.title,
                style = Typo.label(FontWeight.ExtraBold).copy(fontSize = 12.sp, color = Palette.Teal),
                modifier = Modifier.weight(1f),
            )
        }

        // Pie: cupo + botón View
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Up to ${table.maxParticipants} people",
                style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 11.sp, color = Palette.Ink.fade(0.45f)),
            )
            Spacer(Modifier.weight(1f))
            TealPill(
                label = "View",
                textStyle = Typo.paragraph(FontWeight.ExtraBold).copy(fontSize = 12.sp),
                horizontal = 18.dp,
                vertical = 7.dp,
                onTap = { StackNavigator.go(Destination.HangoutDetail(table.id)) },
            )
        }
    }
}

/** ElevatedButton turquesa de pastilla (alto mínimo 40 dentro de un área táctil de 48). */
@Composable
private fun TealPill(
    label: String,
    textStyle: TextStyle,
    horizontal: Dp,
    vertical: Dp,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.padding(vertical = 4.dp)) {
        Box(
            Modifier
                .defaultMinSize(minWidth = 64.dp, minHeight = 40.dp)
                .background(Palette.Teal, shape)
                .inkTap(shape, onTap = onTap)
                .padding(horizontal = horizontal, vertical = vertical),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = textStyle.copy(color = Palette.White))
        }
    }
}

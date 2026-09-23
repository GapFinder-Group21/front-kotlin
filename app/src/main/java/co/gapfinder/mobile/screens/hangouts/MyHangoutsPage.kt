package co.gapfinder.mobile.screens.hangouts

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.HangoutRepository
import co.gapfinder.mobile.domain.Hangout
import co.gapfinder.mobile.domain.HangoutState
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.Glyphs
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.Portrait
import co.gapfinder.mobile.ui.kit.inkTap
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val TAG = "MyHangoutsPage"

/** Mesas creadas y en las que participé (antes MyOpenTablesScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHangoutsPage() {
    var hosted by remember { mutableStateOf<List<Hangout>>(emptyList()) }
    var attended by remember { mutableStateOf<List<Hangout>>(emptyList()) }
    var pending by remember { mutableStateOf(true) }
    var fault by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun pullEverything() {
        pending = true
        fault = null
        try {
            val me = SessionVault.memberId() ?: return
            val (mine, joined) = coroutineScope {
                val a = async { HangoutRepository.hostedBy(me) }
                val b = async { HangoutRepository.joinedBy(me) }
                a.await() to b.await()
            }
            hosted = mine
            // Se quitan de "unidas" las que ya aparecen como creadas
            val ownIds = mine.map { it.id }.toSet()
            attended = joined.filter { it.id !in ownIds }
            pending = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Error loading my Open Tables: $e")
            fault = e.asDartString()
            pending = false
        }
    }

    LaunchedEffect(Unit) { pullEverything() }

    Column(Modifier.fillMaxSize().background(Palette.Fog)) {
        InkHeader(title = "My Open Tables", showBack = true)

        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
            if (pending) {
                CircularProgressIndicator(Modifier.size(36.dp).align(Alignment.Center))
            } else {
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = { scope.launch { pullEverything() } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val problem = fault
                    when {
                        problem != null -> FaultState(problem) { scope.launch { pullEverything() } }
                        hosted.isEmpty() && attended.isEmpty() -> NothingYet()
                        else -> LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                        ) {
                            if (hosted.isNotEmpty()) {
                                item { GroupTitle("Created by me") }
                                items(hosted) { HistoryCard(it, owner = true) }
                                item { Spacer(Modifier.height(20.dp)) }
                            }
                            if (attended.isNotEmpty()) {
                                item { GroupTitle("Participated") }
                                items(attended) { HistoryCard(it, owner = false) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = Typo.label(FontWeight.ExtraBold).copy(
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            color = Palette.Ink.fade(0.4f),
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
    )
}

@Composable
private fun FaultState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Glyphs.errorOutlineRounded, contentDescription = null, tint = Palette.Coral, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Error loading tables",
                style = Typo.heading(FontWeight.Bold).copy(fontSize = 16.sp, color = SurfaceText),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = Typo.paragraph().copy(fontSize = 14.sp, color = MidGrey),
            )
            Spacer(Modifier.height(24.dp))
            // ElevatedButton con estilo por defecto de Material 3
            val pill = RoundedCornerShape(50)
            Box(Modifier.padding(vertical = 4.dp)) {
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 64.dp, minHeight = 40.dp)
                        .shadow(1.dp, pill)
                        .background(Color(0xFFF7F2FA), pill)
                        .inkTap(pill) { onRetry() }
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Retry",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.1.sp,
                            color = ThemePrimary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NothingYet() {
    val boxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth().height(boxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Glyphs.historyRounded, contentDescription = null, tint = Palette.Ink.fade(0.1f), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No Open Tables found.",
                textAlign = TextAlign.Center,
                style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.4f)),
            )
        }
    }
}

@Composable
private fun HistoryCard(item: Hangout, owner: Boolean) {
    val live = item.state == HangoutState.Running
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Palette.Ink.fade(0.04f), spotColor = Palette.Ink.fade(0.04f))
            .background(Color.White, shape)
            .border(1.5.dp, Palette.Ink.fade(0.07f), shape)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Portrait(url = item.host?.avatarUrl, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (owner) "You (Owner)" else (item.host?.name ?: "User"),
                        style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 15.sp, color = Palette.Ink),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StateBadge(live)
                }
                Spacer(Modifier.height(3.dp))
                item.pastime?.let { PastimeTag(it.title) }
            }
        }

        BlurbCaption()
        BlurbBox(item.description, if (live) Palette.Sky else MidGrey.fade(0.3f))

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.pastime?.hobby?.let { hobby ->
                Text(
                    text = hobby.name,
                    style = Typo.paragraph(FontWeight.Bold).copy(fontSize = 11.sp, color = Palette.Sky),
                    modifier = Modifier
                        .background(Palette.Sky.fade(0.1f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (live) {
                PillAction(
                    label = "View",
                    fill = Palette.Ink,
                    textStyle = Typo.paragraph(FontWeight.Bold).copy(fontSize = 12.sp),
                    horizontal = 18.dp,
                    vertical = 7.dp,
                    onTap = { StackNavigator.go(Destination.HangoutDetail(item.id)) },
                )
            } else {
                Text(
                    text = "Table ended",
                    style = Typo.paragraph().copy(fontSize = 11.sp, color = Palette.Ink.fade(0.4f)),
                )
            }
        }
    }
}

@Composable
private fun StateBadge(live: Boolean) {
    Text(
        text = if (live) "ACTIVE" else "ENDED",
        style = Typo.label(FontWeight.ExtraBold).copy(
            fontSize = 9.sp,
            color = if (live) Palette.Teal else MidGrey,
        ),
        modifier = Modifier
            .background(if (live) Palette.Teal.fade(0.1f) else MidGrey.fade(0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

package co.gapfinder.mobile.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.local.LocalStore
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Lecture
import co.gapfinder.mobile.domain.Weekday
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.Connectivity
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.foundation.rememberOnline
import co.gapfinder.mobile.ui.kit.OfflineNote
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException

private enum class SlotKind { Lecture, Break }

private data class AgendaSlot(
    val kind: SlotKind,
    val title: String,
    val from: String,
    val to: String,
    /** Minutes since midnight, used for sorting. */
    val sortKey: Int,
)

/** Full user timetable plus the GAPs calculated by the backend for the week. */
private data class Timetable(val lectures: List<Lecture>, val windows: List<FreeWindow>)

/** Schedule tab for the current day (formerly ScheduleScreen). */
@Composable
fun TimetableTab(isActive: Boolean) {
    var cycle by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<Fetch<Timetable>>(Fetch.Waiting) }
    // true while what is displayed is the Room copy and not the backend response
    var showingSaved by remember { mutableStateOf(false) }
    val online by rememberOnline()

    // Also runs when the network changes: shows saved data offline and auto-refreshes on reconnect
    LaunchedEffect(cycle, online) {
        // Room data is loaded first, so the tab is not empty while the network responds (or if offline)
        val saved = savedTimetable()
        outcome = if (saved != null) Fetch.Done(saved) else Fetch.Waiting
        showingSaved = saved != null

        if (!Connectivity.isOnline()) {
            if (saved == null) outcome = Fetch.Broken(OfflineFault("No connection. Your schedule shows up here when you're back online."))
            return@LaunchedEffect
        }

        try {
            // On first load, use what HomeWarmup already fetched in parallel; retry if it failed
            val warm = SessionVault.memberId()?.let { HomeWarmup.takeTimetable(it) }?.getOrNull()
            outcome = Fetch.Done(warm?.let { Timetable(it.lectures, it.windows) } ?: fetchTimetable())
            showingSaved = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // If the network fails the saved copy is kept; the error is only shown if there is nothing
            if (saved == null) outcome = Fetch.Broken(e)
        }
    }

    // When the tab becomes active again it reloads without clearing what is already shown
    OnTabWake(isActive) { cycle++ }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
            .statusBarsPadding()
    ) {
        // Dark header
        Text(
            text = "GAP FINDER",
            style = Typo.heading(FontWeight.Black).copy(
                fontSize = 20.sp,
                color = Palette.White,
                letterSpacing = 1.5.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(Palette.Ink)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val state = outcome) {
                Fetch.Waiting -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(36.dp))

                is Fetch.Broken -> if (state.cause is OfflineFault) {
                    OfflineNote(
                        text = state.cause.message ?: "",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    Text(
                        text = "Could not load your schedule.\n${state.cause.dartStyleText()}",
                        textAlign = TextAlign.Center,
                        style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.6f)),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }

                is Fetch.Done -> if (state.value.lectures.isEmpty() && state.value.windows.isEmpty()) {
                    Text(
                        text = "You have no imported classes yet.",
                        style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.5f)),
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        if (showingSaved && !online) {
                            OfflineNote("No connection · showing your saved schedule")
                            Spacer(Modifier.height(12.dp))
                        }
                        TodayAgenda(state.value)
                    }
                }
            }
        }
    }
}

/** Fetches lectures + GAPs in parallel (recalculating the week's GAPs) and saves them in Room. */
private suspend fun fetchTimetable(): Timetable {
    val me = SessionVault.memberId() ?: throw Exception("No logged-in user found")
    val fresh = HomeWarmup.loadTimetable(me)
    return Timetable(fresh.lectures, fresh.windows)
}

/** Timetable saved in Room (null if there is no session or it was never saved). */
private suspend fun savedTimetable(): Timetable? {
    val me = SessionVault.memberId() ?: return null
    return LocalStore.timetableOf(me)?.let { Timetable(it.lectures, it.windows) }
}

/** Today's lectures and today's GAPs merged into a single list sorted by start time. */
private fun weaveDay(table: Timetable): List<AgendaSlot> {
    val now = Chrono.now()
    val today = Weekday.entries[now.dayOfWeek.value - 1]

    val lectureSlots = table.lectures
        .filter { it.day == today }
        .map { lecture ->
            AgendaSlot(
                kind = SlotKind.Lecture,
                title = lecture.subject,
                from = twelveHourClock(lecture.startsAt.hour, lecture.startsAt.minute),
                to = twelveHourClock(lecture.endsAt.hour, lecture.endsAt.minute),
                sortKey = lecture.startsAt.hour * 60 + lecture.startsAt.minute,
            )
        }

    // GAPs come with a date: from the full week only today's are shown
    val breakSlots = table.windows
        .filter { it.startsAt.toLocalDate() == now.toLocalDate() }
        .map { window ->
            AgendaSlot(
                kind = SlotKind.Break,
                title = "GAP",
                from = twelveHourClock(window.startsAt.hour, window.startsAt.minute),
                to = twelveHourClock(window.endsAt.hour, window.endsAt.minute),
                sortKey = window.startsAt.hour * 60 + window.startsAt.minute,
            )
        }

    return (lectureSlots + breakSlots).sortedBy { it.sortKey }
}

@Composable
private fun TodayAgenda(table: Timetable) {
    val slots = weaveDay(table)

    if (slots.isEmpty()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "No tienes clases hoy.",
                style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.5f)),
                modifier = Modifier.padding(top = 40.dp),
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        slots.forEach { slot ->
            Box(Modifier.padding(bottom = 8.dp)) {
                if (slot.kind == SlotKind.Lecture) LectureCard(slot) else BreakCard(slot)
            }
        }
    }
}

@Composable
private fun LectureCard(slot: AgendaSlot) {
    SlotCard(
        slot = slot,
        fill = Palette.White,
        edge = Palette.Ink.fade(0.10f),
        bar = Palette.Sky,
        titleStyle = Typo.heading(FontWeight.Bold).copy(fontSize = 14.sp, color = Palette.Ink),
        timeTint = Palette.Ink.fade(0.5f),
    )
}

/** GAP card: same block as a lecture but in coral. Informational only, not tappable. */
@Composable
private fun BreakCard(slot: AgendaSlot) {
    SlotCard(
        slot = slot,
        fill = Palette.Coral.fade(0.07f),
        edge = Palette.Coral.fade(0.40f),
        bar = Palette.Coral,
        titleStyle = Typo.heading(FontWeight.ExtraBold).copy(
            fontSize = 14.sp,
            color = Palette.Coral,
            letterSpacing = 1.sp,
        ),
        timeTint = Palette.Coral.fade(0.7f),
    )
}

@Composable
private fun SlotCard(
    slot: AgendaSlot,
    fill: Color,
    edge: Color,
    bar: Color,
    titleStyle: TextStyle,
    timeTint: Color,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fill, shape)
            .border(1.5.dp, edge, shape)
            // padding from Flutter design + border width (Container adds them together)
            .padding(horizontal = 17.5.dp, vertical = 15.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(36.dp)
                .background(bar, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = slot.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = titleStyle,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${slot.from} – ${slot.to}",
                style = Typo.paragraph(FontWeight.Normal).copy(fontSize = 12.sp, color = timeTint),
            )
        }
    }
}

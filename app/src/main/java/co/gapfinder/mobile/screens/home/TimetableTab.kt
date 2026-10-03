package co.gapfinder.mobile.screens.home

import android.util.Log
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
import co.gapfinder.mobile.data.LectureRepository
import co.gapfinder.mobile.data.WindowRepository
import co.gapfinder.mobile.domain.FreeWindow
import co.gapfinder.mobile.domain.Lecture
import co.gapfinder.mobile.domain.Weekday
import co.gapfinder.mobile.foundation.Chrono
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val LOG_TAG = "TimetableTab"

private enum class SlotKind { Lecture, Break }

private data class AgendaSlot(
    val kind: SlotKind,
    val title: String,
    val from: String,
    val to: String,
    /** Minutos desde medianoche, para ordenar. */
    val sortKey: Int,
)

/** Horario completo del usuario más los GAPs que calculó el backend para la semana. */
private data class Timetable(val lectures: List<Lecture>, val windows: List<FreeWindow>)

/** Pestaña de horario del día (antes ScheduleScreen). */
@Composable
fun TimetableTab(isActive: Boolean) {
    var cycle by remember { mutableIntStateOf(0) }
    var outcome by remember { mutableStateOf<Fetch<Timetable>>(Fetch.Waiting) }

    LaunchedEffect(cycle) {
        outcome = Fetch.Waiting
        outcome = try {
            Fetch.Done(fetchTimetable())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Fetch.Broken(e)
        }
    }

    OnTabWake(isActive) {
        outcome = Fetch.Waiting
        cycle++
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
            .statusBarsPadding()
    ) {
        // Cabecera oscura
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

                is Fetch.Broken -> Text(
                    text = "No se pudo cargar tu horario.\n${state.cause.dartStyleText()}",
                    textAlign = TextAlign.Center,
                    style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.6f)),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                is Fetch.Done -> if (state.value.lectures.isEmpty() && state.value.windows.isEmpty()) {
                    Text(
                        text = "Aún no tienes clases importadas.",
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
                        TodayAgenda(state.value)
                    }
                }
            }
        }
    }
}

/** Recalcula los GAPs de la semana en el backend (sin fallar si no se puede) y trae clases + GAPs. */
private suspend fun fetchTimetable(): Timetable {
    val me = SessionVault.memberId() ?: throw Exception("No hay usuario logueado")
    try {
        WindowRepository.generateWeek(me)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.d(LOG_TAG, "Advertencia: No se pudieron sincronizar los GAPs con el servidor: $e")
    }
    return coroutineScope {
        val lectures = async { LectureRepository.timetableOf(me) }
        val windows = async { WindowRepository.weekOf(me) }
        Timetable(lectures.await(), windows.await())
    }
}

/** Clases de hoy y GAPs de hoy en una sola lista ordenada por hora de inicio. */
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

    // Los GAPs vienen con fecha: de toda la semana solo se muestran los de hoy
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

/** Tarjeta de GAP: mismo bloque que una clase pero en coral. Es informativa, no se puede tocar. */
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
            // padding de Flutter + ancho del borde (Container lo suma)
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

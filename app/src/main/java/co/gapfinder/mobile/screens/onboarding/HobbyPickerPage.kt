package co.gapfinder.mobile.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.gapfinder.mobile.data.HobbyRepository
import co.gapfinder.mobile.data.MemberRepository
import co.gapfinder.mobile.domain.Hobby
import co.gapfinder.mobile.foundation.SessionVault
import co.gapfinder.mobile.ui.kit.InkHeader
import co.gapfinder.mobile.ui.kit.PillButton
import co.gapfinder.mobile.ui.kit.plainTap
import co.gapfinder.mobile.ui.nav.Destination
import co.gapfinder.mobile.ui.nav.StackNavigator
import co.gapfinder.mobile.ui.nav.Toaster
import co.gapfinder.mobile.ui.theme.Palette
import co.gapfinder.mobile.ui.theme.Typo
import co.gapfinder.mobile.ui.theme.fade
import kotlinx.coroutines.launch

/** Estado de la carga del catálogo de intereses. */
private sealed interface CatalogLoad {
    data object Pending : CatalogLoad
    data class Failed(val reason: String) : CatalogLoad
    data class Ready(val items: List<Hobby>) : CatalogLoad
}

/** Texto de error al estilo de Dart (`Exception: ...`). */
private fun Throwable.asDartText(): String = "Exception: ${message ?: ""}"

/** Selección de intereses durante el registro. */
@Composable
fun HobbyPickerPage() {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<CatalogLoad>(CatalogLoad.Pending) }
    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var storing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        catalog = try {
            CatalogLoad.Ready(HobbyRepository.all())
        } catch (e: Exception) {
            CatalogLoad.Failed(e.asDartText())
        }
    }

    fun flip(id: Int) {
        picked = if (id in picked) picked - id else picked + id
    }

    fun commitChoices() {
        if (picked.isEmpty()) {
            Toaster.show("Please select at least one interest")
            return
        }
        storing = true
        scope.launch {
            try {
                val me = SessionVault.memberId() ?: throw Exception("User session not found")
                // Se guarda cada interés uno por uno
                for (hobbyId in picked) {
                    MemberRepository.attachHobby(me, hobbyId)
                }
                StackNavigator.swap(Destination.TimetableSetup)
            } catch (e: Exception) {
                Toaster.show("Error saving interests: ${e.asDartText()}")
            } finally {
                storing = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Fog)
    ) {
        InkHeader(title = "GAP FINDER", subtitle = "Profile Setup")

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val state = catalog) {
                CatalogLoad.Pending -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(36.dp))
                }

                is CatalogLoad.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Error loading interests: ${state.reason}",
                        textAlign = TextAlign.Center,
                        style = Typo.paragraph().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.6f)),
                        modifier = Modifier.padding(32.dp),
                    )
                }

                is CatalogLoad.Ready -> Column(Modifier.fillMaxSize()) {
                    HobbyCloud(
                        items = state.items,
                        picked = picked,
                        onFlip = { flip(it) },
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (storing) {
                            CircularProgressIndicator(Modifier.size(36.dp))
                        } else {
                            PillButton(label = "Continue", onClick = { commitChoices() })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HobbyCloud(
    items: List<Hobby>,
    picked: Set<Int>,
    onFlip: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = "What are you into?",
            style = Typo.heading(FontWeight.ExtraBold).copy(fontSize = 22.sp, color = Palette.Ink),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Select all that apply — we'll use this for matching",
            style = Typo.label().copy(fontSize = 14.sp, color = Palette.Ink.fade(0.55f)),
        )
        Spacer(Modifier.height(28.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { hobby ->
                HobbyChip(
                    name = hobby.name,
                    on = hobby.id in picked,
                    onTap = { onFlip(hobby.id) },
                )
            }
        }
    }
}

@Composable
private fun HobbyChip(name: String, on: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val fill by animateColorAsState(
        targetValue = if (on) Palette.Coral else Palette.White,
        animationSpec = tween(200),
        label = "chipFill",
    )
    val edge by animateColorAsState(
        targetValue = if (on) Palette.Coral else Palette.Ink.fade(0.18f),
        animationSpec = tween(200),
        label = "chipEdge",
    )
    Box(
        modifier = Modifier
            .plainTap(onTap = onTap)
            .background(fill, shape)
            .border(1.5.dp, edge, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = name,
            style = Typo.paragraph(if (on) FontWeight.Bold else FontWeight.Normal).copy(
                fontSize = 13.sp,
                color = if (on) Palette.White else Palette.Ink,
            ),
        )
    }
}

package co.gapfinder.mobile.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import co.gapfinder.mobile.screens.hangouts.HangoutMapTab
import co.gapfinder.mobile.ui.kit.DockBar
import co.gapfinder.mobile.ui.kit.DockTab
import co.gapfinder.mobile.ui.theme.Palette

/** Compone la pestaña siempre (como IndexedStack) pero solo la coloca si está seleccionada. */
private fun Modifier.onlyWhen(visible: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (visible) placeable.place(0, 0) }
}

/** Contenedor principal con las 5 pestañas y la barra inferior (antes MainScreen). */
@Composable
fun HomeShell() {
    var selected by rememberSaveable { mutableStateOf(DockTab.Schedule) }

    Column(Modifier.fillMaxSize().background(Palette.Fog)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Schedule)) {
                TimetableTab(isActive = selected == DockTab.Schedule)
            }
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Friends)) {
                FriendsTab(isActive = selected == DockTab.Friends)
            }
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Match)) {
                PairingTab(isActive = selected == DockTab.Match)
            }
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Map)) {
                HangoutMapTab()
            }
            Box(Modifier.fillMaxSize().onlyWhen(selected == DockTab.Suggest)) {
                RecommendationsTab(isActive = selected == DockTab.Suggest)
            }
        }
        DockBar(active = selected, onSelect = { selected = it })
    }
}

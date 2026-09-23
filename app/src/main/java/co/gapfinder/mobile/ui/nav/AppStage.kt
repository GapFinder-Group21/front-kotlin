package co.gapfinder.mobile.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import co.gapfinder.mobile.screens.hangouts.HangoutComposerPage
import co.gapfinder.mobile.screens.hangouts.HangoutDetailPage
import co.gapfinder.mobile.screens.hangouts.MyHangoutsPage
import co.gapfinder.mobile.screens.home.HomeShell
import co.gapfinder.mobile.screens.home.TimetableTab
import co.gapfinder.mobile.screens.onboarding.CalendarImportPage
import co.gapfinder.mobile.screens.onboarding.CalendarLinkPage
import co.gapfinder.mobile.screens.onboarding.HobbyPickerPage
import co.gapfinder.mobile.screens.onboarding.LandingPage
import co.gapfinder.mobile.screens.onboarding.LocationGatePage
import co.gapfinder.mobile.screens.onboarding.SignInPage
import co.gapfinder.mobile.screens.onboarding.SignUpPage
import co.gapfinder.mobile.screens.onboarding.TimetableSetupPage
import co.gapfinder.mobile.screens.pairing.ConversationPage
import co.gapfinder.mobile.screens.pairing.PairingCandidatePage
import co.gapfinder.mobile.screens.pairing.PairingConfirmedPage
import co.gapfinder.mobile.screens.pairing.PairingInvitePage
import co.gapfinder.mobile.screens.pairing.PairingPendingPage
import co.gapfinder.mobile.screens.pairing.PairingSearchPage

/**
 * Mantiene compuesta cada pantalla de la pila pero solo coloca (dibuja y recibe toques)
 * la de arriba, igual que las rutas apiladas de Flutter.
 */
private fun Modifier.keepAlive(visible: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (visible) placeable.place(0, 0)
    }
}

@Composable
fun AppStage() {
    StackNavigator.start(Destination.Landing)
    val stack = StackNavigator.entries

    BackHandler(enabled = StackNavigator.canGoBack) { StackNavigator.back() }

    Box(Modifier.fillMaxSize()) {
        stack.forEachIndexed { index, entry ->
            key(entry.key) {
                Box(Modifier.fillMaxSize().keepAlive(index == stack.lastIndex)) {
                    Render(entry.destination)
                }
            }
        }
        ToastHost()
    }
}

@Composable
private fun Render(destination: Destination) {
    when (destination) {
        Destination.Landing -> LandingPage()
        Destination.SignUp -> SignUpPage()
        Destination.SignIn -> SignInPage()
        is Destination.LocationGate -> LocationGatePage(fromOnboarding = destination.fromOnboarding)
        Destination.Home -> HomeShell()
        Destination.HobbyPicker -> HobbyPickerPage()
        Destination.Timetable -> TimetableTab(isActive = true)
        Destination.TimetableSetup -> TimetableSetupPage(
            onNavigate = { route -> if (route == "manualSchedule") StackNavigator.swap(Destination.Home) },
        )
        Destination.CalendarImport -> CalendarImportPage()
        Destination.CalendarLink -> CalendarLinkPage()
        is Destination.PairingSearch -> PairingSearchPage(destination.memberId, destination.focus)
        is Destination.PairingCandidate -> PairingCandidatePage(
            memberId = destination.memberId,
            candidate = destination.candidate,
            candidateWindow = destination.candidateWindow,
            affinity = destination.affinity,
            sharedHobbies = destination.sharedHobbies,
        )
        is Destination.PairingPending -> PairingPendingPage(destination.pairingId, destination.requesterId, destination.candidate)
        is Destination.PairingConfirmed -> PairingConfirmedPage(
            pairingId = destination.pairingId,
            candidate = destination.candidate,
            suggested = destination.suggested,
            chosen = destination.chosen,
        )
        is Destination.PairingInvite -> PairingInvitePage(destination.pairingId)
        is Destination.Conversation -> ConversationPage(destination.pairingId, destination.hangoutId)
        Destination.HangoutComposer -> HangoutComposerPage()
        Destination.MyHangouts -> MyHangoutsPage()
        is Destination.HangoutDetail -> HangoutDetailPage(destination.hangoutId)
        is Destination.Unmapped -> Unit
    }
}

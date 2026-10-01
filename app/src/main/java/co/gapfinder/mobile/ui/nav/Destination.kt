package co.gapfinder.mobile.ui.nav

import co.gapfinder.mobile.domain.Member
import co.gapfinder.mobile.domain.PairingFocus
import co.gapfinder.mobile.domain.PairingProspect
import co.gapfinder.mobile.domain.Pastime

/**
 * Todas las pantallas a las que se puede navegar, con sus argumentos tipados.
 * (Reemplaza las rutas con nombre de MaterialApp: '/welcome', '/login', etc.)
 */
sealed interface Destination {
    /** '/welcome' */
    data object Landing : Destination
    /** '/register' */
    data object SignUp : Destination
    /** '/login' */
    data object SignIn : Destination
    /** '/location-permission' */
    data class LocationGate(val fromOnboarding: Boolean = false) : Destination
    /** '/home' — contenedor con la barra inferior. */
    data object Home : Destination
    /** '/interests' */
    data object HobbyPicker : Destination
    /** '/schedule' */
    data object Timetable : Destination
    /** '/schedule-setup' */
    data object TimetableSetup : Destination
    /** '/google-calendar' */
    data object CalendarImport : Destination
    /** '/google-connect' */
    data object CalendarLink : Destination

    /** '/searching-match' */
    data class PairingSearch(val memberId: Int, val focus: PairingFocus) : Destination

    /** '/match-found' — [currentWindowId] es el hueco activo de quien busca. */
    data class PairingCandidate(
        val memberId: Int,
        val currentWindowId: Int,
        val prospect: PairingProspect,
    ) : Destination

    /** '/waiting-match' */
    data class PairingPending(val pairingId: Int, val requesterId: Int, val candidate: Member) : Destination

    /** '/its-a-match' */
    data class PairingConfirmed(
        val pairingId: Int,
        val candidate: Member,
        val suggested: Pastime? = null,
        val chosen: Pastime? = null,
    ) : Destination

    /** '/match-invitation' */
    data class PairingInvite(val pairingId: Int) : Destination

    /** '/create-open-table' */
    data object HangoutComposer : Destination
    /** '/my-open-tables' */
    data object MyHangouts : Destination
    /** '/open-table-detail' */
    data class HangoutDetail(val hangoutId: Int) : Destination

    /** '/gap-detail' */
    data class WindowDetail(val windowId: Int) : Destination

    /**
     * Ruta que no está registrada. Se ignora de forma segura en vez de romper la app
     * (en Flutter un pushNamed a una ruta inexistente lanza una excepción).
     */
    data class Unmapped(val path: String) : Destination
}

package co.gapfinder.mobile.domain

/** Nivel de energía de una actividad o preferencia (QUIET / NORMAL / ACTIVE). */
enum class EnergyLevel(val wire: String) {
    Calm("QUIET"), Regular("NORMAL"), Lively("ACTIVE");

    companion object {
        fun parse(raw: String?): EnergyLevel =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: Regular
    }
}

enum class Weekday(val wire: String) {
    Monday("MON"), Tuesday("TUE"), Wednesday("WED"), Thursday("THU"),
    Friday("FRI"), Saturday("SAT"), Sunday("SUN");

    companion object {
        fun parse(raw: String?): Weekday =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: Monday
    }
}

enum class BondState(val wire: String) {
    Waiting("PENDING"), Confirmed("ACCEPTED"), Declined("REJECTED");

    companion object {
        fun parse(raw: String?): BondState =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: Waiting
    }
}

/** Estados de MatchStatusEnum en el backend. Live = aceptado; si sigue vigente lo decide overlapEnd. */
enum class PairingState(val wire: String) {
    AwaitingReply("PENDING"), Live("ACCEPTED"), Turned("REJECTED"), Finished("CANCELLED");

    companion object {
        fun parse(raw: String?): PairingState =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: AwaitingReply
    }
}

/** Criterio extra con el que se priorizan los candidatos de match. */
enum class PairingFocus(val wire: String, val caption: String) {
    Program("SAME_PROGRAM", "Same Program"),
    Semester("SAME_SEMESTER", "Same Semester"),
    Energy("SAME_EFFORT", "Same Effort");

    companion object {
        fun parse(raw: String): PairingFocus = entries.first { it.wire == raw }
    }
}

/** Tipos de NotificationTypeEnum en el backend; cualquier otro valor cae en Platform. */
enum class AlertKind(val wire: String) {
    PairingAsked("MATCH_REQUEST"),
    PairingAccepted("MATCH_ACCEPTED"),
    PairingDeclined("MATCH_REJECTED"),
    BondAsked("FRIEND_REQUEST"),
    BondAccepted("FRIEND_ACCEPTED"),
    HangoutInvite("OPEN_TABLE_INVITE"),
    HangoutJoined("OPEN_TABLE_JOIN"),
    FriendHosting("FRIEND_OPEN_TABLE_CREATED"),
    FriendFree("GAP_STARTING_FRIEND"),
    CrewFree("GROUP_GAP_AVAILABLE"),
    WindowClosingSoon("GAP_ENDING_SOON"),
    WindowClosed("GAP_ENDED"),
    Platform("SYSTEM");

    companion object {
        fun parse(raw: String?): AlertKind =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: Platform
    }
}

enum class HangoutState(val wire: String) {
    Running("ACTIVE"), Over("ENDED");

    companion object {
        fun parse(raw: String?): HangoutState =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: Running
    }
}

enum class Rsvp(val wire: String) {
    Joined("IN"), Left("OUT"), Undecided("PENDING");

    companion object {
        fun parse(raw: String?): Rsvp =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: Undecided
    }
}

/** Pasos del formulario de creación de una Open Table (ACTIVITY, DESCRIPTION, LOCATION). */
enum class DraftStage(val wire: String) {
    PickPastime("ACTIVITY"), WriteBlurb("DESCRIPTION"), PickSpot("LOCATION");

    companion object {
        fun parse(raw: String): DraftStage = entries.first { it.wire == raw }
    }
}

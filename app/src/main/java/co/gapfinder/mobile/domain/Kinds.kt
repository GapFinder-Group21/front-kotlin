package co.gapfinder.mobile.domain

/** Nivel de esfuerzo de una actividad o preferencia (EffortTypeEnum: LOW / MEDIUM / HIGH). */
enum class EnergyLevel(val wire: String) {
    Calm("LOW"), Regular("MEDIUM"), Lively("HIGH");

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

/** Estados de MatchStatusEnum en el backend. Live = aceptado y todavía sin completar. */
enum class PairingState(val wire: String) {
    AwaitingReply("PENDING"), Live("ACCEPTED"), Turned("REJECTED"), Finished("COMPLETED");

    companion object {
        fun parse(raw: String?): PairingState =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: AwaitingReply
    }
}

/** Criterio con el que se priorizan los candidatos de match (antes MatchModeEnum). */
enum class PairingFocus(val wire: String, val caption: String) {
    Career("CAREER", "Career"),
    Interests("INTERESTS", "Interests"),
    Energy("EFFORT", "Effort");

    companion object {
        fun parse(raw: String?): PairingFocus =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: Career
    }
}

/** Tipos de NotificationTypeEnum en el backend; cualquier otro valor cae en BondAsked. */
enum class AlertKind(val wire: String) {
    BondAsked("FRIEND_REQUEST_SENT"),
    BondAccepted("FRIEND_REQUEST_ACCEPTED"),
    BondDeclined("FRIEND_REQUEST_REJECTED"),
    PairingAsked("MATCH_PROPOSED"),
    PairingAccepted("MATCH_ACCEPTED"),
    PairingDeclined("MATCH_REJECTED"),
    HangoutJoined("OPEN_TABLE_JOINED");

    companion object {
        fun parse(raw: String?): AlertKind =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: BondAsked
    }
}

/** Estados de OpenTableStatusEnum: abierta, llena, terminada o vacía. */
enum class HangoutState(val wire: String) {
    Open("OPEN"), Full("FULL"), Over("COMPLETED"), Vacant("EMPTY");

    companion object {
        fun parse(raw: String?): HangoutState =
            entries.firstOrNull { it.wire.equals(raw, ignoreCase = true) } ?: Open
    }
}

/** Pasos del formulario de creación de una Open Table (ACTIVITY, DESCRIPTION, PARTICIPANTS, LOCATION). */
enum class DraftStage(val wire: String) {
    PickPastime("ACTIVITY"), WriteBlurb("DESCRIPTION"), PickHeadcount("PARTICIPANTS"), PickSpot("LOCATION");

    companion object {
        fun parse(raw: String): DraftStage = entries.first { it.wire == raw }
    }
}

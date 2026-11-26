package ai.naru.app.comma

enum class CommaSpeechStatus { IDLE, LISTENING, RECOGNIZING, FAILED, UNSUPPORTED }

/** Stable UI contract for future Figma styling; contains no Activity or SDK objects. */
data class RestUiState(
    val session: RestSession = RestSession(),
    val cabin: CabinSettings = CabinSettings(),
    val vehicle: VehicleState = VehicleState(),
    val isMock: Boolean = true,
    val history: RestHistoryState = RestHistoryState(),
    val message: String? = null,
    val demoEnabled: Boolean = false,
    val isDemo: Boolean = false,
    val nowMillis: Long = 0,
    val capabilities: VehicleCapabilities = VehicleCapabilities(),
    val vehicleControl: VehicleControlState = VehicleControlState(),
    val vehicleIssue: VehicleIssue? = null,
    val localParkedConfirmed: Boolean = false,
    val demoVehicle: DemoVehicleState? = null,
    val selectedClimateCelsius: Float? = null,
    val climateSelectionIsManual: Boolean = false,
    val pendingIntentProposal: CommaIntentProposal? = null,
    val intentResultMessage: String? = null,
    val intentProcessing: Boolean = false,
    val speechStatus: CommaSpeechStatus = CommaSpeechStatus.IDLE,
    val speechText: String? = null,
    val speechMessage: String? = null,
    val alarmTimingWarning: String? = null,
    val sessionStartBatteryPercent: Int? = null,
    val charging: ChargingSnapshot = ChargingSnapshot(observedAtMillis = 0L),
) {
    val canStartRest get() = if(isDemo) vehicle.parkingStatus==ParkingStatus.PARKED
        else canStartAppRest(vehicle,isMock,localParkedConfirmed)
    val active get() = session.phase == RestPhase.RESTING || session.phase == RestPhase.WAKING
}

data class RestUiActions(
    val selectMinutes: (Int) -> Unit,
    val configure: (CabinSettings) -> Unit,
    val start: () -> Unit,
    val extend: (Int) -> Unit,
    val finish: () -> Unit,
    val requestClimate: () -> Unit,
    val retrySave: () -> Unit,
    val submitFeedback: (String, Int, TemperatureFeedback) -> Unit = { _, _, _ -> },
    val finishResting: () -> Unit = finish,
    val startAlarmOnly: () -> Unit = {},
    val selectClimateTarget: (Float?) -> Unit = {},
    val applySelectedClimate: () -> Unit = {},
    val proposeIntent: (String) -> Unit = {},
    val approveIntent: () -> Unit = {},
    val cancelIntent: () -> Unit = {},
    val startSpeechInput: () -> Unit = {},
    val stopSpeechInput: () -> Unit = {},
)

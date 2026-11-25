package ai.naru.app.comma

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
    val demoVehicle: DemoVehicleState? = null
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
    val finishResting: () -> Unit = finish,
    val startAlarmOnly: () -> Unit = {}
)

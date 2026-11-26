package ai.naru.app.comma

enum class ParkingStatus { PARKED, NOT_PARKED, UNKNOWN }
enum class DrivingStatus { DRIVING, NOT_DRIVING, UNKNOWN }
enum class ControlSupport { SUPPORTED, UNSUPPORTED, UNKNOWN }
data class VehicleCapabilities(
    val climate: ControlSupport = ControlSupport.UNKNOWN,
    val drivingSignal: ControlSupport = ControlSupport.UNKNOWN,
    val lighting: ControlSupport = ControlSupport.UNKNOWN,
    val climateTemperatureRange: ClosedFloatingPointRange<Float>? = null,
)
enum class ControlOutcome { NOT_REQUESTED, PENDING, SUCCESS, FAILURE, UNSUPPORTED, UNKNOWN }
data class VehicleControlState(
    val outcome:ControlOutcome=ControlOutcome.NOT_REQUESTED,
    val message:String="차량 제어 요청 없음"
)
enum class VehicleIssue { FIRST_USE, UNSUPPORTED, CONTROL_FAILURE, CONTROL_UNKNOWN,
    PARKING_UNKNOWN, NOT_PARKED, DRIVING_STOPPED }
enum class VehicleIssueAction { ACKNOWLEDGE, CONFIRM_LOCAL_PARKING, CONTINUE_APP_ONLY,
    CHECK_PARKING, RETRY_CONTROL, RETURN, VIEW_COMPLETION }

/** A local user assertion enables only app-local rest; it never becomes a vehicle signal. */
internal fun canStartAppRest(state:VehicleState,isMock:Boolean,localParkedConfirmed:Boolean):Boolean =
    if(isMock) localParkedConfirmed && state.parkingStatus!=ParkingStatus.NOT_PARKED
    else state.parkingStatus==ParkingStatus.PARKED

internal fun startIssue(
    state:VehicleState,isMock:Boolean,localConfirmed:Boolean,firstUseAcknowledged:Boolean
):VehicleIssue? = when {
    !firstUseAcknowledged -> VehicleIssue.FIRST_USE
    state.parkingStatus==ParkingStatus.NOT_PARKED -> VehicleIssue.NOT_PARKED
    !canStartAppRest(state,isMock,localConfirmed) -> VehicleIssue.PARKING_UNKNOWN
    else -> null
}

/** Only an adapter's real, explicitly supported driving signal can trigger this path. */
internal fun shouldStopForDriving(
    state:VehicleState,capabilities:VehicleCapabilities,isMock:Boolean,isDemo:Boolean,phase:RestPhase
):Boolean = !isMock && !isDemo && capabilities.drivingSignal==ControlSupport.SUPPORTED &&
    state.driving==DrivingStatus.DRIVING && (phase==RestPhase.RESTING || phase==RestPhase.WAKING)

internal fun dispatchDrivingStop(
    state:VehicleState,capabilities:VehicleCapabilities,isMock:Boolean,isDemo:Boolean,
    currentPhase:()->RestPhase,finish:()->Unit
):Boolean {
    if(!shouldStopForDriving(state,capabilities,isMock,isDemo,currentPhase())) return false
    finish()
    return true
}

internal fun controlState(
    result:ClimateResult,isMock:Boolean,support:ControlSupport
):VehicleControlState {
    if(isMock) return VehicleControlState(ControlOutcome.UNSUPPORTED,
        "Mock 응답 · 실제 차량 제어는 수행되지 않았습니다.")
    if(support==ControlSupport.UNSUPPORTED) return VehicleControlState(ControlOutcome.UNSUPPORTED,"차량 제어 미지원")
    if(support!=ControlSupport.SUPPORTED) return VehicleControlState(ControlOutcome.UNKNOWN,"차량 제어 지원 여부 확인 불가")
    return when(result) {
        is ClimateResult.Applied -> VehicleControlState(ControlOutcome.SUCCESS,"차량 응답 확인: 공조 ${result.targetCelsius}°C 적용")
        is ClimateResult.Failed -> VehicleControlState(ControlOutcome.FAILURE,result.reason)
        is ClimateResult.Unavailable -> VehicleControlState(ControlOutcome.UNSUPPORTED,result.reason)
        is ClimateResult.Unknown -> VehicleControlState(ControlOutcome.UNKNOWN,result.reason)
    }
}

internal fun parkingLabel(status:ParkingStatus)=when(status) {
    ParkingStatus.PARKED -> "주차 확인"
    ParkingStatus.NOT_PARKED -> "주차 상태 아님"
    ParkingStatus.UNKNOWN -> "주차 신호 확인 불가"
}

package ai.naru.app.comma

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VehicleState(
    val indoorCelsius: Float? = null,
    val outdoorCelsius: Float? = null,
    val parked: Boolean? = null,
    val batteryPercent: Int? = null,
    val driving: DrivingStatus = DrivingStatus.UNKNOWN
) {
    val parkingStatus:ParkingStatus get() = when(parked) {
        true -> ParkingStatus.PARKED
        false -> ParkingStatus.NOT_PARKED
        null -> ParkingStatus.UNKNOWN
    }
}

/** Automotive/Pleos adapters must provide real readings and acknowledged command results. */
interface VehicleRepository {
    val state: StateFlow<VehicleState>
    val isMock: Boolean
    val capabilities: VehicleCapabilities get() = VehicleCapabilities()
    suspend fun requestClimate(targetCelsius: Float): ClimateResult
}

sealed interface ClimateResult {
    data class Applied(val targetCelsius: Float) : ClimateResult
    data class Unavailable(val reason: String) : ClimateResult
    data class Failed(val reason: String) : ClimateResult
    data class Unknown(val reason: String) : ClimateResult
}

class MockVehicleRepository : VehicleRepository {
    override val isMock = true
    override val capabilities = VehicleCapabilities(ControlSupport.UNSUPPORTED,ControlSupport.UNSUPPORTED)
    override val state = MutableStateFlow(VehicleState(28f, 32f, null, 75))
    override suspend fun requestClimate(targetCelsius: Float): ClimateResult =
        ClimateResult.Unavailable("차량 SDK 미연결: 실제 공조 제어는 수행되지 않았습니다.")
}

data class ClimateRecommendation(val targetCelsius: Float?, val message: String)

fun recommendClimate(state: VehicleState): ClimateRecommendation {
    if (state.parkingStatus != ParkingStatus.PARKED)
        return ClimateRecommendation(null, parkingLabel(state.parkingStatus)+": 주차 상태를 확인한 후 휴식하세요.")
    if (state.batteryPercent == null || state.batteryPercent < 20)
        return ClimateRecommendation(null, "배터리 부족 또는 정보 없음: 공조 사용 전 확인이 필요합니다.")
    val indoor = state.indoorCelsius ?: return ClimateRecommendation(null, "실내 온도 정보 없음")
    val outdoor = state.outdoorCelsius ?: return ClimateRecommendation(null, "실외 온도 정보 없음")
    return when {
        indoor > 26 || outdoor > 30 -> ClimateRecommendation(24f, "냉방 24°C 권장 · 실내외 고온")
        indoor < 18 || outdoor < 10 -> ClimateRecommendation(22f, "난방 22°C 권장 · 실내외 저온")
        else -> ClimateRecommendation(23f, "23°C의 약한 공조 권장 · 쾌적한 온도")
    }
}

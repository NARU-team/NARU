package ai.naru.app.comma

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VehicleBackup(
    val hvacPower: Boolean? = null,
    val targetCelsius: Float? = null,
    val ambientOn: Boolean? = null,
    val ambientBrightness: Int? = null,
    val ambientColorRgb: Int? = null,
) {
    val hasAnyValue: Boolean
        get() = hvacPower != null || targetCelsius != null || ambientOn != null ||
            ambientBrightness != null || ambientColorRgb != null
}

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

    /** 승인된 0~1 밝기를 지원 차량의 앰비언트 범위로 변환해 적용한다. */
    suspend fun applyLighting(brightness: Float): ClimateResult =
        ClimateResult.Unavailable("차량 조명 제어 미지원")

    /** 프로세스 복구를 위해 제어 전 원본 설정을 DB에 보존한다. */
    fun backupSnapshot(): VehicleBackup? = null

    /** 저장된 백업을 프로세스 재시작 뒤 복구 구현에 다시 주입한다. */
    fun installBackup(backup: VehicleBackup) = Unit

    /** 백업값을 복구하고 가능한 항목은 실제 값을 재조회해 결과를 반환한다. */
    suspend fun restoreAndVerify(): RestorationReport = RestorationReport(
        vehicle = if (isMock) RestorationOutcome.UNSUPPORTED else RestorationOutcome.UNKNOWN,
    )

    /** 화면 수명 종료처럼 결과를 기다릴 수 없는 경로의 비동기 복구 요청. */
    fun restoreClimate() = Unit

    /** 실내외 온도와 capability를 다시 읽는다. */
    fun refresh() = Unit
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

fun recommendClimate(
    state: VehicleState,
    records: List<RestRecord> = emptyList(),
): ClimateRecommendation {
    if (state.parkingStatus != ParkingStatus.PARKED)
        return ClimateRecommendation(null, parkingLabel(state.parkingStatus)+": 주차 상태를 확인한 후 휴식하세요.")
    if (state.batteryPercent == null || state.batteryPercent < 20)
        return ClimateRecommendation(null, "배터리 부족 또는 정보 없음: 공조 사용 전 확인이 필요합니다.")
    val indoor = state.indoorCelsius ?: return ClimateRecommendation(null, "실내 온도 정보 없음")
    val outdoor = state.outdoorCelsius ?: return ClimateRecommendation(null, "실외 온도 정보 없음")
    personalizedClimateTarget(records)?.let { target ->
        return ClimateRecommendation(target, "이전 만족 기록을 반영한 ${target}°C 공조 권장")
    }
    return when {
        indoor > 26 || outdoor > 30 -> ClimateRecommendation(24f, "냉방 24°C 권장 · 실내외 고온")
        indoor < 18 || outdoor < 10 -> ClimateRecommendation(22f, "난방 22°C 권장 · 실내외 저온")
        else -> ClimateRecommendation(23f, "23°C의 약한 공조 권장 · 쾌적한 온도")
    }
}

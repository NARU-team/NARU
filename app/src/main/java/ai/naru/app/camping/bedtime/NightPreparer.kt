package ai.naru.app.camping.bedtime

import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.vehicle.AmbientLight
import ai.naru.app.vehicle.FailureReason
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult
import java.time.LocalTime

/** 기상 시각을 정한 방식. */
enum class WakeBasis { MANUAL, SUNRISE }

/** 사용자가 취침 준비 단계에서 정한 오늘 밤 설정. */
data class BedtimeSettings(
    val targetTempC: Float,
    val wakeAt: LocalTime,
    val wakeBasis: WakeBasis,
    val theme: PlaceType,
)

/**
 * 세션 시작 전 원래 차량 설정 (설계 문서 VEHICLE_BACKUP).
 * 읽지 못했거나 미지원이면 null이고, 복구할 때 그 항목은 건너뛴다.
 */
data class VehicleBackup(
    val targetTempC: Float?,
    val ambient: AmbientLight?,
)

/** 차량 제어 결과 (설정 온도, 조명 등 공통). */
sealed interface ControlResult {
    /** 적용하고 다시 조회해서 반영을 확인함 */
    data object Applied : ControlResult

    /** 요청은 전달됐지만 다시 조회한 값이 다름 (예: Virtual Console 비활성) */
    data object Unverified : ControlResult

    /** 차량이 이 제어를 지원하지 않음 */
    data object NotSupported : ControlResult

    /** 제어 요청 실패 */
    data class Failed(val reason: FailureReason) : ControlResult
}

data class NightPreparation(
    val backup: VehicleBackup,
    val temperature: ControlResult,
)

/**
 * "차박 시작하기"를 누른 뒤의 차량 준비 (설계 문서 데이터 흐름의 취침 준비 구간).
 *
 * 1. 지원 여부 확인 → 2. 원래 설정 백업 → 3. 설정 온도 적용 → 4. 다시 조회해서 확인
 * 실패해도 자동으로 다시 시도하지 않는다 (기획서: 제어 실패 시 자동 반복 제한, 직접 조작 안내).
 */
class NightPreparer(private val vehicle: VehicleGateway) {

    suspend fun prepare(settings: BedtimeSettings): NightPreparation {
        val capabilities = vehicle.capabilities()

        val backup = VehicleBackup(
            targetTempC = vehicle.getTargetTemperature().valueOrNull(),
            ambient = if (capabilities.ambientBrightnessRange != null) vehicle.getAmbientLight().valueOrNull() else null,
        )

        return NightPreparation(backup, applyTemperature(settings.targetTempC, capabilities.targetTemperatureRange != null))
    }

    /**
     * 설정 온도 적용: 지원 확인 → 적용 → 다시 조회해서 확인. 밤 단계의 발화 해석 승인에도 쓴다 (이슈 #27).
     */
    suspend fun applyTemperature(celsius: Float, supported: Boolean? = null): ControlResult {
        val canControl = supported ?: (vehicle.capabilities().targetTemperatureRange != null)
        if (!canControl) return ControlResult.NotSupported
        return when (val result = vehicle.setTargetTemperature(celsius)) {
            is VehicleResult.Failure -> ControlResult.Failed(result.reason)
            is VehicleResult.Success -> {
                val actual = vehicle.getTargetTemperature().valueOrNull()
                if (actual == celsius) ControlResult.Applied else ControlResult.Unverified
            }
        }
    }

    private fun <T> VehicleResult<T>.valueOrNull(): T? = (this as? VehicleResult.Success)?.value
}

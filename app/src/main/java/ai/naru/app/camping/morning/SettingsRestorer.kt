package ai.naru.app.camping.morning

import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.camping.bedtime.VehicleBackup
import ai.naru.app.vehicle.AmbientLight
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult

/**
 * 설정 복구 결과. 항목이 null이면 백업이 없어(읽지 못했거나 미지원) 건너뛴 것이다.
 */
data class RestoreReport(
    val temperature: ControlResult?,
    val ambient: ControlResult?,
) {
    /** 직접 확인이 필요한 항목이 있는지 (반영을 확인하지 못했거나 실패) */
    val needsManualCheck: Boolean
        get() = listOfNotNull(temperature, ambient).any { it != ControlResult.Applied }
}

/**
 * 취침 준비 때 백업한 원래 차량 설정을 되돌린다 (설계 문서 데이터 흐름의 기상 구간).
 *
 * 항목마다 지원 여부 확인 → 적용 → 다시 조회해서 확인.
 * 한 항목이 실패해도 나머지는 계속 진행하고, 자동으로 다시 시도하지 않는다.
 */
class SettingsRestorer(private val vehicle: VehicleGateway) {

    suspend fun restore(backup: VehicleBackup?): RestoreReport {
        if (backup == null) return RestoreReport(temperature = null, ambient = null)
        val capabilities = vehicle.capabilities()

        val temperature = backup.targetTempC?.let { original ->
            if (capabilities.targetTemperatureRange == null) {
                ControlResult.NotSupported
            } else {
                when (val result = vehicle.setTargetTemperature(original)) {
                    is VehicleResult.Failure -> ControlResult.Failed(result.reason)
                    is VehicleResult.Success -> {
                        val actual = (vehicle.getTargetTemperature() as? VehicleResult.Success)?.value
                        if (actual == original) ControlResult.Applied else ControlResult.Unverified
                    }
                }
            }
        }

        val ambient = backup.ambient?.let { original ->
            if (capabilities.ambientBrightnessRange == null) {
                ControlResult.NotSupported
            } else {
                when (val result = vehicle.setAmbientLight(original)) {
                    is VehicleResult.Failure -> ControlResult.Failed(result.reason)
                    is VehicleResult.Success -> {
                        val actual = (vehicle.getAmbientLight() as? VehicleResult.Success)?.value
                        if (actual != null && matches(actual, original)) ControlResult.Applied else ControlResult.Unverified
                    }
                }
            }
        }

        return RestoreReport(temperature, ambient)
    }

    /** 꺼진 조명은 켜짐 여부만 본다. 차량은 끌 때 밝기·색을 바꾸지 않는다. */
    private fun matches(actual: AmbientLight, original: AmbientLight): Boolean =
        if (!original.on) !actual.on else actual.on && actual.brightness == original.brightness
}

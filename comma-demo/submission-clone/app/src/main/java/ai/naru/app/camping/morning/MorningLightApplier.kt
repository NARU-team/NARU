package ai.naru.app.camping.morning

import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult

/**
 * 기상 조명 점등. 밤 테마 조명을 밝은 햇살색으로 바꾼다.
 * 지원 여부 확인 → 적용 → 다시 조회해서 확인. 실패해도 다시 시도하지 않는다.
 * 원래 조명은 [SettingsRestorer]가 사용자가 일어난 뒤 되돌린다.
 */
class MorningLightApplier(private val vehicle: VehicleGateway) {

    suspend fun apply(): ControlResult {
        if (vehicle.capabilities().ambientBrightnessRange == null) return ControlResult.NotSupported

        val target = MorningRules.morningLight
        return when (val result = vehicle.setAmbientLight(target)) {
            is VehicleResult.Failure -> ControlResult.Failed(result.reason)
            is VehicleResult.Success -> {
                val actual = (vehicle.getAmbientLight() as? VehicleResult.Success)?.value
                if (actual?.on == true && actual.brightness == target.brightness) ControlResult.Applied else ControlResult.Unverified
            }
        }
    }
}

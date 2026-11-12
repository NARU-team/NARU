package ai.naru.app.camping.night

import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult

/**
 * 밤 테마 조명 적용. 원래 조명은 취침 준비 단계에서 이미 백업했다.
 * 지원 여부 확인 → 적용 → 다시 조회해서 확인. 실패해도 다시 시도하지 않는다.
 */
class ThemeLightApplier(private val vehicle: VehicleGateway) {

    suspend fun apply(theme: PlaceType): ControlResult {
        if (vehicle.capabilities().ambientBrightnessRange == null) return ControlResult.NotSupported

        val target = NightThemes.ambientFor(theme)
        return when (val result = vehicle.setAmbientLight(target)) {
            is VehicleResult.Failure -> ControlResult.Failed(result.reason)
            is VehicleResult.Success -> {
                val actual = (vehicle.getAmbientLight() as? VehicleResult.Success)?.value
                if (actual?.on == true && actual.brightness == target.brightness) ControlResult.Applied else ControlResult.Unverified
            }
        }
    }
}

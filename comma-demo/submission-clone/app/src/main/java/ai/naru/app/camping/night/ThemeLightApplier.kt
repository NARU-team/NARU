package ai.naru.app.camping.night

import ai.naru.app.camping.arrival.PlaceType
import ai.naru.app.camping.bedtime.ControlResult
import ai.naru.app.vehicle.AmbientLight
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

    /** 테마 끄기: 취침 준비 때 백업한 원래 조명으로 되돌린다. 백업이 없으면 건드리지 않는다 */
    suspend fun turnOff(original: AmbientLight?): ControlResult {
        if (vehicle.capabilities().ambientBrightnessRange == null) return ControlResult.NotSupported
        if (original == null) return ControlResult.Unverified
        return when (val result = vehicle.setAmbientLight(original)) {
            is VehicleResult.Failure -> ControlResult.Failed(result.reason)
            is VehicleResult.Success -> {
                val actual = (vehicle.getAmbientLight() as? VehicleResult.Success)?.value
                if (actual?.on == original.on && (!original.on || actual?.brightness == original.brightness)) ControlResult.Applied else ControlResult.Unverified
            }
        }
    }
}

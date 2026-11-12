package ai.naru.app.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 메모리 안의 값으로 동작하는 가짜 차량.
 *
 * 에뮬레이터 없이 화면과 세션 흐름을 개발하고 테스트할 때 쓴다.
 * 기어와 배터리는 [setGear], [setBatteryPercent]로 바꿔서 상황을 재현한다.
 */
class FakeVehicleGateway(
    initialGear: Gear = Gear.PARK,
    initialBatteryPercent: Int? = 80,
    private var targetTemperature: Float = 22.0f,
    private var cabinTemperature: Float = 19.0f,
    private var ambientLight: AmbientLight = AmbientLight(on = false, brightness = 100, colorRgb = 0xFFFFFF),
    private val capabilities: VehicleCapabilities = VehicleCapabilities(
        targetTemperatureRange = 15.0f..30.0f,
        ambientBrightnessRange = 0..100,
        ambientColorSupported = true,
    ),
) : VehicleGateway {

    private val gearState = MutableStateFlow(initialGear)
    private val batteryState = MutableStateFlow(initialBatteryPercent)

    override val gear: Flow<Gear> = gearState
    override val batteryPercent: Flow<Int?> = batteryState

    /** 다음 제어 호출을 실패시키고 싶을 때 지정한다. 한 번 쓰이면 비워진다. */
    var nextFailure: FailureReason? = null

    fun setGear(gear: Gear) {
        gearState.value = gear
    }

    fun setBatteryPercent(percent: Int?) {
        batteryState.value = percent
    }

    override suspend fun capabilities(): VehicleCapabilities = capabilities

    override suspend fun getTargetTemperature(): VehicleResult<Float> =
        VehicleResult.Success(targetTemperature)

    override suspend fun getCabinTemperature(): VehicleResult<Float> =
        VehicleResult.Success(cabinTemperature)

    override suspend fun setTargetTemperature(celsius: Float): VehicleResult<Unit> {
        consumeFailure()?.let { return it }
        val range = capabilities.targetTemperatureRange
            ?: return VehicleResult.Failure(FailureReason.NOT_SUPPORTED)
        if (celsius !in range) {
            return VehicleResult.Failure(FailureReason.NOT_SUPPORTED, "범위 밖 온도: $celsius")
        }
        targetTemperature = celsius
        return VehicleResult.Success(Unit)
    }

    override suspend fun getAmbientLight(): VehicleResult<AmbientLight> =
        VehicleResult.Success(ambientLight)

    override suspend fun setAmbientLight(light: AmbientLight): VehicleResult<Unit> {
        consumeFailure()?.let { return it }
        val range = capabilities.ambientBrightnessRange
            ?: return VehicleResult.Failure(FailureReason.NOT_SUPPORTED)
        if (light.brightness !in range) {
            return VehicleResult.Failure(FailureReason.NOT_SUPPORTED, "범위 밖 밝기: ${light.brightness}")
        }
        ambientLight = light
        return VehicleResult.Success(Unit)
    }

    private fun consumeFailure(): VehicleResult.Failure? {
        val reason = nextFailure ?: return null
        nextFailure = null
        return VehicleResult.Failure(reason, "Fake 실패")
    }
}

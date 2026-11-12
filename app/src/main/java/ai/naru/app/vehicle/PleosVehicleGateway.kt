package ai.naru.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Pleos Connect 차량(에뮬레이터 포함)과 연결된 [VehicleGateway].
 *
 * - 기어: AAOS `CarPropertyManager`의 `GEAR_SELECTION` (Vehicle SDK Gear API는 Deprecated)
 * - 배터리·공조·조명: Pleos Vehicle SDK
 *
 * 영역(area)은 에뮬레이터 실측 결과를 따른다.
 * - 설정·실내 온도: 운전석 단독 영역
 * - 앰비언트: 좌석 묶음 영역 (capability가 알려주는 영역을 그대로 쓴다)
 */
class PleosVehicleGateway(context: Context) : VehicleGateway {

    private val appContext = context.applicationContext
    private val vehicle = Vehicle(appContext).apply { initialize() }
    private val hvac = vehicle.getHVAC()
    private val light = vehicle.getLight()
    private val evBattery = vehicle.getEvBattery()

    private val propertyManager: CarPropertyManager? by lazy {
        runCatching { Car.createCar(appContext).getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager }
            .onFailure { Log.w(TAG, "CarPropertyManager 연결 실패", it) }
            .getOrNull()
    }

    /** capability 조회 후 채워진다. */
    private var cachedCapabilities: VehicleCapabilities? = null
    private var ambientArea: SeatArea? = null

    override val gear: Flow<Gear> = callbackFlow {
        val manager = propertyManager
        if (manager == null) {
            trySend(Gear.UNKNOWN)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                trySend(PleosMapping.gearOf(value.value as? Int))
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                trySend(Gear.UNKNOWN)
            }
        }
        // 구독 직후 현재 값을 한 번 보내고, 이후 변화는 콜백으로 받는다.
        runCatching { manager.getProperty<Int>(VehiclePropertyIds.GEAR_SELECTION, 0).value }
            .onSuccess { trySend(PleosMapping.gearOf(it)) }
            .onFailure { Log.w(TAG, "기어 조회 실패", it); trySend(Gear.UNKNOWN) }
        @Suppress("DEPRECATION")
        manager.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        awaitClose { manager.unregisterCallback(callback) }
    }.distinctUntilChanged()

    override val batteryPercent: Flow<Int?> = callbackFlow {
        val listener = object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(value: Int?) {
                trySend(value)
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "배터리 구독 실패", e)
                trySend(null)
            }
        }
        evBattery.getEvBatteryStateOfCharge(onComplete = { trySend(it) }, onFailed = { trySend(null) })
        evBattery.registerEvBatteryStateOfCharge(listener)
        awaitClose { evBattery.unregisterEvBatteryStateOfCharge(listener) }
    }.distinctUntilChanged()

    override suspend fun capabilities(): VehicleCapabilities {
        cachedCapabilities?.let { return it }

        val temperatureRange = awaitCallback { ok, fail ->
            hvac.checkHvacTemperatureCapabilities(
                onComplete = { config, bounds, _ ->
                    val range = bounds[DRIVER_AREA]?.takeIf { config.accessType.writable() }
                    ok(range?.let { it.minValue..it.maxValue })
                },
                onFailed = fail,
            )
        }.valueOrNull()

        val brightness = awaitCallback { ok, fail ->
            light.checkAmbientLightBrightnessCapabilities(
                onComplete = { config, bounds -> ok(bounds.entries.firstOrNull()?.takeIf { config.accessType.writable() }) },
                onFailed = fail,
            )
        }.valueOrNull()

        val colorSupported = awaitCallback { ok, fail ->
            light.checkAmbientLightBrightnessColorCapabilities(
                onComplete = { config, areas -> ok(config.accessType.writable() && areas.isNotEmpty()) },
                onFailed = fail,
            )
        }.valueOrNull() ?: false

        ambientArea = brightness?.key
        return VehicleCapabilities(
            targetTemperatureRange = temperatureRange,
            ambientBrightnessRange = brightness?.value?.let { it.minValue..it.maxValue },
            ambientColorSupported = colorSupported,
        ).also { cachedCapabilities = it }
    }

    override suspend fun getTargetTemperature(): VehicleResult<Float> = awaitCallback<Float?> { ok, fail ->
        hvac.getHvacTemperature(DRIVER_AREA, TemperatureUnit.CELSIUS, ok, fail)
    }.requireValue()

    override suspend fun getCabinTemperature(): VehicleResult<Float> = awaitCallback<Float?> { ok, fail ->
        hvac.getHvacTemperatureCurrent(DRIVER_AREA, TemperatureUnit.CELSIUS, ok, fail)
    }.requireValue()

    override suspend fun setTargetTemperature(celsius: Float): VehicleResult<Unit> = awaitCallback { ok, fail ->
        hvac.setHvacTemperature(DRIVER_AREA, celsius, TemperatureUnit.CELSIUS, { _, _ -> ok(Unit) }, fail)
    }

    override suspend fun getAmbientLight(): VehicleResult<AmbientLight> {
        val area = requireAmbientArea() ?: return VehicleResult.Failure(FailureReason.NOT_SUPPORTED)
        val switch = awaitCallback<LightSwitch?> { ok, fail -> light.getAmbientLightSwitch(area, ok, fail) }
            .requireValue().onFailure { return it }
        val brightness = awaitCallback<Int?> { ok, fail -> light.getAmbientLightBrightness(area, ok, fail) }
            .requireValue().onFailure { return it }
        val color = awaitCallback<Int?> { ok, fail -> light.getAmbientLightBrightnessColor(area, ok, fail) }
            .requireValue().onFailure { return it }
        return VehicleResult.Success(AmbientLight(on = switch == LightSwitch.ON, brightness = brightness, colorRgb = color))
    }

    override suspend fun setAmbientLight(light: AmbientLight): VehicleResult<Unit> {
        val area = requireAmbientArea() ?: return VehicleResult.Failure(FailureReason.NOT_SUPPORTED)
        val switch = if (light.on) LightSwitch.ON else LightSwitch.OFF
        awaitCallback { ok, fail -> this.light.setAmbientLightSwitch(area, switch, { _, _ -> ok(Unit) }, fail) }
            .onFailure { return it }
        if (!light.on) return VehicleResult.Success(Unit)
        awaitCallback { ok, fail -> this.light.setAmbientLightBrightness(area, light.brightness, { _, _ -> ok(Unit) }, fail) }
            .onFailure { return it }
        if (cachedCapabilities?.ambientColorSupported == true) {
            awaitCallback { ok, fail -> this.light.setAmbientLightBrightnessColor(area, light.colorRgb, { _, _ -> ok(Unit) }, fail) }
                .onFailure { return it }
        }
        return VehicleResult.Success(Unit)
    }

    private suspend fun requireAmbientArea(): SeatArea? {
        if (ambientArea == null) capabilities()
        return ambientArea
    }

    /**
     * SDK의 onComplete/onFailed 콜백을 suspend 함수로 바꾼다.
     * 서비스가 응답하지 않으면 [TIMEOUT_MS] 뒤 CONNECTION 실패로 처리한다.
     */
    private suspend fun <T> awaitCallback(
        call: (ok: (T) -> Unit, fail: (Exception) -> Unit) -> Unit,
    ): VehicleResult<T> = withTimeoutOrNull(TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            call(
                { value -> if (continuation.isActive) continuation.resume(VehicleResult.Success(value)) },
                { error ->
                    Log.w(TAG, "차량 호출 실패", error)
                    if (continuation.isActive) {
                        continuation.resume(VehicleResult.Failure(PleosMapping.failureReasonOf(error), error.message))
                    }
                },
            )
        }
    } ?: VehicleResult.Failure(FailureReason.CONNECTION, "차량 응답 시간 초과")

    private fun <T> VehicleResult<T?>.requireValue(): VehicleResult<T> = when (this) {
        is VehicleResult.Success -> value?.let { VehicleResult.Success(it) }
            ?: VehicleResult.Failure(FailureReason.UNKNOWN, "값 없음")
        is VehicleResult.Failure -> this
    }

    private fun <T> VehicleResult<T>.valueOrNull(): T? = (this as? VehicleResult.Success)?.value

    private inline fun <T> VehicleResult<T>.onFailure(block: (VehicleResult.Failure) -> Nothing): T = when (this) {
        is VehicleResult.Success -> value
        is VehicleResult.Failure -> block(this)
    }

    private fun AccessType.writable() = this == AccessType.READ_WRITE || this == AccessType.WRITE

    companion object {
        private const val TAG = "PleosVehicleGateway"
        private const val TIMEOUT_MS = 5_000L
        private const val VEHICLE_SERVICE_PACKAGE = "ai.pleos.playground.service.vehicle"
        private val DRIVER_AREA = SeatArea(SeatValue.ROW_1_LEFT)

        /** Pleos 차량 서비스가 설치된 기기(Pleos 에뮬레이터·실차)인지 확인한다. */
        fun isAvailable(context: Context): Boolean = try {
            context.packageManager.getPackageInfo(VEHICLE_SERVICE_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

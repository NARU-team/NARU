package ai.naru.app.comma

import ai.naru.app.vehicle.AmbientLight
import ai.naru.app.vehicle.FailureReason
import ai.naru.app.vehicle.FakeVehicleGateway
import ai.naru.app.vehicle.Gear
import ai.naru.app.vehicle.VehicleGateway
import ai.naru.app.vehicle.VehicleResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 공용 VehicleGateway를 쉼표 모드의 안정된 UI 계약으로 변환한다.
 *
 * 제어 순서는 capability 확인 → 원래 값 백업 → 제어 → 재조회 확인이다.
 * 복구는 애플리케이션 스코프에서 실행되어 화면이 닫혀도 취소되지 않는다.
 */
class GatewayCommaVehicleRepository(
    private val gateway: VehicleGateway,
    private val applicationScope: CoroutineScope,
) : VehicleRepository {
    private val mutableState = MutableStateFlow(VehicleState())
    override val state: StateFlow<VehicleState> = mutableState
    override val isMock: Boolean = gateway is FakeVehicleGateway

    @Volatile
    private var support = VehicleCapabilities(
        climate = ControlSupport.UNKNOWN,
        drivingSignal = if (isMock) ControlSupport.UNSUPPORTED else ControlSupport.SUPPORTED,
        lighting = ControlSupport.UNKNOWN,
    )
    override val capabilities: VehicleCapabilities
        get() = support

    private var backedUpHvacPower: Boolean? = null
    private var backedUpTargetCelsius: Float? = null
    private var hasClimateBackup = false
    private var backedUpAmbientLight: AmbientLight? = null

    init {
        applicationScope.launch {
            gateway.gear.collect { gear ->
                mutableState.value = mutableState.value.copy(
                    parked = when (gear) {
                        Gear.PARK -> true
                        Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE -> false
                        Gear.UNKNOWN -> null
                    },
                    driving = when (gear) {
                        Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE -> DrivingStatus.DRIVING
                        Gear.PARK -> DrivingStatus.NOT_DRIVING
                        Gear.UNKNOWN -> DrivingStatus.UNKNOWN
                    },
                )
            }
        }
        applicationScope.launch {
            gateway.batteryPercent.collect { percent ->
                mutableState.value = mutableState.value.copy(
                    batteryPercent = percent?.takeIf { it in 0..100 },
                )
            }
        }
        applicationScope.launch { refreshVehicleSnapshot() }
    }

    override suspend fun requestClimate(targetCelsius: Float): ClimateResult {
        if (state.value.parkingStatus != ParkingStatus.PARKED) {
            return ClimateResult.Failed("주차 상태가 아니어서 공조를 변경하지 않았습니다.")
        }

        val gatewayCapabilities = runCatching { gateway.capabilities() }.getOrElse {
            support = support.copy(climate = ControlSupport.UNKNOWN)
            return ClimateResult.Unknown("공조 지원 여부를 확인하지 못했습니다.")
        }
        val range = gatewayCapabilities.targetTemperatureRange
            ?: run {
                support = support.copy(climate = ControlSupport.UNSUPPORTED)
                return ClimateResult.Unavailable("이 차량은 공조 온도 제어를 지원하지 않습니다.")
            }
        support = support.copy(
            climate = ControlSupport.SUPPORTED,
            climateTemperatureRange = range.start..range.endInclusive,
        )
        if (targetCelsius !in range) {
            return ClimateResult.Failed("요청 온도가 차량 지원 범위를 벗어났습니다.")
        }

        if (!hasClimateBackup) {
            val originalPower = when (val power = gateway.getHvacPower()) {
                is VehicleResult.Success -> power.value
                is VehicleResult.Failure -> {
                    return power.toClimateFailure("기존 공조 전원 상태를 확인하지 못해 변경하지 않았습니다.")
                }
            }
            backedUpHvacPower = originalPower

            if (!originalPower) {
                val powerOn = ensureHvacPowerOn()
                if (powerOn != null) {
                    backedUpHvacPower = null
                    return powerOn
                }
            }

            backedUpTargetCelsius = awaitTargetTemperature()
            if (backedUpTargetCelsius == null) {
                if (!originalPower) restoreOriginalPowerAfterFailedApply()
                backedUpHvacPower = null
                return ClimateResult.Unknown(
                    "기존 공조 온도를 안전하게 확인하지 못해 차량 설정을 변경하지 않았습니다.",
                )
            }
            hasClimateBackup = true
        }

        ensureHvacPowerOn()?.let { return it }
        when (val set = gateway.setTargetTemperature(targetCelsius)) {
            is VehicleResult.Failure -> {
                restoreOriginalPowerAfterFailedApply()
                return set.toClimateFailure("공조 온도를 변경하지 못했습니다.")
            }
            is VehicleResult.Success -> Unit
        }

        val actual = awaitTargetTemperature(expectedCelsius = targetCelsius)
        return if (actual == null) {
            ClimateResult.Unknown("변경 요청은 전달했지만 실제 공조 온도를 확인하지 못했습니다.")
        } else if (kotlin.math.abs(actual - targetCelsius) <= VERIFY_TOLERANCE_C) {
            ClimateResult.Applied(actual)
        } else {
            ClimateResult.Unknown("요청 후 실제 공조 온도가 일치하지 않습니다.")
        }
    }

    private suspend fun ensureHvacPowerOn(): ClimateResult? {
        when (val current = gateway.getHvacPower()) {
            is VehicleResult.Success -> if (current.value) return null
            is VehicleResult.Failure -> {
                return current.toClimateFailure("공조 전원 상태를 확인하지 못했습니다.")
            }
        }
        when (val set = gateway.setHvacPower(true)) {
            is VehicleResult.Failure -> return set.toClimateFailure("공조 전원을 켜지 못했습니다.")
            is VehicleResult.Success -> Unit
        }
        repeat(POWER_VERIFY_ATTEMPTS) { attempt ->
            when (val actual = gateway.getHvacPower()) {
                is VehicleResult.Success -> if (actual.value) return null
                is VehicleResult.Failure -> Unit
            }
            if (attempt < POWER_VERIFY_ATTEMPTS - 1) delay(POWER_VERIFY_DELAY_MS)
        }
        return ClimateResult.Unknown("공조 전원을 켰지만 실제 전원 상태를 확인하지 못했습니다.")
    }

    private suspend fun awaitTargetTemperature(expectedCelsius: Float? = null): Float? {
        var lastRead: Float? = null
        repeat(PROPERTY_VERIFY_ATTEMPTS) { attempt ->
            val actual = gateway.getTargetTemperature()
            if (actual is VehicleResult.Success) {
                lastRead = actual.value
                if (
                    expectedCelsius == null ||
                    kotlin.math.abs(actual.value - expectedCelsius) <= VERIFY_TOLERANCE_C
                ) return actual.value
            }
            if (attempt < PROPERTY_VERIFY_ATTEMPTS - 1) delay(PROPERTY_VERIFY_DELAY_MS)
        }
        return lastRead
    }

    private suspend fun restoreOriginalPowerAfterFailedApply() {
        if (backedUpHvacPower == false) {
            gateway.setHvacPower(false)
        }
    }

    override suspend fun applyLighting(brightness: Float): ClimateResult {
        if (state.value.parkingStatus != ParkingStatus.PARKED) {
            return ClimateResult.Failed("주차 상태가 아니어서 차량 조명을 변경하지 않았습니다.")
        }
        val gatewayCapabilities = runCatching { gateway.capabilities() }.getOrNull()
            ?: return ClimateResult.Unknown("차량 조명 지원 여부를 확인하지 못했습니다.")
        val range = gatewayCapabilities.ambientBrightnessRange ?: run {
            support = support.copy(lighting = ControlSupport.UNSUPPORTED)
            return ClimateResult.Unavailable("이 차량은 앰비언트 조명 제어를 지원하지 않습니다.")
        }
        support = support.copy(lighting = ControlSupport.SUPPORTED)
        if (backedUpAmbientLight == null) {
            backedUpAmbientLight = (gateway.getAmbientLight() as? VehicleResult.Success)?.value
                ?: return ClimateResult.Unknown("기존 차량 조명을 백업하지 못해 변경하지 않았습니다.")
        }
        val requested = AmbientLight(
            on = brightness > 0f,
            brightness = (range.first + (range.last - range.first) * brightness.coerceIn(0f, 1f))
                .toInt().coerceIn(range),
            colorRgb = backedUpAmbientLight?.colorRgb ?: 0xFFFFFF,
        )
        when (val set = gateway.setAmbientLight(requested)) {
            is VehicleResult.Failure -> return set.toClimateFailure("차량 조명 변경에 실패했습니다.")
            is VehicleResult.Success -> Unit
        }
        return when (val actual = gateway.getAmbientLight()) {
            is VehicleResult.Success -> if (
                actual.value.on == requested.on && actual.value.brightness == requested.brightness
            ) ClimateResult.Applied(requested.brightness.toFloat())
            else ClimateResult.Unknown("요청 후 실제 차량 조명 값이 일치하지 않습니다.")
            is VehicleResult.Failure -> actual.toClimateFailure("변경된 차량 조명을 확인하지 못했습니다.")
        }
    }

    override fun backupSnapshot(): VehicleBackup? = VehicleBackup(
        hvacPower = backedUpHvacPower,
        targetCelsius = backedUpTargetCelsius,
        ambientOn = backedUpAmbientLight?.on,
        ambientBrightness = backedUpAmbientLight?.brightness,
        ambientColorRgb = backedUpAmbientLight?.colorRgb,
    ).takeIf { it.hasAnyValue }

    override fun installBackup(backup: VehicleBackup) {
        backedUpHvacPower = backup.hvacPower
        backedUpTargetCelsius = backup.targetCelsius
        hasClimateBackup = backup.hvacPower != null || backup.targetCelsius != null
        backedUpAmbientLight = if (
            backup.ambientOn != null && backup.ambientBrightness != null && backup.ambientColorRgb != null
        ) {
            AmbientLight(backup.ambientOn, backup.ambientBrightness, backup.ambientColorRgb)
        } else null
    }

    override suspend fun restoreAndVerify(): RestorationReport {
        val restorePower = backedUpHvacPower
        val restoreTemperature = backedUpTargetCelsius
        val restoreAmbient = backedUpAmbientLight

        val temperatureOutcome = if (restoreTemperature == null) {
            if (support.climate == ControlSupport.UNSUPPORTED) RestorationOutcome.UNSUPPORTED
            else RestorationOutcome.UNKNOWN
        } else {
            val powerReady = ensureHvacPowerOn() == null
            if (!powerReady) {
                RestorationOutcome.MANUAL_CHECK
            } else {
                when (gateway.setTargetTemperature(restoreTemperature)) {
                    is VehicleResult.Failure -> RestorationOutcome.FAILURE
                    is VehicleResult.Success -> {
                        val actual = awaitTargetTemperature(expectedCelsius = restoreTemperature)
                        when {
                            actual == null -> RestorationOutcome.UNKNOWN
                            kotlin.math.abs(actual - restoreTemperature) <= VERIFY_TOLERANCE_C ->
                                RestorationOutcome.SUCCESS
                            else -> RestorationOutcome.MANUAL_CHECK
                        }
                    }
                }
            }
        }

        val powerOutcome = if (restorePower == null) {
            if (support.climate == ControlSupport.UNSUPPORTED) RestorationOutcome.UNSUPPORTED
            else RestorationOutcome.UNKNOWN
        } else {
            when (gateway.setHvacPower(restorePower)) {
                is VehicleResult.Failure -> RestorationOutcome.FAILURE
                is VehicleResult.Success -> when (val actual = gateway.getHvacPower()) {
                    is VehicleResult.Success -> if (actual.value == restorePower) {
                        RestorationOutcome.SUCCESS
                    } else RestorationOutcome.MANUAL_CHECK
                    is VehicleResult.Failure -> RestorationOutcome.UNKNOWN
                }
            }
        }

        val ambientOutcome = if (restoreAmbient == null) {
            if (support.lighting == ControlSupport.UNSUPPORTED) RestorationOutcome.UNSUPPORTED
            else RestorationOutcome.UNKNOWN
        } else {
            when (gateway.setAmbientLight(restoreAmbient)) {
                is VehicleResult.Failure -> RestorationOutcome.FAILURE
                is VehicleResult.Success -> when (val actual = gateway.getAmbientLight()) {
                    is VehicleResult.Success -> if (actual.value == restoreAmbient) {
                        RestorationOutcome.SUCCESS
                    } else RestorationOutcome.MANUAL_CHECK
                    is VehicleResult.Failure -> RestorationOutcome.UNKNOWN
                }
            }
        }

        if (temperatureOutcome == RestorationOutcome.SUCCESS && powerOutcome == RestorationOutcome.SUCCESS) {
            hasClimateBackup = false
            backedUpHvacPower = null
            backedUpTargetCelsius = null
        }
        if (ambientOutcome == RestorationOutcome.SUCCESS) backedUpAmbientLight = null
        refreshVehicleSnapshot()
        val vehicleOutcome = when {
            listOf(powerOutcome, temperatureOutcome, ambientOutcome).any {
                it == RestorationOutcome.FAILURE || it == RestorationOutcome.MANUAL_CHECK
            } -> RestorationOutcome.FAILURE
            listOf(powerOutcome, temperatureOutcome, ambientOutcome).all {
                it == RestorationOutcome.SUCCESS || it == RestorationOutcome.UNSUPPORTED
            } -> RestorationOutcome.SUCCESS
            else -> RestorationOutcome.UNKNOWN
        }
        return RestorationReport(
            vehicle = vehicleOutcome,
            hvacPower = powerOutcome,
            climateTemperature = temperatureOutcome,
            ambientLight = ambientOutcome,
        )
    }

    override fun restoreClimate() {
        if (!hasClimateBackup && backedUpAmbientLight == null) return
        applicationScope.launch {
            try {
                restoreAndVerify()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 백업은 성공 확인 전까지 유지되어 다음 재확인에서 다시 사용할 수 있다.
            }
        }
    }

    override fun refresh() {
        applicationScope.launch { refreshVehicleSnapshot() }
    }

    private suspend fun refreshVehicleSnapshot() {
        val gatewayCapabilities = runCatching { gateway.capabilities() }.getOrNull()
        support = support.copy(
            climate = when {
                gatewayCapabilities == null -> ControlSupport.UNKNOWN
                gatewayCapabilities.targetTemperatureRange == null -> ControlSupport.UNSUPPORTED
                else -> ControlSupport.SUPPORTED
            },
            climateTemperatureRange = gatewayCapabilities?.targetTemperatureRange?.let {
                it.start..it.endInclusive
            },
            lighting = when {
                gatewayCapabilities == null -> ControlSupport.UNKNOWN
                gatewayCapabilities.ambientBrightnessRange == null -> ControlSupport.UNSUPPORTED
                else -> ControlSupport.SUPPORTED
            },
        )
        val indoor = (gateway.getCabinTemperature() as? VehicleResult.Success)?.value
        val outdoor = (gateway.getOutsideTemperature() as? VehicleResult.Success)?.value
        mutableState.value = mutableState.value.copy(
            indoorCelsius = indoor,
            outdoorCelsius = outdoor,
        )
    }

    private fun VehicleResult.Failure.toClimateFailure(prefix: String): ClimateResult = when (reason) {
        FailureReason.NOT_SUPPORTED -> ClimateResult.Unavailable(prefix)
        FailureReason.PERMISSION -> ClimateResult.Failed("$prefix 차량 권한을 확인해 주세요.")
        FailureReason.CONNECTION -> ClimateResult.Failed("$prefix 차량 연결 상태를 확인해 주세요.")
        FailureReason.DRIVING_RESTRICTED -> ClimateResult.Failed("$prefix 주차 상태에서 다시 시도해 주세요.")
        FailureReason.UNKNOWN -> ClimateResult.Failed("$prefix 잠시 후 다시 시도하거나 온도 변경 없이 계속해 주세요.")
    }

    private companion object {
        const val VERIFY_TOLERANCE_C = 0.25f
        // Pleos Connect Emulator는 HVAC set 콜백 후 실제 VHAL 값 반영까지 약 3초가 걸릴 수 있다.
        const val POWER_VERIFY_ATTEMPTS = 7
        const val POWER_VERIFY_DELAY_MS = 500L
        const val PROPERTY_VERIFY_ATTEMPTS = 7
        const val PROPERTY_VERIFY_DELAY_MS = 500L
    }
}
